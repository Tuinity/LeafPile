package ca.spottedleaf.yamlconfig.type;

import java.util.Objects;

public final class DefaultedValue<T> {

    private final T value;

    public DefaultedValue() {
        this(null);
    }

    public DefaultedValue(final T value) {
        this.value = value;
    }

    public T getValueRaw() {
        return value;
    }

    public T getOrDefault(final T dfl) {
        return this.value != null ? this.value : dfl;
    }

    @Override
    public int hashCode() {
        return this.value == null ? 0 : this.value.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof DefaultedValue<?> defaultedValue && Objects.equals(this.value, defaultedValue.value);
    }

    @Override
    public String toString() {
        return "DefaultedValue{" +
                "value=" + this.value +
                '}';
    }
}
