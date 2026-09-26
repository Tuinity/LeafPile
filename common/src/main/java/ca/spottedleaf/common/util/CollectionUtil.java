package ca.spottedleaf.common.util;

import java.util.Collection;

public final class CollectionUtil {

    public static String toString(final Collection<?> collection, final String name) {
        return CollectionUtil.toString(collection, name, new StringBuilder(name.length() + 128)).toString();
    }

    public static StringBuilder toString(final Collection<?> collection, final String name, final StringBuilder builder) {
        builder.append(name).append("{elements={");

        boolean first = true;

        for (final Object element : collection) {
            if (!first) {
                builder.append(", ");
            }
            first = false;

            builder.append('"').append(element).append('"');
        }

        return builder.append("}}");
    }

    public static boolean intersects(final Collection<?> c1, final Collection<?> c2) {
        for (final Object value : c2) {
            if (c1.contains(value)) {
                return true;
            }
        }

        return false;
    }

    private CollectionUtil() {
        throw new RuntimeException();
    }
}
