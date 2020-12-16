
#include <algorithm>

#include "raster/mobac/MobacMapSourceTilesetSupport.h"

#include "raster/DatasetDescriptor.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/mobac/EquirectangularMobacMapSourceTilesetSupport.h"
#include "raster/mobac/MobacMapSource.h"
#include "raster/mobac/MobacMapSourceFactory.h"
#include "raster/mobac/MobacTileClient.h"
#include "raster/mobac/WebMercatorMobacMapSourceTilesetSupport.h"
#include "raster/tilepyramid/TilesetInfo.h"
#include "db/Database.h"
#include "db/Statement.h"
#include "util/FutureTask.h"
#include "util/IO2.h"

using namespace atakmap::raster::mobac;

using namespace atakmap::db;
using namespace atakmap::raster;
using namespace atakmap::raster::tilepyramid;
using namespace atakmap::renderer;
using namespace atakmap::util;

/*
 protected final MobacMapSource mapSource;
 protected final TilesetInfo tsInfo;
 protected long defaultExpiration;
 protected boolean checkConnectivity;
 protected MobacTileClient client;
 */

atakmap::raster::tilepyramid::TilesetSupport::Spi *const MobacMapSourceTilesetSupport::SPI = createSpi();


namespace
{
    class CachingBitmapLoader : public FutureTaskImpl<Bitmap> {
    public:
        CachingBitmapLoader(MobacTileClient *client, MobacMapSource *source, int latIndex, int lngIndex, int level, bool checkConnectivity/*, BitmapFactory.Options opts*/);
        virtual ~CachingBitmapLoader();
    protected:
        virtual bool runImpl();
    
    private:
        MobacTileClient * const client;
        MobacMapSource * const source;
        const int latIndex;
        const int lngIndex;
        const int level;
        const bool checkConnectivity;
        /*private final BitmapFactory.Options opts;*/
    };
    
    class SpiImpl : public TilesetSupport::Spi {
    public:
        SpiImpl();
    public:
        virtual const char *getName() const;
        virtual TilesetSupport *create(TilesetInfo *tsInfo, AsyncBitmapLoader *bitmapLoader);
    };
}

MobacMapSourceTilesetSupport::MobacMapSourceTilesetSupport(TilesetInfo *info, MobacMapSource *source)
: tsInfo(info),
mapSource(source),
defaultExpiration(strtoll(DatasetDescriptor::getExtraData(*info->getInfo(), "defaultExpiration", "-1"), NULL, 10)),
checkConnectivity(true),
client(nullptr)
{}

MobacMapSourceTilesetSupport::~MobacMapSourceTilesetSupport() { }

void MobacMapSourceTilesetSupport::setOfflineMode(bool offlineOnly)
{
    this->client->setOfflineMode(offlineOnly);
}

bool MobacMapSourceTilesetSupport::isOfflineMode()
{
    return this->client->isOfflineMode();
}

// XXX-- general helpers stuff. Do we have something better, or refactor to share?
namespace  {
    struct FindStringPred {
        FindStringPred(const char *staticStr)
        : str(staticStr) { }
        inline bool operator()(const PGSC::String &item) const {
            return strcmp(item, str) == 0;
        }
        const char *str;
    };

    static bool vectorContains(const std::vector<PGSC::String> &vec, const char *str) {
        return std::find_if(vec.begin(), vec.end(), FindStringPred(str)) != vec.end();
    }
}

void MobacMapSourceTilesetSupport::init()
{
    const char *offlineCache = this->tsInfo->getInfo()->getExtraData("offlineCache");
    TAK::Engine::Port::String pathStr;
    
    //XXX-- emergency fix for iOS cache database open failure
#if 0
    if (offlineCache != nullptr) {
        pathStr = offlineCache;
        TAK::Engine::Util::File_getRuntimePath(pathStr);
        
        if (!atakmap::util::isFile(pathStr.get())) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "MobacMapSourceTilesetSupport: offlineCache database does not exist");
        }
        
        std::auto_ptr<Database> database(db::openDatabase(pathStr.get()));
        std::vector<PGSC::String> tables = db::getTableNames(*database);
        
        if (!vectorContains(tables, "tiles")) {
            database->execute("CREATE TABLE tiles (key INTEGER PRIMARY KEY, provider TEXT, tile BLOB)");
        }
        if (!vectorContains(tables, "ATAK_catalog")) {
            database->execute("CREATE TABLE ATAK_catalog (key INTEGER PRIMARY KEY, access INTEGER, expiration INTEGER, size INTEGER)");
        }
        if (!vectorContains(tables, "ATAK_metadata")) {
            database->execute("CREATE TABLE ATAK_metadata (key TEXT, value TEXT)");
            std::auto_ptr<Statement> stmt(database->compileStatement("INSERT INTO ATAK_metadata (key, value) VALUES('srid', ?)"));
            stmt->bind(1, this->tsInfo->getInfo()->getSpatialReferenceID());
            stmt->execute();
        }
    }
#endif
    
    this->mapSource->clearAuthFailed();
    this->client = std::unique_ptr<MobacTileClient>(new MobacTileClient(this->mapSource.get(), pathStr.get()));
}

void MobacMapSourceTilesetSupport::release()
{
    if (this->client != nullptr)
        this->client->close();
}

void MobacMapSourceTilesetSupport::start()
{
    this->checkConnectivity = true;
}

void MobacMapSourceTilesetSupport::stop()
{
}

FutureTask<Bitmap> MobacMapSourceTilesetSupport::getTile(int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/)
{
    FutureTask<Bitmap> r(PGSC::RefCountablePtr<FutureTaskImpl<Bitmap>>(new CachingBitmapLoader(this->client.get(),
                                                                                               this->mapSource.get(),
                                                                                               latIndex, lngIndex,
                                                                                               level,
                                                                                               this->checkConnectivity/*, opts*/)));
    this->checkConnectivity = false;
    return r;
}

TilesetSupport::Spi *MobacMapSourceTilesetSupport::createSpi()
{
    return new SpiImpl();
}

namespace
{
    CachingBitmapLoader::CachingBitmapLoader(MobacTileClient *c, MobacMapSource *src, int lat, int lng, int lvl, bool checkConn/*, BitmapFactory.Options opts*/) :
    client(c),
    source(src),
    latIndex(lat),
    lngIndex(lng),
    level(lvl),
    checkConnectivity(checkConn)
    {}
    
    CachingBitmapLoader::~CachingBitmapLoader() {
        
    }
    
    bool CachingBitmapLoader::runImpl() {
        
        if (this->checkConnectivity)
            this->source->checkConnectivity();
        
        Bitmap bitmap;
        if (this->client->loadTile(&bitmap, this->level, this->lngIndex, this->latIndex/*, this.opts*/, nullptr)) {
            return this->completeProcessing(bitmap);
        } else {
            this->setState(SharedState::Error);
        }
        
        return false;
    }
    
    SpiImpl::SpiImpl() {}
    
    const char *SpiImpl::getName() const {
        return "mobac";
    }
    
    TilesetSupport *SpiImpl::create(TilesetInfo *info, AsyncBitmapLoader *loader) {
        atakmap::util::Logger::log(atakmap::util::Logger::Debug, "creating Mobac TilesetSupport for %s", info->getInfo()->getURI());
        MobacMapSource *mapSource = MobacMapSourceFactory::create(info->getInfo()->getURI());
        
        if (mapSource == nullptr)
            return nullptr;
        
        std::unique_ptr<MobacMapSourceTilesetSupport> impl(new MobacMapSourceTilesetSupport(info, mapSource));
        if (info->getInfo()->getSpatialReferenceID() == 4326)
            return new EquirectangularMobacMapSourceTilesetSupport(info, loader, impl.release());
        else if (info->getInfo()->getSpatialReferenceID() == 3857)
            return new WebMercatorMobacMapSourceTilesetSupport(info, loader, impl.release());
        else
            return nullptr;
    }
}
