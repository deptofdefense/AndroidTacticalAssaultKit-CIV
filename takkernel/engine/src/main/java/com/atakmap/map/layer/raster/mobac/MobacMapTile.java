
package com.atakmap.map.layer.raster.mobac;

import android.graphics.Bitmap;

public class MobacMapTile {

    public final Bitmap bitmap;
    public final byte[] data;
    public final long expiration;

    public MobacMapTile(Bitmap bitmap, byte[] data, long expiration) {
        this.bitmap = bitmap;
        this.data = data;
        this.expiration = expiration;
    }
}
