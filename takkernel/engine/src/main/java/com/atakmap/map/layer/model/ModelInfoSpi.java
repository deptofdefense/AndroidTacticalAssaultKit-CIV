package com.atakmap.map.layer.model;

import com.atakmap.spi.ServiceProvider;

import java.util.Set;

public interface ModelInfoSpi extends ServiceProvider<Set<ModelInfo>, String> {
    public String getName();
    public int getPriority();
    public boolean isSupported(String uri);
}
