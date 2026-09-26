package ca.spottedleaf.yamlconfig.adapter.type;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class BigIntegerTypeAdapter extends TypeAdapter<BigInteger, String> {

    public static final BigIntegerTypeAdapter INSTANCE = new BigIntegerTypeAdapter();

    @Override
    public BigInteger deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: always expect ParsedNumber
            throw new IllegalArgumentException("Unexpected number");
        }
        if (input instanceof YamlConfig.ParsedNumber parsedNumber) {
            final Number number = parsedNumber.parsed();
            if (number instanceof BigInteger bigInteger) {
                return bigInteger;
            }
            if (number instanceof BigDecimal bigDecimal) {
                return bigDecimal.toBigIntegerExact();
            }
            if (number instanceof Float || number instanceof Double) {
                final double d = number.doubleValue();
                if (!Double.isFinite(d)) {
                    throw new IllegalArgumentException("Value is Infinite or NaN: " + d);
                }
                final double frac = d - Math.floor(d);
                if (frac != 0.0) {
                    throw new ArithmeticException("Value is not representable as an integer: " + d);
                }
                return new BigDecimal(d).toBigIntegerExact();
            } else { // byte, short, int, long
                return BigInteger.valueOf(number.longValue());
            }
        }
        if (input instanceof String string) {
            final Number number = BigDecimalTypeAdapter.parseFloat(string);
            if (number instanceof Double d) {
                final double val = d.doubleValue();
                if (!Double.isFinite(val)) {
                    // Infinity or NaN
                    throw new IllegalArgumentException("Value is Infinite or NaN: " + string);
                }
                // negative 0
                return BigInteger.valueOf((long)val);
            }
            return ((BigDecimal)number).toBigIntegerExact();
        }

        throw new IllegalArgumentException("Not an BigInteger type: " + input.getClass());
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final BigInteger value, final Type type) {
        return value.toString();
    }
}
