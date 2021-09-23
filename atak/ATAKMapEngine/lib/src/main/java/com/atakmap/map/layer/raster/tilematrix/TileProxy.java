package com.atakmap.map.layer.raster.tilematrix;

import android.graphics.Bitmap;

import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.contentservices.CacheRequestListener;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.controls.TileCacheControl;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.util.ReferenceCount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TileProxy implements TileClient {
    final static Comparator<TileFetchTask> requestPriorityComparator = new Comparator<TileFetchTask>() {
        @Override
        public int compare(TileFetchTask a, TileFetchTask b) {
            // items with highest priority go to end of LIFO queue
            return a.priority-b.priority;
        }
    };

    final ReferenceCount<TileClient> client;
    final ReferenceCount<TileContainer> cache;
    final ExecutorService clientExecutor;
    final TileCacheControl.OnTileUpdateListener[] listener;
    final Projection proj;
    long expiry;

    int priority = 0;

    ArrayList<TileFetchTask> downloadQueue = new ArrayList<>();

    PrioritizerImpl prioritizer = new PrioritizerImpl();

    public TileProxy(TileClient client, TileContainer cache) {
        this(client,
             cache,
             System.currentTimeMillis()-(24L*60L*60L*1000L), // 24 hours old
             Executors.newFixedThreadPool(3, new NamedThreadFactory("TileProxy[" + client.getName() + "]")));
    }

    TileProxy(TileClient client, TileContainer cache, long expiry, ExecutorService clientExecutor) {
        if(client == null)
            throw new IllegalArgumentException();
        if(cache == null)
            throw new IllegalArgumentException();
        if(clientExecutor == null)
            throw new IllegalArgumentException();

        this.client = new SharedTileMatrix<>(client);
        this.cache = new SharedTileMatrix<>(cache);
        this.expiry = expiry;
        this.clientExecutor = clientExecutor;
        this.listener = new TileCacheControl.OnTileUpdateListener[1];

        this.proj = ProjectionFactory.getProjection(client.getSRID());
    }

    /**
     * Requests that the client abort the download of the specified tile
     * @param zoom
     * @param x
     * @param y
     */
    public void abortTile(int zoom, int x, int y) {
        synchronized (downloadQueue) {
            // XXX - we could also just mark the tile as aborted, however, that
            //       would potentially lead to a situation where we're taking
            //       up a lot of slots in the circular buffer with tiles that
            //       will aren't downloadable
            final int idx = findQueuedRequestIndexNoSync(zoom, x, y);
            if(idx >= 0)
                downloadQueue.remove(idx);
        }
    }

    int findQueuedRequestIndexNoSync(int zoom, int x, int y) {
        for(int i = downloadQueue.size()-1; i >= 0; i--) {
            final TileFetchTask request = downloadQueue.get(i);
            if(request.x == x && request.y == y && request.z == zoom) {
                return i;
            }
        }
        return -1;
    }

    private void downloadTile(int zoom, int x, int y) {
        synchronized(downloadQueue) {
            final int idx = findQueuedRequestIndexNoSync(zoom, x, y);
            if(idx < 0) {
                downloadQueue.add(new TileFetchTask(++priority, client, cache, x, y, zoom, expiry, listener));
                clientExecutor.execute(new Downloader());
            }
        }
    }

    @Override
    public void clearAuthFailed() {
        client.value.clearAuthFailed();
    }

    @Override
    public void checkConnectivity() {
        client.value.checkConnectivity();
    }

    @Override
    public void cache(CacheRequest request, CacheRequestListener listener) {
        client.value.cache(request, listener);
    }

    @Override
    public int estimateTileCount(CacheRequest request) {
        return client.value.estimateTileCount(request);
    }

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        if(TileCacheControl.class.isAssignableFrom(controlClazz))
            return (T)prioritizer;
        return client.value.getControl(controlClazz);
    }

    @Override
    public void getControls(Collection<Object> controls) {
        controls.add(prioritizer);
        client.value.getControls(controls);
    }

    @Override
    public String getName() {
        return client.value.getName();
    }

    @Override
    public int getSRID() {
        return client.value.getSRID();
    }

    @Override
    public ZoomLevel[] getZoomLevel() {
        return client.value.getZoomLevel();
    }

    @Override
    public double getOriginX() {
        return client.value.getOriginX();
    }

    @Override
    public double getOriginY() {
        return client.value.getOriginY();
    }

    @Override
    public Bitmap getTile(int zoom, int x, int y, Throwable[] error) {
        final Bitmap retval = cache.value.getTile(zoom, x, y, error);
        downloadTile(zoom, x, y);
        return retval;
    }

    @Override
    public byte[] getTileData(int zoom, int x, int y, Throwable[] error) {
        final byte[] retval = cache.value.getTileData(zoom, x, y, error);
        downloadTile(zoom, x, y);
        return retval;
    }

    @Override
    public Envelope getBounds() {
        return client.value.getBounds();
    }

    @Override
    public void dispose() {
        synchronized(listener) {
            listener[0] = null;
        }

        clientExecutor.shutdownNow();

        synchronized(downloadQueue) {
            for(int i = 0; i < downloadQueue.size(); i++) {
                downloadQueue.get(i).cache.dereference();
                downloadQueue.get(i).client.dereference();
            }
            downloadQueue.clear();
        }

        client.dereference();
        cache.dereference();
    }

    final static class SharedTileMatrix<T extends TileMatrix> extends ReferenceCount<T> {
        public SharedTileMatrix(T value) {
            super(value, true);
        }

        @Override
        protected void onDereferenced() {
            super.onDereferenced();
            value.dispose();
        }
    }

    final class Downloader implements Runnable {
        @Override
        public void run() {
            TileFetchTask task;
            synchronized (downloadQueue) {
                if(downloadQueue.isEmpty())
                    return;
                task = downloadQueue.remove(downloadQueue.size()-1);
            }
            task.run();
        }
    }

    final static class TileFetchTask implements Runnable {
        final ReferenceCount<TileClient> client;
        final ReferenceCount<TileContainer> cache;
        final int x;
        final int y;
        final int z;
        final long expiry;
        final TileCacheControl.OnTileUpdateListener[] callback;
        final Envelope bounds;
        final double centroidX;
        final double centroidY;
        final double centroidZ;
        final double radius;

        int priority;

        TileFetchTask(int priority, ReferenceCount<TileClient> client, ReferenceCount<TileContainer> cache, int x, int y, int z, long expiry, TileCacheControl.OnTileUpdateListener[] l) {
            this.priority = priority;
            this.client = client;
            this.cache = cache;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expiry = expiry;
            this.callback = l;

            this.client.reference();
            this.cache.reference();

            bounds = TileMatrix.Util.getTileBounds(client.value, z, x, y);
            centroidX = (bounds.minX+bounds.maxX)/2d;
            centroidY = (bounds.minY+bounds.maxY)/2d;
            centroidZ = (bounds.minZ+bounds.maxZ)/2d;
            radius = MathUtils.distance(centroidX, centroidY, centroidZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        }

        @Override
        public void run() {
            try {
                final long expiration = cache.value.getTileExpiration(z, x, y);
                // if the tile is considered expired, download
                if(expiration < expiry) {
                    byte[] data = client.value.getTileData(z, x, y, null);
                    if (data != null) {
                        cache.value.setTile(z, x, y, data, System.currentTimeMillis());
                        // signal update
                        synchronized (callback) {
                            if (callback[0] != null)
                                callback[0].onTileUpdated(z, x, y);
                        }
                    }
                }
            } catch(Throwable t) {
            } finally {
                client.dereference();
                cache.dereference();
            }
        }
    }

    final class PrioritizerImpl implements TileCacheControl, Comparator<TileFetchTask> {

        GeoPoint p;
        PointD xyz0 = new PointD(0d, 0d, 0d);
        PointD xyz1 = new PointD(0d, 0d, 0d);
        PointD xyz2 = new PointD(0d, 0d, 0d);

        @Override
        public int compare(TileFetchTask a, TileFetchTask b) {
            final double da = MathUtils.distance(a.centroidX, a.centroidY, a.centroidZ, xyz0.x, xyz0.y, xyz0.z);
            final double db = MathUtils.distance(b.centroidX, b.centroidY, b.centroidZ, xyz0.x, xyz0.y, xyz0.z);
            if(da <= a.radius && db <= b.radius)
                return b.z-a.z;
            else if(da <= a.radius)
                return 1;
            else if(db < b.radius)
                return -1;
            // both radii are greater
            else if((da-a.radius) < (db-b.radius))
                return 1;
            else if((da-a.radius) > (db-b.radius))
                return -1;
            else if(a.z < b.z)
                return 1;
            else if(a.z > b.z)
                return -1;
            else
                return a.priority-b.priority;
        }

        @Override
        public void prioritize(GeoPoint p) {
            this.p = (p != null) ? new GeoPoint(p) : null;
            if(this.p != null) {
                proj.forward(p, xyz0);
                proj.forward(p, xyz1);
                proj.forward(p, xyz2);
            }

            if(this.p != null)
            synchronized(downloadQueue) {
                Collections.sort(downloadQueue, this);
            }
        }

        @Override
        public void abort(int level, int x, int y) {
            abortTile(level, x, y);
        }

        @Override
        public boolean isQueued(int level, int x, int y) {
            synchronized(downloadQueue) {
                return findQueuedRequestIndexNoSync(level, x, y) >= 0;
            }
        }

        @Override
        public void setOnTileUpdateListener(OnTileUpdateListener l) {
            synchronized(listener) {
                listener[0] = l;
            }
        }

        @Override
        public void expireTiles(long expiry) {
            TileProxy.this.expiry = expiry;
        }
    }
}
