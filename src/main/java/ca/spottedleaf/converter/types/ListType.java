package ca.spottedleaf.converter.types;

import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class ListType {

    public abstract TypeUtil<?> getTypeUtil();

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(final Object other);

    @Override
    public abstract String toString();

    public abstract ListType createEmptyList();

    public abstract MapType createEmptyMap();

    // Provides a deep copy of this list
    public abstract ListType copy();

    // Returns the type of all elements in this list. Returns NONE if empty, returns UNDEFINED if not supported, MIXED if mixed types
    public abstract ObjectType getUniformType();

    public abstract int size();

    public abstract boolean isEmpty();

    public abstract void remove(final int index);

    public abstract Object getGenericAndRemove(final int index);

    public abstract Object getGeneric(final int index);

    public abstract void setGeneric(final int index, final Object to);

    // types here are strict. if the type on get does not match the underlying type, will throw - except for the
    // default parameter methods, in such cases the default value will be returned.

    public abstract Number getNumber(final int index);

    public abstract Number getNumber(final int index, final Number dfl);

    public abstract BigInteger getBigInteger(final int index);

    public abstract BigInteger getBigInteger(final int index, final BigInteger dfl);

    public abstract void setBigInteger(final int index, final BigInteger to);

    public abstract BigDecimal getBigDecimal(final int index);

    public abstract BigDecimal getBigDecimal(final int index, final BigDecimal dfl);

    public abstract void setBigDecimal(final int index, final BigDecimal to);

    // if the value at index is a Number but not a byte, then returns the number casted to byte.
    public abstract byte getByte(final int index);

    // if the value at index is a Number but not a byte, then returns the number casted to byte.
    public abstract byte getByte(final int index, final byte dfl);

    public abstract void setByte(final int index, final byte to);

    // if the value at index is a Number but not a short, then returns the number casted to short.
    public abstract short getShort(final int index);

    // if the value at index is a Number but not a short, then returns the number casted to short.
    public abstract short getShort(final int index, final short dfl);

    public abstract void setShort(final int index, final short to);

    // if the value at index is a Number but not a int, then returns the number casted to int.
    public abstract int getInt(final int index);

    // if the value at index is a Number but not a int, then returns the number casted to int.
    public abstract int getInt(final int index, final int dfl);

    public abstract void setInt(final int index, final int to);

    // if the value at index is a Number but not a long, then returns the number casted to long.
    public abstract long getLong(final int index);

    // if the value at index is a Number but not a long, then returns the number casted to long.
    public abstract long getLong(final int index, final long dfl);

    public abstract void setLong(final int index, final long to);

    // if the value at index is a Number but not a float, then returns the number casted to float.
    public abstract float getFloat(final int index);

    // if the value at index is a Number but not a float, then returns the number casted to float.
    public abstract float getFloat(final int index, final float dfl);

    public abstract void setFloat(final int index, final float to);

    // if the value at index is a Number but not a double, then returns the number casted to double.
    public abstract double getDouble(final int index);

    // if the value at index is a Number but not a double, then returns the number casted to double.
    public abstract double getDouble(final int index, final double dfl);

    public abstract void setDouble(final int index, final double to);

    public abstract byte[] getBytes(final int index);

    public abstract byte[] getBytes(final int index, final byte[] dfl);

    public abstract void setBytes(final int index, final byte[] to);

    public abstract short[] getShorts(final int index);

    public abstract short[] getShorts(final int index, final short[] dfl);

    public abstract void setShorts(final int index, final short[] to);

    public abstract int[] getInts(final int index);

    public abstract int[] getInts(final int index, final int[] dfl);

    public abstract void setInts(final int index, final int[] to);

    public abstract long[] getLongs(final int index);

    public abstract long[] getLongs(final int index, final long[] dfl);

    public abstract void setLongs(final int index, final long[] to);

    public abstract ListType getList(final int index);

    public abstract ListType getList(final int index, final ListType dfl);

    public abstract void setList(final int index, final ListType list);

    public abstract MapType getMap(final int index);

    public abstract MapType getMap(final int index, final MapType dfl);

    public abstract void setMap(final int index, final MapType to);

    public abstract String getString(final int index);

    public abstract String getString(final int index, final String dfl);

    public abstract void setString(final int index, final String to);

    public abstract void addGeneric(final Object value);

    public abstract void addBigInteger(final BigInteger i);

    public abstract void addBigInteger(final int index, final BigInteger i);

    public abstract void addBigDecimal(final BigDecimal d);

    public abstract void addBigDecimal(final int index, final BigDecimal d);

    public abstract void addByte(final byte b);

    public abstract void addByte(final int index, final byte b);

    public abstract void addShort(final short s);

    public abstract void addShort(final int index, final short s);

    public abstract void addInt(final int i);

    public abstract void addInt(final int index, final int i);

    public abstract void addLong(final long l);

    public abstract void addLong(final int index, final long l);

    public abstract void addFloat(final float f);

    public abstract void addFloat(final int index, final float f);

    public abstract void addDouble(final double d);

    public abstract void addDouble(final int index, final double d);

    public abstract void addByteArray(final byte[] arr);

    public abstract void addByteArray(final int index, final byte[] arr);

    public abstract void addShortArray(final short[] arr);

    public abstract void addShortArray(final int index, final short[] arr);

    public abstract void addIntArray(final int[] arr);

    public abstract void addIntArray(final int index, final int[] arr);

    public abstract void addLongArray(final long[] arr);

    public abstract void addLongArray(final int index, final long[] arr);

    public abstract void addList(final ListType list);

    public abstract void addList(final int index, final ListType list);

    public abstract void addMap(final MapType map);

    public abstract void addMap(final int index, final MapType map);

    public abstract void addString(final String string);

    public abstract void addString(final int index, final String string);

}
