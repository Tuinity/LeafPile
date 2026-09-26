package ca.spottedleaf.sampler;

import ca.spottedleaf.common.util.ThrowUtil;
import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import ca.spottedleaf.ioutil.stream.AbstractBufferInputStream;
import ca.spottedleaf.ioutil.stream.channel.ChannelBufferInputStream;
import ca.spottedleaf.ioutil.stream.zstd.ZSTDBufferInputStream;
import ca.spottedleaf.ioutil.util.BufferReference;
import com.github.luben.zstd.ZstdDecompressCtx;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;

public final class SampleReader implements Closeable {
    private static final String BUFFER_KEY = "sampler:reader";

    private static final long FILE_BUFFER_SIZE = 16L * 1024L; // 16KiB
    private static final long DECOMPRESS_BUFFER_SIZE = 64L * 1024L; // 64KiB
    private static final long COMPRESS_BUFFER_SIZE = 16L * 1024L; // 16KiB

    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd "))
            .append(DateTimeFormatter.ofPattern("HH.mm.ss"))
            .appendOptional(DateTimeFormatter.ofPattern(".SSS"))
            .appendOptional(DateTimeFormatter.ofPattern(" z"))
            .toFormatter();

    private final AbstractBufferInputStream input;
    private final List<BufferReference> buffers = new ArrayList<>();
    private boolean eof;
    private int version = -1;

    private final SamplerTree.IdPool<String> stringPool = new SamplerTree.IdPool<>(null);
    private final SamplerTree samplerTree = new SamplerTree(null, 0L);

    private static final record Sample(long epochMs, long timeNS, long sampleNode) {
    }

    private final Long2ObjectOpenHashMap<String> threadNameMapping = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<Sample>> samplesByTid = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<SamplerInstance.RecordedEvent<? extends Record>>> eventsByTid = new Long2ObjectOpenHashMap<>();

    public SampleReader(final File file) throws IOException {
        ChannelBufferInputStream fin = null;
        ZSTDBufferInputStream in = null;

        try {
            final Buffer fileBuffer = new Buffer(false, BUFFER_KEY, MemoryAllocator.UnPooledNative.INSTANCE, FILE_BUFFER_SIZE, FILE_BUFFER_SIZE);
            this.buffers.add(new BufferReference(fileBuffer, BUFFER_KEY));

            fin = new ChannelBufferInputStream(fileBuffer, FileChannel.open(file.toPath(), StandardOpenOption.READ));

            final Buffer decompressedBuffer = new Buffer(false, BUFFER_KEY, MemoryAllocator.UnPooledNative.INSTANCE, DECOMPRESS_BUFFER_SIZE, DECOMPRESS_BUFFER_SIZE);
            this.buffers.add(new BufferReference(decompressedBuffer, BUFFER_KEY));

            final Buffer compressedBuffer = new Buffer(false, BUFFER_KEY, MemoryAllocator.UnPooledNative.INSTANCE, COMPRESS_BUFFER_SIZE, COMPRESS_BUFFER_SIZE);
            this.buffers.add(new BufferReference(compressedBuffer, BUFFER_KEY));

            in = new ZSTDBufferInputStream(
                    decompressedBuffer, compressedBuffer,
                    new ZstdDecompressCtx(), ZstdDecompressCtx::close,
                    fin
            );

            this.input = in;
        } catch (final Throwable thr) {
            try {
                try {
                    if (in != null) {
                        in.close(); // will close fin
                    } else if (fin != null) {
                        fin.close();
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
    }

    public Long2ObjectOpenHashMap<String> getThreads() {
        return this.threadNameMapping.clone();
    }

    public List<String> toString(final long tid, final long epochMSFrom, final long epochMSTo) {
        final List<Sample> samples = this.samplesByTid.get(tid);
        if (samples == null) {
            if (this.threadNameMapping.containsKey(tid)) {
                return new ArrayList<>();
            }
            throw new IllegalStateException("Thread not recorded: " + tid);
        }

        final Long2LongOpenHashMap samplesByNode = new Long2LongOpenHashMap();

        for (final Sample sample : samples) {
            if (sample.epochMs < epochMSFrom || sample.epochMs > epochMSTo) {
                continue;
            }
            samplesByNode.addTo(sample.sampleNode, 1L);
        }

        final List<String> ret = this.samplerTree.dumpToString(samplesByNode);
        final List<SamplerInstance.RecordedEvent<? extends Record>> events = this.eventsByTid.get(tid);
        if (events != null) {
            ret.add("");
            ret.add("Events: ");

            for (final SamplerInstance.RecordedEvent<? extends Record> event : events) {
                if (event.timeEpochMS() < epochMSFrom || event.timeEpochMS() > epochMSTo) {
                    continue;
                }

                final String date = DATE_FORMAT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(event.timeEpochMS()), ZoneId.systemDefault()));
                ret.add(date + ":" + event.nanoTime() + ":" + event.event().name() + ":" + event.value());
            }
        }

        return ret;
    }

    private String lookupString(final int id) {
        if (id == SampleWriter.NULL_STRING_ID) {
            return null;
        }
        final String ret = this.stringPool.fromId((long)id);
        if (ret == null) {
            throw new IllegalStateException("Missing string with id: " + id);
        }
        return ret;
    }

    public void readAll() throws IOException {
        this.read(0L, Long.MAX_VALUE);
    }

    private String readString() throws IOException {
        return this.lookupString(this.input.readUnsignedVarInt());
    }

    public synchronized void read(final long fromTimeEpochMS, final long toTimeEpochMS) throws IOException {
        byte prevOp = -1; // help breakpoint debugging
        for (;;) {
            final byte type = this.input.readByte();

            switch (type) {
                case SampleWriter.HEADER: {
                    this.version = this.input.readInt();
                    break;
                }
                case SampleWriter.STREAM_END: {
                    this.eof = true;
                    return;
                }
                case SampleWriter.NEW_STRING: {
                    final int id = this.input.readUnsignedVarInt();
                    final String str = this.input.readUTF();

                    this.stringPool.add(id, str);
                    break;
                }
                case SampleWriter.NEW_STACK: {
                    final long elemId = this.input.readUnsignedVarLong();

                    final String classLoaderName = this.readString();
                    final String moduleName = this.readString();
                    final String moduleVersion = this.readString();
                    final String declaringClass = this.readString();
                    final String methodName = this.readString();
                    final String fileName = this.readString();
                    final int lineNumber = this.input.readSignedVarInt();

                    final StackTraceElement stackTraceElement = new StackTraceElement(
                            classLoaderName, moduleName, moduleVersion, declaringClass, methodName, fileName, lineNumber
                    );

                    this.samplerTree.getStackTracePool().add(elemId, stackTraceElement);

                    break;
                }
                case SampleWriter.NEW_SAMPLE_NODE: {
                    final int nodeId = this.input.readUnsignedVarInt();
                    final int stackId = this.input.readUnsignedVarInt();
                    final int parentId = this.input.readUnsignedVarInt();

                    this.samplerTree.addNewNode((long)nodeId, (long)stackId, (long)parentId);
                    break;
                }
                case SampleWriter.NEW_THREAD: {
                    final long tid = this.input.readUnsignedVarLong();
                    final String threadName = this.readString();

                    this.threadNameMapping.put(tid, threadName);
                    break;
                }
                case SampleWriter.THREAD_SAMPLE: {
                    final long tid = this.input.readUnsignedVarLong();
                    final long epochMS = this.input.readUnsignedVarLong();
                    final long nanoTime = this.input.readUnsignedVarLong();
                    final int sampleNode = this.input.readUnsignedVarInt();

                    if (!this.threadNameMapping.containsKey(tid)) {
                        throw new IllegalStateException("Unknown tid: " + tid);
                    }
                    if (!this.samplerTree.hasNode((long)sampleNode)) {
                        throw new IllegalStateException("Unknown node id: " + tid);
                    }

                    if (epochMS < fromTimeEpochMS || epochMS > toTimeEpochMS) {
                        // not in range
                        break;
                    }

                    this.samplesByTid.computeIfAbsent(tid, (final long keyInMap) -> {
                        return new ArrayList<>();
                    }).add(new Sample(epochMS, nanoTime, (long)sampleNode));
                    break;
                }
                case SampleWriter.THREAD_EVENT: {
                    final long tid = this.input.readUnsignedVarLong();
                    final long epochMS = this.input.readUnsignedVarLong();
                    final long nanoTime = this.input.readUnsignedVarLong();
                    final String eventName = this.readString();

                    final EventRegistry.RegisteredEvent<? extends Record> event = EventRegistry.getByName(eventName);
                    if (event == null) {
                        throw new IllegalStateException("Unknown event: " + eventName);
                    }

                    final Object[] toRead = new Object[event.recordComponents().length];
                    for (int i = 0; i < event.recordComponents().length; ++i) {
                        final RecordComponent component = event.recordComponents()[i];
                        final Class<?> cls = component.getType();

                        final Object value;
                        if (byte.class == cls) {
                            value = Byte.valueOf(this.input.readByte());
                        } else if (short.class == cls) {
                            value = Short.valueOf(this.input.readShort());
                        } else if (char.class == cls) {
                            value = Character.valueOf(this.input.readChar());
                        } else if (int.class == cls) {
                            value = Integer.valueOf(this.input.readInt());
                        } else if (long.class == cls) {
                            value = Long.valueOf(this.input.readLong());
                        } else if (float.class == cls) {
                            value = Float.valueOf(this.input.readFloat());
                        } else if (double.class == cls) {
                            value = Double.valueOf(this.input.readDouble());
                        } else if (String.class == cls) {
                            value = this.readString();
                        } else {
                            throw new IllegalStateException(cls.getName());
                        }

                        toRead[i] = value;
                    }

                    if (epochMS < fromTimeEpochMS || epochMS > toTimeEpochMS) {
                        // not in range
                        break;
                    }

                    try {
                        final Record value = event.constructor().newInstance(toRead);

                        this.eventsByTid.computeIfAbsent(tid, (final long keyInMap) -> {
                            return new ArrayList<>();
                        }).add(new SamplerInstance.RecordedEvent(
                                epochMS, nanoTime, tid, event, value
                        ));
                    } catch (final Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                    break;
                }

                default: {
                    throw new IllegalStateException("Unknown type: " + type);
                }
            }

            prevOp = type;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            if (this.input != null) {
                this.input.close();
            }
        } finally {
            try {
                BufferReference.releaseAll(this.buffers);
            } finally {
                this.buffers.clear();
            }
        }
    }
}
