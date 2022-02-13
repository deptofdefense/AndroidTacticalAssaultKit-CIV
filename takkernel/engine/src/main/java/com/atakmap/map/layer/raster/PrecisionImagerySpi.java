package com.atakmap.map.layer.raster;

import com.atakmap.spi.ServiceProvider;

public interface PrecisionImagerySpi extends ServiceProvider<PrecisionImagery, String> {
    @Override
    public PrecisionImagery create(String uri);
    public boolean isSupported(String uri);
    public String getType();
    public int getPriority();
}
