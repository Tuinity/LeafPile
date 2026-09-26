package ca.spottedleaf.sampler;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.concurrent.ConcurrentHashMap;

public final class EventRegistry {

    private static final ConcurrentHashMap<String, RegisteredEvent<? extends Record>> BY_NAME = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<? extends Record>, RegisteredEvent<? extends Record>> BY_CLASS = new ConcurrentHashMap<>();

    public static synchronized <T extends Record> RegisteredEvent<T> register(final String name, final Class<T> record) {
        if (!record.isRecord()) {
            throw new IllegalArgumentException("Class must be record");
        }

        final RecordComponent[] recordComponents = record.getRecordComponents();
        final Class<?>[] recordTypes = new Class[recordComponents.length];

        for (int i = 0; i < recordComponents.length; ++i) {
            final Class<?> type = recordTypes[i] = recordComponents[i].getType();
            if (!type.isPrimitive() && type != String.class) {
                throw new IllegalArgumentException("Event must have only primitive or String components");
            }
        }

        final Constructor<T> constructor;
        try {
            constructor = record.getConstructor(recordTypes);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }

        final RegisteredEvent<T> event = new RegisteredEvent<>(name, record, recordComponents, constructor);

        if (BY_CLASS.contains(record)) {
            throw new IllegalStateException("Record class is already registered");
        }

        if (null != BY_NAME.putIfAbsent(name, event)) {
            throw new IllegalStateException("Event is already registered with name");
        }

        BY_CLASS.put(record, event);

        return event;
    }

    public static RegisteredEvent<?> getByName(final String name) {
        return BY_NAME.get(name);
    }

    public static <T extends Record> RegisteredEvent<T> getByClass(final Class<T> cls) {
        // noinspection unchecked
        return (RegisteredEvent<T>)BY_CLASS.get(cls);
    }

    private EventRegistry() {}

    public static final record RegisteredEvent<T extends Record>(String name, Class<T> cls, RecordComponent[] recordComponents, Constructor<T> constructor) {}
}
