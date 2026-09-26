package ca.spottedleaf.yamlconfig.adapter.collection;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EnumMapTypeAdapter<T extends Enum<T>> extends TypeAdapter<EnumMap<T, Object>, Map<String, Object>> {

    public static final EnumMapTypeAdapter INSTANCE = new EnumMapTypeAdapter();

    @Override
    public EnumMap<T, Object> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("EnumMap field must specify generic type");
        }
        final Type keyType = parameterizedType.getActualTypeArguments()[0];
        final Type valueType = parameterizedType.getActualTypeArguments()[1];
        if (input instanceof Map<?,?> castedInput) {
            final Class<T> enumType = keyType instanceof ParameterizedType enumParamType ?
                    (Class<T>)enumParamType.getRawType() : (Class<T>)keyType;

            final EnumMap<T, Object> ret = new EnumMap<>(enumType);

            for (final Map.Entry<?, ?> entry : castedInput.entrySet()) {
                final String key = (String)entry.getKey();
                final Object value = entry.getValue();

                final T deserializedKey = (T)registry.deserialize(key, keyType);
                final Object deserializedValue = registry.deserialize(value, valueType);

                ret.put(deserializedKey, deserializedValue);
            }

            return ret;
        }

        throw new IllegalArgumentException("Not a map type: " + input.getClass());
    }

    @Override
    public Map<String, Object> serialize(final TypeAdapterRegistry registry, final EnumMap<T, Object> value, final Type type) {
        final LinkedHashMap<String, Object> ret = new LinkedHashMap<>(value.size());

        final Type keyType = type instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[0] : null;
        final Type valueType = type instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[1] : null;

        for (final Map.Entry<T, Object> entry : value.entrySet()) {
            final T key = entry.getKey();
            final Object val = entry.getValue();

            final String serializedKey = (String)registry.serialize(key, keyType);
            final Object serializedVal = registry.serialize(val, valueType);

            ret.put(serializedKey, serializedVal);
        }

        return ret;
    }
}
