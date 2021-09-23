package gov.tak.examples.examplewindow.overlays;

import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.util.Collections2;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.Set;

public class PointerInformationOverlay extends AbstractLayer implements MouseMotionListener {
    static {
        GLLayerFactory.register(GLPointerInformationOverlay.SPI);
    }

    public interface OnPointerLocationUpdateListener {
        void onPointerLocationUpdate(PointerInformationOverlay overlay, float x, float y);
    }

    private Set<OnPointerLocationUpdateListener> listeners = Collections2.newIdentityHashSet();

    public PointerInformationOverlay() {
        super("Pointer Information");
    }

    public synchronized void addOnPointerLocationUpdateListener(OnPointerLocationUpdateListener l) {
        this.listeners.add(l);
    }

    public synchronized void removeOnPointerLocationUpdateListener(OnPointerLocationUpdateListener l) {
        this.listeners.remove(l);
    }

    @Override
    public synchronized void mouseMoved(MouseEvent e) {
        for(OnPointerLocationUpdateListener l : this.listeners)
            l.onPointerLocationUpdate(this, e.getX(), e.getY());
    }

    @Override
    public synchronized void mouseDragged(MouseEvent e) {
        for(OnPointerLocationUpdateListener l : this.listeners)
            l.onPointerLocationUpdate(this, e.getX(), e.getY());
    }
}
