package ca.spottedleaf.ioutil.stream;

import ca.spottedleaf.ioutil.buffer.Buffer;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

public abstract class AbstractBufferInputStream extends InputStream implements DataInput {

    protected final Buffer readBuffer;

    protected AbstractBufferInputStream(final Buffer readBuffer) {
        this.readBuffer = readBuffer;
    }

    // returns the number of bytes currently stored in the read buffer
    public final long getImmediatelyReadable() {
        return this.readBuffer.getReadableBytes();
    }

    public final boolean isEOF() throws IOException {
        return !this.tryEnsure(1L);
    }

    // rets whether any bytes were read
    protected abstract boolean tryFillReadBuffer() throws IOException;

    protected final void ensureReadable(final long nBytes) throws IOException {
        if (!this.tryEnsure(nBytes)) {
            throw new EOFException();
        }
    }

    protected final boolean tryEnsure(final long nBytes) throws IOException {
        if (this.readBuffer.getReadableBytes() >= nBytes) {
            return true;
        }
        return this.tryFill(nBytes);
    }

    private boolean tryFill(final long nBytes) throws IOException {
        if (this.readBuffer.getMaxCapacity() < nBytes) {
            throw new IllegalArgumentException("Asking for more bytes than can hold!");
        }

        // try to fill as long as:
        // 1) we have not filled read buffer to requested capacity
        // 2) we fill at least one byte into read buffer
        while (this.readBuffer.getReadableBytes() < nBytes && this.tryFillReadBuffer());

        return this.readBuffer.getReadableBytes() >= nBytes;
    }

    // InputStream methods

    @Override
    public final int read() throws IOException {
        if (!this.tryEnsure(1L)) {
            return -1;
        }
        return this.readBuffer.readByte() & 255;
    }

    @Override
    public final int read(final byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    @Override
    public final int read(final byte[] b, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, b.length);

        if (!this.tryEnsure(1L)) {
            return -1;
        }
        final int ret = (int)Math.min((long)len, this.readBuffer.getReadableBytes());

        this.readBuffer.readBytes(b, off, ret);

        return ret;
    }

    @Override
    public long skip(final long n) throws IOException {
        long skipped = 0L;

        while (skipped < n && this.tryEnsure(1L)) {
            final long toSkip = Math.min(n - skipped, this.readBuffer.getReadableBytes());

            skipped += toSkip;
            this.readBuffer.skipRead(toSkip);
        }

        return skipped;
    }

    @Override
    public final int available() throws IOException {
        return (int)Math.clamp(this.availableLong(), (long)Integer.MIN_VALUE, (long)Integer.MAX_VALUE);
    }

    public long availableLong() throws IOException {
        return this.readBuffer.getReadableBytes();
    }

    // DataInput methods

    @Override
    public final void readFully(final byte[] bytes) throws IOException {
        this.readFully(bytes, 0, bytes.length);
    }

    @Override
    public final void readFully(final byte[] bytes, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bytes.length);
        int bytesRead = 0;
        while (bytesRead < len) {
            this.ensureReadable(1L);

            final int toRead = (int)Math.min((long)(len - bytesRead), this.readBuffer.getReadableBytes());

            this.readBuffer.readBytes(bytes, off + bytesRead, toRead);
            bytesRead += toRead;
        }
    }

    @Override
    public final int skipBytes(final int n) throws IOException {
        return (int)this.skip((long)n);
    }

    @Override
    public final boolean readBoolean() throws IOException {
        return this.readByte() != (byte)0;
    }

    @Override
    public final byte readByte() throws IOException {
        this.ensureReadable(1L);
        return this.readBuffer.readByte();
    }

    @Override
    public final int readUnsignedByte() throws IOException {
        return (int)this.readByte() & 0xFF;
    }

    @Override
    public final short readShort() throws IOException {
        this.ensureReadable(2L);
        return this.readBuffer.readShortBE();
    }

    @Override
    public final int readUnsignedShort() throws IOException {
        return (int)this.readShort() & 0xFFFF;
    }

    @Override
    public final char readChar() throws IOException {
        this.ensureReadable(2L);
        return this.readBuffer.readCharBE();
    }

    @Override
    public final int readInt() throws IOException {
        this.ensureReadable(4L);
        return this.readBuffer.readIntBE();
    }

    @Override
    public final long readLong() throws IOException {
        this.ensureReadable(8L);
        return this.readBuffer.readLongBE();
    }

    @Override
    public final float readFloat() throws IOException {
        this.ensureReadable(4L);
        return this.readBuffer.readFloatBE();
    }

    @Override
    public final double readDouble() throws IOException {
        this.ensureReadable(8L);
        return this.readBuffer.readDoubleBE();
    }

    @Override
    public final String readLine() throws IOException {
        final StringBuilder ret = new StringBuilder();

        for (;;) {
            if (!this.tryEnsure(1L)) {
                return ret.length() == 0 ? null : ret.toString();
            }

            final int c = (int)this.readBuffer.readByte() & 0xFF;

            if (c != '\n' && c != '\r') {
                ret.append((char)c);
                continue;
            }

            if (c == '\n') {
                break;
            }

            // try to read "\n"
            final long pos;
            if (this.tryEnsure(1L) && ((int)this.readBuffer.readByte(pos = this.readBuffer.getReaderIndex()) & 0xFF) == '\n') {
                this.readBuffer.setReaderIndex(pos + 1L);
            }

            break;
        }

        return ret.toString();
    }

    @Override
    public final String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }

    // specified by us

    public final short readShortLE() throws IOException {
        this.ensureReadable(2L);
        return this.readBuffer.readShortLE();
    }

    public final short readShortNO() throws IOException {
        this.ensureReadable(2L);
        return this.readBuffer.readShortNO();
    }

    public final char readCharLE() throws IOException {
        this.ensureReadable(2L);
        return this.readBuffer.readCharLE();
    }

    public final char readCharNO() throws IOException {
        this.ensureReadable(2L);
        return this.readBuffer.readCharNO();
    }

    public final int readIntLE() throws IOException {
        this.ensureReadable(4L);
        return this.readBuffer.readIntLE();
    }

    public final int readIntNO() throws IOException {
        this.ensureReadable(4L);
        return this.readBuffer.readIntNO();
    }

    public final float readFloatLE() throws IOException {
        this.ensureReadable(4L);
        return this.readBuffer.readFloatLE();
    }

    public final float readFloatNO() throws IOException {
        this.ensureReadable(4L);
        return this.readBuffer.readFloatNO();
    }

    public final long readLongLE() throws IOException {
        this.ensureReadable(8L);
        return this.readBuffer.readLongLE();
    }

    public final long readLongNO() throws IOException {
        this.ensureReadable(8L);
        return this.readBuffer.readLongNO();
    }

    public final double readDoubleLE() throws IOException {
        this.ensureReadable(8L);
        return this.readBuffer.readDoubleLE();
    }

    public final double readDoubleNO() throws IOException {
        this.ensureReadable(8L);
        return this.readBuffer.readDoubleNO();
    }

    public final int readSignedVarInt() throws IOException {
        return Buffer.decodeSignedVarInt(this.readUnsignedVarInt());
    }

    public final int readUnsignedVarInt() throws IOException {
        if (!this.tryEnsure(5L) && this.readBuffer.getReadableBytes() < 1L) {
            throw new EOFException();
        }
        return this.readBuffer.readUnsignedVarInt();
    }

    public final long readSignedVarLong() throws IOException {
        return Buffer.decodeSignedVarLong(this.readUnsignedVarLong());
    }

    public final long readUnsignedVarLong() throws IOException {
        if (!this.tryEnsure(10L) && this.readBuffer.getReadableBytes() < 1L) {
            throw new EOFException();
        }
        return this.readBuffer.readUnsignedVarLong();
    }

    public final void readFully(final short[] shorts) throws IOException {
        this.readFully(shorts, 0, shorts.length);
    }

    public final void readFully(final short[] shorts, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, shorts.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(2L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 1);

            this.readBuffer.readShortsBE(shorts, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyLE(final short[] shorts) throws IOException {
        this.readFullyLE(shorts, 0, shorts.length);
    }

    public final void readFullyLE(final short[] shorts, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, shorts.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(2L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 1);

            this.readBuffer.readShortsLE(shorts, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyNO(final short[] shorts) throws IOException {
        this.readFullyNO(shorts, 0, shorts.length);
    }

    public final void readFullyNO(final short[] shorts, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, shorts.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(2L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 1);

            this.readBuffer.readShortsNO(shorts, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFully(final char[] chars) throws IOException {
        this.readFully(chars, 0, chars.length);
    }

    public final void readFully(final char[] chars, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, chars.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(2L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 1);

            this.readBuffer.readCharsBE(chars, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyLE(final char[] chars) throws IOException {
        this.readFullyLE(chars, 0, chars.length);
    }

    public final void readFullyLE(final char[] chars, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, chars.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(2L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 1);

            this.readBuffer.readCharsLE(chars, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyNO(final char[] chars) throws IOException {
        this.readFullyNO(chars, 0, chars.length);
    }

    public final void readFullyNO(final char[] chars, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, chars.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(2L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 1);

            this.readBuffer.readCharsNO(chars, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final int readUnsignedMedium() throws IOException {
        this.ensureReadable(3L);
        return this.readBuffer.readUnsignedMediumBE();
    }

    public final int readUnsignedMediumLE() throws IOException {
        this.ensureReadable(3L);
        return this.readBuffer.readUnsignedMediumLE();
    }

    public final int readSignedMedium() throws IOException {
        this.ensureReadable(3L);
        return this.readBuffer.readSignedMediumBE();
    }

    public final int readSignedMediumLE() throws IOException {
        this.ensureReadable(3L);
        return this.readBuffer.readSignedMediumLE();
    }

    public final void readUnsignedMediumsFully(final int[] mediums) throws IOException {
        this.readUnsignedMediumsFully(mediums, 0, mediums.length);
    }

    public final void readUnsignedMediumsFully(final int[] mediums, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, mediums.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(3L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() / 3L);

            this.readBuffer.readUnsignedMediumsBE(mediums, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readUnsignedMediumsFullyLE(final int[] mediums) throws IOException {
        this.readUnsignedMediumsFullyLE(mediums, 0, mediums.length);
    }

    public final void readUnsignedMediumsFullyLE(final int[] mediums, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, mediums.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(3L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() / 3L);

            this.readBuffer.readUnsignedMediumsLE(mediums, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readSignedMediumsFully(final int[] mediums) throws IOException {
        this.readSignedMediumsFully(mediums, 0, mediums.length);
    }

    public final void readSignedMediumsFully(final int[] mediums, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, mediums.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(3L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() / 3L);

            this.readBuffer.readSignedMediumsBE(mediums, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readSignedMediumsFullyLE(final int[] mediums) throws IOException {
        this.readSignedMediumsFullyLE(mediums, 0, mediums.length);
    }

    public final void readSignedMediumsFullyLE(final int[] mediums, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, mediums.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(3L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() / 3L);

            this.readBuffer.readSignedMediumsLE(mediums, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFully(final int[] ints) throws IOException {
        this.readFully(ints, 0, ints.length);
    }

    public final void readFully(final int[] ints, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, ints.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(4L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 2);

            this.readBuffer.readIntsBE(ints, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyLE(final int[] ints) throws IOException {
        this.readFullyLE(ints, 0, ints.length);
    }

    public final void readFullyLE(final int[] ints, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, ints.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(4L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 2);

            this.readBuffer.readIntsLE(ints, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyNO(final int[] ints) throws IOException {
        this.readFullyNO(ints, 0, ints.length);
    }

    public final void readFullyNO(final int[] ints, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, ints.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(4L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 2);

            this.readBuffer.readIntsNO(ints, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFully(final float[] floats) throws IOException {
        this.readFully(floats, 0, floats.length);
    }

    public final void readFully(final float[] floats, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, floats.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(4L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 2);

            this.readBuffer.readFloatsBE(floats, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyLE(final float[] floats) throws IOException {
        this.readFullyLE(floats, 0, floats.length);
    }

    public final void readFullyLE(final float[] floats, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, floats.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(4L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 2);

            this.readBuffer.readFloatsLE(floats, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyNO(final float[] floats) throws IOException {
        this.readFullyNO(floats, 0, floats.length);
    }

    public final void readFullyNO(final float[] floats, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, floats.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(4L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 2);

            this.readBuffer.readFloatsNO(floats, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFully(final long[] longs) throws IOException {
        this.readFully(longs, 0, longs.length);
    }

    public final void readFully(final long[] longs, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, longs.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(8L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 3);

            this.readBuffer.readLongsBE(longs, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyLE(final long[] longs) throws IOException {
        this.readFullyLE(longs, 0, longs.length);
    }

    public final void readFullyLE(final long[] longs, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, longs.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(8L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 3);

            this.readBuffer.readLongsLE(longs, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyNO(final long[] longs) throws IOException {
        this.readFullyNO(longs, 0, longs.length);
    }

    public final void readFullyNO(final long[] longs, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, longs.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(8L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 3);

            this.readBuffer.readLongsNO(longs, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFully(final double[] doubles) throws IOException {
        this.readFully(doubles, 0, doubles.length);
    }

    public final void readFully(final double[] doubles, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, doubles.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(8L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 3);

            this.readBuffer.readDoublesBE(doubles, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyLE(final double[] doubles) throws IOException {
        this.readFullyLE(doubles, 0, doubles.length);
    }

    public final void readFullyLE(final double[] doubles, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, doubles.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(8L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 3);

            this.readBuffer.readDoublesLE(doubles, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void readFullyNO(final double[] doubles) throws IOException {
        this.readFullyNO(doubles, 0, doubles.length);
    }

    public final void readFullyNO(final double[] doubles, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, doubles.length);
        int valuesRead = 0;
        while (valuesRead < len) {
            this.ensureReadable(8L);

            final int toRead = (int)Math.min((long)(len - valuesRead), this.readBuffer.getReadableBytes() >> 3);

            this.readBuffer.readDoublesNO(doubles, off + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final long read(final WritableByteChannel channel) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }
        return this.readBuffer.readIntoChannel(channel);
    }

    public final void read(final WritableByteChannel channel, final long nBytes) throws IOException {
        long bytesRead = 0L;
        while (bytesRead < nBytes) {
            this.ensureReadable(1L);

            final long toRead = Math.min(this.readBuffer.getReadableBytes(), nBytes - bytesRead);
            bytesRead += toRead;
            this.readBuffer.readIntoChannel(channel, toRead);
        }
    }

    public final long readFilePos(final FileChannel channel, final long channelPos) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }
        return this.readBuffer.readIntoFilePos(channel, channelPos);
    }

    public final void readFilePos(final FileChannel channel, final long channelPos, final long nBytes) throws IOException {
        long bytesRead = 0L;
        while (bytesRead < nBytes) {
            this.ensureReadable(1L);

            final long toRead = Math.min(this.readBuffer.getReadableBytes(), nBytes - bytesRead);
            this.readBuffer.readIntoFilePos(channel, channelPos + bytesRead, toRead);
            bytesRead += toRead;
        }
    }

    public final long read(final Buffer buffer) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }
        return this.readBuffer.readIntoBuffer(buffer);
    }

    public final void read(final Buffer buffer, final long nBytes) throws IOException {
        buffer.ensureImmediatelyWritable(nBytes);

        long bytesRead = 0L;
        while (bytesRead < nBytes) {
            this.ensureReadable(1L);

            final long toRead = Math.min(this.readBuffer.getReadableBytes(), nBytes - bytesRead);
            bytesRead += toRead;
            this.readBuffer.readIntoBuffer(buffer, toRead);
        }
    }

    public final long read(final ByteBuffer buffer) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }
        return this.readBuffer.readIntoByteBuffer(buffer);
    }

    public final void read(final ByteBuffer buffer, final int nBytes) throws IOException {
        if (buffer.remaining() < nBytes) {
            throw new IllegalArgumentException("Input buffer too small: nBytes:" + nBytes + ", remaining:" + buffer.remaining());
        }

        int bytesRead = 0;
        while (bytesRead < nBytes) {
            this.ensureReadable(1L);

            final int toRead = (int)Math.min(this.readBuffer.getReadableBytes(), (long)(nBytes - bytesRead));
            bytesRead += toRead;
            this.readBuffer.readIntoByteBuffer(buffer, toRead);
        }
    }

    public final long read(final MemorySegment segment, final long dstOffset) throws IOException {
        if (!this.tryEnsure(1L)) {
            return 0L;
        }
        return this.readBuffer.readIntoSegment(segment, dstOffset);
    }

    public final void read(final MemorySegment segment, final long dstOffset, final long nBytes) throws IOException {
        Objects.checkFromIndexSize(dstOffset, nBytes, segment.byteSize());
        long valuesRead = 0;
        while (valuesRead < nBytes) {
            this.ensureReadable(1L);

            final long toRead = Math.min(nBytes - valuesRead, this.readBuffer.getReadableBytes());

            this.readBuffer.readIntoSegment(segment, dstOffset + valuesRead, toRead);
            valuesRead += toRead;
        }
    }

    public final void read(final AbstractBufferOutputStream dst, final long nBytes) throws IOException {
        long valuesRead = 0;
        while (valuesRead < nBytes) {
            this.ensureReadable(1L);

            final long toRead = Math.min(nBytes - valuesRead, this.readBuffer.getReadableBytes());

            dst.write(this.readBuffer, toRead);
            valuesRead += toRead;
        }
    }
}
