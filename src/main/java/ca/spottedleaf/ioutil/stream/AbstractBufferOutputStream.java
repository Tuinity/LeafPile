package ca.spottedleaf.ioutil.stream;

import ca.spottedleaf.ioutil.buffer.Buffer;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Objects;

public abstract class AbstractBufferOutputStream extends OutputStream implements DataOutput {

    protected final Buffer writeBuffer;

    protected AbstractBufferOutputStream(final Buffer writeBuffer) {
        this.writeBuffer = writeBuffer;
    }

    // returns the number of bytes available to store in the write buffer
    public final long getWritable() {
        return this.writeBuffer.getWritableBytes();
    }

    // returns the number of bytes available to store in the write buffer without allocating
    public final long getImmediatelyWritable() {
        return this.writeBuffer.getImmediatelyWritableBytes();
    }

    // increases the allocated space in the write buffer by the specified number of bytes if possible
    public final void tryEnsureImmediatelyWritable(final long nBytes) {
        if (this.writeBuffer.getImmediatelyWritableBytes() < nBytes) {
            this.writeBuffer.ensureImmediatelyWritable(Math.min(nBytes, this.writeBuffer.getWritableBytes()));
        }
    }

    // rets whether any bytes were written
    // note: once this method returns false, it should never return true again
    protected abstract boolean tryFlushWriteBuffer() throws IOException;

    protected final void ensureWritable(final long nBytes) throws IOException {
        if (!this.tryEnsure(nBytes)) {
            throw new IllegalStateException("Cannot ensure that at least " + nBytes + " are available");
        }
    }

    protected final boolean tryEnsure(final long nBytes) throws IOException {
        if (this.writeBuffer.getWritableBytes() >= nBytes) {
            return true;
        }
        return this.tryFlush(nBytes);
    }

    private boolean tryFlush(final long nBytes) throws IOException {
        if (this.writeBuffer.getMaxCapacity() < nBytes) {
            throw new IllegalArgumentException("Asking for more bytes than can hold!");
        }

        // try to write as long as:
        // 1) we have not emptied write buffer to requested capacity
        // 2) we write at least 1 byte from the buffer
        while (this.writeBuffer.getWritableBytes() < nBytes && this.tryFlushWriteBuffer());

        return this.writeBuffer.getWritableBytes() >= nBytes;
    }

    // OutputStream methods

    @Override
    public final void write(final int b) throws IOException {
        this.writeByte(b);
    }

    @Override
    public final void write(final byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public final void write(final byte[] b, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);

        int bytesWritten = 0;
        while (bytesWritten < len) {
            this.ensureWritable(1L);

            final int toWrite = (int)Math.min((long)(len - bytesWritten), this.writeBuffer.getWritableBytes());
            this.writeBuffer.writeBytes(b, off + bytesWritten, toWrite);
            bytesWritten += toWrite;
        }
    }

    // DataOutput methods

    @Override
    public final void writeBoolean(final boolean v) throws IOException {
        this.writeByte(v ? (byte)1 : (byte)0);
    }

    @Override
    public final void writeByte(final int v) throws IOException {
        this.ensureWritable(1L);
        this.writeBuffer.writeByte((byte)v);
    }

    @Override
    public final void writeShort(final int v) throws IOException {
        this.ensureWritable(2L);
        this.writeBuffer.writeShortBE((short)v);
    }

    @Override
    public final void writeChar(final int v) throws IOException {
        this.ensureWritable(2L);
        this.writeBuffer.writeCharBE((char)v);
    }

    @Override
    public final void writeInt(final int v) throws IOException {
        this.ensureWritable(4L);
        this.writeBuffer.writeIntBE(v);
    }

    @Override
    public final void writeLong(final long v) throws IOException {
        this.ensureWritable(8L);
        this.writeBuffer.writeLongBE(v);
    }

    @Override
    public final void writeFloat(final float v) throws IOException {
        this.ensureWritable(4L);
        this.writeBuffer.writeFloatBE(v);
    }

    @Override
    public final void writeDouble(final double v) throws IOException {
        this.ensureWritable(8L);
        this.writeBuffer.writeDoubleBE(v);
    }

    @Override
    public final void writeBytes(final String s) throws IOException {
        this.writeBytes(s, 0, s.length());
    }

    public final void writeBytes(final String s, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, s.length());

        int bytesWritten = 0;
        while (bytesWritten < len) {
            this.ensureWritable(1L);

            final int toWrite = (int)Math.min((long)(len - bytesWritten), this.writeBuffer.getWritableBytes());

            for (int i = 0; i < toWrite; ++i) {
                this.writeBuffer.writeByte((byte)s.charAt(off + bytesWritten + i));
            }

            bytesWritten += toWrite;
        }
    }

    @Override
    public final void writeChars(final String s) throws IOException {
        this.writeChars(s, 0, s.length());
    }

    public final void writeChars(final String s, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, s.length());

        int charsWritten = 0;
        while (charsWritten < len) {
            this.ensureWritable(2L);

            final int toWrite = (int)Math.min((long)(len - charsWritten), this.writeBuffer.getWritableBytes() >> 1);

            for (int i = 0; i < toWrite; ++i) {
                this.writeBuffer.writeCharBE(s.charAt(off + charsWritten + i));
            }

            charsWritten += toWrite;
        }
    }

    private static final int MAX_UTF_BYTES = 65535;

    @Override
    public final void writeUTF(final String str) throws IOException {
        int off = 0;
        int len = str.length();

        // count string length
        int bytes = len; // at least length

        if (bytes > MAX_UTF_BYTES) {
            throw new UTFDataFormatException("Too many bytes (fast-check): " + bytes);
        }

        for (int i = 0; i < len; ++i) {
            final char c = str.charAt(i);
            if (c == 0 || c >= 128) {
                bytes += (c >= 2048) ? 2 : 1;
            }
        }

        if (bytes > MAX_UTF_BYTES) {
            throw new UTFDataFormatException("Too many bytes (slow-check): " + bytes);
        }

        this.writeShort(bytes);

        // attempt ASCII-only write
        if (bytes == len) {
            this.writeBytes(str);
            return;
        }

        // no ASCII only, so we need to use the modified encoding
        while (len > 0) {
            this.ensureWritable(3L);

            final Buffer buffer = this.writeBuffer;
            final long position = buffer.getWriterIndex();

            // assume worst-case that each character could take the full 3 bytes
            final int maxCopy = (int)Math.min(buffer.getWritableBytes() / 3L, (long)len);

            long bufferIdx = position;
            for (int i = off, max = off + maxCopy; i < max; ++i) {
                final char c = str.charAt(i);
                if (c != 0 && c < 128) {
                    buffer.writeByte(bufferIdx, (byte)c);

                    bufferIdx += 1L;
                } else if (c < 2048) {
                    buffer.writeByte(bufferIdx, (byte)(0xC0 | ((c >> 6) & 0x1F)));
                    buffer.writeByte(bufferIdx + 1, (byte)(0x80 | (c & 0x3F)));

                    bufferIdx += 2L;
                } else {
                    buffer.writeByte(bufferIdx, (byte)(0xE0 | ((c >> 12) & 0x0F)));
                    buffer.writeByte(bufferIdx + 1, (byte)(0x80 | ((c >> 6) & 0x3F)));
                    buffer.writeByte(bufferIdx + 2, (byte)(0x80 | (c & 0x3F)));

                    bufferIdx += 3L;
                }
            }
            buffer.setWriterIndex(bufferIdx);

            off += maxCopy;
            len -= maxCopy;
        }
    }

    // specified by us

    public final void writeShortLE(final short v) throws IOException {
        this.ensureWritable(2L);
        this.writeBuffer.writeShortLE(v);
    }

    public final void writeShortNO(final short v) throws IOException {
        this.ensureWritable(2L);
        this.writeBuffer.writeShortNO(v);
    }

    public final void writeCharLE(final char v) throws IOException {
        this.ensureWritable(2L);
        this.writeBuffer.writeCharLE(v);
    }

    public final void writeCharNO(final char v) throws IOException {
        this.ensureWritable(2L);
        this.writeBuffer.writeCharNO(v);
    }

    public final void writeIntLE(final int v) throws IOException {
        this.ensureWritable(4L);
        this.writeBuffer.writeIntLE(v);
    }

    public final void writeIntNO(final int v) throws IOException {
        this.ensureWritable(4L);
        this.writeBuffer.writeIntNO(v);
    }

    public final void writeFloatLE(final float v) throws IOException {
        this.ensureWritable(4L);
        this.writeBuffer.writeFloatLE(v);
    }

    public final void writeFloatNO(final float v) throws IOException {
        this.ensureWritable(4L);
        this.writeBuffer.writeFloatNO(v);
    }

    public final void writeLongLE(final long v) throws IOException {
        this.ensureWritable(8L);
        this.writeBuffer.writeLongLE(v);
    }

    public final void writeLongNO(final long v) throws IOException {
        this.ensureWritable(8L);
        this.writeBuffer.writeLongNO(v);
    }

    public final void writeDoubleLE(final double v) throws IOException {
        this.ensureWritable(8L);
        this.writeBuffer.writeDoubleLE(v);
    }

    public final void writeDoubleNO(final double v) throws IOException {
        this.ensureWritable(8L);
        this.writeBuffer.writeDoubleNO(v);
    }

    public final long writeSignedVarInt(final int value) throws IOException {
        return this.writeUnsignedVarInt(Buffer.encodeSignedVarInt(value));
    }

    public final long writeUnsignedVarInt(final int value) throws IOException {
        this.tryEnsure(5L);
        return this.writeBuffer.writeUnsignedVarInt(value);
    }

    public final long writeSignedVarLong(final long value) throws IOException {
        return this.writeUnsignedVarLong(Buffer.encodeSignedVarLong(value));
    }

    public final long writeUnsignedVarLong(final long value) throws IOException {
        this.tryEnsure(10L);
        return this.writeBuffer.writeUnsignedVarLong(value);
    }

    public final void writeShorts(final short[] values) throws IOException {
        this.writeShorts(values, 0, values.length);
    }

    public final void writeShorts(final short[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(2L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 1);
            this.writeBuffer.writeShortsBE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeShortsLE(final short[] values) throws IOException {
        this.writeShortsLE(values, 0, values.length);
    }

    public final void writeShortsLE(final short[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(2L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 1);
            this.writeBuffer.writeShortsLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeShortsNO(final short[] values) throws IOException {
        this.writeShortsNO(values, 0, values.length);
    }

    public final void writeShortsNO(final short[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(2L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 1);
            this.writeBuffer.writeShortsNO(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeChars(final char[] values) throws IOException {
        this.writeChars(values, 0, values.length);
    }

    public final void writeChars(final char[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(2L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 1);
            this.writeBuffer.writeCharsBE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeCharsLE(final char[] values) throws IOException {
        this.writeCharsLE(values, 0, values.length);
    }

    public final void writeCharsLE(final char[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(2L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 1);
            this.writeBuffer.writeCharsLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeCharsNO(final char[] values) throws IOException {
        this.writeCharsNO(values, 0, values.length);
    }

    public final void writeCharsNO(final char[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(2L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 1);
            this.writeBuffer.writeCharsNO(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeMedium(final int value) throws IOException {
        this.ensureWritable(3L);
        this.writeBuffer.writeMediumBE(value);
    }

    public final void writeMediumLE(final int value) throws IOException {
        this.ensureWritable(3L);
        this.writeBuffer.writeMediumLE(value);
    }

    public final void writeMediums(final int[] values) throws IOException {
        this.writeMediums(values, 0, values.length);
    }

    public final void writeMediums(final int[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(3L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() / 3L);
            this.writeBuffer.writeMediumsBE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeMediumsLE(final int[] values) throws IOException {
        this.writeMediumsLE(values, 0, values.length);
    }

    public final void writeMediumsLE(final int[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(3L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() / 3L);
            this.writeBuffer.writeMediumsLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeInts(final int[] values) throws IOException {
        this.writeInts(values, 0, values.length);
    }

    public final void writeInts(final int[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(4L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 2);
            this.writeBuffer.writeIntsBE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeIntsLE(final int[] values) throws IOException {
        this.writeIntsLE(values, 0, values.length);
    }

    public final void writeIntsLE(final int[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(4L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 2);
            this.writeBuffer.writeIntsLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeIntsNO(final int[] values) throws IOException {
        this.writeIntsNO(values, 0, values.length);
    }

    public final void writeIntsNO(final int[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(4L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 2);
            this.writeBuffer.writeIntsNO(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeFloats(final float[] values) throws IOException {
        this.writeFloats(values, 0, values.length);
    }

    public final void writeFloats(final float[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(4L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 2);
            this.writeBuffer.writeFloatsBE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeFloatsLE(final float[] values) throws IOException {
        this.writeFloatsLE(values, 0, values.length);
    }

    public final void writeFloatsLE(final float[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(4L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 2);
            this.writeBuffer.writeFloatsLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeFloatsNO(final float[] values) throws IOException {
        this.writeFloatsNO(values, 0, values.length);
    }

    public final void writeFloatsNO(final float[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(4L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 2);
            this.writeBuffer.writeFloatsLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeLongs(final long[] values) throws IOException {
        this.writeLongs(values, 0, values.length);
    }

    public final void writeLongs(final long[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(8L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 3);
            this.writeBuffer.writeLongsBE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeLongsLE(final long[] values) throws IOException {
        this.writeLongsLE(values, 0, values.length);
    }

    public final void writeLongsLE(final long[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(8L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 3);
            this.writeBuffer.writeLongsLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeLongsNO(final long[] values) throws IOException {
        this.writeLongsNO(values, 0, values.length);
    }

    public final void writeLongsNO(final long[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(8L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 3);
            this.writeBuffer.writeLongsNO(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeDoubles(final double[] values) throws IOException {
        this.writeDoubles(values, 0, values.length);
    }

    public final void writeDoubles(final double[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(8L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 3);
            this.writeBuffer.writeDoublesBE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeDoublesLE(final double[] values) throws IOException {
        this.writeDoublesLE(values, 0, values.length);
    }

    public final void writeDoublesLE(final double[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(8L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 3);
            this.writeBuffer.writeDoublesLE(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final void writeDoublesNO(final double[] values) throws IOException {
        this.writeDoublesNO(values, 0, values.length);
    }

    public final void writeDoublesNO(final double[] values, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, values.length);

        int valuesWritten = 0;
        while (valuesWritten < len) {
            this.ensureWritable(8L);

            final int toWrite = (int)Math.min((long)(len - valuesWritten), this.writeBuffer.getWritableBytes() >> 3);
            this.writeBuffer.writeDoublesNO(values, off + valuesWritten, toWrite);
            valuesWritten += toWrite;
        }
    }

    public final long write(final ReadableByteChannel channel) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }

        return this.writeBuffer.writeFromChannel(channel);
    }

    public final void write(final ReadableByteChannel channel, final long nBytes) throws IOException {
        this.tryEnsureImmediatelyWritable(nBytes);

        long bytesWritten = 0L;
        while (bytesWritten < nBytes) {
            this.ensureWritable(1L);

            final long toWrite = Math.min((nBytes - bytesWritten), this.writeBuffer.getWritableBytes());
            this.writeBuffer.writeFromChannel(channel, toWrite);
            bytesWritten += toWrite;
        }
    }

    public final long writeFilePos(final FileChannel channel, final long channelPos) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }

        return this.writeBuffer.writeFromFilePos(channel, channelPos);
    }

    public final void writeFilePos(final FileChannel channel, final long channelPos, final long nBytes) throws IOException {
        this.tryEnsureImmediatelyWritable(nBytes);

        long bytesWritten = 0L;
        while (bytesWritten < nBytes) {
            this.ensureWritable(1L);

            final long toWrite = Math.min((nBytes - bytesWritten), this.writeBuffer.getWritableBytes());
            this.writeBuffer.writeFromFilePos(channel, channelPos + bytesWritten, toWrite);
            bytesWritten += toWrite;
        }
    }

    public final long write(final Buffer src) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }

        return this.writeBuffer.writeFromBuffer(src);
    }

    public final void write(final Buffer src, final long nBytes) throws IOException {
        this.tryEnsureImmediatelyWritable(nBytes);

        long bytesWritten = 0L;
        while (bytesWritten < nBytes) {
            this.ensureWritable(1L);

            final long toWrite = Math.min((nBytes - bytesWritten), this.writeBuffer.getWritableBytes());
            this.writeBuffer.writeFromBuffer(src, toWrite);
            bytesWritten += toWrite;
        }
    }

    public final int write(final ByteBuffer src) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0;
        }

        return this.writeBuffer.writeFromByteBuffer(src);
    }

    public final void write(final ByteBuffer src, final int nBytes) throws IOException {
        this.tryEnsureImmediatelyWritable(nBytes);

        int bytesWritten = 0;
        while (bytesWritten < nBytes) {
            this.ensureWritable(1L);

            final int toWrite = (int)Math.min((long)(nBytes - bytesWritten), this.writeBuffer.getWritableBytes());
            this.writeBuffer.writeFromByteBuffer(src, toWrite);
            bytesWritten += toWrite;
        }
    }

    public final void write(final MemorySegment src, final long srcOff, final long nBytes) throws IOException {
        this.tryEnsureImmediatelyWritable(nBytes);

        long bytesWritten = 0L;
        while (bytesWritten < nBytes) {
            this.ensureWritable(1L);

            final long toWrite = Math.min((nBytes - bytesWritten), this.writeBuffer.getWritableBytes());
            this.writeBuffer.writeFromSegment(src, srcOff, toWrite);
            bytesWritten += toWrite;
        }
    }

    public final void write(final AbstractBufferInputStream src, final long nBytes) throws IOException {
        this.tryEnsureImmediatelyWritable(nBytes);

        long bytesWritten = 0L;
        while (bytesWritten < nBytes) {
            this.ensureWritable(1L);

            final long toWrite = Math.min((nBytes - bytesWritten), this.writeBuffer.getWritableBytes());

            src.read(this.writeBuffer, toWrite);
            bytesWritten += toWrite;
        }
    }
}
