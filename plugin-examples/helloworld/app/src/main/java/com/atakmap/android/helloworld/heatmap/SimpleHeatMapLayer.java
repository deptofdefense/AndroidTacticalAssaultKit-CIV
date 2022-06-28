
package com.atakmap.android.helloworld.heatmap;

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
import java.util.concurrent.ConcurrentLinkedQueue;

public class SimpleHeatMapLayer extends AbstractLayer {

    public static final String TAG = "ExampleLayer";

    final int[] layerARGB;
    final int layerWidth;
    final int layerHeight;

    private GeoPoint upperLeft;
    private GeoPoint upperRight;
    private GeoPoint lowerRight;
    private GeoPoint lowerLeft;

    private final Context pluginContext;
    private final MetaShape metaShape;

    private final ConcurrentLinkedQueue<OnLayerChangedListener> layerChangedListeners = new ConcurrentLinkedQueue<>();


    /**
     * Interface for when any attribute of the grid has been changed
     */
    public interface OnLayerChangedListener {
        void onLayerChanged(SimpleHeatMapLayer simpleHeatMapLayer);
    }


    /**
     * Build a simple heat map layer based on bounds
     * @param plugin the context
     * @param name the name
     * @param layerWidth the data width
     * @param layerHeight the data height
     * @param bounds the bounds (IDL sensitive and will likely produce the wrong results but this
     *               is just a sample).
     */
    public SimpleHeatMapLayer(Context plugin, final String name,
                              int layerWidth, int layerHeight,
                              GeoBounds bounds) {
        this(plugin, name, layerWidth, layerHeight,
                new GeoPoint(bounds.getNorth(), bounds.getWest()),
                new GeoPoint(bounds.getNorth(), bounds.getEast()),
                new GeoPoint(bounds.getSouth(), bounds.getEast()),
                new GeoPoint(bounds.getSouth(), bounds.getWest()));

    }

    /**
     * Create a simple heat map layer
     *
     * @param plugin      the plugin
     * @param name        the name of the layer
     * @param layerWidth  the heat map data width
     * @param layerHeight the heat map data height
     * @param upperLeft   the upper left of the layer
     * @param lowerRight  the lower right of the layer
     */
    public SimpleHeatMapLayer(Context plugin, final String name,
                              int layerWidth, int layerHeight,
                              GeoPoint upperLeft, GeoPoint upperRight,
                              GeoPoint lowerRight, GeoPoint lowerLeft) {
        super(name);

        this.pluginContext = plugin;
        layerARGB = new int[layerHeight * layerWidth];
        this.upperLeft = upperLeft;
        this.upperRight = upperRight;
        this.lowerRight = lowerRight;
        this.lowerLeft = lowerLeft;
        this.layerWidth = layerWidth;
        this.layerHeight = layerHeight;

        metaShape = new MetaShape(UUID.randomUUID().toString()) {
            @Override
            public GeoPointMetaData[] getMetaDataPoints() {
                return GeoPointMetaData.wrap(SimpleHeatMapLayer.this.getPoints());
            }

            @Override
            public GeoPoint[] getPoints() {
                return SimpleHeatMapLayer.this.getPoints();
            }

            @Override
            public GeoBounds getBounds(MutableGeoBounds bounds) {
                return SimpleHeatMapLayer.this.getBounds();
            }
        };
        metaShape.setMetaString("callsign", TAG);
        metaShape.setMetaString("shapeName", TAG);
        metaShape.setType("hello_world_layer");
        metaShape.setMetaString("menu", PluginMenuParser.getMenu(
                pluginContext, "menus/layer_menu.xml"));
    }

    /**
     * Return the geobounds for the Simple Heat Map Layer based on the povided geopoints.
     *
     * @return
     */
    public GeoBounds getBounds() {
        return GeoBounds.createFromPoints(getPoints());
    }

    public GeoPoint[] getPoints() {
        return new GeoPoint[]{
                upperLeft, upperRight, lowerRight, lowerLeft
        };
    }

    public MetaShape getMetaShape() {
        return metaShape;
    }

    /**
     * Modify a single point in a heat map
     *
     * @param x    the x value for the point based on the layer data width,height
     * @param y    the y value for the point based on the layer data width,height
     * @param argb the data value
     */
    public void setData(int x, int y, int argb) {
        layerARGB[y * layerHeight + x] = argb;
    }


    /**
     * Refresh the entire layer based on an int array [argb] based
     * @param values the array sizes eneds to match the orginal layer size.
     */
    public void setData(int[] values) {
        if (values.length != layerARGB.length)
            throw new IllegalStateException("data not the same size as the layer");
        for (int i = 0; i < values.length; ++i)
            layerARGB[i] = values[i];
    }

    /**
     * Set the location of the corners for the layer
     *
     * @param corners - array of GeoPoints one for each corner upperleft, upper right, lower right, lower left
     */
    public void setCorners(GeoPoint[] corners) {
        upperLeft = corners[0];
        upperRight = corners[1];
        lowerRight = corners[2];
        lowerLeft = corners[3];
        this.dispatchFrameChanged();
    }

    /**
     * Causes the heat map to be refreshed visually.
     */
    public void refresh() {
        this.dispatchFrameChanged();
    }

    /**
     * Set the corners based on a GeoBounds
     * @param bounds not tested over IDL
     */
    public void setCorners(GeoBounds bounds) {
        setCorners(new GeoPoint[] { new GeoPoint(bounds.getNorth(), bounds.getWest()),
                new GeoPoint(bounds.getNorth(), bounds.getEast()),
                new GeoPoint(bounds.getSouth(), bounds.getEast()),
                new GeoPoint(bounds.getSouth(), bounds.getWest()) });
    }

    /**
     * Adds a listener for changes to the queue
     *
     * @param listener - OnLayerChangedListener to be added
     */
    public void addOnLayerChangedListener(OnLayerChangedListener listener) {
        this.layerChangedListeners.add(listener);
    }

    /**
     * Removes a listener from the queue
     *
     * @param listener - OnLayerChangedListener to be removed
     */
    public void removeOnLayerChangedListener(OnLayerChangedListener listener) {
        this.layerChangedListeners.remove(listener);
    }


    /**
     * Calls for the update when a layer has been changed for each listener in the queue
     */
    private void dispatchFrameChanged() {
        for (OnLayerChangedListener l : this.layerChangedListeners) {
            l.onLayerChanged(this);
        }
    }
}

