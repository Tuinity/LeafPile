package ca.spottedleaf.ioutil.stream;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.BufferTest;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import ca.spottedleaf.ioutil.stream.channel.ChannelBufferInputStream;
import ca.spottedleaf.ioutil.stream.channel.ChannelBufferOutputStream;
import org.junit.jupiter.api.Test;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static ca.spottedleaf.ioutil.stream.SimpleBufferStreamTest.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ChannelBufferStreamTest {

    private static byte[] readBytes(final AbstractBufferInputStream in, final int len) throws IOException {
        final byte[] ret = new byte[len];
        in.readFully(ret);
        return ret;
    }

    private static short[] readShortsLE(final AbstractBufferInputStream in, final int len) throws IOException {
        final short[] ret = new short[len];
        in.readFullyLE(ret);
        return ret;
    }

    @Test
    public void testChannel() throws IOException {
        try (final BufferTest.DummyChannel channel = new BufferTest.DummyChannel(ByteBuffer.allocate(ARRAY_LEN*16))) {
            final Buffer buffer = new Buffer(false, "test", MemoryAllocator.UnPooledHeap.INSTANCE, 64L, 64L);

            try (final ChannelBufferOutputStream out = new ChannelBufferOutputStream(buffer, channel, false)) {
                assertEquals(0L, out.channelPosition());

                out.write(BYTES);
                assertEquals((long)BYTES.length, out.channelPosition());
                out.setPosition(0L);
                assertEquals(0L, out.channelPosition());
                out.writeShortsLE(SHORTS);
                assertEquals(2L*(long)BYTES.length, out.channelPosition());
                out.write(BYTES);
                assertEquals((long)BYTES.length + 2L*(long)BYTES.length, out.channelPosition());
            }

            channel.position(0L);

            try (final ChannelBufferInputStream in = new ChannelBufferInputStream(buffer, channel, false)) {
                assertEquals(0L, in.channelPosition());
                assertEquals((long)BYTES.length + 2L*(long)BYTES.length, in.availableLong());

                assertFalse(in.isEOF());
                assertTrue(in.getImmediatelyReadable() > 0L);

                in.skipNBytes(2L*(long)BYTES.length);
                assertFalse(in.isEOF());
                assertTrue(in.getImmediatelyReadable() > 0L);

                in.skipNBytes((long)BYTES.length);
                assertTrue(in.isEOF());
                assertFalse(in.getImmediatelyReadable() > 0L);
                assertEquals(0L, in.skip(1000L));

                in.setPosition(0L);
                in.skip(2L*(long)BYTES.length);

                assertArrayEquals(BYTES, readBytes(in, BYTES.length));
                in.setPosition(0L);
                assertArrayEquals(SHORTS, readShortsLE(in, SHORTS.length));

                in.setPosition(0L);
                in.isEOF();
                in.skip(1L);
                in.skipNBytes(1L);
                assertEquals(2L, in.channelPosition());

                assertThrows(EOFException.class, () -> {
                    in.skipNBytes((long)BYTES.length + 2L*(long)BYTES.length);
                });
            }
        }
    }
}
