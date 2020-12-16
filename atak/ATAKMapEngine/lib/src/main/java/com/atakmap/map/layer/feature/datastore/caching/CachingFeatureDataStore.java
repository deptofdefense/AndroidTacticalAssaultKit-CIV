package com.atakmap.map.layer.feature.datastore.caching;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.graphics.Point;
import android.util.LruCache;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.Utils;
import com.atakmap.map.layer.feature.cursor.FeatureCursorWrapper;
import com.atakmap.map.layer.feature.cursor.MultiplexingFeatureCursor;
import com.atakmap.map.layer.feature.datastore.AbstractReadOnlyFeatureDataStore2;
import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.math.Rectangle;
import com.atakmap.util.ReferenceCount;

public class CachingFeatureDataStore extends AbstractReadOnlyFeatureDataStore2 implements Controls {

    private final static String TAG = "CachingFeatureDataStore";

    private final static List<TileMatrix.ZoomLevel> DEFAULT_MATRIX = Arrays.asList(new TileMatrix.ZoomLevel[]
    {
        createZoomLevel(0, 180),
        createZoomLevel(1, 90),
        createZoomLevel(2, 30),
        createZoomLevel(3, 10),
        createZoomLevel(4, 5),
        createZoomLevel(5, 1),
        createZoomLevel(6, 0.2),
        createZoomLevel(7, 0.1),
        createZoomLevel(8, 0.05),
        createZoomLevel(9, 0.01),
    });

    /**
     * The feature client.
     */
    private FeatureDataStore2 client;
    private final int clientVersion;
    /**
     * The node tiling matrix.
     */
    private ArrayList<TileMatrix.ZoomLevel> matrix;
    private Map<Integer, Map<Integer, CacheNode>> cacheNodes;
    /**
     * The maximum number of features allowed in a node.
     */
    private final int nodeFeatureLimit;
    private long refreshInterval;

    private QueryRequest refreshRequest;
    
    
    private LruCache<Long, Boolean> recentNodes;
    
    /**
     * The list of FID buffers, used to track the <code>CacheNode</code> that
     * contains the latest version of a feature.
     */
    private List<FIDBuffer> fidBuffers;
    
    private File cacheDir;
    
    public CachingFeatureDataStore(FeatureDataStore2 client, int clientVersion, int nodeFeatureLimit, File cacheDir) {
        this(client, clientVersion, nodeFeatureLimit, cacheDir, DEFAULT_MATRIX);
    }

    public CachingFeatureDataStore(FeatureDataStore2 client, int clientVersion, int nodeFeatureLimit, File cacheDir, List<TileMatrix.ZoomLevel> cacheMatrix) {
        super(0, 0);
        
        this.client = client;
        this.clientVersion = clientVersion;
        this.nodeFeatureLimit = nodeFeatureLimit;
        this.cacheDir = cacheDir;
        
        this.cacheNodes = new HashMap<Integer, Map<Integer, CacheNode>>();

        this.refreshRequest = null;
        
        this.recentNodes = new LruCache<Long, Boolean>(8) {
            @Override
            protected void entryRemoved(boolean evicted,
                                        Long key,
                                        Boolean oldValue,
                                        Boolean newValue) {
                
                if(evicted) {
                    final int level = (int)((key.longValue()>>32L)&0xFFFFFFFFL);
                    final int index = (int)(key.longValue()&0xFFFFFFFFL);
                    
                    Log.d(TAG, "Evicting [" + level + "." + index + "]");
                    CacheNode node = null;
                    synchronized(CachingFeatureDataStore.this) {
                        Map<Integer, CacheNode> levelNodes = cacheNodes.get(level);
                        if(levelNodes != null)
                            node = levelNodes.remove(index);
                    }
                    if(node != null && node.cache != null)
                        node.cache.dereference();

                }

                super.entryRemoved(evicted, key, oldValue, newValue);
            }
        };
        
        // XXX - initialize matrix
        this.matrix = new ArrayList<TileMatrix.ZoomLevel>(cacheMatrix);

        this.fidBuffers = new ArrayList<FIDBuffer>();

        Thread t = new Thread(new RefreshWorker());
        t.setPriority(Thread.NORM_PRIORITY);
        t.setName("caching-fds-worker");
        t.start();
    }
    
    private static TileMatrix.ZoomLevel createZoomLevel(int level, double deg) {
        TileMatrix.ZoomLevel retval = new TileMatrix.ZoomLevel();
        retval.level = level;
        retval.pixelSizeX = deg;
        retval.pixelSizeY = deg;
        retval.resolution = retval.pixelSizeY*111111d;
        retval.tileWidth = 1;
        retval.tileHeight = 1;
        return retval;
    }
    
    public synchronized void setRefreshInterval(long interval) {
        final boolean doDispatch = (interval > 0L &&
                                    this.refreshInterval > 0L &&
                                    interval < this.refreshInterval);
        this.refreshInterval = interval;
        if(doDispatch)
            this.dispatchContentChanged();
    }

    public synchronized long getRefreshInterval() {
        return this.refreshInterval;
    }

    @Override
    public synchronized void dispose() {
        this.client.dispose();
        
        for(Map<Integer, CacheNode> cacheLevel : cacheNodes.values()) {
            for(CacheNode node : cacheLevel.values())
                node.cache.dereference();
        }
        
        cacheNodes.clear();
    }

    private void touchNode(CacheNode node) {
        final long key = ((long)node.level<<32L)|((long)node.index&0xFFFFFFFFL);
        this.recentNodes.put(Long.valueOf(key), Boolean.TRUE);
    }

    /**
     * <P>NOTE: Always called holding the lock on <code>this</code>
     * @param maxLevelIdx
     * @param ul
     * @param lr
     * @param result
     */
    private void recurseForNodes(int maxLevelIdx, GeoPoint ul, GeoPoint lr, Collection<CacheNode> result) {
        Collection<CacheNode> nodes = new LinkedList<CacheNode>();
        Collection<CacheNode> candidates = new LinkedList<CacheNode>();
        if(!recurseForNodes(0,
                            maxLevelIdx,
                            ul.getLatitude(), ul.getLongitude(),
                            lr.getLatitude(), lr.getLongitude(),
                            nodes,
                            candidates)) {

            refreshRequest = new QueryRequest();
            refreshRequest.ul = new GeoPoint(ul);
            refreshRequest.lr = new GeoPoint(lr);
            refreshRequest.maxLevelIdx = maxLevelIdx;
            this.notify();
            
            nodes.addAll(candidates);
        }
        
        for(CacheNode node : nodes) {
            if(node.cache != null)
                result.add(new CacheNode(node));
        }
    }

    private static boolean needsRefresh(CacheNode node, long refreshInterval) {
        long timestamp;
        switch(node.state) {
            case Resolved :
                if(refreshInterval < 1L)
                    return false;

                // normalize the timestamp
                timestamp = (node.timestamp/refreshInterval)*refreshInterval;
                break;
            case Error :
                // if there was an error downloading the node, retry after 30s
                // or the twice the refresh interval
                timestamp = node.lastError;
                if(refreshInterval < 1L)
                    refreshInterval = 30000L;
                else
                    refreshInterval *= 2L;
                break;
            case None :
            case Queued :
                return false;
            case Dirty :
                return true;
            default :
                throw new IllegalStateException();
        }
        
        return ((timestamp+refreshInterval) < System.currentTimeMillis());
    }

    /**
     * <P>NOTE: Always called holding the lock on <code>this</code>
     * @param levelIdx
     * @param maxLevelIdx
     * @param ulLat
     * @param ulLng
     * @param lrLat
     * @param lrLng
     * @param nodes
     * @param candidates
     * @return
     */
    private boolean recurseForNodes(int levelIdx, int maxLevelIdx, double ulLat, double ulLng, double lrLat, double lrLng, Collection<CacheNode> nodes, Collection<CacheNode> candidates) {
        Point minTile = TileMatrix.Util.getTileIndex(-180d, 90d, matrix.get(levelIdx), ulLng, ulLat);
        Point maxTile = TileMatrix.Util.getTileIndex(-180d, 90d, matrix.get(levelIdx), lrLng, lrLat);
        
        maxTile.x = Math.min(maxTile.x, (int)Math.round(360d/matrix.get(levelIdx).pixelSizeX) - 1);
        maxTile.y = Math.min(maxTile.y, (int)Math.round(180d/matrix.get(levelIdx).pixelSizeY) - 1);
        
        Map<Integer, CacheNode> levelNodes = cacheNodes.get(levelIdx);
        if(levelNodes == null) {
            return (levelIdx < maxLevelIdx) ?
                    recurseForNodes(levelIdx+1,
                                    maxLevelIdx,
                                    ulLat,
                                    ulLng,
                                    lrLat,
                                    lrLng,
                                    nodes,
                                    candidates) :
                    false;
        }

        int queued = 0;
        int misses = 0;
        int nonTerminal = 0;

        boolean requestRefresh = false;
        final int numTilesX = (int)Math.round(360d/matrix.get(levelIdx).pixelSizeX);
        for(int y = minTile.y; y <= maxTile.y; y++) {
            for(int x = minTile.x; x <= maxTile.x; x++) {
                int index = (y*numTilesX)+x;
                CacheNode node = levelNodes.get(index);
                if(node == null) {
                    if(this.cacheDir == null)
                        return false;

                    // we've got a cache miss, create an empty node for the
                    // index and queue it for refresh
                    CacheNode miss = new CacheNode();
                    miss.index = index;
                    miss.level = levelIdx;
                    miss.featureCount = 0;
                    miss.cache = null;
                    miss.upperLeft = new GeoPoint(90d-(y*matrix.get(levelIdx).pixelSizeY), -180d+(x*matrix.get(levelIdx).pixelSizeX));
                    miss.lowerRight = new GeoPoint(90d-((y+1)*matrix.get(levelIdx).pixelSizeY), -180d+((x+1)*matrix.get(levelIdx).pixelSizeX));
                    miss.terminal = false;
                    miss.state = CacheNode.State.None;
                    miss.resolution = matrix.get(levelIdx).resolution;

                    boolean loaded = false;
                    long s = System.currentTimeMillis();
                    FIDBuffer fidBuffer = null;
                    try {
                        fidBuffer = new FIDBuffer();
                        loaded = loadCacheNode(this.clientVersion, this.cacheDir, miss, this.nodeFeatureLimit, fidBuffer, (levelIdx != maxLevelIdx));
                        long e = System.currentTimeMillis();
                        Log.d(TAG, "Loaded [" + miss.level + "." + miss.index + "] " + miss.featureCount + " features in " + (e-s) + "ms, " + loaded + (miss.terminal ? " [terminal]" : ""));
                        
                        if(loaded) {
                            // if the file cache for the node is not
                            // terminal and the number of records it
                            // contains is less than the current feature
                            // limit (indicating limit was lower when cache
                            // was created), we should refresh the node to
                            // hold up to the new number of limit features.
                            if(!miss.terminal && fidBuffer.numRecords < this.nodeFeatureLimit)
                                loaded = false;
                            
                            if(updateFIDBuffer(fidBuffer))
                                fidBuffer = null;

                            if(miss.terminal || levelIdx == maxLevelIdx) {
                                nodes.add(miss);
                            } else {
                                candidates.add(miss);
                            }
                        }
                    } finally {
                        if(fidBuffer != null)
                            fidBuffer.finalize();
                    }

                    if(loaded) {
                        levelNodes.put(miss.index, miss);
                        requestRefresh |= needsRefresh(miss, this.refreshInterval);
                    } else {                    
                        misses++;
                    }
                } else if(node.terminal) { // recursion stops
                    // the node is out of date, queue it for a refresh
                    if(needsRefresh(node, refreshInterval))
                        requestRefresh = true;
                    
                    // add the node
                    nodes.add(node);

                    // if the bounds of the node contains the AOI, we are
                    // done
                    if(Rectangle.contains(node.upperLeft.getLongitude(),
                                          node.lowerRight.getLatitude(),
                                          node.lowerRight.getLongitude(),
                                          node.upperLeft.getLongitude(),
                                          ulLng,
                                          lrLat,
                                          lrLng,
                                          ulLat)) {
                        
                        return true;
                    }
                } else if(levelIdx == maxLevelIdx) { // at level, stop recursion
                    switch(node.state) {
                        case None :
                        case Queued :
                            if(node.cache == null)
                                misses++;
                            break;
                        case Dirty :
                            requestRefresh = true;
                            break;
                        case Error :
                            // if there's an error and the node has no cache,
                            // then it does not get added to the results 
                            if(node.cache == null) {
                                break;
                            }
                        case Resolved :
                            // if we are at the max level, but the node is not
                            // terminal, expand the matrix via quadtree
                            if(!node.terminal && levelIdx == (matrix.size()-1)) {
                                matrix.add(createZoomLevel(matrix.size(), matrix.get(levelIdx).pixelSizeX/2d));
                            }

                            // the node is out of date, queue it for a refresh
                            if(needsRefresh(node, refreshInterval))
                                requestRefresh = true;

                            // add the node
                            nodes.add(node);
                            break;
                        default :
                            throw new IllegalStateException();
                    }
                } else {
                    // the node is a candidate, but we will check child nodes as
                    // well
                    if(node.cache != null)
                        candidates.add(node);

                    if(node.state == CacheNode.State.Queued)
                        queued++;
                    else // state is error or resolved
                        nonTerminal++;
                }
            }
        }
        
        if((nonTerminal > 0) && (levelIdx < maxLevelIdx)) {
            if(recurseForNodes(levelIdx+1, maxLevelIdx, ulLat, ulLng, lrLat, lrLng, nodes, candidates))
                return true;
        }

        return ((misses+queued+nonTerminal) == 0) && !requestRefresh;
    }

    @Override
    public FeatureCursor queryFeatures(FeatureQueryParameters params) throws DataStoreException {
        // if a time based query is issued, query client directly
        if(params != null && (params.minimumTimestamp != TIMESTAMP_NONE || params.maximumTimestamp != TIMESTAMP_NONE)) {
            return this.client.queryFeatures(params);
        }

        Collection<CacheNode> queryNodes = new LinkedList<CacheNode>();
        try {
            if(params != null && params.spatialFilter != null) {
                Envelope filterBounds = params.spatialFilter.getEnvelope();
                final GeoPoint ul = new GeoPoint(filterBounds.maxY, filterBounds.minX);
                final GeoPoint lr = new GeoPoint(filterBounds.minY, filterBounds.maxX);
                
                final double dlat = ul.getLatitude()-lr.getLatitude();
                final double dlng = lr.getLongitude()-ul.getLongitude();
                
                final double dmin = Math.min(dlat, dlng);
                final double dmax = Math.max(dlat, dlng);
                final double aspect = dmax/dmin;
                
                final int targetTilesMin = 2;
                final int targetTilesMax = targetTilesMin*(int)aspect;

                // based on AOI select appropriate level
                int targetZoomLevelIdx = 0;
                for(int i = matrix.size()-1; i >= 0; i--) {
                    if((dmax/matrix.get(i).pixelSizeX) <= targetTilesMax &&
                       (dmin/matrix.get(i).pixelSizeX) <= targetTilesMin) {

                        targetZoomLevelIdx = i;
                        break;
                    }
                }
                
                synchronized(this) {
                    // recurse for nodes that intersect the AOI, stopping at
                    // the target zoom level
                    Collection<CacheNode> transfer = new LinkedList<CacheNode>();
                    recurseForNodes(targetZoomLevelIdx, ul, lr, transfer);
                    for(CacheNode ds : transfer) {
                        this.touchNode(ds);

                        queryNodes.add(ds);
                        ds.cache.reference();
                    }
                }
            } else if(params != null && params.ids != null) {
                synchronized(this) {
                    for(Long fid : params.ids) {
                        for(FIDBuffer buf : fidBuffers) {
                            int rec = buf.findRecord(fid);
                            if(rec < 0)
                                continue;
                            Map<Integer, CacheNode> level = cacheNodes.get(buf.getLevel(rec));
                            if(level == null)
                                break;
                            CacheNode node = level.get(buf.getIndex(rec));
                            if(node == null || node.cache == null)
                                break;
                            
                            this.touchNode(node);

                            queryNodes.add(node);
                            node.cache.reference();
                        }
                    }
                }
            } else {
                // XXX - brute force through generic query, should implement
                //       tracking of indices for most recent FID
                synchronized(this) {
                    for(Map<Integer, CacheNode> levelNodes : this.cacheNodes.values()) {
                        for(CacheNode node : levelNodes.values()) {
                            queryNodes.add(node);
                            node.cache.reference();
                            
                            // note that we do not 'touch' here. since this is a
                            // generic query and examines all nodes, there
                            // really is no change in our LRU information
                        }
                    }
                }
            }

            boolean issueCacheQuery = !queryNodes.isEmpty();
            
            // XXX - 
            //for(CacheNode ds : queryNodes)
            //    issueCacheQuery &= ds.terminal;

            Collection<FeatureCursor> cursors = new LinkedList<FeatureCursor>();
            FeatureQueryParameters nodeParams = null;
            do {
                if(issueCacheQuery) {
                    nodeParams = new FeatureQueryParameters(params);
                    nodeParams.limit = nodeParams.offset+nodeParams.limit;
                    for(CacheNode ds : queryNodes)
                        cursors.add(new CacheCursor(ds, nodeParams));
                } else {
                    try {
                        cursors.add(this.client.queryFeatures(params));
                    } catch(DataStoreException e) {
                        issueCacheQuery = true;
                        continue;
                    }
                }
                break;
            } while(true);
    
            if (params != null) {
                return new FeatureCursorImpl(new MultiplexingFeatureCursor(cursors, null), params.limit, params.offset);
            } else {  
                // something really bad has occured to get to this point and 
                // it seems like in this specific instance it should return an 
                // empty FeatureCursor instead of throwing a NullPointerException
                return FeatureCursor.EMPTY;
                
            }
        } finally {
            for(CacheNode ds : queryNodes)
                if(ds.cache != null)
                    ds.cache.dereference();
        }
    }

    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) throws DataStoreException {
        // XXX - better implementation
        return Utils.queryFeaturesCount(this, params);
    }

    @Override
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
        return this.client.queryFeatureSets(params);
    }

    @Override
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) throws DataStoreException {
        return this.client.queryFeatureSetsCount(params);
    }

    @Override
    protected boolean setFeatureVisibleImpl(long fid, boolean visible) {
        // XXX - visibility not yet supported
        return false;
    }

    @Override
    protected boolean setFeatureSetVisibleImpl(long setId, boolean visible) {
        // XXX - visbility not yet supported
        return false;
    }

    @Override
    public boolean hasTimeReference() {
        return this.client.hasTimeReference();
    }
    
    @Override
    public long getMinimumTimestamp() {
        return this.client.getMinimumTimestamp();
    }
    
    @Override
    public long getMaximumTimestamp() {
        return this.client.getMaximumTimestamp();
    }
    
    @Override
    public boolean hasCache() {
        return (this.cacheDir != null);
    }
    
    @Override
    public long getCacheSize() {
        if(this.cacheDir != null)
            return FileSystemUtils.getFileSize(this.cacheDir);
        else
            return 0L;
    }
    
    @Override
    public void clearCache() {
        if(this.cacheDir != null)
            FileSystemUtils.deleteDirectory(this.cacheDir, true);
    }
    
    @Override
    public String getUri() {
        return this.client.getUri();
    }

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        if(this.client instanceof Controls)
            return ((Controls)this.client).getControl(controlClazz);
        else
            return null;
    }
    
    @Override
    public void getControls(Collection<Object> controls) {
        if(this.client instanceof Controls)
            ((Controls)this.client).getControls(controls);
    }
    
    /**************************************************************************/
    
    /**
     * 
     * @param cacheDir  The cache directory
     * @param node      The node
     * @param limit     The current feature limit
     * @param fidBuffer A {@link FIDBuffer} that will store the feature IDs for
     *                  the cache node.
     * 
     * @return  <code>true</code> if the cache file for the node exists and
     *          cached data was successfully loaded; <code>false</code>
     *          otherwise
     */
    private static boolean loadCacheNode(int clientVersion, File cacheDir, CacheNode node, int limit, FIDBuffer fidBuffer, boolean terminalOnly) {
        File cacheFile = new File(new File(cacheDir, String.valueOf(node.level)), String.valueOf(node.index));
        if(!IOProviderFactory.exists(cacheFile))
            return false;
        try {
            FeatureDataStore2 swap;
            
            CacheFile cached = null;
            final long timestamp;
            final int featureCount;
            final int featureSetCount;
            try {
                cached = CacheFile.readCacheFile(cacheFile.getAbsolutePath());
                if(cached == null)
                    return false;
                if(terminalOnly && cached.contentExceedsLimit())
                    return false;
                
                timestamp = cached.getTimestamp();
                featureCount = cached.getNumFeatures();
                featureSetCount = cached.getNumFeatureSets();
    
                //swap = new RuntimeFeatureDataStore2();
                swap = new FeatureSetDatabase2(null);
                swap.acquireModifyLock(true);
                FeatureCursor cacheResult = null;
                try {
                    for(int i = 0; i < featureSetCount; i++) {
                        FeatureSet fs = cached.getFeatureSet(i);
                        swap.insertFeatureSet(fs);
                    }
                    
                    cacheResult = cached.getFeatures();
                    swap.insertFeatures(cacheResult);

                    cacheResult.close();
                    cacheResult = null;
                    
                    FeatureQueryParameters params = new FeatureQueryParameters();
                    params.ignoredFeatureProperties = PROPERTY_FEATURE_ATTRIBUTES|
                                                      PROPERTY_FEATURE_GEOMETRY|
                                                      PROPERTY_FEATURE_NAME|
                                                      PROPERTY_FEATURE_STYLE;
                    cacheResult = swap.queryFeatures(params);
                    while(cacheResult.moveToNext()) {
                        fidBuffer.insert(cacheResult.getId(), timestamp, node.level, node.index);
                    }
                } finally {
                    swap.releaseModifyLock();
                    
                    if(cacheResult != null)
                        cacheResult.close();
                }
            } finally {
                if(cached != null)
                    cached.dispose();
            }

            if(node.cache != null)
                node.cache.dereference();
            node.cache = new FeatureDataStoreRef(swap);
            node.featureCount = featureCount;
            node.terminal = !cached.contentExceedsLimit();
            node.timestamp = timestamp;
            if(cached.getClientVersion() != clientVersion)
                node.state = CacheNode.State.Dirty;
            else
                node.state = CacheNode.State.Resolved;
            return true;
        } catch(IOException e) {
            Log.v(TAG, "IO error loading cache file " + cacheFile, e);
            return false;
        } catch(Throwable t) {
            Log.v(TAG, "General error loading cache file " + cacheFile, t);
            return false;
        }
    }
    
    private static boolean refreshCacheNode(FeatureDataStore2 client, int clientVersion, CacheNode node, int limit, FIDBuffer fidBuffer, File cacheDir) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.spatialFilter = GeometryFactory.fromEnvelope(new Envelope(node.upperLeft.getLongitude(), node.lowerRight.getLatitude(), 0d, node.lowerRight.getLongitude(), node.upperLeft.getLatitude(), 0d));
        params.limit = limit+1;

        FeatureCursor result = null;
        try {
            final long timestamp = System.currentTimeMillis();
            
            try {
                result = client.queryFeatures(params);
            } catch(DataStoreException t) {
                Log.e(TAG, "error", t);
            }
            // XXX - 
            if(result == null) {
                node.state = CacheNode.State.Error;
                node.lastError = timestamp;
                return false;
            }
            
            FeatureDataStore2 swap = new FeatureSetDatabase2(null);
            int count = 0;
            boolean hasDataAtRes;
            try {
                swap.acquireModifyLock(true);
                try {
                    {
                        FeatureSetCursor fs = null;
                        try {
                            fs = client.queryFeatureSets(params.featureSetFilter);
                            swap.insertFeatureSets(fs);
                        } finally {
                            if(fs != null)
                                fs.close();
                        }
                        hasDataAtRes = (swap.queryFeatureSetsCount(null)>0);
                    }
    
                    if(hasDataAtRes) {
                        final FeatureDefinition2 defn2 = Adapters.adapt(result);
                        while(result.moveToNext()) {
                            swap.insertFeature(result.getFsid(),
                                               result.getId(),
                                               defn2,
                                               result.getVersion());
                            fidBuffer.insert(result.getId(), timestamp, node.level, node.index);
                            count++;
                        }
                    } else if(result.moveToNext()){
                        Log.w(TAG, "Illegal State: No Feature Sets, but Features present!!!");
                    }
                } finally {
                    swap.releaseModifyLock();
                }
            } catch(InterruptedException e) {
                // as 'swap' was created in the local scope, nothing else could
                // be holding a modify lock on it
                throw new IllegalStateException(e);
            } catch(DataStoreException e) {
                Log.d(TAG, "Failed to create cache file [" + node.level + "." + node.index + "]", e);
                node.state = CacheNode.State.Error;
                node.lastError = timestamp;
                return false;
            } catch(Throwable e) {
                Log.d(TAG, "Failed to create cache file [" + node.level + "." + node.index + "]", e);
                node.state = CacheNode.State.Error;
                node.lastError = timestamp;
                return false;
            }
            
            if(node.cache != null)
                node.cache.dereference();
            node.cache = new FeatureDataStoreRef(swap);
            node.featureCount = count;
            node.terminal = (count <= limit && hasDataAtRes);
            node.timestamp = timestamp;
            node.state = CacheNode.State.Resolved;

            if(cacheDir != null) {
                File cacheFile = new File(new File(cacheDir, String.valueOf(node.level)), String.valueOf(node.index));
                if (!IOProviderFactory.exists(cacheFile.getParentFile()) && !cacheFile.getParentFile().mkdirs()) {
                     Log.d(TAG, "error creating: " + cacheFile);
                }

                File cacheSwap = null;
                try {
                    long s = System.currentTimeMillis();
                    File cacheSwapDir = new File(cacheDir, ".swap");
                    if (!IOProviderFactory.exists(cacheSwapDir) && !IOProviderFactory.mkdirs(cacheSwapDir)) {
                       Log.d(TAG, "error creating: " + cacheSwapDir);
                    }
                    
                    cacheSwap = IOProviderFactory.createTempFile("cache", ".swap", cacheSwapDir);
                    FeatureQueryParameters cacheParams = new FeatureQueryParameters();
                    cacheParams.limit = limit;
                    CacheFile.createCacheFile(clientVersion, node.level, node.index, node.timestamp, swap, cacheParams, cacheSwap.getAbsolutePath());
                    if(IOProviderFactory.exists(cacheFile))
                        FileSystemUtils.delete(cacheFile);
                    if(!IOProviderFactory.renameTo(cacheSwap, cacheFile))
                        throw new IOException("Unable to rename cache swap file");
                    else
                        cacheSwap = null;
                    long e = System.currentTimeMillis();
                    Log.d(TAG, "Created cache node [" + node.level + "." + node.index + "] " + count + " features in " + (e-s) + "ms");
                } catch(Throwable t) {
                    Log.d(TAG, "Failed to create cache node [" + node.level + "." + node.index + "]", t);
                } finally {
                    if(cacheSwap != null)
                        FileSystemUtils.delete(cacheSwap);
                }
            }
            return true;
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**************************************************************************/

    private static class FeatureDataStoreRef extends ReferenceCount<FeatureDataStore2> {

        public FeatureDataStoreRef(FeatureDataStore2 value) {
            super(value);
        }

        @Override
        protected void onDereferenced() {
            super.onDereferenced();
            
            this.value.dispose();
        }
        
    }
    
    private static class CacheNode {
        enum State {
            None,
            Queued,
            Resolved,
            Error,
            Dirty,
        }

        public int level;
        public int index;
        public boolean terminal;
        public long timestamp;
        public FeatureDataStoreRef cache;
        public GeoPoint upperLeft;
        public GeoPoint lowerRight;
        public double resolution;
        public int featureCount;
        public State state;
        public long lastError;
        
        public CacheNode() {
            level = 0;
            index = 0;
            terminal = false;
            timestamp = 0L;
            cache = null;
            upperLeft = null;
            lowerRight = null;
            resolution = Double.NaN;
            featureCount = 0;
            state = State.None;
            lastError = 0L;
        }
        
        public CacheNode(CacheNode other) {
            level = other.level;
            index = other.index;
            terminal = other.terminal;
            timestamp = other.timestamp;
            cache = other.cache;
            upperLeft = other.upperLeft;
            lowerRight = other.lowerRight;
            resolution = other.resolution;
            featureCount = other.featureCount;
            state = other.state;
            lastError = other.lastError;
        }
        
        @Override
        public boolean equals(Object o) {
            if(o == null)
                return false;
            if(!(o instanceof CacheNode))
                return false;
            CacheNode other = (CacheNode)o;
            return other.level == this.level &&
                   other.index == this.index;
        }
        
        @Override
        public int hashCode() {
            return Long.valueOf(((long)level<<32L)|((long)index&0xFFFFFFFFL)).hashCode();
        }
    }

    
    private static class CacheCursor extends FeatureCursorWrapper {

        private final CacheNode node;
        
        public CacheCursor(CacheNode node, FeatureQueryParameters params) throws DataStoreException {
            super(queryAndReference(node, params));
            
            this.node = node;
        }

        @Override
        public long getVersion() {
            return node.timestamp;
        }
        
        @Override
        public void close() {
            super.close();
            
            this.node.cache.dereference();
        }
        
        private static FeatureCursor queryAndReference(CacheNode node, FeatureQueryParameters params) throws DataStoreException {
            final FeatureCursor retval = node.cache.value.queryFeatures(params);
            node.cache.reference();
            return retval;
        }
    }
    
    private final static class FeatureCursorImpl implements FeatureCursor, FeatureDefinition2 {

        private final FeatureCursor impl;
        private FeatureDataSource.FeatureDefinition row;
        private long rowId;
        private long rowVersion;
        private long rowFsid;
        private long rowTimestamp;
        private boolean hasNext;
        private final int limit;
        private final int offset;
        private int count;
        private final FeatureDefinition2 defn2;

        public FeatureCursorImpl(FeatureCursor filter, int limit, int offset) {
            this.impl = filter;
            
            this.limit = limit;
            this.offset = offset;
            this.count = 0;

            this.defn2 = Adapters.adapt(filter);

            this.row = new FeatureDataSource.FeatureDefinition();
            this.hasNext = this.impl.moveToNext();
        }

        @Override
        public boolean moveToNext() {
            if(this.limit > 0) {
                if((this.count-this.offset) == this.limit)
                    return false;
                while(this.count < this.offset) {
                    if(!this.moveToNextImpl())
                        return false;
                    this.count++;
                }
            }
            final boolean retval = this.moveToNextImpl();
            this.count++;
            return retval;
        }

        private void updateRowData() {
            this.row.attributes = this.impl.getAttributes();
            this.row.geomCoding = this.impl.getGeomCoding();
            this.row.name = this.impl.getName();
            this.row.rawGeom = this.impl.getRawGeometry();
            this.row.rawStyle = this.impl.getRawStyle();
            this.row.styleCoding = this.impl.getStyleCoding();
            if(this.defn2 != null)
                this.rowTimestamp = this.defn2.getTimestamp();
            else
                this.rowTimestamp = TIMESTAMP_NONE;
        }
        
        private boolean moveToNextImpl() {
            if(!this.hasNext)
                return false;

            // for initial row, pull FID, geometry, style ID, attributes,
            // attachment ID
            
            this.rowId = this.impl.getId();
            this.rowVersion = this.impl.getVersion();
            this.rowFsid = this.impl.getFsid();
            
            this.updateRowData();
            
            do {
                this.hasNext = this.impl.moveToNext();
                if(!this.hasNext)
                    break;
                
                if(this.impl.getId() != this.rowId)
                    break;
                
                if(this.impl.getVersion() > this.rowVersion)
                    this.updateRowData();
            } while(true);

            return true;
        }

        @Override
        public void close() {
            this.impl.close();
        }

        @Override
        public boolean isClosed() {
            return this.impl.isClosed();
        }

        @Override
        public Object getRawGeometry() {
            return this.row.rawGeom;
        }

        @Override
        public int getGeomCoding() {
            return this.row.geomCoding;
        }

        @Override
        public String getName() {
            return this.row.name;
        }

        @Override
        public int getStyleCoding() {
            return this.row.styleCoding;
        }

        @Override
        public Object getRawStyle() {
            return this.row.rawStyle;
        }

        @Override
        public AttributeSet getAttributes() {
            return this.row.attributes;
        }

        @Override
        public Feature get() {
            Feature f = this.row.get();
            Feature retval = new Feature(this.getFsid(),
                                         this.getId(),
                                         this.getName(),
                                         f.getGeometry(),
                                         f.getStyle(),
                                         this.getAttributes(),
                                         this.getTimestamp(),
                                         this.getVersion());
            return retval;
        }

        @Override
        public long getId() {
            return this.rowId;
        }

        @Override
        public long getVersion() {
            return this.rowVersion;
        }

        @Override
        public long getFsid() {
            return this.rowFsid;
        }
        
        @Override
        public long getTimestamp() {
            return this.rowTimestamp;
        }
    }

    private static class QueryRequest {
        GeoPoint ul;
        GeoPoint lr;
        int maxLevelIdx;
    }

    /**
     * 
     * @param recordsToMerge
     * @return  <code>true</code> if the supplied <code>FIDBuffer</code> was
     *          consumed, <code>false</code> if the records were merged and it
     *          can be reused.
     */
    private boolean updateFIDBuffer(FIDBuffer recordsToMerge) {
        // XXX - update FID buffers
        if(fidBuffers.isEmpty()) {
            fidBuffers.add(recordsToMerge);
            return true;
        } else {
            FIDBuffer swap = null;
            try {
                for(int i = 0; i < recordsToMerge.numRecords; i++) {
                    boolean updated = false;
                    for(FIDBuffer buf : fidBuffers) {
                        int rec = buf.findRecord(recordsToMerge.getFID(i));
                        if(rec >= 0) {
                            buf.update(rec, recordsToMerge.getFID(i), recordsToMerge.getVersion(i), recordsToMerge.getLevel(i), recordsToMerge.getIndex(i));
                            updated = true;
                            break;
                        } else if(recordsToMerge.getFID(i) > buf.maxFID && buf.numRecords < FIDBuffer.MAX_RECORD_COUNT) {
                            buf.insert(recordsToMerge.getFID(i), recordsToMerge.getVersion(i), recordsToMerge.getLevel(i), recordsToMerge.getIndex(i));
                            updated = true;
                            break;
                        }
                    }
                    if(updated)
                        continue;
                    if(swap == null)
                        swap = new FIDBuffer();
                    swap.insert(recordsToMerge.getFID(i), recordsToMerge.getVersion(i), recordsToMerge.getLevel(i), recordsToMerge.getIndex(i));
                }
                
                // XXX - 
                if(swap != null) {
                    for(FIDBuffer buf : fidBuffers) {
                        if(swap.numRecords > 0)
                            break;
                        FIDBuffer.merge(buf, swap);
                    }
                    
                    if(swap.numRecords > 0) {
                        fidBuffers.add(swap);
                        swap = null;
                    }
                }
            } finally {
                if(swap != null)
                    swap.finalize();
            }
            
            // XXX - defragment and compact
            return false;
        }
    }
    
    private boolean refreshNodes(int levelIdx, int maxLevelIdx, double ulLat, double ulLng, double lrLat, double lrLng) {
        boolean retval = false;
        Point minTile;
        Point maxTile;
        Map<Integer, CacheNode> levelNodes;

        synchronized(this) {
            final TileMatrix.ZoomLevel level = matrix.get(levelIdx);
            minTile = TileMatrix.Util.getTileIndex(-180d, 90d, level, ulLng, ulLat);
            maxTile = TileMatrix.Util.getTileIndex(-180d, 90d, level, lrLng, lrLat);
            
            maxTile.x = Math.min(maxTile.x, (int)Math.round(360d/level.pixelSizeX) - 1);
            maxTile.y = Math.min(maxTile.y, (int)Math.round(180d/level.pixelSizeY) - 1);
            
            levelNodes = cacheNodes.get(levelIdx);
            if(levelNodes == null) {
                levelNodes = new HashMap<Integer, CacheNode>();
                cacheNodes.put(levelIdx, levelNodes);
            }
        }

        final int numTilesX = (int)Math.round(360d/matrix.get(levelIdx).pixelSizeX);
        for(int y = minTile.y; y <= maxTile.y; y++) {
            for(int x = minTile.x; x <= maxTile.x; x++) {
                int index = (y*numTilesX)+x;
                CacheNode node;
                CacheNode miss = null;
                synchronized(this) {
                    node = levelNodes.get(index);
                    if(node == null) {
                        miss = new CacheNode();
                        miss.index = index;
                        miss.level = levelIdx;
                        miss.featureCount = 0;
                        miss.cache = null;
                        miss.upperLeft = new GeoPoint(90d-(y*matrix.get(levelIdx).pixelSizeY), -180d+(x*matrix.get(levelIdx).pixelSizeX));
                        miss.lowerRight = new GeoPoint(90d-((y+1)*matrix.get(levelIdx).pixelSizeY), -180d+((x+1)*matrix.get(levelIdx).pixelSizeX));
                        miss.terminal = false;
                        miss.state = CacheNode.State.None;
                        miss.resolution = matrix.get(levelIdx).resolution;
                        
                        // try to load the node from disk cache
                        boolean loaded = false;
                        if(this.cacheDir != null) {
                            FIDBuffer fidBuffer = null;
                            try {
                                fidBuffer = new FIDBuffer();
                                final long s = System.currentTimeMillis();
                                loaded = loadCacheNode(this.clientVersion, this.cacheDir, miss, this.nodeFeatureLimit, fidBuffer, (levelIdx != maxLevelIdx));
                                final long e = System.currentTimeMillis();
                                Log.d(TAG, "Loaded [" + miss.level + "." + miss.index + "] " + miss.featureCount + " features in " + (e-s) + "ms, " + loaded + (miss.terminal ? " [terminal]" : ""));
                                
                                if(loaded) {
                                    // if the file cache for the node is not
                                    // terminal and the number of records it
                                    // contains is less than the current feature
                                    // limit (indicating limit was lower when cache
                                    // was created), we should refresh the node to
                                    // hold up to the new number of limit features.
                                    if(!miss.terminal && fidBuffer.numRecords < this.nodeFeatureLimit)
                                        miss.state = CacheNode.State.Dirty;
                                    
                                    if(updateFIDBuffer(fidBuffer))
                                        fidBuffer = null;
                                    
                                    node = miss;
                                    miss = null;
                                    levelNodes.put(node.index, node);
                                    retval = true;
                                    
                                    this.dispatchContentChanged();
                                }
                            } finally {
                                if(fidBuffer != null)
                                    fidBuffer.finalize();
                            }
                        }
                    }
                }

                // query for the number of features that will populate the
                // node, if >= limit continue recurse to maxLevelidx
                if(node == null && (levelIdx < maxLevelIdx)) {
                    int featureCount = this.nodeFeatureLimit;
                    try {
                        FeatureQueryParameters params = new FeatureQueryParameters();
                        params.spatialFilter = GeometryFactory.fromEnvelope(new Envelope(miss.upperLeft.getLongitude(), miss.lowerRight.getLatitude(), 0d, miss.lowerRight.getLongitude(), miss.upperLeft.getLatitude(), 0d));
                        params.limit = this.nodeFeatureLimit+1;
                        featureCount = this.client.queryFeaturesCount(params);
                        miss.featureCount = featureCount;
                    } catch(DataStoreException ignored) {}
                    
                    // the node is terminal; attempt to populate it
                    if(featureCount < this.nodeFeatureLimit) {
                        FIDBuffer fidBuffer = null;
                        try {
                            fidBuffer = new FIDBuffer();
                            final boolean refreshed = refreshCacheNode(
                                    this.client,
                                    this.clientVersion,
                                    miss, this.nodeFeatureLimit,
                                    fidBuffer,
                                    this.cacheDir);

                            synchronized(this) {
                                node = miss;
                                miss = null;
                                levelNodes.put(node.index, node);

                                // the node was refreshed, add it to the list
                                // and update the FID records                                
                                if(refreshed && updateFIDBuffer(fidBuffer))
                                    fidBuffer = null;
                                
                                this.dispatchContentChanged();
                            }
                        } finally {
                            if(fidBuffer != null)
                                fidBuffer.finalize();
                        }
                    } else {
                        miss.terminal = false;
                        miss.state = CacheNode.State.Dirty;

                        synchronized(this) {
                            node = miss;
                            miss = null;
                            levelNodes.put(node.index, node);
                        }
                    }
                } else if(node == null) {
                    miss.state = CacheNode.State.Dirty;

                    synchronized(this) {
                        node = miss;
                        miss = null;
                        levelNodes.put(node.index, node);
                    }
                }
                
                if(node.terminal || levelIdx == maxLevelIdx) {
                    if(needsRefresh(node, this.refreshInterval)) {
                        FIDBuffer fidBuffer = null;
                        try {
                            fidBuffer = new FIDBuffer();
                            final boolean refreshed = refreshCacheNode(
                                    this.client,
                                    this.clientVersion,
                                    node,
                                    this.nodeFeatureLimit,
                                    fidBuffer,
                                    this.cacheDir);

                            retval |= refreshed;
                            synchronized(this) {
                                // the node was refreshed, update the FID
                                // records                                
                                if(refreshed && updateFIDBuffer(fidBuffer))
                                    fidBuffer = null;
                                
                                this.dispatchContentChanged();
                            }
                        } finally {
                            if(fidBuffer != null)
                                fidBuffer.finalize();
                        }
                    }
                } else {
                    // continue recursion, limiting AOI to intersection of node
                    // bounds with current AOI
                    retval |= refreshNodes(
                                levelIdx+1,
                                maxLevelIdx,
                                Math.min(ulLat, node.upperLeft.getLatitude()),
                                Math.max(ulLng, node.upperLeft.getLongitude()),
                                Math.max(lrLat, node.lowerRight.getLatitude()),
                                Math.min(lrLng, node.lowerRight.getLongitude()));
                }
            }
        }
        
        return retval;
    }

    private class RefreshWorker implements Runnable {

        @Override
        public void run() {
            CacheNode refreshNode = null;
            boolean newData = false;
            while(true) {
                final QueryRequest servicing;
                synchronized(CachingFeatureDataStore.this) {
                    // XXX - check alive
                    
                    // if refreshed, update the cache
                    if(newData) {
                        dispatchContentChanged();
                        newData = false;
                    }
                    
                    // XXX - refine queue logic
                    if(CachingFeatureDataStore.this.refreshRequest == null) {
                        try {
                            CachingFeatureDataStore.this.wait();
                        } catch(InterruptedException ignored) {}
                        continue;
                    }
                    
                    servicing = CachingFeatureDataStore.this.refreshRequest;
                    CachingFeatureDataStore.this.refreshRequest = null;
                }
                
                newData = refreshNodes(0,
                                       servicing.maxLevelIdx,
                                       servicing.ul.getLatitude(), servicing.ul.getLongitude(),
                                       servicing.lr.getLatitude(), servicing.lr.getLongitude());
            }
        }
    }
    
    private static class FIDBuffer {
        private final static int RECORD_SIZE = 24;
        private final static int HEADER_SIZE = 20;
        public final static int MAX_RECORD_COUNT = 4096;

        private long ptr;
        public long minFID;
        public long maxFID;
        public int numRecords;
        
        FIDBuffer() {
            ptr = Unsafe.allocate(MAX_RECORD_COUNT*RECORD_SIZE);
            minFID = 0L;
            maxFID = 0L;
            numRecords = 0;
        }
        
        @Override
        public void finalize() {
            if(this.ptr != 0L) {
                Unsafe.free(this.ptr);
                this.ptr = 0L;
            }
        }

        public long getFID(int record) {
            return Unsafe.getLong(this.ptr + (RECORD_SIZE*record));
        }
        
        public long getVersion(int record) {
            return Unsafe.getLong(this.ptr + (RECORD_SIZE*record) + 8);
        }
        
        public int getLevel(int record) {
            return Unsafe.getInt(this.ptr + (RECORD_SIZE*record) + 16);
        }
        
        public int getIndex(int record) {
            return Unsafe.getInt(this.ptr + (RECORD_SIZE*record) + 20);
        }
        
        private void _verifyContents() {
            for(int i = 0; i < this.numRecords; i++) {
                for(int j = 0; j < this.numRecords; j++) {
                    if(j == i)
                        continue;
                    
                    long fidi = getFID(i);
                    long fidj = getFID(j);
                    if(fidi == fidj)
                        throw new IllegalStateException();
                }
            }
        }
        public void insert(long fid, long version, int level, int index) {
            //long[] preinsert = new long[this.numRecords*3];
            //for(int i = 0; i < numRecords*3; i++)
            //    preinsert[i] = Unsafe.getLong(ptr+(8*i));
            try {
            if(this.numRecords == 0 || fid > this.maxFID) {
                Unsafe.setLong(this.ptr + (RECORD_SIZE*this.numRecords), fid);
                Unsafe.setLong(this.ptr + (RECORD_SIZE*this.numRecords) + 8, version);
                Unsafe.setInt(this.ptr + (RECORD_SIZE*this.numRecords) + 16, level);
                Unsafe.setInt(this.ptr + (RECORD_SIZE*this.numRecords) + 20, index);
                
                if(this.numRecords == 0)
                    this.minFID = fid;
                this.maxFID = fid;
            } else {
                // XXX - use binary search to find index
                int insertAt = this.numRecords;
                for(int i = 0; i < this.numRecords; i++) {
                    if(getFID(i) == fid) { // do update
                        Unsafe.setLong(this.ptr + (RECORD_SIZE*i) + 8, version);
                        Unsafe.setInt(this.ptr + (RECORD_SIZE*i) + 16, level);
                        Unsafe.setInt(this.ptr + (RECORD_SIZE*i) + 20, index);

                        return;
                    } else if(getFID(i) > fid) {
                        insertAt = i;
                        break;
                    }
                }
                
                // shift the memory by one record at the insertion location
                Unsafe.memmove(this.ptr + (RECORD_SIZE*(insertAt+1)),
                               this.ptr + (RECORD_SIZE*insertAt),
                               (this.numRecords-insertAt)*RECORD_SIZE);
                
                // fill the record
                Unsafe.setLong(this.ptr + (RECORD_SIZE*insertAt), fid);
                Unsafe.setLong(this.ptr + (RECORD_SIZE*insertAt) + 8, version);
                Unsafe.setInt(this.ptr + (RECORD_SIZE*insertAt) + 16, level);
                Unsafe.setInt(this.ptr + (RECORD_SIZE*insertAt) + 20, index);
                
                if(minFID > fid)
                    minFID = fid;
            }
            
            this.numRecords++;
            } finally {
                //long[] postinsert = new long[this.numRecords*3];
                //for(int i = 0; i < numRecords*3; i++)
                //    postinsert[i] = Unsafe.getLong(ptr+(8*i));
                //verifyContents();
            }
        }
        public void update(int record, long fid, long version, int level, int index) {
            long bufferFid = getFID(record); 
            if(bufferFid != fid) {
                long[] contents = new long[this.numRecords*3];
                for(int i = 0; i < this.numRecords*3; i++)
                    contents[i] = Unsafe.getLong(this.ptr + (8*i));
                throw new IllegalArgumentException();
            }
            Unsafe.setLong(this.ptr + (RECORD_SIZE*record) + 8, version);
            Unsafe.setInt(this.ptr + (RECORD_SIZE*record) + 16, level);
            Unsafe.setInt(this.ptr + (RECORD_SIZE*record) + 20, index);
        }

        public int findRecord(long fid) {
            // check for out of bounds
            if(this.numRecords < 1)
                return -1;
            if(fid < this.minFID || fid > this.maxFID)
                return -1;
            
            // check bounds
            if(fid == this.minFID)
                return 0;
            else if(fid == this.maxFID)
                return this.numRecords-1;
            else if(this.numRecords == 2)
                return -1;

            // binary search
            int minRecord = 0;
            long minRecFid = this.minFID;
            int midRecord = this.numRecords/2;
            long midRecFid = getFID(midRecord);
            int maxRecord = this.numRecords-1;
            long maxRecFid = this.maxFID;
            
            while((maxRecord-minRecord) > 1) {
                if(minRecFid == fid)
                    return minRecord;
                else if(maxRecFid == fid)
                    return maxRecord;
                else if(midRecFid == fid)
                    return midRecord;
                
                if(fid > midRecFid) {
                    minRecord = midRecord;
                    minRecFid = midRecFid;
                } else {
                    maxRecord = midRecord;
                    maxRecFid = midRecFid;
                }
                
                midRecord = (minRecord+maxRecord)/2;
                midRecFid = getFID(midRecord);
            }
            
            return -1;
        }
        
        public static void merge(FIDBuffer a, FIDBuffer b) {
            FIDBuffer merge1 = new FIDBuffer();
            FIDBuffer merge2 = new FIDBuffer();
            
            int recA = 0;
            int recB = 0;
            
            FIDBuffer to = merge1;

            long fidA = a.getFID(recA);
            long verA = a.getVersion(recA);
            
            long fidB = b.getFID(recB);
            long verB = b.getVersion(recB);

            while(recA < a.numRecords && recB < b.numRecords) {
                int rec;
                FIDBuffer from;
                if(recA == a.numRecords) { // 'a' is exhausted
                    from = b;
                    rec = recB;
                    recB++;
                } else if(recB == b.numRecords) { // 'b' is exhausted
                    from = a;
                    rec = recA;
                    recA++;
                } else if(fidA < fidB) { // select min FID
                    from = a;
                    rec = recA;
                    recA++;
                } else if(fidA > fidB) {
                    from = b;
                    rec = recB;
                    recB++;
                } else if(verA > verB) { // collision, select newest version
                    from = a;
                    rec = recA;
                    recA++;
                    recB++;
                } else if(verA < verB) {
                    from = b;
                    rec = recB;
                    recA++;
                    recB++;                    
                } else { // total collision
                    from = a;
                    rec = recA;
                    recA++;
                    recB++;
                }
                
                to.insert(from.getFID(rec), from.getVersion(rec), from.getLevel(rec), from.getIndex(rec));
                if(to.numRecords == MAX_RECORD_COUNT)
                    to = merge2;
            }
            
            Unsafe.free(a.ptr);
            a.ptr = merge1.ptr;
            a.minFID = merge1.minFID;
            a.maxFID = merge1.maxFID;
            a.numRecords = merge1.numRecords;
            merge1.ptr = 0L;

            Unsafe.free(b.ptr);
            b.ptr = merge2.ptr;
            b.minFID = merge2.minFID;
            b.maxFID = merge2.maxFID;
            b.numRecords = merge2.numRecords;
            merge2.ptr = 0L;
        }
        
        public static int computeOverlap(FIDBuffer a, FIDBuffer b) {
            if(a.maxFID < b.minFID)
                return 0;
            else if(a.minFID > b.maxFID)
                return 0;
            
            return (int)(Math.min(a.maxFID, b.maxFID)-Math.max(a.minFID, b.minFID));
        }
    }
}
