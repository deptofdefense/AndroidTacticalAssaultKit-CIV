
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi3;
import com.atakmap.android.maps.graphics.GLSegmentFloatingLabel;
import com.atakmap.android.maps.graphics.GLText2;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.FahArrowWidget;
import com.atakmap.android.widgets.FahArrowWidget.OnDesignatorPointChangedListener;
import com.atakmap.android.widgets.FahArrowWidget.OnFahAngleChangedListener;
import com.atakmap.android.widgets.FahArrowWidget.OnFahLegChangedListener;
import com.atakmap.android.widgets.FahArrowWidget.OnFahWidthChangedListener;
import com.atakmap.android.widgets.FahArrowWidget.OnTargetPointChangedListener;
import com.atakmap.android.widgets.FahArrowWidget.OnTouchableChangedListener;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.coremap.conversions.AngleUtilities;

import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPolygon;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.util.Releasable;

/**
 * A GLMapWidget class that will display an arrow representing the Final Attack Heading towards a
 * targeted IP and with an offset from some designator point. The widget will also display a cone
 * that represents the offset width from the heading on each side. The final attack heading text
 * value will be displayed above the heading arrow, as well as the text heading values of the cone's
 * sides.<br>
 * <br>
 *
 * 
 */
public class GLFahArrowWidget extends GLShapeWidget implements
        OnFahWidthChangedListener, OnFahAngleChangedListener,
        OnTouchableChangedListener, OnTargetPointChangedListener,
        OnFahLegChangedListener,
        OnDesignatorPointChangedListener,
        Releasable {

    public final static GLMapItemSpi3 GLITEM_SPI = new GLMapItemSpi3() {
        @Override
        public int getPriority() {
            // FahArrowWidget : Shape : MapItem
            return 2;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> object) {
            if (!(object.second instanceof FahArrowWidget.Item))
                return null;
            return new AbstractGLMapItem2(object.first, object.second,
                    GLMapView.RENDER_PASS_SPRITES | GLMapView.MATCH_SURFACE) {
                private GLFahArrowWidget impl = null;
                {
                    ((FahArrowWidget.Item) this.subject).getBounds(this.bounds);
                }

                @Override
                public void draw(GLMapView ortho, int pass) {
                    if (this.impl == null) {
                        this.impl = new GLFahArrowWidget(
                                ((FahArrowWidget.Item) this.subject).getFAH(),
                                (GLMapView) this.context);
                        this.impl
                                .startObserving(
                                        ((FahArrowWidget.Item) this.subject)
                                                .getFAH());
                    }

                    final boolean surface = MathUtils.hasBits(pass,
                            GLMapView.RENDER_PASS_SURFACE);
                    final boolean sprites = MathUtils.hasBits(pass,
                            GLMapView.RENDER_PASS_SPRITES);
                    if (surface && !bounds.intersects(ortho.northBound,
                            ortho.westBound, ortho.southBound, ortho.eastBound))
                        return;

                    // draw the widget onto the map surface
                    if (surface) {
                        // duplicate GLWidgetsLayer translation
                        GLES20FixedPipeline.glPushMatrix();
                        GLES20FixedPipeline.glTranslatef(0,
                                LegacyAdapters.getRenderContext(this.context)
                                        .getRenderSurface()
                                        .getHeight() - 1,
                                0f);
                        this.impl.drawWidgetSurface();
                        GLES20FixedPipeline.glPopMatrix();
                    }

                    // update the touch point
                    if (sprites) {
                        if (this.impl._visible && this.impl._target != null)
                            this.impl._setHitPoint();

                        impl.drawWidgetSprites();
                    }
                }

                @Override
                public void release() {
                    if (this.impl != null) {
                        this.impl
                                .stopObserving(
                                        ((FahArrowWidget.Item) this.subject)
                                                .getFAH());
                        this.impl.release();
                        this.impl = null;
                    }
                }
            };
        }

    };

    private static final int _ARROW_COLOR = Color.argb(128, 0, 255, 0);
    private static final int _STROKE_COLOR = Color.argb(255, 0, 0, 0);
    private static final int _TEXT_COLOR = Color.argb(255, 255, 255, 255);
    private static final int _CONE_COLOR = Color.argb(128, 255, 255, 255);
    private static final int _CONE_COLOR_ALT = Color.argb(128, 255, 0, 0);

    private final static Style _CONE_STYLE = new CompositeStyle(new Style[] {
            new BasicFillStyle(_CONE_COLOR),
            new BasicStrokeStyle(_STROKE_COLOR, 1f),
    });
    private final static Style _CONE_STYLE_ALT = new CompositeStyle(
            new Style[] {
                    new BasicFillStyle(_CONE_COLOR_ALT),
                    new BasicStrokeStyle(_STROKE_COLOR, 1f),
            });
    private final static Style _ARROW_STYLE = new CompositeStyle(new Style[] {
            new BasicFillStyle(_ARROW_COLOR),
            new BasicStrokeStyle(_STROKE_COLOR, 1f),
    });

    private static final boolean RENDER_TOUCH_POINT_SPRITE = false;

    // arrays to hold the values for the arrow GL object
    private byte[] _arrowLineIndicesB;
    private byte[] _arrowIndicesB;
    private float[] _arrowVertsB;

    // The associated buffers for the arrow GL object
    private ByteBuffer _arrowLineIndices;
    private ByteBuffer _arrowIndices;
    private ByteBuffer _arrowVerts;

    private GeoPoint _arrowStart = null;

    // The buffers for the associated cone GL Object
    private double[] _coneCirclePoints;

    private GLBatchPolygon _coneFront;
    private GLBatchPolygon _coneBack;

    // The GL texts for the widget
    private GLText2 _arrowText;
    private GLSegmentFloatingLabel _coneText0, _coneText1, _coneText2,
            _coneText3;
    private GLSegmentFloatingLabel[] _textArray;
    private GeoPoint _hitPoint;
    private GeoPoint _hitPointInner;

    // Animated float value that interpolates the angle between movements, value
    // is MAGNETIC heading
    private final AnimatedFloat _headingAnimation = new AnimatedFloat(0, 1f,
            0f,
            360f, true);

    // Values that define how the widget will be displayed
    private float _headingWidth = 30f;
    private final float _radius = 100f;
    private GeoPoint _target;
    private GeoPoint _designator;
    private boolean _fakeDesignator = true;
    private double _savedRange = Double.NaN;

    // The MapWidget subject
    private final FahArrowWidget _subject;

    // The instance of GL Circle that blinks or can disappear depending on the touch state
    private final GLPCASCircleWidget _headingTouch = new GLPCASCircleWidget(0f,
            30f,
            0xFF33B5E5,
            0xFF0000FF);

    private static final float div_pi_x180 = 180f / ((float) Math.PI);

    private boolean _visible = true;
    private boolean _oneShotAnimate = true;

    private boolean _touchable;

    /**
     * the rotation that should be applied when RENDERING the arrow and cone,
     * defined as TRUE rotation.
     */
    private float _renderRotation;

    private boolean _coneInvalid;

    private PointD _arrowCenterUnscaled;

    private final SurfaceRendererControl _surfaceCtrl;

    // A static class to define what need buffers need to be updated when a value
    // in the MapWidget subject is changed.
    private enum Update {
        FAH_ANGLE,
        FAH_WIDTH,
        FAH_LEG,
        DESIGNATOR_POINT,
        TARGET_POINT,
        VISIBLE
    }

    public GLFahArrowWidget(FahArrowWidget subject, GLMapView orthoView) {
        super(subject, orthoView);
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _buildArrow();
            }
        });
        _subject = subject;
        _textArray = null;

        _coneInvalid = true;

        _touchable = subject.isTouchable();
        _surfaceCtrl = orthoView.getControl(SurfaceRendererControl.class);
    }

    private void validateTextArray() {
        if (_textArray == null) {
            MapTextFormat mtf = MapView.getTextFormat(Typeface.DEFAULT, +2);
            _arrowText = new GLText2(mtf, String.format(
                    LocaleUtil.getCurrent(), "%03.0f",
                    359f));

            // Build the arrow's vertices, and indices just once

            _textArray = new GLSegmentFloatingLabel[4];
            for (int i = 0; i < _textArray.length; i++) {
                _textArray[i] = new GLSegmentFloatingLabel();
                _textArray[i].setTextFormat(Typeface.DEFAULT, +2);
                _textArray[i].setText(String.format(
                        LocaleUtil.getCurrent(), "%03.0f",
                        359f));
                _textArray[i].setBackgroundColor(0f, 0f, 0f, 0.6f);
                _textArray[i].setClampToGround(true);
                _textArray[i].setSegmentPositionWeight(1f);
                _textArray[i].setRotateToAlign(false);
            }
            _coneText0 = _textArray[0];
            _coneText1 = _textArray[1];
            _coneText2 = _textArray[2];
            _coneText3 = _textArray[3];
        }
    }

    public void startObserving(FahArrowWidget arrow) {
        super.startObserving(arrow);
        arrow.addOnTargetPointChangedListener(this);
        arrow.addOnFahAngleChangedListener(this);
        arrow.addOnFahWidthChangedListener(this);
        arrow.addOnFahLegChangedListener(this);
        arrow.addOnTouchableChangedListener(this);
        arrow.addOnDesignatorPointChangedListener(this);

        _visible = arrow.isVisible();
        _coneInvalid = true;
        _update(arrow, Update.FAH_ANGLE);
        _update(arrow, Update.FAH_WIDTH);
        _update(arrow, Update.FAH_LEG);
        _update(arrow, Update.DESIGNATOR_POINT);
        _update(arrow, Update.TARGET_POINT);
        _update(arrow, Update.VISIBLE);
    }

    public void stopObserving(FahArrowWidget arrow) {
        super.stopObserving(arrow);
        arrow.removeOnTargetPointChangedListener(this);
        arrow.removeOnFahAngleChangedListener(this);
        arrow.removeOnFahWidthChangedListener(this);
        arrow.removeOnFahLegChangedListener(this);
        arrow.removeOnTouchableChangedListener(this);
        arrow.removeOnDesignatorPointChangedListener(this);
    }

    /**
     * Retrieve the bounding RectF of the current state of the Map. This accounts for the
     * OrthoMapView's focus, so DropDowns will be accounted for.
     *
     * @return
     * @return The bounding RectF
     */
    @Override
    protected RectF getWidgetViewF() {
        // Could be in half or third display of dropdown, so use the offset;
        float right = this.orthoView.focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        //float top = this.orthoView.focusy * 2;
        float top = this.orthoView.getTop();
        return new RectF(0f + 20, top - 20, right - 20, 0f + 20);
    }

    @Override
    public void drawWidgetContent() {
        // Doesn't need to be overriden since drawWidget is overriden
    }

    @Override
    public void drawWidget() {
        if (this.drawWidgetSurface()) {
            drawWidgetSprites();

            // Set the hit point of this widget to be the touch circle
            // Draw the touch circle at the end of the arrow and above everything else
            _setHitPoint();

            // FAH is animate, request refresh
            orthoView.requestRefresh();
        }
    }

    private boolean drawWidgetSurface() {
        if (!_visible)
            return false;

        if (_target == null)
            return false;

        this.validateTextArray();

        // Call an update to the animation
        boolean headingMoved = _headingAnimation.update();
        if (headingMoved) {
            // obtain the heading from the animator/interpolator
            float rawheading = _headingAnimation.get();
            if (rawheading > 360)
                rawheading = rawheading - 360;

            // convert from magnetic to true round - must round to 5 prior to converting
            rawheading = (float) ATAKUtilities.convertFromMagneticToTrue(
                    _target, round5(Math.round(rawheading)));
            _renderRotation = rawheading;
        }

        // If the heading moved then the cone should too
        if (headingMoved || _coneInvalid) {
            _buildCone();
        }
        GLES20FixedPipeline.glPushMatrix();
        // Get the orthoview's representation of the target as x y values
        PointF pos = this.orthoView.forward(_target);
        float xPos = pos.x;
        float yPos = pos.y;

        // Get rid of the screen height imposed by the GLMapWidget class
        GLES20FixedPipeline.glTranslatef(0,
                -(getSurface().getHeight() + 1), 0f);

        // Draw the cone with the basic map parameters set for the GL Pipeline
        _drawCone();
        // when the map is tilted, draw the arrow on the surface
        if (orthoView.currentScene.drawTilt > 0d) {
            _drawArrow(orthoView, true);
            if (!RENDER_TOUCH_POINT_SPRITE)
                _drawTouchPointSurface(orthoView);
        }
        GLES20FixedPipeline.glPopMatrix();

        return true;
    }

    private boolean drawWidgetSprites() {
        if (!_visible)
            return false;

        if (_target == null)
            return false;

        // when the map is not tilted, draw the arrow as a sprite for cleaner
        // graphics
        if (orthoView.currentScene.drawTilt == 0d)
            _drawArrow(orthoView, false);
        if (orthoView.currentScene.drawTilt == 0d || RENDER_TOUCH_POINT_SPRITE)
            _drawTouchPointSprite(orthoView);

        this.validateTextArray();

        drawArrowText();

        drawTextAngle(0);
        drawTextAngle(1);

        if (_subject.drawReverse()) {
            drawTextAngle(2);
            drawTextAngle(3);
        }

        return true;
    }

    private int round5(final int val) {
        return (Math.round(val / 5f) * 5);
    }

    @Override
    public void releaseWidget() {
        this.stopObserving(_subject);

        _arrowText = null;
        _coneText0 = null;
        _coneText1 = null;
        _coneText2 = null;
        _coneText3 = null;
        _textArray = null;
    }

    @Override
    public void release() {
        if (_coneFront != null) {
            _coneFront.release();
            _coneFront = null;
        }
        if (_coneBack != null) {
            _coneBack.release();
            _coneBack = null;
        }
    }

    @Override
    public void onFahAngleChanged(FahArrowWidget arrow) {
        _update(arrow, Update.FAH_ANGLE);
    }

    @Override
    public void onVisibleChanged(MapWidget arrow) {

        if (!arrow.isVisible())
            _oneShotAnimate = false;

        _visible = arrow.isVisible();
        // _update(arrow, Update.VISIBLE);

        super.onVisibleChanged(arrow);

        markSurfaceDirty(true);
    }

    @Override
    public void onFahWidthChanged(FahArrowWidget arrow) {
        _update(arrow, Update.FAH_WIDTH);
    }

    @Override
    public void onFahLegChanged(FahArrowWidget arrow) {
        _update(arrow, Update.FAH_LEG);
    }

    @Override
    public void onTouchableChanged(FahArrowWidget arrow) {
        final boolean touchable = arrow.getTouchable();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _touchable = touchable;
                if (_touchable) {
                    _headingTouch.setAlpha(0.6f);
                } else {
                    _headingTouch.setAlpha(0f);
                }
                markSurfaceDirty(false);
            }
        });
    }

    @Override
    public void onTargetChanged(FahArrowWidget arrow) {
        _update(arrow, Update.TARGET_POINT);
    }

    @Override
    public void onDesignatorChanged(FahArrowWidget arrow) {
        _update(arrow, Update.DESIGNATOR_POINT);
    }

    private void _drawArrow(GLMapView view, boolean surface) {
        final float headingM = _headingAnimation.get();
        double heading = ATAKUtilities.convertFromMagneticToTrue(_target,
                headingM);
        final double distance = view.currentScene.drawMapResolution
                * (200 + _radius);
        final double back = ((heading) > 180d) ? heading - 180d
                : heading + 180d;
        // get image space coordinate approximately 100px from '_target'
        _arrowStart = GeoCalculations.pointAtDistance(_target, heading,
                distance);
        _hitPointInner = GeoCalculations.pointAtDistance(_target, back,
                view.currentScene.drawMapResolution * _radius);
        _hitPoint = GeoCalculations.pointAtDistance(_target, back, distance);

        // compute the IMAGE SPACE rotation to be applied. '_renderRotation' is
        // the magnetic heading angle in GEODETIC SPACE.

        // get image space coordinate for '_target'
        view.forward(surface ? _target : adjustedGeoPoint(view, _target),
                view.scratch.pointD);
        final float sx = (float) view.scratch.pointD.x;
        final float sy = (float) view.scratch.pointD.y;
        final float sz = surface ? 0f : (float) view.scratch.pointD.z;

        view.forward(surface ? _hitPoint : adjustedGeoPoint(view, _hitPoint),
                view.scratch.pointF);
        final float ex = view.scratch.pointF.x;
        final float ey = view.scratch.pointF.y;

        view.forward(
                surface ? _hitPointInner
                        : adjustedGeoPoint(view, _hitPointInner),
                view.scratch.pointF);
        final float ix = view.scratch.pointF.x;
        final float iy = view.scratch.pointF.y;

        // compute relative rotation
        final float theta = (float) Math.toDegrees(Math.atan2((sy - ey),
                (sx - ex)));

        final float innerRadius = surface
                ? (float) MathUtils.distance(sx, sy, ix, iy)
                : _radius * 1.05f;

        // Rotate the matrix and position it to draw the arrrow
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(sx, sy, sz);
        GLES20FixedPipeline.glRotatef(theta + 180f, 0, 0, 1f);
        GLES20FixedPipeline.glTranslatef(innerRadius, 0, 0);
        final float scale = (float) MathUtils.distance(ex, ey, sx, sy)
                - innerRadius;
        GLES20FixedPipeline.glScalef(scale, scale, 1f);

        // Fill in the FAH Arrow
        _setColor(_ARROW_COLOR);
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _arrowVerts);
        GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_TRIANGLES,
                _arrowIndices.limit(), GLES20FixedPipeline.GL_UNSIGNED_BYTE,
                _arrowIndices);

        // Stroke around the FAH Arrow
        _setColor(_STROKE_COLOR);
        GLES20FixedPipeline.glLineWidth(2f);
        GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_LINE_LOOP,
                _arrowLineIndices.limit(),
                GLES20FixedPipeline.GL_UNSIGNED_BYTE,
                _arrowLineIndices);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        // Pop that Matrix math off to prep for the text display
        GLES20FixedPipeline.glPopMatrix();
    }

    private void _drawTouchPointSprite(GLMapView view) {
        // Draw the touch circle at the end of the arrow and above everything else
        if (_touchable) {
            view.forward(adjustedGeoPoint(view, _hitPoint),
                    view.scratch.pointD);

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef((float) view.scratch.pointD.x,
                    (float) view.scratch.pointD.y,
                    (float) view.scratch.pointD.z);
            _headingTouch.draw();
            GLES20FixedPipeline.glPopMatrix();
        }
    }

    private void _drawTouchPointSurface(GLMapView view) {
        // Draw the touch circle at the end of the arrow and above everything else
        if (_touchable) {
            view.forward(_hitPoint, view.scratch.pointD);

            final float relativeScale = (float) (view.currentScene.drawMapResolution
                    / view.currentPass.drawMapResolution)
                    / (3f - view.currentPass.relativeScaleHint);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef((float) view.scratch.pointD.x,
                    (float) view.scratch.pointD.y,
                    (float) view.scratch.pointD.z);
            GLES20FixedPipeline.glScalef(relativeScale, relativeScale, 1f);
            _headingTouch.draw();
            GLES20FixedPipeline.glPopMatrix();

            // XXX - some alternative should be found to avoid animating the
            //       surface -- render as sprite?

            // mark surface dirty for the heading touch point animation
            markSurfaceDirty(false);
        }
    }

    private String _getFahText() {
        // Create the heading text
        float heading = _headingAnimation.get();
        // shouldn't need these but they don't hurt
        heading = (heading > 360) ? heading - 360 : heading;
        heading = (heading < 0) ? heading + 360 : heading;

        int mag = Math.round(heading);
        mag = round5(mag) % 360;

        String direction = DirectionType.getDirection(mag).getAbbreviation();
        return direction + AngleUtilities.format(mag) + "M";
    }

    private String _getConeText(int index) {
        // Create the heading text
        float angle;
        if (index == 0) {
            angle = _headingAnimation.get() - _headingWidth / 2;
        } else if (index == 1) {
            angle = _headingAnimation.get() + _headingWidth / 2;
        } else if (index == 2) {
            angle = _headingAnimation.get() + 180 - _headingWidth / 2;
        } else {
            angle = _headingAnimation.get() + 180 + _headingWidth / 2;
        }

        angle = (angle > 360) ? angle - 360 : angle;
        angle = (angle < 0) ? angle + 360 : angle;

        int mag = Math.round(angle);
        mag = round5(mag) % 360;
        return AngleUtilities.format(mag) + "M";
    }

    private void drawTextAngle(int index) {
        if (_textArray == null) {
            return;
        }
        GLSegmentFloatingLabel text = _textArray[index];
        if (text == null) {
            return;
        }

        text.update(orthoView);
        text.getTextPoint(orthoView.scratch.pointD);
        final double ax = orthoView.scratch.pointD.x;
        final double ay = orthoView.scratch.pointD.y;
        orthoView.scratch.geo.set(_target);
        orthoView.scratch.geo.set(orthoView.getTerrainMeshElevation(
                _target.getLatitude(), _target.getLongitude()));
        orthoView.scene.forward(orthoView.scratch.geo,
                orthoView.scratch.pointD);
        final double bx = orthoView.scratch.pointD.x;
        final double by = orthoView.scratch.pointD.y;
        if (MathUtils.distance(ax, ay, bx, by) < 320d)
            return;

        // Draw the text over the black backdrop
        _setColor(_TEXT_COLOR);
        text.setText(_getConeText(index));
        text.setTextColor(_TEXT_COLOR);
        text.draw(orthoView);
    }

    private void drawArrowText() {
        orthoView.forward(adjustedGeoPoint(
                orthoView,
                GeoCalculations.midPointWGS84(_hitPointInner, _hitPoint)),
                orthoView.scratch.pointD);

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(
                (float) orthoView.scratch.pointD.x,
                (float) orthoView.scratch.pointD.y +
                        GLSegmentFloatingLabel.getSurfaceLabelOffset(
                                orthoView, _arrowText.getHeight()),
                (float) orthoView.scratch.pointD.z);

        // Grab the black text backdrop texture
        GLNinePatch smallNinePatch = GLRenderGlobals.get(getSurface())
                .getSmallNinePatch();
        if (smallNinePatch != null) {
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(
                    -(_arrowText.getWidth() / 2f + 8f),
                    -_arrowText.getHeight() / 2f, 0f);
            smallNinePatch.draw(_arrowText.getWidth() + 16f,
                    _arrowText.getHeight());
            GLES20FixedPipeline.glPopMatrix();
        }

        // Shift the matrix over to center the text at the location
        GLES20FixedPipeline.glTranslatef(-_arrowText.getWidth() / 2f,
                -_arrowText.getHeight() / 4f,
                0f);

        // Draw the text over the black backdrop
        _setColor(_TEXT_COLOR);
        _arrowText.setText(_getFahText());
        _arrowText.draw(_TEXT_COLOR);

        GLES20FixedPipeline.glPopMatrix();
    }

    /**
     * Calculate if a cone is between two number honoring wrapping of the numbers
     * around 360.
     */
    private boolean coneBetween(final int low, final int high) {
        final double num = Math.abs(_subject.getTrueOffset());

        if (low > high) {
            if (num >= low || num <= high)
                return true;
        } else {
            if (num >= low && num <= high)
                return true;
        }
        return false;
    }

    private void _drawCone() {
        // XXX - refactor style selection into applicable callbacks

        final Style coneStyle;
        if ((_designator != null && !_fakeDesignator) &&
                (coneBetween(350, 10) || coneBetween(170, 190))) {
            coneStyle = _CONE_STYLE_ALT;
        } else {
            coneStyle = _CONE_STYLE;
        }
        _coneFront.setStyle(coneStyle);
        _coneFront.draw(orthoView);

        if (_subject.drawReverse()) {
            _coneBack.setStyle(coneStyle);
            _coneBack.draw(orthoView);
        }
    }

    private void _setHitPoint() {
        if (_hitPoint != null) {
            final GLMapView view = getSurface().getGLMapView();

            PointF hitPointF = view.currentScene.scene
                    .forward(adjustedGeoPoint(view, _hitPoint), (PointF) null);
            hitPointF.y = getSurface().getHeight() + 1 - hitPointF.y;
            _subject.setPoint(hitPointF.x, hitPointF.y);

        }
    }

    private static GeoPoint adjustedGeoPoint(GLMapView view, GeoPoint point) {
        return new GeoPoint(point.getLatitude(),
                point.getLongitude(),
                view.getTerrainMeshElevation(point.getLatitude(),
                        point.getLongitude()));
    }

    private void markSurfaceDirty(boolean streaming) {
        if (_surfaceCtrl == null)
            return;

        final GeoPoint tgt = _target;
        if (_target == null)
            return;

        final double distance = _subject.getAppropriateDistance();

        final GeoPoint north = GeoCalculations.pointAtDistance(tgt, 0d,
                distance);
        final GeoPoint south = GeoCalculations.pointAtDistance(tgt, 180d,
                distance);
        final GeoPoint east = GeoCalculations.pointAtDistance(tgt, 90d,
                distance);
        final GeoPoint west = GeoCalculations.pointAtDistance(tgt, 270d,
                distance);

        // check for IDL crossing
        if ((east.getLongitude() < tgt.getLongitude())
                || (west.getLongitude() > tgt.getLongitude())) {
            _surfaceCtrl.markDirty(new Envelope(west.getLongitude(),
                    south.getLatitude(), 0d, 180d, north.getLatitude(), 0d),
                    streaming);
            _surfaceCtrl.markDirty(
                    new Envelope(-180d, south.getLatitude(), 0d,
                            east.getLongitude(), north.getLatitude(), 0d),
                    streaming);
        } else {
            _surfaceCtrl.markDirty(
                    new Envelope(west.getLongitude(), south.getLatitude(), 0d,
                            east.getLongitude(), north.getLatitude(), 0d),
                    streaming);
        }
    }

    private void _update(final FahArrowWidget arrow, Update u) {

        switch (u) {
            case FAH_ANGLE:
                // Grab the new angle that represents the heading
                final float angle = (float) arrow.getFahAngle();
                // Run the command on the GL thread
                getSurface().queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        // Move the heading animation
                        _headingAnimation.animateTo(angle);
                        // Update the cone's geopoint values from the already computed
                        // geopoints that are in _coneCirclePoints
                        if (_target != null && _designator != null) {
                            _coneInvalid = true;
                        }

                        markSurfaceDirty(true);
                    }
                });
                break;
            case FAH_WIDTH:
                // When the width changes only change the width
                final float width = (float) arrow.getFahWidth();
                // Run the command on the GL thread
                getSurface().queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        // Update the width
                        _headingWidth = width;
                        // Only call _buildCone which will grab the values from
                        // the already computed geopoints in _coneCirclePoints
                        if (_target != null && _designator != null) {
                            _coneInvalid = true;
                        }

                        markSurfaceDirty(true);
                    }
                });
                break;
            case FAH_LEG:
                // This case expands or shrinks one leg of the cone
                // it does this by adjusting the width of the cone and the angle of the arrow
                final float fahWidth = (float) arrow.getFahWidth();
                final float arrowAngle = (float) arrow.getFahAngle();
                getSurface().queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        _headingAnimation.set(arrowAngle);
                        _headingWidth = fahWidth;
                        if (_target != null && _designator != null) {
                            _coneInvalid = true;
                        }

                        markSurfaceDirty(true);
                    }
                });
                break;
            case VISIBLE:
                _visible = arrow.isVisible();
                break;
            case TARGET_POINT:
                // Only get the new target's geopoint

                if (arrow.getTargetPoint() != null) {
                    final GeoPoint target = new GeoPoint(
                            arrow.getTargetPoint().getLatitude(),
                            arrow.getTargetPoint().getLongitude());

                    if (_designator == null || _fakeDesignator) {
                        // slightly south of the target
                        _designator = new GeoPoint(
                                target.getLatitude() - .01d,
                                target.getLongitude());
                    }

                    // Run the command on the GL thread
                    getSurface().queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            // Update the target point
                            _target = target;
                            // Rebuild the cone only, the change in heading angle
                            // with be handled by the FAH_ANGLE call
                            if (_target != null && _designator != null) {
                                // always rebuild the circle points when the target moves
                                _buildConeCircle();

                                _coneInvalid = true;
                            }

                            markSurfaceDirty(true);
                        }
                    });
                }
                break;
            case DESIGNATOR_POINT:
                // Only get the new designator's geopoint
                final GeoPoint designator;
                if (arrow.getDesignatorPoint() != null) {
                    designator = new GeoPoint(
                            arrow.getDesignatorPoint().getLatitude(),
                            arrow.getDesignatorPoint().getLongitude());
                    _fakeDesignator = false;
                } else if (_target != null) {
                    // slightly south of the target
                    designator = new GeoPoint(
                            _target.getLatitude() - .01d,
                            _target.getLongitude());
                    _fakeDesignator = true;
                } else {
                    designator = null;
                }

                if (designator != null) {
                    // Run the command on the GL thread
                    getSurface().queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            // Update the designator point
                            _designator = designator;
                            // Rebuild the cone only, the change in heading angle
                            // with be handled by the FAH_ANGLE call
                            if (_target != null && _designator != null) {
                                // only rebuild the circle when the distance of the designator changes
                                // by 10% of saved distance
                                double newRange = GeoCalculations.distanceTo(
                                        _designator,
                                        _target);
                                if (Double.isNaN(_savedRange)) {
                                    _savedRange = newRange;
                                    _buildConeCircle();
                                } else if (Math.abs(
                                        newRange - _savedRange) > _savedRange
                                                * 0.1) {
                                    // Update the new range and build the cone's circle points
                                    _savedRange = newRange;
                                    _buildConeCircle();
                                }

                                markSurfaceDirty(true);

                                // Always move the cone when the designator moves
                                _coneInvalid = true;
                            }
                        }
                    });
                }
                break;
            default:
                break;
        }
    }

    /**
     * A Helper method to build the values behind the arrow - only needs to be called once.
     */
    private void _buildArrow() {
        // The vert values will later be scaled
        float[] vertices = {
                0.0f, 0.0f,
                0.2f, 0.2f,
                0.2f, 0.075f,
                0.2f, -0.075f,
                0.2f, -0.2f,
                1.0f, 0.075f,
                1.0f, -0.075f
        };
        // The indices to define the fill and the outline for the GL object
        byte[] indices = {
                0, 1, 2, 0, 2, 3, 0, 3, 4, 3, 2, 5, 3, 5, 6
        };
        byte[] lineIndices = {
                0, 1, 2, 5, 6, 3, 4
        };

        // dynamic computation in case someone changes above without changing
        // here
        float arrowMinX = vertices[0];
        float arrowMinY = vertices[1];
        float arrowMaxX = vertices[0];
        float arrowMaxY = vertices[1];

        for (int i = 2; i < vertices.length; i += 2) {
            arrowMinX = Math.min(vertices[i], arrowMinX);
            arrowMinY = Math.min(vertices[i + 1], arrowMinY);
            arrowMaxX = Math.max(vertices[i], arrowMaxX);
            arrowMaxY = Math.max(vertices[i + 1], arrowMaxY);
        }
        _arrowCenterUnscaled = new PointD((arrowMinX + arrowMaxX) / 2d,
                (arrowMinY + arrowMaxY) / 2d);

        // Save these values in arrays to later be used by buffers.
        _arrowVertsB = vertices;
        _arrowIndicesB = indices;
        _arrowLineIndicesB = lineIndices;

        // Change the arrays to Byte Buffers that can be read in by the GL Pipeline

        // Convert the vertices
        if (_arrowVerts == null) {
            _arrowVerts = com.atakmap.lang.Unsafe
                    .allocateDirect(_arrowVertsB.length * 4);
            _arrowVerts.order(ByteOrder.nativeOrder());
        } else {
            _arrowVerts.clear();
        }
        FloatBuffer fb = _arrowVerts.asFloatBuffer();
        fb.put(_arrowVertsB);
        fb.rewind();
        // Convert the indices that define the fill pattern
        if (_arrowIndices == null) {
            _arrowIndices = com.atakmap.lang.Unsafe
                    .allocateDirect(_arrowIndicesB.length);
        } else {
            _arrowIndices.clear();
        }
        _arrowIndices.put(_arrowIndicesB);
        _arrowIndices.rewind();
        // Convert the line indices that define the stroke sequence
        if (_arrowLineIndices == null) {
            _arrowLineIndices = com.atakmap.lang.Unsafe
                    .allocateDirect(_arrowLineIndicesB.length);
        } else {
            _arrowLineIndices.clear();
        }
        _arrowLineIndices.put(_arrowLineIndicesB);
        _arrowLineIndices.rewind();
    }

    /**
     * A helper method to update the possible geo-points of the cone. These values will later be
     * used by the _buildCone() method. This saves us computations in real time
     */
    private void _buildConeCircle() {

        final double distance = _subject.getAppropriateDistance();

        // The number of vertices to compute for the circle
        final int vertsNum = 360;

        // The cone will have 0.5 degrees between each point
        final float step = 1f;

        if (_coneCirclePoints == null
                || _coneCirclePoints.length < (vertsNum * 2))
            _coneCirclePoints = new double[vertsNum * 2];

        // Loop the wedge's vertices and build the outer point of the wedge
        for (int w = 0; w < vertsNum; w++) {

            // Get the wedge vert's bearing from the target
            float bearing = step * w;

            // Get the point from the target w/ the given heading and distance
            GeoPoint p = GeoCalculations.pointAtDistance(_target, bearing,
                    distance);

            // Place the lat longs in the double array to be used by a later call to _buildCone()
            _coneCirclePoints[w * 2] = p.getLongitude();
            _coneCirclePoints[w * 2 + 1] = p.getLatitude();
        }

    }

    /**
     * Helper method to build the buffers and arrays that make up the necessary values to display
     * the cone
     */
    private void _buildCone() {

        // The cone will have 0.5 degrees between each point, we add one additional vert so that
        // the cone is not artificially smaller than the bounding angles
        int coneVertsNum = (int) Math.ceil(_headingWidth + 1);

        float heading = _renderRotation;

        // Get the back azimuth of the heading
        float back = ((heading) > 180f) ? heading - 180f : heading + 180f;

        float startHeading = (float) Math
                .floor((back - (_headingWidth / 2)));

        if (coneVertsNum <= 0)
            coneVertsNum = 2;

        LineString frontCone = new LineString(2);
        frontCone.addPoint(_target.getLongitude(), _target.getLatitude());

        // Convert the start heading to an index between 0 to 1440 that represents
        // the heading values between 0 to 359.5, add one to the start index so that it
        // correctly draws the starting part of the cone.
        int startIndex = (int) startHeading * 2 + 2;

        int w = startIndex;

        // XXX - TWO LOOPS ?!?!?!

        // Loop the wedge's vertices and build the outer point of the wedge

        for (int i = 0; i < coneVertsNum; i++) {
            // Update the index
            w = startIndex + i * 2;
            if (w >= _coneCirclePoints.length)
                w %= _coneCirclePoints.length;
            // Make sure the value is between 0 & 360
            while (w < 0)
                w += 720;
            w %= 720;

            // Grab the points of the cone from the circle of the same radius
            frontCone.addPoint(_coneCirclePoints[w], _coneCirclePoints[w + 1]);
        }
        // complete the ring
        frontCone.addPoint(_target.getLongitude(), _target.getLatitude());

        _coneText0.setSegment(new GeoPoint[] {
                _target, new GeoPoint(frontCone.getY(1), frontCone.getX(1))
        });
        _coneText1.setSegment(new GeoPoint[] {
                _target,
                new GeoPoint(frontCone.getY(frontCone.getNumPoints() - 2),
                        frontCone.getX(frontCone.getNumPoints() - 2))
        });

        if (_coneFront == null) {
            _coneFront = new GLBatchPolygon(orthoView);
            _coneFront.setAltitudeMode(Feature.AltitudeMode.ClampToGround);
        }
        _coneFront.setGeometry(new Polygon(frontCone));

        // cone back
        LineString backCone = new LineString(2);
        backCone.addPoint(_target.getLongitude(), _target.getLatitude());

        startIndex += 360;
        for (int i = 0; i < coneVertsNum; i++) {
            // Update the index
            w = startIndex + i * 2;
            if (w >= _coneCirclePoints.length)
                w %= _coneCirclePoints.length;
            // Make sure the value is between 0 & 360
            while (w < 0)
                w += 720;
            w %= 720;

            // Grab the points of the cone from the circle of the same radius
            backCone.addPoint(_coneCirclePoints[w], _coneCirclePoints[w + 1]);
        }
        // complete the ring
        backCone.addPoint(_target.getLongitude(), _target.getLatitude());

        _coneText2.setSegment(new GeoPoint[] {
                _target, new GeoPoint(backCone.getY(1), backCone.getX(1))
        });
        _coneText3.setSegment(new GeoPoint[] {
                _target,
                new GeoPoint(backCone.getY(backCone.getNumPoints() - 2),
                        backCone.getX(backCone.getNumPoints() - 2))
        });

        if (_coneBack == null) {
            _coneBack = new GLBatchPolygon(orthoView);
            _coneBack.setAltitudeMode(Feature.AltitudeMode.ClampToGround);
        }
        _coneBack.setGeometry(new Polygon(backCone));

        _coneInvalid = false;

        markSurfaceDirty(false);
    }

    private static ByteBuffer allocOrLimit(ByteBuffer buf, int capacity) {
        if (buf == null || buf.capacity() < capacity) {
            buf = Unsafe.allocateDirect(capacity);
            buf.order(ByteOrder.nativeOrder());
            return buf;
        } else {
            buf.clear();
            buf.limit(capacity);
            return buf;
        }
    }

    /**
     * Sets the color of the GL Pipeline
     *
     * @param color The color to set
     */
    private void _setColor(int color) {
        int alpha = Color.alpha(color);
        if (alpha < 255) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        } // XXX - else turn off blend ???

        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, alpha / 255f);
    }

    // **************************************** Private Classes
    // ****************************************//

    /**
     * The animation class to animate the swing of the arrow's heading/bearing value as it moves
     * from one angle to another smoothly.
     */
    private class AnimatedFloat {

        public AnimatedFloat(float init, float transTime, float min, float max,
                boolean isWrapped) {
            set(init);
            _target = get();
            _transTime = Math.max(0f, transTime);
            _min = min;
            _max = max;
            _range = _max - _min;
            _isWrapped = isWrapped;
        }

        public void animateTo(float target) {
            float wtarget = wrap(normalize(target));
            float wcurrent = wrap(get());
            _target = target;
            _start = get();
            _error = wrap(wtarget - wcurrent);
            if (!_isAnimating) {
                _setTimeMS = SystemClock.elapsedRealtime();
            }
            _isAnimating = true;
        }

        public float get() {
            if (_oneShotAnimate)
                return _current;
            else
                return _target;
        }

        private void set(float current) {
            _current = normalize(current);
        }

        public boolean update() {

            if (_isAnimating) {
                long nowMS = SystemClock.elapsedRealtime();
                float elapsedS = (nowMS - _setTimeMS) / 1000f;
                float alpha = elapsedS / _transTime;
                float t = _scurve(alpha);
                if (alpha >= 1f) {
                    set(_target);
                    _isAnimating = false;
                    return true;
                } else {
                    set(_start + _error * t);
                }
            }
            return _isAnimating;
        }

        private float normalize(float value) {
            float v = value;
            if (v < _min) {
                while (v < _min) {
                    v += _max;
                }
            } else if (v >= _max) {
                while (v >= _max) {
                    v -= _max;
                }
            }
            return v;
        }

        private float wrap(float value) {
            if (_isWrapped) {
                if (value > _range / 2f) {
                    return value - _max;
                } else if (value < -(_range / 2f)) {
                    return value + _max;
                }
            }
            return value;
        }

        private float _scurve(float x) {
            float xx = x * x;
            float xxx = xx * x;
            return 3 * xx - 2 * xxx;
        }

        private boolean _isAnimating = false;
        private float _current;
        private float _start;
        private float _target;
        private float _error;
        private float _transTime = 2f;
        private long _setTimeMS = 0;
        private float _min = -Float.MAX_VALUE;
        private float _max = Float.MAX_VALUE;
        private float _range = 0f;
        private boolean _isWrapped = false;
    }
}
