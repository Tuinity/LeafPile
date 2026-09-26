package ca.spottedleaf.ioutil.stream;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import ca.spottedleaf.ioutil.stream.wrapped.SimpleBufferInputStream;
import ca.spottedleaf.ioutil.stream.wrapped.SimpleBufferOutputStream;
import ca.spottedleaf.ioutil.stream.zstd.ZSTDBufferInputStream;
import ca.spottedleaf.ioutil.stream.zstd.ZSTDBufferOutputStream;
import ca.spottedleaf.ioutil.util.BufferReference;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static ca.spottedleaf.ioutil.stream.SimpleBufferStreamTest.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ZSTDStreamTest {

    @Test
    public void testZSTDNoWrap() throws IOException {
        final Buffer compressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);
        final Buffer smallBuffer1 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer uncompressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);

        final List<BufferReference> references = Arrays.asList(
                new BufferReference(compressed, "test"),
                new BufferReference(smallBuffer1, "test"),
                new BufferReference(uncompressed, "test")
        );
        try {
            try (final ZstdCompressCtx ctx = new ZstdCompressCtx();
                 final ZSTDBufferOutputStream out = new ZSTDBufferOutputStream(compressed, smallBuffer1, ctx, null, null)) {
                out.write(BYTES);
            }

            smallBuffer1.clearAndFill((byte)0);

            try (final ZstdDecompressCtx ctx = new ZstdDecompressCtx();
                 final ZSTDBufferInputStream in = new ZSTDBufferInputStream(smallBuffer1, compressed, ctx, null, null)) {
                assertEquals(compressed.getReadableBytes(), in.availableLong());
                while (0L != in.read(uncompressed));
            }

            final byte[] tmp = new byte[Math.toIntExact(uncompressed.getReadableBytes())];
            uncompressed.readBytes(tmp, 0, tmp.length);
            assertArrayEquals(BYTES, tmp);
        } finally {
            BufferReference.releaseAll(references);
        }
    }

    @Test
    public void testZSTDNoWrapLargeBuffer() throws IOException {
        final Buffer compressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);
        final Buffer largeBuffer1 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);
        final Buffer uncompressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);

        final List<BufferReference> references = Arrays.asList(
                new BufferReference(compressed, "test"),
                new BufferReference(largeBuffer1, "test"),
                new BufferReference(uncompressed, "test")
        );
        try {
            try (final ZstdCompressCtx ctx = new ZstdCompressCtx();
                 final ZSTDBufferOutputStream out = new ZSTDBufferOutputStream(compressed, largeBuffer1, ctx, null, null)) {
                out.write(BYTES);
            }

            largeBuffer1.clearAndFill((byte)0);

            try (final ZstdDecompressCtx ctx = new ZstdDecompressCtx();
                 final ZSTDBufferInputStream in = new ZSTDBufferInputStream(largeBuffer1, compressed, ctx, null, null)) {
                assertEquals(compressed.getReadableBytes(), in.availableLong());
                while (0L != in.read(uncompressed));
            }

            final byte[] tmp = new byte[Math.toIntExact(uncompressed.getReadableBytes())];
            uncompressed.readBytes(tmp, 0, tmp.length);
            assertArrayEquals(BYTES, tmp);
        } finally {
            BufferReference.releaseAll(references);
        }
    }

    @Test
    public void testZSTDWithWrap() throws IOException {
        final Buffer compressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);
        final Buffer smallBuffer1 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer smallBuffer2 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer uncompressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);

        final List<BufferReference> references = Arrays.asList(
                new BufferReference(compressed, "test"),
                new BufferReference(smallBuffer1, "test"),
                new BufferReference(uncompressed, "test")
        );
        try {
            try (final ZstdCompressCtx ctx = new ZstdCompressCtx();
                 final ZSTDBufferOutputStream out = new ZSTDBufferOutputStream(smallBuffer2, smallBuffer1, ctx, null, new SimpleBufferOutputStream(compressed))) {
                out.write(BYTES);
            }

            smallBuffer1.clearAndFill((byte)0);
            smallBuffer2.clearAndFill((byte)0);

            try (final ZstdDecompressCtx ctx = new ZstdDecompressCtx();
                 final ZSTDBufferInputStream in = new ZSTDBufferInputStream(smallBuffer1, smallBuffer2, ctx, null, new SimpleBufferInputStream(compressed))) {
                assertEquals(compressed.getReadableBytes(), in.availableLong());
                while (0L != in.read(uncompressed));
            }

            final byte[] tmp = new byte[Math.toIntExact(uncompressed.getReadableBytes())];
            uncompressed.readBytes(tmp, 0, tmp.length);
            assertArrayEquals(BYTES, tmp);
        } finally {
            BufferReference.releaseAll(references);
        }
    }

    @Test
    public void testZSTDWithWrapLargeBuffer() throws IOException {
        final Buffer compressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);
        final Buffer largeBuffer1 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);
        final Buffer largeBuffer2 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);
        final Buffer uncompressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);

        final List<BufferReference> references = Arrays.asList(
                new BufferReference(compressed, "test"),
                new BufferReference(largeBuffer1, "test"),
                new BufferReference(uncompressed, "test")
        );
        try {
            try (final ZstdCompressCtx ctx = new ZstdCompressCtx();
                 final ZSTDBufferOutputStream out = new ZSTDBufferOutputStream(largeBuffer2, largeBuffer1, ctx, null, new SimpleBufferOutputStream(compressed))) {
                out.write(BYTES);
            }

            largeBuffer1.clearAndFill((byte)0);
            largeBuffer2.clearAndFill((byte)0);

            try (final ZstdDecompressCtx ctx = new ZstdDecompressCtx();
                 final ZSTDBufferInputStream in = new ZSTDBufferInputStream(largeBuffer1, largeBuffer2, ctx, null, new SimpleBufferInputStream(compressed))) {
                assertEquals(compressed.getReadableBytes(), in.availableLong());
                while (0L != in.read(uncompressed));
            }

            final byte[] tmp = new byte[Math.toIntExact(uncompressed.getReadableBytes())];
            uncompressed.readBytes(tmp, 0, tmp.length);
            assertArrayEquals(BYTES, tmp);
        } finally {
            BufferReference.releaseAll(references);
        }
    }

    @Test
    public void testZSTDFlushNoWrap() throws IOException {
        final Buffer compressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);
        final Buffer smallBuffer1 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer uncompressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);

        final List<BufferReference> references = Arrays.asList(
                new BufferReference(compressed, "test"),
                new BufferReference(smallBuffer1, "test"),
                new BufferReference(uncompressed, "test")
        );

        final byte[] toWrite = Arrays.copyOf(BYTES, 128);

        try {
            try (final ZstdCompressCtx ctx = new ZstdCompressCtx();
                 final ZSTDBufferOutputStream out = new ZSTDBufferOutputStream(compressed, smallBuffer1, ctx, null, null)) {
                long lastWrittenLen = 0L;
                for (final byte b : toWrite) {
                    out.writeByte(b);
                    out.flush();
                    assertTrue(compressed.getReadableBytes() > lastWrittenLen);
                    lastWrittenLen = compressed.getReadableBytes();
                }
            }

            smallBuffer1.clearAndFill((byte)0);

            try (final ZstdDecompressCtx ctx = new ZstdDecompressCtx();
                 final ZSTDBufferInputStream in = new ZSTDBufferInputStream(smallBuffer1, compressed, ctx, null, null)) {
                assertEquals(compressed.getReadableBytes(), in.availableLong());
                while (0L != in.read(uncompressed));
            }

            final byte[] tmp = new byte[Math.toIntExact(uncompressed.getReadableBytes())];
            uncompressed.readBytes(tmp, 0, tmp.length);
            assertArrayEquals(toWrite, tmp);
        } finally {
            BufferReference.releaseAll(references);
        }
    }

    @Test
    public void testZSTDFlushWithWrap() throws IOException {
        final Buffer compressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);
        final Buffer smallBuffer1 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer smallBuffer2 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer uncompressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);

        final List<BufferReference> references = Arrays.asList(
                new BufferReference(compressed, "test"),
                new BufferReference(smallBuffer1, "test"),
                new BufferReference(uncompressed, "test")
        );

        final byte[] toWrite = Arrays.copyOf(BYTES, 128);

        try {
            try (final ZstdCompressCtx ctx = new ZstdCompressCtx();
                 final ZSTDBufferOutputStream out = new ZSTDBufferOutputStream(smallBuffer2, smallBuffer1, ctx, null, new SimpleBufferOutputStream(compressed))) {
                long lastWrittenLen = 0L;
                for (final byte b : toWrite) {
                    out.writeByte(b);
                    out.flush();
                    assertTrue(compressed.getReadableBytes() > lastWrittenLen);
                    lastWrittenLen = compressed.getReadableBytes();
                }
            }

            smallBuffer1.clearAndFill((byte)0);

            try (final ZstdDecompressCtx ctx = new ZstdDecompressCtx();
                 final ZSTDBufferInputStream in = new ZSTDBufferInputStream(smallBuffer1, compressed, ctx, null, null)) {
                assertEquals(compressed.getReadableBytes(), in.availableLong());
                while (0L != in.read(uncompressed));
            }

            final byte[] tmp = new byte[Math.toIntExact(uncompressed.getReadableBytes())];
            uncompressed.readBytes(tmp, 0, tmp.length);
            assertArrayEquals(toWrite, tmp);
        } finally {
            BufferReference.releaseAll(references);
        }
    }

    @Test
    public void testZSTDCompressible() throws IOException {
        final Buffer compressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);
        final Buffer smallBuffer1 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer smallBuffer2 = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);
        final Buffer uncompressed = new Buffer(true, "test", MemoryAllocator.UnPooledNative.INSTANCE, 0L, (long)ARRAY_LEN*16L);

        final List<BufferReference> references = Arrays.asList(
                new BufferReference(compressed, "test"),
                new BufferReference(smallBuffer1, "test"),
                new BufferReference(uncompressed, "test")
        );
        final byte[] toWrite = new byte[BYTES.length];
        Arrays.fill(toWrite, (byte)-1);
        try {
            try (final ZstdCompressCtx ctx = new ZstdCompressCtx();
                 final ZSTDBufferOutputStream out = new ZSTDBufferOutputStream(smallBuffer2, smallBuffer1, ctx, null, new SimpleBufferOutputStream(compressed))) {
                out.write(toWrite);
            }

            smallBuffer1.clearAndFill((byte)0);

            try (final ZstdDecompressCtx ctx = new ZstdDecompressCtx();
                 final ZSTDBufferInputStream in = new ZSTDBufferInputStream(smallBuffer1, smallBuffer2, ctx, null, new SimpleBufferInputStream(compressed))) {
                assertEquals(compressed.getReadableBytes(), in.availableLong());
                while (0L != in.read(uncompressed));
            }

            final byte[] tmp = new byte[Math.toIntExact(uncompressed.getReadableBytes())];
            uncompressed.readBytes(tmp, 0, tmp.length);
            assertArrayEquals(toWrite, tmp);
        } finally {
            BufferReference.releaseAll(references);
        }
    }
}
