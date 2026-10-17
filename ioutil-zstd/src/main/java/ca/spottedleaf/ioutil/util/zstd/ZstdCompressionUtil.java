package ca.spottedleaf.ioutil.util.zstd;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.util.CompressionBuffers;
import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

public final class ZstdCompressionUtil {

    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(() -> {
        final CompressionBuffers buffers = CompressionBuffers.get();

        final Cache cache = new Cache(
                buffers.workInRaw, buffers.workIn,
                buffers.workOutRaw, buffers.workOut,
                new ZstdCompressCtx(),
                new ZstdDecompressCtx()
        );

        final ZstdCompressCtx zstdCompressor = cache.zstdCompressor;
        final ZstdDecompressCtx zstdDecompressor = cache.zstdDecompressor;

        CompressionBuffers.CLEANER.register(cache, zstdCompressor::close);
        CompressionBuffers.CLEANER.register(cache, zstdDecompressor::close);

        return cache;
    });

    public static int compress(final Buffer src, final int maxRead,
                               final Buffer dst, final int maxWrite) throws ZstdException {
        return compress(src, maxRead, dst, maxWrite, null);
    }

    public static int compress(final Buffer src, final int toRead,
                               final Buffer dst, final int maxWrite,
                               final Consumer<ZstdCompressCtx> ctxConsumer) throws ZstdException {
        final int ret = compress(
                src.getMemoryAsSegment(), Buffer.castIndexToInt(src.getReaderIndex()), toRead,
                dst, maxWrite, ctxConsumer
        );

        src.skipRead((long)toRead);

        return ret;
    }

    public static int compress(final MemorySegment src, final int srcOff, final int srcLen,
                               final Buffer dst, final int maxWrite) throws ZstdException {
        return compress(src, srcOff, srcLen, dst, maxWrite, null);
    }

    public static int compress(final MemorySegment src, final int srcOff, final int srcLen,
                               final Buffer dst, final int maxWrite,
                               final Consumer<ZstdCompressCtx> ctxConsumer) throws ZstdException {
        Objects.checkFromIndexSize((long)srcOff, (long)srcLen, src.byteSize());
        Objects.checkFromIndexSize(dst.getWriterIndex(), (long)maxWrite, dst.getMaxCapacity());

        final Cache cache = CACHE.get();
        final ZstdCompressCtx ctx = cache.zstdCompressor;

        ctx.reset();

        try {
            if (ctxConsumer != null) {
                ctxConsumer.accept(ctx);
            }

            // setup in/out buffers
            final boolean inputNative = src.isNative();
            final boolean outputNative = dst.isNative();

            final ByteBuffer input;
            final MemorySegment inputRaw;
            if (inputNative) {
                // we can avoid copying by using src directly
                inputRaw = src;
                input = src.asByteBuffer();
                input.position(srcOff);
                input.limit(srcOff + srcLen);
            } else {
                inputRaw = cache.workInRaw;
                input = cache.workIn;
                input.clear();
                // note: we haven't copied data into input, so set the correct limit
                input.limit(0);
            }

            ByteBuffer output;
            MemorySegment outputRaw;
            if (outputNative) {
                // we can possibly avoid copying by using dst directly
                outputRaw = dst.getMemoryAsSegment();
                output = outputRaw.asByteBuffer();
                output.position(Buffer.castIndexToInt(dst.getWriterIndex()));
                output.limit(Math.min(output.capacity(), Buffer.castIndexToInt(dst.getWriterIndex() + (long) maxWrite)));
            } else {
                outputRaw = cache.workOutRaw;
                output = cache.workOut;
                output.clear();
            }

            EndDirective endDirective = EndDirective.CONTINUE;

            if (inputNative) {
                endDirective = EndDirective.END;
            }

            int bytesRead = 0;
            int bytesWritten = 0;
            for (;;) {
                // setup input
                if (!inputNative && !input.hasRemaining()) {
                    // copy more data
                    final int toRead = Math.min(input.capacity(), srcLen - bytesRead);
                    MemorySegment.copy(src, (long)srcOff + (long)bytesRead, inputRaw, 0L, (long)toRead);
                    input.position(0);
                    input.limit(toRead);

                    bytesRead += toRead;

                    // check for end of input
                    if (bytesRead == srcLen) {
                        endDirective = EndDirective.END;
                    }
                }

                // setup output
                if (!outputNative) {
                    // we will copy from output, so must reset indices
                    output.clear();
                } else {
                    // check for resizing output
                    if (!output.hasRemaining()) {
                        if (dst.getImmediatelyWritableBytes() != 0L) {
                            // we kept writer index updated, so this should be 0
                            throw new IllegalStateException();
                        }

                        // resize
                        dst.ensureImmediatelyWritable(1L);

                        // update buffers
                        outputRaw = dst.getMemoryAsSegment();
                        output = outputRaw.asByteBuffer();
                        output.position(Buffer.castIndexToInt(dst.getWriterIndex()));
                        output.limit(Math.min(output.capacity(), Buffer.castIndexToInt(dst.getWriterIndex() + (long)maxWrite - (long)bytesWritten)));
                    }
                }

                // do decompression

                final int oldOutP = output.position();
                final boolean finished = ctx.compressDirectByteBufferStream(output, input, endDirective);
                final int compressed = output.position() - oldOutP;
                bytesWritten += compressed;

                // possibly copy and update indices
                if (compressed != 0) {
                    if (!outputNative) {
                        dst.writeFromSegment(outputRaw, 0L, (long)output.position());
                    } else {
                        // only need to update writer index
                        dst.setWriterIndex(dst.getWriterIndex() + (long)compressed);
                    }
                }

                // did we finish
                if (endDirective == EndDirective.END && finished) {
                    return bytesWritten;
                }
            }
        } finally {
            ctx.reset();
        }
    }

    public static int decompress(final Buffer src, final int maxRead,
                                 final Buffer dst, final int maxWrite) throws ZstdException {
        return decompress(src, maxRead, dst, maxWrite, null);
    }

    public static int decompress(final Buffer src, final int toRead,
                                 final Buffer dst, final int maxWrite,
                                 final Consumer<ZstdDecompressCtx> ctxConsumer) throws ZstdException {
        final int ret = decompress(
                src.getMemoryAsSegment(), Buffer.castIndexToInt(src.getReaderIndex()), toRead,
                dst, maxWrite, ctxConsumer
        );

        src.skipRead((long)toRead);

        return ret;
    }

    public static int decompress(final MemorySegment src, final int srcOff, final int srcLen,
                                 final Buffer dst, final int maxWrite) throws ZstdException {
        return decompress(src, srcOff, srcLen, dst, maxWrite, null);
    }

    public static int decompress(final MemorySegment src, final int srcOff, final int srcLen,
                                 final Buffer dst, final int maxWrite,
                                 final Consumer<ZstdDecompressCtx> ctxConsumer) throws ZstdException {
        Objects.checkFromIndexSize((long)srcOff, (long)srcLen, src.byteSize());
        Objects.checkFromIndexSize(dst.getWriterIndex(), (long)maxWrite, dst.getMaxCapacity());

        final Cache cache = CACHE.get();
        final ZstdDecompressCtx ctx = cache.zstdDecompressor;

        ctx.reset();

        try {
            if (ctxConsumer != null) {
                ctxConsumer.accept(ctx);
            }

            // setup in/out buffers
            final boolean inputNative = src.isNative();
            final boolean outputNative = dst.isNative();

            final ByteBuffer input;
            final MemorySegment inputRaw;
            if (inputNative) {
                // we can avoid copying by using src directly
                inputRaw = src;
                input = src.asByteBuffer();
                input.position(srcOff);
                input.limit(srcOff + srcLen);
            } else {
                inputRaw = cache.workInRaw;
                input = cache.workIn;
                input.clear();
                // note: we haven't copied data into input, so set the correct limit
                input.limit(0);
            }

            ByteBuffer output;
            MemorySegment outputRaw;
            if (outputNative) {
                // we can possibly avoid copying by using dst directly
                outputRaw = dst.getMemoryAsSegment();
                output = outputRaw.asByteBuffer();
                output.position(Buffer.castIndexToInt(dst.getWriterIndex()));
                output.limit(Math.min(output.capacity(), Buffer.castIndexToInt(dst.getWriterIndex() + (long)maxWrite)));
            } else {
                outputRaw = cache.workOutRaw;
                output = cache.workOut;
                output.clear();
            }

            int bytesRead = inputNative ? srcLen : 0;
            int bytesWritten = 0;
            for (;;) {
                // setup input
                if (!inputNative && !input.hasRemaining()) {
                    // copy more data
                    final int toRead = Math.min(input.capacity(), srcLen - bytesRead);
                    MemorySegment.copy(src, (long)srcOff + (long)bytesRead, inputRaw, 0L, (long)toRead);
                    input.position(0);
                    input.limit(toRead);

                    bytesRead += toRead;
                }

                // setup output
                if (!outputNative) {
                    // we will copy from output, so must reset indices
                    output.clear();
                } else {
                    // check for resizing output
                    if (!output.hasRemaining()) {
                        if (dst.getImmediatelyWritableBytes() != 0L) {
                            // we kept writer index updated, so this should be 0
                            throw new IllegalStateException();
                        }

                        // resize
                        dst.ensureImmediatelyWritable(1L);

                        // update buffers
                        outputRaw = dst.getMemoryAsSegment();
                        output = outputRaw.asByteBuffer();
                        output.position(Buffer.castIndexToInt(dst.getWriterIndex()));
                        output.limit(Math.min(output.capacity(), Buffer.castIndexToInt(dst.getWriterIndex() + (long)maxWrite - (long)bytesWritten)));
                    }
                }

                // do decompression

                final int oldOutP = output.position();
                final boolean flushed = ctx.decompressDirectByteBufferStream(output, input);
                final int decompressed = output.position() - oldOutP;
                bytesWritten += decompressed;

                final boolean noMoreInput = (bytesRead == srcLen && !input.hasRemaining());

                // error handling

                if (decompressed == 0 && noMoreInput && !flushed) {
                    // we have no more input, and we produced no output
                    throw new ZstdException(Zstd.errGeneric(), "Ran out of data before reaching end");
                }

                // possibly copy and update indices
                if (decompressed != 0) {
                    if (!outputNative) {
                        dst.writeFromSegment(outputRaw, 0L, (long)output.position());
                    } else {
                        // only need to update writer index
                        dst.setWriterIndex(dst.getWriterIndex() + (long)decompressed);
                    }
                }

                if (flushed && noMoreInput) {
                    return bytesWritten;
                }
            }
        } finally {
            ctx.reset();
        }
    }

    private ZstdCompressionUtil() {}

    private static final class Cache {
        private final MemorySegment workInRaw;
        private final ByteBuffer workIn;
        private final MemorySegment workOutRaw;
        private final ByteBuffer workOut;
        private final ZstdCompressCtx zstdCompressor;
        private final ZstdDecompressCtx zstdDecompressor;

        private Cache(final MemorySegment workInRaw, final ByteBuffer workIn,
                      final MemorySegment workOutRaw, final ByteBuffer workOut,
                      final ZstdCompressCtx zstdCompressor, final ZstdDecompressCtx zstdDecompressor) {
            this.workInRaw = workInRaw;
            this.workIn = workIn;
            this.workOutRaw = workOutRaw;
            this.workOut = workOut;
            this.zstdCompressor = zstdCompressor;
            this.zstdDecompressor = zstdDecompressor;
        }
    }
}
