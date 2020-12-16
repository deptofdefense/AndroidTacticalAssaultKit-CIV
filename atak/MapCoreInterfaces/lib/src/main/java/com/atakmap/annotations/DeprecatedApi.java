
package com.atakmap.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The <code>@DeprecatedApi</code> annotation supplements other built in code
 * deprecation mechanisms to inform API consumers when an API was deprecated
 * and in which version it will be removed.
 *
 * <P>This annotation should be used in addition to the {@link Deprecated}
 * annotation and the <code>@deprecated</code> Javadoc tag. Please refer to the
 * ATAK Coding Style Guide for more information on deprecated APIs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD,
        ElementType.TYPE,
})
@Documented
public @interface DeprecatedApi {
    /**
     * Specifies the version (format: MAJOR.MINOR, e.g. <code>"3.11"</code>) in
     * which the API element was deprecated.
     * @return The version in which the API element was deprecated.
     */
    String since();

    /**
     * Indicates whether the annotated element is subject to removal in a
     * future version. The default value is <code>false</code>.
     * @return Whether the annotated element is subject for removal in a future
     *         version.
     */
    boolean forRemoval() default false;

    /**
     * Specifies the version (format: MAJOR.MINOR, e.g. <code>"3.11"</code>) in
     * which the deprecated API element will be removed. The default value is
     * the empty string.
     * @return The version in which the deprecated API element will be removed.
     */
    String removeAt() default "";
}
