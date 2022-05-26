package com.atakmap.interop;

import gov.tak.api.annotation.DontObfuscate;

/**
 * Generic callback interface for progress events.
 */
@DontObfuscate
public interface ProgressCallback {
    /**
     * Called when progress has been updated.   The value of the progress is defined outside of
     * this interface
     * @param value the numeric value defining progress.
     */
    void progress(int value);

    /**
     * Called when an error has occured
     * @param msg the message to be used for determining when the error has occured.
     */
    void error(String msg);
}
