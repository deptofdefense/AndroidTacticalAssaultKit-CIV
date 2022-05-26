package com.atakmap.map.layer.raster.mobac;

public interface MobacMapSource2 extends MobacMapSource {
    long getRefreshInterval();
    boolean invalidateCacheOnInit();
}
