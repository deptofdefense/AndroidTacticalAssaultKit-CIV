#include "formats/mbtiles/MVTFeatureDataSource.h"

#include "db/Database2.h"
#include "formats/mbtiles/MBTilesInfo.h"
#include "raster/osm/OSMUtils.h"
#include "util/Memory.h"

using namespace TAK::Engine::Formats::MBTiles;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Util;

namespace
{
    class ContentImpl : public FeatureDataSource2::Content
    {
    public :
        ContentImpl(DatabasePtr &&database) NOTHROWS;
        ~ContentImpl() NOTHROWS;
    public :
        virtual const char *getType() const NOTHROWS;
        virtual const char *getProvider() const NOTHROWS;
        virtual TAKErr moveToNextFeature() NOTHROWS;
        virtual TAKErr moveToNextFeatureSet() NOTHROWS;
        virtual TAKErr get(FeatureDefinition2 **feature) const NOTHROWS;
        virtual TAKErr getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS;
        virtual TAKErr getFeatureSetVisible(bool *visible) const NOTHROWS;
        virtual TAKErr getMinResolution(double *value) const NOTHROWS;
        virtual TAKErr getMaxResolution(double *value) const NOTHROWS;
		virtual TAKErr getVisible(bool* visible) const NOTHROWS;
    private :
        DatabasePtr database;
    };
}

MVTFeatureDataSource::MVTFeatureDataSource() NOTHROWS
{}
MVTFeatureDataSource::~MVTFeatureDataSource() NOTHROWS
{}

TAKErr MVTFeatureDataSource::parse(FeatureDataSource2::ContentPtr& content, const char* file) NOTHROWS
{
    TAKErr code(TE_Ok);
    DatabasePtr db(nullptr, nullptr);
    if (Databases_openDatabase(db, file, true) != TE_Ok)
        return TE_InvalidArg;
    MBTilesInfo info;
    if (MBTilesInfo_get(&info, *db) != TE_Ok)
        return TE_InvalidArg;
    if (info.tileset != MBTilesInfo::Vector)
        return TE_InvalidArg;
    content = FeatureDataSource2::ContentPtr(new(std::nothrow) ContentImpl(std::move(db)), Memory_deleter_const<FeatureDataSource2::Content, ContentImpl>);
    if (!content)
        return TE_OutOfMemory;
    return code;
}
const char* MVTFeatureDataSource::getName() const NOTHROWS
{
    return "MVT";
}
int MVTFeatureDataSource::parseVersion() const NOTHROWS
{
    return 1;
}

namespace
{
    ContentImpl::ContentImpl(DatabasePtr &&database_) NOTHROWS :
        database(std::move(database_))
    {}
    ContentImpl::~ContentImpl() NOTHROWS
    {}
    const char *ContentImpl::getType() const NOTHROWS
    {
        return "MVT";
    }
    const char *ContentImpl::getProvider() const NOTHROWS
    {
        return "MVT";
    }
    TAKErr ContentImpl::moveToNextFeature() NOTHROWS
    {
        return TE_Done;
    }
    TAKErr ContentImpl::moveToNextFeatureSet() NOTHROWS
    {
        return TE_Done;
    }
    TAKErr ContentImpl::get(FeatureDefinition2** feature) const NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr ContentImpl::getFeatureSetName(TAK::Engine::Port::String &name) const NOTHROWS
    {
        return TE_Unsupported;
    }
    TAKErr ContentImpl::getFeatureSetVisible(bool *visible) const NOTHROWS
    {
        *visible = true;
        return TE_Ok;
    }
    TAKErr ContentImpl::getMinResolution(double *value) const NOTHROWS
    {
        *value = atakmap::raster::osm::OSMUtils::mapnikTileResolution(0);
        return TE_Ok;
    }
    TAKErr ContentImpl::getMaxResolution(double* value) const NOTHROWS
    {
        *value = atakmap::raster::osm::OSMUtils::mapnikTileResolution(21);
        return TE_Ok;
    }
	TAKErr ContentImpl::getVisible(bool* visible) const NOTHROWS
    {
        *visible = true;
        return TE_Ok;
    }
}
