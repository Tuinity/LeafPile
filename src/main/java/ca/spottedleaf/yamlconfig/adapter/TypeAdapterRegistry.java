package ca.spottedleaf.yamlconfig.adapter;

import ca.spottedleaf.common.time.CalenderDuration;
import ca.spottedleaf.yamlconfig.InitialiseHook;
import ca.spottedleaf.yamlconfig.adapter.collection.CollectionTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.collection.EnumMapTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.collection.EnumSetTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.collection.ListTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.collection.MapTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.collection.SetTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.collection.SortedMapTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.BooleanTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.ByteTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.DoubleTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.FloatTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.IntegerTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.LongTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.ShortTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.primitive.StringTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.type.BigDecimalTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.type.BigIntegerTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.type.CalenderDurationTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.type.DefaultedTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.type.DurationTypeAdapter;
import ca.spottedleaf.yamlconfig.adapter.type.StringEnumTypeAdapter;
import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.type.DefaultedValue;
import ca.spottedleaf.yamlconfig.type.Duration;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TypeAdapterRegistry {

    private final Map<Class<?>, TypeAdapter<?, ?>> adapters = new HashMap<>();
    {
        this.adapters.put(boolean.class, BooleanTypeAdapter.INSTANCE);
        this.adapters.put(byte.class, ByteTypeAdapter.INSTANCE);
        this.adapters.put(short.class, ShortTypeAdapter.INSTANCE);
        this.adapters.put(int.class, IntegerTypeAdapter.INSTANCE);
        this.adapters.put(long.class, LongTypeAdapter.INSTANCE);
        this.adapters.put(float.class, FloatTypeAdapter.INSTANCE);
        this.adapters.put(double.class, DoubleTypeAdapter.INSTANCE);

        this.adapters.put(Boolean.class, BooleanTypeAdapter.INSTANCE);
        this.adapters.put(Byte.class, ByteTypeAdapter.INSTANCE);
        this.adapters.put(Short.class, ShortTypeAdapter.INSTANCE);
        this.adapters.put(Integer.class, IntegerTypeAdapter.INSTANCE);
        this.adapters.put(Long.class, LongTypeAdapter.INSTANCE);
        this.adapters.put(Float.class, FloatTypeAdapter.INSTANCE);
        this.adapters.put(Double.class, DoubleTypeAdapter.INSTANCE);

        this.adapters.put(String.class, StringTypeAdapter.INSTANCE);

        this.adapters.put(Collection.class, CollectionTypeAdapter.INSTANCE);
        this.adapters.put(List.class, ListTypeAdapter.INSTANCE);

        this.adapters.put(Set.class, SetTypeAdapter.INSTANCE);
        this.adapters.put(LinkedHashSet.class, SetTypeAdapter.INSTANCE);
        this.adapters.put(EnumSet.class, EnumSetTypeAdapter.INSTANCE);

        this.useSerializedMapOrder();
        this.adapters.put(EnumMap.class, EnumMapTypeAdapter.INSTANCE);

        this.adapters.put(BigInteger.class, BigIntegerTypeAdapter.INSTANCE);
        this.adapters.put(BigDecimal.class, BigDecimalTypeAdapter.INSTANCE);

        this.adapters.put(Duration.class, DurationTypeAdapter.INSTANCE);
        this.adapters.put(CalenderDuration.class, CalenderDurationTypeAdapter.INSTANCE);
        this.adapters.put(DefaultedValue.class, DefaultedTypeAdapter.INSTANCE);

        // allow overriding enum handling
        this.adapters.put(Enum.class, StringEnumTypeAdapter.INSTANCE);
    }

    /**
     * Preserve the order of {@link Map} and {@link LinkedHashMap} when de-serializing or serializing.
     * <p>
     *     This is the default mode for handling key-value data.
     * </p>
     * @return {@code this}
     */
    public TypeAdapterRegistry useSerializedMapOrder() {
        this.adapters.put(Map.class, MapTypeAdapter.INSTANCE);
        this.adapters.put(LinkedHashMap.class, MapTypeAdapter.INSTANCE);
        return this;
    }

    /**
     * Sorts {@link Map} and {@link LinkedHashMap} types by their {@link String} keys.
     * @param caseSensitive Whether to use case-insensitive ordering
     * @return {@code this}
     * @see String#compareTo(String)
     * @see String#compareToIgnoreCase(String)
     */
    public TypeAdapterRegistry useSortedMapOrder(final boolean caseSensitive) {
        final SortedMapTypeAdapter adapter = caseSensitive ? SortedMapTypeAdapter.SORTED_CASE_SENSITIVE : SortedMapTypeAdapter.SORTED_CASE_INSENSITIVE;

        this.adapters.put(Map.class, adapter);
        this.adapters.put(LinkedHashMap.class, adapter);

        return this;
    }

    /**
     * Sorts {@link Map} and {@link LinkedHashMap} types by their {@link String} keys using the specified
     * comparator.
     * @param comparator The specified comparator
     * @return {@code this}
     */
    public TypeAdapterRegistry useSortedMapOrder(final Comparator<String> comparator) {
        if (comparator == null) {
            // use natural order
            return this.useSortedMapOrder(true);
        }
        if (comparator == String.CASE_INSENSITIVE_ORDER) {
            return this.useSortedMapOrder(false);
        }

        final SortedMapTypeAdapter adapter = new SortedMapTypeAdapter(comparator);

        this.adapters.put(Map.class, adapter);
        this.adapters.put(LinkedHashMap.class, adapter);

        return this;
    }

    public TypeAdapterRegistry addAdapter(final Class<?> clazz, final TypeAdapter<?, ?> adapter) {
        this.replaceAdapter(clazz, adapter);

        return this;
    }

    public TypeAdapter<?, ?> replaceAdapter(final Class<?> clazz, final TypeAdapter<?, ?> adapter) {
        return this.adapters.put(clazz, adapter);
    }

    public TypeAdapter<?, ?> getAdapter(final Class<?> clazz) {
        // try to resolve directly
        TypeAdapter<?, ?> ret = this.adapters.get(clazz);
        if (ret == null && clazz.isEnum()) {
            // try to resolve by Enum
            ret = this.adapters.get(Enum.class);
        }

        return ret;
    }

    public Object deserialize(final Object input, final Type type) {
        TypeAdapter<?, ?> adapter = null;
        if (type instanceof Class<?> clazz) {
            adapter = this.getAdapter(clazz);
        }
        if (adapter == null && (type instanceof ParameterizedType parameterizedType)) {
            adapter = this.getAdapter((Class<?>)parameterizedType.getRawType());
        }

        if (adapter == null) {
            throw new IllegalArgumentException("No adapter for " + input.getClass() + " with type " + type);
        }

        return ((TypeAdapter)adapter).deserialize(this, input, type);
    }

    public Object serialize(final Object input, final Type type) {
        TypeAdapter<?, ?> adapter = null;
        if (type instanceof Class<?> clazz) {
            adapter = this.getAdapter(clazz);
        }
        if (adapter == null && (type instanceof ParameterizedType parameterizedType)) {
            adapter = this.getAdapter((Class<?>)parameterizedType.getRawType());
        }
        if (adapter == null) {
            adapter = this.getAdapter(input.getClass());
        }

        if (adapter == null) {
            throw new IllegalArgumentException("No adapter for " + input.getClass() + " with type " + type);
        }

        return ((TypeAdapter)adapter).serialize(this, input, type);
    }

    public <T> TypeAdapter<T, Map<Object, Object>> makeAdapter(final Class<? extends T> clazz, final Adaptable adaptable) throws Exception {
        final TypeAdapter<T, Map<Object, Object>> ret = new AutoTypeAdapter<>(this, clazz, adaptable);

        this.replaceAdapter(clazz, ret);

        return ret;
    }

    public <T> void callInitialisers(final T object) {
        if (object == null) {
            return;
        }

        if (object instanceof InitialiseHook initialiseHook) {
            initialiseHook.initialise();
        }

        final TypeAdapter<?, ?> adapter = this.getAdapter(object.getClass());

        if (!(adapter instanceof AutoTypeAdapter<?> autoTypeAdapter)) {
            return;
        }

        ((AutoTypeAdapter<T>)autoTypeAdapter).callInitialisers(object);
    }

    private static final class AutoTypeAdapter<T> extends TypeAdapter<T, Map<Object, Object>> {

        private final TypeAdapterRegistry registry;
        private final Constructor<? extends T> constructor;
        private final SerializableField[] fields;

        public AutoTypeAdapter(final TypeAdapterRegistry registry, final Class<? extends T> clazz, final Adaptable adaptable) throws Exception {
            this.registry = registry;
            this.constructor = clazz.getConstructor();
            this.fields = findSerializableFields(registry, clazz, adaptable);
        }

        private static TypeAdapter<?, ?> findOrMakeAdapter(final TypeAdapterRegistry registry, final Class<?> clazz) throws Exception {
            final TypeAdapter<?, ?> ret = registry.getAdapter(clazz);
            if (ret != null) {
                return ret;
            }

            for (final Annotation annotation : clazz.getAnnotations()) {
                if (annotation instanceof Adaptable adaptable) {
                    return registry.makeAdapter(clazz, adaptable);
                }
            }

            throw new IllegalArgumentException("No type adapter for " + clazz + " (Forgot @Adaptable?)");
        }

        private static void ensureGenericsInitialised(final TypeAdapterRegistry registry, final ParameterizedType type) throws Exception {
            for (final Type typeArgument : type.getActualTypeArguments()) {
                if (typeArgument instanceof Class<?> clazz) {
                    if (registry.getAdapter(clazz) != null) {
                        continue;
                    }

                    for (final Annotation annotation : clazz.getAnnotations()) {
                        if (annotation instanceof Adaptable adaptable) {
                            registry.makeAdapter(clazz, adaptable);
                            break;
                        }
                    }
                } else if (typeArgument instanceof ParameterizedType parameterizedType) {
                    ensureGenericsInitialised(registry, parameterizedType);
                }
            }
        }

        private static String makeSerializedKey(final String input) {
            final StringBuilder ret = new StringBuilder();

            final char[] chars = input.toCharArray();
            for (int i = 0; i < chars.length;) {
                while (i < chars.length && !Character.isUpperCase(chars[i])) {
                    ret.append(chars[i++]);
                }

                if (i < chars.length) {
                    ret.append('-');
                }

                while (i < chars.length && Character.isUpperCase(chars[i])) {
                    ret.append(Character.toLowerCase(chars[i++]));
                }
            }

            return ret.toString();
        }

        private static record SerializableField(
                Field field,
                boolean required,
                String comment,
                TypeAdapter<?, ?> adapter,
                boolean serialize,
                String serializedKey
        ) {}

        private static SerializableField[] findSerializableFields(final TypeAdapterRegistry registry, Class<?> clazz,
                                                                  final Adaptable adaptable) throws Exception {
            final List<SerializableField> ret = new ArrayList<>();
            do {
                for (final Field field : clazz.getDeclaredFields()) {
                    for (final Annotation annotation : field.getAnnotations()) {
                        if (!(annotation instanceof Serializable serializable)) {
                            continue;
                        }

                        field.setAccessible(true);

                        // make sure we auto initialise generic types
                        if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
                            ensureGenericsInitialised(registry, parameterizedType);
                        }

                        final TypeAdapter<?, ?> adapter;

                        if (serializable.adapter() != TypeAdapter.class) {
                            adapter = serializable.adapter().getConstructor().newInstance();
                        } else {
                            adapter = findOrMakeAdapter(registry, field.getType());
                        }

                        String serializedKey = serializable.serializedKey();
                        if (serializedKey.isEmpty()) {
                            serializedKey = makeSerializedKey(field.getName());
                        }

                        ret.add(new SerializableField(
                                field, serializable.required(), serializable.comment(), adapter,
                                serializable.serialize(), serializedKey
                        ));

                        break;
                    }
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);

            if (!adaptable.useDeclarationOrder()) {
                ret.sort((final SerializableField c1, final SerializableField c2) -> {
                    return c1.serializedKey.compareTo(c2.serializedKey);
                });
            }

            return ret.toArray(new SerializableField[0]);
        }

        @Override
        public T deserialize(final TypeAdapterRegistry registry, final Object input, final Type type) {
            if (!(input instanceof Map<?,?> inputMap)) {
                throw new IllegalArgumentException("Not a map type: " + input.getClass());
            }

            try {
                final T ret = this.constructor.newInstance();

                for (final SerializableField field : this.fields) {
                    final Object fieldValue = inputMap.get(field.serializedKey);

                    if (fieldValue == null) {
                        if (field.required) {
                            throw new IllegalArgumentException("Missing required field '" + field.serializedKey + "' in " + this.constructor.getDeclaringClass());
                        }
                        continue;
                    }

                    field.field.set(ret, field.adapter.deserialize(registry, fieldValue, field.field.getGenericType()));
                }

                return ret;
            } catch (final Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Map<Object, Object> serialize(final TypeAdapterRegistry registry, final T value, final Type type) {
            final LinkedHashMap<Object, Object> ret = new LinkedHashMap<>();

            for (final SerializableField field : this.fields) {
                if (!field.serialize) {
                    continue;
                }

                final Object fieldValue;
                try {
                    fieldValue = field.field.get(value);
                } catch (final Exception ex) {
                    throw new RuntimeException(ex);
                }

                if (fieldValue != null) {
                    ret.put(
                            field.comment.isBlank() ? field.serializedKey : new CommentedData(field.comment, field.serializedKey),
                            ((TypeAdapter)field.adapter).serialize(
                                    registry, fieldValue, field.field.getGenericType()
                            )
                    );
                }
            }

            return ret;
        }

        public void callInitialisers(final T value) {
            for (final SerializableField field : this.fields) {
                final Object fieldValue;
                try {
                    fieldValue = field.field.get(value);
                } catch (final Exception ex) {
                    throw new RuntimeException(ex);
                }

                this.registry.callInitialisers(fieldValue);
            }
        }
    }

    public static final class CommentedData {

        public final String comment;
        public final Object data;

        public CommentedData(final String comment, final Object data) {
            this.comment = comment;
            this.data = data;
        }
    }
}
