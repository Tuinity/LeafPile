package ca.spottedleaf.converter.types;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import java.util.function.Function;

public abstract class MapType {

    public abstract TypeUtil<?> getTypeUtil();

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(final Object other);

    @Override
    public abstract String toString();

    public abstract ListType createEmptyList();

    public abstract MapType createEmptyMap();

    public abstract int size();

    public abstract boolean isEmpty();

    public abstract void clear();

    public abstract Set<String> keys();

    // Provides a deep copy of this map
    public abstract MapType copy();

    public abstract boolean rename(final String fromKey, final String toKey);

    public abstract boolean renameKeys(final Function<String, String> renamer);

    public abstract boolean hasKey(final String key);

    public abstract boolean hasKey(final String key, final ObjectType type);

    public abstract void remove(final String key);

    public abstract Object getGenericAndRemove(final String key);

    public abstract Object getGeneric(final String key);

    public abstract Object getGeneric(final String key, final Object dfl);

    // types here are not strict. if the key maps to a different type, default is always returned
    // if default is not a parameter, then default is always null

    public abstract Number getNumber(final String key);

    public abstract Number getNumber(final String key, final Number dfl);

    public abstract BigInteger getBigInteger(final String key);

    public abstract BigInteger getBigInteger(final String key, final BigInteger dfl);

    public abstract void setBigInteger(final String key, final BigInteger val);

    public abstract BigDecimal getBigDecimal(final String key);

    public abstract BigDecimal getBigDecimal(final String key, final BigDecimal dfl);

    public abstract void setBigDecimal(final String key, final BigDecimal val);

    public abstract boolean getBoolean(final String key);

    public abstract boolean getBoolean(final String key, final boolean dfl);

    public abstract void setBoolean(final String key, final boolean val);

    // if the mapped value is a Number but not a byte, then the number is casted to byte. If the mapped value does not exist or is not a number, returns 0
    public abstract byte getByte(final String key);

    // if the mapped value is a Number but not a byte, then the number is casted to byte. If the mapped value does not exist or is not a number, returns dfl
    public abstract byte getByte(final String key, final byte dfl);

    public abstract void setByte(final String key, final byte val);

    // if the mapped value is a Number but not a short, then the number is casted to short. If the mapped value does not exist or is not a number, returns 0
    public abstract short getShort(final String key);

    // if the mapped value is a Number but not a short, then the number is casted to short. If the mapped value does not exist or is not a number, returns dfl
    public abstract short getShort(final String key, final short dfl);

    public abstract void setShort(final String key, final short val);

    // if the mapped value is a Number but not a int, then the number is casted to int. If the mapped value does not exist or is not a number, returns 0
    public abstract int getInt(final String key);

    // if the mapped value is a Number but not a int, then the number is casted to int. If the mapped value does not exist or is not a number, returns dfl
    public abstract int getInt(final String key, final int dfl);

    public abstract void setInt(final String key, final int val);

    // if the mapped value is a Number but not a long, then the number is casted to long. If the mapped value does not exist or is not a number, returns 0
    public abstract long getLong(final String key);

    // if the mapped value is a Number but not a long, then the number is casted to long. If the mapped value does not exist or is not a number, returns dfl
    public abstract long getLong(final String key, final long dfl);

    public abstract void setLong(final String key, final long val);

    // if the mapped value is a Number but not a float, then the number is casted to float. If the mapped value does not exist or is not a number, returns 0
    public abstract float getFloat(final String key);

    // if the mapped value is a Number but not a float, then the number is casted to float. If the mapped value does not exist or is not a number, returns dfl
    public abstract float getFloat(final String key, final float dfl);

    public abstract void setFloat(final String key, final float val);

    // if the mapped value is a Number but not a double, then the number is casted to double. If the mapped value does not exist or is not a number, returns 0
    public abstract double getDouble(final String key);

    // if the mapped value is a Number but not a double, then the number is casted to double. If the mapped value does not exist or is not a number, returns dfl
    public abstract double getDouble(final String key, final double dfl);

    public abstract void setDouble(final String key, final double val);

    public abstract byte[] getBytes(final String key);

    public abstract byte[] getBytes(final String key, final byte[] dfl);

    public abstract void setBytes(final String key, final byte[] val);

    public abstract short[] getShorts(final String key);

    public abstract short[] getShorts(final String key, final short[] dfl);

    public abstract void setShorts(final String key, final short[] val);

    public abstract int[] getInts(final String key);

    public abstract int[] getInts(final String key, final int[] dfl);

    public abstract void setInts(final String key, final int[] val);

    public abstract long[] getLongs(final String key);

    public abstract long[] getLongs(final String key, final long[] dfl);

    public abstract void setLongs(final String key, final long[] val);

    public abstract ListType getListUnchecked(final String key);

    public abstract ListType getListUnchecked(final String key, final ListType dfl);

    public ListType getList(final String key, final ObjectType type) {
        return this.getList(key, type, null);
    }

    public ListType getOrCreateList(final String key, final ObjectType type) {
        ListType ret = this.getList(key, type);
        if (ret == null) {
            this.setList(key, ret = this.createEmptyList());
        }

        return ret;
    }

    public ListType getList(final String key, final ObjectType type, final ListType dfl) {
        final ListType ret = this.getListUnchecked(key, null);
        final ObjectType retType;
        if (ret != null && ((retType = ret.getUniformType()) == type || retType == ObjectType.UNDEFINED || retType == ObjectType.NONE)) {
            return ret;
        } else {
            return dfl;
        }
    }

    public abstract void setList(final String key, final ListType val);

    public abstract MapType getMap(final String key);

    public MapType getOrCreateMap(final String key) {
        MapType ret = this.getMap(key);
        if (ret == null) {
            this.setMap(key, ret = this.createEmptyMap());
        }

        return ret;
    }

    public abstract MapType getMap(final String key, final MapType dfl);

    public abstract void setMap(final String key, final MapType val);

    public abstract String getString(final String key);

    public abstract String getString(final String key, final String dfl);

    public abstract void setString(final String key, final String val);

    public abstract void setGeneric(final String key, final Object value);
}
