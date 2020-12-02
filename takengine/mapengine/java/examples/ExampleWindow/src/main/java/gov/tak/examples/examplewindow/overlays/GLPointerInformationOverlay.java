package gov.tak.examples.examplewindow.overlays;

import android.graphics.Typeface;
import android.util.Pair;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLAbstractLayer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import java.nio.FloatBuffer;

final class GLPointerInformationOverlay extends GLAbstractLayer2 implements PointerInformationOverlay.OnPointerLocationUpdateListener {
    final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if(!(object.second instanceof PointerInformationOverlay))
                return null;
            return new GLPointerInformationOverlay(object.first, object.second);
        }
    };

    final static String LLA_FORMAT = "%+02.6f %+03.6f %01.3fm %s";

    boolean debugPointerLocation = false;

    GLText textRenderer;
    float x;
    float y;

    GLPointerInformationOverlay(MapRenderer surface, Layer subject) {
        super(surface, subject, GLMapView.RENDER_PASS_UI);
    }

    @Override
    protected void drawImpl(GLMapView view, int renderPass) {
        if(textRenderer == null)
            textRenderer = GLText.getInstance(new MapTextFormat(Typeface.DEFAULT_BOLD, true, 24));

        view.scratch.pointD.x = this.x;
        view.scratch.pointD.y = view._top-this.y; // invert Y for GL origin
        view.scratch.pointD.z = 0d;

        view.inverse(view.scratch.pointD, view.scratch.geo, MapRenderer2.InverseMode.RayCast, 0, MapRenderer2.DisplayOrigin.Lowerleft);
        view.scratch.geo.set(ElevationManager.getElevation(view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), null));

        final float padding = 8f;
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(padding, view._bottom + padding, 0f);
        textRenderer.draw(String.format("Pointer " + LLA_FORMAT, view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), view.scratch.geo.getAltitude(), "HAE"), 1f, 1f, 1f, 1f);

        GLES20FixedPipeline.glTranslatef(0f, textRenderer.getBaselineSpacing(), 0f);
        view.scene.mapProjection.inverse(view.scene.camera.location, view.scratch.geo);
        textRenderer.draw(String.format("Camera" + LLA_FORMAT, view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), view.scratch.geo.getAltitude()-ElevationManager.getElevation(view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), null), "AGL"), 1f, 1f, 1f, 1f);

        GLES20FixedPipeline.glPopMatrix();

        // XXX - allows visual debug of pointer location
        if(debugPointerLocation) {
            FloatBuffer box = Unsafe.allocateDirect(10, FloatBuffer.class);
            try {
                view.inverse(view.scratch.pointD, view.scratch.geo, MapRenderer2.InverseMode.RayCast, 0, MapRenderer2.DisplayOrigin.Lowerleft);

                box.put(0, -2f);
                box.put(1, 2f);
                box.put(2, 2f);
                box.put(3, 2f);
                box.put(4, 2f);
                box.put(5, -2f);
                box.put(6, -2f);
                box.put(7, -2f);
                box.put(8, -2f);
                box.put(9, 2f);

                GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
                GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, box);

                GLES20FixedPipeline.glLineWidth(4f);

                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(this.x, view._top - this.y, 0f);
                GLES20FixedPipeline.glColor4f(0f, 0f, 1f, 1f);
                GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0, 5);
                GLES20FixedPipeline.glPopMatrix();

                GLES20FixedPipeline.glLineWidth(2f);

                GLES20FixedPipeline.glPushMatrix();
                view.forward(view.scratch.geo, view.scratch.pointF);
                GLES20FixedPipeline.glTranslatef(view.scratch.pointF.x, view.scratch.pointF.y, 0f);
                GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
                GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0, 5);
                GLES20FixedPipeline.glPopMatrix();

                GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            } finally {
                Unsafe.free(box);
            }
        }
    }

    @Override
    public void release() {
        super.release();

        textRenderer = null;
    }

    @Override
    public void start() {
        super.start();

        ((PointerInformationOverlay)this.subject).addOnPointerLocationUpdateListener(this);
    }

    @Override
    public void stop() {
        ((PointerInformationOverlay)this.subject).removeOnPointerLocationUpdateListener(this);

        super.stop();
    }

    @Override
    public void onPointerLocationUpdate(PointerInformationOverlay overlay, float x, float y) {
        this.x = x;
        this.y = y;
    }
}
