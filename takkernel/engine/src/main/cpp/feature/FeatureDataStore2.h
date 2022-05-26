#ifndef TAK_ENGINE_FEATURE_FEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATUREDATASTORE2_H_INCLUDED

#include <cstring>
#include <limits>
#include <memory>

#include "feature/Geometry.h"
#include "feature/Feature2.h"
#include "feature/FeatureSet2.h"
#include "feature/Point.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "port/String.h"
#include "util/Error.h"

namespace atakmap {
    namespace feature {
        class ENGINE_API Style;
    }
    namespace util {
        class ENGINE_API AttributeSet;
    }
}

#define TE_TIMESTAMP_NONE (std::numeric_limits<int64_t>::min())

namespace TAK {
    namespace Engine {
        namespace Feature {

            class ENGINE_API FeatureCursor2;
            class ENGINE_API FeatureSetCursor2;

            typedef std::unique_ptr<FeatureCursor2, void(*)(const FeatureCursor2 *)> FeatureCursorPtr;
            typedef std::unique_ptr<FeatureSetCursor2, void(*)(const FeatureSetCursor2 *)> FeatureSetCursorPtr;


            class ENGINE_API FeatureDataStore2
            {
            public :
                enum
                {
                    FEATURESET_ID_NONE = 0,
                    FEATURE_ID_NONE = 0,
                    FEATURE_VERSION_NONE = 0,
                    FEATURESET_VERSION_NONE = 0,

                    VISIBILITY_SETTINGS_FEATURE =       0x01,
                    VISIBILITY_SETTINGS_FEATURESET =    0x02,

                    MODIFY_BULK_MODIFICATIONS =             0x000008,
                    MODIFY_FEATURESET_INSERT =              0x000001,
                    MODIFY_FEATURESET_UPDATE =              0x000002,
                    MODIFY_FEATURESET_DELETE =              0x000004,
                    MODIFY_FEATURESET_FEATURE_INSERT =      0x000010,
                    MODIFY_FEATURESET_FEATURE_UPDATE =      0x000020,
                    MODIFY_FEATURESET_FEATURE_DELETE =      0x000040,
                    MODIFY_FEATURESET_NAME =                0x000080,
                    MODIFY_FEATURESET_DISPLAY_THRESHOLDS =  0x000100,
                    MODIFY_FEATURESET_READONLY =            0X000200,
                    MODIFY_FEATURE_NAME =                   0x000400,
                    MODIFY_FEATURE_GEOMETRY =               0x000800,
                    MODIFY_FEATURE_STYLE =                  0x001000,
                    MODIFY_FEATURE_ATTRIBUTES =             0x002000,

                    UPDATE_ATTRIBUTESET_SET =               0,
                    UPDATE_ATTRIBUTESET_ADD_OR_REPLACE =    1,
                };

                enum AttributeUpdate
                {
                    Replace,
                    Update,
                    AddUpdate,
                };
            public :
                class ENGINE_API OnDataStoreContentChangedListener;
                class ENGINE_API FeatureQueryParameters;
                class ENGINE_API FeatureSetQueryParameters;
            protected :
                virtual ~FeatureDataStore2() NOTHROWS = 0;
            public :
                virtual Util::TAKErr addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS = 0;
                virtual Util::TAKErr removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS = 0;
                virtual Util::TAKErr getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS = 0;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &cursor) NOTHROWS = 0;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS = 0;
                virtual Util::TAKErr queryFeaturesCount(int *value) NOTHROWS = 0;
                virtual Util::TAKErr queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS = 0;
                virtual Util::TAKErr getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS = 0;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS = 0;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS = 0;
                virtual Util::TAKErr queryFeatureSetsCount(int *value) NOTHROWS = 0;
                virtual Util::TAKErr queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS = 0;
                virtual Util::TAKErr getModificationFlags(int *value) NOTHROWS = 0;
                virtual Util::TAKErr beginBulkModification() NOTHROWS = 0;
                virtual Util::TAKErr endBulkModification(const bool successful) NOTHROWS = 0;
                virtual Util::TAKErr isInBulkModification(bool *value) NOTHROWS = 0;
                virtual Util::TAKErr insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS = 0;
                virtual Util::TAKErr deleteFeatureSet(const int64_t fsid) NOTHROWS = 0;
                virtual Util::TAKErr deleteAllFeatureSets() NOTHROWS = 0;
                virtual Util::TAKErr insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS = 0;
                virtual Util::TAKErr updateFeature(const int64_t fid, const char *name) NOTHROWS = 0;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS = 0;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS = 0;
                virtual Util::TAKErr updateFeature(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS = 0;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS = 0;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS = 0;
                virtual Util::TAKErr updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS = 0;
                virtual Util::TAKErr deleteFeature(const int64_t fid) NOTHROWS = 0;
                virtual Util::TAKErr deleteAllFeatures(const int64_t fsid) NOTHROWS = 0;
                virtual Util::TAKErr getVisibilitySettingsFlags(int *value) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetsReadOnly(const FeatureSetQueryParameters &paramsRef, const bool readOnly) NOTHROWS = 0;
                virtual Util::TAKErr isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS = 0;
                virtual Util::TAKErr isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS = 0;
                virtual Util::TAKErr isFeatureReadOnly(bool *value, const int64_t fsid) NOTHROWS = 0;
                virtual Util::TAKErr isAvailable(bool *value) NOTHROWS = 0;
                virtual Util::TAKErr refresh() NOTHROWS = 0;
                virtual Util::TAKErr getUri(Port::String &value) NOTHROWS = 0;
                virtual Util::TAKErr close() NOTHROWS = 0;

#if 0 // candidates
                virtual Util::TAKErr updateFeatures(const FeatureQueryParameters &params, const char *name) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatures(const FeatureQueryParameters &params, const atakmap::feature::Geometry &geom) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatures(const FeatureQueryParameters &params, const atakmap::feature::Style *style) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatures(const FeatureQueryParameters &params, const atakmap::util::AttributeSet *attribs, const AttributeUpdate updateType) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatures(const FeatureQueryParameters &params, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet *attribs, const AttributeUpdate updateType) NOTHROWS = 0;
                virtual Util::TAKErr deleteFeatures(const FeatureQueryParameters &params) NOTHROWS = 0;

                virtual Util::TAKErr getFeatureModificationFlags(int *value, const int64_t fid) NOTHROWS = 0;
                virtual Util::TAKErr getFeatureSetModificationFlags(int *value, const int64_t fsid) NOTHROWS = 0;
                virtual Util::TAKErr lock(bool block = true) NOTHROWS = 0;
                virtual Util::TAKErr unlock() NOTHROWS = 0;
#endif
            }; // FeatureDataStore


            typedef std::unique_ptr<FeatureDataStore2, void(*)(const FeatureDataStore2 *)> FeatureDataStore2Ptr;

            /**************************************************************************/

            class ENGINE_API FeatureDataStore2::OnDataStoreContentChangedListener
            {
            protected :
                virtual ~OnDataStoreContentChangedListener() NOTHROWS = 0;
            public :
                virtual void onDataStoreContentChanged(FeatureDataStore2 &dataStore) NOTHROWS = 0;
            };

            /**************************************************************************/

            class ENGINE_API FeatureDataStore2::FeatureQueryParameters
            {
            public :
                struct ENGINE_API SpatialOp
                {
                    enum Type
                    {
                        Buffer,
                        Simplify,
                    };

                    Type type;
                    union
                    {
                        struct
                        {
                            double distance;
                        } buffer;
                        struct
                        {
                            double distance;
                        } simplify;
                    } args;

                    bool operator==(const SpatialOp &other)
                    {
                        return (memcmp(this, &other, sizeof(SpatialOp)) == 0);
                    }
                };

                struct ENGINE_API Order
                {
                    enum Type
                    {
                        Resolution,
                        FeatureSet,
                        FeatureName,
                        FeatureId,
                        Distance,
                        GeometryType,
                    };

                    Type type;
                    union
                    {
                        struct
                        {
                            double x;
                            double y;
                            double z;
                        } distance;
                    } args;

                    bool operator==(const Order &other)
                    {
                        return (memcmp(this, &other, sizeof(Order)) == 0);
                    }
                };

                enum IgnoreFields
                {
                    GeometryField = 0x01,
                    StyleField = 0x02,
                    AttributesField = 0x04,
                    NameField = 0x08,
                };
            public :
                FeatureQueryParameters() NOTHROWS;
                FeatureQueryParameters(const FeatureQueryParameters &other) NOTHROWS;
            public :
                ~FeatureQueryParameters();
            public :
                FeatureQueryParameters &operator=(const FeatureQueryParameters &other) NOTHROWS;
            public :
                Port::Collection<Port::String> * const providers;
                Port::Collection<Port::String> * const types;
                Port::Collection<Port::String> * const featureSets;
                Port::Collection<int64_t> * const featureSetIds;
                Port::Collection<Port::String> * const featureNames;
                Port::Collection<int64_t> * const featureIds;
                GeometryPtr_const spatialFilter;
                double minResolution;
                double maxResolution;
                bool visibleOnly;
                Port::Collection<atakmap::feature::Geometry::Type> * const geometryTypes;
                //atakmap::feature:: attributes;
                Port::Collection<SpatialOp> * const ops;
                Port::Collection<Order> * const order;
                int ignoredFields;
                int limit;
                int offset;
            }; // FeatureQueryParameters

            class ENGINE_API FeatureDataStore2::FeatureSetQueryParameters
            {
            public :
                FeatureSetQueryParameters() NOTHROWS;
            public :
                ~FeatureSetQueryParameters();
            public :
                Port::Collection<Port::String> * const providers;
                Port::Collection<Port::String> * const types;
                Port::Collection<Port::String> * const names;
                Port::Collection<int64_t> * const ids;
                bool visibleOnly;
                int limit;
                int offset;
            }; // FeatureSetQueryParameters
        }
    }
}

#endif
