package ca.spottedleaf.ioutil.util;

import ca.spottedleaf.ioutil.buffer.Buffer;
import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class CompressionUtil {

    private static final Cleaner CLEANER = Cleaner.create();
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(() -> {
        final MemorySegment workInRaw = Arena.ofAuto().allocate(24L * 1024L, 32L);
        final MemorySegment workOutRaw = Arena.ofAuto().allocate(24L * 1024L, 32L);

        final Cache cache = new Cache(
                workInRaw, workInRaw.asByteBuffer(),
                workOutRaw, workOutRaw.asByteBuffer(),
                new Deflater(),
                new Inflater(),
                new ZstdCompressCtx(),
                new ZstdDecompressCtx()
        );

        final Deflater deflater = cache.deflater;
        final Inflater inflater = cache.inflater;
        final ZstdCompressCtx zstdCompressor = cache.zstdCompressor;
        final ZstdDecompressCtx zstdDecompressor = cache.zstdDecompressor;

        CLEANER.register(cache, deflater::end);
        CLEANER.register(cache, inflater::end);
        CLEANER.register(cache, zstdCompressor::close);
        CLEANER.register(cache, zstdDecompressor::close);

        return cache;
    });

    public static int deflate(final Buffer src, final int maxRead,
                              final Buffer dst, final int maxWrite) {
        return deflate(src, maxRead, dst, maxWrite, null);
    }

    public static int deflate(final Buffer src, final int toRead,
                              final Buffer dst, final int maxWrite,
                              final Consumer<Deflater> deflaterConsumer) {
        final int ret = deflate(
                src.getMemoryAsSegment(), Buffer.castIndexToInt(src.getReaderIndex()), toRead,
                dst, maxWrite, deflaterConsumer
        );

        src.skipRead((long)toRead);

        return ret;
    }

    public static int deflate(final MemorySegment src, final int srcOff, final int srcLen,
                              final Buffer dst, final int maxWrite) {
        return deflate(src, srcOff, srcLen, dst, maxWrite, null);
    }

    public static int deflate(final MemorySegment src, final int srcOff, final int srcLen,
                              final Buffer dst, final int maxWrite,
                              final Consumer<Deflater> deflaterConsumer) {
        Objects.checkFromIndexSize((long)srcOff, (long)srcLen, src.byteSize());
        Objects.checkFromIndexSize(dst.getWriterIndex(), (long)maxWrite, dst.getMaxCapacity());

        final Cache cache = CACHE.get();
        final Deflater deflater = cache.deflater;

        deflater.reset();
        deflater.setLevel(Deflater.DEFAULT_COMPRESSION);
        deflater.setStrategy(Deflater.DEFAULT_STRATEGY);

        try {
            if (deflaterConsumer != null) {
                deflaterConsumer.accept(deflater);
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

            deflater.setInput(input);

            if (inputNative) {
                deflater.finish();
            }

            int bytesRead = 0;
            int bytesWritten = 0;
            while (!deflater.finished()) {
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
                        deflater.finish();
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

                final int compressed = deflater.deflate(output);
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
            }

            return bytesWritten;
        } finally {
            deflater.reset();
        }
    }

    public static int inflate(final Buffer src, final int maxRead,
                              final Buffer dst, final int maxWrite) throws DataFormatException {
        return inflate(src, maxRead, dst, maxWrite, null);
    }

    public static int inflate(final Buffer src, final int toRead,
                              final Buffer dst, final int maxWrite,
                              final Consumer<Inflater> inflaterConsumer) throws DataFormatException {
        final int ret = inflate(
                src.getMemoryAsSegment(), Buffer.castIndexToInt(src.getReaderIndex()), toRead,
                dst, maxWrite, inflaterConsumer
        );

        src.skipRead((long)toRead);

        return ret;
    }

    public static int inflate(final MemorySegment src, final int srcOff, final int srcLen,
                              final Buffer dst, final int maxWrite) throws DataFormatException {
        return inflate(src, srcOff, srcLen, dst, maxWrite, null);
    }

    public static int inflate(final MemorySegment src, final int srcOff, final int srcLen,
                              final Buffer dst, final int maxWrite,
                              final Consumer<Inflater> inflaterConsumer) throws DataFormatException {
        Objects.checkFromIndexSize((long)srcOff, (long)srcLen, src.byteSize());
        Objects.checkFromIndexSize(dst.getWriterIndex(), (long)maxWrite, dst.getMaxCapacity());

        final Cache cache = CACHE.get();
        final Inflater inflater = cache.inflater;

        inflater.reset();

        try {
            if (inflaterConsumer != null) {
                inflaterConsumer.accept(inflater);
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

            inflater.setInput(input);

            int bytesRead = inputNative ? srcLen : 0;
            int bytesWritten = 0;
            while (!inflater.finished()) {
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

                final int decompressed = inflater.inflate(output);
                bytesWritten += decompressed;

                // error handling

                if (inflater.needsDictionary()) {
                    throw new DataFormatException("Dictionary is requested but not present");
                }

                if (decompressed == 0 && (bytesRead == srcLen && !input.hasRemaining()) && !inflater.finished()) {
                    // we have no more input, and we produced no output
                    throw new DataFormatException("Ran out of data before reaching end");
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
            }

            if (bytesRead != srcLen || input.hasRemaining()) {
                throw new DataFormatException("Reached end before reading all bytes");
            }

            return bytesWritten;
        } finally {
            inflater.reset();
        }
    }


    public static int zstdCompress(final Buffer src, final int maxRead,
                                   final Buffer dst, final int maxWrite) throws ZstdException {
        return zstdCompress(src, maxRead, dst, maxWrite, null);
    }

    public static int zstdCompress(final Buffer src, final int toRead,
                                   final Buffer dst, final int maxWrite,
                                   final Consumer<ZstdCompressCtx> ctxConsumer) throws ZstdException {
        final int ret = zstdCompress(
                src.getMemoryAsSegment(), Buffer.castIndexToInt(src.getReaderIndex()), toRead,
                dst, maxWrite, ctxConsumer
        );

        src.skipRead((long)toRead);

        return ret;
    }

    public static int zstdCompress(final MemorySegment src, final int srcOff, final int srcLen,
                                   final Buffer dst, final int maxWrite) throws ZstdException {
        return zstdCompress(src, srcOff, srcLen, dst, maxWrite, null);
    }

    public static int zstdCompress(final MemorySegment src, final int srcOff, final int srcLen,
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

    public static int zstdDecompress(final Buffer src, final int maxRead,
                                     final Buffer dst, final int maxWrite) throws ZstdException {
        return zstdDecompress(src, maxRead, dst, maxWrite, null);
    }

    public static int zstdDecompress(final Buffer src, final int toRead,
                                     final Buffer dst, final int maxWrite,
                                     final Consumer<ZstdDecompressCtx> ctxConsumer) throws ZstdException {
        final int ret = zstdDecompress(
                src.getMemoryAsSegment(), Buffer.castIndexToInt(src.getReaderIndex()), toRead,
                dst, maxWrite, ctxConsumer
        );

        src.skipRead((long)toRead);

        return ret;
    }

    public static int zstdDecompress(final MemorySegment src, final int srcOff, final int srcLen,
                                     final Buffer dst, final int maxWrite) throws ZstdException {
        return zstdDecompress(src, srcOff, srcLen, dst, maxWrite, null);
    }

    public static int zstdDecompress(final MemorySegment src, final int srcOff, final int srcLen,
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

    private CompressionUtil() {}

    private static final class Cache {
        private final MemorySegment workInRaw;
        private final ByteBuffer workIn;
        private final MemorySegment workOutRaw;
        private final ByteBuffer workOut;
        private final Deflater deflater;
        private final Inflater inflater;
        private final ZstdCompressCtx zstdCompressor;
        private final ZstdDecompressCtx zstdDecompressor;

        private Cache(final MemorySegment workInRaw, final ByteBuffer workIn,
                      final MemorySegment workOutRaw, final ByteBuffer workOut,
                      final Deflater deflater, final Inflater inflater,
                      final ZstdCompressCtx zstdCompressor, final ZstdDecompressCtx zstdDecompressor) {
            this.workInRaw = workInRaw;
            this.workIn = workIn;
            this.workOutRaw = workOutRaw;
            this.workOut = workOut;
            this.deflater = deflater;
            this.inflater = inflater;
            this.zstdCompressor = zstdCompressor;
            this.zstdDecompressor = zstdDecompressor;
        }
    }
}
