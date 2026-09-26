package ca.spottedleaf.yamlconfig;

import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import ca.spottedleaf.yamlconfig.annotation.Serializable;
import ca.spottedleaf.yamlconfig.config.YamlConfig;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public final class EnumAndMapTest {

    @Adaptable
    public static final class TestConfig {

        @Serializable
        public TestEnum enum1;

        @Serializable
        public TestEnum enum2;

        @Serializable
        public List<TestEnum> testEnums1;

        @Serializable
        public EnumSet<TestEnum> testEnums2;

        @Serializable
        public Set<TestEnum> testEnums3;

        @Serializable
        public Map<TestEnum, String> testEnumMap1;

        @Serializable
        public EnumMap<TestEnum, String> testEnumMap2;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestConfig that = (TestConfig) o;
            return enum1 == that.enum1 && enum2 == that.enum2 && Objects.equals(testEnums1, that.testEnums1) &&
                    Objects.equals(testEnums2, that.testEnums2) && Objects.equals(testEnums3, that.testEnums3) &&
                    Objects.equals(testEnumMap1, that.testEnumMap1) && Objects.equals(testEnumMap2, that.testEnumMap2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enum1, enum2, testEnums1, testEnums2, testEnums3, testEnumMap1, testEnumMap2);
        }

        @Override
        public String toString() {
            return "TestConfig{" +
                    "enum1=" + enum1 +
                    ", enum2=" + enum2 +
                    ", testEnums1=" + testEnums1 +
                    ", testEnums2=" + testEnums2 +
                    ", testEnums3=" + testEnums3 +
                    ", testEnumMap1=" + testEnumMap1 +
                    ", testEnumMap2=" + testEnumMap2 +
                    '}';
        }
    }

    private static final String CONFIG_STRING = """
            enum1: A
            enum2: "B"
            test-enums1: [B, C, "D"]
            test-enums2:
             - F
             - D
             - E
             - E
            test-enums3:
             - A
             - A
             - C
            test-enum-map1:
             A: test
             B: test2
             C: test3
            test-enum-map2:
             B: t
             F: t2
             E: t3
            """;

    private static TestConfig createExpectedConfig() {
        final TestConfig ret = new TestConfig();

        ret.enum1 = TestEnum.A;
        ret.enum2 = TestEnum.B;
        ret.testEnums1 = new ArrayList<>(Arrays.asList(TestEnum.B, TestEnum.C, TestEnum.D));
        ret.testEnums2 = EnumSet.of(TestEnum.F, TestEnum.D, TestEnum.E);
        ret.testEnums3 = new LinkedHashSet<>(
                Arrays.asList(
                        TestEnum.A,
                        TestEnum.C
                )
        );
        ret.testEnumMap1 = new HashMap<>(
                Map.of(
                        TestEnum.A, "test",
                        TestEnum.B, "test2",
                        TestEnum.C, "test3"
                )
        );
        ret.testEnumMap2 = new EnumMap<>(
                Map.of(
                        TestEnum.B, "t",
                        TestEnum.F, "t2",
                        TestEnum.E, "t3"
                )
        );

        return ret;
    }

    @Test
    public void deserializeTest() throws Exception {
        final YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(CONFIG_STRING));

        assertEquals(createExpectedConfig(), config.config);
    }

    @Test
    public void serializeTest() throws Exception {
        YamlConfig<TestConfig> config = new YamlConfig<>(TestConfig.class, createExpectedConfig());

        final String serialized = config.saveToString();

        config = new YamlConfig<>(TestConfig.class, new TestConfig());

        config.load(new StringReader(serialized));

        assertEquals(createExpectedConfig(), config.config);
    }

    public static enum TestEnum {
        A, B, C, D, E, F, G
    }
}
