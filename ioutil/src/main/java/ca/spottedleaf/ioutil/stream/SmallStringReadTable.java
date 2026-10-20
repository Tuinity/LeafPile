package ca.spottedleaf.ioutil.stream;

import ca.spottedleaf.common.util.HashUtil;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class SmallStringReadTable {

    static final int SMALL_THRESHOLD = 4*Long.BYTES;

    private static final int MAX_CACHE_SIZE = 1 << 29;
    private static final int INIT_TABLE_SIZE = 1 << 4;

    private static final long NON_ASCII_BITS =
            (1L << 7) | (1L << 15) | (1L << 23) | (1L << 31) |
            (1L << 39) | (1L << 47) | (1L << 55) | (1L << 63);

    private final byte[] asciiReadArray = new byte[4*Long.BYTES];
    private static final VarHandle BYTE_ARRAY_AS_LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    final StringLookup1 lookup1 = new StringLookup1(this.asciiReadArray);
    final StringLookup2 lookup2 = new StringLookup2(this.asciiReadArray);
    final StringLookup3 lookup3 = new StringLookup3(this.asciiReadArray);
    final StringLookup4 lookup4 = new StringLookup4(this.asciiReadArray);

    private static long getMask(final int bytes) {
        // bytes > 0 && bytes <= 8
        return -1L >>> (Long.SIZE - (bytes << 3));
    }

    public String read(final AbstractBufferInputStream in, final long offset, final int strLen) throws IOException {
        switch ((strLen + (Long.BYTES - 1)) >>> 3) { // (len + (Long.BYTES + 1)) / Long.BYTES
            case 1: {
                return this.lookup1.lookup(in, offset, strLen);
            }
            case 2: {
                return this.lookup2.lookup(in, offset, strLen);
            }
            case 3: {
                return this.lookup3.lookup(in, offset, strLen);
            }
            case 4: {
                return this.lookup4.lookup(in, offset, strLen);
            }

            default: {
                throw new IllegalArgumentException();
            }
        }
    }

    static final class StringLookup1 {

        private final byte[] asciiReadArray;

        public StringLookup1(final byte[] asciiReadArray) {
            this.asciiReadArray = asciiReadArray;
        }

        public String decodeString(final AbstractBufferInputStream fallback, final long v1, final int strLen) throws IOException {
            if ((v1 & NON_ASCII_BITS) != 0L) {
                return fallback.readUTFUncached();
            }
            fallback.readBuffer.skipRead(Short.BYTES + strLen);

            final byte[] bytes = this.asciiReadArray;
            BYTE_ARRAY_AS_LONG.set(bytes, 0 << 3, v1);

            return new String(bytes, 0, strLen, StandardCharsets.ISO_8859_1);
        }

        private static final class StringLookupEntry1 implements Cloneable {
            public long v1;
            public int h;
            public int l;

            public String value;

            @Override
            public int hashCode() {
                return this.h;
            }

            private boolean eq(final StringLookupEntry1 other) {
                return other.l == this.l && other.v1 == this.v1;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == null || obj.getClass() != StringLookupEntry1.class) {
                    return false;
                }
                return this.eq((StringLookupEntry1)obj);
            }

            @Override
            protected StringLookupEntry1 clone() {
                try {
                    return (StringLookupEntry1)super.clone();
                } catch (final CloneNotSupportedException ex) {
                    throw new InternalError(ex);
                }
            }
        }

        private final StringLookupEntry1 work = new StringLookupEntry1();
        private StringLookupEntry1[] table = new StringLookupEntry1[INIT_TABLE_SIZE];
        // threshold = 1/4*table
        private int count;

        String lookup(final AbstractBufferInputStream in, final long start, final int strLen) throws IOException {
            final long v1 = in.readBuffer.readLongLE(start) & getMask(strLen - 0*Long.BYTES);

            final StringLookupEntry1 work = this.work;
            work.l = strLen;
            work.v1 = v1;
            work.h = (int)HashUtil.hash(v1);

            final StringLookupEntry1 cached = this.find(work);
            if (cached != null) {
                in.readBuffer.skipRead(Short.BYTES + strLen);
                return cached.value;
            }
            final String value = this.decodeString(in, v1, strLen);

            final StringLookupEntry1 insert = work.clone();
            insert.value = value;
            this.insert(insert);

            return value;
        }

        private StringLookupEntry1 find(final StringLookupEntry1 entry) {
            final StringLookupEntry1[] table = this.table;
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                final StringLookupEntry1 candidate = table[idx];
                if (candidate == null) {
                    return null;
                }
                if (entry.eq(candidate)) {
                    return candidate;
                }
            }
        }

        private static void insertNoDup(final StringLookupEntry1 entry, final StringLookupEntry1[] table) {
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                if (table[idx] == null) {
                    table[idx] = entry;
                    return;
                }
                continue;
            }
        }

        private void insert(final StringLookupEntry1 entry) {
            if (this.count >= MAX_CACHE_SIZE) {
                // Just in case...
                return;
            }
            if (++this.count >= (this.table.length >> 2)) {
                final StringLookupEntry1[] newTable = new StringLookupEntry1[this.table.length << 1];
                for (final StringLookupEntry1 old : this.table) {
                    if (old != null) {
                        insertNoDup(old, newTable);
                    }
                }
                this.table = newTable;
            }
            insertNoDup(entry, this.table);
        }
    }

    static final class StringLookup2 {

        private final byte[] asciiReadArray;

        public StringLookup2(final byte[] asciiReadArray) {
            this.asciiReadArray = asciiReadArray;
        }

        public String decodeString(final AbstractBufferInputStream fallback, final long v1, final long v2, final int strLen) throws IOException {
            if (((v1 | v2) & NON_ASCII_BITS) != 0L) {
                return fallback.readUTFUncached();
            }
            fallback.readBuffer.skipRead(Short.BYTES + strLen);

            final byte[] bytes = this.asciiReadArray;
            BYTE_ARRAY_AS_LONG.set(bytes, 0 << 3, v1);
            BYTE_ARRAY_AS_LONG.set(bytes, 1 << 3, v2);

            return new String(bytes, 0, strLen, StandardCharsets.ISO_8859_1);
        }

        private static final class StringLookupEntry2 implements Cloneable {
            public long v1;
            public long v2;
            public int h;
            public int l;

            public String value;

            @Override
            public int hashCode() {
                return this.h;
            }

            private boolean eq(final StringLookupEntry2 other) {
                return other.l == this.l && other.v1 == this.v1 && other.v2 == this.v2;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == null || obj.getClass() != StringLookupEntry2.class) {
                    return false;
                }
                return this.eq((StringLookupEntry2)obj);
            }

            @Override
            protected StringLookupEntry2 clone() {
                try {
                    return (StringLookupEntry2)super.clone();
                } catch (final CloneNotSupportedException ex) {
                    throw new InternalError(ex);
                }
            }
        }

        private final StringLookupEntry2 work = new StringLookupEntry2();
        private StringLookupEntry2[] table = new StringLookupEntry2[INIT_TABLE_SIZE];
        // threshold = 1/4*table
        private int count;

        String lookup(final AbstractBufferInputStream in, final long start, final int strLen) throws IOException {
            final long v1 = in.readBuffer.readLongLE(start);
            final long v2 = in.readBuffer.readLongLE(start + Long.BYTES) & getMask(strLen - 1*Long.BYTES);

            final StringLookupEntry2 work = this.work;
            work.l = strLen;
            work.v1 = v1;
            work.v2 = v2;

            work.h = (int)HashUtil.hash((v1 + v2));

            final StringLookupEntry2 cached = this.find(work);
            if (cached != null) {
                in.readBuffer.skipRead(Short.BYTES + strLen);
                return cached.value;
            }
            final String value = this.decodeString(in, v1, v2, strLen);

            final StringLookupEntry2 insert = work.clone();
            insert.value = value;
            this.insert(insert);

            return value;
        }

        private StringLookupEntry2 find(final StringLookupEntry2 entry) {
            final StringLookupEntry2[] table = this.table;
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                final StringLookupEntry2 candidate = table[idx];
                if (candidate == null) {
                    return null;
                }
                if (entry.eq(candidate)) {
                    return candidate;
                }
            }
        }

        private static void insertNoDup(final StringLookupEntry2 entry, final StringLookupEntry2[] table) {
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                if (table[idx] == null) {
                    table[idx] = entry;
                    return;
                }
                continue;
            }
        }

        private void insert(final StringLookupEntry2 entry) {
            if (this.count >= MAX_CACHE_SIZE) {
                // Just in case...
                return;
            }
            if (++this.count >= (this.table.length >> 2)) {
                final StringLookupEntry2[] newTable = new StringLookupEntry2[this.table.length << 1];
                for (final StringLookupEntry2 old : this.table) {
                    if (old != null) {
                        insertNoDup(old, newTable);
                    }
                }
                this.table = newTable;
            }
            insertNoDup(entry, this.table);
        }
    }

    static final class StringLookup3 {

        private final byte[] asciiReadArray;

        public StringLookup3(final byte[] asciiReadArray) {
            this.asciiReadArray = asciiReadArray;
        }

        public String decodeString(final AbstractBufferInputStream fallback, final long v1, final long v2, final long v3,
                                   final int strLen) throws IOException {
            if (((v1 | v2 | v3) & NON_ASCII_BITS) != 0L) {
                return fallback.readUTFUncached();
            }
            fallback.readBuffer.skipRead(Short.BYTES + strLen);

            final byte[] bytes = this.asciiReadArray;
            BYTE_ARRAY_AS_LONG.set(bytes, 0 << 3, v1);
            BYTE_ARRAY_AS_LONG.set(bytes, 1 << 3, v2);
            BYTE_ARRAY_AS_LONG.set(bytes, 2 << 3, v3);

            return new String(bytes, 0, strLen, StandardCharsets.ISO_8859_1);
        }

        private static final class StringLookupEntry3 implements Cloneable {
            public long v1;
            public long v2;
            public long v3;
            public int h;
            public int l;

            public String value;

            @Override
            public int hashCode() {
                return this.h;
            }

            private boolean eq(final StringLookupEntry3 other) {
                return other.l == this.l && other.v1 == this.v1 && other.v2 == this.v2 && other.v3 == this.v3;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == null || obj.getClass() != StringLookupEntry3.class) {
                    return false;
                }
                return this.eq((StringLookupEntry3)obj);
            }

            @Override
            protected StringLookupEntry3 clone() {
                try {
                    return (StringLookupEntry3)super.clone();
                } catch (final CloneNotSupportedException ex) {
                    throw new InternalError(ex);
                }
            }
        }

        private final StringLookupEntry3 work = new StringLookupEntry3();
        private StringLookupEntry3[] table = new StringLookupEntry3[INIT_TABLE_SIZE];
        // threshold = 1/4*table
        private int count;

        String lookup(final AbstractBufferInputStream in, final long start, final int strLen) throws IOException {
            final long v1 = in.readBuffer.readLongLE(start);
            final long v2 = in.readBuffer.readLongLE(start + Long.BYTES);
            final long v3 = in.readBuffer.readLongLE(start + 2*Long.BYTES) & getMask(strLen - 2*Long.BYTES);

            final StringLookupEntry3 work = this.work;
            work.l = strLen;
            work.v1 = v1;
            work.v2 = v2;
            work.v3 = v3;

            work.h = (int)HashUtil.hash((v1 + v2) + (v3));

            final StringLookupEntry3 cached = this.find(work);
            if (cached != null) {
                in.readBuffer.skipRead(Short.BYTES + strLen);
                return cached.value;
            }
            final String value = this.decodeString(in, v1, v2, v3, strLen);

            final StringLookupEntry3 insert = work.clone();
            insert.value = value;
            this.insert(insert);

            return value;
        }

        private StringLookupEntry3 find(final StringLookupEntry3 entry) {
            final StringLookupEntry3[] table = this.table;
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                final StringLookupEntry3 candidate = table[idx];
                if (candidate == null) {
                    return null;
                }
                if (entry.eq(candidate)) {
                    return candidate;
                }
            }
        }

        private static void insertNoDup(final StringLookupEntry3 entry, final StringLookupEntry3[] table) {
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                if (table[idx] == null) {
                    table[idx] = entry;
                    return;
                }
                continue;
            }
        }

        private void insert(final StringLookupEntry3 entry) {
            if (this.count >= MAX_CACHE_SIZE) {
                // Just in case...
                return;
            }
            if (++this.count >= (this.table.length >> 2)) {
                final StringLookupEntry3[] newTable = new StringLookupEntry3[this.table.length << 1];
                for (final StringLookupEntry3 old : this.table) {
                    if (old != null) {
                        insertNoDup(old, newTable);
                    }
                }
                this.table = newTable;
            }
            insertNoDup(entry, this.table);
        }
    }

    static final class StringLookup4 {

        private final byte[] asciiReadArray;

        public StringLookup4(final byte[] asciiReadArray) {
            this.asciiReadArray = asciiReadArray;
        }

        public String decodeString(final AbstractBufferInputStream fallback, final long v1, final long v2, final long v3,
                                   final long v4, final int strLen) throws IOException {
            if (((v1 | v2 | v3 | v4) & NON_ASCII_BITS) != 0L) {
                return fallback.readUTFUncached();
            }
            fallback.readBuffer.skipRead(Short.BYTES + strLen);

            final byte[] bytes = this.asciiReadArray;
            BYTE_ARRAY_AS_LONG.set(bytes, 0 << 3, v1);
            BYTE_ARRAY_AS_LONG.set(bytes, 1 << 3, v2);
            BYTE_ARRAY_AS_LONG.set(bytes, 2 << 3, v3);
            BYTE_ARRAY_AS_LONG.set(bytes, 3 << 3, v4);

            return new String(bytes, 0, strLen, StandardCharsets.ISO_8859_1);
        }

        private static final class StringLookupEntry4 implements Cloneable {
            public long v1;
            public long v2;
            public long v3;
            public long v4;
            public int h;
            public int l;

            public String value;

            @Override
            public int hashCode() {
                return this.h;
            }

            private boolean eq(final StringLookupEntry4 other) {
                return other.l == this.l && other.v1 == this.v1 && other.v2 == this.v2 && other.v3 == this.v3 && other.v4 == this.v4;
            }

            @Override
            public boolean equals(final Object obj) {
                if (obj == null || obj.getClass() != StringLookupEntry4.class) {
                    return false;
                }
                return this.eq((StringLookupEntry4)obj);
            }

            @Override
            protected StringLookupEntry4 clone() {
                try {
                    return (StringLookupEntry4)super.clone();
                } catch (final CloneNotSupportedException ex) {
                    throw new InternalError(ex);
                }
            }
        }

        private final StringLookupEntry4 work = new StringLookupEntry4();
        private StringLookupEntry4[] table = new StringLookupEntry4[INIT_TABLE_SIZE];
        // threshold = 1/4*table
        private int count;

        String lookup(final AbstractBufferInputStream in, final long start, final int strLen) throws IOException {
            final long v1 = in.readBuffer.readLongLE(start);
            final long v2 = in.readBuffer.readLongLE(start + Long.BYTES);
            final long v3 = in.readBuffer.readLongLE(start + 2*Long.BYTES);
            final long v4 = in.readBuffer.readLongLE(start + 3*Long.BYTES) & getMask(strLen - 3*Long.BYTES);

            final StringLookupEntry4 work = this.work;
            work.l = strLen;
            work.v1 = v1;
            work.v2 = v2;
            work.v3 = v3;
            work.v4 = v4;

            work.h = (int)HashUtil.hash((v1 + v2) + (v3 + v4));

            final StringLookupEntry4 cached = this.find(work);
            if (cached != null) {
                in.readBuffer.skipRead(Short.BYTES + strLen);
                return cached.value;
            }
            final String value = this.decodeString(in, v1, v2, v3, v4, strLen);

            final StringLookupEntry4 insert = work.clone();
            insert.value = value;
            this.insert(insert);

            return value;
        }

        private StringLookupEntry4 find(final StringLookupEntry4 entry) {
            final StringLookupEntry4[] table = this.table;
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                final StringLookupEntry4 candidate = table[idx];
                if (candidate == null) {
                    return null;
                }
                if (entry.eq(candidate)) {
                    return candidate;
                }
            }
        }

        private static void insertNoDup(final StringLookupEntry4 entry, final StringLookupEntry4[] table) {
            final int mask = table.length - 1;
            for (int idx = entry.h & mask;; idx = (idx + 1) & mask) {
                if (table[idx] == null) {
                    table[idx] = entry;
                    return;
                }
                continue;
            }
        }

        private void insert(final StringLookupEntry4 entry) {
            if (this.count >= MAX_CACHE_SIZE) {
                // Just in case...
                return;
            }
            if (++this.count >= (this.table.length >> 2)) {
                final StringLookupEntry4[] newTable = new StringLookupEntry4[this.table.length << 1];
                for (final StringLookupEntry4 old : this.table) {
                    if (old != null) {
                        insertNoDup(old, newTable);
                    }
                }
                this.table = newTable;
            }
            insertNoDup(entry, this.table);
        }
    }
}
