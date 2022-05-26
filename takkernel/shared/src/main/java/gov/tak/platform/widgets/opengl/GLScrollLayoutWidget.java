
package gov.tak.platform.widgets.opengl;

import java.nio.FloatBuffer;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.commons.opengl.Matrix;
import gov.tak.api.engine.Shader;
import gov.tak.platform.widgets.LinearLayoutWidget;
import gov.tak.platform.widgets.ScrollLayoutWidget;

public class GLScrollLayoutWidget extends GLLinearLayoutWidget
        implements ScrollLayoutWidget.OnScrollChangedListener {

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof ScrollLayoutWidget) {
                ScrollLayoutWidget llw = (ScrollLayoutWidget) subject;
                return new GLScrollLayoutWidget(llw, renderContext);
            } else {
                return null;
            }
        }
    };

    protected final ScrollLayoutWidget _subject;
    protected float _scroll = 0;

    private static final float BAR_THICKNESS = 8f;
    private static final float BAR_COLOR = 95f / 255f;
    private FloatBuffer _scrollBar;

    public GLScrollLayoutWidget(ScrollLayoutWidget subject, MapRenderer ortho) {
        super(subject, ortho);
        _subject = subject;
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof ScrollLayoutWidget)
            ((ScrollLayoutWidget) subject).addOnScrollListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof ScrollLayoutWidget)
            ((ScrollLayoutWidget) subject).removeOnScrollListener(this);
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {
        boolean sizeChanged = _sizeChanged;
        super.drawWidgetContent(drawState);

        // Draw scroll bar on top
        boolean horizontal = _orientation == LinearLayoutWidget.HORIZONTAL;
        float barSize, barScale;
        if (horizontal) {
            barScale = _width / _childrenWidth;
            barSize = _width * barScale;
            if (barSize >= _width)
                return;
        } else {
            barScale = _height / _childrenHeight;
            barSize = _height * barScale;
            if (barSize >= _height)
                return;
        }

        if (sizeChanged || _scrollBar == null) {
            _scrollBar = GLTriangle.createRectangleFloatBuffer(0,0,horizontal ? barSize : BAR_THICKNESS,horizontal ? BAR_THICKNESS : barSize);
        }
        DrawState localDrawState = drawState.clone();
        float barPos = barScale * _scroll;
        if (horizontal)
            Matrix.translateM(localDrawState.modelMatrix, 0,_padding[LEFT] + barPos,
                    -_pHeight + 1f, 0f );
        else
            Matrix.translateM(localDrawState.modelMatrix, 0, _pWidth - BAR_THICKNESS - 1f,
                    -_pHeight + (_pHeight - barSize) - barPos
                            - _padding[TOP],
                    0f);

        Shader shader = getDefaultShader();
        shader.useProgram(true);

        shader.setModelView(drawState.modelMatrix);
        shader.setProjection(drawState.projectionMatrix);

        GLES30.glVertexAttrib4f(getDefaultShader().getUColor(), BAR_COLOR, BAR_COLOR, BAR_COLOR, 1f);
        GLES30.glEnable(GLES30.GL_BLEND);

        GLES30.glEnableVertexAttribArray(getDefaultShader().getAVertexCoords());
        GLES30.glVertexAttribPointer(getDefaultShader().getAVertexCoords(), 2, android.opengl.GLES30.GL_FLOAT,
                false, 8, 0);

        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, 4);
        GLES30.glDisable(GLES30.GL_BLEND);

        localDrawState.recycle();
    }

    @Override
    protected void drawChildren(DrawState drawState, float xOffset, float yOffset) {
        if (_orientation == LinearLayoutWidget.HORIZONTAL)
            super.drawChildren(drawState, -_scroll, 0f);
        else
            super.drawChildren(drawState, 0f, _scroll);
    }

    @Override
    public void onScrollChanged(ScrollLayoutWidget layout,
            final float scroll) {
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _scroll = scroll;
            }
        });
    }
}
