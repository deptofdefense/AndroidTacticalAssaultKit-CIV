
package com.atakmap.android.vehicle.model;

import android.util.Pair;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.maps.RubberModelLayer;
import com.atakmap.android.vehicle.model.opengl.GLVehicleModelLayer;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

/**
 * Map layer for vehicle models
 */
public class VehicleModelLayer extends RubberModelLayer {

    private static final String LAYER_NAME = "Vehicle Models";

    private final GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> p) {
            if (p.second != VehicleModelLayer.this)
                return null;

            return new GLVehicleModelLayer(p.first, VehicleModelLayer.this,
                    _group);
        }
    };

    public VehicleModelLayer(MapView mapView, MapGroup group) {
        super(mapView, group, LAYER_NAME);
        init();
    }

    @Override
    protected GLLayerSpi2 getSPI() {
        return SPI;
    }
}
