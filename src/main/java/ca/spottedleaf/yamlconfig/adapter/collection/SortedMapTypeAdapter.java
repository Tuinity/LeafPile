package ca.spottedleaf.yamlconfig.adapter.collection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SortedMapTypeAdapter extends MapTypeAdapter {

    public static final SortedMapTypeAdapter SORTED_CASE_INSENSITIVE = new SortedMapTypeAdapter(String.CASE_INSENSITIVE_ORDER);
    public static final SortedMapTypeAdapter SORTED_CASE_SENSITIVE = new SortedMapTypeAdapter(null);

    private final Comparator<String> keyComparator;

    public SortedMapTypeAdapter(final Comparator<String> keyComparator) {
        this.keyComparator = keyComparator;
    }

    @Override
    protected LinkedHashMap<String, Object> sortMap(final LinkedHashMap<String, Object> map) {
        final int count = map.size();
        if (count <= 1) {
            return map;
        }

        final List<Map.Entry<String, Object>> sorted = new ArrayList<>(count);

        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            sorted.add(entry);
        }

        if (SortedMapTypeAdapter.this.keyComparator == null) {
            sorted.sort((final Map.Entry<String, Object> e1, final Map.Entry<String, Object> e2) -> {
                final String k1 = e1.getKey();
                final String k2 = e2.getKey();

                return k1.compareTo(k2);
            });
        } else {
            sorted.sort((final Map.Entry<String, Object> e1, final Map.Entry<String, Object> e2) -> {
                return SortedMapTypeAdapter.this.keyComparator.compare(e1.getKey(), e2.getKey());
            });
        }

        final LinkedHashMap<String, Object> ret = new LinkedHashMap<>(count);

        for (final Map.Entry<String, Object> entry : sorted) {
            ret.put(entry.getKey(), entry.getValue());
        }

        return ret;
    }
}
