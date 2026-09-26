package ca.spottedleaf.ioutil.stream.wrapped;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class BufferedBufferInputStream extends AbstractBufferInputStream {

    private static final String INSTANCE_KEY = "ioutil:buffer_input_stream";

    private final byte[] rawBuffer;
    private final InputStream wrap;
    private boolean closed;

    public BufferedBufferInputStream(final byte[] readBuffer, final InputStream wrap) {
        super(new Buffer(false, INSTANCE_KEY, readBuffer));
        this.wrap = wrap;
        this.rawBuffer = readBuffer;
        if (readBuffer.length < 32) {
            throw new IllegalArgumentException("Buffer is too small");
        }
    }

    @Override
    protected boolean tryFillReadBuffer() throws IOException {
        this.readBuffer.shiftReaderToZero();

        final int read = this.wrap.read(this.rawBuffer, (int)this.readBuffer.getWriterIndex(), (int)this.readBuffer.getWritableBytes());
        if (read > 0) {
            this.readBuffer.setWriterIndex(this.readBuffer.getWriterIndex() + read);
            return true;
        }
        return false;
    }

    @Override
    public long availableLong() throws IOException {
        if (this.closed) {
            throw new EOFException();
        }
        final long buffered = super.availableLong();
        final long inWrap = this.wrap instanceof AbstractBufferInputStream wrapBuffer ? wrapBuffer.availableLong() : (long)this.wrap.available();

        final long ret = buffered + inWrap;
        if (ret < 0L) {
            // overflow
            return Long.MAX_VALUE;
        }

        return ret;
    }

    @Override
    public void close() throws IOException {
        try {
            if (!this.closed) {
                this.closed = true;
                this.readBuffer.decReferenceCount(INSTANCE_KEY);
            }
        } finally {
            this.wrap.close();
        }
    }
}
