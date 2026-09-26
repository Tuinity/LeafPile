package ca.spottedleaf.ioutil.refcount;

import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import java.lang.invoke.VarHandle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ReferenceCounted {

    private final Set<String> references;
    private volatile long referenceCount;
    private static final VarHandle REFERENCE_COUNT_HANDLE = ConcurrentUtil.getVarHandle(ReferenceCounted.class, "referenceCount", long.class);

    public ReferenceCounted(final boolean debug, final String initialKey) {
        if (debug) {
            this.references = ConcurrentHashMap.newKeySet();
            this.references.add(initialKey);
        } else {
            this.references = null;
        }

        REFERENCE_COUNT_HANDLE.setRelease(this, 1L);
    }

    private void addReference(final String key) {
        if (!this.references.add(key)) {
            try {
                throw new IllegalStateException("Duplicate reference " + key);
            } finally {
                this.decReferenceCountNoDebug();
            }
        }
    }

    public final void incReferenceCount(final String key) {
        final boolean debug = this.references != null;
        for (long curr = (long)REFERENCE_COUNT_HANDLE.getVolatile(this);;) {
            if (curr == 0L) {
                throw new IllegalStateException("Reference count is zero!");
            }

            if (curr == (curr = (long)REFERENCE_COUNT_HANDLE.compareAndExchange(this, curr, curr + 1L))) {
                if (debug) {
                    this.addReference(key);
                }
                return;
            }
        }
    }

    private void removeReference(final String key) {
        if (!this.references.remove(key)) {
            throw new IllegalStateException("No such reference " + key);
        }
    }

    // rets true if the reference count reached 0
    public final boolean decReferenceCount(final String key) {
        if (this.references != null) {
            this.removeReference(key);
        }

        return this.decReferenceCountNoDebug();
    }

    private boolean decReferenceCountNoDebug() {
        final long dec = (long)REFERENCE_COUNT_HANDLE.getAndAdd(this, -1L);
        if (dec <= 0L) {
            throw new IllegalStateException("Negative reference counter");
        }
        if (dec != 1L) {
            return false;
        }

        this.refCountZero();

        return true;
    }

    public final void ensureRefCountZero() throws IllegalStateException {
        final long count = (long)REFERENCE_COUNT_HANDLE.getVolatile(this);
        if (count == 0L) {
            return;
        }

        if (this.references != null) {
            throw new IllegalStateException("Reference count not zero: " + count + ", references: " + String.join(",", this.references));
        } else {
            throw new IllegalStateException("Reference count not zero: " + count);
        }
    }

    protected abstract void refCountZero();
}
