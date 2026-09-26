package ca.spottedleaf.yamlconfig;

import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import ca.spottedleaf.yamlconfig.type.DefaultedValue;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public final class DefaultedTest {

    @Adaptable
    public static final class TestConfig {

        @Serializable
        public DefaultedValue<List<String>> defaulted;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestConfig that = (TestConfig) o;
            return Objects.equals(defaulted, that.defaulted);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(defaulted);
        }

        @Override
        public String toString() {
            return "TestConfig{" +
                    "defaulted=" + defaulted +
                    '}';
        }
    }

    private static final String CONFIG_STRING_DEFAULT = """
            defaulted: default
            """;

    private static TestConfig createDefaultExpect() {
        final TestConfig ret = new TestConfig();

        ret.defaulted = new DefaultedValue<>();

        return ret;
    }

    private static final String CONFIG_STRING_POPULATED = """
            defaulted:
             - test
             - 23
             - 1.5
             - true
            """;

    private static TestConfig createPopulatedExpect() {
        final TestConfig ret = new TestConfig();

        ret.defaulted = new DefaultedValue<>(
                new ArrayList<>(
                        Arrays.asList(
                                "test",
                                "23",
                                "1.5",
                                "true"
                        )
                )
        );

        return ret;
    }

    private static void testDeserialization(final String str, final TestConfig expect) throws Exception {
        final YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(str));

        assertEquals(expect, config.config);
    }

    @Test
    public void deserializeTest() throws Exception {
        testDeserialization(CONFIG_STRING_DEFAULT, createDefaultExpect());
        testDeserialization(CONFIG_STRING_POPULATED, createPopulatedExpect());
    }

    private static void testSerialization(final TestConfig expect) throws Exception {
        YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, expect);

        final String saved = config.saveToString();

        config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(saved));

        assertEquals(expect, config.config);
    }

    @Test
    public void serializeTest() throws Exception {
        testSerialization(createDefaultExpect());
        testSerialization(createPopulatedExpect());
    }
}
