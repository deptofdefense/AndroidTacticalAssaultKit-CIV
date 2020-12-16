#ifndef ATAKMAP_CPP_CLI_FEATURE_RUNTIME_FEATURE_DATA_STORE_H_INCLUDED
#define ATAKMAP_CPP_CLI_FEATURE_RUNTIME_FEATURE_DATA_STORE_H_INCLUDED

#include "feature/FeatureDataStore.h"
#include "feature/Geometry.h"

namespace atakmap {

        namespace math {
            template<class T> class Point;
        }

        namespace util {
            template<class T> class Quadtree;
        }

        namespace feature {

            class RuntimeFeatureDataStore : public FeatureDataStore
            {
            public:
                class FeatureRecord;
                class FeatureSetRecord;

            public :
                RuntimeFeatureDataStore();
            protected :
                RuntimeFeatureDataStore(int modificationFlags, int visibilityFlags);
            public :
                ~RuntimeFeatureDataStore();
            private :
                void init();
            public : // FeatureDataStore implementation
                virtual Feature *getFeature(int64_t fid) override;
                virtual FeatureCursor *queryFeatures(const FeatureDataStore::FeatureQueryParameters &params) const override;
                virtual size_t queryFeaturesCount(const FeatureDataStore::FeatureQueryParameters &params) const override;
                virtual FeatureSet *getFeatureSet(int64_t featureSetId) override;
                virtual FeatureSetCursor *queryFeatureSets(const FeatureDataStore::FeatureSetQueryParameters &params) const override;
                virtual size_t queryFeatureSetsCount(const FeatureDataStore::FeatureSetQueryParameters &params) const override;
                virtual bool isInBulkModification() const override;
                virtual bool isFeatureVisible(int64_t setId) const override;
                virtual bool isFeatureSetVisible(int64_t setId) const override;
                virtual bool isAvailable() const override;
                virtual void refresh() override;
                virtual const char *getURI() const override;
                virtual void dispose() override;
            protected : // AbstractFeatureDataStore implementation
                virtual void setFeatureVisibleImpl(int64_t fid, bool visible) override;
                virtual void setFeatureSetVisibleImpl(int64_t setId, bool visible) override;
                virtual void beginBulkModificationImpl() override;
                virtual void endBulkModificationImpl(bool successful) override;
                virtual void dispatchDataStoreContentChangedNoSync();
                virtual FeatureSet *insertFeatureSetImpl(const char *provider, const char *type, const char *name, double minResolution, double maxResolution, bool returnRef) override;
                virtual void updateFeatureSetImpl(int64_t fsid, const char *name) override;
                virtual void updateFeatureSetImpl(int64_t fsid, double minResolution, double maxResolution) override;
                virtual void updateFeatureSetImpl(int64_t fsid, const char *name, double minResolution, double maxResolution) override;
                virtual void updateRecordNoSync(int64_t fsid, FeatureSetRecord *record, const char *name, double minResolution, double maxResolution);
                virtual void deleteFeatureSetImpl(int64_t fsid) override;
                virtual void deleteAllFeatureSetsImpl() override;
                
                
                virtual Feature *insertFeatureImpl (int64_t featureSetID,
                                   const char* name,                // Not NULL.
                                   Geometry*,                       // Not NULL.
                                   Style*,                          // May be NULL.
                                   const util::AttributeSet&,
                                   bool returnInstance) override;
                
                virtual
                void
                updateFeatureImpl (int64_t featureID,
                                   const util::AttributeSet&) override;
                
                virtual
                void
                updateFeatureImpl (int64_t featureID,
                                   const Geometry&) override;
                
                virtual
                void
                updateFeatureImpl (int64_t featureID,
                                   const char* featureName)         // Not NULL.
                override;
                
                virtual
                void
                updateFeatureImpl (int64_t featureID,
                                   const Style&) override;
                
                virtual
                void
                updateFeatureImpl (int64_t featureID,
                                   const char* featureName,         // Not NULL.
                                   const Geometry&,
                                   const Style&,
                                   const util::AttributeSet&) override;
                
                virtual void updateRecordNoSync(int64_t fid, FeatureRecord *record, const char *name, const Geometry &geom, const Style *style, const util::AttributeSet &attributes);
                
                virtual void deleteFeatureImpl(int64_t fid) override;
                virtual void deleteFeatureImplNoSync(int64_t fid, bool removeFromSet);
                virtual void deleteAllFeaturesImpl(int64_t fsid) override;
                virtual void deleteAllFeaturesImplNoSync(int64_t fsid);
            private :
                static void FeatureRecordQuadtreeFunction(const FeatureRecord &object, atakmap::math::Point<double> &min, atakmap::math::Point<double> &max);
            private :
                mutable TAK::Engine::Thread::Mutex mutex;
                
                mutable std::string uri;
                
                atakmap::util::Quadtree<FeatureRecord> *featureSpatialIndex;
                
                std::map<int64_t, FeatureRecord> featureIdIndex;
                
                std::map<std::string, std::vector<FeatureRecord *>> featureNameIndex;
                
                std::map<Geometry::Type, std::vector<FeatureRecord *>> featureGeometryTypeIndex;
                
                std::map<int64_t, int64_t> featureIdToFeatureSetId;
                
                typedef std::map<int64_t, FeatureSetRecord> FeatureSetIDIndexMap;
                FeatureSetIDIndexMap featureSetIdIndex;
                
                std::map<std::string, std::vector<FeatureSetRecord *>> featureSetNameIndex;

                bool inBulkModify;

                int64_t nextFeatureSetId;
                int64_t nextFeatureId;
                int oid;
            };

            class RuntimeFeatureDataStore::FeatureRecord
            {
            public :
                FeatureRecord(RuntimeFeatureDataStore::FeatureSetRecord *set, int64_t fid, Feature *feature);
            public :
                ~FeatureRecord();
            public :
                PGSC::RefCountablePtr<Feature> feature;
                const int64_t fid;
                bool visible;
                unsigned long version;
                RuntimeFeatureDataStore::FeatureSetRecord *set;
            };

            class RuntimeFeatureDataStore::FeatureSetRecord {
            public:
                FeatureSetRecord(int64_t fsid, FeatureSet *featureSet);
                ~FeatureSetRecord();
            public:
                FeatureSet *featureSet;
                const int64_t fsid;
                bool visible;
                std::vector<int64_t> visibilityDeviations;
                std::vector<RuntimeFeatureDataStore::FeatureRecord *> features;
                int64_t version;
            };
    }
}

#endif // ATAKMAP_CPP_CLI_FEATURE_RUNTIME_FEATURE_DATA_STORE_H_INCLUDED
