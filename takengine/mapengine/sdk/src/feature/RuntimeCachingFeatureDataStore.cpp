#include "feature/RuntimeCachingFeatureDataStore.h"

#include <cstddef>
#include <cstdlib>

#include "feature/FeatureSetCursor2.h"
#include "feature/MultiplexingFeatureCursor.h"
#include "thread/Lock.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define XXX_MAX_RENDER_QUERY_LIMIT 1000

namespace
{
    class OwnerReferencingFeatureCursor : public FeatureCursor2
    {
    public:
        OwnerReferencingFeatureCursor(const std::shared_ptr<FeatureDataStore2> &owner, FeatureCursorPtr &&impl) NOTHROWS;
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
        std::shared_ptr<FeatureDataStore2> owner;
        FeatureCursorPtr impl;
    };
}

class RuntimeCachingFeatureDataStore::CachingFeatureCursor : public FeatureCursor2
{
public:
    CachingFeatureCursor(RuntimeCachingFeatureDataStore &owner, FeatureCursorPtr &&impl) NOTHROWS;
    ~CachingFeatureCursor() NOTHROWS override;
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
    RuntimeCachingFeatureDataStore &owner;
    FeatureCursorPtr impl;
    std::shared_ptr<RuntimeFeatureDataStore2> cache;
};


RuntimeCachingFeatureDataStore::RuntimeCachingFeatureDataStore(FeatureDataStore2Ptr &&impl_) NOTHROWS :
    impl(std::move(impl_)),
    dirty(true)
{}

TAKErr RuntimeCachingFeatureDataStore::isClientQuery(bool *value, const FeatureQueryParameters &other) NOTHROWS
{
    *value = other.spatialFilter.get() && (!isnan(other.maxResolution) || !isnan(other.minResolution));
    return TE_Ok;
}
TAKErr RuntimeCachingFeatureDataStore::addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    // XXX - should be wrapped
    return impl->addOnDataStoreContentChangedListener(l);
}
TAKErr RuntimeCachingFeatureDataStore::removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    // XXX - should be wrapped
    return impl->removeOnDataStoreContentChangedListener(l);
}
TAKErr RuntimeCachingFeatureDataStore::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS
{
    // XXX - check cache
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (this->cache.get()) {
        // XXX - if missed, query from client and update cache ???
        return this->cache->getFeature(feature, fid);
    }
    

    return TE_InvalidArg;
}
TAKErr RuntimeCachingFeatureDataStore::queryFeatures(FeatureCursorPtr &cursor) NOTHROWS
{
    // XXX - current implementation is never a client query -- should we query
    //       against the client if the user has asked for everything??? this
    //       could be extremely expensive
    TAKErr code(TE_Ok);

    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (this->cache.get()) {
        FeatureCursorPtr retval(nullptr, nullptr);
        code = this->cache->queryFeatures(retval);
        TE_CHECKRETURN_CODE(code);

        cursor = FeatureCursorPtr(new OwnerReferencingFeatureCursor(this->cache, std::move(retval)), Memory_deleter_const<FeatureCursor2, OwnerReferencingFeatureCursor>);
        return code;
    } else {
        cursor = FeatureCursorPtr(new MultiplexingFeatureCursor(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
        return code;
    }
}
TAKErr RuntimeCachingFeatureDataStore::queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    bool clientQuery;
    code = this->isClientQuery(&clientQuery, params);
    if (!clientQuery) {
        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        if (this->cache.get()) {
            FeatureCursorPtr retval(nullptr, nullptr);
            code = this->cache->queryFeatures(retval, params);
            TE_CHECKRETURN_CODE(code);

            cursor = FeatureCursorPtr(new OwnerReferencingFeatureCursor(this->cache, std::move(retval)), Memory_deleter_const<FeatureCursor2, OwnerReferencingFeatureCursor>);
            return code;
        } else {
            cursor = FeatureCursorPtr(new MultiplexingFeatureCursor(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
            return code;
        }
    } else {
        // XXX - this really should be occuring in the renderer -- remove this
        //       code as soon as we have Control/MapRenderer interfaces
        //       implemented
        int count;
        code = this->impl->queryFeaturesCount(&count, params);
        TE_CHECKRETURN_CODE(code);
        if (count > XXX_MAX_RENDER_QUERY_LIMIT) {
            Logger_log(TELL_Warning, "Requested render query contains %d features exceeding limit.", count);
            // if a large number of features is requested, simply return the
            // empty cursor
            cursor = FeatureCursorPtr(new MultiplexingFeatureCursor(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
            return code;
        }

        FeatureCursorPtr retval(nullptr, nullptr);
        code = this->impl->queryFeatures(retval, params);
        TE_CHECKRETURN_CODE(code);

        cursor = FeatureCursorPtr(new CachingFeatureCursor(*this, std::move(retval)), Memory_deleter_const<FeatureCursor2, CachingFeatureCursor>);
        return code;
    }
}
TAKErr RuntimeCachingFeatureDataStore::queryFeaturesCount(int *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    bool clientQuery;
    code = this->isClientQuery(&clientQuery, FeatureQueryParameters());
    if (!clientQuery) {
        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        if (this->cache.get()) {
            return this->cache->queryFeaturesCount(value);
        } else {
            *value = 0;
            return code;
        }
    } else {
        return this->impl->queryFeaturesCount(value);
    }
}
TAKErr RuntimeCachingFeatureDataStore::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    bool clientQuery;
    code = this->isClientQuery(&clientQuery, params);
    if (!clientQuery) {
        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        if (this->cache.get()) {
            return this->cache->queryFeaturesCount(value, params);
        } else {
            *value = 0;
            return code;
        }
    } else {
        return this->impl->queryFeaturesCount(value, params);
    }
}
TAKErr RuntimeCachingFeatureDataStore::getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t featureSetId) NOTHROWS
{
    // XXX - pull from non dirty cache; validate cache if necessary
    return this->impl->getFeatureSet(featureSet, featureSetId);
}
TAKErr RuntimeCachingFeatureDataStore::queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS
{
    // XXX - pull from non dirty cache; validate cache if necessary
    return this->impl->queryFeatureSets(cursor);
}
TAKErr RuntimeCachingFeatureDataStore::queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS
{
    // XXX - pull from non dirty cache; validate cache if necessary
    return this->impl->queryFeatureSets(cursor, params);
}
TAKErr RuntimeCachingFeatureDataStore::queryFeatureSetsCount(int *value) NOTHROWS
{
    // XXX - pull from non dirty cache; validate cache if necessary
    return this->impl->queryFeatureSetsCount(value);
}
TAKErr RuntimeCachingFeatureDataStore::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    // XXX - pull from non dirty cache; validate cache if necessary
    return this->impl->queryFeatureSetsCount(value, params);
}
TAKErr RuntimeCachingFeatureDataStore::getModificationFlags(int *value) NOTHROWS
{
    return this->impl->getModificationFlags(value);
}

#define CLIENT_CACHE_MIRRORED_CALL(fn, ...) \
    TAKErr code(TE_Ok); \
    code = this->impl->fn(__VA_ARGS__); \
    TE_CHECKRETURN_CODE(code); \
    Lock lock(mutex); \
    code = lock.status; \
    TE_CHECKRETURN_CODE(code); \
    if (this->cache.get()) { \
        code = this->cache->fn(__VA_ARGS__); \
        TE_CHECKRETURN_CODE(code); \
    } \
    return code;

TAKErr RuntimeCachingFeatureDataStore::beginBulkModification() NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(beginBulkModification, );
}
TAKErr RuntimeCachingFeatureDataStore::endBulkModification(const bool successful) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(endBulkModification, successful);
}
TAKErr RuntimeCachingFeatureDataStore::isInBulkModification(bool *value) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(isInBulkModification, value);
}
TAKErr RuntimeCachingFeatureDataStore::insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code(TE_Ok);
    FeatureSetPtr_const inserted(nullptr, nullptr);
    code = impl->insertFeatureSet(&inserted, provider, type, name, minResolution, maxResolution);
    TE_CHECKRETURN_CODE(code);

    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (this->cache.get()) {
        // XXX - what to do on error here???
        this->cache->insertFeatureSet(nullptr, *inserted);
    }

    if (featureSet)
        *featureSet = std::move(inserted);

    return code;
}
TAKErr RuntimeCachingFeatureDataStore::updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeatureSet, fsid, name);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeatureSet, fsid, minResolution, maxResolution);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeatureSet, fsid, name, minResolution, maxResolution);
}
TAKErr RuntimeCachingFeatureDataStore::deleteFeatureSet(const int64_t fsid) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(deleteFeatureSet, fsid);
}
TAKErr RuntimeCachingFeatureDataStore::deleteAllFeatureSets() NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(deleteAllFeatureSets, );
}
TAKErr RuntimeCachingFeatureDataStore::insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name,
                                                     const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode,
                                                     const double extrude, const atakmap::feature::Style *style,
                                                     const atakmap::util::AttributeSet &attributes) NOTHROWS {
    TAKErr code(TE_Ok);
    FeaturePtr_const inserted(nullptr, nullptr);
    code = impl->insertFeature(&inserted, fsid, name, geom, altitudeMode, extrude, style, attributes);
    TE_CHECKRETURN_CODE(code);

    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (this->cache.get()) {
        // XXX - what to do on error here???
        this->cache->insertFeature(nullptr, *inserted);
    }

    if (feature)
        *feature = std::move(inserted);

    return code;
}
TAKErr RuntimeCachingFeatureDataStore::updateFeature(const int64_t fid, const char *name) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeature, fid, name);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeature, fid, geom);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeature, fid, geom, altitudeMode, extrude);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeature(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeature, fid, altitudeMode, extrude);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeature, fid, style);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeature, fid, attributes);
}
TAKErr RuntimeCachingFeatureDataStore::updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(updateFeature, fid, name, geom, style, attributes);
}
TAKErr RuntimeCachingFeatureDataStore::deleteFeature(const int64_t fid) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(deleteFeature, fid);
}
TAKErr RuntimeCachingFeatureDataStore::deleteAllFeatures(const int64_t fsid) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(deleteAllFeatures, fsid);
}
TAKErr RuntimeCachingFeatureDataStore::getVisibilitySettingsFlags(int *value) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(getVisibilitySettingsFlags, value);
}
TAKErr RuntimeCachingFeatureDataStore::setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(setFeatureVisible, fid, visible);
}
TAKErr RuntimeCachingFeatureDataStore::setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(setFeaturesVisible, params, visible);
}
TAKErr RuntimeCachingFeatureDataStore::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(isFeatureVisible, value, fid);
}
TAKErr RuntimeCachingFeatureDataStore::setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(setFeatureSetVisible, setId, visible);
}
TAKErr RuntimeCachingFeatureDataStore::setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(setFeatureSetsVisible, params, visible);
}
TAKErr RuntimeCachingFeatureDataStore::isFeatureSetVisible(bool *value, const int64_t setId) NOTHROWS
{
    CLIENT_CACHE_MIRRORED_CALL(isFeatureSetVisible, value, setId);
}

#undef CLIENT_CACHE_MIRRORED_CALL

TAKErr RuntimeCachingFeatureDataStore::setFeatureSetReadOnly(const int64_t fsid, const bool readOnly) NOTHROWS
{
    return this->impl->setFeatureSetReadOnly(fsid, readOnly);
}
TAKErr RuntimeCachingFeatureDataStore::setFeatureSetsReadOnly(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS
{
    return this->impl->setFeatureSetsReadOnly(params, readOnly);
}
TAKErr RuntimeCachingFeatureDataStore::isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS 
{
    return this->impl->isFeatureSetReadOnly(value, fsid);
}
TAKErr RuntimeCachingFeatureDataStore::isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS 
{
    return this->impl->isFeatureReadOnly(value, fid);
}
TAKErr RuntimeCachingFeatureDataStore::isAvailable(bool *value) NOTHROWS
{
    return this->impl->isAvailable(value);
}
TAKErr RuntimeCachingFeatureDataStore::refresh() NOTHROWS
{
    TAKErr code(TE_Ok);
    code = this->impl->refresh();
    TE_CHECKRETURN_CODE(code);

    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->dirty = true;

    return code;
}
TAKErr RuntimeCachingFeatureDataStore::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    return this->impl->getUri(value);
}
TAKErr RuntimeCachingFeatureDataStore::close() NOTHROWS
{
    TAKErr code(TE_Ok);
    code = this->impl->close();
    TE_CHECKRETURN_CODE(code);

    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (this->cache.get()) {
        this->cache->close();
        this->cache.reset();
    }
    return code;
}

RuntimeCachingFeatureDataStore::CachingFeatureCursor::CachingFeatureCursor(RuntimeCachingFeatureDataStore &owner_, FeatureCursorPtr &&impl_) NOTHROWS :
    owner(owner_),
    impl(std::move(impl_))
{
    int modFlags = 0xFFFFFFF;
    owner.impl->getModificationFlags(&modFlags);
    int visFlags = 0xFFFFFFF;
    owner.impl->getVisibilitySettingsFlags(&visFlags);

    std::unique_ptr<RuntimeFeatureDataStore2> cachePtr(
        new RuntimeFeatureDataStore2(
            modFlags | MODIFY_FEATURESET_INSERT | MODIFY_FEATURESET_FEATURE_INSERT,
            visFlags));
    cache = std::move(cachePtr);

    // XXX - not crazy about this....
    do {
        TAKErr code(TE_Ok);
        FeatureSetCursorPtr result(nullptr, nullptr);

        code = this->owner.impl->queryFeatureSets(result);
        TE_CHECKBREAK_CODE(code);

        do {
            code = result->moveToNext();
            TE_CHECKBREAK_CODE(code);

            const FeatureSet2 *featureSet;
            code = result->get(&featureSet);
            TE_CHECKBREAK_CODE(code);

            code = this->cache->insertFeatureSet(nullptr, *featureSet);
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKBREAK_CODE(code);

    } while (false);
}
RuntimeCachingFeatureDataStore::CachingFeatureCursor::~CachingFeatureCursor() NOTHROWS
{
    Lock lock(owner.mutex);
    if (lock.status == TE_Ok) {
        // swap the cache
        owner.cache = this->cache;
    }

    impl.reset();
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::getId(int64_t *value) NOTHROWS
{
    return impl->getId(value);
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::getFeatureSetId(int64_t *value) NOTHROWS
{
    return impl->getFeatureSetId(value);
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::getVersion(int64_t *value) NOTHROWS
{
    return impl->getVersion(value);
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    return impl->getRawGeometry(value);
}
FeatureDefinition2::GeometryEncoding RuntimeCachingFeatureDataStore::CachingFeatureCursor::getGeomCoding() NOTHROWS
{
    return impl->getGeomCoding();
}
AltitudeMode RuntimeCachingFeatureDataStore::CachingFeatureCursor::getAltitudeMode() NOTHROWS
{
    return impl->getAltitudeMode();
}
double RuntimeCachingFeatureDataStore::CachingFeatureCursor::getExtrude() NOTHROWS
{
    return impl->getExtrude();
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::getName(const char **value) NOTHROWS
{
    return impl->getName(value);
}
FeatureDefinition2::StyleEncoding RuntimeCachingFeatureDataStore::CachingFeatureCursor::getStyleCoding() NOTHROWS
{
    return impl->getStyleCoding();
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    return impl->getRawStyle(value);
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    return impl->getAttributes(value);
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::get(const Feature2 **feature) NOTHROWS
{
    return impl->get(feature);
}
TAKErr RuntimeCachingFeatureDataStore::CachingFeatureCursor::moveToNext() NOTHROWS
{
    TAKErr code(TE_Ok);
    code = this->impl->moveToNext();
    if (code == TE_Done)
        return code;
    TE_CHECKRETURN_CODE(code);

    // XXX - not entirely sure what correct behavior is caching fails...
    do {
        const Feature2 *feature;
        code = this->get(&feature);
        TE_CHECKBREAK_CODE(code);

        code = this->cache->insertFeature(nullptr, *feature);
        TE_CHECKBREAK_CODE(code);
    } while (false);

    return TE_Ok;
}

namespace
{
    OwnerReferencingFeatureCursor::OwnerReferencingFeatureCursor(const std::shared_ptr<FeatureDataStore2> &owner_, FeatureCursorPtr &&impl_) NOTHROWS :
        owner(owner_),
        impl(std::move(impl_))
    {}
    TAKErr OwnerReferencingFeatureCursor::getId(int64_t *value) NOTHROWS
    {
            return impl->getId(value);
    }
    TAKErr OwnerReferencingFeatureCursor::getFeatureSetId(int64_t *value) NOTHROWS
    {
        return impl->getFeatureSetId(value);
    }
    TAKErr OwnerReferencingFeatureCursor::getVersion(int64_t *value) NOTHROWS
    {
        return impl->getVersion(value);
    }
    TAKErr OwnerReferencingFeatureCursor::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
    {
        return impl->getRawGeometry(value);
    }
    FeatureDefinition2::GeometryEncoding OwnerReferencingFeatureCursor::getGeomCoding() NOTHROWS
    {
        return impl->getGeomCoding();
    }
    AltitudeMode OwnerReferencingFeatureCursor::getAltitudeMode() NOTHROWS 
    {
        return impl->getAltitudeMode();
    }
    double OwnerReferencingFeatureCursor::getExtrude() NOTHROWS 
    {
        return impl->getExtrude();
    }
    TAKErr OwnerReferencingFeatureCursor::getName(const char **value) NOTHROWS
    {
        return impl->getName(value);
    }
    FeatureDefinition2::StyleEncoding OwnerReferencingFeatureCursor::getStyleCoding() NOTHROWS
    {
        return impl->getStyleCoding();
    }
    TAKErr OwnerReferencingFeatureCursor::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
    {
        return impl->getRawStyle(value);
    }
    TAKErr OwnerReferencingFeatureCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
    {
        return impl->getAttributes(value);
    }
    TAKErr OwnerReferencingFeatureCursor::get(const Feature2 **feature) NOTHROWS
    {
        return impl->get(feature);
    }
    TAKErr OwnerReferencingFeatureCursor::moveToNext() NOTHROWS
    {
        return impl->moveToNext();
    }
}
