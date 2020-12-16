package com.atakmap.map.formats.mapbox;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.UriFactory;
import com.atakmap.io.WebProtocolHandler;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.elevation.ElevationSourceManager;
import com.atakmap.map.elevation.TiledElevationSource;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.spatial.GeometryTransformer;
import com.atakmap.util.Collections2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class MapBoxElevationSource extends TiledElevationSource {

    final static TileMatrix.ZoomLevel[] zoomLevels;
    static {
        TileMatrix.ZoomLevel min = new TileMatrix.ZoomLevel();
        min.level = 0;
        min.resolution = OSMUtils.mapnikTileResolution(0);
        min.pixelSizeX = min.resolution;
        min.pixelSizeY = min.resolution;
        min.tileWidth = 256;
        min.tileHeight = 256;
        zoomLevels = TileMatrix.Util.createQuadtree(min, 15-0+1);
    }

    // bounds in 3857
    final static Envelope gridBounds = new Envelope(
            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 1), OSMUtils.mapnikTileLng(0, 0)), null).x,
            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 1), OSMUtils.mapnikTileLng(0, 0)), null).y,
            0d,
            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 0), OSMUtils.mapnikTileLng(0, 1)), null).x,
            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 0), OSMUtils.mapnikTileLng(0, 1)), null).y,
            0d);

    static File getFile(File cacheDir, int z, int x, int y) {
        return new File(cacheDir, String.valueOf(z) + "/" + String.valueOf(x) + "/" + String.valueOf(y) + ".pngraw");
    }

    private Set<OnContentChangedListener> listeners = Collections2.newIdentityHashSet();
    private Cache cache;
    private Client client;

    private WebProtocolHandler handler;

    private Executor executor = Executors.newFixedThreadPool(3);

    public MapBoxElevationSource(String token, File cacheFile) {
        super("Mapbox", 3857, gridBounds, gridBounds.minX, gridBounds.maxY, TileScheme.WebMercator, zoomLevels, false);

        cache = new Cache(cacheFile);
        client = new Client(token, cacheFile);
        handler = new WebProtocolHandler();
    }
    @Override
    public ElevationChunk getTerrainTile(int zoom, int x, int y) {
        final ElevationChunk retval = cache.get(zoom, x, y);
        if(retval == null)
            client.get(zoom, x, y);
        return retval;
    }

    @Override
    public synchronized void addOnContentChangedListener(OnContentChangedListener l) {
        listeners.add(l);
    }

    @Override
    public synchronized void removeOnContentChangedListener(OnContentChangedListener l) {
        listeners.remove(l);
    }

    final class Client implements TiledElevationSource.Factory.TileFetcher {
        final Set<Long> requested = new HashSet<>();
        final File cache;
        final String token;

        Client(String token, File cache) {
            this.token = token;
            this.cache = cache;
        }

        @Override
        public ElevationChunk get(int zoom, int x, int y) {
            File f = getFile(cache, zoom, x, y);
            if(f.exists())
                return null;
            final long key = OSMUtils.getOSMDroidSQLiteIndex(zoom, x, y);
            synchronized(MapBoxElevationSource.this) {
                if (requested.contains(key))
                    return null;
                requested.add(key);
            }
            executor.execute(new Downloader(zoom, x, y));
            return null;
        }

        final class Downloader implements Runnable {

            final int zoom;
            final int x;
            final int y;

            Downloader(int zoom, int x, int y) {
                this.zoom = zoom;
                this.x = x;
                this.y = y;
            }

            @Override
            public void run() {
                try {
                    final String uri = "https://api.mapbox.com/v4/mapbox.terrain-rgb/" + zoom + "/" + x + "/" + y + ".pngraw";
                    try (UriFactory.OpenResult result = handler.handleURI(uri + "?access_token=" + token)) {
                        if (result == null)
                            return;

                        File f = getFile(cache, zoom, x, y);
                        if(!f.getParentFile().exists())
                            f.getParentFile().mkdirs();
                        FileSystemUtils.copyStream(result.inputStream, false, new FileOutputStream(f), true);

                        synchronized(MapBoxElevationSource.this) {
                            requested.remove(OSMUtils.getOSMDroidSQLiteIndex(zoom, x, y));
                            for(OnContentChangedListener l : MapBoxElevationSource.this.listeners)
                                l.onContentChanged(MapBoxElevationSource.this);
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    final static class Cache implements TiledElevationSource.Factory.TileFetcher {
        TerrainRGBCache bitmapCache = new TerrainRGBCache(10*1024*1024);
        final File cache;

        Cache(File cache) {
            this.cache = cache;
        }

        @Override
        public ElevationChunk get(int zoom, int x, int y) {
            File f = getFile(cache, zoom, x, y);
            if(!f.exists())
                return null;
            try {
                final long key = OSMUtils.getOSMDroidSQLiteIndex(zoom, x, y);
                // check the cache
                final ElevationChunk chunk = bitmapCache.get(key);
                if(chunk != null) // return new reference to cached data
                    return ElevationChunk.Factory.makeShared(chunk);

                // decode the bitmap
                Bitmap data;
                try(FileInputStream fis = IOProviderFactory.getInputStream(f)) {
                    data = BitmapFactory.decodeStream(fis);
                } catch(IOException e) {
                    data = null;
                }
                if (data == null)
                    return null;

                final double uly = OSMUtils.mapnikTileLat(zoom, y);
                final double ulx = OSMUtils.mapnikTileLng(zoom, x);
                final double lry = OSMUtils.mapnikTileLat(zoom, y + 1);
                final double lrx = OSMUtils.mapnikTileLng(zoom, x + 1);

                // create the chunk, as shared reference
                final ElevationChunk terrainrgb = ElevationChunk.Factory.makeShared(
                    ElevationChunk.Factory.create(
                        "Mapbox",
                        f.getAbsolutePath(),
                        ElevationData.MODEL_TERRAIN,
                        OSMUtils.mapnikTileResolution(zoom),
                        (Polygon) GeometryFactory.polygonFromQuad(ulx, uly, lrx, uly, lrx, lry, ulx, lry),
                        Double.NaN,
                        Double.NaN,
                        false,
                        new TerrainRGB(data, x, y, zoom, true)));
                // add a shared reference to the cache
                bitmapCache.put(key, ElevationChunk.Factory.makeShared(terrainrgb));
                // return the locally created reference
                return terrainrgb;
            } catch (Throwable t) {
                return null;
            }
        }
    }
}
