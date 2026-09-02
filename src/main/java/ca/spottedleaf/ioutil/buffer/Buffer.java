package ca.spottedleaf.ioutil.buffer;

import ca.spottedleaf.common.util.IntegerUtil;
import ca.spottedleaf.ioutil.refcount.ReferenceCounted;
import java.io.EOFException;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

public final class Buffer extends ReferenceCounted {

    private static final ValueLayout.OfByte BYTE_NO = ValueLayout.JAVA_BYTE.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());

    private static final ValueLayout.OfShort SHORT_NO = ValueLayout.JAVA_SHORT.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfShort SHORT_BE = ValueLayout.JAVA_SHORT.withByteAlignment(1L).withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfShort SHORT_LE = ValueLayout.JAVA_SHORT.withByteAlignment(1L).withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final ValueLayout.OfChar CHAR_NO = ValueLayout.JAVA_CHAR.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfChar CHAR_BE = ValueLayout.JAVA_CHAR.withByteAlignment(1L).withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfChar CHAR_LE = ValueLayout.JAVA_CHAR.withByteAlignment(1L).withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final ValueLayout.OfInt INT_NO = ValueLayout.JAVA_INT.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfInt INT_BE = ValueLayout.JAVA_INT.withByteAlignment(1L).withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt INT_LE = ValueLayout.JAVA_INT.withByteAlignment(1L).withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final ValueLayout.OfLong LONG_NO = ValueLayout.JAVA_LONG.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfLong LONG_BE = ValueLayout.JAVA_LONG.withByteAlignment(1L).withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong LONG_LE = ValueLayout.JAVA_LONG.withByteAlignment(1L).withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final ValueLayout.OfFloat FLOAT_NO = ValueLayout.JAVA_FLOAT.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfFloat FLOAT_BE = ValueLayout.JAVA_FLOAT.withByteAlignment(1L).withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat FLOAT_LE = ValueLayout.JAVA_FLOAT.withByteAlignment(1L).withOrder(ByteOrder.LITTLE_ENDIAN);

    private static final ValueLayout.OfDouble DOUBLE_NO = ValueLayout.JAVA_DOUBLE.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());
    private static final ValueLayout.OfDouble DOUBLE_BE = ValueLayout.JAVA_DOUBLE.withByteAlignment(1L).withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble DOUBLE_LE = ValueLayout.JAVA_DOUBLE.withByteAlignment(1L).withOrder(ByteOrder.LITTLE_ENDIAN);

    public static final long MAX_VAR_INT_BYTES = 5L;
    public static final long MAX_VAR_LONG_BYTES = 10L;

    private final MemoryAllocator allocator;
    private Memory memory;
    private MemorySegment segment;
    private ByteBuffer memoryAsBuffer;

    // location of next byte to be read
    private long readerIndex;
    // location of next byte to write
    private long writerIndex;

    private final long maxCapacity;

    public Buffer(final boolean debug, final String initialKey, final MemoryAllocator allocator, final long initialCapacity, final long maxCapacity) {
        super(debug, initialKey);

        this.allocator = allocator;
        this.maxCapacity = maxCapacity;

        this.setMemory(allocator.findMemory(initialCapacity, maxCapacity));
    }

    public Buffer(final boolean debug, final String initialKey, final byte[] buffer) {
        super(debug, initialKey);

        this.allocator = null;
        this.maxCapacity = buffer.length;

        this.setMemory(Memory.ofHeap(buffer));
    }

    public Buffer(final boolean debug, final String initialKey, final MemorySegment segment) {
        super(debug, initialKey);

        this.allocator = null;
        this.maxCapacity = segment.byteSize();

        this.setMemory(Memory.ofSegment(segment));
    }

    public void verifyAgainst(final MemorySegment seg, final long segOff, final long thisOff, final long nBytes) {
        for (long i = 0L; i < nBytes; ++i) {
            final byte our = this.segment.get(BYTE_NO, thisOff + i);
            final byte theirs = seg.get(BYTE_NO, segOff + i);
            if (our != theirs) {
                throw new IllegalStateException("Expected " + theirs + " (idx: " + (segOff + i) + ") but got " + our + " (idx: " + (thisOff + i) + ") at byte " + (i + 1L));
            }
        }
    }

    private void setMemory(final Memory memory) {
        this.memoryAsBuffer = null;
        this.memory = memory;
        this.segment = memory == null ? null : memory.getSegment();
    }

    private ByteBuffer makeByteBuffer() {
        if (this.segment == null) {
            return null;
        }
        return this.memoryAsBuffer = this.segment.asByteBuffer();
    }

    public ByteBuffer getMemoryAsBuffer() {
        if (this.memoryAsBuffer != null) {
            return this.memoryAsBuffer;
        }
        return this.makeByteBuffer();
    }

    public ByteBuffer getBufferAsRead() {
        final ByteBuffer ret = this.getMemoryAsBuffer();

        ret.clear();
        ret.position(castIndexToInt(this.readerIndex));
        ret.limit(castIndexToInt(this.writerIndex));

        return ret;
    }

    public ByteBuffer getBufferAsWriter() {
        final ByteBuffer ret = this.getMemoryAsBuffer();

        ret.clear();
        ret.position(castIndexToInt(this.writerIndex));

        return ret;
    }

    public MemorySegment getMemoryAsSegment() {
        return this.segment;
    }

    @Override
    protected void refCountZero() {
        final Memory memory = this.memory;
        this.setMemory(null);

        if (this.allocator != null) {
            this.allocator.returnMemory(memory);
        }
    }

    public boolean isNative() {
        return this.segment.isNative();
    }

    public long getCurrentCapacity() {
        return this.segment.byteSize();
    }

    public long getMaxCapacity() {
        return this.maxCapacity;
    }

    public long getReaderIndex() {
        return this.readerIndex;
    }

    public void setReaderIndex(final long readerIndex) {
        if (readerIndex < 0) {
            throw new IllegalStateException("Negative reader index");
        }
        if (readerIndex > this.writerIndex) {
            throw new IllegalStateException("Reader index cannot be greater-than writer index");
        }

        this.readerIndex = readerIndex;
    }

    public long getWriterIndex() {
        return this.writerIndex;
    }

    public void setWriterIndex(final long writerIndex) {
        if (this.readerIndex > writerIndex) {
            throw new IllegalStateException("Reader index cannot be greater-than writer index");
        }
        if (writerIndex > this.getCurrentCapacity()) {
            throw new IllegalStateException("Writer index > capacity");
        }

        this.writerIndex = writerIndex;
    }

    public void setIndices(final long readerIndex, final long writerIndex) {
        if (readerIndex < 0) {
            throw new IllegalStateException("Negative reader index");
        }
        if (readerIndex > writerIndex) {
            throw new IllegalStateException("Reader index cannot be greater-than writer index");
        }
        if (writerIndex > this.getCurrentCapacity()) {
            throw new IllegalStateException("Writer index > capacity");
        }

        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    public void clear() {
        this.readerIndex = 0L;
        this.writerIndex = 0L;
    }

    public void clearAndFill(final byte value) {
        this.clear();
        this.segment.fill(value);
    }

    public long getReadableBytes() {
        return this.writerIndex - this.readerIndex;
    }

    public long shiftReaderToZero() {
        final long toShift = this.readerIndex;
        final long readable = this.writerIndex - toShift;
        if (toShift == 0L) {
            return 0L;
        }

        if (readable != 0L) {
            final MemorySegment segment = this.segment;
            MemorySegment.copy(segment, toShift, segment, 0L, readable);
        }

        this.readerIndex -= toShift;
        this.writerIndex -= toShift;

        return toShift;
    }

    public long getWritableBytes() {
        return this.maxCapacity - this.writerIndex;
    }

    public long getImmediatelyWritableBytes() {
        return this.segment.byteSize() - this.writerIndex;
    }

    public Buffer copy(final boolean debug, final String key) {
        return this.copy(debug, key, this.allocator);
    }

    public Buffer copy(final boolean debug, final String key, final MemoryAllocator allocator) {
        final long size = this.segment.byteSize();

        final Buffer ret = new Buffer(debug, key, allocator, size, this.maxCapacity);
        ret.setIndices(this.readerIndex, this.writerIndex);

        MemorySegment.copy(this.segment, 0L, ret.segment, 0L, size);

        return ret;
    }

    public long getNativeAddress() {
        if (!this.isNative()) {
            throw new IllegalStateException("Not native memory");
        }
        return this.segment.address();
    }

    public Object getBackingHeapObject() {
        if (this.isNative()) {
            throw new IllegalStateException("Not heap memory");
        }
        return this.segment.heapBase().orElse(null);
    }

    private static IndexOutOfBoundsException ioobe(final long read, final long write) {
        return new IndexOutOfBoundsException("Reader index: " + read + ", writer index: " + write);
    }

    private static IndexOutOfBoundsException ioobe(final long read, final long write, final long len) {
        return new IndexOutOfBoundsException("Reader index: " + read + ", writer index: " + write + ", for len: " + len);
    }

    public static int castIndexToInt(final long idx) {
        if (idx < 0L || idx > (long)Integer.MAX_VALUE) {
            throw new IllegalStateException();
        }
        return (int)idx;
    }

    private long advanceRead(final long by) {
        if (by < 0L) {
            throw new IllegalArgumentException("Negative amount");
        }
        final long oldReaderIndex = this.readerIndex;
        final long newReaderIdx = oldReaderIndex + by;
        if ((newReaderIdx - this.writerIndex) > 0L) { // handle overflow
            throw ioobe(newReaderIdx, this.writerIndex, by);
        }
        this.readerIndex = newReaderIdx;

        return oldReaderIndex;
    }

    public void unread(final long by) {
        if (by < 0L) {
            throw new IllegalArgumentException("Negative amount");
        }
        final long readerIdx = this.readerIndex;
        if (readerIdx - by < 0L) {
            throw new IndexOutOfBoundsException("Cannot unread: " + by + ", reader index: " + readerIdx);
        }
        this.readerIndex = readerIdx - by;
    }

    private void tryAllocate(final Memory currMemory, final long minCapacity) {
        // try to allocate more
        long nextCap = Math.max(2L, minCapacity);
        nextCap += Math.max(1L, (nextCap >>> 1)); // allocate 1.5x current size
        if (nextCap < 0L) {
            nextCap = Long.MAX_VALUE;
        }
        nextCap = Math.min(this.maxCapacity, nextCap);
        if (minCapacity > nextCap) {
            throw new IllegalStateException("Reached maximum capacity: max " + this.maxCapacity);
        }

        this.setMemory(this.allocator.findMemory(nextCap, this.maxCapacity));
        MemorySegment.copy(currMemory.segment, 0L, this.segment, 0L, currMemory.segment.byteSize());

        this.allocator.returnMemory(currMemory);
    }

    public long advanceWrite(final long by) {
        if (by < 0L) {
            throw new IllegalArgumentException("Negative amount");
        }

        final long oldWriterIndex = this.writerIndex;
        final long newWriterIndex = oldWriterIndex + by;

        if (newWriterIndex < 0L) {
            throw new OutOfMemoryError("Max size");
        } else if (newWriterIndex <= this.segment.byteSize()) {
            this.writerIndex = newWriterIndex;

            return oldWriterIndex;
        }
        // newWriterIndex >= 0 && newWriterIndex > this.segment.byteSize()

        return this.advanceWriteAllocate(oldWriterIndex, newWriterIndex);
    }

    private long advanceWriteAllocate(final long oldWriterIndex, final long newWriterIndex) {
        this.tryAllocate(this.memory, newWriterIndex);
        this.writerIndex = newWriterIndex;
        return oldWriterIndex;
    }

    public void ensureReadable(final long bytes) {
        final long reader = this.readerIndex;
        final long writer = this.writerIndex;
        if ((writer - reader) < bytes) {
            throw ioobe(this.readerIndex, this.writerIndex, bytes);
        }
    }

    public void ensureImmediatelyWritable(final long bytes) {
        this.ensureCapacity(this.writerIndex + bytes);
    }

    public void ensureCapacity(final long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Max size");
        }
        if (bytes > this.segment.byteSize()) {
            this.tryAllocate(this.memory, bytes);
        }
    }

    // read

    public byte readByte() {
        return this.segment.get(BYTE_NO, this.advanceRead(1L));
    }

    public byte readByte(final long index) {
        return this.segment.get(BYTE_NO, index);
    }

    public void readBytes(final byte[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, BYTE_NO, this.advanceRead((long)len), dst, off, len);
    }

    public void readBytes(final long index, final byte[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, BYTE_NO, index, dst, off, len);
    }



    public short readShortNO() {
        return this.segment.get(SHORT_NO, this.advanceRead(2L));
    }

    public short readShortNO(final long index) {
        return this.segment.get(SHORT_NO, index);
    }

    public void readShortsNO(final short[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, SHORT_NO, this.advanceRead((long)len << 1), dst, off, len);
    }

    public void readShortsNO(final long index, final short[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, SHORT_NO, index, dst, off, len);
    }


    public short readShortBE() {
        return this.segment.get(SHORT_BE, this.advanceRead(2L));
    }

    public short readShortBE(final long index) {
        return this.segment.get(SHORT_BE, index);
    }

    public void readShortsBE(final short[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, SHORT_BE, this.advanceRead((long)len << 1), dst, off, len);
    }

    public void readShortsBE(final long index, final short[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, SHORT_BE, index, dst, off, len);
    }


    public short readShortLE() {
        return this.segment.get(SHORT_LE, this.advanceRead(2L));
    }

    public short readShortLE(final long index) {
        return this.segment.get(SHORT_LE, index);
    }

    public void readShortsLE(final short[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, SHORT_LE, this.advanceRead((long)len << 1), dst, off, len);
    }

    public void readShortsLE(final long index, final short[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, SHORT_LE, index, dst, off, len);
    }



    public char readCharNO() {
        return this.segment.get(CHAR_NO, this.advanceRead(2L));
    }

    public char readCharNO(final long index) {
        return this.segment.get(CHAR_NO, index);
    }

    public void readCharsNO(final char[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, CHAR_NO, this.advanceRead((long)len << 1), dst, off, len);
    }

    public void readCharsNO(final long index, final char[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, CHAR_NO, index, dst, off, len);
    }


    public char readCharBE() {
        return this.segment.get(CHAR_BE, this.advanceRead(2L));
    }

    public char readCharBE(final long index) {
        return this.segment.get(CHAR_BE, index);
    }

    public void readCharsBE(final char[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, CHAR_BE, this.advanceRead((long)len << 1), dst, off, len);
    }

    public void readCharsBE(final long index, final char[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, CHAR_BE, index, dst, off, len);
    }


    public char readCharLE() {
        return this.segment.get(CHAR_LE, this.advanceRead(2L));
    }

    public char readCharLE(final long index) {
        return this.segment.get(CHAR_LE, index);
    }

    public void readCharsLE(final char[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, CHAR_LE, this.advanceRead((long)len << 1), dst, off, len);
    }

    public void readCharsLE(final long index, final char[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, CHAR_LE, index, dst, off, len);
    }



    public int readSignedMediumBE() {
        final MemorySegment segment = this.segment;
        final long index = this.advanceRead(3L);

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return ((int)segment.get(BYTE_NO, index) << 16) | (int)segment.get(CHAR_BE, index + 1L);
    }

    public int readSignedMediumBE(final long index) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return ((int)segment.get(BYTE_NO, index) << 16) | (int)segment.get(CHAR_BE, index + 1L);
    }

    public void readSignedMediumsBE(final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;
        long index = this.advanceRead((long)len * 3L);

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = ((int)segment.get(BYTE_NO, index) << 16) | (int)segment.get(CHAR_BE, index + 1L);
        }
    }

    public void readSignedMediumsBE(long index, final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = ((int)segment.get(BYTE_NO, index) << 16) | (int)segment.get(CHAR_BE, index + 1L);
        }
    }



    public int readUnsignedMediumBE() {
        final MemorySegment segment = this.segment;
        final long index = this.advanceRead(3L);

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return (((int)segment.get(BYTE_NO, index) & 255) << 16) | (int)segment.get(CHAR_BE, index + 1L);
    }

    public int readUnsignedMediumBE(final long index) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return (((int)segment.get(BYTE_NO, index) & 255) << 16) | (int)segment.get(CHAR_BE, index + 1L);
    }

    public void readUnsignedMediumsBE(final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;
        long index = this.advanceRead((long)len * 3L);

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = (((int)segment.get(BYTE_NO, index) & 255) << 16) | (int)segment.get(CHAR_BE, index + 1L);
        }
    }

    public void readUnsignedMediumsBE(long index, final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = (((int)segment.get(BYTE_NO, index) & 255) << 16) | (int)segment.get(CHAR_BE, index + 1L);
        }
    }



    public int readSignedMediumLE() {
        final MemorySegment segment = this.segment;
        final long index = this.advanceRead(3L);

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return (int)segment.get(CHAR_LE, index) | ((int)segment.get(BYTE_NO, index + 2L) << 16);
    }

    public int readSignedMediumLE(final long index) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return (int)segment.get(CHAR_LE, index) | ((int)segment.get(BYTE_NO, index + 2L) << 16);
    }

    public void readSignedMediumsLE(final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;
        long index = this.advanceRead((long)len * 3L);

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = (int)segment.get(CHAR_LE, index) | ((int)segment.get(BYTE_NO, index + 2L) << 16);
        }
    }

    public void readSignedMediumsLE(long index, final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = (int)segment.get(CHAR_LE, index) | ((int)segment.get(BYTE_NO, index + 2L) << 16);
        }
    }



    public int readUnsignedMediumLE() {
        final MemorySegment segment = this.segment;
        final long index = this.advanceRead(3L);

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return (int)segment.get(CHAR_LE, index) | (((int)segment.get(BYTE_NO, index + 2L) & 255) << 16);
    }

    public int readUnsignedMediumLE(final long index) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        return (int)segment.get(CHAR_LE, index) | (((int)segment.get(BYTE_NO, index + 2L) & 255) << 16);
    }

    public void readUnsignedMediumsLE(final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;
        long index = this.advanceRead((long)len * 3L);

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = (int)segment.get(CHAR_LE, index) | (((int)segment.get(BYTE_NO, index + 2L) & 255) << 16);
        }
    }

    public void readUnsignedMediumsLE(long index, final int[] dst, final int off, final int len) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(off, len, dst.length);
        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        for (int i = 0; i < len; ++i, index += 3L) {
            dst[i + off] = (int)segment.get(CHAR_LE, index) | (((int)segment.get(BYTE_NO, index + 2L) & 255) << 16);
        }
    }



    public int readIntNO() {
        return this.segment.get(INT_NO, this.advanceRead(4L));
    }

    public int readIntNO(final long index) {
        return this.segment.get(INT_NO, index);
    }

    public void readIntsNO(final int[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, INT_NO, this.advanceRead((long)len << 2), dst, off, len);
    }

    public void readIntsNO(final long index, final int[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, INT_NO, index, dst, off, len);
    }


    public int readIntBE() {
        return this.segment.get(INT_BE, this.advanceRead(4L));
    }

    public int readIntBE(final long index) {
        return this.segment.get(INT_BE, index);
    }

    public void readIntsBE(final int[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, INT_BE, this.advanceRead((long)len << 2), dst, off, len);
    }

    public void readIntsBE(final long index, final int[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, INT_BE, index, dst, off, len);
    }


    public int readIntLE() {
        return this.segment.get(INT_LE, this.advanceRead(4L));
    }

    public int readIntLE(final long index) {
        return this.segment.get(INT_LE, index);
    }

    public void readIntsLE(final int[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, INT_LE, this.advanceRead((long)len << 2), dst, off, len);
    }

    public void readIntsLE(final long index, final int[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, INT_LE, index, dst, off, len);
    }



    public long readLongNO() {
        return this.segment.get(LONG_NO, this.advanceRead(8L));
    }

    public long readLongNO(final long index) {
        return this.segment.get(LONG_NO, index);
    }

    public void readLongsNO(final long[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, LONG_NO, this.advanceRead((long)len << 3), dst, off, len);
    }

    public void readLongsNO(final long index, final long[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, LONG_NO, index, dst, off, len);
    }


    public long readLongBE() {
        return this.segment.get(LONG_BE, this.advanceRead(8L));
    }

    public long readLongBE(final long index) {
        return this.segment.get(LONG_BE, index);
    }

    public void readLongsBE(final long[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, LONG_BE, this.advanceRead((long)len << 3), dst, off, len);
    }

    public void readLongsBE(final long index, final long[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, LONG_BE, index, dst, off, len);
    }


    public long readLongLE() {
        return this.segment.get(LONG_LE, this.advanceRead(8L));
    }

    public long readLongLE(final long index) {
        return this.segment.get(LONG_LE, index);
    }

    public void readLongsLE(final long[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, LONG_LE, this.advanceRead((long)len << 3), dst, off, len);
    }

    public void readLongsLE(final long index, final long[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, LONG_LE, index, dst, off, len);
    }



    public float readFloatNO() {
        return this.segment.get(FLOAT_NO, this.advanceRead(4L));
    }

    public float readFloatNO(final long index) {
        return this.segment.get(FLOAT_NO, index);
    }

    public void readFloatsNO(final float[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, FLOAT_NO, this.advanceRead((long)len << 2), dst, off, len);
    }

    public void readFloatsNO(final long index, final float[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, FLOAT_NO, index, dst, off, len);
    }


    public float readFloatBE() {
        return this.segment.get(FLOAT_BE, this.advanceRead(4L));
    }

    public float readFloatBE(final long index) {
        return this.segment.get(FLOAT_BE, index);
    }

    public void readFloatsBE(final float[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, FLOAT_BE, this.advanceRead((long)len << 2), dst, off, len);
    }

    public void readFloatsBE(final long index, final float[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, FLOAT_BE, index, dst, off, len);
    }


    public float readFloatLE() {
        return this.segment.get(FLOAT_LE, this.advanceRead(4L));
    }

    public float readFloatLE(final long index) {
        return this.segment.get(FLOAT_LE, index);
    }

    public void readFloatsLE(final float[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, FLOAT_LE, this.advanceRead((long)len << 2), dst, off, len);
    }

    public void readFloatsLE(final long index, final float[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, FLOAT_LE, index, dst, off, len);
    }



    public double readDoubleNO() {
        return this.segment.get(DOUBLE_NO, this.advanceRead(8L));
    }

    public double readDoubleNO(final long index) {
        return this.segment.get(DOUBLE_NO, index);
    }

    public void readDoublesNO(final double[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, DOUBLE_NO, this.advanceRead((long)len << 3), dst, off, len);
    }

    public void readDoublesNO(final long index, final double[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, DOUBLE_NO, index, dst, off, len);
    }


    public double readDoubleBE() {
        return this.segment.get(DOUBLE_BE, this.advanceRead(8L));
    }

    public double readDoubleBE(final long index) {
        return this.segment.get(DOUBLE_BE, index);
    }

    public void readDoublesBE(final double[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, DOUBLE_BE, this.advanceRead((long)len << 3), dst, off, len);
    }

    public void readDoublesBE(final long index, final double[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, DOUBLE_BE, index, dst, off, len);
    }


    public double readDoubleLE() {
        return this.segment.get(DOUBLE_LE, this.advanceRead(8L));
    }

    public double readDoubleLE(final long index) {
        return this.segment.get(DOUBLE_LE, index);
    }

    public void readDoublesLE(final double[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, DOUBLE_LE, this.advanceRead((long)len << 3), dst, off, len);
    }

    public void readDoublesLE(final long index, final double[] dst, final int off, final int len) {
        MemorySegment.copy(this.segment, DOUBLE_LE, index, dst, off, len);
    }

    // other read

    public static int decodeSignedVarInt(final int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    public int readSignedVarInt() {
        return decodeSignedVarInt(this.readUnsignedVarInt());
    }

    public int readSignedVarInt(final long index) {
        return decodeSignedVarInt(this.readUnsignedVarInt(index));
    }

    private int readUnsignedVarIntFallback() {
        int ret = 0;

        for (int shift = 0; shift < Integer.SIZE; shift += 7) {
            final byte b = this.readByte();
            ret |= ((b & 127) << shift);
            if (((int)b & 128) == 0) {
                return ret;
            }
        }

        this.unread(MAX_VAR_INT_BYTES);
        throw new IllegalArgumentException(); // TODO illegal input
    }

    private static final long VARINT_CONT_BITS =
                    ((1L << 7) << (0*8)) |
                    ((1L << 7) << (1*8)) |
                    ((1L << 7) << (2*8)) |
                    ((1L << 7) << (3*8)) |
                    ((1L << 7) << (4*8)); // we need to read the 5th cont bit to validate

    public int readUnsignedVarInt() {
        final MemorySegment segment = this.segment;
        final long readerIdx = this.readerIndex;
        if (segment.byteSize() - readerIdx < 8L) {
            return this.readUnsignedVarIntFallback();
        }
        // note: must be little endian read
        final long value = segment.get(LONG_LE, readerIdx);

        final long contBits = (value & VARINT_CONT_BITS) ^ VARINT_CONT_BITS;
        if (contBits == 0L) {
            throw new IllegalArgumentException(); // TODO illegal input
        }
        // what we are looking for in the original long value is the first cont bit set to 0.
        // when we mask the cont bits and then XOR, we are then looking for the first set 1.

        // possible values from Long.numberOfTrailingZeros(contBits) mapped to byte result:
        // 7 -> 1
        // 15 -> 2
        // 23 -> 3
        // 31 -> 4
        // 39 -> 5
        // note: this is just (value + 1) / 8

        final int bytes = (Long.numberOfTrailingZeros(contBits) + 1) >>> 3;
        // note: correctly blows up if we try to advance past writer
        // note: only ask for 1 byte more than readable to maintain parity with readUnsignedVarIntFallback
        this.advanceRead(Math.min(bytes, this.writerIndex - readerIdx + 1L));

        // mask out values we aren't reading
        final long ret = value & ((1L << (bytes << 3)) - 1L);

        return (int)(
                (ret & 127L) |
                        ((ret & (127L << 8)) >> 1) |
                        ((ret & (127L << 16)) >> 2) |
                        ((ret & (127L << 24)) >> 3) |
                        ((ret & (127L << 32)) >> 4)
        );
    }

    private int readUnsignedVarIntFallback(long index) {
        int ret = 0;

        for (int shift = 0; shift < Integer.SIZE; shift += 7) {
            final byte b = this.readByte(index++);
            ret |= (((int)b & 127) << shift);
            if (((int)b & 128) == 0) {
                return ret;
            }
        }

        throw new IllegalArgumentException(); // TODO illegal input
    }

    public int readUnsignedVarInt(final long index) {
        final MemorySegment segment = this.segment;
        if (segment.byteSize() - index < 8L) {
            return this.readUnsignedVarIntFallback(index);
        }
        // note: must be little endian read
        final long value = segment.get(LONG_LE, index);

        final long contBits = (value & VARINT_CONT_BITS) ^ VARINT_CONT_BITS;
        if (contBits == 0L) {
            throw new IllegalArgumentException(); // TODO illegal input
        }
        // what we are looking for in the original long value is the first cont bit set to 0.
        // when we mask the cont bits and then XOR, we are then looking for the first set 1.

        // possible values from Long.numberOfTrailingZeros(contBits) mapped to byte result:
        // 7 -> 1
        // 15 -> 2
        // 23 -> 3
        // 31 -> 4
        // 39 -> 5
        // note: this is just (value + 1) / 8

        final int bytes = (Long.numberOfTrailingZeros(contBits) + 1) >>> 3;

        // mask out values we aren't reading
        final long ret = value & ((1L << (bytes << 3)) - 1L);

        return (int)(
                (ret & 127L) |
                        ((ret & (127L << 8)) >>> 1) |
                        ((ret & (127L << 16)) >>> 2) |
                        ((ret & (127L << 24)) >>> 3) |
                        ((ret & (127L << 32)) >>> 4)
        );
    }

    public static long decodeSignedVarLong(final long value) {
        return (value >>> 1) ^ -(value & 1L);
    }

    public long readSignedVarLong() {
        return decodeSignedVarLong(this.readUnsignedVarLong());
    }

    public long readSignedVarLong(final long index) {
        return decodeSignedVarLong(this.readUnsignedVarLong(index));
    }

    public long readUnsignedVarLong() {
        long ret = 0;

        for (int shift = 0; shift < Long.SIZE; shift += 7) {
            final byte b = this.readByte();
            ret |= (((long)b & 127L) << shift);
            if (((long)b & 128L) == 0L) {
                return ret;
            }
        }

        this.unread(MAX_VAR_LONG_BYTES);
        throw new IllegalArgumentException(); // TODO illegal input
    }

    public long readUnsignedVarLong(long index) {
        long ret = 0;

        for (int shift = 0; shift < Long.SIZE; shift += 7) {
            final byte b = this.readByte(index++);
            ret |= (((long)b & 127L) << shift);
            if (((long)b & 128L) == 0L) {
                return ret;
            }
        }

        throw new IllegalArgumentException(); // TODO illegal input
    }

    public long readIntoChannel(final WritableByteChannel channel) throws IOException {
        final ByteBuffer asBuffer = this.getBufferAsRead();

        final long r = (long)channel.write(asBuffer);
        this.readerIndex += Math.max(0L, r);
        return r;
    }

    public void readIntoChannel(final WritableByteChannel channel, final long nBytes) throws IOException {
        final long read = this.readerIndex;
        final long write = this.writerIndex;
        if ((write - read) < nBytes) {
            // not enough readable bytes
            throw ioobe(read, write, nBytes);
        }

        final ByteBuffer asBuffer = this.getMemoryAsBuffer();
        asBuffer.clear();
        asBuffer.position(castIndexToInt(read));
        asBuffer.limit(castIndexToInt(read + nBytes));

        long bytesRead = 0L;
        while (bytesRead < nBytes) {
            final long r = Math.max((long)channel.write(asBuffer), 0L);
            bytesRead += r;
            this.readerIndex += r;

            if (r == 0L) {
                // may happen in the case of non-blocking sockets, but we should throw instead of spinning
                throw new EOFException();
            }
        }
    }

    public long readIntoFilePos(final FileChannel channel, final long filePos) throws IOException {
        final ByteBuffer asBuffer = this.getBufferAsRead();

        final long r = (long)channel.write(asBuffer, filePos);
        this.readerIndex += Math.max(0L, r);
        return r;
    }

    public void readIntoFilePos(final FileChannel channel, final long filePos, final long nBytes) throws IOException {
        final long read = this.readerIndex;
        final long write = this.writerIndex;
        if ((write - read) < nBytes) {
            // not enough readable bytes
            throw ioobe(read, write, nBytes);
        }

        final ByteBuffer asBuffer = this.getMemoryAsBuffer();
        asBuffer.clear();
        asBuffer.position(castIndexToInt(read));
        asBuffer.limit(castIndexToInt(read + nBytes));

        long bytesRead = 0L;
        while (bytesRead < nBytes) {
            final long r = Math.max((long)channel.write(asBuffer, filePos + bytesRead), 0L);
            bytesRead += r;
            this.readerIndex += r;

            if (r == 0L) {
                throw new EOFException();
            }
        }
    }

    public void skipRead(final long nBytes) {
        this.advanceRead(nBytes);
    }

    public long readIntoBuffer(final Buffer dst) {
        final long dstWritable = dst.getWritableBytes();
        final long srcReadable = this.getReadableBytes();

        final long ret = Math.min(dstWritable, srcReadable);

        dst.ensureImmediatelyWritable(ret);
        MemorySegment.copy(this.segment, this.readerIndex, dst.segment, dst.writerIndex, ret);

        dst.writerIndex += ret;
        this.readerIndex += ret;

        return ret;
    }

    public void readIntoBuffer(final Buffer dst, final long nBytes) {
        final long dstWritable = dst.getWritableBytes();
        final long srcReadable = this.getReadableBytes();

        if (dstWritable < nBytes || srcReadable < nBytes) {
            throw new IndexOutOfBoundsException("Writable: " + dstWritable + ", readable: " + srcReadable + ", nBytes: " + nBytes);
        }

        dst.ensureImmediatelyWritable(nBytes);
        MemorySegment.copy(this.segment, this.readerIndex, dst.segment, dst.writerIndex, nBytes);

        dst.writerIndex += nBytes;
        this.readerIndex += nBytes;
    }

    public int readIntoByteBuffer(final ByteBuffer dst) {
        final int dstWritable = dst.remaining();
        final long srcReadable = this.getReadableBytes();

        final int ret = (int)Math.min((long)dstWritable, srcReadable);

        final ByteBuffer asBuffer = this.getMemoryAsBuffer();
        asBuffer.clear();
        asBuffer.position(castIndexToInt(this.readerIndex));
        asBuffer.limit(asBuffer.position() + ret);

        dst.put(asBuffer);

        this.readerIndex += ret;

        return ret;
    }

    public void readIntoByteBuffer(final ByteBuffer dst, final int nBytes) {
        final int dstWritable = dst.remaining();
        final long srcReadable = this.getReadableBytes();

        if (dstWritable < nBytes || srcReadable < nBytes) {
            throw new IndexOutOfBoundsException("Writable: " + dstWritable + ", readable: " + srcReadable + ", nBytes: " + nBytes);
        }

        final ByteBuffer asBuffer = this.getMemoryAsBuffer();
        asBuffer.clear();
        asBuffer.position(castIndexToInt(this.readerIndex));
        asBuffer.limit(asBuffer.position() + nBytes);

        dst.put(asBuffer);

        this.readerIndex += (long)nBytes;
    }

    public long readIntoSegment(final MemorySegment dst, final long dstOff) {
        final long dstWritable = dst.byteSize() - dstOff;
        final long srcReadable = this.getReadableBytes();

        if (dstWritable < 0L) {
            throw new IndexOutOfBoundsException("Offset: " + dstOff + ", size: " + dst.byteSize());
        }

        final long ret = Math.min(dstWritable, srcReadable);

        MemorySegment.copy(this.segment, this.readerIndex, dst, dstOff, ret);

        this.readerIndex += ret;
        return ret;
    }

    public void readIntoSegment(final MemorySegment dst, final long dstOff, final long nBytes) {
        Objects.checkFromIndexSize(dstOff, nBytes, dst.byteSize());
        MemorySegment.copy(this.segment, this.advanceRead(nBytes), dst, dstOff, nBytes);
    }

    // write


    public void writeByte(final byte val) {
        final long writeIndex = this.advanceWrite(1L);
        this.segment.set(BYTE_NO, writeIndex, val);
    }

    public void writeByte(final long index, final byte val) {
        this.segment.set(BYTE_NO, index, val);
    }

    public void writeBytes(final byte[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len);
        MemorySegment.copy(src, off, this.segment, BYTE_NO, writeIndex, len);
    }

    public void writeBytes(final long index, final byte[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, BYTE_NO, index, len);
    }




    public void writeShortNO(final short val) {
        final long writeIndex = this.advanceWrite(2L);
        this.segment.set(SHORT_NO, writeIndex, val);
    }

    public void writeShortNO(final long index, final short val) {
        this.segment.set(SHORT_NO, index, val);
    }

    public void writeShortsNO(final short[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 1);
        MemorySegment.copy(src, off, this.segment, SHORT_NO, writeIndex, len);
    }

    public void writeShortsNO(final long index, final short[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, SHORT_NO, index, len);
    }


    public void writeShortBE(final short val) {
        final long writeIndex = this.advanceWrite(2L);
        this.segment.set(SHORT_BE, writeIndex, val);
    }

    public void writeShortBE(final long index, final short val) {
        this.segment.set(SHORT_BE, index, val);
    }

    public void writeShortsBE(final short[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 1);
        MemorySegment.copy(src, off, this.segment, SHORT_BE, writeIndex, len);
    }

    public void writeShortsBE(final long index, final short[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, SHORT_BE, index, len);
    }


    public void writeShortLE(final short val) {
        final long writeIndex = this.advanceWrite(2L);
        this.segment.set(SHORT_LE, writeIndex, val);
    }

    public void writeShortLE(final long index, final short val) {
        this.segment.set(SHORT_LE, index, val);
    }

    public void writeShortsLE(final short[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 1);
        MemorySegment.copy(src, off, this.segment, SHORT_LE, writeIndex, len);
    }

    public void writeShortsLE(final long index, final short[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, SHORT_LE, index, len);
    }



    public void writeCharNO(final char val) {
        final long writeIndex = this.advanceWrite(2L);
        this.segment.set(CHAR_NO, writeIndex, val);
    }

    public void writeCharNO(final long index, final char val) {
        this.segment.set(CHAR_NO, index, val);
    }

    public void writeCharsNO(final char[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 1);
        MemorySegment.copy(src, off, this.segment, CHAR_NO, writeIndex, len);
    }

    public void writeCharsNO(final long index, final char[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, CHAR_NO, index, len);
    }


    public void writeCharBE(final char val) {
        final long writeIndex = this.advanceWrite(2L);
        this.segment.set(CHAR_BE, writeIndex, val);
    }

    public void writeCharBE(final long index, final char val) {
        this.segment.set(CHAR_BE, index, val);
    }

    public void writeCharsBE(final char[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 1);
        MemorySegment.copy(src, off, this.segment, CHAR_BE, writeIndex, len);
    }

    public void writeCharsBE(final long index, final char[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, CHAR_BE, index, len);
    }


    public void writeCharLE(final char val) {
        final long writeIndex = this.advanceWrite(2L);
        this.segment.set(CHAR_LE, writeIndex, val);
    }

    public void writeCharLE(final long index, final char val) {
        this.segment.set(CHAR_LE, index, val);
    }

    public void writeCharsLE(final char[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 1);
        MemorySegment.copy(src, off, this.segment, CHAR_LE, writeIndex, len);
    }

    public void writeCharsLE(final long index, final char[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, CHAR_LE, index, len);
    }



    public void writeMediumBE(final int medium) {
        final long writeIndex = this.advanceWrite(3L);
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(writeIndex, 3L, segment.byteSize());
        segment.set(BYTE_NO, writeIndex, (byte)(medium >>> 16));
        segment.set(CHAR_BE, writeIndex + 1L, (char)medium);
    }

    public void writeMediumBE(final long index, final int medium) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        segment.set(BYTE_NO, index, (byte)(medium >>> 16));
        segment.set(CHAR_BE, index + 1L, (char)medium);
    }

    public void writeMediumsBE(final int[] src, final int off, final int len) {
        long writeIndex = this.advanceWrite((long)len * 3L);
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(writeIndex, (long)len * 3L, segment.byteSize());
        Objects.checkFromIndexSize(off, len, src.length);
        for (int i = 0; i < len; ++i, writeIndex += 3L) {
            final int write = src[i + off];
            segment.set(BYTE_NO, writeIndex, (byte)(write >>> 16));
            segment.set(CHAR_BE, writeIndex + 1L, (char)write);
        }
    }

    public void writeMediumsBE(long index, final int[] src, final int off, final int len) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        Objects.checkFromIndexSize(off, len, src.length);
        for (int i = 0; i < len; ++i, index += 3L) {
            final int write = src[i + off];
            segment.set(BYTE_NO, index, (byte)(write >>> 16));
            segment.set(CHAR_BE, index + 1L, (char)write);
        }
    }



    public void writeMediumLE(final int medium) {
        final long writeIndex = this.advanceWrite(3L);
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(writeIndex, 3L, segment.byteSize());
        segment.set(CHAR_LE, writeIndex, (char)medium);
        segment.set(BYTE_NO, writeIndex + 2L, (byte)(medium >>> 16));
    }

    public void writeMediumLE(final long index, final int medium) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, 3L, segment.byteSize());
        segment.set(CHAR_LE, index, (char)medium);
        segment.set(BYTE_NO, index + 2L, (byte)(medium >>> 16));
    }

    public void writeMediumsLE(final int[] src, final int off, final int len) {
        long writeIndex = this.advanceWrite((long)len * 3L);
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(writeIndex, (long)len * 3L, segment.byteSize());
        Objects.checkFromIndexSize(off, len, src.length);
        for (int i = 0; i < len; ++i, writeIndex += 3L) {
            final int write = src[i + off];
            segment.set(CHAR_LE, writeIndex, (char)write);
            segment.set(BYTE_NO, writeIndex + 2L, (byte)(write >>> 16));
        }
    }

    public void writeMediumsLE(long index, final int[] src, final int off, final int len) {
        final MemorySegment segment = this.segment;

        Objects.checkFromIndexSize(index, (long)len * 3L, segment.byteSize());
        Objects.checkFromIndexSize(off, len, src.length);
        for (int i = 0; i < len; ++i, index += 3L) {
            final int write = src[i + off];
            segment.set(CHAR_LE, index, (char)write);
            segment.set(BYTE_NO, index + 2L, (byte)(write >>> 16));
        }
    }



    public void writeIntNO(final int val) {
        final long writeIndex = this.advanceWrite(4L);
        this.segment.set(INT_NO, writeIndex, val);
    }

    public void writeIntNO(final long index, final int val) {
        this.segment.set(INT_NO, index, val);
    }

    public void writeIntsNO(final int[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 2);
        MemorySegment.copy(src, off, this.segment, INT_NO, writeIndex, len);
    }

    public void writeIntsNO(final long index, final int[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, INT_NO, index, len);
    }


    public void writeIntBE(final int val) {
        final long writeIndex = this.advanceWrite(4L);
        this.segment.set(INT_BE, writeIndex, val);
    }

    public void writeIntBE(final long index, final int val) {
        this.segment.set(INT_BE, index, val);
    }

    public void writeIntsBE(final int[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 2);
        MemorySegment.copy(src, off, this.segment, INT_BE, writeIndex, len);
    }

    public void writeIntsBE(final long index, final int[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, INT_BE, index, len);
    }


    public void writeIntLE(final int val) {
        final long writeIndex = this.advanceWrite(4L);
        this.segment.set(INT_LE, writeIndex, val);
    }

    public void writeIntLE(final long index, final int val) {
        this.segment.set(INT_LE, index, val);
    }

    public void writeIntsLE(final int[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 2);
        MemorySegment.copy(src, off, this.segment, INT_LE, writeIndex, len);
    }

    public void writeIntsLE(final long index, final int[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, INT_LE, index, len);
    }



    public void writeLongNO(final long val) {
        final long writeIndex = this.advanceWrite(8L);
        this.segment.set(LONG_NO, writeIndex, val);
    }

    public void writeLongNO(final long index, final long val) {
        this.segment.set(LONG_NO, index, val);
    }

    public void writeLongsNO(final long[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 3);
        MemorySegment.copy(src, off, this.segment, LONG_NO, writeIndex, len);
    }

    public void writeLongsNO(final long index, final long[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, LONG_NO, index, len);
    }


    public void writeLongBE(final long val) {
        final long writeIndex = this.advanceWrite(8L);
        this.segment.set(LONG_BE, writeIndex, val);
    }

    public void writeLongBE(final long index, final long val) {
        this.segment.set(LONG_BE, index, val);
    }

    public void writeLongsBE(final long[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 3);
        MemorySegment.copy(src, off, this.segment, LONG_BE, writeIndex, len);
    }

    public void writeLongsBE(final long index, final long[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, LONG_BE, index, len);
    }


    public void writeLongLE(final long val) {
        final long writeIndex = this.advanceWrite(8L);
        this.segment.set(LONG_LE, writeIndex, val);
    }

    public void writeLongLE(final long index, final long val) {
        this.segment.set(LONG_LE, index, val);
    }

    public void writeLongsLE(final long[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 3);
        MemorySegment.copy(src, off, this.segment, LONG_LE, writeIndex, len);
    }

    public void writeLongsLE(final long index, final long[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, LONG_LE, index, len);
    }



    public void writeFloatNO(final float val) {
        final long writeIndex = this.advanceWrite(4L);
        this.segment.set(FLOAT_NO, writeIndex, val);
    }

    public void writeFloatNO(final long index, final float val) {
        this.segment.set(FLOAT_NO, index, val);
    }

    public void writeFloatsNO(final float[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 2);
        MemorySegment.copy(src, off, this.segment, FLOAT_NO, writeIndex, len);
    }

    public void writeFloatsNO(final long index, final float[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, FLOAT_NO, index, len);
    }


    public void writeFloatBE(final float val) {
        final long writeIndex = this.advanceWrite(4L);
        this.segment.set(FLOAT_BE, writeIndex, val);
    }

    public void writeFloatBE(final long index, final float val) {
        this.segment.set(FLOAT_BE, index, val);
    }

    public void writeFloatsBE(final float[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 2);
        MemorySegment.copy(src, off, this.segment, FLOAT_BE, writeIndex, len);
    }

    public void writeFloatsBE(final long index, final float[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, FLOAT_BE, index, len);
    }


    public void writeFloatLE(final float val) {
        final long writeIndex = this.advanceWrite(4L);
        this.segment.set(FLOAT_LE, writeIndex, val);
    }

    public void writeFloatLE(final long index, final float val) {
        this.segment.set(FLOAT_LE, index, val);
    }

    public void writeFloatsLE(final float[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 2);
        MemorySegment.copy(src, off, this.segment, FLOAT_LE, writeIndex, len);
    }

    public void writeFloatsLE(final long index, final float[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, FLOAT_LE, index, len);
    }



    public void writeDoubleNO(final double val) {
        final long writeIndex = this.advanceWrite(8L);
        this.segment.set(DOUBLE_NO, writeIndex, val);
    }

    public void writeDoubleNO(final long index, final double val) {
        this.segment.set(DOUBLE_NO, index, val);
    }

    public void writeDoublesNO(final double[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 3);
        MemorySegment.copy(src, off, this.segment, DOUBLE_NO, writeIndex, len);
    }

    public void writeDoublesNO(final long index, final double[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, DOUBLE_NO, index, len);
    }


    public void writeDoubleBE(final double val) {
        final long writeIndex = this.advanceWrite(8L);
        this.segment.set(DOUBLE_BE, writeIndex, val);
    }

    public void writeDoubleBE(final long index, final double val) {
        this.segment.set(DOUBLE_BE, index, val);
    }

    public void writeDoublesBE(final double[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 3);
        MemorySegment.copy(src, off, this.segment, DOUBLE_BE, writeIndex, len);
    }

    public void writeDoublesBE(final long index, final double[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, DOUBLE_BE, index, len);
    }


    public void writeDoubleLE(final double val) {
        final long writeIndex = this.advanceWrite(8L);
        this.segment.set(DOUBLE_LE, writeIndex, val);
    }

    public void writeDoubleLE(final long index, final double val) {
        this.segment.set(DOUBLE_LE, index, val);
    }

    public void writeDoublesLE(final double[] src, final int off, final int len) {
        final long writeIndex = this.advanceWrite((long)len << 3);
        MemorySegment.copy(src, off, this.segment, DOUBLE_LE, writeIndex, len);
    }

    public void writeDoublesLE(final long index, final double[] src, final int off, final int len) {
        MemorySegment.copy(src, off, this.segment, DOUBLE_LE, index, len);
    }

    // other write

    public static int encodeSignedVarInt(final int value) {
        return (value << 1) ^ (value >> (Integer.SIZE - 1));
    }

    public long writeSignedVarInt(final int value) {
        return this.writeUnsignedVarInt(encodeSignedVarInt(value));
    }

    public long writeSignedVarInt(final long index, final int value) {
        return this.writeUnsignedVarInt(index, encodeSignedVarInt(value));
    }

    private long writeUnsignedVarIntFallback(int value) {
        long ret = 0;
        while ((value & 127) != value) {
            this.writeByte((byte)(value | 128));
            value >>>= 7;
            ++ret;
        }
        this.writeByte((byte)value);
        ++ret;
        return ret;
    }

    private static final int DIV_7_BITS = 8;
    private static final int DIV_7_MAGIC = (int) IntegerUtil.getUnsignedDivisorMagic(7L, DIV_7_BITS);

    /*
     * We can achieve a fast unsigned VarInt write by understanding that the maximum encoded bytes for an
     * encoded VarInt is 5. We can create an 8 byte value representing the maximally encoded value from the
     * input (i.e. assuming all 5 bytes are needed), count the number of bytes actually needed using numberOfLeadingZeros,
     * set the continutation bits accordingly, and then write the VarInt using a single little endian 8 byte read and write.
     */

    public long writeUnsignedVarInt(final int value) {
        final MemorySegment segment = this.segment;
        final long writerIdx = this.writerIndex;
        if ((segment.byteSize() - writerIdx) < 8L) {
            return this.writeUnsignedVarIntFallback(value);
        }
        // value | 1 ensures 0 <= numberOfLeadingZeros <= SIZE - 1
        final long bytes = (long)((((Integer.SIZE - 1) - Integer.numberOfLeadingZeros(value | 1)) * DIV_7_MAGIC) >> DIV_7_BITS);

        final long write = VARINT_CONT_BITS & ((1L << (bytes << 3)) - 1L) |
                (
                        (((long)value & (127L << 0))) |
                        (((long)value & (127L << 7)) << 1) |
                        (((long)value & (127L << 14)) << 2) |
                        (((long)value & (127L << 21)) << 3) |
                        (((long)value & ((127L << 28) & 0xFFFFFFFFL)) << 4)
                );
        final long written = bytes + 1L;
        final long readMask = -(1L << (written << 3));

        this.writerIndex += written;

        segment.set(LONG_LE, writerIdx, write | (readMask & segment.get(LONG_LE, writerIdx)));
        return written;
    }

    private long writeUnsignedVarIntFallback(long index, int value) {
        long ret = 0L;
        while ((value & 127) != value) {
            this.writeByte(index++, (byte)(value | 128));
            value >>>= 7;
            ++ret;
        }
        this.writeByte(index, (byte)value);
        ++ret;
        return ret;
    }

    public long writeUnsignedVarInt(final long index, final int value) {
        final MemorySegment segment = this.segment;
        if ((segment.byteSize() - index) < 8L) {
            return this.writeUnsignedVarIntFallback(index, value);
        }
        // value | 1 ensures 0 <= numberOfLeadingZeros <= SIZE - 1
        final long bytes = (long)((((Integer.SIZE - 1) - Integer.numberOfLeadingZeros(value | 1)) * DIV_7_MAGIC) >> DIV_7_BITS);

        final long write = VARINT_CONT_BITS & ((1L << (bytes << 3)) - 1L) |
                (
                        (((long)value & (127L << 0))) |
                        (((long)value & (127L << 7)) << 1) |
                        (((long)value & (127L << 14)) << 2) |
                        (((long)value & (127L << 21)) << 3) |
                        (((long)value & ((127L << 28) & 0xFFFFFFFFL)) << 4)
                );
        final long written = bytes + 1L;
        final long readMask = -(1L << (written << 3));

        segment.set(LONG_LE, index, write | (readMask & segment.get(LONG_LE, index)));
        return written;
    }

    public static long encodeSignedVarLong(final long value) {
        return (value << 1) ^ (value >> (Long.SIZE - 1));
    }

    public long writeSignedVarLong(final long value) {
        return this.writeUnsignedVarLong(encodeSignedVarLong(value));
    }

    public long writeSignedVarLong(final long index, final long value) {
        return this.writeUnsignedVarLong(index, encodeSignedVarLong(value));
    }

    public long writeUnsignedVarLong(long value) {
        long ret = 0L;
        while ((value & 127L) != value) {
            this.writeByte((byte)(value | 128));
            value >>>= 7;
            ++ret;
        }
        this.writeByte((byte)value);
        ++ret;
        return ret;
    }

    public long writeUnsignedVarLong(long index, long value) {
        long ret = 0L;
        while ((value & 127L) != value) {
            this.writeByte(index++, (byte)(value | 128L));
            value >>>= 7;
            ++ret;
        }
        this.writeByte(index, (byte)value);
        ++ret;
        return ret;
    }

    public long writeFromChannel(final ReadableByteChannel channel) throws IOException {
        this.ensureImmediatelyWritable(1L);

        final ByteBuffer asBuffer = this.getBufferAsWriter();

        final long r = (long)channel.read(asBuffer);
        this.writerIndex += Math.max(0L, r);
        return r;
    }

    public void writeFromChannel(final ReadableByteChannel channel, final long nBytes) throws IOException {
        this.ensureImmediatelyWritable(nBytes);

        final ByteBuffer asBuffer = this.getBufferAsWriter();
        asBuffer.limit(castIndexToInt(this.writerIndex + nBytes));

        long bytesWritten = 0L;
        while (bytesWritten < nBytes) {
            final long r = Math.max((long)channel.read(asBuffer), 0L); // treat -1 and 0 the same
            bytesWritten += r;
            this.writerIndex += r;

            if (r == 0L) {
                throw new EOFException();
            }
        }
    }

    public long writeFromFilePos(final FileChannel channel, final long filePos) throws IOException {
        this.ensureImmediatelyWritable(1L);

        final ByteBuffer asBuffer = this.getBufferAsWriter();

        final long r = (long)channel.read(asBuffer, filePos);
        this.writerIndex += Math.max(0L, r);
        return r;
    }

    public void writeFromFilePos(final FileChannel channel, final long filePos, final long nBytes) throws IOException {
        this.ensureImmediatelyWritable(nBytes);

        final ByteBuffer asBuffer = this.getBufferAsWriter();
        asBuffer.limit(castIndexToInt(this.writerIndex + nBytes));

        long bytesWritten = 0L;
        while (bytesWritten < nBytes) {
            final long r = Math.max((long)channel.read(asBuffer, filePos + bytesWritten), 0L); // treat -1 and 0 the same
            bytesWritten += r;
            this.writerIndex += r;

            if (r == 0L) {
                throw new EOFException();
            }
        }
    }

    public long writeFromBuffer(final Buffer src) {
        return src.readIntoBuffer(this);
    }

    public void writeFromBuffer(final Buffer src, final long nBytes) {
        // add test case (for this) if we no longer invoke src.readIntoBuffer
        src.readIntoBuffer(this, nBytes);
    }

    public int writeFromByteBuffer(final ByteBuffer src) {
        final int toWrite = (int)Math.min((long)src.remaining(), this.getWritableBytes());
        if (toWrite > 0) {
            this.writeFromByteBuffer(src, toWrite);
        }
        return toWrite;
    }

    public void writeFromByteBuffer(final ByteBuffer src, final int nBytes) {
        // add test case (for both) if we no longer invoke this.writeFromSegment
        this.writeFromSegment(MemorySegment.ofBuffer(src), 0L, (long)nBytes);
        src.position(src.position() + nBytes);
    }

    public void writeFromSegment(final MemorySegment src, final long srcOff, final long nBytes) {
        Objects.checkFromIndexSize(srcOff, nBytes, src.byteSize());
        final long writeIndex = this.advanceWrite(nBytes);
        MemorySegment.copy(src, srcOff, this.segment, writeIndex, nBytes);
    }

    public static void copy(final Buffer src, final long srcPos, final Buffer dst, final long dstPos,
                            final long nBytes) {
        MemorySegment.copy(src.segment, srcPos, dst.segment, dstPos, nBytes);
    }
}
