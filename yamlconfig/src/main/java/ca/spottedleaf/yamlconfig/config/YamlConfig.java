package ca.spottedleaf.yamlconfig.config;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import ca.spottedleaf.yamlconfig.annotation.Adaptable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.constructor.ConstructorException;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class YamlConfig<T> {

    public final TypeAdapterRegistry typeAdapters;

    private final Class<? extends T> clazz;

    public volatile T config;

    private final Yaml yaml;
    private final LoaderOptions loaderOptions;
    private final DumperOptions dumperOptions;

    public YamlConfig(final Class<? extends T> clazz, final T dfl) throws Exception {
        this(clazz, dfl, new TypeAdapterRegistry());
    }

    public YamlConfig(final Class<? extends T> clazz, final T dfl, final TypeAdapterRegistry registry) throws Exception {
        Adaptable adaptable = null;
        for (final Annotation annotation : clazz.getAnnotations()) {
            if (annotation instanceof Adaptable a) {
                adaptable = a;
                break;
            }
        }
        if (adaptable == null) {
            throw new IllegalArgumentException("Class '" + clazz.getName() + "' must have the Adaptable annotation!");
        }

        this.clazz = clazz;
        this.config = dfl;
        this.typeAdapters = registry;
        this.typeAdapters.makeAdapter(clazz, adaptable);

        final LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setProcessComments(true);

        final DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setProcessComments(true);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        this.loaderOptions = loaderOptions;
        this.dumperOptions = dumperOptions;
        this.yaml = new Yaml(new YamlConstructor(loaderOptions), new YamlRepresenter(dumperOptions), dumperOptions, loaderOptions);
    }

    public void load(final File file) throws IOException {
        this.load(file, StandardCharsets.UTF_8);
    }

    public void load(final File file, final Charset charset) throws IOException {
        try (final InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            this.load(is, charset);
        }
    }

    public void load(final InputStream is) throws IOException {
        this.load(is, StandardCharsets.UTF_8);
    }

    public void load(final InputStream is, final Charset charset) throws IOException {
        this.load(new InputStreamReader(is, charset));
    }

    public void load(final Reader reader) {
        final Object serialized = this.yaml.load(reader);

        this.config = (T)this.typeAdapters.deserialize(serialized, this.clazz);
    }

    public void save(final File file) throws IOException {
        this.save(file, "");
    }

    public void save(final File file, final String header) throws IOException {
        if (file.isDirectory()) {
            throw new IOException("File is a directory");
        }

        final File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        final File tmp = new File(parent, file.getName() + ".tmp");
        tmp.delete();
        tmp.createNewFile();
        try {
            try (final OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp))) {
                this.save(os, header);
            }

            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException ex) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            tmp.delete();
        }
    }

    public void save(final OutputStream os) throws IOException {
        os.write(this.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    public void save(final OutputStream os, final String header) throws IOException {
        os.write(this.saveToString(header).getBytes(StandardCharsets.UTF_8));
    }

    public String saveToString() {
        return this.yaml.dump(this.typeAdapters.serialize(this.config, this.clazz));
    }

    public String saveToString(final String header) {
        if (header.isBlank()) {
            return this.saveToString();
        }

        final StringBuilder ret = new StringBuilder();
        final String lineBreak = this.dumperOptions.getLineBreak().getString();

        for (final String line : header.split("\n")) {
            ret.append("# ").append(line.trim()).append(lineBreak);
        }

        ret.append(lineBreak);

        return ret.append(this.saveToString()).toString();
    }

    public void callInitialisers() {
        this.typeAdapters.callInitialisers(this.config);
    }

    public static record ParsedNumber(Number parsed, String original) {}

    private static final class YamlConstructor extends Constructor {

        public YamlConstructor(final LoaderOptions loadingConfig) {
            super(loadingConfig);

            // solve norway problem

            this.yamlConstructors.put(Tag.BOOL, new ConstructBoolean());

            // remove the yaml 1.1 base 60 parsing with ":" - who could possibly even expect parsing to work this way

            this.yamlConstructors.put(Tag.INT, new ConstructInteger());
            this.yamlConstructors.put(Tag.FLOAT, new ConstructFloat());
        }

        private final class ConstructBoolean extends AbstractConstruct {

            @Override
            public Object construct(final Node node) {
                final String original = YamlConstructor.this.constructScalar((ScalarNode)node);
                if (original.length() > 5) {
                    return original;
                }
                switch (original) {
                    case "false": {
                        return Boolean.FALSE;
                    }
                    case "true": {
                        return Boolean.TRUE;
                    }
                    default: {
                        return original;
                    }
                }
            }
        }

        private final class ConstructInteger extends AbstractConstruct {

            /* https://yaml.org/type/int.html */

            private static final int[] MAX_CHARS_BY_RADIX_INT32 = new int[Character.MAX_RADIX + 1];
            private static final int[] MAX_CHARS_BY_RADIX_INT64 = new int[Character.MAX_RADIX + 1];
            static {
                for (int i = 0; i <= Character.MAX_RADIX; ++i) {
                    MAX_CHARS_BY_RADIX_INT32[i] = i < Character.MIN_RADIX ? -1 : Integer.toString(Integer.MIN_VALUE, i).length() - 1;
                    MAX_CHARS_BY_RADIX_INT64[i] = i < Character.MIN_RADIX ? -1 : Long.toString(Long.MIN_VALUE, i).length() - 1;
                }
            }

            private static Number parse(String value, final boolean positive, final int radix) {
                final int len = value.length();
                if (!positive) {
                    value = "-".concat(value);
                }

                final int int32Max = MAX_CHARS_BY_RADIX_INT32[radix];
                final int int64Max = MAX_CHARS_BY_RADIX_INT64[radix];

                if (len < int32Max) {
                    return Integer.valueOf(Integer.parseInt(value, radix));
                }
                if (len < int64Max) {
                    final long ret = Long.parseLong(value, radix);
                    if (ret > (long)Integer.MAX_VALUE || ret < (long)Integer.MIN_VALUE) {
                        return Long.valueOf(ret);
                    }
                    return Integer.valueOf((int)ret);
                }

                final BigInteger ret = new BigInteger(value, radix);
                if (len > int64Max) {
                    return ret;
                }

                if (ret.bitLength() < Long.SIZE) {
                    return ret.longValueExact();
                }

                return ret;
            }

            @Override
            public Object construct(final Node node) {
                final String original = YamlConstructor.this.constructScalar((ScalarNode)node);
                final String scalar = original.replace("_", "");

                if (scalar.isEmpty()) {
                    throw new AccessibleConstructorException("while parsing integer", node.getStartMark(), "scalar is empty", node.getEndMark());
                }

                // parse sign
                final boolean positive;
                final String toParse;

                switch (scalar.charAt(0)) {
                    case '+': {
                        toParse = scalar.substring(1);
                        positive = true;
                        break;
                    }
                    case '-': {
                        toParse = scalar.substring(1);
                        positive = false;
                        break;
                    }
                    default: {
                        toParse = scalar;
                        positive = true;
                        break;
                    }
                }

                // parse base

                if (toParse.isEmpty()) {
                    throw new AccessibleConstructorException("while parsing integer", node.getStartMark(), "only contains sign", node.getEndMark());
                }

                if (toParse.indexOf(':') != -1) {
                    // I don't think anyone using ':' expects this to be parsed as an integer...
                    // Route to string
                    return original;
                }

                if (toParse.charAt(0) == '0') {
                    if (toParse.length() == 1) {
                        // toParse == "0"
                        return new ParsedNumber(Integer.valueOf(0), original);
                    }
                    switch (toParse.charAt(1)) {
                        case 'b': {
                            return new ParsedNumber(parse(toParse.substring(2), positive, 2), original);
                        }
                        case 'x': {
                            return new ParsedNumber(parse(toParse.substring(2), positive, 16), original);
                        }
                        default: {
                            return new ParsedNumber(parse(toParse.substring(1), positive, 8), original);
                        }
                    }
                } else {
                    return new ParsedNumber(parse(toParse, positive, 10), original);
                }
            }
        }

        private final class ConstructFloat extends AbstractConstruct {

            /* https://yaml.org/type/float.html */

            @Override
            public Object construct(final Node node) {
                final String original = YamlConstructor.this.constructScalar((ScalarNode)node);
                String scalar = original.replace("_", "");

                if (scalar.isEmpty()) {
                    throw new AccessibleConstructorException("while parsing float", node.getStartMark(), "scalar is empty", node.getEndMark());
                }

                // handle inf, nan
                boolean positive;

                final String infnan;
                switch (scalar.charAt(0)) {
                    case '+': {
                        infnan = scalar.substring(1);
                        positive = true;
                        break;
                    }
                    case '-': {
                        infnan = scalar.substring(1);
                        positive = false;
                        break;
                    }
                    default: {
                        infnan = scalar;
                        positive = true;
                        break;
                    }
                }
                // .inf/Infinity or .nan/NaN
                if (infnan.equalsIgnoreCase(".nan") || infnan.equals("NaN")) {
                    return new ParsedNumber(Double.valueOf(Double.NaN), original);
                }
                if (infnan.equalsIgnoreCase(".inf") || infnan.equals("Infinity")) {
                    return new ParsedNumber(positive ? Double.valueOf(Double.POSITIVE_INFINITY) : Double.valueOf(Double.NEGATIVE_INFINITY), original);
                }

                if (scalar.indexOf(':') != -1) {
                    // I don't think anyone using ':' expects this to be parsed as a float...
                    // Route to string
                    return original;
                }

                // force all floats to be big decimal to avoid precision loss for BigDecimal types
                final BigDecimal ret = new BigDecimal(scalar);
                if (ret.compareTo(BigDecimal.ZERO) == 0) {
                    // handle negative zero by passing Double instead
                    return new ParsedNumber(!positive ? Double.valueOf(-0.0) : ret, original);
                }
                return new ParsedNumber(ret, original);
            }
        }

        private static final class AccessibleConstructorException extends ConstructorException {

            AccessibleConstructorException(final String context, final Mark contextMark, final String problem, final Mark problemMark, final Throwable cause) {
                super(context, contextMark, problem, problemMark, cause);
            }

            AccessibleConstructorException(final String context, final Mark contextMark, final String problem, final Mark problemMark) {
                super(context, contextMark, problem, problemMark);
            }
        }
    }

    private static final class YamlRepresenter extends Representer {

        public YamlRepresenter(final DumperOptions options) {
            super(options);

            this.representers.put(TypeAdapterRegistry.CommentedData.class, new CommentedDataRepresenter());
        }

        private final class CommentedDataRepresenter implements Represent {

            @Override
            public Node representData(final Object data0) {
                final TypeAdapterRegistry.CommentedData commentedData = (TypeAdapterRegistry.CommentedData)data0;

                final Node node = YamlRepresenter.this.representData(commentedData.data);

                final List<CommentLine> comments = new ArrayList<>();

                for (final String line : commentedData.comment.split("\n")) {
                    comments.add(new CommentLine(null, null, " ".concat(line.trim()), CommentType.BLOCK));
                }

                node.setBlockComments(comments);

                return node;
            }
        }
    }
}
