package ca.spottedleaf.ioutil.util;

import ca.spottedleaf.common.util.ThrowUtil;
import ca.spottedleaf.ioutil.buffer.Buffer;
import java.util.Arrays;

public final class BufferReference {

    private Buffer buffer;
    private String key;

    public BufferReference(final Buffer buffer, final String key) {
        this.buffer = buffer;
        this.key = key;
    }

    public void release() {
        final Buffer buffer = this.buffer;
        final String key = this.key;

        if (buffer == null || key == null) {
            throw new IllegalStateException("Buffer is not referenced");
        }

        this.buffer = null;
        this.key = null;
        buffer.decReferenceCount(key);
    }

    public static void releaseAll(final BufferReference reference) {
        reference.release();
    }

    public static void releaseAll(final BufferReference... references) {
        releaseAll(Arrays.asList(references));
    }

    public static void releaseAll(final Iterable<BufferReference> references) {
        Throwable first = null;
        for (final BufferReference reference : references) {
            if (reference == null) {
                continue;
            }
            try {
                reference.release();
            } catch (final Throwable thr) {
                if (first == null) {
                    first = thr;
                } else {
                    first.addSuppressed(thr);
                }
            }
        }
        if (first != null) {
            ThrowUtil.throwUnchecked(first);
        }
    }
}
