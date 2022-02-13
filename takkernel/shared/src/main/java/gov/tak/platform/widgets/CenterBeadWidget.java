
package gov.tak.platform.widgets;

import gov.tak.platform.ui.MotionEvent;

public class CenterBeadWidget extends ShapeWidget {

    public CenterBeadWidget() {
        setName("Center Bead");
    }

    @Override
    public boolean testHit(float x, float y) {
        return false;
    }

    @Override
    public MapWidget seekWidgetHit(MotionEvent event, float x, float y) {
        return null;
    }
}
