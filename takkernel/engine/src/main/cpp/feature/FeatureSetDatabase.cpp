#include "feature/FeatureSetDatabase.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Util;

#define DEFAULT_MODIFICATION_FLAGS  \
        (FeatureDataStore2::MODIFY_BULK_MODIFICATIONS | \
         FeatureDataStore2::MODIFY_FEATURESET_INSERT | \
         FeatureDataStore2::MODIFY_FEATURESET_DELETE | \
         FeatureDataStore2::MODIFY_FEATURESET_UPDATE | \
         FeatureDataStore2::MODIFY_FEATURESET_DISPLAY_THRESHOLDS | \
         FeatureDataStore2::MODIFY_FEATURESET_READONLY | \
         FeatureDataStore2::MODIFY_FEATURESET_NAME | \
         FeatureDataStore2::MODIFY_FEATURESET_FEATURE_INSERT | \
         FeatureDataStore2::MODIFY_FEATURESET_FEATURE_DELETE | \
         FeatureDataStore2::MODIFY_FEATURESET_FEATURE_UPDATE | \
         FeatureDataStore2::MODIFY_FEATURE_NAME | \
         FeatureDataStore2::MODIFY_FEATURE_GEOMETRY | \
         FeatureDataStore2::MODIFY_FEATURE_STYLE | \
         FeatureDataStore2::MODIFY_FEATURE_ATTRIBUTES)

#define DEFAULT_VISIBILITY_FLAGS  \
        (FeatureDataStore2::VISIBILITY_SETTINGS_FEATURE | \
         FeatureDataStore2::VISIBILITY_SETTINGS_FEATURESET)

FeatureSetDatabase::FeatureSetDatabase() NOTHROWS :
    FDB(DEFAULT_MODIFICATION_FLAGS, DEFAULT_VISIBILITY_FLAGS)
{}

FeatureSetDatabase::FeatureSetDatabase(int modificationFlags, int visibilityFlags) NOTHROWS :
    FDB(modificationFlags, visibilityFlags)
{}

TAKErr FeatureSetDatabase::insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code(TE_Ok);
    FDB::Builder inserter(*this);
    code = inserter.insertFeatureSet(fsid, provider, type, name);
    TE_CHECKRETURN_CODE(code);
    code = inserter.updateFeatureSet(fsid, minResolution, maxResolution);
    TE_CHECKRETURN_CODE(code);

    return code;
}


    /**************************************************************************/
    // Builder

#if 0
class FeatureSetDatabase::Builder
{
public:
    class BulkInsertion;
public:
    Builder() NOTHROWS;
public:
    Util::TAKErr create(const char *databaseFile) NOTHROWS;
public:
    Util::TAKErr insertFeature(const int64_t fsid, const FeatureDefinition2 &def) NOTHROWS;
    Util::TAKErr insertFeature(const int64_t fsid, const char *name, const atakmap::feature::Geometry &geometry, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attribs) NOTHROWS;
    Util::TAKErr insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name) NOTHROWS;
    Util::TAKErr insertFeatureSet(const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS;
    Util::TAKErr updateFeatureSet(const int64_t fsid, const bool visible, const double minResolution, const double maxResolution) NOTHROWS;
    Util::TAKErr deleteFeatureSet(const int64_t fsid) NOTHROWS;
    Util::TAKErr createIndices() NOTHROWS;
private:
    std::unique_ptr<FDB::Builder> impl;
};



#endif

FeatureSetDatabase::Builder::Builder() NOTHROWS
{}

TAKErr FeatureSetDatabase::Builder::create(const char *path) NOTHROWS 
{
    int dbVersionIgnored;
    return create(path, &dbVersionIgnored);
}

TAKErr FeatureSetDatabase::Builder::create(const char *path, int* dbVersion) NOTHROWS
{
    TAKErr code;
    if (db.get())
        return TE_IllegalState;

    std::unique_ptr<FeatureSetDatabase> fsdb(new FeatureSetDatabase());
    code = fsdb->open(path, dbVersion, false);
    TE_CHECKRETURN_CODE(code);

    impl.reset(new FDB::Builder(*fsdb));
    db.reset(fsdb.release());

    return code;
}

TAKErr FeatureSetDatabase::Builder::close() NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    TAKErr code(TAKErr::TE_Ok);

    impl.release();
    db->close();
    db.release();

    return code;
}

TAKErr FeatureSetDatabase::Builder::beginBulkInsertion() NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->beginBulkInsertion();
}

TAKErr FeatureSetDatabase::Builder::endBulkInsertion(const bool successful) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->endBulkInsertion(successful);
}

TAKErr FeatureSetDatabase::Builder::insertFeature(int64_t* fid, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->insertFeature(fid, fsid, def);
}

TAKErr FeatureSetDatabase::Builder::insertFeature(const int64_t fsid, const char *name, const atakmap::feature::Geometry &geometry, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attribs) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->insertFeature(fsid, name, geometry, altitudeMode, extrude, style, attribs);
}

TAKErr FeatureSetDatabase::Builder::setFeatureSetVisible(const int64_t fsid, const bool visible) NOTHROWS 
{
    if (!db.get()) return TE_IllegalState;
    return impl->setFeatureSetVisible(fsid, visible);
}

TAKErr FeatureSetDatabase::Builder::setFeatureVisible(const int64_t fid, const bool visible)
{
	if (!db.get())
		return TE_IllegalState;
	return impl->setFeatureVisible(fid, visible);
}

TAKErr FeatureSetDatabase::Builder::setFeatureVersion(const int64_t fid, const int64_t version) 
{
    if (!db.get()) 
        return TE_IllegalState;
    return impl->setFeatureVersion(fid, version);
}

TAKErr FeatureSetDatabase::Builder::insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->insertFeatureSet(fsid, provider, type, name);
}

TAKErr FeatureSetDatabase::Builder::insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name, const bool readOnly) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->insertFeatureSet(fsid, provider, type, name, readOnly);
}

TAKErr FeatureSetDatabase::Builder::insertFeatureSet(int64_t *fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->insertFeatureSet(fsid, provider, type, name, minResolution, maxResolution);
}

TAKErr FeatureSetDatabase::Builder::insertFeatureSet(int64_t *fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution, const bool readOnly) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->insertFeatureSet(fsid, provider, type, name, minResolution, maxResolution, readOnly);
}

TAKErr FeatureSetDatabase::Builder::updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->updateFeatureSet(fsid, minResolution, maxResolution);
}

TAKErr FeatureSetDatabase::Builder::deleteFeatureSet(const int64_t fsid) NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->deleteFeatureSet(fsid);
}

TAKErr FeatureSetDatabase::Builder::createIndices() NOTHROWS
{
    if (!db.get())
        return TE_IllegalState;
    return impl->createIndices();
}

FeatureSetDatabase::Builder::BulkInsertion::BulkInsertion(FeatureSetDatabase::Builder &builder_) NOTHROWS :
    builder(builder_),
    valid(builder.beginBulkInsertion()),
    successful(false)
{}

FeatureSetDatabase::Builder::BulkInsertion::~BulkInsertion() NOTHROWS
{
    if (valid == TE_Ok)
        builder.endBulkInsertion(successful);
}

TAKErr FeatureSetDatabase::Builder::BulkInsertion::checkInit() const NOTHROWS
{
    return valid;
}

TAKErr FeatureSetDatabase::Builder::BulkInsertion::setSuccessful() NOTHROWS
{
    if (valid != TE_Ok)
        return TE_IllegalState;
    successful = true;
    return TE_Ok;
}
