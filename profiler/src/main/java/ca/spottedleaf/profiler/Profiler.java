package ca.spottedleaf.profiler;

import ca.spottedleaf.common.util.DecimalFormats;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class Profiler {

    private static final Logger LOGGER = LoggerFactory.getLogger(Profiler.class);

    public final ProfilerRegistry registry;
    private final ProfileGraph graph;

    private long[] accumulatedTimers = new long[0];
    private long[] accumulatedCounters = new long[0];

    private long[] timers = new long[16];
    private long[] counters = new long[16];
    private final IntArrayFIFOQueue callStack = new IntArrayFIFOQueue();
    private int topOfStack = ProfileGraph.ROOT_NODE;
    private final LongArrayFIFOQueue timerStack = new LongArrayFIFOQueue();
    private long lastTimerStart = 0L;

    public Profiler(final ProfilerRegistry registry, final ProfileGraph graph) {
        this.registry = registry;
        this.graph = graph;
    }

    private static void add(final long[] dst, final long[] src) {
        final int srcLen = src.length;
        Objects.checkFromToIndex(0, srcLen, dst.length);
        for (int i = 0; i < srcLen; ++i) {
            dst[i] += src[i];
        }
    }

    public ProfilingData copyCurrent() {
        return new ProfilingData(
                this.registry, this.graph, this.timers.clone(), this.counters.clone()
        );
    }

    public ProfilingData copyAccumulated() {
        return new ProfilingData(
                this.registry, this.graph, this.accumulatedTimers.clone(), this.accumulatedCounters.clone()
        );
    }

    public void accumulate() {
        if (this.accumulatedTimers.length != this.timers.length) {
            this.accumulatedTimers = Arrays.copyOf(this.accumulatedTimers, this.timers.length);
        }
        add(this.accumulatedTimers, this.timers);
        Arrays.fill(this.timers, 0L);

        if (this.accumulatedCounters.length != this.counters.length) {
            this.accumulatedCounters = Arrays.copyOf(this.accumulatedCounters, this.counters.length);
        }
        add(this.accumulatedCounters, this.counters);
        Arrays.fill(this.counters, 0L);
    }

    public void clearCurrent() {
        Arrays.fill(this.timers, 0L);
        Arrays.fill(this.counters, 0L);
    }

    private long[] resizeTimers(final long[] old, final int least) {
        return this.timers = Arrays.copyOf(old, Math.max(old.length * 2, least * 2));
    }

    private void incrementTimersDirect(final int nodeId, final long count) {
        final long[] timers = this.timers;
        if (nodeId >= timers.length) {
            this.resizeTimers(timers, nodeId)[nodeId] += count;
        } else {
            timers[nodeId] += count;
        }
    }

    private long[] resizeCounters(final long[] old, final int least) {
        return this.counters = Arrays.copyOf(old, Math.max(old.length * 2, least * 2));
    }

    private void incrementCountersDirect(final int nodeId, final long count) {
        final long[] counters = this.counters;
        if (nodeId >= counters.length) {
            this.resizeCounters(counters, nodeId)[nodeId] += count;
        } else {
            counters[nodeId] += count;
        }
    }

    public void incrementCounter(final int timerId, final long count) {
        final int node = this.graph.getOrCreateNode(this.topOfStack, timerId);
        this.incrementCountersDirect(node, count);
    }

    public void incrementTimer(final int timerId, final long count) {
        final int node = this.graph.getOrCreateNode(this.topOfStack, timerId);
        this.incrementTimersDirect(node, count);
    }

    public void startTimer(final int timerId, final long startTime) {
        final long lastTimerStart = this.lastTimerStart;
        final ProfileGraph graph = this.graph;
        final int parentNode = this.topOfStack;
        final IntArrayFIFOQueue callStack = this.callStack;
        final LongArrayFIFOQueue timerStack = this.timerStack;

        this.lastTimerStart = startTime;
        this.topOfStack = graph.getOrCreateNode(parentNode, timerId);

        callStack.enqueue(parentNode);
        timerStack.enqueue(lastTimerStart);
    }

    public void stopTimer(final int timerId, final long endTime) {
        final long lastStart = this.lastTimerStart;
        final int currentNode = this.topOfStack;
        final IntArrayFIFOQueue callStack = this.callStack;
        final LongArrayFIFOQueue timerStack = this.timerStack;
        this.lastTimerStart = timerStack.dequeueLastLong();
        this.topOfStack = callStack.dequeueLastInt();

        if (currentNode != this.graph.getNode(this.topOfStack, timerId)) {
            final ProfilerRegistry.ProfilerEntry timer = this.registry.getById(timerId);
            throw new IllegalStateException("Timer " + (timer == null ? "null" : timer.name()) + " did not stop");
        }

        this.incrementTimersDirect(currentNode, endTime - lastStart);
        this.incrementCountersDirect(currentNode, 1L);
    }

    public void stopLastTimer(final long endTime) {
        final long lastStart = this.lastTimerStart;
        final int currentNode = this.topOfStack;
        final IntArrayFIFOQueue callStack = this.callStack;
        final LongArrayFIFOQueue timerStack = this.timerStack;
        this.lastTimerStart = timerStack.dequeueLastLong();
        this.topOfStack = callStack.dequeueLastInt();

        this.incrementTimersDirect(currentNode, endTime - lastStart);
        this.incrementCountersDirect(currentNode, 1L);
    }

    private static final class ProfileNode {

        public final ProfileNode parent;
        public final int nodeId;
        public final ProfilerRegistry.ProfilerEntry profiler;
        public final long totalTime;
        public final long totalCount;
        public final List<ProfileNode> children = new ArrayList<>();
        public long childrenTimingCount;
        public int depth = -1;
        public boolean lastChild;

        private ProfileNode(final ProfileNode parent, final int nodeId, final ProfilerRegistry.ProfilerEntry profiler,
                            final long totalTime, final long totalCount) {
            this.parent = parent;
            this.nodeId = nodeId;
            this.profiler = profiler;
            this.totalTime = totalTime;
            this.totalCount = totalCount;
        }
    }



    public static final record ProfilingData(
            ProfilerRegistry registry,
            ProfileGraph graph,
            long[] timers,
            long[] counters
    ) {
        public List<String> dumpToString() {
            final List<ProfileGraph.GraphNode> graphDFS = this.graph.getDFS();
            final Reference2ReferenceOpenHashMap<ProfileGraph.GraphNode, ProfileNode> nodeMap = new Reference2ReferenceOpenHashMap<>();

            final ArrayDeque<ProfileNode> orderedNodes = new ArrayDeque<>();

            for (int i = 0, len = graphDFS.size(); i < len; ++i) {
                final ProfileGraph.GraphNode graphNode = graphDFS.get(i);
                final ProfileNode parent = nodeMap.get(graphNode.parent());
                final int nodeId = graphNode.nodeId();

                final long totalTime = nodeId >= this.timers.length ? 0L : this.timers[nodeId];
                final long totalCount = nodeId >= this.counters.length ? 0L : this.counters[nodeId];
                final ProfilerRegistry.ProfilerEntry profiler = this.registry.getById(graphNode.timerId());

                final ProfileNode profileNode = new ProfileNode(parent, nodeId, profiler, totalTime, totalCount);

                if (parent != null) {
                    parent.childrenTimingCount += totalTime;
                    parent.children.add(profileNode);
                } else if (i != 0) { // i == 0 is root
                    throw new IllegalStateException("Node " + nodeId + " must have parent");
                } else {
                    // set up
                    orderedNodes.add(profileNode);
                }

                nodeMap.put(graphNode, profileNode);
            }

            final List<String> ret = new ArrayList<>();

            long totalTime = 0L;

            // totalTime = sum of times for root node's children
            for (final ProfileNode node : orderedNodes.peekFirst().children) {
                totalTime += node.totalTime;
            }

            final ArrayDeque<ProfileNode> flatOrderedNodes = new ArrayDeque<>();

            ProfileNode profileNode;
            while ((profileNode = orderedNodes.pollFirst()) != null) {
                final int depth = profileNode.depth;
                profileNode.children.sort((final ProfileNode p1, final ProfileNode p2) -> {
                    final int typeCompare = p1.profiler.type().compareTo(p2.profiler.type());
                    if (typeCompare != 0) {
                        // first count, then profiler
                        return typeCompare;
                    }

                    if (p1.profiler.type() == ProfilerRegistry.ProfileType.COUNTER) {
                        // highest count first
                        return Long.compare(p2.totalCount, p1.totalCount);
                    } else {
                        // highest time first
                        return Long.compare(p2.totalTime, p1.totalTime);
                    }
                });

                boolean first = true;
                for (int i = profileNode.children.size() - 1; i >= 0; --i) {
                    final ProfileNode child = profileNode.children.get(i);
                    if (child.totalCount == 0L) {
                        // skip nodes not recorded
                        continue;
                    }
                    if (first) {
                        child.lastChild = true;
                        first = false;
                    }
                    child.depth = depth + 1;
                    orderedNodes.addFirst(child);
                }

                flatOrderedNodes.addLast(profileNode);
            }

            final StringBuilder builder = new StringBuilder();
            final IntList closed = new IntArrayList();
            while ((profileNode = flatOrderedNodes.pollFirst()) != null) {
                final int depth = profileNode.depth;
                closed.removeIf((int d) -> d >= depth);
                if (profileNode.lastChild) {
                    closed.add(depth);
                }
                if (profileNode.nodeId == ProfileGraph.ROOT_NODE) {
                    // don't display root
                    continue;
                }

                final boolean noParent = profileNode.parent == null || profileNode.parent.nodeId == ProfileGraph.ROOT_NODE;

                final long parentTime = noParent ? totalTime : profileNode.parent.totalTime;
                final ProfilerRegistry.ProfilerEntry profilerEntry = profileNode.profiler;

                // format:
                // For profiler type:
                // <indent><name> X% total, Y% parent, self A% total, self B% children, avg X sum Y, Dms raw sum
                // For counter type:
                // <indent>#<name> avg X sum Y
                builder.setLength(0);
                // prepare indent
                for (int i = 0; i < depth; ++i) {
                    if (i == depth - 1) {
                        if (flatOrderedNodes.peekFirst() == null || profileNode.lastChild) {
                            builder.append("  └─");
                        } else {
                            builder.append("  ├─");
                        }
                    } else if (!closed.contains(i + 1)) {
                        builder.append("  │ ");
                    } else {
                        builder.append("    ");
                    }
                }

                switch (profilerEntry.type()) {
                    case TIMER: {
                        ret.add(
                                builder
                                        .append(profilerEntry.name())
                                        .append(' ')
                                        .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format(((double)profileNode.totalTime / (double)totalTime) * 100.0))
                                        .append("% total, ")
                                        .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format(((double)profileNode.totalTime / (double)parentTime) * 100.0))
                                        .append("% parent, self ")
                                        .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format(((double)(profileNode.totalTime - profileNode.childrenTimingCount) / (double)totalTime) * 100.0))
                                        .append("% total, self ")
                                        .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format(((double)(profileNode.totalTime - profileNode.childrenTimingCount) / (double)profileNode.totalTime) * 100.0))
                                        .append("% children, avg ")
                                        .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format((double)profileNode.totalCount / (double)(noParent ? 1L : profileNode.parent.totalCount)))
                                        .append(" sum ")
                                        .append(DecimalFormats.NO_DECIMAL_PLACES.get().format(profileNode.totalCount))
                                        .append(", ")
                                        .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format((double)profileNode.totalTime / 1.0E6))
                                        .append("ms raw sum")
                                        .toString()
                        );
                        break;
                    }
                    case COUNTER: {
                        ret.add(
                                builder
                                        .append('#')
                                        .append(profilerEntry.name())
                                        .append(" avg ")
                                        .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format((double)profileNode.totalCount / (double)(noParent ? 1L : profileNode.parent.totalCount)))
                                        .append(" sum ")
                                        .append(DecimalFormats.NO_DECIMAL_PLACES.get().format(profileNode.totalCount))
                                        .toString()
                        );
                        break;
                    }
                    default: {
                        throw new IllegalStateException("Unknown type " + profilerEntry.type());
                    }
                }
            }

            return ret;
        }
    }
}
