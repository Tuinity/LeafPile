package ca.spottedleaf.yamlconfig;

import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public final class BigDecimalTest {

    private static final BigDecimal BIG_NUMBER = new BigDecimal("1234567890123456789012345678901234567890.1234567890123456789012345678901234567890");

    @Adaptable
    public static final class TestConfig {

        @Serializable
        public BigDecimal _value;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            TestConfig that = (TestConfig) o;
            return _value == null ? that._value == null : _value.compareTo(that._value) == 0;
        }

        @Override
        public int hashCode() {
            return _value.stripTrailingZeros().hashCode();
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

    private static String createSerializedTemplate(final BigDecimal value) {
        return createSerializedTemplate(value.toString());
    }

    private static String createSerializedTemplate(final String value) {
        return TEMPLATE_CONFIG.replace("$replace$", value);
    }

    private static TestConfig createTemplateConfig(final BigDecimal value) {
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
        testDeserialization(createSerializedTemplate(BIG_NUMBER), createTemplateConfig(BIG_NUMBER));
        testDeserialization(createSerializedTemplate(BigDecimal.ONE), createTemplateConfig(BigDecimal.ONE));
        testDeserialization(createSerializedTemplate(new BigDecimal(BIG_NUMBER.toBigInteger())), createTemplateConfig(new BigDecimal(BIG_NUMBER.toBigInteger())));
    }

    private static final BigDecimal BIGGER_THAN_ONE = new BigDecimal("1.00000000000000000000000000000000000000000000000000001");

    @Test
    public void testFloatingConversion() throws Exception {
        testDeserialization(createSerializedTemplate(BIGGER_THAN_ONE), createTemplateConfig(BIGGER_THAN_ONE));
    }

    @Test
    public void testNegativeZero() throws Exception {
        testDeserialization(createSerializedTemplate("-0.0"), createTemplateConfig(BigDecimal.ZERO));
        testDeserialization(createSerializedTemplate("\"-0.0\""), createTemplateConfig(BigDecimal.ZERO));
    }

    @Test
    public void testStringDecode() throws Exception {
        testDeserialization(createSerializedTemplate("\"" + BIGGER_THAN_ONE + "\""), createTemplateConfig(BIGGER_THAN_ONE));
    }

    private static void testSerialization(final TestConfig expect) throws Exception {
        YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, expect);

        final String saved = config.saveToString();

        config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(saved));

        assertEquals(expect, config.config);
    }

    private static void testDeserializationAndSerialization(final BigDecimal reference) throws Exception {
        final TestConfig expect = createTemplateConfig(reference);
        testSerialization(expect);
        testDeserialization(createSerializedTemplate(reference.toString()), expect);
        testDeserialization(createSerializedTemplate("\"" + reference.toString() + "\""), expect);
    }

    @Test
    public void testNormals() throws Exception {
        testDeserializationAndSerialization(BigDecimal.ONE.negate());
        testDeserializationAndSerialization(BigDecimal.ONE);
        testDeserializationAndSerialization(BIG_NUMBER);
        testDeserializationAndSerialization(BIGGER_THAN_ONE);
    }
}
