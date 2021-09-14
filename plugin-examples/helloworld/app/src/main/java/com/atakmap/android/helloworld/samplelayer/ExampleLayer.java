
package com.atakmap.android.helloworld.samplelayer;

import com.atakmap.android.maps.MetaShape;
import com.atakmap.android.menu.PluginMenuParser;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import com.atakmap.map.layer.AbstractLayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.UUID;

public class ExampleLayer extends AbstractLayer {

    public static final String TAG = "ExampleLayer";

    final int[] layerARGB;
    final int layerWidth;
    final int layerHeight;

    final GeoPoint upperLeft;
    final GeoPoint upperRight;
    final GeoPoint lowerRight;
    final GeoPoint lowerLeft;

    private final Context pluginContext;
    private final MetaShape metaShape;

    public ExampleLayer(Context plugin, final String name, final String uri) {
        super(name);

        this.pluginContext = plugin;

        this.upperLeft = GeoPoint.createMutable();
        this.upperRight = GeoPoint.createMutable();
        this.lowerRight = GeoPoint.createMutable();
        this.lowerLeft = GeoPoint.createMutable();

        final Bitmap bitmap = BitmapFactory.decodeFile(uri);
        upperLeft.set(50, -50);
        upperRight.set(50, -40);
        lowerRight.set(40, -40);
        lowerLeft.set(40, -50);

        layerWidth = bitmap.getWidth();
        layerHeight = bitmap.getHeight();
        Log.d(TAG,
                "decode file: " + uri + " " + layerWidth + " " + layerHeight);
        layerARGB = new int[layerHeight * layerWidth];

        bitmap.getPixels(layerARGB, 0, layerWidth, 0, 0, layerWidth,
                layerHeight);

        metaShape = new MetaShape(UUID.randomUUID().toString()) {
            @Override
            public GeoPointMetaData[] getMetaDataPoints() {
                return GeoPointMetaData.wrap(ExampleLayer.this.getPoints());
            }

            @Override
            public GeoPoint[] getPoints() {
                return ExampleLayer.this.getPoints();
            }

            @Override
            public GeoBounds getBounds(MutableGeoBounds bounds) {
                return ExampleLayer.this.getBounds();
            }
        };
        metaShape.setMetaString("callsign", TAG);
        metaShape.setMetaString("shapeName", TAG);
        metaShape.setType("hello_world_layer");
        metaShape.setMetaString("menu", PluginMenuParser.getMenu(
                pluginContext, "menus/layer_menu.xml"));
        bitmap.recycle();
    }

    public GeoBounds getBounds() {
        return GeoBounds.createFromPoints(getPoints());
    }

    public GeoPoint[] getPoints() {
        return new GeoPoint[] {
                upperLeft, upperRight, lowerRight, lowerLeft
        };
    }

    public MetaShape getMetaShape() {
        return metaShape;
    }
}
