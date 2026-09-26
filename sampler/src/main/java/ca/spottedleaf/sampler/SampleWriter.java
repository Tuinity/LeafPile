package ca.spottedleaf.sampler;

import ca.spottedleaf.common.util.ThrowUtil;
import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import ca.spottedleaf.ioutil.stream.channel.ChannelBufferOutputStream;
import ca.spottedleaf.ioutil.stream.zstd.ZSTDBufferOutputStream;
import ca.spottedleaf.ioutil.util.BufferReference;
import com.github.luben.zstd.ZstdCompressCtx;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class SampleWriter implements Closeable {

    private static final int VERSION = 0;

    private static final String BUFFER_KEY = "sampler:writer";

    private static final long FILE_BUFFER_SIZE = 16L * 1024L; // 16KiB
    private static final long DECOMPRESS_BUFFER_SIZE = 64L * 1024L; // 64KiB
    private static final long COMPRESS_BUFFER_SIZE = 16L * 1024L; // 16KiB

    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    /*
     * Unsigned Int: version
     */
    public static final byte HEADER = 0;
    /*
     * No data
     */
    public static final byte STREAM_END = 1;
    /*
     * Unsigned VarInt: id
     * DataOutput UTF: string
     */
    public static final byte NEW_STRING = 2;
    /*
     * Unsigned VarInt: id
     * Unsigned VarInt: class loader name string id
     * Unsigned VarInt: module name string id
     * Unsigned VarInt: module version string id
     * Unsigned VarInt: declaring class string id
     * Unsigned VarInt: method name string id
     * Unsigned VarInt: file name string id
     * Signed VarInt: line number
     */
    public static final byte NEW_STACK = 3;
    /*
     * Unsigned VarInt: id
     * Unsigned VarInt: stack id
     * Unsigned VarInt: sample node parent id
     */
    public static final byte NEW_SAMPLE_NODE = 4;
    /*
     * Unsigned VarLong: thread id (Thread#threadId())
     * Unsigned VarInt: thread name string id
     */
    public static final byte NEW_THREAD = 5;
    /*
     * Unsigned VarLong: thread id (Thread#threadId())
     * Unsigned VarLong: epoch time (ms)
     * Unsigned VarLong: nano time (ns)
     * Unsigned VarInt: sample node id
     */
    public static final byte THREAD_SAMPLE = 6;
    /*
     * Unsigned VarLong: thread id (Thread#threadId())
     * Unsigned VarLong: epoch time (ms)
     * Unsigned VarLong: nano time (ns)
     * Unsigned VarInt: event name string id
     * Record fields: (byte, short, char, integer, long, float, double, String)
     * String record fields are serialized as signed VarInt
     */
    public static final byte THREAD_EVENT = 7;

    public static final int NULL_STRING_ID = 0;

    private final AbstractBufferOutputStream output;
    private final List<BufferReference> buffers = new ArrayList<>();

    private final SamplerTree.IdPool<String> stringPool;

    public SampleWriter(final File file) throws IOException {
        ChannelBufferOutputStream fout = null;
        ZSTDBufferOutputStream out = null;

        try {
            final Buffer fileBuffer = new Buffer(false, BUFFER_KEY, MemoryAllocator.UnPooledNative.INSTANCE, FILE_BUFFER_SIZE, FILE_BUFFER_SIZE);
            this.buffers.add(new BufferReference(fileBuffer, BUFFER_KEY));

            fout = new ChannelBufferOutputStream(fileBuffer, FileChannel.open(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE));

            final Buffer decompressedBuffer = new Buffer(false, BUFFER_KEY, MemoryAllocator.UnPooledNative.INSTANCE, DECOMPRESS_BUFFER_SIZE, DECOMPRESS_BUFFER_SIZE);
            this.buffers.add(new BufferReference(decompressedBuffer, BUFFER_KEY));

            final Buffer compressedBuffer = new Buffer(false, BUFFER_KEY, MemoryAllocator.UnPooledNative.INSTANCE, COMPRESS_BUFFER_SIZE, COMPRESS_BUFFER_SIZE);
            this.buffers.add(new BufferReference(compressedBuffer, BUFFER_KEY));

            out = new ZSTDBufferOutputStream(
                    decompressedBuffer, compressedBuffer,
                    new ZstdCompressCtx(), ZstdCompressCtx::close,
                    fout
            );

            this.output = out;
        } catch (final Throwable thr) {
            try {
                try {
                    if (out != null) {
                        out.close(); // will close fout
                    } else if (fout != null) {
                        fout.close();
                    }
                } finally {
                    try {
                        BufferReference.releaseAll(this.buffers);
                    } finally {
                        this.buffers.clear();
                    }
                }
            } finally {
                ThrowUtil.throwUnchecked(thr);
                throw new RuntimeException(); // unreachable
            }
        }

        this.stringPool = new SamplerTree.IdPool<>((final Long id, final String string) -> {
            SampleWriter.this.writeNewString(id.longValue(), string);
        });
    }

    public int getStringCount() {
        return this.stringPool.size();
    }

    private int getStringIdNullable(final String str) {
        return str == null ? NULL_STRING_ID : castToInt(this.stringPool.getId(str));
    }

    private static int castToInt(final long val) {
        if (val < (long)Integer.MIN_VALUE || val > (long)Integer.MAX_VALUE) {
            throw new IllegalStateException();
        }
        return (int)val;
    }

    public synchronized void writeHeader() {
        try {
            this.output.writeByte(HEADER);
            this.output.writeInt(VERSION);
        } catch (final IOException ex) {
            ThrowUtil.throwUnchecked(ex);
        }
    }

    public synchronized void writeNewString(final long id, final String string) {
        try {
            this.output.writeByte(NEW_STRING);
            this.output.writeUnsignedVarInt(castToInt(id));

            this.output.writeUTF(string);
        } catch (final IOException ex) {
            ThrowUtil.throwUnchecked(ex);
        }
    }

    public synchronized void writeNewStack(final long id, final StackTraceElement stack) {
        try {
            // get string ids, writing if needed
            final int classLoaderName = this.getStringIdNullable(stack.getClassLoaderName());
            final int moduleName = this.getStringIdNullable(stack.getModuleName());
            final int moduleVersion = this.getStringIdNullable(stack.getModuleVersion());
            final int declaringClass = this.getStringIdNullable(stack.getClassName());
            final int methodName = this.getStringIdNullable(stack.getMethodName());
            final int fileName = this.getStringIdNullable(stack.getFileName());
            final int lineNumber = stack.getLineNumber();

            this.output.writeByte(NEW_STACK);
            this.output.writeUnsignedVarInt(castToInt(id));

            this.output.writeUnsignedVarInt(classLoaderName);
            this.output.writeUnsignedVarInt(moduleName);
            this.output.writeUnsignedVarInt(moduleVersion);
            this.output.writeUnsignedVarInt(declaringClass);
            this.output.writeUnsignedVarInt(methodName);
            this.output.writeUnsignedVarInt(fileName);
            this.output.writeSignedVarInt(lineNumber);
        } catch (final IOException ex) {
            ThrowUtil.throwUnchecked(ex);
        }
    }

    public synchronized void writeNewSampleNode(final long nodeId, final long stackId, final long parentId) {
        try {
            this.output.writeByte(NEW_SAMPLE_NODE);

            this.output.writeUnsignedVarInt(castToInt(nodeId));
            this.output.writeUnsignedVarInt(castToInt(stackId));
            this.output.writeUnsignedVarInt(castToInt(parentId));
        } catch (final IOException ex) {
            ThrowUtil.throwUnchecked(ex);
        }
    }

    public synchronized void writeNewThread(final long tid, final String name) {
        try {
            final int nameId = this.getStringIdNullable(name);

            this.output.writeByte(NEW_THREAD);

            this.output.writeUnsignedVarLong(tid);
            this.output.writeUnsignedVarInt(nameId);
        } catch (final IOException ex) {
            ThrowUtil.throwUnchecked(ex);
        }
    }

    public synchronized void writeSample(final long tid, final long epochTimeMS, final long nanoTime, final long sampleNode) {
        try {
            this.output.writeByte(THREAD_SAMPLE);

            this.output.writeUnsignedVarLong(tid);
            this.output.writeUnsignedVarLong(epochTimeMS);
            this.output.writeUnsignedVarLong(nanoTime);
            this.output.writeUnsignedVarInt(castToInt(sampleNode));
        } catch (final IOException ex) {
            ThrowUtil.throwUnchecked(ex);
        }
    }

    private static record ProcessedString(int stringId) {}

    public synchronized <T extends Record> void writeEvent(final long tid, final long epochTimeMS, final long nanoTime, final EventRegistry.RegisteredEvent<T> event, final T value) {
        try {
            final Object[] toWrite = new Object[event.recordComponents().length];
            for (int i = 0; i < event.recordComponents().length; ++i) {
                final RecordComponent component = event.recordComponents()[i];
                try {
                    final Object componentValue = component.getAccessor().invoke(value, EMPTY_OBJECT_ARRAY);
                    if (componentValue instanceof String componentString) {
                        toWrite[i] = new ProcessedString(this.getStringIdNullable(componentString));
                    } else {
                        toWrite[i] = componentValue;
                    }
                } catch (final Exception ex) {
                    throw new RuntimeException(ex);
                }
            }

            final int eventNameId = this.getStringIdNullable(event.name());

            this.output.writeByte(THREAD_EVENT);

            this.output.writeUnsignedVarLong(tid);
            this.output.writeUnsignedVarLong(epochTimeMS);
            this.output.writeUnsignedVarLong(nanoTime);
            this.output.writeUnsignedVarInt(eventNameId);

            for (final Object recordComponent : toWrite) {
                final Class<?> cls = recordComponent.getClass();

                if (Byte.class == cls) {
                    this.output.writeByte(((Byte)recordComponent).byteValue());
                } else if (Short.class == cls) {
                    this.output.writeShort(((Short)recordComponent).shortValue());
                } else if (Character.class == cls) {
                    this.output.writeChar(((Character)recordComponent).charValue());
                } else if (Integer.class == cls) {
                    this.output.writeInt(((Integer)recordComponent).intValue());
                } else if (Long.class == cls) {
                    this.output.writeLong(((Long)recordComponent).longValue());
                } else if (Float.class == cls) {
                    this.output.writeFloat(((Float)recordComponent).floatValue());
                } else if (Double.class == cls) {
                    this.output.writeDouble(((Double)recordComponent).doubleValue());
                } else if (ProcessedString.class == cls) {
                    this.output.writeSignedVarInt(((ProcessedString)recordComponent).stringId);
                } else {
                    throw new IllegalStateException(cls.getName());
                }
            }
        } catch (final IOException ex) {
            ThrowUtil.throwUnchecked(ex);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.buffers.isEmpty()) {
            return;
        }

        try {
            this.output.writeByte(STREAM_END);
        } finally {
            try {
                this.output.close();
            } finally {
                try {
                    BufferReference.releaseAll(this.buffers);
                } finally {
                    this.buffers.clear();
                }
            }
        }
    }
}
