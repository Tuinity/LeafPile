package ca.spottedleaf.common.util;

public final class ThrowUtil {

    private ThrowUtil() {}

    public static <T extends Throwable> void throwUnchecked(final Throwable thr) throws T {
        // noinspection unchecked
        throw (T)thr;
    }

    public static void closeAll(final AutoCloseable... closeables) {
        Throwable first = null;
        for (final AutoCloseable closeable : closeables) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (final Throwable thr) {
                if (first == null) {
                    first = thr;
                } else {
                    first.addSuppressed(thr);
                }
            }
        }
        if (first != null) {
            throwUnchecked(first);
        }
    }
}
