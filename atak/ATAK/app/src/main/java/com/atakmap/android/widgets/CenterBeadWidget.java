
package com.atakmap.android.widgets;

public class CenterBeadWidget extends ShapeWidget {

    public CenterBeadWidget() {
        setName("Center Bead");
    }

    @Override
    public boolean testHit(float x, float y) {
        return false;
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        return null;
    }
}
