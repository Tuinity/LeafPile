package ca.spottedleaf.yamlconfig;

import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public final class BigIntegerTest {

    private static final BigInteger BIG_NUMBER = new BigInteger("1234567890123456789012345678901234567890");

    @Adaptable
    public static final class TestConfig {

        @Serializable
        public BigInteger _value;

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

    private static String createSerializedTemplate(final BigInteger value) {
        return createSerializedTemplate(value.toString());
    }

    private static String createSerializedTemplate(final String value) {
        return TEMPLATE_CONFIG.replace("$replace$", value);
    }

    private static TestConfig createTemplateConfig(final BigInteger value) {
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
        testDeserialization(createSerializedTemplate(BigInteger.ONE), createTemplateConfig(BigInteger.ONE));
        testDeserialization(createSerializedTemplate(BIG_NUMBER), createTemplateConfig(BIG_NUMBER));
    }

    @Test
    public void testFloatingError() throws Exception {
        assertDoesNotThrow(() -> {
            testDeserialization(createSerializedTemplate("1.0"), createTemplateConfig(BigInteger.ONE));
            testDeserialization(createSerializedTemplate("+1.0"), createTemplateConfig(BigInteger.ONE));
            testDeserialization(createSerializedTemplate("-0.0"), createTemplateConfig(BigInteger.ZERO));
        });
        assertThrows(Exception.class, () -> {
            testDeserialization(createSerializedTemplate("1.00000000000000000000000000000000000000000000000000001"), createTemplateConfig(BigInteger.ONE));
        });
    }

    @Test
    public void testStringDecode() throws Exception {
        testDeserialization(createSerializedTemplate("\"" + BIG_NUMBER.toString() + "\""), createTemplateConfig(BIG_NUMBER));
        testDeserialization(createSerializedTemplate("\"" + BIG_NUMBER.toString() + ".0" + "\""), createTemplateConfig(BIG_NUMBER));

        testDeserialization(createSerializedTemplate("\"-0.0\""), createTemplateConfig(BigInteger.ZERO));
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
        testSerialization(createTemplateConfig(BIG_NUMBER));
    }
}
