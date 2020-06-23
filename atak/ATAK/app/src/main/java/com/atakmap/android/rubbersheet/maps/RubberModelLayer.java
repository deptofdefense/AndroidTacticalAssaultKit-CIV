
package com.atakmap.android.rubbersheet.maps;

import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

/**
 * Map layer for rubber models
 */
public class RubberModelLayer extends AbstractLayer {

    private static final String LAYER_NAME = "Rubber Models";

    private final MapView _mapView;
    private final RubberSheetMapGroup _group;

    private final GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> p) {
            if (p.second != RubberModelLayer.this)
                return null;

            return new GLRubberModelLayer(p.first, RubberModelLayer.this,
                    _group);
        }
    };

    public RubberModelLayer(MapView mapView, RubberSheetMapGroup group) {
        super(LAYER_NAME);
        _mapView = mapView;
        _group = group;

        GLLayerFactory.register(SPI);
        _mapView.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, this);
    }

    public void dispose() {
        GLLayerFactory.unregister(SPI);
        _mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, this);
    }
}
