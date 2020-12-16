package gov.tak.examples.examplewindow.overlays;

import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.opengl.GLLayerFactory;

public class FrameRateOverlay extends AbstractLayer {
    static {
        GLLayerFactory.register(GLFrameRateOverlay.SPI);
    }

    public FrameRateOverlay() {
        super("Frame Rate");
    }
}
