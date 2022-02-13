package gov.tak.api.engine.map;

import java.util.List;

public interface IGlobe {
    interface OnLayersChangedListener {
        void onLayerAdded(IGlobe mapView, ILayer layer);
        void onLayerRemoved(IGlobe mapView, ILayer layer);

        /**
         * Notifies the user when the position of the layer has been explicitly
         * changed. This callback will <B>NOT</B> be invoked when a layer's
         * position changes due to the addition or removal of other layers.
         *
         * @param mapView       The map view
         * @param layer         The layer
         * @param oldPosition   The layer's old position
         * @param newPosition   The layer's new position
         */
        void onLayerPositionChanged(IGlobe mapView, ILayer layer, int oldPosition, int newPosition);
    }

    void getLayers(List<ILayer> layers);
    ILayer getLayerAt(int position);
    int getNumLayers();
    void setLayerPosition(ILayer layer, int position);
    void removeAllLayers();
    void removeLayer(ILayer layer);
    void addLayer(int position, ILayer layer);
    void addLayer(ILayer layer);

    void addOnLayersChangedListener(OnLayersChangedListener l);
    void removeOnLayersChangedListener(OnLayersChangedListener l);
}
