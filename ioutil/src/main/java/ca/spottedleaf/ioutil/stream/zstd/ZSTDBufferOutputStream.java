package ca.spottedleaf.ioutil.stream.zstd;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.ZstdCompressCtx;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

public final class ZSTDBufferOutputStream extends AbstractBufferOutputStream {

    private static final String REF_KEY = "ioutil:zstd_out_stream";

    private final Buffer compressedBuffer;
    private boolean closed;
    private final ZstdCompressCtx compressor;
    private final Consumer<ZstdCompressCtx> closeCompressor;
    // if wrap is null, then we only write to compressed buffer
    private final AbstractBufferOutputStream wrap;

    public ZSTDBufferOutputStream(final Buffer compressedBuffer, final Buffer decompressedBuffer,
                                  final ZstdCompressCtx compressor,
                                  final Consumer<ZstdCompressCtx> closeCompressor,
                                  final AbstractBufferOutputStream wrap) {
        super(decompressedBuffer);

        Objects.requireNonNull(compressor, "Compressor must be non-null");

        if (!decompressedBuffer.isNative() || !compressedBuffer.isNative()) {
            throw new IllegalArgumentException("Buffers must be native");
        }

        decompressedBuffer.ensureCapacity(32L);
        decompressedBuffer.clear();

        if (wrap != null) {
            compressedBuffer.ensureCapacity(32L);
            compressedBuffer.clear();
        }

        compressedBuffer.incReferenceCount(REF_KEY);
        decompressedBuffer.incReferenceCount(REF_KEY);

        synchronized (this) {
            this.compressedBuffer = compressedBuffer;
            this.compressor = compressor;
            this.closeCompressor = closeCompressor;
            this.wrap = wrap;
        }
    }

    private void flushCompressedBuffer() throws IOException {
        if (this.compressedBuffer.getReadableBytes() != 0L) {
            this.wrap.write(this.compressedBuffer, this.compressedBuffer.getReadableBytes());
            this.compressedBuffer.clear();
        }
    }

    private void requireWrapFlushed() throws IOException {
        if (this.wrap == null) {
            if (this.compressedBuffer.getWritableBytes() == 0L) {
                throw new EOFException();
            }
            return;
        }

        if (this.compressedBuffer.getWritableBytes() == 0L) {
            this.flushCompressedBuffer();
        }
    }

    private boolean performCompress(final EndDirective endDirective) throws IOException {
        this.requireWrapFlushed();

        final ByteBuffer src = this.writeBuffer.getBufferAsRead();
        final int srcStart = src.position();
        if (this.compressedBuffer.getImmediatelyWritableBytes() == 0L) {
            // expand compressed buffer
            this.compressedBuffer.ensureImmediatelyWritable(1L);
        }
        final ByteBuffer dst = this.compressedBuffer.getBufferAsWriter();
        final int dstStart = dst.position();

        final boolean ret = this.compressor.compressDirectByteBufferStream(dst, src, endDirective);

        // need to adjust indices of buffers manually
        final int bytesWritten = dst.position() - dstStart;
        final int bytesRead = src.position() - srcStart;

        this.writeBuffer.setReaderIndex(this.writeBuffer.getReaderIndex() + bytesRead);
        this.compressedBuffer.setWriterIndex(this.compressedBuffer.getWriterIndex() + bytesWritten);

        return ret;
    }

    private void flushWriteBuffer(final EndDirective endDirective) throws IOException {
        final boolean force = endDirective != EndDirective.CONTINUE;

        boolean flushed = false;
        while (this.writeBuffer.getReadableBytes() != 0L || (force && !flushed)) {
            flushed = this.performCompress(endDirective);
        }
        this.writeBuffer.clear();
    }

    @Override
    protected synchronized boolean tryFlushWriteBuffer() throws IOException {
        if (this.closed) {
            return false;
        }
        this.flushWriteBuffer(EndDirective.CONTINUE);
        return true;
    }

    @Override
    public synchronized void flush() throws IOException {
        if (this.closed) {
            return;
        }
        this.flushWriteBuffer(EndDirective.FLUSH);
        if (this.wrap != null) {
            this.flushCompressedBuffer();
            this.wrap.flush();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;

        try {
            try {
                this.flushWriteBuffer(EndDirective.END);
                if (this.wrap != null) {
                    this.flushCompressedBuffer();
                }
            } finally {
                try {
                    if (this.closeCompressor != null) {
                        this.closeCompressor.accept(this.compressor);
                    }
                } finally {
                    this.writeBuffer.decReferenceCount(REF_KEY);
                    this.compressedBuffer.decReferenceCount(REF_KEY);
                }
            }
        } finally {
            if (this.wrap != null) {
                this.wrap.close();
            }
        }
    }
}
