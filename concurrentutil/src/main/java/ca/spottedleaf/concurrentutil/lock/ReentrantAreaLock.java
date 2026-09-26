package ca.spottedleaf.concurrentutil.lock;

import ca.spottedleaf.concurrentutil.map.concurrent.longs.ConcurrentChainedLong2ReferenceHashTable;
import ca.spottedleaf.common.util.IntPairUtil;
import java.util.Objects;

public final class ReentrantAreaLock {

    public final int coordinateShift;

    // aggressive load factor to reduce contention
    private final ConcurrentChainedLong2ReferenceHashTable<Node> nodes = ConcurrentChainedLong2ReferenceHashTable.createWithCapacity(128, 0.2f);

    public ReentrantAreaLock(final int coordinateShift) {
        this.coordinateShift = coordinateShift;
    }

    public boolean isHeldByCurrentThread(final int x, final int y) {
        final Thread currThread = Thread.currentThread();
        final int shift = this.coordinateShift;
        final int sectionX = x >> shift;
        final int sectionY = y >> shift;

        final long coordinate = IntPairUtil.key(sectionX, sectionY);
        final Node node = this.nodes.get(coordinate);

        return node != null && node.thread == currThread;
    }

    public boolean isHeldByCurrentThread(final int centerX, final int centerY, final int radius) {
        return this.isHeldByCurrentThread(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }

    public boolean isHeldByCurrentThread(final int fromX, final int fromY, final int toX, final int toY) {
        if (fromX > toX || fromY > toY) {
            throw new IllegalArgumentException();
        }

        final Thread currThread = Thread.currentThread();
        final int shift = this.coordinateShift;
        final int fromSectionX = fromX >> shift;
        final int fromSectionY = fromY >> shift;
        final int toSectionX = toX >> shift;
        final int toSectionY = toY >> shift;

        for (int currY = fromSectionY; currY <= toSectionY; ++currY) {
            for (int currX = fromSectionX; currX <= toSectionX; ++currX) {
                final long coordinate = IntPairUtil.key(currX, currY);

                final Node node = this.nodes.get(coordinate);

                if (node == null || node.thread != currThread) {
                    return false;
                }
            }
        }

        return true;
    }

    public Node tryLock(final int x, final int y) {
        return this.tryLock(x, y, x, y);
    }

    public Node tryLock(final int centerX, final int centerY, final int radius) {
        return this.tryLock(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }

    public Node tryLock(final int fromX, final int fromY, final int toX, final int toY) {
        if (fromX > toX || fromY > toY) {
            throw new IllegalArgumentException();
        }

        final Thread currThread = Thread.currentThread();
        final int shift = this.coordinateShift;
        final int fromSectionX = fromX >> shift;
        final int fromSectionY = fromY >> shift;
        final int toSectionX = toX >> shift;
        final int toSectionY = toY >> shift;

        final long[] areaAffected = new long[(toSectionX - fromSectionX + 1) * (toSectionY - fromSectionY + 1)];
        int areaAffectedLen = 0;

        final Node ret = new Node(this, areaAffected, currThread);

        boolean failed = false;

        // try to fast acquire area
        for (int currY = fromSectionY; currY <= toSectionY; ++currY) {
            for (int currX = fromSectionX; currX <= toSectionX; ++currX) {
                final long coordinate = IntPairUtil.key(currX, currY);

                final Node prev = this.nodes.putIfAbsent(coordinate, ret);

                if (prev == null) {
                    areaAffected[areaAffectedLen++] = coordinate;
                    continue;
                }

                if (prev.thread != currThread) {
                    failed = true;
                    break;
                }
            }
        }

        if (!failed) {
            return ret;
        }

        // failed, undo logic
        if (areaAffectedLen != 0) {
            for (int i = 0; i < areaAffectedLen; ++i) {
                final long key = areaAffected[i];

                if (this.nodes.remove(key) != ret) {
                    throw new IllegalStateException();
                }
            }

            areaAffectedLen = 0;

            // since we inserted, we need to drain waiters
            ret.notifier.notifyThreads();
        }

        return null;
    }

    public Node lock(final int x, final int y) {
        final Thread currThread = Thread.currentThread();
        final int shift = this.coordinateShift;
        final int sectionX = x >> shift;
        final int sectionY = y >> shift;

        final long coordinate = IntPairUtil.key(sectionX, sectionY);
        final long[] areaAffected = new long[1];
        areaAffected[0] = coordinate;

        final Node ret = new Node(this, areaAffected, currThread);

        for (;;) {
            final Node park;

            // try to fast acquire area
            {
                final Node prev = this.nodes.putIfAbsent(coordinate, ret);

                if (prev == null) {
                    ret.areaAffectedLen = 1;
                    return ret;
                } else if (prev.thread != currThread) {
                    park = prev;
                } else {
                    // only one node we would want to acquire, and it's owned by this thread already
                    // areaAffectedLen = 0 already
                    return ret;
                }
            }

            park.notifier.waitUntilReady();
        }
    }

    public Node lock(final int centerX, final int centerY, final int radius) {
        return this.lock(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }

    public Node lock(final int fromX, final int fromY, final int toX, final int toY) {
        if (fromX > toX || fromY > toY) {
            throw new IllegalArgumentException();
        }

        final Thread currThread = Thread.currentThread();
        final int shift = this.coordinateShift;
        final int fromSectionX = fromX >> shift;
        final int fromSectionY = fromY >> shift;
        final int toSectionX = toX >> shift;
        final int toSectionY = toY >> shift;

        if (((fromSectionX ^ toSectionX) | (fromSectionY ^ toSectionY)) == 0) {
            return this.lock(fromX, fromY);
        }

        final long[] areaAffected = new long[(toSectionX - fromSectionX + 1) * (toSectionY - fromSectionY + 1)];
        int areaAffectedLen = 0;

        final Node ret = new Node(this, areaAffected, currThread);

        for (;;) {
            Node park = null;
            boolean addedToArea = false;
            boolean alreadyOwned = false;
            boolean allOwned = true;

            // try to fast acquire area
            for (int currY = fromSectionY; currY <= toSectionY; ++currY) {
                for (int currX = fromSectionX; currX <= toSectionX; ++currX) {
                    final long coordinate = IntPairUtil.key(currX, currY);

                    final Node prev = this.nodes.putIfAbsent(coordinate, ret);

                    if (prev == null) {
                        addedToArea = true;
                        allOwned = false;
                        areaAffected[areaAffectedLen++] = coordinate;
                        continue;
                    }

                    if (prev.thread != currThread) {
                        park = prev;
                        alreadyOwned = true;
                        break;
                    }
                }
            }

            // check for failure
            if ((park != null && addedToArea) || (park == null && alreadyOwned && !allOwned)) {
                // failure to acquire: added and we need to block, or improper lock usage
                for (int i = 0; i < areaAffectedLen; ++i) {
                    final long key = areaAffected[i];

                    if (this.nodes.remove(key) != ret) {
                        throw new IllegalStateException();
                    }
                }

                areaAffectedLen = 0;

                // since we inserted, we need to drain waiters
                ret.notifier.notifyThreadsAndBlock();
            }

            if (park == null) {
                if (alreadyOwned && !allOwned) {
                    ret.notifier.notifyThreads();
                    throw new IllegalStateException("Improper lock usage: Should never acquire intersecting areas");
                }
                ret.areaAffectedLen = areaAffectedLen;
                return ret;
            }

            // failed
        }
    }

    public void unlock(final Node node) {
        if (node.lock != this) {
            throw new IllegalStateException("Unlock target lock mismatch");
        }

        final long[] areaAffected = node.areaAffected;
        final int areaAffectedLen = node.areaAffectedLen;

        if (areaAffectedLen == 0) {
            // here we are not in the node map, and so do not need to remove from the node map or unblock any waiters
            return;
        }

        Objects.checkFromToIndex(0, areaAffectedLen, areaAffected.length);

        // remove from node map; allowing other threads to lock
        for (int i = 0; i < areaAffectedLen; ++i) {
            final long coordinate = areaAffected[i];
            if (this.nodes.remove(coordinate, node) != node) {
                throw new IllegalStateException();
            }
        }

        node.notifier.notifyThreads();
    }

    public static final class Node {

        private final ReentrantAreaLock lock;
        private final Notifier notifier = new Notifier(true);
        private final long[] areaAffected;
        private int areaAffectedLen;
        private final Thread thread;

        private Node(final ReentrantAreaLock lock, final long[] areaAffected, final Thread thread) {
            this.lock = lock;
            this.areaAffected = areaAffected;
            this.thread = thread;
        }

        @Override
        public String toString() {
            return "Node{" +
                "areaAffected=" + IntPairUtil.toString(this.areaAffected, 0, this.areaAffectedLen) +
                ", thread=" + this.thread +
                '}';
        }
    }
}
