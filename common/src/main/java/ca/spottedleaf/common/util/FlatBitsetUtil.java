package ca.spottedleaf.common.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.BitSet;
import java.util.Objects;

public final class FlatBitsetUtil {

    private static final int LOG2_LONG = 6;
    private static final long ALL_SET = -1L;
    private static final int BITS_PER_LONG = Long.SIZE;

    public static int[] bitsetToInts(final long[] bitset) {
        final IntArrayList ret = new IntArrayList(bitset.length << LOG2_LONG);

        int offset = 0;
        for (long mask : bitset) {
            for (int i = 0, len = Long.bitCount(mask); i < len; ++i) {
                ret.add(offset | Long.numberOfTrailingZeros(mask));
                mask ^= IntegerUtil.getTrailingBit(mask);
            }
            offset += BITS_PER_LONG;
        }

        return ret.toIntArray();
    }

    public static long[] intsToBitset(final int[] ints) {
        final BitSet bitSet = new BitSet();

        for (final int idx : ints) {
            bitSet.set(idx);
        }

        return bitSet.toLongArray();
    }

    public static boolean getChecked(final long[] bitset, final int index) {
        final int arrIdx = index >> LOG2_LONG;
        return arrIdx >= 0 && arrIdx < bitset.length && (bitset[arrIdx] & (1L << index)) != 0L;
    }

    public static boolean getUnchecked(final long[] bitset, final int index) {
        return (bitset[index >> LOG2_LONG] & (1L << index)) != 0L;
    }

    public static int sizeBitset(final int size) {
        return (size + (Long.SIZE - 1)) >>> LOG2_LONG;
    }

    public static void set(final long[] bitset, final int index) {
        bitset[index >> LOG2_LONG] |= (1L << index);
    }

    public static void clear(final long[] bitset, final int index) {
        bitset[index >> LOG2_LONG] &= ~(1L << index);
    }

    public static void or(final long[] bitset, final int index, final boolean value) {
        bitset[index >> LOG2_LONG] |= ((value ? 1L : 0L) << index);
    }

    public static void xor(final long[] bitset, final int index, final boolean value) {
        bitset[index >> LOG2_LONG] ^= ((value ? 1L : 0L) << index);
    }

    public static void and(final long[] bitset, final int index, final boolean value) {
        bitset[index >> LOG2_LONG] &= ((value ? 1L : 0L) << index);
    }

    // from inclusive
    // to exclusive
    public static int firstSet(final long[] bitset, final int from, final int to) {
        if ((from | to | (to - from)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        int bitsetIdx = from >>> LOG2_LONG;
        int bitIdx = from & ~(BITS_PER_LONG - 1);

        long tmp = bitset[bitsetIdx] & (ALL_SET << from);
        for (;;) {
            if (tmp != 0L) {
                final int ret = bitIdx | Long.numberOfTrailingZeros(tmp);
                return ret >= to ? -1 : ret;
            }

            bitIdx += BITS_PER_LONG;

            if (bitIdx >= to) {
                return -1;
            }

            tmp = bitset[++bitsetIdx];
        }
    }

    // from inclusive
    // to exclusive
    public static int firstClear(final long[] bitset, final int from, final int to) {
        if ((from | to | (to - from)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        // like firstSet, but invert the bitset

        int bitsetIdx = from >>> LOG2_LONG;
        int bitIdx = from & ~(BITS_PER_LONG - 1);

        long tmp = (~bitset[bitsetIdx]) & (ALL_SET << from);
        for (;;) {
            if (tmp != 0L) {
                final int ret = bitIdx | Long.numberOfTrailingZeros(tmp);
                return ret >= to ? -1 : ret;
            }

            bitIdx += BITS_PER_LONG;

            if (bitIdx >= to) {
                return -1;
            }

            tmp = ~bitset[++bitsetIdx];
        }
    }

    // from inclusive
    // to exclusive
    public static void clearRange(final long[] bitset, final int from, int to) {
        if ((from | to | (to - from)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (from == to) {
            return;
        }

        --to;

        final int fromBitsetIdx = from >>> LOG2_LONG;
        final int toBitsetIdx = to >>> LOG2_LONG;

        final long keepFirst = ~(ALL_SET << from);
        final long keepLast = ~(ALL_SET >>> ((BITS_PER_LONG - 1) ^ to));

        Objects.checkFromToIndex(fromBitsetIdx, toBitsetIdx, bitset.length);

        if (fromBitsetIdx == toBitsetIdx) {
            // special case: need to keep both first and last
            bitset[fromBitsetIdx] &= (keepFirst | keepLast);
        } else {
            bitset[fromBitsetIdx] &= keepFirst;

            for (int i = fromBitsetIdx + 1; i < toBitsetIdx; ++i) {
                bitset[i] = 0L;
            }

            bitset[toBitsetIdx] &= keepLast;
        }
    }

    // from inclusive
    // to exclusive
    public static boolean isRangeSet(final long[] bitset, final int from, final int to) {
        return firstClear(bitset, from, to) == -1;
    }

    private FlatBitsetUtil() {}
}
