package ca.spottedleaf.ioutil.buffer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public final class BufferTest {

    private static final Random RDM = new Random(1L);

    private static final byte EMPTY_INIT = (byte)0xAA;

    private static final int VALUES_PER_TEST = 1024;
    private static final int END_GARBAGE_BYTES = 16;
    private static final byte[] END_GARBAGE = new byte[END_GARBAGE_BYTES];
    static {
        Arrays.fill(END_GARBAGE, EMPTY_INIT);
    }

    private static final byte[] FOR_BYTES = new byte[VALUES_PER_TEST];
    static {
        RDM.nextBytes(FOR_BYTES);
    }

    @Test
    public void testReadByte() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Byte.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeBytes(FOR_BYTES, 0, FOR_BYTES.length);
        assertEquals(VALUES_PER_TEST * Byte.BYTES, buffer.getReadableBytes());

        final byte[] readBack = new byte[VALUES_PER_TEST];

        Arrays.fill(readBack, (byte)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readByte();
        }
        assertArrayEquals(FOR_BYTES, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (byte)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readByte((long)i * Byte.BYTES);
        }
        assertArrayEquals(FOR_BYTES, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (byte)1);
        buffer.readBytes(readBack, 0, readBack.length);
        assertArrayEquals(FOR_BYTES, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (byte)1);
        buffer.readBytes(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_BYTES, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_BYTES), 0L, 0L, VALUES_PER_TEST * Byte.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Byte.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteByte() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Byte.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_BYTES.length; ++i) {
            buffer.writeByte(FOR_BYTES[i]);
        }
        assertEquals(VALUES_PER_TEST * Byte.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_BYTES), 0L, 0L, VALUES_PER_TEST * Byte.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Byte.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteByte() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Byte.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_BYTES.length; ++i) {
            buffer.writeByte((long)i, FOR_BYTES[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_BYTES), 0L, 0L, VALUES_PER_TEST * Byte.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Byte.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteBytes() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Byte.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeBytes(FOR_BYTES, 0, FOR_BYTES.length);
        assertEquals(VALUES_PER_TEST * Byte.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_BYTES), 0L, 0L, VALUES_PER_TEST * Byte.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Byte.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteBytes() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Byte.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeBytes(0L, FOR_BYTES, 0, FOR_BYTES.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_BYTES), 0L, 0L, VALUES_PER_TEST * Byte.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Byte.BYTES, END_GARBAGE_BYTES);
    }

    private static final short[] FOR_SHORT = new short[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_SHORT[i] = (short)RDM.nextLong();
        }
    }
    private static final byte[] FOR_SHORT_NO = new byte[VALUES_PER_TEST * Short.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_SHORT_NO);
        buffer.order(ByteOrder.nativeOrder());
        for (final short value : FOR_SHORT) {
            buffer.putShort(value);
        }
    }
    private static final byte[] FOR_SHORT_BE = new byte[VALUES_PER_TEST * Short.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_SHORT_BE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (final short value : FOR_SHORT) {
            buffer.putShort(value);
        }
    }
    private static final byte[] FOR_SHORT_LE = new byte[VALUES_PER_TEST * Short.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_SHORT_LE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (final short value : FOR_SHORT) {
            buffer.putShort(value);
        }
    }

    @Test
    public void testReadShortNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsNO(FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        final short[] readBack = new short[VALUES_PER_TEST];

        Arrays.fill(readBack, (short)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readShortNO();
        }
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (short)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readShortNO((long)i * Short.BYTES);
        }
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (short)1);
        buffer.readShortsNO(readBack, 0, readBack.length);
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (short)1);
        buffer.readShortsNO(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_NO), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadShortBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsBE(FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        final short[] readBack = new short[VALUES_PER_TEST];

        Arrays.fill(readBack, (short)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readShortBE();
        }
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (short)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readShortBE((long)i * Short.BYTES);
        }
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (short)1);
        buffer.readShortsBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (short)1);
        buffer.readShortsBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_BE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadShortLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsLE(FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        final short[] readBack = new short[VALUES_PER_TEST];

        Arrays.fill(readBack, (short)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readShortLE();
        }
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (short)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readShortLE((long)i * Short.BYTES);
        }
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (short)1);
        buffer.readShortsLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (short)1);
        buffer.readShortsLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_SHORT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_LE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteShortNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_SHORT.length; ++i) {
            buffer.writeShortNO(FOR_SHORT[i]);
        }
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_NO), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteShortNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_SHORT.length; ++i) {
            buffer.writeShortNO((long)i * (long)Short.BYTES, FOR_SHORT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_NO), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteShortsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsNO(FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_NO), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteShortsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsNO(0L, FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_NO), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteShortBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_SHORT.length; ++i) {
            buffer.writeShortBE(FOR_SHORT[i]);
        }
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_BE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteShortBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_SHORT.length; ++i) {
            buffer.writeShortBE((long)i * (long)Short.BYTES, FOR_SHORT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_BE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteShortsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsBE(FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_BE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteShortsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsBE(0L, FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_BE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteShortLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_SHORT.length; ++i) {
            buffer.writeShortLE(FOR_SHORT[i]);
        }
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_LE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteShortLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_SHORT.length; ++i) {
            buffer.writeShortLE((long)i * (long)Short.BYTES, FOR_SHORT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_LE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteShortsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsLE(FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(VALUES_PER_TEST * Short.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_LE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteShortsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Short.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeShortsLE(0L, FOR_SHORT, 0, FOR_SHORT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_SHORT_LE), 0L, 0L, VALUES_PER_TEST * Short.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Short.BYTES, END_GARBAGE_BYTES);
    }
    private static final char[] FOR_CHAR = new char[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_CHAR[i] = (char)RDM.nextLong();
        }
    }
    private static final byte[] FOR_CHAR_NO = new byte[VALUES_PER_TEST * Character.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_CHAR_NO);
        buffer.order(ByteOrder.nativeOrder());
        for (final char value : FOR_CHAR) {
            buffer.putChar(value);
        }
    }
    private static final byte[] FOR_CHAR_BE = new byte[VALUES_PER_TEST * Character.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_CHAR_BE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (final char value : FOR_CHAR) {
            buffer.putChar(value);
        }
    }
    private static final byte[] FOR_CHAR_LE = new byte[VALUES_PER_TEST * Character.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_CHAR_LE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (final char value : FOR_CHAR) {
            buffer.putChar(value);
        }
    }

    @Test
    public void testReadCharNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsNO(FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        final char[] readBack = new char[VALUES_PER_TEST];

        Arrays.fill(readBack, (char)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readCharNO();
        }
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (char)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readCharNO((long)i * Character.BYTES);
        }
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (char)1);
        buffer.readCharsNO(readBack, 0, readBack.length);
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (char)1);
        buffer.readCharsNO(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_NO), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadCharBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsBE(FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        final char[] readBack = new char[VALUES_PER_TEST];

        Arrays.fill(readBack, (char)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readCharBE();
        }
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (char)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readCharBE((long)i * Character.BYTES);
        }
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (char)1);
        buffer.readCharsBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (char)1);
        buffer.readCharsBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_BE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadCharLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsLE(FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        final char[] readBack = new char[VALUES_PER_TEST];

        Arrays.fill(readBack, (char)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readCharLE();
        }
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (char)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readCharLE((long)i * Character.BYTES);
        }
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (char)1);
        buffer.readCharsLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (char)1);
        buffer.readCharsLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_CHAR, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_LE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteCharNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_CHAR.length; ++i) {
            buffer.writeCharNO(FOR_CHAR[i]);
        }
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_NO), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteCharNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_CHAR.length; ++i) {
            buffer.writeCharNO((long)i * (long)Character.BYTES, FOR_CHAR[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_NO), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteCharsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsNO(FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_NO), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteCharsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsNO(0L, FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_NO), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteCharBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_CHAR.length; ++i) {
            buffer.writeCharBE(FOR_CHAR[i]);
        }
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_BE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteCharBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_CHAR.length; ++i) {
            buffer.writeCharBE((long)i * (long)Character.BYTES, FOR_CHAR[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_BE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteCharsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsBE(FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_BE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteCharsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsBE(0L, FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_BE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteCharLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_CHAR.length; ++i) {
            buffer.writeCharLE(FOR_CHAR[i]);
        }
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_LE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteCharLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_CHAR.length; ++i) {
            buffer.writeCharLE((long)i * (long)Character.BYTES, FOR_CHAR[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_LE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteCharsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsLE(FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(VALUES_PER_TEST * Character.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_LE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteCharsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Character.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeCharsLE(0L, FOR_CHAR, 0, FOR_CHAR.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_CHAR_LE), 0L, 0L, VALUES_PER_TEST * Character.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Character.BYTES, END_GARBAGE_BYTES);
    }
    private static final int[] FOR_MEDIUM = new int[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_MEDIUM[i] = (int)(RDM.nextLong() & 0xFFFFFF);
        }
    }
    private static final int[] FOR_MEDIUM_SIGN_EXTENDED = new int[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_MEDIUM_SIGN_EXTENDED[i] = (FOR_MEDIUM[i] << 8) >> 8;
        }
    }
    private static final byte[] FOR_MEDIUM_BE = new byte[VALUES_PER_TEST * 3 + Integer.SIZE];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_MEDIUM_BE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (final int value : FOR_MEDIUM) {
            buffer.putInt((value << 8));
            buffer.position(buffer.position() - 1);
        }
    }
    private static final byte[] FOR_MEDIUM_LE = new byte[VALUES_PER_TEST * 3 + Integer.SIZE];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_MEDIUM_LE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (final int value : FOR_MEDIUM) {
            buffer.putInt(value);
            buffer.position(buffer.position() - 1);
        }
    }

    @Test
    public void testReadUnsignedMediumBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsBE(FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        final int[] readBack = new int[VALUES_PER_TEST];

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readUnsignedMediumBE();
        }
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readUnsignedMediumBE((long)i * 3);
        }
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (int)1);
        buffer.readUnsignedMediumsBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        buffer.readUnsignedMediumsBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_BE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadSignedMediumBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsBE(FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        final int[] readBack = new int[VALUES_PER_TEST];

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readSignedMediumBE();
        }
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readSignedMediumBE((long)i * 3);
        }
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (int)1);
        buffer.readSignedMediumsBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        buffer.readSignedMediumsBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_BE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadUnsignedMediumLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsLE(FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        final int[] readBack = new int[VALUES_PER_TEST];

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readUnsignedMediumLE();
        }
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readUnsignedMediumLE((long)i * 3);
        }
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (int)1);
        buffer.readUnsignedMediumsLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        buffer.readUnsignedMediumsLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_LE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadSignedMediumLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsLE(FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        final int[] readBack = new int[VALUES_PER_TEST];

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readSignedMediumLE();
        }
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readSignedMediumLE((long)i * 3);
        }
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (int)1);
        buffer.readSignedMediumsLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        buffer.readSignedMediumsLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_MEDIUM_SIGN_EXTENDED, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_LE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteMediumBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_MEDIUM.length; ++i) {
            buffer.writeMediumBE(FOR_MEDIUM[i]);
        }
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_BE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteMediumBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_MEDIUM.length; ++i) {
            buffer.writeMediumBE((long)i * (long)3, FOR_MEDIUM[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_BE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteMediumsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsBE(FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_BE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteMediumsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsBE(0L, FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_BE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }



    @Test
    public void testRelativeSingleWriteMediumLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_MEDIUM.length; ++i) {
            buffer.writeMediumLE(FOR_MEDIUM[i]);
        }
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_LE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteMediumLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_MEDIUM.length; ++i) {
            buffer.writeMediumLE((long)i * (long)3, FOR_MEDIUM[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_LE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteMediumsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsLE(FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(VALUES_PER_TEST * 3, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_LE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteMediumsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * 3 + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeMediumsLE(0L, FOR_MEDIUM, 0, FOR_MEDIUM.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_MEDIUM_LE), 0L, 0L, VALUES_PER_TEST * 3);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * 3, END_GARBAGE_BYTES);
    }
    private static final int[] FOR_INT = new int[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_INT[i] = (int)RDM.nextLong();
        }
    }
    private static final byte[] FOR_INT_NO = new byte[VALUES_PER_TEST * Integer.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_INT_NO);
        buffer.order(ByteOrder.nativeOrder());
        for (final int value : FOR_INT) {
            buffer.putInt(value);
        }
    }
    private static final byte[] FOR_INT_BE = new byte[VALUES_PER_TEST * Integer.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_INT_BE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (final int value : FOR_INT) {
            buffer.putInt(value);
        }
    }
    private static final byte[] FOR_INT_LE = new byte[VALUES_PER_TEST * Integer.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_INT_LE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (final int value : FOR_INT) {
            buffer.putInt(value);
        }
    }

    @Test
    public void testReadIntNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsNO(FOR_INT, 0, FOR_INT.length);
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        final int[] readBack = new int[VALUES_PER_TEST];

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readIntNO();
        }
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readIntNO((long)i * Integer.BYTES);
        }
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (int)1);
        buffer.readIntsNO(readBack, 0, readBack.length);
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        buffer.readIntsNO(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_NO), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadIntBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsBE(FOR_INT, 0, FOR_INT.length);
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        final int[] readBack = new int[VALUES_PER_TEST];

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readIntBE();
        }
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readIntBE((long)i * Integer.BYTES);
        }
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (int)1);
        buffer.readIntsBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        buffer.readIntsBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_BE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadIntLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsLE(FOR_INT, 0, FOR_INT.length);
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        final int[] readBack = new int[VALUES_PER_TEST];

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readIntLE();
        }
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readIntLE((long)i * Integer.BYTES);
        }
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (int)1);
        buffer.readIntsLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (int)1);
        buffer.readIntsLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_INT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_LE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteIntNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_INT.length; ++i) {
            buffer.writeIntNO(FOR_INT[i]);
        }
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_NO), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteIntNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_INT.length; ++i) {
            buffer.writeIntNO((long)i * (long)Integer.BYTES, FOR_INT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_NO), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteIntsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsNO(FOR_INT, 0, FOR_INT.length);
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_NO), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteIntsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsNO(0L, FOR_INT, 0, FOR_INT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_NO), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteIntBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_INT.length; ++i) {
            buffer.writeIntBE(FOR_INT[i]);
        }
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_BE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteIntBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_INT.length; ++i) {
            buffer.writeIntBE((long)i * (long)Integer.BYTES, FOR_INT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_BE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteIntsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsBE(FOR_INT, 0, FOR_INT.length);
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_BE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteIntsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsBE(0L, FOR_INT, 0, FOR_INT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_BE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteIntLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_INT.length; ++i) {
            buffer.writeIntLE(FOR_INT[i]);
        }
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_LE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteIntLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_INT.length; ++i) {
            buffer.writeIntLE((long)i * (long)Integer.BYTES, FOR_INT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_LE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteIntsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsLE(FOR_INT, 0, FOR_INT.length);
        assertEquals(VALUES_PER_TEST * Integer.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_LE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteIntsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Integer.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeIntsLE(0L, FOR_INT, 0, FOR_INT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_INT_LE), 0L, 0L, VALUES_PER_TEST * Integer.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Integer.BYTES, END_GARBAGE_BYTES);
    }
    private static final long[] FOR_LONG = new long[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_LONG[i] = (long)RDM.nextLong();
        }
    }
    private static final byte[] FOR_LONG_NO = new byte[VALUES_PER_TEST * Long.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_LONG_NO);
        buffer.order(ByteOrder.nativeOrder());
        for (final long value : FOR_LONG) {
            buffer.putLong(value);
        }
    }
    private static final byte[] FOR_LONG_BE = new byte[VALUES_PER_TEST * Long.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_LONG_BE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (final long value : FOR_LONG) {
            buffer.putLong(value);
        }
    }
    private static final byte[] FOR_LONG_LE = new byte[VALUES_PER_TEST * Long.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_LONG_LE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (final long value : FOR_LONG) {
            buffer.putLong(value);
        }
    }

    @Test
    public void testReadLongNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsNO(FOR_LONG, 0, FOR_LONG.length);
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        final long[] readBack = new long[VALUES_PER_TEST];

        Arrays.fill(readBack, (long)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readLongNO();
        }
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (long)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readLongNO((long)i * Long.BYTES);
        }
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (long)1);
        buffer.readLongsNO(readBack, 0, readBack.length);
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (long)1);
        buffer.readLongsNO(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_NO), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadLongBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsBE(FOR_LONG, 0, FOR_LONG.length);
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        final long[] readBack = new long[VALUES_PER_TEST];

        Arrays.fill(readBack, (long)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readLongBE();
        }
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (long)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readLongBE((long)i * Long.BYTES);
        }
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (long)1);
        buffer.readLongsBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (long)1);
        buffer.readLongsBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_BE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadLongLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsLE(FOR_LONG, 0, FOR_LONG.length);
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        final long[] readBack = new long[VALUES_PER_TEST];

        Arrays.fill(readBack, (long)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readLongLE();
        }
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (long)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readLongLE((long)i * Long.BYTES);
        }
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (long)1);
        buffer.readLongsLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (long)1);
        buffer.readLongsLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_LONG, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_LE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteLongNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_LONG.length; ++i) {
            buffer.writeLongNO(FOR_LONG[i]);
        }
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_NO), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteLongNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_LONG.length; ++i) {
            buffer.writeLongNO((long)i * (long)Long.BYTES, FOR_LONG[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_NO), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteLongsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsNO(FOR_LONG, 0, FOR_LONG.length);
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_NO), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteLongsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsNO(0L, FOR_LONG, 0, FOR_LONG.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_NO), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteLongBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_LONG.length; ++i) {
            buffer.writeLongBE(FOR_LONG[i]);
        }
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_BE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteLongBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_LONG.length; ++i) {
            buffer.writeLongBE((long)i * (long)Long.BYTES, FOR_LONG[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_BE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteLongsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsBE(FOR_LONG, 0, FOR_LONG.length);
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_BE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteLongsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsBE(0L, FOR_LONG, 0, FOR_LONG.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_BE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteLongLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_LONG.length; ++i) {
            buffer.writeLongLE(FOR_LONG[i]);
        }
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_LE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteLongLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_LONG.length; ++i) {
            buffer.writeLongLE((long)i * (long)Long.BYTES, FOR_LONG[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_LE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteLongsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsLE(FOR_LONG, 0, FOR_LONG.length);
        assertEquals(VALUES_PER_TEST * Long.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_LE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteLongsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Long.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeLongsLE(0L, FOR_LONG, 0, FOR_LONG.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_LONG_LE), 0L, 0L, VALUES_PER_TEST * Long.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Long.BYTES, END_GARBAGE_BYTES);
    }
    private static final float[] FOR_FLOAT = new float[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_FLOAT[i] = (float)RDM.nextLong();
        }
    }
    private static final byte[] FOR_FLOAT_NO = new byte[VALUES_PER_TEST * Float.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_FLOAT_NO);
        buffer.order(ByteOrder.nativeOrder());
        for (final float value : FOR_FLOAT) {
            buffer.putFloat(value);
        }
    }
    private static final byte[] FOR_FLOAT_BE = new byte[VALUES_PER_TEST * Float.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_FLOAT_BE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (final float value : FOR_FLOAT) {
            buffer.putFloat(value);
        }
    }
    private static final byte[] FOR_FLOAT_LE = new byte[VALUES_PER_TEST * Float.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_FLOAT_LE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (final float value : FOR_FLOAT) {
            buffer.putFloat(value);
        }
    }

    @Test
    public void testReadFloatNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsNO(FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        final float[] readBack = new float[VALUES_PER_TEST];

        Arrays.fill(readBack, (float)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readFloatNO();
        }
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (float)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readFloatNO((long)i * Float.BYTES);
        }
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (float)1);
        buffer.readFloatsNO(readBack, 0, readBack.length);
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (float)1);
        buffer.readFloatsNO(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_NO), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadFloatBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsBE(FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        final float[] readBack = new float[VALUES_PER_TEST];

        Arrays.fill(readBack, (float)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readFloatBE();
        }
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (float)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readFloatBE((long)i * Float.BYTES);
        }
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (float)1);
        buffer.readFloatsBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (float)1);
        buffer.readFloatsBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_BE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadFloatLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsLE(FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        final float[] readBack = new float[VALUES_PER_TEST];

        Arrays.fill(readBack, (float)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readFloatLE();
        }
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (float)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readFloatLE((long)i * Float.BYTES);
        }
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (float)1);
        buffer.readFloatsLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (float)1);
        buffer.readFloatsLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_FLOAT, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_LE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteFloatNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_FLOAT.length; ++i) {
            buffer.writeFloatNO(FOR_FLOAT[i]);
        }
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_NO), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteFloatNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_FLOAT.length; ++i) {
            buffer.writeFloatNO((long)i * (long)Float.BYTES, FOR_FLOAT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_NO), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteFloatsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsNO(FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_NO), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteFloatsNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsNO(0L, FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_NO), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteFloatBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_FLOAT.length; ++i) {
            buffer.writeFloatBE(FOR_FLOAT[i]);
        }
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_BE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteFloatBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_FLOAT.length; ++i) {
            buffer.writeFloatBE((long)i * (long)Float.BYTES, FOR_FLOAT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_BE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteFloatsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsBE(FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_BE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteFloatsBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsBE(0L, FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_BE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteFloatLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_FLOAT.length; ++i) {
            buffer.writeFloatLE(FOR_FLOAT[i]);
        }
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_LE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteFloatLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_FLOAT.length; ++i) {
            buffer.writeFloatLE((long)i * (long)Float.BYTES, FOR_FLOAT[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_LE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteFloatsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsLE(FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(VALUES_PER_TEST * Float.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_LE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteFloatsLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Float.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeFloatsLE(0L, FOR_FLOAT, 0, FOR_FLOAT.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_FLOAT_LE), 0L, 0L, VALUES_PER_TEST * Float.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Float.BYTES, END_GARBAGE_BYTES);
    }
    private static final double[] FOR_DOUBLE = new double[VALUES_PER_TEST];
    static {
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            FOR_DOUBLE[i] = (double)RDM.nextLong();
        }
    }
    private static final byte[] FOR_DOUBLE_NO = new byte[VALUES_PER_TEST * Double.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_DOUBLE_NO);
        buffer.order(ByteOrder.nativeOrder());
        for (final double value : FOR_DOUBLE) {
            buffer.putDouble(value);
        }
    }
    private static final byte[] FOR_DOUBLE_BE = new byte[VALUES_PER_TEST * Double.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_DOUBLE_BE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        for (final double value : FOR_DOUBLE) {
            buffer.putDouble(value);
        }
    }
    private static final byte[] FOR_DOUBLE_LE = new byte[VALUES_PER_TEST * Double.BYTES];
    static {
        final ByteBuffer buffer = ByteBuffer.wrap(FOR_DOUBLE_LE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (final double value : FOR_DOUBLE) {
            buffer.putDouble(value);
        }
    }

    @Test
    public void testReadDoubleNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesNO(FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        final double[] readBack = new double[VALUES_PER_TEST];

        Arrays.fill(readBack, (double)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readDoubleNO();
        }
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (double)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readDoubleNO((long)i * Double.BYTES);
        }
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (double)1);
        buffer.readDoublesNO(readBack, 0, readBack.length);
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (double)1);
        buffer.readDoublesNO(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_NO), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadDoubleBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesBE(FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        final double[] readBack = new double[VALUES_PER_TEST];

        Arrays.fill(readBack, (double)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readDoubleBE();
        }
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (double)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readDoubleBE((long)i * Double.BYTES);
        }
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (double)1);
        buffer.readDoublesBE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (double)1);
        buffer.readDoublesBE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_BE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testReadDoubleLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesLE(FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        final double[] readBack = new double[VALUES_PER_TEST];

        Arrays.fill(readBack, (double)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readDoubleLE();
        }
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (double)1);
        for (int i = 0; i < VALUES_PER_TEST; ++i) {
            readBack[i] = buffer.readDoubleLE((long)i * Double.BYTES);
        }
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setReaderIndex(0L);

        Arrays.fill(readBack, (double)1);
        buffer.readDoublesLE(readBack, 0, readBack.length);
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        Arrays.fill(readBack, (double)1);
        buffer.readDoublesLE(0L, readBack, 0, readBack.length);
        assertArrayEquals(FOR_DOUBLE, readBack);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_LE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteDoubleNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_DOUBLE.length; ++i) {
            buffer.writeDoubleNO(FOR_DOUBLE[i]);
        }
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_NO), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteDoubleNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_DOUBLE.length; ++i) {
            buffer.writeDoubleNO((long)i * (long)Double.BYTES, FOR_DOUBLE[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_NO), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteDoublesNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesNO(FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_NO), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteDoublesNO() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesNO(0L, FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_NO), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteDoubleBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_DOUBLE.length; ++i) {
            buffer.writeDoubleBE(FOR_DOUBLE[i]);
        }
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_BE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteDoubleBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_DOUBLE.length; ++i) {
            buffer.writeDoubleBE((long)i * (long)Double.BYTES, FOR_DOUBLE[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_BE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteDoublesBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesBE(FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_BE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteDoublesBE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesBE(0L, FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_BE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeSingleWriteDoubleLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_DOUBLE.length; ++i) {
            buffer.writeDoubleLE(FOR_DOUBLE[i]);
        }
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_LE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsSingleWriteDoubleLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        for (int i = 0; i < FOR_DOUBLE.length; ++i) {
            buffer.writeDoubleLE((long)i * (long)Double.BYTES, FOR_DOUBLE[i]);
        }
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_LE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testRelativeWriteDoublesLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesLE(FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(VALUES_PER_TEST * Double.BYTES, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_LE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    @Test
    public void testAbsoluteWriteDoublesLE() {
        final Buffer buffer = new Buffer(false, "test", new byte[VALUES_PER_TEST * Double.BYTES + END_GARBAGE_BYTES]);

        buffer.getMemoryAsSegment().fill(EMPTY_INIT);

        buffer.writeDoublesLE(0L, FOR_DOUBLE, 0, FOR_DOUBLE.length);
        assertEquals(0L, buffer.getReadableBytes());

        buffer.verifyAgainst(MemorySegment.ofArray(FOR_DOUBLE_LE), 0L, 0L, VALUES_PER_TEST * Double.BYTES);
        buffer.verifyAgainst(MemorySegment.ofArray(END_GARBAGE), 0L, VALUES_PER_TEST * Double.BYTES, END_GARBAGE_BYTES);
    }

    private void testUnsignedVarInt(final Buffer buffer, final int value, final int nBytes) {
        // test relative
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeUnsignedVarInt(value);
        assertEquals(nBytes, buffer.getReadableBytes());
        assertEquals(value, buffer.readUnsignedVarInt());
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setIndices(0L, 0L);

        // test absolute
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeUnsignedVarInt(0L, value);
        assertEquals(0L, buffer.getReadableBytes());
        assertEquals(value, buffer.readUnsignedVarInt(0L));
        buffer.setIndices(0L, 0L);
    }

    private void testUnsignedVarInt(final Buffer buffer) {
        final int value1Byte = -1 & ((1 << 7) - 1);
        final int value2Byte = -1 & ((1 << 14) - 1);
        final int value3Byte = -1 & ((1 << 21) - 1);
        final int value4Byte = -1 & ((1 << 28) - 1);
        final int value5Byte = -1;

        this.testUnsignedVarInt(buffer, value1Byte, 1);
        this.testUnsignedVarInt(buffer, value2Byte, 2);
        this.testUnsignedVarInt(buffer, value3Byte, 3);
        this.testUnsignedVarInt(buffer, value4Byte, 4);
        this.testUnsignedVarInt(buffer, value5Byte, 5);

        // test illegal encoding
        for (int i = 0; i < 5; ++i) {
            buffer.writeByte((byte)128);
        }
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readUnsignedVarInt(0L);
        });
        assertEquals(5, buffer.getReadableBytes());
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readUnsignedVarInt();
        });
        assertEquals(5, buffer.getReadableBytes());
    }

    @Test
    public void testUnsignedVarIntConstrained() {
        this.testUnsignedVarInt(new Buffer(false, "test", new byte[5 + 1]));
    }

    @Test
    public void testUnsignedVarIntUnconstrained() {
        this.testUnsignedVarInt(new Buffer(false, "test", new byte[8 + 1]));
    }

    private void testSignedVarInt(final Buffer buffer, final int value, final int nBytes) {
        // test relative
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeSignedVarInt(value);
        assertEquals(nBytes, buffer.getReadableBytes());
        assertEquals(value, buffer.readSignedVarInt());
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setIndices(0L, 0L);

        // test absolute
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeSignedVarInt(0L, value);
        assertEquals(0L, buffer.getReadableBytes());
        assertEquals(value, buffer.readSignedVarInt(0L));
        buffer.setIndices(0L, 0L);
    }

    private void testSignedVarInt(final Buffer buffer) {
        final int value1Byte = -1;
        final int value2Byte = -1 & ((1 << 7) - 1);
        final int value3Byte = -1 & ((1 << 14) - 1);
        final int value4Byte = -1 & ((1 << 21) - 1);
        final int value5Byte = Integer.MAX_VALUE;
        final int value5Byte2 = Integer.MIN_VALUE;

        this.testSignedVarInt(buffer, value1Byte, 1);
        this.testSignedVarInt(buffer, value2Byte, 2);
        this.testSignedVarInt(buffer, value3Byte, 3);
        this.testSignedVarInt(buffer, value4Byte, 4);
        this.testSignedVarInt(buffer, value5Byte, 5);
        this.testSignedVarInt(buffer, value5Byte2, 5);

        // test illegal encoding
        for (int i = 0; i < 5; ++i) {
            buffer.writeByte((byte)128);
        }
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readSignedVarInt(0L);
        });
        assertEquals(5, buffer.getReadableBytes());
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readSignedVarInt();
        });
        assertEquals(5, buffer.getReadableBytes());
    }

    @Test
    public void testSignedVarIntConstrained() {
        this.testSignedVarInt(new Buffer(false, "test", new byte[5 + 1]));
    }

    @Test
    public void testSignedVarIntUnconstrained() {
        this.testSignedVarInt(new Buffer(false, "test", new byte[8 + 1]));
    }

    private void testUnsignedVarLong(final Buffer buffer, final long value, final int nBytes) {
        // test relative
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeUnsignedVarLong(value);
        assertEquals(nBytes, buffer.getReadableBytes());
        assertEquals(value, buffer.readUnsignedVarLong());
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setIndices(0L, 0L);

        // test absolute
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeUnsignedVarLong(0L, value);
        assertEquals(0L, buffer.getReadableBytes());
        assertEquals(value, buffer.readUnsignedVarLong(0L));
        buffer.setIndices(0L, 0L);
    }

    private void testUnsignedVarLong(final Buffer buffer) {
        final long value1Byte = -1L & ((1L << 7) - 1);
        final long value2Byte = -1L & ((1L << 14) - 1);
        final long value3Byte = -1L & ((1L << 21) - 1);
        final long value4Byte = -1L & ((1L << 28) - 1);
        final long value5Byte = -1L & ((1L << 35) - 1);
        final long value6Byte = -1L & ((1L << 42) - 1);
        final long value7Byte = -1L & ((1L << 49) - 1);
        final long value8Byte = -1L & ((1L << 56) - 1);
        final long value9Byte = -1L & ((1L << 63) - 1);
        final long value10Byte = -1L;

        this.testUnsignedVarLong(buffer, value1Byte, 1);
        this.testUnsignedVarLong(buffer, value2Byte, 2);
        this.testUnsignedVarLong(buffer, value3Byte, 3);
        this.testUnsignedVarLong(buffer, value4Byte, 4);
        this.testUnsignedVarLong(buffer, value5Byte, 5);
        this.testUnsignedVarLong(buffer, value6Byte, 6);
        this.testUnsignedVarLong(buffer, value7Byte, 7);
        this.testUnsignedVarLong(buffer, value8Byte, 8);
        this.testUnsignedVarLong(buffer, value9Byte, 9);
        this.testUnsignedVarLong(buffer, value10Byte, 10);

        // test illegal encoding
        for (int i = 0; i < 10; ++i) {
            buffer.writeByte((byte)128);
        }
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readUnsignedVarLong(0L);
        });
        assertEquals(10, buffer.getReadableBytes());
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readUnsignedVarLong();
        });
        assertEquals(10, buffer.getReadableBytes());
    }

    @Test
    public void testUnsignedVarLongConstrained() {
        this.testUnsignedVarLong(new Buffer(false, "test", new byte[10 + 1]));
    }

    @Test
    public void testUnsignedVarLongUnconstrained() {
        this.testUnsignedVarLong(new Buffer(false, "test", new byte[16 + 1]));
    }

    private void testSignedVarLong(final Buffer buffer, final long value, final int nBytes) {
        // test relative
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeSignedVarLong(value);
        assertEquals(nBytes, buffer.getReadableBytes());
        assertEquals(value, buffer.readSignedVarLong());
        assertEquals(0L, buffer.getReadableBytes());
        buffer.setIndices(0L, 0L);

        // test absolute
        buffer.getMemoryAsSegment().fill(EMPTY_INIT);
        buffer.writeSignedVarLong(0L, value);
        assertEquals(0L, buffer.getReadableBytes());
        assertEquals(value, buffer.readSignedVarLong(0L));
        buffer.setIndices(0L, 0L);
    }

    private void testSignedVarLong(final Buffer buffer) {
        final long value1Byte = -1L;
        final long value2Byte = -1L & ((1L << 7) - 1);
        final long value3Byte = -1L & ((1L << 14) - 1);
        final long value4Byte = -1L & ((1L << 21) - 1);
        final long value5Byte = -1L & ((1L << 28) - 1);
        final long value6Byte = -1L & ((1L << 35) - 1);
        final long value7Byte = -1L & ((1L << 42) - 1);
        final long value8Byte = -1L & ((1L << 49) - 1);
        final long value9Byte = -1L & ((1L << 56) - 1);
        final long value10Byte = Long.MAX_VALUE;
        final long value10Byte2 = Long.MIN_VALUE;

        this.testSignedVarLong(buffer, value1Byte, 1);
        this.testSignedVarLong(buffer, value2Byte, 2);
        this.testSignedVarLong(buffer, value3Byte, 3);
        this.testSignedVarLong(buffer, value4Byte, 4);
        this.testSignedVarLong(buffer, value5Byte, 5);
        this.testSignedVarLong(buffer, value6Byte, 6);
        this.testSignedVarLong(buffer, value7Byte, 7);
        this.testSignedVarLong(buffer, value8Byte, 8);
        this.testSignedVarLong(buffer, value9Byte, 9);
        this.testSignedVarLong(buffer, value10Byte, 10);
        this.testSignedVarLong(buffer, value10Byte2, 10);

        // test illegal encoding
        for (int i = 0; i < 10; ++i) {
            buffer.writeByte((byte)128);
        }
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readSignedVarLong(0L);
        });
        assertEquals(10, buffer.getReadableBytes());
        assertThrows(IllegalArgumentException.class, () -> {
            buffer.readSignedVarLong();
        });
        assertEquals(10, buffer.getReadableBytes());
    }

    @Test
    public void testSignedVarLongConstrained() {
        this.testSignedVarLong(new Buffer(false, "test", new byte[10 + 1]));
    }

    @Test
    public void testSignedVarLongUnconstrained() {
        this.testSignedVarLong(new Buffer(false, "test", new byte[16 + 1]));
    }

    @Test
    public void testCopy() {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 4L, 4L);
        buffer.writeIntNO(-1);

        final Buffer cpy = buffer.copy(false, "test");

        assertEquals(buffer.getReadableBytes(), cpy.getReadableBytes());
        assertEquals(buffer.readIntNO(0L), cpy.readIntNO(0L));
        assertNotEquals(buffer.getMemoryAsSegment(), cpy.getMemoryAsSegment());
    }

    @Test
    public void testShiftAndReAllocateToZero() {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 4L, 5L);
        buffer.writeByte((byte)-1);
        final int toWrite = 0x01 | (0x02 << 8) | (0x04 << 16) | (0x08 << 24);
        buffer.writeIntNO(toWrite);

        assertEquals((byte)-1, buffer.readByte());
        buffer.shiftReaderToZero();
        assertEquals(4L, buffer.getReadableBytes());
        assertEquals(0L, buffer.getReaderIndex());
        assertEquals(toWrite, buffer.readIntNO());
        assertEquals(0L, buffer.getReadableBytes());
    }

    private void testChannel(final boolean useNBytes) throws IOException {
        try (final DummyChannel channel = new DummyChannel(ByteBuffer.allocateDirect(16))) {
            final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 32L, 32L);

            // test empty

            assertEquals(0L, buffer.writeFromChannel(channel));
            assertEquals(0L, buffer.readIntoChannel(channel));
            assertEquals(0L, buffer.writeFromFilePos(channel, 0L));
            assertEquals(0L, buffer.readIntoFilePos(channel, 0L));
            assertEquals(0L, channel.position());
            assertEquals(0L, channel.size());

            assertThrows(Exception.class, () -> {
                buffer.writeFromChannel(channel, 1L);
            });
            assertThrows(Exception.class, () -> {
                buffer.readIntoChannel(channel, 1L);
            });

            assertThrows(Exception.class, () -> {
                buffer.writeFromFilePos(channel, 0L, 1L);
            });
            assertThrows(Exception.class, () -> {
                buffer.readIntoFilePos(channel, 0L, 1L);
            });

            // test read/write value

            int toWrite = 0x01 | (0x02 << 8) | (0x04 << 16) | (0x08 << 24);
            buffer.writeIntNO(toWrite);

            // write
            if (!useNBytes) {
                assertEquals(4L, buffer.readIntoChannel(channel));
            } else {
                buffer.readIntoChannel(channel, 4L);
            }
            assertEquals(0L, buffer.getReadableBytes());
            assertEquals(4L, channel.position());
            assertEquals(4L, channel.size());

            // read back
            channel.position(0L);
            assertEquals(0L, channel.position());

            if (!useNBytes) {
                assertEquals(4L, buffer.writeFromChannel(channel));
            } else {
                buffer.writeFromChannel(channel, 4L);
            }
            assertEquals(4L, buffer.getReaderIndex());
            assertEquals(toWrite, buffer.readIntNO());
            assertEquals(4L, channel.position());
            assertEquals(4L, channel.size());

            buffer.clear();
            channel.position(0L);

            // again with specified positions
            toWrite = Integer.rotateLeft(toWrite, 1);
            buffer.writeIntNO(toWrite);

            if (useNBytes) {
                buffer.readIntoFilePos(channel, 0L, 4L);
            } else {
                assertEquals(4L, buffer.readIntoFilePos(channel, 0L));
            }
            assertEquals(0L, buffer.getReadableBytes());
            assertEquals(0L, channel.position()); // did not adjust pos
            assertEquals(4L, channel.size()); // did not adjust size

            // read back
            if (useNBytes) {
                buffer.writeFromFilePos(channel, 0L, 4L);
            } else {
                assertEquals(4L, buffer.writeFromFilePos(channel, 0L));
            }

            assertEquals(4L, buffer.getReaderIndex());
            assertEquals(toWrite, buffer.readIntNO());
            assertEquals(0L, channel.position()); // did not adjust pos
            assertEquals(4L, channel.size()); // did not adjust size


            channel.position(4L);

            assertEquals(4L, channel.position());
            assertEquals(4L, channel.size());

            assertThrows(Exception.class, () -> {
                buffer.writeFromChannel(channel, 1L);
            });
            assertThrows(Exception.class, () -> {
                buffer.readIntoChannel(channel, 1L);
            });

            assertThrows(Exception.class, () -> {
                buffer.writeFromFilePos(channel, 4L, 1L);
            });
            assertThrows(Exception.class, () -> {
                buffer.readIntoFilePos(channel, 4L, 1L);
            });
        }
    }

    @Test
    public void testChannel() throws IOException {
        this.testChannel(false);
    }

    @Test
    public void testChannelNBytes() throws IOException {
        this.testChannel(true);
    }

    @Test
    public void testBufferToBuffer() {
        final Buffer buffer1 = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 32L, 32L);
        final Buffer buffer2 = new Buffer(false, "test", MemoryAllocator.UnPooledNative.INSTANCE, 32L, 32L);

        assertEquals(0L, buffer1.writeFromBuffer(buffer2));
        assertEquals(0L, buffer2.readIntoBuffer(buffer1));

        int toWrite = 0x01 | (0x02 << 8) | (0x04 << 16) | (0x08 << 24);
        buffer1.writeIntNO(toWrite = Integer.rotateLeft(toWrite, 1));

        assertEquals(4L, buffer1.readIntoBuffer(buffer2));
        assertEquals(toWrite, buffer2.readIntNO());
        assertEquals(0L, buffer2.getReadableBytes());
        assertEquals(0L, buffer1.getReadableBytes());

        buffer1.writeIntNO(toWrite = Integer.rotateLeft(toWrite, 1));
        assertEquals(4L, buffer2.writeFromBuffer(buffer1));
        assertEquals(toWrite, buffer2.readIntNO());
        assertEquals(0L, buffer2.getReadableBytes());
        assertEquals(0L, buffer1.getReadableBytes());

        assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer1.readIntoBuffer(buffer2, 1L);
            buffer2.readIntoBuffer(buffer1, 1L);
        });

        buffer1.writeIntNO(toWrite = Integer.rotateLeft(toWrite, 1));
        buffer1.readIntoBuffer(buffer2, 4L);
        assertEquals(toWrite, buffer2.readIntNO());
        assertEquals(0L, buffer2.getReadableBytes());
        assertEquals(0L, buffer1.getReadableBytes());
    }

    @Test
    public void testByteBuffer() {
        final Buffer buffer1 = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 32L, 32L);
        final ByteBuffer buffer2 = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());

        assertEquals(0, buffer1.readIntoByteBuffer(buffer2));
        assertEquals(0, buffer2.position());
        assertEquals(buffer2.capacity(), buffer2.limit());

        int toWrite = 0x01 | (0x02 << 8) | (0x04 << 16) | (0x08 << 24);
        buffer2.putInt(toWrite = Integer.rotateLeft(toWrite, 1));
        buffer2.flip();
        assertEquals(4, buffer1.writeFromByteBuffer(buffer2));
        assertEquals(toWrite, buffer1.readIntNO());
        assertEquals(0L, buffer1.getReadableBytes());
        assertEquals(0, buffer2.remaining());

        buffer2.clear();
        buffer2.putInt(toWrite = Integer.rotateLeft(toWrite, 1));
        buffer2.flip();
        buffer1.writeFromByteBuffer(buffer2, 4);
        assertEquals(toWrite, buffer1.readIntNO());
        assertEquals(0L, buffer1.getReadableBytes());
        assertEquals(0, buffer2.remaining());

        buffer2.clear();
        buffer1.writeIntNO(toWrite = Integer.rotateLeft(toWrite, 1));
        buffer1.readIntoByteBuffer(buffer2, 4);
        buffer2.flip();
        assertEquals(4, buffer2.remaining());
        assertEquals(toWrite, buffer2.getInt());
        assertEquals(0L, buffer1.getReadableBytes());
    }

    private static final ValueLayout.OfInt INT_NO = ValueLayout.JAVA_INT.withByteAlignment(1L).withOrder(ByteOrder.nativeOrder());

    @Test
    public void testMemorySegment() {
        final Buffer buffer1 = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 32L, 32L);
        final MemorySegment buffer2 = Arena.ofAuto().allocate(32L, 8L);

        assertEquals(0L, buffer1.readIntoSegment(buffer2, 0L));
        buffer1.readIntoSegment(buffer2, 0L, 0L);

        int toWrite = 0x01 | (0x02 << 8) | (0x04 << 16) | (0x08 << 24);
        buffer1.writeIntNO(toWrite = Integer.rotateLeft(toWrite, 1));

        assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer1.readIntoSegment(buffer2, buffer2.byteSize() + 1L);
            buffer1.readIntoSegment(buffer2, buffer2.byteSize() - 3L, 4L);
        });

        assertEquals(4L, buffer1.readIntoSegment(buffer2, 1L));
        assertEquals(toWrite, buffer2.get(INT_NO, 1L));
        assertEquals(0L, buffer1.getReadableBytes());

        buffer1.writeIntNO(toWrite = Integer.rotateLeft(toWrite, 1));
        buffer1.readIntoSegment(buffer2, 2L, 4L);
        assertEquals(0L, buffer1.getReadableBytes());
        assertEquals(toWrite, buffer2.get(INT_NO, 2L));
    }

    @Test
    public void testReadOverflow() {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 32L, 32L);
        assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer.skipRead(1L);
        });
        assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer.unread(1L);
        });
        assertThrows(IndexOutOfBoundsException.class, () -> {
            buffer.ensureReadable(1L);
        });
    }

    @Test
    public void testCapacity() {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 1L, 32L);
        assertEquals(1L, buffer.getCurrentCapacity());
        assertEquals(32L, buffer.getMaxCapacity());

        buffer.ensureImmediatelyWritable(1L);
        assertEquals(1L, buffer.getCurrentCapacity());
        buffer.ensureImmediatelyWritable(2L);
        assertNotEquals(2L, buffer.getCurrentCapacity());
        buffer.writeShortLE((short)0xAA);
        buffer.writeShortLE((short)0xFF);
        assertEquals(0x00FF00AA, buffer.readIntLE());
        assertEquals(0, buffer.getReadableBytes());
    }

    @Test
    public void testFree() {
        final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 1L, 32L);
        assertThrows(Exception.class, () -> {
            buffer.ensureRefCountZero();
        });
        buffer.incReferenceCount("test2");
        assertNotEquals(null, buffer.getMemoryAsBuffer());
        buffer.decReferenceCount("test");
        buffer.decReferenceCount("test2");
        assertNull(buffer.getMemoryAsSegment());
        assertNull(buffer.getMemoryAsBuffer());
        assertThrows(Exception.class, () -> {
            buffer.readIntNO();
        });
        assertThrows(Exception.class, () -> {
            buffer.writeIntNO(-1);
        });
        buffer.ensureRefCountZero();
    }

    @Test
    public void testFreeDebug() {
        final Buffer buffer = new Buffer(true, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 1L, 32L);
        assertThrows(Exception.class, () -> {
            buffer.ensureRefCountZero();
        });
        buffer.incReferenceCount("test2");
        assertThrows(Exception.class, () -> {
            buffer.incReferenceCount("test2");
        });
        assertNotEquals(null, buffer.getMemoryAsBuffer());
        assertThrows(Exception.class, () -> {
            buffer.decReferenceCount("test3");
        });
        buffer.decReferenceCount("test");
        buffer.decReferenceCount("test2");
        assertNull(buffer.getMemoryAsSegment());
        assertNull(buffer.getMemoryAsBuffer());
        assertThrows(Exception.class, () -> {
            buffer.readIntNO();
        });
        assertThrows(Exception.class, () -> {
            buffer.writeIntNO(-1);
        });
        buffer.ensureRefCountZero();
    }

    public static final class DummyChannel extends FileChannel {
        // position is position
        // limit is size
        private final ByteBuffer stored;

        public DummyChannel(final ByteBuffer stored) {
            this.stored = stored;
            this.stored.position(0);
            this.stored.limit(0);
        }

        @Override
        public long position() {
            return (long)this.stored.position();
        }

        @Override
        public DummyChannel position(final long newPosition) {
            final int posInt = Math.toIntExact(newPosition);
            if (posInt > this.stored.capacity()) {
                throw new UnsupportedOperationException();
            }
            if (posInt > this.stored.limit()) {
                this.stored.limit(posInt);
            }
            this.stored.position(posInt);
            return this;
        }

        @Override
        public long size() {
            return (long)this.stored.limit();
        }

        @Override
        public int read(final ByteBuffer dst) {
            final int toRead = Math.min(dst.remaining(), this.stored.remaining());
            if (toRead > 0) {
                dst.put(dst.position(), this.stored, this.stored.position(), toRead);

                dst.position(dst.position() + toRead);
                this.stored.position(this.stored.position() + toRead);
            }

            return toRead;
        }

        @Override
        public int read(final ByteBuffer dst, final long pos) {
            final int posInt = Math.toIntExact(pos);
            final int toRead = Math.clamp(dst.remaining(), 0, this.stored.limit() - posInt);
            if (toRead > 0) {
                dst.put(dst.position(), this.stored, posInt, toRead);
                dst.position(dst.position() + toRead);
            }

            return toRead;
        }

        @Override
        public int write(final ByteBuffer src) {
            final int toWrite = Math.min(src.remaining(), this.stored.capacity() - this.stored.position());
            if (toWrite > 0) {
                final int writeIdx = this.stored.position();
                this.stored.limit(Math.max(this.stored.limit(), this.stored.position() + toWrite));
                this.stored.position(this.stored.position() + toWrite);

                this.stored.put(writeIdx, src, src.position(), toWrite);
                src.position(src.position() + toWrite);
            }
            return toWrite;
        }

        @Override
        public int write(final ByteBuffer src, final long pos) {
            final int posInt = Math.toIntExact(pos);
            final int toWrite = Math.min(src.remaining(), this.stored.capacity() - posInt);

            if (toWrite > 0) {
                this.stored.limit(Math.max(this.stored.limit(), posInt + toWrite));
                this.stored.put(posInt, src, src.position(), toWrite);
                src.position(src.position() + toWrite);
            }
            return toWrite;
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void force(boolean metaData) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            throw new UnsupportedOperationException();
        }


        @Override
        protected void implCloseChannel() throws IOException {}
    }
}
