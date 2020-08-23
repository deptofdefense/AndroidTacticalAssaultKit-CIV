
package com.atakmap.android.rubbersheet.maps;

import android.util.Pair;

import com.atakmap.android.maps.MapGroup;
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

    protected final MapView _mapView;
    protected final MapGroup _group;

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

    protected RubberModelLayer(MapView mapView, MapGroup group,
            String layerName) {
        super(layerName);
        _mapView = mapView;
        _group = group;
        init();
    }

    public RubberModelLayer(MapView mapView, MapGroup group) {
        this(mapView, group, LAYER_NAME);
    }

    protected void init() {
        GLLayerSpi2 spi = getSPI();
        if (spi != null) {
            GLLayerFactory.register(spi);
            _mapView.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, this);
        }
    }

    public void dispose() {
        GLLayerFactory.unregister(getSPI());
        _mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, this);
    }

    protected GLLayerSpi2 getSPI() {
        return SPI;
    }
}
