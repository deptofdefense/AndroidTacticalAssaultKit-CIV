
package gov.tak.platform.widgets.opengl;
import android.graphics.Color;

import gov.tak.api.commons.graphics.TextFormat;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.engine.Shader;
import gov.tak.api.widgets.ITextWidget;

import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.android.maps.MapTextFormat;

import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.commons.opengl.Matrix;
import gov.tak.platform.widgets.TextWidget;
import com.atakmap.opengl.GLText;

public class GLTextWidget extends GLWidget implements TextWidget.OnTextChangedListener,
        TextWidget.OnColorChangedListener, TextWidget.OnHasBackgroundChangedListener {
    protected int _background = TextWidget.TRANSLUCENT;
    private float _r, _g, _b, _a;

    protected int[] _colors = null;
    protected int[] _colorBuf = null;
    MapTextFormat _mapTextFormat;
    protected String _text = "";

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            // TextWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof ITextWidget) {
                ITextWidget textWidget = (ITextWidget) subject;
                return new GLTextWidget(textWidget, renderContext);
            }
            return null;
        }
    };

    public GLTextWidget(ITextWidget subject, MapRenderer orthoView) {
        super(subject, orthoView);
        onTextWidgetTextChanged(subject);
        onTextWidgetColorChanged(subject);
        onTextWidgetHasBackgroundChanged(subject);
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof ITextWidget) {
            ITextWidget tw = (ITextWidget) subject;
            tw.addOnTextChangedListener(this);
            tw.addOnColorChangedListener(this);
            tw.addOnHasBackgroundChangedListener(this);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof ITextWidget) {
            ITextWidget tw = (ITextWidget) subject;
            tw.removeOnTextChangedListener(this);
            tw.removeOnColorChangedListener(this);
            tw.removeOnHasBackgroundChangedListener(this);
        }
    }

    @Override
    public void setY(float y) {
        super.setY(y);
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {
        if (getRenderContext() != null && _mapTextFormat != null && _width > 0
                && _height > 0) {
            // not saving a global reference to GLText because the MapTextFormat changes on this
            // widget

            Shader shader = getDefaultShader();
            GLText glText = GLText.getInstance(_renderContext, _mapTextFormat);
            GLNinePatch ninePatch = GLRenderGlobals.get(getRenderContext()).getMediumNinePatch();
            if (ninePatch != null && _background != TextWidget.TRANSLUCENT) {
                DrawState ninePatchDrawState = drawState.clone();
                Matrix.translateM(ninePatchDrawState.modelMatrix, 0, 0f, -_pHeight, 0f);

                int prevProgram = shader.useProgram(true);
                shader.setModelView(ninePatchDrawState.modelMatrix);
                shader.setProjection(ninePatchDrawState.projectionMatrix);
                shader.setColor4f(_r, _g, _b, _a);

                ninePatch.draw(shader, _pWidth, _pHeight, false);
                GLES30.glUseProgram(prevProgram);

                ninePatchDrawState.recycle();
            }

            DrawState glTextDrawState = drawState.clone();
            Matrix.translateM(glTextDrawState.modelMatrix, 0, _padding[LEFT],
                    _mapTextFormat.getBaselineOffsetFromBottom()
                            - _padding[TOP],
                    0f);

            int[] colors = _colors;
            if (_colors != null && _colorBuf != null
                    && _colors.length == _colorBuf.length
                    && glTextDrawState.alphaMod < 1f) {
                // Need to apply alpha mod here
                for (int i = 0; i < _colors.length; i++)
                    _colorBuf[i] = Color.argb(
                            Math.round(Color.alpha(_colors[i])
                                    * glTextDrawState.alphaMod),
                            Color.red(_colors[i]),
                            Color.green(_colors[i]),
                            Color.blue(_colors[i]));
                colors = _colorBuf;
            }

            glText.drawSplitString(_text, colors, glTextDrawState.projectionMatrix, glTextDrawState.modelMatrix);

            glTextDrawState.recycle();
        }
    }

    @Override
    public void releaseWidget() {
    }

    @Override
    public void onTextWidgetColorChanged(ITextWidget widget) {
        final int[] colors = widget.getColors();
        runOrQueueEvent(new Runnable() {
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
    public void onTextWidgetTextChanged(ITextWidget widget) {
        final String text = widget.getText();
        final TextFormat format = widget.getWidgetTextFormat();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _text = GLText.localize(text);
                _mapTextFormat = new MapTextFormat(format);
            }
        });
    }

    @Override
    public void onTextWidgetHasBackgroundChanged(ITextWidget widget) {
        final int background = widget.getBackground();
        runOrQueueEvent(new Runnable() {

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
