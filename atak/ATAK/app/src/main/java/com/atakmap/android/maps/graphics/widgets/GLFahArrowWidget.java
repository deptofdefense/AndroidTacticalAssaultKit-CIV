
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
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import com.atakmap.coremap.locale.LocaleUtil;

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
        OnDesignatorPointChangedListener {

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

                    // draw the widget onto the map surface
                    if (MathUtils.hasBits(pass,
                            GLMapView.RENDER_PASS_SURFACE)) {
                        // duplicate GLWidgetsLayer translation
                        GLES20FixedPipeline.glPushMatrix();
                        GLES20FixedPipeline.glTranslatef(0,
                                LegacyAdapters.getRenderContext(this.context)
                                        .getRenderSurface()
                                        .getHeight() - 1,
                                0f);
                        this.impl.drawWidgetImpl();
                        GLES20FixedPipeline.glPopMatrix();
                    }

                    // update the touch point
                    if (MathUtils.hasBits(pass,
                            GLMapView.RENDER_PASS_SPRITES)) {
                        if (this.impl._visible && this.impl._target != null)
                            this.impl._setHitPoint();
                    }
                }

                @Override
                public void release() {
                    if (this.impl != null) {
                        this.impl
                                .stopObserving(
                                        ((FahArrowWidget.Item) this.subject)
                                                .getFAH());
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

    // arrays to hold the values for the arrow GL object
    private byte[] _arrowLineIndicesB;
    private byte[] _arrowIndicesB;
    private float[] _arrowVertsB;

    // The associated buffers for the arrow GL object
    private ByteBuffer _arrowLineIndices;
    private ByteBuffer _arrowIndices;
    private ByteBuffer _arrowVerts;

    // The buffers for the associated cone GL Object
    private FloatBuffer _coneVertsFront;
    private FloatBuffer _coneVertsBack;

    private ByteBuffer _coneIndicesFront;
    private ByteBuffer _coneIndicesBack;
    private ByteBuffer _coneLineIndicesFront;
    private ByteBuffer _coneLineIndicesBack;

    // The arrays that hold the lat & longs of the cone
    // the _coneCirclePoints updates every time the target/designator move
    // and creates an array of points in a circle from the specified x2.5 range
    // _conePoints then grabs those associated values when the cone is moving
    // to avoid geoprojection computations while the user is adjusting the FAH
    private DoubleBuffer _conePointsFront;
    private DoubleBuffer _conePointsBack;
    private double[] _coneCirclePoints;

    // The GL texts for the widget
    private GLText2 _arrowText;
    private GLText2 _coneText0, _coneText1, _coneText2, _coneText3;
    private GLText2[] _textArray;
    private RectF _view;
    private PointF _targetF;
    private GeoPoint _hitPoint;

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

    /**
     * the rotation that should be applied when RENDERING the arrow and cone,
     * defined as TRUE rotation.
     */
    private float _renderRotation;

    private boolean _coneInvalid;

    private PointD _arrowCenterUnscaled;

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
    }

    private void validateTextArray() {
        if (_textArray == null) {
            MapTextFormat mtf = MapView.getTextFormat(Typeface.DEFAULT, +2);
            _arrowText = new GLText2(mtf, String.format(
                    LocaleUtil.getCurrent(), "%03.0f",
                    359f));
            _coneText0 = new GLText2(mtf, String.format(
                    LocaleUtil.getCurrent(), "%03.0f",
                    359f));
            _coneText1 = new GLText2(mtf, String.format(
                    LocaleUtil.getCurrent(), "%03.0f",
                    359f));
            _coneText2 = new GLText2(mtf, String.format(
                    LocaleUtil.getCurrent(), "%03.0f",
                    359f));
            _coneText3 = new GLText2(mtf, String.format(
                    LocaleUtil.getCurrent(), "%03.0f",
                    359f));

            // Build the arrow's vertices, and indices just once

            _textArray = new GLText2[] {
                    _coneText0, _coneText1, _coneText2, _coneText3
            };
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
        return new RectF(0f + 20,
                top - (MapView.getMapView().getActionBarHeight() + 20),
                right - 20, 0f + 20);
    }

    @Override
    public void drawWidgetContent() {
        // Doesn't need to be overriden since drawWidget is overriden
    }

    @Override
    public void drawWidget() {
        if (this.drawWidgetImpl()) {
            // Set the hit point of this widget to be the touch circle
            // Draw the touch circle at the end of the arrow and above everything else
            _setHitPoint();

            // FAH is animate, request refresh
            orthoView.requestRefresh();
        }
    }

    private boolean drawWidgetImpl() {
        if (!_visible)
            return false;

        // clear the hit point
        _hitPoint = null;

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
        // Adjust when drawing the arrow to center on the target's position
        GLES20FixedPipeline.glTranslatef(xPos, yPos, 0f);
        _drawArrow(orthoView);
        _computeHitPoint(_headingAnimation.get());
        GLES20FixedPipeline.glPopMatrix();

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
                if (touchable) {
                    _headingTouch.setAlpha(0.6f);
                } else {
                    _headingTouch.setAlpha(0f);
                }
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

    private void _drawArrow(GLMapView view) {

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

        // compute the IMAGE SPACE rotation to be applied. '_renderRotation' is
        // the magnetic heading angle in GEODETIC SPACE.

        // get image space coordinate for '_target'
        view.forward(_target, view.scratch.pointF);
        final float sx = view.scratch.pointF.x;
        final float sy = view.scratch.pointF.y;

        // get image space coordinate approximately 100px from '_target'
        view.forward(DistanceCalculations.metersFromAtBearing(_target,
                view.drawMapResolution * 100, _renderRotation),
                view.scratch.pointF);
        final float ex = view.scratch.pointF.x;
        final float ey = view.scratch.pointF.y;

        // compute relative rotation
        final float theta = (float) Math.toDegrees(Math.atan2((ey - sy),
                (ex - sx)));

        // Rotate the matrix and position it to draw the arrrow
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glRotatef(theta + 180f, 0, 0, 1f);
        GLES20FixedPipeline.glTranslatef(_radius * 1.05f + 0, 0, 0);
        GLES20FixedPipeline.glScalef(200f, 200f, 1f);

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

        // Rotate and move the matrix about half way up the arrow
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glRotatef(theta + 180, 0, 0, 1f);
        GLES20FixedPipeline.glTranslatef(_radius * 1.05f + 200f / 2f, 0, 0);
        GLES20FixedPipeline.glRotatef(-theta + 180, 0, 0,
                1f);

        // Now that we have the point - remove the rotation of the screen so that the text
        // always displays horizontally
        // GLES20FixedPipeline.glRotatef(-(float)orthoView.drawRotation, 0f, 0f, 1f);

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
        // Popping this matrix brings us back to the screen rotation
        GLES20FixedPipeline.glPopMatrix();

        // Draw the touch circle at the end of the arrow and above everything else
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glRotatef(theta, 0, 0, 1f);
        GLES20FixedPipeline.glTranslatef(-210f - _radius, 0, 0);
        GLES20FixedPipeline.glRotatef(-theta, 0, 0, 1f);
        _headingTouch.draw();
        GLES20FixedPipeline.glPopMatrix();
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
        GLText2 text = _textArray[index];
        if (text == null || _view == null || _targetF == null) {
            return;
        }

        PointF vertF;
        if (index == 0 || index == 1) {
            int vertIndex = 2;
            if (index == 1) {
                vertIndex = _coneVertsFront.limit() - 2;
            }
            vertF = new PointF(_coneVertsFront.get(vertIndex),
                    _coneVertsFront.get(vertIndex + 1));
        } else {
            int vertIndex = 2;
            if (index == 3) {
                vertIndex = _coneVertsBack.limit() - 2;
            }
            vertF = new PointF(_coneVertsBack.get(vertIndex),
                    _coneVertsBack.get(vertIndex + 1));
        }

        float[] tarray = _getTextPoint(text, _targetF, vertF);
        PointF textF = new PointF(tarray[0], tarray[1]);
        float dF = tarray[2];

        // If those points are outside of the current view of the map
        // then we need to slide them down or up so the are still in the
        // field of view for the user
        if (_view.left > textF.x || textF.x > _view.right
                || _view.bottom > textF.y
                || textF.y > _view.top) {
            // If not contained then we need to put it at the edge
            PointF intersect = _getIntersectionPoint(_view, _targetF, vertF);
            if (intersect != null && !Float.isNaN(intersect.x)
                    && !Float.isNaN(intersect.y)) {
                tarray = _getTextPoint(text, _targetF, intersect);
                textF = new PointF(tarray[0], tarray[1]);
                dF = tarray[2];
            } else {
                textF = new PointF(Float.NaN, Float.NaN);
            }
        }

        // Grab the black text backdrop texture
        GLNinePatch smallNinePatch = GLRenderGlobals.get(getSurface())
                .getSmallNinePatch();
        if (smallNinePatch != null) {
            // Check to see if we should draw the text
            if (text != null && !Float.isNaN(textF.x) && !Float.isNaN(textF.y)
                    && dF > 320f) {

                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(textF.x, textF.y, 0f);

                GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(-(text.getWidth() / 2f + 8f),
                        -text.getHeight() / 2f, 0f);
                smallNinePatch.draw(text.getWidth() + 16f, text.getHeight());
                GLES20FixedPipeline.glPopMatrix();

                // Shift the matrix over to center the text at the location
                GLES20FixedPipeline.glTranslatef(-text.getWidth() / 2f,
                        -text.getHeight() / 4f, 0f);

                // Draw the text over the black backdrop
                _setColor(_TEXT_COLOR);
                text.setText(_getConeText(index));
                text.draw(_TEXT_COLOR);

                GLES20FixedPipeline.glPopMatrix();
            }
        }
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
        // Project the double array of geopoints to the mapview's x and y floats
        _projectPoints();

        GLES20FixedPipeline.glPushMatrix();

        //fill the wedge with either white or red depending on the offset
        if ((_designator != null && !_fakeDesignator) &&
                (coneBetween(350, 10) || coneBetween(170, 190))) {
            _setColor(_CONE_COLOR_ALT);
        } else {
            _setColor(_CONE_COLOR);
        }
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _coneVertsFront);
        GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_TRIANGLES,
                _coneIndicesFront.limit(),
                GLES20FixedPipeline.GL_UNSIGNED_BYTE,
                _coneIndicesFront);

        // Stroke around the edge in black
        _setColor(_STROKE_COLOR);
        GLES20FixedPipeline.glLineWidth(1f);
        GLES20FixedPipeline
                .glDrawElements(GLES20FixedPipeline.GL_LINE_LOOP,
                        _coneLineIndicesFront.limit(),
                        GLES20FixedPipeline.GL_UNSIGNED_BYTE,
                        _coneLineIndicesFront);
        if (_subject.drawReverse()) {
            GLES20FixedPipeline.glVertexPointer(2,
                    GLES20FixedPipeline.GL_FLOAT, 0,
                    _coneVertsBack);
            if ((_designator != null && !_fakeDesignator) &&
                    (coneBetween(350, 10) || coneBetween(170, 190))) {
                _setColor(_CONE_COLOR_ALT);
            } else {
                _setColor(_CONE_COLOR);
            }
            GLES20FixedPipeline
                    .glDrawElements(GLES20FixedPipeline.GL_TRIANGLES,
                            _coneIndicesBack.limit(),
                            GLES20FixedPipeline.GL_UNSIGNED_BYTE,
                            _coneIndicesBack);
            _setColor(_STROKE_COLOR);
            GLES20FixedPipeline
                    .glDrawElements(GLES20FixedPipeline.GL_LINE_LOOP,
                            _coneLineIndicesBack.limit(),
                            GLES20FixedPipeline.GL_UNSIGNED_BYTE,
                            _coneLineIndicesBack);
        }

        // Exit the drawing state
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glPopMatrix();

        // Get the bounding view available
        _view = this.getWidgetViewF();

        // XXX - use Unsafe

        // Get the target, and side vertices of the cone
        _targetF = new PointF(_coneVertsFront.get(0),
                _coneVertsFront.get(1));

        GLES20FixedPipeline.glPushMatrix();

        drawTextAngle(0);
        drawTextAngle(1);

        if (_subject.drawReverse()) {
            drawTextAngle(2);
            drawTextAngle(3);
        }

        // Popping this matrix brings us back to the screen rotation
        GLES20FixedPipeline.glPopMatrix();
    }

    /**
     * Returns the text point for the offset and the distance from target.
     *
     * @param text The GLText to get the width & height from
     * @param c The Center point
     * @param v The Vertex point
     * @return The float array with [x, y, distance]
     */
    private float[] _getTextPoint(GLText2 text, PointF c, PointF v) {
        float nx = 0f;
        float ny = 0f;
        // Get the slope to the center point
        float dx = (c.x - v.x);
        float dy = (c.y - v.y);
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (dx != 0 && dy != 0) {
            nx = dx / d;
            ny = dy / d;
        }
        // Get half the diagonal of the text box
        float w = text.getWidth();
        float h = text.getHeight();
        float dT = (float) Math.sqrt(w * w + h * h) / 2;

        return new float[] {
                v.x + dT * nx, v.y + dT * ny, d, Math.copySign(1, -dx),
                Math.copySign(1, -dy)
        };
    }

    private PointF _getIntersectionPoint(RectF r, PointF cF, PointF vF) {

        if (r.left < cF.x && cF.x < r.right && r.bottom < cF.y && cF.y < r.top
                &&
                r.left < vF.x && vF.x < r.right && r.bottom < vF.y
                && vF.y < r.top) {
            return null;
        }

        PointF ret = null;// new PointF(0f, 0f);
        Vector2D[] rets = new Vector2D[4];
        Vector2D c = new Vector2D(cF.x, cF.y);
        Vector2D v = new Vector2D(vF.x, vF.y);

        Vector2D topLeft = new Vector2D(r.left, r.top);
        Vector2D topRight = new Vector2D(r.right, r.top);
        Vector2D botRight = new Vector2D(r.right, r.bottom);
        Vector2D botLeft = new Vector2D(r.left, r.bottom);

        // Start at top line and go clockwise

        rets[0] = Vector2D
                .segmentToSegmentIntersection(topLeft, topRight, c, v);
        rets[1] = Vector2D.segmentToSegmentIntersection(topRight, botRight, c,
                v);
        rets[2] = Vector2D
                .segmentToSegmentIntersection(botRight, botLeft, c, v);
        rets[3] = Vector2D.segmentToSegmentIntersection(botLeft, topLeft, c, v);

        float farthest = Float.MIN_VALUE;

        // Check the returned values - we want the farthest from the target
        for (int i = 0; i < 4; i++) {
            // Check to see if it intersected
            if (rets[i] != null) {
                float distance = (float) c.distance(rets[i]);
                // If new farthest then set as new return point
                if (distance > farthest) {
                    farthest = distance;
                    if (ret == null) {
                        ret = new PointF(0f, 0f);
                    }
                    ret.set((float) rets[i].x, (float) rets[i].y);
                }
            }
        }

        return ret;
    }

    /**
     * Sets the hit point of this widget as the center of the touch circle, usually this would be
     * the center point of the widget. But this widget centers around the target.
     * @param headingM the heading in magnetic.
     */
    private void _computeHitPoint(float headingM) {
        // Converting from the OpenGL 2D space to Android 2D touch space
        // main difference is that the map's OpenGL space has y = 0 at the bottom of the screen,
        // android starts y = 0 at the top of the screen - so the hit point has to be flipped along
        // the y-axis

        final double distance = DistanceCalculations.computeDirection(
                _designator,
                _target)[0];

        final float heading = (float) ATAKUtilities.convertFromMagneticToTrue(
                _target, headingM);

        // Get the back azimuth of the heading
        double back = ((heading) > 180d) ? heading - 180d : heading + 180d;

        // Find the end point of where the arrow should be given this heading
        GeoPoint end = DistanceCalculations.computeDestinationPoint(_target,
                back, distance);

        final GLMapView view = getSurface().getGLMapView();

        PointF endF = view.forward(adjustedGeoPoint(view, end));
        PointF targetF = view.forward(adjustedGeoPoint(view, _target));

        // Gives the adjusted angle from the x axis if we use Math.atan2(y,x) - so we need to adjust
        // it again by -90
        float adjusted = (float) Math.atan2(endF.x - targetF.x, endF.y
                - targetF.y);

        // Adjust from the target point and set as the location
        targetF.x += 310f * Math.sin(adjusted);
        targetF.y += 310f * Math.cos(adjusted);

        _hitPoint = view.inverse(targetF);
    }

    private void _setHitPoint() {
        if (_hitPoint != null) {
            final GLMapView view = getSurface().getGLMapView();

            PointF hitPointF = view.forward(adjustedGeoPoint(view, _hitPoint));
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

    private void _update(FahArrowWidget arrow, Update u) {

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
                                double newRange = DistanceCalculations
                                        .computeDirection(_designator,
                                                _target)[0];
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
            GeoPoint p = DistanceCalculations.computeDestinationPoint(_target,
                    bearing, distance);

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

        // Vertices will go target, left-side of cone, right-side of cone, back to target
        // Allocate the buffers for the wedge vertices number
        final int conePointCapacity = ((coneVertsNum + 1) * 2);
        if (_conePointsFront == null
                || _conePointsFront.capacity() < conePointCapacity) {
            ByteBuffer buf = com.atakmap.lang.Unsafe
                    .allocateDirect(conePointCapacity * 8);
            buf.order(ByteOrder.nativeOrder());
            _conePointsFront = buf.asDoubleBuffer();
        } else {
            _conePointsFront.clear();
        }
        if (_conePointsBack == null
                || _conePointsBack.capacity() < conePointCapacity) {
            ByteBuffer buf = com.atakmap.lang.Unsafe
                    .allocateDirect(conePointCapacity * 8);
            buf.order(ByteOrder.nativeOrder());
            _conePointsBack = buf.asDoubleBuffer();
        } else {
            _conePointsBack.clear();
        }
        if (_coneVertsFront == null
                || _coneVertsFront.capacity() < conePointCapacity) {
            ByteBuffer bbF = com.atakmap.lang.Unsafe
                    .allocateDirect(conePointCapacity * 4);
            bbF.order(ByteOrder.nativeOrder());
            _coneVertsFront = bbF.asFloatBuffer();
        } else {
            _coneVertsFront.clear();
            _coneVertsFront.limit(conePointCapacity);
        }
        if (_coneVertsBack == null
                || _coneVertsBack.capacity() < conePointCapacity) {
            ByteBuffer bbB = com.atakmap.lang.Unsafe
                    .allocateDirect(conePointCapacity * 4);
            bbB.order(ByteOrder.nativeOrder());
            _coneVertsBack = bbB.asFloatBuffer();
        } else {
            _coneVertsBack.clear();
            _coneVertsBack.limit(conePointCapacity);
        }
        _coneIndicesFront = allocOrLimit(_coneIndicesFront,
                ((coneVertsNum - 1) * 3 * 2));
        _coneIndicesBack = allocOrLimit(_coneIndicesBack,
                ((coneVertsNum - 1) * 3 * 2));
        _coneLineIndicesFront = allocOrLimit(_coneLineIndicesFront,
                ((coneVertsNum + 1) * 2));
        _coneLineIndicesBack = allocOrLimit(_coneLineIndicesBack,
                ((coneVertsNum + 1) * 2));

        // Initialize with the first point
        _coneLineIndicesFront.put((byte) 0);

        Unsafe.put(_conePointsFront, _target.getLongitude(),
                _target.getLatitude());

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
            Unsafe.put(_conePointsFront, _coneCirclePoints[w],
                    _coneCirclePoints[w + 1]);

            // Update the Buffers with the values
            // Indices gives the fill and lineIndices gives the order of the outline
            if (i != coneVertsNum - 1) {
                byte[] triangleIndices = new byte[] {
                        0, (byte) (i + 1), (byte) (i + 2)
                };
                _coneIndicesFront.put(triangleIndices);
            }
            _coneLineIndicesFront.put((byte) (i + 1));
        }
        // set the buffer limit and reset the position
        _conePointsFront.flip();
        _coneIndicesFront.flip();
        _coneLineIndicesFront.flip();

        // cone back
        _coneLineIndicesBack.put((byte) 0);

        Unsafe.put(_conePointsBack, _target.getLongitude(),
                _target.getLatitude());
        //_conePointsBack[0] = _target.getLongitude();
        //_conePointsBack[1] = _target.getLatitude();

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
            Unsafe.put(_conePointsBack, _coneCirclePoints[w],
                    _coneCirclePoints[w + 1]);
            //_conePointsBack[(i + 1) * 2] = _coneCirclePoints[w];
            //_conePointsBack[(i + 1) * 2 + 1] = _coneCirclePoints[w + 1];

            // Update the Buffers with the values
            // Indices gives the fill and lineIndices gives the order of the outline
            if (i != coneVertsNum - 1) {
                byte[] triangleIndices = new byte[] {
                        0, (byte) (i + 1), (byte) (i + 2)
                };
                _coneIndicesBack.put(triangleIndices);
            }
            _coneLineIndicesBack.put((byte) (i + 1));
        }
        // set the buffer limit and reset the position
        _conePointsBack.flip();
        _coneIndicesBack.flip();
        _coneLineIndicesBack.flip();

        _coneInvalid = false;
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

    /**
     * Helper function to add a GeoPoint to a FloatBuffer as a projected float point.
     *
     */
    private void _projectPoints() {
        this.orthoView.forward(_conePointsFront, _coneVertsFront);
        this.orthoView.forward(_conePointsBack, _coneVertsBack);
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
