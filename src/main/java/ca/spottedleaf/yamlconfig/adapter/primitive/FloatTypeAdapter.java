package ca.spottedleaf.yamlconfig.adapter.primitive;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class FloatTypeAdapter extends TypeAdapter<Float, Float> {

    public static final FloatTypeAdapter INSTANCE = new FloatTypeAdapter();

    private static Float cast(final Object original, final BigDecimal value) {
        // note: silently ignore precision loss
        final float floatVal = value.floatValue();
        if (!Float.isFinite(floatVal)) {
            throw new IllegalArgumentException("Float value is out of range: " + original.toString());
        }
        return Float.valueOf(floatVal);
    }

    @Override
    public Float deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: always expect ParsedNumber
            throw new IllegalArgumentException("Unexpected number");
        }
        if (input instanceof YamlConfig.ParsedNumber parsedNumber) {
            final Number number = parsedNumber.parsed();
            if (number instanceof Float f) {
                return f;
            }
            if (number instanceof Double d) {
                final double raw = d.doubleValue();
                if (Double.isFinite(raw) && Math.abs(raw) > (double)Float.MAX_VALUE) {
                    throw new IllegalArgumentException("Float value is out of range: " + raw);
                }
                return Float.valueOf((float)raw);
            }

            if (number instanceof BigDecimal bigDecimal) {
                return cast(input, bigDecimal);
            }
            if (number instanceof BigInteger bigInteger) {
                return cast(input, new BigDecimal(bigInteger));
            }
            // must be integer type
            return Float.valueOf((float)number.longValue());
        }
        if (input instanceof String string) {
            final Number number = BigDecimalTypeAdapter.parseFloat(string);
            if (number instanceof Double d) {
                // inf or nan or -0
                return Float.valueOf((float)d.doubleValue());
            } // else: is BigDecimal
            return cast(input, (BigDecimal)number);
        }

        throw new IllegalArgumentException("Not a float type: " + input.getClass());
    }

    @Override
    public Float serialize(final TypeAdapterRegistry registry, final Float value, final Type type) {
        return value;
    }
}
