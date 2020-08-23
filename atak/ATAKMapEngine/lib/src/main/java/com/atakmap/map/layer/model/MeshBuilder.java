package com.atakmap.map.layer.model;

import com.atakmap.interop.DataType;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.MathUtils;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public final class MeshBuilder implements Disposable {
    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            ModelBuilder.destruct(pointer);
        }
    };

    private final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;

    private Pointer pointer;

    MeshBuilder(ByteBuffer vertexData, int vertexAttr, Envelope aabb, Model.DrawMode drawMode) {
        throw new UnsupportedOperationException();
    }


    public MeshBuilder(int vertexAttr, boolean indexed, Model.DrawMode drawMode) {
        int tedm;
        switch(drawMode) {
            case Points:
                tedm = NativeMesh.getTEDM_Points();
                break;
            case Triangles:
                tedm = NativeMesh.getTEDM_Triangles();
                break;
            case TriangleStrip:
                tedm = NativeMesh.getTEDM_TriangleStrip();
                break;
            default :
                throw new IllegalArgumentException();
        }
        final int cattrs = NativeMesh.getNativeAttributes(vertexAttr);
        if(indexed) {
            this.pointer = ModelBuilder.create(tedm, cattrs, DataType.TEDT_UInt16);
        } else {
            this.pointer = ModelBuilder.create(tedm, cattrs);
        }

        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
    }

    public MeshBuilder(int vertexAttr, Class<?> indexType, Model.DrawMode drawMode) {
        int tedm;
        switch(drawMode) {
            case Points:
                tedm = NativeMesh.getTEDM_Points();
                break;
            case Triangles:
                tedm = NativeMesh.getTEDM_Triangles();
                break;
            case TriangleStrip:
                tedm = NativeMesh.getTEDM_TriangleStrip();
                break;
            default :
                throw new IllegalArgumentException();
        }
        final int cattrs = NativeMesh.getNativeAttributes(vertexAttr);
        if(indexType != null) {
            this.pointer = ModelBuilder.create(tedm, cattrs, DataType.convert(indexType, false));
        } else {
            this.pointer = ModelBuilder.create(tedm, cattrs);
        }

        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
    }

    public MeshBuilder(VertexDataLayout layout, Class<?> indexType, Model.DrawMode drawMode) {
        int tedm;
        switch(drawMode) {
            case Points:
                tedm = NativeMesh.getTEDM_Points();
                break;
            case Triangles:
                tedm = NativeMesh.getTEDM_Triangles();
                break;
            case TriangleStrip:
                tedm = NativeMesh.getTEDM_TriangleStrip();
                break;
            default :
                throw new IllegalArgumentException();
        }
        final int cattrs = NativeMesh.getNativeAttributes(layout.attributes);
        if(indexType != null) {
            this.pointer = ModelBuilder.create(tedm,
                    cattrs,
                    layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32, layout.position.offset, layout.position.stride,
                    layout.texCoord0.dataType != null ? DataType.convert(layout.texCoord0.dataType, false) : DataType.TEDT_Float32, layout.texCoord0.offset, layout.texCoord0.stride,
                    layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32, layout.normal.offset, layout.normal.stride,
                    layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32, layout.color.offset, layout.color.stride,
                    layout.interleaved,
                    DataType.convert(indexType, false));
        } else {
            this.pointer = ModelBuilder.create(tedm,
                    cattrs,
                    layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32, layout.position.offset, layout.position.stride,
                    layout.texCoord0.dataType != null ? DataType.convert(layout.texCoord0.dataType, false) : DataType.TEDT_Float32, layout.texCoord0.offset, layout.texCoord0.stride,
                    layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32, layout.normal.offset, layout.normal.stride,
                    layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32, layout.color.offset, layout.color.stride,
                    layout.interleaved);
        }

        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);
    }

    public void reserveVertices(int count) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            ModelBuilder.reserveVertices(this.pointer.raw, count);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    public void reserveIndices(int count) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            ModelBuilder.reserveIndices(this.pointer.raw, count);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    public void setWindingOrder(Model.WindingOrder windingOrder) {
        this.rwlock.acquireRead();
        try {
            if (this.pointer.raw == 0L)
                throw new IllegalStateException();
            switch (windingOrder) {
                case Clockwise:
                    ModelBuilder.setWindingOrder(this.pointer.raw, NativeMesh.getTEWO_Clockwise());
                    break;
                case CounterClockwise:
                    ModelBuilder.setWindingOrder(this.pointer.raw, NativeMesh.getTEWO_CounterClockwise());
                    break;
                case Undefined:
                    ModelBuilder.setWindingOrder(this.pointer.raw, NativeMesh.getTEWO_Undefined());
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void addMaterial(Material material) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            ModelBuilder.addMaterial(this.pointer.raw, material.getTextureUri(), material.getColor());
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void addVertex(double posx, double posy, double posz,
                          float texu, float texv,
                          float nx, float ny, float nz,
                          float r, float g, float b, float a) {

        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            ModelBuilder.addVertex(
                    this.pointer.raw,
                    posx, posy, posz,
                    texu, texv,
                    nx, ny, nz,
                    r, g, b, a);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void addVertex(double posx, double posy, double posz,
                          float[] texuv,
                          float nx, float ny, float nz,
                          float r, float g, float b, float a) {

        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            ModelBuilder.addVertex(
                    this.pointer.raw,
                    posx, posy, posz,
                    texuv,
                    nx, ny, nz,
                    r, g, b, a);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void addFace(int a, int b, int c) {
        this.addIndex(a);
        this.addIndex(b);
        this.addIndex(c);
    }

    public void addIndex(int index) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            ModelBuilder.addIndex(this.pointer.raw, index);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    /**
     *
     * @param indices
     * @param off
     * @param count The number of faces
     */
    public void addIndices(int[] indices, int off, int count) {
        // XXX -
        for(int i = 0; i < count; i++) {
            this.addIndex(indices[off+i]);
        }
    }

    public void addIndices(short[] indices, int off, int count) {
        // XXX -
        for(int i = 0; i < count; i++) {
            this.addIndex(indices[off+i]&0xFFFF);
        }
    }

    public void addIndices(IntBuffer indices) {
        // XXX -
        while(indices.hasRemaining())
            this.addIndex(indices.get());
    }

    public void addIndices(ShortBuffer indices) {
        // XXX -
        while(indices.hasRemaining())
            this.addIndex(indices.get()&0xFFFF);
    }

    public Mesh build() {
        this.rwlock.acquireWrite();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final Mesh retval = new NativeMesh(ModelBuilder.build(this.pointer.raw));
            ModelBuilder.destruct(this.pointer);
            return retval;
        } finally {
            this.rwlock.releaseWrite();
        }
    }

    @Override
    public void dispose() {
        if(cleaner != null)
            cleaner.clean();
    }

    public static Mesh build(Mesh.DrawMode drawMode, Mesh.WindingOrder winding, VertexDataLayout layout, Material[] materials, Envelope aabb, int numVertices, Buffer data) {
        final int numMaterials = materials.length;
        int[] matType = new int[numMaterials];
        String[] matTexUri = new String[numMaterials];
        int[] matColor = new int[numMaterials];
        for(int i = 0; i < numMaterials; i++) {
            // XXX -
            matType[i] = materials[i].getPropertyType().ordinal();

            matTexUri[i] = materials[i].getTextureUri();
            matColor[i] = materials[i].getColor();
        }
        final int cattrs = NativeMesh.getNativeAttributes(layout.attributes);
        int[] texTypes = new int[8];
        int[] texOffs = new int[8];
        int[] texStrides = new int[8];
        buildTexCoordAttrs(layout, texTypes, texOffs, texStrides);
        return new NativeMesh(
            ModelBuilder.build(
                    NativeMesh.convertDrawMode(drawMode),
                    NativeMesh.convertWindingOrder(winding),
                    cattrs,
                    layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32, layout.position.offset, layout.position.stride,
                    texTypes, texOffs, texStrides,
                    layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32, layout.normal.offset, layout.normal.stride,
                    layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32, layout.color.offset, layout.color.stride,
                    numMaterials,
                    matType,
                    matTexUri,
                    matColor,
                    aabb.minX, aabb.minY, aabb.minZ,
                    aabb.maxX, aabb.maxY, aabb.maxZ,
                    numVertices,
                    data)
        );
    }

    public static Mesh build(Mesh.DrawMode drawMode, Mesh.WindingOrder winding, VertexDataLayout layout, Material[] materials, Envelope aabb, int numVertices, Buffer data, Class<?> indexType, int numIndices, Buffer indices) {
        final int numMaterials = materials.length;
        int[] matType = new int[numMaterials];
        String[] matTexUri = new String[numMaterials];
        int[] matColor = new int[numMaterials];
        for(int i = 0; i < numMaterials; i++) {
            // XXX -
            matType[i] = materials[i].getPropertyType().ordinal();

            matTexUri[i] = materials[i].getTextureUri();
            matColor[i] = materials[i].getColor();
        }
        final int cattrs = NativeMesh.getNativeAttributes(layout.attributes);
        int[] texTypes = new int[8];
        int[] texOffs = new int[8];
        int[] texStrides = new int[8];
        buildTexCoordAttrs(layout, texTypes, texOffs, texStrides);
        return new NativeMesh(
                ModelBuilder.build(
                        NativeMesh.convertDrawMode(drawMode),
                        NativeMesh.convertWindingOrder(winding),
                        cattrs,
                        layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32, layout.position.offset, layout.position.stride,
                        texTypes, texOffs, texStrides,
                        layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32, layout.normal.offset, layout.normal.stride,
                        layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32, layout.color.offset, layout.color.stride,
                        numMaterials,
                        matType,
                        matTexUri,
                        matColor,
                        aabb.minX, aabb.minY, aabb.minZ,
                        aabb.maxX, aabb.maxY, aabb.maxZ,
                        numVertices,
                        data,
                        DataType.convert(indexType, false),
                        numIndices,
                        indices)
        );
    }

    public static Mesh build(Mesh.DrawMode drawMode, Mesh.WindingOrder winding, VertexDataLayout layout, Material[] materials, Envelope aabb, int numVertices, Buffer positions, Buffer[] texCoords, Buffer normals, Buffer colors) {
        final int numMaterials = materials.length;
        int[] matType = new int[numMaterials];
        String[] matTexUri = new String[numMaterials];
        int[] matColor = new int[numMaterials];
        for(int i = 0; i < numMaterials; i++) {
            // XXX -
            matType[i] = materials[i].getPropertyType().ordinal();

            matTexUri[i] = materials[i].getTextureUri();
            matColor[i] = materials[i].getColor();
        }
        final int cattrs = NativeMesh.getNativeAttributes(layout.attributes);
        int[] texTypes = new int[8];
        int[] texOffs = new int[8];
        int[] texStrides = new int[8];
        buildTexCoordAttrs(layout, texTypes, texOffs, texStrides);

        return new NativeMesh(
                ModelBuilder.build(
                        NativeMesh.convertDrawMode(drawMode),
                        NativeMesh.convertWindingOrder(winding),
                        cattrs,
                        layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32, layout.position.offset, layout.position.stride,
                        texTypes, texOffs, texStrides,
                        layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32, layout.normal.offset, layout.normal.stride,
                        layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32, layout.color.offset, layout.color.stride,
                        numMaterials,
                        matType,
                        matTexUri,
                        matColor,
                        aabb.minX, aabb.minY, aabb.minZ,
                        aabb.maxX, aabb.maxY, aabb.maxZ,
                        numVertices,
                        positions,
                        texCoords,
                        normals,
                        colors)
        );
    }

    public static Mesh build(Mesh.DrawMode drawMode, Mesh.WindingOrder winding, VertexDataLayout layout, Material[] materials, Envelope aabb, int numVertices, Buffer positions, Buffer[] texCoords, Buffer normals, Buffer colors, Class<?> indexType, int numIndices, Buffer indices) {
        final int numMaterials = materials.length;
        int[] matType = new int[numMaterials];
        String[] matTexUri = new String[numMaterials];
        int[] matColor = new int[numMaterials];
        for(int i = 0; i < numMaterials; i++) {
            // XXX -
            matType[i] = materials[i].getPropertyType().ordinal();

            matTexUri[i] = materials[i].getTextureUri();
            matColor[i] = materials[i].getColor();
        }
        final int cattrs = NativeMesh.getNativeAttributes(layout.attributes);
        int[] texTypes = new int[8];
        int[] texOffs = new int[8];
        int[] texStrides = new int[8];
        buildTexCoordAttrs(layout, texTypes, texOffs, texStrides);

        return new NativeMesh(
                ModelBuilder.build(
                        NativeMesh.convertDrawMode(drawMode),
                        NativeMesh.convertWindingOrder(winding),
                        cattrs,
                        layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32, layout.position.offset, layout.position.stride,
                        texTypes, texOffs, texStrides,
                        layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32, layout.normal.offset, layout.normal.stride,
                        layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32, layout.color.offset, layout.color.stride,
                        numMaterials,
                        matType,
                        matTexUri,
                        matColor,
                        aabb.minX, aabb.minY, aabb.minZ,
                        aabb.maxX, aabb.maxY, aabb.maxZ,
                        numVertices,
                        positions,
                        texCoords,
                        normals,
                        colors,
                        DataType.convert(indexType, false),
                        numIndices,
                        indices)
        );
    }

    private static void buildTexCoordAttrs(VertexDataLayout layout, int[] types, int[] offs, int[] strides) {
        int idx = 0;
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_0, layout.texCoord0, idx, types, offs, strides);
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_1, layout.texCoord1, idx, types, offs, strides);
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_2, layout.texCoord2, idx, types, offs, strides);
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_3, layout.texCoord3, idx, types, offs, strides);
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_4, layout.texCoord4, idx, types, offs, strides);
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_5, layout.texCoord5, idx, types, offs, strides);
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_6, layout.texCoord6, idx, types, offs, strides);
        idx = buildTexCoordAttrs(layout, Mesh.VERTEX_ATTR_TEXCOORD_7, layout.texCoord7, idx, types, offs, strides);
    }

    private static int buildTexCoordAttrs(VertexDataLayout layout, int attr, VertexDataLayout.Array array, int idx, int[] types, int[] offs, int[] strides) {
        if(MathUtils.hasBits(layout.attributes, attr)) {
            types[idx] = array.dataType != null ? DataType.convert(array.dataType, false) : DataType.TEDT_Float32;
            offs[idx] = array.offset;
            strides[idx] = array.stride;
            idx++;
        }
        return idx;
    }
}
