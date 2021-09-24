#ifndef TAK_ENGINE_FEATURE_RUNTIMECACHINGFEATUREDATASTORE_H_INCLUDED
#define TAK_ENGINE_FEATURE_RUNTIMECACHINGFEATUREDATASTORE_H_INCLUDED

#include "feature/FeatureDataStore2.h"
#include "feature/RuntimeFeatureDataStore2.h"
#include "thread/Mutex.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API RuntimeCachingFeatureDataStore : public FeatureDataStore2
            {
            private :
                class CachingFeatureCursor;
            public:
                RuntimeCachingFeatureDataStore(FeatureDataStore2Ptr &&impl) NOTHROWS;
            protected :
                virtual Util::TAKErr isClientQuery(bool *value, const FeatureQueryParameters &other) NOTHROWS;
            public: // FeatureDataStore2
                virtual Util::TAKErr addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS override;
                virtual Util::TAKErr removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS override;
                virtual Util::TAKErr getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &cursor) NOTHROWS override;
                virtual Util::TAKErr queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS override;
                virtual Util::TAKErr queryFeaturesCount(int *value) NOTHROWS override;
                virtual Util::TAKErr queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS override;
                virtual Util::TAKErr getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSetsCount(int *value) NOTHROWS override;
                virtual Util::TAKErr queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS override;
                virtual Util::TAKErr getModificationFlags(int *value) NOTHROWS override;
                virtual Util::TAKErr beginBulkModification() NOTHROWS override;
                virtual Util::TAKErr endBulkModification(const bool successful) NOTHROWS override;
                virtual Util::TAKErr isInBulkModification(bool *value) NOTHROWS override;
                virtual Util::TAKErr insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr deleteFeatureSet(const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr deleteAllFeatureSets() NOTHROWS override;
                virtual Util::TAKErr insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr updateFeature(const int64_t fid, const char *name) NOTHROWS override;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS override;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS override;
                virtual Util::TAKErr updateFeature(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS override;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS override;
                virtual Util::TAKErr updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr deleteFeature(const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr deleteAllFeatures(const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr getVisibilitySettingsFlags(int *value) NOTHROWS override;
                virtual Util::TAKErr setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS override;
                virtual Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS override;
                virtual Util::TAKErr isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsReadOnly(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS override;
                virtual Util::TAKErr isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr isAvailable(bool *value) NOTHROWS override;
                virtual Util::TAKErr refresh() NOTHROWS override;
                virtual Util::TAKErr getUri(Port::String &value) NOTHROWS override;
                virtual Util::TAKErr close() NOTHROWS override;
            private :
                FeatureDataStore2Ptr impl;
                std::shared_ptr<RuntimeFeatureDataStore2> cache;
                bool dirty;
                Thread::Mutex mutex;
            }; // FeatureDataStore
        }
    }
}

#endif
