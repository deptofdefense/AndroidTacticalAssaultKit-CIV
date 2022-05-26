package com.atakmap.map.layer.feature.gpkg;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.RowIteratorWrapper;
import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.map.gpkg.GeoPackage.ContentsRow;
import com.atakmap.map.layer.feature.AbstractFeatureDataStore2;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.cursor.BruteForceLimitOffsetFeatureCursor;
import com.atakmap.map.layer.feature.cursor.MultiplexingFeatureCursor;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.gpkg.GeoPackageSchemaHandler.OnFeatureDefinitionsChangedListener;
import com.atakmap.util.Collections2;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import android.net.Uri;


public
class GeoPackageFeatureDataStore
    extends AbstractFeatureDataStore2
  {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================


    public
    GeoPackageFeatureDataStore (File dbFile)
      {
        super (MODIFY_PROHIBITED, VISIBILITY_SETTINGS_FEATURESET);
        URI = Uri.fromFile (dbFile).toString ();
        initDataStore (dbFile);
      }


    //==================================
    //  FeatureDataStore INTERFACE
    //==================================


    @Override
    public
    Feature
    getFeature (long fid)
      {
        Feature result = null;
        final long layerID = fid >>> (64 - fsIdBits);
        final LayerInfo layer = layersById.get(Long.valueOf(layerID));
        if (layer != null)
          {
            FeatureCursor cursor = null;
            FeatureQueryParameters params = new FeatureQueryParameters ();

            params.featureIds = Collections.singleton (Long.valueOf (fid & fIdMask));

            try
              {
                cursor = queryFeatures (layer, params);

                if (cursor.moveToNext ())
                  {
                    result = cursor.get ();
                  }
              }
            finally
              {
                if (cursor != null)
                  {
                    cursor.close ();
                  }
              }
          }

        return result;
      }


    @Override
    public
    FeatureSet
    getFeatureSet (long featureSetID)
      {
        FeatureSet result = null;

        FeatureSetInfo info = featureSetInfos.get (Long.valueOf(featureSetID));
        if (info != null)
          {
            
            result = new FeatureSet (PROVIDER, TYPE,
                                     info.featureSetName,
                                     info.minResolution,
                                     info.maxResolution);
            adopt (result,
                   featureSetID,
                   schemaHandler.getSchemaVersion ());
          }

        return result;
      }


    public final
    GeoPackageSchemaHandler
    getSchemaHandler ()
      { return schemaHandler; }


    @Override
    public
    String
    getUri ()
      { return URI; }


    @Override
    public synchronized
    boolean
    isAvailable ()
      { return !disposed; }


    @Override
    public
    boolean
    isFeatureVisible (long fid)
      { return isFeatureSetVisible (fid >>> (64 - fsIdBits)); }


    @Override
    public
    boolean
    isFeatureSetVisible (long setId)
      {
        final FeatureSetInfo fsInfo = featureSetInfos.get(Long.valueOf(setId));
        return fsInfo != null
            ? fsInfo.visible
            : false;
      }


    @Override
    public
    FeatureCursor
    queryFeatures (FeatureQueryParameters params)
      {
        Map<LayerInfo, FeatureQueryParameters> queryMap = splitQuery (params);
        boolean bruteForceLimit = params.limit != 0 && queryMap.size () > 1;

        if (bruteForceLimit)
          {
            for (FeatureQueryParameters fsParams : queryMap.values ())
              {
                if (fsParams != null)
                  {
                    fsParams.offset = fsParams.limit = 0;
                  }
              }
          }

        List<FeatureCursor> cursors
            = new ArrayList<FeatureCursor> (queryMap.size ());

        for (Map.Entry<LayerInfo, FeatureQueryParameters> entry
             : queryMap.entrySet ())
          {
            cursors.add (queryFeatures (entry.getKey (), entry.getValue ()));
          }

        FeatureCursor result
            = new MultiplexingFeatureCursor (cursors, params.order);

        return bruteForceLimit
            ? new BruteForceLimitOffsetFeatureCursor (result,
                                                      params.offset,
                                                      params.limit)
            : result;
      }


    @Override
    public
    int
    queryFeaturesCount (FeatureQueryParameters params)
      {
        Map<LayerInfo, FeatureQueryParameters> queryMap = splitQuery (params);
        boolean bruteForceLimit = params.limit != 0 && queryMap.size () > 1;

        if (bruteForceLimit)
          {
            return AbstractFeatureDataStore2.queryFeaturesCount(this, params);
          }

        int featuresCount = 0;
        for (Map.Entry<LayerInfo, FeatureQueryParameters> entry
             : queryMap.entrySet ())
          {
            String layerName = entry.getKey().name;
            featuresCount += schemaHandler.queryFeaturesCount (layerName, entry.getValue());
          }

        return featuresCount;
      }


    @Override
    public
    FeatureSetCursor
    queryFeatureSets (FeatureSetQueryParameters params)
      {
        //
        // Sort results by FeatureSet name.
        //
        Map<FeatureSetInfo, Long> matchingSets
            = new TreeMap<FeatureSetInfo, Long>
                      (new Comparator<FeatureSetInfo> ()
                          {
                            @Override
                            public
                            int
                            compare (FeatureSetInfo lhs,
                                     FeatureSetInfo rhs)
                              { return lhs.featureSetName.compareToIgnoreCase (rhs.featureSetName); }                            
                          });

        for (FeatureSetInfo fsInfo : featureSetInfos.values())
          {
            if (matches (fsInfo, params))
              {
                matchingSets.put (fsInfo,
                                  fsInfo.fsid);
              }
          }

        return new FeatureSetCursorImpl (new ArrayList<Long> (matchingSets.values ()));
      }


    @Override
    public
    int
    queryFeatureSetsCount (FeatureSetQueryParameters params)
      {
        final int abslimit = (params != null) ? (params.offset+params.limit) : 0;
        final int offset = (params != null) ? params.offset : 0;
        int matchingSetCount = 0;

        for (FeatureSetInfo fsInfo : featureSetInfos.values())
          {
            if (matches (fsInfo, params))
              {
                ++matchingSetCount;
                if(matchingSetCount == abslimit)
                    break;
              }
          }

        return Math.max(matchingSetCount-offset, 0);
      }


    @Override
    public synchronized
    void
    refresh ()
      {
        if (!disposed)
          {
            String dbPath = getPackagePath ();

            geoPackage.close ();
            schemaHandler.removeOnFeatureDefinitionsChangedListener (changeListener);
            schemaHandler = null;
            featureSetInfos.clear ();
            if (dbPath != null) 
                initDataStore (new File (FileSystemUtils.sanitizeWithSpacesAndSlashes(dbPath)));
          }
      }


    //==================================
    //  Disposable INTERFACE
    //==================================


    @Override
    public synchronized
    void
    dispose ()
      {
        if (!disposed)
          {
            geoPackage.close ();
            schemaHandler.removeOnFeatureDefinitionsChangedListener (changeListener);
            schemaHandler = null;
            featureSetInfos.clear ();
            disposed = true;
          }
      }


    //==================================
    //
    //  PROTECTED INTERFACE
    //
    //==================================


    //==================================
    //  AbstractFeatureDataStore2 INTERFACE
    //==================================


    @Override
    protected
    void
    beginBulkModificationImpl ()
      { }


    @Override
    protected
    boolean
    deleteAllFeaturesImpl (long fsid)
      { return false; }


    @Override
    protected
    boolean
    deleteAllFeatureSetsImpl ()
      { return false; }


    @Override
    protected
    boolean
    deleteFeatureImpl (long fsid)
      { return false; }


    @Override
    protected
    boolean
    deleteFeatureSetImpl (long fsid)
      { return false; }


    @Override
    protected
    boolean
    endBulkModificationImpl (boolean successful)
      { return false; }


    @Override
    protected
    boolean
    insertFeatureImpl (long fsid,
                       FeatureDefinition def,
                       Feature[] returnRef)
      { return false; }


    @Override
    protected
    boolean
    insertFeatureImpl (long fsid,
                       String name,
                       Geometry geom,
                       Style style,
                       AttributeSet attributes,
                       Feature[] returnRef)
      { return false; }


    @Override
    protected
    boolean
    insertFeatureSetImpl (String provider,
                          String type,
                          String name,
                          double minResolution,
                          double maxResolution,
                          FeatureSet[] returnRef)
      { return false; }


    @Override
    protected
    boolean
    setFeatureVisibleImpl (long fid,
                           boolean visible)
      { return false; }


    @Override
    protected
    boolean
    setFeaturesVisibleImpl (FeatureQueryParameters params,
                            boolean visible)
      { return false; }


    @Override
    protected
    boolean
    setFeatureSetVisibleImpl (long setId,
                              boolean visible)
      {
        boolean changed = false;
        FeatureSetInfo fsInfo = featureSetInfos.get (Long.valueOf(setId));

        if (fsInfo != null)
          {
            changed = visible != fsInfo.visible;
            fsInfo.visible = visible;
          }

        return changed;
      }


    @Override
    protected
    boolean
    setFeatureSetsVisibleImpl (FeatureSetQueryParameters params,
                               boolean visible)
      {
        boolean changed = false;

        for (FeatureSetInfo fsInfo : featureSetInfos.values())
          {
            if (matches (fsInfo, params))
              {
                changed |= setFeatureSetVisibleImpl (fsInfo.fsid, visible);
              }
          }

        return changed;
      } 


    @Override
    protected
    boolean
    updateFeatureImpl (long fid,
                       String name)
      { return false; }


    @Override
    protected
    boolean
    updateFeatureImpl (long fid,
                       Geometry geom)
      { return false; }


    @Override
    protected
    boolean
    updateFeatureImpl (long fid,
                       Style style)
      { return false; }


    @Override
    protected
    boolean
    updateFeatureImpl (long fid,
                       AttributeSet attributes)
      { return false; }


    @Override
    protected
    boolean
    updateFeatureImpl (long fid,
                       String name,
                       Geometry geom,
                       Style style,
                       AttributeSet attributes)
      { return false; }


    @Override
    protected
    boolean
    updateFeatureSetImpl (long fsid,
                          String name)
      { return false; }


    @Override
    protected
    boolean
    updateFeatureSetImpl (long fsid,
                          double minResolution,
                          double maxResolution)
      { return false; }


    @Override
    protected
    boolean
    updateFeatureSetImpl (long fsid,
                          String name,
                          double minResolution,
                          double maxResolution)
      { return false; }


    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================


    private
    class FeatureCursorImpl
        extends RowIteratorWrapper
        implements FeatureCursor
      {
        //==============================
        //  FeatureCursor INTERFACE
        //==============================


        @Override
        public
        Feature
        get ()
          {
            if (feature == null)
              {
                feature = new Feature (fsID,
                                       getId(),
                                       getName (),
                                       (Geometry) getRawGeometry (),
                                       (Style) getRawStyle (),
                                       getAttributes (),
                                       FeatureDataStore2.TIMESTAMP_NONE,
                                       getVersion ());
              }

            return feature;
          }


        @Override
        public
        AttributeSet
        getAttributes ()
          {
            if (attribs == null)
              { attribs = this.filter.getAttributes(); }

            return attribs;
          }


        @Override
        public
        long
        getFsid ()
          { return fsID; }
          

        @Override
        public
        int
        getGeomCoding ()
          { return GEOM_ATAK_GEOMETRY; }


        @Override
        public
        long
        getId ()
          {
            return fsID << (64 - GeoPackageFeatureDataStore.this.fsIdBits)
                        | getFeatureID ();
          }


        @Override
        public
        String
        getName ()
          { return this.filter.getName(); }


        @Override
        public
        Object
        getRawGeometry ()
          {
            if (rawGeometry == null)
              { rawGeometry = this.filter.getGeometry(); }

            return rawGeometry;
          }


        @Override
        public
        Object
        getRawStyle ()
          {
            if (rawStyle == null)
              { rawStyle = this.filter.getStyle(); }

            return rawStyle;
          }


        @Override
        public
        int
        getStyleCoding ()
          { return STYLE_ATAK_STYLE; }


        @Override
        public
        long
        getVersion ()
          { return this.filter.getVersion(); }


        //==============================
        //  RowIteratorWrapper INTERFACE
        //==============================


        @Override
        public
        boolean
        moveToNext ()
          {
            rawGeometry = null;
            rawStyle = null;
            attribs = null;
            feature = null;
            return super.moveToNext ();
          }


        //==============================
        //
        //  PRIVATE IMPLEMENTATION
        //
        //==============================


        FeatureCursorImpl (GeoPackageFeatureCursor wrapped,
                           GeoPackageSchemaHandler handler,
                           long featureSetID)
          {
            super (wrapped);
            this.filter = wrapped;
            schemaHandler = handler;
            fsID = featureSetID;
          }


        private
        long
        getFeatureID ()
          { return this.filter.getID(); }


        private GeoPackageSchemaHandler schemaHandler;
        private long fsID;
        private Geometry rawGeometry;
        private Style rawStyle;
        private AttributeSet attribs;
        private Feature feature;
        private final GeoPackageFeatureCursor filter;
      }


    private
    class FeatureSetCursorImpl
        implements FeatureSetCursor
      {
        FeatureSetCursorImpl (List<Long> ids)
          { featureSetIDs = ids; }


        //==============================
        //  FeatureSetCursor INTERFACE
        //==============================


        @Override
        public
        FeatureSet
        get ()
          {
            return i < featureSetIDs.size ()
                ? GeoPackageFeatureDataStore.this.getFeatureSet
                      (featureSetIDs.get (i).longValue ())
                : null;
          }


        //==============================
        //  RowIterator INTERFACE
        //==============================


        @Override
        public
        void
        close ()
          {
            featureSetIDs.clear ();
            i = 0;
          }


        @Override
        public
        boolean
        isClosed ()
          { return featureSetIDs.isEmpty () && i == 0; }


        @Override
        public
        boolean
        moveToNext ()
          { return ++i < featureSetIDs.size (); }


        private List<Long> featureSetIDs;
        private int i = -1;
      }


    private static
    class FeatureSetInfo
      {
        FeatureSetInfo (String layerName,
                        long fsid,
                        String featureSetName,
                        double minResolution,
                        double maxResolution)
          {
            this.layerName = layerName;
            this.fsid = fsid;
            this.featureSetName = featureSetName;
            this.minResolution = minResolution;
            this.maxResolution = maxResolution;
          }


        final String layerName;
        final long fsid;
        final String featureSetName;
        final double minResolution;
        final double maxResolution;
        boolean visible = true;
      }


    //
    // Returns a copy of the supplied FeatureQueryParameters filtered for a
    // particular FeatureSet.  Returns null if the supplied
    // FeatureQueryParameters is null or the query against the FeatureSet needs
    // no restrictions.
    //
    private
    FeatureQueryParameters
    filterQuery (LayerInfo layer,
                 FeatureQueryParameters params)
      {
        FeatureQueryParameters result = null;

        if (params != null)
          {
            FeatureQueryParameters fsParams
                = new FeatureQueryParameters (params);

            if (params.featureIds != null)
              {
                //
                // Check and mask off FSID.
                //
                fsParams.featureIds = new ArrayList<Long> ();
                for (Long fID : params.featureIds)
                  {
                    long longID = fID.longValue ();

                    if (longID >>> (64 - fsIdBits) == layer.layerId)
                      {
                        fsParams.featureIds.add (longID & fIdMask);
                      }
                  }
                result = fsParams;
              }
            if(params.visibleOnly)
              {
                Set<Long> visibleFSIDs = new HashSet<Long>();
                for(FeatureSetInfo fsInfo : layer.featureSets.values())
                  {
                    if(fsInfo.visible)
                        visibleFSIDs.add(Long.valueOf(fsInfo.fsid));
                  }
                if(visibleFSIDs.size() != layer.featureSets.size())
                  {
                    if(fsParams.featureSetIds == null)
                        fsParams.featureSetIds = visibleFSIDs;
                    else
                        fsParams.featureSetIds.retainAll(visibleFSIDs);
                  }
                params = fsParams;
              }
            if (params.featureSets != null)
              {
                Set<String> absoluteNames = new HashSet<String>();
                for(String featureSetName : layer.featureSetNames)
                  {
                    if(matches(params.featureSets, featureSetName, '%'))
                        absoluteNames.add(featureSetName);
                  }
                fsParams.featureSets = absoluteNames;
                result = fsParams;
              }
            if (params.featureSetIds != null)
              {
                // intersect FSIDs with layer FSIDs
                fsParams.featureSetIds.retainAll(layer.featureSets.keySet());
                
                // convert FSIDs to feature set names
                Set<String> featureSetNames = new HashSet<String>();
                for(Long fsid : params.featureSetIds)
                  {
                    final FeatureSetInfo fsInfo = layer.featureSets.get(fsid);
                    if(fsInfo != null)
                        featureSetNames.add(fsInfo.featureSetName);
                  }
                fsParams.featureSetIds = null;

                // intersect FSID derived names with parameter names
                if(fsParams.featureSets != null)
                    featureSetNames.retainAll(fsParams.featureSets);
                
                // params will utilize names for schema handler
                fsParams.featureSets = featureSetNames;
                result = fsParams;
              }

            if (params.providers != null)
              {
                fsParams.providers = null;
                result = fsParams;
              }
            if (params.types != null)
              {
                fsParams.types = null;
                result = fsParams;
              }
            if (result == null
                && (params.visibleOnly
                    || params.geometryTypes != null
                    || params.ignoredFields != 0
                    || !Double.isNaN (params.maxResolution)
                    || !Double.isNaN (params.minResolution)
                    || params.offset != 0
                    || params.ops != null
                    || params.order != null
                    || params.spatialFilter != null))
              {
                result = fsParams;
              }
          }
 
        return result;
      }


    private
    String
    getPackagePath ()
      {
        //
        // The path to the GeoPackage is the path to the main database.
        //
        String result = null;
        CursorIface cursor = null;

        try
          {
            cursor = geoPackage.getDatabase ().query ("PRAGMA database_list",
                                                      null);
            while (result == null && cursor.moveToNext ())
              {
                if (cursor.getString (1).equals ("main"))
                  {
                    result = cursor.getString (2);
                  }
              }
          }
        catch (Exception e)
          { Log.e (TAG, "Error getting GeoPackage path from database list"); }
        finally
          {
            if (cursor != null)
              {
                cursor.close ();
              }
          }

        return result;
      }


    private void initDataStore(File dbFile) {
        if (!GeoPackage.isGeoPackage (dbFile))
            throw new IllegalArgumentException ("File " + dbFile
                                            + " is not a GeoPackage");
        geoPackage = new GeoPackage (dbFile);
        schemaHandler = GeoPackageSchemaHandlerRegistry.getHandler (geoPackage);
        if (schemaHandler == null)
            schemaHandler = new DefaultGeoPackageSchemaHandler2 (geoPackage);
        schemaHandler.addOnFeatureDefinitionsChangedListener (changeListener);

        for (ContentsRow content : geoPackage.getPackageContents ()) {
            if (content.data_type == GeoPackage.TableType.FEATURES) {
                final String layerName = content.identifier;

                if(schemaHandler.ignoreLayer(layerName))
                    continue;

                LayerInfo layer = new LayerInfo(nextLayerId++,
                                                layerName,
                                                schemaHandler.getMinResolution (layerName),
                                                schemaHandler.getMaxResolution (layerName));
                layer.featureSetNames.addAll(schemaHandler.getLayerFeatureSets(layerName));
                for(String featureSetName : layer.featureSetNames)
                  {
                    final FeatureSetInfo fs =
                        new FeatureSetInfo (layer.name,
                                            nextFSID++,
                                            featureSetName,
                                            layer.minResolution,
                                            layer.maxResolution);
                    fs.visible = schemaHandler.getDefaultFeatureSetVisibility(layer.name, featureSetName);
                    featureSetInfos.put(Long.valueOf(fs.fsid), fs);

                    layer.featureSets.put(Long.valueOf(fs.fsid), fs);
                  }

                layersById.put(Long.valueOf(layer.layerId), layer);
                layersByName.put(layer.name, layer);
            }
        }

        if (featureSetInfos.isEmpty ()) {
            geoPackage.close ();
            throw new IllegalArgumentException ("GeoPackage " + dbFile
                                            + " contains no feature sets");
        }
        fsIdBits = (int) Math.ceil (Math.log (featureSetInfos.size ())
                                / Math.log (2.0));
        fIdMask = -1L >>> fsIdBits;
    }

    public GeoPackage getGeoPackage() {
        return this.geoPackage;
    }

    //
    // Returns true if the supplied FeatureQueryParameters are consistent with a
    // particular FeatureSet.
    //
    private
    boolean
    matches (String layerName,
             FeatureQueryParameters params)
      {
        if (params == null)
          {
            return true;
          }

        // provider/type
        if (params.providers != null
            && !matches (params.providers, PROVIDER, '%')
            || params.types != null
            && !matches (params.types, TYPE, '%'))
          {
            return false;
          }
        
        final LayerInfo info = layersByName.get(layerName);
        if(info == null)
            return false;

        // FSIDs
        if (params.featureSetIds != null
            && !Collections2.containsAny(info.featureSets.keySet(), params.featureSetIds))
          {
            return false;
          }
        // FIDs
        if (params.featureIds != null)
          {
            //
            // Find requests for features in this FeatureSet.
            //
            boolean matches = false;

            for (Long fID : params.featureIds)
              {
                if (fID.longValue () >>> (64 - fsIdBits) == info.layerId)
                  {
                    matches = true;
                    break;
                  }
              }
            if (!matches)
              {
                return false;
              }
          }

        // feature set names
        if(params.featureSets != null)
          {
            boolean matches = false;
            for(String featureSetName : info.featureSetNames)
              {
                if(matches (params.featureSets, featureSetName, '%'))
                  {
                    matches = true;
                    break;
                  }
              }
            if (!matches)
              {
                return false;
              }
          }
        // resolution thresholds
        if ((!Double.isNaN (params.minResolution)
            && !Double.isNaN (info.maxResolution)
            && params.minResolution < info.maxResolution)
            || (!Double.isNaN (params.maxResolution)
            && !Double.isNaN (info.minResolution)
            && params.maxResolution > info.minResolution))
          {
            return false;
          }
        // visibility
        if (params.visibleOnly && !isVisible(info))
          {
            return false;
          }
        // geometry types
        if (params.geometryTypes != null)
          {
            boolean matches = false;
            Class<? extends Geometry> layerGeometry
                = schemaHandler.getGeometryType (layerName);

            if (layerGeometry != null)
              {
                for (Class<? extends Geometry> geoClass : params.geometryTypes)
                  {
                    //
                    // A layer may declare a general type name (e.g., GEOMETRY
                    // or GEOMETRYCOLLECTION) in the gpkg_geometry_columns table.
                    // We can only rule out the FeatureSet if the geometry
                    // generalization hierarchies don't intersect.
                    //
                    if (geoClass.isAssignableFrom (layerGeometry)
                        || layerGeometry.isAssignableFrom (geoClass))
                      {
                        matches = true;
                        break;
                      }
                  }
              }
            if (!matches)
              {
                return false;
              }
          }
        
        return true;
      }
    
    private static boolean isVisible(LayerInfo layer) {
        for(FeatureSetInfo info : layer.featureSets.values())
            if(info.visible)
                return true;
        return false;
    }

    //
    // Returns true if the supplied FeatureSetQueryParameters are consistent
    // with a particular FeatureSet.
    //
    private
    boolean
    matches (FeatureSetInfo fsInfo,
             FeatureSetQueryParameters params)
      {
        if (params == null)
          {
            return true;
          }

        if (params.providers != null && !params.providers.isEmpty ()
            && !matches (params.providers, PROVIDER, '%')
            || params.types != null && !params.types.isEmpty ()
            && !matches (params.types, TYPE, '%')
            || params.ids != null && !params.ids.isEmpty ()
            && !params.ids.contains (Long.valueOf (fsInfo.fsid))
            || params.names != null && !params.names.isEmpty ()
            && !matches (params.names, fsInfo.featureSetName, '%')
            || params.visibleOnly && !fsInfo.visible)
          {
            return false;
          }
        
        return true;
      }
    

    private
    FeatureCursor
    queryFeatures (LayerInfo layer,
                   FeatureQueryParameters params)
      {
        return new FeatureCursorImpl (schemaHandler.queryFeatures (layer.name,
                                                                   params),
                                      schemaHandler,
                                      layer.layerId);
      }


    //
    // Splits the supplied FeatureQueryParameters into FeatureSet-specific
    // parameters.
    //
    /**
     * Splits the specified query parameters into layer-specific parameters.
     * Any feature set filtering will be expanded into all matching feature set
     * names on per-layer basis.
     *  
     * @param params    The parameters
     * 
     * @return  
     */
    private
    Map<LayerInfo, FeatureQueryParameters>
    splitQuery (FeatureQueryParameters params)
      {
        Map<LayerInfo, FeatureQueryParameters> result
            = new HashMap<LayerInfo, FeatureQueryParameters> ();

        for (LayerInfo layer : layersById.values())
          {
            if (matches (layer.name, params))
              {
                result.put (layer, filterQuery (layer, params));
              }
          }

        return result;
      }


    //==================================
    //  PRIVATE REPRESENTATION
    //==================================


    //
    // TODO - These two should probably be promoted to FeatureDataStore.
    //
    private static final int MODIFY_PROHIBITED = 0;
//    private static final int VISIBILITY_SETTINGS_PROHIBITED = 0;

    private static final String TAG = "GeoPackageFeatureDataStore";
    private static final String PROVIDER = "gpkg";
    private static final String TYPE = "gpkg";

    private final OnFeatureDefinitionsChangedListener changeListener
        = new OnFeatureDefinitionsChangedListener ()
                {
                  @Override
                  public
                  void
                  onFeatureDefinitionsChanged (GeoPackageSchemaHandler handler)
                    {
                      synchronized(GeoPackageFeatureDataStore.this) {
                          schemaRefreshes++;
                          dispatchDataStoreContentChangedNoSync ();
                      }
                    }
                };
    private final Map<String, LayerInfo> layersByName
        = new HashMap<String, LayerInfo> ();
    private final Map<Long, LayerInfo> layersById
    = new HashMap<Long, LayerInfo> ();
    private int nextLayerId = 1;
    private final Map<Long, FeatureSetInfo> featureSetInfos
        = new HashMap<Long, FeatureSetInfo> ();
    private int nextFSID = 1;
    private final String URI;

    protected GeoPackage geoPackage;
    protected GeoPackageSchemaHandler schemaHandler;
    protected boolean disposed;
    protected int fsIdBits;               // Number of feature set bits in id.
    protected long fIdMask;               // Bit mask for feature portion of id.
    protected int schemaRefreshes;
    
    private static class LayerInfo {
        public final String name;
        public final int layerId;
        public final Map<Long, FeatureSetInfo> featureSets;
        public final Set<String> featureSetNames;
        public final double minResolution;
        public final double maxResolution;
        
        public LayerInfo(int id, String name, double minResolution, double maxResolution) {
            this.layerId = id;
            this.name = name;
            this.featureSets = new HashMap<Long, FeatureSetInfo>();
            this.featureSetNames = new HashSet<String>();
            this.minResolution = minResolution;
            this.maxResolution = maxResolution;
        }
    }
  }
