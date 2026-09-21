package ca.spottedleaf.concurrentutil.executor;

import ca.spottedleaf.concurrentutil.lock.Notifier;
import ca.spottedleaf.concurrentutil.util.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueueExecutorRunnable implements Runnable, PrioritisedExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueExecutorRunnable.class);

    public final Thread thread;
    protected final PrioritisedExecutor queue;

    protected volatile boolean threadShutdown;

    private final Notifier notifier = new Notifier(false);

    protected volatile boolean halted;

    public QueueExecutorRunnable(final Thread thread, final PrioritisedExecutor queue) {
        this.thread = thread;
        this.queue = queue;
    }

    @Override
    public final void run() {
        try {
            this.begin();
            this.doRun();
        } finally {
            this.die();
        }
    }

    // rets whether there are more tasks
    private boolean mainLoop() {
        this.pollTasks();

        if (this.handleClose()) {
            return false;
        }

        this.notifier.block();
        if (this.pollTasks()) {
            this.notifier.notifyThreads();
            return true;
        }

        if (this.handleClose()) {
            this.notifier.notifyThreads();
            return false;
        }

        this.notifier.waitUntilReady();

        return true;
    }

    private void doRun() {
        while (this.mainLoop());
    }

    protected void begin() {}

    protected void die() {}

    /**
     * Attempts to poll as many tasks as possible, returning when finished.
     * @return Whether any tasks were executed.
     */
    protected boolean pollTasks() {
        boolean ret = false;

        for (;;) {
            if (this.halted) {
                return ret;
            }
            try {
                if (!this.queue.executeTask()) {
                    return ret;
                }
                ret = true;
            } catch (final Throwable throwable) {
                LOGGER.error("Exception thrown from prioritized runnable task in thread '" + this.thread.getName() + "'", throwable);
            }
        }
    }

    protected final boolean handleClose() {
        if (this.threadShutdown) {
            this.pollTasks(); // this ensures we've emptied the queue
            return true;
        }
        return false;
    }

    /**
     * Notify this thread that a task has been added to its queue
     * @return {@code true} if this thread was waiting for tasks, {@code false} if it is executing tasks
     */
    public final boolean notifyTasks() {
        return this.notifier.notifyThreads();
    }

    @Override
    public long getTotalTasksExecuted() {
        return this.queue.getTotalTasksExecuted();
    }

    @Override
    public long getTotalTasksScheduled() {
        return this.queue.getTotalTasksScheduled();
    }

    @Override
    public long generateNextSubOrder() {
        return this.queue.generateNextSubOrder();
    }

    @Override
    public boolean shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    /**
     * {@inheritDoc}
     * @throws IllegalStateException Always
     */
    @Override
    public boolean executeTask() throws IllegalStateException {
        throw new IllegalStateException();
    }

    @Override
    public PrioritisedExecutor.PrioritisedTask queueTask(final Runnable task) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createTask(task);

        ret.queue();

        return ret;
    }

    @Override
    public PrioritisedExecutor.PrioritisedTask queueTask(final Runnable task, final Priority priority) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createTask(task, priority);

        ret.queue();

        return ret;
    }

    @Override
    public PrioritisedExecutor.PrioritisedTask queueTask(final Runnable task, final Priority priority, final long subOrder,
                                                         final long stream) {
        final PrioritisedExecutor.PrioritisedTask ret = this.createTask(task, priority, subOrder, stream);

        ret.queue();

        return ret;
    }


    @Override
    public PrioritisedExecutor.PrioritisedTask createTask(final Runnable task) {
        final PrioritisedExecutor.PrioritisedTask queueTask = this.queue.createTask(task);

        return new WrappedTask(queueTask);
    }

    @Override
    public PrioritisedExecutor.PrioritisedTask createTask(final Runnable task, final Priority priority) {
        final PrioritisedExecutor.PrioritisedTask queueTask = this.queue.createTask(task, priority);

        return new WrappedTask(queueTask);
    }

    @Override
    public PrioritisedExecutor.PrioritisedTask createTask(final Runnable task, final Priority priority, final long subOrder,
                                                          final long stream) {
        final PrioritisedExecutor.PrioritisedTask queueTask = this.queue.createTask(task, priority, subOrder, stream);

        return new WrappedTask(queueTask);
    }

    /**
     * Closes this queue executor's queue. Optionally waits for all tasks in queue to be executed if {@code wait} is true.
     * <p>
     *     This function is MT-Safe.
     * </p>
     * @param wait If this call is to wait until this thread shuts down.
     * @param killQueue Whether to shutdown this thread's queue
     * @return whether this thread shut down the queue
     * @see #halt(boolean)
     */
    public boolean close(final boolean wait, final boolean killQueue) {
        final boolean ret = killQueue && this.queue.shutdown();
        this.threadShutdown = true;

        // force thread to respond to the shutdown
        this.notifyTasks();

        if (wait) {
            boolean interrupted = false;
            for (;;) {
                if (!this.thread.isAlive()) {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
                try {
                    this.thread.join();
                } catch (final InterruptedException ex) {
                    interrupted = true;
                }
            }
        }

        return ret;
    }


    /**
     * Causes this thread to exit without draining the queue. To ensure tasks are completed, use {@link #close(boolean, boolean)}.
     * <p>
     *     This is not safe to call with {@link #close(boolean, boolean)} if <code>wait = true</code>, in which case
     *     the waiting thread may block indefinitely.
     * </p>
     * <p>
     *     This function is MT-Safe.
     * </p>
     * @param killQueue Whether to shutdown this thread's queue
     * @see #close(boolean, boolean)
     */
    public void halt(final boolean killQueue) {
        if (killQueue) {
            this.queue.shutdown();
        }
        this.threadShutdown = true;
        this.halted = true;

        // force thread to respond to the shutdown
        this.notifyTasks();
    }

    /**
     * Required so that queue() can notify (unpark) this thread
     */
    private final class WrappedTask implements PrioritisedExecutor.PrioritisedTask {
        private final PrioritisedExecutor.PrioritisedTask queueTask;

        public WrappedTask(final PrioritisedExecutor.PrioritisedTask queueTask) {
            this.queueTask = queueTask;
        }

        @Override
        public PrioritisedExecutor getExecutor() {
            return QueueExecutorRunnable.this;
        }

        @Override
        public boolean queue() {
            final boolean ret = this.queueTask.queue();
            if (ret) {
                QueueExecutorRunnable.this.notifyTasks();
            }
            return ret;
        }

        @Override
        public boolean isQueued() {
            return this.queueTask.isQueued();
        }

        @Override
        public boolean cancel() {
            return this.queueTask.cancel();
        }

        @Override
        public boolean execute() {
            return this.queueTask.execute();
        }

        @Override
        public Priority getPriority() {
            return this.queueTask.getPriority();
        }

        @Override
        public boolean setPriority(final Priority priority) {
            return this.queueTask.setPriority(priority);
        }

        @Override
        public boolean raisePriority(final Priority priority) {
            return this.queueTask.raisePriority(priority);
        }

        @Override
        public boolean lowerPriority(final Priority priority) {
            return this.queueTask.lowerPriority(priority);
        }

        @Override
        public long getSubOrder() {
            return this.queueTask.getSubOrder();
        }

        @Override
        public boolean setSubOrder(final long subOrder) {
            return this.queueTask.setSubOrder(subOrder);
        }

        @Override
        public boolean raiseSubOrder(final long subOrder) {
            return this.queueTask.raiseSubOrder(subOrder);
        }

        @Override
        public boolean lowerSubOrder(final long subOrder) {
            return this.queueTask.lowerSubOrder(subOrder);
        }

        @Override
        public long getStream() {
            return this.queueTask.getStream();
        }

        @Override
        public boolean setStream(final long stream) {
            return this.queueTask.setStream(stream);
        }

        @Override
        public boolean setPrioritySubOrderStream(final Priority priority, final long subOrder, final long stream) {
            return this.queueTask.setPrioritySubOrderStream(priority, subOrder, stream);
        }

        @Override
        public PrioritisedExecutor.PriorityState getPriorityState() {
            return this.queueTask.getPriorityState();
        }
    }
}
