
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Marker.OnIconChangedListener;
import com.atakmap.android.maps.Marker.OnStateChangedListener;
import com.atakmap.android.maps.Marker.OnStyleChangedListener;
import com.atakmap.android.maps.Marker.OnSummaryChangedListener;
import com.atakmap.android.maps.Marker.OnTitleChangedListener;
import com.atakmap.android.maps.Marker.OnTrackChangedListener;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitRect;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.opengl.GLLabelManager;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLText;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GLMarker2 extends GLPointMapItem2 implements
        OnIconChangedListener,
        OnStateChangedListener, OnTitleChangedListener,
        OnTrackChangedListener,
        OnStyleChangedListener,
        OnSummaryChangedListener,
        MapItem.OnHeightChangedListener,
        GLMapBatchable2, Marker.OnLabelTextSizeChangedListener,
        Marker.OnLabelPriorityChangedListener {

    public static final String TAG = "GLMarker";

    private final static int SELECTED_COLOR = Color.argb(127, 255, 255, 255);
    private final static double ICON_SCALE = 1d;

    private final static double DEFAULT_MIN_RENDER_SCALE = (1.0d / 100000.0d);

    private final Marker _subject;
    private int state;
    private GLIcon _icon;
    private int _iconVisibility;
    private final GLTriangle.Fan _verts;
    private int _labelID = GLLabelManager.NO_ID;
    private int _labelOffX = 0;
    private int _labelOffY = 0;
    private float _labelOffZ = 0f;
    private final PointD _point = new PointD();
    private GLLabelManager.VerticalAlignment _labelVAlign = GLLabelManager.VerticalAlignment.Bottom;
    private int _textColor;
    private String _text = "";
    private final HitRect _hitRect = new HitRect();
    private ByteBuffer _borderVerts;
    private float _heading = 0f;
    private double _height = 0d; // height in meters
    private int _style;
    private int _color = Color.WHITE;
    private GLImage _alertImage;
    private int textRenderFlag;
    private boolean _labelVisible;

    private int _labelTextSize;
    private final Typeface _labelTypeface;

    private static GLImageCache.Entry _alertImageEntry;
    private static ByteBuffer tiltLineBuffer = null;
    private static long tiltLineBufferPtr = 0L;

    private final GLLabelManager _labelManager;
    private String _extraLinesText;

    private boolean _nadirClamp;

    GLLabelManager.Priority _labelPriority = GLLabelManager.Priority.Standard;

    public GLMarker2(MapRenderer surface, Marker subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES);
        _subject = subject;
        state = subject.getState();
        textRenderFlag = subject.getTextRenderFlag();
        _verts = new GLTriangle.Fan(2, 4);
        _labelTextSize = subject.getLabelTextSize();
        _labelTypeface = subject.getLabelTypeface();
        _labelManager = ((GLMapView) surface).getLabelManager();
        initState(subject);
    }

    public String toString() {
        return "marker_marker"; // _infoText.toString();
    }

    private void initState(Marker subject) {
        final Icon icon = subject.getIcon();
        final String title = getTitle(subject);
        final String extraLines = getExtraLines(subject);
        final float heading = (float) subject.getTrackHeading();
        final int style = subject.getStyle();
        final int textColor = subject.getTextColor();
        final int trf = subject.getTextRenderFlag();
        final double height = subject.getHeight();

        onLabelPriorityChanged(subject);

        _buildArrow();

        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                // Selection highlight box
                _borderVerts = GLTriangle.Strip.createBuffer(
                        GLTriangle.Strip.createRectangle(-32, -32, 64, 64,
                                null),
                        _borderVerts);

                textRenderFlag = trf;

                _height = height;

                refreshLabel(title, extraLines);

                _updateIcon(icon, state);
                _setHeading(heading);
                _style = style;

                setTextColor(textColor);
            }
        });
    }

    private String getTitle(Marker marker) {
        String title = marker.getTitle();
        if (title == null)
            return "";
        int nl = title.indexOf("\n");
        return nl > -1 ? title.substring(0, nl) : title;
    }

    private String getExtraLines(Marker marker) {
        String title = marker.getTitle();
        if (title == null)
            title = "";
        int nl = title.indexOf("\n");
        if (nl > -1)
            title = title.substring(nl + 1);
        else
            title = "";
        String summary = marker.getSummary();
        if (FileSystemUtils.isEmpty(summary))
            return title;
        if (FileSystemUtils.isEmpty(title))
            return summary;
        return title + "\n" + summary;
    }

    private void removeLabel() {
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (_labelID != GLLabelManager.NO_ID) {
                    _labelManager.removeLabel(_labelID);
                    _labelID = GLLabelManager.NO_ID;
                }
            }
        });
    }

    private boolean ensureLabel(GLMapView view) {
        if (_labelID == GLLabelManager.NO_ID) {
            _labelID = _labelManager.addLabel();
            MapTextFormat mapTextFormat = new MapTextFormat(_labelTypeface,
                    _labelTextSize);
            _labelManager.setTextFormat(_labelID, mapTextFormat);
            _labelManager.setFill(_labelID, true);
            _labelManager.setBackgroundColor(_labelID,
                    Color.argb(153 /*=60%*/, 0, 0, 0));
            _labelManager.setVerticalAlignment(_labelID, _labelVAlign);
            _labelManager.setDesiredOffset(_labelID, _labelOffX, _labelOffY,
                    _labelOffZ);
            _labelManager.setVisible(_labelID,
                    _labelVisible = shouldDrawLabel(view));
            _labelManager.setAltitudeMode(_labelID, getAltitudeMode());
            _labelManager.setPriority(_labelID, _labelPriority);
            return true;
        } else {
            return false;
        }
    }

    private void setTextColor(int textColor) {
        if (_textColor != textColor) {
            _textColor = textColor;
            if (_labelID != GLLabelManager.NO_ID)
                _labelManager.setColor(_labelID, textColor);
        }
    }

    private void updateLabelVisibility(GLMapView view) {
        if (ensureLabel(view)) {
            // label was newly created, init geometry, text, etc.
            if (_text != null && !_text.isEmpty()) {
                String fullText = _text;
                if (_extraLinesText != null && !_extraLinesText.isEmpty())
                    fullText = fullText + "\n" + _extraLinesText;
                _labelManager.setText(_labelID, fullText);
            }
            refreshLabelGeometry();
            _labelManager.setColor(_labelID, _textColor);
        }

        // if the displayLables preference is checked display the text if
        // the marker requested to always have the text show or if the scale is zoomed in enough

        boolean labelVisible = shouldDrawLabel(view);
        if (labelVisible != _labelVisible)
            _labelManager.setVisible(_labelID, _labelVisible = labelVisible);

        int offx = 0;
        int offy = 0;
        float offz = 0f;

        boolean adjustForIcon = (view.currentPass.drawTilt > 0d);
        if (_icon != null) {
            adjustForIcon |= (_iconVisibility != Marker.ICON_GONE);
            if (_icon.getHeight() > 0 && adjustForIcon) {
                offy = (int) (ICON_SCALE
                        * -(_icon.getHeight() - _icon.getAnchorY() - 1));
                if (view.currentPass.drawTilt > 0d) {
                    // flip offset relative to top
                    offy = -offy;

                    // account for adjustment in `getDrawPosition`
                    // move up half icon height
                    if (_iconVisibility != Marker.ICON_GONE) {
                        offy += (_icon.getHeight() / 2d);
                        offz += (float) (-0.00025
                                * Math.cos(Math
                                        .toRadians(view.currentPass.drawTilt)));
                    }
                }
            }

            if (_icon.getWidth() > 0) {
                offx = (int) (ICON_SCALE
                        * ((_icon.getWidth() / 2f) - _icon.getAnchorX()));
            }
        }
        GLLabelManager.VerticalAlignment verticalAlignment;
        if (adjustForIcon) {
            verticalAlignment = (view.currentPass.drawTilt > 0d)
                    ? GLLabelManager.VerticalAlignment.Top
                    : GLLabelManager.VerticalAlignment.Bottom;
        } else {
            verticalAlignment = GLLabelManager.VerticalAlignment.Middle;
        }

        if (offx != _labelOffX || offy != _labelOffY || offz != _labelOffZ) {
            _labelOffX = offx;
            _labelOffY = offy;
            _labelOffZ = offz;
            _labelManager.setDesiredOffset(_labelID, offx, offy, offz);
        }

        if (verticalAlignment != _labelVAlign) {
            _labelVAlign = verticalAlignment;
            _labelManager.setVerticalAlignment(_labelID, verticalAlignment);
        }

        final boolean shouldMarquee = GLMapSurface.SETTING_shortenLabels
                && (_style & Marker.STYLE_MARQUEE_TITLE_MASK) != 0;
        if (shouldMarquee)
            _labelManager.setHints(_labelID, _labelManager.getHints(_labelID)
                    | GLLabelManager.HINT_SCROLLING_TEXT);
        else
            _labelManager.setHints(_labelID, _labelManager.getHints(_labelID)
                    & ~GLLabelManager.HINT_SCROLLING_TEXT);
    }

    private void refreshLabel(String title, String extraLines) {
        if (title != null)
            _text = title;
        else
            _text = "";
        _text = GLText.localize(_text);
        _extraLinesText = extraLines;

        if (_labelID != GLLabelManager.NO_ID) {
            String fullText = _text;
            if (extraLines != null && !extraLines.isEmpty()) {
                fullText = fullText + "\n" + extraLines;
            }

            _labelManager.setText(_labelID, fullText);
        }
    }

    private void updateHitRect() {
        float x = (float) _point.x;
        float y = (float) _point.y;
        float w = 0, h = 0;
        if (_icon != null && _iconVisibility != Marker.ICON_GONE) {
            // Icon is visible - use icon dimensions
            w = _icon.getWidth();
            h = _icon.getHeight();
        } else if (_labelID != GLLabelManager.NO_ID) {
            // Icon is not visible - use the label dimensions
            Rectangle r = new Rectangle();
            _labelManager.getSize(_labelID, r);
            _hitRect.set((float) r.X,
                    (float) r.Y,
                    (float) (r.X + r.Width),
                    (float) (r.Y + r.Height));
            return;
        }
        w /= 2;
        h /= 2;
        _hitRect.set(x - w, y - h, x + w, y + h);
    }

    @Override
    public void startObserving() {
        final Marker marker = (Marker) this.subject;
        super.startObserving();
        marker.addOnIconChangedListener(this);
        marker.addOnStateChangedListener(this);
        marker.addOnTitleChangedListener(this);
        marker.addOnSummaryChangedListener(this);
        marker.addOnTrackChangedListener(this);
        marker.addOnStyleChangedListener(this);
        marker.addOnLabelSizeChangedListener(this);
        marker.addOnLabelPriorityChangedListener(this);
        marker.addOnHeightChangedListener(this);
        initState(marker);
    }

    @Override
    public void stopObserving() {
        final Marker marker = (Marker) this.subject;
        super.stopObserving();
        removeLabel();
        marker.removeOnIconChangedListener(this);
        marker.removeOnStateChangedListener(this);
        marker.removeOnTitleChangedListener(this);
        marker.removeOnSummaryChangedListener(this);
        marker.removeOnTrackChangedListener(this);
        marker.removeOnStyleChangedListener(this);
        marker.removeOnLabelSizeChangedListner(this);
        marker.removeOnLabelPriorityChangedListener(this);
        marker.removeOnHeightChangedListener(this);
    }

    @Override
    public void onIconChanged(Marker marker) {
        final Icon icon = marker.getIcon();
        final int iconVis = marker.getIconVisibility();
        final int state = marker.getState();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _releaseIcon();
                _updateIcon(icon, state);
                _iconVisibility = iconVis;
            }
        });
    }

    @Override
    public void onStyleChanged(Marker marker) {
        final int style = marker.getStyle();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _style = style;
            }
        });
    }

    @Override
    public void onLabelSizeChanged(final Marker marker) {
        final int labelTextSize = marker.getLabelTextSize();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _labelTextSize = labelTextSize;
                onTitleChanged(marker);
            }
        });
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        super.onPointChanged(item);
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                refreshLabelGeometry();
            }
        });
    }

    private void refreshLabelGeometry() {
        if (_labelID != GLLabelManager.NO_ID) {
            final Point p = getLabelPoint();
            _labelManager.setGeometry(_labelID, p);
            _labelManager.setAltitudeMode(_labelID, getAltitudeMode());
        }
    }

    private Point getLabelPoint() {
        double alt = point.getAltitude();
        if (Double.isNaN(alt))
            alt = 0d;
        final double height = _height;
        if (!_nadirClamp && !Double.isNaN(height))
            alt += height;
        return new Point(point.getLongitude(), point.getLatitude(), alt);
    }

    @Override
    public void onStateChanged(Marker marker) {
        final Icon icon = marker.getIcon();
        final int sstate = marker.getState();
        final int trf = marker.getTextRenderFlag();
        final int textColor = marker.getTextColor();

        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                textRenderFlag = trf;
                // toggle `displayLabel` to force state reset
                state = sstate;
                setTextColor(textColor);

                _updateIcon(icon, sstate);
            }
        });
    }

    @Override
    public void onTitleChanged(final Marker marker) {
        final String title = getTitle(marker);
        final String extraLines = getExtraLines(marker);
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                refreshLabel(title, extraLines);
            }
        });
    }

    @Override
    public void onSummaryChanged(Marker marker) {
        final String title = getTitle(marker);
        final String extraLines = getExtraLines(marker);
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                refreshLabel(title, extraLines);
            }
        });
    }

    @Override
    public void onLabelPriorityChanged(Marker marker) {
        final Marker.LabelPriority p = marker.getLabelPriority();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (p == Marker.LabelPriority.High)
                    _labelPriority = GLLabelManager.Priority.High;
                else if (p == Marker.LabelPriority.Low)
                    _labelPriority = GLLabelManager.Priority.Low;
                else
                    _labelPriority = GLLabelManager.Priority.Standard;

                if (_labelID != GLLabelManager.NO_ID)
                    _labelManager.setPriority(_labelID, _labelPriority);
            }
        });
    }

    @Override
    public void onTrackChanged(Marker marker) {
        final float heading = (float) marker.getTrackHeading();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _setHeading(heading);
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
    }

    private void _setColor(int color) {
        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    private void _buildArrow() {

        float tip = (float) Math.ceil(20f * MapView.DENSITY);
        final double size = Math.ceil(6f * MapView.DENSITY);

        float[] points = new float[8];

        points[0] = (float) (Math.cos(-2.356194) * size);
        points[1] = tip + (float) (Math.sin(-2.356194) * size);

        points[2] = 0;
        points[3] = tip;

        points[4] = (float) (Math.cos(-.785398) * size); // one side
        points[5] = tip + (float) (Math.sin(-.785398) * size);

        points[6] = points[0];
        points[7] = points[1];

        _verts.setPoints(points);
    }

    /**
     * Get the current altitude mode which takes into account the
     * {@link ClampToGroundControl}
     * @return Altitude mode
     */
    @Override
    protected AltitudeMode getAltitudeMode() {
        return _nadirClamp ? AltitudeMode.ClampToGround
                : super.getAltitudeMode();
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        if (this.subject == null)
            return;

        // Check if we need to update the points based on if the map is tilted
        checkNadirClamp(ortho);

        updateDrawPosition(ortho);
        float xpos = (float) _point.x;
        float ypos = (float) _point.y;
        float zpos = (float) _point.z;

        // draw a line segment from the center of the point into the earth's
        // surface
        if (shouldDrawLollipop(ortho)) {
            final double terrain = validateLocalElevation(ortho);
            double anchorEl = Math.min(terrain, 0d);
            ortho.scratch.geo.set(
                    (terrain + GLMapView.elevationOffset)
                            * ortho.elevationScaleFactor);

            ortho.scene.forward(ortho.scratch.geo, ortho.scratch.pointD);
            float x1 = (float) ortho.scratch.pointD.x;
            float y1 = (float) ortho.scratch.pointD.y;
            float z1 = (float) ortho.scratch.pointD.z;

            float x0 = xpos;
            float y0 = ypos;
            float z0 = zpos;

            // if the lollipop end is behind the camera, recompute to avoid
            // rendering artifacts as non perspective adjusted coordinates
            // get mirrored
            if (zpos >= 1f) {
                // get camera position in LLA
                ortho.currentPass.scene.mapProjection.inverse(
                        ortho.currentPass.scene.camera.location,
                        ortho.scratch.geo);
                // compute lollipop top at camera height
                ortho.scratch.geo.set(latitude, longitude,
                        ortho.scratch.geo.getAltitude()
                                - ortho.currentPass.scene.camera.nearMeters);
                ortho.currentPass.scene.forward(ortho.scratch.geo,
                        ortho.scratch.pointD);
                x0 = (float) ortho.scratch.pointD.x;
                y0 = (float) ortho.scratch.pointD.y;
                z0 = (float) ortho.scratch.pointD.z;
            }

            if (z0 < 1f && z1 < 1f) {
                if (tiltLineBuffer == null) {
                    tiltLineBuffer = Unsafe.allocateDirect(24);
                    tiltLineBuffer.order(ByteOrder.nativeOrder());
                    tiltLineBufferPtr = Unsafe.getBufferPointer(tiltLineBuffer);
                }

                Unsafe.setFloats(tiltLineBufferPtr + 0, x0, y0, z0);
                Unsafe.setFloats(tiltLineBufferPtr + 12, x1, y1, z1);

                GLES20FixedPipeline.glColor4f(Color.red(_color) / 255f,
                        Color.green(_color) / 255f,
                        Color.blue(_color) / 255f,
                        Color.alpha(_color) / 255f);
                GLES20FixedPipeline.glLineWidth(2f);
                GLES20FixedPipeline
                        .glEnableClientState(
                                GLES20FixedPipeline.GL_VERTEX_ARRAY);
                GLES20FixedPipeline.glVertexPointer(3,
                        GLES20FixedPipeline.GL_FLOAT, 0, tiltLineBuffer);
                GLES20FixedPipeline
                        .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, 2);
                GLES20FixedPipeline
                        .glDisableClientState(
                                GLES20FixedPipeline.GL_VERTEX_ARRAY);
            }
        }

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(xpos, ypos, zpos);

        _drawBorderVerts();

        if ((_style & Marker.STYLE_ALERT_MASK) != 0) {
            // draw alert
            GLImageCache.Entry alertTexture = getAlertTexture(ortho
                    .getRenderContext());
            if (alertTexture.getTextureId() != 0) {
                if (_alertImage == null) {
                    _alertImage = new GLImage(alertTexture.getTextureId(),
                            alertTexture.getTextureWidth(),
                            alertTexture.getTextureHeight(),
                            alertTexture.getImageTextureX(),
                            alertTexture.getImageTextureY(),
                            alertTexture.getImageTextureWidth(),
                            alertTexture.getImageTextureHeight(),
                            -alertTexture.getImageWidth() / 2f,
                            -alertTexture.getImageHeight() / 2f,
                            alertTexture.getImageWidth(),
                            alertTexture.getImageHeight());
                }
                GLES20FixedPipeline.glPushMatrix();
                float scale = (float) (ortho.animationLastTick % 500) / 500f;
                GLES20FixedPipeline.glScalef(scale, scale, 1f);
                _alertImage.draw();
                GLES20FixedPipeline.glPopMatrix();

                // animation is occuring, request refresh
                ortho.requestRefresh();
            }
        }

        if (_icon != null && _iconVisibility != Marker.ICON_GONE) {
            _icon.validate();
            if (_icon.isEntryInvalid()
                    && subject.getMetaString("backupIconUri", null) != null) {
                Icon.Builder backup = new Icon.Builder();
                backup.setImageUri(Icon.STATE_DEFAULT,
                        subject.getMetaString("backupIconUri", null));
                backup.setColor(Icon.STATE_DEFAULT, _color);
                _updateIcon(backup.build(), Icon.STATE_DEFAULT);
            }
        }
        if (_icon != null && _iconVisibility == Marker.ICON_VISIBLE) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            if (((_style & Marker.STYLE_ROTATE_HEADING_NOARROW_MASK) != 0 &&
                    (_style & Marker.STYLE_ROTATE_HEADING_MASK) != 0) ||
                    subject.getUID().equals(MapView.getDeviceUid())) {

                // draw a fully rotated icon or the self marker because the style
                // indicated no arrow and heading

                GLES20FixedPipeline.glPushMatrix();
                float f = 360f - _heading
                        + (float) ortho.currentPass.drawRotation;
                // additional protection
                if (Float.isNaN(f))
                    f = 0f;
                GLES20FixedPipeline.glRotatef(f, 0f, 0f, 1f);
                _icon.drawColor(_color);
                GLES20FixedPipeline.glPopMatrix();

            } else if ((_style & Marker.STYLE_ROTATE_HEADING_MASK) != 0) {
                // draw the appropriate arrow on the map but do not rotate the icon
                GLES20FixedPipeline.glPushMatrix();
                float f = 360f - _heading
                        + (float) ortho.currentPass.drawRotation;
                // additional protection
                if (Float.isNaN(f))
                    f = 0f;
                GLES20FixedPipeline.glRotatef(f, 0f, 0f, 1f);
                if (_verts != null) { // otherwise draw the heading arrow
                    _setColor(Color.BLACK);
                    GLES20FixedPipeline.glLineWidth(3f);
                    _verts.draw(GLES20FixedPipeline.GL_LINE_STRIP);

                    _setColor(_color);
                    GLES20FixedPipeline.glLineWidth(2f);
                    _verts.draw(GLES20FixedPipeline.GL_LINE_STRIP);
                    GLES20FixedPipeline.glLineWidth(1f);
                }
                GLES20FixedPipeline.glPopMatrix();
                _icon.drawColor(_color);
            } else {
                // draw an unrotated icon with no arrow
                _icon.drawColor(_color);
            }

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }

        updateLabelVisibility(ortho);
        updateHitRect();

        GLES20FixedPipeline.glPopMatrix();
    }

    private void checkNadirClamp(GLMapView ortho) {
        boolean nadirClamp = getClampToGroundAtNadir()
                && Double.compare(ortho.currentScene.drawTilt, 0) == 0;
        if (_nadirClamp != nadirClamp) {
            _nadirClamp = nadirClamp;
            refreshLabelGeometry();
        }
    }

    private void updateDrawPosition(GLMapView ortho) {
        ortho.scratch.geo.set(this.latitude,
                ortho.idlHelper.wrapLongitude(this.longitude));

        // Altitude mode calculation
        double alt = altHae;
        double terrain = validateLocalElevation(ortho);

        AltitudeMode altMode = getAltitudeMode();
        if (!GeoPoint.isAltitudeValid(alt)
                || altMode == AltitudeMode.ClampToGround)
            alt = terrain;
        else if (altMode == AltitudeMode.Relative)
            alt += terrain;

        // Offset the height if the marker has a "height" meta value, which it should have if
        // a shape was extruded in GLPolyline
        double height = _height;
        if (!_nadirClamp && !Double.isNaN(height))
            alt += height;

        ortho.scratch.geo.set(alt);
        forward(ortho, ortho.scratch.geo, _point, 0d, terrain);
        if (ortho.currentPass.drawTilt > 0d) {
            // move up ~5 pixels from surface
            // ypos += 5;
            // move up half icon height
            if (_icon != null && _iconVisibility != Marker.ICON_GONE) {
                _point.y += (_icon.getHeight() / 2d)
                        * Math.sin(Math.toRadians(ortho.currentScene.drawTilt));
                _point.z += -0.00025d
                        * Math.cos(Math.toRadians(ortho.currentScene.drawTilt));
            }
        }
    }

    /**
     * Check if we should draw the label at the current map resolution
     * @param ortho Map view
     * @return True to draw label
     */
    private boolean shouldDrawLabel(GLMapView ortho) {

        // Text empty or labels set to never show
        if (textRenderFlag == Marker.TEXT_STATE_NEVER_SHOW
                || !GLMapSurface.SETTING_displayLabels
                || FileSystemUtils.isEmpty(_text)
                || !this.visible)
            return false;

        // Always show label
        if (textRenderFlag == Marker.TEXT_STATE_ALWAYS_SHOW)
            return true;

        // Legacy min render scale
        if (subject.hasMetaValue("minRenderScale")
                && ortho.drawMapScale >= subject.getMetaDouble(
                        "minRenderScale", DEFAULT_MIN_RENDER_SCALE))
            return true;

        // Ensure map resolution is within range
        double minRes = subject.getMetaDouble("minLabelRenderResolution",
                Marker.DEFAULT_MIN_LABEL_RENDER_RESOLUTION);
        double maxRes = subject.getMetaDouble("maxLabelRenderResolution",
                Marker.DEFAULT_MAX_LABEL_RENDER_RESOLUTION);

        return ortho.currentPass.drawMapResolution > minRes
                && ortho.currentPass.drawMapResolution < maxRes;
    }

    /**
     * Check if we should draw the altitude "lollipop" under the marker
     * @param ortho Map view
     * @return True to draw lollipop stick
     */
    private boolean shouldDrawLollipop(GLMapView ortho) {
        if ((!getLollipopsVisible() && ortho.currentPass.drawTilt > 0))
            return false;
        return ortho.currentScene.drawTilt > 0d
                || ortho.currentPass.scene.camera.perspective && !_nadirClamp;
    }

    private boolean isBatchable(GLMapView ortho) {
        if (this.subject == null)
            return false;

        // needs to draw border for selection mask
        if ((state & Marker.STATE_PRESSED_MASK) == Marker.STATE_PRESSED_MASK)
            return false;

        // XXX - support batching for alert texture

        // needs to draw alert
        if ((_style & Marker.STYLE_ALERT_MASK) == Marker.STYLE_ALERT_MASK)
            return false;

        int textureCount = 0;
        if ((_style & Marker.STYLE_ALERT_MASK) == Marker.STYLE_ALERT_MASK)
            textureCount++;

        if (_icon != null) {
            _icon.validate();
            if (_icon.getImage() != null)
                textureCount++;
        }

        if ((_style
                & Marker.STYLE_ROTATE_HEADING_MASK) == Marker.STYLE_ROTATE_HEADING_MASK)
            return false;

        // if the displayLables preference is checked display the text if
        // the marker requested to always have the text show or if the scale is zoomed in enough

        if (shouldDrawLabel(ortho))
            textureCount += 2;

        return (textureCount <= GLRenderBatch.getBatchTextureUnitLimit());
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        if (!this.isBatchable(view)) {
            batch.end();
            this.draw(view, renderPass);
            batch.begin();
            return;
        }

        // Check if we need to update the points based on if the map is tilted
        checkNadirClamp(view);

        long deltaTime = view.animationDelta;

        updateDrawPosition(view);
        float xpos = (float) _point.x;
        float ypos = (float) _point.y;
        float zpos = (float) _point.z;

        // draw a line segment from the center of the point into the earth's
        // surface
        if (shouldDrawLollipop(view)) {
            final double terrain = validateLocalElevation(view);
            double anchorEl = Math.min(terrain, 0d);
            view.scratch.geo.set(
                    (terrain + GLMapView.elevationOffset)
                            * view.elevationScaleFactor);

            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            float x1 = (float) view.scratch.pointD.x;
            float y1 = (float) view.scratch.pointD.y;
            float z1 = (float) view.scratch.pointD.z;

            float x0 = xpos;
            float y0 = ypos;
            float z0 = zpos;

            // if the lollipop end is behind the camera, recompute to avoid
            // rendering artifacts as non perspective adjusted coordinates
            // get mirrored
            if (z0 >= 1f) {
                // get camera position in LLA
                view.currentPass.scene.mapProjection.inverse(
                        view.currentPass.scene.camera.location,
                        view.scratch.geo);
                // compute lollipop top at camera height
                view.scratch.geo.set(latitude, longitude,
                        view.scratch.geo.getAltitude()
                                - view.currentPass.scene.camera.nearMeters);
                view.currentPass.scene.forward(view.scratch.geo,
                        view.scratch.pointD);
                x0 = (float) view.scratch.pointD.x;
                y0 = (float) view.scratch.pointD.y;
                z0 = (float) view.scratch.pointD.z;
            }

            if (z0 < 1f && z1 < 1f) {
                batch.setLineWidth(2f);
                batch.batch(x0, y0, z0,
                        x1, y1, z1,
                        Color.red(_color) / 255f,
                        Color.green(_color) / 255f,
                        Color.blue(_color) / 255f,
                        Color.alpha(_color) / 255f);
            }
        }

        if (_icon != null && _iconVisibility == Marker.ICON_VISIBLE) {
            _icon.validate();
            if (_icon.isEntryInvalid()
                    && subject.getMetaString("backupIconUri", null) != null) {
                Icon.Builder backup = new Icon.Builder();
                backup.setImageUri(Icon.STATE_DEFAULT,
                        subject.getMetaString("backupIconUri", null));
                backup.setColor(Icon.STATE_DEFAULT, _color);
                _updateIcon(backup.build(), Icon.STATE_DEFAULT);
            }

            GLImage img = _icon.getImage();
            if (img != null && img.getTexId() != 0) {
                float offx = -_icon.getAnchorX();
                float offy = -(img.getHeight() - _icon.getAnchorY() - 1);
                batch.batch(img.getTexId(),
                        xpos + offx,
                        ypos + offy,
                        zpos,
                        xpos + offx + _icon.getWidth(),
                        ypos + offy,
                        zpos,
                        xpos + offx + _icon.getWidth(),
                        ypos + offy + _icon.getHeight(),
                        zpos,
                        xpos + offx,
                        ypos + offy + _icon.getHeight(),
                        zpos,
                        img.u0, img.v0,
                        img.u1, img.v0,
                        img.u1, img.v1,
                        img.u0, img.v1,
                        Color.red(_color) / 255f,
                        Color.green(_color) / 255f,
                        Color.blue(_color) / 255f,
                        Color.alpha(_color) / 255f);
            }
        }

        updateLabelVisibility(view);
        updateHitRect();
    }

    private void _setHeading(float heading) {
        _heading = heading;
    }

    private void _releaseIcon() {
        if (_icon != null) {
            _icon.release();
            _icon = null;
        }
    }

    private void _setIcon(Icon icon) {
        _releaseIcon();
        if (icon != null) {
            _icon = new GLIcon(icon.getWidth(), icon.getHeight(),
                    icon.getAnchorX(),
                    icon.getAnchorY());
        }
    }

    private void _updateIcon(Icon icon, int state) {
        if (_icon == null) {
            _setIcon(icon);
        }
        _color = Color.WHITE;
        if (icon != null) {
            _color = icon.getColor(state);
            if (_icon != null) {
                GLImageCache.Entry iconEntry = GLRenderGlobals
                        .get(context).getImageCache()
                        .fetchAndRetain(
                                icon.getImageUri(state), true);
                _icon.updateCacheEntry(iconEntry);
            }
        }
    }

    protected void _drawBorderVerts() {
        if (_borderVerts != null) {
            if ((state & Marker.STATE_PRESSED_MASK) != 0) {
                GLES20FixedPipeline
                        .glEnableClientState(
                                GLES20FixedPipeline.GL_VERTEX_ARRAY);
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

                int color = SELECTED_COLOR;
                float r = Color.red(color) / 255f;
                float g = Color.green(color) / 255f;
                float b = Color.blue(color) / 255f;
                float a = Color.alpha(color) / 255f;
                GLES20FixedPipeline.glColor4f(r, g, b, a);

                GLES20FixedPipeline.glVertexPointer(2,
                        GLES20FixedPipeline.GL_FLOAT, 0,
                        _borderVerts);
                GLES20FixedPipeline.glDrawArrays(
                        GLES20FixedPipeline.GL_TRIANGLE_STRIP, 0, 4);
                GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
                GLES20FixedPipeline
                        .glDisableClientState(
                                GLES20FixedPipeline.GL_VERTEX_ARRAY);
            }
        }
    }

    /**
     * @deprecated No longer used (never actually used in the first place)
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void onMarkerHitBoundsChanged(Marker marker) {
    }

    // XXX - from GLMapSurface
    private static GLImageCache.Entry getAlertTexture(RenderContext surface) {
        if (_alertImageEntry == null) {
            _alertImageEntry = GLRenderGlobals.get(surface).getImageCache()
                    .fetchAndRetain(
                            "resource://" + R.drawable.map_ping_flash, false);
        }
        return _alertImageEntry;
    }

    @Override
    public void onHeightChanged(MapItem item) {
        final double height = item.getHeight();
        context.queueEvent(new Runnable() {
            public void run() {
                _height = height;
                refreshLabelGeometry();

                synchronized (bounds) {
                    updateBoundsZ();
                }
                dispatchOnBoundsChanged();
            }
        });
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        if (!_hitRect.isEmpty() && _hitRect.intersects(params.rect))
            return new HitTestResult(_subject, params.geo);
        return null;
    }

    @Override
    protected void updateBoundsZ() {
        double minAlt = Math.min(DEFAULT_MIN_ALT, altHae - 10d); // lollipop bottom
        double maxAlt = altHae + 10d;
        if (!Double.isNaN(_height))
            maxAlt += _height;
        if (Double.isNaN(altHae)) {
            minAlt = Double.NaN;
            maxAlt = Double.NaN;
        } else {
            switch (getAltitudeMode()) {
                case Absolute:
                    //
                    maxAlt = Math.max(DEFAULT_MAX_ALT, altHae);
                    break;
                case Relative:
                    // offset from min/max surface altitudes
                    minAlt += DEFAULT_MIN_ALT;
                    maxAlt += DEFAULT_MAX_ALT;
                    break;
                case ClampToGround:
                    minAlt = DEFAULT_MIN_ALT + _height;
                    maxAlt = DEFAULT_MAX_ALT + _height;
                    break;
                default:
                    minAlt = Double.NaN;
                    maxAlt = Double.NaN;
                    break;
            }
        }
        bounds.setMinAltitude(minAlt);
        bounds.setMaxAltitude(maxAlt);
    }

}
