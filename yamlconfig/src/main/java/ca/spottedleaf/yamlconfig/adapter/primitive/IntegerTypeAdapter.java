package ca.spottedleaf.yamlconfig.adapter.primitive;

import ca.spottedleaf.common.util.IntegerUtil;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;

public final class IntegerTypeAdapter extends TypeAdapter<Integer, Integer> {

    public static final IntegerTypeAdapter INSTANCE = new IntegerTypeAdapter();

    private static Integer cast(final Object original, final long value) {
        if (value < (long)Integer.MIN_VALUE || value > (long)Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Integer value is out of range: " + original.toString());
        }
        return Integer.valueOf((int)value);
    }

    @Override
    public Integer deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: always expect ParsedNumber
            throw new IllegalArgumentException("Unexpected number");
        }
        if (input instanceof YamlConfig.ParsedNumber parsedNumber) {
            return cast(input, IntegerUtil.longValueExact(parsedNumber.parsed()));
        }
        if (input instanceof String string) {
            // use floating point parsing to allow exponent notation
            return cast(input, IntegerUtil.longValueExact(BigDecimalTypeAdapter.parseFloat(string)));
        }

        throw new IllegalArgumentException("Not an integer type: " + input.getClass());
    }

    @Override
    public Integer serialize(final TypeAdapterRegistry registry, final Integer value, final Type type) {
        return value;
    }
}
