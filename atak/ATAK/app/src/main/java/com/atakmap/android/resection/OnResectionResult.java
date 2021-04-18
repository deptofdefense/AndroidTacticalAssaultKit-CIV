/*
 * Copyright 2020 PAR Government Systems
 *
 *  Unlimited Rights:
 *  PAR Government retains ownership rights to this software.  The Government has Unlimited Rights
 *  to use, modify, reproduce, release, perform, display, or disclose this
 *  software as identified in the purchase order contract. Any
 *  reproduction of computer software or portions thereof marked with this
 *  legend must also reproduce the markings. Any person who has been provided
 *  access to this software must be aware of the above restrictions.
 */
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
