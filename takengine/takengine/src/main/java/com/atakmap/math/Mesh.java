package com.atakmap.math;

import com.atakmap.interop.Pointer;

public class Mesh extends NativeGeometryModel {
    
    public Mesh(double[] vertices, int numVertexColumns, int numVertexRows) {
        super(create(getVertices(vertices, numVertexColumns*numVertexRows), numVertexColumns, numVertexRows), null);
    }

    public Mesh(PointD[] vertices, int numVertexColumns, int numVertexRows) {
        this(getVertices(vertices, numVertexColumns*numVertexRows), numVertexColumns, numVertexRows);
    }

    Mesh(Pointer pointer, Object owner) {
        super(pointer, owner);
    }

    private static double[] getVertices(double[] vertices, int count) {
        if(count*3 < vertices.length)
            throw new IllegalArgumentException("vertices does not have required number of elements");
        return vertices;
    }
    private static double[] getVertices(PointD[] vertices, int count) {
        final double[] mesh = new double[count*3];

        int idx = 0;
        for(int i = 0; i < count; i++) {
            mesh[idx++] = vertices[i].x;
            mesh[idx++] = vertices[i].y;
            mesh[idx++] = vertices[i].z;
        }

        return mesh;
    }

    private static native Pointer create(double[] vertices, int numCols, int numRows);
}
