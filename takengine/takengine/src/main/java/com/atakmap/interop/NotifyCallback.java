package com.atakmap.interop;

/**
 * Generic callback interface.
 */
public interface NotifyCallback {
    /**
     *
     * @return  <code>false</code> if the callback is no longer interested in receiving updates
     */
    boolean onEvent();
}
