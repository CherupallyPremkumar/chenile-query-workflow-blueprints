package org.chenile.query.repository.annotation;

import org.springframework.context.annotation.Import;
import java.lang.annotation.*;

/**
 * Annotation to enable automatic registration of ChenileRepository interfaces.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ChenileRepositoriesRegistrar.class)
public @interface EnableChenileRepositories {
    /**
     * Base packages to scan for annotated components.
     */
    String[] value() default {};

    /**
     * Base packages to scan for annotated components.
     */
    String[] basePackages() default {};
}
