package ca.spottedleaf.converter.datatypes;

public abstract class DataHook<T, R> {

    public abstract R preHook(final T data, final long fromVersion, final long toVersion);

    public abstract R postHook(final T data, final long fromVersion, final long toVersion);

}
