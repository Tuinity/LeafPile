package ca.spottedleaf.yamlconfig.adapter.primitive;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class DoubleTypeAdapter extends TypeAdapter<Double, Double> {

    public static final DoubleTypeAdapter INSTANCE = new DoubleTypeAdapter();

    private static Double cast(final Object original, final BigDecimal value) {
        final double doubleVal = value.doubleValue();
        if (!Double.isFinite(doubleVal)) {
            throw new IllegalArgumentException("Double float value is out of range: " + original.toString());
        }
        // note: silently ignore precision loss
        return Double.valueOf(doubleVal);
    }

    @Override
    public Double deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: always expect ParsedNumber
            throw new IllegalArgumentException("Unexpected number");
        }

        if (input instanceof YamlConfig.ParsedNumber parsedNumber) {
            final Number number = parsedNumber.parsed();
            if (number instanceof Float f) {
                return Double.valueOf((double)f.floatValue());
            }
            if (number instanceof Double d) {
                return d;
            }

            if (number instanceof BigDecimal bigDecimal) {
                return cast(input, bigDecimal);
            }
            if (number instanceof BigInteger bigInteger) {
                return cast(input, new BigDecimal(bigInteger));
            }
            // must be integer type
            return (double)number.longValue();
        }
        if (input instanceof String string) {
            final Number number = BigDecimalTypeAdapter.parseFloat(string);
            if (number instanceof Double d) {
                // inf or nan or -0
                return d;
            } // else: is BigDecimal
            return cast(input, (BigDecimal)number);
        }

        throw new IllegalArgumentException("Not a double float type: " + input.getClass());
    }

    @Override
    public Double serialize(final TypeAdapterRegistry registry, final Double value, final Type type) {
        return value;
    }
}
