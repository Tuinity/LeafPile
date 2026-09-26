package ca.spottedleaf.ioutil.stream.channel;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;

public final class ChannelBufferInputStream extends AbstractBufferInputStream {

    private final ReadableByteChannel channel;
    private final boolean closeChannel;
    private Long lastPosition;

    public ChannelBufferInputStream(final Buffer readBuffer, final ReadableByteChannel channel) {
        this(readBuffer, channel, true);
    }

    public ChannelBufferInputStream(final Buffer readBuffer, final ReadableByteChannel channel, final boolean closeChannel) {
        super(readBuffer);
        this.channel = channel;
        this.closeChannel = closeChannel;
        readBuffer.ensureCapacity(32L);
        readBuffer.clear();
    }

    @Override
    protected boolean tryFillReadBuffer() throws IOException {
        this.lastPosition = null;
        this.readBuffer.shiftReaderToZero();
        return this.readBuffer.writeFromChannel(this.channel) > 0L;
    }

    private long getChannelPos(final SeekableByteChannel seekableByteChannel) throws IOException {
        if (this.lastPosition != null) {
            return this.lastPosition.longValue();
        }

        final long ret = seekableByteChannel.position();
        this.lastPosition = Long.valueOf(ret);
        return ret;
    }

    private void setChannelPos(final SeekableByteChannel seekableByteChannel, final long to) throws IOException {
        seekableByteChannel.position(to);
        this.lastPosition = Long.valueOf(to);
    }

    @Override
    public void close() throws IOException {
        this.lastPosition = null;
        if (this.closeChannel) {
            this.channel.close();
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (!(this.channel instanceof SeekableByteChannel seekableByteChannel)) {
            return super.skip(n);
        }
        long totalSkipped = 0L;

        // skip in buffer
        final long bufferSkip = Math.min(n, this.readBuffer.getReadableBytes());
        this.readBuffer.skipRead(bufferSkip);

        totalSkipped += bufferSkip;
        n -= bufferSkip;

        // skip in channel
        if (n > 0L) {
            final long position = this.getChannelPos(seekableByteChannel);
            final long size = seekableByteChannel.size();

            final long fileSkip = Math.min(Math.max(0L, size - position), n);

            if (fileSkip != 0L) {
                this.setChannelPos(seekableByteChannel, fileSkip + position);
            }

            totalSkipped += fileSkip;
            n -= fileSkip;
        }

        return totalSkipped;
    }

    @Override
    public void skipNBytes(final long n) throws IOException {
        if (!(this.channel instanceof SeekableByteChannel seekableByteChannel)) {
            super.skipNBytes(n);
            return;
        }

        if (n <= 0) {
            return;
        }

        final long skipInBuffer = Math.min(n, this.readBuffer.getReadableBytes());
        if (skipInBuffer != 0L) {
            this.readBuffer.skipRead(skipInBuffer);
        }

        final long skipInPosition = n - skipInBuffer;
        if (skipInPosition != 0L) {
            final long nextPosition = this.getChannelPos(seekableByteChannel) + skipInPosition;
            if (nextPosition - seekableByteChannel.size() > 0L) {
                throw new EOFException();
            }
            this.setChannelPos(seekableByteChannel, nextPosition);
        }
    }

    @Override
    public long availableLong() throws IOException {
        if (!(this.channel instanceof SeekableByteChannel seekableByteChannel)) {
            return super.availableLong();
        }
        // silently discard odd race conditions
        return this.readBuffer.getReadableBytes() + Math.max(0L, seekableByteChannel.size() - this.getChannelPos(seekableByteChannel));
    }

    public long channelPosition() throws IOException {
        if (!(this.channel instanceof SeekableByteChannel seekableByteChannel)) {
            throw new IllegalStateException("Channel is not seekable");
        }

        // subtract the bytes we have not yet read
        return this.getChannelPos(seekableByteChannel) - this.readBuffer.getReadableBytes();
    }

    public void setPosition(final long pos) throws IOException {
        if (!(this.channel instanceof SeekableByteChannel seekableByteChannel)) {
            throw new IllegalStateException("Channel is not seekable");
        }

        // we actually need to discard the read buffer, as we do not know what bytes were read from where
        this.readBuffer.clear();
        this.setChannelPos(seekableByteChannel, pos);
    }
}
