
package com.atakmap.android.resection;

/**
 * Describes a resectioning workflow. This workflow may also be used for describing a GPS-denied
 * navigation aid.
 */
public interface ResectionWorkflow {
    /**
     * Gets a descriptive name of.
     * @return Descriptive name
     */
    String getName();

    /**
     * Gets a short description of how the tool works.
     * @return Description
     */
    String getDescription();

    /**
     * Gets a description of the ideal conditions for the tool.
     * @return Ideal conditions
     */
    String getIdealConditions();

    /**
     * Gets a description of the relative accuracy of the tool.
     * @return String description of the relative accuracy
     */
    String getRelativeAccuracy();

    /**
     * Starts the workflow.
     * @param callback Callback that will be called by the resectioning workflow when it has
     *                 computed a result
     */
    void start(OnResectionResult callback);
}
