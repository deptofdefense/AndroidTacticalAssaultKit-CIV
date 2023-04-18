package com.atakmap.map.layer.model;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.Matrix;

final class MeshModel implements Model {
    final Mesh mesh;

    MeshModel(Mesh mesh) {
        this.mesh = mesh;
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }

    @Override
    public Envelope getAABB() {
        return mesh.getAABB();
    }

    @Override
    public int getNumMeshes() {
        return 1;
    }

    @Override
    public Mesh getMesh(int i) {
        if(i != 0)
            throw new IndexOutOfBoundsException();
        return mesh;
    }

    @Override
    public Mesh getMesh(int i, boolean postTransform) {
        return getMesh(i);
    }

    @Override
    public Matrix getTransform(int meshId) {
        return null;
    }

    @Override
    public SceneNode getRootSceneNode() {
        return null;
    }

    @Override
    public int getInstanceId(int meshId) {
        return 0;
    }
}