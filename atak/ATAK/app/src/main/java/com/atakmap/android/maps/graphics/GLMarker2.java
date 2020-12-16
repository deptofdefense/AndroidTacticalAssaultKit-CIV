
package com.atakmap.android.maps.graphics;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Marker.OnIconChangedListener;
import com.atakmap.android.maps.Marker.OnMarkerHitBoundsChangedListener;
import com.atakmap.android.maps.Marker.OnStateChangedListener;
import com.atakmap.android.maps.Marker.OnStyleChangedListener;
import com.atakmap.android.maps.Marker.OnSummaryChangedListener;
import com.atakmap.android.maps.Marker.OnTitleChangedListener;
import com.atakmap.android.maps.Marker.OnTrackChangedListener;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLText;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class GLMarker2 extends GLPointMapItem2 implements
        OnIconChangedListener,
        OnStateChangedListener, OnTitleChangedListener,
        OnMarkerHitBoundsChangedListener,
        OnTrackChangedListener,
        OnStyleChangedListener,
        OnSummaryChangedListener,
        GLMapBatchable2, Marker.OnLabelTextSizeChangedListener {

    public static final String TAG = "GLMarker";

    private final static int SELECTED_COLOR = Color.argb(127, 255, 255, 255);
    private final static double ICON_SCALE = 1d;

    private final static int MAX_TEXT_WIDTH = Math.round(80 * MapView.DENSITY);
    private final static double DEFAULT_MIN_RENDER_SCALE = (1.0d / 100000.0d);

    private static final float LINE_WIDTH = (float) Math
            .ceil(3f * MapView.DENSITY);
    private static final float OUTLINE_WIDTH = (float) Math
            .ceil(55f * MapView.DENSITY);

    private static final double div_pi_4 = Math.PI / 4f;
    private static final double FOURTY_FIVE_DEGREES = 0.785398; // Expressed in Radians

    private int state;
    private GLIcon _icon;
    private int _iconVisibility;
    private final GLTriangle.Fan _verts;
    private GLText _glText;
    private String _text = "";
    private float _textWidth = 0f;
    private float _textHeight = 0f;
    private int _extraLines = 0;
    private ArrayList<String> _linesArray = new ArrayList<>();
    private ByteBuffer _borderVerts;
    private float _heading = 0f;
    private int _style;
    private int _color = Color.WHITE;
    private float _textColorR;
    private float _textColorG;
    private float _textColorB;
    private float _textColorA;
    private float _marqueeOffset = 0f;
    private long _marqueeTimer = 0;
    private GLImage _alertImage;
    private int textRenderFlag = Marker.TEXT_STATE_DEFAULT;
    private boolean displayLabel; // set by isBatchable,
                                  // used by batch()
    private int _labelTextSize;
    private final Typeface _labelTypeface;

    private static GLImageCache.Entry _alertImageEntry;
    private static ByteBuffer tiltLineBuffer = null;
    private static long tiltLineBufferPtr = 0L;

    public GLMarker2(MapRenderer surface, Marker subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES);
        state = subject.getState();
        textRenderFlag = subject.getTextRenderFlag();
        _linesArray = new ArrayList<>();
        _verts = new GLTriangle.Fan(2, 4);
        _labelTextSize = subject.getLabelTextSize();
        _labelTypeface = subject.getLabelTypeface();
        initState(subject);
    }

    public String toString() {
        return "marker_marker"; // _infoText.toString();
    }

    private void initState(Marker subject) {
        final Icon icon = subject.getIcon();
        final Rect hitBounds = subject.getMarkerHitBounds();
        final String title = getTitle(subject);
        final String extraLines = getExtraLines(subject);
        final float heading = (float) subject.getTrackHeading();
        final int style = subject.getStyle();
        final int textColor = subject.getTextColor();
        final int trf = subject.getTextRenderFlag();

        _buildArrow();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                textRenderFlag = trf;

                refreshLabel(title, extraLines);

                _updateIcon(icon, state);
                _setBoarderVerts(hitBounds);
                _setHeading(heading);
                _style = style;

                _textColorR = Color.red(textColor) / 255f;
                _textColorG = Color.green(textColor) / 255f;
                _textColorB = Color.blue(textColor) / 255f;
                _textColorA = Color.alpha(textColor) / 255f;
            }
        };

        if (GLMapSurface.isGLThread())
            r.run();
        else
            this.context.queueEvent(r);
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

    private void refreshLabel(String title, String extraLines) {
        if (title != null)
            _text = title;
        else
            _text = "";
        _text = GLText.localize(_text);

        if (_glText == null) {
            MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                    _labelTextSize);
            _glText = GLText.getInstance(textFormat);
        }

        _textWidth = _glText.getStringWidth(_text);
        _textHeight = _glText.getStringHeight();

        // Check if we have extra text
        parseText(extraLines);

        // Loop through any extra lines we have
        for (String t : _linesArray) {
            if (_glText.getStringWidth(t) > _textWidth)
                _textWidth = _glText.getStringWidth(t);
        }
        centerText();
    }

    @Override
    public void startObserving() {
        final Marker marker = (Marker) this.subject;
        super.startObserving();
        marker.addOnIconChangedListener(this);
        marker.addOnStateChangedListener(this);
        marker.addOnTitleChangedListener(this);
        marker.addOnSummaryChangedListener(this);
        marker.addOnMarkerHitBoundsChangedListener(this);
        marker.addOnTrackChangedListener(this);
        marker.addOnStyleChangedListener(this);
        marker.addOnLabelSizeChangedListener(this);
        initState(marker);
    }

    @Override
    public void stopObserving() {
        final Marker marker = (Marker) this.subject;
        super.stopObserving();
        marker.removeOnIconChangedListener(this);
        marker.removeOnStateChangedListener(this);
        marker.removeOnTitleChangedListener(this);
        marker.removeOnSummaryChangedListener(this);
        marker.removeOnMarkerHitBoundsChangedListener(this);
        marker.removeOnTrackChangedListener(this);
        marker.removeOnStyleChangedListener(this);
        marker.removeOnLabelSizeChangedListner(this);
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
    public void onStateChanged(Marker marker) {
        final Icon icon = marker.getIcon();
        final int sstate = marker.getState();
        final int trf = marker.getTextRenderFlag();
        final int textColor = marker.getTextColor();

        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                textRenderFlag = trf;
                state = sstate;
                _textColorR = Color.red(textColor) / 255f;
                _textColorG = Color.green(textColor) / 255f;
                _textColorB = Color.blue(textColor) / 255f;
                _textColorA = Color.alpha(textColor) / 255f;
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

                // Need to re-center in case there's a summary
                if (_extraLines > 0)
                    onSummaryChanged(marker);
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
    public void onTrackChanged(Marker marker) {
        final float heading = (float) marker.getTrackHeading();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _setHeading(heading);
            }
        });
    }

    private void _setColor(int color) {
        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    /**
     * Function to parse the string, splits the string on newline char's else the string will remain
     * one big long string
     * 
     * @param text - The string to parse
     */
    private void parseText(String text) {
        _extraLines = 0;
        _linesArray.clear();
        if (FileSystemUtils.isEmpty(text))
            return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            // If we hit a new line char
            if (c == '\n') {
                sb.deleteCharAt(sb.length() - 1);
                // Store what we currently have
                _linesArray.add(sb.toString());
                _extraLines += 1;
                // Get ready to read chars until the next newline
                sb.delete(0, sb.length());
            }
        }
        if (sb.length() > 0) {
            _extraLines += 1;
            _linesArray.add(sb.toString());
        }
    }

    /**
     * Function that centers the text in the background
     */
    private void centerText() {
        ArrayList<String> temp = new ArrayList<>();
        for (String s : _linesArray) {
            StringBuilder full = new StringBuilder(s);

            // Get the width of the longest string
            while (_glText.getStringWidth(s) < _textWidth) {
                full.append(" ");
                full.insert(0, " ");
                s = full.toString();
            }
            temp.add(s);
        }

        // Do the same thing for the callsign
        _linesArray.clear();
        _linesArray = temp;

        StringBuilder full = new StringBuilder(_text);
        while (_glText.getStringWidth(_text) < _textWidth) {
            full.append(" ");
            full.insert(0, " ");
            _text = full.toString();
        }
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

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        if (this.subject == null)
            return;

        float[] pos = getDrawPosition(ortho);
        float xpos = pos[0], ypos = pos[1], zpos = pos[2];

        // if tilted, draw a line segment from the center of the point into the
        // earth's surface
        if (ortho.drawTilt > 0d) {
            final double terrain = validateLocalElevation(ortho);
            double anchorEl = Math.min(terrain, 0d);
            ortho.scratch.geo.set(
                    (terrain + GLMapView.elevationOffset)
                            * ortho.elevationScaleFactor);

            ortho.scene.forward(ortho.scratch.geo, ortho.scratch.pointD);
            float xsurface = (float) ortho.scratch.pointD.x;
            float ysurface = (float) ortho.scratch.pointD.y;
            float zsurface = (float) ortho.scratch.pointD.z;

            if (tiltLineBuffer == null) {
                tiltLineBuffer = Unsafe.allocateDirect(24);
                tiltLineBuffer.order(ByteOrder.nativeOrder());
                tiltLineBufferPtr = Unsafe.getBufferPointer(tiltLineBuffer);
            }

            Unsafe.setFloats(tiltLineBufferPtr + 0, xpos, ypos, zpos);
            Unsafe.setFloats(tiltLineBufferPtr + 12, xsurface, ysurface,
                    zsurface);

            GLES20FixedPipeline.glColor4f(Color.red(_color) / 255f,
                    Color.green(_color) / 255f,
                    Color.blue(_color) / 255f,
                    Color.alpha(_color) / 255f);
            GLES20FixedPipeline.glLineWidth(2f);
            GLES20FixedPipeline
                    .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3,
                    GLES20FixedPipeline.GL_FLOAT, 0, tiltLineBuffer);
            GLES20FixedPipeline
                    .glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, 2);
            GLES20FixedPipeline
                    .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
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
                float f = 360f - _heading + (float) ortho.drawRotation;
                // additional protection
                if (Float.isNaN(f))
                    f = 0f;
                GLES20FixedPipeline.glRotatef(f, 0f, 0f, 1f);
                _icon.drawColor(_color);
                GLES20FixedPipeline.glPopMatrix();

            } else if ((_style & Marker.STYLE_ROTATE_HEADING_MASK) != 0) {
                // draw the appropriate arrow on the map but do not rotate the icon
                GLES20FixedPipeline.glPushMatrix();
                float f = 360f - _heading + (float) ortho.drawRotation;
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

        // if the displayLables preference is checked display the text if
        // the marker requested to always have the text show or if the scale is zoomed in enough
        if (shouldDrawLabel(ortho)) {
            if (_glText == null) {
                MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                        _labelTextSize);
                _glText = GLText
                        .getInstance(textFormat);
            }

            float offy = 0;
            float offtx = 0;

            boolean shouldMarquee = GLMapSurface.SETTING_shortenLabels
                    && (_style & Marker.STYLE_MARQUEE_TITLE_MASK) != 0;
            shouldMarquee &= (_textWidth > MAX_TEXT_WIDTH
                    * AtakMapView.DENSITY);

            float textWidth = !shouldMarquee ? _textWidth
                    : Math.min(MAX_TEXT_WIDTH * AtakMapView.DENSITY,
                            _textWidth);
            if (_icon != null && _iconVisibility != Marker.ICON_GONE) {
                float scale = (float) ICON_SCALE;

                if (_icon.getHeight() > 0) {
                    offy = scale
                            * -(_icon.getHeight() - _icon.getAnchorY() - 1);

                    if (ortho.drawTilt > 0d)
                        offy = (offy * -1f) + _textHeight
                                + _glText.getDescent();
                }
                if (_icon.getWidth() > 0) {
                    offtx = scale
                            * ((_icon.getWidth() / 2f) - _icon.getAnchorX());
                }
            } else {
                offy = _glText.getDescent() + _textHeight / 2f;
            }

            GLES20FixedPipeline.glTranslatef(offtx - textWidth / 2f, offy
                    - _textHeight, 0f);

            if (shouldMarquee) {
                this.drawLabelMarquee(ortho, offtx, xpos, ypos, textWidth,
                        _textHeight);
                ortho.requestRefresh();

            } else {
                this.drawLabelNoMarquee(ortho, textWidth, _textHeight);
            }
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    private float[] getDrawPosition(GLMapView ortho) {
        ortho.scratch.geo.set(this.latitude,
                ortho.idlHelper.wrapLongitude(this.longitude));

        // Offset the height if the marker has a "height" meta value, which it should have if
        // a shape was extruded in GLPolyline
        double height = this.subject.getHeight();
        if (!Double.isNaN(height)) {
            if (!Double.isNaN(altHae))
                height += altHae;
            ortho.scratch.geo.set(height);
        } else if (ortho.drawTilt > 0d)
            ortho.scratch.geo.set(altHae);

        forward(ortho, ortho.scratch.geo, ortho.scratch.pointD, 0d,
                validateLocalElevation(ortho));
        float[] pos = {
                (float) ortho.scratch.pointD.x,
                (float) ortho.scratch.pointD.y,
                (float) ortho.scratch.pointD.z
        };
        if (ortho.drawTilt > 0d) {
            // move up ~5 pixels from surface
            // ypos += 5;
            // move up half icon height
            if (_icon != null && _iconVisibility != Marker.ICON_GONE)
                pos[1] += (_icon.getHeight() / 2d);
        }
        return pos;
    }

    // XXX - combine next two
    private void drawLabelMarquee(GLMapView ortho, float offtx, float xpos,
            float ypos,
            float textWidth, float textHeight) {
        long deltaTime = ortho.animationDelta;

        GLNinePatch smallNinePatch = GLRenderGlobals.get(this.context)
                .getSmallNinePatch();
        if (smallNinePatch != null) {
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
            GLES20FixedPipeline.glPushMatrix();

            if (_extraLines > 0) {
                GLES20FixedPipeline.glTranslatef(-4f, -_glText.getDescent()
                        - (25 * _extraLines) - 4, 0f);
                smallNinePatch.draw(textWidth + 7f, textHeight
                        + (textHeight * _extraLines));

            } else {
                GLES20FixedPipeline
                        .glTranslatef(-4f, -_glText.getDescent(), 0f);
                smallNinePatch.draw(textWidth + 8f, textHeight);
            }

            GLES20FixedPipeline.glPopMatrix();
        }

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(_marqueeOffset, 0f, 0f);
        _glText.draw(_text,
                _textColorR,
                _textColorG,
                _textColorB,
                _textColorA,
                -_marqueeOffset, -_marqueeOffset + textWidth);

        if (_extraLines > 0) {
            int count = 0;
            // Draw all of the extra text lines
            for (String t : _linesArray) {
                GLES20FixedPipeline.glTranslatef(0f, -_glText.getDescent()
                        - textHeight + 7, 0f);
                _glText.draw(t,
                        _textColorR,
                        _textColorG,
                        _textColorB,
                        _textColorA,
                        -_marqueeOffset, -_marqueeOffset + textWidth);
                count++;
            }
        }
        GLES20FixedPipeline.glPopMatrix();

        float textEndX = _marqueeOffset + _textWidth;
        if (_marqueeTimer <= 0) {

            // return to neutral scroll and wait 3 seconds
            if (textEndX <= MAX_TEXT_WIDTH * AtakMapView.DENSITY) {
                _marqueeTimer = 3000;
                _marqueeOffset = 0f;
            } else {
                // animate at 10 pixels per second
                _marqueeOffset -= (deltaTime * 0.02f);
                if (_marqueeOffset + _textWidth <= MAX_TEXT_WIDTH
                        * AtakMapView.DENSITY) {
                    _marqueeOffset = MAX_TEXT_WIDTH * AtakMapView.DENSITY
                            - _textWidth;
                    _marqueeTimer = 2000;
                }
            }
        } else {
            _marqueeTimer -= deltaTime;
        }
    }

    private void drawLabelNoMarquee(GLMapView view, float textWidth,
            float textHeight) {
        GLNinePatch smallNinePatch = GLRenderGlobals.get(this.context)
                .getSmallNinePatch();
        if (smallNinePatch != null) {
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
            GLES20FixedPipeline.glPushMatrix();

            if (_extraLines > 0) {
                GLES20FixedPipeline.glTranslatef(-4f, -_glText.getDescent()
                        - (textHeight * _extraLines), 0f);
                smallNinePatch.draw(textWidth + 8f, textHeight
                        + (textHeight * _extraLines - 1));

            } else {
                GLES20FixedPipeline
                        .glTranslatef(-4f, -_glText.getDescent(), 0f);
                smallNinePatch.draw(textWidth + 8f, textHeight);
            }
            GLES20FixedPipeline.glPopMatrix();

        }

        _glText.draw(_text,
                _textColorR,
                _textColorG,
                _textColorB,
                _textColorA);

        if (_extraLines > 0) {
            for (String t : _linesArray) {
                GLES20FixedPipeline.glTranslatef(0f, -_glText.getDescent()
                        - textHeight + 7, 0f);
                _glText.draw(t,
                        _textColorR,
                        _textColorG,
                        _textColorB,
                        _textColorA);
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
                || FileSystemUtils.isEmpty(_text))
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

        return ortho.drawMapResolution > minRes
                && ortho.drawMapResolution < maxRes;
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

        displayLabel = shouldDrawLabel(ortho);
        if (displayLabel) {
            if (_text.length() > 0)
                textureCount += 2;
            // Don't know what textureCount does but apparently we need to add to it here
            if (_extraLines > 0)
                textureCount += 2;
        }

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

        long deltaTime = view.animationDelta;

        float[] pos = getDrawPosition(view);
        float xpos = pos[0], ypos = pos[1], zpos = pos[2];

        // if tilted, draw a line segment from the center of the point into the
        // earth's surface
        if (view.drawTilt > 0d) {
            final double terrain = validateLocalElevation(view);
            double anchorEl = Math.min(terrain, 0d);
            view.scratch.geo.set(
                    (terrain + GLMapView.elevationOffset)
                            * view.elevationScaleFactor);

            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            float xsurface = (float) view.scratch.pointD.x;
            float ysurface = (float) view.scratch.pointD.y;
            float zsurface = (float) view.scratch.pointD.z;

            batch.setLineWidth(2f);
            batch.batch(xpos, ypos, zpos,
                    xsurface, ysurface, zsurface,
                    Color.red(_color) / 255f,
                    Color.green(_color) / 255f,
                    Color.blue(_color) / 255f,
                    Color.alpha(_color) / 255f);
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

        // if the displayLables preference is checked display the text if
        // the marker requested to always have the text show or if the scale is zoomed in enough
        if (displayLabel) {
            if (_text.length() > 0) {

                if (_glText == null) {
                    MapTextFormat textFormat = new MapTextFormat(_labelTypeface,
                            _labelTextSize);
                    _glText = GLText
                            .getInstance(textFormat);
                }

                float offy = 0;
                float offtx = 0;

                boolean shouldMarquee = GLMapSurface.SETTING_shortenLabels
                        && (_style & Marker.STYLE_MARQUEE_TITLE_MASK) != 0;
                shouldMarquee &= (_textWidth > MAX_TEXT_WIDTH
                        * AtakMapView.DENSITY);

                final float textWidth = !shouldMarquee ? _textWidth
                        : Math.min(
                                MAX_TEXT_WIDTH * AtakMapView.DENSITY,
                                _textWidth);

                if (_icon != null && _iconVisibility != Marker.ICON_GONE) {
                    float scale = (float) ICON_SCALE;

                    if (_icon.getHeight() > 0) {
                        offy = scale
                                * -(_icon.getHeight() - _icon.getAnchorY() - 1);

                        if (view.drawTilt > 0d)
                            offy = (offy * -1f) + _textHeight
                                    + _glText.getDescent();
                    }
                    if (_icon.getWidth() > 0) {
                        offtx = scale
                                * ((_icon.getWidth() / 2f)
                                        - _icon.getAnchorX());
                    }
                } else {
                    offy = _glText.getDescent() + _textHeight / 2f;
                }

                float textRenderX = xpos + offtx - textWidth / 2f;
                final float textRenderYMult = ypos + offy - (_textHeight * 2);
                final float textRenderY = ypos + offy - _textHeight;

                GLNinePatch smallNinePatch = GLRenderGlobals.get(
                        this.context).getSmallNinePatch();
                if (smallNinePatch != null) {
                    if (_extraLines > 0) {
                        smallNinePatch.batch(batch, textRenderX - 4f,
                                textRenderYMult - _glText.getDescent()
                                        - (_textHeight * (_extraLines - 1)),
                                zpos,
                                textWidth + 8f,
                                _textHeight + (_textHeight * _extraLines), 0f,
                                0f, 0f, 0.6f);
                    } else {
                        smallNinePatch.batch(batch, textRenderX - 4f,
                                textRenderY - _glText.getDescent(),
                                zpos,
                                textWidth + 8f,
                                _textHeight, 0f,
                                0f, 0f, 0.6f);
                    }
                }

                float scissorX0 = 0.0f;
                float scissorX1 = Float.MAX_VALUE;
                if (shouldMarquee) {
                    scissorX0 = -_marqueeOffset;
                    scissorX1 = -_marqueeOffset + textWidth;
                    textRenderX += _marqueeOffset;
                }

                _glText.batch(batch,
                        _text,
                        textRenderX,
                        textRenderY,
                        zpos,
                        _textColorR,
                        _textColorG,
                        _textColorB,
                        _textColorA,
                        scissorX0, scissorX1);
                if (_extraLines > 0) {
                    int count = 1;
                    for (String s : _linesArray) {
                        _glText.batch(batch,
                                s,
                                textRenderX,
                                textRenderY - (_textHeight * count),
                                zpos,
                                _textColorR,
                                _textColorG,
                                _textColorB,
                                _textColorA,
                                scissorX0, scissorX1);
                        count++;
                    }
                }

                if (shouldMarquee) {
                    float textEndX = _marqueeOffset + _textWidth;
                    if (_marqueeTimer <= 0) {
                        // return to neutral scroll and wait 3 seconds
                        if (textEndX <= MAX_TEXT_WIDTH * AtakMapView.DENSITY) {
                            _marqueeTimer = 3000;
                            _marqueeOffset = 0f;
                        } else {
                            // animate at 10 pixels per second
                            _marqueeOffset -= (deltaTime * 0.02f);
                            if (_marqueeOffset + _textWidth <= MAX_TEXT_WIDTH
                                    * AtakMapView.DENSITY) {
                                _marqueeOffset = MAX_TEXT_WIDTH
                                        * AtakMapView.DENSITY - _textWidth;
                                _marqueeTimer = 2000;
                            }
                        }
                    } else {
                        _marqueeTimer -= deltaTime;
                    }
                }
            }
        }
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

    private void _setBoarderVerts(Rect rect) {
        _borderVerts = GLTriangle.Strip.createBuffer(
                GLTriangle.Strip.createRectangle(-32, -32, 64, 64, null),
                _borderVerts);
    }

    @Override
    public void onMarkerHitBoundsChanged(Marker marker) {
        final Rect rect = marker.getMarkerHitBounds(null);
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _setBoarderVerts(rect);
            }
        });
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
}
