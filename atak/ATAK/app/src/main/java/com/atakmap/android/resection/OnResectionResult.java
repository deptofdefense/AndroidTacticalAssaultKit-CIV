
package com.atakmap.android.resection;

/**
 * This interface describes an event that is fired by a Resection Workflow implementation when it
 * has computed a location estimate.
 */
public interface OnResectionResult {
    /**
     * Fires when a location estimate has been computed
     * @param rwf The {@link ResectionWorkflow} that computed the estimate
     * @param estimate The location estimate
     */
    void result(ResectionWorkflow rwf, ResectionLocationEstimate estimate);
}
