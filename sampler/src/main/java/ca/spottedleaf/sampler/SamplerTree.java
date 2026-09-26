package ca.spottedleaf.sampler;

import ca.spottedleaf.common.util.DecimalFormats;
import ca.spottedleaf.concurrentutil.map.concurrent.longs.ConcurrentChainedLong2ReferenceHashTable;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class SamplerTree {

    public static final long FLAG_RECORD_STACK_LINES = (1L << 0);
    private static final int FAKE_LINE_NUMBER = -123;

    private final AtomicLong nodeIdGenerator = new AtomicLong(1L);
    private final IdPool<StackTraceElement> stackTracePool;

    private final SamplerNode root = new SamplerNode(0L, -1L, null);
    private final ConcurrentChainedLong2ReferenceHashTable<SamplerNode> bySamplerId = new ConcurrentChainedLong2ReferenceHashTable<>();
    {
        this.bySamplerId.put(this.root.id, this.root);
    }
    private final long flags;

    private final SampleWriter writer;

    public SamplerTree(final SampleWriter writer, final long flags) {
        this.writer = writer;
        this.flags = flags;
        this.stackTracePool = new IdPool<>(writer == null ? null : (final Long id, final StackTraceElement stack) -> {
            writer.writeNewStack(id.longValue(), stack);
        });
    }

    private List<SamplerNode> getDFS() {
        final List<SamplerNode> ret = new ArrayList<>();
        final ArrayDeque<SamplerNode> queue = new ArrayDeque<>();

        queue.addFirst(this.root);

        SamplerNode node;
        while ((node = queue.pollFirst()) != null) {
            ret.add(node);

            for (final SamplerNode child : node.children.values()) {
                queue.addFirst(child);
            }
        }

        return ret;
    }

    private static final class ToStringNode {
        public final ToStringNode parent;
        public final SamplerNode node;
        public final List<ToStringNode> children = new ArrayList<>();
        public int depth = -1;
        public boolean lastChild;
        public long totalCount;
        public long selfCount;

        public ToStringNode(final ToStringNode parent, final SamplerNode node) {
            this.parent = parent;
            this.node = node;
        }
    }

    public List<String> dumpToString(final Long2LongOpenHashMap samplesById) {
        final Long2ReferenceOpenHashMap<ToStringNode> nodeMap = new Long2ReferenceOpenHashMap<>();

        // setup sampler node -> to string node mapping

        for (final SamplerNode node : this.getDFS()) {
            final ToStringNode toStringNode = new ToStringNode(node.parent == null ? null : nodeMap.get(node.parent.id), node);
            nodeMap.put(node.id, toStringNode);

            if (toStringNode.parent != null) {
                toStringNode.parent.children.add(toStringNode);
            }
        }

        // setup counts (note: samplesById only records the node hit, it does not include parents - we need to calculate this ourselves)

        for (final Iterator<Long2LongMap.Entry> iterator = samplesById.long2LongEntrySet().fastIterator(); iterator.hasNext();) {
            final Long2LongMap.Entry entry = iterator.next();
            final long id = entry.getLongKey();
            final long count = entry.getLongValue();

            final ToStringNode node = nodeMap.get(id);

            if (node == null) {
                throw new IllegalStateException("Unknown node: " + id);
            }

            node.selfCount = count;
            node.totalCount += count;

            ToStringNode parent = node;
            while ((parent = parent.parent) != null) {
                parent.totalCount += count;
            }
        }

        final ToStringNode root = nodeMap.get(this.root.id);
        final ArrayDeque<ToStringNode> orderedNodes = new ArrayDeque<>();
        orderedNodes.addFirst(root);

        final ArrayDeque<ToStringNode> flatOrderedNodes = new ArrayDeque<>();

        ToStringNode toStringNode;
        while ((toStringNode = orderedNodes.pollFirst()) != null) {
            // sort children by most count
            toStringNode.children.sort((final ToStringNode n1, final ToStringNode n2) -> {
                return Long.compare(n2.totalCount, n1.totalCount);
            });

            flatOrderedNodes.addLast(toStringNode);

            final int depth = toStringNode.depth;
            boolean first = true;
            for (int i = toStringNode.children.size() - 1; i >= 0; --i) {
                final ToStringNode child = toStringNode.children.get(i);
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
        }

        final List<String> ret = new ArrayList<>();

        final StringBuilder builder = new StringBuilder();
        final IntArrayList closed = new IntArrayList();
        while ((toStringNode = flatOrderedNodes.pollFirst()) != null) {
            final int depth = toStringNode.depth;
            closed.removeIf((int d) -> d >= depth);
            if (toStringNode.lastChild) {
                closed.add(depth);
            }
            if (toStringNode.parent == null) {
                // don't display root
                continue;
            }

            // format:
            // <stack element> - <percent of total>% (<proportion in self percent>% self)
            builder.setLength(0);
            // prepare indent
            for (int i = 0; i < depth; ++i) {
                if (i == depth - 1) {
                    if (flatOrderedNodes.peekFirst() == null || toStringNode.lastChild) {
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

            ret.add(
                builder
                    .append(this.stackTracePool.fromId(toStringNode.node.stackElementId).toString())
                    .append(" - ")
                    .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format(((double)toStringNode.totalCount / (double)root.totalCount) * 100.0))
                    .append("% (")
                    .append(DecimalFormats.THREE_DECIMAL_PLACES.get().format(((double)toStringNode.selfCount / (double)toStringNode.totalCount) * 100.0))
                    .append("% self)")
                    .toString()
            );
        }

        return ret;
    }

    IdPool<StackTraceElement> getStackTracePool() {
        return this.stackTracePool;
    }

    public int getNodeCount() {
        return this.bySamplerId.size();
    }

    public int getStackCount() {
        return this.stackTracePool.size();
    }

    void addNewNode(final long nodeId, final long stackId, final long parentId) {
        final SamplerNode parent = this.bySamplerId.get(parentId);
        if (parent == null) {
            throw new IllegalStateException("Unknown parent: " + parentId);
        }
        if (this.stackTracePool.fromId(stackId) == null) {
            throw new IllegalStateException("Unknown stack id: " + stackId);
        }

        final SamplerNode newNode = new SamplerNode(nodeId, stackId, parent);

        if (null != this.bySamplerId.putIfAbsent(nodeId, newNode)) {
            throw new IllegalStateException("Node id is already mapped");
        }

        if (parent.children.putIfAbsent(stackId, newNode) != null) {
            throw new IllegalStateException("Node stack id is already mapped on parent");
        }
    }

    boolean hasNode(final long nodeId) {
        return this.bySamplerId.containsKey(nodeId);
    }

    public StackTraceElement[] formStackTrace(final long nodeId) {
        final List<StackTraceElement> ret = new ArrayList<>();

        SamplerNode node = this.bySamplerId.get(nodeId);

        while (node != this.root) {
            ret.add(this.stackTracePool.fromId(node.stackElementId));
            node = node.parent;
        }

        return ret.toArray(new StackTraceElement[0]);
    }

    private StackTraceElement rewrite(final StackTraceElement element) {
        if ((this.flags & FLAG_RECORD_STACK_LINES) != 0L) {
            return element;
        }

        return new StackTraceElement(
                element.getClassLoaderName(), element.getModuleName(), element.getModuleVersion(),
                element.getClassName(), element.getMethodName(), element.getFileName(), FAKE_LINE_NUMBER
        );
    }

    public long getNodeFor(final StackTraceElement[] trace) {
        final long[] stacks = new long[trace.length];
        for (int i = trace.length - 1; i >= 0; --i) {
            stacks[(trace.length - 1) - i] = this.stackTracePool.getId(this.rewrite(trace[i]));
        }

        SamplerNode ret = this.root;

        for (final long stack : stacks) {
            ret = ret.findChild(stack);
        }

        return ret.id;
    }

    private final class SamplerNode {

        private final ConcurrentChainedLong2ReferenceHashTable<SamplerNode> children = new ConcurrentChainedLong2ReferenceHashTable<>();
        private final long id;
        private final long stackElementId;
        private final SamplerNode parent;

        private final ConcurrentChainedLong2ReferenceHashTable.BiLongObjectFunction<SamplerNode> computeNewChild = (final long forStackId) -> {
            final SamplerNode ret = new SamplerNode(SamplerTree.this.nodeIdGenerator.getAndIncrement(), forStackId, SamplerNode.this);

            SamplerTree.this.bySamplerId.put(ret.id, ret);

            if (SamplerTree.this.writer != null) {
                SamplerTree.this.writer.writeNewSampleNode(ret.id, ret.stackElementId, SamplerNode.this.id);
            }

            return ret;
        };

        private SamplerNode(final long id, final long stackElementId, final SamplerNode parent) {
            this.id = id;
            this.stackElementId = stackElementId;
            this.parent = parent;
        }

        public SamplerNode findChild(final long stackElementId) {
            return this.children.computeIfAbsent(stackElementId, this.computeNewChild);
        }
    }

    static final class IdPool<E> {
        private final AtomicLong idGenerator = new AtomicLong();
        private final ConcurrentChainedLong2ReferenceHashTable<E> byId = new ConcurrentChainedLong2ReferenceHashTable<>();
        private final ConcurrentHashMap<E, Long> toId = new ConcurrentHashMap<>();

        private final BiConsumer<Long, E> newEncounter;

        public IdPool(final BiConsumer<Long, E> newEncounter) {
            this.newEncounter = newEncounter;
        }

        public int size() {
            return this.toId.size();
        }

        public void add(final long id, final E value) {
            if (null != this.byId.put(id, value)) {
                throw new IllegalStateException("Id already mapped");
            }
            if (null != this.toId.put(value, Long.valueOf(id))) {
                this.byId.remove(id, value);
                throw new IllegalStateException("Value already mapped");
            }
        }

        private final Function<E, Long> addNewIdFunction = (final E keyInMap) -> {
            // note: this reserves id 0
            final long id = IdPool.this.idGenerator.incrementAndGet();
            IdPool.this.byId.put(id, keyInMap);

            final Long boxedId = Long.valueOf(id);

            if (IdPool.this.newEncounter != null) {
                IdPool.this.newEncounter.accept(boxedId, keyInMap);
            }

            return boxedId;
        };

        // note: id of first element is always 1
        public long getId(final E element) {
            return this.toId.computeIfAbsent(element, this.addNewIdFunction).longValue();
        }

        public E fromId(final long id) {
            return this.byId.get(id);
        }
    }
}
