package ca.spottedleaf.yamlconfig.adapter.type;

import ca.spottedleaf.yamlconfig.adapter.generic.StringFormTypeAdapter;
import ca.spottedleaf.yamlconfig.type.Duration;

public final class DurationTypeAdapter extends StringFormTypeAdapter<Duration> {

    public static final DurationTypeAdapter INSTANCE = new DurationTypeAdapter();

    @Override
    public Duration fromString(final String value) {
        return Duration.parse(value);
    }

    @Override
    public String toString(final Duration value) {
        return value.toString();
    }
}
