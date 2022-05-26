package com.atakmap.map.layer.model;

import com.atakmap.interop.DataType;
import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.util.ReadWriteLock;

import java.nio.Buffer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class NativeMesh implements Mesh {
    final NativePeerManager.Cleaner CLEANER = new InteropCleaner(Mesh.class);

    private final ReadWriteLock rwlock;
    private final Cleaner cleaner;
    Pointer pointer;

    final int numVertices;
    final int numFaces;
    final boolean isIndexed;
    final Class<?> indexType;
    final int indexOffset;
    final WindingOrder windingOrder;
    final DrawMode drawMode;
    final Envelope aabb;
    final VertexDataLayout layout;
    final Material[] materials;

    NativeMesh(Pointer pointer) {
        this.rwlock = new ReadWriteLock();
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.numVertices = getNumVertices(this.pointer);
        this.numFaces = getNumFaces(this.pointer);
        this.isIndexed = isIndexed(this.pointer);
        this.indexType = this.isIndexed ? DataType.convert(getIndexType(this.pointer)) : null;
        this.indexOffset = this.isIndexed ? getIndexOffset(this.pointer) : 0;
        this.windingOrder = convertWindingOrder(getFaceWindingOrder(this.pointer));
        this.drawMode = convertDrawMode(getDrawMode(this.pointer));
        double[] aabb = new double[6];
        getAABB(this.pointer, aabb);
        this.aabb = new Envelope(aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]);

        this.layout = new VertexDataLayout();
        VertexDataLayout.fromNative(getVertexDataLayout(this.pointer), this.layout);

        final int numMaterials = getNumMaterials(this.pointer.raw);
        this.materials = new Material[numMaterials];
        for(int i = 0; i < numMaterials; i++) {
            this.materials[i] = new Material(getMaterialTextureUri(this.pointer.raw, i),
                                             Material.PropertyType.Diffuse,
                                             getMaterialColor(this.pointer.raw, i),
                                             getMaterialTextureIndex(this.pointer.raw, i));
        }
    }

    @Override
    public int getNumMaterials() {
        return this.materials.length;
    }

    @Override
    public Material getMaterial(int idx) {
        return this.materials[idx];
    }

    @Override
    public Material getMaterial(Material.PropertyType type) {
        for(int i = 0; i < this.materials.length; i++)
            if(materials[i].getPropertyType() == type)
                return materials[i];
        return null;
    }

    @Override
    public int getNumVertices() {
        return this.numVertices;
    }

    @Override
    public int getNumFaces() {
        return this.numFaces;
    }

    @Override
    public boolean isIndexed() {
        return this.isIndexed;
    }

    @Override
    public void getPosition(int i, PointD xyz) {
        getPosition(this.pointer, i, xyz);
    }

    @Override
    public void getTextureCoordinate(int texCoord, int i, PointD uv) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.value == 0L)
                throw new IllegalStateException();
            getTextureCoordinate(this.pointer, getNativeAttributes(texCoord), i, uv);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void getNormal(int i, PointD xyz) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.value == 0L)
                throw new IllegalStateException();
            getNormal(this.pointer, i, xyz);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getColor(int i) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.value == 0L)
                throw new IllegalStateException();
            return getColor(this.pointer, i);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Class<?> getVertexAttributeType(int attr) {
        switch(attr) {
            case VERTEX_ATTR_COLOR :
                return this.layout.color.dataType;
            case VERTEX_ATTR_NORMAL :
                return this.layout.normal.dataType;
            case VERTEX_ATTR_POSITION :
                return this.layout.position.dataType;
            case VERTEX_ATTR_TEXCOORD_0 :
                return this.layout.texCoord0.dataType;
            case VERTEX_ATTR_TEXCOORD_1 :
                return this.layout.texCoord1.dataType;
            case VERTEX_ATTR_TEXCOORD_2 :
                return this.layout.texCoord2.dataType;
            case VERTEX_ATTR_TEXCOORD_3 :
                return this.layout.texCoord3.dataType;
            case VERTEX_ATTR_TEXCOORD_4 :
                return this.layout.texCoord4.dataType;
            case VERTEX_ATTR_TEXCOORD_5 :
                return this.layout.texCoord5.dataType;
            case VERTEX_ATTR_TEXCOORD_6 :
                return this.layout.texCoord6.dataType;
            case VERTEX_ATTR_TEXCOORD_7 :
                return this.layout.texCoord7.dataType;
            default :
                return null;
        }
    }

    @Override
    public Class<?> getIndexType() {
        return this.indexType;
    }

    @Override
    public int getIndex(int i) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.value == 0L)
                throw new IllegalStateException();
            return getIndex(this.pointer, i);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public Buffer getIndices() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.value == 0L)
                throw new IllegalStateException();
            return getIndices(this.pointer, null);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public int getIndexOffset() {
        return this.indexOffset;
    }

    @Override
    public Buffer getVertices(int mattr) {
        final int cattr = getNativeAttributes(mattr);
        this.rwlock.acquireRead();
        try {
            if(this.pointer.value == 0L)
                throw new IllegalStateException();
            return getVertices(this.pointer, cattr);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public WindingOrder getFaceWindingOrder() {
        return this.windingOrder;
    }

    @Override
    public DrawMode getDrawMode() {
        return this.drawMode;
    }

    @Override
    public Envelope getAABB() {
        return this.aabb;
    }

    @Override
    public VertexDataLayout getVertexDataLayout() {
        return this.layout;
    }

    @Override
    public void dispose() {
        if(cleaner != null)
            cleaner.clean();
    }

    static Mesh create(Pointer pointer, Object owner) {
        return new NativeMesh(pointer);
    }

    static long getPointer(Mesh object) {
        if(object instanceof NativeMesh)
            return ((NativeMesh)object).pointer.raw;
        else
            return 0L;
    }
    //static Pointer wrap(T object);
    static boolean hasPointer(Mesh object) {
        return (object instanceof NativeMesh);
    }
    //static T create(Pointer pointer, Object ownerReference);
    //static boolean hasObject(long pointer);
    //static T getObject(long pointer);
    //static Pointer clone(long otherRawPointer);

    static int getNativeAttributes(int mattr) {
        int cattr = 0;
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_COLOR))
            cattr |= getTEVA_Color();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_NORMAL))
            cattr |= getTEVA_Normal();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_POSITION))
            cattr |= getTEVA_Position();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_0))
            cattr |= getTEVA_TexCoord0();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_1))
            cattr |= getTEVA_TexCoord1();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_2))
            cattr |= getTEVA_TexCoord2();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_3))
            cattr |= getTEVA_TexCoord3();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_4))
            cattr |= getTEVA_TexCoord4();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_5))
            cattr |= getTEVA_TexCoord5();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_6))
            cattr |= getTEVA_TexCoord6();
        if(MathUtils.hasBits(mattr, VERTEX_ATTR_TEXCOORD_7))
            cattr |= getTEVA_TexCoord7();
        return cattr;
    }
    static native int getNumMaterials(long pointer);
    static native String getMaterialTextureUri(long pointer, int index);
    static native int getMaterialColor(long pointer, int index);
    static native int getMaterialTextureIndex(long pointer, int index);

    static native int getNumVertices(Pointer pointer);
    static native int getNumFaces(Pointer pointer);
    static native boolean isIndexed(Pointer pointer);
    static native void getPosition(Pointer pointer, int i, PointD xyz);
    static native void getTextureCoordinate(Pointer pointer, int attr, int i, PointD uv);
    static native void getNormal(Pointer pointer, int i, PointD xyz);
    static native int getColor(Pointer pointer, int i);
    static native long getVertexDataLayout(Pointer pointer);
    static native int getIndexType(Pointer pointer);
    static native int getIndex(Pointer pointer, int i);
    static native Buffer getIndices(Pointer pointer, Object ref);
    static native int getIndexOffset(Pointer pointer);
    static native Buffer getVertices(Pointer pointer, int attr);
    static native int getFaceWindingOrder(Pointer pointer);
    static native int getDrawMode(Pointer pointer);
    static native void getAABB(Pointer pointer, double[] aabb);
    static native void destruct(Pointer pointer);

    static native int getTEWO_Clockwise();
    static native int getTEWO_CounterClockwise();
    static native int getTEWO_Undefined();

    static native int getTEDM_Points();
    static native int getTEDM_Triangles();
    static native int getTEDM_TriangleStrip();
    static native int getTEDM_Lines();
    static native int getTEDM_LineStrip();

    static native int getTEVA_Position();
    static native int getTEVA_TexCoord0();
    static native int getTEVA_TexCoord1();
    static native int getTEVA_TexCoord2();
    static native int getTEVA_TexCoord3();
    static native int getTEVA_TexCoord4();
    static native int getTEVA_TexCoord5();
    static native int getTEVA_TexCoord6();
    static native int getTEVA_TexCoord7();
    static native int getTEVA_Normal();
    static native int getTEVA_Color();

    static int convertWindingOrder(WindingOrder jwo) {
        switch(jwo) {
            case Clockwise:
                return getTEWO_Clockwise();
            case CounterClockwise:
                return getTEWO_CounterClockwise();
            case Undefined:
                return getTEWO_Undefined();
            default :
                throw new IllegalArgumentException();
        }
    }
    static WindingOrder convertWindingOrder(int tewo) {
        if(tewo == getTEWO_Clockwise())
            return WindingOrder.Clockwise;
        else if(tewo == getTEWO_CounterClockwise())
            return WindingOrder.CounterClockwise;
        else if(tewo == getTEWO_Undefined())
            return WindingOrder.Undefined;
        else
            throw new IllegalArgumentException();
    }
    static int convertDrawMode(DrawMode jdm) {
        switch(jdm) {
            case Points:
                return getTEDM_Points();
            case Triangles:
                return getTEDM_Triangles();
            case TriangleStrip:
                return getTEDM_TriangleStrip();
            case Lines :
                return getTEDM_Lines();
            case LineStrip:
                return getTEDM_LineStrip();
            default :
                throw new IllegalArgumentException();
        }
    }
    static DrawMode convertDrawMode(int tedm) {
        if(tedm == getTEDM_Points())
            return DrawMode.Points;
        else if(tedm == getTEDM_Triangles())
            return DrawMode.Triangles;
        else if(tedm == getTEDM_TriangleStrip())
            return DrawMode.TriangleStrip;
        else if(tedm == getTEDM_Lines())
            return DrawMode.Lines;
        else if(tedm == getTEDM_LineStrip())
            return DrawMode.LineStrip;
        else
            throw new IllegalArgumentException();
    }
}
