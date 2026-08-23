package ca.spottedleaf.yamlconfig;

import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public final class BooleanTest {

    @Adaptable
    public static final class TestConfig {

        @Serializable
        public boolean _value;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            TestConfig that = (TestConfig) o;
            return _value == that._value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(_value);
        }

        @Override
        public String toString() {
            return "TestConfig{" +
                    "_value=" + _value +
                    '}';
        }
    }

    private static final String TEMPLATE_CONFIG = """
            _value: $replace$
            """;

    private static String createSerializedTemplate(final boolean value) {
        return createSerializedTemplate(Boolean.toString(value));
    }

    private static String createSerializedTemplate(final String value) {
        return TEMPLATE_CONFIG.replace("$replace$", value);
    }

    private static TestConfig createTemplateConfig(final boolean value) {
        final TestConfig ret = new TestConfig();

        ret._value = value;

        return ret;
    }

    private static void testDeserialization(final String str, final TestConfig expect) throws Exception {
        final YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(str));

        assertEquals(expect, config.config);
    }

    @Test
    public void testBasicConfig() throws Exception {
        testDeserialization(createSerializedTemplate(false), createTemplateConfig(false));
        testDeserialization(createSerializedTemplate(true), createTemplateConfig(true));
    }

    @Test
    public void testStringDecode() throws Exception {
        testDeserialization(createSerializedTemplate("\"" + Boolean.toString(false) + "\""), createTemplateConfig(false));
        testDeserialization(createSerializedTemplate("\"" + Boolean.toString(true) + "\""), createTemplateConfig(true));
    }

    private static void testSerialization(final TestConfig expect) throws Exception {
        YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, expect);

        final String saved = config.saveToString();

        config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(saved));

        assertEquals(expect, config.config);
    }

    @Test
    public void testSerialization() throws Exception {
        testSerialization(createTemplateConfig(false));
        testSerialization(createTemplateConfig(true));
    }
}
