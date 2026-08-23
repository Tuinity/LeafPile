package ca.spottedleaf.yamlconfig.adapter.primitive;

import ca.spottedleaf.common.util.IntegerUtil;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;

public final class ByteTypeAdapter extends TypeAdapter<Byte, Byte> {

    public static final ByteTypeAdapter INSTANCE = new ByteTypeAdapter();

    private static Byte cast(final Object original, final long value) {
        if (value < (long)Byte.MIN_VALUE || value > (long)Byte.MAX_VALUE) {
            throw new IllegalArgumentException("Byte value is out of range: " + original.toString());
        }
        return Byte.valueOf((byte)value);
    }

    @Override
    public Byte deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
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

        throw new IllegalArgumentException("Not a byte type: " + input.getClass());
    }

    @Override
    public Byte serialize(final TypeAdapterRegistry registry, final Byte value, final Type type) {
        return value;
    }
}
