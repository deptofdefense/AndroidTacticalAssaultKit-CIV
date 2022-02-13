package com.atakmap.map.layer.raster.gpkg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.atakmap.content.BindArgument;
import com.atakmap.content.WhereClauseBuilder;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.DatabaseIface;
import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.map.gpkg.TileTable;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseSpi2;
import com.atakmap.map.layer.raster.sqlite.SQLiteMosaicDatabase;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.PointD;

public class GeoPackageMosaicDatabase extends SQLiteMosaicDatabase {

    public final static String TYPE = "gpkg";

    public final static MosaicDatabaseSpi2 SPI = new MosaicDatabaseSpi2() {
        @Override
        public String getName() {
            return TYPE;
        }

        @Override
        public MosaicDatabase2 createInstance() {
            return new GeoPackageMosaicDatabase();
        }
    };

    private GeoPackage gpkg;

    public GeoPackageMosaicDatabase() {
        super(TYPE);
    }
    
    @Override
    protected DatabaseIface openImpl(File f) {
        this.gpkg = new GeoPackage(f);
        return this.gpkg.getDatabase();
    }

    @Override
    protected void closeImpl() {
        if(this.gpkg != null) {
            this.gpkg.close();
            this.gpkg = null;
        }
    }

    @Override
    protected void parseTileTables(Map<String, TileTableSpec> tables) {
        double minRes = Double.NaN;
        double maxRes = Double.NaN;
        GeometryCollection bounds = new GeometryCollection(2);
        for(GeoPackage.ContentsRow content : this.gpkg.getPackageContents()) {
            if(content.data_type == GeoPackage.TableType.TILES) {
                TileTableSpec spec = createTileTableSpec(gpkg, content);

                // if spec is null, we were unable to create a tile table spec from the gpkg and the underlying 
                // content, just go ahead and skip it

                if (spec != null) {  
                    tables.put(this.file.getName() + "[" + content.table_name + "]", spec);
                    if(Double.isNaN(minRes) || spec.coverage.minGSD > minRes)
                        minRes = spec.coverage.minGSD;
                    if(Double.isNaN(maxRes) || spec.coverage.maxGSD < maxRes)
                        maxRes = spec.coverage.maxGSD;
                    bounds.addGeometry(spec.coverage.geometry);
                }
            }
        }

        if(tables.size() == 1) {
            TileTableSpec spec = tables.values().iterator().next();
            tables.clear();
            tables.put(this.file.getName(), spec);
        }
    }

    private static TileTableSpec createTileTableSpec(GeoPackage gpkg, GeoPackage.ContentsRow contents) {
        final String tableName = contents.table_name;
        
        TileTable tiles = gpkg.getTileTable(tableName);


        // something is severely messed up, just go ahead and give up for now.
        // the caller will in turn give up as well.
        if (tiles == null)
              return null;

        TileTable.TileMatrixSet matrix = tiles.getTileMatrixSetInfo();


        // something is severely messed up, just go ahead and give up for now.
        // the caller will in turn give up as well.
        if (matrix  == null)
              return null;


        final int srid = GeoPackageLayerInfoSpi.getSRID(gpkg, matrix);

        TileTableSpec retval = new TileTableSpec(tableName, srid, new PointD(matrix.min_x, matrix.max_y));
        final Projection projection = ProjectionFactory.getProjection(srid);
        
        // bounds discovery
        final GeoPoint ul = GeoPoint.createMutable();
        final GeoPoint ur = GeoPoint.createMutable();
        final GeoPoint lr = GeoPoint.createMutable();
        final GeoPoint ll = GeoPoint.createMutable();
        
        projection.inverse(new PointD(matrix.min_x, matrix.max_y), ul); 
        projection.inverse(new PointD(matrix.max_x, matrix.max_y), ur);
        projection.inverse(new PointD(matrix.max_x, matrix.min_y), lr);
        projection.inverse(new PointD(matrix.min_x, matrix.min_y), ll);

        // XXX - can compute this more rigorously by examining the projected
        //       extents
        final boolean pixelSizeIsDeg = (srid==4326);

        int[] levelIndices = tiles.getZoomLevels();
        retval.zoomLevels = new TileMatrix.ZoomLevel[levelIndices.length];
        for(int i = 0; i < levelIndices.length; i++) {
            TileTable.ZoomLevelRow zoomLevel = tiles.getInfoForZoomLevel(levelIndices[i]);

            // something is severely messed up, just go ahead and give up for now.
            // the caller will in turn give up as well.
            if (zoomLevel == null)
                 return null;
            
            retval.zoomLevels[i] = new TileMatrix.ZoomLevel();
            retval.zoomLevels[i].level = zoomLevel.zoom_level;
            retval.zoomLevels[i].pixelSizeX = zoomLevel.pixel_x_size;
            retval.zoomLevels[i].pixelSizeY = zoomLevel.pixel_y_size;
            retval.zoomLevels[i].tileWidth = zoomLevel.tile_width;
            retval.zoomLevels[i].tileHeight = zoomLevel.tile_height;
            if(pixelSizeIsDeg) {
                retval.zoomLevels[i].resolution = zoomLevel.pixel_x_size * 111319d;
            } else {
                retval.zoomLevels[i].resolution = Math.sqrt(zoomLevel.pixel_x_size*zoomLevel.pixel_y_size);
            }
        }            

        // now that the zoom levels have been created, check the reported
        // coverage from the contents table as that may be more accurate with
        // respect to what tiles are actually present and use that to construct
        // the coverage that the mosaicdb will return
        if(contents.min_x != null && contents.min_y != null && contents.max_x != null && contents.max_y != null) {
            projection.inverse(new PointD(contents.min_x, contents.max_y), ul); 
            projection.inverse(new PointD(contents.max_x, contents.max_y), ur);
            projection.inverse(new PointD(contents.max_x, contents.min_y), lr);
            projection.inverse(new PointD(contents.min_x, contents.min_y), ll);
        }
        
        retval.coverage = new Coverage(DatasetDescriptor.createSimpleCoverage(ul, ur, lr, ll),
                                     retval.zoomLevels[0].resolution,
                                     retval.zoomLevels[retval.zoomLevels.length-1].resolution);
        
        return retval;
    }

    @Override
    protected void queryImpl(List<QuerySpec> queries, TileTableSpec spec) {
        StringBuilder sql = new StringBuilder();
        ArrayList<BindArgument> args = new ArrayList<BindArgument>(10);

        sql.append("SELECT id, zoom_level, tile_column, tile_row FROM ");
        sql.append(spec.name);

        queries.add(new QuerySpec(sql.toString(), args, spec, 0, 1, 2, 3, 0, spec.zoomLevels.length-1));
    }

    @Override
    protected void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int minZLevelIdx, int maxZLevelIdx) {
        StringBuilder sql = new StringBuilder();
        ArrayList<BindArgument> args = new ArrayList<BindArgument>(10);

        sql.append("SELECT id, zoom_level, tile_column, tile_row FROM ");
        sql.append(spec.name);

        WhereClauseBuilder where = new WhereClauseBuilder();
        where.beginCondition();
        where.append("zoom_level >= ?");
        where.addArg(spec.zoomLevels[minZLevelIdx].level);
        
        where.beginCondition();
        where.append("zoom_level <= ?");
        where.addArg(spec.zoomLevels[maxZLevelIdx].level);

        sql.append(" WHERE ");
        sql.append(where.getSelection());
        args.addAll(where.getBindArgs());

        sql.append(" ORDER BY zoom_level DESC");

        queries.add(new QuerySpec(sql.toString(), args, spec, 0, 1, 2, 3, minZLevelIdx, maxZLevelIdx));
    }
    
    @Override
    protected void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int zLevelIdx, int minTileX, int minTileY, int maxTileX, int maxTileY) {
        StringBuilder sql = new StringBuilder();
        ArrayList<BindArgument> args = new ArrayList<BindArgument>(10);

        sql.append("SELECT id, zoom_level, tile_column, tile_row FROM ");
        sql.append(spec.name);

        WhereClauseBuilder where = new WhereClauseBuilder();
        where.beginCondition();
        where.append("zoom_level = ?");
        where.addArg(spec.zoomLevels[zLevelIdx].level);

        // min tile column
        where.beginCondition();
        where.append("tile_column >= ?");
        where.addArg(minTileX);
        // max tile column
        where.beginCondition();
        where.append("tile_column <= ?");
        where.addArg(maxTileX);
        // min tile column
        where.beginCondition();
        where.append("tile_row >= ?");
        where.addArg(minTileY);
        // max tile column
        where.beginCondition();
        where.append("tile_row <= ?");
        where.addArg(maxTileY);

        sql.append(" WHERE ");
        sql.append(where.getSelection());
        args.addAll(where.getBindArgs());

        queries.add(new QuerySpec(sql.toString(), args, spec, 0, 1, 2, 3, zLevelIdx));
    }

    @Override
    protected void buildQueryForTile(StringBuilder sql, TileTableSpec tile, int tileX, int tileY, int tileZ) {
        sql.append("SELECT tile_data FROM ");
        sql.append(tile.name);
        sql.append(" WHERE zoom_level=");
        sql.append(tileZ);
        sql.append(" AND tile_column=");
        sql.append(tileX);
        sql.append(" AND tile_row=");
        sql.append(tileY);
        sql.append(" LIMIT 1");
    }
}
