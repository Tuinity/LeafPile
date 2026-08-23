package ca.spottedleaf.converter.types.impl.java;

import ca.spottedleaf.converter.types.ListType;
import ca.spottedleaf.converter.types.MapType;
import ca.spottedleaf.converter.types.ObjectType;
import ca.spottedleaf.converter.types.TypeUtil;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JavaTypeUtil extends TypeUtil<Object> {

    public static final JavaTypeUtil INSTANCE = new JavaTypeUtil();

    @Override
    public JavaListType createEmptyList() {
        return new JavaListType();
    }

    @Override
    public JavaMapType createEmptyMap() {
        return new JavaMapType();
    }

    @Override
    public Object convertFromBaseToGeneric(final Object input, final TypeUtil<?> to) {
        return convertJavaToGeneric(to, input);
    }

    @Override
    public Object convertGenericToGeneric(final Object valueGeneric, final TypeUtil<?> to) {
        if (valueGeneric == null || valueGeneric instanceof String || valueGeneric instanceof Boolean) {
            return valueGeneric;
        }
        if (valueGeneric instanceof Number number) {
            if (to.isCompatibleNumber(number)) {
                return valueGeneric;
            }
            throw new IllegalStateException("Unknown type: " + number.getClass());
        }
        if (valueGeneric.getClass().isArray()) {
            if (to.isCompatibleArray(valueGeneric)) {
                return valueGeneric;
            }
            throw new IllegalStateException("Unknown type: " + valueGeneric.getClass());
        }
        if (valueGeneric instanceof JavaListType listType) {
            return convertJava(to, listType.list);
        }
        if (valueGeneric instanceof JavaMapType mapType) {
            return convertJava(to, mapType.map);
        }

        throw new IllegalStateException("Unknown type: " + valueGeneric);
    }

    @Override
    public Object baseToGeneric(final Object input) {
        if (input instanceof LinkedHashMap map) {
            return new JavaMapType(map);
        }
        if (input instanceof ArrayList list) {
            return new JavaListType(list);
        }

        // null, number, string, array
        return input;
    }

    @Override
    public Object genericToBase(final Object input) {
        if (input instanceof JavaMapType map) {
            return map.map;
        }
        if (input instanceof JavaListType list) {
            return list.list;
        }

        // null, number, string, array
        return input;
    }

    @Override
    public boolean isCompatibleNumber(final Number number) {
        return switch (number) {
            case Byte b -> true;
            case Short s -> true;
            case Integer i -> true;
            case Long l -> true;
            case Float f -> true;
            case Double d -> true;
            case BigInteger bi -> true;
            case BigDecimal bd -> true;

            default -> false;
        };
    }

    @Override
    public boolean isCompatibleArray(final Object array) {
        return switch (array) {
            case byte[] b -> true;
            case short[] s -> true;
            case int[] i -> true;
            case long[] l -> true;

            default -> false;
        };
    }

    @Override
    public ObjectType getTypeBase(final Object value) {
        if (value instanceof Number) {
            if (value instanceof Byte) {
                return ObjectType.BYTE;
            } else if (value instanceof Short) {
                return ObjectType.SHORT;
            } else if (value instanceof Integer) {
                return ObjectType.INT;
            } else if (value instanceof Long) {
                return ObjectType.LONG;
            } else if (value instanceof Float) {
                return ObjectType.FLOAT;
            } else if (value instanceof Double) {
                return ObjectType.DOUBLE;
            } else if (value instanceof BigInteger) {
                return ObjectType.BIG_INTEGER;
            } else if (value instanceof BigDecimal) {
                return ObjectType.BIG_DECIMAL;
            } // else return null
        } else if (value instanceof LinkedHashMap<?,?>) {
            return ObjectType.MAP;
        } else if (value instanceof ArrayList<?>) {
            return ObjectType.LIST;
        } else if (value instanceof String) {
            return ObjectType.STRING;
        } else if (value.getClass().isArray()) {
            if (value instanceof byte[]) {
                return ObjectType.BYTE_ARRAY;
            } else if (value instanceof short[]) {
                return ObjectType.SHORT_ARRAY;
            } else if (value instanceof int[]) {
                return ObjectType.INT_ARRAY;
            } else if (value instanceof long[]) {
                return ObjectType.LONG_ARRAY;
            } // else return null
        }

        return null;
    }

    // does not check that the input is a base type
    @Override
    public Object deepCopy(final Object base) {
        if (base == null) {
            return null;
        }

        final Class<?> clazz = base.getClass();

        if (clazz.isArray()) {
            // note: all array types are immutable, no need to copy the elements
            final int len = Array.getLength(base);

            final Object newInstance = Array.newInstance(clazz.getComponentType(), len);

            System.arraycopy(base, 0, newInstance, 0, len);

            return newInstance;
        }

        if (clazz == LinkedHashMap.class) {
            return JavaMapType.deepCopy((LinkedHashMap<String, Object>)base);
        }
        if (clazz == ArrayList.class) {
            return JavaListType.deepCopy((ArrayList<Object>)base);
        }

        // is either: number, string, boolean: immutable
        return base;
    }

    private static Object convertJavaToGeneric(final TypeUtil<?> to, final Object java) {
        if (java == null) {
            return null;
        }

        final Class<?> clazz = java.getClass();

        if (clazz == JavaMapType.class) {
            return convertJava(to, ((JavaMapType)java).map);
        }
        if (clazz == JavaListType.class) {
            return convertJava(to, ((JavaListType)java).list);
        }

        // array, string, number
        return java;
    }

    public static MapType convertJava(final TypeUtil<?> to, final LinkedHashMap<String, Object> map) {
        final MapType ret = to.createEmptyMap();

        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            ret.setGeneric(entry.getKey(), convertJavaToGeneric(to, entry.getValue()));
        }

        return ret;
    }

    public static ListType convertJava(final TypeUtil<?> to, final ArrayList<Object> list) {
        final ListType ret = to.createEmptyList();

        for (int i = 0, len = list.size(); i < len; ++i) {
            ret.addGeneric(convertJavaToGeneric(to, list.get(i)));
        }

        return ret;
    }
}
