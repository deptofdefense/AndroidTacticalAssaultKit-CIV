package com.atakmap.map.layer.model.assimp;

import android.graphics.Color;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Material;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.MeshBuilder;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelSpi;
import com.atakmap.map.layer.model.obj.ObjUtils;
import com.atakmap.map.layer.model.pointcloud.PlyModelInfoSpi;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jassimp.AiColor;
import jassimp.AiMaterial;
import jassimp.AiMatrix4f;
import jassimp.AiMesh;
import jassimp.AiNode;
import jassimp.AiPostProcessSteps;
import jassimp.AiScene;
import jassimp.AiTextureInfo;
import jassimp.AiTextureType;
import jassimp.Jassimp;

public class AssimpModelSpi implements ModelSpi {

    public final static String TAG = "AssimpModelSpi";
    public final static ModelSpi INSTANCE = new AssimpModelSpi();

    // ModelInfo.type that resulting in Point DrawType
    public final static Set<String> pointTypes = new HashSet<>();

    static {
        pointTypes.add(PlyModelInfoSpi.TYPE);
    }

    @Override
    public String getType() {
        return "ASSIMP";
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public Model create(ModelInfo modelInfo, Callback callback) {
        ATAKAiIOSystem iosys = null;
        try {
            Set<AiPostProcessSteps> postSteps = new HashSet<>();
            /*postSteps = EnumSet.of(
                    AiPostProcessSteps.PRE_TRANSFORM_VERTICES
                    //AiPostProcessSteps.REMOVE_REDUNDANT_MATERIALS,
                    //AiPostProcessSteps.TRIANGULATE,
                    //AiPostProcessSteps.FLIP_UVS, // flip V because they copy to GL in reverse
                    //AiPostProcessSteps.TRIANGULATE, // guarantee triangles
                    //AiPostProcessSteps.DEBONE // ignore bones for now and bake in default positions
                    // XXX-- Splits, de-dupe and other optimizations seem to take too much memory for very large OBJs
            );*/

            String path = modelInfo.uri;
            if (path.startsWith("zip://"))
                path = path.substring(6);

            File file = new File(path);
            iosys = ATAKAiIOSystem.forModelSpiCallback(callback, 50);

            AiScene scene = Jassimp.importFile(file.getPath(), postSteps, iosys);

            if (callback != null && callback.isProbeOnly()) {
                callback.setProbeMatch(true);
                return null;
            }

            if(callback != null && callback.isCanceled()) {
                Log.d(TAG, "Load " + modelInfo.name + " canceled");
                return null;
            }

            Map<String, MeshBuilder> builders = new HashMap<>();
            ModelBuilder modelBuilder = new ModelBuilder();

            Object rootNode = scene.getSceneRoot(null);
            if(rootNode instanceof AiNode) {
                // XXX - collada needs to be rotated 90 about x-axis
                Matrix rootTransform = null;
                if(modelInfo.uri.endsWith(".dae")) {
                    rootTransform = Matrix.getIdentity();
                    rootTransform.rotate(Math.toRadians(90), 1d, 0d, 0d);
                }

                Map<String, int[]> meshVertCounts = new HashMap<>();
                int[] meshInstanceCount = new int[scene.getNumMeshes()];
                preprocessScene(modelInfo, file, meshVertCounts, meshInstanceCount, scene, scene.getMeshes(), (AiNode)rootNode);
                buildScene(modelInfo, file, meshVertCounts, meshInstanceCount, new boolean[scene.getNumMeshes()], builders, modelBuilder, scene, scene.getMeshes(), (AiNode)rootNode, rootTransform, new int[] {0, scene.getNumMeshes()}, callback);
            } else {
                List<AiMesh> meshes = scene.getMeshes();
                for (AiMesh mesh : meshes) {
                    if(callback != null && callback.isCanceled())
                        break;
                    addMesh(modelInfo, file, new HashMap<String, int[]>(), builders, modelBuilder, scene, 0, mesh, null, new int[]{0, scene.getNumMeshes()}, false, callback);
                }
            }


            for(MeshBuilder mesh : builders.values()) {
                if(callback != null && callback.isCanceled())
                    break;
                modelBuilder.addMesh(mesh);
            }

            if(callback != null && callback.isCanceled()) {
                Log.d(TAG, "Load " + modelInfo.name + " canceled");
                return null;
            }
            return modelBuilder.build();
        } catch (Exception e) {
            Log.i(TAG, "Assimp does not support " + modelInfo.uri, e);
            if (callback != null && callback.isProbeOnly()) {
                callback.setProbeMatch(false);
            }
        } finally {
            if(iosys != null)
                iosys.dispose();
        }

        return null;
    }

    private static Matrix getTransform(AiNode node) {
        Object o = node.getTransform(null);
        if(o instanceof AiMatrix4f) {
            AiMatrix4f mx = (AiMatrix4f)o;
            return new Matrix(
                mx.get(0, 0), mx.get(0, 1), mx.get(0, 2), mx.get(0, 3),
                mx.get(1, 0), mx.get(1, 1), mx.get(1, 2), mx.get(1, 3),
                mx.get(2, 0), mx.get(2, 1), mx.get(2, 2), mx.get(2, 3),
                mx.get(3, 0), mx.get(3, 1), mx.get(3, 2), mx.get(3, 3));
        }
        return null;
    }

    private static boolean isIdentity(Matrix mx) {
        for(int i = 0; i < 4; i++) {
            for(int j = 0; j < 4; j++) {
                final double v = mx.get(i, j);
                if(i != j && v != 0d)
                    return false;
                else if(i == j && v != 1d)
                    return false;
            }
        }

        return true;
    }

    /**
     *
     * @param meshProgress  A two dimensional array. Index 0 stores the number of processed meshes,
     *                      index 1 contains the total number of meshes
     * @param callback
     */
    private static void buildScene(ModelInfo modelInfo, File file, Map<String, int[]> meshVertCount, int[] meshInstanceCount, boolean[] meshDataPushed, Map<String, MeshBuilder> builders, ModelBuilder builder, AiScene scene, List<AiMesh> meshes, AiNode node, Matrix accumulated, int[] meshProgress, ModelSpi.Callback callback) {
        String uri = modelInfo.uri;
        Matrix nodeTransform = getTransform(node);
        if(nodeTransform != null && !isIdentity(nodeTransform)) {
            if(accumulated != null)
                nodeTransform.preConcatenate(accumulated);
            accumulated = nodeTransform;
        }

        final int[] meshIds = node.getMeshes();
        if(meshIds != null && meshIds.length > 0) {

            for (int i = 0; i < meshIds.length; i++) {
                if(callback != null && callback.isCanceled())
                    return;

                final int instanceCount = meshInstanceCount[meshIds[i]];
                final boolean isInstanced = (instanceCount > 1);
                // XXX - instancing not yet supported
                if(isInstanced && instanceCount > 64)
                    continue;
                if(isInstanced && meshDataPushed[meshIds[i]]) {
                    builder.addMesh(meshIds[i]+1, accumulated);
                } else {
                    AiMesh mesh = meshes.get(meshIds[i]);
                    addMesh(modelInfo, file, meshVertCount, builders, builder, scene, meshIds[i], mesh, accumulated, meshProgress, isInstanced, callback);
                    meshDataPushed[meshIds[i]] = true;
                }
            }
        }
        List<AiNode> children = node.getChildren();
        if(children == null)
            return;
        for(AiNode child : children) {
            if(callback != null && callback.isCanceled())
                return;
            buildScene(modelInfo, file, meshVertCount, meshInstanceCount, meshDataPushed, builders, builder, scene, meshes, child, accumulated, meshProgress, callback);
        }
    }

    /**
     *
     * @param meshProgress  A two dimensional array. Index 0 stores the number of processed meshes,
     *                      index 1 contains the total number of meshes
     * @param callback
     */
    private static void addMesh(ModelInfo modelInfo, File file, Map<String, int[]> meshVertCounts, Map<String, MeshBuilder> builders, ModelBuilder models, AiScene scene, int meshId, AiMesh mesh, Matrix accumulated, int[] meshProgress, boolean isInstance, ModelSpi.Callback callback) {

        String uri = modelInfo.uri;

        int vertexAttr = Mesh.VERTEX_ATTR_POSITION;
        FloatBuffer positionBuffer = mesh.getPositionBuffer();

        FloatBuffer normalBuffer = mesh.getNormalBuffer();
        if (normalBuffer != null) {
            vertexAttr |= Mesh.VERTEX_ATTR_NORMAL;
        }

        int numTexCoords = 0;
        FloatBuffer[] texCoords = { null, null, null, null, null, null, null, null };

        FloatBuffer texCoords0 = mesh.getTexCoordBuffer(0);
        if (texCoords0 != null) {
            vertexAttr |= Mesh.VERTEX_ATTR_TEXCOORD_0;
            texCoords[numTexCoords++] = texCoords0;
        }

        // Assimp defines 8 tex coord buffers

        for (int i = 0; i < 7; ++i) {
            FloatBuffer extCoords = mesh.getTexCoordBuffer(i + 1);
            if (extCoords != null) {
                vertexAttr |= Mesh.VERTEX_ATTR_TEXCOORD_1 << i;

                texCoords[numTexCoords++] = extCoords;
            }
        }

        if (numTexCoords > 1) {
            Log.w(TAG, "multiple texture coordinates currently ignored for " + uri);
        }

        FloatBuffer colorBuffer = mesh.getColorBuffer(0);
        if (colorBuffer != null) {
            vertexAttr |= Mesh.VERTEX_ATTR_COLOR;
        }

        //XXX-- Assimp allows for 8 color buffers

        // Assimp defines materials as a matrix of "texture types", but this may be overly
        // generalized. We just check the 0 index of each texture type. It's unlikely there
        // will be multiple of each (with the exception of Diffuse).
        // XXX-- currently we only support 1 diffuse material (no multi-texture).

        Material mat0 = Material.whiteDiffuse();

        int materialIndex = mesh.getMaterialIndex();
        String textureUri = null;
        if (materialIndex >= 0 && materialIndex < scene.getMaterials().size()) {
            AiMaterial material = scene.getMaterials().get(materialIndex);

            int bitFlags = 0;
            if (material.getTwoSided() != 0) {
                bitFlags |= Material.TWO_SIDED_BIT_FLAG;
            }

            int texCoordIndex = Material.NO_TEXCOORD_INDEX;

            // XXX - need to generate materials for each texture type, as applicable
            // XXX - iterate over number of textures for given type

            int materialTextureIndex = 0;
            AiTextureType materialTextureType = AiTextureType.DIFFUSE;

            AiTextureInfo textureInfo = material.getTextureInfo(materialTextureType, materialTextureIndex);
            String texFile = material.getTextureFile(materialTextureType, materialTextureIndex);

            if (texFile != null && !texFile.isEmpty()) {
                if (texFile.charAt(0) == '*') {
                    // XXX - pretty broken
                    String baseFileName = file.getName();
                    baseFileName = baseFileName.substring(0, baseFileName.lastIndexOf('.'));
                    if (baseFileName.endsWith("_simplified_3d_mesh"))
                        baseFileName = baseFileName.replace("_simplified_3d_mesh", "");

                    String[] exts = new String[]
                            {
                                    "_texture.jpg",
                                    "_texture.jpeg",
                                    "_texture.png",
                                    ".jpg",
                                    ".jpeg",
                                    ".png",
                            };

                    File parent = file.getParentFile();
                    if (!(parent instanceof ZipVirtualFile) && FileSystemUtils.isZipPath(parent)) {
                        try {
                            parent = new ZipVirtualFile(parent);
                        } catch (Throwable ignored) {
                        }
                    }
                    File f = ObjUtils.findFile(parent, baseFileName, exts);
                    if (f != null)
                        textureUri = f.getAbsolutePath();
                } else {
                    if (modelInfo.resourceMap != null && modelInfo.resourceMap.containsKey(texFile))
                        texFile = modelInfo.resourceMap.get(texFile);
                    textureUri = new File(file.getParent() + File.separator + texFile).getAbsolutePath();
                }
                texCoordIndex = textureInfo.getUVIndex();
            } else {
                Log.w(TAG, "No texture for diffuse material");
            }

            AiColor diffuse = (AiColor) material.getDiffuseColor(null);
            AiColor ambient = material.getAmbientColor(null);
            //Object rc = material.getReflectiveColor(null);
            //Object ec = material.getEmissiveColor(null);
            //Object tc = material.getTransparentColor(null);
            float opacity = material.getOpacity();
            //AiBlendMode bm = material.getBlendMode();
            //AiShadingMode sm = material.getShadingMode();

            final float red = red(diffuse);
            final float green = green(diffuse);
            final float blue = blue(diffuse);
            final float alpha = alpha(diffuse);

            int color = (((int) (alpha * 0xFF) << 24) & 0xFF000000) |
                    (((int) (red * 0xFF) << 16) & 0xFF0000) |
                    (((int) (green * 0xFF) << 8) & 0xFF00) |
                    (((int) (blue * 0xFF)) & 0xFF);

            // XXX - points_8_0_0__v1__AGISOFT.kmz color pointer only,
            //       bad color modulation
            if(colorBuffer != null)
                color = -1;

            mat0 = new Material(textureUri, Material.PropertyType.Diffuse, color, texCoordIndex, bitFlags);
        }

        Mesh.DrawMode drawMode = Mesh.DrawMode.Triangles;
        if (pointTypes.contains(modelInfo.type)) {
            drawMode = Mesh.DrawMode.Points;
        }

        final int vertCount = positionBuffer.limit() / 3;

        // XXX -
        MeshBuilder builder = isInstance ? null : builders.get(textureUri);
        if(builder == null) {
            if(!isInstance)
                vertexAttr |= Mesh.VERTEX_ATTR_COLOR;

            builder = new MeshBuilder(vertexAttr, false, drawMode);
            if(!isInstance)
                builders.put(textureUri, builder);
            if (mat0.getTwoSided())
                builder.setWindingOrder(Mesh.WindingOrder.Undefined);
            else
                builder.setWindingOrder(Model.WindingOrder.CounterClockwise);

            // color modulation will occur on vertex add to reduce material switches at render time
            if(isInstance)
                builder.addMaterial(mat0);
            else
                builder.addMaterial(new Material(mat0.getTextureUri(), mat0.getPropertyType(), -1, mat0.getTexCoordIndex(), mat0.getTwoSided() ? Material.TWO_SIDED_BIT_FLAG : 0));

            int reserveVerts;
            if(meshVertCounts.containsKey(textureUri) && !isInstance)
                reserveVerts = meshVertCounts.get(textureUri)[0];
            else
                reserveVerts = vertCount;

            builder.reserveVertices(reserveVerts);
        }

        final float matr = Color.red(mat0.getColor())/255f;
        final float matg = Color.green(mat0.getColor())/255f;
        final float matb = Color.blue(mat0.getColor())/255f;
        final float mata = Color.alpha(mat0.getColor())/255f;

        final int updateInterval = vertCount/100;
        PointD pos = new PointD(0d, 0d, 0d);
        float[] uv = new float[16];
        final int limit = (drawMode == Mesh.DrawMode.Triangles) ? (vertCount/3)*3 : vertCount;
        for (int i = 0; i < limit; ++i) {
            if(callback != null && callback.isCanceled())
                return;

            pos.x = positionBuffer.get(i * 3);
            pos.y = positionBuffer.get(i * 3 + 1);
            pos.z = positionBuffer.get(i * 3 + 2);
            if(!isInstance && accumulated != null)
                accumulated.transform(pos, pos);

            // think java will unroll these? oh well going to be native
            for(int j = 0; j < numTexCoords; j++) {
                final float u = texCoords[j].get(i * 2);
                final float v = texCoords[j].get(i * 2 + 1);
                uv[j*2] = u;
                uv[j*2+1] = 1 - v;
            }

            float nx = 0f, ny = 0f, nz = 0f;
            if (normalBuffer != null) {
                nx = normalBuffer.get(i * 3);
                ny = normalBuffer.get(i * 3 + 1);
                nz = normalBuffer.get(i * 3 + 2);
            }

            float r = 1f, g = 1f, b = 1f, a = 1f;
            if (colorBuffer != null) {
                r = colorBuffer.get(i * 4);
                g = colorBuffer.get(i * 4 + 1);
                b = colorBuffer.get(i * 4 + 2);
                a = colorBuffer.get(i * 4 + 3);
            }

            r *= matr;
            g *= matg;
            b *= matb;
            a *= mata;

            //XXX-- multiple texture coordinates

            builder.addVertex(pos.x, pos.y, pos.z,
                              uv,
                              nx, ny, nz,
                              r, g, b, a);

            if(callback != null) {
                if(updateInterval > 0 && (i%updateInterval) == 0) {
                    final double meshProgressSlice = 1d / (double)meshProgress[1];
                    double percentMeshComplete = meshProgress[0] * meshProgressSlice;
                    final double vertProgress = (double)i/(double)vertCount;
                    final double totalMeshProcessProgress = percentMeshComplete + (meshProgressSlice*vertProgress);
                    callback.progress(50 + (int)(totalMeshProcessProgress * 50d));
                }
            }
        }
/*
        for(Material mat : materials) {
            builder.addMaterial(mat);

            if (mat.getTwoSided()) {
                builder.setWindingOrder(Mesh.WindingOrder.Undefined);
            }
        }
*/

        if(isInstance)
            models.addMesh(builder, meshId+1, accumulated);

        meshProgress[0]++;
    }

    private static void preprocessScene(ModelInfo modelInfo, File file, Map<String, int[]> meshVertCounts, int[] meshInstanceCount, AiScene scene, List<AiMesh> meshes, AiNode node) {
        // determine all instanced meshes
        preprocessSceneCountMeshInstances(meshInstanceCount, node);

        // compile the scene mesh vertex counts for aggregation, excluding instanced meshes
        preprocessSceneMeshVertCounts(modelInfo, file, meshVertCounts, meshInstanceCount, scene, meshes, node);
    }

    private static void preprocessSceneCountMeshInstances(int[] meshInstanceCount, AiNode node) {
        final int[] meshIds = node.getMeshes();
        if(meshIds != null && meshIds.length > 0) {
            for (int i = 0; i < meshIds.length; i++) {
                meshInstanceCount[meshIds[i]]++;
            }
        }
        List<AiNode> children = node.getChildren();
        if(children == null)
            return;
        for(AiNode child : children) {
            preprocessSceneCountMeshInstances(meshInstanceCount, child);
        }
    }

    private static void preprocessSceneMeshVertCounts(ModelInfo modelInfo, File file, Map<String, int[]> meshVertCounts, int[] meshInstanceCount, AiScene scene, List<AiMesh> meshes, AiNode node) {
        String uri = modelInfo.uri;

        final int[] meshIds = node.getMeshes();
        if(meshIds != null && meshIds.length > 0) {
            for (int i = 0; i < meshIds.length; i++) {
                // skip instanced meshes
                if(meshInstanceCount[meshIds[i]] > 1)
                    continue;
                AiMesh mesh = meshes.get(meshIds[i]);
                preprocessMesh(modelInfo, file, meshVertCounts, scene, mesh);
            }
        }
        List<AiNode> children = node.getChildren();
        if(children == null)
            return;
        for(AiNode child : children) {
            preprocessSceneMeshVertCounts(modelInfo, file, meshVertCounts, meshInstanceCount, scene, meshes, child);
        }
    }

    private static void preprocessMesh(ModelInfo modelInfo, File file, Map<String, int[]> meshVertCounts, AiScene scene, AiMesh mesh) {
        int materialIndex = mesh.getMaterialIndex();
        String textureUri = null;
        if (materialIndex >= 0 && materialIndex < scene.getMaterials().size()) {
            AiMaterial material = scene.getMaterials().get(materialIndex);

            // XXX - need to generate materials for each texture type, as applicable
            // XXX - iterate over number of textures for given type

            int materialTextureIndex = 0;
            AiTextureType materialTextureType = AiTextureType.DIFFUSE;

            AiTextureInfo textureInfo = material.getTextureInfo(materialTextureType, materialTextureIndex);
            String texFile = material.getTextureFile(materialTextureType, materialTextureIndex);

            if (texFile != null && !texFile.isEmpty()) {
                if (texFile.charAt(0) == '*') {
                    // XXX - pretty broken
                    String baseFileName = file.getName();
                    baseFileName = baseFileName.substring(0, baseFileName.lastIndexOf('.'));
                    if (baseFileName.endsWith("_simplified_3d_mesh"))
                        baseFileName = baseFileName.replace("_simplified_3d_mesh", "");

                    String[] exts = new String[]
                            {
                                    "_texture.jpg",
                                    "_texture.jpeg",
                                    "_texture.png",
                                    ".jpg",
                                    ".jpeg",
                                    ".png",
                            };

                    File parent = file.getParentFile();
                    if (!(parent instanceof ZipVirtualFile) && FileSystemUtils.isZipPath(parent)) {
                        try {
                            parent = new ZipVirtualFile(parent);
                        } catch (Throwable ignored) {
                        }
                    }
                    File f = ObjUtils.findFile(parent, baseFileName, exts);
                    if (f != null)
                        textureUri = f.getAbsolutePath();
                } else {
                    if (modelInfo.resourceMap != null && modelInfo.resourceMap.containsKey(texFile))
                        texFile = modelInfo.resourceMap.get(texFile);
                    textureUri = new File(file.getParent() + File.separator + texFile).getAbsolutePath();
                }
            }
        }

        // XXX -
        int[] vertCount = meshVertCounts.get(textureUri);
        if(vertCount == null) {
            vertCount = new int[1];
            meshVertCounts.put(textureUri, vertCount);
        }

        vertCount[0] += mesh.getNumVertices();
    }

    static float red(AiColor c) {
        return (c != null) ? c.getRed() : 1f;
    }

    static float green(AiColor c) {
        return (c != null) ? c.getGreen() : 1f;
    }

    static float blue(AiColor c) {
        return (c != null) ? c.getBlue() : 1f;
    }

    static float alpha(AiColor c) {
        return (c != null) ? c.getAlpha() : 1f;
    }

    @Override
    public Model create(ModelInfo object) {
        return this.create(object, null);
    }

    public static Envelope getAabb(File file) {
        ATAKAiIOSystem iosys = null;
        try {
            Set<AiPostProcessSteps> postSteps = Collections.emptySet();
            iosys = ATAKAiIOSystem.forModelSpiCallback(null, 50);
            AiScene scene = Jassimp.importFile(file.getPath(), postSteps, iosys);
            return getAabb(scene);
        } catch (IOException e) {
            return null;
        } finally {
            if (iosys != null)
                iosys.dispose();
        }
    }

    private static Envelope getAabb(AiScene scene) {
        Envelope result = null;
        for (int i = 0; i < scene.getNumMeshes(); ++i) {
            AiMesh mesh = scene.getMeshes().get(i);
            FloatBuffer buffer = mesh.getPositionBuffer();
            Envelope aabb = getAabb(buffer, buffer.limit() / 3);
            if (result == null)
                result = aabb;
            if (aabb.minX < result.minX)
                result.minX = aabb.minX;
            if (aabb.minY < result.minY)
                result.minY = aabb.minY;
            if (aabb.minZ < result.minZ)
                result.minZ = aabb.minZ;
            if (aabb.maxX > result.maxX)
                result.maxX = aabb.maxX;
            if (aabb.maxY > result.maxY)
                result.maxY = aabb.maxY;
            if (aabb.maxZ > result.maxZ)
                result.maxZ = aabb.maxZ;
        }
        return result;
    }

    private static Envelope getAabb(FloatBuffer buf, int numVerts) {
        if(numVerts < 1)
            return new Envelope(0d, 0d, 0d, 0d, 0d, 0d);

        Envelope retval = new Envelope(buf.get(0), buf.get(1), buf.get(2), buf.get(0), buf.get(1), buf.get(2));
        for(int i = 1; i < numVerts; i++) {
            final float x = buf.get(i*3);
            final float y = buf.get(i*3+1);
            final float z = buf.get(i*3+2);
            if(x < retval.minX) retval.minX = x;
            else if(x > retval.maxX) retval.maxX = x;
            if(y < retval.minY) retval.minY = y;
            else if(y > retval.maxY) retval.maxY = y;
            if(z < retval.minZ) retval.minZ = z;
            else if(z > retval.maxZ) retval.maxZ = z;
        }
        return retval;
    }
}
