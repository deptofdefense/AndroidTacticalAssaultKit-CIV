package com.atakmap.map.layer.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.spi.PriorityServiceProvider;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface GLLayerSpi2 extends PriorityServiceProvider<GLLayer2, Pair<MapRenderer, Layer>> {
}
