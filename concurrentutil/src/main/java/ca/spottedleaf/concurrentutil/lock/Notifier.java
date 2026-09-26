package ca.spottedleaf.concurrentutil.lock;

import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;

/**
 * Synchronisation utility which may be used to park threads waiting
 * on a single condition.
 */
public final class Notifier {

    private final Lock lock;

    public Notifier(final boolean blocked) {
        this.lock = new Lock(blocked);
    }

    private static final class Lock extends AbstractQueuedLongSynchronizer {

        private static final long STATE_READY = 0L; // unblocked
        private static final long STATE_NOT_READY = 1L; // blocked

        public Lock(final boolean blocked) {
            this.setState(blocked ? STATE_NOT_READY : STATE_READY);
        }

        private static final long ACQUIRE_FAIL = -1L;
        private static final long ACQUIRE_SUCCESS = 1L;

        @Override
        protected long tryAcquireShared(final long arg) {
            if ((arg & 1L) == STATE_READY) {
                // note: this branch only taken during initial attempt
                return ACQUIRE_SUCCESS;
            }

            // if the state has changed: (this.getState() ^ arg != 0)
            // then at least one indicateReady or indicateReadyAndBlock has been invoked, and we should be allowed to
            // proceed
            return (this.getState() ^ arg) == 0L ? ACQUIRE_FAIL : ACQUIRE_SUCCESS;
        }

        @Override
        protected boolean tryRelease(final long arg) {
            final long add = arg + 1L;
            final long state = this.getState();

            return ((state & 1L) == STATE_NOT_READY) && this.compareAndSetState(state, state + add);
        }

        public boolean indicateReady() {
            return this.release(0);
        }

        public boolean indicateReadyAndBlock() {
            return this.release(1);
        }

        public boolean block() {
            final long state = this.getState();

            return ((state & 1L) == STATE_READY) && this.compareAndSetState(state, state + 1L);
        }

        public void waitUntilReady() {
            this.acquireShared(this.getState());
        }
    }

    /**
     * Unblocks and unparks all waiting threads. It is guaranteed that all threads which are parked
     * when this function is invoked are unparked.
     * @return {@code true} if the blocked state was changed to {@code false}, or {@code false} if not blocked
     */
    public boolean notifyThreads() {
        return this.lock.indicateReady();
    }

    /**
     * This function is equivalent to invoking {@link #notifyThreads()} followed by {@link #block()} except
     * that it is atomic and possibly faster. This function is useful for causing all threads previously parked
     * to become schedulable again while forcing new threads which enter {@link #waitUntilReady()} to park.
     * @return {@code true} if the blocked state is {@code true}, {@code false} otherwise
     */
    public boolean notifyThreadsAndBlock() {
        return this.lock.indicateReadyAndBlock();
    }

    /**
     * Requires any thread entering {@link #waitUntilReady()} to park until notified.
     * @return {@code false} if the blocked state is already set, {@code true} if this thread set the blocked state.
     */
    public boolean block() {
        return this.lock.block();
    }

    /**
     * This function causes this thread to be parked if blocked. The thread is only unblocked when
     * a notification arrives, such as through {@link #notifyThreads()}.
     *
     */
    public void waitUntilReady() {
        this.lock.waitUntilReady();
    }
}
