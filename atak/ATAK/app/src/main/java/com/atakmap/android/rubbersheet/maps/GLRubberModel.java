
package com.atakmap.android.rubbersheet.maps;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.model.opengl.GLModelRenderer2.TextureLoader;
import com.atakmap.android.rubbersheet.data.RubberSheetManager;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.opengl.GLMesh;
import com.atakmap.map.layer.model.opengl.MaterialManager;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.io.File;

public class GLRubberModel extends AbstractGLMapItem2 implements
        Shape.OnPointsChangedListener, AbstractSheet.OnAlphaChangedListener,
        AbstractSheet.OnLoadListener, RubberModel.OnChangedListener,
        Shape.OnStrokeColorChangedListener {

    private static final String TAG = "GLRubberModel";
    private static final int MIN_RENDER_PX = 32;

    protected final MapRenderer _renderCtx;
    protected final RubberModel _subject;

    // Color and transparency
    protected int _color;
    protected float _alpha;
    protected ColorControl[] _ctrl;

    // Model parameters
    protected Model _model;
    protected ModelInfo _modelInfo;

    // Used to transform to model to map coordinates
    protected final Matrix _matrix = Matrix.getIdentity();

    // Rendering anchor point
    protected final GeoPoint _anchorPoint = GeoPoint.createMutable();
    protected PointD _modelAnchorPoint = new PointD(0, 0, 0);

    // Scaled dimensions of the model in meters [width, length, height]
    protected final double[] _modelDim = new double[3];

    // Used for tracking draw updates
    protected int _drawVersion;

    // Model is loaded and ready to render
    protected boolean _readyToRender;

    // Model is on screen and should be rendererd
    protected boolean _onScreen;

    // No level of detail - model is too small to bother rendering
    protected boolean _noLod;

    // Meshes
    protected GLMesh[] _glMeshes;
    protected boolean _meshesLocked;

    // Mesh textures
    protected MaterialManager _matManager;

    // Subject was released
    protected boolean _released;

    // Last scene used to render the model (used for hit testing)
    protected MapSceneModel _scene;

    public GLRubberModel(MapRenderer ctx, RubberModel subject) {
        super(ctx, subject, GLMapView.RENDER_PASS_SCENES);
        _renderCtx = ctx;
        _subject = subject;
        _color = subject.getStrokeColor();
        _alpha = subject.getAlpha() / 255f;
        _model = subject.getModel();
        _modelInfo = subject.getInfo();
        bounds.set(subject.getPoints());

        File texDir = new File(RubberSheetManager.DIR, _subject.getUID());
        _matManager = new MaterialManager(_renderCtx,
                new TextureLoader(_renderCtx, texDir, 4096));
    }

    @Override
    public void startObserving() {
        super.startObserving();
        _subject.addOnStrokeColorChangedListener(this);
        _subject.addOnAlphaChangedListener(this);
        _subject.addOnPointsChangedListener(this);
        _subject.addChangeListener(this);
        _subject.addLoadListener(this);
        _subject.setRenderer(this);
        refresh();
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.setRenderer(null);
        _subject.removeOnStrokeColorChangedListener(this);
        _subject.removeOnAlphaChangedListener(this);
        _subject.removeOnPointsChangedListener(this);
        _subject.removeChangeListener(this);
        _subject.removeLoadListener(this);
    }

    @Override
    public void onAlphaChanged(AbstractSheet sheet, int alpha) {
        _alpha = alpha / 255f;
        requestRefresh();
    }

    @Override
    public void onStrokeColorChanged(Shape s) {
        _color = (s.getStrokeColor() & 0xFFFFFF) + 0xFF000000;
        requestRefresh();
    }

    @Override
    public void onLoadStateChanged(AbstractSheet sheet, LoadState ls) {
        if (ls == LoadState.SUCCESS) {
            _renderCtx.queueEvent(new Runnable() {
                @Override
                public void run() {
                    if (_released)
                        return;
                    _model = _subject.getModel();
                    _modelInfo = _subject.getInfo();
                    releaseMesh();
                    refresh();
                }
            });
        } else
            requestRefresh();
    }

    @Override
    public void onLoadProgress(AbstractSheet sheet, int progress) {
    }

    @Override
    public void onPointsChanged(Shape shape) {
        bounds.set(shape.getPoints());
        dispatchOnBoundsChanged();
        refresh();
    }

    @Override
    public void onRotationChanged(RubberModel model, double[] rotation) {
        refresh();
    }

    @Override
    public void onAltitudeChanged(RubberModel model, double altitude,
            GeoPoint.AltitudeReference reference) {
        refresh();
    }

    protected void requestRefresh() {
        _renderCtx.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (_released)
                    return;
                onRefresh();
            }
        });
    }

    private void refresh() {
        if (_modelInfo == null || _model == null)
            return;

        // Translate the center by the model offset in meters
        GeoPoint center = _subject.getCenterPoint();
        double[] scale = _subject.getModelScale();
        final double[] dim = _subject.getModelDimensions(true);

        PointD p = new PointD(0d, 0d, 0d);
        ProjectionFactory.getProjection(_modelInfo.srid).forward(center, p);

        // Clear local matrix
        _matrix.setToIdentity();

        // Models require the following transformation order:
        // translate -> scale -> rotate

        // Translate to lat/lon position
        _matrix.translate(p.x, p.y, p.z);

        // Meters -> lat/lon
        double metersLat = GeoCalculations.approximateMetersPerDegreeLatitude(
                center.getLatitude());
        double metersLng = GeoCalculations.approximateMetersPerDegreeLongitude(
                center.getLatitude());
        scale[0] *= 1d / metersLng;
        scale[1] *= 1d / metersLat;

        // Apply scale
        _matrix.scale(scale[0], scale[1], scale[2]);

        //XXX-- use quaternion to avoid gimbal lock
        double[] r = _subject.getModelRotation();
        _matrix.rotate(Math.toRadians(r[0]), 1.0f, 0.0f, 0.0f);
        _matrix.rotate(Math.toRadians(360d - r[1]), 0.0f, 0.0f, 1.0f);
        _matrix.rotate(Math.toRadians(r[2]), 0.0f, 1.0f, 0.0f);

        // Apply matrix to model
        final GeoPoint fAnchor = center;
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_released)
                    return;
                System.arraycopy(dim, 0, _modelDim, 0, 3);
                if (_modelInfo.localFrame == null)
                    _modelInfo.localFrame = Matrix.getIdentity();
                _modelInfo.localFrame.set(_matrix);
                _anchorPoint.set(fAnchor);
                _readyToRender = true;
                _drawVersion = -1;
                refreshLocalFrame();
                onRefresh();
            }
        });
    }

    protected void onRefresh() {
        // Sub-classes can update stuff here
    }

    public void draw(GLMapView view, int renderPass) {
        if (!MathUtils.hasBits(renderPass, getRenderPass()))
            return;

        MapSceneModel scene = null;
        try {
            // Do not render
            if (!shouldRender())
                return;

            // Update map forwards
            updateDrawVersion(view);

            // Only draw model if we have some decent level of detail
            if (!_noLod && drawModel(view))
                scene = view.currentScene.scene;
        } finally {
            // Update scene used for hit-testing
            _scene = scene;
        }
    }

    protected boolean shouldRender() {
        return _readyToRender && _onScreen && _modelInfo != null
                && _model != null && _alpha > 0.0f;
    }

    protected void onDrawVersionChanged(GLMapView view) {
        forward(view, _anchorPoint, _modelAnchorPoint);

        // Check if it's worth rendering the model from our current view
        _noLod = _modelDim[0]
                / view.currentPass.drawMapResolution < MIN_RENDER_PX
                || _modelDim[1]
                        / view.currentPass.drawMapResolution < MIN_RENDER_PX;
    }

    public void updateDrawVersion(GLMapView view) {
        if (_drawVersion != view.currentPass.drawVersion) {
            onDrawVersionChanged(view);
            _drawVersion = view.currentScene.drawVersion;
        }
    }

    private synchronized boolean drawModel(GLMapView view) {
        if (_glMeshes == null)
            _glMeshes = createGLMeshes();

        if (_ctrl == null) {
            _ctrl = new ColorControl[_glMeshes.length];
            for (int i = 0; i < _ctrl.length; i++)
                _ctrl[i] = _glMeshes[i].getControl(ColorControl.class);
        }

        for (ColorControl col : _ctrl)
            col.setColor(_color);

        GLES20FixedPipeline.glPushAlphaMod(_alpha);
        for (GLMesh mesh : _glMeshes)
            mesh.draw(view, this.getRenderPass());
        GLES20FixedPipeline.glPopAlphaMod();

        return true;
    }

    protected GLMesh[] createGLMeshes() {
        GLMesh[] meshes = new GLMesh[_model.getNumMeshes()];
        for (int i = 0; i < _model.getNumMeshes(); ++i) {
            Mesh mesh = _model.getMesh(i, false);
            GLMesh glMesh = new GLMesh(_modelInfo, mesh,
                    _modelAnchorPoint, _matManager);
            glMesh.setDisposeMesh(!_subject.isSharedModel());
            meshes[i] = glMesh;
        }
        return meshes;
    }

    /**
     * The model's local frame matrix has been modified - notify meshes
     * so they may reset any vars which depend on this
     */
    private synchronized void refreshLocalFrame() {
        if (_glMeshes != null) {
            for (GLMesh mesh : _glMeshes)
                mesh.refreshLocalFrame();
        }
    }

    private synchronized void releaseMesh() {
        if (_glMeshes != null) {
            // if the meshes aren't locked release, otherwise they'll be
            // released when unlocked
            if (!_meshesLocked) {
                for (GLMesh mesh : _glMeshes)
                    mesh.release();
            }
            _glMeshes = null;
        }
    }

    @Override
    public synchronized void release() {
        _released = true;
        stopObserving();
        releaseMesh();
        _matManager.dispose();
        if (_model != null) {
            // XXX - disposal is a carryover from original prototype API,
            //       individual mesh dispose handles
            _model = null;
            _modelInfo = null;
        }
    }

    /**
     * Flag the model as being onscreen according to the last layer query
     * @param onScreen True if the model is on screen
     */
    public void setOnScreen(boolean onScreen) {
        _onScreen = onScreen;
    }

    @Override
    protected boolean getClickable() {
        return super.getClickable() && !_noLod && shouldRender();
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {

        // Model isn't being rendered
        final MapSceneModel scene = _scene;
        if (scene == null)
            return null;

        final GLMesh[] meshes;
        synchronized (this) {
            if (_glMeshes == null)
                return null;
            meshes = _glMeshes;
            _meshesLocked = true;
        }

        HitTestResult retval = null;

        GeoPoint geo = GeoPoint.createMutable();
        for (GLMesh mesh : meshes) {
            // XXX - need to find closest hit
            if (mesh.hitTest(scene, params.point.x, params.point.y, geo)) {
                retval = new HitTestResult(_subject, geo);
                break;
            }
        }

        synchronized (this) {
            if (meshes != _glMeshes) {
                for (GLMesh mesh : meshes)
                    mesh.release();
            }
            _meshesLocked = false;
        }
        return retval;
    }

    /**
     * @deprecated Replaced by {@link AbstractGLMapItem2#hitTest(MapRenderer3, HitTestQueryParameters)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public boolean hitTest(int xpos, int ypos, GeoPoint result, MapView view) {
        return false;
    }
}
