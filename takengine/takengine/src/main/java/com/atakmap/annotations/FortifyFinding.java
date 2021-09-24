
package com.atakmap.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The <code>@FortifyFinding</code> annotation supplements other built in code
 * mechanisms to inform FortifyFinding that an issue has been adjudicated and the
 * rational for the issue
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD,
        ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.LOCAL_VARIABLE,
        ElementType.TYPE_USE
})
@Documented
public @interface FortifyFinding {

    /**
     * Specifies the version (format: MAJOR.MINOR[.SUBMAJOR.SUBMINOR], e.g. <code>"4.3"</code>) in
     * which the finding was adjudicated. The default value is the empty string.
     * @return The version in which the modifier will be enforced.
     */
    String target() default "";


    /**
     * The fortify finding from the report.
     */
    String finding();

    /**
     * The response for the finding.
     */
     String rational();

    /**
     * The remediation identifier that maps back to the to a record in a third party system.
     */
    String remediationId() default "";

}
