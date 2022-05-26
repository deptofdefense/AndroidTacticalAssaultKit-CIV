
package com.atakmap.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to mark API elements as an incubating API. Defines when the Incubating
 * API element is introduced.
 *
 * <P>The annotation conveys following information:
 * <UL>
 *   <LI>The API is fairly new and we would appreciate your feedback. For
 *       example: what are you missing from the API to solve your use case?
 *   <LI>The API might change before becoming stable or could be removed if
 *       found unncessary.
 * </UL>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD,
        ElementType.TYPE,
})
@Documented
public @interface IncubatingApi {
    /**
     * Specifies the version (format: MAJOR.MINOR, e.g. <code>"3.11"</code>) in
     * which the API element was introduced.
     * @return The version in which the API element was introduced.
     */
    String since();
}
