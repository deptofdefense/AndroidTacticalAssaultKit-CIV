
package gov.tak.platform.widgets.opengl;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.engine.Shader;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.widgets.ArcWidget;
import gov.tak.platform.graphics.Color;

public class GLArcWidget extends GLShapeWidget implements
        ArcWidget.OnRadiusChangedListener,
        ArcWidget.OnCentralAngleChangedListener,
        ArcWidget.OnOffsetAngleChangedListener {

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            // ArcWidget : ShapeWidget : MapWidget
            return 2;
        }


        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof ArcWidget) {
                ArcWidget arc = (ArcWidget) subject;
                GLArcWidget glArc = new GLArcWidget(arc, renderContext);
                return glArc;
            } else {
                return null;
            }
        }
    };

    private int[]_posBuffer;
    private int vertCount = 0;

    public GLArcWidget(ArcWidget arc, MapRenderer renderContext) {
        super(arc, renderContext);
        _posBuffer = new int[1];
        GLES30.glGenBuffers(1, _posBuffer, 0);
        _updateArc(arc);
    }
    protected void setupVertexBuffers() {
        // Positions buffer
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof ArcWidget) {
            ArcWidget arc = (ArcWidget) subject;
            arc.addOnCentralAngleChangedListener(this);
            arc.addOnOffsetAngleChangedListener(this);
            arc.addOnRadiusChangedListener(this);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof ArcWidget) {
            ArcWidget arc = (ArcWidget) subject;
            arc.removeOnCentralAngleChangedListener(this);
            arc.removeOnOffsetAngleChangedListener(this);
            arc.removeOnRadiusChangedListener(this);
        }
    }

    @Override
    public void onRadiusChanged(ArcWidget arc) {
        _updateArc(arc);
    }

    @Override
    public void onCentralAngleChanged(ArcWidget arc) {
        _updateArc(arc);
    }

    @Override
    public void onOffsetAngleChanged(ArcWidget arc) {
        _updateArc(arc);
    }

    @Override
    public void drawWidgetContent(DrawState drawstate) {
        if (vertCount != 0) {

            Shader shader = getDefaultShader();

            shader.useProgram(true);

            shader.setProjection(drawstate.projectionMatrix);
            shader.setModelView(drawstate.modelMatrix);

            shader.setColor4f(0,0,0,1f);
            GLES30.glLineWidth(OUTLINE_WIDTH);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, _posBuffer[0]);
            GLES30.glEnableVertexAttribArray(getDefaultShader().getAVertexCoords());
            GLES30.glVertexAttribPointer(getDefaultShader().getAVertexCoords(), 2, GLES30.GL_FLOAT,
                    false, 8, 0);

            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, vertCount);

            _setColor(strokeColor);
            GLES30.glLineWidth(LINE_WIDTH);
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, vertCount);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        }
    }

    @Override
    public void releaseWidget() {
    }

    private void _updateArc(ArcWidget arc) {
        final float centralAngle = arc.getCentralAngle();
        final float offsetAngle = arc.getOffsetAngle();
        final float radius = arc.getRadius();

        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _buildArc(radius, offsetAngle, centralAngle);
            }
        });
    }

    private void _buildArc(float radius, float offsetAngle,
            float centralAngle) {

        int lineCount = 8;// (int)((centralAngle / 90f) * 12f);
        // if (lineCount<4) lineCount=4; //Always have at least 4 segments
        // if (lineCount>8) lineCount=8;
        vertCount = lineCount + 1;

        double angle = offsetAngle * Math.PI / 180d;
        double step = (centralAngle / lineCount) * Math.PI / 180d;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 2 * vertCount);
        byteBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer verts = byteBuffer.asFloatBuffer();

        for (int i = 0; i < vertCount; i += 2) {
            float px = radius * (float) Math.cos(angle);
            float py = radius * (float) Math.sin(angle);

            verts.put(px);
            verts.put(py);
            angle += step;
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, _posBuffer[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 4 * 2 * vertCount,
                verts, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT,
                false, 8, 0);
        GLES30.glEnableVertexAttribArray(0);
    }

    private void _setColor(int color) {
        getDefaultShader().setColor4f(Color.red(color) / 255f,Color.green(color) / 255f,Color.blue(color) / 255f,Color.alpha(color) / 255f);
    }

    private static final float LINE_WIDTH = 1f;
    private static final float OUTLINE_WIDTH = LINE_WIDTH + 2;
}
