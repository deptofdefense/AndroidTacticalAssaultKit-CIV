package com.atakmap.map.layer.raster.mbtiles;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.atakmap.content.BindArgument;
import com.atakmap.content.WhereClauseBuilder;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.DatabaseIface;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseSpi2;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.osm.OSMWebMercator;
import com.atakmap.map.layer.raster.sqlite.SQLiteMosaicDatabase;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;

public class MBTilesMosaicDatabase extends SQLiteMosaicDatabase {

    public final static String TYPE = "mbtiles";

    public final static MosaicDatabaseSpi2 SPI = new MosaicDatabaseSpi2() {
        @Override
        public String getName() {
            return TYPE;
        }

        @Override
        public MosaicDatabase2 createInstance() {
            return new MBTilesMosaicDatabase();
        }
    };

    private MBTilesInfo mbtiles;

    public MBTilesMosaicDatabase() {
        super(TYPE);
    }

    @Override
    protected DatabaseIface openImpl(File file) {
        DatabaseIface[] ref = new DatabaseIface[1];
        this.mbtiles = MBTilesInfo.get(file.getAbsolutePath(), ref);
        return ref[0];
    }

    @Override
    protected void parseTileTables(Map<String, TileTableSpec> tables) {
        TileTableSpec spec = new TileTableSpec(
                "tiles",
                3857,
                OSMWebMercator.INSTANCE.forward(
                        new GeoPoint(
                                OSMWebMercator.INSTANCE.getMaxLatitude(),
                                OSMWebMercator.INSTANCE.getMinLongitude()),
                        null));
        
        final int numZoomLevels = (mbtiles.maxLevel-mbtiles.minLevel+1);
        
        TileMatrix.ZoomLevel zLevelMin = new TileMatrix.ZoomLevel();
        zLevelMin.level = mbtiles.minLevel;
        zLevelMin.pixelSizeX = OSMUtils.mapnikTileResolution(zLevelMin.level);
        zLevelMin.pixelSizeY = OSMUtils.mapnikTileResolution(zLevelMin.level);
        zLevelMin.resolution = OSMUtils.mapnikTileResolution(zLevelMin.level);
        zLevelMin.tileWidth = mbtiles.tileWidth;
        zLevelMin.tileHeight = mbtiles.tileHeight;
        
        spec.zoomLevels = TileMatrix.Util.createQuadtree(zLevelMin, numZoomLevels);
        
        final int numTileRows = (1<<spec.zoomLevels[0].level);
        
        final double south = OSMUtils.mapnikTileLat(mbtiles.minLevel, (numTileRows-1) - mbtiles.minLevelGridMinY + 1);
        final double west = OSMUtils.mapnikTileLng(mbtiles.minLevel, mbtiles.minLevelGridMinX);
        final double north = OSMUtils.mapnikTileLat(mbtiles.minLevel, (numTileRows-1) - mbtiles.minLevelGridMaxY);
        final double east = OSMUtils.mapnikTileLng(mbtiles.minLevel, mbtiles.minLevelGridMaxX + 1);
        
        final GeoPoint covUL = new GeoPoint(north, west); 
        final GeoPoint covUR = new GeoPoint(north, east);
        final GeoPoint covLR = new GeoPoint(south, east);
        final GeoPoint covLL = new GeoPoint(south, west);
        
        spec.coverage = new Coverage(DatasetDescriptor.createSimpleCoverage(covUL, covUR, covLR, covLL),
                                     spec.zoomLevels[0].resolution,
                                     spec.zoomLevels[spec.zoomLevels.length-1].resolution);
        
        tables.put(file.getName(), spec);
    }

    @Override
    protected String getTileUriScheme() {
        if(this.mbtiles.hasTileAlpha)
            return "mbtiles";
        else
            return super.getTileUriScheme();
    }
    
    @Override
    protected void queryImpl(List<QuerySpec> queries, TileTableSpec spec) {
        this.queryImpl(queries,  spec, 0, spec.zoomLevels.length-1);
    }

    @Override
    protected void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int minZLevelIdx, int maxZLevelIdx) {
        for(int i = 0; i < (maxZLevelIdx-minZLevelIdx+1); i++) {
            queryImpl(queries, spec, maxZLevelIdx-i);
        }
    }

    private void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int zLevelIdx) {
        final int numTileRows = (1<<spec.zoomLevels[zLevelIdx].level);
        
        StringBuilder sql = new StringBuilder();
        ArrayList<BindArgument> args = new ArrayList<BindArgument>(10);

        sql.append("SELECT ROWID, zoom_level AS z, tile_column AS x, (? - tile_row) AS y FROM tiles");
        // Y
        args.add(new BindArgument(numTileRows-1));
        
        WhereClauseBuilder where = new WhereClauseBuilder();
        where.beginCondition();
        where.append("zoom_level = ?");
        where.addArg(spec.zoomLevels[zLevelIdx].level);

        sql.append(" WHERE ");
        sql.append(where.getSelection());
        args.addAll(where.getBindArgs());

        queries.add(new QuerySpec(sql.toString(), args, spec, 0, 1, 2, 3, zLevelIdx));
    }
    
    @Override
    protected void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int zLevelIdx, int minTileX, int minTileY, int maxTileX, int maxTileY) {
        StringBuilder sql = new StringBuilder();
        ArrayList<BindArgument> args = new ArrayList<BindArgument>(10);

        final int numTileRows = (1<<spec.zoomLevels[zLevelIdx].level);
        
        sql.append("SELECT ROWID, zoom_level AS z, tile_column AS x, (? - tile_row) AS y FROM tiles");
        // Y
        args.add(new BindArgument(numTileRows-1));
        
        WhereClauseBuilder where = new WhereClauseBuilder();
        where.beginCondition();
        where.append("zoom_level = ?");
        where.addArg(spec.zoomLevels[zLevelIdx].level);
        
        where.beginCondition();
        where.append("tile_column >= ?");
        where.addArg(minTileX);
        
        where.beginCondition();
        where.append("tile_column <= ?");
        where.addArg(maxTileX);
        
        where.beginCondition();
        where.append("(?-tile_row) >= ?");
        where.addArg(numTileRows-1);
        where.addArg(minTileY);
        
        where.beginCondition();
        where.append("(?-tile_row) <= ?");
        where.addArg(numTileRows-1);
        where.addArg(maxTileY);

        sql.append(" WHERE ");
        sql.append(where.getSelection());
        args.addAll(where.getBindArgs());

        queries.add(new QuerySpec(sql.toString(), args, spec, 0, 1, 2, 3, zLevelIdx));
    }

    @Override
    protected void buildQueryForTile(StringBuilder sql, TileTableSpec tile, int tileX, int tileY, int tileZ) {
        // Y origin is inverted for MBtiles
        for(int i = 0; i < tile.zoomLevels.length; i++) {
            if(tile.zoomLevels[i].level == tileZ) {
                final int numTileRows = (1<<tile.zoomLevels[i].level);
                tileY = numTileRows-tileY-1;
                break;
            }
        }

        sql.append("SELECT tile_data");
        if(this.mbtiles.hasTileAlpha)
            sql.append(", tile_alpha");
        sql.append(" FROM tiles WHERE zoom_level = ");
        sql.append(tileZ);
        sql.append(" AND tile_column = ");
        sql.append(tileX);
        sql.append(" AND tile_row = ");
        sql.append(tileY);
        sql.append(" LIMIT 1");
    }
}
