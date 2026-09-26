package ca.spottedleaf.common.util;

import java.util.ArrayList;
import java.util.List;

public final class StringUtil {

    private static final String[] EMPTY_ARRAY = new String[0];

    /**
     * Like {@link String#split(String)}, but without regex. This split additionally
     * does not remove trailing empty strings. For example, splitting the value
     * "world" on 'd' will yield "worl" and "", whereas the {@link String} method will
     * yield only "worl".
     * @param value The string to split.
     * @param separator The separator.
     * @return The string split by the separator.
     */
    public static String[] split(final String value, final char separator) {
        final List<String> ret = new ArrayList<>();

        int currentIndex = 0;
        int lastEnd = 0;
        while ((currentIndex = (value.indexOf(separator, currentIndex))) != -1 && currentIndex < value.length()) {
            ret.add(value.substring(lastEnd, currentIndex));
            lastEnd = currentIndex + 1;
            currentIndex += 1;
        }

        ret.add(value.substring(lastEnd));

        return ret.toArray(EMPTY_ARRAY);
    }

    public static boolean startsWithIgnoreCase(final String string, final String prefix) {
        return string.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
