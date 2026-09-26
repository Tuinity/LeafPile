package ca.spottedleaf.yamlconfig.adapter.collection;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SetTypeAdapter extends TypeAdapter<Set<Object>, List<Object>> {

    public static final SetTypeAdapter INSTANCE = new SetTypeAdapter();

    @Override
    public Set<Object> deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
        final TypeAdapter<List<Object>, List<Object>> listAdapter = (TypeAdapter<List<Object>, List<Object>>)registry.getAdapter(List.class);

        return new LinkedHashSet<>(listAdapter.deserialize(registry, input, type));
    }

    @Override
    public List<Object> serialize(final TypeAdapterRegistry registry, final Set<Object> value, final Type type) {
        final TypeAdapter<List<Object>, List<Object>> listAdapter = (TypeAdapter<List<Object>, List<Object>>)registry.getAdapter(List.class);

        return listAdapter.serialize(registry, new ArrayList<>(value), type);
    }
}