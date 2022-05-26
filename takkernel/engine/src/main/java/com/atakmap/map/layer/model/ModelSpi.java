package com.atakmap.map.layer.model;

import com.atakmap.spi.InteractiveServiceProvider;

public interface ModelSpi extends InteractiveServiceProvider<Model, ModelInfo> {
    public String getType();
    public int getPriority();
}
