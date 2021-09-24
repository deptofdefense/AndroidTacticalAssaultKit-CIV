package com.atakmap.map.layer.feature;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.content.BindArgument;
import com.atakmap.content.CatalogCurrency;
import com.atakmap.content.CatalogCurrencyRegistry;
import com.atakmap.content.WhereClauseBuilder;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.FilteredCursor;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.FeatureDataSource.Content;
import com.atakmap.map.layer.feature.datastore.FeatureSpatialDatabase;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.raster.osm.OSMUtils;

/** @deprecated use {@link PersistentDataSourceFeatureDataStore2} */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class PersistentDataSourceFeatureDataStore extends AbstractDataSourceFeatureDataStore {

    private final static String CURRENCY_NAME = "PersistentDataSourceFeatureDataStore.Currency";
    private final static int CURRENCY_VERSION = 2;

    private String uri;
    private FeatureSpatialDatabase spatialDb;
    private DatabaseIface impl;
    private boolean inTransaction;
    private Map<Long, GroupVisibleInfo> groupVisibility;
    private boolean groupVisibilityDirty;

    public PersistentDataSourceFeatureDataStore(File file) {
        final CatalogCurrencyRegistry currency = new CatalogCurrencyRegistry();
        currency.register(new ValidateCurrency());

        this.uri = file.getAbsolutePath();
        this.spatialDb = new FeatureSpatialDatabase(file, currency);
        this.impl = this.spatialDb.getDatabase();
        
        this.groupVisibility = new HashMap<Long, GroupVisibleInfo>();
        this.groupVisibilityDirty = true;
    }

    private void refreshGroupVisibilityInfoNoSync() {
        CursorIface result = null;
        try {
            result = this.impl.query("SELECT id, visible, visible_version, visible_check FROM groups", null);
            while(result.moveToNext()) {
                this.groupVisibility.put(Long.valueOf(result.getLong(0)),
                                         new GroupVisibleInfo(
                                                 (result.getInt(1) != 0),
                                                 (result.getInt(3) != 0),
                                                 result.getInt(2)));
            }
            this.groupVisibilityDirty = false;
        } finally {
            if(result != null)
                result.close();
        }
    }
    
    @Override
    public Feature getFeature(long featureId) {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureIds = Collections.singleton(Long.valueOf(featureId));
        
        FeatureCursor result = null;
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
        StringBuilder sql = new StringBuilder("SELECT Geometry.id, Geometry.group_id, Geometry.name, ");
        if(params != null && params.ops != null) {
            StringBuilder geomStr = new StringBuilder("Geometry.geom");            
            for(FeatureQueryParameters.SpatialOp op : params.ops) {
                if(op instanceof FeatureQueryParameters.Simplify) {
                    geomStr.insert(0, "Simplify(");
                    geomStr.append(", ");
                    geomStr.append(((FeatureQueryParameters.Simplify)op).distance);
                    geomStr.append(")");
                } else if(op instanceof FeatureQueryParameters.Buffer) {
                    geomStr.insert(0, "Buffer(");
                    geomStr.append(", ");
                    geomStr.append(((FeatureQueryParameters.Buffer)op).distance);
                    geomStr.append(")");
                }
            }
            sql.append(geomStr);
        } else {
            sql.append("Geometry.geom");
        }
        sql.append(", Style.style_rep, NULL");
        
        Collection<BindArgument> args = null;

        final int idCol = 0;
        final int groupIdCol = 1;
        final int nameCol = 2;
        final int geomCol = 3;
        final int styleCol = 4;
        final int attribsCol = 5;
        
        int extrasCol = attribsCol + 1;
        
        int groupVisibleVersionCol = -1;
        int visibleCol = -1;
        
        // if 'visibleOnly' selection, we will need additional columns to
        // determine visibility
        if(params != null && params.visibleOnly) {
            sql.append(", Geometry.group_visible_version, Geometry.visible");
            groupVisibleVersionCol = extrasCol++;
            visibleCol = extrasCol++;
        }

        sql.append(" FROM Geometry LEFT JOIN Style ON Geometry.style_id = Style.id");
        
        Map<Long, GroupVisibleInfo> groupVisibleInfo = new HashMap<Long, GroupVisibleInfo>();
        if(params != null) {
            LinkedList<BindArgument> largs = new LinkedList<BindArgument>();

            WhereClauseBuilder whereClause = new WhereClauseBuilder();
            final boolean emptyResults = !buildParamsWhereClause(params, whereClause, groupVisibleInfo);
            String where = whereClause.getSelection();
            if(where != null) {
                sql.append(" WHERE ");
                sql.append(where);
                largs.addAll(whereClause.getBindArgs());
            }

            if(!emptyResults) {
                if(params.order != null) {
                    sql.append(" ORDER BY");
                    boolean first = true;
                    for(FeatureQueryParameters.Order order : params.order) {
                        appendOrder(order, sql, largs, first);
                        first = false;
                    }
                }
                
                if(!params.visibleOnly && params.limit > 0) {
                    sql.append(" LIMIT ? OFFSET ?");
                    largs.add(new BindArgument(params.limit));
                    largs.add(new BindArgument(params.offset));
                }
            } else {
                sql.append(" LIMIT 0");
            }

            if(!largs.isEmpty())
                args = largs;
        }

        //System.out.println("QUERY FEATURES: " + sql);
        //System.out.println("ARGS: " + args);
        
        CursorIface result = null;
        try {
            result = BindArgument.query(this.impl, sql.toString(), args);
            
            // filter the result set if visible only
            if(params != null && params.visibleOnly) {
                result = new VisibleOnlyFeatureFilter(result,
                                               groupIdCol,
                                               groupVisibleVersionCol,
                                               visibleCol,
                                               groupVisibleInfo,
                                               params.limit,
                                               params.offset);
            }
            
            final FeatureCursor retval = 
                   new FeatureCursorImpl(result,
                                         idCol,
                                         groupIdCol,
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

    @Override
    public int queryFeaturesCount(FeatureQueryParameters params) {
        // XXX - would be nice to handle as single query
        if(params != null && params.visibleOnly) {
            FeatureCursor result = null;
            try {
                result = this.queryFeatures(params);
                int retval = 0;
                while(result.moveToNext())
                    retval++;
                return retval;
            } finally {
                if(result != null)
                    result.close();
            }
        }

        StringBuilder sql = new StringBuilder("SELECT Count(1) FROM Geometry");
        Collection<BindArgument> args = null;
        if(params != null) {
            WhereClauseBuilder whereClause = new WhereClauseBuilder();
            if(!buildParamsWhereClause(params, whereClause, null))
                return 0;

            if(whereClause != null) {
                String where = whereClause.getSelection();
                if(where != null) {
                    sql.append(" WHERE ");
                    sql.append(where);
                    args = whereClause.getBindArgs();
                }
            }
        }
        
        CursorIface result = null;
        try {
            result = BindArgument.query(this.impl, sql.toString(), args);
            if(!result.moveToNext())
                return 0;
            return result.getInt(0);
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public FeatureSet getFeatureSet(long featureSetId) {
        CursorIface result = null;
        try {
            result = this.impl.query("SELECT id, name, provider, type, min_lod, max_lod FROM groups WHERE id = ? LIMIT 1", new String[] {String.valueOf(featureSetId)});
            if(!result.moveToNext())
                return null;
            // XXX - min/max GSD
            return new FeatureSet(this,
                                  result.getLong(0),
                                  result.getString(2),
                                  result.getString(3),
                                  result.getString(1),
                                  OSMUtils.mapnikTileResolution(result.getInt(4)),
                                  OSMUtils.mapnikTileResolution(result.getInt(5)),
                                  1);
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Override
    public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
        StringBuilder sql = new StringBuilder("SELECT id, name, provider, type, min_lod, max_lod FROM groups");
        Collection<BindArgument> args = null;
        if(params != null) {
            WhereClauseBuilder where = new WhereClauseBuilder();
            if(params.providers != null) {
                where.beginCondition();
                where.appendIn("provider", params.providers);
            }
            if(params.types != null) {
                where.beginCondition();
                where.appendIn("type", params.types);
            }
            if(params.names != null) {
                where.beginCondition();
                where.appendIn("name", params.names);
            }
            if(params.ids != null) {
                where.beginCondition();
                Collection<BindArgument> arg = new LinkedList<BindArgument>();
                for(Long id : params.ids)
                    arg.add(new BindArgument(id.longValue()));
                where.appendIn2("id", arg);
            }
            
            String selection = where.getSelection();
            if(selection != null) {
                sql.append(" WHERE ");
                sql.append(selection);
                args = where.getBindArgs();
            }
        }
        
        sql.append(" ORDER BY name");
        
        if(params != null && !params.visibleOnly && params.limit != 0) {
            sql.append(" LIMIT ? OFFSET ?");
            if(args == null) {
                args = new LinkedList<BindArgument>();
            } else {
                args = new LinkedList<BindArgument>(args);
            }
            args.add(new BindArgument(params.limit));
            args.add(new BindArgument(params.offset));
        }

        CursorIface result = BindArgument.query(this.impl,
                                                sql.toString(),
                                                args);
        if(params != null && params.visibleOnly)
            result = new VisibleOnlyFeatureSetCursor(result, 0, params.limit, params.offset);

        return new FeatureSetCursorImpl(result,
                                        0, 2, 3, 1, 4, 5);
    }
    
    @Override
    public int queryFeatureSetsCount(FeatureSetQueryParameters params) {
        // XXX - brute force
        if(params != null && params.visibleOnly) {
            FeatureSetCursor result = null;
            try {
                result = this.queryFeatureSets(params);
                int retval = 0;
                while(result.moveToNext())
                    retval++;
                return retval;
            } finally {
                if(result != null)
                    result.close();
            }
        }

        CursorIface result = null;
        try {
            StringBuilder sql = new StringBuilder("SELECT Count(1) FROM groups");
            Collection<BindArgument> args = null;
            if(params != null) {
                WhereClauseBuilder where = new WhereClauseBuilder();
                if(params.providers != null) {
                    where.beginCondition();
                    where.appendIn("provider", params.providers);
                }
                if(params.types != null) {
                    where.beginCondition();
                    where.appendIn("type", params.types);
                }
                if(params.names != null) {
                    where.beginCondition();
                    where.appendIn("name", params.names);
                }
                if(params.ids != null) {
                    where.beginCondition();
                    Collection<String> arg = new LinkedList<String>();
                    for(Long id : params.ids)
                        arg.add(id.toString());
                    where.appendIn("id", arg);
                }
                
                String selection = where.getSelection();
                if(selection != null) {
                    sql.append(" WHERE ");
                    sql.append(selection);
                    args = where.getBindArgs();
                }
            }
            
            if(params != null && params.limit != 0) {
                sql.append("LIMIT ? OFFSET ?");
                if(args == null) {
                    args = new LinkedList<BindArgument>();
                } else {
                    args = new LinkedList<BindArgument>(args);
                }
                args.add(new BindArgument(params.limit));
                args.add(new BindArgument(params.offset));
            }

            result = BindArgument.query(this.impl, sql.toString(), args);
            if (result.moveToNext())
                return result.getInt(0);
            else
                return 0;
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Override
    public boolean isInBulkModification() {
        return this.inTransaction;
    }

    @Override
    public synchronized boolean isFeatureSetVisible(long setId) {
        CursorIface result;
        
        result = null;
        final int visibleVersion;
        final boolean groupVisible;
        try {
            result = this.impl.query("SELECT visible, visible_check, visible_version FROM groups WHERE id = ?", new String[] {String.valueOf(setId)});
            if(!result.moveToNext())
                return false;
            groupVisible = (result.getInt(0)!=0);
            if(result.getInt(1) == 0)
                return groupVisible;
            visibleVersion = result.getInt(2);
        } finally {
            if(result != null)
                result.close();
        }
        
        // XXX - combine next two into single query
        if(groupVisible) {
            // if the group is marked as visible, the presence any child items
            // that defer visibility to the group make that group visible
            result = null;
            try {
                result = this.impl.query("SELECT 1 FROM Geometry WHERE group_visible_version != ? AND group_id = ? LIMIT 1",
                                new String[] {
                                        String.valueOf(visibleVersion),
                                        String.valueOf(setId)
                                });
                return result.moveToNext();
            } finally {
                if (result != null)
                    result.close();
            }
        }

        // look for any items marked visible that are not deferring to the group
        result = null;
        try {
            result = this.impl.query("SELECT 1 FROM Geometry WHERE visible = 1 AND group_visible_version = ? AND group_id = ? LIMIT 1",
                            new String[] {
                                    String.valueOf(visibleVersion),
                                    String.valueOf(setId)
                            });

            return result.moveToNext();
        } finally {
            if (result != null)
                result.close();
        }
    }
    
    @Override
    public boolean isFeatureVisible(long fid) {
        CursorIface result = null;
        try {
            result = this.impl.query("SELECT groups.visible, groups.visible_check, groups.visible_version, Geometry.visible, Geometry.group_visible_version FROM Geometry LEFT JOIN groups ON Geometry.group_id = groups.id WHERE Geometry.id = ?",
                            new String[] {
                                    String.valueOf(fid)
                            });

            if(!result.moveToNext())
                return false;
            
            final boolean groupVisible = (result.getInt(0)!=0);

            // if visible check is not marked or the feature's visible version
            // doesn't equal the group's, the feature assumes the group's
            // visibility
            if(result.getInt(1) == 0 || result.getInt(2) != result.getInt(4))
                return groupVisible;
            
            // check visible is toggled and feature visible version matches
            // group visible version -- return visibility flag for feature
            return (result.getInt(3)!=0);
        } finally {
            if (result != null)
                result.close();
        }
    }

    @Override
    public synchronized boolean isAvailable() {
        return (this.spatialDb != null);
    }

    @Override
    public synchronized void dispose() {
        if(this.spatialDb != null) {
            this.spatialDb.close();
            this.spatialDb = null;
            this.impl = null;
        }
    }

    @Override
    protected boolean containsImpl(File file) {
        // XXX - 
        if(file instanceof com.atakmap.io.ZipVirtualFile)
            file = ((com.atakmap.io.ZipVirtualFile)file).getZipFile();

        FeatureSpatialDatabase.CatalogCursor result = null;
        try {
            result = this.spatialDb.queryCatalog(file);
            return result.moveToNext();
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    protected File getFileNoSync(long fsid) {
        CursorIface stmt = null;
        try {
            stmt = this.impl.query("SELECT catalog.path FROM groups LEFT JOIN catalog ON groups.file_id = catalog.id WHERE groups.id = ?", new String[] {String.valueOf(fsid)});
            if(!stmt.moveToNext())
                return null;
            return new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(stmt.getString(0)));
        } finally {
            if(stmt != null)
                stmt.close();
        }
    }

    @Override
    protected void addImpl(File file, Content content) throws IOException {
        // XXX - 
        if(file instanceof com.atakmap.io.ZipVirtualFile)
            file = ((com.atakmap.io.ZipVirtualFile)file).getZipFile();

        final long catalogId = this.spatialDb.addCatalogEntry(file, new GenerateCurrency(content));

        long groupId;
        int minLOD;
        int maxLOD;
        Map<Object, Long> styleIdMap = new HashMap<Object, Long>();
        FeatureDataSource.FeatureDefinition defn;
        Long styleId;
        while(content.moveToNext(FeatureDataSource.Content.ContentPointer.FEATURE_SET)) {
            minLOD = OSMUtils.mapnikTileLevel(content.getMinResolution());
            maxLOD = OSMUtils.mapnikTileLevel(content.getMaxResolution());
            groupId = this.spatialDb.insertGroup(catalogId, content.getProvider(), content.getType(), content.getFeatureSetName(), minLOD, maxLOD);
            styleIdMap.clear();

            while(content.moveToNext(FeatureDataSource.Content.ContentPointer.FEATURE)) {
                defn = content.get();
    
                if (defn != null) {
                    styleId = styleIdMap.get(defn.rawStyle);
                    if(styleId == null) {
                        if(defn.styleCoding == FeatureDataSource.FeatureDefinition.STYLE_OGR) {
                            styleId = Long.valueOf(this.spatialDb.insertStyle(catalogId, (String)defn.rawStyle));
                        } else {
                            // XXX - need to convert style to OGR Style specification
                            throw new UnsupportedOperationException("style is not ogr style specification compliant");
                        }
                        styleIdMap.put(defn.rawStyle, styleId);
                    }
                } else { 
                    throw new UnsupportedOperationException("definition is null");
                }
                this.spatialDb.insertFeature(catalogId,
                                             groupId,
                                             defn,
                                             styleId.longValue(),
                                             minLOD,
                                             maxLOD);
            }
            
            // if the group visibility isn't dirty, simply add the new records
            if(!this.groupVisibilityDirty)
                this.groupVisibility.put(Long.valueOf(groupId), new GroupVisibleInfo(true, false, 0));
        }
    }

    @Override
    protected void removeImpl(File file) {
        // XXX - 
        if(file instanceof com.atakmap.io.ZipVirtualFile)
            file = ((com.atakmap.io.ZipVirtualFile)file).getZipFile();

        this.spatialDb.deleteCatalog(file);
        
        this.groupVisibilityDirty = true;
    }

    @Override
    protected synchronized void deleteAllFeatureSetsImpl() {
        this.spatialDb.deleteAll();
        
        this.groupVisibility.clear();
        this.groupVisibilityDirty = false;
    }

    
    @Override
    public synchronized void refresh() {
        final boolean localBulkModify = !this.isInBulkModification();
        if(localBulkModify)
            this.beginBulkModificationImpl();
        boolean success = false;
        try {
            this.spatialDb.validateCatalog();
            success = true;

            this.groupVisibilityDirty = true;

            this.dispatchDataStoreContentChangedNoSync();
        } finally {
            if(localBulkModify)
                this.endBulkModification(success);
        }
    }

    @Override
    protected void setFeatureVisibleImpl(long fid, boolean visible) {
        StatementIface stmt = null;
        try {
            stmt = this.impl.compileStatement("UPDATE Geometry SET visible = ? WHERE id = ?");
            stmt.bind(1, visible ? 1 : 0);
            stmt.bind(2, fid);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }

        synchronized(this) {
            this.groupVisibilityDirty = true;
            this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    @Override
    protected void setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
        StringBuilder sql = new StringBuilder("UPDATE Geometry SET visible = ");
        sql.append(String.valueOf(visible ? 1 : 0));

        String[] args = null;
        
        if(params != null && params.visibleOnly) {

            // clone the input parameters
            params = new FeatureQueryParameters(params);

            WhereClauseBuilder whereClause = new WhereClauseBuilder();
            
            // restrict to groups that may be visible -- this will be groups
            // that are explicitly marked as visible or have the visible_check
            // flag toggled
            whereClause.beginCondition();
            whereClause.append("(visible = 1 OR visible_check > 0)");

            // if the user has specified restriction over group IDs/names,
            // further restrict the selection according to their parameters
            if(params.featureSetIds != null) {
                whereClause.beginCondition();
                whereClause.appendIn("id", WhereClauseBuilder.stringify(params.featureSetIds));
            }
            if(params.featureSets != null) {
                whereClause.beginCondition();
                whereClause.appendIn("name", params.featureSets);
            }

            CursorIface result = null;
            Map<Long, GroupVisibleInfo> groupVisibleInfo = null;
            try {
                result = this.impl.query("SELECT id, visible_version, visible_check, visible FROM groups WHERE " + whereClause.getSelection(), whereClause.getArgs());
                
                groupVisibleInfo = new HashMap<Long, GroupVisibleInfo>();

                while(result.moveToNext()) {
                    groupVisibleInfo.put(Long.valueOf(result.getLong(0)),
                                         new GroupVisibleInfo(
                                                 (result.getInt(3)!=0),
                                                 (result.getInt(2)!=0),
                                                 result.getInt(1)));
                }
            } finally {
                if(result != null)
                    result.close();
            }
            
            // clear any user specified group filter and replace with the
            // result set groups
            params.featureSets = null;
            params.featureSetIds = groupVisibleInfo.keySet();
        }

        if(params != null) {
            LinkedList<String> largs = new LinkedList<String>();

            WhereClauseBuilder whereClause = new WhereClauseBuilder();
            if(params.providers != null) {
                whereClause.beginCondition();
                whereClause.append("Geometry.group_id IN (SELECT id FROM groups WHERE ");
                whereClause.appendIn("provider", params.providers);
                whereClause.append(")");
            }
            if(params.types != null) {
                whereClause.beginCondition();
                whereClause.append("Geometry.group_id IN (SELECT id FROM groups WHERE ");
                whereClause.appendIn("type", params.types);
                whereClause.append(")");
            }
            if(params.featureSets != null) {
                whereClause.beginCondition();
                whereClause.append("Geometry.group_id IN (SELECT id FROM groups WHERE ");
                whereClause.appendIn("name", params.featureSets);
                whereClause.append(")");
            }
            if(params.featureSetIds != null) {
                whereClause.beginCondition();
                whereClause.appendIn("Geometry.group_id", WhereClauseBuilder.stringify(params.featureSetIds));
            }
            if(params.featureNames != null) {
                whereClause.beginCondition();
                whereClause.appendIn("Geometry.name", params.featureNames);
            }
            if(params.featureIds != null) {
                whereClause.beginCondition();
                whereClause.appendIn("Geometry.id", WhereClauseBuilder.stringify(params.featureIds));
            }
            if(!Double.isNaN(params.minResolution)) {
                whereClause.beginCondition();
                whereClause.append("Geometry.max_lod >= ?");
                whereClause.addArg(String.valueOf(OSMUtils.mapnikTileLevel(params.minResolution)));
            }
            if(!Double.isNaN(params.maxResolution)) {
                whereClause.beginCondition();
                whereClause.append("Geometry.min_lod <= ?");
                whereClause.addArg(String.valueOf(OSMUtils.mapnikTileLevel(params.maxResolution)));
            }
            if(params.spatialFilter != null) {
                appendSpatialFilter(params.spatialFilter, whereClause, FeatureSpatialDatabase.SPATIAL_INDEX_ENABLED);
            }

            String where = whereClause.getSelection();
            if(where != null) {
                sql.append(" WHERE ");
                sql.append(where);
                largs.addAll(whereClause.getArgsList());
            }
            
            if(largs.size() > 0)
                args = largs.toArray(new String[0]);
        }

        //System.out.println("UPDATE FEATURES VISIBILITY: " + sql);
        //System.out.println("ARGS: " + java.util.Arrays.toString(args));

        this.impl.execute(sql.toString(), args);
        
        synchronized(this) {
            this.groupVisibilityDirty = true;
            this.dispatchDataStoreContentChangedNoSync();
        }
    }

    @Override
    protected void setFeatureSetVisibleImpl(long setId, boolean visible) {
        StatementIface stmt = null;
        try {
            stmt = this.impl.compileStatement("UPDATE groups SET visible = ? WHERE id = ?");
            stmt.bind(1, visible ? 1 : 0);
            stmt.bind(2, setId);
            
            stmt.execute();
        } finally {
            if(stmt != null)
                stmt.close();
        }
        
        synchronized(this) {
            this.groupVisibilityDirty = true;
            this.dispatchDataStoreContentChangedNoSync();
        }
    }
    
    @Override
    protected void setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible) {
        StringBuilder sql = new StringBuilder("UPDATE groups SET visible = ");
        sql.append(visible ? 1 : 0);
        
        String[] args = null;
        if(params != null) {
            WhereClauseBuilder where = new WhereClauseBuilder();
            if(params.providers != null) {
                where.beginCondition();
                where.appendIn("provider", params.providers);
            }
            if(params.types != null) {
                where.beginCondition();
                where.appendIn("type", params.types);
            }
            if(params.names != null) {
                where.beginCondition();
                where.appendIn("name", params.names);
            }
            if(params.ids != null) {
                where.beginCondition();
                Collection<String> arg = new LinkedList<String>();
                for(Long id : params.ids)
                    arg.add(id.toString());
                where.appendIn("id", arg);
            }
            
            String selection = where.getSelection();
            if(selection != null) {
                sql.append(" WHERE ");
                sql.append(selection);
                args = where.getArgs();
            }
        }

        this.impl.execute(sql.toString(), args);

        synchronized(this) {
            this.groupVisibilityDirty = true;
            this.dispatchDataStoreContentChangedNoSync();
        }
    }

    @Override
    protected void beginBulkModificationImpl() {
        if(this.inTransaction)
            throw new IllegalStateException();
        this.impl.beginTransaction();
        this.inTransaction = true;
    }

    @Override
    protected void endBulkModificationImpl(boolean successful) {
        if(!this.inTransaction)
            throw new IllegalStateException();
        try {
            if(successful)
                this.impl.setTransactionSuccessful();
            this.impl.endTransaction();
        } finally {
            this.inTransaction = false;
        }
    }
    
    @Override
    public String getUri() {
        return this.uri;
    }

    @Override
    public FileCursor queryFiles() {
        return new FileCursorImpl(this.spatialDb.queryCatalog());
    }

    /**************************************************************************/

    protected boolean buildParamsWhereClause(FeatureQueryParameters params, WhereClauseBuilder whereClause, Map<Long, GroupVisibleInfo> groupVisibleInfo) {
        boolean indexedSpatialFilter = (params.spatialFilter != null);

        // if min or max resolution is specified, filter out the groups that do
        // won't meet the LOD requirements
        if(!Double.isNaN(params.maxResolution) || !Double.isNaN(params.minResolution)) {            
            WhereClauseBuilder groupLodWhereClause = new WhereClauseBuilder();
            
            // restrict to groups that may be visible -- this will be groups
            // that are explicitly marked as visible or have the visible_check
            // flag toggled

            if(!Double.isNaN(params.minResolution)) {
                groupLodWhereClause.beginCondition();
                groupLodWhereClause.append("max_lod >= ?");
                groupLodWhereClause.addArg(OSMUtils.mapnikTileLevel(params.minResolution));
            }
            if(!Double.isNaN(params.maxResolution)) {
                final int lod = OSMUtils.mapnikTileLevel(params.maxResolution);
                groupLodWhereClause.beginCondition();
                groupLodWhereClause.append("min_lod <= ?");
                groupLodWhereClause.addArg(lod);
                
                // XXX - make this more dynamic
                indexedSpatialFilter &= (lod>2);
            }

            // if the user has specified restriction over group IDs/names,
            // further restrict the selection according to their parameters
            if(params.featureSetIds != null) {
                groupLodWhereClause.beginCondition();
                Collection<BindArgument> args = new LinkedList<BindArgument>();
                for(Long fsid : params.featureSetIds)
                    args.add(new BindArgument(fsid.longValue()));   
                groupLodWhereClause.appendIn2("id", args);
            }
            if(params.featureSets != null) {
                groupLodWhereClause.beginCondition();
                groupLodWhereClause.appendIn("name", params.featureSets);
            }

            Set<Long> lodGroups = new HashSet<Long>();
            CursorIface result = null;
            try {
                result = BindArgument.query(this.impl, "SELECT id FROM groups WHERE " + groupLodWhereClause.getSelection(), groupLodWhereClause.getBindArgs());
                //result = BindArgument.query(this.impl, "SELECT id FROM groups WHERE min_lod <= 8", (BindArgument[])null);

                while(result.moveToNext()) {
                    lodGroups.add(Long.valueOf(result.getLong(0)));
                }
            } finally {
                if(result != null)
                    result.close();
            }
            
            // clone the input parameters
            params = new FeatureQueryParameters(params);

            // clear any user specified group filter and replace with the
            // result set groups
            
            // XXX - intersection
            params.featureSets = null;
            params.featureSetIds = lodGroups;
            
            params.minResolution = Double.NaN;
            params.maxResolution = Double.NaN;

            // all groups are excluded by virtue of resolution, force return of
            // zero results
            if(lodGroups.isEmpty())
                return false;
        }

        if(params.visibleOnly) {
            boolean allStrictlyVisible = true;
            boolean allInvisible = true; 
            Set<Long> visibleFSIDs = new HashSet<Long>();
            synchronized(this) {
                if(this.groupVisibilityDirty)
                    this.refreshGroupVisibilityInfoNoSync();
                if(groupVisibleInfo != null)
                    groupVisibleInfo.putAll(this.groupVisibility);
                
                // determine if all groups are strictly visible, meaning that no
                // features deviate
                GroupVisibleInfo visInfo;
                for(Map.Entry<Long, GroupVisibleInfo> entry : this.groupVisibility.entrySet()) {
                    visInfo = entry.getValue();
                    allStrictlyVisible &= visInfo.visible && !visInfo.check;
                    allInvisible &= !visInfo.visible && !visInfo.check;
                    
                    if(visInfo.visible || visInfo.check)
                        visibleFSIDs.add(entry.getKey());
                }
            }
            
            if(allInvisible) {
                // we have requested visible only and all features are currently
                // invisible
                return false;
            } else if(!allStrictlyVisible) {
                // not all sets are strictly visible, we will need to apply
                // filtering to discover visibility

                // XXX -
                params = new FeatureQueryParameters(params);
                if(params.featureSets != null) {
                    Collection<Long> fsFsids = params.featureSetIds;
                    if(params.featureSetIds == null)
                        params.featureSetIds = new HashSet<Long>();

                    // translate FeatureSet names to IDs
                    FeatureSetQueryParameters fsParams = new FeatureSetQueryParameters();
                    fsParams.names = params.featureSets;
                    fsParams.ids = fsFsids;
                    
                    FeatureSetCursor result = null;
                    try {
                        result = this.queryFeatureSets(fsParams);
                        while(result.moveToNext())
                            params.featureSetIds.add(Long.valueOf(result.get().getId()));
                    } finally {
                        if(result != null)
                            result.close();
                    }
                    
                    params.featureSets = null;
                }

                if(params.featureSetIds != null)
                    params.featureSetIds.retainAll(visibleFSIDs);
                else
                    params.featureSetIds = visibleFSIDs;
                
                if(params.featureSetIds.isEmpty())
                    return false;
            } // else all are strictly visible, continue with original filters
        }

        if(params.providers != null) {
            whereClause.beginCondition();
            whereClause.append("Geometry.group_id IN (SELECT id FROM groups WHERE ");
            whereClause.appendIn("provider", params.providers);
            whereClause.append(")");
        }
        if(params.types != null) {
            whereClause.beginCondition();
            whereClause.append("Geometry.group_id IN (SELECT id FROM groups WHERE ");
            whereClause.appendIn("type", params.types);
            whereClause.append(")");
        }
        if(params.featureSets != null) {
            whereClause.beginCondition();
            whereClause.append("Geometry.group_id IN (SELECT id FROM groups WHERE ");
            whereClause.appendIn("name", params.featureSets);
            whereClause.append(")");
        }
        if(params.featureSetIds != null) {
            whereClause.beginCondition();
            Collection<BindArgument> args = new LinkedList<BindArgument>();
            for(Long fsid : params.featureSetIds)
                args.add(new BindArgument(fsid.longValue()));            
            whereClause.appendIn2("Geometry.group_id", args);
        }
        if(params.featureNames != null) {
            whereClause.beginCondition();
            whereClause.appendIn("Geometry.name", params.featureNames);
        }
        if(params.featureIds != null) {
            whereClause.beginCondition();
            Collection<BindArgument> args = new LinkedList<BindArgument>();
            for(Long fid : params.featureIds)
                args.add(new BindArgument(fid.longValue()));
            whereClause.appendIn2("Geometry.id", args);
        }
        if(!Double.isNaN(params.minResolution)) {
            whereClause.beginCondition();
            whereClause.append("Geometry.max_lod >= ?");
            whereClause.addArg(OSMUtils.mapnikTileLevel(params.minResolution));
        }
        if(!Double.isNaN(params.maxResolution)) {
            whereClause.beginCondition();
            whereClause.append("Geometry.min_lod <= ?");
            whereClause.addArg(OSMUtils.mapnikTileLevel(params.maxResolution));
        }
        if(params.spatialFilter != null) {
            appendSpatialFilter(params.spatialFilter,
                                whereClause,
                                indexedSpatialFilter &&
                                  FeatureSpatialDatabase.SPATIAL_INDEX_ENABLED);
        }
        
        return true;
    }

    /**************************************************************************/
     
    private static void appendOrder(FeatureQueryParameters.Order order, StringBuilder sql, LinkedList<BindArgument> args, boolean first) {
        if(order instanceof FeatureQueryParameters.FeatureId) {
            if(!first) sql.append(",");
            sql.append(" Geometry.id ASC");
        } else if(order instanceof FeatureQueryParameters.FeatureName) {
            if(!first) sql.append(",");
            sql.append(" Geometry.name ASC");
        } else if(order instanceof FeatureQueryParameters.FeatureSet) {
            if(!first) sql.append(",");
            sql.append(" Geometry.group_id ASC");
        } else if(order instanceof FeatureQueryParameters.Distance) {
            FeatureQueryParameters.Distance distance = (FeatureQueryParameters.Distance)order;
            
            // XXX - order by distance not working when parametizing MakePoint!! 

            if(!first) sql.append(",");
            sql.append(" Distance(Geometry.geom, MakePoint(?, ?, 4326), 1) ASC");
            args.add(new BindArgument(distance.point.getLongitude()));
            args.add(new BindArgument(distance.point.getLatitude()));

/*
            if(!first) sql.append(",");
            sql.append(" Distance(Geometry.geom, MakePoint(");
            sql.append(String.valueOf(distance.point.getLongitude()));
            sql.append(", ");
            sql.append(String.valueOf(distance.point.getLatitude()));
            sql.append(", 4326), 1) ASC");
*/
        } else if(order instanceof FeatureQueryParameters.Resolution) {
            if(!first) sql.append(",");
            sql.append(" Geometry.max_lod DESC");
        } else {
            // XXX - 
        }
    }

    private static void appendSpatialFilter(FeatureQueryParameters.SpatialFilter filter, WhereClauseBuilder whereClause, boolean spatialFilterEnabled) {
        if(filter instanceof FeatureQueryParameters.RadiusSpatialFilter) {
            FeatureQueryParameters.RadiusSpatialFilter radius = (FeatureQueryParameters.RadiusSpatialFilter)filter;

            whereClause.beginCondition();
            whereClause.append("Distance(MakePoint(?, ?, 4326), Geometry.geom, 1) <= ?");
            whereClause.addArg(radius.point.getLongitude());
            whereClause.addArg(radius.point.getLatitude());
            whereClause.addArg(radius.radius);
        } else if(filter instanceof FeatureQueryParameters.RegionSpatialFilter) {
            FeatureQueryParameters.RegionSpatialFilter region = (FeatureQueryParameters.RegionSpatialFilter)filter;

            whereClause.beginCondition();
            if(spatialFilterEnabled) {
                whereClause.append("Geometry.ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = \'Geometry\' AND search_frame = BuildMbr(?, ?, ?, ?, 4326))");
                
                whereClause.addArg(region.upperLeft.getLongitude());
                whereClause.addArg(region.upperLeft.getLatitude());
                whereClause.addArg(region.lowerRight.getLongitude());
                whereClause.addArg(region.lowerRight.getLatitude());
            } else {
                whereClause.append("Intersects(BuildMbr(?, ?, ?, ?, 4326), Geometry.geom) = 1");
                whereClause.addArg(region.upperLeft.getLongitude());
                whereClause.addArg(region.upperLeft.getLatitude());
                whereClause.addArg(region.lowerRight.getLongitude());
                whereClause.addArg(region.lowerRight.getLatitude());
            }
        } else {
            // XXX - 
        }
    } 

    /**************************************************************************/
    // Currency
    
    private static int getCodedStringLength(String s) {
        return 4 + (2*s.length());
    }

    private static ByteBuffer putString(ByteBuffer buffer, String s) {
        buffer.putInt(s.length());
        for(int i = 0; i < s.length(); i++)
            buffer.putChar(s.charAt(i));
        return buffer;
    }

    private static String getString(ByteBuffer buffer) {
        final int len = buffer.getInt();
        StringBuilder retval = new StringBuilder(len);
        for(int i = 0; i < len; i++)
            retval.append(buffer.getChar());
        return retval.toString();
    }

    private final static class ValidateCurrency implements CatalogCurrency {

        private ValidateCurrency() {}

        @Override
        public String getName() {
            return CURRENCY_NAME;
        }

        @Override
        public int getAppVersion() {
            return CURRENCY_VERSION;
        }

        @Override
        public byte[] getAppData(File file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isValidApp(File f, int appVersion, byte[] appData) {
            if(appVersion != this.getAppVersion())
                return false;

            ByteBuffer parse = ByteBuffer.wrap(appData);
            parse.order(ByteOrder.LITTLE_ENDIAN);

            final int numSpis = parse.getInt();
            int parseVersion;
            FeatureDataSource spi;
            for(int i = 0; i < numSpis; i++) {
                parseVersion = parse.getShort()&0xFFFF;
                spi = FeatureDataSourceContentFactory.getProvider(getString(parse));
                if(spi == null || spi.parseVersion() != parseVersion) {
                    return false;
                }
            }
            
            // XXX - 
            if(f instanceof com.atakmap.io.ZipVirtualFile)
                f = ((com.atakmap.io.ZipVirtualFile)f).getZipFile();

            final boolean isDirectory = ((parse.get()&0x01) == 0x01);
            if(IOProviderFactory.isDirectory(f) != isDirectory) {
                return false;
            }
            final long length = parse.getLong();
            if(length != FileSystemUtils.getFileSize(f)) {
                return false;
            }
            final long lastModified = parse.getLong();
            if(lastModified != FileSystemUtils.getLastModified(f)) {
                return false;
            }
            return true;
        }
    }
    
    private static final class GenerateCurrency implements CatalogCurrency {

        private final Collection<FeatureDataSource> contentSpis;
        
        GenerateCurrency(FeatureDataSource.Content content) {
            this.contentSpis = new LinkedList<FeatureDataSource>();
            FeatureDataSource spi;
            spi = FeatureDataSourceContentFactory.getProvider(content.getProvider());
            if(spi == null)
                throw new IllegalStateException();
            this.contentSpis.add(spi);
        }

        @Override
        public String getName() {
            return CURRENCY_NAME;
        }

        @Override
        public int getAppVersion() {
            return CURRENCY_VERSION;
        }

        @Override
        public byte[] getAppData(File file) {
            // XXX - 
            if(file instanceof com.atakmap.io.ZipVirtualFile)
                file = ((com.atakmap.io.ZipVirtualFile)file).getZipFile();

            int len = 4 + (2*this.contentSpis.size()) + 1 + 16;
            for(FeatureDataSource spi : this.contentSpis)
                len += getCodedStringLength(spi.getName());
            
            ByteBuffer retval = ByteBuffer.wrap(new byte[len]);
            retval.order(ByteOrder.LITTLE_ENDIAN);
            
            retval.putInt(this.contentSpis.size());
            for(FeatureDataSource spi : this.contentSpis) {
                retval.putShort((short)spi.parseVersion());
                putString(retval, spi.getName());
            }
            retval.put(IOProviderFactory.isDirectory(file) ? (byte)0x01 : (byte)0x00);
            retval.putLong(FileSystemUtils.getFileSize(file));
            retval.putLong(FileSystemUtils.getLastModified(file));
            
            if(retval.remaining() > 0)
                throw new IllegalStateException("remaining=" + retval.remaining());
            return retval.array();
        }

        @Override
        public boolean isValidApp(File f, int appVersion, byte[] appData) {
            throw new UnsupportedOperationException();
        }
    }
    
    /**************************************************************************/

    private static class GroupVisibleInfo {
        public final boolean visible;
        public final int version;
        public final boolean check;
        
        public GroupVisibleInfo(boolean visible, boolean check, int version) {
            this.visible = visible;
            this.check = check;
            this.version = version;
        }
    }

    private static class VisibleOnlyFeatureFilter extends FilteredCursor {

        private final Map<Long, GroupVisibleInfo> info;
        private final int groupIdCol;
        private final int visibleVersionCol;
        private final int visibleCol;
        private final int limit;
        private final int offset;

        private int pointer;

        public VisibleOnlyFeatureFilter(CursorIface cursor, int groupIdCol, int visVersionCol, int visCol, Map<Long, GroupVisibleInfo> info, int limit, int offset) {
            super(cursor);
            
            this.info = info;
            this.groupIdCol = groupIdCol;
            this.visibleVersionCol = visVersionCol;
            this.visibleCol = visCol;
            this.limit = limit + offset;
            this.offset = offset;
            
            this.pointer = 0;
        }

        @Override
        protected boolean accept() {
            GroupVisibleInfo i = info.get(Long.valueOf(this.getLong(this.groupIdCol)));
            if(i == null)
                return false;
            if(!i.check || (i.version != this.getInt(this.visibleVersionCol)))
                return i.visible;
            return (this.getInt(this.visibleCol) != 0);
        }
        
        @Override
        public boolean moveToNext() {
            if(this.limit == 0)
                return super.moveToNext();
            if(this.pointer >= this.limit)
                return false;
            
            do {
                if(!super.moveToNext())
                    return false;
                this.pointer++;
                if(this.pointer < this.offset)
                    continue;
                break;
            } while(true);
            return true;
        }
    }

    private static class FeatureCursorImpl extends CursorWrapper implements FeatureCursor {

        private final int idCol;
        private final int groupIdCol;
        private final int nameCol;
        private final int geomCol;
        private final int styleCol;
        private final int attribsCol;
        
        private Feature row;

        protected FeatureCursorImpl(CursorIface filter, int idCol, int groupIdCol, int nameCol, int geomCol, int styleCol, int attribsCol) {
            super(filter);
            
            this.idCol = idCol;
            this.groupIdCol = groupIdCol;
            this.nameCol = nameCol;
            this.geomCol = geomCol;
            this.styleCol = styleCol;
            this.attribsCol = attribsCol;
            
            this.row = null;
        }

        @Override
        public Feature get() {
            if(this.row == null) {
                final long fsid = this.getLong(this.groupIdCol);
                final long fid = this.getLong(this.idCol);
    
                final String name = this.getString(this.nameCol);
                final byte[] geomBlob = this.getBlob(this.geomCol);
                final String ogrStyle = this.getString(this.styleCol);
                final byte[] attribsBlob = this.getBlob(this.attribsCol);
    
                Geometry geom = null;
                if(geomBlob != null)
                    geom = GeometryFactory.parseSpatiaLiteBlob(geomBlob);
                Style style = null;
                if(ogrStyle != null)
                    style = FeatureStyleParser.parse2(ogrStyle);
                AttributeSet attribs = null;
                if(attribsBlob != null) {
                    // XXX - 
                }
    
                this.row = new Feature(fsid, fid, name, geom, style, attribs, FeatureDataStore2.TIMESTAMP_NONE, 1L);
            }

            return this.row;
        }

        @Override
        public Object getRawGeometry() {
            return this.getBlob(this.geomCol);
        }

        @Override
        public int getGeomCoding() {
            return GEOM_SPATIALITE_BLOB;
        }

        @Override
        public String getName() {
            return this.getString(this.nameCol);
        }

        @Override
        public int getStyleCoding() {
            return STYLE_OGR;
        }

        @Override
        public Object getRawStyle() {
            return this.getString(this.styleCol);
        }

        @Override
        public AttributeSet getAttributes() {
            return null;
        }

        @Override
        public long getId() {
            return this.getLong(this.idCol);
        }

        @Override
        public long getVersion() {
            return 1;
        }
        
        @Override
        public long getFsid() {
            return this.getLong(this.groupIdCol);
        }

        @Override
        public boolean moveToNext() {
            this.row = null;
            return super.moveToNext();
        }
    }

    private class FeatureSetCursorImpl extends CursorWrapper implements FeatureSetCursor {

        private final int idCol;
        private final int providerCol;
        private final int typeCol;
        private final int nameCol;
        private final int minGsdCol;
        private final int maxGsdCol;
        
        public FeatureSetCursorImpl(CursorIface filter, int idCol, int providerCol, int typeCol, int nameCol, int minGsdCol, int maxGsdCol) {
            super(filter);

            this.idCol = idCol;
            this.providerCol = providerCol;
            this.typeCol = typeCol;
            this.nameCol = nameCol;
            this.minGsdCol = minGsdCol;
            this.maxGsdCol = maxGsdCol;
        }

        @Override
        public FeatureSet get() {
            return new FeatureSet(PersistentDataSourceFeatureDataStore.this,
                                  this.getLong(this.idCol),
                                  this.getString(this.providerCol),
                                  this.getString(this.typeCol),
                                  this.getString(this.nameCol),
                                  OSMUtils.mapnikTileResolution(this.getInt(this.minGsdCol)),
                                  OSMUtils.mapnikTileResolution(this.getInt(this.maxGsdCol)),
                                  1);
        }
    }
    
    private class VisibleOnlyFeatureSetCursor extends FilteredCursor {

        private final int fsidCol;
        private final int limit;
        private final int offset;

        private int pointer;

        public VisibleOnlyFeatureSetCursor(CursorIface cursor, int fsidCol, int limit, int offset) {
            super(cursor);
            
            this.fsidCol = fsidCol;
            this.limit = limit + offset;
            this.offset = offset;
            
            this.pointer = 0;
        }

        @Override
        protected boolean accept() {
            return PersistentDataSourceFeatureDataStore.this.isFeatureSetVisible(this.getLong(this.fsidCol));
        }
        
        @Override
        public boolean moveToNext() {
            if(this.limit == 0)
                return super.moveToNext();
            if(this.pointer >= this.limit)
                return false;
            
            do {
                if(!super.moveToNext())
                    return false;
                this.pointer++;
                if(this.pointer < this.offset)
                    continue;
                break;
            } while(true);
            return true;
        }
    }
    
    private final static class FileCursorImpl extends CursorWrapper implements FileCursor {

        protected FileCursorImpl(FeatureSpatialDatabase.CatalogCursor filter) {
            super(filter);
        }

        @Override
        public File getFile() {
            return new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(
                 ((FeatureSpatialDatabase.CatalogCursor)this.filter).getPath()));
        }
        
    }
}
