#include "feature/AbstractFeatureDataStore2.h"

#include <regex>

#include "feature/FeatureCursor2.h"
#include "thread/Lock.h"
#include "util/Logging.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

namespace
{
    std::string replace(const std::string &src, const char old, const char *cnew)
    {
        std::string dst = src;
        std::size_t pos = 0u;
        std::size_t repLen = strlen(cnew);
        do 
        {
            pos = dst.find(old, pos);
            if (pos == std::string::npos)
                break;
            dst = dst.replace(pos, 1, cnew);
            pos += repLen;
        } while (true);
        return dst;
    }

    std::string replace(const std::string &src, const char *old, const char *cnew)
    {
        const std::size_t oldLen = strlen(old);
        std::string dst = src;
        std::size_t pos = 0u;
        std::size_t repLen = strlen(cnew);
        do
        {
            pos = dst.find(old, pos);
            if (pos == std::string::npos)
                break;
            dst = dst.replace(pos, oldLen, cnew);
            pos += repLen;
        } while (true);
        return dst;
    }

    std::string regexEscapeChar(const char *s)
    {
        // note backslash is FIRST character
        static char regexChars[16] = { '\\', '[', ']', '^', '-',
                                       '&', '$', '?', '*', '+',
                                       '{', '}', ',', '(', ')',
                                       '|', };

        std::string retval(s);
        char escaped[3] {'\\', '\0', '\0'};
        for (std::size_t i = 0; i < 16; i++) {
            escaped[1] = regexChars[i];
            retval = replace(retval, regexChars[i], escaped);
        }

        return retval;
    }
        }

AbstractFeatureDataStore2::AbstractFeatureDataStore2(int modificationFlags_, int visibilityFlags_) :
    modificationFlags(modificationFlags_),
    visibilityFlags(visibilityFlags_),
    inBulkModification(0),
    contentChanged(false),
    mutex(TEMT_Recursive)
{}
    
TAKErr AbstractFeatureDataStore2::addOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    // XXX - check for throws
    this->contentChangedListeners.insert(l);
    return TE_Ok;
}

    
TAKErr AbstractFeatureDataStore2::removeOnDataStoreContentChangedListener(OnDataStoreContentChangedListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    this->contentChangedListeners.erase(l);
    return TE_Ok;
}

void AbstractFeatureDataStore2::setContentChanged() NOTHROWS
{
    this->contentChanged = true;
}

void AbstractFeatureDataStore2::dispatchDataStoreContentChangedNoSync(bool force) NOTHROWS
{
    if (this->inBulkModification > 0)
        return;
    if (this->contentChanged || force) {
        this->contentChanged = false;

        std::set<OnDataStoreContentChangedListener *>::iterator it;
        for (it = this->contentChangedListeners.begin(); it != this->contentChangedListeners.end(); it++)
            (*it)->onDataStoreContentChanged(*this);
    }
}

    
TAKErr AbstractFeatureDataStore2::getVisibilitySettingsFlags(int *value) NOTHROWS
{
    *value = this->visibilityFlags;
    return TE_Ok;
}

    
TAKErr AbstractFeatureDataStore2::setFeatureVisible(const int64_t fid, const bool visible) NOTHROWS
{
    if ((this->visibilityFlags&VISIBILITY_SETTINGS_FEATURE) != VISIBILITY_SETTINGS_FEATURE)
        return TE_Unsupported;
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    code = this->setFeatureVisibleImpl(fid, visible);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}
    
TAKErr AbstractFeatureDataStore2::setFeaturesVisible(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    if ((this->visibilityFlags&VISIBILITY_SETTINGS_FEATURE) != VISIBILITY_SETTINGS_FEATURE)
        return TE_Unsupported;
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    code = this->setFeaturesVisibleImpl(params, visible);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

TAKErr AbstractFeatureDataStore2::setFeatureSetVisible(const int64_t setId, const bool visible) NOTHROWS
{
    if ((this->visibilityFlags&VISIBILITY_SETTINGS_FEATURESET) != VISIBILITY_SETTINGS_FEATURESET)
        return TE_Unsupported;
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    code = this->setFeatureSetVisibleImpl(setId, visible);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

TAKErr AbstractFeatureDataStore2::setFeatureSetsVisible(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    if ((this->visibilityFlags&VISIBILITY_SETTINGS_FEATURESET) != VISIBILITY_SETTINGS_FEATURESET)
        return TE_Unsupported;
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    code = this->setFeatureSetsVisibleImpl(params, visible);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::getModificationFlags(int *value) NOTHROWS {
    *value = this->modificationFlags;
    return TE_Ok;
}

    
TAKErr AbstractFeatureDataStore2::beginBulkModification() NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_BULK_MODIFICATIONS);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    code = this->beginBulkModificationImpl();
    if (code == TE_Ok)
        this->inBulkModification++;
    return code;
}

    
TAKErr AbstractFeatureDataStore2::endBulkModification(const bool successful) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_BULK_MODIFICATIONS);
    TE_CHECKRETURN_CODE(code);

    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    if (this->inBulkModification == 0)
        return TE_IllegalState;

    code = this->endBulkModificationImpl(successful);
    if (code == TE_Ok) {
        this->inBulkModification--;
        if (this->inBulkModification == 0)
            this->dispatchDataStoreContentChangedNoSync(false);
    }
    return code;
}

TAKErr AbstractFeatureDataStore2::isInBulkModification(bool *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    *value = (this->inBulkModification > 0);
    return TE_Ok;
}

TAKErr AbstractFeatureDataStore2::insertFeatureSet(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_INSERT);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->insertFeatureSetImpl(featureSet, provider, type, name, minResolution, maxResolution);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeatureSet(const int64_t fsid, const char *name) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_UPDATE | MODIFY_FEATURESET_NAME);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureSetImpl(fsid, name);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_UPDATE | MODIFY_FEATURESET_DISPLAY_THRESHOLDS);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureSetImpl(fsid, minResolution, maxResolution);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeatureSet(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_UPDATE | MODIFY_FEATURESET_NAME | MODIFY_FEATURESET_DISPLAY_THRESHOLDS);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureSetImpl(fsid, name, minResolution, maxResolution);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::deleteFeatureSet(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_DELETE);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->deleteFeatureSetImpl(fsid);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::deleteAllFeatureSets() NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_DELETE);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->deleteAllFeatureSetsImpl();
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::insertFeature(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_INSERT);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->insertFeatureImpl(feature, fsid, name, geom, style, attributes);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeature(const int64_t fid, const char *name) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE | MODIFY_FEATURE_NAME);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureImpl(fid, name);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeature(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE | MODIFY_FEATURE_GEOMETRY);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureImpl(fid, geom);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeature(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE | MODIFY_FEATURE_STYLE);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureImpl(fid, style);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeature(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE | MODIFY_FEATURE_ATTRIBUTES);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureImpl(fid, attributes);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::updateFeature(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_UPDATE | MODIFY_FEATURE_NAME | MODIFY_FEATURE_GEOMETRY | MODIFY_FEATURE_STYLE | MODIFY_FEATURE_ATTRIBUTES);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->updateFeatureImpl(fid, name, geom, style, attributes);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::deleteFeature(const int64_t fid) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->deleteFeatureImpl(fid);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

    
TAKErr AbstractFeatureDataStore2::deleteAllFeatures(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    code = this->checkModificationFlags(MODIFY_FEATURESET_FEATURE_DELETE);
    TE_CHECKRETURN_CODE(code);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->deleteAllFeaturesImpl(fsid);
    if (code == TE_Ok)
        this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

TAKErr AbstractFeatureDataStore2::checkModificationFlags(const int capability) NOTHROWS {
    const bool retval = ((this->modificationFlags&capability) == capability);
    if (!retval)
        return TE_Unsupported;
    return TE_Ok;
}

TAKErr AbstractFeatureDataStore2::matches(bool *matched, const char *ctest, const char *value, const char wildcard) NOTHROWS
{
    // NULL cases
    if (!ctest && !value) {
        *matched = true;
        return TE_Ok;
    } else if (!ctest || !value) {
        *matched = false;
        return TE_Ok;
    }

    std::string test = ctest;
    if (test.find(wildcard) == std::string::npos) {
        *matched = (strcmp(value, ctest) == 0);
        return TE_Ok;
    }

    const char scratch[3] = { wildcard, wildcard, '\0' };

    while (test.find(scratch) != std::string::npos)
        test = replace(test, scratch, scratch+1);
    test = regexEscapeChar(test.c_str());
    test = replace(test, wildcard, ".*");
    try {
        *matched = std::regex_match(value, std::regex(test));
        return TE_Ok;
    } catch (std::regex_error &e) {
        Logger::log(Logger::Error, "AbstractFeatureDataStore2::matches: failed to construct regular expression from %s", test.c_str());
        return TE_Err;
    }
}

TAKErr AbstractFeatureDataStore2::matches(bool *matched, TAK::Engine::Port::Collection<TAK::Engine::Port::String> &test, const char *value, const char wildcard) NOTHROWS
{
    *matched = false;
    if (test.empty())
        return TE_Ok;

    TAKErr code;
    TAK::Engine::Port::Collection<TAK::Engine::Port::String>::IteratorPtr iter(NULL, NULL);
    code = test.iterator(iter);
    TE_CHECKRETURN_CODE(code);

    TAK::Engine::Port::String arg;
    do {
        code = iter->get(arg);
        if (code != TE_Ok)
            break;
        code = matches(matched, arg, value, wildcard);
        TE_CHECKBREAK_CODE(code);
        if (*matched)
            break;

        code = iter->next();
        if (code != TE_Ok)
            break;
    } while (true);

    return (code == TE_Done) ? TE_Ok : code;
}

TAKErr AbstractFeatureDataStore2::queryFeaturesCount(int *value, FeatureDataStore2 &dataStore, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code;
    FeatureCursorPtr result(NULL, NULL);
    code = dataStore.queryFeatures(result, params);
    TE_CHECKRETURN_CODE(code);
    int v = 0;
    
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);
        v++;
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);
    if (value)
        *value = v;
    
    return code;
}
