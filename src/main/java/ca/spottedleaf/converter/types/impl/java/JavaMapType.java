package ca.spottedleaf.converter.types.impl.java;

import ca.spottedleaf.converter.types.ListType;
import ca.spottedleaf.converter.types.MapType;
import ca.spottedleaf.converter.types.ObjectType;
import ca.spottedleaf.converter.types.TypeUtil;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class JavaMapType extends MapType {

    final LinkedHashMap<String, Object> map;

    public JavaMapType() {
        this.map = new LinkedHashMap<>();
    }

    public JavaMapType(final int capacity) {
        this.map = new LinkedHashMap<>(capacity);
    }

    public JavaMapType(final int capacity, final float loadFactor) {
        this.map = new LinkedHashMap<>(capacity, loadFactor);
    }

    public JavaMapType(final LinkedHashMap<String, Object> map) {
        this.map = map;
    }

    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || obj.getClass() != JavaMapType.class) {
            return false;
        }

        return this.map.equals(((JavaMapType)obj).map);
    }

    @Override
    public String toString() {
        return "JavaMapType{" +
                "map=" + this.map +
                '}';
    }

    @Override
    public JavaListType createEmptyList() {
        return new JavaListType();
    }

    @Override
    public JavaMapType createEmptyMap() {
        return new JavaMapType();
    }

    @Override
    public TypeUtil<?> getTypeUtil() {
        return JavaTypeUtil.INSTANCE;
    }

    @Override
    public int size() {
        return this.map.size();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public void clear() {
        this.map.clear();
    }

    @Override
    public Set<String> keys() {
        return this.map.keySet();
    }

    public static LinkedHashMap<String, Object> deepCopy(final LinkedHashMap<String, Object> map) {
        final int size = map.size();

        if (size == 0) {
            return new LinkedHashMap<>();
        }

        final LinkedHashMap<String, Object> ret = new LinkedHashMap<>(size);

        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            ret.put(entry.getKey(), JavaTypeUtil.INSTANCE.deepCopy(entry.getValue()));
        }

        return ret;
    }

    @Override
    public JavaMapType copy() {
        return new JavaMapType(deepCopy(this.map));
    }

    @Override
    public boolean rename(final String fromKey, final String toKey) {
        final Object value = this.map.remove(fromKey);
        if (value == null) {
            return false;
        }

        this.map.put(toKey, value);
        return true;
    }

    @Override
    public boolean renameKeys(final Function<String, String> renamer) {
        record RenameEntry(String newKey, Object value) {}

        List<RenameEntry> renames = null;

        for (final Iterator<Map.Entry<String, Object>> iterator = this.map.entrySet().iterator(); iterator.hasNext();) {
            final Map.Entry<String, Object> entry = iterator.next();

            final String renamed = renamer.apply(entry.getKey());

            if (renamed == null) {
                continue;
            }

            final Object value = entry.getValue();

            iterator.remove();

            if (renames == null) {
                renames = new ArrayList<>();
            }

            renames.add(new RenameEntry(renamed, value));
        }

        if (renames == null) {
            return false;
        }

        for (int i = 0, len = renames.size(); i < len; ++i) {
            final RenameEntry entry = renames.get(i);

            this.map.put(entry.newKey, entry.value);
        }

        return true;
    }

    @Override
    public boolean hasKey(final String key) {
        return this.map.containsKey(key);
    }

    @Override
    public boolean hasKey(final String key, final ObjectType type) {
        final Object java = this.map.get(key);
        if (java == null) {
            return false;
        }

        final ObjectType valueType = JavaTypeUtil.INSTANCE.getTypeBase(java);

        return valueType == type || (type == ObjectType.NUMBER && valueType.isNumber());
    }

    @Override
    public void remove(final String key) {
        this.map.remove(key);
    }

    @Override
    public Object getGenericAndRemove(final String key) {
        return JavaTypeUtil.INSTANCE.baseToGeneric(this.map.remove(key));
    }

    @Override
    public Object getGeneric(final String key) {
        return JavaTypeUtil.INSTANCE.baseToGeneric(this.map.get(key));
    }

    @Override
    public Object getGeneric(final String key, final Object dfl) {
        final Object ret = this.map.get(key);

        return ret == null ? dfl : JavaTypeUtil.INSTANCE.baseToGeneric(ret);
    }

    @Override
    public Number getNumber(final String key) {
        return this.getNumber(key, null);
    }

    @Override
    public Number getNumber(final String key, final Number dfl) {
        final Object ret = this.map.get(key);

        return ret instanceof Number number ? number : dfl;
    }

    @Override
    public BigInteger getBigInteger(final String key) {
        return this.getBigInteger(key, null);
    }

    @Override
    public BigInteger getBigInteger(final String key, final BigInteger dfl) {
        final Object ret = this.map.get(key);

        if (ret instanceof Number number) {
            if (number instanceof BigInteger bigInteger) {
                return bigInteger;
            } else if (number instanceof BigDecimal bigDecimal) {
                return bigDecimal.toBigInteger();
            } else {
                return BigInteger.valueOf(number.longValue());
            }
        }

        return dfl;
    }

    @Override
    public void setBigInteger(final String key, final BigInteger val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, val);
    }

    @Override
    public BigDecimal getBigDecimal(final String key) {
        return this.getBigDecimal(key, null);
    }

    @Override
    public BigDecimal getBigDecimal(final String key, final BigDecimal dfl) {
        final Object ret = this.map.get(key);

        if (ret instanceof Number number) {
            if (number instanceof BigInteger bigInteger) {
                return new BigDecimal(bigInteger);
            } else if (number instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            } else {
                // note: use exact conversion for floating point
                if (ret instanceof Float f) {
                    return new BigDecimal((double)f.floatValue());
                }
                if (ret instanceof Double d) {
                    return new BigDecimal(d.doubleValue());
                }
                return BigDecimal.valueOf(number.longValue());
            }
        }

        return dfl;
    }

    @Override
    public void setBigDecimal(final String key, final BigDecimal val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, val);
    }

    @Override
    public boolean getBoolean(final String key) {
        return this.getBoolean(key, false);
    }

    @Override
    public boolean getBoolean(final String key, final boolean dfl) {
        final Object value = this.map.get(key);

        if (value == null) {
            return dfl;
        }

        if (value instanceof Boolean bool) {
            return bool.booleanValue();
        } else {
            return dfl;
        }
    }

    @Override
    public void setBoolean(final String key, final boolean val) {
        Objects.requireNonNull(key, "Key may not be null");

        this.map.put(key, Boolean.valueOf(val));
    }

    @Override
    public byte getByte(final String key) {
        return this.getByte(key, (byte)0);
    }

    @Override
    public byte getByte(final String key, final byte dfl) {
        final Object value = this.map.get(key);

        if (value instanceof Number number) {
            return number.byteValue();
        }

        return dfl;
    }

    @Override
    public void setByte(final String key, final byte val) {
        Objects.requireNonNull(key, "Key may not be null");

        this.map.put(key, Byte.valueOf(val));
    }

    @Override
    public short getShort(final String key) {
        return this.getShort(key, (short)0);
    }

    @Override
    public short getShort(final String key, final short dfl) {
        final Object value = this.map.get(key);

        if (value instanceof Number number) {
            return number.shortValue();
        }

        return dfl;
    }

    @Override
    public void setShort(final String key, final short val) {
        Objects.requireNonNull(key, "Key may not be null");

        this.map.put(key, Short.valueOf(val));
    }

    @Override
    public int getInt(final String key) {
        return this.getInt(key, 0);
    }

    @Override
    public int getInt(final String key, final int dfl) {
        final Object value = this.map.get(key);

        if (value instanceof Number number) {
            return number.intValue();
        }

        return dfl;
    }

    @Override
    public void setInt(final String key, final int val) {
        Objects.requireNonNull(key, "Key may not be null");

        this.map.put(key, Integer.valueOf(val));
    }

    @Override
    public long getLong(final String key) {
        return this.getLong(key, 0L);
    }

    @Override
    public long getLong(final String key, final long dfl) {
        final Object value = this.map.get(key);

        if (value instanceof Number number) {
            return number.longValue();
        }

        return dfl;
    }

    @Override
    public void setLong(final String key, final long val) {
        Objects.requireNonNull(key, "Key may not be null");

        this.map.put(key, Long.valueOf(val));
    }

    @Override
    public float getFloat(final String key) {
        return this.getFloat(key, 0.0f);
    }

    @Override
    public float getFloat(final String key, final float dfl) {
        final Object value = this.map.get(key);

        if (value instanceof Number number) {
            return number.floatValue();
        }

        return dfl;
    }

    @Override
    public void setFloat(final String key, final float val) {
        Objects.requireNonNull(key, "Key may not be null");

        this.map.put(key, Float.valueOf(val));
    }

    @Override
    public double getDouble(final String key) {
        return this.getDouble(key, 0.0D);
    }

    @Override
    public double getDouble(final String key, final double dfl) {
        final Object value = this.map.get(key);

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return dfl;
    }

    @Override
    public void setDouble(final String key, final double val) {
        Objects.requireNonNull(key, "Key may not be null");

        this.map.put(key, Double.valueOf(val));
    }

    @Override
    public byte[] getBytes(final String key) {
        return this.getBytes(key, null);
    }

    @Override
    public byte[] getBytes(final String key, final byte[] dfl) {
        final Object value = this.map.get(key);

        return value instanceof byte[] arr ? arr : dfl;
    }

    @Override
    public void setBytes(final String key, final byte[] val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, val);
    }

    @Override
    public short[] getShorts(final String key) {
        return this.getShorts(key, null);
    }

    @Override
    public short[] getShorts(final String key, final short[] dfl) {
        final Object value = this.map.get(key);

        return value instanceof short[] arr ? arr : dfl;
    }

    @Override
    public void setShorts(final String key, final short[] val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, val);
    }

    @Override
    public int[] getInts(final String key) {
        return this.getInts(key, null);
    }

    @Override
    public int[] getInts(final String key, final int[] dfl) {
        final Object value = this.map.get(key);

        return value instanceof int[] arr ? arr : dfl;
    }

    @Override
    public void setInts(final String key, final int[] val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, val);
    }

    @Override
    public long[] getLongs(final String key) {
        return this.getLongs(key, null);
    }

    @Override
    public long[] getLongs(final String key, final long[] dfl) {
        final Object value = this.map.get(key);

        return value instanceof long[] arr ? arr : dfl;
    }

    @Override
    public void setLongs(final String key, final long[] val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, val);
    }

    @Override
    public JavaListType getListUnchecked(final String key) {
        return this.getListUnchecked(key, null);
    }

    @Override
    public JavaListType getListUnchecked(final String key, final ListType dfl) {
        final Object value = this.map.get(key);
        return value instanceof ArrayList list ? new JavaListType(list) : (JavaListType)dfl;
    }

    @Override
    public JavaListType getList(final String key, final ObjectType type) {
        return this.getList(key, type, null);
    }

    @Override
    public JavaListType getOrCreateList(final String key, final ObjectType type) {
        JavaListType ret = this.getList(key, type);
        if (ret == null) {
            this.setList(key, ret = this.createEmptyList());
        }

        return ret;
    }

    @Override
    public JavaListType getList(final String key, final ObjectType type, final ListType dfl) {
        final JavaListType ret = this.getListUnchecked(key, null);
        final ObjectType retType;
        if (ret != null && ((retType = ret.getUniformType()) == type || retType == ObjectType.UNDEFINED || retType == ObjectType.NONE)) {
            return ret;
        } else {
            return (JavaListType)dfl;
        }
    }

    @Override
    public void setList(final String key, final ListType val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, ((JavaListType)val).list);
    }

    @Override
    public JavaMapType getMap(final String key) {
        return this.getMap(key, null);
    }

    @Override
    public JavaMapType getOrCreateMap(final String key) {
        JavaMapType ret = this.getMap(key);
        if (ret == null) {
            this.setMap(key, ret = this.createEmptyMap());
        }

        return ret;
    }

    @Override
    public JavaMapType getMap(final String key, final MapType dfl) {
        final Object value = this.map.get(key);
        return value instanceof LinkedHashMap map ? new JavaMapType(map) : (JavaMapType)dfl;
    }

    @Override
    public void setMap(final String key, final MapType val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, ((JavaMapType)val).map);
    }

    @Override
    public String getString(final String key) {
        return this.getString(key, null);
    }

    @Override
    public String getString(final String key, final String dfl) {
        final Object value = this.map.get(key);
        return value instanceof String string ? string : dfl;
    }

    @Override
    public void setString(final String key, final String val) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(val, "Value may not be null");

        this.map.put(key, val);
    }

    @Override
    public void setGeneric(final String key, final Object value) {
        Objects.requireNonNull(key, "Key may not be null");
        Objects.requireNonNull(value, "Value may not be null");

        this.map.put(key, JavaTypeUtil.INSTANCE.genericToBase(value));
    }
}
