package ca.spottedleaf.common.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class DecimalFormats {

    public static final ThreadLocal<DecimalFormat> FOUR_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.0000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> THREE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> NO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> FOUR_DECIMAL_PLACES_NO_COMMA = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("0.0000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> THREE_DECIMAL_PLACES_NO_COMMA = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("0.000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES_NO_COMMA = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES_NO_COMMA = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });
    public static final ThreadLocal<DecimalFormat> NO_DECIMAL_PLACES_NO_COMMA = ThreadLocal.withInitial(() -> {
        return new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ROOT));
    });

    private DecimalFormats() {}

}
