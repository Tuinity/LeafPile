package ca.spottedleaf.ioutil.stream.wrapped;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class BufferedBufferOutputStream extends AbstractBufferOutputStream {
    private static final String INSTANCE_KEY = "ioutil:buffer_output_stream";

    private final byte[] rawBuffer;
    private final OutputStream wrap;
    private boolean closed;

    public BufferedBufferOutputStream(final byte[] writeBuffer, final OutputStream wrap) {
        super(new Buffer(false, INSTANCE_KEY, writeBuffer));
        this.wrap = wrap;
        this.rawBuffer = writeBuffer;
        if (writeBuffer.length < 32) {
            throw new IllegalArgumentException("Buffer is too small");
        }
    }

    @Override
    protected boolean tryFlushWriteBuffer() throws IOException {
        final long read = this.writeBuffer.getReaderIndex();
        final long write = this.writeBuffer.getWriterIndex();

        final int toWrite = (int)(write - read);
        if (toWrite == 0) {
            this.writeBuffer.clear();
            return true;
        }

        this.wrap.write(this.rawBuffer, (int)read, toWrite);
        this.writeBuffer.clear();
        return true;
    }

    @Override
    public void flush() throws IOException {
        this.tryFlushWriteBuffer();
        this.wrap.flush();
    }

    @Override
    public void close() throws IOException {
        try {
            try {
                this.tryFlushWriteBuffer();
            } finally {
                if (!this.closed) {
                    this.closed = true;
                    this.writeBuffer.decReferenceCount(INSTANCE_KEY);
                }
            }
        } finally {
            this.wrap.close();
        }
    }
}
