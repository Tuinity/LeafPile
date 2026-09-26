package ca.spottedleaf.ioutil.buffer;

import java.lang.foreign.Arena;

public interface MemoryAllocator {

    public boolean isNative();

    public Memory findMemory(final long minBytes, final long maxBytes);

    public void returnMemory(final Memory memory);

    public static final class AutoNative implements MemoryAllocator {
        public static final AutoNative INSTANCE = new AutoNative();

        private static final ThreadLocal<Arena> AUTO_ARENA = ThreadLocal.withInitial(Arena::ofAuto);

        private AutoNative() {}

        @Override
        public boolean isNative() {
            return true;
        }

        @Override
        public Memory findMemory(final long minBytes, final long maxBytes) {
            return Memory.ofSegment(AUTO_ARENA.get().allocate(minBytes, 8L));
        }

        @Override
        public void returnMemory(final Memory memory) {
            memory.close();
        }
    }

    public static final class UnPooledNative implements MemoryAllocator {

        public static final UnPooledNative INSTANCE = new UnPooledNative();

        private UnPooledNative() {}

        @Override
        public boolean isNative() {
            return true;
        }

        @Override
        public Memory findMemory(final long minBytes, final long maxBytes) {
            return Memory.allocateNative(minBytes, 8L);
        }

        @Override
        public void returnMemory(final Memory memory) {
            memory.close();
        }
    }

    public static final class UnPooledHeap implements MemoryAllocator {

        public static final UnPooledHeap INSTANCE = new UnPooledHeap();

        private UnPooledHeap() {}

        @Override
        public boolean isNative() {
            return false;
        }

        @Override
        public Memory findMemory(final long minBytes, final long maxBytes) {
            return Memory.allocateHeap(minBytes);
        }

        @Override
        public void returnMemory(final Memory memory) {
            // just to clear the segment field, so that later usages NPE
            memory.close();
        }
    }
}
