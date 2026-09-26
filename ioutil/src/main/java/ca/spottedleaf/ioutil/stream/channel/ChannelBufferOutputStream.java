package ca.spottedleaf.ioutil.stream.channel;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;

public final class ChannelBufferOutputStream extends AbstractBufferOutputStream {

    private final WritableByteChannel channel;
    private final boolean closeChannel;
    private Long lastPosition;

    public ChannelBufferOutputStream(final Buffer readBuffer, final WritableByteChannel channel) {
        this(readBuffer, channel, true);
    }

    public ChannelBufferOutputStream(final Buffer readBuffer, final WritableByteChannel channel, final boolean closeChannel) {
        super(readBuffer);
        this.channel = channel;
        this.closeChannel = closeChannel;
        readBuffer.ensureCapacity(32L);
        readBuffer.clear();
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
    protected boolean tryFlushWriteBuffer() throws IOException {
        final long toRead = this.writeBuffer.getReadableBytes();
        if (toRead == 0L) {
            return true;
        }

        this.lastPosition = null;
        this.writeBuffer.readIntoChannel(this.channel, toRead);
        this.writeBuffer.clear();

        return true;
    }

    @Override
    public void flush() throws IOException {
        this.tryFlushWriteBuffer();
    }

    @Override
    public void close() throws IOException {
        try {
            this.tryFlushWriteBuffer();
        } finally {
            this.lastPosition = null;
            if (this.closeChannel) {
                this.channel.close();
            }
        }
    }

    public long channelPosition() throws IOException {
        if (!(this.channel instanceof SeekableByteChannel seekableByteChannel)) {
            throw new IllegalStateException("Channel is not seekable");
        }

        // add the bytes we have not yet written
        return this.getChannelPos(seekableByteChannel) + this.writeBuffer.getReadableBytes();
    }

    public void setPosition(final long pos) throws IOException {
        if (!(this.channel instanceof SeekableByteChannel seekableByteChannel)) {
            throw new IllegalStateException("Channel is not seekable");
        }

        // we need to flush bytes written to the current position
        this.flush();

        // now we can update the position
        this.setChannelPos(seekableByteChannel, pos);
    }
}
