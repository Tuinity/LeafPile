package ca.spottedleaf.yamlconfig.adapter.generic;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;

public abstract class StringFormTypeAdapter<T> extends TypeAdapter<T, String> {
    @Override
    public final T deserialize(TypeAdapterRegistry registry, Object input, Type type) {
        final TypeAdapter<String, String> forString = (TypeAdapter<String, String>)registry.getAdapter(String.class);

        return this.fromString(forString.deserialize(registry, input, type));
    }

    @Override
    public final String serialize(final TypeAdapterRegistry registry, final T value, final Type type) {
        final TypeAdapter<String, String> forString = (TypeAdapter<String, String>)registry.getAdapter(String.class);

        return forString.serialize(registry, this.toString(value), type);
    }

    public abstract T fromString(final String value);

    public abstract String toString(final T value);
}
