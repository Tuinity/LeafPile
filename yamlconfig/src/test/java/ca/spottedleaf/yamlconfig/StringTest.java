package ca.spottedleaf.yamlconfig;

import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public final class StringTest {

    @Adaptable
    public static final class TestConfig {

        @Serializable
        public String _value;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            TestConfig that = (TestConfig) o;
            return Objects.equals(_value, that._value);
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

    private static String createSerializedTemplate(final Object value) {
        return createSerializedTemplate(Objects.toString(value));
    }

    private static String createSerializedTemplate(final String value) {
        return TEMPLATE_CONFIG.replace("$replace$", value);
    }

    private static TestConfig createTemplateConfig(final String value) {
        final TestConfig ret = new TestConfig();

        ret._value = value;

        return ret;
    }

    private static void testDeserialization(final String str, final TestConfig expect) throws Exception {
        final YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(str));

        assertEquals(expect, config.config);
    }

    private static final String[] NON_STRINGS = new String[] {
            "false",
            "true",
            "1",
            BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE).toString(),
            "1.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001",
            // ensure we do not do yaml 1.1 parsing on these:
            "22:22", // base 60 integer
            "22:22.5", // base 60 float
            "yes", // boolean true
            "no", // boolean false
            "on", // boolean true
            "off" // boolean false
    };

    @Test
    public void testDecode() throws Exception {
        for (final String test : NON_STRINGS) {
            testDeserialization(createSerializedTemplate(test), createTemplateConfig(test));
            testDeserialization(createSerializedTemplate("\"" + test + "\""), createTemplateConfig(test));
        }
    }

    @Test
    public void testNonString() throws Exception {
        assertThrows(Exception.class, () -> {
            testDeserialization(
                    """
                    _value:
                     - test
                    """,
                    createTemplateConfig(""));
        });
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
        for (final String test : NON_STRINGS) {
            testSerialization(createTemplateConfig(test));
        }
    }
}
