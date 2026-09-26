package ca.spottedleaf.yamlconfig.adapter.type;

import ca.spottedleaf.common.time.CalenderDuration;
import ca.spottedleaf.yamlconfig.adapter.generic.StringFormTypeAdapter;
import java.time.temporal.ChronoUnit;

public final class CalenderDurationTypeAdapter extends StringFormTypeAdapter<CalenderDuration> {

    public static final CalenderDurationTypeAdapter INSTANCE = new CalenderDurationTypeAdapter();

    @Override
    public CalenderDuration fromString(final String value) {
        return CalenderDuration.parse(value);
    }

    @Override
    public String toString(final CalenderDuration value) {
        final String parsedForm = value.getParsedForm();
        return parsedForm == null ? value.toPrettyValue(ChronoUnit.SECONDS) : parsedForm;
    }
}
