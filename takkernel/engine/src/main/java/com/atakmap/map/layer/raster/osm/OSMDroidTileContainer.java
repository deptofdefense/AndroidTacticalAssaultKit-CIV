package com.atakmap.map.layer.raster.osm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.tilematrix.TileContainer;
import com.atakmap.map.layer.raster.tilematrix.TileContainerSpi;
import com.atakmap.map.layer.raster.tilematrix.TileEncodeException;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.util.Collections2;
import com.atakmap.util.Disposable;

public final class OSMDroidTileContainer implements TileContainer {

    private final static int MAX_NUM_LEVELS = 30;
    
    private final static ZoomLevel[] OSMDROID_ZOOM_LEVELS_3857 = Util.createQuadtree(createLevel0(OSMWebMercator.INSTANCE, 1, 1), MAX_NUM_LEVELS);
    private final static ZoomLevel[] OSMDROID_ZOOM_LEVELS_4326 = Util.createQuadtree(createLevel0(EquirectangularMapProjection.INSTANCE, 2, 1), MAX_NUM_LEVELS);
    
    private final static Envelope BOUNDS_3857 = getMatrixBounds(OSMWebMercator.INSTANCE);
    private final static Envelope BOUNDS_4326 = getMatrixBounds(EquirectangularMapProjection.INSTANCE);
    
    public final static TileContainerSpi SPI = new TileContainerSpi() {
        @Override
        public String getName() {
            return "OSMDroid";
        }

        @Override
        public String getDefaultExtension() {
            return ".sqlite";
        }

        @Override
        public TileContainer create(String name, String path, TileMatrix spec) {
            // verify compatibility
            if(!isCompatible(spec))
                return null;
            
            // since we are creating, if the file exists delete it to overwrite
            File f = new File(path);
            if(IOProviderFactory.exists(f))
                FileSystemUtils.delete(f);
            
            // adopt the name from the spec if not defined
            if(name == null)
                name = spec.getName();

            DatabaseIface db = null;
            try {
                db = IOProviderFactory.createDatabase(new File(path));
                createTables(db, spec.getSRID(), true);
                final TileContainer retval =  new OSMDroidTileContainer(name, spec.getSRID(), db);
                db = null;
                return retval;
            } finally {
                if(db != null)
                    db.close();
            }
        }

        @Override
        public TileContainer open(String path, TileMatrix spec, boolean readOnly) {
            File f = new File(path);
            if(!IOProviderFactory.exists(f))
                return null;
            
            DatabaseIface db = null;
            try {
                db = IOProviderFactory.createDatabase(new File(path), readOnly ? DatabaseInformation.OPTION_READONLY : 0);
                OSMDroidInfo info = OSMDroidInfo.get(db, OSMDroidInfo.BoundsDiscovery.Skip);
                if(info == null)
                    return null;

                // if a spec is specified, verify compatibility
                if(spec != null) {
                    // ensure spec shares same SRID
                    if(info.srid != spec.getSRID())
                        return null;
                    // check compatibility
                    if(!isCompatible(spec))
                        return null;
                }
            
                final TileContainer retval =  new OSMDroidTileContainer(info.provider, info.srid, db);
                db = null;
                return retval;
            } finally {
                if(db != null)
                    db.close();
            }
        }
        
        @Override
        public boolean isCompatible(TileMatrix spec) {
            return OSMDroidTileContainer.isCompatible(spec);
        }
    };
    
    private final String name;
    private final DatabaseIface db;
    private final int srid;
    private final ZoomLevel[] zoomLevels;
    private final boolean hasAtakMetadata;
    private final Envelope bounds;
    
    private long[] precompiledStmtThreadIds;
    private PrecompiledStatements[] precompiledStmts;
    private Map<Long, PrecompiledStatements> precompiledStmtOverflow;

    private OSMDroidTileContainer(String name, int srid, DatabaseIface db) {
        this.name = name;
        switch(srid) {
            case 4326 :
                this.zoomLevels = OSMDROID_ZOOM_LEVELS_4326;
                this.bounds = BOUNDS_4326;
                break;
            case 900913 :
                srid = 3857;
            case 3857 :
                this.zoomLevels = OSMDROID_ZOOM_LEVELS_3857;
                this.bounds = BOUNDS_3857;
                break;
            default :
                throw new IllegalArgumentException();
        }
        this.srid = srid;
        this.db = db;
        
        final Set<String> tableNames = Databases.getTableNames(this.db);
        this.hasAtakMetadata = Collections2.containsIgnoreCase(tableNames, "ATAK_metadata") && Collections2.containsIgnoreCase(tableNames, "ATAK_catalog");
        
        this.precompiledStmts = new PrecompiledStatements[16];
        this.precompiledStmtThreadIds = new long[this.precompiledStmts.length];
        Arrays.fill(this.precompiledStmtThreadIds, -1L);
        this.precompiledStmtOverflow = new HashMap<Long, PrecompiledStatements>();
    }
    
    private PrecompiledStatements getPrecompiledStmts() {
        final long tid = Thread.currentThread().getId();
        final int limit = this.precompiledStmts.length;
        for(int i = 0; i < limit; i++) {
            if(this.precompiledStmtThreadIds[i] < 0L) {
                this.precompiledStmts[i] = new PrecompiledStatements();
                this.precompiledStmtThreadIds[i] = tid;
            } else if(this.precompiledStmtThreadIds[i] != tid) {
                continue;
            }
            
            return this.precompiledStmts[i];
        }
        
        PrecompiledStatements retval = this.precompiledStmtOverflow.get(tid);
        if(retval == null) {
            retval = new PrecompiledStatements();
            this.precompiledStmtOverflow.put(tid, retval);
        }
        return retval;
    }

    @Override
    public boolean hasTileExpirationMetadata() {
        return this.hasAtakMetadata;
    }

    @Override
    public synchronized long getTileExpiration(int level, int x, int y) {
        if(!this.hasAtakMetadata)
            return -1L;
        final PrecompiledStatements stmts = getPrecompiledStmts();
        
        QueryIface result = null;
        try {
            if(stmts.queryTileExpiration == null)
                stmts.queryTileExpiration = this.db.compileQuery("SELECT expiration FROM ATAK_catalog WHERE key = ? LIMIT 1");
            result = stmts.queryTileExpiration;

            result.clearBindings();
            result.bind(1, OSMUtils.getOSMDroidSQLiteIndex(level, x, y));
            if(!result.moveToNext())
                return -1L;
            return result.getLong(0);
        } finally {
            if(result != null) {
                if(stmts != null)
                    result.reset();
                else
                    result.close();
            }
        }
    }

    private boolean hasTile(int level, int x, int y) {
        final PrecompiledStatements stmts = getPrecompiledStmts();
        
        QueryIface result = null;
        try {
            if(stmts.queryTileExists == null)
                stmts.queryTileExists = this.db.compileQuery("SELECT 1 FROM tiles WHERE key = ? LIMIT 1");
            result = stmts.queryTileExists;
            
            result.clearBindings();
            result.bind(1, OSMUtils.getOSMDroidSQLiteIndex(level, x, y));
            return result.moveToNext();
        } finally {
            if(result != null) {
                if(stmts != null)
                    result.reset();
                else
                    result.close();
            }
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getSRID() {
        return this.srid;
    }

    @Override
    public ZoomLevel[] getZoomLevel() {
        return this.zoomLevels;
    }

    @Override
    public double getOriginX() {
        return this.bounds.minX;
    }

    @Override
    public double getOriginY() {
        return this.bounds.minY;
    }

    @Override
    public Bitmap getTile(int zoom, int x, int y, Throwable[] error) {
        byte[] data = this.getTileData(zoom, x, y, error);
        if(data == null)
            return null;
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    @Override
    public synchronized byte[] getTileData(int zoom, int x, int y, Throwable[] error) {
        final PrecompiledStatements stmts = getPrecompiledStmts();
        
        QueryIface result = null;
        try {
            if(stmts.queryTileData == null)
                stmts.queryTileData = this.db.compileQuery("SELECT tile FROM tiles WHERE key = ? LIMIT 1");
            result = stmts.queryTileData;

            result.clearBindings();
            result.bind(1, OSMUtils.getOSMDroidSQLiteIndex(zoom, x, y));
            
            if(!result.moveToNext())
                return null;
            return result.getBlob(0);
        } catch(Throwable t) {
            if(error != null)
                error[0] = t;
            return null;
        } finally {
            if(result != null) {
                if(stmts != null)
                    result.reset();
                else
                    result.close();
            }
        }
    }

    @Override
    public boolean isReadOnly() {
        return this.db.isReadOnly();
    }

    @Override
    public synchronized void setTile(int level, int x, int y, byte[] data, long expiration) {
        if(this.isReadOnly())
            throw new UnsupportedOperationException("TileContainer is read-only");
        
        PrecompiledStatements stmts = getPrecompiledStmts();

        final boolean update = hasTile(level, x, y);

        StatementIface stmt;
        
        stmt = null;
        try {
            if(update) {
                if(stmts.updateTile == null)
                    stmts.updateTile = this.db.compileStatement("UPDATE tiles SET provider = ?, tile = ? WHERE key = ?");
                stmt = stmts.updateTile;
            } else {
                if(stmts.insertTile == null)
                    stmts.insertTile = this.db.compileStatement("INSERT INTO tiles (provider, tile, key) VALUES(?, ?, ?)");
                stmt = stmts.insertTile;
            }
            stmt.bind(1, this.name);
            stmt.bind(2, data);
            stmt.bind(3, OSMUtils.getOSMDroidSQLiteIndex(level, x, y));
            
            stmt.execute();
        } finally {
            if(stmt != null) {
                if(stmts != null)
                    stmt.clearBindings();
                else
                    stmt.close();
            }
        }
        
        if(this.hasAtakMetadata) {
            stmt = null;
            try {
                if(update) {
                    if(stmts.updateExpiration == null)
                        stmts.updateExpiration = this.db.compileStatement("UPDATE ATAK_catalog SET expiration = ? WHERE key = ?");
                    stmt = stmts.updateExpiration;
                } else {
                    if(stmts.insertExpiration == null)
                        stmts.insertExpiration = this.db.compileStatement("INSERT INTO ATAK_catalog (expiration, key) VALUES(?, ?)");
                    stmt = stmts.insertExpiration;
                }
                stmt.bind(1, expiration);
                stmt.bind(2, OSMUtils.getOSMDroidSQLiteIndex(level, x, y));
                
                stmt.execute();
            } finally {
                if(stmt != null) {
                    if(stmts != null)
                        stmt.clearBindings();
                    else
                        stmt.close();
                }
            }
        }
    }

    @Override
    public void setTile(int level, int x, int y, Bitmap data, long expiration) throws TileEncodeException {
        // convert bitmap to byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int)(data.getWidth()*data.getHeight()*4*0.5));
        if(!data.compress(data.hasAlpha() ? CompressFormat.PNG : CompressFormat.JPEG, 75, bos))
            throw new TileEncodeException();
        setTile(level, x, y, bos.toByteArray(), expiration);
    }

    @Override
    public Envelope getBounds() {
        return this.bounds;
    }

    @Override
    public void dispose() {
        this.db.close();
        
        for(int i = 0; i < this.precompiledStmts.length; i++) {
            if(this.precompiledStmts[i] == null)
                break;
            this.precompiledStmts[i].dispose();
            this.precompiledStmts[i] = null;
        }
        Arrays.fill(this.precompiledStmtThreadIds, -1L);
        for(PrecompiledStatements stmt : this.precompiledStmtOverflow.values())
            stmt.dispose();
        this.precompiledStmtOverflow.clear();
    }

    /**************************************************************************/

    private static Envelope getMatrixBounds(Projection proj) {
        PointD ul = proj.forward(new GeoPoint(proj.getMaxLatitude(), proj.getMinLongitude()), null);
        PointD ur = proj.forward(new GeoPoint(proj.getMaxLatitude(), proj.getMaxLongitude()), null);
        PointD lr = proj.forward(new GeoPoint(proj.getMinLatitude(), proj.getMaxLongitude()), null);
        PointD ll = proj.forward(new GeoPoint(proj.getMinLatitude(), proj.getMinLongitude()), null);
        
        return new Envelope(MathUtils.min(ul.x, ur.x, lr.x, ll.x),
                            MathUtils.min(ul.y, ur.y, lr.y, ll.y),
                            0d,
                            MathUtils.max(ul.x, ur.x, lr.x, ll.x),
                            MathUtils.max(ul.y, ur.y, lr.y, ll.y),
                            0);
    }

    private static TileMatrix.ZoomLevel createLevel0(Projection proj, int gridCols, int gridRows) {
        PointD upperLeft = proj.forward(new GeoPoint(proj.getMaxLatitude(), proj.getMinLongitude()), null);
        PointD lowerRight = proj.forward(new GeoPoint(proj.getMinLatitude(), proj.getMaxLongitude()), null);
        
        // XXX - better resolution for 4326???

        TileMatrix.ZoomLevel retval = new ZoomLevel();
        retval.level = 0;
        retval.resolution = OSMUtils.mapnikTileResolution(retval.level);
        retval.tileWidth = 256;
        retval.tileHeight = 256;
        retval.pixelSizeX = (lowerRight.x-upperLeft.x) / (retval.tileWidth*gridCols);
        retval.pixelSizeY = (upperLeft.y-lowerRight.y) / (retval.tileHeight*gridRows);
        return retval;
    }

    public static boolean isCompatibleSchema(DatabaseIface db, boolean atakMetadata) {
        Map<String, Collection<String>> schema = new HashMap<String, Collection<String>>();
        schema.put("tiles", Arrays.asList("key", "provider", "tile"));
        if(atakMetadata) {
            schema.put("ATAK_metadata", Arrays.asList("key", "value"));
            schema.put("ATAK_catalog", Arrays.asList("key", "access", "expiration", "size"));
        }
        
        return Databases.matchesSchema(db, schema, false);        
    }
    
    public static void createTables(DatabaseIface db, int srid, boolean atakMetadataAlways) {
        Set<String> tables = Databases.getTableNames(db);
        if (!tables.contains("tiles")) {
            db.execute("CREATE TABLE tiles (key INTEGER PRIMARY KEY, provider TEXT, tile BLOB)", null);
        }
        if(srid < 0)
            srid = 3857;
        if((srid != 3857) || atakMetadataAlways) {
            if (!tables.contains("ATAK_catalog")) {
                db.execute("CREATE TABLE ATAK_catalog (key INTEGER PRIMARY KEY, access INTEGER, expiration INTEGER, size INTEGER)", null);
            }
            if (!tables.contains("ATAK_metadata")) {
                db.execute("CREATE TABLE ATAK_metadata (key TEXT, value TEXT)", null);
                if(srid >= 0) {
                    StatementIface stmt = null;
                    try {
                        stmt = db.compileStatement("INSERT INTO ATAK_metadata (key, value) VALUES(?, ?)");
                        stmt.bind(1, "srid");
                        stmt.bind(2, srid);
                        stmt.execute();
                    } finally {
                        if(stmt != null)
                            stmt.close();
                    }
                }
            }
        }
    }
    
    public static boolean isCompatible(TileMatrix spec) {
        // verify compatible SRID and origin
        TileMatrix.ZoomLevel[] zoomLevels;
        Envelope bnds;
        switch(spec.getSRID()) {
            case 4326 :
                zoomLevels = OSMDROID_ZOOM_LEVELS_4326;
                bnds = BOUNDS_4326;
                break;
            case 900913 :
            case 3857 :
                zoomLevels = OSMDROID_ZOOM_LEVELS_3857;
                bnds = BOUNDS_3857;
                break;
            default :
                return false;
        }
        if(spec.getOriginX() != bnds.minX || spec.getOriginY() != bnds.maxY)
            return false;
        
        // check compatibility of tiling
        TileMatrix.ZoomLevel[] specLevels = spec.getZoomLevel();
        final int limit = zoomLevels.length-1;
        for(int i = 0; i < specLevels.length; i++) {
            // check for out of bounds level
            if(specLevels[i].level < 0 || specLevels[i].level > limit)
                return false;
            
            // NOTE: resolution is only 'informative' so we aren't going to
            // check it
            TileMatrix.ZoomLevel level = zoomLevels[specLevels[i].level];
            if(specLevels[i].pixelSizeX != level.pixelSizeX ||
               specLevels[i].pixelSizeY != level.pixelSizeY ||
               specLevels[i].tileWidth != level.tileWidth ||
               specLevels[i].tileHeight != level.tileHeight) {

                return false;
            }
        }
        
        return true;
    }
    
    public static TileContainer openOrCreate(String path, String provider, int srid) {
        File f = new File(path);
        if(IOProviderFactory.exists(f)) {
            TileContainer retval = SPI.open(path, null, false);
            if(retval != null && retval.getSRID() == srid)
                return retval;
            
            FileSystemUtils.delete(f);
        }
        
        DatabaseIface db = null;
        try {
            db = IOProviderFactory.createDatabase(new File(path));
            createTables(db, srid, true);
            final TileContainer retval =  new OSMDroidTileContainer(provider, srid, db);
            db = null;
            return retval;
        } finally {
            if(db != null)
                db.close();
        }
    }
    
    
    private static class PrecompiledStatements implements Disposable {
        QueryIface queryTileExpiration;
        QueryIface queryTileData;
        QueryIface queryTileExists;
        StatementIface insertTile;
        StatementIface updateTile;
        StatementIface insertExpiration;
        StatementIface updateExpiration;

        @Override
        public void dispose() {
            if(queryTileExpiration != null)
                queryTileExpiration.close();
            if(queryTileData != null)
                queryTileData.close();
            if(queryTileExists != null)
                queryTileExists.close();
            if(insertTile != null)
                insertTile.close();
            if(updateTile != null)
                updateTile.close();
            if(insertExpiration != null)
                insertExpiration.close();
            if(updateExpiration != null)
                updateExpiration.close();
        }
    }
}
