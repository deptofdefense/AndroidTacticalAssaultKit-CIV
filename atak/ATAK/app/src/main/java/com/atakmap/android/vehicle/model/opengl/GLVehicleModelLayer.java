
package com.atakmap.android.vehicle.model.opengl;

import android.graphics.PointF;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.model.opengl.GLModelRenderer2.TextureLoader;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.GLRubberModel;
import com.atakmap.android.rubbersheet.maps.GLRubberModelLayer;
import com.atakmap.android.rubbersheet.maps.LoadState;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.android.vehicle.model.VehicleModelCache;
import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.android.vehicle.model.VehicleModelLayer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.opengl.MaterialManager;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GL renderer for 3D vehicle models
 */
public class GLVehicleModelLayer extends GLRubberModelLayer implements
        RubberModel.OnLoadListener, MapItem.OnMetadataChangedListener,
        AbstractSheet.OnAlphaChangedListener {

    public static final boolean USE_INSTANCES = true;

    private final MaterialManager _matManager;
    private final Map<String, List<GLInstancedMesh>> _meshInstances = new HashMap<>();
    private final Map<String, GLInstancedPolyline> _lineInstances = new HashMap<>();
    private final GLMeshLayer _meshLayer = new GLMeshLayer();

    public GLVehicleModelLayer(MapRenderer renderer, VehicleModelLayer subject,
            MapGroup group) {
        super(renderer, subject, group);

        File resourceDir = new File(VehicleModelCache.DIR, ".resources");
        _matManager = new MaterialManager(renderer,
                new TextureLoader(renderer, resourceDir, 4096));
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE | GLMapView.RENDER_PASS_SCENES;
    }

    @Override
    protected GLRubberModel createGLModel(RubberModel mdl) {
        if (!(mdl instanceof VehicleModel))
            return super.createGLModel(mdl);

        VehicleModel vehicle = (VehicleModel) mdl;
        vehicle.addLoadListener(this);
        vehicle.addOnAlphaChangedListener(this);
        vehicle.addOnMetadataChangedListener(this);
        return new GLVehicleModel(renderContext, vehicle);
    }

    @Override
    protected void unregister(RubberModel mdl) {
        super.unregister(mdl);
        if (mdl instanceof VehicleModel) {
            VehicleModel vehicle = (VehicleModel) mdl;
            vehicle.removeLoadListener(this);
            vehicle.removeOnAlphaChangedListener(this);
            vehicle.removeOnMetadataChangedListener(this);
            VehicleModelInfo info = vehicle.getVehicleInfo();
            if (info != null)
                unregister(info, mdl.getInfo(), mdl.getUID());
        }
    }

    private void unregister(final VehicleModelInfo info, ModelInfo mInfo,
            final String uid) {
        final String uri = mInfo != null ? mInfo.uri : null;
        renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                // Unregister usage of model
                boolean lastUsage = VehicleModelCache.getInstance()
                        .unregisterUsage(info, uid);

                // Last usage of this model - release associated meshes
                if (lastUsage && USE_INSTANCES)
                    releaseMeshInstances(uri);
            }
        });
    }

    @Override
    protected boolean updateRenderList(ViewState state,
            Collection<GLMapRenderable2> pending) {
        if (USE_INSTANCES)
            pending = prepareMeshInstances(pending);
        return super.updateRenderList(state, pending);
    }

    private synchronized List<GLMapRenderable2> prepareMeshInstances(
            Collection<GLMapRenderable2> pending) {

        List<GLMapRenderable2> toRender = new ArrayList<>();
        Set<String> added = new HashSet<>();
        List<GLInstancedMesh> opaque = new ArrayList<>();
        List<GLVehicleModel> transparent = new ArrayList<>();

        for (GLMapRenderable2 renderable : pending) {
            if (!(renderable instanceof GLVehicleModel))
                continue;

            GLVehicleModel model = (GLVehicleModel) renderable;
            VehicleModel vehicle = (VehicleModel) model.getSubject();

            ModelInfo info = model.getModelInfo();
            if (info == null)
                continue;

            VehicleModelInfo vInfo = vehicle.getVehicleInfo();
            if (vInfo == null)
                continue;

            // Get each mesh used by a given model URI
            List<GLInstancedMesh> meshes = _meshInstances.get(info.uri);
            if (meshes == null) {
                // Create meshes
                String name = info.uri.substring(info.uri.lastIndexOf('/') + 1);
                _meshInstances.put(info.uri, meshes = new ArrayList<>());
                for (Mesh mesh : model.getMeshes()) {
                    GLInstancedMesh iMesh = new GLInstancedMesh(
                            name + "/" + meshes.size(), mesh, _matManager);
                    meshes.add(iMesh);
                }
            }

            // Outline instances
            GLInstancedPolyline line = _lineInstances.get(info.uri);
            if (line == null) {
                List<PointF> points = vInfo.getOutline(new Runnable() {
                    @Override
                    public void run() {
                        // Points are now ready to use
                        invalidate();
                    }
                });
                if (points != null) {
                    line = new GLInstancedPolyline(vInfo.name, points);
                    _lineInstances.put(info.uri, line);
                }
            }

            // First time adding these meshes to the render list for this step
            if (!added.contains(info.uri)) {

                // Add meshes to render list
                opaque.addAll(meshes);
                added.add(info.uri);

                // Reset instance list for this drawing step
                for (GLInstancedMesh mesh : meshes)
                    mesh.reset();
                if (line != null) {
                    toRender.add(line);
                    line.reset();
                }
            }

            // Add instance data for this drawing step
            GLInstanceData mInstance = model.getInstanceData();
            int alpha = vehicle.getAlpha();
            if (alpha > 0) {
                if (alpha < 255) {
                    transparent.add(model);
                } else {
                    for (GLInstancedMesh mesh : meshes)
                        mesh.addInstance(mInstance);
                }
            }

            // Vehicle with outline
            if (line != null && vehicle.showOutline())
                line.addInstance(mInstance);
        }

        // Add the mesh layer
        _meshLayer.setMeshes(opaque, transparent);
        toRender.add(_meshLayer);

        return toRender;
    }

    private synchronized void releaseMeshInstances(String uri) {
        List<GLInstancedMesh> meshes = _meshInstances.remove(uri);
        if (FileSystemUtils.isEmpty(meshes))
            return;

        for (GLInstancedMesh mesh : meshes)
            mesh.flagRelease();

        GLInstancedPolyline line = _lineInstances.remove(uri);
        if (line != null)
            line.flagRelease();
    }

    @Override
    public void onLoadStateChanged(AbstractSheet sheet, LoadState ls) {
        // Model has changed - need to refresh mesh instances
        if (USE_INSTANCES)
            invalidate();
    }

    @Override
    public void onLoadProgress(AbstractSheet sheet, int progress) {
    }

    @Override
    public void onMetadataChanged(MapItem item, final String field) {
        if (USE_INSTANCES && field.equals("outline"))
            invalidate();
    }

    @Override
    public void onAlphaChanged(AbstractSheet sheet, int alpha) {
        if (USE_INSTANCES)
            invalidate();
    }

    /**
     * Meta-renderable that allows us to sort the draw order of each instanced
     * mesh based on the current view
     */
    private static class GLMeshLayer implements GLMapRenderable2 {

        private final List<GLInstancedMesh> _opaque = new ArrayList<>();
        private final List<GLVehicleModel> _transparent = new ArrayList<>();

        public synchronized void setMeshes(List<GLInstancedMesh> opaque,
                List<GLVehicleModel> transparent) {
            synchronized (this) {
                _opaque.clear();
                _opaque.addAll(opaque);
                _transparent.clear();
                _transparent.addAll(transparent);
            }
        }

        @Override
        public void draw(GLMapView view, int renderPass) {
            if (!MathUtils.hasBits(renderPass, getRenderPass()))
                return;

            List<GLInstancedMesh> opaque;
            List<GLVehicleModel> transparent;
            synchronized (this) {
                opaque = new ArrayList<>(_opaque);
                transparent = new ArrayList<>(_transparent);
            }

            // First draw all the opaque mesh instances, regardless of z-order
            for (GLInstancedMesh mesh : opaque)
                mesh.draw(view, renderPass);

            // Update forwards for transparent models
            for (GLVehicleModel model : transparent)
                model.updateDrawVersion(view);

            // Draw transparent models in ascending z-order
            Collections.sort(transparent, GLVehicleModel.SORT_Z);
            for (GLVehicleModel model : transparent)
                model.draw(view, renderPass);
        }

        @Override
        public synchronized void release() {
            _opaque.clear();
            _transparent.clear();
        }

        @Override
        public int getRenderPass() {
            return GLMapView.RENDER_PASS_SCENES;
        }
    }
}
