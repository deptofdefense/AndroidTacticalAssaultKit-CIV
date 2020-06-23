package com.atakmap.map.layer.feature.style.opengl;

import android.util.Pair;

import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.spi.PriorityServiceProvider;

public interface GLStyleSpi extends PriorityServiceProvider<GLStyle, Pair<Style, Geometry>>{}
