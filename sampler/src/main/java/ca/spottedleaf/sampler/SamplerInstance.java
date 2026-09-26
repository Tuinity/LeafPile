package ca.spottedleaf.sampler;

import ca.spottedleaf.common.time.Schedule;
import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.list.COWArrayList;
import ca.spottedleaf.concurrentutil.map.concurrent.longs.ConcurrentChainedLong2ReferenceHashTable;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

public final class SamplerInstance implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SamplerInstance.class);

    private static final long THREAD_SELECT_INTERVAL = TimeUnit.SECONDS.toNanos(1L);

    private final long id;
    private long intervalNS;
    private volatile long nextIntervalNS;
    private final Predicate<Thread> threadSelector;
    private long lastThreadSelect;
    private long[] lastSelectedThreads;
    private volatile LongLinkedOpenHashSet lastSelectedThreadsSet; // visible to event system

    private final SampleWriter writer;
    private final SamplerTree tree;

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean listeningForEvents = new AtomicBoolean();
    private final MultiThreadedQueue<RecordedEvent<? extends Record>> pendingEvents = new MultiThreadedQueue<>();

    private static final AtomicLong ID_GENERATOR = new AtomicLong();
    private static final ConcurrentChainedLong2ReferenceHashTable<SamplerInstance> BY_ID = new ConcurrentChainedLong2ReferenceHashTable<>();
    private static final COWArrayList<SamplerInstance> RUNNING_INSTANCES = new COWArrayList<>(SamplerInstance.class);

    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

    private final LongArrayFIFOQueue sampleTimes5s = new LongArrayFIFOQueue();

    private SamplerInstance(final long id, final File file, final long intervalNS,
                            final Predicate<Thread> threadSelector, final boolean listenForEvents,
                            final long treeFlags) throws IOException {
        this.id = id;
        this.intervalNS = intervalNS;
        this.nextIntervalNS = intervalNS;
        this.threadSelector = threadSelector;

        this.writer = new SampleWriter(file);
        this.writer.writeHeader();
        this.tree = new SamplerTree(this.writer, treeFlags);

        this.listeningForEvents.set(listenForEvents);
    }

    public static SamplerInstance getById(final long id) {
        return BY_ID.get(id);
    }

    public static List<SamplerInstance> getAll() {
        return new ArrayList<>(Arrays.asList(RUNNING_INSTANCES.getArray()));
    }

    public static SamplerInstance create(final File file, final long intervalNS, final Predicate<Thread> threadSelector,
                                         final boolean listenForEvents, final long treeFlags) throws IOException {
        final SamplerInstance ret = new SamplerInstance(
                ID_GENERATOR.getAndIncrement(), file, intervalNS, threadSelector, listenForEvents,
                treeFlags
        );

        BY_ID.put(ret.getId(), ret);
        RUNNING_INSTANCES.add(ret);

        final Thread thread = new Thread(ret, "Sampler instance #" + ret.getId());
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((final Thread t, final Throwable thr) -> {
            LOGGER.error("Uncaught excecption in thread: " + t.getName(), thr);
        });

        thread.start();

        return ret;
    }

    public static <T extends Record> void pushEvent(final EventRegistry.RegisteredEvent<T> event, final T value) {
        final SamplerInstance[] running = RUNNING_INSTANCES.getArray();

        if (running.length == 0) {
            return;
        }

        final long tid = Thread.currentThread().threadId();

        RecordedEvent<T> recordedEvent = null;
        for (final SamplerInstance sampler : running) {
            if (!sampler.listeningForEvents.get()) {
                continue;
            }

            final LongLinkedOpenHashSet selectedThreads = sampler.lastSelectedThreadsSet;
            if (selectedThreads == null || !selectedThreads.contains(tid)) {
                continue;
            }

            if (recordedEvent == null) {
                if (value.getClass() != event.cls()) {
                    throw new IllegalArgumentException("Mismatch of registered event and input value");
                }

                recordedEvent = new RecordedEvent<>(
                    System.currentTimeMillis(), System.nanoTime(), tid, event, value
                );
            }

            sampler.pendingEvents.add(recordedEvent);
        }
    }

    public long getId() {
        return this.id;
    }

    public boolean cancel() {
        return !this.cancelled.get() && !this.cancelled.compareAndExchange(false, true);
    }

    public boolean setEventListen(final boolean on) {
        return this.listeningForEvents.get() == !on && !on == this.listeningForEvents.compareAndExchange(!on, on);
    }

    private void waitUntil(final long deadline) {
        // TODO impl unparking?
        while (true) {
            final long currTime = System.nanoTime();
            final long delay = deadline - currTime;
            if (delay <= 0L) {
                return;
            }
            Thread.interrupted();
            LockSupport.parkNanos(delay);
        }
    }

    public int getSampleNodeCount() {
        return this.tree.getNodeCount();
    }

    public int getStackCount() {
        return this.tree.getStackCount();
    }

    public int getStringCount() {
        return this.writer.getStringCount();
    }

    public synchronized double getLast5sRate() {
        this.purgeOld(System.nanoTime());

        return (double)this.sampleTimes5s.size() / 5.0;
    }

    private synchronized void purgeOld(final long timeNS) {
        while (!this.sampleTimes5s.isEmpty() && (timeNS - this.sampleTimes5s.firstLong()) >= TimeUnit.SECONDS.toNanos(5L)) {
            this.sampleTimes5s.dequeueLong();
        }
    }

    private synchronized void updateRate(final long sampleTimeNS) {
        this.purgeOld(sampleTimeNS);

        this.sampleTimes5s.enqueue(sampleTimeNS);
    }

    public void setNextIntervalNS(final long intervalNS) {
        this.nextIntervalNS = intervalNS;
    }

    @Override
    public void run() {
        try {
            final Schedule schedule = new Schedule(System.nanoTime());
            while (!this.cancelled.get()) {
                this.intervalNS = this.nextIntervalNS;

                final long deadline = schedule.getDeadline(this.intervalNS);
                this.waitUntil(deadline);

                RecordedEvent event;
                while ((event = this.pendingEvents.poll()) != null) {
                    this.writer.writeEvent(
                        event.tid(), event.timeEpochMS(), event.nanoTime(), event.event(), event.value()
                    );
                }

                final long nanoTime = System.nanoTime();

                final long periodsAhead = schedule.getPeriodsAhead(this.intervalNS, nanoTime);
                schedule.advanceBy(Math.max(1, periodsAhead), this.intervalNS);

                this.sample(System.currentTimeMillis(), nanoTime);
                this.updateRate(nanoTime);
            }
        } finally {
            try {
                RUNNING_INSTANCES.remove(this);
                BY_ID.remove(this.id, this);
            } finally {
                try {
                    this.writer.close();
                } catch (final IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    private static Thread[] getAllThreads() {
        // find root thread group
        ThreadGroup parent, group = Thread.currentThread().getThreadGroup();
        while (group != null && (parent = group.getParent()) != null) {
            group = parent;
        }

        // read all threads from root group
        Thread[] threads;
        int totalThreads = group.activeCount();
        do {
            threads = new Thread[(3 * Math.max(totalThreads, 1)) >> 1];
            totalThreads = group.enumerate(threads);
        } while (totalThreads >= threads.length);

        return Arrays.copyOf(threads, totalThreads);
    }

    private ThreadInfo[] reSelectThreads(final long nanoTime) {
        final LongLinkedOpenHashSet selectedThreads = this.lastSelectedThreads == null ? new LongLinkedOpenHashSet() : new LongLinkedOpenHashSet(this.lastSelectedThreads);

        // find new threads
        for (final Thread thread : getAllThreads()) {
            final long tid = thread.threadId();

            if (this.threadSelector != null) {
                if (selectedThreads.contains(tid)) {
                    // don't bother re-testing selected threads
                    continue;
                }
                if (!this.threadSelector.test(thread)) {
                    // did not pass
                    continue;
                }
            }

            if (selectedThreads.add(tid)) {
                this.writer.writeNewThread(tid, thread.getName());
            }
        }

        final long[] tentativeThreads = selectedThreads.toLongArray();
        final ThreadInfo[] infos = THREAD_MX_BEAN.getThreadInfo(tentativeThreads, Integer.MAX_VALUE);

        final List<ThreadInfo> ret = new ArrayList<>(infos.length);

        // clean up dead threads
        for (int i = 0; i < infos.length; ++i) {
            final ThreadInfo info = infos[i];
            if (info == null) {
                // remove dead thread from selected
                selectedThreads.remove(tentativeThreads[i]);
                continue;
            }

            ret.add(info);
        }

        this.lastThreadSelect = nanoTime;
        this.lastSelectedThreads = selectedThreads.toLongArray();
        this.lastSelectedThreadsSet = selectedThreads;

        return ret.toArray(new ThreadInfo[0]);
    }

    private ThreadInfo[] sampleThreads(final long nanoTime) {
        if (this.lastSelectedThreads == null || nanoTime - this.lastThreadSelect >= THREAD_SELECT_INTERVAL) {
            return this.reSelectThreads(nanoTime);
        } else {
            return THREAD_MX_BEAN.getThreadInfo(this.lastSelectedThreads, Integer.MAX_VALUE);
        }
    }

    private void sample(final long epochTimeMS, final long nanoTime) {
        for (final ThreadInfo info : this.sampleThreads(nanoTime)) {
            if (info == null) {
                // dead thread, will eventually be cleaned up
                continue;
            }

            final long node = this.tree.getNodeFor(info.getStackTrace());

            this.writer.writeSample(info.getThreadId(), epochTimeMS, nanoTime, node);
        }
    }

    public static final record RecordedEvent<T extends Record>(long timeEpochMS, long nanoTime, long tid, EventRegistry.RegisteredEvent<T> event, T value) {}
}
