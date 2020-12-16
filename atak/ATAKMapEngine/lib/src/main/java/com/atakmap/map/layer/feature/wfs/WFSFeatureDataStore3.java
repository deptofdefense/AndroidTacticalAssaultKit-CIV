package com.atakmap.map.layer.feature.wfs;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.CoordinateTransformation;
import org.gdal.osr.SpatialReference;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.IteratorCursor;
import com.atakmap.database.QueryIface;
import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.database.StatementIface;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.feature.AbstractFeatureDataStore;
import com.atakmap.map.layer.feature.AbstractFeatureDataStore2;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.cursor.BruteForceLimitOffsetFeatureCursor;
import com.atakmap.map.layer.feature.cursor.MultiplexingFeatureCursor;
import com.atakmap.map.layer.feature.datastore.FeatureDatabase;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.ogr.OgrFeatureDataSource;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.util.ReferenceCount;

public class WFSFeatureDataStore3 extends AbstractFeatureDataStore implements Runnable {

    private final static Comparator<FeatureDb> FEATUREDB_NAME_COMPARATOR = new Comparator<FeatureDb>() {
        @Override
        public int compare(FeatureDb arg0, FeatureDb arg1) {
            int retval = arg0.name.compareToIgnoreCase(arg1.name);
            if(retval == 0 && (arg0.fsid != arg1.fsid))
                retval = (arg0.fsid < arg1.fsid) ? -1 : 1;
            return retval;
        }
    };
    
    private final static int SCHEMA_VERSION = 3;

    private final static String TAG = "WFSFeatureDataStore3";

    private final static String TYPE = "wfs";
    private final static String PROVIDER = "wfs";
    
    private final String uri;
    private final File workingDir;

    private Thread refreshThread;

    private Map<Long, FeatureDb> fsidToFeatureDb;
    private Map<String, FeatureDb> fsNameToFeatureDb;
    
    private int refreshRequest;

    private boolean disposed;
    private boolean disposing;
    
    private WFSSchemaHandler schemaHandler;

    public WFSFeatureDataStore3(String uri, File workingDir) {
        super(0,
              VISIBILITY_SETTINGS_FEATURESET);
        
        this.uri = uri;

        if(IOProviderFactory.exists(workingDir) && IOProviderFactory.isFile(workingDir))
            FileSystemUtils.delete(workingDir);

        // XXX - 
        if(true)
            if (!workingDir.delete()) { 
                Log.d(TAG, "could not delete: " + workingDir);
            }
        if(!IOProviderFactory.exists(workingDir)) {
            if (!IOProviderFactory.mkdirs(workingDir)) {
                Log.d(TAG, "could not mkdir: " + workingDir);
            }
        }
        this.workingDir = workingDir;
        
        this.fsidToFeatureDb = new HashMap<Long, FeatureDb>();
        this.fsNameToFeatureDb = new HashMap<String, FeatureDb>();

        this.disposed = false;
        this.disposing = false;

        this.schemaHandler = WFSSchemaHandlerRegistry.get(this.uri);
        if(this.schemaHandler == null)
            this.schemaHandler = new DefaultWFSSchemaHandler(this.uri);

        // refresh to update immediately from the cache (if available) and fetch
        // the feature data from remote
        this.refresh();
    }
    
    private static Class<? extends Geometry> getStyleGeomType(org.gdal.ogr.Geometry feature) {
        if (feature == null)
            return null;

        final int ogrGeomType = feature.GetGeometryType();
        switch(ogrGeomType) {
            case ogrConstants.wkbMultiPoint :
            case ogrConstants.wkbMultiPoint25D :
            case ogrConstants.wkbPoint :
            case ogrConstants.wkbPoint25D :
                return Point.class;
            case ogrConstants.wkbCircularString :
            case ogrConstants.wkbCircularStringZ :
            case ogrConstants.wkbCompoundCurve :
            case ogrConstants.wkbCompoundCurveZ :
            case ogrConstants.wkbLinearRing :
            case ogrConstants.wkbLineString :
            case ogrConstants.wkbLineString25D :
            case ogrConstants.wkbMultiCurve :
            case ogrConstants.wkbMultiCurveZ :
            case ogrConstants.wkbMultiLineString :
            case ogrConstants.wkbMultiLineString25D :
                return LineString.class;
            case ogrConstants.wkbCurvePolygon :
            case ogrConstants.wkbCurvePolygonZ :
            case ogrConstants.wkbMultiPolygon :
            case ogrConstants.wkbMultiPolygon25D :
            case ogrConstants.wkbMultiSurface :
            case ogrConstants.wkbMultiSurfaceZ :
            case ogrConstants.wkbPolygon :
            case ogrConstants.wkbPolygon25D :
                return Polygon.class;
            case ogrConstants.wkbGeometryCollection :
            case ogrConstants.wkbGeometryCollection25D :
                Set<Class<? extends Geometry>> types = new HashSet<Class<? extends Geometry>>();
                Class<? extends Geometry> childType;
                for(int i = 0; i < feature.GetGeometryCount(); i++) {
                    childType = getStyleGeomType(feature.GetGeometryRef(i));
                    if(childType != null)
                        types.add(childType);
                }
                if(types.size() == 1)
                    return types.iterator().next();
            default :
                return null;
        }
    }


    private static String getDescription(Layer layer, org.gdal.ogr.Feature feature) {
        StringBuilder retval = new StringBuilder();

        for(int i = 0; i < feature.GetFieldCount(); i++) {
            FieldDefn def = feature.GetFieldDefnRef(i);
            if (def != null) { 
                retval.append(def.GetName());
                retval.append(": ");
                if(def.GetFieldType() == ogrConstants.OFTInteger) {
                    retval.append(feature.GetFieldAsInteger(i));
                } else if(def.GetFieldType() == ogrConstants.OFTInteger64) {
                    retval.append(feature.GetFieldAsInteger64(i));
                } else if(def.GetFieldType() == ogrConstants.OFTReal) {
                    retval.append(feature.GetFieldAsDouble(i));
                } else if(def.GetFieldType() == ogrConstants.OFTString) {
                    retval.append(feature.GetFieldAsString(i));
                }
                retval.append("\n");
            }
        }
        
        return retval.toString();
    }

    /**************************************************************************/
    // Runnable

    @Override
    public void run() {
        // open index DB
        long start = System.currentTimeMillis();
        Log.d(TAG, "Starting refresh thread for " + getUri());
        DatabaseIface indexDatabase = null;
        try {
            File indexDbFile = new File(this.workingDir, "index.sqlite");
            boolean checkSchema;
            do {
                checkSchema = IOProviderFactory.exists(indexDbFile);
                // try to open the database. if the database somehow became
                // corrupted, delete the file and start anew
                try {
                    indexDatabase = IOProviderFactory.createDatabase(indexDbFile);
                    indexDatabase.execute("PRAGMA journal_mode = WAL", null);
                    break;
                } catch(Throwable t) {
                    if(IOProviderFactory.exists(indexDbFile) && !indexDbFile.delete())
                        throw new RuntimeException("Unable to delete file", t);
                }
            } while(true);

            if(checkSchema && indexDatabase.getVersion() != SCHEMA_VERSION)
                indexDatabase.execute("DROP TABLE IF EXISTS featuredbs", null);
            
            indexDatabase.execute("CREATE TABLE IF NOT EXISTS featuredbs (fsid INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT, name TEXT, featurecount INTEGER, minresolution REAL, maxresolution REAL, minx REAL, miny REAL, maxx REAL, maxy REAL, visible INTEGER, version INTEGER, lastupdate INTEGER)", null);
            indexDatabase.setVersion(SCHEMA_VERSION);
            
            int requestServiced = -1;
            while(true) {
                Set<String> invalidFsNames = new HashSet<String>();

                synchronized(this) {
                    if(requestServiced == -1) {
                        // initial state, update the featuredbs
                        CursorIface result = null;
                        try {
                            for(FeatureDb db : this.fsidToFeatureDb.values()) {
                                if(db.database != null)
                                    db.database.dereference();
                            }
                            this.fsidToFeatureDb.clear();
                            this.fsNameToFeatureDb.clear();
                            
                            result = indexDatabase.query("SELECT fsid, path, name, featurecount, minresolution, maxresolution, minx, miny, maxx, maxy, visible, version, lastupdate FROM featuredbs", null);
                            FeatureDb db;
                            while(result.moveToNext()) {
                                Log.d(TAG, "open cached " + result.getString(2) + "  " + result.getString(1));
                                if(result.getString(1) == null)
                                    continue;

                                db = new FeatureDb();
                                db.fsid = result.getLong(0);
                                db.numRecords = result.getLong(3);
                                db.bounds = new Envelope(result.getDouble(6), result.getDouble(7), 0, result.getDouble(8), result.getDouble(9), 0);
                                //db.visible = (result.getInt(9)!=0);
                                db.name = result.getString(2);
                                db.minResolution = result.getDouble(4);
                                db.maxResolution = result.getDouble(5);
                                db.version = result.getInt(10);
                                db.path = result.getString(1);
                                db.lastUpdate = result.getLong(11);
                                db.database = new DatabaseRef(new FeatureDatabase(new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(result.getString(1))), 0, VISIBILITY_SETTINGS_FEATURESET));

                                this.fsidToFeatureDb.put(Long.valueOf(db.fsid), db);
                                this.fsNameToFeatureDb.put(db.name, db);
                                invalidFsNames.add(db.name);
                            }
                            this.dispatchDataStoreContentChangedNoSync();
                        } finally {
                            if(result != null)
                                result.close();
                        }
                    }
                    if(this.refreshRequest == requestServiced)
                        break;
                    requestServiced = this.refreshRequest;
                }

                // connect to WFS
                DataSource wfs = null;
                try {
                    wfs = ogr.Open("WFS:" + this.uri, false);
                    if(wfs == null) {
                        Log.w(TAG, "Failed to connect to WFS " + this.uri + ", server may be unavailable.");
                        return;
                    }
                    
                    Collection<Thread> layerHandlers = new LinkedList<Thread>();
                    QueryIface result = null;
                    try {
                        result = indexDatabase.compileQuery("SELECT fsid, version, lastupdate FROM featuredbs WHERE name = ?");
                        
                        Layer layer;
                        long fsid;
                        long version;
                        String layerName;
                        for(int i = 0; i < wfs.GetLayerCount(); i++) {
                            layer = wfs.GetLayer(i);
                            if (layer != null) { 
                                if(this.schemaHandler.ignoreLayer(layer.GetName()))
                                    continue;

                                layerName = this.schemaHandler.getLayerName(layer.GetName());
                                try {
                                    result.clearBindings();
                                    result.bind(1, layerName);
                                    
                                    if(result.moveToNext()) {
                                        // mark as valid
                                        invalidFsNames.remove(layerName);
                                        fsid = result.getLong(0);
                                        version = result.getLong(1);
    
                                        // if the content for the layer was pulled
                                        // down within the last week, don't
                                        // redownload
                                        if((System.currentTimeMillis()-result.getLong(2)) < (7L*24L*60L*60L*1000L)) {
                                            continue;
                                        }
                                    } else {
                                        // insert
                                        indexDatabase.execute("INSERT INTO featuredbs (path, name, featurecount, minresolution, maxresolution, minx, miny, maxx, maxy, visible) VALUES (NULL, ?, 0, 1000000000, 0, -180, -90, 180, 90, 0)", new String[] {layerName});
                                        fsid = Databases.lastInsertRowId(indexDatabase);
                                        version = 0L;
                                    }
                                } finally {
                                    result.reset();
                                }

                                // create the updated instance of the feature db.
                                // Set the FSID, name and current version; the other
                                // fields will be filled in by the handler prior to
                                // update
                                FeatureDb updateDb = new FeatureDb();
                                updateDb.fsid = fsid;
                                updateDb.name = layerName;
                                updateDb.version = version;
                                
                                Thread updateThread = new Thread(new LayerHandler(indexDatabase, layer, updateDb), TAG + "-Update");
                                updateThread.setPriority(Thread.NORM_PRIORITY);
                                updateThread.start();
                                
                                layerHandlers.add(updateThread);    
                            }
                        }
                    } finally {
                        if(result != null)
                            result.close();
                    }
                    
                    synchronized(this) {
                        FeatureDb db;
                        for(String fsName : invalidFsNames) {
                            db = this.fsNameToFeatureDb.remove(fsName);
                            this.fsidToFeatureDb.remove(db.fsid);
                            
                            if(db.database != null)
                                db.database.dereference();
                        }
                        
                        // if we removed any invalid layers, notify content
                        // changed
                        if(!invalidFsNames.isEmpty())
                            this.dispatchDataStoreContentChangedNoSync();
                    }

                    // join layer handlers
                    for(Thread thread : layerHandlers) {
                        try {
                            thread.join();
                        } catch(InterruptedException ignored) {}
                    }
                    layerHandlers.clear();
                } finally {
                    if(wfs != null)
                        wfs.delete();
                }
            }
        } finally {
            if(indexDatabase != null)
                indexDatabase.close();
        }
        Log.d(TAG, "Finished refresh for " + getUri() + " in "
                + (System.currentTimeMillis() - start) + "ms");
        synchronized (this) {
            this.refreshThread = null;
            notify();
        }
        Log.d(TAG, "Refresh notify finished for " + getUri());
    }
    
    /**************************************************************************/

    @Override
    public Feature getFeature(long fid) {
        final long fsid = ((fid>>32L)&0xFFFFFFFFL);
        synchronized(this) {
            FeatureDb db = this.fsidToFeatureDb.get(Long.valueOf(fsid));
            if(db == null)
                return null;
            Feature retval = db.database.value.getFeature(fid&0xFFFFFFFFL);
            if(retval == null)
                return null;
            retval = new Feature(
                retval.getFeatureSetId(),
                (retval.getFeatureSetId()<<32L)|retval.getId(),
                   retval.getName(),
                   retval.getGeometry(),
                   retval.getStyle(),
                   retval.getAttributes(),
                   retval.getTimestamp(),
                   retval.getVersion());
            return retval;
        }
    }

    private static boolean matches(FeatureDb db, FeatureQueryParameters params) {
        if(params == null)
            return true;

        if(params.providers != null && !AbstractFeatureDataStore2.matches(params.providers, PROVIDER, '%'))
            return false;
        if(params.types != null && !AbstractFeatureDataStore2.matches(params.types, TYPE, '%'))
            return false;
        if(params.featureSetIds != null && !params.featureSetIds.contains(Long.valueOf(db.fsid)))
            return false;
        if(params.featureIds != null) {
            boolean matches = false;
            for(Long fid : params.featureIds) {
                final long fsid = ((fid.longValue()>>32)&0xFFFFFFFFL);
                if(fsid == db.fsid) {
                    matches = true;
                    break;
                }
            }
            if(!matches)
                return false;
        }
        if(!Double.isNaN(params.minResolution)) {
            // XXX - 
        }
        if(!Double.isNaN(params.maxResolution)) {
            // XXX - 
        }
        
        return true;
    }
    
    private static FeatureQueryParameters filter(FeatureDb db, FeatureQueryParameters params) {
        FeatureQueryParameters retval = null;
        if(params == null)
            return retval;

        if(params.featureIds != null) {
            if(retval == null)
                retval = new FeatureQueryParameters(params);
            
            // check and mask off FSID
            retval.featureIds = new LinkedList<Long>();
            for(Long fid : params.featureIds) {
                final long fsid = ((fid.longValue()>>32)&0xFFFFFFFFL);
                if(fsid == db.fsid)
                    retval.featureIds.add(Long.valueOf(fid.longValue()&0xFFFFFFFFL));
            }
        }
        if(params.featureSetIds != null) {
            if(retval == null)
                retval = new FeatureQueryParameters(params);
            retval.featureSetIds = null;
        }
        if(params.featureSets != null) {
            if(retval == null)
                retval = new FeatureQueryParameters(params);
            retval.featureSets = null;
        }
        if(params.providers != null) {
            if(retval == null)
                retval = new FeatureQueryParameters(params);
            retval.providers = null;
        }
        if(params.types != null) {
            if(retval == null)
                retval = new FeatureQueryParameters(params);
            retval.types = null;
        }
        if(params.visibleOnly ||
           params.geometryTypes != null ||
           params.ignoredFields != 0 ||
           !Double.isNaN(params.maxResolution) ||
           !Double.isNaN(params.minResolution) ||
           params.offset != 0 ||
           params.ops != null ||
           params.order != null ||
           params.spatialFilter != null) {
            
            if(retval == null)
                retval = new FeatureQueryParameters(params);
        }
 
        return retval;
    }

    private Map<FeatureDb, FeatureQueryParameters> prepareQuery(FeatureQueryParameters params) {
        Map<FeatureDb, FeatureQueryParameters> retval = new HashMap<FeatureDb, FeatureQueryParameters>();
        for(FeatureDb db : this.fsidToFeatureDb.values())
            if(matches(db, params))
                retval.put(db, filter(db, params));
        return retval;
    }

    @Override
    public FeatureCursor queryFeatures(FeatureQueryParameters params) {
        CursorIface result = null;
        try {
            Map<FeatureDb, FeatureQueryParameters> dbs = this.prepareQuery(params);
            // if there is a limit/offset specified and we are going to be
            // querying more than one database we will need to brute force;
            // strip the offset/limit off of any DB instance queries 
            if(dbs.size() > 1 && params.limit != 0) {
                for(FeatureQueryParameters dbParams : dbs.values()) {
                    if(dbParams != null) {
                        dbParams.offset = 0;
                        dbParams.limit = 0;
                    }
                }
            }
            Collection<FeatureCursor> cursors = new LinkedList<FeatureCursor>();
            for(Map.Entry<FeatureDb, FeatureQueryParameters> db : dbs.entrySet())
                cursors.add(new DistributedFeatureCursorImpl(db.getKey().database.value.queryFeatures(db.getValue()), db.getKey().database));
            FeatureCursor retval = new MultiplexingFeatureCursor(cursors, params.order);
            if(dbs.size() > 1 && params.limit != 0)
                retval = new BruteForceLimitOffsetFeatureCursor(retval, params.offset, params.limit);
            return retval;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) {
        if(params != null && params.limit > 0) {
            return AbstractFeatureDataStore2.queryFeaturesCount(this, params);
        } else {
            Map<FeatureDb, FeatureQueryParameters> dbs = this.prepareQuery(params);
            int retval = 0;
            for(Map.Entry<FeatureDb, FeatureQueryParameters> db : dbs.entrySet())
                retval += db.getKey().database.value.queryFeaturesCount(db.getValue());
            return retval;
        }
    }

    @Override
    public synchronized FeatureSet getFeatureSet(long fsid) {
        final FeatureDb db = this.fsidToFeatureDb.get(Long.valueOf(fsid));
        if(db == null)
            return null;

        final FeatureSet retval = new FeatureSet(PROVIDER,
                                                 TYPE,
                                                 db.name,
                                                 db.minResolution,
                                                 db.maxResolution);
        this.adopt(retval, db.fsid, db.version);
        return retval;
    }

    @Override
    public synchronized FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
        Set<Long> fsids = new HashSet<Long>(this.fsidToFeatureDb.keySet());
        if(params != null && params.providers != null) {
            if(!AbstractFeatureDataStore2.matches(params.providers, PROVIDER, '%'))
                fsids = Collections.<Long>emptySet();
        }
        if(params != null && params.types != null) {
            if(!AbstractFeatureDataStore2.matches(params.types, TYPE, '%'))
                fsids = Collections.<Long>emptySet();
        }

        if(params != null && params.ids != null)
            fsids.retainAll(params.ids);

        if(params != null && params.visibleOnly) {
            Iterator<Long> iter = fsids.iterator();
            FeatureDb db;
            while(iter.hasNext()) {
                db = this.fsidToFeatureDb.get(iter.next());
                if(db == null || !db.database.value.isFeatureSetVisible(db.fsid))
                    iter.remove();
            }
        }
        if(params != null && params.names != null) {
            Iterator<Long> iter = fsids.iterator();
            FeatureDb db;
            while(iter.hasNext()) {
                db = this.fsidToFeatureDb.get(iter.next());
                if(db == null || !AbstractFeatureDataStore2.matches(params.names, db.name, '%'))
                    iter.remove();
            }
        }

        SortedSet<FeatureDb> dbs = new TreeSet<FeatureDb>(FEATUREDB_NAME_COMPARATOR);
        FeatureDb db;
        for(Long fsid : fsids) {
            db = this.fsidToFeatureDb.get(fsid);
            if(db != null)
                dbs.add(db);
        }
        
        if(params != null && params.offset != 0) {
            for(int i = 0; i < params.offset; i++) {
                if(dbs.isEmpty())
                    break;
                dbs.remove(dbs.first());
            }
        }
        if(params != null && params.limit != 0) {
            while(dbs.size() > params.limit)
                dbs.remove(dbs.last());
        }
        
        return new FeatureSetCursorImpl(dbs.iterator());
    }

    @Override
    public synchronized int queryFeatureSetsCount(FeatureSetQueryParameters params) {
        if(params.providers != null) {
            if(!AbstractFeatureDataStore2.matches(params.providers, PROVIDER, '%'))
                return 0;
        }
        if(params.types != null) {
            if(!AbstractFeatureDataStore2.matches(params.types, TYPE, '%'))
                return 0;
        }
        
        Set<Long> fsids = new HashSet<Long>(this.fsidToFeatureDb.keySet());
        if(params.ids != null)
            fsids.retainAll(params.ids);

        if(params.visibleOnly) {
            Iterator<Long> iter = fsids.iterator();
            FeatureDb db;
            while(iter.hasNext()) {
                db = this.fsidToFeatureDb.get(iter.next());
                if(db == null || !db.database.value.isFeatureSetVisible(db.fsid))
                    iter.remove();
            }
        }
        if(params.names != null) {
            Iterator<Long> iter = fsids.iterator();
            FeatureDb db;
            while(iter.hasNext()) {
                db = this.fsidToFeatureDb.get(iter.next());
                if(db == null || !AbstractFeatureDataStore2.matches(params.names, db.name, '%'))
                    iter.remove();
            }
        }
        
        return fsids.size();
    }

    @Override
    public boolean isInBulkModification() {
        return false;
    }

    @Override
    public synchronized boolean isFeatureSetVisible(long fsid) {
        final FeatureDb db = this.fsidToFeatureDb.get(Long.valueOf(fsid));
        if(db == null)
            return false;
        return db.database.value.isFeatureSetVisible(fsid);
    }

    @Override
    public boolean isAvailable() {
        return !this.disposing && !this.disposed;
    }

    @Override
    public synchronized void refresh() {
        if(this.disposing || this.disposed)
            return;

        if(this.refreshThread != null)
            return;
        
        this.refreshRequest++;
        this.refreshThread = new Thread(this, TAG + "-Refresh");
        this.refreshThread.setPriority(Thread.NORM_PRIORITY);
        this.refreshThread.start();
    }

    @Override
    public String getUri() {
        return this.workingDir.getAbsolutePath();
    }

    @Override
    public synchronized void dispose() {
        if(this.disposed)
            return;

        this.disposing = true;
        while(this.refreshThread != null) {
            try {
                // TODO: Find a way to properly exit out of layer refresh
                // Certain data sets take a very long time to finish
                // This causes the WFS importer to stay locked,
                // which then prevents any further WFS data sets
                // from being imported until one is finished
                this.wait();
            } catch(InterruptedException ignored) {}
        }

        for(FeatureDb db : this.fsidToFeatureDb.values())
            if(db.database != null)
                db.database.dereference();
        this.fsidToFeatureDb.clear();
        this.fsNameToFeatureDb.clear();
        this.disposed = true;
    }

    @Override
    protected void setFeatureVisibleImpl(long fid, boolean visible) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected synchronized void setFeatureSetVisibleImpl(long setId, boolean visible) {
        FeatureDb db = this.fsidToFeatureDb.get(Long.valueOf(setId));
        if(db == null)
            return;
        db.database.value.setFeatureSetVisible(db.fsid, visible);
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected synchronized void setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible) {
        if(params == null) {
            for(FeatureDb db : this.fsidToFeatureDb.values())
                db.database.value.setFeatureSetVisible(db.fsid, visible);
            return;
        }

        if(params.providers != null) {
            if(!params.providers.contains(PROVIDER))
                return;
        }
        if(params.types != null) {
            if(!params.types.contains(PROVIDER))
                return;
        }
        
        Set<Long> fsids = new HashSet<Long>(this.fsidToFeatureDb.keySet());
        if(params.ids != null)
            fsids.retainAll(params.ids);

        if(params.visibleOnly) {
            Iterator<Long> iter = fsids.iterator();
            FeatureDb db;
            while(iter.hasNext()) {
                db = this.fsidToFeatureDb.get(iter.next());
                if(db == null || !db.database.value.isFeatureSetVisible(db.fsid))
                    iter.remove();
            }
        }
        if(params.names != null) {
            Iterator<Long> iter = fsids.iterator();
            FeatureDb db;
            while(iter.hasNext()) {
                db = this.fsidToFeatureDb.get(iter.next());
                if(db == null || !AbstractFeatureDataStore2.matches(params.names, db.name, '%'))
                    iter.remove();
            }
        }
        
        FeatureDb db;
        for(Long fsid : fsids) {
            db = this.fsidToFeatureDb.get(fsid);
            if(db != null)
                db.database.value.setFeatureSetVisible(db.fsid, visible);
        }
        
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    protected void beginBulkModificationImpl() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void endBulkModificationImpl(boolean successful) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void deleteAllFeatureSetsImpl() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    protected FeatureSet insertFeatureSetImpl(String provider, String type, String name, double minResolution, double maxResolution, boolean returnRef) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureSetImpl(long fsid, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureSetImpl(long fsid, double minResolution, double maxResolution) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureSetImpl(long fsid, String name, double minResolution, double maxResolution) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void deleteFeatureSetImpl(long fsid) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Feature insertFeatureImpl(long fsid, String name, Geometry geom, Style style, AttributeSet attributes, boolean returnRef) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureImpl(long fid, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureImpl(long fid, Geometry geom) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureImpl(long fid, Style style) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureImpl(long fid, AttributeSet attributes) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void updateFeatureImpl(long fid, String name, Geometry geom, Style style, AttributeSet attributes) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void deleteFeatureImpl(long fsid) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void deleteAllFeaturesImpl(long fsid) {
        throw new UnsupportedOperationException();
    }

    /**************************************************************************/

    private static class FeatureDb {
        public DatabaseRef database;
        public long fsid;
        public long numRecords;
        public Envelope bounds;
        //public boolean visible = true;
        public String name;
        public double minResolution;
        public double maxResolution;
        public long version;
        public String path;
        public long lastUpdate;
    }
    
    /**************************************************************************/

    private class FeatureSetCursorImpl extends IteratorCursor<FeatureDb> implements FeatureSetCursor {

        public FeatureSetCursorImpl(Iterator<FeatureDb> iter) {
            super(iter);
        }

        @Override
        public FeatureSet get() {
            FeatureDb db = this.getRowData();
            if(db == null)
                return null;

            final FeatureSet retval = new FeatureSet(PROVIDER,
                                                     TYPE,
                                                     db.name,
                                                     db.minResolution,
                                                     db.maxResolution);
            WFSFeatureDataStore3.this.adopt(retval, db.fsid, db.version);
            return retval;
        }
        
    }

    /**************************************************************************/
    
    private static class FeatureDefinitionImpl implements com.atakmap.map.layer.feature.FeatureDefinition {

        AttributeSet attribs;
        byte[] wkbGeom;
        String ogrStyle;
        String name;

        @Override
        public Object getRawGeometry() {
            return wkbGeom;
        }

        @Override
        public int getGeomCoding() {
            return GEOM_WKB;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getStyleCoding() {
            return STYLE_OGR;
        }

        @Override
        public Object getRawStyle() {
            return this.ogrStyle;
        }

        @Override
        public AttributeSet getAttributes() {
            return this.attribs;
        }

        @Override
        public Feature get() {
            return new Feature(this);
        }
    }

    private class LayerHandler implements Runnable {
        private DatabaseIface indexDatabase;
        private Layer layer;
        private FeatureDb db;
        
        public LayerHandler(DatabaseIface indexDatabase, Layer layer, FeatureDb db) {
            this.indexDatabase = indexDatabase;
            this.layer = layer;
            this.db = db;
        }

        @Override
        public void run() {
            // create new DB
            final File layerDbFile;
            try {
                layerDbFile = IOProviderFactory.createTempFile("layer" + this.db.fsid, ".sqlite", WFSFeatureDataStore3.this.workingDir);
            } catch(IOException e) {
                throw new RuntimeException(e);
            }

            if(IOProviderFactory.exists(layerDbFile))
                FileSystemUtils.delete(layerDbFile);
            
            Log.d(TAG, "refreshing layer " + layer.GetName() + "(" + layerDbFile.getAbsolutePath() + ")");
            
            FeatureDatabase.Builder builder = null;
            try {
                builder = new FeatureDatabase.Builder(layerDbFile, this.db.fsid, this.db.name, PROVIDER, TYPE);

            
                CoordinateTransformation layer2wgs84 = null;
                
                final SpatialReference layerSpatialRef = this.layer.GetSpatialRef();
                final int layerSrid = GdalLibrary.getSpatialReferenceID(layerSpatialRef);
                if (layerSrid != 4326 && layerSpatialRef != null)
                    layer2wgs84 = new CoordinateTransformation(layerSpatialRef, GdalLibrary.EPSG_4326);
                else
                    layer2wgs84 = null;

                builder.beginBulkInsertion();
                org.gdal.ogr.Feature feature = null;
                FeatureDefinitionImpl defn = new FeatureDefinitionImpl();
                try {
                    // iterate layer features; insert into DB
                    this.layer.ResetReading();
                    
                    org.gdal.ogr.Geometry geom;
                    double[] envelope = new double[4];
    
                    int numVerts = 0;
                    
                    db.numRecords = 0;
                    do {
                        feature = this.layer.GetNextFeature();
                        if(feature == null)
                            break;
                        
                        defn.attribs = OgrFeatureDataSource.ogr2attr(feature);
                        if(schemaHandler.ignoreFeature(this.layer.GetName(), defn.attribs))
                            continue;

                        geom = feature.GetGeometryRef();
                        if(geom == null)
                            continue;
                        
                        geom.FlattenTo2D();
                        if(layer2wgs84 != null)
                            geom.Transform(layer2wgs84);
                        if(geom.HasCurveGeometry() != 0) { 
                            org.gdal.ogr.Geometry lGeom = 
                                geom.GetLinearGeometry();

                            if (lGeom != null)
                                geom = lGeom;
                        }
                        
                        // XXX - 
                        numVerts += geom.GetPointCount();
                        //numVerts += getPointCount(geom);

                        defn.name = schemaHandler.getFeatureName(this.layer.GetName(), defn.attribs);
                        defn.wkbGeom = geom.ExportToWkb();
                        defn.ogrStyle = getStyle(this.layer.GetName(), defn.attribs, feature);
                        
                        builder.insertFeature(defn);
                        
                        // update bounds
                        geom.GetEnvelope(envelope);
                        if(db.bounds == null) {
                            db.bounds = new Envelope(envelope[0],
                                                     envelope[2],
                                                     0d,
                                                     envelope[1],
                                                     envelope[3],
                                                     0d);
                        } else {
                            if(db.bounds.minX > envelope[0])
                                db.bounds.minX = envelope[0];
                            if(db.bounds.maxX < envelope[1])
                                db.bounds.maxX = envelope[1];
                            if(db.bounds.minY > envelope[2])
                                db.bounds.minY = envelope[2];
                            if(db.bounds.maxY < envelope[3])
                                db.bounds.maxY = envelope[3];
                        }

                        db.numRecords++;
                    } while(true);
                    
                    Log.d(TAG, layer.GetName() + " " + db.numRecords + " features");
    
                    if(db.numRecords < 1) {
                        Log.d(TAG, "No features for " + layer.GetName());
                        return;
                    }
    
                    // get the total number of vertices for the geometries in the
                    // layer. this value will be used to adjust the minimum LOD
/*
                    
    
                    result = null;
                    try {
                        result = db.database.value.query("SELECT Sum(ST_NPoints(geometry)) FROM features", null);
                        if(result.moveToNext())
                            numVerts = result.getInt(0);
                        else
                            numVerts = (int)db.numRecords;
                    } finally {
                        if(result != null)
                            result.close();
                    }
*/                    
    
                    // XXX - taken directly from OgrFeatureDataSource
    
                    final int dpi = (int)Math.ceil(GLRenderGlobals.getRelativeScaling()*240);
    
                    // compute LOD
                    final int threshold = (int) Math
                            .ceil(((double) (dpi * dpi) / (double) (96 * 96)) * 64.0d);
                    int levelOfDetail = computeLevelOfDetail(threshold,
                                                             this.db.bounds.maxY,
                                                             this.db.bounds.minX,
                                                             this.db.bounds.minY,
                                                             this.db.bounds.maxX);
    
                    final int maxFeatureDensity = 5000;
                    if (numVerts > maxFeatureDensity) {
                        int lodFudge = (int) Math.ceil(Math.log((double) numVerts
                                / (double) maxFeatureDensity)
                                / Math.log(2.0)) + 2;
                        Log.d(TAG, "ADJUST LOD FOR HIGH DENSITY DATASET (lod+" + lodFudge + ")");
                        levelOfDetail += lodFudge;
                    }
    
                    builder.updateSettings(true, OSMUtils.mapnikTileResolution(levelOfDetail), 0d);
                    
                    db.lastUpdate = System.currentTimeMillis();
                    db.maxResolution = 0d;
                    db.minResolution = OSMUtils.mapnikTileResolution(levelOfDetail);
                    
    
                    Log.d(TAG, layer.GetName() + " num vertices=" + numVerts);
    
                    builder.createIndices();

                    Log.d(TAG, layer.GetName() + " created spatial index");
                } catch(Throwable t) {
                    Log.d(TAG, layer.GetName() + " srid=" + layerSrid + " ERROR !");
                    Log.e(TAG, "error", t);
                    if(feature != null) {
                        Log.d(TAG, "feature FID: " + feature.GetFID());
                        Log.d(TAG, "description: " + getDescription(layer, feature));
                        Log.d(TAG, "geometeryWkt: " + feature.GetGeometryRef().ExportToWkt());
                    }
                    return;
                } finally {
                    builder.endBulkInsertion(true);
                }
            } finally {
                if(builder != null)
                    builder.close();
            }

            db.path = layerDbFile.getAbsolutePath();
            db.database = new DatabaseRef(new FeatureDatabase(
                                            layerDbFile,
                                            0,
                                            VISIBILITY_SETTINGS_FEATURESET));
            
            synchronized(WFSFeatureDataStore3.this) {
                Log.d(TAG, layer.GetName() + " completed ingest");
                
                WFSFeatureDataStore3.this.fsidToFeatureDb.put(Long.valueOf(db.fsid), db);
                WFSFeatureDataStore3.this.fsNameToFeatureDb.put(db.name, db);
                
                // dispatch content changed
                WFSFeatureDataStore3.this.dispatchDataStoreContentChangedNoSync();
                
                // update index DB
                StatementIface stmt = null;
                try {
                    stmt = indexDatabase.compileStatement("UPDATE featuredbs SET path = ?, featurecount = ?, minresolution = ?, maxresolution = ?, minx = ?, miny = ?, maxx = ?, maxy = ?, version = ?, lastupdate = ? WHERE fsid = ?");
                    try {
                        stmt.bind(1, db.path);
                        stmt.bind(2, db.numRecords);
                        stmt.bind(3, db.minResolution);
                        stmt.bind(4, db.maxResolution);
                        stmt.bind(5, db.bounds.minX);
                        stmt.bind(6, db.bounds.minY);
                        stmt.bind(7, db.bounds.maxX);
                        stmt.bind(8, db.bounds.maxY);
                        stmt.bind(9, db.version);
                        stmt.bind(10, db.lastUpdate);
                        stmt.bind(11, db.fsid);
                        
                        stmt.execute();
                    } finally {
                        stmt.clearBindings();
                    }
                } finally {
                    if(stmt != null)
                        stmt.close();
                }
            }
        }
        
        Map<Style, String> styleCache = new HashMap<Style, String>();

        private String getStyle(String layer, AttributeSet metadata, org.gdal.ogr.Feature feature) {
            Class<? extends Geometry> geomType = getStyleGeomType(feature.GetGeometryRef());
            if(geomType == null)
                return null;
            return getStyle(layer, metadata, geomType);
        }

        private String getStyle(String layerName, AttributeSet metadata, Class<? extends Geometry> geomType) {
            Style style = schemaHandler.getFeatureStyle(layerName, metadata, geomType);
            String cached = styleCache.get(style);
            if(cached != null)
                return cached;
            cached = FeatureStyleParser.pack(style);
            styleCache.put(style, cached);
            return cached;
        }
    }
    
    private static class DatabaseRef extends ReferenceCount<FeatureDatabase> {

        public DatabaseRef(FeatureDatabase value) {
            super(value);
        }
        
        @Override
        protected void onDereferenced() {
            super.onDereferenced();
            
            this.value.dispose();
        }
    }

    /**************************************************************************/
    
    private static int computeLevelOfDetail(int threshold, double mbrULLat, double mbrULLon,
            double mbrLRLat, double mbrLRLon) {
        // XXX - check both clamped at same edge

        if (mbrULLat > 85.0511)
            mbrULLat = 85.0511;
        else if (mbrULLat < -85.0511)
            mbrULLat = -85.0511;
        if (mbrLRLat > 85.0511)
            mbrLRLat = 85.0511;
        else if (mbrLRLat < -85.0511)
            mbrLRLat = -85.0511;

        double area0 = computeMapnikArea(0, mbrULLat, mbrULLon, mbrLRLat, mbrLRLon);
        if (area0 > threshold)
            return 0;

        if (area0 == 0.0d)
            area0 = 0.0002;

        int level = (int) Math.ceil(Math.log(128.0d / area0) / Math.log(4.0d));
        level = MathUtils.clamp(level, 1, 19);

        double area;
        do {
            area = computeMapnikArea(level, mbrULLat, mbrULLon, mbrLRLat, mbrLRLon);
            while (area >= threshold) {
                area = computeMapnikArea(level - 1, mbrULLat, mbrULLon, mbrLRLat, mbrLRLon);
                if (area < threshold || level == 0)
                    return level;
                level--;
            }
            while (area < threshold) {
                area = computeMapnikArea(++level, mbrULLat, mbrULLon, mbrLRLat, mbrLRLon);
                if (area >= threshold || level > 20)
                    return level;
            }
        } while (true);
    }

    private static double computeMapnikArea(int level, double mbrULLat, double mbrULLon,
            double mbrLRLat, double mbrLRLon) {
        int tileULx = OSMUtils.mapnikTileX(level, mbrULLon);
        int tileULy = OSMUtils.mapnikTileY(level, mbrULLat);
        int tileURx = OSMUtils.mapnikTileX(level, mbrLRLon);
        int tileURy = OSMUtils.mapnikTileY(level, mbrULLat);
        int tileLRx = OSMUtils.mapnikTileX(level, mbrLRLon);
        int tileLRy = OSMUtils.mapnikTileY(level, mbrLRLat);
        int tileLLx = OSMUtils.mapnikTileX(level, mbrULLon);
        int tileLLy = OSMUtils.mapnikTileY(level, mbrLRLat);

        long pxULx = OSMUtils.mapnikPixelX(level, tileULx, mbrULLon) + (tileULx * 256);
        long pxULy = OSMUtils.mapnikPixelY(level, tileULy, mbrULLat) + (tileULy * 256);
        long pxURx = OSMUtils.mapnikPixelX(level, tileURx, mbrLRLon) + (tileURx * 256);
        long pxURy = OSMUtils.mapnikPixelY(level, tileURy, mbrULLat) + (tileURy * 256);
        long pxLRx = OSMUtils.mapnikPixelX(level, tileLRx, mbrLRLon) + (tileLRx * 256);
        long pxLRy = OSMUtils.mapnikPixelY(level, tileLRy, mbrLRLat) + (tileLRy * 256);
        long pxLLx = OSMUtils.mapnikPixelX(level, tileLLx, mbrULLon) + (tileLLx * 256);
        long pxLLy = OSMUtils.mapnikPixelY(level, tileLLy, mbrLRLat) + (tileLLy * 256);

        int upperDx = (int) Math.abs(pxURx - pxULx);
        int upperDy = (int) Math.abs(pxURy - pxULy);
        int rightDx = (int) Math.abs(pxLRx - pxURx);
        int rightDy = (int) Math.abs(pxLRy - pxURy);
        int lowerDx = (int) Math.abs(pxLRx - pxLLx);
        int lowerDy = (int) Math.abs(pxLRy - pxLLy);
        int leftDx = (int) Math.abs(pxLLx - pxLLx);
        int leftDy = (int) Math.abs(pxLRy - pxURy);

        double upperSq = ((upperDx * upperDx) + (upperDy * upperDy));
        double rightSq = ((rightDx * rightDx) + (rightDy * rightDy));
        double lowerSq = ((lowerDx * lowerDx) + (lowerDy * lowerDy));
        double leftSq = ((leftDx * leftDx) + (leftDy * leftDy));

        double diag0sq = ((pxLRx - pxULx) * (pxLRx - pxULx) + (pxLRy - pxULy) * (pxLRy - pxULy));
        double diag1sq = ((pxURx - pxLLx) * (pxURx - pxLLx) + (pxURy - pxLLy) * (pxURy - pxLLy));

        return 0.25 * Math.sqrt((4 * diag0sq * diag1sq) - (rightSq + leftSq - upperSq - lowerSq));
    }
    
    private final static class DistributedFeatureCursorImpl extends RowIteratorWrapper implements FeatureCursor {

        final DatabaseRef db;
        final FeatureCursor filter;
        boolean leaked;

        public DistributedFeatureCursorImpl(FeatureCursor cursor, DatabaseRef db) {
            super(cursor);
            this.filter = cursor;
            this.db = db;
            this.db.reference();
            this.leaked = true;
        }

        @Override
        public Object getRawGeometry() {
            return this.filter.getRawGeometry();
        }

        @Override
        public int getGeomCoding() {
            return this.filter.getGeomCoding();
        }

        @Override
        public String getName() {
            return this.filter.getName();
        }

        @Override
        public int getStyleCoding() {
            return this.filter.getStyleCoding();
        }

        @Override
        public Object getRawStyle() {
            return this.filter.getRawStyle();
        }

        @Override
        public AttributeSet getAttributes() {
            return this.filter.getAttributes();
        }

        @Override
        public Feature get() {
            Feature retval = this.filter.get();
            retval = new Feature(retval.getFeatureSetId(),
                                 (retval.getFeatureSetId()<<32L)|retval.getId(),
                                 retval.getName(),
                                 retval.getGeometry(),
                                 retval.getStyle(),
                                 retval.getAttributes(),
                                 retval.getTimestamp(),
                                 retval.getVersion());
            return retval;
        }

        @Override
        public long getId() {
            return (this.filter.getFsid()<<32L)|this.filter.getId();
        }

        @Override
        public long getVersion() {
            return this.filter.getVersion();
        }
        
        @Override
        public long getFsid() {
            return this.filter.getFsid();
        }
        
        @Override
        public void close() {
            try {
                super.close();
            } finally {
                this.db.dereference();
                this.leaked = false;
            }
        }
        
        @Override
        protected void finalize() throws Throwable {
            if(this.leaked) {
                Log.w(TAG, "cursor leaked");
                this.db.dereference();
            }
            super.finalize();
        }
    }
}
