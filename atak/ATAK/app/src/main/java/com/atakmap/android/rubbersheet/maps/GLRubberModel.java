
package com.atakmap.android.rubbersheet.maps;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.maps.tilesets.graphics.GLPendingTexture;
import com.atakmap.android.model.opengl.GLModelRenderer2.TextureLoader;
import com.atakmap.android.rubbersheet.data.RubberSheetManager;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.opengl.GLMesh;
import com.atakmap.map.layer.model.opengl.MaterialManager;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;

import java.io.File;

public class GLRubberModel extends AbstractGLMapItem2 implements
        Shape.OnPointsChangedListener, AbstractSheet.OnAlphaChangedListener,
        AbstractSheet.OnLoadListener, RubberModel.OnChangedListener {

    private static final String TAG = "GLRubberModel";
    private static final int MIN_RENDER_PX = 32;

    private final MapRenderer _renderCtx;
    private final RubberModel _subject;

    // Alpha transparency
    private float _alpha;

    // Model parameters
    private Model _model;
    private ModelInfo _modelInfo;

    // Used to transform to model to map coordinates
    private Matrix _matrix = Matrix.getIdentity();

    // Rendering anchor point
    private final GeoPoint _anchorPoint = GeoPoint.createMutable();
    private PointD _modelAnchorPoint = new PointD(0, 0, 0);

    // Scaled dimensions of the model in meters [width, length, height]
    private final double[] _modelDim = new double[3];

    // Used for tracking draw updates
    private int _drawVersion;

    // Model is loaded and ready to render
    private boolean _readyToRender;

    // Model is on screen and should be rendererd
    private boolean _onScreen;

    // No level of detail - model is too small to bother rendering
    private boolean _noLod;

    // Meshes
    private GLMesh[] _glMeshes;
    private boolean _meshesLocked;

    // Mesh textures
    private GLPendingTexture _textureLoader;
    private GLTexture _texture;
    private MaterialManager _matManager;

    public GLRubberModel(MapRenderer ctx, RubberModel subject) {
        super(ctx, subject, GLMapView.RENDER_PASS_SCENES);
        _renderCtx = ctx;
        _subject = subject;
        _alpha = subject.getAlpha() / 255f;
        _model = subject.getModel();
        _modelInfo = subject.getInfo();
        bounds.set(subject.getPoints());

        File texDir = new File(RubberSheetManager.DIR, _subject.getUID());
        _matManager = new MaterialManager(_renderCtx,
                new TextureLoader(_renderCtx, texDir, 4096));
        startObserving();
    }

    @Override
    public void startObserving() {
        super.startObserving();
        _subject.addOnPointsChangedListener(this);
        _subject.addOnAlphaChangedListener(this);
        _subject.addChangeListener(this);
        _subject.addLoadListener(this);
        _subject.setRenderer(this);
        refresh();
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.setRenderer(null);
        _subject.removeOnPointsChangedListener(this);
        _subject.removeOnAlphaChangedListener(this);
        _subject.removeChangeListener(this);
        _subject.removeLoadListener(this);
    }

    @Override
    public void onAlphaChanged(AbstractSheet sheet, int alpha) {
        _alpha = alpha / 255f;
        _renderCtx.requestRefresh();
    }

    @Override
    public void onLoadStateChanged(AbstractSheet sheet, LoadState ls) {
        if (ls == LoadState.SUCCESS) {
            _model = _subject.getModel();
            _modelInfo = _subject.getInfo();
            refresh();
        } else
            _renderCtx.requestRefresh();
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
        _renderCtx.queueEvent(new Runnable() {
            @Override
            public void run() {
                System.arraycopy(dim, 0, _modelDim, 0, 3);
                if (_modelInfo.localFrame == null)
                    _modelInfo.localFrame = Matrix.getIdentity();
                _modelInfo.localFrame.set(_matrix);
                _anchorPoint.set(fAnchor);
                _readyToRender = true;
                _drawVersion = -1;
                refreshLocalFrame();
            }
        });
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!shouldRender() || !MathUtils.hasBits(renderPass, getRenderPass()))
            return;

        // Update map forwards
        if (_drawVersion != view.drawVersion) {
            onDrawVersionChanged(view);
            _drawVersion = view.drawVersion;
        }

        // Only draw model if we have some decent level of detail
        if (!_noLod)
            drawModel(view);
    }

    private boolean shouldRender() {
        return _readyToRender && _onScreen && _modelInfo != null
                && _model != null;
    }

    private void onDrawVersionChanged(GLMapView view) {
        // Update the model anchor point
        forward(view, _anchorPoint, _modelAnchorPoint);

        // Check if it's worth rendering the model from our current view
        float minRenderPx = MIN_RENDER_PX * AtakMapView.DENSITY;
        _noLod = _modelDim[0] / view.drawMapResolution < minRenderPx
                || _modelDim[1] / view.drawMapResolution < minRenderPx;
    }

    private synchronized boolean drawModel(GLMapView view) {
        if (_glMeshes == null) {
            _glMeshes = new GLMesh[_model.getNumMeshes()];
            for (int i = 0; i < _model.getNumMeshes(); ++i) {
                Mesh mesh = _model.getMesh(i);
                GLMesh glMesh = new GLMesh(_modelInfo, mesh,
                        _modelAnchorPoint, _matManager);
                _glMeshes[i] = glMesh;
            }
        }

        GLES20FixedPipeline.glPushAlphaMod(_alpha);
        for (GLMesh mesh : _glMeshes)
            mesh.draw(view, this.getRenderPass());
        GLES20FixedPipeline.glPopAlphaMod();

        return true;
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

    @Override
    public synchronized void release() {
        stopObserving();
        if (_glMeshes != null) {
            // if the meshes aren't locked release, otherwise they'll be
            // released when unlocked
            if (!_meshesLocked) {
                for (GLMesh mesh : _glMeshes)
                    mesh.release();
            }
            _glMeshes = null;
        }
        _matManager.dispose();
        if (_model != null) {
            // XXX - disposal is a carryover from original prototype API,
            //       individual mesh dispose handles
            _model = null;
            _modelInfo = null;
        }

        if (_textureLoader != null) {
            _textureLoader.cancel();
            _textureLoader = null;
        }
        if (_texture != null) {
            _texture.release();
            _texture = null;
        }
    }

    /**
     * Flag the model as being onscreen according to the last layer query
     * @param onScreen True if the model is on screen
     */
    public void setOnScreen(boolean onScreen) {
        _onScreen = onScreen;
    }

    public boolean hitTest(int xpos, int ypos, GeoPoint result, MapView view) {

        if (_noLod || !shouldRender())
            return false;

        final GLMesh[] meshes;
        final MapSceneModel sm = view.getSceneModel();
        synchronized (this) {
            if (_glMeshes == null)
                return false;
            meshes = _glMeshes;
            _meshesLocked = true;
        }

        boolean retval = false;

        for (GLMesh mesh : meshes) {
            // XXX - need to find closest hit
            if (mesh.hitTest(sm, xpos, ypos, result)) {
                retval = true;
                break;
            }
        }

        synchronized (this) {
            if (meshes != _glMeshes) {
                _renderCtx.queueEvent(new Runnable() {
                    public void run() {
                        for (GLMesh mesh : meshes)
                            mesh.release();
                    }
                });
            }
            _meshesLocked = false;
        }
        return retval;
    }
}
