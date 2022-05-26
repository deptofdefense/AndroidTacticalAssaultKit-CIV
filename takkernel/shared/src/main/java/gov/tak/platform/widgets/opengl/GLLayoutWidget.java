
package gov.tak.platform.widgets.opengl;
import android.graphics.Color;

import java.nio.FloatBuffer;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.ILayoutWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.api.engine.Shader;

import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLNinePatch;

public class GLLayoutWidget extends GLAbstractParentWidget implements
        ILayoutWidget.OnBackingColorChangedListener {

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            // ButtonWidget : AbstractParentWidget : MapWidget
            return 2;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof ILayoutWidget) {
                ILayoutWidget compositWidget = (ILayoutWidget) subject;
                GLLayoutWidget glCompositWidget = new GLLayoutWidget(
                        compositWidget, renderContext);
                return glCompositWidget;
            } else {
                return null;
            }
        }
    };

    protected final ILayoutWidget _subject;

    public GLLayoutWidget(ILayoutWidget subject, MapRenderer _orthoWorldMap) {
        super(subject, _orthoWorldMap);
        _subject = subject;
        _width = subject.getWidth();
        _height = subject.getHeight();
        _backingColor = subject.getBackingColor();
        _ninePatchBG = subject.getNinePatchBG();
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof ILayoutWidget) {
            ILayoutWidget lw = (ILayoutWidget) subject;
            lw.addOnBackingColorChangedListener(this);
            lw.addOnWidgetSizeChangedListener(this);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof ILayoutWidget) {
            ILayoutWidget lw = (ILayoutWidget) subject;
            lw.removeOnBackingColorChangedListener(this);
            lw.addOnWidgetSizeChangedListener(this);
        }
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {
        DrawState localDrawState = drawState.clone();

        localDrawState.alphaMod *= _subject.getAlpha() / 255f;
        drawBacking(localDrawState);
        super.drawWidgetContent(localDrawState);
        _sizeChanged = false;

        // Fade in progress - request render of next frame
        if (_subject.isFadingAlpha())
            getRenderContext().requestRefresh();

        localDrawState.recycle();
    }

    @Override
    public void onBackingColorChanged(ILayoutWidget layout) {
        final int backingColor = layout.getBackingColor();
        final boolean ninePatchBG = layout.getNinePatchBG();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _backingColor = backingColor;
                _ninePatchBG = ninePatchBG;
            }
        });
    }

    /**
     * Draw the backing fill for this layout
     */
    protected void drawBacking(DrawState drawState) {
        if (_backingColor == 0)
            return;

        float r = Color.red(_backingColor) / 255f;
        float g = Color.green(_backingColor) / 255f;
        float b = Color.blue(_backingColor) / 255f;
        float a = Color.alpha(_backingColor) / 255f;

        Shader shader = getDefaultShader();
        int prevProgram = shader.useProgram(true);
        shader.setModelView(drawState.modelMatrix);
        shader.setProjection(drawState.projectionMatrix);

        GLNinePatch ninePatch = GLRenderGlobals.get(getRenderContext())
                .getMediumNinePatch();
        if (_ninePatchBG && ninePatch != null) {
            shader.setColor4f(r, g, b, a);
            ninePatch.draw(shader, _pWidth, _pHeight, false);
        } else {

            if (_sizeChanged || _fb == null) {
                _fb = GLTriangle.createRectangleFloatBuffer(0,0,_pWidth,_pHeight);
            }
            shader.setColor4f(r, g, b, a);
            GLES30.glEnable(GLES30.GL_BLEND);

            GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, android.opengl.GLES30.GL_FLOAT,
                    false, 8, _fb);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
            GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glDisable(GLES30.GL_BLEND);
        }
        GLES30.glUseProgram(prevProgram);
    }

    private FloatBuffer _fb = null;
    protected int _backingColor;
    protected boolean _ninePatchBG;
}
