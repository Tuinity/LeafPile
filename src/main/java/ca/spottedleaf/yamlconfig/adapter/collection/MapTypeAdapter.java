package ca.spottedleaf.yamlconfig.adapter.collection;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapTypeAdapter extends TypeAdapter<Map<Object, Object>, Map<String, Object>> {

    public static final MapTypeAdapter INSTANCE = new MapTypeAdapter();

    protected LinkedHashMap<String, Object> sortMap(final LinkedHashMap<String, Object> map) {
        return map;
    }

    @Override
    public final Map<Object, Object> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("Collection field must specify generic type");
        }
        final Type keyType = parameterizedType.getActualTypeArguments()[0];
        final Type valueType = parameterizedType.getActualTypeArguments()[1];

        if (input instanceof Map<?,?>  mapInput) {
            final LinkedHashMap<String, Object> sorted = this.sortMap(
                    mapInput instanceof LinkedHashMap<?,?> ? (LinkedHashMap<String, Object>)mapInput : new LinkedHashMap<>((Map<String, Object>)mapInput)
            );

            final LinkedHashMap<Object, Object> ret = new LinkedHashMap<>(sorted.size());

            for (final Map.Entry<String, Object> entry : sorted.entrySet()) {
                final String key = entry.getKey();
                final Object value = entry.getValue();

                final Object deserializedKey = registry.deserialize(key, keyType);
                final Object deserializedValue = registry.deserialize(value, valueType);

                ret.put(deserializedKey, deserializedValue);
            }

            return ret;
        }

        throw new IllegalArgumentException("Not a map type: " + input.getClass());
    }

    @Override
    public final Map<String, Object> serialize(final TypeAdapterRegistry registry, final Map<Object, Object> value, final Type type) {
        final LinkedHashMap<String, Object> ret = new LinkedHashMap<>(value.size());

        final Type keyType = type instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[0] : null;
        final Type valueType = type instanceof ParameterizedType parameterizedType ? parameterizedType.getActualTypeArguments()[1] : null;

        for (final Map.Entry<Object, Object> entry : value.entrySet()) {
            final Object key = entry.getKey();
            final Object val = entry.getValue();

            final String serializedKey = (String)registry.serialize(key, keyType);
            final Object serializedVal = registry.serialize(val, valueType);

            ret.put(serializedKey, serializedVal);
        }

        return this.sortMap(ret);
    }
}
