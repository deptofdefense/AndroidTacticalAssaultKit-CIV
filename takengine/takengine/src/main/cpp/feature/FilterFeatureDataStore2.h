#ifndef TAK_ENGINE_FEATURE_FILTERFEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_FILTERFEATUREDATASTORE2_H_INCLUDED

#include "feature/FeatureDataStore2.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FilterFeatureDataStore2 : public FeatureDataStore2
            {
            public :
                FilterFeatureDataStore2(FeatureDataStore2Ptr&& impl) NOTHROWS;
                ~FilterFeatureDataStore2() NOTHROWS;
            public :
                Util::TAKErr addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS override;
                Util::TAKErr removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS override;
                Util::TAKErr getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS override;
                Util::TAKErr queryFeatures(FeatureCursorPtr &cursor) NOTHROWS override;
                Util::TAKErr queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS override;
                Util::TAKErr queryFeaturesCount(int *value) NOTHROWS override;
                Util::TAKErr queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS override;
                Util::TAKErr getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS override;
                Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS override;
                Util::TAKErr queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS override;
                Util::TAKErr queryFeatureSetsCount(int *value) NOTHROWS override;
                Util::TAKErr queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS override;
                Util::TAKErr getModificationFlags(int *value) NOTHROWS override;
                Util::TAKErr beginBulkModification() NOTHROWS override;
                Util::TAKErr endBulkModification(const bool successful) NOTHROWS override;
                Util::TAKErr isInBulkModification(bool *value) NOTHROWS override;
                Util::TAKErr insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                Util::TAKErr updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS override;
                Util::TAKErr updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS override;
                Util::TAKErr updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                Util::TAKErr deleteFeatureSet(const int64_t fsid) NOTHROWS override;
                Util::TAKErr deleteAllFeatureSets() NOTHROWS override;
                Util::TAKErr insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                Util::TAKErr updateFeature(const int64_t fid, const char *name) NOTHROWS override;
                Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS override;
                Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS override;
                Util::TAKErr updateFeature(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS override;
                Util::TAKErr updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS override;
                Util::TAKErr updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                Util::TAKErr updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                Util::TAKErr deleteFeature(const int64_t fid) NOTHROWS override;
                Util::TAKErr deleteAllFeatures(const int64_t fsid) NOTHROWS override;
                Util::TAKErr getVisibilitySettingsFlags(int *value) NOTHROWS override;
                Util::TAKErr setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS override;
                Util::TAKErr setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS override;
                Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS override;
                Util::TAKErr setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS override;
                Util::TAKErr setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS override;
                Util::TAKErr setFeatureSetsReadOnly(const FeatureSetQueryParameters &paramsRef, const bool readOnly) NOTHROWS override;
                Util::TAKErr isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS override;
                Util::TAKErr setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS override;
                Util::TAKErr isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS override;
                Util::TAKErr isFeatureReadOnly(bool *value, const int64_t fsid) NOTHROWS override;
                Util::TAKErr isAvailable(bool *value) NOTHROWS override;
                Util::TAKErr refresh() NOTHROWS override;
                Util::TAKErr getUri(Port::String &value) NOTHROWS override;
                Util::TAKErr close() NOTHROWS override;
            protected :
                FeatureDataStore2Ptr impl;
            };
        }
    }
}

#endif
