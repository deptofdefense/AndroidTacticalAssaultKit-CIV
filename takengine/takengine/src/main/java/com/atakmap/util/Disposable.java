package com.atakmap.util;

/**
 * Interface supporting object disposal. Disposal should release all resources
 * associated with the object; use of the Object following disposal is
 * undefined.
 *  
 * @author Developer
 * 
 * @see com.atakmap.Disposer
 * @see com.atakmap.util.Releasable
 */
public interface Disposable {
    
    /**
     * Releases all resources associated with the Object. The Object should not
     * be used following an invocation of this method and any usage will result
     * in undefined and possible application ending behavior.
     */
    public void dispose();
}
