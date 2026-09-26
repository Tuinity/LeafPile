package ca.spottedleaf.ioutil.buffer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class Memory implements AutoCloseable {

    private final Arena arena;
    MemorySegment segment;

    private Memory(final Arena arena, final MemorySegment segment) {
        this.arena = arena;
        this.segment = segment;
    }

    public MemorySegment getSegment() {
        return this.segment;
    }

    public static Memory allocateNative(final long bytes, final long alignment) {
        final Arena arena = Arena.ofShared();

        return new Memory(arena, arena.allocate(bytes, alignment));
    }

    public static Memory allocateHeap(final long bytes) {
        if (bytes < 0L) {
            throw new IllegalArgumentException("Bytes < 0");
        }
        if (bytes > (long)Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Bytes > Integer.MAX_VALUE");
        }
        return new Memory(null, MemorySegment.ofArray(new byte[(int)bytes]));
    }

    public static Memory ofHeap(final byte[] bytes) {
        return new Memory(null, MemorySegment.ofArray(bytes));
    }

    public static Memory ofSegment(final MemorySegment segment) {
        return new Memory(null, segment);
    }

    @Override
    public void close() {
        this.segment = null;
        if (this.arena != null) {
            this.arena.close();
        }
    }
}
