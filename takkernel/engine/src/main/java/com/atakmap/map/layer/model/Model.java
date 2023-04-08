package com.atakmap.map.layer.model;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.Matrix;

import gov.tak.api.annotation.DontObfuscate;
import gov.tak.api.util.Disposable;

@DontObfuscate
public interface Model extends Disposable {

    int INSTANCE_ID_NONE = 0;

    /**
     * Returns the number of meshes within this model
     * @return
     */
    int getNumMeshes();

    /**
     * Get the mesh at a given index, post-transformed. Equivalent to invoking
     * <code>getMesh(i, true)</code>
     * @param i
     * @return
     */
    Mesh getMesh(int i);

    /**
     * Retrieves the specified mesh.
     *
     * @param i             The mesh ID
     * @param postTransform If <code>true</code>, the mesh will have any local
     *                      transform applied. If <code>false</code> the mesh
     *                      will not have its local transform applied.
     * @return
     */
    Mesh getMesh(int i, boolean postTransform);

    /**
     * Retrieves the transform associated with the specified mesh.
     *
     * @param meshId    The mesh ID
     * @return  The transform for the mesh, or <code>null</code> if the mesh
     *          does not have an associated transform
     */
    Matrix getTransform(int meshId);

    /**
     * Reserved for future scene node information
     * @return
     */
    SceneNode getRootSceneNode();

    /**
     * Returns the instance ID associated with the given mesh
     * @param meshId
     * @return
     */
    int getInstanceId(int meshId);

    Envelope getAABB();
}
