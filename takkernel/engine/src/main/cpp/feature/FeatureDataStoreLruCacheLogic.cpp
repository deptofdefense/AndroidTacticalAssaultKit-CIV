#ifdef MSVC
#include "feature/FeatureDataStoreLruCacheLogic.h"

#include "thread/Lock.h"

#include "FeatureCursor2.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

FeatureDataStoreLruCacheLogic::FeatureDataStoreLruCacheLogic(FeatureDataStore2Ptr &&impl_, const int cacheMaxItems_) NOTHROWS :
    impl(std::move(impl_)),
    maxItems(cacheMaxItems_)
{
}
FeatureDataStoreLruCacheLogic::~FeatureDataStoreLruCacheLogic() NOTHROWS
{
}
TAKErr FeatureDataStoreLruCacheLogic::addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    return impl->addOnDataStoreContentChangedListener(l);
}
TAKErr FeatureDataStoreLruCacheLogic::removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    return impl->removeOnDataStoreContentChangedListener(l);
}
TAKErr FeatureDataStoreLruCacheLogic::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS
{
    TAKErr code;
    const Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    code = this->impl->getFeature(feature, fid);
    TE_CHECKRETURN_CODE(code);

    const auto& it = lruIterators.find(fid);
    lru.erase(it->second);
    lru.push_front(it->first);
    it->second = lru.begin();

    return code;
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeatures(FeatureCursorPtr &cursor) NOTHROWS
{
    return this->impl->queryFeatures(cursor);
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS
{
    return this->impl->queryFeatures(cursor, params);
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeaturesCount(int *value) NOTHROWS
{
    return this->impl->queryFeaturesCount(value);
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    return this->impl->queryFeaturesCount(value, params);
}
TAKErr FeatureDataStoreLruCacheLogic::getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS
{
    return this->impl->getFeatureSet(featureSet, featureSetId);
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS
{
    return this->impl->queryFeatureSets(cursor);
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS
{
    return this->impl->queryFeatureSets(cursor, params);
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeatureSetsCount(int *value) NOTHROWS
{
    return this->impl->queryFeatureSetsCount(value);
}
TAKErr FeatureDataStoreLruCacheLogic::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    return this->impl->queryFeatureSetsCount(value, params);
}
TAKErr FeatureDataStoreLruCacheLogic::getModificationFlags(int *value) NOTHROWS
{
    return this->impl->getModificationFlags(value);
}
TAKErr FeatureDataStoreLruCacheLogic::beginBulkModification() NOTHROWS
{
    return this->impl->beginBulkModification();
}
TAKErr FeatureDataStoreLruCacheLogic::endBulkModification(const bool successful) NOTHROWS
{
    return this->impl->endBulkModification(successful);
}
TAKErr FeatureDataStoreLruCacheLogic::isInBulkModification(bool *value) NOTHROWS
{
    return this->impl->isInBulkModification(value);
}
TAKErr FeatureDataStoreLruCacheLogic::insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return this->impl->insertFeatureSet(featureSet, provider, type, name, minResolution, maxResolution);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS
{
    return this->impl->updateFeatureSet(fsid, name);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    return this->impl->updateFeatureSet(fsid, minResolution, maxResolution);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return this->impl->updateFeatureSet(fsid, name, minResolution, maxResolution);
}
TAKErr FeatureDataStoreLruCacheLogic::deleteFeatureSet(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    FeatureQueryParameters params;
    params.featureSetIds->add(fsid);
    FeatureCursorPtr cursor(nullptr, nullptr);
    std::vector<int64_t> featureIds;

    const Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

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

    code = this->impl->deleteFeatureSet(fsid);
    TE_CHECKRETURN_CODE(code);

    for (const auto fid : featureIds)
    {
        const auto& it = lruIterators.find(fid);
        lru.erase(it->second);
        lruIterators.erase(it);
    }

    return code;
}
TAKErr FeatureDataStoreLruCacheLogic::deleteAllFeatureSets() NOTHROWS
{
    TAKErr code;
    const Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    code = this->impl->deleteAllFeatureSets();
    TE_CHECKRETURN_CODE(code);

    lruIterators.clear();
    lru.clear();
    return code;
}
TAKErr FeatureDataStoreLruCacheLogic::insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name,
                                                     const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode,
                                                     const double extrude, const atakmap::feature::Style *style,
                                                     const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code;
    const Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    code = this->impl->insertFeature(feature, fsid, name, geom, altitudeMode, extrude, style, attributes);
    TE_CHECKRETURN_CODE(code);

    const auto fid = (*feature)->getId();
    if (lruIterators.size() == maxItems)
    {
        code = this->impl->deleteFeature(lru.back());
        TE_CHECKRETURN_CODE(code);
        lruIterators.erase(lru.back());
        lru.pop_back();
    }
    lru.push_front(fid);
    lruIterators.emplace(fid, lru.begin());

    return code;
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeature(const int64_t fid, const char *name) NOTHROWS
{
    return this->impl->updateFeature(fid, name);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    return this->impl->updateFeature(fid, geom);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return this->impl->updateFeature(fid, geom, altitudeMode, extrude);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeature(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return this->impl->updateFeature(fid, altitudeMode, extrude);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    return this->impl->updateFeature(fid, style);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return this->impl->updateFeature(fid, attributes);
}
TAKErr FeatureDataStoreLruCacheLogic::updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return this->impl->updateFeature(fid, name, geom, style, attributes);
}
TAKErr FeatureDataStoreLruCacheLogic::deleteFeature(const int64_t fid) NOTHROWS
{
    TAKErr code;
    const Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    code = this->impl->deleteFeature(fid);
    if (code == TE_Ok)
    {
        const auto& it = lruIterators.find(fid);
        lru.erase(it->second);
        lruIterators.erase(it);
    }

    return code;
}
TAKErr FeatureDataStoreLruCacheLogic::deleteAllFeatures(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    FeatureQueryParameters params;
    params.featureSetIds->add(fsid);
    FeatureCursorPtr cursor(nullptr, nullptr);
    std::vector<int64_t> featureIds;

    const Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

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

    code = this->impl->deleteAllFeatures(fsid);
    TE_CHECKRETURN_CODE(code);

    for (const auto fid : featureIds)
    {
        const auto& it = lruIterators.find(fid);
        lru.erase(it->second);
        lruIterators.erase(it);
    }

    return code;
}
TAKErr FeatureDataStoreLruCacheLogic::getVisibilitySettingsFlags(int *value) NOTHROWS
{
    return this->impl->getVisibilitySettingsFlags(value);
}
TAKErr FeatureDataStoreLruCacheLogic::setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS
{
    return this->impl->setFeatureVisible(fid, visible);
}
TAKErr FeatureDataStoreLruCacheLogic::setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    return this->impl->setFeaturesVisible(params, visible);
}
TAKErr FeatureDataStoreLruCacheLogic::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    return this->impl->isFeatureVisible(value, fid);
}
TAKErr FeatureDataStoreLruCacheLogic::setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS
{
    return this->impl->setFeatureSetVisible(setId, visible);
}
TAKErr FeatureDataStoreLruCacheLogic::setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    return this->impl->setFeatureSetsVisible(params, visible);
}
TAKErr FeatureDataStoreLruCacheLogic::isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS
{
    return this->impl->isFeatureSetVisible(value, setId);
}
TAKErr FeatureDataStoreLruCacheLogic::setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS
{
    return this->impl->setFeatureSetReadOnly(fsid, readOnly);
}
TAKErr FeatureDataStoreLruCacheLogic::setFeatureSetsReadOnly(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS
{
    return this->impl->setFeatureSetsReadOnly(params, readOnly);
}
TAKErr FeatureDataStoreLruCacheLogic::isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS 
{
    return this->impl->isFeatureSetReadOnly(value, fsid);
}
TAKErr FeatureDataStoreLruCacheLogic::isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS 
{
    return this->impl->isFeatureReadOnly(value, fid);
}
TAKErr FeatureDataStoreLruCacheLogic::isAvailable(bool *value) NOTHROWS
{
    return this->impl->isAvailable(value);
}
TAKErr FeatureDataStoreLruCacheLogic::refresh() NOTHROWS
{
    return this->impl->refresh();
}
TAKErr FeatureDataStoreLruCacheLogic::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    return this->impl->getUri(value);
}
TAKErr FeatureDataStoreLruCacheLogic::close() NOTHROWS
{
    return this->impl->close();
}
#endif
