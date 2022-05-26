package com.atakmap.interop;

import gov.tak.api.annotation.DontObfuscate;

/**
 * Generic callback interface.
 */
@DontObfuscate
public interface NotifyCallback {
    /**
     *
     * @return  <code>false</code> if the callback is no longer interested in receiving updates
     */
    boolean onEvent();
}
