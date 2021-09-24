package com.atakmap.map.layer.raster.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.spi.PriorityServiceProvider;

public interface GLMapLayerSpi3 extends PriorityServiceProvider<GLMapLayer3, Pair<MapRenderer, DatasetDescriptor>> {
}
