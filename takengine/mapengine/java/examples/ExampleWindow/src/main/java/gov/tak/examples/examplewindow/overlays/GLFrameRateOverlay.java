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

public class GLFrameRateOverlay extends GLAbstractLayer2 {
    final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> object) {
            if (!(object.second instanceof FrameRateOverlay))
                return null;
            return new GLFrameRateOverlay(object.first, object.second);
        }
    };

    final static String FPS_FORMAT = "%03.2f FPS";

    GLText textRenderer;

    long count;
    long timeCall;
    double currentFramerate;
    long lastReport;

    GLFrameRateOverlay(MapRenderer surface, Layer subject) {
        super(surface, subject, GLMapView.RENDER_PASS_UI);
    }

    @Override
    protected void drawImpl(GLMapView view, int renderPass) {
        if (textRenderer == null)
            textRenderer = GLText.getInstance(new MapTextFormat(Typeface.DEFAULT_BOLD, true, 24));

        if (count == 0) {
            timeCall = view.animationLastTick;
        } else if (count > 1000) {
            currentFramerate = (1000000.0 / (view.animationLastTick - timeCall));
            timeCall = view.animationLastTick;
            count = 0;
            lastReport = timeCall;
        } else if ((view.animationLastTick - lastReport) > 1000) {
            currentFramerate = ((count * 1000.0d) / (view.animationLastTick - timeCall));
            lastReport = view.animationLastTick;

            if ((view.animationLastTick - timeCall) > 5000) {
                timeCall = view.animationLastTick;
                count = 0;
                lastReport = timeCall;
            }
        }
        count++;



        final float padding = 8f;
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(padding, view._top - padding - textRenderer.getCharHeight(), 0f);
        textRenderer.draw(String.format(FPS_FORMAT, currentFramerate), 1f, 1f, 1f, 1f);
        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void release() {
        super.release();

        textRenderer = null;
    }
}
