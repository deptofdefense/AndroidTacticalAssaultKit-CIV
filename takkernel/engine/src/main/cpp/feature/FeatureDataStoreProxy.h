#ifndef TAK_ENGINE_FEATURE_FEATUREDATASTOREPROXY_H_INCLUDED
#define TAK_ENGINE_FEATURE_FEATUREDATASTOREPROXY_H_INCLUDED

#include "feature/FeatureDataStore2.h"
#include "thread/Mutex.h"
#include "thread/Monitor.h"
#include "thread/ThreadPool.h"

#include <unordered_map>

namespace TAK {
    namespace Engine {
        namespace Feature {
            class ENGINE_API FeatureDataStoreProxy : public FeatureDataStore2
            {
            private:
                class CacheFeatureCursor;
                class CacheFeatureSetCursor;
            public:
                FeatureDataStoreProxy(FeatureDataStore2Ptr &&client, FeatureDataStore2Ptr &&cache) NOTHROWS;
                FeatureDataStoreProxy(FeatureDataStore2Ptr &&client, FeatureDataStore2Ptr &&cache, const int64_t seconds_until_stale, const int64_t max_cache_items) NOTHROWS;
                ~FeatureDataStoreProxy() NOTHROWS;
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
            private:
                class FeatureIdMap
                {
                public:
                    Util::TAKErr add(const int64_t clientFid, const int64_t cacheFid, const int64_t staleTime) NOTHROWS;
                    Util::TAKErr update(const int64_t clientFid, const int64_t oldCacheFid, const int64_t newCacheFid, const int64_t staleTime) NOTHROWS;
                    Util::TAKErr getClientFid(int64_t *clientFid, const int64_t cacheFid) const NOTHROWS;
                    Util::TAKErr getCacheFid(int64_t *cacheFid, const int64_t clientFid) const NOTHROWS;
                    Util::TAKErr getCacheStaleTime(int64_t *staleTime, const int64_t cacheFid) const NOTHROWS;
                    Util::TAKErr getClientFids(std::unordered_map<int64_t, int64_t> *clientFids) const NOTHROWS;
                    void removeClientFid(const int64_t clientFid) NOTHROWS;
                    void removeCacheFid(const int64_t cacheFid) NOTHROWS;
                    void clear() NOTHROWS;
                private:
                    mutable Thread::Mutex mutex;
                    std::unordered_map<int64_t, int64_t> clientFids;
                    std::unordered_map<int64_t, const std::pair<const int64_t, const int64_t>> cacheFids;  // pair is clientFid and staleTime
                };
                class FeatureSetIdMap
                {
                public:
                    Util::TAKErr add(const int64_t clientFsid, const int64_t cacheFsid) NOTHROWS;
                    bool hasClientFsid(const int64_t clientFsid) const NOTHROWS;
                    bool hasCacheFsid(const int64_t cacheFsid) const NOTHROWS;
                    Util::TAKErr getClientFsid(int64_t *clientFsid, const int64_t cacheFsid) const NOTHROWS;
                    Util::TAKErr getCacheFsid(int64_t *cacheFsid, const int64_t clientFsid) const NOTHROWS;
                    void removeClientFsid(const int64_t clientFsid) NOTHROWS;
                    void removeCacheFsid(const int64_t cacheFsid) NOTHROWS;
                    void clear() NOTHROWS;
                private:
                    mutable Thread::Mutex mutex;
                    std::unordered_map<int64_t, const int64_t> clientFsids;
                    std::unordered_map<int64_t, const int64_t> cacheFsids;
                };
            private:
                struct QueriedRegion
                {
                    int64_t staleTime;
                    FeatureQueryParameters queryParams;
                    QueriedRegion(const int64_t staleTime, const FeatureQueryParameters& queryParams) NOTHROWS;
                };
            private:
                enum class ClientRequestTaskType
                {
                    QUERY_FEATURES_NO_PARAMS,
                    QUERY_FEATURES,
                    QUERY_FEATURE_SETS_NO_PARAMS,
                    QUERY_FEATURE_SETS,
                    REFRESH
                };
                struct ClientRequestTask
                {
                    ClientRequestTaskType taskType;
                    std::weak_ptr<FeatureDataStore2> source;
                    std::weak_ptr<FeatureDataStore2> sink;
                    int64_t ms_until_stale;
                    int64_t max_cache_items;
                    std::shared_ptr<FeatureQueryParameters> featureQueryParameters;
                    std::shared_ptr<FeatureSetQueryParameters> featureSetQueryParameters;
                    bool operator==(const ClientRequestTask& rhs) const NOTHROWS;
                    bool operator!=(const ClientRequestTask& rhs) const NOTHROWS;
                };
                struct ClientRequestWorker
                {
                    std::shared_ptr<std::vector<ClientRequestTask>> clientRequestQueue;
                    std::shared_ptr<Thread::Monitor> monitor;
                    std::shared_ptr<bool> detached;
                    std::shared_ptr<Thread::Mutex> addFeatureMutex;
                    std::shared_ptr<Thread::Mutex> addFeatureSetMutex;
                    std::shared_ptr<FeatureIdMap> featureIdMap;
                    std::shared_ptr<FeatureSetIdMap> featureSetIdMap;
                    std::shared_ptr<std::vector<QueriedRegion>> queriedRegions;
                };
                Util::TAKErr clientRequest(const ClientRequestTaskType taskType) NOTHROWS;
                Util::TAKErr clientRequest(const FeatureQueryParameters& params) NOTHROWS;
                Util::TAKErr clientRequest(const FeatureSetQueryParameters& params) NOTHROWS;
                Util::TAKErr clientRequest(ClientRequestTask& task) NOTHROWS;
                static void* clientRequestThread(void *opaque) NOTHROWS;
                static Util::TAKErr queryHasStaleData(
                    bool *result, 
                    const ClientRequestWorker* worker, 
                    const std::shared_ptr<FeatureDataStore2>& cache, 
                    const std::shared_ptr<FeatureQueryParameters>& clientParams,
                    const int64_t max_cache_items) NOTHROWS;
                static Util::TAKErr queryHasStaleRegion(
                    bool *result,
                    const ClientRequestWorker* worker, 
                    const std::shared_ptr<FeatureQueryParameters>& clientParams, 
                    const int64_t expireTime) NOTHROWS;
                static Util::TAKErr queryHasStaleFeatureIds(
                    bool *result,
                    const ClientRequestWorker* worker, 
                    const std::shared_ptr<FeatureDataStore2>& cache, 
                    const std::shared_ptr<FeatureQueryParameters>& clientParams, 
                    const int64_t expireTime) NOTHROWS;
                static Util::TAKErr addToCache(
                    std::vector<int64_t>* clientFids,
                    const ClientRequestWorker* worker, 
                    const FeatureCursorPtr& clientCursor, 
                    const std::shared_ptr<FeatureDataStore2>& client, 
                    const std::shared_ptr<FeatureDataStore2>& cache,
                    const int64_t expireTime,
                    const int64_t newStaleTime) NOTHROWS;
                static Util::TAKErr addToCache(
                    const ClientRequestWorker* worker, 
                    const FeatureSetCursorPtr& cursor, 
                    const std::shared_ptr<FeatureDataStore2>& cache) NOTHROWS;
                static void removeDuplicateTasks(
                    const ClientRequestWorker* worker, 
                    const ClientRequestTask& task) NOTHROWS;
                static Util::TAKErr refresh(
                    const ClientRequestWorker* worker, 
                    const std::shared_ptr<FeatureDataStore2>& client, 
                    const std::shared_ptr<FeatureDataStore2>& cache, 
                    const int64_t newStaleTime) NOTHROWS;
            private:
                std::shared_ptr<FeatureDataStore2> client;
                std::shared_ptr<FeatureDataStore2> cache;
                const int64_t ms_until_stale;
                const int64_t max_cache_items;
                int64_t staleQueryFeaturesNoParams;
                std::shared_ptr<std::vector<ClientRequestTask>> clientRequestQueue;
                std::shared_ptr<Thread::Monitor> monitor;
                Thread::ThreadPoolPtr threadPool;
                std::shared_ptr<bool> detached;
                std::shared_ptr<Thread::Mutex> addFeatureMutex;
                std::shared_ptr<Thread::Mutex> addFeatureSetMutex;
                std::shared_ptr<FeatureIdMap> featureIdMap;
                std::shared_ptr<FeatureSetIdMap> featureSetIdMap;
                std::shared_ptr<std::vector<QueriedRegion>> queriedRegions;
            };
        }
    }
}

#endif
