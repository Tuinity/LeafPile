package ca.spottedleaf.yamlconfig.adapter.primitive;

import ca.spottedleaf.common.util.IntegerUtil;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;

public final class LongTypeAdapter extends TypeAdapter<Long, Long> {

    public static final LongTypeAdapter INSTANCE = new LongTypeAdapter();

    @Override
    public Long deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Number number) {
            // note: always expect ParsedNumber
            throw new IllegalArgumentException("Unexpected number");
        }
        if (input instanceof YamlConfig.ParsedNumber parsedNumber) {
            return Long.valueOf(IntegerUtil.longValueExact(parsedNumber.parsed()));
        }
        if (input instanceof String string) {
            return Long.valueOf(IntegerUtil.longValueExact(BigDecimalTypeAdapter.parseFloat(string)));
        }

        throw new IllegalArgumentException("Not a long type: " + input.getClass());
    }

    @Override
    public Long serialize(final TypeAdapterRegistry registry, final Long value, final Type type) {
        return value;
    }
}
