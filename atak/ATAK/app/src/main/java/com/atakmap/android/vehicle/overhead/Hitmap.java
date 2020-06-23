
package com.atakmap.android.vehicle.overhead;

import android.graphics.PointF;
import java.util.BitSet;

/**
 * 1bpp bitmap used for hit-testing
 */

public class Hitmap {

    private final BitSet _data;
    private final int _width;
    private final int _height;

    public Hitmap(final BitSet pixels, final int width, final int height) {
        _data = pixels;
        _width = width;
        _height = height;
    }

    public int getWidth() {
        return _width;
    }

    public int getHeight() {
        return _height;
    }

    public boolean testHit(final int x, final int y) {
        return x >= 0 && x < _width && y >= 0 && y < _height
                && _data.get(getIndex(x, y));
    }

    public boolean testHit(final float x, final float y) {
        return testHit(Math.round(x), Math.round(y));
    }

    public boolean testHit(final PointF p) {
        return testHit(p.x, p.y);
    }

    public BitSet getData() {
        return (BitSet) _data.clone();
    }

    private int getIndex(final int x, final int y) {
        return x + (y * getWidth());
    }
}
