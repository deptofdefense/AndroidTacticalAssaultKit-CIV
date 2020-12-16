
#ifndef TAK_ENGINE_FEATURE_RUNTIMEFEATUREDATASTORE2_H_INCLUDED
#define TAK_ENGINE_FEATURE_RUNTIMEFEATUREDATASTORE2_H_INCLUDED

#include <unordered_map>
#include <map>
#include <list>

#include "feature/AbstractFeatureDataStore2.h"
#include "feature/FeatureDefinition2.h"

#include "util/Quadtree.h"

namespace TAK {
    namespace Engine {
        namespace Feature {
            class RuntimeFeatureDataStore2 : public AbstractFeatureDataStore2
            {
            private :
                struct StringCaseInsensitiveWithNULL_LT
                {
                    bool operator()(const char *a, const char *b) const
                    {
                        if (a && b) {
#ifdef MSVC
                            return _stricmp(a, b) < 0;
#else
                            return strcasecmp(a, b) < 0;
#endif
                        } else if (a) {
                            return true;
                        } else if(b) {
                            return false;
                        } else {
                            return false;
                        }
                    }
                };
            public:
                RuntimeFeatureDataStore2() NOTHROWS;
                RuntimeFeatureDataStore2(int modificationFlags, int visibilityFlags) NOTHROWS;
                virtual ~RuntimeFeatureDataStore2() NOTHROWS;
            public :
                using AbstractFeatureDataStore2::insertFeature;
                using AbstractFeatureDataStore2::insertFeatureSet;

                virtual Util::TAKErr insertFeature(FeaturePtr_const *inserted, const Feature2 &feature) NOTHROWS;
                virtual Util::TAKErr insertFeatureSet(FeatureSetPtr_const *inserted, const FeatureSet2 &featureSet) NOTHROWS;
            public :
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
                virtual Util::TAKErr isFeatureVisible(bool *value, const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS override;
                virtual Util::TAKErr isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr isAvailable(bool *value) NOTHROWS override;
                virtual Util::TAKErr refresh() NOTHROWS override;
                virtual Util::TAKErr getUri(Port::String &value) NOTHROWS override;
                virtual Util::TAKErr close() NOTHROWS override;
            protected:
                virtual Util::TAKErr beginBulkModificationImpl() NOTHROWS override;
                virtual Util::TAKErr endBulkModificationImpl(const bool successful) NOTHROWS override;
                virtual Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr insertFeatureSetImpl(FeatureSetPtr_const *inserted, const FeatureSet2 &featureSet) NOTHROWS;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS override;
                virtual Util::TAKErr deleteFeatureSetImpl(const int64_t fsid) NOTHROWS override;
                virtual Util::TAKErr deleteAllFeatureSetsImpl() NOTHROWS override;
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr insertFeatureImpl(FeaturePtr_const *inserted, const Feature2 &feature) NOTHROWS;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode, const double extrude) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS override;
                virtual Util::TAKErr deleteFeatureImpl(const int64_t fid) NOTHROWS override;
                virtual Util::TAKErr deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS override;;
                virtual Util::TAKErr setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetVisibleImpl(const int64_t setId, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS override;
                virtual Util::TAKErr setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS override;
            private:
                struct FeatureSetRecord;
                struct FeatureRecord;
                
                typedef std::unordered_map<int64_t, std::shared_ptr<FeatureRecord>> FeatureIdMap;
                typedef std::unordered_map<int64_t, FeatureSetRecord> FeatureSetIdMap;
                typedef std::map<Port::String, std::list<std::shared_ptr<FeatureRecord> *>, StringCaseInsensitiveWithNULL_LT> FeatureNameMap;
                typedef std::map<Port::String, std::list<FeatureSetRecord *>, StringCaseInsensitiveWithNULL_LT> FeatureSetNameMap;
                
                static void featureSpatialIndexVisitor(std::shared_ptr<FeatureRecord> *record, void *opaque);
                
            private:
                atakmap::util::Quadtree<std::shared_ptr<FeatureRecord>> featureSpatialIndex;
                
                FeatureIdMap featureIdIndex;
                FeatureSetIdMap featureSetIdIndex;
                
                FeatureNameMap featureNameIndex;
                FeatureSetNameMap featureSetNameIndex;
                
                int64_t nextFeatureSetId;
                int64_t nextFeatureId;
                int64_t visibleGeneration;
                
                bool inBulkModify;
            };
            
            
        }
    }
}

#endif
