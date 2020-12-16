package com.atakmap.map.layer.model.contextcapture;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelHitTestControl;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelSpi;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.assimp.AssimpModelSpi;
import com.atakmap.map.layer.model.opengl.GLMaterial;
import com.atakmap.map.layer.model.opengl.GLMesh;
import com.atakmap.map.layer.model.opengl.MaterialManager;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLResolvable;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** @deprecated PROTOTYPE CODE; SUBJECT TO REMOVAL AT ANY TIME; DO NOT CREATE DIRECT DEPENDENCIES */
@Deprecated
@DeprecatedApi(since = "4.1")
final class GLContextCaptureTile implements GLTileNode, Controls {
    public boolean lods;
    int minLod;
    int maxLod;
    int tileX;
    int tileY;

    LODMeshes[] lodMeshes;

    Envelope mbb;

    ModelInfo info;
    String baseTileDir;
    String tileNameSpec;

    GLMapView ctx;

    int lastRenderIdx = -1;
    int fadeCountdown;
    int fadeIdx;

    Collection<Object> controls;


    public GLContextCaptureTile(ModelInfo info, String baseTileDir, int tileX, int tileY) {
        this.info = info;
        this.baseTileDir = baseTileDir;
        this.tileX = tileX;
        this.tileY = tileY;
        final String baseTileDirSansTrailingSlash = this.baseTileDir.substring(0, this.baseTileDir.length()-1);
        final int lastFSlash = baseTileDirSansTrailingSlash.lastIndexOf('/');
        final int lastBSlash = baseTileDirSansTrailingSlash.lastIndexOf('\\');
        this.tileNameSpec = baseTileDirSansTrailingSlash.substring(Math.max(lastFSlash, lastBSlash)+1);

        controls = new ArrayList<>(1);
        controls.add(new HitTestControlImpl());
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        // choose LOD for view
        final int lod = selectLod(view, false);
        if(lod < minLod-1) {
            // drop levels not being used
            synchronized(this) {
                if(lodMeshes != null) {
                    final int numLods = maxLod - minLod + 1;
                    for (int i = 0; i < numLods; i++) {
                        if (lodMeshes[i] != null) {
                            if (lodMeshes[i].locks == 0)
                                lodMeshes[i].release();
                            lodMeshes[i] = null;
                        }
                    }
                }
            }
            return;
        }

        this.ctx = view;

        final int clampedLod = MathUtils.clamp(lod-1, minLod, maxLod);
        if(lodMeshes == null)
            return;

        final int numLods = maxLod-minLod+1;

        // render "best" populated LOD, first minimum >= target, second maximum <= target
        int renderIdx = clampedLod-minLod;
        for(int i = renderIdx; i < numLods; i++) {
            if(lodMeshes[i] != null && lodMeshes[i].data.length > 0 && lodMeshes[i].isResolved()) {
                renderIdx = i;
                break;
            }
        }
        for(int i = renderIdx; i >= 0; i--) {
            if(lodMeshes[i] != null && lodMeshes[i].data.length > 0 && lodMeshes[i].isResolved()) {
                renderIdx = i;
                break;
            }
        }

        //if(renderIdx != lastRenderIdx && lastRenderIdx != -1 && fadeIdx != lastRenderIdx) {
        //    fadeCountdown = 10;
        //    fadeIdx = lastRenderIdx;
        //}

        float fade = fadeCountdown/10f;
        //if(lastRenderIdx != -1 && lastRenderIdx != renderIdx) {
        //    lodMeshes[lastRenderIdx].setAlpha(fade);
        //    lodMeshes[lastRenderIdx].draw(view, renderPass);
        //}
        if(lodMeshes[renderIdx] != null) {
//            if(lodMeshes[renderIdx].ctrl != null) {
//                int color = (((tileX+tileY)%2) == 0) ? 0xFFFFFF00 : 0xFF00FFFF;
//                for(ColorControl ctrl : lodMeshes[renderIdx].ctrl)
//                    ctrl.setColor(color);
//            }
            //lodMeshes[renderIdx].setAlpha(1f-fade);
            lodMeshes[renderIdx].draw(view, renderPass);
            //if(fadeCountdown > 0) {
            //    fadeCountdown--;
            //} else {
            //    lastRenderIdx = renderIdx;
            //}
        }

        // drop levels not being used
        synchronized(this) {
            this.lastRenderIdx = renderIdx;

            for(int i = 1; i < numLods; i++) {
                if(i == (clampedLod-minLod))
                    continue;
                if(i == renderIdx)
                    continue;
                //if(i == lastRenderIdx)
                //    continue;
                if(lodMeshes[i] != null) {
                    if(lodMeshes[i].locks == 0)
                        lodMeshes[i].release();
                    lodMeshes[i] = null;
                }
            }
        }
    }

    @Override
    public void release() {
        synchronized(this) {
            if (lodMeshes != null) {
                for (int i = 0; i < lodMeshes.length; i++) {
                    LODMeshes lod = lodMeshes[i];
                    if (lod == null)
                        continue;
                    if(lod.locks == 0)
                        lod.release();
                    lodMeshes[i] = null;
                }
                lodMeshes = null;
            }
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES;
    }

    GLMesh[] fetchImpl(GLMapView view, final int lod, MaterialManager matmgr, final AtomicBoolean cancelToken) {
        // XXX - quadtree/octree

        final StringBuilder entryPath = new StringBuilder(this.baseTileDir);
        entryPath.append(this.tileNameSpec);
        if(lods) {
            entryPath.append("_L");
            entryPath.append(String.format("%02d", lod));
        }
        entryPath.append(".obj");

        //final Thread currentThread = Thread.currentThread();
        final ModelSpiCanceler cb = new ModelSpiCanceler(cancelToken);

        GLMesh[] retval;
        try {
            Model m = fetch(info.uri + "/" + entryPath.toString().replace('\\', '/'), cb);
            if(m == null && lods) {
                // there was a miss
                // see if the tile is quadtree/octree
                ZipVirtualFile tiledir = new ZipVirtualFile(info.uri + "/" + this.baseTileDir);
                File[] subs = IOProviderFactory.listFiles(tiledir, new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return filename.startsWith(tileNameSpec + "_L" + String.format("%02d_", lod)) && filename.endsWith(".obj");
                    }
                });

                if(subs != null) {
                    List<GLMesh> meshes = new LinkedList<>();
                    for(File sub : subs) {
                        if(cb.isCanceled())
                            break;

                        Model s = fetch(sub.getAbsolutePath(), cb);
                        if(s != null) {
                            final int numMeshes = s.getNumMeshes();
                            for(int i = 0; i < numMeshes; i++) {
                                meshes.add(new GLMesh(info.localFrame, ModelInfo.AltitudeMode.Absolute, s.getMesh(i), new PointD(0d, 0d, 0d), matmgr));
                            }
                        }
                    }
                    retval = meshes.toArray(new GLMesh[0]);
                } else {
                    retval = new GLMesh[0];
                }
            } else if (m != null) {
                final int numMeshes = m.getNumMeshes();
                retval = new GLMesh[m.getNumMeshes()];
                for(int i = 0; i < numMeshes; i++)
                    retval[i] = new GLMesh(info.localFrame, ModelInfo.AltitudeMode.Absolute, m.getMesh(i), new PointD(0d, 0d, 0d), matmgr);
            } else { 
               Log.w("GLContextCaptureTile", "model is null");
               retval = new GLMesh[0];
            }
        } catch(Throwable t) {
            Log.w("GLContextCaptureTile", "Failed to load LOD " + entryPath);
            retval = new GLMesh[0];
        }

        // XXX - load all textures before returning

        return retval;
    }

    static Model fetch(String uri, ModelSpi.Callback cb) {
        Log.w("GLContextCaptureTile", "Fetching " + uri);
        ModelInfo fetchInfo = new ModelInfo();
        fetchInfo.uri = uri;
        return AssimpModelSpi.INSTANCE.create(fetchInfo, cb);
    }

    // next two derived from https://docs.bentley.com/LiveContent/web/ContextCapture%20Help-v10/en/GUID-531B1CDD-7234-4D93-8048-1FA41B2F163A.html

    static double gsd2lod(double gsd) {
        return 16d - (Math.log(gsd)/Math.log(2));
    }

    static double lod2gsd(int lod) {
        if(lod < 16) {
            return 1<<(16-lod);
        } else if(lod == 16) {
            return 1d;
        } else if(lod > 16) {
            return 1d / (double)(1<<(lod-16));
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public void asyncLoad(LoadContext loadContext, AtomicBoolean cancelToken) {
        if(loadContext.opaque == null) // nothing to load
            return;

        final int lod = (Integer)loadContext.opaque;

        if(cancelToken.get())
            return;

        // load the meshes
        final MaterialManager matmgr = new MaterialManager(this.ctx, 1024);
        final GLMesh[] fetchedMeshes = fetchImpl(this.ctx, lod, matmgr, cancelToken);
        if(fetchedMeshes != null) {
            final LODMeshes fetched = new LODMeshes();
            fetched.setData(fetchedMeshes);
            fetched.matmgr = matmgr;

            // prefetch the materials
            for(GLMesh glmesh : fetchedMeshes) {
                final Mesh mesh = glmesh.getSubject();
                final int numMats = ((Mesh) mesh).getNumMaterials();
                for(int i = 0; i < numMats; i++) {
                    fetched.prefetched.add(fetched.matmgr.load(mesh.getMaterial(i)));
                }
            }

            if(!cancelToken.get()) {
                Envelope aabb = null;
                if(fetchedMeshes.length >0 && lod == minLod) {
                    aabb = new Envelope(fetchedMeshes[0].getSubject().getAABB());
                    for(int i = 1; i < fetchedMeshes.length; i++) {
                        Envelope tmbb = fetchedMeshes[i].getSubject().getAABB();
                        if(tmbb.minX < aabb.minX)
                            aabb.minX = tmbb.minX;
                        if(tmbb.maxX > aabb.maxX)
                            aabb.maxX = tmbb.maxX;
                        if(tmbb.minY < aabb.minY)
                            aabb.minY = tmbb.minY;
                        if(tmbb.maxY > aabb.maxY)
                            aabb.maxY = tmbb.maxY;
                        if(tmbb.minZ < aabb.minZ)
                            aabb.minZ = tmbb.minZ;
                        if(tmbb.maxZ > aabb.maxZ)
                            aabb.maxZ = tmbb.maxZ;
                    }
                    ModelInfo wgs84 = new ModelInfo();
                    wgs84.srid = 4326;
                    Models.transform(aabb, info, aabb, wgs84);
                }
                synchronized(this) {
                    if(this.lodMeshes == null)
                        this.lodMeshes = new LODMeshes[this.maxLod-this.minLod+1];
                    if(this.lodMeshes[lod-minLod] != null) {
                        // dispose existing entry
                        final LODMeshes toRelease = this.lodMeshes[lod-minLod];
                        if(toRelease.locks == 0)
                            this.ctx.queueEvent(new Runnable() {
                                public void run() {
                                    toRelease.release();
                                }
                            });
                    }
                    this.lodMeshes[lod-minLod] = fetched;

                    // update mbb
                    if(aabb != null)
                        mbb = aabb;
                    this.ctx.requestRefresh();
                }
            } else {
                // canceled, dispose
                this.ctx.queueEvent(new Runnable() {
                    public void run() {
                        fetched.release();
                    }
                });
            }
        }
    }

    @Override
    public boolean isLoaded(GLMapView view) {
        if(lodMeshes == null)
            return false;

        // choose LOD for view
        final int lod = selectLod(view, true);

        this.ctx = view;
        final int clampedLod = MathUtils.clamp(lod-1, minLod, maxLod);

        // if LOD not populated, fetch. always need to fetch minimum LOD
        return (lodMeshes[0] != null) && (lodMeshes[clampedLod-minLod] != null);
    }

    @Override
    public LoadContext prepareLoadContext(GLMapView view) {
        // choose LOD for view
        final int lod = selectLod(view, true);

        this.ctx = view;
        final int clampedLod = MathUtils.clamp(lod-1, minLod, maxLod);

        LoadContext retval = new LoadContext();
        retval.centroid = new GeoPoint((mbb.minY+mbb.maxY)/2d, (mbb.minX+mbb.maxX)/2d, (mbb.minZ+mbb.maxZ)/2d);
        retval.boundingSphereRadius = 100d;

        if(lodMeshes == null || lodMeshes[0] == null)
            retval.opaque = Integer.valueOf(minLod);
        else if(lodMeshes[clampedLod-minLod] == null)
            retval.opaque = Integer.valueOf(clampedLod);
        else
            retval.opaque = null;

        retval.gsd = (retval.opaque != null) ? lod2gsd((Integer)retval.opaque) : lod2gsd(minLod);

        return retval;
    }

    private int selectLod(GLMapView view, boolean checkPrefetch) {
        // XXX - waiting on perspective
        //view.scratch.pointD.x = (mbb.minX+mbb.maxX)/2d;
        //view.scratch.pointD.y = (mbb.minY+mbb.maxY)/2d;
        //view.scratch.pointD.z = (mbb.minZ+mbb.maxZ)/2d;
        //final double radius = 0d; // XXX - compute radius in meters
        //final double gsd = GLMapView.estimateResolution(view, view.scratch.pointD, radius, null);

        final double gsd = view.drawMapResolution;
        int targetLod = (int)(gsd2lod(gsd)+0.5d);

        // XXX - remove hack here once perspective is implemented and better
        //       estimated GSD is computed
        if(checkPrefetch && !MapSceneModel.intersects(view.scene, mbb.minX, mbb.minY, mbb.minZ, mbb.maxX, mbb.maxY, mbb.maxZ)) {
            targetLod = (targetLod+minLod)/2;
        }

        return targetLod;
    }

    @Override
    public RenderVisibility isRenderable(GLMapView view) {
        final int lod = selectLod(view, false);
        if(lod < minLod-1)
            return RenderVisibility.None;

        if(MapSceneModel.intersects(view.scene, mbb.minX, mbb.minY, mbb.minZ, mbb.maxX, mbb.maxY, mbb.maxZ))
            return RenderVisibility.Draw;

        final double wx = (mbb.maxX-mbb.minX);
        final double wy = (mbb.maxY-mbb.minY);
        final double wz = (mbb.maxZ-mbb.minZ);

        return MapSceneModel.intersects(view.scene,
                                        mbb.minX-wx,
                                        mbb.minY-wy,
                                        mbb.minZ-wz,
                                        mbb.maxX+wx,
                                        mbb.maxY + wy,
                                        mbb.maxZ+wz) ? RenderVisibility.Prefetch : RenderVisibility.None;
    }

    @Override
    public void unloadLODs() {
        synchronized(this) {
            if(this.lodMeshes == null)
                return;
            for(int i = 1; i < this.lodMeshes.length; i++)
                if(this.lodMeshes[i] != null) {
                    this.lodMeshes[i].release();
                    this.lodMeshes[i] = null;
                }
        }
    }

    @Override
    public boolean hasLODs() {
        return (this.maxLod>this.minLod);
    }

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        for(Object ctrl : this.controls)
            if(controlClazz.isAssignableFrom(ctrl.getClass()))
                return controlClazz.cast(ctrl);
        return null;
    }

    @Override
    public void getControls(Collection<Object> controls) {
        controls.addAll(this.controls);
    }

    static class LODMeshes {
        GLMesh[] data;
        Set<GLMaterial> prefetched = Collections.newSetFromMap(new IdentityHashMap<GLMaterial, Boolean>());
        MaterialManager matmgr;
        ColorControl[] ctrl;
        int locks;

        void setData(GLMesh[] data) {
            this.data = data;
            if(this.data != null) {
                this.ctrl = new ColorControl[this.data.length];
                for(int i = 0; i < this.data.length; i++)
                    this.ctrl[i] = this.data[i].getControl(ColorControl.class);
            } else {
                this.ctrl = null;
            }
        }

        void setAlpha(float a) {
            if(this.ctrl == null)
                return;
            final int color = (int)(a*255f)|0xFFFFFF;
            for(ColorControl c : this.ctrl)
                c.setColor(color);
        }
        boolean isResolved() {
            for(GLMaterial mat : prefetched) {
                if(!mat.isTextured())
                    continue;
                // XXX - force state update
                mat.getTexture();

                if(mat.getState() == GLResolvable.State.RESOLVING)
                    return false;
            }
            return true;
        }

        void draw(GLMapView view, int renderPass) {
            if(data != null) {
                for(GLMesh mesh : data) {
                    mesh.draw(view, renderPass);
                }
            }
        }
        void release() {
            if(data != null) {
                for(GLMesh mesh : data)
                    mesh.release();
                data = null;
            }
            for(GLMaterial mat : prefetched)
                matmgr.unload(mat);
            matmgr.dispose();
        }
    }

    class HitTestControlImpl implements ModelHitTestControl {

        @Override
        public boolean hitTest(float screenX, float screenY, GeoPoint result) {
            final LODMeshes meshes;
            final int idx;
            synchronized(GLContextCaptureTile.this) {
                if(lodMeshes == null)
                    return false;
                if(lastRenderIdx < 0 || lastRenderIdx >= lodMeshes.length)
                    return false;
                idx = lastRenderIdx;
                meshes = lodMeshes[idx];
                if(meshes == null || meshes.data == null)
                    return false;
                meshes.locks++;
            }

            final MapSceneModel sm = ((GLMapSurface)ctx.getRenderContext()).getMapView().getSceneModel();
            boolean retval = false;
            for (GLMesh mesh : meshes.data) {
                // XXX - need to find closest hit
                if (mesh != null && mesh.hitTest(sm, screenX, screenY, result)) {
                    retval = true;
                    break;
                }
            }

            final boolean release;
            synchronized(GLContextCaptureTile.this) {
                meshes.locks--;
                // release the LOD meshes if it is unlocked and it is no longer
                // loaded for prefetch or draw
                release = (meshes.locks == 0 && (lodMeshes == null || lodMeshes[idx] != meshes));
            }

            if(release)
                ctx.queueEvent(new Runnable() {
                    public void run() {
                        meshes.release();
                    }
                });

            return retval;
        }
    }

    final static class ModelSpiCanceler  implements ModelSpi.Callback {
        final AtomicBoolean cancelToken;

        ModelSpiCanceler(AtomicBoolean token) {
            this.cancelToken = token;
        }

        public boolean isCanceled() { return cancelToken.get(); }
        public boolean isProbeOnly() { return false; }
        public int getProbeLimit() { return 0; }
        public void setProbeMatch(boolean match) {}
        public void errorOccurred(String msg, Throwable t) {}
        public void progress(int progress) {}
    };
}
