
#include <stdio.h>
#include <iomanip>

#include "raster/tilepyramid/SimpleUriTilesetSupport.h"

#include "raster/DatasetDescriptor.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/osm/OSMTilesetSupport.h"
#include "raster/osm/OSMUtils.h"
#include "raster/tilepyramid/EquirectangularTilesetSupport.h"
#include "raster/tilepyramid/TilesetInfo.h"
//#include "raster/tilepyramid/TilesetResolver.h"
#include "raster/tilepyramid/TilesetSupport.h"
#include "renderer/AsyncBitmapLoader.h"
#include "db/Database.h"
#include "util/FutureTask.h"
#include "util/URILoader.h"
#include "renderer/BitmapFactory.h"

using namespace atakmap::raster::tilepyramid;

using namespace atakmap::db;
using namespace atakmap::raster;
using namespace atakmap::raster::osm;
using namespace atakmap::renderer;
using namespace atakmap::util;

TilesetSupport::Spi * const SimpleUriTilesetSupport::SPI = createSpi();

namespace
{
    typedef std::string (*ResolveFunction)(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level);
    
    std::string NASA_WW(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level);
    std::string NASAWW_LARGE_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level);
    std::string TMS_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level);
    std::string OSMDROID_ZIP_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level);
    std::string OSMDROID_SQLITE_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level);
    std::string NETT_WARRIOR_SQLITE_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level);
    
    class EquirectangularTilesetFilter : public EquirectangularTilesetSupport {
    public:
        EquirectangularTilesetFilter(TilesetInfo *tsInfo, AsyncBitmapLoader *loader, ResolveFunction filter);
    public:
        virtual void start() override;
        virtual void stop() override;
        virtual void init() override;
        virtual void release() override;
        virtual FutureTask<Bitmap> getTile(int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/) override;
    private :
        TilesetInfo * const tsInfo;
        const ResolveFunction resolver;
    };
    
    class OSMDroidFilter : public OSMTilesetSupport
    {
    public:
        OSMDroidFilter(TilesetInfo *tsInfo, AsyncBitmapLoader *loader, ResolveFunction filter);
    public:
        virtual void start() override;
        virtual void stop() override;
        virtual void init() override;
        virtual void release() override;
        virtual FutureTask<Bitmap> getTile(int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/) override;
    private:
        TilesetInfo * const tsInfo;
        const ResolveFunction resolver;
    };
    
    class SpiImpl : public TilesetSupport::Spi
    {
    public:
        SpiImpl();
    public:
        virtual const char *getName() const;
        virtual TilesetSupport *create(TilesetInfo *tsInfo, AsyncBitmapLoader *bitmapLoader);
    };
    
    Future<Bitmap> getTileImpl(AsyncBitmapLoader *bitmapLoader, TilesetInfo *tsInfo, ResolveFunction *resolver, int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/);
    void initImpl(AsyncBitmapLoader *loader, TilesetInfo *tsInfo);
    void releaseImpl(AsyncBitmapLoader *loader, TilesetInfo *tsInfo);
    
}

/**************************************************************************/

SimpleUriTilesetSupport::SimpleUriTilesetSupport()
{}

SimpleUriTilesetSupport::~SimpleUriTilesetSupport() { }

TilesetSupport::Spi *SimpleUriTilesetSupport::createSpi() {
    static SpiImpl impl;
    return &impl;
}

namespace {
    class BitmapLoaderListener : public FutureTaskImpl<Bitmap> {
    public:
        BitmapLoaderListener(std::string uri)
        : uri(uri) { }
        
        virtual ~BitmapLoaderListener() { }
        
        virtual bool runImpl() {
            std::unique_ptr<DataInput> input(atakmap::util::URILoader::openURI(uri.c_str(), nullptr));
            if (input) {
                Bitmap bitmap;
                BitmapFactory::DecodeResult result = BitmapFactory::decode(*input, bitmap, nullptr);
                if (result == BitmapFactory::Success) {
                    this->completeProcessing(bitmap);
                } else {
                    std::stringstream ss;
                    ss << "bitmap decode failed for " << uri;
                    this->completeProcessingWithError(ss.str().c_str());
                }
            } else {
                std::stringstream ss;
                ss << "no protocol for " << uri;
                this->completeProcessingWithError(ss.str().c_str());
            }
            
            return true;
        }
        
    private:
        std::string uri;
    };
    
    FutureTask<Bitmap> getTileImpl(AsyncBitmapLoader *bitmapLoader, TilesetInfo *tsInfo, ResolveFunction resolver, int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/)
    {
        /*struct OneOff : public OneOffListener<Bitmap>, public AsyncBitmapLoader::Listener {
            virtual ~OneOff() { }
            
            virtual void bitmapLoadComplete(int jobid, AsyncBitmapLoader::ERRCODE errcode, Bitmap b) {
                if (errcode != AsyncBitmapLoader::BITMAP_OK) {
                    this->setListenerResult(b);
                } else {
                    this->setListenerError();
                }
                this->deleteListener();
            }
        };
        std::pair<Future<Bitmap>, OneOff *> oneOff = Promise<Bitmap>::createOneOff<OneOff>();
        bitmapLoader->loadBitmap(resolver(tsInfo, latIndex, lngIndex, level), oneOff.second);
        return oneOff.first;*/
        
        std::string uri = resolver(tsInfo, latIndex, lngIndex, level);
        BitmapLoaderListener *callback = new BitmapLoaderListener(uri);
        FutureTask<Bitmap> retval = FutureTask<Bitmap>(PGSC::RefCountablePtr<FutureTaskImpl<Bitmap>>(callback));
        bitmapLoader->loadBitmap(retval);
        return retval;
    }
    
    void initImpl(AsyncBitmapLoader *loader, TilesetInfo *tsInfo)
    {
        /*const char *uri = tsInfo->getInfo()->getURI();
        if (TilesetInfo::isZipArchive(uri))
   //         AsyncBitmapLoader::mountArchive(uri);
#if 0
        else if (Databases::isSQLiteDatabase(uri))
#else
            // XXX - file is locked from reading using System.Data.SQLite???
            else if (uri->ToLower()->EndsWith(".sqlite"))
#endif
                AsyncBitmapLoader::mountDatabase(uri);*/
    }
    
    void releaseImpl(AsyncBitmapLoader *loader, TilesetInfo *tsInfo)
    {
        /*String * const uri = tsInfo->getInfo()->getURI();
        if (TilesetInfo::isZipArchive(uri))
            AsyncBitmapLoader::unmountArchive(uri);
#if 0
        else if (Databases::isSQLiteDatabase(uri))
#else
            // XXX - file is locked from reading using System.Data.SQLite???
            else if (uri->ToLower()->EndsWith(".sqlite"))
#endif
                AsyncBitmapLoader::unmountDatabase(uri);*/
    }
    
    /**
     * Dynamic string number printf
     */
    int dsnprintf(char **buf, size_t n, const char *fmt, ...) {
        
        va_list args;
        va_start (args, fmt);
        int neededLength = vsnprintf(NULL, 0, fmt, args);
        va_end (args);
        
        char *cleanupBuf = NULL;
        if (neededLength > n) {
            cleanupBuf = (char *)malloc(neededLength + 1);
            *buf = cleanupBuf;
        }
        
        va_start(args, fmt);
        int r = vsnprintf(*buf, neededLength + 1, fmt, args);
        va_end(args);
        *buf = cleanupBuf;
        return r;
    }
    
    std::string NASAWW_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level)
    {
        const ImageDatasetDescriptor *info = tsInfo->getInfo();
        
        const char *arc = "";
        const char *arcBang = "";
        if (tsInfo->isArchive()) {
            arc = "arc:";
            arcBang = "!";
        }
        
        char buf[200];
        char *dbuf = buf;
        dsnprintf(&dbuf, sizeof(buf), "%s%s%s/%d/%04d/%04d%s", arc, info->getURI(), arcBang, level, latIndex, lngIndex, tsInfo->getImageExt());
        std::string result = dbuf;
        if (dbuf != buf) free(dbuf);
        return result;
    }
    
    std::string NASAWW_LARGE_RESOLVER(TilesetInfo *info, int latIndex, int lngIndex, int level)
    {
        /*int zeroLatIndex = latIndex, zeroLngIndex = lngIndex;
        int baseLevel = level;
        String *archiveName = nullptr;
        
        if (level != 0) {
            // resolve the lat and lon index to the first level to determine
            // what archive file to use
            
            while (baseLevel > 0) {
                zeroLatIndex = zeroLatIndex / 2;
                zeroLngIndex = zeroLngIndex / 2;
                baseLevel--;
            }
        }
        
        // XXX -
        archiveName = "";// info->_zeroDirectoryLookup.get(zeroLat);
        
        String *parentDirectory = Directory::GetParent(info->getInfo()->getURI())->FullName;
        String *resolvedArchive = Path::Combine(parentDirectory, archiveName);
        StringBuilder sb(resolvedArchive);
        if (info->isArchive()) {
            sb.Insert(0, "arc:");
            sb.Append("!");
        }
        
        String * const NASAWW_INDEX_FORMAT = "0000";
        
        sb.Append("/");
        sb.Append(level);
        sb.Append("/");
        sb.Append(latIndex.ToString(NASAWW_INDEX_FORMAT));
        sb.Append("/");
        sb.Append(latIndex.ToString(NASAWW_INDEX_FORMAT));
        sb.Append("_");
        sb.Append(lngIndex.ToString(NASAWW_INDEX_FORMAT));
        sb.Append(info->getImageExt());
        
        return sb.ToString();*/
        return "";
    }
    
    std::string TMS_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level)
    {
        const ImageDatasetDescriptor *info = tsInfo->getInfo();
        int tmsLatIndex = (1 << level) - latIndex - 1;
        
        const char *arc = "";
        const char *arcBang = "";
        if (tsInfo->isArchive()) {
            arc = "arc:";
            arcBang = "!";
        }
        
        char buf[200];
        char *dbuf = buf;
        dsnprintf(&dbuf, sizeof(buf), "%s%s%s/%d/%d/%d%s", arc, info->getURI(), arcBang, level, lngIndex, tmsLatIndex, tsInfo->getImageExt());
        std::string result = dbuf;
        if (dbuf != buf) free(dbuf);
        return result;
    }
    
    std::string OSMDROID_ZIP_RESOLVER(TilesetInfo *tsInfo, int latIndex, int lngIndex, int level)
    {
        const int osmLevel = level + atoi(DatasetDescriptor::getExtraData(*tsInfo->getInfo(), "levelOffset", "0"));
        const int osmLatIndex = (1 << osmLevel) - latIndex - 1;
        
        const char *arc = "";
        const char *arcBang = "";
        if (tsInfo->isArchive()) {
            arc = "arc:";
            arcBang = "!";
        }
        
        char buf[200];
        char *dbuf = buf;
        dsnprintf(&dbuf, sizeof(buf), "%s%s%s/%d/%d/%d%s", arc, tsInfo->getInfo()->getURI(), arcBang, osmLevel, lngIndex, osmLatIndex,
                  tsInfo->getImageExt());
        std::string  result = dbuf;
        if (dbuf != buf) free(dbuf);
        return result;
    }
    
    std::string OSMDROID_SQLITE_RESOLVER(TilesetInfo *info, int latIndex, int lngIndex, int level)
    {
        const int osmLevel = level + atoi(DatasetDescriptor::getExtraData(*info->getInfo(), "levelOffset", "0"));
        const int osmLatIndex = (1 << osmLevel) - latIndex - 1;
        
        int64_t sqLiteIndex = OSMUtils::getOSMDroidSQLiteIndex(osmLevel, lngIndex, osmLatIndex);
        
        char buf[200];
        char *dbuf = buf;
        dsnprintf(&dbuf, sizeof(buf), "sqlite://%s?query=SELECT%%20tile%%20FROM%%20tiles%%20WHERE%%20key%%20=%%20%lld",
                  info->getInfo()->getURI(), sqLiteIndex);
        std::string  result = buf;
        if (dbuf != buf) free(dbuf);
        return result;
    }
    
    std::string NETT_WARRIOR_SQLITE_RESOLVER(TilesetInfo *info, int latIndex, int lngIndex, int level)
    {
        const int osmLevel = level + atoi(DatasetDescriptor::getExtraData(*info->getInfo(), "levelOffset", "0"));
        
        char buf[200];
        char *dbuf = buf;
        dsnprintf(&dbuf, sizeof(buf), "sqlite://%s?query="
                                      "SELECT%%20tile%%20FROM%%20tiles%%20"
                                      "WHERE%%20zoom_level%%20=%%20%ld"
                                      "%%20AND%%20tile_column%%20=%%20%d"
                                      "%%20AND%%20tile_row%%20=%%20%d",
                  info->getInfo()->getURI(), osmLevel, lngIndex, latIndex);
        std::string  result = buf;
        if (dbuf != buf) free(dbuf);
        return result;
    }
    
    /*************************************************************************/
    // EquirectangularTilesetFilter
    
    EquirectangularTilesetFilter::EquirectangularTilesetFilter(TilesetInfo *info, AsyncBitmapLoader *loader, ResolveFunction filter)
    : EquirectangularTilesetSupport(info, loader),
    tsInfo(info),
    resolver(filter)
    {}
    
    void EquirectangularTilesetFilter::start()
    {}
    
    void EquirectangularTilesetFilter::stop()
    {}
    
    void EquirectangularTilesetFilter::init()
    {
        initImpl(this->bitmapLoader, this->tsInfo);
    }
    
    void EquirectangularTilesetFilter::release()
    {
        releaseImpl(this->bitmapLoader, this->tsInfo);
    }
    
    FutureTask<Bitmap> EquirectangularTilesetFilter::getTile(int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/)
    {
        return getTileImpl(this->bitmapLoader, this->tsInfo, this->resolver, latIndex, lngIndex, level/*, opts*/);
    }
    
    /*************************************************************************/
    // OSMDroidFilter
    
    OSMDroidFilter::OSMDroidFilter(TilesetInfo *info, AsyncBitmapLoader *loader, ResolveFunction filter) :
    OSMTilesetSupport(info, loader),
    tsInfo(info),
    resolver(filter)
    {}
    
    void OSMDroidFilter::start()
    {}
    
    void OSMDroidFilter::stop()
    {}
    
    void OSMDroidFilter::init()
    {
        initImpl(this->bitmapLoader, this->tsInfo);
    }
    
    void OSMDroidFilter::release()
    {
        releaseImpl(this->bitmapLoader, this->tsInfo);
    }
    
    FutureTask<Bitmap> OSMDroidFilter::getTile(int latIndex, int lngIndex, int level/*, BitmapFactory.Options opts*/)
    {
        return getTileImpl(this->bitmapLoader, this->tsInfo, this->resolver, latIndex, lngIndex, level/*, opts*/);
    }
    
    /*************************************************************************/
    // SpiImpl
    
    SpiImpl::SpiImpl()
    {}
    
    const char *SpiImpl::getName() const
    {
        return "simple";
    }
    
    TilesetSupport *SpiImpl::create(TilesetInfo *tsInfo, AsyncBitmapLoader *bitmapLoader)
    {
        const char *_pathStructure = DatasetDescriptor::getExtraData(*tsInfo->getInfo(), "_pathStructure", "NASAWW");
        if (strcmp(_pathStructure, "NASAWW") == 0) {
            return new EquirectangularTilesetFilter(tsInfo, bitmapLoader, NASAWW_RESOLVER);
        }
        else if (strcmp(_pathStructure, "TMS") == 0) {
            return new EquirectangularTilesetFilter(tsInfo, bitmapLoader, TMS_RESOLVER);
        }
        else if (strcmp(_pathStructure, "NASAWW_LARGE") == 0) {
            return new EquirectangularTilesetFilter(tsInfo, bitmapLoader, NASAWW_LARGE_RESOLVER);
        }
        else if (strcmp(_pathStructure, "OSM_DROID") == 0) {
            return new OSMDroidFilter(tsInfo, bitmapLoader, OSMDROID_ZIP_RESOLVER);
        }
        else if (strcmp(_pathStructure, "OSM_DROID_SQLITE") == 0
                 && tsInfo->getInfo()->getSpatialReferenceID() == 3857) {
            return new OSMDroidFilter(tsInfo, bitmapLoader, OSMDROID_SQLITE_RESOLVER);
        }
        else if (strcmp(_pathStructure, "OSM_DROID_SQLITE") == 0) {
            return new EquirectangularTilesetFilter(tsInfo, bitmapLoader, OSMDROID_SQLITE_RESOLVER);
        }
        else if (strcmp(_pathStructure, "NETT_WARRIOR_SQLITE") == 0) {
            return new OSMDroidFilter(tsInfo, bitmapLoader, NETT_WARRIOR_SQLITE_RESOLVER);
        }
        else {
            return nullptr;
        }
    }
}
