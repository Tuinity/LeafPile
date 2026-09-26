package ca.spottedleaf.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class FlagParser {

    private static final String FLAG_PREFIX = "--";

    private final Map<String, String> flags;

    public FlagParser(final Set<String> recognizedFlags, final String[] input) {
        this(recognizedFlags, input, 0, input.length);
    }

    public FlagParser(final Set<String> recognizedFlags, final String[] input, final int inputOff) {
        this(recognizedFlags, input, inputOff, (input.length - inputOff));
    }

    public FlagParser(final Set<String> recognizedFlags, final String[] input, final int inputOff, final int len) {
        this(recognizedFlags, join(input, inputOff, len, " "));
    }

    public FlagParser(final Set<String> recognizedFlags, final String input) {
        this(parseFlags(input));

        if (recognizedFlags != null) {
            for (final String flag : this.flags.keySet()) {
                if (!recognizedFlags.contains(flag)) {
                    throw new IllegalArgumentException("Unknown flag: " + flag);
                }
            }
        }
    }

    private FlagParser(final Map<String, String> flags) {
        this.flags = flags;
    }

    private static int skipWhiteSpace(final String str, int idx, final int len) {
        while (idx < len && Character.isWhitespace(str.charAt(idx))) {
            ++idx;
        }

        return idx;
    }

    private static int skipNotWhiteSpace(final String str, int idx, final int len) {
        while (idx < len && !Character.isWhitespace(str.charAt(idx))) {
            ++idx;
        }

        return idx;
    }

    @FunctionalInterface
    public static interface FlagValueTabCompleter {

        public List<String> complete(final FlagParser parsedFlags, final String flag, final String value);

    }

    public static List<String> tabComplete(final String input, final Set<String> recognizedFlags,
                                           final FlagValueTabCompleter valueTabCompleter) {
        if (input.isEmpty()) {
            return CommandUtil.getSortedList(
                    recognizedFlags, FLAG_PREFIX::concat
            );
        }

        final Set<String> remainingFlags = new LinkedHashSet<>(recognizedFlags);

        final LinkedHashMap<String, String> parsed = new LinkedHashMap<>();
        for (int i = 0, len = input.length(); i < len;) {
            i = skipWhiteSpace(input, i, len);

            if (i >= len) {
                // missing flag
                return CommandUtil.getSortedList(
                        remainingFlags, FLAG_PREFIX::concat
                );
            }

            if (!input.startsWith(FLAG_PREFIX, i)) {
                // possibly invalid flag
                return CommandUtil.getSortedList(
                        remainingFlags, FLAG_PREFIX::concat, input.substring(i, skipNotWhiteSpace(input, i, len))
                );
            }
            i += FLAG_PREFIX.length();

            // --

            final int flagStart = i;
            i = skipNotWhiteSpace(input, i, len);
            final String flag = input.substring(flagStart, i).toLowerCase(Locale.ROOT);

            // --<flag>
            final int flagEnd = i;
            i = skipWhiteSpace(input, i, len);
            if (i >= len) {
                if (i == flagEnd) {
                    // we skipped no whitespace
                    return CommandUtil.getSortedList(
                            remainingFlags, FLAG_PREFIX::concat, FLAG_PREFIX.concat(flag)
                    );
                } else {
                    // we skipped whitespace, so we need to tab compete a value
                    return valueTabCompleter == null ? new ArrayList<>() : valueTabCompleter.complete(
                            new FlagParser(parsed), flag, ""
                    );
                }
            }

            final String value;

            if (input.charAt(i) != '"') {
                // simple: not quoted

                // detect empty flag value
                if (input.startsWith(FLAG_PREFIX, i)) {
                    value = "";

                    // don't tab complete, this is not the last value
                } else {
                    int valueStart = i;
                    i = skipNotWhiteSpace(input, i, len);
                    value = input.substring(valueStart, i);

                    if (value.indexOf('"') != -1) {
                        // unquoted ", invalid
                        return new ArrayList<>();
                    }

                    // is this the last value?
                    if (len == skipWhiteSpace(input, i, len)) {
                        // we can tab complete
                        return valueTabCompleter == null ? new ArrayList<>() : valueTabCompleter.complete(
                                new FlagParser(parsed), flag, value
                        );
                    } // else: continue to parse next key
                }
            } else {
                ++i;

                boolean escaped = false;
                boolean closed = false;
                final StringBuilder valueBuilder = new StringBuilder();

                for (;i < len; ++i) {
                    final char c = input.charAt(i);

                    if (c != '"' && c != '\\') {
                        // fast path for none of the escape chars
                        if (escaped) {
                            // discard escape
                            escaped = false;
                            valueBuilder.append('\\');
                        }
                        valueBuilder.append(c);
                        continue;
                    }

                    if (escaped) {
                        // two cases:
                        // 1. c == '\'
                        // 2. c == '"'

                        // case 1: handle "\\" by inserting single '\' (allow escaping the escape character)
                        // case 2: handle "\"" by inserting single '"' (allow escaping the quotation character)
                        // both cases covered by the same code
                        valueBuilder.append(c);
                        escaped = false;
                        continue;
                    } else {
                        if (c == '\\') {
                            escaped = true;
                            continue;
                        } else { // c == '"'
                            closed = true;
                            ++i;
                            break;
                        }
                    }
                }

                value = valueBuilder.toString();

                if (!closed) {
                    // we need to tab complete

                    // note: we need to re-format the completions so that we don't break the existing input
                    final List<String> ret = valueTabCompleter == null ? new ArrayList<>() : new ArrayList<>(
                            valueTabCompleter.complete(new FlagParser(parsed), flag, value)
                    );

                    for (final ListIterator<String> iterator = ret.listIterator(); iterator.hasNext();) {
                        final String completion = iterator.next();

                        iterator.set(
                                completion
                                        // escape \
                                        .replace("\\", "\\\\")
                                        // escape "
                                        .replace("\"", "\\\"")
                        );
                    }

                    return ret;
                }
            }

            if (parsed.containsKey(flag)) {
                // duplicate flag, invalid
                return new ArrayList<>();
            }

            parsed.put(flag, value);
            remainingFlags.remove(flag);
        }

        return new ArrayList<>();
    }

    private static LinkedHashMap<String, String> parseFlags(final String input) {
        final LinkedHashMap<String, String> ret = new LinkedHashMap<>();
        for (int i = 0, len = input.length(); i < len;) {
            i = skipWhiteSpace(input, i, len);

            if (i >= len) {
                break;
            }

            if (!input.startsWith(FLAG_PREFIX, i)) {
                throw new IllegalArgumentException("Expected flag starting with --");
            }
            i += FLAG_PREFIX.length();

            // --

            final int flagStart = i;
            i = skipNotWhiteSpace(input, i, len);
            final String flag = input.substring(flagStart, i).toLowerCase(Locale.ROOT);

            // --<flag>
            i = skipWhiteSpace(input, i, len);

            final String value;
            // detect empty flag value
            if (i >= len) {
                value = "";
            } else {
                // possibly has value
                if (input.charAt(i) != '"') {
                    // simple: not quoted

                    // detect empty flag value
                    if (input.startsWith(FLAG_PREFIX, i)) {
                        value = "";
                    } else {
                        int valueStart = i;
                        i = skipNotWhiteSpace(input, i, len);
                        value = input.substring(valueStart, i);

                        if (value.indexOf('"') != -1) {
                            throw new IllegalArgumentException("Value for flag '" + flag + "' has illegal \" (must be enclosed and escaped): " + value);
                        }
                    }
                } else {
                    ++i;

                    boolean escaped = false;
                    boolean closed = false;
                    final StringBuilder valueBuilder = new StringBuilder();

                    for (; i < len; ++i) {
                        final char c = input.charAt(i);

                        if (c != '"' && c != '\\') {
                            // fast path for none of the escape chars
                            if (escaped) {
                                // discard escape
                                escaped = false;
                                valueBuilder.append('\\');
                            }
                            valueBuilder.append(c);
                            continue;
                        }

                        if (escaped) {
                            // two cases:
                            // 1. c == '\'
                            // 2. c == '"'

                            // case 1: handle "\\" by inserting single '\' (allow escaping the escape character)
                            // case 2: handle "\"" by inserting single '"' (allow escaping the quotation character)
                            // both cases covered by the same code
                            valueBuilder.append(c);
                            escaped = false;
                            continue;
                        } else {
                            if (c == '\\') {
                                escaped = true;
                                continue;
                            } else { // c == '"'
                                closed = true;
                                ++i;
                                break;
                            }
                        }
                    }

                    if (!closed) {
                        throw new IllegalArgumentException("Value for flag '" + flag + "' is not closed");
                    }

                    value = valueBuilder.toString();
                }
            }

            if (ret.containsKey(flag)) {
                throw new IllegalArgumentException("Duplicate flag: " + flag);
            }

            ret.put(flag, value);
        }

        return ret;
    }


    public static String join(final String[] strs, final String with) {
        return join(strs, 0, with);
    }

    public static String join(final String[] strs, final int off, final String with) {
        return join(strs, off, strs.length - off, with);
    }

    public static String join(final String[] strs, final int off, final int len, final String with) {
        return String.join(with, Arrays.copyOfRange(strs, off, off + len));
    }

    public boolean hasFlag(final String flagName) {
        return this.flags.containsKey(flagName);
    }

    public Set<String> getFlags() {
        return this.flags.keySet();
    }

    public String getString(final String flagName) {
        final String ret = this.flags.get(flagName);
        if (ret == null) {
            throw new IllegalArgumentException("Flag not present: " + flagName);
        }
        return ret;
    }

    public String getStringOr(final String flagName, final String dfl) {
        return this.flags.getOrDefault(flagName, dfl);
    }

    public int getInt(final String flagName) {
        try {
            return Integer.parseInt(this.getString(flagName));
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Flag " + flagName + " is not an integer", ex);
        }
    }

    public long getLong(final String flagName) {
        try {
            return Long.parseLong(this.getString(flagName));
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Flag " + flagName + " is not an integer", ex);
        }
    }

    public double getDouble(final String flagName) {
        try {
            return Double.parseDouble(this.getString(flagName));
        } catch (final NumberFormatException ex) {
            throw new IllegalArgumentException("Flag " + flagName + " is not a decimal", ex);
        }
    }

    public <T> T getGeneric(final String flagName, final Function<String, T> parser) {
        final String value = this.getString(flagName);
        try {
            return parser.apply(value);
        } catch (final Throwable thr) {
            throw new IllegalArgumentException("Invalid format for " + flagName + ": " + thr.getMessage(), thr);
        }
    }

    public <T> List<T> getList(final String flagName, final Function<String, T> parser, final char separator) {
        final String value = this.getString(flagName);
        try {
            final List<T> ret = new ArrayList<>();

            for (final String val : StringUtil.split(value, separator)) {
                ret.add(parser.apply(val));
            }

            return ret;
        } catch (final Throwable thr) {
            throw new IllegalArgumentException("Invalid format for " + flagName + ": " + thr.getMessage(), thr);
        }
    }
}
