package ca.spottedleaf.yamlconfig.adapter.collection;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class EnumSetTypeAdapter<T extends Enum<T>> extends TypeAdapter<EnumSet<T>, List<Object>> {

    public static final EnumSetTypeAdapter INSTANCE = new EnumSetTypeAdapter();

    @Override
    public EnumSet<T> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("EnumSet field must specify generic type");
        }

        final Type elemType = parameterizedType.getActualTypeArguments()[0];

        final Class<T> enumClazz = elemType instanceof ParameterizedType enumParamType ?
                (Class<T>)enumParamType.getRawType() : (Class<T>)elemType;

        final EnumSet<T> ret = EnumSet.noneOf(enumClazz);

        final TypeAdapter<List<Object>, List<Object>> listAdapter = (TypeAdapter<List<Object>, List<Object>>)registry.getAdapter(List.class);

        final List<Object> elems = listAdapter.deserialize(registry, input, type);

        for (final Object elem : elems) {
            ret.add((T)elem);
        }

        return ret;
    }

    @Override
    public List<Object> serialize(final TypeAdapterRegistry registry, final EnumSet<T> value, final Type type) {
        final TypeAdapter<List<Object>, List<Object>> listAdapter = (TypeAdapter<List<Object>, List<Object>>)registry.getAdapter(List.class);

        return listAdapter.serialize(registry, new ArrayList<>(value), type);
    }
}
