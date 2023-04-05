
package com.atakmap.android.tilecapture;

import android.graphics.Matrix;

import com.atakmap.annotations.ModifierApi;
import com.atakmap.coremap.maps.coords.GeoBounds;

/**
 * Tile capture bounds and matrix
 */
public class TileCaptureBounds extends GeoBounds {

    // Imagery-local bounds
    public double southImageBound = Long.MAX_VALUE;
    public double westImageBound = Long.MAX_VALUE;
    public double northImageBound = -Long.MAX_VALUE;
    public double eastImageBound = -Long.MAX_VALUE;

    // Image dimensions of tiles (before aspect ratio correction)
    public int tileImageWidth;
    public int tileImageHeight;

    // Final image dimensions
    public int imageWidth;
    public int imageHeight;

    // Correction for tiles being captured
    public final Matrix tileToPixel = new Matrix();

    public TileCaptureBounds(GeoBounds bounds) {
        super(bounds);
    }
}
