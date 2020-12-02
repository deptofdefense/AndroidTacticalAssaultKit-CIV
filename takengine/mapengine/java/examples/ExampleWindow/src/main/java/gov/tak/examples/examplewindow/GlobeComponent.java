package gov.tak.examples.examplewindow;

import com.atakmap.map.Globe;
import com.atakmap.map.MapRenderer2;
import com.jogamp.opengl.GLAutoDrawable;

import java.awt.*;

public interface GlobeComponent extends GLAutoDrawable {
    interface OnRendererInitializedListener {
        void onRendererInitialized(Component comp, MapRenderer2 renderer);
    }

    void setOnRendererInitializedListener(OnRendererInitializedListener l);
    Globe getGlobe();
    MapRenderer2 getRenderer();
}
