package com.atakmap.map.layer.model;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.math.Matrix;

import gov.tak.api.annotation.DontObfuscate;

/**
 * @deprecated use {@link Mesh}
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
@DontObfuscate
public interface Model extends Mesh {

    public final static int INSTANCE_ID_NONE = 0;

    /**
     * Returns the number of meshes within this model
     * @return
     */
    public int getNumMeshes();

    /**
     * Get the mesh at a given index, post-transformed. Equivalent to invoking
     * <code>getMesh(i, true)</code>
     * @param i
     * @return
     */
    public Mesh getMesh(int i);

    /**
     * Retrieves the specified mesh.
     *
     * @param i             The mesh ID
     * @param postTransform If <code>true</code>, the mesh will have any local
     *                      transform applied. If <code>false</code> the mesh
     *                      will not have its local transform applied.
     * @return
     */
    public Mesh getMesh(int i, boolean postTransform);

    /**
     * Retrieves the transform associated with the specified mesh.
     *
     * @param meshId    The mesh ID
     * @return  The transform for the mesh, or <code>null</code> if the mesh
     *          does not have an associated transform
     */
    public Matrix getTransform(int meshId);

    /**
     * Reserved for future scene node information
     * @return
     */
    public SceneNode getRootSceneNode();

    /**
     * Returns the instance ID associated with the given mesh
     * @param meshId
     * @return
     */
    public int getInstanceId(int meshId);
}
