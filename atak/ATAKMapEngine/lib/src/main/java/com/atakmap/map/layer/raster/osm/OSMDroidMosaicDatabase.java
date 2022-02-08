package com.atakmap.map.layer.raster.osm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.atakmap.content.BindArgument;
import com.atakmap.content.WhereClauseBuilder;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.DatabaseIface;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseSpi2;
import com.atakmap.map.layer.raster.sqlite.SQLiteMosaicDatabase;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.WebMercatorProjection;

public class OSMDroidMosaicDatabase extends SQLiteMosaicDatabase {

    public final static String TYPE = "osmdroid";

    public final static MosaicDatabaseSpi2 SPI = new MosaicDatabaseSpi2() {
        @Override
        public String getName() {
            return TYPE;
        }

        @Override
        public MosaicDatabase2 createInstance() {
            return new OSMDroidMosaicDatabase();
        }
    };

    private OSMDroidInfo osm;

    public OSMDroidMosaicDatabase() {
        super(TYPE);
    }

    @Override
    protected DatabaseIface openImpl(File file) {
        DatabaseIface[] ref = new DatabaseIface[1];
        this.osm = OSMDroidInfo.get(file.getAbsolutePath(), OSMDroidInfo.BoundsDiscovery.Skip, ref);
        return ref[0];
    }

    @Override
    protected void parseTileTables(Map<String, TileTableSpec> tables) {
        tables.put(osm.provider, createTileTableSpec(osm));
    }

    private static TileTableSpec createTileTableSpec(OSMDroidInfo osm) {
        final int srid = osm.srid;

        Projection proj = MobileImageryRasterLayer2.getProjection(srid);
        TileTableSpec retval = new TileTableSpec(
                "tiles",
                srid,
                proj.forward(
                        new GeoPoint(
                                proj.getMaxLatitude(),
                                proj.getMinLongitude()),
                        null));
        
        final int numZoomLevels = (osm.maxLevel-osm.minLevel+1);
        
        TileMatrix.ZoomLevel zLevelMin = new TileMatrix.ZoomLevel();
        zLevelMin.level = osm.minLevel;
        if(srid == 4326) {
            zLevelMin.pixelSizeX = 180d / ((double)(1<<zLevelMin.level) * osm.tileWidth);
            zLevelMin.pixelSizeY = 180d / ((double)(1<<zLevelMin.level) * osm.tileHeight);
        } else {
            zLevelMin.pixelSizeX = OSMUtils.mapnikTileResolution(zLevelMin.level);
            zLevelMin.pixelSizeY = OSMUtils.mapnikTileResolution(zLevelMin.level);
        }
        zLevelMin.resolution = OSMUtils.mapnikTileResolution(zLevelMin.level);
        zLevelMin.tileWidth = osm.tileWidth;
        zLevelMin.tileHeight = osm.tileHeight;
        
        retval.zoomLevels = TileMatrix.Util.createQuadtree(zLevelMin, numZoomLevels);
        
        // bounds discovery
        final double south;
        final double west;
        final double north;
        final double east;
        if (srid == WebMercatorProjection.INSTANCE.getSpatialReferenceID()) {
            south = OSMUtils.mapnikTileLat(osm.minLevel, osm.minLevelGridMaxY + 1);
            west = OSMUtils.mapnikTileLng(osm.minLevel, osm.minLevelGridMinX);
            north = OSMUtils.mapnikTileLat(osm.minLevel, osm.minLevelGridMinY);
            east = OSMUtils.mapnikTileLng(osm.minLevel, osm.minLevelGridMaxX + 1);
        } else {
            final double tileSize = (180.0d / (1 << osm.minLevel));
            south = 90.0d - (tileSize * (osm.minLevelGridMaxY + 1));
            west = -180.0d + (tileSize * osm.minLevelGridMinX);
            north = 90.0d - (tileSize * osm.minLevelGridMinY);
            east = -180.0d + (tileSize * (osm.minLevelGridMaxX + 1));
        }
        
        final GeoPoint covUL = new GeoPoint(north, west); 
        final GeoPoint covUR = new GeoPoint(north, east);
        final GeoPoint covLR = new GeoPoint(south, east);
        final GeoPoint covLL = new GeoPoint(south, west);
        
        retval.coverage = new Coverage(DatasetDescriptor.createSimpleCoverage(covUL, covUR, covLR, covLL),
                                     retval.zoomLevels[0].resolution,
                                     retval.zoomLevels[retval.zoomLevels.length-1].resolution);
        
        return retval;
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
        StringBuilder sql = new StringBuilder();
        ArrayList<BindArgument> args = new ArrayList<BindArgument>(10);

        sql.append("SELECT key, ? AS z, ((key >> ?) - (? << ?)) AS x, (? & key) AS y FROM tiles");
        // Z
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        // X
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        // Y
        args.add(new BindArgument(~(0xFFFFFFFFFFFFFFFFL << (long)spec.zoomLevels[zLevelIdx].level)));
        
        WhereClauseBuilder where = new WhereClauseBuilder();
        where.beginCondition();
        where.append("key >= ?");
        where.addArg(OSMUtils.getOSMDroidSQLiteMinIndex(spec.zoomLevels[zLevelIdx].level));
        
        where.beginCondition();
        where.append("key < ?");
        where.addArg(OSMUtils.getOSMDroidSQLiteMinIndex(spec.zoomLevels[zLevelIdx].level+1));

        sql.append(" WHERE ");
        sql.append(where.getSelection());
        args.addAll(where.getBindArgs());

        queries.add(new QuerySpec(sql.toString(), args, spec, 0, 1, 2, 3, zLevelIdx));
    }
    
    @Override
    protected void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int zLevelIdx, int minTileX, int minTileY, int maxTileX, int maxTileY) {
        for(int i = 0; i < (maxTileX-minTileX+1); i++) {
            queryImpl(queries, spec, zLevelIdx, minTileX+i, minTileY, maxTileY);
        }
    }

    private void queryImpl(List<QuerySpec> queries, TileTableSpec spec, int zLevelIdx, int tileX, int minTileY, int maxTileY) {
        StringBuilder sql = new StringBuilder();
        ArrayList<BindArgument> args = new ArrayList<BindArgument>(10);

        sql.append("SELECT key, ? AS z, ((key >> ?) - (? << ?)) AS x, (? & key) AS y FROM tiles");
        // Z
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        // X
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        args.add(new BindArgument(spec.zoomLevels[zLevelIdx].level));
        // Y
        args.add(new BindArgument(~(0xFFFFFFFFFFFFFFFFL << (long)spec.zoomLevels[zLevelIdx].level)));
        
        WhereClauseBuilder where = new WhereClauseBuilder();
        where.beginCondition();
        where.append("key >= ?");
        where.addArg(OSMUtils.getOSMDroidSQLiteIndex(spec.zoomLevels[zLevelIdx].level, tileX, minTileY));
        
        where.beginCondition();
        where.append("key <= ?");
        where.addArg(OSMUtils.getOSMDroidSQLiteIndex(spec.zoomLevels[zLevelIdx].level, tileX, maxTileY));

        sql.append(" WHERE ");
        sql.append(where.getSelection());
        args.addAll(where.getBindArgs());

        queries.add(new QuerySpec(sql.toString(), args, spec, 0, 1, 2, 3, zLevelIdx));
    }

    @Override
    protected void buildQueryForTile(StringBuilder sql, TileTableSpec tile, int tileX, int tileY, int tileZ) {
        sql.append("SELECT tile FROM tiles WHERE key=");
        sql.append(OSMUtils.getOSMDroidSQLiteIndex(tileZ, tileX, tileY));
        sql.append(" LIMIT 1");
    }
}
