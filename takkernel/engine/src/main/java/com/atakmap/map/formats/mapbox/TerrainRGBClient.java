package com.atakmap.map.formats.mapbox;

import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.elevation.TiledElevationSource;

import java.io.File;
import java.util.concurrent.Executor;

final class TerrainRGBClient implements TiledElevationSource.Factory.TileFetcher {
    ElevationSource.OnContentChangedListener listener;

    File cacheDir;
    String baseUrl;
    String token;

    Executor worker;

    public synchronized void setListener(ElevationSource.OnContentChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public synchronized ElevationChunk get(int zoom, int x, int y) {
        // XXX - if queued, return
        // XXX - add to queue
        // XXX - sort queue
        //       > level, asc
        //       > insert, desc

        return null;
    }

    public static String getTileUrl(String baseUrl, int zoom, int x, int y, String token) {
        StringBuilder sb = new StringBuilder(baseUrl);
        if(baseUrl.charAt(baseUrl.length()-1) != '/')
            sb.append('/');
        sb.append(zoom);
        sb.append('/');
        sb.append(x);
        sb.append('/');
        sb.append(y);
        sb.append(".pngraw");
        if(token != null) {
            sb.append("?access_token=");
            sb.append(token);
        }
        return sb.toString();
    }
}
