package ca.spottedleaf.converter.datatypes;

public abstract class DataWalker<T> {

    public static final DataWalker<?> NO_OP = new DataWalker<>() {
        @Override
        public Object walk(final Object data, final long fromVersion, final long toVersion) {
            return null;
        }
    };

    public static <T> DataWalker<T> noOp() {
        return (DataWalker<T>)NO_OP;
    }

    public abstract T walk(final T data, final long fromVersion, final long toVersion);

}
