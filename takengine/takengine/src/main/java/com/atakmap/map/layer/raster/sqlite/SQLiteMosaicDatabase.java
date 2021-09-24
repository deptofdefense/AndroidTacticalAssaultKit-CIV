package com.atakmap.map.layer.raster.sqlite;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.database.sqlite.SQLiteException;
import android.graphics.Point;

import com.atakmap.content.BindArgument;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MultiplexingMosaicDatabaseCursor2;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;

public abstract class SQLiteMosaicDatabase implements MosaicDatabase2 {

    private final static String TAG = "SQLiteMosaicDatabase";

    protected final static QueryParameters EMPTY_PARAMS = new QueryParameters();
    protected final static MosaicDatabase2.Cursor EMPTY_CURSOR = Cursor.EMPTY;

    protected File file;
    private Map<String, TileTableSpec> tiles;
    private Coverage coverage;
    protected DatabaseIface database;

    private final String type;

    protected SQLiteMosaicDatabase(String type) {
        this.type = type;
    }

    @Override
    public final String getType() {
        return this.type;
    }

    protected DatabaseIface openImpl(File f) {
        return Databases.openDatabase(f.getAbsolutePath(), true);
    }
    
    /**
     * 
     * @param type
     * @return
     */
    public synchronized int getSrid(String type) {
        if(this.tiles == null)
            return -1;
        final TileTableSpec spec = this.tiles.get(type);
        if(spec == null)
            return -1;
        return spec.srid;
    }
    

    /**
     * Provided a Map, construct a list of files[contents] and the associated 
     * TileTableSpecs for the Mosaic database.
     */
    protected abstract void parseTileTables(Map<String, TileTableSpec> tables);

    @Override
    public synchronized void open(File f) {
        if(this.database != null)
            throw new IllegalStateException();
        
        this.database = this.openImpl(f);
        if(this.database == null)
            throw new IllegalArgumentException();
        this.file = f;
        
        this.tiles = new HashMap<String, TileTableSpec>();
        this.parseTileTables(this.tiles);
        if(this.tiles.isEmpty())
            throw new IllegalArgumentException();
        
        double minRes = Double.NaN;
        double maxRes = Double.NaN;
        GeometryCollection bounds = new GeometryCollection(2);
        for(Map.Entry<String, TileTableSpec> entry : this.tiles.entrySet()) {
            TileTableSpec spec = entry.getValue();
            spec.type = entry.getKey();
            spec.mbb = spec.coverage.geometry.getEnvelope();
            if(Double.isNaN(minRes) || spec.coverage.minGSD > minRes)
                minRes = spec.coverage.minGSD;
            if(Double.isNaN(maxRes) || spec.coverage.maxGSD < maxRes)
                maxRes = spec.coverage.maxGSD;
            bounds.addGeometry(spec.coverage.geometry);
        }
        
        this.coverage = new Coverage(bounds, minRes, maxRes);
    }

    protected void closeImpl() {
        this.database.close();
    }

    @Override
    public synchronized void close() {
        if(this.database != null) {
            this.closeImpl();
            this.database = null;
            this.file = null;
            this.tiles.clear();
            this.coverage = null;
        }
    }

    @Override
    public final Coverage getCoverage() {
        return this.coverage;
    }

    @Override
    public final synchronized void getCoverages(Map<String, Coverage> coverages) {
        for(Map.Entry<String, TileTableSpec> entry : this.tiles.entrySet()) {
            coverages.put(entry.getKey(), entry.getValue().coverage);
        }
    }

    @Override
    public final synchronized Coverage getCoverage(String type) {
        final TileTableSpec spec = this.tiles.get(type);
        if(spec != null)
            return spec.coverage;
        return null;
    }

    protected String getTileUriScheme() {
        return "sqlite";
    }

    @Override
    public final MosaicDatabase2.Cursor query(QueryParameters params) {

        if (tiles == null) {
             Log.e(TAG, "cannot query a MosaicDatabase that failed to open", new IllegalArgumentException("tiles == null"));
             return EMPTY_CURSOR;
        }

        if(params == null)
            params = EMPTY_PARAMS;

        java.util.Set<TileTableSpec> tables = new java.util.HashSet<TileTableSpec>();
        if(params.types == null) {
            tables.addAll(this.tiles.values());
        } else {
            for(String type : params.types) {
                TileTableSpec spec = tiles.get(type);
                if(spec != null)
                    tables.add(spec);
            }
        }

        if(params.precisionImagery != null && params.precisionImagery.booleanValue())
            tables.clear();

        List<MosaicDatabase2.Cursor> retval = new ArrayList<MosaicDatabase2.Cursor>(tables.size());
        for(TileTableSpec spec : tables)
            retval.add(querySpecImpl(spec, params));
        if(retval.size() == 1)
            return retval.get(0);
        return new MultiplexingMosaicDatabaseCursor2(retval, params.order);
    }
    
    private MosaicDatabase2.Cursor querySpecImpl(TileTableSpec spec, QueryParameters params) {
        if(params == null)
            params = EMPTY_PARAMS;

        int minTileZIdx = 0;
        int maxTileZIdx = spec.zoomLevels.length-1;

        boolean whereZoomLevel = false;

        // compute max query Z
        if(!Double.isNaN(params.maxGsd)) {
            // the lowest resolution level exceeds the requested GSD limit,
            // return an empty cursor 
            if(spec.zoomLevels[0].resolution < params.maxGsd)
                return EMPTY_CURSOR;

            // XXX - fudge factor

            // find greatest level that is >= 'params.maxGsd' (check above
            // prevents possible AIOOBE)
            while(spec.zoomLevels[maxTileZIdx].resolution < (params.maxGsd*2d) && maxTileZIdx > 0)
                maxTileZIdx--;
            
            whereZoomLevel |= (maxTileZIdx < (spec.zoomLevels.length - 1));
        }

        // compute min query Z
        if(!Double.isNaN(params.minGsd)) {
            // the greatest resolution level exceeds the requested GSD limit,
            // return an empty cursor 
            if(spec.zoomLevels[maxTileZIdx].resolution > params.minGsd)
                return EMPTY_CURSOR;

            // find least level that is <= 'params.minGsd' (check above
            // prevents possible AIOOBE)
            while(spec.zoomLevels[minTileZIdx].resolution > params.minGsd)
                minTileZIdx++;
            
            whereZoomLevel |= (minTileZIdx > 0);
        }

        if(minTileZIdx > maxTileZIdx) {
            return EMPTY_CURSOR;
        }


       

        List<QuerySpec> queries = new ArrayList<QuerySpec>(maxTileZIdx-minTileZIdx+1);
        if(params.spatialFilter == null) {
            if(whereZoomLevel) {
                this.queryImpl(queries, spec, minTileZIdx, maxTileZIdx);
            } else {
                this.queryImpl(queries, spec);
            }
        } else {
            // compute intersection AOI with bounds in projected coordinate space
            Envelope aoi = params.spatialFilter.getEnvelope();
            if(Rectangle.intersects(aoi.minX,
                                    aoi.minY,
                                    aoi.maxX,
                                    aoi.maxY,
                                    spec.mbb.minX,
                                    spec.mbb.minY,
                                    spec.mbb.maxX,
                                    spec.mbb.maxY)) {
                
                double aoiIsectULLat = Math.min(aoi.maxY, spec.mbb.maxY);
                double aoiIsectULLng = Math.max(aoi.minX, spec.mbb.minX);
                double aoiIsectLRLat = Math.max(aoi.minY, spec.mbb.minY);
                double aoiIsectLRLng = Math.min(aoi.maxX, spec.mbb.maxX);
                
                PointD ul = spec.proj.forward(new GeoPoint(aoiIsectULLat, aoiIsectULLng), null);
                PointD ur = spec.proj.forward(new GeoPoint(aoiIsectULLat, aoiIsectLRLng), null);
                PointD lr = spec.proj.forward(new GeoPoint(aoiIsectLRLat, aoiIsectLRLng), null);
                PointD ll = spec.proj.forward(new GeoPoint(aoiIsectLRLat, aoiIsectULLng), null);
                        
                double aoiIsectMinX = MathUtils.min(ul.x, ur.x, lr.x, ll.x);
                double aoiIsectMinY = MathUtils.min(ul.y, ur.y, lr.y, ll.y);
                double aoiIsectMaxX = MathUtils.max(ul.x, ur.x, lr.x, ll.x);
                double aoiIsectMaxY = MathUtils.max(ul.y, ur.y, lr.y, ll.y);
                
                for(int zLevelIdx = maxTileZIdx; zLevelIdx >= minTileZIdx; zLevelIdx--) {
                    // compute bounding X,Y @ max Z
                    Point minTile = TileMatrix.Util.getTileIndex(
                            spec.origin.x,
                            spec.origin.y,
                            spec.zoomLevels[zLevelIdx],
                            aoiIsectMinX,
                            aoiIsectMaxY);
                    Point maxTile = TileMatrix.Util.getTileIndex(
                            spec.origin.x,
                            spec.origin.y,
                            spec.zoomLevels[zLevelIdx],
                            aoiIsectMaxX,
                            aoiIsectMinY);

                    queryImpl(queries, spec, zLevelIdx, minTile.x, minTile.y, maxTile.x, maxTile.y);
                }
            }
        }

        // if requested in ascending GSD order, reverse the list
        if(params.order == QueryParameters.Order.MaxGsdAsc || params.order == QueryParameters.Order.MinGsdAsc)
            Collections.reverse(queries);

        if(queries.size() == 1)
            return queries.get(0).query();
        else
            return new QueryIterCursor(queries.iterator());

    }

    protected abstract void queryImpl(List<QuerySpec> queries, TileTableSpec spec);
    protected abstract void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int minZLevelIdx, int maxZLevelIdx);
    protected abstract void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int zLevelIdx, int minTileX, int minTileY, int maxTileX, int maxTileY);
    protected abstract void buildQueryForTile(StringBuilder sql, TileTableSpec tile, int tileX, int tileY, int tileZ);

    /**************************************************************************/
    
    protected static class TileTableSpec {
        /** the name of the table containing the tiles */
        public final String name;
        /** the SRID of the tiles */ 
        public final int srid;

        public final Projection proj;
        
        public final PointD origin;
        
        /** zoom level information */
        public TileMatrix.ZoomLevel[] zoomLevels;
        /** coverage of the tiles */
        public Coverage coverage;
        public Envelope mbb;
        /** the imagery type to be associated with the tiles */ 
        public String type;

        public TileTableSpec(String tableName, int srid, PointD origin) {
            tableName = "\"" + tableName + "\"";

            this.name = tableName;
            this.srid = srid;
            this.proj = MobileImageryRasterLayer2.getProjection(this.srid);
            
            if(origin == null)
                origin = this.proj.forward(new GeoPoint(this.proj.getMaxLatitude(), this.proj.getMinLongitude()), null);
            
            this.origin = origin;
        }
    }
    
    private class MosaicCursorImpl extends CursorWrapper implements MosaicDatabase2.Cursor {

        private final TileTableSpec spec;
        private final int idCol;
        private final int zoomLevelCol;
        private final int tileColumnCol;
        private final int tileRowCol;
        private final int minZIdx;
        private final int maxZIdx;
        
        private RowData rowData;

        public MosaicCursorImpl(CursorIface cursor, TileTableSpec spec, int idCol, int zoomLevelCol, int tileColumnCol, int tileRowCol, int minZIdx, int maxZIdx) {
            super(cursor);
            
            this.spec = spec;
            this.idCol = idCol;
            this.zoomLevelCol = zoomLevelCol;
            this.tileColumnCol = tileColumnCol;
            this.tileRowCol = tileRowCol;            
            this.minZIdx = minZIdx;
            this.maxZIdx = maxZIdx;
            
            this.rowData = new RowData();
            
            if(minZIdx > maxZIdx)
                throw new IllegalArgumentException();
        }

        private int getZoomLevelIdx() {
            // XXX - brute force
            if(this.maxZIdx == this.minZIdx) {
                return this.maxZIdx;
            } else {
                final int zoomLevel = this.getInt(this.zoomLevelCol); 
                for(int i = maxZIdx; i >= minZIdx; i--) {
                    if(zoomLevel == this.spec.zoomLevels[i].level)
                        return i;
                }
                return -1;
            }
        }
        
        @Override
        public boolean moveToNext() {
            final boolean retval;
            try {
                retval = super.moveToNext();
            } catch(Throwable t) {
                Log.e(TAG, "Unexpected error occurred iterating cursor, discarding results.", t);
                return false;
            }
            if(retval) {
                // capture data for current row
                final int zLevelIdx = getZoomLevelIdx();
                final TileMatrix.ZoomLevel zoomLevel = this.spec.zoomLevels[zLevelIdx];
                
                // capture data for current row
                this.rowData.zoomLevelIdx = zLevelIdx;
                
                this.rowData.id = this.getInt(idCol);
                final int tx = this.getInt(tileColumnCol);
                final int ty = this.getInt(tileRowCol);
                
                // upper-left
                this.rowData.scratch.x = spec.origin.x + (tx*zoomLevel.tileWidth*zoomLevel.pixelSizeX);
                this.rowData.scratch.y = spec.origin.y - (ty*zoomLevel.tileHeight*zoomLevel.pixelSizeY);
                spec.proj.inverse(rowData.scratch, rowData.ul);
                // upper-right
                this.rowData.scratch.x = spec.origin.x + ((tx+1)*zoomLevel.tileWidth*zoomLevel.pixelSizeX);;
                this.rowData.scratch.y = spec.origin.y - (ty*zoomLevel.tileHeight*zoomLevel.pixelSizeY);
                spec.proj.inverse(rowData.scratch, rowData.ur);
                // lower-right
                this.rowData.scratch.x = spec.origin.x + ((tx+1)*zoomLevel.tileWidth*zoomLevel.pixelSizeX);;
                this.rowData.scratch.y = spec.origin.y - ((ty+1)*zoomLevel.tileHeight*zoomLevel.pixelSizeY);
                spec.proj.inverse(rowData.scratch, rowData.lr);
                // lower-left
                this.rowData.scratch.x = spec.origin.x + (tx*zoomLevel.tileWidth*zoomLevel.pixelSizeX);;
                this.rowData.scratch.y = spec.origin.y - ((ty+1)*zoomLevel.tileHeight*zoomLevel.pixelSizeY);
                spec.proj.inverse(rowData.scratch, rowData.ll);
            }
            return retval;
        }

        @Override
        public GeoPoint getUpperLeft() {
            return this.rowData.ul; 
        }

        @Override
        public GeoPoint getUpperRight() {
            return this.rowData.ur; 
        }

        @Override
        public GeoPoint getLowerRight() {
            return this.rowData.lr; 
        }

        @Override
        public GeoPoint getLowerLeft() {
            return this.rowData.ll;
        }

        @Override
        public double getMinLat() {
            return this.rowData.lr.getLatitude();
        }

        @Override
        public double getMinLon() {
            return this.rowData.ul.getLongitude();
        }

        @Override
        public double getMaxLat() {
            return this.rowData.ul.getLatitude();
        }

        @Override
        public double getMaxLon() {
            return this.rowData.lr.getLongitude();
        }

        @Override
        public String getPath() {
            final int tileX = this.getInt(this.tileColumnCol);
            final int tileY = this.getInt(this.tileRowCol);
            final int tileZ = this.getInt(this.zoomLevelCol);

            StringBuilder query = new StringBuilder();
            SQLiteMosaicDatabase.this.buildQueryForTile(query,
                                                        this.spec,
                                                        tileX,
                                                        tileY,
                                                        tileZ);
            
            return SQLiteSingleTileReader.generateTileUri(
                    SQLiteMosaicDatabase.this.getTileUriScheme(),
                    SQLiteMosaicDatabase.this.file,
                    query.toString(),
                    this.getWidth(),
                    this.getHeight());
        }

        @Override
        public String getType() {
            return this.spec.type;
        }

        @Override
        public double getMinGSD() {
            return this.getMaxGSD();
        }

        @Override
        public double getMaxGSD() {
            return this.spec.zoomLevels[this.rowData.zoomLevelIdx].resolution;
        }

        @Override
        public int getWidth() {
            return this.spec.zoomLevels[this.rowData.zoomLevelIdx].tileWidth;
        }

        @Override
        public int getHeight() {
            return this.spec.zoomLevels[this.rowData.zoomLevelIdx].tileHeight;
        }

        @Override
        public int getId() {
            return this.rowData.id;
        }

        @Override
        public int getSrid() {
            return this.spec.srid;
        }

        @Override
        public boolean isPrecisionImagery() {
            return false;
        }

        @Override
        public Frame asFrame() {
            return new MosaicDatabase2.Frame(this.getId(),
                                             this.getPath(),
                                             this.getType(),
                                             this.isPrecisionImagery(),
                                             this.getMinLat(),
                                             this.getMinLon(),
                                             this.getMaxLat(),
                                             this.getMaxLon(),
                                             new GeoPoint(this.getUpperLeft()),
                                             new GeoPoint(this.getUpperRight()),
                                             new GeoPoint(this.getLowerRight()),
                                             new GeoPoint(this.getLowerLeft()),
                                             this.getMinGSD(),
                                             this.getMaxGSD(),
                                             this.getWidth(),
                                             this.getHeight(),
                                             this.getSrid());
        }
    }
    
    protected static class RowData {
        public int id;
        public int zoomLevelIdx;
        public GeoPoint ul;
        public GeoPoint ur;
        public GeoPoint lr;
        public GeoPoint ll;

        public PointD scratch;

        public RowData() {
            this.id = 0;
            this.zoomLevelIdx = -1;
            this.ul = GeoPoint.createMutable();
            this.ur = GeoPoint.createMutable();
            this.lr = GeoPoint.createMutable();
            this.ll = GeoPoint.createMutable();
            
            this.scratch = new PointD(0d, 0d);
        }
    }
    
    protected class QuerySpec {
        private final TileTableSpec spec;
        private final int idCol;
        private final int zoomLevelCol;
        private final int tileColumnCol;
        private final int tileRowCol;
        private final int minZLevelIdx;
        private final int maxZLevelIdx;
        private final String sql;
        private Collection<BindArgument> args;

        public QuerySpec(String sql, Collection<BindArgument> args, TileTableSpec spec, int idCol, int zoomLevelCol, int tileColumnCol, int tileRowCol, int zLevelIdx) {
            this(sql, args, spec, idCol, zoomLevelCol, tileColumnCol, tileRowCol, zLevelIdx, zLevelIdx);
            
        }

        public QuerySpec(String sql, Collection<BindArgument> args, TileTableSpec spec, int idCol, int zoomLevelCol, int tileColumnCol, int tileRowCol, int minZLevelIdx, int maxZLevelIdx) {
            this.sql = sql;
            this.args = args;
            this.spec = spec;
            this.idCol = idCol;
            this.zoomLevelCol = zoomLevelCol;
            this.tileColumnCol = tileColumnCol;
            this.tileRowCol = tileRowCol;
            this.minZLevelIdx = minZLevelIdx;
            this.maxZLevelIdx = maxZLevelIdx;
            
            if(this.minZLevelIdx > this.maxZLevelIdx)
                throw new IllegalArgumentException();
        }
        
        public MosaicDatabase2.Cursor query() {
            CursorIface result = null;
            try {
                result = BindArgument.query(SQLiteMosaicDatabase.this.database,
                                            this.sql,
                                            this.args);
                final MosaicDatabase2.Cursor retval =
                        new MosaicCursorImpl(result,
                                                 this.spec,
                                                 this.idCol,
                                                 this.zoomLevelCol,
                                                 this.tileColumnCol,
                                                 this.tileRowCol,
                                                 this.minZLevelIdx,
                                                 this.maxZLevelIdx);
                result = null;
                return retval;
            } finally {
                if(result != null)
                    result.close();
            }
        }
    }
    
    private static class QueryIterCursor implements MosaicDatabase2.Cursor {

        private Iterator<QuerySpec> queries;
        private MosaicDatabase2.Cursor impl;

        public QueryIterCursor(Iterator<QuerySpec> queries) {
            this.queries = queries;
            this.impl = null;
        }

        @Override
        public boolean moveToNext() {
            while(true) {
                if(this.impl != null) {
                    if(this.impl.isClosed()) {
                        return false;
                    } else if(this.impl.moveToNext()) {
                        return true;
                    } else {
                        this.impl.close();
                        this.impl = null;
                        continue;
                    }
                } else if(this.queries.hasNext()) {
                    try {
                        this.impl = this.queries.next().query();
                    } catch(SQLiteException e) {
                        Log.e("SQLiteMosaicDatabase$QueryIterCursor", "Unexpected SQL error occured generating tile query", e);
                    }
                } else {
                    return false;
                }
            }
        }

        @Override
        public void close() {
            if(this.impl != null)
                this.impl.close();
        }

        @Override
        public boolean isClosed() {
            return (this.impl != null && this.impl.isClosed());
        }

        @Override
        public GeoPoint getUpperLeft() {
            return this.impl.getUpperLeft();
        }

        @Override
        public GeoPoint getUpperRight() {
            return this.impl.getUpperRight();
        }

        @Override
        public GeoPoint getLowerRight() {
            return this.impl.getLowerRight();
        }

        @Override
        public GeoPoint getLowerLeft() {
            return this.impl.getLowerLeft();
        }

        @Override
        public double getMinLat() {
            return this.impl.getMinLat();
        }

        @Override
        public double getMinLon() {
            return this.impl.getMinLon();
        }

        @Override
        public double getMaxLat() {
            return this.impl.getMaxLat();
        }

        @Override
        public double getMaxLon() {
            return this.impl.getMaxLon();
        }

        @Override
        public String getPath() {
            return this.impl.getPath();
        }

        @Override
        public String getType() {
            return this.impl.getType();
        }

        @Override
        public double getMinGSD() {
            return this.impl.getMinGSD();
        }

        @Override
        public double getMaxGSD() {
            return this.impl.getMaxGSD();
        }

        @Override
        public int getWidth() {
            return this.impl.getWidth();
        }

        @Override
        public int getHeight() {
            return this.impl.getHeight();
        }

        @Override
        public int getId() {
            return this.impl.getId();
        }

        @Override
        public int getSrid() {
            return this.impl.getSrid();
        }

        @Override
        public boolean isPrecisionImagery() {
            return this.impl.isPrecisionImagery();
        }

        @Override
        public Frame asFrame() {
            return this.impl.asFrame();
        }
    }
}
