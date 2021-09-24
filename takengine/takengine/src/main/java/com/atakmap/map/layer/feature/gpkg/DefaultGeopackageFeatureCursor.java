package com.atakmap.map.layer.feature.gpkg;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.atakmap.content.BindArgument;
import com.atakmap.content.WhereClauseBuilder;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.CursorIface;
import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDefinition;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.spatial.GeometryTransformer;

/**
 * <P>The default implementation expects  
 * @author Developer
 */
public final class DefaultGeopackageFeatureCursor extends RowIteratorWrapper implements GeoPackageFeatureCursor {

    private static class StyleSpec {
        public final static StyleSpec POINT = new StyleSpec(Point.class, new IconPointStyle (-1, "asset:/icons/reference_point.png"));
        public final static StyleSpec LINESTRING = new StyleSpec(LineString.class, new BasicStrokeStyle(-1, 2));
        public final static StyleSpec POLYGON = new StyleSpec(Polygon.class, new BasicStrokeStyle(-1, 2));
        
        public final Class<? extends Geometry> clazz;
        public final Style style;
        
        private StyleSpec(Class<? extends Geometry> clazz, Style style) {
            this.clazz = clazz;
            this.style = style;
        }
    }

    private final static StyleSpec[] DEFAULT_STYLES =
        {
            StyleSpec.POINT,
            StyleSpec.LINESTRING,
            StyleSpec.POLYGON,
        };

    private final static AttributeSet EMPTY_ATTRIBS = new AttributeSet();

    /**************************************************************************/
    
    protected CursorIface filter;
    protected RowData row;
    protected final int fidIndex;
    protected final int nameIndex;
    protected final int geometryIndex;
    protected final int geometrySrid;
    protected final int attributesIndex;

    private DefaultGeopackageFeatureCursor(CursorIface impl, int fidIndex, int nameIndex, int geometryIndex, int geomSrid, int attributesIndex) {
        super(impl);
        this.filter = impl;
        
        this.fidIndex = fidIndex;
        this.nameIndex = nameIndex;
        this.geometryIndex = geometryIndex;
        this.geometrySrid = geomSrid;
        this.attributesIndex = attributesIndex;
        
        this.row = new RowData();
    }

    /**************************************************************************/
    // RowIterator
    
    @Override
    public boolean moveToNext() {
        this.row.reset();
        return super.moveToNext();
    }

    @Override
    public void close() {
        this.row.reset();
        super.close();
    }

    /**************************************************************************/
    // GeoPackageFeatureCursor

    @Override
    public AttributeSet getAttributes() {
        if(this.row.attributes != null)
            return this.row.attributes;

        if(this.attributesIndex < 0)
            return EMPTY_ATTRIBS;
        final int numAttributes = this.filter.getInt(this.attributesIndex);
        if(numAttributes < 1)
            return EMPTY_ATTRIBS;
        
        int colIdx = this.attributesIndex+1;

        this.row.attributes = new AttributeSet();
        for(int i = 0; i < numAttributes; i++) {
            final String key = this.filter.getColumnName(colIdx);
            switch(this.filter.getType(colIdx)) {
                case CursorIface.FIELD_TYPE_INTEGER :
                    this.row.attributes.setAttribute(key, this.filter.getLong(colIdx));
                    break;
                case CursorIface.FIELD_TYPE_FLOAT :
                    this.row.attributes.setAttribute(key, this.filter.getDouble(colIdx));
                    break;
                case CursorIface.FIELD_TYPE_STRING :
                    this.row.attributes.setAttribute(key, this.filter.getString(colIdx));
                    break;
                case CursorIface.FIELD_TYPE_BLOB :
                    this.row.attributes.setAttribute(key, this.filter.getBlob(colIdx));
                    break;
                case CursorIface.FIELD_TYPE_NULL :
                    this.row.attributes.setAttribute(key, (String)null);
                    break;
                default :
                    break;
            }
            colIdx++;
        }
        return this.row.attributes;
    }

    @Override
    public Geometry getGeometry() {
        if(this.row.geometry != null)
            return this.row.geometry;
        
        if(this.geometryIndex < 0)
            return null;
        int colIdx = this.geometryIndex;
        final int geomCoding = this.filter.getInt(colIdx++);
        final byte[] blob = this.filter.getBlob(colIdx++);
        if(blob == null)
            return null;
        try {
            switch(geomCoding) {
                case 0 :
                    this.row.geometry = GeopackageBlobParser.parse(blob);
                    break;
                case 1 :
                    this.row.geometry = GeometryFactory.parseSpatiaLiteBlob(blob);
                    break;
                default :
                    throw new UnsupportedOperationException();
            }
        } catch(Throwable t) {
            Log.e("DefaultGeopackageFeatureCursor", "Unexpected error parsing Geopackage geometry", t);
            return null;
        }
        if(this.geometrySrid != -1 && this.row.geometry != null) {
            this.row.geometry = GeometryTransformer.transform(this.row.geometry,
                                                              this.geometrySrid,
                                                              4326);
        }

        return this.row.geometry;
    }

    @Override
    public long getID() {
        if(this.row.fid != FeatureDataStore.FEATURE_ID_NONE)
            return this.row.fid;
        if(this.fidIndex < 0)
            return FeatureDataStore.FEATURE_ID_NONE;
        this.row.fid = this.filter.getLong(this.fidIndex);
        return this.row.fid;
    }

    @Override
    public String getName() {
        if(this.row.name != null)
            return this.row.name;
        if(this.nameIndex < 0)
            return null;
        this.row.name = this.filter.getString(this.nameIndex);
        return this.row.name;
    }

    @Override
    public Style getStyle() {
        if(this.row.style != null)
            return this.row.style;
        this.row.style = getDefaultStyle(this.getGeometry());
        return this.row.style;
    }

    @Override
    public long getVersion() {
        return 1;
    }

    /**************************************************************************/
    
    public static Style getDefaultStyle(Geometry geom) {
        if(geom == null) {
            return null;
        } else if(geom instanceof GeometryCollection) {
            // XXX - I think these geometries should be constrained to one child
            //       type -- check for multipoint, if not, stroke it
            Collection<Geometry> children = ((GeometryCollection)geom).getGeometries();
            for(Geometry child : children) {
                final Style retval = getDefaultStyle(child);
                if(retval != null)
                    return retval;
            }
            return null;
        } else {
            return getDefaultStyle(geom.getClass());
        }
    }
    
    private static Style getDefaultStyle(Class<? extends Geometry> geomClazz) {
        for(int i = 0; i < DEFAULT_STYLES.length; i++)
            if(DEFAULT_STYLES[i].clazz.equals(geomClazz))
                return DEFAULT_STYLES[i].style;
        throw new IllegalArgumentException();
    }
    
    private static double getProjectionUnitsDistance(Projection proj, double distDegrees) {
        PointD left = proj.forward(new GeoPoint(0d, -distDegrees/2d), null);
        PointD right = proj.forward(new GeoPoint(0d, distDegrees/2d), null);
        
        return Math.sqrt((right.x-left.x)*(right.x-left.x) + (right.y-left.y)*(right.y-left.y));
    }

    public static GeoPackageFeatureCursor query(GeoPackage gpkg, String tableName, String nameColumn, String geometryColumn, int geometrySrid, boolean autoAttributes, FeatureQueryParameters params) {
        return query(gpkg, tableName, nameColumn, geometryColumn, geometrySrid, null, autoAttributes, params);
    }

    public static GeoPackageFeatureCursor query(GeoPackage gpkg, String tableName, String nameColumn, String geometryColumn, int geometrySrid, String rtreeIndex, boolean autoAttributes, FeatureQueryParameters params) {
        Set<String> attributeColumns = null;
        if(autoAttributes && ((params == null) || !MathUtils.hasBits(params.ignoredFields, FeatureDataStore.FeatureQueryParameters.FIELD_ATTRIBUTES))) {
            CursorIface result = null;
            try {
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT * FROM ");
                sql.append(tableName);
                sql.append(" LIMIT 1");
                result = gpkg.getDatabase().query(sql.toString(), null);
                if(result.moveToNext()) {
                    attributeColumns = new HashSet<String>();
                    for(int i = 0; i < result.getColumnCount(); i++)
                        attributeColumns.add(result.getColumnName(i));
                }
            } finally {
                if(result != null)
                    result.close();
            }
        }
        
        return query(gpkg, tableName, nameColumn, geometryColumn, geometrySrid, rtreeIndex, attributeColumns, params);
    }
    
    public static GeoPackageFeatureCursor query(GeoPackage gpkg, String tableName, String nameColumn, String geometryColumn, int geometrySrid, String rtreeTableName, Set<String> attributeColumns, FeatureQueryParameters params) {
        if(params == null)
            params = new FeatureQueryParameters();

        final Projection layerProjection = ProjectionFactory.getProjection(geometrySrid);

        int fidIndex = -1;
        int nameIndex = -1;
        int geomIndex = -1;
        int geomSridIndex = -1;
        int generalizedGeomIndex = -1;
        int generalizedGeomIdIndex = -1;
        int styleRulesIndex = -1;
        int attributesIndex = -1;
        
        int nextColumnIndex = 10;

        StringBuilder sql = new StringBuilder();
        LinkedList<BindArgument> args = new LinkedList<BindArgument>();
        LinkedList<String> joins = new LinkedList<String>();

        // SELECTION
        
        sql.append("SELECT ?, ?, ? , ?, ?, ?, ?, ?, ? As query_min_resolution, ? As query_max_resolution");
        args.add(Double.isNaN(params.minResolution) ? new BindArgument() : new BindArgument(params.minResolution));
        args.add(Double.isNaN(params.maxResolution) ? new BindArgument() : new BindArgument(params.maxResolution));
        
        // FID
        {
            sql.append(", ");
            sql.append(tableName);
            sql.append(".ROWID");
            fidIndex = nextColumnIndex++;
        }
        
        // name
        if(nameColumn != null && !MathUtils.hasBits(params.ignoredFields, FeatureQueryParameters.FIELD_NAME)) {
            sql.append(", ");
            sql.append(tableName);
            sql.append(".");
            sql.append(nameColumn);
            nameIndex = nextColumnIndex++;
        }
        
        // geometry
        if(!MathUtils.hasBits(params.ignoredFields, FeatureQueryParameters.FIELD_GEOMETRY)) {
            StringBuilder geom = new StringBuilder();
            geom.append(tableName);
            geom.append(".");
            geom.append(geometryColumn);
            
            int geomCoding = FeatureDefinition.GEOM_WKB;

            geom.insert(0, "GeomFromGPB(");
            geom.append(")");

            if(params.ops != null && layerProjection != null) {
                for (FeatureQueryParameters.SpatialOp op : params.ops) {
                    if (op instanceof FeatureQueryParameters.Simplify) {
                        geom.insert(0, "SimplifyPreserveTopology(");
                        geom.append(", ?)");

                        args.add(new BindArgument(getProjectionUnitsDistance(layerProjection, ((FeatureQueryParameters.Simplify) op).distance)));
                    } else if (op instanceof FeatureQueryParameters.Buffer) {
                        geom.insert(0, "Buffer(");
                        geom.append(", ?)");

                        args.add(new BindArgument(getProjectionUnitsDistance(layerProjection, ((FeatureQueryParameters.Buffer) op).distance)));
                    }
                }
            }
            geometrySrid = -1;
            
            sql.append(", ");
            sql.append(geomCoding);
            sql.append(", ");
            sql.append(geom);
            geomIndex = nextColumnIndex++;
            
            sql.append(", ? As ");
            sql.append(tableName);
            sql.append("_");
            sql.append(geometryColumn);
            sql.append("_srid");
            args.add(new BindArgument(geometrySrid));
            geomSridIndex = nextColumnIndex++;            
        }
        
        // attributes
        if(!MathUtils.hasBits(params.ignoredFields, FeatureQueryParameters.FIELD_ATTRIBUTES)) {
            int columnsUsed = 0;
            
            // num attribute columns
            sql.append(", ");
            if(attributeColumns == null)
                sql.append("0");
            else
                sql.append(attributeColumns.size());
            sql.append(" As attribute_columns_count");
            columnsUsed++;
            
            // attributes
            if(attributeColumns != null) {
                for(String attributeColumn : attributeColumns) {
                    sql.append(", ");
                    sql.append(tableName);
                    sql.append(".");
                    sql.append(attributeColumn);
                    columnsUsed++;
                }
            }
            
            attributesIndex = nextColumnIndex;
            nextColumnIndex += columnsUsed;
        }

        // FROM
        sql.append(" FROM ");
        sql.append(tableName);

        // JOINS
        for(String join : joins) {
            sql.append(" LEFT JOIN ");
            sql.append(join);
        }

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
        
        // LIMIT 
        
        if(params.limit != 0) {
            sql.append(" LIMIT ? OFFSET ?");
            args.add(new BindArgument(params.limit));
            args.add(new BindArgument(params.offset));
        }
        
        // indices will always be first seven args
        args.addFirst(new BindArgument(attributesIndex));
        args.addFirst(new BindArgument(styleRulesIndex));
        args.addFirst(new BindArgument(generalizedGeomIdIndex));
        args.addFirst(new BindArgument(generalizedGeomIndex));
        args.addFirst(new BindArgument(geomSridIndex));
        args.addFirst(new BindArgument(geomIndex));
        args.addFirst(new BindArgument(nameIndex));
        args.addFirst(new BindArgument(fidIndex));
        
        return new DefaultGeopackageFeatureCursor(
                BindArgument.query(gpkg.getDatabase(), sql.toString(), args),
                fidIndex,
                nameIndex,
                geomIndex,
                geometrySrid,
                attributesIndex);
    }

    /**************************************************************************/
    
    protected static class RowData {
        public long fid;
        public String name;
        public Geometry geometry;
        public Style style;
        public AttributeSet attributes;
        
        public RowData() {
            this.reset();
        }
        
        public void reset() {
            this.fid = FeatureDataStore. FEATURE_ID_NONE;
            this.name = null;
            this.geometry = null;
            this.style = null;
            this.attributes = null;
        }
    }

}
