package com.atakmap.map.layer.model;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @deprecated use {@link MeshBuilder}
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class ModelBuilder {
    private static final String TAG = "ModelBuilder";
    private MeshBuilder mesh;
    private List<MeshReference> meshes = new LinkedList<>();
    private Map<Integer, MeshReference> instancedMeshData = new LinkedHashMap<>();

    private int instanceId;

    public ModelBuilder() { }

    /**
     * @deprecated use {@link MeshBuilder#setWindingOrder(Model.WindingOrder)} instead
     * @param windingOrder
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void setWindingOrder(Model.WindingOrder windingOrder) {
        mesh.setWindingOrder(windingOrder);
    }

    /**
     * @deprecated use {@link MeshBuilder#addMaterial(Material)} instead
     * @param textureUri
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void setTextureUri(String textureUri) {
        this.mesh.addMaterial(new Material(textureUri, Material.PropertyType.Diffuse, -1));
    }

    /**
     * @deprecated use {@link MeshBuilder#addVertex(double, double, double, float, float, float,
     *     float, float, float, float, float, float)} instead
     * @param posx
     * @param posy
     * @param posz
     * @param texu
     * @param texv
     * @param nx
     * @param ny
     * @param nz
     * @param r
     * @param g
     * @param b
     * @param a
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void addVertex(double posx, double posy, double posz,
                          float texu, float texv,
                          float nx, float ny, float nz,
                          float r, float g, float b, float a) {

        this.mesh.addVertex(posx, posy, posz,
                texu, texv,
                nx, ny, nz,
                r, g, b, a);
    }

    /**
     * @deprecated use {@link MeshBuilder#addFace(int, int, int)} instead
     * @param a
     * @param b
     * @param c
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void addFace(int a, int b, int c) {
        this.mesh.addFace(a, b, c);
    }

    /**
     * @deprecated use {@link MeshBuilder#addIndex(int)} instead
     * @param index
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void addIndex(int index) {
        this.mesh.addIndex(index);
    }

    /**
     * @deprecated use {@link MeshBuilder#addIndices(int[], int, int)} instead
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public void addIndices(int[] indices, int off, int count) {
        this.mesh.addIndices(indices, off, count);
    }

    /**
     * @deprecated use {@link MeshBuilder#addIndices(short[], int, int)} instead
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public void addIndices(short[] indices, int off, int count) {
        this.mesh.addIndices(indices, off, count);
    }

    /**
     * @deprecated use {@link MeshBuilder#addIndices(IntBuffer)} instead
     */
    @Deprecated
    @DeprecatedApi(since ="4.1", forRemoval = true, removeAt="4.4")
    public void addIndices(IntBuffer indices) {
        this.mesh.addIndices(indices);
    }

    /**
     * @deprecated use {@link MeshBuilder#addIndices(ShortBuffer)} instead
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public void addIndices(ShortBuffer indices) {
        // XXX -
        while(indices.hasRemaining())
            this.addIndex(indices.get()&0xFFFF);
    }

    public void addMesh(MeshBuilder meshBuilder) {
        addMesh(meshBuilder, Model.INSTANCE_ID_NONE, null);
    }

    /**
     * @deprecated use {@link #addMesh(MeshBuilder)} instead
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public MeshBuilder beginMesh(int vertexAttr, boolean indexed, Model.DrawMode drawMode) {
        addMesh(new MeshBuilder(vertexAttr, indexed, drawMode));
        return this.mesh;
    }

    /**
     * @deprecated use {@link #addMesh(MeshBuilder)} instead
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public MeshBuilder addMesh(int vertexAttrs, Class<?> indexType, Mesh.DrawMode drawMode) {
        addMesh(new MeshBuilder(vertexAttrs, indexType, drawMode));
        return this.mesh;
    }

    public void addMesh(Mesh mesh) {
        addMesh(mesh, Model.INSTANCE_ID_NONE, null);
    }

    public void addMesh(MeshBuilder mesh, int instanceId, Matrix transform) {
        addMeshImpl(new BuilderMeshReference(mesh, instanceId, transform), mesh);
    }

    public void addMesh(Mesh mesh, int instanceId, Matrix transform) {
        addMeshImpl(new ReferenceMeshReference(mesh, instanceId, transform), null);
    }

    public void addMesh(int instanceId, Matrix transform) {
        if(instanceId == Model.INSTANCE_ID_NONE)
            throw new IllegalArgumentException();
        addMeshImpl(new InstancedMeshReference(instanceId, transform, instancedMeshData), null);
    }

    private void addMeshImpl(MeshReference ref, MeshBuilder builder) {
        this.mesh = builder;

        // if the mesh is an instanced mesh and has data, it is the mesh data
        // for the instance ID
        if (ref.instanceId != Model.INSTANCE_ID_NONE && ref.hasData()) {
            // if we've already recorded the mesh data for the instance ID,
            // raise Illegal Argument
            if (instancedMeshData.containsKey(ref.instanceId))
                throw new IllegalArgumentException();
            instancedMeshData.put(ref.instanceId, ref);
            ref = new InstancedMeshReference(ref.instanceId, ref.transform, instancedMeshData);
        }
        meshes.add(ref);
    }

    public int getNumMeshes() {
        return this.meshes.size();
    }

    public int reserveInstanceId() {
        return instanceId++;
    }

    public Model build() {
        if (meshes.isEmpty()) {
            throw new IllegalStateException("must contain at least 1 mesh");
        }

        // pre-resolve all instanced meshes
        for(Map.Entry<Integer, MeshReference> entry : instancedMeshData.entrySet())
            entry.setValue(entry.getValue().resolve());

        MeshReference[] meshes = new MeshReference[this.meshes.size()];
        Envelope aabb = new Envelope(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
        int idx = 0;
        for (MeshReference ref : this.meshes) {
            meshes[idx] = ref.resolve();
            if(meshes[idx] == null) {
                Log.w(TAG, "Unexpected null Mesh reference");
                continue;
            }

            // XXX - skipping instanced geometry in bounds computation
            if(ref.instanceId != Model.INSTANCE_ID_NONE) {
                idx++;
                continue;
            }

            Envelope b = meshes[idx].get().getAABB();
            idx++;
            // transform the AABB if necessary
            if(ref.transform != null) {
                ModelInfo srcInfo = new ModelInfo();
                srcInfo.localFrame = ref.transform;
                ModelInfo dstInfo = new ModelInfo();
                dstInfo.localFrame =null;

                Models.transform(b, srcInfo, b, dstInfo);
            }

            if (b.minX < aabb.minX)
                aabb.minX = b.minX;
            if (b.maxX > aabb.maxX)
                aabb.maxX = b.maxX;
            if (b.minY < aabb.minY)
                aabb.minY = b.minY;
            if (b.maxY > aabb.maxY)
                aabb.maxY = b.maxY;
            if (b.minZ < aabb.minZ)
                aabb.minZ = b.minZ;
            if (b.maxZ > aabb.maxZ)
                aabb.maxZ = b.maxZ;
        }
        return new ModelImpl(meshes, idx, aabb);
    }
    public static Model build(Mesh[] meshes) {
        return build(meshes, null);
    }

    public static Model build(Mesh[] meshes, Matrix[] transforms) {
        if(meshes.length == 1)
            return build(meshes[0]);

        MeshReference[] refs = new MeshReference[meshes.length];
        Envelope dstAabb = new Envelope(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
        for(int i = 0; i < meshes.length; i++) {
            Matrix transform = transforms == null ? null : transforms[i];
            refs[i] = new ReferenceMeshReference(meshes[i], Model.INSTANCE_ID_NONE, transform);
            Envelope meshAabb = meshes[i].getAABB();
            if (transform != null) {
                PointD p = transform.transform(new PointD(meshAabb.minX, meshAabb.minY, meshAabb.minZ), null);
                meshAabb.minX = p.x;
                meshAabb.minY = p.y;
                meshAabb.minZ = p.z;
                p = transform.transform(new PointD(meshAabb.maxX, meshAabb.maxY, meshAabb.maxZ), null);
                meshAabb.maxX = p.x;
                meshAabb.maxY = p.y;
                meshAabb.maxZ = p.z;
            }
            if(meshAabb.minX < dstAabb.minX)    dstAabb.minX = meshAabb.minX;
            if(meshAabb.minY < dstAabb.minY)    dstAabb.minY = meshAabb.minY;
            if(meshAabb.minZ < dstAabb.minZ)    dstAabb.minZ = meshAabb.minZ;
            if(meshAabb.maxX > dstAabb.maxX)    dstAabb.maxX = meshAabb.maxX;
            if(meshAabb.maxY > dstAabb.maxY)    dstAabb.maxY = meshAabb.maxY;
            if(meshAabb.maxZ > dstAabb.maxZ)    dstAabb.maxZ = meshAabb.maxZ;
        }
        return new ModelBuilder.ModelImpl(refs, refs.length, dstAabb);
    }

    public static Model build(Mesh mesh) {
        return new MeshModel(mesh);
    }

    /*************************************************************************/

    final static class ModelImpl implements Model {
        Envelope aabb;
        MeshReference[] meshes;
        int meshCount;

        ModelImpl(MeshReference[] meshes, int count, Envelope aabb) {
            this.meshes = meshes;
            this.meshCount = count;
            this.aabb = aabb;
        }

        @Override
        public int getNumMaterials() {
            return meshes[0].get().getNumMaterials();
        }

        @Override
        public Material getMaterial(int index) {
            return meshes[0].get().getMaterial(index);
        }

        @Override
        public Material getMaterial(Material.PropertyType propertyType) {
            return meshes[0].get().getMaterial(propertyType);
        }

        @Override
        public int getNumVertices() {
            return this.meshes[0].get().getNumVertices();
        }

        @Override
        public int getNumFaces() {
            return this.meshes[0].get().getNumFaces();
        }

        @Override
        public boolean isIndexed() {
            return this.meshes[0].get().isIndexed();
        }

        @Override
        public int getIndexOffset() {
            return this.meshes[0].get().getIndexOffset();
        }

        @Override
        public void getPosition(int i, PointD xyz) {
            this.meshes[0].get().getPosition(i, xyz);
        }

        @Override
        public void getTextureCoordinate(int texCoordNum, int i, PointD uv) {
            this.meshes[0].get().getTextureCoordinate(texCoordNum, i, uv);
        }

        @Override
        public void getNormal(int i, PointD xyz) {
            this.meshes[0].get().getNormal(i, xyz);
        }

        @Override
        public int getColor(int i) {
            return this.meshes[0].get().getColor(i);
        }

        @Override
        public Class<?> getVertexAttributeType(int flag) {
            return this.meshes[0].get().getVertexAttributeType(flag);
        }

        @Override
        public Class<?> getIndexType() {
            return this.meshes[0].get().getIndexType();
        }

        @Override
        public int getIndex(int i) {
            return this.meshes[0].get().getIndex(i);
        }

        @Override
        public Buffer getIndices() {
            return this.meshes[0].get().getIndices();
        }

        @Override
        public Buffer getVertices(int flag) {
            return this.meshes[0].get().getVertices(flag);
        }

        @Override
        public WindingOrder getFaceWindingOrder() {
            return this.meshes[0].get().getFaceWindingOrder();
        }

        @Override
        public DrawMode getDrawMode() {
            return this.meshes[0].get().getDrawMode();
        }

        @Override
        public Envelope getAABB() {
            return this.aabb;
        }

        @Override
        public void dispose() {
            for (MeshReference mesh : meshes) {
                mesh.dispose();
            }
        }

        @Override
        public VertexDataLayout getVertexDataLayout() {
            return this.meshes[0].get().getVertexDataLayout();
        }

        @Override
        public int getNumMeshes() {
            return meshCount;
        }

        @Override
        public Mesh getMesh(int i) {
            return this.getMesh(i, true);
        }

        @Override
        public Mesh getMesh(int i, boolean postTransform) {
            Mesh retval = this.meshes[i].get();
            if(postTransform && this.meshes[i].transform != null) {
                ModelInfo srcInfo = new ModelInfo();
                srcInfo.localFrame = this.meshes[i].transform;
                ModelInfo dstInfo = new ModelInfo();
                dstInfo.localFrame = null;
                retval = Models.transform(srcInfo, retval, dstInfo, retval.getVertexDataLayout(), null);
            }
            return retval;
        }

        @Override
        public Matrix getTransform(int meshId) {
            return this.meshes[meshId].transform;
        }

        @Override
        public SceneNode getRootSceneNode() {
            return null;
        }

        @Override
        public int getInstanceId(int meshId) {
            return this.meshes[meshId].instanceId;
        }
    }

    static abstract class MeshReference {
        public final int instanceId;
        public final Matrix transform;

        MeshReference(int instanceId, Matrix transform) {
            this.instanceId = instanceId;
            this.transform = transform;
        }
        public abstract Mesh get ();

        public MeshReference resolve() {
            if(isResolved())
                return this;
            final Mesh mesh = this.get();
            if(mesh == null)
                throw new IllegalStateException();
            return new ReferenceMeshReference(mesh, instanceId, transform);
        }

        public abstract boolean hasData();
        public abstract boolean isResolved();
        public abstract void dispose();
    }

    static final class BuilderMeshReference extends MeshReference {
        final MeshBuilder builder;

        BuilderMeshReference(MeshBuilder builder, int instanceId, Matrix transform) {
            super(instanceId, transform);
            this.builder = builder;
        }

        public Mesh get() {
            return builder.build();
        }

        public boolean hasData() {
            return true;
        }

        public boolean isResolved() {
            return false;
        }
        public void dispose() {
            this.builder.dispose();
        }
    }

    static final class InstancedMeshReference extends MeshReference {
        Map<Integer, MeshReference> instancedMeshRefs;

        InstancedMeshReference(int instanceId, Matrix transform, Map<Integer, MeshReference> instancedMeshRefs) {
            super(instanceId, transform);

            if(instanceId == Model.INSTANCE_ID_NONE)
                throw new IllegalArgumentException();

            this.instancedMeshRefs = instancedMeshRefs;
        }

        public Mesh get() {
            MeshReference instanceRef = instancedMeshRefs.get(instanceId);
            if(instanceRef == null)
                throw new IllegalStateException();
            return instanceRef.get();
        }

        public boolean hasData() {
            return false;
        }

        public boolean isResolved() {
            return false;
        }

        public void dispose() {}
    }

    static final class ReferenceMeshReference extends MeshReference {
        final Mesh instance;

        ReferenceMeshReference(Mesh instance, int instanceId, Matrix transform) {
            super(instanceId, transform);
            this.instance = instance;
        }

        public Mesh get() {
            return this.instance;
        }

        public boolean hasData() {
            return true;
        }

        public boolean isResolved() {
            return true;
        }

        public void dispose() {
            this.instance.dispose();
        }
    }

    /*************************************************************************/

    static native Pointer create(int drawMode, int attrs);
    static native Pointer create(int drawMode, int attrs, int indexType);
    static native Pointer create(int drawMode,
                              int attrs,
                              int posType, int posOff, int posStride,
                              int texCoordType, int texCoordOff, int texCoordStride,
                              int normalType, int normalOff, int normalStride,
                              int colorType, int colorOff, int colorStride,
                              boolean interleaved);
    static native Pointer create(int drawMode,
                              int attrs,
                              int posType, int posOff, int posStride,
                              int texCoordType, int texCoordOff, int texCoordStride,
                              int normalType, int normalOff, int normalStride,
                              int colorType, int colorOff, int colorStride,
                              boolean interleaved,
                              int indexType);
    static native void reserveVertices(long pointer, int count);
    static native void reserveIndices(long pointer, int count);
    static native void setWindingOrder(long pointer, int windingOrder);
    static native void addMaterial(long pointer, String textureUri, int color);
    static native void addVertex(long pointer,
                                 double posx, double posy, double posz,
                                 float texu, float texv,
                                 float nx, float ny, float nz,
                                 float r, float g, float b, float a);
    static native void addVertex(long pointer,
                                 double posx, double posy, double posz,
                                 float[] texuv,
                                 float nx, float ny, float nz,
                                 float r, float g, float b, float a);
    static native void addIndex(long pointer, int index);
    static native Pointer build(long pointer);
    static native void destruct(Pointer pointer);

    static native Pointer build(int tedm, int tewo,
                                int attrs,
                                int posType, int posOff, int posStride,
                                int[] texCoordType, int[] texCoordOff, int[] texCoordStride,
                                int normalType, int normalOff, int normalStride,
                                int colorType, int colorOff, int colorStride,
                                int numMats, int[] matType, String[] texUri, int[] matColor,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int numVertices, Buffer verticesPtr);
    static native Pointer build(int tedm, int tewo,
                                int attrs,
                                int posType, int posOff, int posStride,
                                int[] texCoordType, int[] texCoordOff, int[] texCoordStride,
                                int normalType, int normalOff, int normalStride,
                                int colorType, int colorOff, int colorStride,
                                int numMats, int[] matType, String[] texUri, int[] matColor,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int numVertices, Buffer verticesPtr, int indicesType, int numIndices, Buffer indicesPtr);
    static native Pointer build(int tedm, int tewo,
                                int attrs,
                                int posType, int posOff, int posStride,
                                int[] texCoordType, int[] texCoordOff, int[] texCoordStride,
                                int normalType, int normalOff, int normalStride,
                                int colorType, int colorOff, int colorStride,
                                int numMats, int[] matType, String[] texUri, int[] matColor,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int numVertices,
                                Buffer positionsPtr,
                                Buffer[] texCoordsPtr,
                                Buffer normalsPtr,
                                Buffer colorsPtr);
    static native Pointer build(int tedm, int tewo,
                                int attrs,
                                int posType, int posOff, int posStride,
                                int[] texCoordType, int[] texCoordOff, int[] texCoordStride,
                                int normalType, int normalOff, int normalStride,
                                int colorType, int colorOff, int colorStride,
                                int numMats, int[] matType, String[] texUri, int[] matColor,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int numVertices,
                                Buffer positionsPtr,
                                Buffer[] texCoordsPtr,
                                Buffer normalsPtr,
                                Buffer colorsPtr,
                                int indicesType, int numIndices, Buffer indicesPtr);
}

