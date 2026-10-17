package ca.spottedleaf.ioutil.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;

/**
 * Per-thread native work buffers shared by the compression utilities.
 */
public final class CompressionBuffers {

    public static final Cleaner CLEANER = Cleaner.create();
    private static final ThreadLocal<CompressionBuffers> BUFFERS = ThreadLocal.withInitial(CompressionBuffers::new);

    public final MemorySegment workInRaw;
    public final ByteBuffer workIn;
    public final MemorySegment workOutRaw;
    public final ByteBuffer workOut;

    private CompressionBuffers() {
        this.workInRaw = Arena.ofAuto().allocate(24L * 1024L, 32L);
        this.workIn = this.workInRaw.asByteBuffer();
        this.workOutRaw = Arena.ofAuto().allocate(24L * 1024L, 32L);
        this.workOut = this.workOutRaw.asByteBuffer();
    }

    public static CompressionBuffers get() {
        return BUFFERS.get();
    }
}
