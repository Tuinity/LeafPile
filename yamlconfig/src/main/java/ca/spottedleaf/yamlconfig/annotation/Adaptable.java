package ca.spottedleaf.yamlconfig.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used on a class to indicate that its type adapter may automatically be generated. The class must have
 * a public no-args constructor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Adaptable {

    /**
     * Whether to use the field declaration order instead of sorting the fields by name.
     */
    public boolean useDeclarationOrder() default false;

}
