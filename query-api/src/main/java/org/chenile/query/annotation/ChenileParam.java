package org.chenile.query.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used on repository method parameters to specify the filter key
 * that the parameter should be mapped to in a Chenile search request.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChenileParam {
    /**
     * The name of the filter key in the search request.
     */
    String value();
}
