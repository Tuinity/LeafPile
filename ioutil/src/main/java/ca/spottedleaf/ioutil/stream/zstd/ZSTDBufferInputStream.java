package ca.spottedleaf.ioutil.stream.zstd;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.stream.AbstractBufferInputStream;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdIOException;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

public final class ZSTDBufferInputStream extends AbstractBufferInputStream {

    private static final String REF_KEY = "ioutil:zstd_in_stream";

    private final Buffer compressedBuffer;
    private boolean closed;
    private final ZstdDecompressCtx decompressor;
    private final Consumer<ZstdDecompressCtx> closeDecompressor;
    // if wrap is null, then we only read from compressed buffer
    private final AbstractBufferInputStream wrap;
    private boolean lastDecompressFlushed;
    private boolean done;

    public ZSTDBufferInputStream(final Buffer decompressedBuffer, final Buffer compressedBuffer,
                                 final ZstdDecompressCtx decompressor,
                                 final Consumer<ZstdDecompressCtx> closeDecompressor,
                                 final AbstractBufferInputStream wrap) {
        super(decompressedBuffer);

        Objects.requireNonNull(decompressor, "Decompressor must be non-null");

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
            this.decompressor = decompressor;
            this.closeDecompressor = closeDecompressor;
            this.wrap = wrap;
        }
    }

    @Override
    protected synchronized boolean tryFillReadBuffer() throws IOException {
        if (this.closed) {
            throw new IOException("Closed stream");
        }

        if (this.done) {
            return false;
        }

        for (;;) {
            // try to read more data from wrapped if needed
            if (this.compressedBuffer.getReadableBytes() == 0) {
                final long read;
                if (this.wrap == null) {
                    read = 0L;
                } else {
                    this.compressedBuffer.clear();
                    read = this.wrap.read(this.compressedBuffer);
                }

                if (read == 0L) {
                    // EOF
                    if (!this.lastDecompressFlushed) {
                        throw new ZstdIOException(Zstd.errCorruptionDetected(), "Truncated stream");
                    }
                    // expected
                    return false;
                } else {
                    // more data to decompress, so reset the last flushed
                    this.lastDecompressFlushed = false;
                }
            }

            // we have data in compressed


            // make room in read buffer
            this.readBuffer.shiftReaderToZero();

            final ByteBuffer dst = this.readBuffer.getBufferAsWriter();
            final int dstStart = dst.position();

            final ByteBuffer src = this.compressedBuffer.getBufferAsRead();
            final int srcStart = src.position();

            this.lastDecompressFlushed = this.decompressor.decompressDirectByteBufferStream(dst, src);

            // update indices on real buffers

            final int bytesWritten = dst.position() - dstStart;
            final int bytesRead = src.position() - srcStart;

            this.readBuffer.setWriterIndex(this.readBuffer.getWriterIndex() + bytesWritten);
            this.compressedBuffer.setReaderIndex(this.compressedBuffer.getReaderIndex() + bytesRead);

            // check if we can return

            if (bytesWritten != 0) {
                // if we wrote anything, then we're good
                return true;
            } else if (this.lastDecompressFlushed) {
                // reached end of stream: no data read, all buffers flushed from zstd
                this.done = true;
                return false;
            } // else: we need more compressed data to produce output
        }
    }

    @Override
    public synchronized long availableLong() throws IOException {
        if (this.closed) {
            throw new EOFException();
        }

        final long readLen = this.readBuffer.getReadableBytes();
        final long compressedLen = this.compressedBuffer.getReadableBytes();
        final long wrapLen = (this.wrap == null ? 0L : this.wrap.availableLong());

        final long a = readLen + compressedLen;
        if (a < 0L) {
            // overflow
            return Long.MAX_VALUE;
        }
        final long ret = a + wrapLen;
        if (ret < 0L) {
            // overflow
            return Long.MAX_VALUE;
        }

        return ret;
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;

        try {
            if (this.wrap != null) {
                this.wrap.close();
            }
        } finally {
            try {
                if (this.closeDecompressor != null) {
                    this.closeDecompressor.accept(this.decompressor);
                }
            } finally {
                this.readBuffer.decReferenceCount(REF_KEY);
                this.compressedBuffer.decReferenceCount(REF_KEY);
            }
        }
    }
}
