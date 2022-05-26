
package com.atakmap.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The <code>@ModifierApi</code> annotation supplements other built in code
 * deprecation mechanisms to inform API consumers when an modifier has been
 * marked for change and in which version it will be .
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD,
        ElementType.TYPE,
})
@Documented
public @interface ModifierApi {
    /**
     * Specifies the version (format: MAJOR.MINOR, e.g. <code>"3.11"</code>) in
     * which the API field was identifier for modification.
     * @return The version in which the API element was marked.
     */
    String since();

    /**
     * Specifies the list of modifiers that will be enforced when the
     * target is released
     * @return the new list of modifiers for the API change.
     */
    String[] modifiers();

    /**
     * Specifies the version (format: MAJOR.MINOR, e.g. <code>"3.11"</code>) in
     * which the the modifier will be changed. The default value is
     * the empty string.
     * @return The version in which the modifier will be enforced.
     */
    String target() default "";
}
