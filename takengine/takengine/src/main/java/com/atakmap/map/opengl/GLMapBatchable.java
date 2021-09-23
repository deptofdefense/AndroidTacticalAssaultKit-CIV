
package com.atakmap.map.opengl;

import com.atakmap.opengl.GLRenderBatch;

/**
 * Interface to be implemented by renderables that contain batchable content.
 * 
 * @author Developer
 */
public interface GLMapBatchable {
    /**
     * Returns a flag indicating whether or not the renderable is immediately
     * batchable. If <code>false</code> is returned, the renderable may be
     * queried again during a subsequent render/batch pump.
     * 
     * @param view  The view
     * 
     * @return  <code>true</code> if the renderable can be batched,
     *          <code>false</code> otherwise.
     */
    public boolean isBatchable(GLMapView view);

    /**
     * Adds the content for the renderable to the specified batch. Any
     * modifications to the scene (setting color, modifying the matrix, etc.)
     * may produce undefined and undesirable results.
     * 
     * <P>It should be assumed that {@link #isBatchable(GLMapView)} is always
     * invoked immediately prior to the invocation of this method and that this
     * method will be executed if and only if {@link #isBatchable(GLMapView)}
     * returned <code>true</code>.
     * 
     * @param view  The view
     * @param batch The batch
     */
    public void batch(GLMapView view, GLRenderBatch batch);
}
