
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.Typeface;
import android.opengl.GLES30;

import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.Association.OnClampToGroundChangedListener;
import com.atakmap.android.maps.Association.OnColorChangedListener;
import com.atakmap.android.maps.Association.OnFirstItemChangedListener;
import com.atakmap.android.maps.Association.OnLinkChangedListener;
import com.atakmap.android.maps.Association.OnSecondItemChangedListener;
import com.atakmap.android.maps.Association.OnStrokeWeightChangedListener;
import com.atakmap.android.maps.Association.OnStyleChangedListener;
import com.atakmap.android.maps.Association.OnTextChangedListener;
import com.atakmap.android.maps.AssociationSet;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLExtrude;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.PatternStrokeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLAntiMeridianHelper;
import com.atakmap.map.opengl.GLLabelManager;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.util.Visitor;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

public class GLAssociation2 extends AbstractGLMapItem2 implements
        OnStyleChangedListener,
        OnLinkChangedListener, OnColorChangedListener,
        OnFirstItemChangedListener,
        OnSecondItemChangedListener, OnStrokeWeightChangedListener,
        OnClampToGroundChangedListener,
        OnTextChangedListener,
        MapItem.OnHeightChangedListener,
        Association.OnParentChangedListener {

    private final GeoPoint[] _points = {
            null, null
    };

    private int _link;
    private int _labelID = GLLabelManager.NO_ID;
    private String _text;
    private boolean _clampToGround;
    private double _unwrap;
    private boolean _nadirClamp;

    // Height extrusion
    private double _height;
    private boolean _hasHeight;
    private DoubleBuffer _fillPointsPreForward;
    private FloatBuffer _fillPoints;
    private final GLBatchLineString _3dOutline;
    private boolean _extrudeInvalid;
    private boolean _extrudeCrossesIDL;
    private int _extrudePrimaryHemi;
    private final GeoPoint _extrusionCentroid = GeoPoint.createMutable();
    private final PointD _extrusionCentroidProj = new PointD(0d, 0d, 0d);
    private int _extrusionCentroidSrid = -1;
    private int _extrusionTerrainVersion = -1;

    private final GLBatchLineString impl;
    private boolean implInvalid;
    private boolean labelInvalid;
    private int color = -1;
    private boolean outline = false;
    private byte pattern = (byte) 0xFF;
    private float strokeWidth = 1f;
    private AssociationSet _parent;

    private SurfaceRendererControl _surfaceCtrl;

    private final static float div_180_pi = 180f / (float) Math.PI;
    private static final float div_2 = 1f / 2f;
    private final GLLabelManager _labelManager;
    private final GLMapView _glMapView;
    private Envelope _geomBounds;

    public GLAssociation2(MapRenderer surface, Association subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES
                | GLMapView.RENDER_PASS_SURFACE
                | GLMapView.RENDER_PASS_SCENES);

        _glMapView = (GLMapView) surface;
        _labelManager = _glMapView.getLabelManager();

        impl = new GLBatchLineString(surface);

        _3dOutline = new GLBatchLineString(surface);
        _3dOutline.setTessellationEnabled(false);
        _3dOutline.setAltitudeMode(Feature.AltitudeMode.Absolute);

        if (surface instanceof MapRenderer3)
            _surfaceCtrl = ((MapRenderer3) surface)
                    .getControl(SurfaceRendererControl.class);
        else
            surface.visitControl(null, new Visitor<SurfaceRendererControl>() {
                @Override
                public void visit(SurfaceRendererControl object) {
                    _surfaceCtrl = object;
                }
            }, SurfaceRendererControl.class);

        _link = subject.getLink();
        _clampToGround = subject.getClampToGround();
        setHeight(subject.getHeight());

        final PointMapItem i1 = subject.getFirstItem();
        if (i1 != null)
            _setEndPoint(0, i1.getPoint(), true);
        final PointMapItem i2 = subject.getSecondItem();
        if (i2 != null)
            _setEndPoint(1, i2.getPoint(), true);
    }

    @Override
    public void startObserving() {
        final Association association = (Association) this.subject;
        super.startObserving();
        association.addOnStyleChangedListener(this);
        association.addOnLinkChangedListener(this);
        association.addOnColorChangedListener(this);
        association.addOnFirstItemChangedListener(this);
        association.addOnSecondItemChangedListener(this);
        association.addOnStrokeWeightChangedListener(this);
        association.addOnTextChangedListener(this);
        association.addOnHeightChangedListener(this);
        association.addOnParentChangedListener(this);

        final PointMapItem firstItem = association.getFirstItem();
        if (firstItem != null) {
            firstItem.addOnPointChangedListener(_firstItemMovedListener);
        }
        final PointMapItem secondItem = association.getSecondItem();
        if (secondItem != null) {
            secondItem.addOnPointChangedListener(_secondItemMovedListener);
        }

        // sync 'impl' with 'subject'
        onAssociationStyleChanged(association);
        onAssociationColorChanged(association);
        onAssociationStrokeWeightChanged(association);
        onAssociationTextChanged(association);
        _setEndPoint(0, _points[0], true);
    }

    @Override
    public void stopObserving() {
        final Association association = (Association) this.subject;
        super.stopObserving();
        removeLabel();
        association.removeOnStyleChangedListener(this);
        association.removeOnLinkChangedListener(this);
        association.removeOnColorChangedListener(this);
        association.removeOnSecondItemChangedListner(this);
        final PointMapItem firstItem = association.getFirstItem();
        if (firstItem != null) {
            firstItem.removeOnPointChangedListener(_firstItemMovedListener);
        }
        final PointMapItem secondItem = association.getSecondItem();
        if (secondItem != null) {
            secondItem.removeOnPointChangedListener(_secondItemMovedListener);
        }
        association.removeOnSecondItemChangedListner(this);
        association.removeOnStrokeWeightChangedListener(this);
        association.removeOnTextChangedListener(this);
        association.removeOnHeightChangedListener(this);
        association.removeOnParentChangedListener(this);
    }

    private void refreshStyle() {
        final Style[] update = {
                null
        };
        if ((pattern & 0xFF) != 0xFF) {
            update[0] = new PatternStrokeStyle(pattern & 0xFF, 8, this.color,
                    strokeWidth);
        } else {
            update[0] = new BasicStrokeStyle(color, strokeWidth);
            if (outline) {
                BasicStrokeStyle bg = new BasicStrokeStyle(
                        0xFF000000 & this.color, strokeWidth + 2f);
                update[0] = new CompositeStyle(new Style[] {
                        bg, update[0]
                });
            }
        }
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                impl.setStyle(update[0]);
                _3dOutline.setStyle(update[0]);
                if (_surfaceCtrl != null &&
                        _points != null &&
                        _points[0] != null &&
                        _points[1] != null) {

                    Envelope.Builder eb = new Envelope.Builder();
                    eb.add(_points[0].getLongitude(), _points[0].getLatitude());
                    eb.add(_points[1].getLongitude(), _points[1].getLatitude());
                    _surfaceCtrl.markDirty(eb.build(), true);
                }
            }
        });
    }

    private void removeLabel() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (_labelID != GLLabelManager.NO_ID) {
                    _labelManager.removeLabel(_labelID);
                    _labelID = GLLabelManager.NO_ID;
                }
                labelInvalid = true;
            }
        });
    }

    private boolean ensureLabel() {
        if (_labelID == GLLabelManager.NO_ID) {
            MapTextFormat mtf = MapView.getTextFormat(
                    Typeface.DEFAULT, +2);
            _labelID = _labelManager.addLabel();
            _labelManager.setTextFormat(_labelID,
                    null,
                    mtf.getFontSize(),
                    mtf.getTypeface().isBold(),
                    mtf.getTypeface().isItalic(),
                    false,
                    false);
            _labelManager.setVerticalAlignment(_labelID,
                    GLLabelManager.VerticalAlignment.Middle);
            _labelManager.setFill(_labelID, true);
            _labelManager.setBackgroundColor(_labelID,
                    Color.argb(204 /*=80%*/, 0, 0, 0));
            return true;
        }
        return false;
    }

    @Override
    public void onAssociationLinkChanged(final Association association) {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _link = association.getLink();
            }
        });
    }

    @Override
    public void onAssociationStyleChanged(final Association association) {
        int style = association.getStyle();
        switch (style) {
            case Association.STYLE_DASHED:
                pattern = (byte) 0x3F;
                break;
            case Association.STYLE_DOTTED:
                pattern = (byte) 0x03;
                break;
            case Association.STYLE_SOLID:
                outline = false;
                pattern = (byte) 0xFF;
                break;
            case Association.STYLE_OUTLINED:
                outline = true;
                pattern = (byte) 0xFF;
                break;

        }

        refreshStyle();
    }

    @Override
    public void onAssociationColorChanged(final Association association) {
        color = association.getColor();
        refreshStyle();
    }

    @Override
    public void onSecondAssociationItemChanged(Association association,
            PointMapItem prevItem) {
        if (prevItem != null)
            prevItem.removeOnPointChangedListener(_secondItemMovedListener);

        final PointMapItem secondItem = association.getSecondItem();
        if (secondItem != null) {
            secondItem.addOnPointChangedListener(_secondItemMovedListener);
        }
        _updateItemPoint(1, secondItem);
    }

    private void _updateItemPoint(final int index,
            final PointMapItem pointItem) {
        final GeoPoint point = (pointItem != null) ? pointItem.getPoint()
                : null;
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _setEndPoint(index, point, true);
            }
        });
    }

    private void setHeight(double height) {
        _height = height;
        _hasHeight = Double.compare(_height, 0) != 0
                && !Double.isNaN(_height);
        if (!_hasHeight) {
            // Free unused buffers
            freeExtrusionBuffers();
        }
        implInvalid = labelInvalid = _extrudeInvalid = true;

        updateBoundsZ();
        dispatchOnBoundsChanged();
    }

    @Override
    public void onFirstAssociationItemChanged(Association association,
            PointMapItem prevItem) {
        prevItem.removeOnPointChangedListener(_firstItemMovedListener);
        PointMapItem firstItem = association.getFirstItem();
        if (firstItem != null) {
            firstItem.addOnPointChangedListener(_firstItemMovedListener);
        }
        _updateItemPoint(0, firstItem);
    }

    @Override
    public void onAssociationStrokeWeightChanged(Association association) {
        strokeWidth = (float) association.getStrokeWeight();
        refreshStyle();
    }

    @Override
    public void onAssociationTextChanged(Association assoc) {
        final String text = assoc.getText();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _text = text;
                if (_labelID != GLLabelManager.NO_ID)
                    _labelManager.setText(_labelID, _text);
                else
                    labelInvalid = true; // mark label dirty
            }
        });
    }

    @Override
    public void onAssociationClampToGroundChanged(Association assoc) {
        final boolean clampToGround = assoc.getClampToGround();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _clampToGround = clampToGround;
                implInvalid = true;
                labelInvalid = true;

                updateBoundsZ();
                dispatchOnBoundsChanged();
            }
        });
    }

    @Override
    public void onHeightChanged(MapItem item) {
        final double height = item.getHeight();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                setHeight(height);
            }
        });
    }

    @Override
    public void onParentChanged(Association assoc,
            final AssociationSet parent) {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                _parent = parent;
            }
        });
    }

    @Override
    public void release() {
        super.release();
        if (_labelID != GLLabelManager.NO_ID) {
            _labelManager.removeLabel(_labelID);
            _labelID = GLLabelManager.NO_ID;
        }
        labelInvalid = true;
        impl.release();
        implInvalid = true;
        _extrudeInvalid = true;

        freeExtrusionBuffers();
    }

    private void freeExtrusionBuffers() {
        Unsafe.free(_fillPointsPreForward);
        _fillPointsPreForward = null;

        Unsafe.free(_fillPoints);
        _fillPoints = null;

        _3dOutline.release();
    }

    /**
     * Sync the NADIR clamp boolean with the current clamp to ground setting
     * @param ortho Map view
     */
    protected void updateNadirClamp(GLMapView ortho) {
        boolean nadirClamp = getClampToGroundAtNadir()
                && Double.compare(ortho.currentPass.drawTilt, 0) == 0;
        if (_nadirClamp != nadirClamp) {
            _nadirClamp = nadirClamp;
            implInvalid = _extrudeInvalid = labelInvalid = true;
        }
    }

    protected boolean clampToGround() {
        return _nadirClamp || _clampToGround;
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        boolean sprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);
        boolean surface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);
        boolean scenes = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SCENES);

        if (!surface)
            updateNadirClamp(ortho);

        boolean clampToGround = clampToGround();

        this.bounds.setWrap180(ortho.continuousScrollEnabled);
        _unwrap = ortho.idlHelper.getUnwrap(this.bounds);

        if (_points[0] != null &&
                _points[1] != null &&
                _link != Association.LINK_NONE) {

            if (labelInvalid || _labelID == GLLabelManager.NO_ID) {
                // if label is newly created, reassign geometry
                implInvalid |= ensureLabel();

                _labelManager.setText(_labelID, _text);
                labelInvalid = false;
            }
            if (implInvalid) {
                _setEndPoint(0, _points[0], false);
                labelInvalid = true;
            }

            // delegate drawing of the link to GLPolyline
            if (surface && clampToGround || sprites && !clampToGround)
                impl.draw(ortho);

            if (scenes && _hasHeight && !_nadirClamp) {

                final int terrainVersion = ortho.getTerrainVersion();
                _extrudeInvalid |= (_extrusionTerrainVersion != terrainVersion);

                float a = 0f;

                // Fill based on parent alpha - disabled for now
                /*if (_parent != null && MathUtils.hasBits(_parent.getStyle(),
                        Shape.STYLE_FILLED_MASK | Polyline.STYLE_CLOSED_MASK))
                    a = Color.alpha(_parent.getFillColor()) / 255f;*/

                if (_extrudeInvalid) {
                    _extrusionTerrainVersion = terrainVersion;
                    _extrusionCentroidSrid = -1;

                    // Find min/max altitude
                    double minAlt = Double.MAX_VALUE;
                    double maxAlt = -Double.MAX_VALUE;
                    GeoPoint[] points = _points;
                    double[] alts = new double[points.length];
                    for (int i = 0; i < points.length; i++) {
                        GeoPoint gp = points[i];
                        double alt = gp.getAltitude();
                        if (clampToGround || !gp.isAltitudeValid())
                            alt = ortho.getTerrainMeshElevation(
                                    gp.getLatitude(),
                                    gp.getLongitude());
                        minAlt = Math.min(alt, minAlt);
                        maxAlt = Math.max(alt, maxAlt);
                        alts[i] = alt;
                    }

                    if (_parent != null) {
                        GeoPoint[] parentPoints = _parent.getPoints();
                        int parLen = parentPoints.length;
                        if (_parent instanceof Rectangle)
                            parLen = 4; // XXX - HACK
                        for (int i = 0; i < parLen; i++) {
                            GeoPoint gp = parentPoints[i];
                            double alt = gp.getAltitude();
                            if (clampToGround || !gp.isAltitudeValid())
                                alt = ortho.getTerrainMeshElevation(
                                        gp.getLatitude(),
                                        gp.getLongitude());
                            minAlt = Math.min(alt, minAlt);
                            maxAlt = Math.max(alt, maxAlt);
                        }
                    }

                    // Center altitude is meant to be (min + max) / 2 based on
                    // how KMLs render relative height
                    double centerAlt = (maxAlt + minAlt) / 2;

                    //int extrudeMode = getExtrudeMode();
                    int extrudeMode = Polyline.HEIGHT_EXTRUDE_CENTER_ALT;
                    int extOptions = GLExtrude.OPTION_TOP_ONLY
                            | GLExtrude.OPTION_SIMPLIFIED_OUTLINE;
                    double height = _height;
                    double baseAltitude = minAlt;

                    if (extrudeMode == Polyline.HEIGHT_EXTRUDE_MAX_ALT)
                        baseAltitude = maxAlt;
                    else if (extrudeMode == Polyline.HEIGHT_EXTRUDE_CENTER_ALT)
                        baseAltitude = centerAlt; // KML style

                    // Update point buffer with terrain elevations if we're clamped
                    if (clampToGround) {
                        // XXX - Dirty hack for ATAK-14494
                        // Use the lowest valid altitude value as the base of the
                        // extrusion
                        if (ortho.currentPass.drawTilt > 0) {
                            Arrays.fill(alts, GeoPoint.MIN_ACCEPTABLE_ALTITUDE);
                            if (extrudeMode == Polyline.HEIGHT_EXTRUDE_PER_POINT)
                                height += baseAltitude
                                        - GeoPoint.MIN_ACCEPTABLE_ALTITUDE;
                        }

                        // Store terrain elevation in point buffer
                        for (int i = 0; i < points.length; i++) {
                            GeoPoint gp = points[i];
                            points[i] = new GeoPoint(gp.getLatitude(),
                                    gp.getLongitude(), alts[i]);
                        }
                    }

                    // Generate height offsets to create flat top/bottom effect
                    double[] heights;
                    if (extrudeMode != Polyline.HEIGHT_EXTRUDE_PER_POINT) {
                        heights = new double[alts.length];
                        for (int i = 0; i < alts.length; i++)
                            heights[i] = (baseAltitude + height) - alts[i];
                    } else
                        heights = new double[] {
                                height
                        };

                    freeExtrusionBuffers();

                    if (a > 0) {
                        _fillPointsPreForward = GLExtrude.extrudeRelative(
                                Double.NaN, points, true, heights);

                        _fillPoints = Unsafe.allocateDirect(
                                _fillPointsPreForward.limit(),
                                FloatBuffer.class);
                        _fillPointsPreForward.rewind();

                        final int idlInfo = GLAntiMeridianHelper
                                .normalizeHemisphere(3, _fillPointsPreForward,
                                        _fillPointsPreForward);
                        _fillPointsPreForward.flip();
                        _extrudePrimaryHemi = (idlInfo
                                & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
                        _extrudeCrossesIDL = (idlInfo
                                & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;
                    }

                    // Extrude the line based on height
                    DoubleBuffer extruded = GLExtrude.extrudeOutline(
                            Double.NaN, points, extOptions, heights);
                    extruded.rewind();

                    // Normalize IDL crossing
                    int idlInfo = GLAntiMeridianHelper.normalizeHemisphere(3,
                            extruded, extruded);
                    extruded.flip();
                    _extrudePrimaryHemi = (idlInfo
                            & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
                    _extrudeCrossesIDL = (idlInfo
                            & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0;

                    // Copy and release buffer to regular double array
                    double[] pts = new double[extruded.limit()];
                    extruded.get(pts);
                    Unsafe.free(extruded);

                    // Convert to line string and pass to 3D outline renderer
                    LineString ls = new LineString(3);
                    ls.addPoints(pts, 0, 2, 3);
                    _3dOutline.setGeometry(ls);

                    _extrudeInvalid = false;
                }

                // extrusion vertices (fill+outline) need to be rebuilt when projection changes
                final boolean rebuildExtrusionVertices = (_extrusionCentroidSrid != ortho.currentPass.drawSrid);
                if (rebuildExtrusionVertices) {
                    ortho.currentPass.scene.mapProjection
                            .forward(_extrusionCentroid,
                                    _extrusionCentroidProj);
                    _extrusionCentroidSrid = ortho.currentPass.drawSrid;
                }

                // set up model-view matrix
                GLES20FixedPipeline
                        .glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glLoadIdentity();

                ortho.scratch.matrix.set(ortho.currentPass.scene.forward);
                // apply hemisphere shift if necessary
                final double unwrap = GLAntiMeridianHelper.getUnwrap(ortho,
                        _extrudeCrossesIDL, _extrudePrimaryHemi);
                ortho.scratch.matrix.translate(unwrap, 0d, 0d);
                // translate relative-to-center for extrusion geometry
                ortho.scratch.matrix.translate(_extrusionCentroidProj.x,
                        _extrusionCentroidProj.y, _extrusionCentroidProj.z);
                // upload model-view transform
                ortho.scratch.matrix.get(ortho.scratch.matrixD,
                        Matrix.MatrixOrder.COLUMN_MAJOR);
                for (int i = 0; i < 16; i++)
                    ortho.scratch.matrixF[i] = (float) ortho.scratch.matrixD[i];
                GLES20FixedPipeline.glLoadMatrixf(ortho.scratch.matrixF, 0);

                GLES20FixedPipeline
                        .glEnableClientState(
                                GLES20FixedPipeline.GL_VERTEX_ARRAY);

                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
                GLES20FixedPipeline.glBlendFunc(
                        GLES20FixedPipeline.GL_SRC_ALPHA,
                        GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
                float r = Color.red(color) / 255.0f;
                float g = Color.green(color) / 255.0f;
                float b = Color.blue(color) / 255.0f;

                if (a > 0) {
                    // validate the render vertices
                    if (rebuildExtrusionVertices) {
                        _fillPoints.clear();
                        for (int i = 0; i < _fillPointsPreForward
                                .limit(); i += 3) {
                            final double lng = _fillPointsPreForward.get(i);
                            final double lat = _fillPointsPreForward.get(i + 1);
                            final double alt = _fillPointsPreForward.get(i + 2);
                            ortho.scratch.geo.set(lat, lng, alt);
                            ortho.currentPass.scene.mapProjection.forward(
                                    ortho.scratch.geo, ortho.scratch.pointD);
                            _fillPoints.put((float) (ortho.scratch.pointD.x
                                    - _extrusionCentroidProj.x));
                            _fillPoints.put((float) (ortho.scratch.pointD.y
                                    - _extrusionCentroidProj.y));
                            _fillPoints.put((float) (ortho.scratch.pointD.z
                                    - _extrusionCentroidProj.z));
                        }
                        _fillPoints.flip();
                    }

                    GLES20FixedPipeline.glColor4f(r, g, b, a);

                    GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
                    GLES30.glPolygonOffset(1.0f, 1.0f);

                    GLES20FixedPipeline.glVertexPointer(3,
                            GLES20FixedPipeline.GL_FLOAT, 0, _fillPoints);

                    int pCount = _fillPoints.limit() / 3;

                    GLES20FixedPipeline.glDrawArrays(GLES30.GL_TRIANGLES, 0,
                            pCount);

                    GLES30.glPolygonOffset(0.0f, 0.0f);
                    GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);
                }

                _3dOutline.draw(ortho);

                GLES20FixedPipeline
                        .glDisableClientState(
                                GLES20FixedPipeline.GL_VERTEX_ARRAY);
                GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
                GLES20FixedPipeline.glPopMatrix();
            }
        }
    }

    private void _setEndPoint(final int index, final GeoPoint point,
            boolean dispatchBounds) {
        impl.setAltitudeMode(
                clampToGround() ? Feature.AltitudeMode.ClampToGround
                        : Feature.AltitudeMode.Absolute);
        impl.setTessellationEnabled(clampToGround());

        _points[index] = point;

        final boolean valid = (_points[0] != null && _points[1] != null);

        final LineString ls = new LineString(3);
        if (_points[0] != null)
            ls.addPoint(_points[0].getLongitude(), _points[0].getLatitude(),
                    getHae(_points[0]));
        if (_points[1] != null)
            ls.addPoint(_points[1].getLongitude(), _points[1].getLatitude(),
                    getHae(_points[1]));
        impl.setGeometry(ls);
        if (_labelID != GLLabelManager.NO_ID) {
            _labelManager.setGeometry(_labelID, ls);
            _labelManager.setAltitudeMode(_labelID,
                    clampToGround() ? Feature.AltitudeMode.ClampToGround
                            : Feature.AltitudeMode.Absolute);
        }

        implInvalid = false;
        _extrudeInvalid = true;

        // dispatch bounds update
        if (valid && dispatchBounds) {
            final double lat0 = _points[0].getLatitude();
            final double lon0 = _points[0].getLongitude();
            final double lat1 = _points[1].getLatitude();
            final double lon1 = _points[1].getLongitude();

            this.context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    MapView mv = MapView.getMapView();
                    final double N = Math.max(lat0, lat1); // N
                    double W = Math.min(lon0, lon1); // W
                    final double S = Math.min(lat0, lat1); // S
                    double E = Math.max(lon0, lon1); // E

                    // if both are unwrapped, wrap
                    if (Math.abs(W) > 180d && Math.abs(E) > 180d) {
                        final double ew = GeoCalculations.wrapLongitude(E);
                        final double ww = GeoCalculations.wrapLongitude(W);
                        E = Math.max(ew, ww);
                        W = Math.min(ew, ww);
                    }
                    bounds.set(N, W, S, E);
                    bounds.setWrap180(mv != null
                            && mv.isContinuousScrollEnabled());
                    bounds.getCenter(_extrusionCentroid);
                    _extrusionCentroidSrid = -1;

                    _geomBounds = ls.getEnvelope();
                    updateBoundsZ();

                    dispatchOnBoundsChanged();
                }

            });
        }
    }

    /**
     * Adjust the point up so it does not end up rendering subsurface
     * XXX - direct copy of the GLArrow2 implementation for 4.3.1,   Larger discussion needs to occur
     * how this can be handled more generically.
     */
    private double getHae(final GeoPoint geo) {
        final boolean ptAgl = (geo
                .getAltitudeReference() == GeoPoint.AltitudeReference.AGL ||
                Double.isNaN(geo.getAltitude()));
        if (!ptAgl)
            return geo.getAltitude();

        double hae = geo.getAltitude();
        if (Double.isNaN(hae))
            hae = 0d;
        final double terrain = +_glMapView
                .getTerrainMeshElevation(geo.getLatitude(), geo.getLongitude());
        if (!Double.isNaN(terrain))
            hae += terrain;
        return hae;
    }

    private final PointMapItem.OnPointChangedListener _firstItemMovedListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            _updateItemPoint(0, item);
        }
    };

    private final PointMapItem.OnPointChangedListener _secondItemMovedListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            _updateItemPoint(1, item);
        }
    };

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        HitTestResult result = impl.hitTest(renderer, params);
        return result != null ? new HitTestResult(subject, result) : null;
    }

    private void updateBoundsZ() {
        double minZ = _geomBounds != null ? _geomBounds.minZ : Double.NaN;
        double maxZ = _geomBounds != null ? _geomBounds.maxZ : Double.NaN;

        if (_clampToGround) {
            // geometry is clamped to ground/always surface; assume maximum and minimum surface
            // altitudes
            minZ = DEFAULT_MIN_ALT;
            maxZ = DEFAULT_MAX_ALT;
        } else {
            // no additional interpretation
        }

        // apply extrusion. This is not strict, however, it should be
        // sufficient to cover the various permutations.
        if (_hasHeight) {
            maxZ += _height;
        }

        bounds.setMinAltitude(minZ);
        bounds.setMaxAltitude(maxZ);
    }
}
