package ca.spottedleaf.common.util;

import ca.spottedleaf.common.time.CalenderDuration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;

public final class TimeUtil {

    public static final long DEADLINE_NOT_SET = Long.MIN_VALUE;

    /*
     * The comparator is not a valid comparator for every long value. To prove where it is valid, see below.
     *
     * For reflexivity, we have that x - x = 0. We then have that for any long value x that
     * compareTimes(x, x) == 0, as expected.
     *
     * For symmetry, we have that x - y = -(y - x) except for when y - x = Long.MIN_VALUE.
     * So, the difference between any times x and y must not be equal to Long.MIN_VALUE.
     *
     * As for the transitive relation, consider we have x,y such that x - y = a > 0 and z such that
     * y - z = b > 0. Then, we will have that the x - z > 0 is equivalent to a + b > 0. For long values,
     * this holds as long as a + b <= Long.MAX_VALUE.
     *
     * Also consider we have x, y such that x - y = a < 0 and z such that y - z = b < 0. Then, we will have
     * that x - z < 0 is equivalent to a + b < 0. For long values, this holds as long as a + b >= -Long.MAX_VALUE.
     *
     * Thus, the comparator is only valid for timestamps such that abs(c - d) <= Long.MAX_VALUE for all timestamps
     * c and d.
     */

    /**
     * This function is appropriate to be used as a {@link java.util.Comparator} between two timestamps, which
     * indicates whether the timestamps represented by t1, t2 that t1 is before, equal to, or after t2.
     */
    public static int compareTimes(final long t1, final long t2) {
        final long diff = t1 - t2;

        // HD, Section 2-7
        return (int)((diff >> 63) | (-diff >>> 63));
    }

    /**
     * Tests whether {@code t1} is before {@code t2}.
     */
    public static boolean isBefore(final long t1, final long t2) {
        return compareTimes(t1, t2) < 0L;
    }

    /**
     * Tests whether {@code t1} is the same as or before {@code t2}.
     */
    public static boolean isBeforeOrSame(final long t1, final long t2) {
        return compareTimes(t1, t2) <= 0L;
    }

    /**
     * Tests whether {@code t1} is after {@code t2}.
     */
    public static boolean isAfter(final long t1, final long t2) {
        return compareTimes(t1, t2) > 0L;
    }

    /**
     * Tests whether {@code t1} is the same as or after {@code t2}.
     */
    public static boolean isAfterOrSame(final long t1, final long t2) {
        return compareTimes(t1, t2) >= 0L;
    }

    public static long getGreatestTime(final long t1, final long t2) {
        final long diff = t1 - t2;
        return diff < 0L ? t2 : t1;
    }

    public static long getLeastTime(final long t1, final long t2) {
        final long diff = t1 - t2;
        return diff > 0L ? t2 : t1;
    }

    public static long clampTime(final long value, final long min, final long max) {
        final long diffMax = value - max;
        final long diffMin = value - min;

        if (diffMax > 0L) {
            return max;
        }
        if (diffMin < 0L) {
            return min;
        }
        return value;
    }

    public static ZonedDateTime parseEpochMilli(final DateTimeFormatter format, final String dateTime) throws DateTimeParseException {
        final TemporalAccessor parsed = format.parse(dateTime);

        final LocalDate localDate = parsed.query(TemporalQueries.localDate());

        LocalTime localTime = parsed.query(TemporalQueries.localTime());
        if (localTime == null) {
            localTime = LocalTime.of(0, 0);
        }

        ZoneId zoneId = parsed.query(TemporalQueries.zoneId());
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }

        return ZonedDateTime.of(localDate, localTime, zoneId);
    }

    public static String formatEpochMilli(final DateTimeFormatter format, final long timeMS, final ZoneId zoneId) {
        return format.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMS), zoneId == null ? ZoneId.systemDefault() : zoneId));
    }

    public static String formatDateWithRelative(final DateTimeFormatter format, final long timeMS, final ZoneId zoneId) {
        return formatDateWithRelative(format, timeMS, zoneId, ChronoUnit.MINUTES);
    }

    public static String formatDateWithRelative(final DateTimeFormatter format, final long timeMS, final ZoneId zoneId,
                                                final ChronoUnit relativeMaxResolution) {
        final String dateTime = formatEpochMilli(format, timeMS, zoneId);

        final ZonedDateTime now = ZonedDateTime.now();
        final ZonedDateTime target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeMS), zoneId);
        final boolean targetInFuture = target.isAfter(now);

        final CalenderDuration difference = CalenderDuration.difference(now, target, relativeMaxResolution, true);
        if (difference.isZero()) {
            return dateTime + " (approximately now)";
        } else {
            return dateTime + " (" + difference.toPrettyValue(relativeMaxResolution) + " in the " + (targetInFuture ? "future" : "past") + ")";
        }
    }

    private TimeUtil() {}
}
