package gov.tak.examples.examplewindow.overlays;

import android.graphics.Typeface;
import android.util.Pair;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLAbstractLayer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.PointD;
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

    boolean debugPointerLocation = true;

    GLText textRenderer;
    MapRenderer2 renderer;
    GeoPoint pointerLocation = GeoPoint.createMutable();
    MapRenderer2.InverseResult locationType = MapRenderer2.InverseResult.None;
    float x;
    float y;

    GLPointerInformationOverlay(MapRenderer surface, Layer subject) {
        super(surface, subject, GLMapView.RENDER_PASS_UI);

        if(surface instanceof MapRenderer)
            renderer = (MapRenderer2)surface;
    }

    @Override
    protected void drawImpl(GLMapView view, int renderPass) {
        if(textRenderer == null)
            textRenderer = GLText.getInstance(new MapTextFormat(Typeface.DEFAULT_BOLD, true, 24));
        if(renderer == null)
            renderer = view;

        view.scratch.geo.set(this.pointerLocation);

        final float padding = 8f;
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(padding, view._bottom + padding, 0f);
        if(view.scratch.geo.isValid())
            textRenderer.draw(String.format("Pointer " + LLA_FORMAT, view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), ElevationManager.getElevation(view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), null), "HAE"), 1f, 1f, 1f, 1f);
        else
            textRenderer.draw("Pointer ???", 1f, 1f, 1f, 1f);

        GLES20FixedPipeline.glTranslatef(0f, textRenderer.getBaselineSpacing(), 0f);
        view.scene.mapProjection.inverse(view.scene.camera.location, view.scratch.geo);
        textRenderer.draw(String.format("Camera" + LLA_FORMAT, view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), view.scratch.geo.getAltitude()-ElevationManager.getElevation(view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude(), null), "AGL"), 1f, 1f, 1f, 1f);

        GLES20FixedPipeline.glPopMatrix();

        // XXX - allows visual debug of pointer location
        if(debugPointerLocation) {
            FloatBuffer box = Unsafe.allocateDirect(10, FloatBuffer.class);
            try {
                view.scratch.geo.set(this.pointerLocation);

                final float radius = 8f;
                box.put(0, -radius);
                box.put(1, radius);
                box.put(2, radius);
                box.put(3, radius);
                box.put(4, radius);
                box.put(5, -radius);
                box.put(6, -radius);
                box.put(7, -radius);
                box.put(8, -radius);
                box.put(9, radius);

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
                GLES20FixedPipeline.glRotatef(45f, 0f, 0f, 1f);
                switch(locationType) {
                    case GeometryModel:
                        GLES20FixedPipeline.glColor4f(1f, 1f, 0f, 1f);
                        break;
                    case SurfaceMesh:
                        GLES20FixedPipeline.glColor4f(0f, 1f, 0f, 1f);
                        break;
                    case TerrainMesh:
                        GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
                        break;
                    case Transformed:
                        GLES20FixedPipeline.glColor4f(1f, 1f, 1f, 1f);
                        break;
                    case None:
                        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0f);
                        break;
                    default:
                        GLES20FixedPipeline.glColor4f(0f, 0.5f, 0.5f, 1f);
                        break;
                }
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

        locationType = MapRenderer2.InverseResult.None;
        if(renderer != null)
            locationType = renderer.inverse(new PointD(x, y), pointerLocation, MapRenderer2.InverseMode.RayCast, 0, MapRenderer2.DisplayOrigin.UpperLeft);
        if (locationType == MapRenderer2.InverseResult.None)
            pointerLocation.set(Double.NaN, Double.NaN, Double.NaN);
    }
}
