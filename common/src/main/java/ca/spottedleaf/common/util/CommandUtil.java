package ca.spottedleaf.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class CommandUtil {

    public static final char LIST_SEPARATOR = ',';

    public static List<String> getSortedList(final Iterable<String> iterable) {
        return getSortedList(iterable, (String)null);
    }

    public static List<String> getSortedList(final Iterable<String> iterable, final String prefix) {
        return getSortedList(iterable, Function.identity(), prefix);
    }

    public static <T> List<String> getSortedList(final Iterable<T> iterable, final Function<T, String> transform) {
        return getSortedList(iterable, transform, null);
    }

    public static <T> List<String> getSortedList(final Iterable<T> iterable, final Function<T, String> transform, final String prefix) {
        final List<String> ret = new ArrayList<>();
        for (final T val : iterable) {
            final String string = transform.apply(val);
            if (string != null && (prefix == null || StringUtil.startsWithIgnoreCase(string, prefix))) {
                ret.add(string);
            }
        }

        ret.sort(String.CASE_INSENSITIVE_ORDER);

        return ret;
    }

    public static UUID parseUUID(final String string) {
        if (string.length() == 32) {
            try {
                final long msb = Long.parseUnsignedLong(string, 0, 16, 16);
                final long lsb = Long.parseUnsignedLong(string, 16, 32, 16);
                return new UUID(msb, lsb);
            } catch (final Exception ex) {
                return null;
            }
        }
        if (string.length() == 36) {
            try {
                return UUID.fromString(string);
            } catch (final Exception ex) {
                return null;
            }
        }

        return null;
    }

    public static List<String> tabCompleteListAgainst(final String value, final Iterable<String> against,
                                                      final char separator) {
        return CommandUtil.tabCompleteListAgainst(value, against, separator, Function.identity());
    }

    public static <T> List<String> tabCompleteListAgainst(final String value, final Iterable<T> against,
                                                          final char separator, final Function<T, String> transform) {
        return CommandUtil.tabCompleteList(
                value, Function.identity(), separator,
                (final String input, final List<String> existing) -> {
                    final List<String> ret = new ArrayList<>();

                    for (final T test : against) {
                        final String testParsed = transform.apply(test);
                        if (testParsed == null) {
                            continue;
                        }
                        if (!StringUtil.startsWithIgnoreCase(testParsed, input)) {
                            continue;
                        }

                        if (!existing.contains(testParsed)) {
                            ret.add(testParsed);
                        }
                    }

                    return ret;
                }
        );
    }

    public static <T> List<String> tabCompleteList(final String input, final Function<String, T> parser, final char separator,
                                                   final BiFunction<String, List<T>, List<String>> completer) {
        try {
            final String[] split = StringUtil.split(input, separator);

            final List<T> existing = new ArrayList<>(split.length - 1);
            for (int i = 0, len = split.length - 1; i < len; ++i) {
                existing.add(parser.apply(split[i]));
            }

            final List<String> ret = new ArrayList<>(completer.apply(split[split.length - 1], existing));

            // prepend proceeding input to avoid clobbering existing values, as the completer only completes for
            // the last input string
            final String prepend = String.join(
                    String.valueOf(separator),
                    Arrays.copyOfRange(split, 0, split.length - 1)
            ).concat((split.length == 1 ? "" : String.valueOf(separator)));
            for (final ListIterator<String> iterator = ret.listIterator(); iterator.hasNext();) {
                final String completion = iterator.next();

                iterator.set(prepend.concat(completion));
            }

            return CommandUtil.getSortedList(ret);
        } catch (final Exception ex) {
            return new ArrayList<>();
        }
    }

    private CommandUtil() {}
}
