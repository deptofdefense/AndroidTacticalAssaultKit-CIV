
package gov.tak.platform.widgets.opengl;
import android.graphics.Color;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IButtonWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.commons.opengl.Matrix;
import gov.tak.api.engine.Shader;


import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.widgets.ButtonWidget;

import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.opengl.GLText;

public class GLButtonWidget extends GLAbstractButtonWidget implements
        IButtonWidget.OnSizeChangedListener {

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            // ButtonWidget : AbstractButtonWidget : MapWidget
            return 2;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof IButtonWidget) {
                IButtonWidget buttonWidget = (IButtonWidget) subject;
                GLAbstractButtonWidget glButtonWidget = new GLButtonWidget(
                        buttonWidget, renderContext);
                return glButtonWidget;
            } else {
                return null;
            }
        }
    };

    public GLButtonWidget(IButtonWidget subject, MapRenderer orthoView) {
        super(subject, orthoView);
        _width = subject.getButtonWidth();
        _height = subject.getButtonHeight();
        _bgDirty = true;
    }

    @Override
    public void onButtonSizeChanged(IButtonWidget button) {
        final float width = button.getButtonWidth();
        final float height = button.getButtonHeight();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _width = width;
                _height = height;
                _bgDirty = true;
            }
        });
    }

    @Override
    void drawButtonBackground(DrawState drawState, int bgColor) {
        if (bgColor != 0) {

            if (_bgDirty) {
                float[] verts = GLTriangle.createRectangle(0,0, _width, _height, null);

                int size = 4 * 4 * 4;
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size);
                byteBuffer.order(ByteOrder.nativeOrder());
                _fb = byteBuffer.asFloatBuffer();
                _fb.put(verts);
                _fb.position(0);

                _bgDirty = false;
            }

            GLES30.glEnable(GLES30.GL_BLEND);
            float r = Color.red(bgColor) / 255f;
            float g = Color.green(bgColor) / 255f;
            float b = Color.blue(bgColor) / 255f;
            float a = Color.alpha(bgColor) / 255f;

            Shader shader = getDefaultShader();
            int prevProgram = shader.useProgram(true);
            shader.setModelView(drawState.modelMatrix);
            shader.setProjection(drawState.projectionMatrix);
            shader.setColor4f(r, g, b, a);
            GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, GLES30.GL_FLOAT, false, 0, _fb);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

            GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glUseProgram(prevProgram);
        }
    }

    @Override
    void drawButtonText(DrawState drawState, GLText _glText, String _textValue) {
        float texOffx = (_width - _glText.getStringWidth(_textValue)) / 2f;
        float texOffy = (_height - _glText.getStringHeight()) / 2f;

        DrawState localDrawState = drawState.clone();
        Matrix.translateM(localDrawState.modelMatrix, 0, texOffx, texOffy, 0f);
        _glText.draw(_textValue, 1.0f, 1.0f, 1.0f, 1.0f, localDrawState.projectionMatrix, localDrawState.modelMatrix);
        localDrawState.recycle();
    }

    @Override
    void drawButtonIcon(DrawState drawState, int iconColor, GLImage image) {
        Shader shader = getTextureShader();
        shader.setModelView(drawState.modelMatrix);
        shader.setProjection(drawState.projectionMatrix);
        shader.useProgram(true);
        image.draw(shader, Color.red(iconColor)/255f, Color.green(iconColor)/255f, Color.blue(iconColor)/255f, Color.alpha(iconColor)/255f);
    }

    FloatBuffer _fb = null;
    private float _width;
    private float _height;
    private boolean _bgDirty;
}
