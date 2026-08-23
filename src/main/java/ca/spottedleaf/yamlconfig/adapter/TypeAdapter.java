package ca.spottedleaf.yamlconfig.adapter;

import java.lang.reflect.Type;

/**
 * Class which defines serialization to and from YAML.
 *
 * @param <T> The deserialized type of the object
 * @param <S> The serialized type of the object
 */
public abstract class TypeAdapter<T, S> {

    /**
     * Deserializes the value from YAML.
     * @param registry The type adapter registry context.
     * @param input The object from YAML.
     * @param type The type information associated with the deserialized object.
     * @return The deserialized form of the object.
     */
    public abstract T deserialize(final TypeAdapterRegistry registry, final Object input, final Type type);

    /**
     * Serializes the value to YAML.
     * @param registry The type adapter registry context.
     * @param value The deserialized form of the object.
     * @param type The type information associated with the deserialized object.
     * @return The serialized form of the object.
     */
    public abstract S serialize(final TypeAdapterRegistry registry, final T value, final Type type);

}
