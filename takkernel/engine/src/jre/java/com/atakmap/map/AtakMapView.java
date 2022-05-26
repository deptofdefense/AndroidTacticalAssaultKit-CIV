
package com.atakmap.map;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.projection.Projection;

import java.util.List;

public abstract class AtakMapView implements RenderSurface {
    public interface OnDisplayFlagsChangedListener {
        void onDisplayFlagsChanged(AtakMapView view);
    }
    public interface OnLayersChangedListener {
        void onLayerAdded(AtakMapView mapView, Layer layer);
        void onLayerRemoved(AtakMapView mapView, Layer layer);
        void onLayerPositionChanged(AtakMapView mapView, Layer layer, int oldPosition, int newPosition);
    }
    public interface OnElevationExaggerationFactorChangedListener {
        void onTerrainExaggerationFactorChanged(AtakMapView mapView, double factor);
    }
    public interface OnContinuousScrollEnabledChangedListener {
        void onContinuousScrollEnabledChanged(AtakMapView mapView, boolean enabled);
    }
    public interface OnMapViewResizedListener {
        void onMapViewResized(AtakMapView view);
    }
    public interface OnMapMovedListener {
        void onMapMoved (AtakMapView view, boolean animate);
    }
    public interface OnMapProjectionChangedListener {
        void onMapProjectionChanged(AtakMapView view);
    }

    public abstract Globe getGlobe();

    public static MapTextFormat getDefaultTextFormat() {
        throw new UnsupportedOperationException();
    }

    public abstract AtakMapController getMapController();
    public abstract double getMapTilt();
    public abstract double getMapRotation();
    public abstract double getMapScale();
    public abstract double getLatitude();
    public abstract double getLongitude();
    public abstract boolean isContinuousScrollEnabled();
    public abstract Projection getProjection();
    public abstract List<Layer> getLayers();
    public abstract double getDisplayDpi();
}
