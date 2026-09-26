package ca.spottedleaf.ioutil.stream;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.BufferTest;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import ca.spottedleaf.ioutil.stream.channel.ChannelBufferInputStream;
import ca.spottedleaf.ioutil.stream.wrapped.BufferedBufferInputStream;
import ca.spottedleaf.ioutil.stream.wrapped.BufferedBufferOutputStream;
import ca.spottedleaf.ioutil.stream.wrapped.SimpleBufferInputStream;
import ca.spottedleaf.ioutil.stream.wrapped.SimpleBufferOutputStream;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public final class SimpleBufferStreamTest {

    private static final Random RDM = new Random(3L);

    static final int ARRAY_LEN = 8192 + 1;

    static final byte[] BYTES = new byte[ARRAY_LEN];
    static final short[] SHORTS = new short[ARRAY_LEN];
    static final char[] CHARS = new char[ARRAY_LEN];
    static final int[] INTS = new int[ARRAY_LEN];
    static final int[] MEDIUMS = new int[ARRAY_LEN];
    static final int[] MEDIUMS_UNSIGNED = new int[ARRAY_LEN];
    static final float[] FLOATS = new float[ARRAY_LEN];
    static final long[] LONGS = new long[ARRAY_LEN];
    static final double[] DOUBLES = new double[ARRAY_LEN];

    static {
        for (int i = 0; i < ARRAY_LEN; ++i) {
            BYTES[i] = (byte)RDM.nextInt();
            SHORTS[i] = (short)RDM.nextInt();
            CHARS[i] = (char)RDM.nextInt();
            INTS[i] = (int)RDM.nextInt();
            MEDIUMS[i] = ((RDM.nextInt() & 0xFFFFFF) << 8) >> 8;
            MEDIUMS_UNSIGNED[i] = RDM.nextInt() & 0xFFFFFF;
            FLOATS[i] = (float)RDM.nextDouble();
            LONGS[i] = RDM.nextLong();
            DOUBLES[i] = RDM.nextDouble();
        }
        for (int i = 0; i < ARRAY_LEN/10; ++i) {
            final int rdmIndex1 = RDM.nextInt(ARRAY_LEN);
            if (RDM.nextBoolean()) {
                FLOATS[rdmIndex1] = Float.NaN;
            } else {
                FLOATS[rdmIndex1] = RDM.nextBoolean() ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            }

            final int rdmIndex2 = RDM.nextInt(ARRAY_LEN);
            if (RDM.nextBoolean()) {
                DOUBLES[rdmIndex2] = Double.NaN;
            } else {
                DOUBLES[rdmIndex2] = RDM.nextBoolean() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }
        }
    }

    private static final int SMALL_BUFFER_LEN = 32;
    private static final int LARGE_BUFFER_LEN = 16*ARRAY_LEN;

    private static AbstractBufferOutputStream createSmallIntermediateOut(final Buffer buffer) {
        return new BufferedBufferOutputStream(
                new byte[SMALL_BUFFER_LEN],
                new BufferedBufferOutputStream(
                        new byte[LARGE_BUFFER_LEN],
                        createNoIntermediateOut(buffer)
                )
        );
    }

    private static AbstractBufferOutputStream createLargeIntermediateOut(final Buffer buffer) {
        return new BufferedBufferOutputStream(
                new byte[LARGE_BUFFER_LEN],
                new BufferedBufferOutputStream(
                        new byte[SMALL_BUFFER_LEN],
                        createNoIntermediateOut(buffer)
                )
        );
    }

    private static SimpleBufferOutputStream createNoIntermediateOut(final Buffer buffer) {
        return new SimpleBufferOutputStream(buffer);
    }

    private static AbstractBufferInputStream createSmallIntermediateIn(final Buffer buffer) {
        return new BufferedBufferInputStream(
                new byte[SMALL_BUFFER_LEN],
                new BufferedBufferInputStream(
                        new byte[LARGE_BUFFER_LEN], createNoIntermediateIn(buffer)
                )
        );
    }

    private static AbstractBufferInputStream createLargeIntermediateIn(final Buffer buffer) {
        return new BufferedBufferInputStream(
                new byte[LARGE_BUFFER_LEN],
                new BufferedBufferInputStream(
                        new byte[SMALL_BUFFER_LEN], createNoIntermediateIn(buffer)
                )
        );
    }

    private static SimpleBufferInputStream createNoIntermediateIn(final Buffer buffer) {
        return new SimpleBufferInputStream(buffer);
    }

    @Test
    public void testByte() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Byte.SIZE / 8;
        final byte expect = BYTES[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeByte(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readByte());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testByteNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Byte.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final byte expect = BYTES[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeByte(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readByte());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }


    @Test
    public void testShort() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Short.SIZE / 8;
        final short expect = SHORTS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeShort(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readShort());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testShortNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Short.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final short expect = SHORTS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeShort(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readShort());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testShortLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Short.SIZE / 8;
        final short expect = SHORTS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeShortLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readShortLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testShortLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Short.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final short expect = SHORTS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeShortLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readShortLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testShortNO() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Short.SIZE / 8;
        final short expect = SHORTS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeShortNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readShortNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testShortNONoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Short.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final short expect = SHORTS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeShortNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readShortNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testChar() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Character.SIZE / 8;
        final char expect = CHARS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeChar(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readChar());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testCharNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Character.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final char expect = CHARS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeChar(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readChar());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testCharLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Character.SIZE / 8;
        final char expect = CHARS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeCharLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readCharLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testCharLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Character.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final char expect = CHARS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeCharLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readCharLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testCharNO() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Character.SIZE / 8;
        final char expect = CHARS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeCharNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readCharNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testCharNONoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Character.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final char expect = CHARS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeCharNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readCharNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testUnsignedMedium() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int expect = INTS[0] & 0xFFFFFF;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeMedium(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readUnsignedMedium());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testUnsignedMediumNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = INTS[0] & 0xFFFFFF;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeMedium(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readUnsignedMedium());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testSignedMedium() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int expect = ((INTS[0] & 0xFFFFFF) | (1 << 23)) << 8 >> 8;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeMedium(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readSignedMedium());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testSignedMediumNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = ((INTS[0] & 0xFFFFFF) | (1 << 23)) << 8 >> 8;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeMedium(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readSignedMedium());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testUnsignedMediumLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int expect = INTS[0] & 0xFFFFFF;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeMediumLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readUnsignedMediumLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testUnsignedMediumLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = INTS[0] & 0xFFFFFF;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeMediumLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readUnsignedMediumLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testSignedMediumLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int expect = ((INTS[0] & 0xFFFFFF) | (1 << 23)) << 8 >> 8;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeMediumLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readSignedMediumLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testSignedMediumLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = 3;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = ((INTS[0] & 0xFFFFFF) | (1 << 23)) << 8 >> 8;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeMediumLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readSignedMediumLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testUnsignedVarInt() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_INT_BYTES;
        final int expect = -1;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeUnsignedVarInt(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readUnsignedVarInt());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testUnsignedVarIntNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_INT_BYTES;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = -1;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeUnsignedVarInt(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readUnsignedVarInt());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testSignedVarInt() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_INT_BYTES;
        final int expect = Integer.MIN_VALUE;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeSignedVarInt(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readSignedVarInt());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testSignedVarIntNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_INT_BYTES;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = Integer.MIN_VALUE;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeSignedVarInt(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readSignedVarInt());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testInt() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Integer.SIZE / 8;
        final int expect = INTS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeInt(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readInt());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testIntNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Integer.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = INTS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeInt(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readInt());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testIntLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Integer.SIZE / 8;
        final int expect = INTS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeIntLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readIntLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testIntLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Integer.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = INTS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeIntLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readIntLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testIntNO() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Integer.SIZE / 8;
        final int expect = INTS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeIntNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readIntNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testIntNONoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Integer.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final int expect = INTS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeIntNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readIntNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testFloat() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Float.SIZE / 8;
        final float expect = FLOATS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeFloat(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readFloat());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testFloatNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Float.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final float expect = FLOATS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeFloat(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readFloat());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testFloatLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Float.SIZE / 8;
        final float expect = FLOATS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeFloatLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readFloatLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testFloatLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Float.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final float expect = FLOATS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeFloatLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readFloatLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testFloatNO() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Float.SIZE / 8;
        final float expect = FLOATS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeFloatNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readFloatNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testFloatNONoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Float.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final float expect = FLOATS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeFloatNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readFloatNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testLong() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Long.SIZE / 8;
        final long expect = LONGS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeLong(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readLong());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testLongNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Long.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final long expect = LONGS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeLong(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readLong());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testLongLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Long.SIZE / 8;
        final long expect = LONGS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeLongLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readLongLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testLongLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Long.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final long expect = LONGS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeLongLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readLongLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testLongNO() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Long.SIZE / 8;
        final long expect = LONGS[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeLongNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readLongNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testLongNONoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Long.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final long expect = LONGS[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeLongNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readLongNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testDouble() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Double.SIZE / 8;
        final double expect = DOUBLES[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeDouble(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readDouble());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testDoubleNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Double.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final double expect = DOUBLES[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeDouble(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readDouble());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testDoubleLE() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Double.SIZE / 8;
        final double expect = DOUBLES[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeDoubleLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readDoubleLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testDoubleLENoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Double.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final double expect = DOUBLES[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeDoubleLE(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readDoubleLE());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testDoubleNO() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Double.SIZE / 8;
        final double expect = DOUBLES[0];

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeDoubleNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readDoubleNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testDoubleNONoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = Double.SIZE / 8;
        final int garbageToWrite = 32 - (unitLen - 1);
        final double expect = DOUBLES[0];

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeDoubleNO(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readDoubleNO());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }


    @Test
    public void testUnsignedVarLong() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_LONG_BYTES;
        final long expect = -1L;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeUnsignedVarLong(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readUnsignedVarLong());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testUnsignedVarLongNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_LONG_BYTES;
        final int garbageToWrite = 32 - (unitLen - 1);
        final long expect = -1L;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeUnsignedVarLong(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readUnsignedVarLong());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testSignedVarLong() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_LONG_BYTES;
        final long expect = Long.MIN_VALUE;

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.writeSignedVarLong(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createNoIntermediateIn(buffer)) {
            assertEquals(expect, in.readSignedVarLong());
            assertTrue(in.isEOF());
        }

        assertEquals((long)unitLen, buffer.getReaderIndex());
        assertEquals((long)unitLen, buffer.getWriterIndex());
    }

    @Test
    public void testSignedVarLongNoBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final int unitLen = (int)Buffer.MAX_VAR_LONG_BYTES;
        final int garbageToWrite = 32 - (unitLen - 1);
        final long expect = Long.MIN_VALUE;

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                out.write(0);
            }
            out.writeSignedVarLong(expect);
        }

        assertEquals(0L, buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (int i = 0; i < garbageToWrite; ++i) {
                in.read();
            }
            assertEquals(expect, in.readSignedVarLong());
            assertTrue(in.isEOF());
        }

        assertEquals((long)(unitLen + garbageToWrite), buffer.getReaderIndex());
        assertEquals((long)(unitLen + garbageToWrite), buffer.getWriterIndex());
    }

    @Test
    public void testStringBytes() throws IOException {
        final String[] strings = new String[] {
                "smaller than 32 bytes",
                "this string is longer than 32 bytes"
        };

        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (final String str : strings) {
                out.writeByte(str.length());
                out.writeBytes(str);
                out.flush();
            }
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (final String str : strings) {
                final int len = (int)in.readByte() & 0xFF;
                assertEquals(len, str.length());

                final byte[] bytes = new byte[len];
                in.readFully(bytes);

                assertEquals(str, new String(bytes, 0, 0, bytes.length));
            }
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testStringChars() throws IOException {
        final String[] strings = new String[] {
                "small 32 byte",
                "larger than 32 bytes"
        };

        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 128L);

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (final String str : strings) {
                out.writeByte(str.length());
                out.writeChars(str);
                out.flush();
            }
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (final String str : strings) {
                final int len = (int)in.readByte() & 0xFF;
                assertEquals(len, str.length());

                final char[] chars = new char[len];
                in.readFully(chars);

                assertEquals(str, new String(chars));
            }
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testStringLine() throws IOException {
        final String[] strings = new String[] {
                "large string with unix newline" + "\n",
                "large string with windows newline" + "\r\n",
                "large string with legacy mac newline" + "\r",
                "separator" + "\n",
                "\n",
                "\r\n",
                "\r",
                "string with no newline",
        };

        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 256L);

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            for (final String str : strings) {
                out.writeBytes(str);
                out.flush();
            }
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (final String str : strings) {
                assertEquals(str.strip(), in.readLine());
            }
            assertNull(in.readLine());
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testStringUTF() throws IOException {
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 1024L);

        final char[] largeFastCheckUTF = new char[Character.MAX_VALUE + 1];
        Arrays.fill(largeFastCheckUTF, 'a');

        final char[] largeSlowCheckUTF = new char[Character.MAX_VALUE / 3 + 1];
        Arrays.fill(largeSlowCheckUTF, (char)0x800);

        final String[] strings = new String[] {
                "this is a very large ascii only string",
                "small",

                "this is a very large not ascii only string" + String.valueOf((char)0),
                "this is a very large not ascii only string" + String.valueOf((char)0x80),
                "this is a very large not ascii only string" + String.valueOf((char)0) + String.valueOf((char)0x800),
                "this is a very large not ascii only string" + String.valueOf((char)0x80) + String.valueOf((char)0x800),

                "small" + String.valueOf((char)0),
                "small" + String.valueOf((char)0x80),
                "small" + String.valueOf((char)0) + String.valueOf((char)0x800),
                "small" + String.valueOf((char)0x80) + String.valueOf((char)0x800),
        };

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            assertThrows(UTFDataFormatException.class, () -> {
                out.writeUTF(new String(largeFastCheckUTF));
            });
            assertThrows(UTFDataFormatException.class, () -> {
                out.writeUTF(new String(largeSlowCheckUTF));
            });

            for (final String str : strings) {
                out.writeUTF(str);
                out.flush();
            }
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            for (final String str : strings) {
                assertEquals(str, in.readUTF());
            }
            assertTrue(in.isEOF());
            assertThrows(Exception.class, () -> {
                in.readUTF();
            });
        }
    }

    @Test
    public void testByteArray() throws IOException {
        final long bufferLen = (long)(Byte.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.write(BYTES);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final byte[] chk = new byte[ARRAY_LEN];
            in.readFully(chk);

            assertArrayEquals(BYTES, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testShortArray() throws IOException {
        final long bufferLen = (long)(Short.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeShorts(SHORTS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final short[] chk = new short[ARRAY_LEN];
            in.readFully(chk);

            assertArrayEquals(SHORTS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testShortArrayLE() throws IOException {
        final long bufferLen = (long)(Short.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeShortsLE(SHORTS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final short[] chk = new short[ARRAY_LEN];
            in.readFullyLE(chk);

            assertArrayEquals(SHORTS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testShortArrayNO() throws IOException {
        final long bufferLen = (long)(Short.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeShortsNO(SHORTS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final short[] chk = new short[ARRAY_LEN];
            in.readFullyNO(chk);

            assertArrayEquals(SHORTS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testCharArray() throws IOException {
        final long bufferLen = (long)(Character.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeChars(CHARS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final char[] chk = new char[ARRAY_LEN];
            in.readFully(chk);

            assertArrayEquals(CHARS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testCharArrayLE() throws IOException {
        final long bufferLen = (long)(Character.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeCharsLE(CHARS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final char[] chk = new char[ARRAY_LEN];
            in.readFullyLE(chk);

            assertArrayEquals(CHARS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testCharArrayNO() throws IOException {
        final long bufferLen = (long)(Character.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeCharsNO(CHARS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final char[] chk = new char[ARRAY_LEN];
            in.readFullyNO(chk);

            assertArrayEquals(CHARS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testIntArray() throws IOException {
        final long bufferLen = (long)(Integer.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeInts(INTS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int[] chk = new int[ARRAY_LEN];
            in.readFully(chk);

            assertArrayEquals(INTS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testIntArrayLE() throws IOException {
        final long bufferLen = (long)(Integer.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeIntsLE(INTS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int[] chk = new int[ARRAY_LEN];
            in.readFullyLE(chk);

            assertArrayEquals(INTS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testIntArrayNO() throws IOException {
        final long bufferLen = (long)(Integer.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeIntsNO(INTS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int[] chk = new int[ARRAY_LEN];
            in.readFullyNO(chk);

            assertArrayEquals(INTS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testFloatArray() throws IOException {
        final long bufferLen = (long)(Float.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeFloats(FLOATS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final float[] chk = new float[ARRAY_LEN];
            in.readFully(chk);

            assertArrayEquals(FLOATS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testFloatArrayLE() throws IOException {
        final long bufferLen = (long)(Float.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeFloatsLE(FLOATS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final float[] chk = new float[ARRAY_LEN];
            in.readFullyLE(chk);

            assertArrayEquals(FLOATS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testFloatArrayNO() throws IOException {
        final long bufferLen = (long)(Float.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeFloatsNO(FLOATS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final float[] chk = new float[ARRAY_LEN];
            in.readFullyNO(chk);

            assertArrayEquals(FLOATS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testLongArray() throws IOException {
        final long bufferLen = (long)(Long.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeLongs(LONGS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final long[] chk = new long[ARRAY_LEN];
            in.readFully(chk);

            assertArrayEquals(LONGS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testLongArrayLE() throws IOException {
        final long bufferLen = (long)(Long.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeLongsLE(LONGS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final long[] chk = new long[ARRAY_LEN];
            in.readFullyLE(chk);

            assertArrayEquals(LONGS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testLongArrayNO() throws IOException {
        final long bufferLen = (long)(Long.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeLongsNO(LONGS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final long[] chk = new long[ARRAY_LEN];
            in.readFullyNO(chk);

            assertArrayEquals(LONGS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testDoubleArray() throws IOException {
        final long bufferLen = (long)(Double.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeDoubles(DOUBLES);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final double[] chk = new double[ARRAY_LEN];
            in.readFully(chk);

            assertArrayEquals(DOUBLES, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testDoubleArrayLE() throws IOException {
        final long bufferLen = (long)(Double.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeDoublesLE(DOUBLES);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final double[] chk = new double[ARRAY_LEN];
            in.readFullyLE(chk);

            assertArrayEquals(DOUBLES, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testDoubleArrayNO() throws IOException {
        final long bufferLen = (long)(Double.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeDoublesNO(DOUBLES);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final double[] chk = new double[ARRAY_LEN];
            in.readFullyNO(chk);

            assertArrayEquals(DOUBLES, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testUnsignedMediumArray() throws IOException {
        final long bufferLen = (long)(Double.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeMediums(MEDIUMS_UNSIGNED);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int[] chk = new int[ARRAY_LEN];
            in.readUnsignedMediumsFully(chk);

            assertArrayEquals(MEDIUMS_UNSIGNED, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testSignedMediumArray() throws IOException {
        final long bufferLen = (long)(Double.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeMediums(MEDIUMS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int[] chk = new int[ARRAY_LEN];
            in.readSignedMediumsFully(chk);

            assertArrayEquals(MEDIUMS, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testUnsignedMediumArrayLE() throws IOException {
        final long bufferLen = (long)(Double.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeMediumsLE(MEDIUMS_UNSIGNED);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int[] chk = new int[ARRAY_LEN];
            in.readUnsignedMediumsFullyLE(chk);

            assertArrayEquals(MEDIUMS_UNSIGNED, chk);
            assertTrue(in.isEOF());
        }
    }

    @Test
    public void testSignedMediumArrayLE() throws IOException {
        final long bufferLen = (long)(Double.SIZE / 8) * (ARRAY_LEN);
        final Buffer buffer = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, bufferLen, bufferLen);


        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.writeMediumsLE(MEDIUMS);
        }

        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int[] chk = new int[ARRAY_LEN];
            in.readSignedMediumsFullyLE(chk);

            assertArrayEquals(MEDIUMS, chk);
            assertTrue(in.isEOF());
        }
    }

    private static void chk(final BufferTest.DummyChannel channel, final long channelPos, final byte[] ref, final int off, final int len) throws IOException {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

        final byte[] val = new byte[len];

        channel.position(channelPos);
        try (final ChannelBufferInputStream in = new ChannelBufferInputStream(buffer, channel, false)) {
            in.readFully(val);
        }

        assertArrayEquals(val, Arrays.copyOfRange(ref, off, off + len));
    }

    @Test
    public void testChannel() throws IOException {
        try (final BufferTest.DummyChannel channel = new BufferTest.DummyChannel(ByteBuffer.allocate(ARRAY_LEN*16))) {
            final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);

            // write full
            buffer.writeBytes(BYTES, 0, BYTES.length);

            try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
                while (0L != in.read(channel));

                assertEquals(BYTES.length, channel.position());
                assertEquals(BYTES.length, channel.size());
            }

            chk(channel, 0L, BYTES, 0, BYTES.length);

            // write 64 bytes at the 64 byte offset
            channel.position(64L);
            buffer.clear();
            buffer.writeBytes(BYTES, 64, 64);
            try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
                in.read(channel, 64L);
                assertThrows(Exception.class, () -> {
                    in.read(channel, 1L);
                });
                assertEquals(64L + 64L, channel.position());
            }
            chk(channel, 64L, BYTES, 64, 64);


            // write full
            buffer.clear();
            channel.position(10L);
            buffer.writeBytes(BYTES, 0, BYTES.length);
            try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
                for (long off = 0L;;) {
                    final long written = in.readFilePos(channel, off);
                    off += written;
                    if (written == 0L) {
                        break;
                    }
                }

                assertEquals(10L, channel.position());
                assertEquals(BYTES.length, channel.size());
            }
            chk(channel, 0L, BYTES, 0, BYTES.length);

            // write 64 bytes at the 64 byte offset
            channel.position(5L);
            buffer.clear();
            buffer.writeBytes(BYTES, 64, 64);
            try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
                in.readFilePos(channel, 64L, 64L);
                assertThrows(Exception.class, () -> {
                    in.readFilePos(channel, 65L, 1L);
                });
                assertEquals(5L, channel.position());
            }
            chk(channel, 64L, BYTES, 64, 64);

            channel.position(0L);
            buffer.clear();
            buffer.writeBytes(BYTES, 0, BYTES.length);
            buffer.readIntoFilePos(channel, 0L, (long)BYTES.length);
            buffer.clearAndFill((byte)0);

            try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
                final byte[] tmp = new byte[BYTES.length];
                final int read = Math.toIntExact(out.write(channel));
                out.flush();

                assertEquals(read, channel.position());
                assertEquals(0L, buffer.getReaderIndex());
                assertEquals(read, buffer.getWriterIndex());

                buffer.readBytes(tmp, 0, read);
                assertArrayEquals(Arrays.copyOfRange(BYTES, 0, read), Arrays.copyOfRange(tmp, 0, read));

                out.write(channel, 128L);
                out.flush();
                assertEquals(read + 128L, channel.position());
                assertEquals(0L + read, buffer.getReaderIndex());
                assertEquals(read + 128L, buffer.getWriterIndex());

                buffer.readBytes(tmp, 0, 128);
                assertArrayEquals(Arrays.copyOfRange(BYTES, read, read + 128), Arrays.copyOfRange(tmp, 0, 128));


                final int read2 = Math.toIntExact(out.writeFilePos(channel, 128L));
                out.flush();
                assertEquals(read + 128L, channel.position());
                assertEquals(0L + read + 128L, buffer.getReaderIndex());
                assertEquals(read + 128L + read2, buffer.getWriterIndex());

                buffer.readBytes(tmp, 0, read2);
                assertArrayEquals(Arrays.copyOfRange(BYTES, 128, 128 + read2), Arrays.copyOfRange(tmp, 0, read2));

                out.writeFilePos(channel, 256L, 1024L);
                out.flush();
                assertEquals(read + 128L, channel.position());
                assertEquals(0L + read + 128L + read2, buffer.getReaderIndex());
                assertEquals(read + 128L + read2 + 1024L, buffer.getWriterIndex());

                buffer.readBytes(tmp, 0, 1024);
                assertArrayEquals(Arrays.copyOfRange(BYTES, 256, 256 + 1024), Arrays.copyOfRange(tmp, 0, 1024));
            }
        }
    }

    @Test
    public void testBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);

        final Buffer buffer2 = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);
        buffer2.writeBytes(BYTES, 0, BYTES.length);

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            final int written = Math.toIntExact(out.write(buffer2));
            out.flush();

            final byte[] chk1 = new byte[written];
            buffer.readBytes(chk1, 0, chk1.length);

            assertArrayEquals(Arrays.copyOfRange(BYTES, 0, written), chk1);
            assertEquals(0L, buffer.getReadableBytes());

            out.write(buffer2, 256);
            out.flush();
            final byte[] chk2 = new byte[256];
            buffer.readBytes(chk2, 0, 256);

            assertArrayEquals(Arrays.copyOfRange(BYTES, written, written + 256), chk2);
            assertEquals(0L, buffer.getReadableBytes());
        }

        buffer.clearAndFill((byte)0);
        buffer2.clearAndFill((byte)0);

        buffer.writeBytes(BYTES, 0, BYTES.length);
        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int read = Math.toIntExact(in.read(buffer2));
            final byte[] chk1 = new byte[read];
            buffer2.readBytes(chk1, 0, chk1.length);

            assertArrayEquals(Arrays.copyOfRange(BYTES, 0, read), chk1);
            assertEquals(0L, buffer2.getReadableBytes());

            in.read(buffer2, 256L);
            final byte[] chk2 = new byte[256];
            buffer2.readBytes(chk2, 0, 256);

            assertArrayEquals(Arrays.copyOfRange(BYTES, read, read + 256), chk2);
            assertEquals(0L, buffer.getReadableBytes());
        }
    }

    @Test
    public void testByteBuffer() throws IOException {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);

        final ByteBuffer buffer2 = ByteBuffer.allocate(ARRAY_LEN*16);
        buffer2.put(BYTES);
        buffer2.flip();

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            final int written = Math.toIntExact(out.write(buffer2));
            out.flush();

            final byte[] chk1 = new byte[written];
            buffer.readBytes(chk1, 0, chk1.length);

            assertArrayEquals(Arrays.copyOfRange(BYTES, 0, written), chk1);
            assertEquals(0L, buffer.getReadableBytes());

            out.write(buffer2, 256);
            out.flush();
            final byte[] chk2 = new byte[256];
            buffer.readBytes(chk2, 0, 256);

            assertArrayEquals(Arrays.copyOfRange(BYTES, written, written + 256), chk2);
            assertEquals(0L, buffer.getReadableBytes());
        }

        buffer.clearAndFill((byte)0);
        buffer2.clear();
        for (int i = 0; i < buffer2.limit(); ++i) {
            buffer2.put(0, (byte)0);
        }

        buffer.writeBytes(BYTES, 0, BYTES.length);
        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int read = Math.toIntExact(in.read(buffer2));
            final byte[] chk1 = new byte[read];
            buffer2.flip();
            buffer2.get(chk1, 0, chk1.length);

            assertArrayEquals(Arrays.copyOfRange(BYTES, 0, read), chk1);
            assertEquals(0L, buffer2.remaining());

            buffer2.clear();
            in.read(buffer2, 256);
            final byte[] chk2 = new byte[256];
            buffer2.flip();
            buffer2.get(chk2, 0, 256);

            assertArrayEquals(Arrays.copyOfRange(BYTES, read, read + 256), chk2);
            assertEquals(0L, buffer.getReadableBytes());
        }
    }

    private static void chk(final byte[] ref, final int roff, final MemorySegment chk, final long coff, final long len) {
        final byte[] tmp = new byte[Math.toIntExact(len)];

        MemorySegment.copy(chk, ValueLayout.JAVA_BYTE, coff, tmp, 0, tmp.length);

        assertArrayEquals(Arrays.copyOfRange(ref, roff, roff + Math.toIntExact(len)), tmp);
    }

    @Test
    public void testMemorySegment() throws IOException {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L);

        final MemorySegment segment = MemoryAllocator.UnPooledHeap.INSTANCE.findMemory((long)ARRAY_LEN*16L, (long)ARRAY_LEN*16L).getSegment();
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0L, BYTES, 0, BYTES.length);

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer)) {
            out.write(segment, 128L, 64L);
            out.flush();

            final byte[] tmp = new byte[64];
            buffer.readBytes(tmp, 0, tmp.length);

            assertEquals(0L, buffer.getReadableBytes());

            assertArrayEquals(Arrays.copyOfRange(BYTES, 128, 128 + 64), tmp);
        }


        buffer.clearAndFill((byte)0);
        segment.fill((byte)0);

        buffer.writeBytes(BYTES, 0, BYTES.length);
        try (final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            final int read = Math.toIntExact(in.read(segment, 0L));
            chk(BYTES, 0, segment, 0L, (long)read);

            in.read(segment, 1L, 256L);
            chk(BYTES, read, segment, 1L, 256L);
        }
    }

    @Test
    public void testStreamRead() throws IOException {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)BYTES.length, (long)BYTES.length);
        final Buffer buffer2 = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)BYTES.length, (long)BYTES.length);

        buffer.writeBytes(BYTES, 0, BYTES.length);

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer2);
             final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            assertEquals(BYTES.length, in.availableLong());
            in.read(out, BYTES.length);
            out.flush();

            assertTrue(in.isEOF());
            assertEquals(0L, buffer.getReadableBytes());
        }

        final byte[] tmp = new byte[BYTES.length];
        buffer2.readBytes(tmp, 0, tmp.length);

        assertArrayEquals(BYTES, tmp);
    }

    @Test
    public void testStreamWrite() throws IOException {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)BYTES.length, (long)BYTES.length);
        final Buffer buffer2 = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)BYTES.length, (long)BYTES.length);

        buffer.writeBytes(BYTES, 0, BYTES.length);

        try (final AbstractBufferOutputStream out = createSmallIntermediateOut(buffer2);
             final AbstractBufferInputStream in = createSmallIntermediateIn(buffer)) {
            assertEquals(BYTES.length, in.availableLong());
            out.write(in, BYTES.length);
            out.flush();

            assertTrue(in.isEOF());
            assertEquals(0L, buffer.getReadableBytes());
        }

        final byte[] tmp = new byte[BYTES.length];
        buffer2.readBytes(tmp, 0, tmp.length);

        assertArrayEquals(BYTES, tmp);
    }

    @Test
    public void testLimitedOut() throws IOException {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, (long)BYTES.length, (long)BYTES.length);

        try (final AbstractBufferOutputStream out = createNoIntermediateOut(buffer)) {
            out.write(BYTES);

            assertThrows(Exception.class, () -> {
                out.writeByte((byte)1);
            });
        }
    }
}
