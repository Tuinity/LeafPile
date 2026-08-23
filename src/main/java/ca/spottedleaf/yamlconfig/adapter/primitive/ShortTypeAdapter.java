package ca.spottedleaf.yamlconfig.adapter.primitive;

import ca.spottedleaf.common.util.IntegerUtil;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;

public final class ShortTypeAdapter extends TypeAdapter<Short, Short> {

    public static final ShortTypeAdapter INSTANCE = new ShortTypeAdapter();

    private static Short cast(final Object original, final long value) {
        if (value < (long)Short.MIN_VALUE || value > (long)Short.MAX_VALUE) {
            throw new IllegalArgumentException("Short value is out of range: " + original.toString());
        }
        return Short.valueOf((short)value);
    }

    @Override
    public Short deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
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

        throw new IllegalArgumentException("Not a short type: " + input.getClass());
    }

    @Override
    public Short serialize(final TypeAdapterRegistry registry, final Short value, final Type type) {
        return value;
    }
}
