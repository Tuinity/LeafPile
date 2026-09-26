package ca.spottedleaf.converter.types;

import java.lang.reflect.Array;

public abstract class TypeUtil<T> {

    public abstract ListType createEmptyList();

    public abstract MapType createEmptyMap();

    public Object convertFromBaseToGeneric(final T input, final TypeUtil<?> to) {
        return this.convertGenericToGeneric(this.baseToGeneric(input), to);
    }

    public <D> D convertBaseToBase(final T input, final TypeUtil<D> to) {
        return to.genericToBase(this.convertFromBaseToGeneric(input, to));
    }

    public <D> D convertGenericToBase(final Object valueGeneric, final TypeUtil<D> to) {
        return to.genericToBase(this.convertGenericToGeneric(valueGeneric, to));
    }

    public abstract Object convertGenericToGeneric(final Object valueGeneric, final TypeUtil<?> to);

    public abstract Object baseToGeneric(final T input);

    public abstract T genericToBase(final Object input);

    public abstract boolean isCompatibleNumber(final Number number);

    public abstract boolean isCompatibleArray(final Object array);

    public abstract ObjectType getTypeBase(final T value);

    public abstract Object deepCopy(final T base);

    public static Object deepCopyGeneric(final Object generic) {
        if (generic == null) {
            return null;
        }

        final Class<?> clazz = generic.getClass();

        if (clazz.isArray()) {
            // note: all array types are immutable, no need to copy the elements
            final int len = Array.getLength(generic);

            final Object newInstance = Array.newInstance(clazz.getComponentType(), len);

            System.arraycopy(generic, 0, newInstance, 0, len);

            return newInstance;
        }

        if (generic instanceof MapType mapType) {
            return mapType.copy();
        }
        if (generic instanceof ListType listType) {
            return listType.copy();
        }

        // is either: number, string, boolean: immutable
        return generic;
    }
}
