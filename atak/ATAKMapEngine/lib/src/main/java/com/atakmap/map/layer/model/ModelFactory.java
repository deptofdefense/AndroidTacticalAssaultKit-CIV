package com.atakmap.map.layer.model;

import android.graphics.Color;


import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.spi.InteractivePrioritizedStrategyServiceProviderRegistry2;

import java.util.Set;

public final class ModelFactory {
    private final static InteractivePrioritizedStrategyServiceProviderRegistry2<Model, ModelInfo, ModelSpi, String> registry = new InteractivePrioritizedStrategyServiceProviderRegistry2<Model, ModelInfo, ModelSpi, String>();

    private ModelFactory() {}

    public static void registerSpi(ModelSpi spi) {
        registry.register(spi, spi.getType(), spi.getPriority());
    }

    public static void unregisterSpi(ModelSpi spi) {
        registry.unregister(spi);
    }

    public static Model create(ModelInfo info) {
        return registry.create(info, null, null);
    }
    public static Model create(ModelInfo info, String hint) {
        return registry.create(info, hint, null);
    }

    public static Model create(ModelInfo info, String hint, ModelSpi.Callback callback) {
        return registry.create(info, hint, callback);
    }

}
