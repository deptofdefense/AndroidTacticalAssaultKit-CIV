package com.atakmap.map.layer.raster.tilematrix;

import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.contentservices.CacheRequestListener;
import com.atakmap.map.layer.control.Controls;

public interface TileClient extends TileMatrix, Controls {
    public void clearAuthFailed(); 
    public void checkConnectivity();

    public void cache(CacheRequest request, CacheRequestListener listener);
    public int estimateTileCount(CacheRequest request);
}
