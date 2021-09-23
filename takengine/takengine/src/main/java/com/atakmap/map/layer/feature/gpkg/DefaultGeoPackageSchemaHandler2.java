package com.atakmap.map.layer.feature.gpkg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.atakmap.content.BindArgument;
import com.atakmap.content.WhereClauseBuilder;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.gpkg.GeoPackageSchemaHandler;
import com.atakmap.map.layer.raster.gpkg.GeoPackageLayerInfoSpi;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.spatial.GeometryTransformer;

public class DefaultGeoPackageSchemaHandler2 implements GeoPackageSchemaHandler {
    // borrowed from GoMobile
    private static final String[] POTENTIAL_TITLE_ATTRIBUTES = { "name", "nam", "title", "feature_id", "featureid", "fcode" };
    
    private final GeoPackage gpkg;

    private Map<String, Layer> layers;

    public DefaultGeoPackageSchemaHandler2(GeoPackage gpkg) {
        this.gpkg = gpkg;

        this.layers = new HashMap<String, Layer>();
        parseLayers(this.gpkg, this.layers);
    }

    @Override
    public void addOnFeatureDefinitionsChangedListener(OnFeatureDefinitionsChangedListener l) {}

    @Override
    public Class<? extends Geometry> getGeometryType(String layerName) {
        Layer layer = this.layers.get(layerName);
        if(layer == null)
            return null;
        return layer.geometryType;
    }

    @Override
    public boolean getDefaultFeatureSetVisibility(String layer, String featureSet) {
        return true;
    }

    @Override
    public Set<String> getLayerFeatureSets(String remote) {
        return Collections.<String>singleton(remote.replaceAll("\\/", "\\/"));
    }

    @Override
    public double getMaxResolution(String layer) {
        return 0;
    }

    @Override
    public double getMinResolution(String name) {
        final Layer layer = this.layers.get(name);
        if(layer == null)
            return 0d;
        return layer.minimumResolution;
    }

    @Override
    public String getSchemaType() {
        // XXX - 
        return "GPKG";
    }

    @Override
    public long getSchemaVersion() {
        // XXX - 
        return 1;
    }

    @Override
    public boolean ignoreFeature(String layer, AttributeSet metadata) {
        // XXX - 
        return false;
    }

    @Override
    public boolean ignoreLayer(String layer) {
        return !this.layers.containsKey(layer);
    }

    @Override
    public boolean isFeatureVisible(String layer, AttributeSet metadata) {
        // XXX - 
        return true;
    }

    @Override
    public boolean isLayerVisible(String layer) {
        // XXX - 
        return true;
    }

    @Override
    public GeoPackageFeatureCursor queryFeatures(String layerName, FeatureQueryParameters params) {
        final Layer layer = this.layers.get(layerName);
        if(layer == null)
            throw new IllegalArgumentException();
        
        return DefaultGeopackageFeatureCursor.query(this.gpkg,
                                                    layer.tableName,
                                                    layer.nameColumn,
                                                    layer.geometryColumn,
                                                    layer.srid,
                                                    layer.rtreeTableName,
                                                    layer.attributeColumns,
                                                    params);
    }

    @Override
    public int queryFeaturesCount(String layerName, FeatureQueryParameters params) {
        final Layer layer = this.layers.get(layerName);
        if(layer == null)
            throw new IllegalArgumentException();
        
        return queryFeaturesCount(this.gpkg,
                                  layer.tableName,
                                  layer.nameColumn,
                                  layer.geometryColumn,
                                  layer.srid,
                                  layer.rtreeTableName,
                                  params);
    }

    @Override
    public void removeOnFeatureDefinitionsChangedListener(OnFeatureDefinitionsChangedListener l) {}

    /**************************************************************************/

    private static void parseLayers(GeoPackage gpkg, Map<String, Layer> layers) {
        final DatabaseIface database = gpkg.getDatabase();
        
        CursorIface result;
        
        // ensure that the geopackage contains features
        result = null;
        try {
            result = database.query("SELECT Count(1) FROM (SELECT 1 FROM gpkg_contents WHERE data_type = \'features\' LIMIT 1)", null);
            if(!result.moveToNext() || result.getInt(0) < 1)
                return;
        } finally {
            if(result != null)
                result.close();
        }

        // parse gpkg_contents for features tables
        result = null;
        try {
            result = database.query("SELECT gpkg_contents.table_name, gpkg_geometry_columns.column_name, gpkg_geometry_columns.geometry_type_name, gpkg_geometry_columns.srs_id, gpkg_contents.identifier FROM gpkg_contents JOIN gpkg_geometry_columns ON gpkg_contents.table_name = gpkg_geometry_columns.table_name WHERE data_type = \'features\'", null);
            while(result.moveToNext()) {
                Layer layer = new Layer();
                layer.tableName = result.getString(0);
                layer.geometryColumn = result.getString(1);
                layer.geometryType = getGeometryTypeClass(result.getString(2));
                layer.srid = GeoPackageLayerInfoSpi.getSRID(gpkg, result.getInt(3));

                layers.put(result.getString(4), layer);
            }
        } finally {
            if(result != null)
                result.close();
        }
        
        for(Layer layer : layers.values()) {
            layer.minimumResolution = estimateDisplayThreshold(gpkg.getDatabase(), layer.tableName, layer.geometryColumn);

            // name column
            layer.nameColumn = guessFeatureNameColumn(database, layer.tableName);
            if(layer.nameColumn == null)
                    layer.nameColumn = "ROWID";
    
            // attribute columns
            Set<String> columns = Databases.getColumnNames(database, layer.tableName);
            if(columns != null)
                layer.attributeColumns = new HashSet<String>(columns);

            if(layer.attributeColumns != null) {
                layer.attributeColumns.remove(layer.geometryColumn);
                layer.attributeColumns.remove(layer.nameColumn);
            }

            layer.projection = ProjectionFactory.getProjection(layer.srid);

            // check for index
            layer.rtreeTableName = getGeometryRtreeTable(database, layer.tableName, layer.geometryColumn);
        }
    }

    private static Class<? extends Geometry> getGeometryTypeClass(String type) {
        return Geometry.class;
    }
    
    private static String getGeometryRtreeTable(DatabaseIface database, String tableName, String columnName) {
        QueryIface query = null;
        try {            
            query = database.compileQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name IN (SELECT 'rtree_' || table_name || '_' || column_name FROM gpkg_extensions WHERE table_name = ? AND column_name = ? AND extension_name = 'gpkg_rtree_index' LIMIT 1)");
            query.bind(1, tableName);
            query.bind(2, columnName);
            
            if(query.moveToNext())
                return query.getString(0);
            
            // XXX - encountered sample files without registered rtree -- do an
            //       explicit lookup of the rtree table if the extension isn't
            //       defined
            query.close();
            query = database.compileQuery("SELECT tbl_name FROM sqlite_master WHERE tbl_name = 'rtree_' || ? || '_' || ? LIMIT 1");
            query.bind(1, tableName);
            query.bind(2, columnName);
            if(!query.moveToNext())
                return null;
            return query.getString(0);
        } finally {
            if(query != null)
                query.close();
        }
    }

    public static double estimateDisplayThreshold(DatabaseIface database, String tableName, String geometryColumn) {
        // XXX - compute a nominal display resolution based on the number of
        //       features in the layer versus its reported extents as reported
        //       in the gpkg_contents table
    
        // XXX - 
        final int targetFeaturesPerTile = 50;
    
        CursorIface result = null;
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT count, extent, Srid(extent) FROM (SELECT Count(1) As count, Extent(GeomFromGPB(");
            sql.append(geometryColumn);
            sql.append(")) As extent FROM ");
            sql.append(tableName);
            sql.append(") LIMIT 1");

            result = database.query(sql.toString(), null);
            if(result.moveToNext()) {
                final int numFeatures = result.getInt(0);
                final byte[] extentBlob = result.getBlob(1); 
                Geometry extent = (extentBlob == null) ? null : GeometryFactory.parseSpatiaLiteBlob(extentBlob);
                final int srid = result.getInt(2);
                if(extent != null && !result.isNull(2) && (ProjectionFactory.getProjection(srid) != null)) {
                    if(srid != 4326)
                        extent = GeometryTransformer.transform(extent, srid, 4326);
                    final Envelope mbb = extent.getEnvelope();
                    final int lodLat = (int)Math.ceil(Math.log(180d/(mbb.maxY-mbb.minY)) / Math.log(2d));
                    final int lodLng = (int)Math.ceil(Math.log(360d/(mbb.maxX-mbb.minX)) / Math.log(2d));
                    final int lod = Math.max(lodLat, lodLng)/2;
                    
                    final int nTiles = (int)Math.ceil((double)numFeatures / targetFeaturesPerTile);
                    final int tileLod = (int)Math.ceil((Math.log(nTiles)/Math.log(2d)));

                    final double minimumDisplayGsd = OSMUtils.mapnikTileResolution(MathUtils.clamp(tileLod+lod, 0, 17));
                    return minimumDisplayGsd;
                } else if(numFeatures > targetFeaturesPerTile) {
                    // do a coarse computation based on total feature count.
                    // this will be refined later based on RTREE queries
                    final int nTiles = (int)Math.ceil((double)numFeatures / targetFeaturesPerTile);
    
                    final double minimumDisplayGsd = OSMUtils.mapnikTileResolution((int)MathUtils.clamp((Math.log(nTiles)/Math.log(2d)), 0, 17));
                    return minimumDisplayGsd;
                }
            }
            
            return Double.MAX_VALUE;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    private static boolean isNameString (String candidate, Collection<String> nearMisses) {
        if (candidate.equals ("name")) {
            return true;
        } else if (candidate.startsWith ("name") || candidate.endsWith ("name")) {
            nearMisses.add (candidate);
        } else {
            for(int i = 0; i < POTENTIAL_TITLE_ATTRIBUTES.length; i++) {
                if(POTENTIAL_TITLE_ATTRIBUTES[i].equalsIgnoreCase(candidate))
                    nearMisses.add(candidate);
            }
        }
        return false;
    }
    
    public static String guessFeatureNameColumn(DatabaseIface database, String table) {
        // Try to find a column name, short name, or title that is literally
        // "name", or begins/ends with "name" and also contains either "feature"
        // or the layer name.
        // Failing all that, accept the first one that begins/ends with "name".
        List<String> namishColumns = new ArrayList<String> ();

        Set<String> textColumns = new HashSet<String>();
        
        CursorIface result = null;
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM ");
            sql.append(table);
            sql.append(" LIMIT 1");
            result = database.query(sql.toString(), null);
            if(!result.moveToNext())
                return null;
            
            for(int i = 0; i < result.getColumnCount(); i++) {
                if(result.getType(i) == CursorIface.FIELD_TYPE_STRING)
                    textColumns.add(result.getColumnName(i));
            }
        } finally {
            if(result != null)
                result.close();
        }
        
        String candidate = null;
        for (String column : textColumns) {
            if (isNameString (column.toLowerCase (LocaleUtil.getCurrent()),
                              namishColumns)) {

                candidate = column;
                break;
            }
        }
        if (candidate == null && !namishColumns.isEmpty ()) {
            candidate = namishColumns.get (0);
        }
        return candidate;
    }
    
    public static int queryFeaturesCount(GeoPackage gpkg, String tableName, String nameColumn, String geometryColumn, int geometrySrid, String rtreeTableName, FeatureQueryParameters params) {
        if(params == null)
            params = new FeatureQueryParameters();

        StringBuilder sql = new StringBuilder();
        LinkedList<BindArgument> args = new LinkedList<BindArgument>();

        // SELECTION
        
        sql.append("SELECT Count(ROWID)");

        // FROM
        sql.append(" FROM ");
        sql.append(tableName);

        // WHERE
        
        WhereClauseBuilder where = new WhereClauseBuilder();

        if(params.visibleOnly) {
            // XXX - 
        }
        if(params.featureIds != null) {
            where.beginCondition();
            where.appendIn(tableName + ".ROWID", params.featureIds.size());
            for(Long fid : params.featureIds)
                where.addArg(fid.longValue());
        }
        if(nameColumn != null && params.featureNames != null) {
            where.beginCondition();
            where.appendIn(tableName + "." + nameColumn, params.featureNames);
        }
        if(params.geometryTypes != null) {
            
        }
        if(!Double.isNaN(params.minResolution) || !Double.isNaN(params.maxResolution)) {
            // not applicable
        }
        if(params.spatialFilter != null) {
            if (params.spatialFilter instanceof FeatureQueryParameters.RadiusSpatialFilter) {
                FeatureQueryParameters.RadiusSpatialFilter radius = (FeatureQueryParameters.RadiusSpatialFilter) params.spatialFilter;

                where.beginCondition();
                where.append("Distance(MakePoint(?, ?, ?), GeomFromGPB(");
                where.append(tableName);
                where.append(".");
                where.append(geometryColumn);
                where.append("), 0) <= ?");

                where.addArg(radius.point.getLongitude());
                where.addArg(radius.point.getLatitude());
                where.addArg(geometrySrid);
                where.addArg(radius.radius);
            } else if (params.spatialFilter instanceof FeatureQueryParameters.RegionSpatialFilter) {
                FeatureQueryParameters.RegionSpatialFilter region = (FeatureQueryParameters.RegionSpatialFilter) params.spatialFilter;

                Projection layerProjection = ProjectionFactory.getProjection(geometrySrid);

                where.beginCondition();
                if (layerProjection != null) { 
                    if (rtreeTableName != null) {
                        where.append(tableName);
                        where.append(".ROWID IN (SELECT ROWID FROM ");
                        where.append(rtreeTableName);
                        where.append(" WHERE ? <= maxX AND ? >= minX AND ? >= minY AND ? <= maxY)");
    
                        PointD ul = layerProjection.forward(region.upperLeft, null);
                        PointD lr = layerProjection.forward(region.lowerRight, null);
    
                        where.addArg(ul.x);
                        where.addArg(lr.x);
                        where.addArg(ul.y);
                        where.addArg(lr.y);
                    } else {
                        where.append("Intersects(BuildMbr(?, ?, ?, ?, ?), GeomFromGPB(");
                        where.append(tableName);
                        where.append(".");
                        where.append(geometryColumn);
                        where.append(")) = 1");
    
                        PointD ul = layerProjection.forward(region.upperLeft, null);
                        PointD lr = layerProjection.forward(region.lowerRight, null);
                        
                        where.addArg(ul.x);
                        where.addArg(ul.y);
                        where.addArg(lr.x);
                        where.addArg(lr.y);
                        where.addArg(geometrySrid);
                    } 
                }
            } else {
                // XXX -
            }
        }
        
        if(where.getSelection() != null) {
            sql.append(" WHERE ");
            sql.append(where.getSelection());
            
            List<BindArgument> whereArgs = where.getBindArgs();
            if(whereArgs != null)
                args.addAll(whereArgs);
        }

        // ORDER
        
        CursorIface result = null;
        try {
            result = BindArgument.query(gpkg.getDatabase(), sql.toString(), args);
            if(!result.moveToNext())
                return 0;
            return result.getInt(0);
        } finally {
            if(result != null)
                result.close();
        }
    }

    /**************************************************************************/
    
    static class Layer {
        public String tableName;
        public String geometryColumn;
        public String nameColumn;
        public Class<? extends Geometry> geometryType;
        public int srid;
        public Projection projection;
        public Set<String> attributeColumns;
        // XXX - dynamic style
        public String rtreeTableName;
        public double minimumResolution;
    }
}
