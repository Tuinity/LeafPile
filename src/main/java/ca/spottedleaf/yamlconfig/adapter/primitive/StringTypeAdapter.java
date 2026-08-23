package ca.spottedleaf.yamlconfig.adapter.primitive;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.config.YamlConfig;

import java.lang.reflect.Type;

public final class StringTypeAdapter extends TypeAdapter<String, String> {

    public static final StringTypeAdapter INSTANCE = new StringTypeAdapter();

    @Override
    public String deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof Boolean bool) {
            return String.valueOf(bool.booleanValue());
        }
        if (input instanceof Number number) {
            // note: always expect ParsedNumber
            throw new IllegalArgumentException("Unexpected number");
        }
        if (input instanceof YamlConfig.ParsedNumber parsedNumber) {
            return parsedNumber.original();
        }
        if (input instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Not a string type: " + input.getClass());
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final String value, final Type type) {
        return value;
    }
}
