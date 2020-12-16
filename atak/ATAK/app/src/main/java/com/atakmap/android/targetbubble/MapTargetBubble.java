
package com.atakmap.android.targetbubble;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.Globe;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MapTargetBubble extends AbstractLayer {

    private final int _x;
    private final int _y;
    private final int _width;
    private final int _height;
    private final ConcurrentLinkedQueue<OnLocationChangedListener> _onLocationChangedListener = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnScaleChangedListener> _onScaleChangedListener = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnCrosshairColorChangedListener> _onCrosshairColorChangedListener = new ConcurrentLinkedQueue<>();

    private final Polygon viewport;

    private final boolean coordExtraction;

    private final Globe globe;
    private final CrosshairLayer crosshair;
    private double _latitude;
    private double _longitude;
    private double _scale;

    public MapTargetBubble(MapView mapView, int x, int y, int width,
            int height, double mapScale) {
        this(mapView,
                legacyLayerSet(mapView),
                x, y, width, height,
                mapScale);
    }

    public MapTargetBubble(MapView mapView, List<? extends Layer> layers,
            int x, int y, int width,
            int height, double mapScale) {

        this(mapView, layers, createRectangle(x, y, width, height), mapScale,
                false);
    }

    public MapTargetBubble(MapView mapView, List<? extends Layer> layers,
            Polygon viewport, double mapScale, boolean coordExtraction) {
        super("Target Bubble");

        this.globe = new Globe();
        for (Layer layer : layers)
            this.globe.addLayer(layer);

        this.crosshair = new CrosshairLayer("Target Bubble Crosshair");
        this.crosshair.setColor(0xFF000000);

        this.viewport = viewport;

        if (this.viewport != null) {
            Envelope bounds = this.viewport.getEnvelope();
            _x = (int) bounds.minX;
            _y = (int) bounds.minY;
            _width = (int) Math.ceil(bounds.maxX - bounds.minX);
            _height = (int) Math.ceil(bounds.maxY - bounds.minY);
        } else {
            _x = 0;
            _y = 0;
            _width = mapView.getWidth();
            _height = mapView.getHeight();
        }

        // XXX - should never happen, need to trace up to mosaic type layer to
        // find out why we're getting a bad resolution/scale
        if (mapScale <= 0.0d)
            mapScale = 1.0d / 1926.0d;

        _latitude = mapView.getLatitude();
        _longitude = mapView.getLongitude();
        _scale = mapScale;

        this.coordExtraction = coordExtraction;
    }

    public boolean isCoordExtractionBubble() {
        return coordExtraction;
    }

    public Polygon getViewport() {
        return this.viewport;
    }

    public void shiftLocation(final double latShift, final double lngShift) {
        setLocation(_latitude + latShift, _longitude + lngShift);
    }

    public void setLocation(final double latitude, final double longitude) {
        _latitude = latitude;
        _longitude = longitude;
        onLocationChanged();
    }

    public List<Layer> getLayers() {
        return this.globe.getLayers();
    }

    public Globe getGlobe() {
        return this.globe;
    }

    public CrosshairLayer getCrosshair() {
        return crosshair;
    }

    public double getLatitude() {
        return _latitude;
    }

    public double getLongitude() {
        return _longitude;
    }

    public int getX() {
        return _x;
    }

    public int getY() {
        return _y;
    }

    public int getWidth() {
        return _width;
    }

    public int getHeight() {
        return _height;
    }

    /**
     * Returns an appropriate map scale for displaying the content in the bubble based on the
     * available layers.
     * 
     * @return the map scale.
     */
    public double getMapScale() {
        return _scale;
    }

    /**
     * Sets the map scale for displaying the content of the bubble based on the available layers
     * @param scale the scale to set
     */
    public void setMapScale(double scale) {
        _scale = scale;
        onScaleChanged();
    }

    public int getCrosshairColor() {
        return this.crosshair.getCrosshairColor();
    }

    /**
     * Sets the  color of the crosshair.
     * @param color the crosshair changed
     */
    public void setCrosshairColor(int color) {
        if (getCrosshairColor() != color) {
            this.crosshair.setColor(color);

            this.onCrosshairColorChanged();
        }
    }

    public interface OnLocationChangedListener {
        void onMapTargetBubbleLocationChanged(MapTargetBubble bubble);
    }

    public void addOnLocationChangedListener(OnLocationChangedListener l) {
        _onLocationChangedListener.add(l);
    }

    public void removeOnLocationChangedListener(OnLocationChangedListener l) {
        _onLocationChangedListener.remove(l);
    }

    protected void onLocationChanged() {
        for (OnLocationChangedListener l : _onLocationChangedListener) {
            l.onMapTargetBubbleLocationChanged(this);
        }
    }

    public interface OnScaleChangedListener {
        void onMapTargetBubbleScaleChanged(MapTargetBubble bubble);
    }

    public void addOnScaleChangedListener(OnScaleChangedListener l) {
        _onScaleChangedListener.add(l);
    }

    public void removeOnScaleChangedListener(OnScaleChangedListener l) {
        _onScaleChangedListener.remove(l);
    }

    protected void onScaleChanged() {
        for (OnScaleChangedListener l : _onScaleChangedListener) {
            l.onMapTargetBubbleScaleChanged(this);
        }
    }

    public interface OnCrosshairColorChangedListener {
        void onMapTargetBubbleCrosshairColorChanged(MapTargetBubble bubble);
    }

    public void addOnCrosshairColorChangedListener(
            OnCrosshairColorChangedListener l) {
        _onCrosshairColorChangedListener.add(l);
    }

    public void removeOnCrosshairColorChangedListener(
            OnCrosshairColorChangedListener l) {
        _onCrosshairColorChangedListener.remove(l);
    }

    protected void onCrosshairColorChanged() {
        for (OnCrosshairColorChangedListener l : _onCrosshairColorChangedListener) {
            l.onMapTargetBubbleCrosshairColorChanged(this);
        }
    }

    private static List<Layer> legacyLayerSet(MapView mapView) {
        List<Layer> retval = new LinkedList<>();

        retval.addAll(mapView.getLayers(MapView.RenderStack.BASEMAP));
        retval.addAll(mapView.getLayers(MapView.RenderStack.MAP_LAYERS));
        retval.addAll(mapView.getLayers(MapView.RenderStack.RASTER_OVERLAYS));

        return retval;
    }

    private static Polygon createRectangle(int x, int y, int w, int h) {
        LineString rect = new LineString(2);
        rect.addPoint(x, y);
        rect.addPoint(x + w, y);
        rect.addPoint(x + w, y + h);
        rect.addPoint(x, y + h);
        rect.addPoint(x, y);

        Polygon retval = new Polygon(rect.getDimension());
        retval.addRing(rect);
        return retval;
    }
}
