package com.atakmap.map.formats.mapbox;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.math.MathUtils;

final class TerrainRGB extends ElevationChunk.Factory.Sampler {
    final int tileColumn;
    final int tileRow;
    final int tileZoom;

    Bitmap data;
    boolean recycleOnDispose;

    public TerrainRGB(Bitmap data, int x, int y, int z, boolean recycleOnDispose) {
        this.data = data;

        this.tileColumn = x;
        this.tileRow = y;
        this.tileZoom = z;
        this.recycleOnDispose = recycleOnDispose;
    }
    @Override
    public double sample(double latitude, double longitude) {
        final double x = OSMUtils.mapnikPixelXd(tileZoom, tileColumn, longitude);
        final double y = OSMUtils.mapnikPixelYd(tileZoom, tileRow, latitude);
        if(x < 0d || x > 255d || y < 0d || y > 255d)
            return Double.NaN;

        final double weight_l = 1d-(x-(int)x);
        final double weight_r = (x-(int)x);
        final double weight_t = 1d-(y-(int)y);
        final double weight_b = (y-(int)y);

        final double ul = decode(data, MathUtils.clamp((int)x, 0, data.getWidth()-1), MathUtils.clamp((int)y, 0, data.getHeight()-1));
        final double ur = decode(data, MathUtils.clamp((int)Math.ceil(x), 0, data.getWidth()-1), MathUtils.clamp((int)y, 0, data.getHeight()-1));
        final double lr = decode(data, MathUtils.clamp((int)Math.ceil(x), 0, data.getWidth()-1), MathUtils.clamp((int)Math.ceil(y), 0, data.getHeight()-1));
        final double ll = decode(data, MathUtils.clamp((int)x, 0, data.getWidth()-1), MathUtils.clamp((int)Math.ceil(y), 0, data.getHeight()-1));

        return (ul*weight_l*weight_t) +
               (ur*weight_r*weight_t) +
               (lr*weight_r*weight_b) +
               (ll*weight_l*weight_b);
    }

    @Override
    public void dispose() {
        if(this.recycleOnDispose)
            data.recycle();
    }

    final double decode(Bitmap bitmap, int x, int y) {
        final int v = bitmap.getPixel(x, y);
        final int R = Color.red(v);
        final int G = Color.green(v);
        final int B = Color.blue(v);
        return -10000 + ((R * 256 * 256 + G * 256 + B) * 0.1);
    }
}
