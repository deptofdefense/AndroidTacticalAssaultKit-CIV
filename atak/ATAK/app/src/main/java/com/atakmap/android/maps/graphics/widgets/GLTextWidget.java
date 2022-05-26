
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.android.widgets.TextWidget.OnColorChangedListener;
import com.atakmap.android.widgets.TextWidget.OnHasBackgroundChangedListener;
import com.atakmap.android.widgets.TextWidget.OnTextChangedListener;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLTextWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLTextWidget extends GLWidget2 implements OnTextChangedListener,
        OnColorChangedListener, OnHasBackgroundChangedListener {
    protected int _background = TextWidget.TRANSLUCENT;
    private float _r, _g, _b, _a;

    protected int[] _colors = null;
    protected int[] _colorBuf = null;
    protected MapTextFormat _mapTextFormat;
    protected String _text = "";

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // TextWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof TextWidget) {
                TextWidget textWidget = (TextWidget) subject;
                return new GLTextWidget(textWidget, orthoView);
            }
            return null;
        }
    };

    public GLTextWidget(TextWidget subject, GLMapView orthoView) {
        super(subject, orthoView);
        onTextWidgetTextChanged(subject);
        onTextWidgetColorChanged(subject);
        onTextWidgetHasBackgroundChanged(subject);
        startObserving(subject);
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof TextWidget) {
            TextWidget tw = (TextWidget) subject;
            tw.addOnTextChangedListener(this);
            tw.addOnColorChangedListener(this);
            tw.addOnHasBackgroundChangedListener(this);
        }
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof TextWidget) {
            TextWidget tw = (TextWidget) subject;
            tw.removeOnTextChangedListener(this);
            tw.removeOnColorChangedListener(this);
            tw.removeOnHasBackgroundChangedListener(this);
        }
    }

    @Override
    public void drawWidgetContent() {
        if (getSurface() != null && _mapTextFormat != null && _width > 0
                && _height > 0) {
            // not saving a global reference to GLText because the MapTextFormat changes on this
            // widget
            GLText glText = GLText.getInstance(_mapTextFormat);
            GLNinePatch ninePatch = GLRenderGlobals.get(getSurface())
                    .getMediumNinePatch();
            if (ninePatch != null && _background != TextWidget.TRANSLUCENT) {
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(_margin[LEFT],
                        -_pHeight + _margin[BOTTOM], 0f);

                GLES20FixedPipeline.glColor4f(_r, _g, _b, _a);
                ninePatch.draw(_pWidth, _pHeight);
                GLES20FixedPipeline.glPopMatrix();
            }
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(_padding[LEFT] + _margin[LEFT],
                    _mapTextFormat.getBaselineOffsetFromBottom()
                            - _padding[TOP] + _margin[BOTTOM],
                    0f);

            int[] colors = _colors;
            if (_colors != null && _colorBuf != null
                    && _colors.length == _colorBuf.length
                    && GLES20FixedPipeline.getAlphaMod() < 1f) {
                // Need to apply alpha mod here
                for (int i = 0; i < _colors.length; i++)
                    _colorBuf[i] = Color.argb(
                            Math.round(Color.alpha(_colors[i])
                                    * GLES20FixedPipeline.getAlphaMod()),
                            Color.red(_colors[i]),
                            Color.green(_colors[i]),
                            Color.blue(_colors[i]));
                colors = _colorBuf;
            }

            glText.drawSplitString(_text, colors);
            GLES20FixedPipeline.glPopMatrix();
        }
    }

    @Override
    public void releaseWidget() {
        stopObserving(subject);
    }

    @Override
    public void onTextWidgetColorChanged(TextWidget widget) {
        final int[] colors = widget.getColors();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _colors = colors;
                if (_colors == null)
                    _colorBuf = null;
                else if (_colorBuf == null
                        || _colors.length != _colorBuf.length)
                    _colorBuf = new int[_colors.length];
            }
        });
    }

    @Override
    public void onTextWidgetTextChanged(TextWidget widget) {
        final String text = widget.getText();
        final MapTextFormat format = widget.getTextFormat();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _text = GLText.localize(text);
                _mapTextFormat = format;
            }
        });
    }

    @Override
    public void onTextWidgetHasBackgroundChanged(TextWidget widget) {
        final int background = widget.getBackground();
        getSurface().queueEvent(new Runnable() {

            @Override
            public void run() {
                _background = background;
                int b = _background & 0xFF;
                int g = (_background >> 8) & 0xFF;
                int r = (_background >> 16) & 0xFF;
                int a = (_background >> 24) & 0xFF;
                _b = b / 255f;
                _g = g / 255f;
                _r = r / 255f;
                _a = a / 255f;
            }
        });

    }

}
