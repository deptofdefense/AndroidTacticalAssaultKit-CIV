
package com.atakmap.map.layer.raster.mobac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import com.atakmap.coremap.maps.coords.GeoBounds;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

public class CustomMultiLayerMobacMapSource implements MobacMapSource {

    private final String name;
    private final int srid;
    private final float[] layerAlpha;
    private final MobacMapSource[] layers;
    private final int minZoom;
    private final int maxZoom;
    private final int backgroundColor;
    private Config config = new Config();

    public CustomMultiLayerMobacMapSource(String name, MobacMapSource[] layers, float[] layerAlpha,
            int backgroundColor) {
        this.name = name;
        this.layers = layers;
        
        if (layerAlpha == null) {
            layerAlpha = new float[this.layers.length];
            Arrays.fill(layerAlpha, 1.0f);
        }
        this.layerAlpha = layerAlpha;

        int min = this.layers[0].getMinZoom();
        int max = this.layers[0].getMaxZoom();
        for (int i = 0; i < this.layers.length; i++) {
            if (this.layers[i].getMinZoom() < min)
                min = this.layers[i].getMinZoom();
            if (this.layers[i].getMaxZoom() > max)
                max = this.layers[i].getMaxZoom();
        }
        this.minZoom = min;
        this.maxZoom = max;
        this.backgroundColor = backgroundColor;
        
        this.srid = this.layers[0].getSRID();
        for(int i = 1; i < this.layers.length; i++) {
            if(this.layers[i].getSRID() != this.srid)
                throw new IllegalArgumentException();
        }
    }

    @Override
    public int getSRID() {
        return this.srid;
    }

    @Override
    public final void clearAuthFailed() {
        for (int i = 0; i < this.layers.length; i++)
            this.layers[i].clearAuthFailed();
    }

    @Override
    public final String getName() {
        return this.name;
    }

    @Override
    public final int getTileSize() {
        return this.layers[0].getTileSize();
    }

    @Override
    public final int getMinZoom() {
        return this.minZoom;
    }

    @Override
    public final int getMaxZoom() {
        return this.maxZoom;
    }

    @Override
    public final String getTileType() {
        return "JPG";
    }

    @Override
    public GeoBounds getBounds() {
        return null;
    }

    @Override
    public final void checkConnectivity() {
        for (int i = 0; i < this.layers.length; i++)
            this.layers[i].checkConnectivity();
    }

    @Override
    public MobacMapTile loadTile(int zoom, int x, int y, BitmapFactory.Options opts)
            throws IOException {
        long expiration = Long.MAX_VALUE;

        Bitmap composite = Bitmap.createBitmap(this.getTileSize(), this.getTileSize(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(composite);
        canvas.drawColor(this.backgroundColor);

        Paint alphaPaint = new Paint();

        BitmapFactory.Options tileOpts = null;
        if(opts != null && opts.inJustDecodeBounds) {
            tileOpts = new BitmapFactory.Options();
            tileOpts.inDensity = opts.inDensity;
            tileOpts.inDither = opts.inDither;
            tileOpts.inInputShareable = opts.inInputShareable;
            tileOpts.inJustDecodeBounds = false;
            tileOpts.inMutable = opts.inMutable;
            tileOpts.inPreferQualityOverSpeed = opts.inPreferQualityOverSpeed;
            tileOpts.inPreferredConfig = opts.inPreferredConfig;
            tileOpts.inPurgeable = opts.inPurgeable;
            tileOpts.inSampleSize = opts.inSampleSize;
            tileOpts.inScaled = opts.inScaled;
            tileOpts.inScreenDensity = opts.inScreenDensity;
            tileOpts.inTargetDensity = opts.inTargetDensity;
            tileOpts.inTempStorage = opts.inTempStorage;
            tileOpts.mCancel = opts.mCancel;
        }

        MobacMapTile tile;
        for (int i = 0; i < this.layers.length; i++) {
            if (zoom < this.layers[i].getMinZoom() || zoom > this.layers[i].getMaxZoom())
                continue;
            tile = null;
            try {
                tile = this.layers[i].loadTile(zoom, x, y, tileOpts);
                if (tile == null || tile.bitmap == null)
                    continue;
                if (tile.expiration < expiration)
                    expiration = tile.expiration;
                alphaPaint.setAlpha((int) (this.layerAlpha[i] * 255));
                canvas.drawBitmap(tile.bitmap, 0, 0, alphaPaint);
            } finally {
                if (tile != null && tile.bitmap != null)
                    tile.bitmap.recycle();
            }
        }

        byte[] data = null;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            if (composite.compress(Bitmap.CompressFormat.PNG, 80, os))
                data = os.toByteArray();
        } finally {
            os.close();
        }

        if(opts != null && opts.inJustDecodeBounds && composite != null) {
            opts.outWidth = composite.getWidth();
            opts.outHeight = composite.getHeight();

            composite.recycle();
            composite = null;
        }

        return new MobacMapTile(composite, data, expiration);
    }

    @Override
    public void setConfig(Config c) {
        config = c;
    }

}
