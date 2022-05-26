package com.atakmap.map.layer.feature.style.opengl;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.spi.PriorityServiceProvider;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public interface GLStyleSpi extends PriorityServiceProvider<GLStyle, Pair<Style, Geometry>>{}
