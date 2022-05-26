package com.atakmap.map.layer.model;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.nio.Buffer;

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
    public int getNumVertices() {
        return mesh.getNumVertices();
    }

    @Override
    public int getNumFaces() {
        return mesh.getNumFaces();
    }

    @Override
    public boolean isIndexed() {
        return mesh.isIndexed();
    }

    @Override
    public void getPosition(int i, PointD xyz) {
        mesh.getPosition(i, xyz);
    }

    @Override
    public void getTextureCoordinate(int texCoordNum, int i, PointD uv) {
        mesh.getTextureCoordinate(texCoordNum, i, uv);
    }

    @Override
    public void getNormal(int i, PointD xyz) {
        mesh.getNormal(i, xyz);
    }

    @Override
    public int getColor(int i) {
        return mesh.getColor(i);
    }

    @Override
    public Class<?> getVertexAttributeType(int attr) {
        return mesh.getVertexAttributeType(attr);
    }

    @Override
    public Class<?> getIndexType() {
        return mesh.getIndexType();
    }

    @Override
    public int getIndex(int i) {
        return mesh.getIndex(i);
    }

    @Override
    public Buffer getIndices() {
        return mesh.getIndices();
    }

    @Override
    public int getIndexOffset() {
        return mesh.getIndexOffset();
    }

    @Override
    public Buffer getVertices(int attr) {
        return mesh.getVertices(attr);
    }

    @Override
    public WindingOrder getFaceWindingOrder() {
        return mesh.getFaceWindingOrder();
    }

    @Override
    public DrawMode getDrawMode() {
        return mesh.getDrawMode();
    }

    @Override
    public Envelope getAABB() {
        return mesh.getAABB();
    }

    @Override
    public VertexDataLayout getVertexDataLayout() {
        return mesh.getVertexDataLayout();
    }

    @Override
    public int getNumMaterials() {
        return mesh.getNumMaterials();
    }

    @Override
    public Material getMaterial(int index) {
        return mesh.getMaterial(index);
    }

    @Override
    public Material getMaterial(Material.PropertyType propertyType) {
        return mesh.getMaterial(propertyType);
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