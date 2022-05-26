
package gov.tak.platform.widgets.opengl;

import java.nio.FloatBuffer;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.commons.opengl.Matrix;
import gov.tak.api.engine.Shader;
import gov.tak.platform.widgets.CenterBeadWidget;

public class GLCenterBeadWidget extends GLShapeWidget {

    FloatBuffer _crossHairLine;

    private final CenterBeadWidget sw;

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            // CenterBeadWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof CenterBeadWidget) {
                return new GLCenterBeadWidget(
                        (CenterBeadWidget) subject,
                        renderContext);
            }
            return null;
        }
    };

    public GLCenterBeadWidget(CenterBeadWidget subject, MapRenderer renderContext) {
        super(subject, renderContext);
        this.sw = subject;
        _crossHairLine = GLTriangle.createRectangleFloatBuffer(0,0,1,1);
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {
        if (!sw.isVisible())
            return;


        float density = (float) (this.getRenderContext().getRenderSurface().getDpi() / 240d);
        float radius = 50f * density;
        float diameter = radius * 2;
        float d8 = diameter / 8, d32 = diameter / 32;

        float left = drawState.scene.focusx - radius;
        int height = getRenderContext().getRenderSurface().getHeight();
        float focusY = getMapRenderer().getMapSceneModel(true, getMapRenderer().getDisplayOrigin()).focusy;
        float bottom = ((height - 1) - focusY) - radius;

        float[] modelMatrix = new float[16];

        Shader shader = getDefaultShader();
        int prevProgram = shader.useProgram(true);

        shader.setColor4f(1f, 0f, 0f, .7f);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, (left + radius + d8),
                bottom + radius - 1f, 0f);
        Matrix.scaleM(modelMatrix, 0, radius,4f, 1f);

        shader.setProjection(drawState.projectionMatrix);
        shader.setModelView(modelMatrix);

        GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
        GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, GLES30.GL_FLOAT, false, 0, _crossHairLine);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left + radius + d32,
                bottom + radius, 0f);
        Matrix.scaleM(modelMatrix, 0, radius,1f, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left - d8,
                bottom + radius - 1f, 0f);
        Matrix.scaleM(modelMatrix, 0, radius,4f, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left - d32,
                bottom + radius, 0f);
        Matrix.scaleM(modelMatrix, 0, radius,1f, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left + radius - 1f,
                bottom + radius + d8, 0f);
        Matrix.scaleM(modelMatrix, 0, 4f,radius, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left + radius,
                bottom + radius + d32, 0f);
        Matrix.scaleM(modelMatrix, 0, 1f, radius, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left + radius - 1f,
                bottom - d8, 0f);
        Matrix.scaleM(modelMatrix, 0, 4f, radius, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left + radius,
                bottom - d32, 0f);
        Matrix.scaleM(modelMatrix, 0, 1f, radius, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        // center dot (black outline, red center)
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left + radius - 3,
                bottom + radius - 3, 0f);
        Matrix.scaleM(modelMatrix, 0, 7f, 7f, 1f);
        shader.setColor4f(0,0,0,.70f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, left + radius - 2,
                bottom + radius - 2, 0f);
        Matrix.scaleM(modelMatrix, 0, 5f, 5f, 1f);
        shader.setModelView(modelMatrix);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
        GLES30.glUseProgram(prevProgram);
    }

    public void releaseWidget() {
    }
}
