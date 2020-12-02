
package com.atakmap.android.rubbersheet.maps;

import android.util.LongSparseArray;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.rubbersheet.tool.RubberModelEditTool;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.opengl.GLAsynchronousLayer2;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Render layer for rubber models
 */
public class GLRubberModelLayer extends
        GLAsynchronousLayer2<Collection<GLMapRenderable2>>
        implements MapGroup.OnItemListChangedListener,
        GLMapItem2.OnVisibleChangedListener,
        GLMapItem2.OnBoundsChangedListener {

    private final static String TAG = "GLRubberModelLayer";

    private final RubberModelLayer _modelLayer;
    private final MapGroup _group;
    private final LongSparseArray<GLRubberModel> _modelList = new LongSparseArray<>();
    private final List<GLMapRenderable2> _drawList = new ArrayList<>();
    private final MutableGeoBounds _scratchBounds = new MutableGeoBounds(0, 0,
            0, 0);

    public GLRubberModelLayer(MapRenderer surface, RubberModelLayer subject,
            MapGroup group) {
        super(surface, subject);
        _modelLayer = subject;
        _group = group;
    }

    @Override
    public void start() {
        super.start();
        for (MapItem item : _group.getItems()) {
            if (item instanceof RubberModel)
                register((RubberModel) item);
        }
        _group.addOnItemListChangedListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        _group.removeOnItemListChangedListener(this);
        _drawList.clear();
        for (MapItem item : _group.getItems()) {
            if (item instanceof RubberModel)
                unregister((RubberModel) item);
        }
        _modelList.clear();
    }

    @Override
    protected Collection<? extends GLMapRenderable2> getRenderList() {
        return _drawList;
    }

    @Override
    protected void resetPendingData(Collection<GLMapRenderable2> pendingData) {
        pendingData.clear();
    }

    @Override
    protected void releasePendingData(
            Collection<GLMapRenderable2> pendingData) {
        pendingData.clear();
    }

    @Override
    protected Collection<GLMapRenderable2> createPendingData() {
        return new LinkedList<>();
    }

    @Override
    protected boolean updateRenderList(ViewState state,
            Collection<GLMapRenderable2> pendingData) {
        _drawList.clear();
        _drawList.addAll(pendingData);
        return true;
    }

    @Override
    protected void query(ViewState state, Collection<GLMapRenderable2> result) {
        // Determine which models to draw
        MapView mv = MapView.getMapView();
        Tool tool = ToolManagerBroadcastReceiver.getInstance().getActiveTool();
        PointMapItem focus = null;
        if (mv != null) {
            if (tool instanceof RubberModelEditTool)
                focus = ((RubberModelEditTool) tool).getMarker();
            else
                focus = mv.getMapTouchController().getFreeForm3DItem();
        }
        for (int i = 0; i < _modelList.size(); i++) {
            GLRubberModel glMDL = _modelList.valueAt(i);
            RubberModel mdl = (RubberModel) glMDL.getSubject();

            // Model is invisible
            if (!glMDL.isVisible())
                continue;

            boolean onScreen = false;

            // Free form 3D focus
            if (focus != null && focus == mdl.getAnchorItem())
                onScreen = true;

            // Check view state bounds against model render bounds
            if (!onScreen) {
                glMDL.getBounds(_scratchBounds);
                onScreen = _scratchBounds.intersects(state.northBound,
                        state.westBound, state.southBound, state.eastBound,
                        state.crossesIDL);
            }

            if (onScreen)
                result.add(glMDL);
            glMDL.setOnScreen(onScreen);
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SCENES;
    }

    protected void register(RubberModel mdl) {
        long id = mdl.getSerialId();
        if (_modelList.get(id) == null) {
            GLRubberModel glMDL = createGLModel(mdl);
            glMDL.addVisibleListener(this);
            glMDL.addBoundsListener(this);
            _modelList.put(id, glMDL);
        }
        invalidate();
    }

    protected void unregister(RubberModel mdl) {
        long id = mdl.getSerialId();
        GLRubberModel glMDL = _modelList.get(id);
        if (glMDL != null) {
            _modelList.remove(mdl.getSerialId());
            glMDL.removeVisibleListener(this);
            glMDL.removeBoundsListener(this);
            glMDL.release();
        }
        if (!mdl.isSharedModel()) {
            Model model = mdl.getModel();
            if (model != null)
                model.dispose();
        }
        invalidate();
    }

    protected GLRubberModel createGLModel(RubberModel mdl) {
        return new GLRubberModel(renderContext, mdl);
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        if (item instanceof RubberModel) {
            final RubberModel mdl = (RubberModel) item;
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    register(mdl);
                }
            });
        }
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item instanceof RubberModel) {
            final RubberModel mdl = (RubberModel) item;
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    unregister(mdl);
                }
            });
        }
    }

    @Override
    public void onVisibleChanged(GLMapItem2 item, boolean visible) {
        invalidate();
    }

    @Override
    public void onBoundsChanged(GLMapItem2 item, GeoBounds bounds) {
        invalidate();
    }
}
