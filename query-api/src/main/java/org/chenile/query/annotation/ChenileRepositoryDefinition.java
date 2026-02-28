package org.chenile.query.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a ChenileRepository interface for automatic registration.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChenileRepositoryDefinition {
    /**
     * The model/POJO/DTO class that this repository handles.
     */
    Class<?> entityClass();
}
