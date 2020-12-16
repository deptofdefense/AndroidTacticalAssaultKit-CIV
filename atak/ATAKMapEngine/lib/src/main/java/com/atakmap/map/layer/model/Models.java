package com.atakmap.map.layer.model;

import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.interop.DataType;
import com.atakmap.interop.Pointer;
import com.atakmap.interop.ProgressCallback;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.MathUtils;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;
import com.atakmap.math.Ray;
import com.atakmap.math.Vector3D;

import java.nio.Buffer;

public final class Models {
    public static interface OnTransformProgressListener {
        public void onTransformProgress(int progress);
    }

    private Models() {}

    public static PointD findAnchorPoint(Model m) {
        Envelope aabb = m.getAABB();
        for(int i = 0; i < m.getNumMeshes(); i++) {
            Mesh mesh = m.getMesh(i);
            int pointIndex = findMeshPointAtZ(mesh, aabb.minZ, 0.00001);
            if(pointIndex != -1) {
                PointD retval = new PointD(0d, 0d, 0d);
                mesh.getPosition(pointIndex, retval);
                return retval;
            }
        }
        return null;
    }

    private static int findMeshPointAtZ(Mesh m, double z, double threnshold) {
        PointD point = new PointD(0d, 0d, 0d);
        for (int i = 0; i < m.getNumVertices(); i++) {
            m.getPosition(i, point);
            if (Math.abs(point.z - z) < threnshold)
                return i;
        }
        return -1;
    }

    public static PointD findAnchorPoint(Mesh m) {
        if(m instanceof Model) {
            return findAnchorPoint((Model)m);
        } else {
            PointD retval = new PointD(0d, 0d, 0d);
            Envelope aabb = m.getAABB();
            for (int i = 0; i < m.getNumVertices(); i++) {
                m.getPosition(i, retval);
                if (retval.z == aabb.minZ)
                    return retval;
            }

            return null;
        }
    }

    /**
     * Transforms the {@link Model} described by <code>src</code> into a new {@link Model} consistent with the <I>populated</I> fields in <code>dst</code>. The following fields in <code>dst</code> are used for transformation:
     * <UL>
     *     <LI>{@link ModelInfo#srid} - if not <code>-1</code>, the target Spatial Reference</LI>
     *     <LI>{@link ModelInfo#localFrame} - if not <code>null</code>, the target local frame</LI>
     * </UL>
     * The fields of <code>dst</code> will be populated on return as follows:
     * <UL>
     *     <LI>{@link ModelInfo#srid} - if <code>-1</code>, will be set to <code>src.srid</code></LI>
     *     <LI>{@link ModelInfo#localFrame} - if <code>null</code>, will be set to <code>src.localFrame</code></LI>
     *     <LI>{@link ModelInfo#altitudeMode} - will be set to <code>src.altitudeMode</code></LI>
     *     <LI>{@link ModelInfo#location} - will be set to <code>src.location</code></LI>
     *     <LI>{@link ModelInfo#minDisplayResolution} - will be set to <code>src.minDisplayResolution</code></LI>
     *     <LI>{@link ModelInfo#maxDisplayResolution} - will be set to <code>src.maxDisplayResolution</code></LI>
     *     <LI>{@link ModelInfo#type} - will be set to <code>null</code></LI>
     *     <LI>{@link ModelInfo#uri} - will be set to <code>null</code></LI>
     *     <LI>{@link ModelInfo#name} - will be set to <code>src.name</code></LI>
     * </UL>
     * @param src       The information for the source model data
     * @param srcData   The source model data
     * @param dst       The information for the destination model data.
     * @return
     */
    public static Model transform(ModelInfo src, Model srcData, ModelInfo dst) {
        return transform(src, srcData, dst, null);
    }
    public static Model transform(ModelInfo src, Model srcData, ModelInfo dst, OnTransformProgressListener listener) {
        return transform(src, srcData, dst, srcData.getVertexDataLayout(), listener);
    }

    public static Model transform(ModelInfo src, Model srcData, ModelInfo dst, VertexDataLayout dstLayout, OnTransformProgressListener listener) {
        Mesh[] dstMeshes = new Mesh[srcData.getNumMeshes()];
        for(int i = 0; i < srcData.getNumMeshes(); i++) {
            dstMeshes[i] = transform(src, srcData.getMesh(i), dst, dstLayout, listener);
            if(dstMeshes[i] == null)
                return null;
        }
        try {
            return ModelBuilder.build(dstMeshes);
        } catch(Throwable t) {
            Log.e("Models", "Failed to transform mesh", t);
            return null;
        }
    }

    public static Mesh transform(ModelInfo src, Mesh srcData, ModelInfo dst, VertexDataLayout dstLayout, final OnTransformProgressListener listener) {
        if(dst.srid == -1)
            dst.srid = src.srid;

        double[] srcMx = null;
        if(src.localFrame != null) {
            srcMx = new double[16];
            src.localFrame.get(srcMx);
        }
        double[] dstMx = new double[16];
        if(dst.localFrame != null) {
            dst.localFrame.get(dstMx);
        }
        final Pointer retval;
        try {
            ProgressCallback callback = null;
            if(listener != null) {
                callback = new ProgressCallback() {
                    @Override
                    public void progress(int value) {
                        listener.onTransformProgress(value);
                    }
                    @Override
                    public void error(String msg) {
                        Log.e("Models", "Transform error occurred, msg=" + msg);
                    }
                };
            }
            if(!(srcData instanceof NativeMesh))
                srcData = adapt(srcData);

            if (srcData == null)
               return null;

            retval = transform(((NativeMesh) srcData).pointer, src.srid, srcMx, dst.srid, dst.localFrame != null, dstMx, callback);
        } catch(Throwable t) {
            Log.e("Models", "Failed to transform mesh", t);
            return null;
        }
        if(retval == null)
            return null;
        dst.localFrame = new Matrix(dstMx[0], dstMx[1], dstMx[2], dstMx[3], dstMx[4], dstMx[5], dstMx[6], dstMx[7], dstMx[8], dstMx[9], dstMx[10], dstMx[11], dstMx[12], dstMx[13], dstMx[14], dstMx[15]);
        dst.altitudeMode = src.altitudeMode;
        return new NativeMesh(retval);
    }

    /**
     * Transforms a model into the destination layout described by a VertexDataLayout.
     * @param src the source model.
     * @param dstLayout the destination layout
     * @return null if the transform fails to be adapted into a native model or if the
     * native model fails to be transformed.
     */
    public static Model transform(final Model src, final VertexDataLayout dstLayout) {
        Mesh[] dst = new Mesh[src.getNumMeshes()];
        for(int i = 0; i < src.getNumMeshes(); i++) {
            dst[i] = transform(src.getMesh(i), dstLayout);
        }
        return ModelBuilder.build(dst);
    }

    public static Mesh transform(final Mesh src, final VertexDataLayout dstLayout) {
        if(src instanceof NativeMesh) {
            return transformImpl((NativeMesh)src, dstLayout);
        } else {
            NativeMesh adapted = null;
            try {
                adapted = adapt(src);

                if (adapted == null)
                    return null;

                return transformImpl(adapted, dstLayout);
            } finally {
                if(adapted != null)
                    adapted.dispose();
            }
        }
    }

    private static Model transformImpl(NativeMesh src, VertexDataLayout dstLayout) {
        Pointer retval =
                transform(((NativeMesh)src).pointer,
                        VertexDataLayout.getNativeAttributes(dstLayout.attributes),
                        dstLayout.position.dataType != null ? DataType.convert(dstLayout.position.dataType, true) : DataType.TEDT_Float32,
                        dstLayout.position.offset,
                        dstLayout.position.stride,
                        dstLayout.texCoord0.dataType != null ? DataType.convert(dstLayout.texCoord0.dataType, false) : DataType.TEDT_Float32,
                        dstLayout.texCoord0.offset,
                        dstLayout.texCoord0.stride,
                        dstLayout.normal.dataType != null ? DataType.convert(dstLayout.normal.dataType, true) : DataType.TEDT_Float32,
                        dstLayout.normal.offset,
                        dstLayout.normal.stride,
                        dstLayout.color.dataType != null ? DataType.convert(dstLayout.color.dataType, false) : DataType.TEDT_Float32,
                        dstLayout.color.offset,
                        dstLayout.color.stride,
                        dstLayout.interleaved);
        if(retval == null)
            return null;
        return new MeshModel(new NativeMesh(retval));
    }

    private static boolean isScaleTranslateOnly(Matrix mx) {
        return mx.get(0, 1) == 0d &&
                mx.get(0, 2) == 0d &&
                mx.get(1, 0) == 0d &&
                mx.get(1, 2) == 0d &&
                mx.get(2, 0) == 0d &&
                mx.get(2, 1) == 0d &&
                mx.get(3, 0) == 0d &&
                mx.get(3, 1) == 0d &&
                mx.get(3, 2) == 0d &&
                mx.get(3, 3) == 1d;
    }

    public static int getNumIndices(Mesh m) {
        return m.isIndexed() ? getNumIndices(m.getDrawMode(), m.getNumFaces()) : 0;
    }

    public static int getNumIndices(Mesh.DrawMode mode, int numFaces) {
        switch(mode) {
            case Triangles:
                return numFaces*3;
            case TriangleStrip:
                return numFaces+2;
            default:
                throw new IllegalStateException();
        }
    }

    public static GeometryModel createGeometryModel(final Mesh m, final Matrix localFrame) {
        return new GeometryModel() {

            @Override
            public PointD intersect(Ray ray) {
                if (m == null)
                    return null;

                // if 'true' both the ray and the intersect point are in the
                // LCS, if 'false', both are in the WCS
                boolean isectIsLocal = false;
                try {
                    // attempt to transform the ray into the LCS, if
                    // successful, this will eliminate the need to transform
                    // the mesh triangles when searching for intersection
                    ray = inverse(ray, localFrame);
                    isectIsLocal = (localFrame != null);
                } catch (NoninvertibleTransformException ignored) {}

                // check for AABB intersection first, if no AABB intersect, then skip
                if (!aabbIntersects(m.getAABB(), isectIsLocal ? null : localFrame, ray))
                    return null;

                //long st = System.currentTimeMillis();
                PointD isect = new PointD(0d, 0d, 0d);

                int mode;
                switch(m.getDrawMode()) {
                    case Triangles:
                        mode = NativeMesh.getTEDM_Triangles();
                        break;
                    case TriangleStrip:
                        mode = NativeMesh.getTEDM_TriangleStrip();
                        break;
                    default :
                        return null;
                }
                if(m.getVertexAttributeType(Model.VERTEX_ATTR_POSITION) == null)
                    return null;
                final int verticesType = DataType.convert(m.getVertexAttributeType(Model.VERTEX_ATTR_POSITION), true);
                final int indicesType = m.isIndexed() ? DataType.convert(m.getIndexType(), false) : DataType.TEDT_UInt16;
                if(indicesType == GLES30.GL_NONE && m.isIndexed())
                    return null;

                double[] mx = null;
                if (!isectIsLocal && localFrame != null) {
                    mx = new double[16];
                    localFrame.get(mx);
                }
                isect = Models.intersect(mode,
                        m.getNumFaces(),
                        verticesType,
                        getPointer(m.getVertices(Model.VERTEX_ATTR_POSITION), m.getVertexDataLayout().position.offset),
                        m.getVertexDataLayout().position.stride,
                        indicesType,
                        getPointer(m.getIndices(), m.getIndexOffset()),
                        ray.origin.x, ray.origin.y, ray.origin.z,
                        ray.direction.X, ray.direction.Y, ray.direction.Z,
                        mx,
                        isect) ?
                        isect : null;

                if(isect != null && isectIsLocal && localFrame != null)
                    localFrame.transform(isect, isect);

                //long et = System.currentTimeMillis();
                //Log.d("Models", "Java model raycast in " + (et-st) + "ms");
                return isect;
            }
        };
    }

    private static Ray inverse(Ray ray, Matrix localFrame) throws NoninvertibleTransformException {
        if(localFrame == null)
            return ray;
        PointD org = new PointD(ray.origin.x, ray.origin.y, ray.origin.z);
        PointD tgt = new PointD(ray.origin.x+ray.direction.X, ray.origin.y+ray.direction.Y, ray.origin.z+ray.direction.Z);

        Matrix invLocalFrame = localFrame.createInverse();
        invLocalFrame.transform(org, org);
        invLocalFrame.transform(tgt, tgt);
        return new Ray(org, new Vector3D(tgt.x-org.x, tgt.y-org.y, tgt.z-org.z));
    }

    private static boolean aabbIntersects(Envelope aabb, Matrix localFrame, Ray ray) {
        if(localFrame != null) {
            ModelInfo src = new ModelInfo();
            src.srid = -1;
            src.localFrame = localFrame;
            ModelInfo dst = new ModelInfo();
            dst.srid = -1;
            dst.localFrame = null;

            Envelope transformedAabb = new Envelope(0d, 0d, 0d, 0d, 0d, 0d);
            transform(aabb, src, transformedAabb, dst);
            aabb = transformedAabb;
        }
        final double invDirX = 1d / ray.direction.X;
        final double invDirY = 1d / ray.direction.Y;
        final double invDirZ = 1d / ray.direction.Z;

        final boolean signDirX = invDirX < 0;
        final boolean signDirY = invDirY < 0;
        final boolean signDirZ = invDirZ < 0;

        //This differs from Amy et Al's algorithm, but it does save making the int[3] on creation of the
        //AABB object. If it's slower it may be worth swapping over?
        //Point2<double> bbox = signDirX ? this->maxPt : this->minPt;
        double comp;
        comp = signDirX ? aabb.maxX : aabb.minX;
        double tmin = (comp - ray.origin.x) * invDirX;
        //bbox = signDirX ? this->minPt : this->maxPt;
        comp = signDirX ? aabb.minX : aabb.maxX;
        double tmax = (comp - ray.origin.x) * invDirX;
        //bbox = signDirY ? this->maxPt : this->minPt;
        comp = signDirY ? aabb.maxY : aabb.minY;
        double tymin = (comp - ray.origin.y) * invDirY;
        //bbox = signDirY ? this->minPt : this->maxPt;
        comp = signDirY ? aabb.minY : aabb.maxY;
        double tymax = (comp - ray.origin.y) * invDirY;

        if ((tmin > tymax) || (tymin > tymax))
        {
            return false;
        }
        if (tymin > tmin)
        {
            tmin = tymin;
        }
        if (tymax < tmax)
        {
            tmax = tymax;
        }

        //bbox = signDirZ ? this->maxPt : this->minPt;
        comp = signDirZ ? aabb.maxZ : aabb.minZ;
        double tzmin = (comp - ray.origin.z) * invDirZ;
        //bbox = signDirZ ? this->minPt : this->maxPt;
        comp = signDirZ ? aabb.minZ : aabb.maxZ;
        double tzmax = (comp - ray.origin.z) * invDirZ;
        if ((tmin > tzmax) || (tzmin > tmax))
        {
            return false;
        }
        if (tzmin > tmin)
        {
            tmin = tzmin;
        }
        if (tzmax < tmax)
        {
            tmax = tzmax;
        }

        //The Amy et al and link above check an intersection interval for validity before returning true
        //Our intersect doesn't take those in, possibly should?

        return true;
    }

    public static void transform(Envelope srcAABB, ModelInfo srcInfo, Envelope dstAABB, ModelInfo dstInfo) {
        PointD[] pts = new PointD[8];
        pts[0] = new PointD(srcAABB.minX, srcAABB.minY, srcAABB.minZ);
        pts[1] = new PointD(srcAABB.maxX, srcAABB.minY, srcAABB.minZ);
        pts[2] = new PointD(srcAABB.maxX, srcAABB.maxY, srcAABB.minZ);
        pts[3] = new PointD(srcAABB.minX, srcAABB.maxY, srcAABB.minZ);
        pts[4] = new PointD(srcAABB.minX, srcAABB.minY, srcAABB.maxZ);
        pts[5] = new PointD(srcAABB.maxX, srcAABB.minY, srcAABB.maxZ);
        pts[6] = new PointD(srcAABB.maxX, srcAABB.maxY, srcAABB.maxZ);
        pts[7] = new PointD(srcAABB.minX, srcAABB.maxY, srcAABB.maxZ);
        if(srcInfo.localFrame != null) {
            for (int i = 0; i < pts.length; i++)
                srcInfo.localFrame.transform(pts[i], pts[i]);
        }
        if(srcInfo.srid != dstInfo.srid) {
            Projection srcProj = ProjectionFactory.getProjection(srcInfo.srid);
            if(srcProj == null)
                throw new IllegalArgumentException();
            Projection dstProj = ProjectionFactory.getProjection(dstInfo.srid);
            if(dstProj == null)
                throw new IllegalArgumentException();
            GeoPoint geo = GeoPoint.createMutable();
            for(int i = 0; i < pts.length; i++) {
                srcProj.inverse(pts[i], geo);
                dstProj.forward(geo, pts[i]);
            }
        }
        if(dstInfo.localFrame != null) {
            Matrix dstLocalFrameInv;
            try {
                dstLocalFrameInv = dstInfo.localFrame.createInverse();
            } catch(NoninvertibleTransformException e) {
                throw new IllegalArgumentException(e);
            }

            for(int i = 0; i < pts.length; i++)
                dstLocalFrameInv.transform(pts[i], pts[i]);
        }

        dstAABB.minX = pts[0].x;
        dstAABB.minY = pts[0].y;
        dstAABB.minZ = pts[0].z;
        dstAABB.maxX = pts[0].x;
        dstAABB.maxY = pts[0].y;
        dstAABB.maxZ = pts[0].z;

        for(int i = 1; i < pts.length; i++) {
            if(pts[i].x < dstAABB.minX)
                dstAABB.minX = pts[i].x;
            else if(pts[i].x > dstAABB.maxX)
                dstAABB.maxX = pts[i].x;
            if(pts[i].y < dstAABB.minY)
                dstAABB.minY = pts[i].y;
            else if(pts[i].y > dstAABB.maxY)
                dstAABB.maxY = pts[i].y;
            if(pts[i].z < dstAABB.minZ)
                dstAABB.minZ = pts[i].z;
            else if(pts[i].z > dstAABB.maxZ)
                dstAABB.maxZ = pts[i].z;
        }
    }

    public static String getTextureUri(Mesh mesh) {
        final Material diffuse = mesh.getMaterial(Material.PropertyType.Diffuse);
        if(diffuse == null)
            return null;
        return diffuse.getTextureUri();
    }

    public static Material getMaterial(Mesh m, Material.PropertyType t) {
        for(int i = 0; i < m.getNumMaterials(); i++) {
            Material mat = m.getMaterial(i);
            if(mat.getPropertyType() == t)
                return mat;
        }
        return null;
    }

    private static long getPointer(Buffer b, int off) {
        if(b == null)
            return 0L;
        long retval = Unsafe.getBufferPointer(b);
        if(retval != 0L)
            retval += off;
        return retval;
    }
    private static native boolean intersect(int mode,
                                            int faceCount,
                                            int verticesType,
                                            long verticesPtr,
                                            int stride,
                                            int indicesType,
                                            long indicesPtr,
                                            double rox, double roy, double roz,
                                            double rdx, double rdy, double rdz,
                                            double[] localFrame,
                                            PointD result);

    static native Pointer transform(Pointer srcModel,
                                    int dstAttrs,
                                    int dstposType, int dstposOff, int dstposStride,
                                    int dsttexCoordType, int dsttexCoordOff, int dsttexCoordStride,
                                    int dstnormalType, int dstnormalOff, int dstnormalStride,
                                    int dstcolorType, int dstcolorOff, int dstcolorStride,
                                    boolean dstinterleaved);
    static native Pointer transform(Pointer srcModel, int srcSrid, double[] srcMx, int dstSrid, boolean dstMxDefined, double[] dstMx, ProgressCallback callback);


    static NativeMesh adapt(Mesh managed) {
        final VertexDataLayout layout = managed.getVertexDataLayout();
        final Envelope aabb = managed.getAABB();
        String textureUri = null;
        Material diffuse = managed.getMaterial(Material.PropertyType.Diffuse);
        if(diffuse != null)
            textureUri = diffuse.getTextureUri();
        Pointer retval =
                adapt(managed,
                        textureUri,
                        managed.getNumVertices(),
                        managed.getNumFaces(),
                        managed.isIndexed(),
                        managed.isIndexed() ? DataType.convert(managed.getIndexType(), false) : DataType.TEDT_UInt16,
                        Models.getNumIndices(managed),
                        managed.isIndexed() ? Unsafe.getBufferPointer(managed.getIndices()) : 0L,
                        managed.getIndexOffset(),
                        MathUtils.hasBits(layout.attributes, Model.VERTEX_ATTR_POSITION) ? Unsafe.getBufferPointer(managed.getVertices(Model.VERTEX_ATTR_POSITION)) : 0L,
                        MathUtils.hasBits(layout.attributes, Model.VERTEX_ATTR_TEXCOORD_0) ? Unsafe.getBufferPointer(managed.getVertices(Model.VERTEX_ATTR_TEXCOORD_0)) : 0L,
                        MathUtils.hasBits(layout.attributes, Model.VERTEX_ATTR_NORMAL) ? Unsafe.getBufferPointer(managed.getVertices(Model.VERTEX_ATTR_NORMAL)) : 0L,
                        MathUtils.hasBits(layout.attributes, Model.VERTEX_ATTR_COLOR) ? Unsafe.getBufferPointer(managed.getVertices(Model.VERTEX_ATTR_COLOR)) : 0L,
                        NativeMesh.convertWindingOrder(managed.getFaceWindingOrder()),
                        NativeMesh.convertDrawMode(managed.getDrawMode()),
                        aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ,
                        VertexDataLayout.getNativeAttributes(layout.attributes),
                        layout.position.dataType != null ? DataType.convert(layout.position.dataType, true) : DataType.TEDT_Float32,
                        layout.position.offset,
                        layout.position.stride,
                        layout.texCoord0.dataType != null ? DataType.convert(layout.texCoord0.dataType, false) : DataType.TEDT_Float32,
                        layout.texCoord0.offset,
                        layout.texCoord0.stride,
                        layout.normal.dataType != null ? DataType.convert(layout.normal.dataType, true) : DataType.TEDT_Float32,
                        layout.normal.offset,
                        layout.normal.stride,
                        layout.color.dataType != null ? DataType.convert(layout.color.dataType, false) : DataType.TEDT_Float32,
                        layout.color.offset,
                        layout.color.stride,
                        layout.interleaved);
        if(retval == null)
            return null;
        return new NativeMesh(retval);
    }
    static native Pointer adapt(Mesh managed,
                                String textureUri,
                                int numVertices,
                                int numFaces,
                                boolean isIndexed,
                                int indexType,
                                int numIndices,
                                long indices,
                                int indexOffset,
                                // vertices
                                long positionsVertices,
                                long texCoordVertices,
                                long normalVertices,
                                long colorVertices,
                                //===
                                int windingOrder,
                                int drawMode,
                                // AABB
                                double aabbMinX, double aabbMinY, double aabbMinZ,
                                double aabbMaxX, double aabbMaxY, double aabbMaxZ,
                                // VertexDataLayout
                                int attrs,
                                int posType, int posOff, int posStride,
                                int texCoordType, int texCoordOff, int texCoordStride,
                                int normalType, int normalOff, int normalStride,
                                int colorType, int colorOff, int colorStride,
                                boolean interleaved);
}
