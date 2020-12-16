#include "feature/AbstractDataSourceFeatureDataStore2.h"

#include <memory>

#include "thread/Lock.h"

//#include "feature/FeatureDataSource.h"
#include "util/Logging.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::util;

typedef atakmap::feature::FeatureDataSource FeatureDataSource_Legacy;
typedef atakmap::feature::Geometry Geometry_Legacy;
typedef atakmap::feature::Style Style_Legacy;
typedef atakmap::util::AttributeSet AttributeSet_Legacy;


#define VISIBILITY_FLAGS (FeatureDataStore2::VISIBILITY_SETTINGS_FEATURE|FeatureDataStore2::VISIBILITY_SETTINGS_FEATURESET)
#define MODIFICATION_FLAGS (FeatureDataStore2::MODIFY_BULK_MODIFICATIONS| \
                            FeatureDataStore2::MODIFY_FEATURESET_DELETE | \
                            FeatureDataStore2::MODIFY_FEATURESET_UPDATE | \
                            FeatureDataStore2::MODIFY_FEATURESET_DISPLAY_THRESHOLDS)


AbstractDataSourceFeatureDataStore2::AbstractDataSourceFeatureDataStore2(int visFlags) NOTHROWS :
    AbstractFeatureDataStore2(MODIFICATION_FLAGS, visFlags)
{}

TAKErr AbstractDataSourceFeatureDataStore2::contains(bool *value, const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    return this->containsImpl(value, file);
}

TAKErr AbstractDataSourceFeatureDataStore2::getFile(TAK::Engine::Port::String &path, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    return this->getFileNoSync(path, fsid);
}
  
TAKErr AbstractDataSourceFeatureDataStore2::add(const char *file) NOTHROWS
{
    return this->add(file, NULL);
}

       
TAKErr AbstractDataSourceFeatureDataStore2::add(const char *file, const char *hint) NOTHROWS
{
    return this->addNoSync(file, hint, true);
}

TAKErr AbstractDataSourceFeatureDataStore2::addNoSync(const char *file, const char *hint, const bool notify) NOTHROWS
{
    std::auto_ptr<FeatureDataSource_Legacy::Content> content(NULL);

    content.reset(FeatureDataSource_Legacy::parse(file, hint));
    Logger::log(Logger::Debug, "ADD %s (%s) -> %s",file, hint, content->getFeatureSetName());
    if (content.get() == NULL)
        TE_CHECKLOGRETURN_CODE(TE_Err, Logger::Warning, "Failed to create FeatureDataSource for %s", file);

    TAKErr code;
    {
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        TE_CHECKRETURN_CODE(code);
        bool v;
        code = this->contains(&v, file);
        TE_CHECKRETURN_CODE(code);
        if (v)
            return TE_InvalidArg;
        code = this->addImpl(file, *content);
        TE_CHECKRETURN_CODE(code);
        if (notify)
            this->dispatchDataStoreContentChangedNoSync(true);
    }
    return code;
}

TAKErr AbstractDataSourceFeatureDataStore2::remove(const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    return this->removeNoSync(file, true);
}

TAKErr AbstractDataSourceFeatureDataStore2::removeNoSync(const char *file, const bool notify) NOTHROWS
{
    TAKErr code;
    bool b;
    code = this->containsImpl(&b, file);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_Ok;
    code = this->removeImpl(file);
    TE_CHECKRETURN_CODE(code);
    if (notify)
        this->dispatchDataStoreContentChangedNoSync(true);
    return code;
}
    
TAKErr AbstractDataSourceFeatureDataStore2::update(const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    bool b;
    code = this->containsImpl(&b, file);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_InvalidArg;
    return this->updateImpl(file);
}

TAKErr AbstractDataSourceFeatureDataStore2::update(const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    TAK::Engine::Port::String toUpdate(NULL);
    code = this->getFile(toUpdate, fsid);
    TE_CHECKRETURN_CODE(code);
    return this->updateImpl(toUpdate);
}

TAKErr AbstractDataSourceFeatureDataStore2::updateImpl(const char *file) NOTHROWS
{
    TAKErr code;
    code = this->removeNoSync(file, false);
    TE_CHECKRETURN_CODE(code);

    // XXX - specify provider from existing feature sets?
    return this->addNoSync(file, NULL, true);
}


TAKErr AbstractDataSourceFeatureDataStore2::insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}


TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::deleteFeatureSetImpl(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    TAK::Engine::Port::String path(NULL);
    code = this->getFileNoSync(path, fsid);
    TE_CHECKRETURN_CODE(code);
    return this->remove(path);
}


TAKErr AbstractDataSourceFeatureDataStore2::insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}


TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const Geometry_Legacy &geom) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const Style_Legacy *style) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const AttributeSet_Legacy &attributes) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name, const Geometry_Legacy &geom, const Style_Legacy *style, const AttributeSet_Legacy &attributes) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::deleteFeatureImpl(const int64_t fsid) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr AbstractDataSourceFeatureDataStore2::deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS
{
    return TE_Unsupported;
}
