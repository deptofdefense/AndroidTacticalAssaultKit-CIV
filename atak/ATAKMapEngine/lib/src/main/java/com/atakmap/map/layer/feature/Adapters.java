package com.atakmap.map.layer.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.math.MathUtils;

public final class Adapters {
    private Adapters() {}
    
    private static <T> Set<T> setOrCreate(Collection<T> src, Set<T> dst) {
        if(src == null)
            return null;
        if(dst == null) {
            dst = new HashSet<T>(src);
        } else {
            dst.clear();
            dst.addAll(src);
        }
        return dst;
    }
    
    private static <T> Collection<T> setOrCreate(Collection<T> src, Collection<T> dst) {
        if(src == null)
            return null;
        if(dst == null) {
            dst = new HashSet<T>(src);
        } else {
            dst.clear();
            dst.addAll(src);
        }
        return dst;
    }
    
    public static FeatureDataStore2 adapt(FeatureDataStore legacy) {
        return new FeatureDataStoreAdapter(legacy);
    }
    
    public static FeatureDataStore.FeatureQueryParameters adapt(FeatureDataStore2.FeatureQueryParameters src, FeatureDataStore.FeatureQueryParameters dst) {
        if(src == null)
            return null;
        if(dst == null)
            dst = new FeatureDataStore.FeatureQueryParameters();
        
        dst.featureIds = setOrCreate(src.ids, dst.featureIds);
        dst.featureNames = setOrCreate(src.names, dst.featureNames);
        if(src.featureSetFilter != null) {
            dst.featureSetIds = setOrCreate(src.featureSetFilter.ids, dst.featureSetIds);
            dst.featureSets = setOrCreate(src.featureSetFilter.names, dst.featureSets);
            dst.minResolution = src.featureSetFilter.minResolution;
            dst.maxResolution = src.featureSetFilter.maxResolution;
        }
        dst.geometryTypes = setOrCreate(src.geometryTypes, dst.geometryTypes);
        {
            dst.ignoredFields = 0;
            if(MathUtils.hasBits(src.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_ATTRIBUTES))
                dst.ignoredFields |= FeatureDataStore.FeatureQueryParameters.FIELD_ATTRIBUTES;
            if(MathUtils.hasBits(src.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY))
                dst.ignoredFields |= FeatureDataStore.FeatureQueryParameters.FIELD_GEOMETRY;
            if(MathUtils.hasBits(src.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_NAME))
                dst.ignoredFields |= FeatureDataStore.FeatureQueryParameters.FIELD_NAME;
            if(MathUtils.hasBits(src.ignoredFeatureProperties, FeatureDataStore2.PROPERTY_FEATURE_STYLE))
                dst.ignoredFields |= FeatureDataStore.FeatureQueryParameters.FIELD_STYLE;
        }
        dst.limit = src.limit;
        dst.offset = src.offset;
        if(src.spatialFilter != null) {
            Envelope filterBounds = src.spatialFilter.getEnvelope();
            dst.spatialFilter = new FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter(new GeoPoint(filterBounds.maxY, filterBounds.minX), new GeoPoint(filterBounds.minY, filterBounds.maxX));
        } else {
            dst.spatialFilter = null;
        }
        if(src.spatialOps != null) {
            if(dst.ops == null)
                dst.ops = new ArrayList<FeatureDataStore.FeatureQueryParameters.SpatialOp>(src.spatialOps.size());
            else
                dst.ops.clear();
            
            for(FeatureDataStore2.FeatureQueryParameters.SpatialOp srcOp : src.spatialOps) {
                if(srcOp instanceof FeatureDataStore2.FeatureQueryParameters.SpatialOp.Buffer) {
                    FeatureDataStore2.FeatureQueryParameters.SpatialOp.Buffer buffer = (FeatureDataStore2.FeatureQueryParameters.SpatialOp.Buffer)srcOp;
                    dst.ops.add(new FeatureDataStore.FeatureQueryParameters.Buffer(buffer.distance));
                } else if(srcOp instanceof FeatureDataStore2.FeatureQueryParameters.SpatialOp.Simplify) {
                    FeatureDataStore2.FeatureQueryParameters.SpatialOp.Simplify simplify = (FeatureDataStore2.FeatureQueryParameters.SpatialOp.Simplify)srcOp;
                    dst.ops.add(new FeatureDataStore.FeatureQueryParameters.Simplify(simplify.distance));
                }
            }
        }
        dst.visibleOnly = src.visibleOnly;

        return dst;
    }

    public static FeatureDataStore2.FeatureQueryParameters adapt(FeatureDataStore.FeatureQueryParameters src, FeatureDataStore2.FeatureQueryParameters dst) {
        if(src == null)
            return null;
        if(dst == null)
            dst = new FeatureDataStore2.FeatureQueryParameters();
        
        if(dst.featureSetFilter == null)
            dst.featureSetFilter = new FeatureDataStore2.FeatureSetQueryParameters();
        
        dst.ids = setOrCreate(src.featureIds, dst.ids);
        dst.names = setOrCreate(src.featureNames, dst.names);
        dst.featureSetFilter.ids = setOrCreate(src.featureSetIds, dst.featureSetFilter.ids);
        dst.featureSetFilter.names = setOrCreate(src.featureSets, dst.featureSetFilter.names);
        dst.featureSetFilter.providers = setOrCreate(src.providers, dst.featureSetFilter.providers);
        dst.featureSetFilter.types = setOrCreate(src.types, dst.featureSetFilter.types);
        dst.featureSetFilter.minResolution = src.minResolution;
        dst.featureSetFilter.maxResolution = src.maxResolution;
        dst.featureSetFilter.limit = 0;
        dst.featureSetFilter.offset = 0;
        dst.geometryTypes = setOrCreate(src.geometryTypes, dst.geometryTypes);
        {
            dst.ignoredFeatureProperties = 0;
            if(MathUtils.hasBits(src.ignoredFields, FeatureDataStore.FeatureQueryParameters.FIELD_ATTRIBUTES))
                dst.ignoredFeatureProperties |= FeatureDataStore2.PROPERTY_FEATURE_ATTRIBUTES;
            if(MathUtils.hasBits(src.ignoredFields, FeatureDataStore.FeatureQueryParameters.FIELD_GEOMETRY))
                dst.ignoredFeatureProperties |= FeatureDataStore2.PROPERTY_FEATURE_GEOMETRY;
            if(MathUtils.hasBits(src.ignoredFields, FeatureDataStore.FeatureQueryParameters.FIELD_NAME))
                dst.ignoredFeatureProperties |= FeatureDataStore2.PROPERTY_FEATURE_NAME;
            if(MathUtils.hasBits(src.ignoredFields, FeatureDataStore.FeatureQueryParameters.FIELD_STYLE))
                dst.ignoredFeatureProperties |= FeatureDataStore2.PROPERTY_FEATURE_STYLE;
        }
        dst.limit = src.limit;
        dst.offset = src.offset;
        if(src.spatialFilter instanceof FeatureDataStore.FeatureQueryParameters.RadiusSpatialFilter) {
            FeatureDataStore.FeatureQueryParameters.RadiusSpatialFilter radius = (FeatureDataStore.FeatureQueryParameters.RadiusSpatialFilter)src.spatialFilter;
            GeoPoint n = GeoCalculations.pointAtDistance(radius.point, 0d, radius.radius);
            GeoPoint e = GeoCalculations.pointAtDistance(radius.point, 90d, radius.radius);
            GeoPoint s = GeoCalculations.pointAtDistance(radius.point, 180d, radius.radius);
            GeoPoint w = GeoCalculations.pointAtDistance(radius.point, 270d, radius.radius);
            dst.spatialFilter = GeometryFactory.fromEnvelope(new Envelope(w.getLongitude(), s.getLatitude(), 0d, e.getLongitude(), n.getLatitude(), 0d));
        } else if(src.spatialFilter instanceof FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter) {
            FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter region = (FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter)src.spatialFilter;
            dst.spatialFilter = GeometryFactory.fromEnvelope(new Envelope(region.upperLeft.getLongitude(), region.lowerRight.getLatitude(), 0d, region.lowerRight.getLongitude(), region.upperLeft.getLatitude(), 0d));
        } else {
            dst.spatialFilter = null;
        }
        if(src.ops != null) {
            if(dst.spatialOps == null)
                dst.spatialOps = new ArrayList<FeatureDataStore2.FeatureQueryParameters.SpatialOp>(src.ops.size());
            else
                dst.spatialOps.clear();
            
            for(FeatureDataStore.FeatureQueryParameters.SpatialOp srcOp : src.ops) {
                if(srcOp instanceof FeatureDataStore.FeatureQueryParameters.Buffer) {
                    FeatureDataStore.FeatureQueryParameters.Buffer buffer = (FeatureDataStore.FeatureQueryParameters.Buffer)srcOp;
                    dst.spatialOps.add(new FeatureDataStore2.FeatureQueryParameters.SpatialOp.Buffer(buffer.distance));
                } else if(srcOp instanceof FeatureDataStore.FeatureQueryParameters.Simplify) {
                    FeatureDataStore.FeatureQueryParameters.Simplify simplify = (FeatureDataStore.FeatureQueryParameters.Simplify)srcOp;
                    dst.spatialOps.add(new FeatureDataStore2.FeatureQueryParameters.SpatialOp.Simplify(simplify.distance));
                }
            }
        }
        dst.minimumTimestamp = FeatureDataStore2.TIMESTAMP_NONE;
        dst.maximumTimestamp = FeatureDataStore2.TIMESTAMP_NONE;
        dst.visibleOnly = src.visibleOnly;
        dst.attributeFilters = null;

        return dst;
    }
    
    public static FeatureDataStore.FeatureSetQueryParameters adapt(FeatureDataStore2.FeatureSetQueryParameters src, FeatureDataStore.FeatureSetQueryParameters dst) {
        if(src == null)
            return null;
        if(dst == null)
            dst = new FeatureDataStore.FeatureSetQueryParameters();
        dst.ids = setOrCreate(src.ids, dst.ids);
        dst.names = setOrCreate(src.names, dst.names);
        dst.providers = setOrCreate(src.providers, dst.providers);
        dst.types = setOrCreate(src.types, dst.types);
        dst.visibleOnly = src.visibleOnly;
        dst.limit = src.limit;
        dst.offset = src.offset;
        return dst;
    }
    
    public static FeatureDataStore2.FeatureSetQueryParameters adapt(FeatureDataStore.FeatureSetQueryParameters src, FeatureDataStore2.FeatureSetQueryParameters dst) {
        if(src == null)
            return null;
        if(dst == null)
            dst = new FeatureDataStore2.FeatureSetQueryParameters();
        dst.ids = setOrCreate(src.ids, dst.ids);
        dst.names = setOrCreate(src.names, dst.names);
        dst.providers = setOrCreate(src.providers, dst.providers);
        dst.types = setOrCreate(src.types, dst.types);
        dst.minResolution = Double.NaN;
        dst.maxResolution = Double.NaN;
        dst.visibleOnly = src.visibleOnly;
        dst.limit = src.limit;
        dst.offset = src.offset;
        return dst;
    }
    
    public static int adaptVisibilityFlags(int srcFlags, int srcFlagsApiVer, int dstFlagsApiVer) {
        if(srcFlagsApiVer == dstFlagsApiVer)
            return srcFlags;
        
        if(srcFlagsApiVer == 1 && dstFlagsApiVer == 2) {
            int retval = 0;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.VISIBILITY_SETTINGS_FEATURE))
                retval |= FeatureDataStore2.VISIBILITY_SETTINGS_FEATURE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.VISIBILITY_SETTINGS_FEATURESET))
                retval |= FeatureDataStore2.VISIBILITY_SETTINGS_FEATURESET;
            return retval;
        } else if(srcFlagsApiVer == 2 && dstFlagsApiVer == 1) {
            int retval = 0;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.VISIBILITY_SETTINGS_FEATURE))
                retval |= FeatureDataStore.VISIBILITY_SETTINGS_FEATURE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.VISIBILITY_SETTINGS_FEATURESET))
                retval |= FeatureDataStore.VISIBILITY_SETTINGS_FEATURESET;
            return retval;            
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static int adaptModificationFlags(int srcFlags, int srcFlagsVer, int dstFlagsVer) {
        if(srcFlagsVer == dstFlagsVer)
            return srcFlags;
        
        if(srcFlagsVer == 1 && dstFlagsVer == 2) {
            int dstFlags = 0;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_INSERT))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_INSERT;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_UPDATE))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_UPDATE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_DELETE))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_DELETE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_FEATURE_INSERT))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_FEATURE_INSERT;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_FEATURE_UPDATE))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_FEATURE_UPDATE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_FEATURE_DELETE))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_FEATURE_DELETE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_NAME))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_NAME;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURESET_DISPLAY_THRESHOLDS))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURESET_DISPLAY_THRESHOLDS;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURE_NAME))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURE_NAME;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURE_GEOMETRY))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURE_GEOMETRY;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURE_STYLE))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURE_STYLE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore.MODIFY_FEATURE_ATTRIBUTES))
                dstFlags |= FeatureDataStore2.MODIFY_FEATURE_ATTRIBUTES;
            return dstFlags;
        } else if(srcFlagsVer == 2 && dstFlagsVer == 1) {
            int dstFlags = 0;
            dstFlags |= FeatureDataStore.MODIFY_BULK_MODIFICATIONS;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_INSERT))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_INSERT;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_UPDATE))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_UPDATE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_DELETE))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_DELETE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_FEATURE_INSERT))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_FEATURE_INSERT;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_FEATURE_UPDATE))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_FEATURE_UPDATE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_FEATURE_DELETE))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_FEATURE_DELETE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_NAME))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_NAME;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURESET_DISPLAY_THRESHOLDS))
                dstFlags |= FeatureDataStore.MODIFY_FEATURESET_DISPLAY_THRESHOLDS;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURE_NAME))
                dstFlags |= FeatureDataStore.MODIFY_FEATURE_NAME;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURE_GEOMETRY))
                dstFlags |= FeatureDataStore.MODIFY_FEATURE_GEOMETRY;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURE_STYLE))
                dstFlags |= FeatureDataStore.MODIFY_FEATURE_STYLE;
            if(MathUtils.hasBits(srcFlags, FeatureDataStore2.MODIFY_FEATURE_ATTRIBUTES))
                dstFlags |= FeatureDataStore.MODIFY_FEATURE_ATTRIBUTES;
            return dstFlags;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static FeatureDefinition2 adapt(final FeatureDefinition def) {
        if(def instanceof FeatureDefinition2)
            return (FeatureDefinition2)def;
        else
            return new FeatureDefinition2() {
                @Override
                public int getStyleCoding() { return def.getStyleCoding(); }
                @Override
                public Object getRawStyle() { return def.getRawStyle(); }
                @Override
                public Object getRawGeometry() { return def.getRawGeometry(); }
                @Override
                public String getName() { return def.getName(); }
                @Override
                public int getGeomCoding() { return def.getGeomCoding(); }
                @Override
                public AttributeSet getAttributes() { return def.getAttributes(); }
                @Override
                public Feature get() { return def.get(); }
                @Override
                public long getTimestamp() { return FeatureDataStore2.TIMESTAMP_NONE; }
            };
    }
    
    public static FeatureDefinition2 adapt(final Feature f) {
        return new FeatureDefinition2() {
            @Override
            public int getStyleCoding() { return STYLE_ATAK_STYLE; }
            @Override
            public Object getRawStyle() { return f.getStyle(); }
            @Override
            public Object getRawGeometry() { return f.getGeometry(); }
            @Override
            public String getName() { return f.getName(); }
            @Override
            public int getGeomCoding() { return GEOM_ATAK_GEOMETRY; }
            @Override
            public AttributeSet getAttributes() { return f.getAttributes(); }
            @Override
            public Feature get() { return f; }
            @Override
            public long getTimestamp() { return f.getTimestamp(); }
        };
    }
    /**************************************************************************/

    static final class FeatureSetCursorAdapter implements FeatureSetCursor {
        final FeatureDataStore.FeatureSetCursor impl;
        
        FeatureSetCursorAdapter(FeatureDataStore.FeatureSetCursor impl) {
            this.impl = impl;
        }

        @Override
        public boolean moveToNext() { return this.impl.moveToNext(); }
        @Override
        public void close() { this.impl.close(); }
        @Override
        public boolean isClosed() { return this.impl.isClosed(); }
        @Override
        public FeatureSet get() { return this.impl.get(); }
        @Override
        public long getId() { return this.impl.get().getId(); }
        @Override
        public String getType() { return this.impl.get().getType(); }
        @Override
        public String getProvider() { return this.impl.get().getProvider(); }
        @Override
        public String getName() { return this.impl.get().getName(); }
        @Override
        public double getMinResolution() { return this.impl.get().getMinResolution(); }
        @Override
        public double getMaxResolution() { return this.impl.get().getMaxResolution(); }
    }
    
    static class FeatureDataStoreAdapter implements FeatureDataStore2 {

        private class CallbackForwarder implements FeatureDataStore.OnDataStoreContentChangedListener {
            
            final OnDataStoreContentChangedListener impl;
            
            CallbackForwarder(OnDataStoreContentChangedListener impl) {
                this.impl = impl;
            }

            @Override
            public void onDataStoreContentChanged(FeatureDataStore dataStore) {
                this.impl.onDataStoreContentChanged(FeatureDataStoreAdapter.this);
            }
            
        }

        final FeatureDataStore impl;
        final Map<OnDataStoreContentChangedListener, CallbackForwarder> listeners;
        
        FeatureDataStoreAdapter(FeatureDataStore impl) {
            this.impl = impl;
            this.listeners = new IdentityHashMap<>();
        }

        @Override
        public void dispose() { this.impl.dispose(); }

        @Override
        public FeatureCursor queryFeatures(FeatureQueryParameters params) throws DataStoreException {
            return this.impl.queryFeatures(adapt(params, (FeatureDataStore.FeatureQueryParameters)null));
        }

        @Override
        public int queryFeaturesCount(FeatureQueryParameters params) throws DataStoreException {
            return this.impl.queryFeaturesCount(adapt(params, (FeatureDataStore.FeatureQueryParameters)null));
        }

        @Override
        public FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
            return new FeatureSetCursorAdapter(this.impl.queryFeatureSets(adapt(params, (FeatureDataStore.FeatureSetQueryParameters)null)));
        }

        @Override
        public int queryFeatureSetsCount(FeatureSetQueryParameters params) throws DataStoreException {
            return this.impl.queryFeatureSetsCount(adapt(params, (FeatureDataStore.FeatureSetQueryParameters)null));
        }

        @Override
        public long insertFeature(long fsid, long fid, FeatureDefinition2 def, long version)
                throws DataStoreException {
            
            if(fsid == FEATURESET_ID_NONE)
                throw new IllegalArgumentException("fsid != featureset_id_none");
            if(fid != FEATURE_ID_NONE)
                throw new IllegalArgumentException("fid != feature_id_none");
            if(version != FEATURE_VERSION_NONE)
                throw new IllegalArgumentException("version != feature_version_none");

            Feature f = this.impl.insertFeature(fsid, def, true);

            if(f == null)
                throw new IllegalArgumentException("unknown feature insertion failure");

            return f.getId();
        }

        @Override
        public long insertFeature(final Feature feature) throws DataStoreException {
            return this.insertFeature(feature.getFeatureSetId(), feature.getId(), new FeatureDefinition2() {
                @Override
                public Object getRawGeometry() { return feature.getGeometry(); } 
                @Override
                public int getGeomCoding() { return GEOM_ATAK_GEOMETRY; }
                @Override
                public String getName() { return feature.getName(); }
                @Override
                public int getStyleCoding() { return STYLE_ATAK_STYLE; }
                @Override
                public Object getRawStyle() { return feature.getStyle(); }
                @Override
                public AttributeSet getAttributes() { return feature.getAttributes(); }
                @Override
                public long getTimestamp() { return feature.getTimestamp(); }
                @Override
                public Feature get() { return feature; }
            }, feature.getVersion());
        }

        @Override
        public void insertFeatures(FeatureCursor features) throws DataStoreException {
            this.impl.beginBulkModification();
            boolean success = false;
            try {
                while(features.moveToNext())
                    this.insertFeature(features.get());
                success = true;
            } finally {
                this.impl.endBulkModification(success);
            }
        }

        @Override
        public long insertFeatureSet(FeatureSet featureSet) throws DataStoreException {
            if(featureSet.getId() != FEATURESET_ID_NONE)
                throw new IllegalArgumentException("featureset_id not equal to none for " + featureSet.getProvider() + "-" + featureSet.getName());
            
            Long retval = this.impl.insertFeatureSet(featureSet.getProvider(), featureSet.getType(), featureSet.getName(), featureSet.getMinResolution(), featureSet.getMaxResolution(), true).getId();
            if (retval == null) 
                throw new IllegalArgumentException("feature insertion failed for " + featureSet.getProvider() + "-" +  featureSet.getName());

            return retval;
                
        }

        @Override
        public void insertFeatureSets(FeatureSetCursor featureSet) throws DataStoreException {
            this.impl.beginBulkModification();
            boolean success = false;
            try {
                while(featureSet.moveToNext())
                    this.insertFeatureSet(featureSet.get());
                success = true;
            } finally {
                this.impl.endBulkModification(success);
            }
        }

        @Override
        public void updateFeature(long fid, int updatePropertyMask, String name, Geometry geometry, Style style, AttributeSet attributes, int attrUpdateType) throws DataStoreException {
            this.impl.beginBulkModification();
            boolean success = false;
            try {
                if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_NAME))
                    this.impl.updateFeature(fid, name);
                if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_GEOMETRY))
                    this.impl.updateFeature(fid, geometry);
                if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_STYLE))
                    this.impl.updateFeature(fid, style);
                if(MathUtils.hasBits(updatePropertyMask, PROPERTY_FEATURE_ATTRIBUTES)) {
                    switch(attrUpdateType) {
                        case UPDATE_ATTRIBUTES_ADD_OR_REPLACE :
                            Feature f = this.impl.getFeature(fid);
                            if(f != null && f.getAttributes() != null) {
                                final AttributeSet old = new AttributeSet(f.getAttributes());
                                for(String key : attributes.getAttributeNames()) {
                                    Class<?> attrType = attributes.getAttributeType(key);
                                    if(attrType == null)
                                        old.removeAttribute(key);
                                    else if(attrType == Integer.TYPE)
                                        old.setAttribute(key, attributes.getIntAttribute(key));
                                    else if(attrType == Long.TYPE)
                                        old.setAttribute(key, attributes.getLongAttribute(key));
                                    else if(attrType == Double.TYPE)
                                        old.setAttribute(key, attributes.getDoubleAttribute(key));
                                    else if(attrType == String.class)
                                        old.setAttribute(key, attributes.getStringAttribute(key));
                                    else if(attrType == byte[].class)
                                        old.setAttribute(key, attributes.getBinaryAttribute(key));
                                    else if(attrType == int[].class)
                                        old.setAttribute(key, attributes.getIntArrayAttribute(key));
                                    else if(attrType == long[].class)
                                        old.setAttribute(key, attributes.getLongArrayAttribute(key));
                                    else if(attrType == double[].class)
                                        old.setAttribute(key, attributes.getDoubleArrayAttribute(key));
                                    else if(attrType == String[].class)
                                        old.setAttribute(key, attributes.getStringArrayAttribute(key));
                                    else if(attrType == byte[][].class)
                                        old.setAttribute(key, attributes.getBinaryArrayAttribute(key));
                                    else if(attrType == AttributeSet.class)
                                        old.setAttribute(key, attributes.getAttributeSetAttribute(key));
                                }
                                attributes = old;
                            }
                        case UPDATE_ATTRIBUTES_SET :
                            this.impl.updateFeature(fid, attributes);
                            break;
                        default :
                            throw new IllegalArgumentException();
                    }
                }
                success = true;
            } finally {
                this.impl.endBulkModification(success);
            }
        }

        @Override
        public void updateFeatureSet(long fsid, String name, double minResolution, double maxResolution) throws DataStoreException {
            this.impl.updateFeatureSet(fsid, name, minResolution, maxResolution);
        }
        @Override
        public void updateFeatureSet(long fsid, String name) throws DataStoreException {
            this.impl.updateFeatureSet(fsid, name);
        }
        @Override
        public void updateFeatureSet(long fsid, double minResolution, double maxResolution) throws DataStoreException {
            this.impl.updateFeatureSet(fsid, minResolution, maxResolution);
        }

        @Override
        public void deleteFeature(long fid) throws DataStoreException { this.impl.deleteFeature(fid); }

        @Override
        public void deleteFeatures(FeatureQueryParameters params) throws DataStoreException {
            this.impl.beginBulkModification();
            boolean success = false;
            try {
                FeatureCursor result = null;
                try {
                    result = this.impl.queryFeatures(adapt(params, (FeatureDataStore.FeatureQueryParameters)null));
                    while(result.moveToNext())
                        this.impl.deleteFeature(result.getId());
                } finally {
                    if(result != null)
                        result.close();
                }
                success = true;
            } finally {
                this.impl.endBulkModification(success);
            }
        }

        @Override
        public void deleteFeatureSet(long fsid) throws DataStoreException { this.impl.deleteFeatureSet(fsid); }
        @Override
        public void deleteFeatureSets(FeatureSetQueryParameters params) throws DataStoreException {
            this.impl.beginBulkModification();
            boolean success = false;
            try {
                FeatureDataStore.FeatureSetCursor featureSet = null;
                try {
                    featureSet = this.impl.queryFeatureSets(adapt(params, (FeatureDataStore.FeatureSetQueryParameters)null));
                    while(featureSet.moveToNext())
                        this.impl.deleteFeatureSet(featureSet.get().getId());
                } finally {
                    if(featureSet != null)
                        featureSet.close();
                }
                success = true;
            } finally {
                this.impl.endBulkModification(success);
            }
        }

        @Override
        public void setFeatureVisible(long fid, boolean visible) throws DataStoreException {
            this.impl.setFeatureVisible(fid, visible);            
        }

        @Override
        public void setFeaturesVisible(FeatureQueryParameters params, boolean visible) throws DataStoreException {
            this.impl.setFeaturesVisible(adapt(params, (FeatureDataStore.FeatureQueryParameters)null), visible);
        }

        @Override
        public void setFeatureSetVisible(long fsid, boolean visible) throws DataStoreException {
            this.impl.setFeatureSetVisible(fsid, visible);
        }

        @Override
        public void setFeatureSetsVisible(FeatureSetQueryParameters params, boolean visible) throws DataStoreException {
            this.impl.setFeatureSetsVisible(adapt(params, (FeatureDataStore.FeatureSetQueryParameters)null), visible);
        }

        @Override
        public boolean hasTimeReference() { return false; }
        @Override
        public long getMinimumTimestamp() { return TIMESTAMP_NONE; }
        @Override
        public long getMaximumTimestamp() { return TIMESTAMP_NONE; }
        
        @Override
        public int getModificationFlags() {
            return adaptModificationFlags(impl.getModificationFlags(), 1, 2);
        }

        @Override
        public int getVisibilityFlags() {
            return adaptVisibilityFlags(impl.getVisibilitySettingsFlags(), 1, 2);
        }
        
        @Override
        public String getUri() { return this.impl.getUri(); }

        @Override
        public boolean hasCache() { return false; }
        @Override
        public void clearCache() {}
        @Override
        public long getCacheSize() { return 0L; }

        @Override
        public void addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
            CallbackForwarder forwarder;
            synchronized(this.listeners) {
                if(this.listeners.containsKey(l))
                    return;
                forwarder = new CallbackForwarder(l);
                this.listeners.put(l, forwarder);
            }
            this.impl.addOnDataStoreContentChangedListener(forwarder);
        }

        @Override
        public void removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener l) {
            CallbackForwarder forwarder;
            synchronized(this.listeners) {
                forwarder =this.listeners.remove(l);
                if(forwarder == null)
                    return;
            }
            this.impl.removeOnDataStoreContentChangedListener(forwarder);
        }

        @Override
        public void acquireModifyLock(boolean bulkModify) throws InterruptedException {
            if(bulkModify)
                this.impl.beginBulkModification();
        }
        @Override
        public void releaseModifyLock() {
            if(this.impl.isInBulkModification())
                this.impl.endBulkModification(true);
        }
        
        @Override
        public boolean supportsExplicitIDs() { return false; }
    }
}
