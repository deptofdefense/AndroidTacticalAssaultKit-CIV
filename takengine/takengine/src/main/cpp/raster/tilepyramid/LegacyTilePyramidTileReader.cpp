
#include "string/StringHacks.hh"

#include "core/GeoPoint.h"
#include "feature/Geometry.h"
#include "raster/tilepyramid/LegacyTilePyramidTileReader.h"
#include "raster/tilepyramid/Tilesetinfo.h"
#include "raster/gdal/GdalLibrary.h"
#include "renderer/BitmapFactory.h"
#include "renderer/BitmapCompositor.h"
#include "math/Utils.h"
#include "renderer/GLRendererGlobals.h"

#define DEBUG_TILE_DRAW 0

#define RESTRICT_COMPOSITE 1

using namespace atakmap;
using namespace atakmap::core;
using namespace atakmap::feature;
using namespace atakmap::raster;
using namespace atakmap::raster::tilepyramid;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

LegacyTilePyramidTileReader::RegisterValue::~RegisterValue() {
    if (this->bitmapLoaderRelease) {
        this->bitmapLoaderRelease(this->bitmapLoader);
    }
    if (this->tilesetInfoRelease) {
        this->tilesetInfoRelease(this->tilesetInfo);
    }
}

Mutex LegacyTilePyramidTileReader::staticMutex;
LegacyTilePyramidTileReader::RegisterMap LegacyTilePyramidTileReader::registerMap;

atakmap::raster::tilereader::TileReader *LegacyTilePyramidTileReader::Spi::create(const char *uri, const tilereader::TileReaderFactory::Options *options) {
    
    if (strstr(uri, "tsinfo://") != uri) {
        return NULL;
    }
    
    RegisterMap::iterator entry = registerMap.find(uri + 9);
    if (entry == registerMap.end()) {
        return NULL;
    }
    
    TilesetInfo *tsInfo = entry->second.tilesetInfo;
    if (tsInfo == NULL) {
        return NULL;
    }
    
    TilesetSupport *support = TilesetSupport::create(tsInfo, entry->second.bitmapLoader);
    if(support == NULL)
        return NULL;
    
    PGSC::RefCountableIndirectPtr<TileReader::AsynchronousIO> asyncIO = atakmap::raster::gdal::GdalLibrary::getMasterIOThread();
    if (options && options->asyncIO.get())
            asyncIO = options->asyncIO;
        
    return new LegacyTilePyramidTileReader(tsInfo, support, asyncIO);
}


const double LegacyTilePyramidTileReader::EPSILON = 0.000000001;

LegacyTilePyramidTileReader::LegacyTilePyramidTileReader(atakmap::raster::tilepyramid::TilesetInfo *info, renderer::AsyncBitmapLoader *loader)
: LegacyTilePyramidTileReader(info, TilesetSupport::create(info, loader), atakmap::raster::gdal::GdalLibrary::getMasterIOThread())
{ }

LegacyTilePyramidTileReader::LegacyTilePyramidTileReader(TilesetInfo *info,
                                                         TilesetSupport *support, PGSC::RefCountableIndirectPtr<AsynchronousIO> io)
: tilereader::TileReader(info->getInfo()->getURI(), NULL, INT_MAX, io), support(support) {
    
    if(this->support == NULL) {
        throw std::invalid_argument("TilesetSupport cannot be NULL");
    }
    
    this->support->init();
    this->support->start();
    
    this->tileWidth = info->getTilePixelWidth();
    this->tileHeight = info->getTilePixelHeight();
    
    this->levelCount = info->getLevelCount();
    
    const double zeroHeight = info->getZeroHeight();
    const double zeroWidth = info->getZeroWidth();
    
    const double gridOriginLat = info->getGridOriginLat();
    const double gridOriginLng = info->getGridOriginLng();
    
    feature::Envelope mbb = info->getInfo()->getCoverage(NULL)->getEnvelope();
    
    double south = mbb.minY;
    double north = mbb.maxY;
    double west = mbb.minX;
    double east = mbb.maxX;
    
    // Shrink down on the 4 corners as much as we can
    GeoPoint sw(mbb.minY, mbb.minX);
    GeoPoint nw(mbb.maxY, mbb.minX);
    GeoPoint ne(mbb.maxY, mbb.maxX);
    GeoPoint se(mbb.minY, mbb.maxX);
    
    const double coverageSouth = fmin(sw.latitude, se.latitude);
    const double coverageNorth = fmax(nw.latitude, ne.latitude);
    const double coverageEast = fmax(ne.longitude, se.longitude);
    const double coverageWest = fmin(nw.longitude, sw.longitude);
    
    south = _alignMin(gridOriginLat, fmax(coverageSouth, south), zeroHeight);
    west = _alignMin(gridOriginLng, fmax(coverageWest, west), zeroWidth);
    north = _alignMax(gridOriginLat, fmin(coverageNorth, north), zeroHeight);
    east = _alignMax(gridOriginLng, fmin(coverageEast, east), zeroWidth);
    
    int _gridHeight = info->getGridHeight();
    if (_gridHeight < 0)
        _gridHeight = (int) (((north - south) + EPSILON) / zeroHeight);
    int _gridWidth = info->getGridWidth();
    if (_gridWidth < 0)
        _gridWidth = (int) (((east - west) + EPSILON) / zeroWidth);
    int _gridX = info->getGridOffsetX();
    if (_gridX < 0)
        _gridX = (int) ((west - gridOriginLng + EPSILON) / zeroWidth);
    int _gridY = info->getGridOffsetY();
    if (_gridY < 0)
        _gridY = (int) ((south - gridOriginLat + EPSILON) / zeroHeight);
    
    this->gridHeight = _gridHeight;
    this->gridWidth = _gridWidth;
    this->gridOffsetX = _gridX;
    this->gridOffsetY = _gridY;
    
    this->width = this->tileWidth*(this->gridWidth<<(this->levelCount-1));
    this->height = this->tileHeight*(this->gridHeight<<(this->levelCount-1));
}

LegacyTilePyramidTileReader::~LegacyTilePyramidTileReader() { }

void LegacyTilePyramidTileReader::start() {
    this->support->start();
}

void LegacyTilePyramidTileReader::stop() {
    this->support->stop();
}

/**************************************************************************/
// Tile Reader

void LegacyTilePyramidTileReader::disposeImpl() {
    TileReader::disposeImpl();
    if(this->support != NULL) {
        this->support->release();
        this->support = NULL;
    }
}

static const uint64_t NANOS_PER_USEC = 1000ULL;
static const uint64_t NANOS_PER_MILLISEC = 1000ULL * NANOS_PER_USEC;
static const uint64_t NANOS_PER_SEC = 1000ULL * NANOS_PER_MILLISEC;

void LegacyTilePyramidTileReader::cancelAsyncRead(int id) {
    TileReader::cancelAsyncRead(id);
    
    if(this->pending.valid())
        this->pending.cancel();
}

LegacyTilePyramidTileReader::ReadResult
LegacyTilePyramidTileReader::getTileImpl(int level, int64_t tileColumn, int64_t tileRow, renderer::Bitmap *retval) {
    
    if (level < 0)
        throw std::invalid_argument("level");
    
    // need to invert level and tile row for lower-left origin of legacy
    // tileset infrastructure
    const int tsLevel = (this->levelCount-level-1);
    tileRow = (this->gridHeight<<tsLevel)-tileRow-1;
    
    const int latIndex = (int)tileRow + (this->gridOffsetY<<tsLevel);
    const int lngIndex = (int)tileColumn + (this->gridOffsetX<<tsLevel);
    
    /*TODO--const BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;*/
    
    util::Future<renderer::Bitmap> bitmap = this->support->getTile(latIndex, lngIndex, tsLevel/*, opts*/).getFuture();
    if(!bitmap.valid()) {
        return TileReader::Error;
    }
    
    try {
        renderer::Bitmap r = bitmap.get();
        
        if(bitmap.getState() == util::SharedState::Canceled)
            return TileReader::Canceled;
        else if(r.data == NULL)
            return TileReader::Error;
        else {
#if DEBUG_TILE_DRAW
            std::stringstream ss;
            ss << level << ":" << tileColumn << ", " << tileRow;
            std::auto_ptr<renderer::BitmapCompositor> compositor(renderer::BitmapCompositor::create(r));
            compositor->debugText(0, 0, r.width, r.height, ss.str().c_str());
#endif
            *retval = r;
            return TileReader::Success;
        }
    } catch (const std::exception &e) {
    }
    return TileReader::Error;
}

LegacyTilePyramidTileReader::ReadResult
LegacyTilePyramidTileReader::read(int level, int64_t tileColumn, int64_t tileRow, void *data, size_t dataSize) {
    
    if (level < 0)
        throw std::invalid_argument("level");
    
    renderer::Bitmap bitmap;
    bitmap.data = data;
    bitmap.dataLen = dataSize;
    bitmap.releaseData = NULL;
    ReadResult code = this->getTileImpl(level, tileColumn, tileRow, &bitmap);
    
    //XXX-- get rid of copy
    if (code == Success) {
        memcpy(data, bitmap.data, bitmap.dataLen);
    }
    
    if (bitmap.releaseData) {
        bitmap.releaseData(bitmap);
    }
    
    return code;
}

struct RectF {
    float top, left, bottom, right;
};

LegacyTilePyramidTileReader::ReadResult
LegacyTilePyramidTileReader::read(int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH, int dstW,
                int dstH, void *data, size_t dataSize) {
    
    const int level = math::clamp((int)(log(fmax((double)srcW / (double)dstW, (double)srcH / (double)dstH)) / log(2.0)), 0, this->levelCount-1);
    
    const double subsampleX = (double)dstW / ((double)srcW/(double)(1<<level));
    const double subsampleY = (double)dstH / ((double)srcH/(double)(1<<level));
    
    const int64_t stx = math::clamp(this->getTileColumn(level, srcX), 0ll, this->getNumTilesX(level) - 1);
    const int64_t ftx = math::clamp(this->getTileColumn(level, srcX+srcW-1), 0ll, this->getNumTilesX(level)-1);
    
    const int64_t sty = math::clamp(this->getTileRow(level, srcY), 0ll, this->getNumTilesY(level)-1);
    const int64_t fty = math::clamp(this->getTileRow(level, srcY+srcH-1), 0ll, this->getNumTilesY(level)-1);

    // if we are reading exactly one tile, skip the compositing
    if(subsampleX == 1.0 && subsampleY == 1.0 &&
       stx == ftx && sty == fty &&
       dstW == this->tileWidth && dstH == this->tileHeight) {
        
       return this->read(level, stx, sty, data, dataSize);
    }
    
#if RESTRICT_COMPOSITE
    //XXX-- stop if the read is too far out
    int64_t area = (ftx - stx) * (fty - sty);
    if (area > 16) {
        return Error;
    }
#endif
    
    ReadResult result = Success;
    renderer::BitmapOptions bitmapOpts;
    bitmapOpts.dataType = GL_UNSIGNED_BYTE;
    bitmapOpts.format = GL_RGBA;
    bitmapOpts.width = dstW;
    bitmapOpts.height = dstH;
    renderer::Bitmap bitmap = renderer::BitmapFactory::create(bitmapOpts);
    ReadResult code;
    
    std::auto_ptr<renderer::BitmapCompositor> compositor(renderer::BitmapCompositor::create(bitmap));
    renderer::Bitmap tile;

    // XXX-- why are these flipped from Android?
    int64_t aty = fty;

    for(int64_t ty = sty; ty <= fty; ty++) {
        
        //RectF dst;
        float dstY = (float)((double)((this->getTileSourceY(level, ty)-srcY)>>level) * subsampleY);
        float dstH = (float)((double)((this->getTileSourceY(level, ty+1)-1-srcY)>>level) * subsampleY) - dstY;
        
        dstY = floorf(dstY);
        dstH = ceilf(dstH);
        
        for(int64_t tx = stx; tx <= ftx; tx++) {
            
            if (!this->valid) {
                return Error;
            }
            
            code = this->getTileImpl(level, tx, aty, &tile);
            if(code == Success) {
                float dstX = (float)((double)((this->getTileSourceX(level, tx)-srcX)>>level) * subsampleX);
                float dstW = (float)((double)((this->getTileSourceX(level, tx+1)-1-srcX)>>level) * subsampleX) - dstX;
                
                dstX = floorf(dstX);
                dstW = ceilf(dstW);
                
                compositor->composite(dstX, dstY, dstW, dstH,
                                      0, 0, tile.width, tile.height,
                                      tile);
                
                if (tile.releaseData) {
                    tile.releaseData(tile);
                    tile.releaseData = nullptr;
                }
            }
        }
        
        --aty;
    }
    
    memcpy(data, bitmap.data, bitmap.dataLen);
    if (bitmap.releaseData) {
        bitmap.releaseData(bitmap);
    }
    
    return result;
}

/**************************************************************************/

std::string LegacyTilePyramidTileReader::generateKey(renderer::AsyncBitmapLoader *loader, TilesetInfo *info) {

    static uint64_t keyIndex = 1;
    uint64_t value = keyIndex++;

    // XXX - hash codes? static counter?

    std::stringstream ss;
    ss << value;
    return ss.str();
}

PGSC::String LegacyTilePyramidTileReader::registerTilesetInfo(renderer::AsyncBitmapLoader *loader, TilesetInfo *tsInfo) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, staticMutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("LegacyTilePyramidTileReader::registerTilesetInfo: Failed to acquire mutex");
    
    const std::string retval = generateKey(loader, tsInfo);

    //    registerMap.put(retval, new ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>(Pair.create(loader, new WeakReference<TilesetInfo>(info))));
    registerMap.insert(std::pair<PGSC::String, RegisterValue>(retval.c_str(), RegisterValue(loader, tsInfo)));

    return ("tsinfo://" + retval).c_str();
}

void LegacyTilePyramidTileReader::unregisterLayer(TilesetInfo *info) {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, staticMutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("LegacyTilePyramidTileReader::unregisterLayer: Failed to acquire mutex");
    
    /*ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>> ref = NULL;
    
    Iterator<ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>> iter;
    
    iter = register.values().iterator();
    TilesetInfo registerInfo;
    while(iter.hasNext()) {
        ref = iter.next();
        
        registerInfo = ref.value.second.get();
        
        // if the reference was reclaimed, remove
        if(registerInfo == NULL)
            iter.remove();
        if(registerInfo == info) {
            ref.dereference();
            if(!ref.isReferenced())
                iter.remove();
            break;
        }
        
    }*/
}