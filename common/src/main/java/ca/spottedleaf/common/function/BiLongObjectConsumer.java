package ca.spottedleaf.common.function;

@FunctionalInterface
public interface BiLongObjectConsumer<V> {

    public void accept(final long key, final V value);

}
