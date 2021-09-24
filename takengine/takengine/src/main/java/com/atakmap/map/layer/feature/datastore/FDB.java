package com.atakmap.map.layer.feature.datastore;

import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.atakmap.content.BindArgument;
import com.atakmap.content.WhereClauseBuilder;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.IteratorCursor;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.AbstractFeatureDataStore2;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters.Order;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.cursor.BruteForceLimitOffsetFeatureCursor;
import com.atakmap.map.layer.feature.cursor.MultiplexingFeatureCursor;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.math.MathUtils;
import com.atakmap.util.Disposable;
import com.atakmap.util.StringIgnoreCaseComparator;

abstract class FDB extends AbstractFeatureDataStore2 {

    public static final String ABS_TAG = "FeatureDatabase";

    private final static Map<Class<?>, Integer> ATTRIB_TYPES = new HashMap<Class<?>, Integer>();
    static {
        ATTRIB_TYPES.put(Integer.TYPE, 0);
        ATTRIB_TYPES.put(Long.TYPE, 1);
        ATTRIB_TYPES.put(Double.TYPE, 2);
        ATTRIB_TYPES.put(String.class, 3);
        ATTRIB_TYPES.put(byte[].class, 4);
        ATTRIB_TYPES.put(AttributeSet.class, 5);
        ATTRIB_TYPES.put(int[].class, 6);
        ATTRIB_TYPES.put(long[].class, 7);
        ATTRIB_TYPES.put(double[].class, 8);
        ATTRIB_TYPES.put(String[].class, 9);
        ATTRIB_TYPES.put(byte[][].class, 10);        
    }
    
    protected final static int DEFAULT_VISIBILITY_FLAGS =
            VISIBILITY_SETTINGS_FEATURESET |
            VISIBILITY_SETTINGS_FEATURE;
    
    private final static int DATABASE_VERSION = 2;

    final String databaseFile;

    final boolean spatialIndexEnabled;

    DatabaseIface database;

    private static class FeatureSetDefn {
        boolean visible;
        int visibleVersion;
        boolean visibleCheck;
        int minLod;
        int maxLod;
        int lodVersion;
        boolean lodCheck;
        int nameVersion;
        long fsid;
        String name;
        String type;
        String provider;
    }
    
    Map<Long, FeatureSetDefn> featureSets;
    boolean infoDirty;
    boolean visible;
    boolean visibleCheck;
    int minLod;
    int maxLod;
    boolean lodCheck;
    
    final Map<Long, AttributeSpec> idToAttrSchema;
    final Map<String, AttributeSpec> keyToAttrSchema;
    boolean attrSchemaDirty;
    
    protected FDB(File db, int modificationFlags, int visibilityFlags) {
        this(db,
             true,
             modificationFlags,
             visibilityFlags);
    }

    protected FDB(File dbFile,
                  boolean buildIndices,
                  int modificationFlags,
                  int visibilityFlags) {

        super(modificationFlags, visibilityFlags);

        this.databaseFile = dbFile.getAbsolutePath();
        if(!IOProviderFactory.exists(dbFile) || IOProviderFactory.length(dbFile) == 0L) {
            this.database = IOProviderFactory.createDatabase(
                new DatabaseInformation(Uri.fromFile(dbFile)));

            this.buildTables(buildIndices);
            this.spatialIndexEnabled = buildIndices;
        } else {
            this.database = IOProviderFactory.createDatabase(
                new DatabaseInformation(Uri.fromFile(dbFile)));

            // test for spatial index
            this.spatialIndexEnabled = (Databases.getTableNames(this.database).contains("idx_features_geometry"));
        }

        this.idToAttrSchema = new HashMap<Long, AttributeSpec>();
        this.keyToAttrSchema = new HashMap<String, AttributeSpec>();
        
        this.attrSchemaDirty = true;

        this.featureSets = new HashMap<Long, FeatureSetDefn>();

        this.refresh();
    }
    
    private void buildTables(boolean indices) {
        CursorIface result;

        final int major = FeatureSpatialDatabase.getSpatialiteMajorVersion(this.database);
        final int minor = FeatureSpatialDatabase.getSpatialiteMinorVersion(this.database);

        final String initSpatialMetadataSql;
        if (major > 4 || (major == 4 && minor >= 1))
            initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
        else
            initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

        result = null;
        try {
            result = this.database.query(initSpatialMetadataSql, null);
            result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }

        this.database.execute("CREATE TABLE featuresets" +
                              "    (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                              "     name TEXT," +
                              "     name_version INTEGER," +
                              "     visible INTEGER," +
                              "     visible_version INTEGER," +
                              "     visible_check INTEGER," +
                              "     min_lod INTEGER," +
                              "     max_lod INTEGER," +
                              "     lod_version INTEGER," +
                              "     lod_check INTEGER," +
                              "     type TEXT," +
                              "     provider TEXT)",
                              null);

        this.database.execute("CREATE TABLE features" +
                              "    (fid INTEGER PRIMARY KEY AUTOINCREMENT," +
                              "     fsid INTEGER," +
                              "     name TEXT COLLATE NOCASE," +
                              "     style_id INTEGER," +
                              "     attribs_id INTEGER," +
                              "     version INTEGER," +
                              "     visible INTEGER," +
                              "     visible_version INTEGER," +
                              "     min_lod INTEGER," +
                              "     max_lod INTEGER," +
                              "     lod_version INTEGER)",
                              null);
        
        result = null;
        try {
            result = this.database.query(
                    "SELECT AddGeometryColumn(\'features\', \'geometry\', 4326, \'GEOMETRY\', \'XY\')",
                    null);
            result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }

        this.database.execute("CREATE TABLE styles" +
                              "    (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                              "     coding TEXT," +
                              "     value BLOB)",
                              null);

        this.database.execute("CREATE TABLE attributes" +
                              "    (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                              "     value BLOB)",
                              null);

        this.database.execute("CREATE TABLE attribs_schema" +
                              "    (id INTEGER PRIMARY KEY AUTOINCREMENT," +
                              "     name TEXT," +
                              "     coding INTEGER)",
                              null);

        this.createTriggersNoSync();
        if(indices)
            this.createIndicesNoSync();

        this.database.setVersion(DATABASE_VERSION);
    }
    
    private void createIndicesNoSync() {
        this.database
                .execute(
                        "CREATE INDEX IF NOT EXISTS IdxFeaturesLevelOfDetail ON features(min_lod, max_lod)",
                        null);
        this.database.execute(
                "CREATE INDEX IF NOT EXISTS IdxFeaturesName ON features(name)",
                null);

        CursorIface result = null;
        try {
            result = this.database.query("SELECT CreateSpatialIndex(\'features\', \'geometry\')", null);
            result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }
    }

    private void dropIndicesNoSync() {
        this.database.execute("DROP INDEX IF EXISTS IdxFeaturesLevelOfDetail", null);
        this.database.execute("DROP INDEX IF EXISTS IdxFeaturesGroupIdName", null);

        if (Databases.getTableNames(this.database).contains("idx_features_geometry")) {
            CursorIface result = null;
            try {
                result = this.database.query("SELECT DisableSpatialIndex(\'features\', \'geometry\')",
                        null);
                result.moveToNext();
            } finally {
                if (result != null)
                    result.close();
            }

            this.database.execute("DROP TABLE idx_features_geometry", null);
        }
    }
    
    private void createTriggersNoSync() {
        this.database.execute("CREATE TRIGGER IF NOT EXISTS features_visible_update AFTER UPDATE OF visible ON features " +
                              "BEGIN " +
                              "UPDATE featuresets SET visible_check = 1; " + 
                              "UPDATE features SET visible_version = (SELECT visible_version FROM featuresets WHERE id = OLD.fsid LIMIT 1) WHERE fid = OLD.fid; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS featuresets_visible_update AFTER UPDATE OF visible ON featuresets " +
                              "BEGIN " +
                              "UPDATE featuresets SET visible_version = (OLD.visible_version+1), visible_check = 0 WHERE id = OLD.id; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS features_min_lod_update AFTER UPDATE OF min_lod ON features " +
                              "BEGIN " +
                              "UPDATE featuresets SET lod_check = 1; " + 
                              "UPDATE features SET lod_version = (SELECT lod_version FROM featuresets WHERE id = OLD.fsid LIMIT 1) WHERE fid = OLD.fid; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS features_max_lod_update AFTER UPDATE OF max_lod ON features " +
                              "BEGIN " +
                              "UPDATE featuresets SET lod_check = 1; " + 
                              "UPDATE features SET lod_version = (SELECT lod_version FROM featuresets WHERE id = OLD.fsid LIMIT 1) WHERE fid = OLD.fid; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS featuresets_min_lod_update AFTER UPDATE OF min_lod ON featuresets " +
                              "BEGIN " +
                              "UPDATE featuresets SET lod_version = (OLD.lod_version+1), lod_check = 0 WHERE id = OLD.id; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS featuresets_max_lod_update AFTER UPDATE OF max_lod ON featuresets " +
                              "BEGIN " +
                              "UPDATE featuresets SET lod_version = (OLD.lod_version+1), lod_check = 0 WHERE id = OLD.id; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS featuresets_name_update AFTER UPDATE OF name ON featuresets " +
                              "BEGIN " +
                              "UPDATE featuresets SET name_version = (OLD.name_version+1) WHERE id = OLD.id; " + 
                              "END;", null);
        this.database.execute("CREATE TRIGGER IF NOT EXISTS featuresets_delete AFTER DELETE ON featuresets " +
                              "FOR EACH ROW " +
                              "BEGIN " +
                              "DELETE FROM features WHERE fsid = OLD.id; " + 
                              "END;", null);
    }

    protected void validateInfo() {
        if(!this.infoDirty)
            return;

        this.visible = false;
        this.visibleCheck = false;
        this.minLod = Integer.MAX_VALUE;
        this.maxLod = 0;
        this.lodCheck = false;

        CursorIface result = null;
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT");
            sql.append("    id,");
            sql.append("    name, name_version,");
            sql.append("    visible, visible_version, visible_check,");
            sql.append("    min_lod, max_lod, lod_version, lod_check");
            sql.append(" FROM featuresets");

            result = this.database.query(sql.toString(), null);
            while(result.moveToNext()) {
                FeatureSetDefn fs = this.featureSets.get(Long.valueOf(result.getLong(0)));
                if(fs == null)
                    continue;

                fs.fsid = result.getLong(0);
                fs.name = result.getString(1);
                fs.nameVersion = result.getInt(2);
                fs.visible = (result.getInt(3) != 0);
                fs.visibleVersion = result.getInt(4);
                fs.visibleCheck = (result.getInt(5) != 0);
                fs.minLod = result.getInt(6);
                fs.maxLod = result.getInt(7);
                fs.lodVersion = result.getInt(8);
                fs.lodCheck = (result.getInt(9) != 0);
                
                this.visible |= fs.visible;
                this.visibleCheck |= fs.visibleCheck;
                
                if(fs.minLod < this.minLod)
                    this.minLod = fs.minLod;
                if(fs.maxLod > this.maxLod)
                    this.maxLod = fs.maxLod;
                this.lodCheck |= fs.lodCheck;
            }
            
            this.infoDirty = false;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    private void validateAttributeSchema() {
        if(this.attrSchemaDirty) {
            CursorIface result = null;
            try {
                result = this.database.query("SELECT id, name, coding FROM attribs_schema", null);
                long id;
                String name;
                int coding;
                AttributeSpec schemaSpec;
                AttributeSpec parentSpec;
                while(result.moveToNext()) {
                    id = result.getLong(0);
                    name = result.getString(1);
                    coding = result.getInt(2);
                    
                    schemaSpec = new AttributeSpec(name, id, coding);
                    this.idToAttrSchema.put(Long.valueOf(id), schemaSpec);
                    
                    parentSpec = this.keyToAttrSchema.get(name);
                    if(parentSpec != null)
                        parentSpec.secondaryDefs.put(Integer.valueOf(coding), schemaSpec);
                    else
                        this.keyToAttrSchema.put(name, schemaSpec);
                }
            } finally {
                if(result != null)
                    result.close();
            }

            this.attrSchemaDirty = false;
        }
    }
    
    protected static boolean isCompatible(FeatureSetDefn defn, FeatureSetQueryParameters params) {
        if(params == null)
            return true;
        if(params.ids != null && !params.ids.contains(Long.valueOf(defn.fsid)))
            return false;
        if(params.names != null && !matches(params.names, defn.name, '%'))
            return false;
        if(params.types != null &&  !matches(params.types, defn.type, '%'))
            return false;
        if(params.providers != null && !matches(params.providers, defn.provider, '%'))
            return false;
        return true;
    }

    protected static boolean isCompatible(FeatureSetDefn defn, FeatureQueryParameters params) {
        if(params == null)
            return true;
        if(params.featureSetIds != null && !params.featureSetIds.contains(Long.valueOf(defn.fsid)))
            return false;
        if(params.featureSets != null && !matches(params.featureSets, defn.name, '%'))
            return false;
        if(params.types != null && !matches(params.types, defn.type, '%'))
            return false;
        if(params.providers != null && !matches(params.providers, defn.provider, '%'))
            return false;
        return true;
    }
    
    protected boolean isCompatible(FeatureSetQueryParameters params) {
        if(this.featureSets.isEmpty())
            return false;
        
        for(FeatureSetDefn fs : this.featureSets.values())
            if(!isCompatible(fs, params))
                return false;
        return true;
    }

    protected boolean isCompatible(FeatureQueryParameters params) {
        if(this.featureSets.isEmpty())
            return false;
        
        for(FeatureSetDefn fs : this.featureSets.values())
            if(!isCompatible(fs, params))
                return false;
        return true;
    }
    
    @Override
    public Feature getFeature(long fid) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureIds = Collections.singleton(Long.valueOf(fid));
        params.limit = 1;
        
        FeatureCursor result = this.queryFeatures(params);
        try {
            result = this.queryFeatures(params);
            if(!result.moveToNext())
                return null;
            return result.get();
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public FeatureCursor queryFeatures(FeatureQueryParameters params) {
        final int ignoredFields = (params == null) ? 0 : params.ignoredFields;

        final int idCol = 0;
        final int fsidCol = 1;
        final int versionCol = 2;
        int extrasCol = versionCol + 1;
        
        int nameCol = -1;
        int geomCol = -1;
        int styleCol = -1;
        int attribsCol = -1;
        
        LinkedList<BindArgument> args = new LinkedList<BindArgument>();
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT features.fid, features.fsid, features.version");

        if(!MathUtils.hasBits(ignoredFields, FeatureQueryParameters.FIELD_NAME)) {
            sql.append(", features.name");
            nameCol = extrasCol++;
        }
        if(!MathUtils.hasBits(ignoredFields, FeatureQueryParameters.FIELD_GEOMETRY)) {
            if(params != null && params.ops != null) {
                StringBuilder geomStr = new StringBuilder("features.geometry");            
                for(FeatureQueryParameters.SpatialOp op : params.ops) {
                    if(op instanceof FeatureQueryParameters.Simplify) {
                        geomStr.insert(0, "SimplifyPreserveTopology(");
                        geomStr.append(", ?)");
                        
                        args.add(new BindArgument(((FeatureQueryParameters.Simplify)op).distance));
                    } else if(op instanceof FeatureQueryParameters.Buffer) {
                        geomStr.insert(0, "Buffer(");
                        geomStr.append(", ?)");
                        
                        args.add(new BindArgument(((FeatureQueryParameters.Buffer)op).distance));
                    }
                }
                sql.append(", ");
                sql.append(geomStr);
            } else {
                sql.append(", features.geometry");
            }
            geomCol = extrasCol++;
        }
        if(!MathUtils.hasBits(ignoredFields, FeatureQueryParameters.FIELD_STYLE)) {
            sql.append(", styles.value");
            styleCol = extrasCol++;
        }
        if(!MathUtils.hasBits(ignoredFields, FeatureQueryParameters.FIELD_ATTRIBUTES)) {
            sql.append(", attributes.value");
            attribsCol = extrasCol++;
        }
        
        sql.append(" FROM features");
        if(!MathUtils.hasBits(ignoredFields, FeatureQueryParameters.FIELD_STYLE))
            sql.append(" LEFT JOIN styles ON features.style_id = styles.id");
        if(!MathUtils.hasBits(ignoredFields, FeatureQueryParameters.FIELD_ATTRIBUTES))
            sql.append(" LEFT JOIN attributes ON features.attribs_id = attributes.id");
        
        if(params == null) {
            CursorIface result = null;
            try {
                result = BindArgument.query(this.database, sql.toString(), args.isEmpty() ? null : args);

                final FeatureCursor retval = 
                       new FeatureCursorImpl(result,
                                             idCol,
                                             fsidCol,
                                             versionCol,
                                             nameCol,
                                             geomCol,
                                             styleCol,
                                             attribsCol);
                result = null;
                return retval;
            } finally {
                if(result != null)
                    result.close();
            }
        }
        
        Collection<FeatureSetDefn> fsNoCheck = this.filterNoSync(params, true);
        if(fsNoCheck.isEmpty())
            return new MultiplexingFeatureCursor(Collections.<FeatureCursor>emptySet(), null);

        Collection<FeatureSetDefn> fsCheck = new LinkedList<FeatureSetDefn>();
        if(params.visibleOnly) {
            this.validateInfo();
            
            Iterator<FeatureSetDefn> iter = fsNoCheck.iterator();
            FeatureSetDefn defn;
            while(iter.hasNext()) {
                defn = iter.next();
                if(defn.visibleCheck) {
                    iter.remove();
                    fsCheck.add(defn);
                }
            }
        }
        if(!Double.isNaN(params.minResolution) || !Double.isNaN(params.maxResolution)) {
            this.validateInfo();
            
            Iterator<FeatureSetDefn> iter = fsNoCheck.iterator();
            FeatureSetDefn defn;
            while(iter.hasNext()) {
                defn = iter.next();
                if(defn.lodCheck) {
                    iter.remove();
                    fsCheck.add(defn);
                }
            }
        }
        
        LinkedList<FeatureCursor> retval = new LinkedList<FeatureCursor>();
        if(fsCheck != null) {
            for(FeatureSetDefn fs : fsCheck) {
                StringBuilder subsql = new StringBuilder();
                LinkedList<BindArgument> subargs = new LinkedList<BindArgument>();
                WhereClauseBuilder where = new WhereClauseBuilder();
                
                if(!this.buildParamsWhereClauseCheck(params, fs, where))
                    continue;
                
                subsql.append(sql);
                subargs.addAll(args);

                final String select = where.getSelection();
                if(select != null) {
                    subsql.append(" WHERE ");
                    subsql.append(select);
                    subargs.addAll(where.getBindArgs());
                }

                if(params.order != null) {
                    boolean first = true;
                    StringBuilder orderSql = new StringBuilder();
                    for(Order order : params.order)
                        first = !appendOrder(order, orderSql, subargs, first);
                    if(!first) {
                        subsql.append(" ORDER BY ");
                        subsql.append(orderSql);
                    }
                }

                if(params.limit > 0) {
                    subsql.append(" LIMIT ?");
                    subargs.add(new BindArgument(params.limit));
                    
                    if(fsCheck.size() == 1 && fsNoCheck.isEmpty() && params.offset > 0) {
                        subsql.append(" OFFSET ?");
                        subargs.add(new BindArgument(params.offset));
                    }
                }
                
                CursorIface result = null;
                try {
                    result = BindArgument.query(this.database, subsql.toString(), subargs.isEmpty() ? null : subargs);

                    retval.add(new FeatureCursorImpl(result,
                                                     idCol,
                                                     fsidCol,
                                                     versionCol,
                                                     nameCol,
                                                     geomCol,
                                                     styleCol,
                                                     attribsCol));
                    result = null;
                } finally {
                    if(result != null)
                        result.close();
                }
            }
        }

        if(!fsNoCheck.isEmpty()) {
            do {
                StringBuilder subsql = new StringBuilder();
                LinkedList<BindArgument> subargs = new LinkedList<BindArgument>();
                WhereClauseBuilder where = new WhereClauseBuilder();
                
                if(!this.buildParamsWhereClauseNoCheck(params, (fsNoCheck.size() == this.featureSets.size()) ? null : fsNoCheck, where))
                    continue;
                
                subsql.append(sql);
                subargs.addAll(args);

                final String select = where.getSelection();
                if(select != null) {
                    subsql.append(" WHERE ");
                    subsql.append(select);
                    subargs.addAll(where.getBindArgs());
                }

                if(params.order != null) {
                    boolean first = true;
                    StringBuilder orderSql = new StringBuilder();
                    for(Order order : params.order)
                        first = !appendOrder(order, orderSql, subargs, first);
                    if(!first) {
                        subsql.append(" ORDER BY ");
                        subsql.append(orderSql);
                    }
                }

                if(params.limit > 0) {
                    subsql.append(" LIMIT ?");
                    subargs.add(new BindArgument(params.limit));
                    
                    if(fsCheck.isEmpty() && params.offset > 0) {
                        subsql.append(" OFFSET ?");
                        subargs.add(new BindArgument(params.offset));
                    }
                }
                
                CursorIface result = null;
                try {
                    result = BindArgument.query(this.database, subsql.toString(), subargs.isEmpty() ? null : subargs);

                    retval.add(new FeatureCursorImpl(result,
                                                     idCol,
                                                     fsidCol,
                                                     versionCol,
                                                     nameCol,
                                                     geomCol,
                                                     styleCol,
                                                     attribsCol));
                    result = null;
                } finally {
                    if(result != null)
                        result.close();
                }
            } while(false);
        }
        
        if(retval.size() == 1) {
            return retval.getFirst();
        } else {
            FeatureCursor cursor = new MultiplexingFeatureCursor(retval, params.order);
            if(params.limit > 0)
                cursor = new BruteForceLimitOffsetFeatureCursor(cursor, params.offset, params.limit);
            return cursor;
        }
    }

    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) {
        if(params == null) {
            CursorIface result = null;
            try {
                result = this.database.query("SELECT Count(1) FROM features", null);
                if(!result.moveToNext())
                    return 0;
                return result.getInt(0);
            } finally {
                if(result != null)
                    result.close();
            }
        }

        Collection<FeatureSetDefn> fsNoCheck = this.filterNoSync(params, true);
        if(fsNoCheck.isEmpty())
            return 0;

        LinkedList<BindArgument> args = new LinkedList<BindArgument>();

        Collection<FeatureSetDefn> fsCheck = new LinkedList<FeatureSetDefn>();
        if(params.visibleOnly) {
            this.validateInfo();
            
            Iterator<FeatureSetDefn> iter = fsNoCheck.iterator();
            FeatureSetDefn defn;
            while(iter.hasNext()) {
                defn = iter.next();
                if(defn.visibleCheck) {
                    iter.remove();
                    fsCheck.add(defn);
                }
            }
        }
        if(!Double.isNaN(params.minResolution) || !Double.isNaN(params.maxResolution)) {
            this.validateInfo();
            
            Iterator<FeatureSetDefn> iter = fsNoCheck.iterator();
            FeatureSetDefn defn;
            while(iter.hasNext()) {
                defn = iter.next();
                if(defn.lodCheck) {
                    iter.remove();
                    fsCheck.add(defn);
                }
            }
        }
        
        if((params.limit > 0 && params.offset > 0) &&
           (fsCheck.size() > 1 ||
                   (!fsCheck.isEmpty() && !fsNoCheck.isEmpty()))) {
            
            return queryFeaturesCount(this, params);
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        if(params.limit > 0)
            sql.append("1");
        else
            sql.append("Count(1)");
        sql.append(" FROM features");

        int retval = 0;
        if(fsCheck != null) {
            for(FeatureSetDefn fs : fsCheck) {
                StringBuilder subsql = new StringBuilder();
                LinkedList<BindArgument> subargs = new LinkedList<BindArgument>();
                WhereClauseBuilder where = new WhereClauseBuilder();
                
                if(!this.buildParamsWhereClauseCheck(params, fs, where))
                    continue;
                
                subsql.append(sql);
                subargs.addAll(args);

                final String select = where.getSelection();
                if(select != null) {
                    subsql.append(" WHERE ");
                    subsql.append(select);
                    subargs.addAll(where.getBindArgs());
                }

                if(params.order != null) {
                    boolean first = true;
                    StringBuilder orderSql = new StringBuilder();
                    for(Order order : params.order)
                        first = !appendOrder(order, orderSql, subargs, first);
                    if(!first) {
                        subsql.append(" ORDER BY ");
                        subsql.append(orderSql);
                    }
                }

                if(params.limit > 0) {
                    subsql.append(" LIMIT ?");
                    subargs.add(new BindArgument(params.limit));
                    
                    if(fsCheck.size() == 1 && fsNoCheck.isEmpty() && params.offset > 0) {
                        subsql.append(" OFFSET ?");
                        subargs.add(new BindArgument(params.offset));
                    }
                    
                    subsql.insert(0, "SELECT Count(1) FROM (");
                    subsql.append(")");
                }
                
                CursorIface result = null;
                try {
                    result = BindArgument.query(this.database, subsql.toString(), subargs.isEmpty() ? null : subargs);
                    if(result.moveToNext())
                        retval += result.getInt(0);
                } finally {
                    if(result != null)
                        result.close();
                }
            }
        }

        if(!fsNoCheck.isEmpty()) {
            do {
                StringBuilder subsql = new StringBuilder();
                LinkedList<BindArgument> subargs = new LinkedList<BindArgument>();
                WhereClauseBuilder where = new WhereClauseBuilder();
                
                if(!this.buildParamsWhereClauseNoCheck(params, (fsNoCheck.size() == this.featureSets.size()) ? null : fsNoCheck, where))
                    continue;
                
                subsql.append(sql);
                subargs.addAll(args);

                final String select = where.getSelection();
                if(select != null) {
                    subsql.append(" WHERE ");
                    subsql.append(select);
                    subargs.addAll(where.getBindArgs());
                }

                if(params.order != null) {
                    boolean first = true;
                    StringBuilder orderSql = new StringBuilder();
                    for(Order order : params.order)
                        first = !appendOrder(order, orderSql, subargs, first);
                    if(!first) {
                        subsql.append(" ORDER BY ");
                        subsql.append(orderSql);
                    }
                }

                if(params.limit > 0) {
                    subsql.append(" LIMIT ?");
                    subargs.add(new BindArgument(params.limit));
                    
                    if(fsCheck.isEmpty() && params.offset > 0) {
                        subsql.append(" OFFSET ?");
                        subargs.add(new BindArgument(params.offset));
                    }
                    
                    subsql.insert(0, "SELECT Count(1) FROM (");
                    subsql.append(")");
                }
                
                CursorIface result = null;
                try {
                    result = BindArgument.query(this.database, subsql.toString(), subargs.isEmpty() ? null : subargs);
                    if(result.moveToNext())
                        retval += result.getInt(0);
                } finally {
                    if(result != null)
                        result.close();
                }
            } while(false);
        }

        return retval;
    }

    @Override
    public synchronized FeatureSet getFeatureSet(long fsid) {
        FeatureSetDefn featureSet = this.featureSets.get(Long.valueOf(fsid));
        if(featureSet == null)
            return null;

        this.validateInfo();
        return this.getFeatureSetImpl(featureSet);
    }
    
    private FeatureSet getFeatureSetImpl(FeatureSetDefn defn) {
        FeatureSet retval = new FeatureSet(defn.provider,
                                           defn.type,
                                           defn.name,
                                           OSMUtils.mapnikTileResolution(
                                                   defn.minLod),
                                           OSMUtils.mapnikTileResolution(
                                                   defn.maxLod));
        this.adopt(retval,
                   defn.fsid,
                   defn.visibleVersion+defn.lodVersion+defn.nameVersion);
        return retval;        
    }

    protected Collection<FeatureSetDefn> filterNoSync(FeatureSetQueryParameters params, boolean softVisibilityCheck) {
        if(params != null && params.visibleOnly)
            this.validateInfo();

        LinkedList<FeatureSetDefn> retval = new LinkedList<FeatureSetDefn>();
        for(FeatureSetDefn fs : this.featureSets.values()) {
            if(!isCompatible(fs, params))
                continue;
            if(params != null && params.visibleOnly) {
                if(!fs.visibleCheck && !fs.visible)
                    continue;
                if(!softVisibilityCheck && fs.visibleCheck && !this.isFeatureSetVisible(fs.fsid))
                    continue;
            }
            retval.add(fs);
        }

        return retval;
    }
    
    protected Collection<FeatureSetDefn> filterNoSync(FeatureQueryParameters params, boolean softChecks) {
        if(params != null && params.visibleOnly)
            this.validateInfo();

        LinkedList<FeatureSetDefn> retval = new LinkedList<FeatureSetDefn>();
        for(FeatureSetDefn fs : this.featureSets.values()) {
            if(!isCompatible(fs, params))
                continue;
            if(params != null) {
                if(params.visibleOnly) {
                    if(!fs.visibleCheck && !fs.visible)
                        continue;
                    if(!softChecks && fs.visibleCheck && !this.isFeatureSetVisible(fs.fsid))
                        continue;
                }
                if(!Double.isNaN(params.minResolution)) {
                    final int queryMinLod = OSMUtils.mapnikTileLevel(params.minResolution);
                    if(!fs.lodCheck && fs.maxLod < queryMinLod) {
                        continue;
                    } else if(fs.lodCheck && !softChecks) {
                        StringBuilder sql = new StringBuilder();
                        LinkedList<BindArgument> args = new LinkedList<BindArgument>();
                        
                        sql.append("SELECT 1 FROM features WHERE fsid = ? AND ");
                        args.add(new BindArgument(fs.fsid));

                        if(fs.maxLod >= queryMinLod) {
                            sql.append("(lod_version != ? OR (lod_version = ? AND max_lod >= ?))");
                            args.add(new BindArgument(fs.lodVersion));
                            args.add(new BindArgument(fs.lodVersion));
                            args.add(new BindArgument(queryMinLod));
                        } else {
                            sql.append("(lod_version = ? AND max_lod >= ?)");
                            args.add(new BindArgument(fs.lodVersion));
                            args.add(new BindArgument(queryMinLod));
                        }
                        
                        sql.append(" LIMIT 1");
                        
                        CursorIface result = null;
                        try {
                            result = BindArgument.query(this.database, sql.toString(), args);
                            if(!result.moveToNext() || result.getInt(0) < 1)
                                continue;
                        } finally {
                            if(result != null)
                                result.close();
                        }
                    }
                }
                if(!Double.isNaN(params.maxResolution)) {
                    final int queryMaxLod = OSMUtils.mapnikTileLevel(params.maxResolution);
                    if(!fs.lodCheck && fs.minLod > queryMaxLod) {
                        continue;
                    } else if(fs.lodCheck && !softChecks) {
                        StringBuilder sql = new StringBuilder();
                        LinkedList<BindArgument> args = new LinkedList<BindArgument>();
                        
                        sql.append("SELECT 1 FROM features WHERE fsid = ? AND ");
                        args.add(new BindArgument(fs.fsid));

                        if(fs.minLod <= queryMaxLod) {
                            sql.append("(lod_version != ? OR (lod_version = ? AND min_lod <= ?))");
                            args.add(new BindArgument(fs.lodVersion));
                            args.add(new BindArgument(fs.lodVersion));
                            args.add(new BindArgument(queryMaxLod));
                        } else {
                            sql.append("(lod_version = ? AND min_lod <= ?)");
                            args.add(new BindArgument(fs.lodVersion));
                            args.add(new BindArgument(queryMaxLod));
                        }
                        
                        sql.append(" LIMIT 1");
                        
                        CursorIface result = null;
                        try {
                            result = BindArgument.query(this.database, sql.toString(), args);
                            if(!result.moveToNext() || result.getInt(0) < 1)
                                continue;
                        } finally {
                            if(result != null)
                                result.close();
                        }
                    }
                }
            }
            retval.add(fs);
        }

        return retval;
    }


    @Override
    public synchronized FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
        Collection<FeatureSetDefn> retval = this.filterNoSync(params, false);
        Map<String, FeatureSetDefn> sorted = new TreeMap<String, FeatureSetDefn>(StringIgnoreCaseComparator.INSTANCE);
        for(FeatureSetDefn fs : retval)
            sorted.put(fs.name, fs);
        return new FeatureSetCursorImpl(sorted.values());
    }

    @Override
    public synchronized int queryFeatureSetsCount(FeatureSetQueryParameters params) {
        return this.filterNoSync(params, false).size();
    }

    @Override
    public synchronized boolean isFeatureVisible(long fid) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureIds = Collections.singleton(Long.valueOf(fid));
        params.visibleOnly = true;
        params.limit = 1;
            
        return (this.queryFeaturesCount(params)>0);
    }

    @Override
    public synchronized boolean isFeatureSetVisible(long fsid) {
        FeatureSetDefn featureSet = this.featureSets.get(Long.valueOf(fsid));
        if(featureSet == null)
            return false;

        this.validateInfo();
        if(!featureSet.visibleCheck)
            return featureSet.visible;
        
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureSetIds = Collections.singleton(Long.valueOf(fsid));
        params.visibleOnly = true;
        params.limit = 1;
        
        return (this.queryFeaturesCount(params)>0);
    }

    @Override
    public synchronized boolean isAvailable() {
        return (this.database != null);
    }

    @Override
    public synchronized void refresh() {
        this.featureSets.clear();
        
        this.visible = false;
        this.visibleCheck = false;
        this.minLod = Integer.MAX_VALUE;
        this.maxLod = 0;
        this.lodCheck = false;

        CursorIface result = null;
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT");
            sql.append("    id,");
            sql.append("    name, name_version,");
            sql.append("    visible, visible_version, visible_check,");
            sql.append("    min_lod, max_lod, lod_version, lod_check,");
            sql.append("    type, provider");
            sql.append(" FROM featuresets");

            result = this.database.query(sql.toString(), null);
            while(result.moveToNext()) {
                FeatureSetDefn fs = new FeatureSetDefn();
                fs.fsid = result.getLong(0);
                fs.name = result.getString(1);
                fs.nameVersion = result.getInt(2);
                fs.visible = (result.getInt(3) != 0);
                fs.visibleVersion = result.getInt(4);
                fs.visibleCheck = (result.getInt(5)!=0);
                fs.minLod = result.getInt(6);
                fs.maxLod = result.getInt(7);
                fs.lodVersion = result.getInt(8);
                fs.lodCheck = (result.getInt(9) != 0);
                fs.type = result.getString(10);
                fs.provider = result.getString(11);
                
                this.featureSets.put(Long.valueOf(fs.fsid), fs);
                
                this.visible |= fs.visible;
                this.visibleCheck |= fs.visibleCheck;
                
                if(fs.minLod < this.minLod)
                    this.minLod = fs.minLod;
                if(fs.maxLod > this.maxLod)
                    this.maxLod = fs.maxLod;
                this.lodCheck |= fs.lodCheck;
            }
            
            this.infoDirty = false;
        } finally {
            if(result != null)
                result.close();
        }
        
        this.dispatchDataStoreContentChangedNoSync();
    }

    @Override
    public String getUri() {
        return this.databaseFile;
    }

    @Override
    public synchronized void dispose() {
        if(this.database != null) {
            this.database.close();
            this.database = null;
        }
    }

    @Override
    protected boolean setFeatureVisibleImpl(long fid, boolean visible) {
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE features SET visible = ? WHERE fid = ?");
            stmt.bind(1,  fid);
            stmt.bind(2, visible ? 1 : 0);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        this.infoDirty = true;

        return true;
    }

    @Override
    protected boolean setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
        if(params != null && !isCompatible(params))
            return false;
        
        if(params == null ||
            (!params.visibleOnly &&
             params.featureIds == null &&
             params.featureNames == null &&
             params.spatialFilter == null &&
             Double.isNaN(params.minResolution) &&
             Double.isNaN(params.maxResolution))) {

            StringBuilder sql = new StringBuilder();
            LinkedList<BindArgument> args = new LinkedList<BindArgument>();
            WhereClauseBuilder where = new WhereClauseBuilder();
            
            sql.append("UPDATE featuresets SET visible = ?");
            args.add(new BindArgument(visible ? 1 : 0));
            
            if(params != null && params.providers == null && params.types == null) {
                if(params.featureSetIds != null) {
                    where.beginCondition();
                    Collection<BindArgument> fsids = new LinkedList<BindArgument>();
                    for(Long fsid : params.featureSetIds)
                        fsids.add(new BindArgument(fsid.longValue()));
                    where.appendIn2("id", fsids);
                }
                if(params.featureSets != null) {
                    where.beginCondition();
                    where.appendIn("name", params.featureSets);
                }
            } else {
                Collection<FeatureSetDefn> fs = this.filterNoSync(params, true);
                
                where.beginCondition();
                Collection<BindArgument> fsids = new LinkedList<BindArgument>();
                for(FeatureSetDefn defn : fs)
                    fsids.add(new BindArgument(defn.fsid));
                where.appendIn2("id", fsids);
            }
            
            final String selection = where.getSelection();
            if(selection != null) {
                sql.append(" WHERE ");
                sql.append(selection);
                args.addAll(where.getBindArgs());
            }

            // visibility for all features is being toggled
            StatementIface stmt = null;
            try {
                stmt = this.database.compileStatement("UPDATE featuresets SET visible = ?");
                int idx = 1;
                for(BindArgument arg : args)
                    arg.bind(stmt, idx++);
                stmt.execute();
            } finally {
                if(stmt != null)
                    stmt.close();
            }
            return true;
        } else {
            Collection<FeatureSetDefn> fsNoCheck = this.filterNoSync(params, true);
            if(fsNoCheck.isEmpty())
                return false;

            Collection<FeatureSetDefn> fsCheck = null;
            if(params.visibleOnly) {
                this.validateInfo();
                
                Iterator<FeatureSetDefn> iter = fsNoCheck.iterator();
                FeatureSetDefn defn;
                while(iter.hasNext()) {
                    defn = iter.next();
                    if(defn.visibleCheck) {
                        iter.remove();
                        if(fsCheck == null)
                            fsCheck = new LinkedList<FeatureSetDefn>();
                        fsCheck.add(defn);
                    }
                }
            }
            if(!Double.isNaN(params.minResolution) || !Double.isNaN(params.maxResolution)) {
                this.validateInfo();
                
                Iterator<FeatureSetDefn> iter = fsNoCheck.iterator();
                FeatureSetDefn defn;
                while(iter.hasNext()) {
                    defn = iter.next();
                    if(defn.lodCheck) {
                        iter.remove();
                        if(fsCheck == null)
                            fsCheck = new LinkedList<FeatureSetDefn>();
                        fsCheck.add(defn);
                    }
                }
            }
            
            if(fsNoCheck.size() == this.featureSets.size())
                fsNoCheck = null;
            
            if(fsCheck != null) {
                for(FeatureSetDefn fs : fsCheck) {
                    StringBuilder sql = new StringBuilder();
                    LinkedList<BindArgument> args = new LinkedList<BindArgument>();
                    WhereClauseBuilder where = new WhereClauseBuilder();
                    
                    sql.append("UPDATE features SET visible = ?");
                    args.add(new BindArgument(visible ? 1 : 0));
                    if(!this.buildParamsWhereClauseCheck(params, fs, where))
                        continue;
                    
                    sql.append(" WHERE ");
                    sql.append(where.getSelection());
                    
                    args.addAll(where.getBindArgs());
                    
                    StatementIface stmt = null;
                    try {
                        stmt = this.database.compileStatement(sql.toString());
                        int idx = 1;
                        for(BindArgument arg : args)
                            arg.bind(stmt, idx++);
                        stmt.execute();
                    } finally {
                        if(stmt != null)
                            stmt.close();
                    }
                }
            }

            if(fsNoCheck == null || !fsNoCheck.isEmpty()) {
                do {
                    StringBuilder sql = new StringBuilder();
                    LinkedList<BindArgument> args = new LinkedList<BindArgument>();
                    WhereClauseBuilder where = new WhereClauseBuilder();
                    
                    sql.append("UPDATE features SET visible = ?");
                    args.add(new BindArgument(visible ? 1 : 0));
                    if(!this.buildParamsWhereClauseNoCheck(params, fsNoCheck, where))
                        continue;
                    
                    final String selection = where.getSelection();
                    if(selection != null) {
                        sql.append(" WHERE ");
                        sql.append(selection);
                        args.addAll(where.getBindArgs());
                    }

                    StatementIface stmt = null;
                    try {
                        stmt = this.database.compileStatement(sql.toString());
                        int idx = 1;
                        for(BindArgument arg : args)
                            arg.bind(stmt, idx++);
                        stmt.execute();
                    } finally {
                        if(stmt != null)
                            stmt.close();
                    }
                } while(false);
            }

            // some unknown features had visibility toggled; mark info as dirty
            this.infoDirty = true;

            return true;
        }
    }

    @Override
    protected boolean setFeatureSetVisibleImpl(long fsid, boolean visible) {
        FeatureSetDefn featureSet = this.featureSets.get(Long.valueOf(fsid));
        if(featureSet == null)
            return false;
        
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE featuresets SET visible = ?");
            stmt.bind(1, visible ? 1 : 0);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        // update the visibility info -- we will leave current clean/dirty state
        // as version may not yet be validated.
        featureSet.visible = visible;
        featureSet.visibleCheck = false;
        featureSet.visibleVersion++;
        
        return true;
    }

    @Override
    protected boolean setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible) {
        Collection<FeatureSetDefn> fs = this.filterNoSync(params, false);
        if(fs.isEmpty())
            return false;

        StringBuilder sql = new StringBuilder();
        LinkedList<BindArgument> args = new LinkedList<BindArgument>();
        
        sql.append("UPDATE featuresets SET visible = ?");
        args.add(new BindArgument(visible ? 1 : 0));
        
        
        if(params != null) {
            sql.append(" WHERE id IN (");
            
            Iterator<FeatureSetDefn> iter = fs.iterator();
            if(!iter.hasNext())
                throw new IllegalStateException();

            sql.append("?");
            args.add(new BindArgument(iter.next().fsid));
            
            while(iter.hasNext()) {
                sql.append(", ?");
                args.add(new BindArgument(iter.next().fsid));
            }
            sql.append(")");
        }
        
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement(sql.toString());
            int idx = 1;
            for(BindArgument arg : args)
                arg.bind(stmt, idx++);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        for(FeatureSetDefn defn : fs) {
            defn.visible = visible;
            defn.visibleVersion++;
            defn.visibleCheck = false;
        }

        return true;
    }

    @Override
    protected void beginBulkModificationImpl() {
        // no-op
    }

    @Override
    protected boolean endBulkModificationImpl(boolean successful) {
        // no-op
        return successful;
    }

    @Override
    protected final boolean insertFeatureSetImpl(String provider, String type, String name,
            double minResolution, double maxResolution, FeatureSet[] returnRef) {

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO featuresets" +
                    "    (name," +
                    "     name_version," +
                    "     visible," +
                    "     visible_version," +
                    "     visible_check," +
                    "     min_lod," +
                    "     max_lod," +
                    "     lod_version," +
                    "     lod_check," +
                    "     type," +
                    "     provider)" +
                    " VALUES (?, 1, 1, 1, 0, ?, ?, 1, 0, ?, ?)");
                   
            stmt.bind(1, name);
            stmt.bind(2, OSMUtils.mapnikTileLevel(minResolution));
            stmt.bind(3, OSMUtils.mapnikTileLevel(maxResolution));
            stmt.bind(4, type);
            stmt.bind(5, provider);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        final long fsid = Databases.lastInsertRowId(this.database);
        
        FeatureSetDefn defn = new FeatureSetDefn();
        defn.fsid = fsid;
        defn.name = name;
        defn.nameVersion = 1;
        defn.minLod = OSMUtils.mapnikTileLevel(minResolution);
        defn.maxLod = OSMUtils.mapnikTileLevel(maxResolution);
        defn.lodCheck = false;
        defn.lodVersion = 1;
        defn.type = type;
        defn.provider = provider;
        defn.visible = true;
        defn.visibleCheck = false;
        defn.visibleVersion = 1;
        
        this.featureSets.put(Long.valueOf(fsid), defn);
        if(returnRef != null)
            returnRef[0] = this.getFeatureSetImpl(defn);
        
        return true;
    }
    
    private void insertFeatureSetImpl(long fsid, String provider, String type, String name,
            double minResolution, double maxResolution) {

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO featuresets" +
                    "    (id," +
                    "     name," +
                    "     name_version," +
                    "     visible," +
                    "     visible_version," +
                    "     visible_check," +
                    "     min_lod," +
                    "     max_lod," +
                    "     lod_version," +
                    "     lod_check," +
                    "     type," +
                    "     provider)" +
                    " VALUES (?, ?, 1, 1, 1, 0, ?, ?, 1, 0, ?, ?)");
                   
            stmt.bind(1, fsid);
            stmt.bind(2, name);
            stmt.bind(3, OSMUtils.mapnikTileLevel(minResolution));
            stmt.bind(4, OSMUtils.mapnikTileLevel(maxResolution));
            stmt.bind(5, type);
            stmt.bind(6, provider);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        FeatureSetDefn defn = new FeatureSetDefn();
        defn.fsid = fsid;
        defn.name = name;
        defn.nameVersion = 1;
        defn.minLod = OSMUtils.mapnikTileLevel(minResolution);
        defn.maxLod = OSMUtils.mapnikTileLevel(maxResolution);
        defn.lodCheck = false;
        defn.lodVersion = 1;
        defn.type = type;
        defn.provider = provider;
        defn.visible = true;
        defn.visibleCheck = false;
        defn.visibleVersion = 1;
        
        this.featureSets.put(Long.valueOf(fsid), defn);
    }


    @Override
    protected boolean updateFeatureSetImpl(long fsid, String name) {
        FeatureSetDefn defn = this.featureSets.get(Long.valueOf(fsid));
        if(defn == null)
            return false;

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE featuresets SET name = ? WHERE id = ?");
            stmt.bind(1, name);
            stmt.bind(2, fsid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        defn.name = name;
        defn.nameVersion++;
        return true;
    }

    @Override
    protected boolean updateFeatureSetImpl(long fsid, double minResolution, double maxResolution) {
        FeatureSetDefn defn = this.featureSets.get(Long.valueOf(fsid));
        if(defn == null)
            return false;

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE featuresets SET min_lod = ?, max_lod = ? WHERE id = ?");
            stmt.bind(1, OSMUtils.mapnikTileLevel(minResolution));
            stmt.bind(2, OSMUtils.mapnikTileLevel(maxResolution));
            stmt.bind(3, fsid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        defn.minLod = OSMUtils.mapnikTileLevel(minResolution);
        defn.maxLod = OSMUtils.mapnikTileLevel(maxResolution);
        defn.lodCheck = false;
        defn.lodVersion++;
        return true;
    }

    @Override
    protected boolean updateFeatureSetImpl(long fsid, String name, double minResolution,
            double maxResolution) {

        FeatureSetDefn defn = this.featureSets.get(Long.valueOf(fsid));
        if(defn == null)
            return false;

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE featuresets SET name = ?, min_lod = ?, max_lod = ? WHERE id = ?");
            stmt.bind(1, name);
            stmt.bind(2, OSMUtils.mapnikTileLevel(minResolution));
            stmt.bind(3, OSMUtils.mapnikTileLevel(maxResolution));
            stmt.bind(4, fsid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        defn.name = name;
        defn.nameVersion++;
        defn.minLod = OSMUtils.mapnikTileLevel(minResolution);
        defn.maxLod = OSMUtils.mapnikTileLevel(maxResolution);
        defn.lodCheck = false;
        defn.lodVersion++;
        return true;
    }

    @Override
    protected boolean deleteFeatureSetImpl(long fsid) {
        FeatureSetDefn featureSet = this.featureSets.remove(Long.valueOf(fsid));
        if(featureSet == null)
            return false;

        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("DELETE FROM featuresets WHERE id = ?");
        } finally {
            if(stmt != null)
                stmt.close();
        }
        return true;
    }

    @Override
    protected boolean deleteAllFeatureSetsImpl() {
        this.featureSets.clear();
        // delete features first in single statement to avoid the trigger
        this.database.execute("DELETE FROM features", null);
        this.database.execute("DELETE FROM featuresets", null);
        return true;
    }

    @Override
    protected boolean insertFeatureImpl(long fsid, String name, Geometry geom, Style style,
            AttributeSet attributes, Feature[] returnRef) {
        
        return this.insertFeatureImpl(fsid, new DefaultFeatureDefinition(name, geom, style, attributes), returnRef);
    }
    
    @Override
    protected boolean insertFeatureImpl(long fsid, FeatureDefinition def, Feature[] returnRef) {
        if(!this.featureSets.containsKey(Long.valueOf(fsid)))
            return false;

        InsertContext ctx = new InsertContext();
        try {
            final long fid = this.insertFeatureImpl(ctx, fsid, def);
            if(returnRef != null)
                returnRef[0] = this.getFeature(fid);
            return true;
        } finally {
            ctx.dispose();
        }
    }

    long insertFeatureImpl(InsertContext ctx, long fsid, FeatureDefinition def) {
        String ogrStyle;
        switch(def.getStyleCoding()) {
            case FeatureDefinition.STYLE_OGR :
                ogrStyle = (String)def.getRawStyle();
                break;
            case FeatureDefinition.STYLE_ATAK_STYLE :
                ogrStyle = FeatureStyleParser.pack((Style)def.getRawStyle());
                break;
            default :
                ogrStyle = FeatureStyleParser.pack(def.get().getStyle());
                break;
        }

        Long styleId = ctx.styleIds.get(ogrStyle);
        if(styleId == null) {
            try {
                if(ctx.insertStyleStatement == null)
                    ctx.insertStyleStatement = this.database.compileStatement("INSERT INTO styles (coding, value) VALUES ('ogr', ?)");
                
                ctx.insertStyleStatement.clearBindings();
                ctx.insertStyleStatement.bind(1, ogrStyle);
                
                ctx.insertStyleStatement.execute();
            } finally {
                if(ctx.insertStyleStatement != null)
                    ctx.insertStyleStatement.clearBindings();
            }
            final long scratch = Databases.lastInsertRowId(this.database);
            styleId = (scratch>0L) ? Long.valueOf(scratch) : null;
            ctx.styleIds.put(ogrStyle, styleId);
        }
        
        long attributesId = 0L;
        final AttributeSet attribs = def.getAttributes();
        if(attribs != null) {
            ctx.codedAttribs.reset();
            encodeAttributes(this, ctx, attribs);
            if(ctx.codedAttribs.size() > 0) {
                try {
                    if(ctx.insertAttributesStatement == null)
                        ctx.insertAttributesStatement = this.database.compileStatement("INSERT INTO attributes (value) VALUES (?)");
                    ctx.insertAttributesStatement.clearBindings();
                    ctx.insertAttributesStatement.bind(1, ctx.codedAttribs.toByteArray());
                    ctx.insertAttributesStatement.execute();
                } finally {
                    if(ctx.insertAttributesStatement != null)
                        ctx.insertAttributesStatement.clearBindings();
                }
                
                attributesId = Databases.lastInsertRowId(this.database);
            }
        }

        StatementIface stmt = null;
        try {
            do {
                switch (def.getGeomCoding()) {
                    case FeatureDataSource.FeatureDefinition.GEOM_SPATIALITE_BLOB :
                    {
                        if (ctx.insertFeatureBlobStatement == null) {
                            ctx.insertFeatureBlobStatement = this.database
                                    .compileStatement("INSERT INTO features " +
                                            "(name, " + // 1
                                            " geometry, " + // 2
                                            " style_id," + // 3
                                            " attribs_id," + // 4
                                            " visible," + // 5
                                            " visible_version," + // 6
                                            " min_lod," + // 7
                                            " max_lod," + // 8
                                            " lod_version, " + // 9
                                            " version, " + // 10
                                            " fsid) " + // 11
                                            " VALUES (?, ?, ?, ?, 1, 0, 0, ?, 0, 1, ?)");
                            continue;
                        }
                        stmt = ctx.insertFeatureBlobStatement;
                        ctx.insertGeomArg.set((byte[])def.getRawGeometry());
                        break;
                    }
                    case FeatureDataSource.FeatureDefinition.GEOM_WKB :
                    {
                        if (ctx.insertFeatureWkbStatement == null) {
                            ctx.insertFeatureWkbStatement = this.database
                                    .compileStatement("INSERT INTO features " +
                                            "(name, " + // 1
                                            " geometry, " + // 2
                                            " style_id," + // 3
                                            " attribs_id," + // 4
                                            " visible," + // 5
                                            " visible_version," + // 6
                                            " min_lod," + // 7
                                            " max_lod," + // 8
                                            " lod_version, " + // 9
                                            " version, " + // 10
                                            " fsid) " + // 11
                                            " VALUES (?, GeomFromWKB(?, 4326), ?, ?, 1, 0, 0, ?, 0, 1, ?)");
                            continue;
                        }
                        stmt = ctx.insertFeatureWkbStatement;
                        ctx.insertGeomArg.set((byte[])def.getRawGeometry());
                        break;
                    }
                    case FeatureDataSource.FeatureDefinition.GEOM_WKT :
                    {
                        if (ctx.insertFeatureWktStatement == null) {
                            ctx.insertFeatureWktStatement = this.database
                                    .compileStatement("INSERT INTO features " +
                                            "(name, " + // 1
                                            " geometry, " + // 2
                                            " style_id," + // 3
                                            " attribs_id," + // 4
                                            " visible," + // 5
                                            " visible_version," + // 6
                                            " min_lod," + // 7
                                            " max_lod," + // 8
                                            " lod_version, " + // 9
                                            " version, " + // 10
                                            " fsid) " + // 11
                                            " VALUES (?, GeomFromText(?, 4326), ?, ?, 1, 0, 0, ?, 0, 1, ?)");
                            continue;
                        }
                        stmt = ctx.insertFeatureWktStatement;
                        ctx.insertGeomArg.set((String)def.getRawGeometry());
                        break;
                    }
                    default:
                        throw new IllegalArgumentException();
                }
            } while(stmt == null);
            
            stmt.clearBindings();

            int idx = 1;
            // name
            stmt.bind(idx++, def.getName());
            // geometry
            ctx.insertGeomArg.bind(stmt, idx++);
            // style ID
            if (styleId != null)
                stmt.bind(idx++, styleId.longValue());
            else
                stmt.bindNull(idx++);
            // attributes ID
            if(attributesId > 0L)
                stmt.bind(idx++, attributesId);
            else
                stmt.bindNull(idx++);
            // maximum LOD
            stmt.bind(idx++, Integer.MAX_VALUE);
            // FSID
            stmt.bind(idx++, fsid);

            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.clearBindings();
        }

        return Databases.lastInsertRowId(this.database);
    }

    @Override
    protected boolean updateFeatureImpl(long fid, String name) {
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE features SET name = ? WHERE fid = ?");
            stmt.bind(1, name);
            stmt.bind(2, fid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        return true;
    }

    @Override
    protected boolean updateFeatureImpl(long fid, Geometry geom) {
        byte[] wkb = null;
        if(geom != null) {
            wkb = new byte[geom.computeWkbSize()];
            geom.toWkb(ByteBuffer.wrap(wkb));
        }
        
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE features SET geom = GeomFromWkb(?) WHERE fid = ?");
            stmt.bind(1, wkb);
            stmt.bind(2, fid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        return true;
    }

    @Override
    protected boolean updateFeatureImpl(long fid, Style style) {
        String ogrStyle = FeatureStyleParser.pack(style);        
        StatementIface stmt;
        
        stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO styles (coding, value) VALUES('ogr', ?)");
            stmt.bind(1, ogrStyle);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        final long styleId = Databases.lastInsertRowId(this.database);
        stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE features SET style_id = ? WHERE fid = ?");
            stmt.bind(1, styleId);
            stmt.bind(2, fid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        return true;
    }

    @Override
    protected boolean updateFeatureImpl(long fid, AttributeSet attributes) {
        byte[] blob = null;
        if(attributes != null) {
            InsertContext ctx = new InsertContext();
            try {
                encodeAttributes(this, ctx, attributes);
                blob = (ctx.codedAttribs.size() > 0) ? ctx.codedAttribs.toByteArray() : null;
            } finally {
                ctx.dispose();
            }
        }

        StatementIface stmt;
        
        stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO attributes (value) VALUES(?)");
            stmt.bind(1, blob);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        final long attribsId = Databases.lastInsertRowId(this.database);
        stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE features SET attribs_id = ? WHERE fid = ?");
            stmt.bind(1, attribsId);
            stmt.bind(2, fid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        return true;
    }

    @Override
    protected boolean updateFeatureImpl(long fid, String name, Geometry geom, Style style, AttributeSet attributes) {
        byte[] wkb = null;
        if(geom != null) {
            wkb = new byte[geom.computeWkbSize()];
            geom.toWkb(ByteBuffer.wrap(wkb));
        }
        String ogrStyle = FeatureStyleParser.pack(style);        
        byte[] attribsBlob = null;
        if(attributes != null) {
            InsertContext ctx = new InsertContext();
            try {
                encodeAttributes(this, ctx, attributes);
                attribsBlob = (ctx.codedAttribs.size() > 0) ? ctx.codedAttribs.toByteArray() : null;
            } finally {
                ctx.dispose();
            }
        }

        StatementIface stmt;
                
        stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO styles(coding,value) VALUES('ogr', ?)");
            stmt.bind(1, ogrStyle);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        final long styleId = Databases.lastInsertRowId(this.database);

        stmt = null;
        try {
            stmt = this.database.compileStatement("INSERT INTO attributes (value) VALUES(?)");
            stmt.bind(1, attribsBlob);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        final long attribsId = Databases.lastInsertRowId(this.database);

        stmt = null;
        try {
            stmt = this.database.compileStatement("UPDATE features SET name = ?, geom = GeomFromWkb(?), style_id = ?, attribs_id = ? WHERE fid = ?");
            stmt.bind(1, name);
            stmt.bind(2, wkb);
            stmt.bind(3, styleId);
            stmt.bind(4, attribsId);
            stmt.bind(5, fid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }

        return true;
    }

    @Override
    protected boolean deleteFeatureImpl(long fid) {
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("DELETE FROM features WHERE fid = ?");
            stmt.bind(1, fid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        return (Databases.lastChangeCount(this.database)>0);
    }

    @Override
    protected boolean deleteAllFeaturesImpl(long fsid) {
        FeatureSetDefn featureSet = this.featureSets.get(Long.valueOf(fsid));
        if(featureSet == null)
            return false;
        
        StatementIface stmt = null;
        try {
            stmt = this.database.compileStatement("DELETE FROM features WHERE fsid = ?");
            stmt.bind(1, fsid);
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        return (Databases.lastChangeCount(this.database)>0);
    }
    
    /**************************************************************************/
    
    private boolean buildParamsWhereClauseNoCheck(FeatureQueryParameters params, Collection<FeatureSetDefn> fs, WhereClauseBuilder whereClause) {
        boolean indexedSpatialFilter = (params.spatialFilter != null);

        if(fs != null) {
            whereClause.beginCondition();
            Collection<BindArgument> args = new LinkedList<BindArgument>();
            for(FeatureSetDefn defn : fs)
                args.add(new BindArgument(defn.fsid));
            whereClause.appendIn2("features.fsid", args);
        }

        
        if(!Double.isNaN(params.maxResolution) || !Double.isNaN(params.minResolution)) {
            // handled upstream
        }
        if(params.visibleOnly) {
            // handled upstream
        }
        if(params.providers != null) {
            // handled upstream
        }
        if(params.types != null) {
            // handled upstream
        }
        if(params.featureSets != null) {
            // handled upstream
        }
        if(params.featureSetIds != null) {
            // handled upstream
        }
        if(params.featureNames != null) {
            whereClause.beginCondition();
            whereClause.appendIn("features.name", params.featureNames);
        }
        if(params.featureIds != null) {
            whereClause.beginCondition();
            Collection<BindArgument> args = new LinkedList<BindArgument>();
            for(Long fid : params.featureIds)
                args.add(new BindArgument(fid.longValue()));
            whereClause.appendIn2("features.fid", args);
        }
        if(params.spatialFilter != null) {
            appendSpatialFilter(params.spatialFilter,
                                whereClause,
                                indexedSpatialFilter &&
                                  this.spatialIndexEnabled,
                                params.limit);
        }

        return true;
    }
    
    private boolean buildParamsWhereClauseCheck(FeatureQueryParameters params, FeatureSetDefn defn, WhereClauseBuilder whereClause) {
        boolean indexedSpatialFilter = (params.spatialFilter != null);

        if(!Double.isNaN(params.maxResolution) || !Double.isNaN(params.minResolution)) {
            this.validateInfo();
            
            if(defn.lodCheck) {
                // restrict to groups that may be visible -- this will be groups
                // that are explicitly marked as visible or have the visible_check
                // flag toggled

                if(!Double.isNaN(params.minResolution)) {
                    final int queryMinLod = OSMUtils.mapnikTileLevel(params.minResolution);
                    whereClause.beginCondition();
                    if(defn.maxLod >= queryMinLod) {
                        whereClause.append("(lod_version != ? OR (lod_version = ? AND max_lod >= ?))");
                        whereClause.addArg(defn.lodVersion);
                        whereClause.addArg(defn.lodVersion);
                        whereClause.addArg(queryMinLod);
                    } else {
                        whereClause.append("(lod_version = ? AND max_lod >= ?)");
                        whereClause.addArg(defn.lodVersion);
                        whereClause.addArg(queryMinLod);
                    }
                }
                if(!Double.isNaN(params.maxResolution)) {
                    final int queryMaxLod = OSMUtils.mapnikTileLevel(params.maxResolution);
                    whereClause.beginCondition();
                    if(defn.minLod <= queryMaxLod) {
                        whereClause.append("(lod_version != ? OR (lod_version = ? AND min_lod <= ?))");
                        whereClause.addArg(defn.lodVersion);
                        whereClause.addArg(defn.lodVersion);
                        whereClause.addArg(queryMaxLod);
                    } else {
                        whereClause.append("(lod_version = ? AND min_lod <= ?)");
                        whereClause.addArg(defn.lodVersion);
                        whereClause.addArg(queryMaxLod);
                    }
                    
                    // XXX - make this more dynamic
                    indexedSpatialFilter &= (queryMaxLod>2);
                }                
            } else {
                if(!Double.isNaN(params.minResolution)) {
                    final int queryMinLod = OSMUtils.mapnikTileLevel(params.minResolution);
                    if(defn.maxLod < queryMinLod)
                        return false;
                }
                if(!Double.isNaN(params.maxResolution)) {
                    final int queryMaxLod = OSMUtils.mapnikTileLevel(params.maxResolution);
                    if(defn.minLod > queryMaxLod)
                        return false;
                    
                    // XXX - make this more dynamic
                    indexedSpatialFilter &= (queryMaxLod>2);
                }   
            }
        }

        if(params.visibleOnly) {
            this.validateInfo();
            if(defn.visibleCheck) {
                whereClause.beginCondition();
                if(defn.visible) {
                    whereClause.append("(features.visible_version != ? OR (features.visible_version = ? AND features.visible = 1))");
                    whereClause.addArg(defn.visibleVersion);
                    whereClause.addArg(defn.visibleVersion);
                } else {
                    whereClause.append("(features.visible_version = ? AND features.visible = 1)");
                    whereClause.addArg(defn.visibleVersion);
                }
            } else if(!defn.visible) {
                return false;
            } // else - no check required and no features deviate from global
        }

        if(params.providers != null) {
            // handled upstream
        }
        if(params.types != null) {
            // handled upstream
        }
        if(params.featureSets != null) {
            // handled upstream
        }
        if(params.featureSetIds != null) {
            // handled upstream
        }
        if(params.featureNames != null) {
            whereClause.beginCondition();
            whereClause.appendIn("features.name", params.featureNames);
        }
        if(params.featureIds != null) {
            whereClause.beginCondition();
            Collection<BindArgument> args = new LinkedList<BindArgument>();
            for(Long fid : params.featureIds)
                args.add(new BindArgument(fid.longValue()));
            whereClause.appendIn2("features.fid", args);
        }
        if(params.spatialFilter != null) {
            appendSpatialFilter(params.spatialFilter,
                                whereClause,
                                indexedSpatialFilter &&
                                  this.spatialIndexEnabled,
                                params.limit);
        }
        
        whereClause.beginCondition();
        whereClause.append("fsid = ?");
        whereClause.addArg(defn.fsid);

        return true;
    }

    /**************************************************************************/
     
    private static boolean appendOrder(FeatureQueryParameters.Order order, StringBuilder sql, LinkedList<BindArgument> args, boolean first) {
        if(order instanceof FeatureQueryParameters.FeatureId) {
            if(!first) sql.append(",");
            sql.append(" features.fid ASC");
            return true;
        } else if(order instanceof FeatureQueryParameters.FeatureName) {
            if(!first) sql.append(",");
            sql.append(" features.name ASC");
            return true;
        } else if(order instanceof FeatureQueryParameters.FeatureSet) {
            // order by FS is handled upstream
            return false;
        } else if(order instanceof FeatureQueryParameters.Distance) {
            FeatureQueryParameters.Distance distance = (FeatureQueryParameters.Distance)order;
            
            if(!first) sql.append(",");
            sql.append(" Distance(features.geometry, MakePoint(?, ?, 4326), 1) ASC");
            args.add(new BindArgument(distance.point.getLongitude()));
            args.add(new BindArgument(distance.point.getLatitude()));
            return true;
        } else if(order instanceof FeatureQueryParameters.Resolution) {
            if(!first) sql.append(",");
            sql.append(" features.max_lod DESC");
            return true;
        } else {
            // XXX - 
            return false;
        }
    }

    private static void appendSpatialFilter(FeatureQueryParameters.SpatialFilter filter, WhereClauseBuilder whereClause, boolean spatialFilterEnabled, int limit) {
        if(filter instanceof FeatureQueryParameters.RadiusSpatialFilter) {
            FeatureQueryParameters.RadiusSpatialFilter radius = (FeatureQueryParameters.RadiusSpatialFilter)filter;

            whereClause.beginCondition();
            whereClause.append("Distance(MakePoint(?, ?, 4326), features.geometry, 1) <= ?");
            whereClause.addArg(radius.point.getLongitude());
            whereClause.addArg(radius.point.getLatitude());
            whereClause.addArg(radius.radius);
        } else if(filter instanceof FeatureQueryParameters.RegionSpatialFilter) {
            FeatureQueryParameters.RegionSpatialFilter region = (FeatureQueryParameters.RegionSpatialFilter)filter;

            String select;
            if(spatialFilterEnabled) {
                select = "features.ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = \'features\' AND search_frame = BuildMbr(?, ?, ?, ?, 4326)";
                if(limit > 0)
                    select += " LIMIT " + limit;
                select += ")";
            } else {
                select = "Intersects(BuildMbr(?, ?, ?, ?, 4326), features.geometry) = 1";
            }
            whereClause.beginCondition();
            whereClause.append("(" + select + " OR " + select + " OR " + select + ")");

            // Scan all possible regions for features
            // Features which cross the IDL have unwrapped (>180 or <-180) longitude values
            double minLng = region.upperLeft.getLongitude() - 360;
            double maxLng = region.lowerRight.getLongitude() - 360;
            for (int i = 0; i < 3; i++) {
                whereClause.addArg(minLng);
                whereClause.addArg(region.upperLeft.getLatitude());
                whereClause.addArg(maxLng);
                whereClause.addArg(region.lowerRight.getLatitude());
                minLng += 360;
                maxLng += 360;
            }
        } else {
            // XXX - 
        }
    }

    private static AttributeSpec insertAttrSchema(InsertContext ctx, DatabaseIface database, String key, AttributeSet metadata) {
        Class<?> type = metadata.getAttributeType(key);
        Integer typeCode = ATTRIB_TYPES.get(type);
        if(typeCode == null) {
            Log.w(ABS_TAG, "Skipping attribute " + key + " with unsupported type " + type);
            return null;
        }
        try {
            if(ctx.insertAttributeSchemaStatement == null)
                ctx.insertAttributeSchemaStatement = database.compileStatement("INSERT INTO attribs_schema (name, coding) VALUES (?, ?)");
            ctx.insertAttributeSchemaStatement.bind(1, key);
            ctx.insertAttributeSchemaStatement.bind(2, typeCode.intValue());
            ctx.insertAttributeSchemaStatement.execute();
            ctx.insertAttributeSchemaStatement.clearBindings();
        } finally {
            if(ctx.insertAttributeSchemaStatement != null)
                ctx.insertAttributeSchemaStatement.clearBindings();
        }
        
        return new AttributeSpec(key, Databases.lastInsertRowId(database), typeCode.intValue());
    }

    protected static void encodeAttributes(FDB impl, InsertContext ctx, AttributeSet metadata) {
        DataOutputStream dos = null;
        try {
            dos = new DataOutputStream(ctx.codedAttribs);
            
            Set<String> keys = metadata.getAttributeNames();
            
            dos.writeInt(1); // version
            dos.writeInt(keys.size()); // number of entries

            AttributeSpec schemaSpec;
            for(String key : keys) {
                schemaSpec = impl.keyToAttrSchema.get(key);
                if(schemaSpec == null) {
                    schemaSpec = insertAttrSchema(ctx, impl.database, key, metadata);
                    if(schemaSpec == null)
                        continue;
                    impl.keyToAttrSchema.put(key, schemaSpec);
                    impl.idToAttrSchema.put(Long.valueOf(schemaSpec.id), schemaSpec);
                } else {
                    Class<?> type = metadata.getAttributeType(key);
                    Integer typeCode = ATTRIB_TYPES.get(type);
                    if(typeCode == null) {
                        Log.w(ABS_TAG, "Skipping attribute " + key + " with unsupported type " + type);
                        continue;
                    }
                    
                    // add a secondary type for the key as a new schema row
                    if(schemaSpec.type != typeCode.intValue()) {
                        AttributeSpec secondarySchema = schemaSpec.secondaryDefs.get(typeCode);
                        if(secondarySchema == null) {
                            secondarySchema = insertAttrSchema(ctx, impl.database, key, metadata);
                            if(secondarySchema == null)
                                continue;
                            schemaSpec.secondaryDefs.put(typeCode, secondarySchema);
                            impl.idToAttrSchema.put(Long.valueOf(secondarySchema.id), secondarySchema);

                            schemaSpec = secondarySchema;
                        }
                    }
                }

                dos.writeInt((int)schemaSpec.id);
                if(schemaSpec.type != 5) {
                    schemaSpec.coder.encode(dos, metadata, key);
                } else {
                    // recurse
                    dos.flush();
                    encodeAttributes(impl, ctx, metadata.getAttributeSetAttribute(key));
                    dos = new DataOutputStream(ctx.codedAttribs);
                }
            }
        } catch(IOException e) {
            Log.e(ABS_TAG, "Failed to encode feature metadata", e);
        } finally {
            if(dos != null)
                try {
                    dos.flush();
                } catch(IOException ignored) {}
        }
    }
    
    private static AttributeSet decodeAttributes(byte[] attribsBlob, Map<Long, AttributeSpec> schema) {
        if(attribsBlob == null)
            return null;

        DataInputStream dis = null;
        try {
            dis = new DataInputStream(new ByteArrayInputStream(attribsBlob));
            return decodeAttributesImpl(dis, schema);
        } finally {
            if(dis != null)
                try {
                    dis.close();
                } catch(IOException ignored) {}
        }
    }

    private static AttributeSet decodeAttributesImpl(DataInputStream dis, Map<Long, AttributeSpec> schema) {
        try {
            final int version = dis.readInt(); // version
            if(version != 1) {
                Log.e(ABS_TAG, "Bad AttributeSet coding version: " + version);
                return null;
            }

            final int numKeys = dis.readInt(); // number of entries

            AttributeSet retval = new AttributeSet();

            AttributeSpec schemaSpec;
            for(int i = 0; i < numKeys; i++) {
                final int schemaSpecId = dis.readInt();
                schemaSpec = schema.get(Long.valueOf(schemaSpecId));
                if(schemaSpec == null) {
                    Log.e(ABS_TAG,  "Unable to located AttributeSpec schema ID " + schemaSpecId);
                    return null;
                }

                if(schemaSpec.type != 5) {
                    schemaSpec.coder.decode(retval, dis, schemaSpec.key);
                } else {
                    retval.setAttribute(schemaSpec.key, decodeAttributesImpl(dis, schema));
                }
            }
            
            return retval;
        } catch(IOException e) {
            Log.e(ABS_TAG, "Failed to decode feature metadata", e);
            return null;
        }
    }

    /**************************************************************************/
    
    static class AttributeSpec {
        private final static Map<Class<?>, AttributeCoder> CLAZZ_TO_CODER = new HashMap<Class<?>, AttributeCoder>();
        static {
            CLAZZ_TO_CODER.put(Integer.TYPE, AttributeCoder.INT);
            CLAZZ_TO_CODER.put(Long.TYPE, AttributeCoder.LONG);
            CLAZZ_TO_CODER.put(Double.TYPE, AttributeCoder.DOUBLE);
            CLAZZ_TO_CODER.put(String.class, AttributeCoder.STRING);
            CLAZZ_TO_CODER.put(byte[].class, AttributeCoder.BINARY);
            CLAZZ_TO_CODER.put(AttributeSet.class, null);
            CLAZZ_TO_CODER.put(int[].class, AttributeCoder.INT_ARRAY);
            CLAZZ_TO_CODER.put(long[].class, AttributeCoder.LONG_ARRAY);
            CLAZZ_TO_CODER.put(double[].class, AttributeCoder.DOUBLE_ARRAY);
            CLAZZ_TO_CODER.put(String[].class, AttributeCoder.STRING_ARRAY);
            CLAZZ_TO_CODER.put(byte[][].class, AttributeCoder.BINARY_ARRAY);      
        }
        private final static Map<Integer, AttributeCoder> TYPECODE_TO_CODER = new HashMap<Integer, AttributeCoder>();
        static {
            for(Map.Entry<Class<?>, AttributeCoder> entry : CLAZZ_TO_CODER.entrySet())
                TYPECODE_TO_CODER.put(ATTRIB_TYPES.get(entry.getKey()), entry.getValue());
        }

        final String key;
        final long id;
        final int type;
        final AttributeCoder coder;
        
        final Map<Integer, AttributeSpec> secondaryDefs;
        
        AttributeSpec(String key, long id, int type) {
            this.key = key;
            this.id = id;
            this.type = type;
            this.coder = TYPECODE_TO_CODER.get(Integer.valueOf(type));
            
            this.secondaryDefs = new HashMap<Integer, AttributeSpec>();
        }
    }
    
    static interface AttributeCoder {

        
        public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException;
        public void decode(AttributeSet attr, DataInputStream dos, String key) throws IOException;
        
        public static final AttributeCoder INT = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                dos.writeInt(attr.getIntAttribute(key));                
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                attr.setAttribute(key, dis.readInt());   
            }
        };
        
        public static final AttributeCoder LONG = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                dos.writeLong(attr.getLongAttribute(key));                
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                attr.setAttribute(key, dis.readLong());   
            }
        };
        
        public static final AttributeCoder DOUBLE = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                dos.writeDouble(attr.getDoubleAttribute(key));                
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                attr.setAttribute(key, dis.readDouble());   
            }
        };
        
        public static final AttributeCoder STRING = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                dos.writeUTF(attr.getStringAttribute(key));                
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                attr.setAttribute(key, dis.readUTF());   
            }
        };
        
        public static final AttributeCoder BINARY = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                byte[] blob = attr.getBinaryAttribute(key);
                dos.writeInt(blob != null ? blob.length : -1);
                if(blob != null)
                    dos.write(blob);
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                int blobLen = dis.readInt();
                if(blobLen < 0) {
                    attr.setAttribute(key, (byte[])null);
                } else if(blobLen == 0) {
                    attr.setAttribute(key, new byte[0]);
                } else {
                    byte[] blob = new byte[blobLen];

                    final int numRead = dis.read(blob);
                    if (numRead != blobLen) 
                         Log.e(ABS_TAG, "did not completely decode the binary attribute, expected: " + blobLen + " received: " + numRead);

                    attr.setAttribute(key, blob);
                }
            }
        };
        
        public static final AttributeCoder INT_ARRAY = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                final int[] arr = attr.getIntArrayAttribute(key);
                final int length = (arr != null) ? arr.length : -1; 
                dos.writeInt(length);
                for(int i = 0; i < length; i++)
                    dos.writeInt(arr[i]);
                        
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                final int arrLen = dis.readInt();
                if(arrLen < 0) {
                    attr.setAttribute(key, (int[])null);
                } else if(arrLen == 0) {
                    attr.setAttribute(key, new int[0]);
                } else {
                    int[] arr = new int[arrLen];
                    for(int i = 0; i < arrLen; i++)
                        arr[i] = dis.readInt();
                    attr.setAttribute(key, arr);
                }
            }
        };
        
        public static final AttributeCoder LONG_ARRAY = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                final long[] arr = attr.getLongArrayAttribute(key);
                final int length = (arr != null) ? arr.length : -1; 
                dos.writeInt(length);
                for(int i = 0; i < length; i++)
                    dos.writeLong(arr[i]);
                        
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                final int arrLen = dis.readInt();
                if(arrLen < 0) {
                    attr.setAttribute(key, (long[])null);
                } else if(arrLen == 0) {
                    attr.setAttribute(key, new long[0]);
                } else {
                    long[] arr = new long[arrLen];
                    for(int i = 0; i < arrLen; i++)
                        arr[i] = dis.readLong();
                    attr.setAttribute(key, arr);
                }
            }
        };
        
        public static final AttributeCoder DOUBLE_ARRAY = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                final double[] arr = attr.getDoubleArrayAttribute(key);
                final int length = (arr != null) ? arr.length : -1; 
                dos.writeInt(length);
                for(int i = 0; i < length; i++)
                    dos.writeDouble(arr[i]);
                        
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                final int arrLen = dis.readInt();
                if(arrLen < 0) {
                    attr.setAttribute(key, (double[])null);
                } else if(arrLen == 0) {
                    attr.setAttribute(key, new double[0]);
                } else {
                    double[] arr = new double[arrLen];
                    for(int i = 0; i < arrLen; i++)
                        arr[i] = dis.readDouble();
                    attr.setAttribute(key, arr);
                }
            }
        };
        
        public static final AttributeCoder STRING_ARRAY = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                final String[] arr = attr.getStringArrayAttribute(key);
                final int length = (arr != null) ? arr.length : -1; 
                dos.writeInt(length);
                for(int i = 0; i < length; i++)
                    dos.writeUTF(arr[i]);
                        
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                final int arrLen = dis.readInt();
                if(arrLen < 0) {
                    attr.setAttribute(key, (String[])null);
                } else if(arrLen == 0) {
                    attr.setAttribute(key, new String[0]);
                } else {
                    String[] arr = new String[arrLen];
                    for(int i = 0; i < arrLen; i++)
                        arr[i] = dis.readUTF();
                    attr.setAttribute(key, arr);
                }
            }
        };
        
        public static final AttributeCoder BINARY_ARRAY = new AttributeCoder() {
            @Override
            public void encode(DataOutputStream dos, AttributeSet attr, String key) throws IOException {
                final byte[][] arr = attr.getBinaryArrayAttribute(key);
                final int length = (arr != null) ? arr.length : -1; 
                dos.writeInt(length);
                for(int i = 0; i < length; i++) {
                    if(arr[i] != null) {
                        dos.writeInt(arr[i].length);
                        dos.write(arr[i]);
                    } else {
                        dos.writeInt(-1);
                    }
                }
                        
            }

            @Override
            public void decode(AttributeSet attr, DataInputStream dis, String key) throws IOException {
                final int arrLen = dis.readInt();
                if(arrLen < 0) {
                    attr.setAttribute(key, (byte[][])null);
                } else if(arrLen == 0) {
                    attr.setAttribute(key, new byte[0][]);
                } else {
                    byte[][] arr = new byte[arrLen][];
                    for(int i = 0; i < arrLen; i++) {
                        int len = dis.readInt();
                        if(len < 0) {
                            arr[i] = null;
                        } else if(len == 0) {
                            arr[i] = new byte[0];
                        } else {
                            arr[i] = new byte[len];
                            int numRead = dis.read(arr[i]);
                            if (numRead != len) {
                                 Log.e(ABS_TAG, "did not completely decode the binary array attribute, expected: " + len + " received: " + numRead);
                            }
                        }
                    }
                    attr.setAttribute(key, arr);
                }
            }
        };
    }

    /**************************************************************************/
    // FeatureCursorImpl

    private final class FeatureCursorImpl extends CursorWrapper implements FeatureCursor {

        private final int idCol;
        private final int fsidCol;
        private final int nameCol;
        private final int geomCol;
        private final int styleCol;
        private final int attribsCol;
        private final int versionCol;
        
        private Feature row;

        protected FeatureCursorImpl(CursorIface filter, int idCol, int fsidCol, int versionCol, int nameCol, int geomCol, int styleCol, int attribsCol) {
            super(filter);
            
            this.idCol = idCol;
            this.fsidCol = fsidCol;
            this.versionCol = versionCol;
            this.nameCol = nameCol;
            this.geomCol = geomCol;
            this.styleCol = styleCol;
            this.attribsCol = attribsCol;
            
            this.row = null;
        }

        @Override
        public Feature get() {
            if(this.row == null) {
                final long fid = this.getId();
                final long fsid = this.getFsid();
                final long version = this.getVersion();
                
                final String name = this.getName();
                final byte[] geomBlob = (byte[])this.getRawGeometry();
                final String ogrStyle = (String)this.getRawStyle();
                final AttributeSet attribs = this.getAttributes();
    
                Geometry geom = null;
                if(geomBlob != null)
                    geom = GeometryFactory.parseSpatiaLiteBlob(geomBlob);
                Style style = null;
                if(ogrStyle != null)
                    style = FeatureStyleParser.parse2(ogrStyle);
    
                this.row = new Feature(fsid, fid, name, geom, style, attribs, FeatureDataStore2.TIMESTAMP_NONE, version);
            }

            return this.row;
        }

        @Override
        public Object getRawGeometry() {
            if(this.geomCol == -1)
                return null;
            return this.getBlob(this.geomCol);
        }

        @Override
        public int getGeomCoding() {
            return GEOM_SPATIALITE_BLOB;
        }

        @Override
        public String getName() {
            if(this.nameCol == -1)
                return null;
            return this.getString(this.nameCol);
        }

        @Override
        public int getStyleCoding() {
            return STYLE_OGR;
        }

        @Override
        public Object getRawStyle() {
            if(this.styleCol == -1)
                return null;
            return this.getString(this.styleCol);
        }

        @Override
        public AttributeSet getAttributes() {
            if(this.attribsCol == -1)
                return null;
            
            FDB.this.validateAttributeSchema();
            return decodeAttributes(this.getBlob(this.attribsCol), FDB.this.idToAttrSchema);
        }

        @Override
        public long getId() {
            return this.getLong(this.idCol);
        }

        @Override
        public long getVersion() {
            return this.getLong(this.versionCol);
        }
        
        @Override
        public long getFsid() {
            if(this.fsidCol == -1)
                return FEATURESET_ID_NONE;
            return this.getLong(this.fsidCol);
        }

        @Override
        public boolean moveToNext() {
            this.row = null;
            return super.moveToNext();
        }
    }

    /**************************************************************************/
    // FeatureSetCursorImpl

    private class FeatureSetCursorImpl extends IteratorCursor<FeatureSetDefn> implements FeatureSetCursor {
        public FeatureSetCursorImpl(Collection<FeatureSetDefn> rows) {
            super(rows.iterator());
        }

        @Override
        public FeatureSet get() {
            return FDB.this.getFeatureSetImpl(this.getRowData());
        }        
    }
    
    /**************************************************************************/
    // InsertContext

    static class InsertContext implements Disposable {
        Map<String, Long> styleIds;
        StatementIface insertFeatureBlobStatement;
        StatementIface insertFeatureWktStatement;
        StatementIface insertFeatureWkbStatement;
        StatementIface insertStyleStatement;
        StatementIface insertAttributesStatement;
        StatementIface insertAttributeSchemaStatement;
        BindArgument insertGeomArg;
        ByteArrayOutputStream codedAttribs;
        
        public InsertContext() {
            this.styleIds = new HashMap<String, Long>();
            this.insertGeomArg = new BindArgument();
            this.codedAttribs = new ByteArrayOutputStream(8);
        }

        @Override
        public void dispose() {
            this.styleIds = null;
            if(this.insertFeatureBlobStatement != null) {
                this.insertFeatureBlobStatement.close();
                this.insertFeatureBlobStatement = null;
            }
            if(this.insertFeatureWktStatement != null) {
                this.insertFeatureWktStatement.close();
                this.insertFeatureWktStatement = null;
            }
            if(this.insertFeatureWkbStatement != null) {
                this.insertFeatureWkbStatement.close();
                this.insertFeatureWkbStatement = null;
            }
            if(this.insertStyleStatement != null) {
                this.insertStyleStatement.close();
                this.insertStyleStatement = null;
            }
            if(this.insertAttributesStatement != null) {
                this.insertAttributesStatement.close();
                this.insertAttributesStatement = null;
            }
            if(this.insertAttributeSchemaStatement != null) {
                this.insertAttributeSchemaStatement.close();
                this.insertAttributeSchemaStatement = null;
            }
            this.insertGeomArg = null;
            this.codedAttribs = null;
        }
    }
    
    /**************************************************************************/
    // Builder

    static abstract class Builder {
        InsertContext ctx;
        FDB db;
        
        protected Builder(FDB db) {
            this.db = db;
            this.ctx = new InsertContext();
        }
        
        public void createIndices() {
            this.db.createIndicesNoSync();
        }

        public void close() {
            this.ctx.dispose();
            this.db.dispose();
        }

        public void beginBulkInsertion() {
            this.db.database.beginTransaction();
        }
        
        public void endBulkInsertion(boolean commit) {
            if(commit)
                this.db.database.setTransactionSuccessful();
            this.db.database.endTransaction();
        }

        protected long insertFeatureSet(String provider, String type, String name, double minResolution, double maxResolution) {
            FeatureSet[] ref = new FeatureSet[1];
            this.db.insertFeatureSetImpl(provider, type, name, minResolution, maxResolution, ref);
            return ref[0].getId();
        }

        protected void insertFeatureSet(long fsid, String provider, String type, String name) {
            this.db.insertFeatureSetImpl(fsid, provider, type, name, Double.MAX_VALUE, 0.0d);
        }

        protected void insertFeature(long fsid, FeatureDefinition def) {
            this.db.insertFeatureImpl(this.ctx, fsid, def);
        }
        
        protected void insertFeature(long fsid, String name, Geometry geometry, Style style, AttributeSet attribs) {
            this.insertFeature(fsid, new DefaultFeatureDefinition(name, geometry, style, attribs));
        }
        
        protected void updateFeatureSet(long fsid, boolean visible, double minResolution, double maxResolution) {
            this.db.updateFeatureSetImpl(fsid, minResolution, maxResolution);
            this.db.setFeatureSetVisibleImpl(fsid, visible);
        }
        
        protected void deleteFeatureSet(long fsid) {
            this.db.deleteFeatureSetImpl(fsid);
        }
    }
    
    /**************************************************************************/
    // DefaultFeatureDefinition

    private static class DefaultFeatureDefinition implements FeatureDefinition {
        private Feature impl;
        
        public DefaultFeatureDefinition(String name, Geometry geometry, Style style, AttributeSet attribs) {
            this.impl = new Feature(name, geometry, style, attribs);
        }

        @Override
        public Object getRawGeometry() {
            return this.impl.getGeometry();
        }

        @Override
        public int getGeomCoding() {
            return GEOM_ATAK_GEOMETRY;
        }

        @Override
        public String getName() {
            return this.impl.getName();
        }

        @Override
        public int getStyleCoding() {
            return STYLE_ATAK_STYLE;
        }

        @Override
        public Object getRawStyle() {
            return this.impl.getStyle();
        }

        @Override
        public AttributeSet getAttributes() {
            return this.impl.getAttributes();
        }

        @Override
        public Feature get() {
            return new Feature(this);
        }
    }
}
