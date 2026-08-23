package ca.spottedleaf.common.time;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class CalenderDuration {

    private static final ChronoUnit[] ALL_CHRONO_UNITS = ChronoUnit.values();

    // in order of greatest to least
    private static final ChronoUnit[] SUPPORTED_UNITS = new ChronoUnit[] {
            ChronoUnit.YEARS,
            ChronoUnit.MONTHS,
            ChronoUnit.WEEKS,
            ChronoUnit.DAYS,
            ChronoUnit.HOURS,
            ChronoUnit.MINUTES,
            ChronoUnit.SECONDS,
            ChronoUnit.MILLIS,
            ChronoUnit.NANOS
    };
    private static final EnumSet<ChronoUnit> SUPPORTED_UNITS_SET = EnumSet.noneOf(ChronoUnit.class);
    static {
        SUPPORTED_UNITS_SET.addAll(Arrays.asList(SUPPORTED_UNITS));
    }

    // may only be integers in string form
    private static final ChronoUnit[] INTEGER_ONLY_UNITS = new ChronoUnit[] {
            ChronoUnit.YEARS,
            ChronoUnit.MONTHS,
            ChronoUnit.WEEKS,
            ChronoUnit.DAYS,
    };

    // may only be decimal in string form
    private static final ChronoUnit[] DECIMAL_ONLY_UNITS = new ChronoUnit[] {
            ChronoUnit.HOURS,
            ChronoUnit.MINUTES,
            ChronoUnit.SECONDS,
            ChronoUnit.MILLIS,
            ChronoUnit.NANOS
    };

    private static final EnumMap<ChronoUnit, String[]> SUFFIXES_BY_UNIT = new EnumMap<>(
            Map.of(
                    // These are only parsable through integers
                    ChronoUnit.YEARS, new String[] {
                            "y", "yr", "yrs", "year", "years"
                    },
                    ChronoUnit.MONTHS, new String[] {
                            "mo", "month", "months"
                    },
                    ChronoUnit.WEEKS, new String[] {
                            "w", "week", "weeks"
                    },
                    ChronoUnit.DAYS, new String[] {
                            "d", "day", "days"
                    },

                    // These may be parsed through decimals, up to NS resolution
                    ChronoUnit.HOURS, new String[] {
                            "h", "hr", "hrs", "hour", "hours"
                    },
                    ChronoUnit.MINUTES, new String[] {
                            "m", "min", "mins", "minute", "minutes"
                    },
                    ChronoUnit.SECONDS, new String[] {
                            "s", "sec", "secs", "second", "seconds"
                    },
                    ChronoUnit.MILLIS, new String[] {
                            "ms", "millis", "millisecond", "milliseconds"
                    },
                    ChronoUnit.NANOS, new String[] {
                            "ns", "nanos", "nanosecond", "nanoseconds"
                    }
            )
    );
    private static final EnumMap<ChronoUnit, String[]> PRETTY_SUFFIXES_BY_UNIT = new EnumMap<>(
            Map.of(
                    // These are only parsable through integers
                    ChronoUnit.YEARS, new String[] {
                            "year", "years"
                    },
                    ChronoUnit.MONTHS, new String[] {
                            "month", "months"
                    },
                    ChronoUnit.WEEKS, new String[] {
                            "week", "weeks"
                    },
                    ChronoUnit.DAYS, new String[] {
                            "day", "days"
                    },

                    // These may be parsed through decimals, up to NS resolution
                    ChronoUnit.HOURS, new String[] {
                            "hour", "hours"
                    },
                    ChronoUnit.MINUTES, new String[] {
                            "minute", "minutes"
                    },
                    ChronoUnit.SECONDS, new String[] {
                            "second", "seconds"
                    },
                    ChronoUnit.MILLIS, new String[] {
                            "millis"
                    },
                    ChronoUnit.NANOS, new String[] {
                            "nanos"
                    }
            )
    );
    private static final Map<String, ChronoUnit> UNIT_BY_SUFFIX = new HashMap<>();
    static {
        for (final Map.Entry<ChronoUnit, String[]> entry : SUFFIXES_BY_UNIT.entrySet()) {
            final ChronoUnit unit = entry.getKey();
            for (final String suffix : entry.getValue()) {
                final ChronoUnit curr = UNIT_BY_SUFFIX.putIfAbsent(suffix, unit);
                if (curr != null) {
                    throw new IllegalStateException("Unit conflict (suffix:" + suffix + ", unit1:" + unit + ", unit2:" + curr + ")");
                }
            }
        }
    }
    @SafeVarargs
    private static <T> Set<T> uniqueSet(final T... values) {
        final List<T> list = Arrays.asList(values);
        final Set<T> set = new LinkedHashSet<>(list);
        if (set.size() != list.size()) {
            throw new IllegalArgumentException();
        }
        return set;
    }

    static {
        final Set<ChronoUnit> integer = uniqueSet(INTEGER_ONLY_UNITS);
        final Set<ChronoUnit> decimal = uniqueSet(DECIMAL_ONLY_UNITS);
        if (integer.removeAll(decimal)) {
            throw new IllegalStateException();
        }

        integer.addAll(decimal);

        final Set<ChronoUnit> supported = uniqueSet(SUPPORTED_UNITS);

        if (!integer.equals(supported)) {
            throw new IllegalStateException();
        }

        if (!SUFFIXES_BY_UNIT.keySet().equals(supported)) {
            throw new IllegalStateException();
        }
        if (!PRETTY_SUFFIXES_BY_UNIT.keySet().equals(supported)) {
            throw new IllegalStateException();
        }
    }

    private final long[] values;
    private final long sumNanos;
    private final String string;

    private CalenderDuration(final long[] values, final String string) {
        if (values.length != ALL_CHRONO_UNITS.length) {
            throw new IllegalArgumentException();
        }
        this.values = values;
        this.string = string;
        this.sumNanos = this.computeNanos();
    }

    public boolean isZero() {
        return this.sumNanos == 0L;
    }

    private static boolean isDecimalCharacter(final int codePoint) {
        return isStrictDecimalCharacter(codePoint) ||
                codePoint == 'e' ||
                codePoint == 'E';
    }

    private static boolean isStrictDecimalCharacter(final int codePoint) {
        return Character.isDigit(codePoint) ||
                codePoint == '.' ||
                codePoint == '+' ||
                codePoint == '-';
    }

    // i.e from days to weeks
    private static void moveExcessive(final long[] values, final ChronoUnit from, final ChronoUnit to) {
        final long fromNS = from.getDuration().toNanos();
        final long toNS = to.getDuration().toNanos();

        final long factor = Math.divideExact(toNS, fromNS);
        // check: divides evenly (from < to)
        if ((toNS % fromNS) != 0L) {
            throw new IllegalArgumentException();
        }

        final long fromRaw = values[from.ordinal()];
        final long toVal = fromRaw / factor;

        values[from.ordinal()] = fromRaw % factor;
        values[to.ordinal()] += toVal;
    }

    private static EnumMap<ChronoUnit, BigDecimal> parseString(final String input) {
        if (input.isBlank()) {
            throw new IllegalArgumentException("Empty input");
        }

        final EnumMap<ChronoUnit, BigDecimal> ret = new EnumMap<>(ChronoUnit.class);

        for (int index = 0; index < input.length();) {
            int decimalStart = index;

            int decimalEnd = decimalStart;
            for (;;) {
                if (decimalEnd >= input.length()) {
                    throw new IllegalArgumentException("Truncated input");
                }

                final char c = input.charAt(decimalEnd);

                if (!Character.isWhitespace(c) && !isDecimalCharacter(c)){
                    break;
                }

                ++decimalEnd;
            }

            final BigDecimal bigDecimal = new BigDecimal(input.substring(decimalStart, decimalEnd).trim());

            if (bigDecimal.signum() == -1) {
                throw new IllegalArgumentException("Negative value");
            }

            int nextDecimalStart = decimalEnd;
            for (; nextDecimalStart < input.length(); nextDecimalStart++) {
                if (isStrictDecimalCharacter((int)input.charAt(nextDecimalStart))) {
                    break;
                }
            }

            final String suffix = input.substring(decimalEnd, nextDecimalStart).trim();
            final ChronoUnit unit = UNIT_BY_SUFFIX.get(suffix.toLowerCase(Locale.ROOT));

            if (unit == null) {
                throw new IllegalArgumentException("Unknown unit: " + suffix);
            }

            final BigDecimal prev = ret.putIfAbsent(unit, bigDecimal);

            if (prev != null) {
                throw new IllegalArgumentException("Duplicate value for unit: " + unit.name());
            }

            index = nextDecimalStart;
        }

        return ret;
    }

    public static CalenderDuration parse(final long value, final ChronoUnit unit) {
        if (!SUPPORTED_UNITS_SET.contains(unit)) {
            throw new IllegalArgumentException("Unsupported unit: " + unit);
        }

        final EnumMap<ChronoUnit, BigDecimal> values = new EnumMap<>(ChronoUnit.class);
        values.put(unit, BigDecimal.valueOf(value));

        return parse(values, null);
    }

    public static CalenderDuration parse(final String input) {
        return parse(parseString(input), input);
    }

    private static CalenderDuration parse(final EnumMap<ChronoUnit, BigDecimal> values, final String input) {
        try {
            final long[] rawVals = new long[ALL_CHRONO_UNITS.length];

            // these values must be integers exactly
            for (final ChronoUnit integerUnit : INTEGER_ONLY_UNITS) {
                rawVals[integerUnit.ordinal()] = values.getOrDefault(integerUnit, BigDecimal.ZERO).toBigIntegerExact().longValueExact();
            }

            moveExcessive(rawVals, ChronoUnit.DAYS, ChronoUnit.WEEKS);
            moveExcessive(rawVals, ChronoUnit.MONTHS, ChronoUnit.YEARS);

            // sum all the decimal values together
            BigDecimal decimalNanosSum = BigDecimal.ZERO;
            for (final ChronoUnit decimalUnit : DECIMAL_ONLY_UNITS) {
                decimalNanosSum = decimalNanosSum.add(values.getOrDefault(decimalUnit, BigDecimal.ZERO).multiply(new BigDecimal(decimalUnit.getDuration().toNanos())));
            }

            // round it
            BigInteger integerNanosSum = decimalNanosSum.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();

            // determine unit value by allowing higher unit to take from the nanos sum
            for (final ChronoUnit decimalUnit : DECIMAL_ONLY_UNITS) {
                final BigInteger nanosVal = BigInteger.valueOf(decimalUnit.getDuration().toNanos());

                rawVals[decimalUnit.ordinal()] = integerNanosSum.divide(nanosVal).longValueExact();
                integerNanosSum = integerNanosSum.mod(nanosVal);
            }

            // should be zero, last unit is nanos
            if (integerNanosSum.longValueExact() != 0L) {
                throw new IllegalStateException();
            }

            return new CalenderDuration(rawVals, input);
        } catch (final Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public ZonedDateTime offset(final ZonedDateTime input) {
        ZonedDateTime ret = input;

        for (final ChronoUnit unit : SUPPORTED_UNITS) {
            final long value = this.values[unit.ordinal()];
            if (value != 0L) {
                ret = ret.plus(value, unit);
            }
        }

        return ret;
    }

    private long computeNanos() {
        long ret = 0L;

        for (final ChronoUnit unit : SUPPORTED_UNITS) {
            final long value = this.values[unit.ordinal()];

            if (value == 0L) {
                continue;
            }

            ret = Math.addExact(ret, Math.multiplyExact(value, unit.getDuration().toNanos()));
        }

        return ret;
    }

    public long sumNanos() {
        return this.sumNanos;
    }

    public long sumMillis() {
        return TimeUnit.NANOSECONDS.toMillis(this.sumNanos());
    }

    /**
     * Returns the parsed form if one was provided, else {@code null}
     * @return The parsed form if one was provided, else {@code null}
     */
    public String getParsedForm() {
        return this.string;
    }

    public CalenderDuration floor(final ChronoUnit floor) {
        if (floor == ChronoUnit.NANOS) {
            return this;
        }

        final long[] values = this.values.clone();

        for (final ChronoUnit unit : SUPPORTED_UNITS) {
            if (unit.getDuration().toNanos() < floor.getDuration().toNanos()) {
                values[unit.ordinal()] = 0L;
            }
        }

        return new CalenderDuration(values, null);
    }

    public static CalenderDuration difference(final ZonedDateTime t1, final ZonedDateTime t2) {
        return difference(t1, t2, ChronoUnit.NANOS);
    }

    public static CalenderDuration difference(final ZonedDateTime t1, final ZonedDateTime t2, final ChronoUnit maxResolution) {
        return difference(t1, t2, maxResolution, false);
    }

    // ceil is true if values after the max resolution should add
    public static CalenderDuration difference(final ZonedDateTime t1, final ZonedDateTime t2, final ChronoUnit maxResolution,
                                              final boolean ceil) {
        final boolean t1Before = t1.isBefore(t2);
        ZonedDateTime min = t1Before ? t1 : t2;
        final ZonedDateTime minOriginal = min;
        final ZonedDateTime max = t1Before ? t2 : t1;

        final long[] values = new long[ALL_CHRONO_UNITS.length];
        for (final ChronoUnit unit : SUPPORTED_UNITS) {
            if (unit.getDuration().toNanos() < maxResolution.getDuration().toNanos()) {
                break;
            }

            final long value = min.until(max, unit);
            min = min.plus(value, unit);

            values[unit.ordinal()] = value;
        }

        final CalenderDuration floorValue = new CalenderDuration(values, null);
        if (!ceil || min.until(max, ChronoUnit.NANOS) == 0L) {
            // exact or we are in floor mode
            return floorValue;
        }

        // we need to ceil

        final ZonedDateTime newMax = max.plus(1L, maxResolution);
        return difference(minOriginal, newMax, maxResolution, false);
    }

    public static String differencePretty(final ZonedDateTime t1, final ZonedDateTime t2) {
        return differencePretty(t1, t2, ChronoUnit.NANOS);
    }

    public static String differencePretty(final ZonedDateTime t1, final ZonedDateTime t2, final ChronoUnit maxResolution) {
        return differencePretty(t1, t2, maxResolution, false);
    }

    public static String differencePretty(final ZonedDateTime t1, final ZonedDateTime t2, final ChronoUnit maxResolution, final boolean ceil) {
        return difference(t1, t2, maxResolution, ceil).toPrettyValue(maxResolution);
    }

    public static String differenceCompact(final ZonedDateTime t1, final ZonedDateTime t2) {
        return differenceCompact(t1, t2, ChronoUnit.NANOS);
    }

    public static String differenceCompact(final ZonedDateTime t1, final ZonedDateTime t2, final ChronoUnit maxResolution) {
        return differenceCompact(t1, t2, maxResolution, false);
    }

    public static String differenceCompact(final ZonedDateTime t1, final ZonedDateTime t2, final ChronoUnit maxResolution, final boolean ceil) {
        return difference(t1, t2, maxResolution, ceil).toString(maxResolution);
    }

    public String toPrettyValue() {
        return this.toPrettyValue(ChronoUnit.SECONDS);
    }

    private static String getPrettyUnit(final long value, final ChronoUnit unit) {
        final String[] suffixes = PRETTY_SUFFIXES_BY_UNIT.get(unit);
        if (suffixes.length == 1) {
            return suffixes[0];
        } else {
            return value == 1L ? suffixes[0] : suffixes[1];
        }
    }

    public String toPrettyValue(final ChronoUnit zeroUnit) {
        if (this.isZero()) {
            return "0 ".concat(getPrettyUnit(0L, zeroUnit));
        }

        final StringBuilder ret = new StringBuilder();

        for (final ChronoUnit unit : SUPPORTED_UNITS) {
            final long value = this.values[unit.ordinal()];

            if (value == 0L) {
                continue;
            }

            if (!ret.isEmpty()) {
                ret.append(' ');
            }

            ret.append(value).append(' ').append(getPrettyUnit(value, unit));
        }

        return ret.toString();
    }

    @Override
    public String toString() {
        return this.toString(ChronoUnit.SECONDS);
    }

    public String toString(final ChronoUnit zeroUnit) {
        if (this.isZero()) {
            return "0".concat(SUFFIXES_BY_UNIT.get(zeroUnit)[0]);
        }

        final StringBuilder ret = new StringBuilder();

        for (final ChronoUnit unit : SUPPORTED_UNITS) {
            final long value = this.values[unit.ordinal()];

            if (value == 0L) {
                continue;
            }

            ret.append(value).append(SUFFIXES_BY_UNIT.get(unit)[0]);
        }

        return ret.toString();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.values);
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof CalenderDuration calenderDuration && Arrays.equals(this.values, calenderDuration.values);
    }
}
