package ca.spottedleaf.ioutil.util;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;

import static org.junit.jupiter.api.Assertions.*;

public final class CompressionTest {

    private static final Random RANDOM = new Random(7L);
    private static final byte[] SMALL_DATA_BYTES = new byte[128];
    private static final byte[] SMALL_DATA_INCOMPRESSIBLE_BYTES = new byte[128];
    private static final byte[] LARGE_DATA_BYTES = new byte[1*1024*1024];
    private static final byte[] LARGE_DATA_INCOMPRESSIBLE_BYTES = new byte[1*1024*1024];
    static {
        Arrays.fill(SMALL_DATA_BYTES, (byte)0xAF);
        RANDOM.nextBytes(SMALL_DATA_INCOMPRESSIBLE_BYTES);

        Arrays.fill(LARGE_DATA_BYTES, (byte)0xAF);
        RANDOM.nextBytes(LARGE_DATA_INCOMPRESSIBLE_BYTES);
    }

    private static void runSingleTest(final byte[] data, final boolean srcNative, final boolean dstNative, final int dstInitSize,
                                      final boolean deflate) throws DataFormatException, ZstdException {
        final String key = "decompress";

        // setup buffers
        Buffer src = new Buffer(
                true, key, srcNative ? MemoryAllocator.AutoNative.INSTANCE : MemoryAllocator.UnPooledHeap.INSTANCE,
                (long)data.length, (long)data.length + 1L
        );
        // store test data
        src.writeBytes(data, 0, data.length);

        final Buffer dst = new Buffer(
                true, key, dstNative ? MemoryAllocator.AutoNative.INSTANCE : MemoryAllocator.UnPooledHeap.INSTANCE, (long)dstInitSize,
                (long)data.length + Math.max(128, (data.length >> 1))
        );
        try {
            // compress
            int wrote;
            if (deflate) {
                wrote = CompressionUtil.deflate(src, (int)src.getReadableBytes(), dst, (int)dst.getWritableBytes());
            } else {
                wrote = CompressionUtil.zstdCompress(src, (int)src.getReadableBytes(), dst, (int)dst.getWritableBytes());
            }

            // check indices
            assertEquals(data.length, src.getReaderIndex());
            assertEquals(data.length, src.getWriterIndex());
            assertEquals(0L, dst.getReaderIndex());
            assertEquals((long)wrote, dst.getWriterIndex());

            // did not overwrite data
            final byte[] test = new byte[data.length];
            src.readBytes(0L, test, 0, test.length);
            assertArrayEquals(data, test);

            // reset src to write back into
            src.decReferenceCount(key);
            src = null;
            src =  new Buffer(
                    true, key, srcNative ? MemoryAllocator.AutoNative.INSTANCE : MemoryAllocator.UnPooledHeap.INSTANCE,
                    (long)0L, (long)data.length + 1L
            );

            // store compressed data for check later
            final byte[] compressedData = new byte[wrote];
            dst.readBytes(0L, compressedData, 0, compressedData.length);

            // decompress
            final int compressedSize = wrote;
            if (deflate) {
                wrote = CompressionUtil.inflate(dst, (int)dst.getReadableBytes(), src, (int)src.getWritableBytes());
            } else {
                wrote = CompressionUtil.zstdDecompress(dst, (int)dst.getReadableBytes(), src, (int)src.getWritableBytes());
            }

            // check indices
            assertEquals(data.length, wrote);
            assertEquals(0L, src.getReaderIndex());
            assertEquals(data.length, src.getWriterIndex());
            assertEquals((long)compressedSize, dst.getReaderIndex());
            assertEquals((long)compressedSize, dst.getWriterIndex());

            // did not overwrite data
            final byte[] test2 = new byte[compressedSize];
            dst.readBytes(0L, test2, 0, test2.length);
            assertArrayEquals(compressedData, test2);

            // decompressed correctly
            final byte[] test3 = new byte[data.length];
            src.readBytes(0L, test3, 0, test3.length);
            assertArrayEquals(data, test3);
        } finally {
            try {
                src.decReferenceCount(key);
            } finally {
                dst.decReferenceCount(key);
            }
        }
    }

    private static void runTests(final boolean srcNative, final boolean dstNative, final boolean deflate) throws DataFormatException, ZstdException {
        runTests(srcNative, dstNative, deflate, 0.0);
        runTests(srcNative, dstNative, deflate, 0.1);
        runTests(srcNative, dstNative, deflate, 0.5);
        runTests(srcNative, dstNative, deflate, 1.0);
        runTests(srcNative, dstNative, deflate, 2.0);
    }

    private static void runTests(final boolean srcNative, final boolean dstNative, final boolean deflate,
                                 final double initSizeFactor) throws DataFormatException, ZstdException {
        runSingleTest(SMALL_DATA_BYTES, srcNative, dstNative, (int)(initSizeFactor * (double)SMALL_DATA_BYTES.length), deflate);
        runSingleTest(SMALL_DATA_INCOMPRESSIBLE_BYTES, srcNative, dstNative, (int)(initSizeFactor * (double)SMALL_DATA_INCOMPRESSIBLE_BYTES.length), deflate);
        runSingleTest(LARGE_DATA_BYTES, srcNative, dstNative, (int)(initSizeFactor * (double)LARGE_DATA_BYTES.length), deflate);
        runSingleTest(LARGE_DATA_INCOMPRESSIBLE_BYTES, srcNative, dstNative, (int)(initSizeFactor * (double)LARGE_DATA_INCOMPRESSIBLE_BYTES.length), deflate);
        runSingleBadTest(SMALL_DATA_BYTES, srcNative, dstNative, (int)(initSizeFactor * (double)SMALL_DATA_BYTES.length), deflate);
    }

    private static void runSingleBadTest(final byte[] data, final boolean srcNative, final boolean dstNative, final int dstInitSize,
                                         final boolean deflate) {
        final String key = "decompress";

        // setup buffers
        final Buffer src = new Buffer(
                true, key, srcNative ? MemoryAllocator.AutoNative.INSTANCE : MemoryAllocator.UnPooledHeap.INSTANCE,
                (long)data.length, (long)data.length + 1
        );
        // store test data
        src.writeBytes(data, 0, data.length);

        final Buffer dst = new Buffer(
                true, key, dstNative ? MemoryAllocator.AutoNative.INSTANCE : MemoryAllocator.UnPooledHeap.INSTANCE, (long)dstInitSize,
                (long)data.length + Math.max(128, (data.length >> 1))
        );
        try {
            // compress
            final int wrote;
            if (deflate) {
                wrote = CompressionUtil.deflate(src, (int)src.getReadableBytes(), dst, (int)dst.getWritableBytes());
            } else {
                wrote = CompressionUtil.zstdCompress(src, (int)src.getReadableBytes(), dst, (int)dst.getWritableBytes());
            }

            src.setIndices(0L, 0L);
            dst.ensureImmediatelyWritable(1L);

            // decompress with extra data
            if (deflate) {
                assertThrows(DataFormatException.class, () -> {
                    CompressionUtil.inflate(dst, (int)dst.getReadableBytes() + 1, src, (int)src.getWritableBytes());
                });
            } else {
                assertThrows(ZstdException.class, () -> {
                    CompressionUtil.zstdDecompress(dst, (int)dst.getReadableBytes() + 1, src, (int)src.getWritableBytes());
                });
            }

            src.setIndices(0L, 0L);
            dst.setIndices(0L, (long)wrote);

            // decompress with not enough data
            if (deflate) {
                assertThrows(DataFormatException.class, () -> {
                    CompressionUtil.inflate(dst, (int)dst.getReadableBytes() - 1, src, (int)src.getWritableBytes());
                });
            } else {
                assertThrows(ZstdException.class, () -> {
                    CompressionUtil.zstdDecompress(dst, (int)dst.getReadableBytes() - 1, src, (int)src.getWritableBytes());
                });
            }

            src.setIndices(0L, 0L);
            dst.setIndices(0L, (long)wrote);
        } finally {
            try {
                src.decReferenceCount(key);
            } finally {
                dst.decReferenceCount(key);
            }
        }
    }

    @Test
    public void testDeflateNonNative() throws DataFormatException, ZstdException {
        runTests(false, false, true);
    }

    @Test
    public void testDeflateSrcNative() throws DataFormatException, ZstdException {
        runTests(true, false, true);
    }

    @Test
    public void testDeflateDstNative() throws DataFormatException, ZstdException {
        runTests(false, true, true);
    }

    @Test
    public void testDeflateNative() throws DataFormatException, ZstdException {
        runTests(true, true, true);
    }

    @Test
    public void testZSTDNonNative() throws DataFormatException, ZstdException {
        runTests(false, false, false);
    }

    @Test
    public void testZSTDSrcNative() throws DataFormatException, ZstdException {
        runTests(true, false, false);
    }

    @Test
    public void testZSTDDstNative() throws DataFormatException, ZstdException {
        runTests(false, true, false);
    }

    @Test
    public void testZSTDNative() throws DataFormatException, ZstdException {
        runTests(true, true, false);
    }
}
