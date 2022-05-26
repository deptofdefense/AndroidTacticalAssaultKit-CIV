package com.atakmap.util;

/**
 * Interface supporting object release. Release should release all resources
 * currently associated with the object; use of the Object following disposal is
 * will re-initialize resources as needed. The Object should be able to cycle
 * between the released and initialized state without restriction during the
 * lifetime of the object.
 *  
 * @author Developer
 * 
 * @see com.atakmap.util.Disposable
 */
public interface Releasable {
    
    /**
     * Releases all resources associated with the Object. If the Object is used
     * subsequently, resources will be reinitialized as appropriate.
     */
    public void release();
}