
#include "feature/PersistentFeatureDataStore2.h"
#include "raster/osm/OSMUtils.h"
#include "db/Database2.h"
#include "db/BindArgument.h"
#include "db/CursorWrapper2.h"
#include "db/Statement2.h"
#include "db/WhereClauseBuilder2.h"
#include "feature/AbstractFeatureDataStore2.h"
#include "feature/FeatureCursor2.h"
#include "feature/FeatureDefinition2.h"
#include "feature/FeatureSetCursor2.h"
#include "port/Platform.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"

using namespace TAK::Engine::Feature;

PersistentFeatureDataStore2::~PersistentFeatureDataStore2() NOTHROWS {
    
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::setFeatureVisibleImpl(fid, visible);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::setFeaturesVisibleImpl(params, visible);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::setFeatureSetVisibleImpl(fsid, visible);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::setFeatureSetsVisibleImpl(params, visible);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::insertFeatureSetImpl(FeatureSetPtr_const *ref, const char *provider, const char *type, const char *name, double minResolution, double maxResolution) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::insertFeatureSetImpl(ref, provider, type, name, minResolution, maxResolution);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureSetImpl(fsid, name);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureSetImpl(fsid, minResolution, maxResolution);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureSetImpl(fsid, name, minResolution, maxResolution);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::deleteFeatureSetImpl(const int64_t fsid) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::deleteFeatureSetImpl(fsid);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::deleteAllFeatureSetsImpl() NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::deleteAllFeatureSetsImpl();
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::insertFeatureImpl(FeaturePtr_const *ref, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::insertFeatureImpl(ref, fsid, name, geom, style, attributes);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::insertFeatureImpl(FeaturePtr_const *ref, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::insertFeatureImpl(ref, fsid, def);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureImpl(fid, name);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureImpl(fid, geom);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureImpl(fid, style);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureImpl(fid, attributes);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::updateFeatureImpl(fid, name, geom, style, attributes);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::deleteFeatureImpl(const int64_t fid) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::deleteFeatureImpl(fid);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS {
    Util::TAKErr code = FeatureSetDatabase::deleteAllFeaturesImpl(fsid);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}

TAK::Engine::Util::TAKErr PersistentFeatureDataStore2::updateFeatureSet(const int64_t fsid, const char *name, const char *type, const double minResolution, const double maxResolution) NOTHROWS
{
    Util::TAKErr code = FeatureSetDatabase::updateFeatureSetImpl(fsid, name, type, minResolution, maxResolution);
    if (code == Util::TE_Ok)
        this->setContentChanged();
    return code;
}


