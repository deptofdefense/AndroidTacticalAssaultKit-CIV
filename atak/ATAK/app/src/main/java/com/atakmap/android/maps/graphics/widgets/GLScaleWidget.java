
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.android.widgets.ScaleWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLScaleWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLScaleWidget extends GLShapeWidget implements
        ScaleWidget.OnTextChangedListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // ScaleWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof ScaleWidget) {
                ScaleWidget scaleWidget = (ScaleWidget) subject;
                GLScaleWidget glScaleWidget = new GLScaleWidget(scaleWidget,
                        orthoView);
                glScaleWidget.startObserving(scaleWidget);
                return glScaleWidget;
            } else {
                return null;
            }
        }
    };

    public GLScaleWidget(ScaleWidget subject, GLMapView orthoView) {
        super(subject, orthoView);
        _verts = new GLTriangle.Fan(2, 4);
        verts = new float[] {
                0, 0,
                0, 0,
                0, 0,
                0, 0
        };
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof ScaleWidget)
            ((ScaleWidget) subject).addOnTextChangedListener(this);
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof ScaleWidget)
            ((ScaleWidget) subject).removeOnTextChangedListener(this);
    }

    @Override
    public void drawWidgetContent() {
        if (getSurface() == null || _mapTextFormat == null
                || !subject.isVisible())
            return;

        GLText glText = GLText.getInstance(_mapTextFormat);

        float left = _padding[LEFT];
        float right = left + _width;
        float bottom = -_pHeight;
        float top = 0;

        float npWidth = _textWidth + _padding[LEFT] + _padding[RIGHT];
        float npX = left + (_width - npWidth) / 2;
        float centerY = bottom + (_pHeight / 2);

        verts[0] = left;
        verts[1] = top;

        verts[2] = left;
        verts[3] = centerY;

        verts[4] = right;
        verts[5] = centerY;

        verts[6] = right;
        verts[7] = bottom;

        _verts.setPoints(verts);

        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 1f);
        GLES20FixedPipeline.glLineWidth(LINE_WIDTH + 2);
        _verts.draw(GLES20FixedPipeline.GL_LINE_STRIP);

        GLES20FixedPipeline.glColor4f(1f, 1f, 1f, 1f);
        GLES20FixedPipeline.glLineWidth(LINE_WIDTH);
        _verts.draw(GLES20FixedPipeline.GL_LINE_STRIP);

        // not saving a global reference to GLText because the MapTextFormat changes on this
        // widget
        GLNinePatch ninePatch = GLRenderGlobals.get(getSurface())
                .getMediumNinePatch();
        if (ninePatch != null) {
            GLES20FixedPipeline.glPushMatrix();

            GLES20FixedPipeline.glTranslatef(npX, -_pHeight, 0f);
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.9f);
            ninePatch.draw(npWidth, _pHeight);

            GLES20FixedPipeline.glPopMatrix();
        }
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(npX + _padding[LEFT], -_pHeight
                + _padding[BOTTOM] + _mapTextFormat
                        .getBaselineOffsetFromBottom(),
                0f);
        glText.draw(_text, 1f, 1f, 1f, 1f);
        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 widget) {
        super.onWidgetSizeChanged(widget);
        if (!(widget instanceof ScaleWidget))
            return;
        ScaleWidget sw = (ScaleWidget) widget;
        final MapTextFormat fmt = sw.getTextFormat();
        final String text = sw.getText();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _text = text;
                _mapTextFormat = fmt;
                _textWidth = _mapTextFormat.measureTextWidth(text);
            }
        });
    }

    @Override
    public void onScaleTextChanged(ScaleWidget sw) {
        onWidgetSizeChanged(sw);
    }

    @Override
    public void releaseWidget() {
        stopObserving(subject);
    }

    private static final float LINE_WIDTH = 2f;

    final GLTriangle.Fan _verts;
    final float[] verts;

    private MapTextFormat _mapTextFormat;
    private String _text = "";
    private float _textWidth = 0;
}
