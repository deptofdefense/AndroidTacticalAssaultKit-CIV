#ifdef MSVC 
#include "feature/FeatureDataStoreProxy.h"

#include <cstddef>
#include <cstdlib>

#include "feature/FeatureCursor2.h"
#include "feature/FeatureSetCursor2.h"
#include "math/Rectangle2.h"
#include "thread/Lock.h"

#include "feature/GeometryCollection.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    constexpr char TAG[] = "FeatureDataStoreProxy";
    constexpr int maxClientRequestThreads = 3;
    constexpr bool featureIdMapDebug = false;

    bool isValidResolutionValue(const double value) NOTHROWS
    {
        return value != 0.0 && !TE_ISNAN(value);
    }

    bool isRegionWholeGlobe(const atakmap::feature::Envelope& envelope) NOTHROWS
    {
        bool isWholeGlobe = true;
        isWholeGlobe = isWholeGlobe && static_cast<int>(envelope.maxX) == 180;
        isWholeGlobe = isWholeGlobe && static_cast<int>(envelope.maxY) == 90;
        isWholeGlobe = isWholeGlobe && static_cast<int>(envelope.minX) == -180;
        isWholeGlobe = isWholeGlobe && static_cast<int>(envelope.minY) == -90;
        return isWholeGlobe;
    }
}

class FeatureDataStoreProxy::CacheFeatureCursor : public FeatureCursor2
{
public:
    CacheFeatureCursor(FeatureCursorPtr &&impl, std::shared_ptr<FeatureIdMap> &featureIdMap, std::shared_ptr<FeatureSetIdMap> &featureSetIdMap) NOTHROWS;
    ~CacheFeatureCursor() NOTHROWS override;
public: // FeatureCursor2
    TAKErr getId(int64_t *value) NOTHROWS override;
    TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
    TAKErr getVersion(int64_t *value) NOTHROWS override;
public: // FeatureDefinition2
    TAKErr getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS override;
    FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS override;
    AltitudeMode getAltitudeMode() NOTHROWS override;
    double getExtrude() NOTHROWS override;
    TAKErr getName(const char **value) NOTHROWS override;
    FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS override;
    TAKErr getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS override;
    TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
    TAKErr get(const Feature2 **feature) NOTHROWS override;
public: // RowIterator
    TAKErr moveToNext() NOTHROWS override;
private:
    FeatureCursorPtr impl;
    std::shared_ptr<FeatureIdMap> featureIdMap;
    std::shared_ptr<FeatureSetIdMap> featureSetIdMap;
};

class FeatureDataStoreProxy::CacheFeatureSetCursor : public FeatureSetCursor2
{
public:
    CacheFeatureSetCursor(FeatureSetCursorPtr &&impl, std::shared_ptr<FeatureSetIdMap> &featureSetIdMap) NOTHROWS;
    ~CacheFeatureSetCursor() NOTHROWS override;
public: // FeatureSetCursor2
    TAKErr get(const FeatureSet2 **featureSet) NOTHROWS override;
    TAKErr moveToNext() NOTHROWS override;
private:
    FeatureSetCursorPtr impl;
    std::shared_ptr<FeatureSetIdMap> featureSetIdMap;
};

FeatureDataStoreProxy::FeatureDataStoreProxy(FeatureDataStore2Ptr &&client_, FeatureDataStore2Ptr &&cache_) NOTHROWS :
    FeatureDataStoreProxy(std::move(client_), std::move(cache_), 300, 8192)
{
}

FeatureDataStoreProxy::FeatureDataStoreProxy(FeatureDataStore2Ptr &&client_, FeatureDataStore2Ptr &&cache_, const int64_t seconds_until_stale, const int64_t max_cache_items_) NOTHROWS :
    client(std::move(client_)),
    cache(std::move(cache_)),
    ms_until_stale(seconds_until_stale * 1000),
    max_cache_items(max_cache_items_),
    staleQueryFeaturesNoParams(0),
    clientRequestQueue(std::make_shared<std::vector<ClientRequestTask>>()),
    monitor(std::make_shared<Monitor>()),
    threadPool(nullptr, nullptr),
    detached(std::make_shared<bool>(false)),
    addFeatureMutex(std::make_shared<Mutex>()),
    addFeatureSetMutex(std::make_shared<Mutex>()),
    featureIdMap(std::make_shared<FeatureIdMap>()),
    featureSetIdMap(std::make_shared<FeatureSetIdMap>()),
    queriedRegions(std::make_shared<std::vector<QueriedRegion>>())
{
}

FeatureDataStoreProxy::~FeatureDataStoreProxy() NOTHROWS
{
    Monitor::Lock lock(*monitor);
    // Detach all threads in the pool. This allows for immediate destruct,
    // client and cache will only be kept open if 1) referenced externally or
    // 2) ongoing active download/cache operation
    if (threadPool) {
        threadPool->detachAll();
        threadPool.reset();
    }

    // mark as detached
    *detached = true;

    // clear the queue
    clientRequestQueue->clear();

    // notify
    lock.broadcast();
}

TAKErr FeatureDataStoreProxy::addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    return this->cache->addOnDataStoreContentChangedListener(l);
}
TAKErr FeatureDataStoreProxy::removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    return this->cache->removeOnDataStoreContentChangedListener(l);
}
TAKErr FeatureDataStoreProxy::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS
{
    // Translate client FeatureId to cache FeatureId.
    int64_t cacheFid;
    TAKErr code = featureIdMap->getCacheFid(&cacheFid, fid);
    TE_CHECKRETURN_CODE(code);

    FeaturePtr_const cacheFeature(nullptr, nullptr);
    code = this->cache->getFeature(cacheFeature, cacheFid);
    TE_CHECKRETURN_CODE(code);

    feature = FeaturePtr_const(::new (std::nothrow) Feature2(
        fid, cacheFeature->getFeatureSetId(), cacheFeature->getName(), 
        *cacheFeature->getGeometry(), cacheFeature->getAltitudeMode(), cacheFeature->getExtrude(), 
        *cacheFeature->getStyle(), *cacheFeature->getAttributes(), cacheFeature->getVersion()),
        Memory_deleter_const<Feature2>);

    if (feature == nullptr)
        return TE_OutOfMemory;

    return TE_Ok;
}
TAKErr FeatureDataStoreProxy::queryFeatures(FeatureCursorPtr &cursor) NOTHROWS
{
    const int64_t sysTime = Port::Platform_systime_millis();
    if (sysTime > staleQueryFeaturesNoParams)
    {
        staleQueryFeaturesNoParams = sysTime + ms_until_stale;
        clientRequest(ClientRequestTaskType::QUERY_FEATURES_NO_PARAMS);
    }

    FeatureCursorPtr retval(nullptr, nullptr);
    TAKErr code = this->cache->queryFeatures(retval);
    TE_CHECKRETURN_CODE(code);

    cursor = FeatureCursorPtr(new CacheFeatureCursor(std::move(retval), featureIdMap, featureSetIdMap), Memory_deleter_const<FeatureCursor2, CacheFeatureCursor>);
    return code;
}
TAKErr FeatureDataStoreProxy::queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS
{
    clientRequest(params);

    TAKErr code(TE_Ok);
    FeatureCursorPtr retval(nullptr, nullptr);
    if (params.featureIds != nullptr && !params.featureIds->empty())
    {
        // Translate client FeatureIds to cache FeatureIds.
        FeatureQueryParameters cacheParams(params);
        cacheParams.featureIds->clear();
        Port::Collection<int64_t>::IteratorPtr iterClientFid(nullptr, nullptr);
        code = params.featureIds->iterator(iterClientFid);
        TE_CHECKRETURN_CODE(code);
        while (true)
        {
            int64_t clientFid;
            code = iterClientFid->get(clientFid);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            int64_t cacheFid;
            code = featureIdMap->getCacheFid(&cacheFid, clientFid);
            if (code == TE_Ok) {
                code = cacheParams.featureIds->add(cacheFid);
                TE_CHECKBREAK_CODE(code);
            }
            else
                Logger_log(TELL_Warning, "%s: Did not find clientFid 0x%llx in featureIdMap.", TAG, clientFid);

            code = iterClientFid->next();
            TE_CHECKBREAK_CODE(code);
        }
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        code = this->cache->queryFeatures(retval, cacheParams);
        TE_CHECKRETURN_CODE(code);
    }
    else
    {
        code = this->cache->queryFeatures(retval, params);
        TE_CHECKRETURN_CODE(code);
    }

    cursor = FeatureCursorPtr(new CacheFeatureCursor(std::move(retval), featureIdMap, featureSetIdMap), Memory_deleter_const<FeatureCursor2, CacheFeatureCursor>);
    return code;
}
TAKErr FeatureDataStoreProxy::queryFeaturesCount(int *value) NOTHROWS
{
    return this->cache->queryFeaturesCount(value);
}
TAKErr FeatureDataStoreProxy::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    if (value == nullptr)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    FeatureCursorPtr retval(nullptr, nullptr);
    if (params.featureIds != nullptr && !params.featureIds->empty())
    {
        // Translate client FeatureIds to cache FeatureIds.
        FeatureQueryParameters cacheParams(params);
        cacheParams.featureIds->clear();
        Port::Collection<int64_t>::IteratorPtr iterClientFid(nullptr, nullptr);
        code = params.featureIds->iterator(iterClientFid);
        TE_CHECKRETURN_CODE(code);
        while (true)
        {
            int64_t clientFid;
            code = iterClientFid->get(clientFid);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            int64_t cacheFid;
            code = featureIdMap->getCacheFid(&cacheFid, clientFid);
            if (code == TE_Ok) {
                code = cacheParams.featureIds->add(cacheFid);
            }
            else
                Logger_log(TELL_Warning, "%s: Did not find clientFid 0x%llx in featureIdMap.", TAG, clientFid);
            TE_CHECKBREAK_CODE(code);

            code = iterClientFid->next();
            TE_CHECKBREAK_CODE(code);
        }
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        return this->cache->queryFeaturesCount(value, cacheParams);
    }

    return this->cache->queryFeaturesCount(value, params);
}
TAKErr FeatureDataStoreProxy::getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS
{
    // Translate client FeatureSetId to cache FeatureSetId.
    int64_t cacheFsid;
    TAKErr code = featureSetIdMap->getCacheFsid(&cacheFsid, featureSetId);
    TE_CHECKRETURN_CODE(code);

    FeatureSetPtr_const cacheFeatureSet(nullptr, nullptr);
    code = this->cache->getFeatureSet(cacheFeatureSet, cacheFsid);
    TE_CHECKRETURN_CODE(code);

    featureSet = FeatureSetPtr_const(::new (std::nothrow) FeatureSet2(
        featureSetId, cacheFeatureSet->getProvider(), cacheFeatureSet->getType(), 
        cacheFeatureSet->getName(), cacheFeatureSet->getMinResolution(), cacheFeatureSet->getMaxResolution(), 
        cacheFeatureSet->getVersion()),
        Memory_deleter_const<FeatureSet2>);

    if (featureSet == nullptr)
        return TE_OutOfMemory;

    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS
{
    clientRequest(ClientRequestTaskType::QUERY_FEATURE_SETS_NO_PARAMS);

    FeatureSetCursorPtr retval(nullptr, nullptr);
    TAKErr code = this->cache->queryFeatureSets(retval);
    TE_CHECKRETURN_CODE(code);

    cursor = FeatureSetCursorPtr(new CacheFeatureSetCursor(std::move(retval), featureSetIdMap), Memory_deleter_const<FeatureSetCursor2, CacheFeatureSetCursor>);
    return code;
}
TAKErr FeatureDataStoreProxy::queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS
{
    clientRequest(params);

    TAKErr code(TE_Ok);
    FeatureSetCursorPtr retval(nullptr, nullptr);
    if (params.ids != nullptr && !params.ids->empty())
    {
        FeatureSetQueryParameters cacheParams(params);
        cacheParams.ids->clear();
        Port::Collection<int64_t>::IteratorPtr iterClientFsid(nullptr, nullptr);
        code = params.ids->iterator(iterClientFsid);
        TE_CHECKRETURN_CODE(code);
        while (true)
        {
            int64_t clientFsid;
            code = iterClientFsid->get(clientFsid);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            int64_t cacheFsid;
            code = featureSetIdMap->getCacheFsid(&cacheFsid, clientFsid);
            if (code == TE_Ok) {
                code = cacheParams.ids->add(cacheFsid);
                TE_CHECKBREAK_CODE(code);
            }
            else
                Logger_log(TELL_Warning, "%s: Did not find clientFsid 0x%llx in featureSetIdMap.", TAG, clientFsid);

            code = iterClientFsid->next();
            TE_CHECKBREAK_CODE(code);
        }
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        code = this->cache->queryFeatureSets(retval, cacheParams);
        TE_CHECKRETURN_CODE(code);
    }
    else
    {
        code = this->cache->queryFeatureSets(retval, params);
        TE_CHECKRETURN_CODE(code);
    }

    cursor = FeatureSetCursorPtr(new CacheFeatureSetCursor(std::move(retval), featureSetIdMap), Memory_deleter_const<FeatureSetCursor2, CacheFeatureSetCursor>);
    return code;
}
TAKErr FeatureDataStoreProxy::queryFeatureSetsCount(int *value) NOTHROWS
{
    return this->cache->queryFeatureSetsCount(value);
}
TAKErr FeatureDataStoreProxy::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    if (value == nullptr)
        return TE_InvalidArg;

    if (params.ids != nullptr && !params.ids->empty())
    {
        TAKErr code(TE_Ok);
        FeatureSetQueryParameters cacheParams(params);
        cacheParams.ids->clear();
        Port::Collection<int64_t>::IteratorPtr iterClientFsid(nullptr, nullptr);
        code = params.ids->iterator(iterClientFsid);
        TE_CHECKRETURN_CODE(code);
        while (true)
        {
            int64_t clientFsid;
            code = iterClientFsid->get(clientFsid);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            int64_t cacheFsid;
            code = featureSetIdMap->getCacheFsid(&cacheFsid, clientFsid);
            if (code == TE_Ok) {
                code = cacheParams.ids->add(cacheFsid);
            }
            else
                Logger_log(TELL_Warning, "%s: Did not find clientFid 0x%llx in featureIdMap.", TAG, clientFsid);
            TE_CHECKBREAK_CODE(code);

            code = iterClientFsid->next();
            TE_CHECKBREAK_CODE(code);
        }
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        return this->cache->queryFeatureSetsCount(value, cacheParams);
    }

    return this->cache->queryFeatureSetsCount(value, params);
}
TAKErr FeatureDataStoreProxy::getModificationFlags(int *value) NOTHROWS
{
    return this->client->getModificationFlags(value);
}
TAKErr FeatureDataStoreProxy::beginBulkModification() NOTHROWS
{
    return this->cache->beginBulkModification();
}
TAKErr FeatureDataStoreProxy::endBulkModification(const bool successful) NOTHROWS
{
    return this->cache->endBulkModification(successful);
}
TAKErr FeatureDataStoreProxy::isInBulkModification(bool *value) NOTHROWS
{
    return this->cache->isInBulkModification(value);
}
TAKErr FeatureDataStoreProxy::insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::deleteFeatureSet(const int64_t fsid) NOTHROWS
{
    int64_t cacheFsid;
    TAKErr code = featureSetIdMap->getCacheFsid(&cacheFsid, fsid);
    TE_CHECKRETURN_CODE(code);
    code = this->cache->deleteFeatureSet(cacheFsid);
    TE_CHECKRETURN_CODE(code);
    featureSetIdMap->removeCacheFsid(cacheFsid);
    return code;
}
TAKErr FeatureDataStoreProxy::deleteAllFeatureSets() NOTHROWS
{
    TAKErr code = this->cache->deleteAllFeatureSets();
    TE_CHECKRETURN_CODE(code);
    featureIdMap->clear();
    featureSetIdMap->clear();
    return code;
}
TAKErr FeatureDataStoreProxy::insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name,
                                                     const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode,
                                                     const double extrude, const atakmap::feature::Style *style,
                                                     const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeature(const int64_t fid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeature(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr FeatureDataStoreProxy::deleteFeature(const int64_t fid) NOTHROWS
{
    int64_t cacheFid;
    TAKErr code = featureIdMap->getCacheFid(&cacheFid, fid);
    TE_CHECKRETURN_CODE(code);
    code = this->cache->deleteFeature(cacheFid);
    TE_CHECKRETURN_CODE(code);
    featureIdMap->removeCacheFid(cacheFid);
    return code;
}
TAKErr FeatureDataStoreProxy::deleteAllFeatures(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    int64_t cacheFsid;
    code = featureSetIdMap->getCacheFsid(&cacheFsid, fsid);
    TE_CHECKRETURN_CODE(code);

    FeatureQueryParameters params;
    params.featureSetIds->add(cacheFsid);
    FeatureCursorPtr cursor(nullptr, nullptr);
    std::vector<int64_t> featureIds;

    code = queryFeatures(cursor, params);
    while (true)
    {
        code = cursor->moveToNext();
        TE_CHECKBREAK_CODE(code);

        int64_t fid;
        code = cursor->getId(&fid);
        TE_CHECKBREAK_CODE(code);

        featureIds.push_back(fid);
    }
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    code = this->cache->deleteAllFeatures(cacheFsid);
    TE_CHECKRETURN_CODE(code);

    for (const auto fid : featureIds)
        featureIdMap->removeCacheFid(fid);

    return code;
}
TAKErr FeatureDataStoreProxy::getVisibilitySettingsFlags(int *value) NOTHROWS
{
    return this->client->getVisibilitySettingsFlags(value);
}
TAKErr FeatureDataStoreProxy::setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS
{
    int64_t cacheFid;
    TAKErr code = featureIdMap->getCacheFid(&cacheFid, fid);
    TE_CHECKRETURN_CODE(code);
    return this->cache->setFeatureVisible(cacheFid, visible);
}
TAKErr FeatureDataStoreProxy::setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    if (params.featureIds != nullptr && !params.featureIds->empty())
    {
        TAKErr code(TE_Ok);
        FeatureQueryParameters cacheParams(params);
        cacheParams.featureIds->clear();
        Port::Collection<int64_t>::IteratorPtr iterClientFid(nullptr, nullptr);
        code = params.featureIds->iterator(iterClientFid);
        TE_CHECKRETURN_CODE(code);
        while (true)
        {
            int64_t clientFid;
            code = iterClientFid->get(clientFid);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            int64_t cacheFid;
            code = featureIdMap->getCacheFid(&cacheFid, clientFid);
            if (code == TE_Ok) {
                code = cacheParams.featureIds->add(cacheFid);
            }
            else
                Logger_log(TELL_Warning, "%s: Did not find clientFid 0x%llx in featureIdMap.", TAG, clientFid);
            TE_CHECKBREAK_CODE(code);

            code = iterClientFid->next();
            TE_CHECKBREAK_CODE(code);
        }
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        return this->cache->setFeaturesVisible(cacheParams, visible);
    }
    return this->cache->setFeaturesVisible(params, visible);
}
TAKErr FeatureDataStoreProxy::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    int64_t cacheFid;
    TAKErr code = featureIdMap->getCacheFid(&cacheFid, fid);
    TE_CHECKRETURN_CODE(code);
    return this->cache->isFeatureVisible(value, cacheFid);
}
TAKErr FeatureDataStoreProxy::setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS
{
    int64_t cacheFsid;
    TAKErr code = featureSetIdMap->getCacheFsid(&cacheFsid, setId);
    TE_CHECKRETURN_CODE(code);
    return this->cache->setFeatureSetVisible(cacheFsid, visible);
}
TAKErr FeatureDataStoreProxy::setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    if (params.ids != nullptr && !params.ids->empty())
    {
        TAKErr code(TE_Ok);
        FeatureSetQueryParameters cacheParams(params);
        cacheParams.ids->clear();
        Port::Collection<int64_t>::IteratorPtr iterClientFsid(nullptr, nullptr);
        code = params.ids->iterator(iterClientFsid);
        TE_CHECKRETURN_CODE(code);
        while (true)
        {
            int64_t clientFsid;
            code = iterClientFsid->get(clientFsid);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            int64_t cacheFsid;
            code = featureSetIdMap->getCacheFsid(&cacheFsid, clientFsid);
            if (code == TE_Ok) {
                code = cacheParams.ids->add(cacheFsid);
            }
            else
                Logger_log(TELL_Warning, "%s: Did not find clientFid 0x%llx in featureIdMap.", TAG, clientFsid);
            TE_CHECKBREAK_CODE(code);

            code = iterClientFsid->next();
            TE_CHECKBREAK_CODE(code);
        }

        return this->cache->setFeatureSetsVisible(cacheParams, visible);
    }
    return this->cache->setFeatureSetsVisible(params, visible);
}
TAKErr FeatureDataStoreProxy::isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS
{
    int64_t cacheFsid;
    TAKErr code = featureSetIdMap->getCacheFsid(&cacheFsid, setId);
    TE_CHECKRETURN_CODE(code);
    return this->cache->isFeatureSetVisible(value, cacheFsid);
}
TAKErr FeatureDataStoreProxy::setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS
{
    int64_t cacheFsid;
    TAKErr code = featureSetIdMap->getCacheFsid(&cacheFsid, fsid);
    TE_CHECKRETURN_CODE(code);
    return this->cache->setFeatureSetReadOnly(cacheFsid, readOnly);
}
TAKErr FeatureDataStoreProxy::setFeatureSetsReadOnly(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS
{
    if (params.ids != nullptr && !params.ids->empty())
    {
        TAKErr code(TE_Ok);
        FeatureSetQueryParameters cacheParams(params);
        cacheParams.ids->clear();
        Port::Collection<int64_t>::IteratorPtr iterClientFsid(nullptr, nullptr);
        code = params.ids->iterator(iterClientFsid);
        TE_CHECKRETURN_CODE(code);
        while (true)
        {
            int64_t clientFsid;
            code = iterClientFsid->get(clientFsid);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            int64_t cacheFsid;
            code = featureSetIdMap->getCacheFsid(&cacheFsid, clientFsid);
            if (code == TE_Ok) {
                code = cacheParams.ids->add(cacheFsid);
            }
            else
                Logger_log(TELL_Warning, "%s: Did not find clientFid 0x%llx in featureIdMap.", TAG, clientFsid);
            TE_CHECKBREAK_CODE(code);

            code = iterClientFsid->next();
            TE_CHECKBREAK_CODE(code);
        }
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        return this->cache->setFeatureSetsReadOnly(cacheParams, readOnly);
    }
    return this->cache->setFeatureSetsReadOnly(params, readOnly);
}
TAKErr FeatureDataStoreProxy::isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS 
{
    int64_t cacheFsid;
    TAKErr code = featureSetIdMap->getCacheFsid(&cacheFsid, fsid);
    TE_CHECKRETURN_CODE(code);
    return this->cache->isFeatureSetReadOnly(value, cacheFsid);
}
TAKErr FeatureDataStoreProxy::isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS 
{
    int64_t cacheFid;
    TAKErr code = featureIdMap->getCacheFid(&cacheFid, fid);
    TE_CHECKRETURN_CODE(code);
    return this->cache->isFeatureReadOnly(value, cacheFid);
}
TAKErr FeatureDataStoreProxy::isAvailable(bool *value) NOTHROWS
{
    return this->client->isAvailable(value);
}
TAKErr FeatureDataStoreProxy::refresh() NOTHROWS
{
    return clientRequest(ClientRequestTaskType::REFRESH);
}
TAKErr FeatureDataStoreProxy::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    return this->client->getUri(value);
}
TAKErr FeatureDataStoreProxy::close() NOTHROWS
{
    TAKErr code(TE_Ok);
    code = this->client->close();
    TE_CHECKRETURN_CODE(code);
    this->cache->close();  // ignore return closing cache FDS.
    return code;
}

TAKErr FeatureDataStoreProxy::clientRequest(const ClientRequestTaskType taskType) NOTHROWS
{
    ClientRequestTask task;
    task.taskType = taskType;
    return clientRequest(task);
}

TAKErr FeatureDataStoreProxy::clientRequest(const FeatureQueryParameters& params) NOTHROWS
{
    ClientRequestTask task;
    task.taskType = ClientRequestTaskType::QUERY_FEATURES;
    task.featureQueryParameters.reset(new FeatureQueryParameters(params));
    return clientRequest(task);
}

TAKErr FeatureDataStoreProxy::clientRequest(const FeatureSetQueryParameters& params) NOTHROWS
{
    ClientRequestTask task;
    task.taskType = ClientRequestTaskType::QUERY_FEATURE_SETS;
    task.featureSetQueryParameters.reset(new FeatureSetQueryParameters(params));
    return clientRequest(task);
}

TAKErr FeatureDataStoreProxy::clientRequest(ClientRequestTask& task) NOTHROWS
{
    Monitor::Lock lock(*monitor);
    TE_CHECKRETURN_CODE(lock.status);
    if(!threadPool) {
        int featureSetsCount;
        TAKErr code = this->client->queryFeatureSetsCount(&featureSetsCount);
        TE_CHECKRETURN_CODE(code);
        if (featureSetsCount == 0)
            return TE_Err;
        const std::size_t numClientRequestThreads = 
            featureSetsCount > maxClientRequestThreads ? maxClientRequestThreads : featureSetsCount;

        std::vector<void *> workerData;
        workerData.reserve(numClientRequestThreads);
        for(std::size_t i = 0u; i < numClientRequestThreads; i++) {
            std::unique_ptr<ClientRequestWorker> worker(new ClientRequestWorker());
            worker->clientRequestQueue = clientRequestQueue;
            worker->monitor = monitor;
            worker->detached = detached;
            worker->addFeatureMutex = addFeatureMutex;
            worker->addFeatureSetMutex = addFeatureSetMutex;
            worker->featureIdMap = featureIdMap;
            worker->featureSetIdMap = featureSetIdMap;
            worker->queriedRegions = queriedRegions;
            workerData.push_back(worker.release());
        }
        if (ThreadPool_create(threadPool, numClientRequestThreads, clientRequestThread, &workerData.at(0)) != TE_Ok)
            return TE_Err;
    }
    task.source = this->client;
    task.sink = this->cache;
    task.ms_until_stale = ms_until_stale;
    task.max_cache_items = max_cache_items;

    clientRequestQueue->push_back(task);
    lock.signal();

    return TE_Ok;
}

void* FeatureDataStoreProxy::clientRequestThread(void* opaque) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::unique_ptr<ClientRequestWorker> worker(static_cast<ClientRequestWorker*>(opaque));
    while(true)
    {
        ClientRequestTask task;
        {
            Monitor::Lock lock(*worker->monitor);
            if (*worker->detached)
                break;
            if(worker->clientRequestQueue->empty()) {
                lock.wait();
                continue;
            }

            task = worker->clientRequestQueue->back();
            worker->clientRequestQueue->pop_back();

            removeDuplicateTasks(worker.get(), task);
        }

        const std::shared_ptr<FeatureDataStore2> client = task.source.lock();
        if (!client) {
            Logger_log(TELL_Warning, "%s: Could not get client from source.", TAG);
            continue;
        } else {
            bool available;
            code = client->isAvailable(&available);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);
            if (!available) {
                Port::String uri;
                code = client->getUri(uri);
                Logger_log(TELL_Info, "%s: Client at \"%s\" is not available.", TAG, uri.get());
            }
        }

        const std::shared_ptr<FeatureDataStore2> cache = task.sink.lock();
        if (!cache) {
            Logger_log(TELL_Warning, "%s: Could not get cache from sink.", TAG);
            continue;
        }

        const int64_t curTime = Port::Platform_systime_millis();
        const int64_t staleTime = curTime + task.ms_until_stale;

        // execute task
        switch (task.taskType) {
        case ClientRequestTaskType::QUERY_FEATURES_NO_PARAMS: {
            FeatureCursorPtr clientCursor(nullptr, nullptr);
            code = client->queryFeatures(clientCursor);
            TE_CHECKBREAK_CODE(code);
            code = addToCache(nullptr, worker.get(), clientCursor, client, cache, curTime, staleTime);
            TE_CHECKBREAK_CODE(code);
            break;
        }
        case ClientRequestTaskType::QUERY_FEATURES: {
            const FeatureQueryParameters* params = task.featureQueryParameters.get();
            bool doQuery;
            code = queryHasStaleData(&doQuery, worker.get(), cache, task.featureQueryParameters, task.max_cache_items);
            TE_CHECKBREAK_CODE(code);
            if (doQuery) {
                FeatureCursorPtr clientCursor(nullptr, nullptr);
                code = client->queryFeatures(clientCursor, *params);
                TE_CHECKBREAK_CODE(code);
                code = addToCache(nullptr, worker.get(), clientCursor, client, cache, curTime, staleTime);
                TE_CHECKBREAK_CODE(code);

                if (params->spatialFilter != nullptr && !isRegionWholeGlobe(params->spatialFilter->getEnvelope())) {
                    worker->queriedRegions->emplace_back(staleTime, *params);
                    //const atakmap::feature::Envelope& envelope = params->spatialFilter->getEnvelope();
                    //std::stringstream msg;
                    //msg << std::fixed;
                    //msg.precision(15);
                    //msg << "Region: " << envelope.maxY << ", " << envelope.minX << ", " << envelope.minY << ", " << envelope.maxX;
                    //Logger_log(TELL_Info, "%s: Adding to queriedRegions: %s", TAG, msg.str().c_str());
                }
            }
            break;
        }
        case ClientRequestTaskType::QUERY_FEATURE_SETS_NO_PARAMS: {
            FeatureSetCursorPtr clientCursor(nullptr, nullptr);
            code = client->queryFeatureSets(clientCursor);
            TE_CHECKBREAK_CODE(code);
            code = addToCache(worker.get(), clientCursor, cache);  // featureSets do not go stale
            TE_CHECKBREAK_CODE(code);
            break;
        }
        case ClientRequestTaskType::QUERY_FEATURE_SETS: {
            FeatureSetCursorPtr clientCursor(nullptr, nullptr);
            code = client->queryFeatureSets(clientCursor, *task.featureSetQueryParameters);
            TE_CHECKBREAK_CODE(code);
            code = addToCache(worker.get(), clientCursor, cache);  // featureSets do not go stale
            TE_CHECKBREAK_CODE(code);
            break;
        }
        case ClientRequestTaskType::REFRESH: {
            code = refresh(worker.get(), client, cache, staleTime);
            TE_CHECKBREAK_CODE(code);
            break;
        }
        default:
            break;
        }
    }

    return nullptr;
}

TAKErr FeatureDataStoreProxy::queryHasStaleData(
    bool *result,
    const ClientRequestWorker* worker,
    const std::shared_ptr<FeatureDataStore2>& cache, 
    const std::shared_ptr<FeatureQueryParameters>& clientParams,
    const int64_t max_cache_items) NOTHROWS
{
    if (result == nullptr)
        return TE_InvalidArg;

    TAKErr code;
    const int64_t sysTime = Port::Platform_systime_millis();

    if (clientParams->spatialFilter != nullptr)
    {
        int cacheSize;
        code = cache->queryFeaturesCount(&cacheSize);
        TE_CHECKRETURN_CODE(code);
        const bool cacheFull = cacheSize >= max_cache_items;

        code = queryHasStaleRegion(result, worker, clientParams, sysTime);
        TE_CHECKRETURN_CODE(code);

        if (cacheFull && !*result)
            Logger_log(TELL_Debug, "%s: Query region is not stale and cache is at max capacity.", TAG);

        return code;
    }

    if (clientParams->featureIds != nullptr && !clientParams->featureIds->empty())
    {
        code = queryHasStaleFeatureIds(result, worker, cache, clientParams, sysTime);
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    // If no spatialFilter and no featureIds then set true so we'll do the query.
    *result = true;
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::queryHasStaleRegion(
    bool *result, 
    const ClientRequestWorker* worker, 
    const std::shared_ptr<FeatureQueryParameters>& clientParams,
    const int64_t expireTime) NOTHROWS
{
    if (result == nullptr)
        return TE_InvalidArg;

    *result = true;
    if (!worker->queriedRegions->empty())
    {
        const auto& paramsEnv = clientParams->spatialFilter->getEnvelope();
        for (auto itQueriedRegions = worker->queriedRegions->begin(); itQueriedRegions != worker->queriedRegions->end();)
        {
            if (expireTime >= itQueriedRegions->staleTime) {
                itQueriedRegions = worker->queriedRegions->erase(itQueriedRegions);
                continue;
            }
            bool queriedRegionContainsEnv;
            const auto& qrEnv = itQueriedRegions->queryParams.spatialFilter->getEnvelope();
            TAKErr code = Math::Rectangle2_contains<double>(queriedRegionContainsEnv, 
                qrEnv.minX, qrEnv.minY, qrEnv.maxX, qrEnv.maxY, 
                paramsEnv.minX, paramsEnv.minY, paramsEnv.maxX, paramsEnv.maxY);
            TE_CHECKRETURN_CODE(code);
            if (queriedRegionContainsEnv) {
                *result = false;
                break;
            }
            ++itQueriedRegions;
        }
    }
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::queryHasStaleFeatureIds(
    bool *result, 
    const ClientRequestWorker* worker,
    const std::shared_ptr<FeatureDataStore2>& cache, 
    const std::shared_ptr<FeatureQueryParameters>& clientParams,
    const int64_t expireTime) NOTHROWS
{
    if (result == nullptr)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    FeatureQueryParameters cacheParams;
    Port::Collection<int64_t>::IteratorPtr iterClientFid(nullptr, nullptr);
    code = clientParams->featureIds->iterator(iterClientFid);
    TE_CHECKRETURN_CODE(code);
    while (true)
    {
        int64_t clientFid;
        code = iterClientFid->get(clientFid);
        TE_CHECKBREAK_CODE(code);

        int64_t cacheFid;
        code = worker->featureIdMap->getCacheFid(&cacheFid, clientFid);
        if (code == TE_Done)
            *result = true;
        TE_CHECKBREAK_CODE(code);

        // Query the cache and see if the feature is still there.
        cacheParams.featureIds->clear();
        cacheParams.featureIds->add(cacheFid);

        int count;
        code = cache->queryFeaturesCount(&count, cacheParams);
        TE_CHECKBREAK_CODE(code);

        if (count == 0)
        {
            *result = true;
            break;
        }

        if (count == 1) {
            // check stale time
            int64_t staleTime;
            code = worker->featureIdMap->getCacheStaleTime(&staleTime, clientFid);
            if (code == TE_Ok && expireTime >= staleTime) {
                *result = true;
                break;
            }
        }

        code = iterClientFid->next();
        TE_CHECKBREAK_CODE(code);
    }
    if (code == TE_Done)
        code = TE_Ok;

    return code;
}

TAKErr FeatureDataStoreProxy::addToCache(
    std::vector<int64_t>* clientFids,
    const ClientRequestWorker* worker, 
    const FeatureCursorPtr& clientCursor, 
    const std::shared_ptr<FeatureDataStore2>& client, 
    const std::shared_ptr<FeatureDataStore2>& cache,
    const int64_t expireTime,
    const int64_t newStaleTime) NOTHROWS
{
    bool available = true;
    bool bulkMod = false;
    TAKErr code(TE_Ok);

    do {
        if (*worker->detached)
            break;

        code = client->isAvailable(&available);
        TE_CHECKBREAK_CODE(code);
        if (!available)
            break;

        code = clientCursor->moveToNext();
        TE_CHECKBREAK_CODE(code);

        const Feature2 *clientFeature;
        code = clientCursor->get(&clientFeature);
        TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

        const int64_t clientFid = clientFeature->getId();
        if (clientFids != nullptr)
            clientFids->push_back(clientFid);

        const Lock lock(*worker->addFeatureMutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        // Check for existing Feature.
        int64_t cacheFid;
        bool updateFeatureIdMap = false;
        code = worker->featureIdMap->getCacheFid(&cacheFid, clientFid);
        if (code == TE_Ok)
        {
            // Check to see if it's been removed by the cache grooming.
            FeatureQueryParameters params;
            params.featureIds->add(cacheFid);

            int count;
            code = cache->queryFeaturesCount(&count, params);
            TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

            if (count == 0)
            {
                updateFeatureIdMap = true;
                //Logger_log(TELL_Info, "%s: Feature not found in cache, must have been groomed.", TAG);
            }
            else
            {
                int64_t cachedStaleTime;
                code = worker->featureIdMap->getCacheStaleTime(&cachedStaleTime, cacheFid);
                TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);
                if (expireTime >= cachedStaleTime)
                    updateFeatureIdMap = true;
                else
                    continue;  // Still in the cache and not stale.
            }
        }
        // else: feature is not in the cache.

        if (!bulkMod) {
            bulkMod = true;
            cache->beginBulkModification();
        }

        int64_t cacheFsid;
        code = worker->featureSetIdMap->getCacheFsid(&cacheFsid, clientFeature->getFeatureSetId());
        if (code == TE_Done)
        {
            FeatureSetQueryParameters params;
            params.ids->add(clientFeature->getFeatureSetId());

            FeatureSetCursorPtr featureSetCursor(nullptr, nullptr);
            code = client->queryFeatureSets(featureSetCursor, params);
            TE_CHECKBREAK_CODE(code);

            code = addToCache(worker, featureSetCursor, cache);
            TE_CHECKBREAK_CODE(code);

            code = worker->featureSetIdMap->getCacheFsid(&cacheFsid, clientFeature->getFeatureSetId());
            if (code == TE_Done)
                return TE_IllegalState;
        }

        const atakmap::feature::Geometry* featureGeometry = clientFeature->getGeometry();
        if (featureGeometry == nullptr) {
            Logger_log(TELL_Error, "%s: getGeometry returned nullptr.", TAG);
            continue;
        }

        // BL -- Added this check because I was seeing an issue that
        // this mitigated but I intended to come back and try to fix it
        // properly.  Now I cannot reproduce the issue.  Leaving the
        // check and warning for now.
        if (featureGeometry->getType() == atakmap::feature::Geometry::COLLECTION)
        {
            const auto geometryCollection = dynamic_cast<const atakmap::feature::GeometryCollection*>(featureGeometry);
            if (geometryCollection != nullptr && geometryCollection->empty())
            {
                Logger_log(TELL_Warning, "%s: Feature with empty geometry collection.", TAG);
                continue;
            }
        }

        const atakmap::util::AttributeSet featureAttrs(*clientFeature->getAttributes());

        FeaturePtr_const cachedFeature(nullptr, nullptr);
        code = cache->insertFeature(
            &cachedFeature,
            cacheFsid,
            clientFeature->getName(), 
            *featureGeometry,
            clientFeature->getAltitudeMode(), 
            clientFeature->getExtrude(), 
            clientFeature->getStyle(), 
            featureAttrs);
        TE_CHECKLOGCONTINUE_CODE(code, TELL_Warning);

        if (updateFeatureIdMap) {
            worker->featureIdMap->update(clientFid, cacheFid, cachedFeature->getId(), newStaleTime);
            cache->deleteFeature(cacheFid);
        }
        else
            worker->featureIdMap->add(clientFid, cachedFeature->getId(), newStaleTime);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;

    if (bulkMod)
        cache->endBulkModification(true);

    return code;
}

TAKErr FeatureDataStoreProxy::addToCache(
    const ClientRequestWorker* worker, 
    const FeatureSetCursorPtr& cursor, 
    const std::shared_ptr<FeatureDataStore2>& cache) NOTHROWS
{
    bool bulkMod = false;
    TAKErr code(TE_Ok);

    do {
        if (*worker->detached)
            break;

        code = cursor->moveToNext();
        TE_CHECKBREAK_CODE(code);

        const FeatureSet2 *featureSet;
        code = cursor->get(&featureSet);
        TE_CHECKBREAK_CODE(code);

        const Lock lock(*worker->addFeatureSetMutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        if (worker->featureSetIdMap->hasClientFsid(featureSet->getId()))
            continue;

        if (!bulkMod) {
            bulkMod = true;
            cache->beginBulkModification();
        }

        const double minResolution = featureSet->getMinResolution();
        const double maxResolution = featureSet->getMaxResolution();

        FeatureSetPtr_const cachedFeatureSet(nullptr, nullptr);
        code = cache->insertFeatureSet(
            &cachedFeatureSet,
            featureSet->getProvider(),
            featureSet->getType(),
            featureSet->getName(),
            (isValidResolutionValue(minResolution) && minResolution != DBL_MAX) ? minResolution : 0.0,
            isValidResolutionValue(maxResolution) ? maxResolution : DBL_MAX);
        TE_CHECKBREAK_CODE(code);
        worker->featureSetIdMap->add(featureSet->getId(), cachedFeatureSet->getId());
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;

    if (bulkMod)
        cache->endBulkModification(true);

    return code;
}

void FeatureDataStoreProxy::removeDuplicateTasks(const ClientRequestWorker* worker, const ClientRequestTask& task) NOTHROWS
{
    Monitor::Lock lock(*worker->monitor);
    if (!worker->clientRequestQueue->empty()) {
        for (auto& it = worker->clientRequestQueue->begin(); it != worker->clientRequestQueue->end();)
        {
            if (task == *it)
                it = worker->clientRequestQueue->erase(it);
            else 
                ++it;
        }
    }
}

TAKErr FeatureDataStoreProxy::refresh(
    const ClientRequestWorker* worker, 
    const std::shared_ptr<FeatureDataStore2>& client, 
    const std::shared_ptr<FeatureDataStore2>& cache, 
    const int64_t newStaleTime) NOTHROWS
{
    TAKErr code;
    std::unordered_map<int64_t, int64_t> originalClientFids;
    code = worker->featureIdMap->getClientFids(&originalClientFids);
    TE_CHECKRETURN_CODE(code);

    for (auto& queriedRegion : *worker->queriedRegions)
    {
        FeatureCursorPtr cursor(nullptr, nullptr);
        code = client->queryFeatures(cursor, queriedRegion.queryParams);
        TE_CHECKBREAK_CODE(code);

        std::vector<int64_t> foundClientFids;
        code = addToCache(&foundClientFids, worker, cursor, client, cache, INT64_MAX, newStaleTime);
        TE_CHECKBREAK_CODE(code);

        queriedRegion.staleTime = newStaleTime;

        for (const auto foundClientFid : foundClientFids) {
            originalClientFids.erase(foundClientFid);
        }
    }
    TE_CHECKRETURN_CODE(code);

    bool bulkMod = false;
    for (const auto& originalClientFid : originalClientFids) {
        if (!bulkMod) {
            bulkMod = true;
            cache->beginBulkModification();
        }
        worker->featureIdMap->removeCacheFid(originalClientFid.second);
        cache->deleteFeature(originalClientFid.second);
    }
    if (bulkMod)
        cache->endBulkModification(true);

    return code;
}

FeatureDataStoreProxy::QueriedRegion::QueriedRegion(const int64_t staleTime_, const FeatureQueryParameters& queryParams_) NOTHROWS :
    staleTime(staleTime_),
    queryParams(queryParams_)
{}

bool FeatureDataStoreProxy::ClientRequestTask::operator==(const ClientRequestTask& rhs) const NOTHROWS
{
    bool result = true;
    result = result && taskType == rhs.taskType;

    if (result && taskType == ClientRequestTaskType::QUERY_FEATURES)
        result = *featureQueryParameters == *rhs.featureQueryParameters;

    if (result && taskType == ClientRequestTaskType::QUERY_FEATURE_SETS)
        result = *featureSetQueryParameters == *rhs.featureSetQueryParameters;

    return result;
}

bool FeatureDataStoreProxy::ClientRequestTask::operator!=(const ClientRequestTask& rhs) const NOTHROWS
{
    return !operator==(rhs);
}

TAKErr FeatureDataStoreProxy::FeatureIdMap::add(const int64_t clientFid, const int64_t cacheFid, const int64_t staleTime) NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const bool existsInClientFids = clientFids.find(clientFid) != clientFids.end();
    const bool existsInCacheFids = cacheFids.find(cacheFid) != cacheFids.end();

    if (existsInClientFids || existsInCacheFids) {
        if (featureIdMapDebug) {
            if (existsInClientFids && existsInCacheFids) {
                Logger_log(TELL_Warning, "%s: FeatureIdMap::add -- FeatureId exists in both maps.", TAG);
            }
            else {
                Logger_log(TELL_Warning, "%s: FeatureIdMap::add -- FeatureId exists in the %s map.", TAG, existsInClientFids ? "clientFids" : "cacheFids");
            }
        }
        return TE_BadIndex;
    }

    if (featureIdMapDebug)
        Logger_log(TELL_Info, "FeatureIdMap::add: clientFid 0x%llx, cacheFid 0x%llx", clientFid, cacheFid);

    clientFids.insert(std::make_pair(clientFid, cacheFid));
    cacheFids.insert(std::make_pair(cacheFid, std::make_pair(clientFid, staleTime)));
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::FeatureIdMap::update(const int64_t clientFid, const int64_t oldCacheFid, const int64_t newCacheFid, const int64_t staleTime) NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const auto& clientFidItem = clientFids.find(clientFid);
    const auto& cacheFidItem = cacheFids.find(oldCacheFid);

    const bool existsInClientFids = clientFidItem != clientFids.end();
    const bool existsInCacheFids = cacheFidItem != cacheFids.end();

    if (!existsInClientFids || !existsInCacheFids) {
        if (featureIdMapDebug) {
            if (!existsInClientFids && !existsInCacheFids) {
                Logger_log(TELL_Warning, "%s: FeatureIdMap::add -- FeatureId does not exist in either map.", TAG);
            }
            else {
                Logger_log(TELL_Warning, "%s: FeatureIdMap::add -- FeatureId does not exist in the %s map.", TAG, existsInClientFids ? "cacheFids" : "clientFids");
            }
        }
        return TE_BadIndex;
    }

    if (featureIdMapDebug)
        Logger_log(TELL_Info, "FeatureIdMap::update: clientFid 0x%llx, oldCacheFid 0x%llx, newCacheFid 0x%llx", clientFid, oldCacheFid, newCacheFid);

    cacheFids.erase(cacheFidItem);
    clientFidItem->second = newCacheFid;
    cacheFids.insert(std::make_pair(newCacheFid, std::make_pair(clientFid, staleTime)));
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::FeatureIdMap::getClientFid(int64_t *clientFid, const int64_t cacheFid) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const auto it = cacheFids.find(cacheFid);
    if (it == cacheFids.end())
        return TE_Done;
    *clientFid = it->second.first;
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::FeatureIdMap::getCacheFid(int64_t *cacheFid, const int64_t clientFid) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const auto it = clientFids.find(clientFid);
    if (it == clientFids.end())
        return TE_Done;
    *cacheFid = it->second;
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::FeatureIdMap::getCacheStaleTime(int64_t *staleTime, const int64_t cacheFid) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const auto it = cacheFids.find(cacheFid);
    if (it == cacheFids.end())
        return TE_Done;
    *staleTime = it->second.second;
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::FeatureIdMap::getClientFids(std::unordered_map<int64_t, int64_t> *clientFids_) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (clientFids_ == nullptr)
        return TE_InvalidArg;

    *clientFids_ = clientFids;
    return TE_Ok;
}

void FeatureDataStoreProxy::FeatureIdMap::removeClientFid(const int64_t clientFid) NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN(code);

    const auto itClientToCache = clientFids.find(clientFid);
    if (itClientToCache != clientFids.end())
    {
        const auto cacheFid = itClientToCache->second;
        clientFids.erase(itClientToCache);
        if (featureIdMapDebug)
            Logger_log(TELL_Info, "FeatureIdMap::removeClientFid: clientFid 0x%llx", clientFid);

        const auto itCacheToClient = cacheFids.find(cacheFid);
        if (itCacheToClient != cacheFids.end()) {
            cacheFids.erase(itCacheToClient);
            if (featureIdMapDebug)
                Logger_log(TELL_Info, "FeatureIdMap::removeClientFid: cacheFid 0x%llx", cacheFid);
        }
        else if (featureIdMapDebug)
            Logger_log(TELL_Warning, "FeatureIdMap::removeClientFid: cacheFid 0x%llx not found!", cacheFid);
    }
    else if (featureIdMapDebug)
        Logger_log(TELL_Warning, "FeatureIdMap::removeClientFid: clientFid 0x%llx not found!", clientFid);
}

void FeatureDataStoreProxy::FeatureIdMap::removeCacheFid(const int64_t cacheFid) NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN(code);

    const auto itCacheFids = cacheFids.find(cacheFid);
    if (itCacheFids != cacheFids.end())
    {
        const auto clientFid = itCacheFids->second.first;
        cacheFids.erase(itCacheFids);
        if (featureIdMapDebug)
            Logger_log(TELL_Info, "FeatureIdMap::removeCacheFid: cacheFid 0x%llx", cacheFid);

        const auto itClientFids = clientFids.find(clientFid);
        if (itClientFids != clientFids.end()) {
            clientFids.erase(itClientFids);
            if (featureIdMapDebug)
                Logger_log(TELL_Info, "FeatureIdMap::removeCacheFid: clientFid 0x%llx", clientFid);
        }
        else if (featureIdMapDebug)
            Logger_log(TELL_Warning, "FeatureIdMap::removeCacheFid: clientFid 0x%llx not found!", clientFid);
    }
    else if (featureIdMapDebug)
        Logger_log(TELL_Warning, "FeatureIdMap::removeCacheFid: cacheFid 0x%llx not found!", cacheFid);
}

void FeatureDataStoreProxy::FeatureIdMap::clear() NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN(code);
    clientFids.clear();
    cacheFids.clear();
}

TAKErr FeatureDataStoreProxy::FeatureSetIdMap::add(const int64_t clientFsid, const int64_t cacheFsid) NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const bool existsInClientFsids = clientFsids.find(clientFsid) != clientFsids.end();
    const bool existsInCacheFsids = cacheFsids.find(cacheFsid) != cacheFsids.end();

    if (existsInClientFsids || existsInCacheFsids) {
        return TE_BadIndex;
    }

    clientFsids.insert(std::make_pair(clientFsid, cacheFsid));
    cacheFsids.insert(std::make_pair(cacheFsid, clientFsid));
    return TE_Ok;
}

bool FeatureDataStoreProxy::FeatureSetIdMap::hasClientFsid(const int64_t clientFsid) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);
    return clientFsids.find(clientFsid) != clientFsids.end();
}

bool FeatureDataStoreProxy::FeatureSetIdMap::hasCacheFsid(const int64_t cacheFsid) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);
    return cacheFsids.find(cacheFsid) != cacheFsids.end();
}

TAKErr FeatureDataStoreProxy::FeatureSetIdMap::getClientFsid(int64_t *clientFsid, const int64_t cacheFsid) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const auto& it = cacheFsids.find(cacheFsid);
    if (it == cacheFsids.end())
        return TE_Done;
    *clientFsid = it->second;
    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::FeatureSetIdMap::getCacheFsid(int64_t *cacheFsid, const int64_t clientFsid) const NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN_CODE(code);

    const auto& it = clientFsids.find(clientFsid);
    if (it == clientFsids.end())
        return TE_Done;
    *cacheFsid = it->second;
    return TE_Ok;
}

void FeatureDataStoreProxy::FeatureSetIdMap::removeClientFsid(const int64_t clientFsid) NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN(code);

    const auto& itClientToCache = clientFsids.find(clientFsid);
    if (itClientToCache != clientFsids.end())
    {
        const auto cacheFsid = itClientToCache->second;
        clientFsids.erase(itClientToCache);
        if (featureIdMapDebug)
            Logger_log(TELL_Info, "FeatureSetIdMap::removeClientFsid: clientFsid 0x%llx", clientFsid);

        const auto itCacheToClient = cacheFsids.find(cacheFsid);
        if (itCacheToClient != cacheFsids.end()) {
            cacheFsids.erase(itCacheToClient);
            if (featureIdMapDebug)
                Logger_log(TELL_Info, "FeatureSetIdMap::removeClientFsid: cacheFsid 0x%llx", cacheFsid);
        }
        else if (featureIdMapDebug)
            Logger_log(TELL_Warning, "FeatureSetIdMap::removeClientFsid: cacheFsid 0x%llx not found!", cacheFsid);
    }
    else if (featureIdMapDebug)
        Logger_log(TELL_Warning, "FeatureSetIdMap::removeClientFsid: clientFsid 0x%llx not found!", clientFsid);
}

void FeatureDataStoreProxy::FeatureSetIdMap::removeCacheFsid(const int64_t cacheFsid) NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN(code);

    const auto& itCacheToClient = cacheFsids.find(cacheFsid);
    if (itCacheToClient != cacheFsids.end())
    {
        const auto clientFsid = itCacheToClient->second;
        cacheFsids.erase(itCacheToClient);
        if (featureIdMapDebug)
            Logger_log(TELL_Info, "FeatureSetIdMap::removeCacheFsid: cacheFid 0x%llx", cacheFsid);

        const auto itClientFsids = clientFsids.find(clientFsid);
        if (itClientFsids != clientFsids.end()) {
            clientFsids.erase(itClientFsids);
            if (featureIdMapDebug)
                Logger_log(TELL_Info, "FeatureSetIdMap::removeCacheFsid: clientFsid 0x%llx", clientFsid);
        }
        else if (featureIdMapDebug)
            Logger_log(TELL_Warning, "FeatureSetIdMap::removeCacheFsid: clientFsid 0x%llx not found!", clientFsid);
    }
    else if (featureIdMapDebug)
        Logger_log(TELL_Warning, "FeatureSetIdMap::removeCacheFsid: cacheFsid 0x%llx not found!", cacheFsid);
}

void FeatureDataStoreProxy::FeatureSetIdMap::clear() NOTHROWS
{
    const Lock lock(mutex);
    TAKErr code = lock.status;
    TE_CHECKRETURN(code);
    clientFsids.clear();
    cacheFsids.clear();
}

FeatureDataStoreProxy::CacheFeatureCursor::CacheFeatureCursor(
    FeatureCursorPtr &&impl_, 
    std::shared_ptr<FeatureIdMap> &featureIdMap_, 
    std::shared_ptr<FeatureSetIdMap> &featureSetIdMap_) NOTHROWS :
    impl(std::move(impl_)),
    featureIdMap(featureIdMap_),
    featureSetIdMap(featureSetIdMap_)
{
}

FeatureDataStoreProxy::CacheFeatureCursor::~CacheFeatureCursor() NOTHROWS
{
    impl.reset();
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::getId(int64_t *value) NOTHROWS
{
    if (value == nullptr)
        return TE_InvalidArg;
    
    int64_t cacheFid;
    TAKErr code = impl->getId(&cacheFid);
    TE_CHECKRETURN_CODE(code);

    int64_t clientFid;
    code = featureIdMap->getClientFid(&clientFid, cacheFid);
    if (code == TE_Ok)
    {
        *value = clientFid;
        return TE_Ok;
    }
    //Logger_log(TELL_Warning, "FeatureDataStoreProxy::CachingFeatureCursor::getId: cacheFid 0x%llx not found!", cacheFid);
    return code;
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::getFeatureSetId(int64_t *value) NOTHROWS
{
    if (value == nullptr)
        return TE_InvalidArg;
    
    int64_t cacheFsid;
    TAKErr code = impl->getFeatureSetId(&cacheFsid);
    TE_CHECKRETURN_CODE(code);

    int64_t clientFsid;
    code = featureSetIdMap->getClientFsid(&clientFsid, cacheFsid);
    if (code == TE_Ok)
    {
        *value = clientFsid;
        return TE_Ok;
    }
    //Logger_log(TELL_Warning, "FeatureDataStoreProxy::CachingFeatureCursor::getFeatureSetId: cacheFid 0x%llx not found!", cacheFsid);
    return code;
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::getVersion(int64_t *value) NOTHROWS
{
    return impl->getVersion(value);
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    return impl->getRawGeometry(value);
}

FeatureDefinition2::GeometryEncoding FeatureDataStoreProxy::CacheFeatureCursor::getGeomCoding() NOTHROWS
{
    return impl->getGeomCoding();
}

AltitudeMode FeatureDataStoreProxy::CacheFeatureCursor::getAltitudeMode() NOTHROWS
{
    return impl->getAltitudeMode();
}

double FeatureDataStoreProxy::CacheFeatureCursor::getExtrude() NOTHROWS
{
    return impl->getExtrude();
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::getName(const char **value) NOTHROWS
{
    return impl->getName(value);
}

FeatureDefinition2::StyleEncoding FeatureDataStoreProxy::CacheFeatureCursor::getStyleCoding() NOTHROWS
{
    return impl->getStyleCoding();
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    return impl->getRawStyle(value);
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    return impl->getAttributes(value);
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::get(const Feature2 **feature) NOTHROWS
{
    if (!feature)
        return TE_InvalidArg;

    TAKErr code;
    const Feature2* cacheFeature;
    code = impl->get(&cacheFeature);
    TE_CHECKRETURN_CODE(code);

    const int64_t cacheFid = cacheFeature->getId();
    int64_t clientFid;
    code = featureIdMap->getClientFid(&clientFid, cacheFid);
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<Feature2> newFeature(::new (std::nothrow) Feature2(
        clientFid, cacheFeature->getFeatureSetId(), cacheFeature->getName(), 
        *cacheFeature->getGeometry(), cacheFeature->getAltitudeMode(), cacheFeature->getExtrude(), 
        *cacheFeature->getStyle(), *cacheFeature->getAttributes(), cacheFeature->getVersion()));

    if (newFeature != nullptr)
        *feature = newFeature.release();
    else
        return TE_OutOfMemory;

    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::CacheFeatureCursor::moveToNext() NOTHROWS
{
    return impl->moveToNext();
}

FeatureDataStoreProxy::CacheFeatureSetCursor::CacheFeatureSetCursor(
    FeatureSetCursorPtr &&impl_, 
    std::shared_ptr<FeatureSetIdMap> &featureSetIdMap_) NOTHROWS :
    impl(std::move(impl_)),
    featureSetIdMap(featureSetIdMap_)
{
}

FeatureDataStoreProxy::CacheFeatureSetCursor::~CacheFeatureSetCursor() NOTHROWS
{
    impl.reset();
}

TAKErr FeatureDataStoreProxy::CacheFeatureSetCursor::get(const FeatureSet2 **featureSet) NOTHROWS
{
    if (!featureSet)
        return TE_InvalidArg;

    TAKErr code;
    const FeatureSet2* cacheFeatureSet;
    code = impl->get(&cacheFeatureSet);
    TE_CHECKRETURN_CODE(code);

    const int64_t cacheFsid = cacheFeatureSet->getId();
    int64_t clientFsid;
    code = featureSetIdMap->getClientFsid(&clientFsid, cacheFsid);
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<FeatureSet2> newFeatureSet(::new (std::nothrow) FeatureSet2(
        clientFsid, cacheFeatureSet->getProvider(), cacheFeatureSet->getType(), 
        cacheFeatureSet->getName(), cacheFeatureSet->getMinResolution(), cacheFeatureSet->getMaxResolution(), 
        cacheFeatureSet->getVersion()));

    if (newFeatureSet != nullptr)
        *featureSet = newFeatureSet.release();
    else
        return TE_OutOfMemory;

    return TE_Ok;
}

TAKErr FeatureDataStoreProxy::CacheFeatureSetCursor::moveToNext() NOTHROWS
{
    return impl->moveToNext();
}

#endif
