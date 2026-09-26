package ca.spottedleaf.yamlconfig.adapter.collection;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CollectionTypeAdapter extends TypeAdapter<Collection<Object>, List<Object>> {

    public static final CollectionTypeAdapter INSTANCE = new CollectionTypeAdapter();

    @Override
    public Collection<Object> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        final TypeAdapter<List<Object>, List<Object>> listAdapter = (TypeAdapter<List<Object>, List<Object>>)registry.getAdapter(List.class);

        return listAdapter.deserialize(registry, input, type);
    }

    @Override
    public List<Object> serialize(final TypeAdapterRegistry registry, final Collection<Object> value, final Type type) {
        final TypeAdapter<List<Object>, List<Object>> listAdapter = (TypeAdapter<List<Object>, List<Object>>)registry.getAdapter(List.class);

        if (value instanceof List<Object> valueList) {
            return listAdapter.serialize(registry, valueList, type);
        } else {
            return listAdapter.serialize(registry, new ArrayList<>(value), type);
        }
    }
}
