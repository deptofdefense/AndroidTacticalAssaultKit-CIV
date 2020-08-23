#ifndef TAK_ENGINE_FEATURE_ABSTRACTFEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_ABSTRACTFEATUREDATASTORE2_H_INCLUDED

#include <set>
#include <map>

#include "feature/FeatureDataStore2.h"
#include "port/Collection.h"
#include "thread/Mutex.h"

namespace atakmap {
    namespace feature {
        class FeatureSet;
    }
}

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API AbstractFeatureDataStore2 : public virtual FeatureDataStore2
            {
            protected :
                AbstractFeatureDataStore2(int modificationFlags, int visibilityFlags);
            public :
                virtual Util::TAKErr addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS override;
                virtual Util::TAKErr removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS override;
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
                virtual Util::TAKErr setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsReadOnly(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS override;
            protected :
                virtual Util::TAKErr beginBulkModificationImpl() NOTHROWS = 0;
                virtual Util::TAKErr endBulkModificationImpl(const bool successful) NOTHROWS = 0;
                virtual Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS = 0;
                virtual Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS = 0;
                virtual Util::TAKErr deleteAllFeatureSetsImpl() NOTHROWS = 0;
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS = 0;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS = 0;
                virtual Util::TAKErr deleteFeatureImpl(const int64_t fid) NOTHROWS = 0;
                virtual Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetVisibleImpl(const int64_t setId, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS = 0;
                virtual Util::TAKErr setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS = 0;
            protected :
                TAK::Engine::Util::TAKErr checkModificationFlags(const int capability) NOTHROWS;
                void setContentChanged() NOTHROWS;
                void dispatchDataStoreContentChangedNoSync(bool force) NOTHROWS;
            public :
                static Util::TAKErr matches(bool *matched, const char *ctest, const char *value, const char wildcard) NOTHROWS;
                static Util::TAKErr matches(bool *matched, Port::Collection<Port::String> &test, const char *value, const char wildcard) NOTHROWS;
            public :
                static Util::TAKErr queryFeaturesCount(int *value, FeatureDataStore2 &dataStore, const FeatureQueryParameters &params) NOTHROWS;
            private:
                Util::TAKErr checkFeatureSetReadOnly(const int64_t fsid) NOTHROWS;
            private :
                std::set<OnDataStoreContentChangedListener *> content_changed_listeners_;
                int visibility_flags_;
                int modification_flags_;
                int in_bulk_modification_;
                bool content_changed_;
                std::map<int64_t, bool> read_only_map_;
            protected :
                TAK::Engine::Thread::Mutex mutex_;
            };
        }
    }
}

#endif
