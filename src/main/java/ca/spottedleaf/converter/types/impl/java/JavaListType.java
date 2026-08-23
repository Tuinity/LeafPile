package ca.spottedleaf.converter.types.impl.java;

import ca.spottedleaf.converter.types.ListType;
import ca.spottedleaf.converter.types.MapType;
import ca.spottedleaf.converter.types.ObjectType;
import ca.spottedleaf.converter.types.TypeUtil;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class JavaListType extends ListType {

    final ArrayList<Object> list;

    public JavaListType() {
        this.list = new ArrayList<>();
    }

    public JavaListType(final int capacity) {
        this.list = new ArrayList<>(capacity);
    }

    public JavaListType(final ArrayList<Object> list) {
        this.list = list;
    }

    @Override
    public int hashCode() {
        return this.list.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || obj.getClass() != JavaListType.class) {
            return false;
        }

        return this.list.equals(((JavaListType)obj).list);
    }

    @Override
    public String toString() {
        return "JavaListType{" +
                "list=" + this.list +
                '}';
    }

    @Override
    public TypeUtil<?> getTypeUtil() {
        return JavaTypeUtil.INSTANCE;
    }

    public static ArrayList<Object> deepCopy(final ArrayList<Object> list) {
        final int size = list.size();

        if (size == 0) {
            return list;
        }

        final ArrayList<Object> ret = new ArrayList<>(size);

        for (int i = 0; i < size; ++i) {
            ret.add(JavaTypeUtil.INSTANCE.deepCopy(list.get(i)));
        }

        return ret;
    }

    @Override
    public JavaListType copy() {
        return new JavaListType(deepCopy(this.list));
    }

    @Override
    public ObjectType getUniformType() {
        return ObjectType.MIXED;
    }

    @Override
    public int size() {
        return this.list.size();
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    @Override
    public void remove(final int index) {
        this.list.remove(index);
    }

    @Override
    public Object getGenericAndRemove(final int index) {
        return JavaTypeUtil.INSTANCE.baseToGeneric(this.list.remove(index));
    }

    @Override
    public Object getGeneric(final int index) {
        return JavaTypeUtil.INSTANCE.baseToGeneric(this.list.get(index));
    }

    @Override
    public void setGeneric(final int index, final Object to) {
        this.list.set(index, JavaTypeUtil.INSTANCE.genericToBase(to));
    }

    @Override
    public Number getNumber(final int index) {
        return this.getNumber(index, null);
    }

    @Override
    public Number getNumber(final int index, final Number dfl) {
        final Object ret = this.list.get(index);

        return ret instanceof Number number ? number : dfl;
    }

    @Override
    public BigInteger getBigInteger(final int index) {
        return this.getBigInteger(index, null);
    }

    @Override
    public BigInteger getBigInteger(final int index, final BigInteger dfl) {
        final Object ret = this.list.get(index);

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
    public void setBigInteger(final int index, final BigInteger to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, to);
    }

    @Override
    public BigDecimal getBigDecimal(final int index) {
        return this.getBigDecimal(index, null);
    }

    @Override
    public BigDecimal getBigDecimal(final int index, final BigDecimal dfl) {
        final Object ret = this.list.get(index);

        if (ret instanceof Number number) {
            if (number instanceof BigInteger bigInteger) {
                return new BigDecimal(bigInteger);
            } else if (number instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            } else {
                if (ret instanceof Float || ret instanceof Double) {
                    // note: use exact conversion
                    return new BigDecimal(number.doubleValue());
                } else {
                    return BigDecimal.valueOf(number.longValue());
                }
            }
        }

        return dfl;
    }

    @Override
    public void setBigDecimal(final int index, final BigDecimal to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, to);
    }

    @Override
    public byte getByte(final int index) {
        return this.getByte(index, (byte)0);
    }

    @Override
    public byte getByte(final int index, final byte dfl) {
        final Object value = this.list.get(index);

        if (value instanceof Number number) {
            return number.byteValue();
        }

        return dfl;
    }

    @Override
    public void setByte(final int index, final byte to) {
        this.list.set(index, Byte.valueOf(to));
    }

    @Override
    public short getShort(final int index) {
        return this.getShort(index, (short)0);
    }

    @Override
    public short getShort(final int index, final short dfl) {
        final Object value = this.list.get(index);

        if (value instanceof Number number) {
            return number.shortValue();
        }

        return dfl;
    }

    @Override
    public void setShort(final int index, final short to) {
        this.list.set(index, Short.valueOf(to));
    }

    @Override
    public int getInt(final int index) {
        return this.getInt(index, 0);
    }

    @Override
    public int getInt(final int index, final int dfl) {
        final Object value = this.list.get(index);

        if (value instanceof Number number) {
            return number.intValue();
        }

        return dfl;
    }

    @Override
    public void setInt(final int index, final int to) {
        this.list.set(index, Integer.valueOf(to));
    }

    @Override
    public long getLong(final int index) {
        return this.getLong(index, 0L);
    }

    @Override
    public long getLong(final int index, final long dfl) {
        final Object value = this.list.get(index);

        if (value instanceof Number number) {
            return number.longValue();
        }

        return dfl;
    }

    @Override
    public void setLong(final int index, final long to) {
        this.list.set(index, Long.valueOf(to));
    }

    @Override
    public float getFloat(final int index) {
        return this.getFloat(index, 0.0f);
    }

    @Override
    public float getFloat(final int index, final float dfl) {
        final Object value = this.list.get(index);

        if (value instanceof Number number) {
            return number.floatValue();
        }

        return dfl;
    }

    @Override
    public void setFloat(final int index, final float to) {
        this.list.set(index, Float.valueOf(to));
    }

    @Override
    public double getDouble(final int index) {
        return this.getDouble(index, 0.0D);
    }

    @Override
    public double getDouble(final int index, final double dfl) {
        final Object value = this.list.get(index);

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return dfl;
    }

    @Override
    public void setDouble(final int index, final double to) {
        this.list.set(index, Double.valueOf(to));
    }

    @Override
    public byte[] getBytes(final int index) {
        return this.getBytes(index, null);
    }

    @Override
    public byte[] getBytes(final int index, final byte[] dfl) {
        final Object value = this.list.get(index);

        return value instanceof byte[] arr ? arr : dfl;
    }

    @Override
    public void setBytes(final int index, final byte[] to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, to);
    }

    @Override
    public short[] getShorts(final int index) {
        return this.getShorts(index, null);
    }

    @Override
    public short[] getShorts(final int index, final short[] dfl) {
        final Object value = this.list.get(index);

        return value instanceof short[] arr ? arr : dfl;
    }

    @Override
    public void setShorts(final int index, final short[] to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, to);
    }

    @Override
    public int[] getInts(final int index) {
        return this.getInts(index, null);
    }

    @Override
    public int[] getInts(final int index, final int[] dfl) {
        final Object value = this.list.get(index);

        return value instanceof int[] arr ? arr : dfl;
    }

    @Override
    public void setInts(final int index, final int[] to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, to);
    }

    @Override
    public long[] getLongs(final int index) {
        return this.getLongs(index, null);
    }

    @Override
    public long[] getLongs(final int index, final long[] dfl) {
        final Object value = this.list.get(index);

        return value instanceof long[] arr ? arr : dfl;
    }

    @Override
    public void setLongs(final int index, final long[] to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, to);
    }

    @Override
    public ListType getList(final int index) {
        return this.getList(index, null);
    }

    @Override
    public ListType getList(final int index, final ListType dfl) {
        final Object value = this.list.get(index);

        return value instanceof ArrayList list ? new JavaListType(list) : dfl;
    }

    @Override
    public void setList(final int index, final ListType list) {
        Objects.requireNonNull(list, "To may not be null");

        this.list.set(index, ((JavaListType)list).list);
    }

    @Override
    public MapType getMap(final int index) {
        return this.getMap(index, null);
    }

    @Override
    public MapType getMap(final int index, final MapType dfl) {
        final Object value = this.list.get(index);

        return value instanceof LinkedHashMap map ? new JavaMapType(map) : dfl;
    }

    @Override
    public void setMap(final int index, final MapType to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, ((JavaMapType)to).map);
    }

    @Override
    public String getString(final int index) {
        return this.getString(index, null);
    }

    @Override
    public String getString(final int index, final String dfl) {
        final Object value = this.list.get(index);

        return value instanceof String string ? string : dfl;
    }

    @Override
    public void setString(final int index, final String to) {
        Objects.requireNonNull(to, "To may not be null");

        this.list.set(index, to);
    }

    @Override
    public void addGeneric(final Object value) {
        this.list.add(JavaTypeUtil.INSTANCE.genericToBase(Objects.requireNonNull(value)));
    }

    @Override
    public void addBigInteger(final BigInteger i) {
        this.list.add(Objects.requireNonNull(i));
    }

    @Override
    public void addBigInteger(final int index, final BigInteger i) {
        this.list.add(index, Objects.requireNonNull(i));
    }

    @Override
    public void addBigDecimal(final BigDecimal d) {
        this.list.add(Objects.requireNonNull(d));
    }

    @Override
    public void addBigDecimal(final int index, final BigDecimal d) {
        this.list.add(index, Objects.requireNonNull(d));
    }

    @Override
    public void addByte(final byte b) {
        this.list.add(Byte.valueOf(b));
    }

    @Override
    public void addByte(final int index, final byte b) {
        this.list.add(index, Byte.valueOf(b));
    }

    @Override
    public void addShort(final short s) {
        this.list.add(Short.valueOf(s));
    }

    @Override
    public void addShort(final int index, final short s) {
        this.list.add(index, Short.valueOf(s));
    }

    @Override
    public void addInt(final int i) {
        this.list.add(Integer.valueOf(i));
    }

    @Override
    public void addInt(final int index, final int i) {
        this.list.add(index, Integer.valueOf(i));
    }

    @Override
    public void addLong(final long l) {
        this.list.add(Long.valueOf(l));
    }

    @Override
    public void addLong(final int index, final long l) {
        this.list.add(index, Long.valueOf(l));
    }

    @Override
    public void addFloat(final float f) {
        this.list.add(Float.valueOf(f));
    }

    @Override
    public void addFloat(final int index, final float f) {
        this.list.add(index, Float.valueOf(f));
    }

    @Override
    public void addDouble(final double d) {
        this.list.add(Double.valueOf(d));
    }

    @Override
    public void addDouble(final int index, final double d) {
        this.list.add(index, Double.valueOf(d));
    }

    @Override
    public void addByteArray(final byte[] arr) {
        this.list.add(Objects.requireNonNull(arr));
    }

    @Override
    public void addByteArray(final int index, final byte[] arr) {
        this.list.add(index, Objects.requireNonNull(arr));
    }

    @Override
    public void addShortArray(final short[] arr) {
        this.list.add(Objects.requireNonNull(arr));
    }

    @Override
    public void addShortArray(final int index, final short[] arr) {
        this.list.add(index, Objects.requireNonNull(arr));
    }

    @Override
    public void addIntArray(final int[] arr) {
        this.list.add(Objects.requireNonNull(arr));
    }

    @Override
    public void addIntArray(final int index, final int[] arr) {
        this.list.add(index, Objects.requireNonNull(arr));
    }

    @Override
    public void addLongArray(final long[] arr) {
        this.list.add(Objects.requireNonNull(arr));
    }

    @Override
    public void addLongArray(final int index, final long[] arr) {
        this.list.add(index, Objects.requireNonNull(arr));
    }

    @Override
    public void addList(final ListType list) {
        this.list.add(((JavaListType)list).list);
    }

    @Override
    public void addList(final int index, final ListType list) {
        this.list.add(index, ((JavaListType)list).list);
    }

    @Override
    public void addMap(final MapType map) {
        this.list.add(((JavaMapType)map).map);
    }

    @Override
    public void addMap(final int index, final MapType map) {
        this.list.add(index, ((JavaMapType)map).map);
    }

    @Override
    public void addString(final String string) {
        this.list.add(Objects.requireNonNull(string));
    }

    @Override
    public void addString(final int index, final String string) {
        this.list.add(index, Objects.requireNonNull(string));
    }
}
