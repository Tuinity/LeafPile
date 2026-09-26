package ca.spottedleaf.yamlconfig.adapter.type;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class StringEnumTypeAdapter<T extends Enum<T>> extends TypeAdapter<Enum<T>, String> {

    public static final StringEnumTypeAdapter<? extends Enum<?>> INSTANCE = new StringEnumTypeAdapter<>();

    @Override
    public Enum<T> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (input instanceof String string) {
            if (type instanceof ParameterizedType parameterizedType) {
                return Enum.valueOf((Class<T>)parameterizedType.getRawType(), string);
            } else {
                return Enum.valueOf((Class<T>)type, string);
            }
        }
        throw new IllegalArgumentException("Not a string type: " + input.getClass());
    }

    @Override
    public String serialize(final TypeAdapterRegistry registry, final Enum<T> value, final Type type) {
        return value.name();
    }
}