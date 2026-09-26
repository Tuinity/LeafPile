package ca.spottedleaf.yamlconfig.adapter.type;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class BigDecimalTypeAdapter extends TypeAdapter<BigDecimal, String> {

    public static final BigDecimalTypeAdapter INSTANCE = new BigDecimalTypeAdapter();

    public static Number parseFloat(final String input) {
        return parseFloat(input, false);
    }

    public static Number parseFloat(final String input, final boolean asBigDecimal) {
        // TODO allow '_' character
        // TODO allow integers with base format (see YamlConfig INT override)
        // handle inf, nan
        // handle inf, nan
        boolean positive;

        final String infnan;
        switch (input.charAt(0)) {
            case '+': {
                infnan = input.substring(1);
                positive = true;
                break;
            }
            case '-': {
                infnan = input.substring(1);
                positive = false;
                break;
            }
            default: {
                infnan = input;
                positive = true;
                break;
            }
        }
        // .inf/Infinity or .nan/NaN
        if (infnan.equalsIgnoreCase(".nan") || infnan.equals("NaN")) {
            if (asBigDecimal) {
                throw new IllegalArgumentException("NaN unsupported for BigDecimal parsing: " + input);
            }
            return Double.valueOf(Double.NaN);
        }
        if (infnan.equalsIgnoreCase(".inf") || infnan.equals("Infinity")) {
            if (asBigDecimal) {
                throw new IllegalArgumentException("Infinity unsupported for BigDecimal parsing: " + input);
            }
            return positive ? Double.valueOf(Double.POSITIVE_INFINITY) : Double.valueOf(Double.NEGATIVE_INFINITY);
        }

        // force all floats to be big decimal to avoid precision loss for BigDecimal types
        // note: default float/double adapter will parse from BigDecimal#toString
        final BigDecimal ret = new BigDecimal(input);
        if (!asBigDecimal && ret.compareTo(BigDecimal.ZERO) == 0) {
            // handle negative zero by passing Double instead
            return !positive ? Double.valueOf(-0.0) : ret;
        }
        return ret;
    }

    @Override
    public BigDecimal deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: always expect ParsedNumber
            throw new IllegalArgumentException("Unexpected number");
        }
        if (input instanceof YamlConfig.ParsedNumber parsedNumber) {
            final Number number = parsedNumber.parsed();
            if (number instanceof BigInteger bigInteger) {
                return new BigDecimal(bigInteger);
            }
            if (number instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            if (number instanceof Float || number instanceof Double) {
                final double d = number.doubleValue();
                if (!Double.isFinite(d)) {
                    throw new IllegalArgumentException("Value is Infinite or NaN: " + d);
                }
                return new BigDecimal(d);
            }

            // // byte, short, int, long
            return BigDecimal.valueOf(number.longValue());
        }
        if (input instanceof String string) {
            return (BigDecimal)parseFloat(string, true);
        }

        throw new IllegalArgumentException("Not an BigDecimal type: " + input.getClass());
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final BigDecimal value, final Type type) {
        return value.toString();
    }
}
