#include "formats/quantizedmesh/impl/QMElevationSource.h"
#include "formats/quantizedmesh/impl/QMElevationSampler.h"
#include "formats/quantizedmesh/impl/TileCoordImpl.h"
#include "formats/quantizedmesh/impl/TerrainDataCache.h"
#include "formats/quantizedmesh/impl/TerrainData.h"
#include "formats/quantizedmesh/TileCoord.h"
#include "formats/quantizedmesh/QMESourceLayer.h"
#include "elevation/ElevationData.h"
#include "elevation/ElevationChunkFactory.h"
#include "elevation/ElevationChunkCursor.h"
#include "math/Utils.h"
#include "util/IO2.h"
#include "port/STLVectorAdapter.h"

#include <list>
#include <string>

using namespace TAK::Engine;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;

namespace {
    const Feature::Envelope2 WORLD(-180, -90, 180, 90);

    class CursorImpl : public Elevation::ElevationChunkCursor
    {
    public:
        CursorImpl();
        CursorImpl(std::shared_ptr<QMESourceLayer> layer, const Feature::Envelope2 &mbb);
        virtual ~CursorImpl() NOTHROWS;
        virtual Util::TAKErr moveToNext() NOTHROWS;
        virtual Util::TAKErr get(Elevation::ElevationChunkPtr &value) NOTHROWS;
        virtual Util::TAKErr getResolution(double *value) NOTHROWS;
        virtual Util::TAKErr isAuthoritative(bool *value) NOTHROWS;
        virtual Util::TAKErr getCE(double *value) NOTHROWS;
        virtual Util::TAKErr getLE(double *value) NOTHROWS;
        virtual Util::TAKErr getUri(const char **value) NOTHROWS;
        virtual Util::TAKErr getType(const char **value) NOTHROWS;
        virtual Util::TAKErr getBounds(const Feature::Polygon2 **value) NOTHROWS;
        virtual Util::TAKErr getFlags(unsigned int *value) NOTHROWS;

    private:
        void queryTileCoords();
        void queryTileCoords(const TileExtents &extents, int level);
        void resolveSeams(TileCoordImpl tile, TileCoordImpl neighbors[4]);
        /**
         * Check if we need to download a tile and all its adjacent tiles
         * We want the adjacent tiles for seam calculations
         * @param tile Center tile
         * @param neighbors Neighboring tiles
         * @param dirExists True if the tile dir exists
         *                  Avoids an extra getTileFile call if it doesn't
         * @return True if one or more tile requests are busy
         */
        bool requestTiles(TileCoordImpl tile, const TileCoordImpl neighbors[4], bool dirExists);
        bool requestTile(const TileCoordImpl &tile, bool dirExists);

        /**
         * Calculate the maximum level of detail to query based on the extents
         * We do not want to go full detail when the extents are huge or else
         * you can easily end up with 10000+ tile queries per cursor
         * @return Maximum level of detail
         */
        static int calculateMaxLevel(const Feature::Envelope2 &bounds, const QMESourceLayer &layer);

        std::shared_ptr<QMESourceLayer> layer;
        std::list<TileCoordImpl> coords;
        Feature::Envelope2 bounds;
        Feature::Geometry2Ptr boundsPoly;
        int maxLevel;

        std::list<TileCoordImpl>::iterator curCoordIter;
        bool hitFirstRow;

        Port::String curRowUri;
    };
}

QMElevationSource::QMElevationSource(std::shared_ptr<QMESourceLayer> layer) NOTHROWS : layer(layer), bounds(WORLD)
{
}

QMElevationSource::~QMElevationSource() NOTHROWS
{
}

const char *QMElevationSource::getName() const NOTHROWS
{
    return "QME";
}

Util::TAKErr QMElevationSource::query(Elevation::ElevationChunkCursorPtr &value, const QueryParameters &params) NOTHROWS
{
    
    Util::TAKErr code = Util::TE_Ok;
    Feature::Envelope2 mbb = WORLD;
    if (params.spatialFilter != nullptr) {
        code = params.spatialFilter->getEnvelope(&mbb);
        TE_CHECKRETURN_CODE(code);
    }
    if (params.authoritative != nullptr && *(params.authoritative) == true ||
           params.flags != nullptr && ((*(params.flags) & Elevation::ElevationData::MODEL_TERRAIN) == 0)) {
        value = Elevation::ElevationChunkCursorPtr(new CursorImpl(), Util::Memory_deleter_const<Elevation::ElevationChunkCursor, CursorImpl>);
        return Util::TE_Ok;
    }
    value = Elevation::ElevationChunkCursorPtr(new CursorImpl(layer, mbb), Util::Memory_deleter_const<Elevation::ElevationChunkCursor, CursorImpl>);
    return Util::TE_Ok;
}

Feature::Envelope2 QMElevationSource::getBounds() const NOTHROWS
{
    return bounds;
}

Util::TAKErr QMElevationSource::addOnContentChangedListener(OnContentChangedListener *l) NOTHROWS
{
    return Util::TE_Ok;
}

Util::TAKErr QMElevationSource::removeOnContentChangedListener(OnContentChangedListener *l) NOTHROWS
{
    return Util::TE_Ok;
}


CursorImpl::CursorImpl() : 
    layer(nullptr), coords(), bounds(), 
    boundsPoly(NULL, NULL),
    maxLevel(0),
    curCoordIter(coords.end()),
    hitFirstRow(false),
    curRowUri("Unknown")
{
}

CursorImpl::CursorImpl(std::shared_ptr<QMESourceLayer> layer, const Feature::Envelope2 &mbb) : 
    layer(layer), coords(), bounds(mbb), 
    boundsPoly(NULL, NULL),
    maxLevel(calculateMaxLevel(mbb, *layer)), 
    curCoordIter(coords.end()),
    hitFirstRow(false),
    curRowUri("Unknown")
{
    queryTileCoords();
    curCoordIter = coords.begin();
}

CursorImpl::~CursorImpl() NOTHROWS
{
}

Util::TAKErr CursorImpl::moveToNext() NOTHROWS
{
    if (curCoordIter == coords.end())
        return Util::TE_Done;
    
    if (!hitFirstRow)
        hitFirstRow = true;
    else if (++curCoordIter == coords.end())
        return Util::TE_Done;

    // Clear and/or cache items for the current row
    Util::TAKErr code = layer->getTileFilename(&curRowUri, curCoordIter->x, curCoordIter->y, curCoordIter->z);
    if (code != Util::TE_Ok)
        curRowUri = "Unknown";

    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::get(Elevation::ElevationChunkPtr &value) NOTHROWS
{
    Util::TAKErr code = Util::TE_Ok;
    if (curCoordIter == coords.end())
        return Util::TE_Done;

    Port::String fn;
    code = layer->getTileFilename(&fn, curCoordIter->x, curCoordIter->y, curCoordIter->z);
    TE_CHECKRETURN_CODE(code);

    Elevation::SamplerPtr sampler(new QMElevationSampler(fn.get(), TileCoord_getGSD(curCoordIter->z)), Util::Memory_deleter_const<Elevation::Sampler, QMElevationSampler>);

    // downcast to polygon
    Feature::Geometry2Ptr geom(NULL, NULL);
    code = curCoordIter->getCoverage(geom);
    TE_CHECKRETURN_CODE(code);

    return Elevation::ElevationChunkFactory_create(value, "QME", fn.get(), Elevation::ElevationData::MODEL_TERRAIN, TileCoord_getGSD(curCoordIter->z), *((Feature::Polygon2 *)geom.get()), NAN, NAN, false, std::move(sampler));
}

Util::TAKErr CursorImpl::getResolution(double *value) NOTHROWS
{
    *value = TileCoord_getGSD(curCoordIter->z);
    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::isAuthoritative(bool *value) NOTHROWS
{
    *value = false;
    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::getCE(double *value) NOTHROWS
{
    *value = NAN;
    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::getLE(double *value) NOTHROWS
{
    *value = NAN;
    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::getUri(const char **value) NOTHROWS
{
    *value = curRowUri.get();
    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::getType(const char **value) NOTHROWS
{
    *value = "quantized-mesh";
    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::getBounds(const Feature::Polygon2 **value) NOTHROWS
{
    if (!boundsPoly.get()) {
        Util::TAKErr code = Feature::Polygon2_fromEnvelope(boundsPoly, bounds);
        TE_CHECKRETURN_CODE(code);
    }

    *value = (Feature::Polygon2 *)boundsPoly.get();
    return Util::TE_Ok;
}

Util::TAKErr CursorImpl::getFlags(unsigned int *value) NOTHROWS
{
    *value = Elevation::ElevationData::MODEL_TERRAIN;
    return Util::TE_Ok;
}


void CursorImpl::queryTileCoords()
{
    bool enabled = false;
    if (layer->isEnabled(&enabled) != Util::TE_Ok || !enabled)
        return;

    TileExtents extents;
    for (int z = maxLevel; z >= 0; z--) {
        extents.startX = (int) floor(TileCoord_getTileX(bounds.minX, z));
        extents.startY = (int) floor(TileCoord_getTileY(bounds.minY, z));
        extents.endX = (int) floor(TileCoord_getTileX(bounds.maxX, z));
        extents.endY = (int) floor(TileCoord_getTileY(bounds.maxY, z));
        queryTileCoords(extents, z);
    }
}

void CursorImpl::queryTileCoords(const TileExtents &extents, int level)
{
    int layerMaxLevel = 0;
    Util::TAKErr code = layer->getMaxLevel(&layerMaxLevel);
    TE_CHECKRETURN(code);

    if (level < 0 || level > layerMaxLevel)
        return;

    Port::String fn;
    bool dirExists = false;
    code = layer->getLevelDirName(&fn, level);
    if (code == Util::TE_Ok)
        Util::IO_exists(&dirExists, fn.get());

    Port::STLVectorAdapter<TileExtents> available;
    code = layer->getAvailableExtents(&available, level);
    if (code != Util::TE_Ok)
        return;
    
    Port::STLVectorAdapter<TileExtents>::IteratorPtr iter(NULL, NULL);
    code = available.iterator(iter);
    if (code != Util::TE_Ok)
        return;

    TileExtents e;
    while (iter->get(e) == Util::TE_Ok) {

        int startX = atakmap::math::max(e.startX, extents.startX);
        int startY = atakmap::math::max(e.startY, extents.startY);
        int endX = atakmap::math::min(e.endX, extents.endX);
        int endY = atakmap::math::min(e.endY, extents.endY);
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                TileCoordImpl c(x, y, level);
                TileCoordImpl neighbors[4] = {
                    TileCoordImpl(c.x, c.y + 1, c.z),
                    TileCoordImpl(c.x + 1, c.y, c.z),
                    TileCoordImpl(c.x, c.y - 1, c.z),
                    TileCoordImpl(c.x - 1, c.y, c.z),
                };

                // Check if we need to download this tile and its
                // neighbors. This tile will not be used until its
                // neighbors are downloaded as well.
                if (requestTiles(c, neighbors, dirExists))
                    continue;

                // Make sure seams are resolved first
                resolveSeams(c, neighbors);

                // Finally add tile to the query list
                coords.push_back(c);
            }
        }
        iter->next();
    }
}


/**
* Resolve seams between a tile and its neighbors
* @param tile Center tile
* @param neighbors Neighboring tiles
*/
void CursorImpl::resolveSeams(TileCoordImpl tile, TileCoordImpl neighbors[4])
{
    std::shared_ptr<TerrainData> center = TerrainDataCache_getData(*layer, tile);

    if (center == nullptr || center->areSeamsResolved())
        return;

    std::vector<TerrainData *> neighborData;
    std::vector<std::shared_ptr<TerrainData>> neighborDataHolder;
    for (int i = 0; i < 4; i++) {
        bool hasNeighbor;
        Util::TAKErr code = layer->hasTile(&hasNeighbor, neighbors[i].x, neighbors[i].y, neighbors[i].z);
        if (code != Util::TE_Ok || !hasNeighbor)
            continue;

        std::shared_ptr<TerrainData> dptr = TerrainDataCache_getData(*layer, neighbors[i]);
        neighborDataHolder.push_back(dptr);
        neighborData.push_back(dptr.get());
    }

    center->resolveSeams(neighborData);
}


/**
* Check if we need to download a tile and all its adjacent tiles
* We want the adjacent tiles for seam calculations
* @param tile Center tile
* @param neighbors Neighboring tiles
* @param dirExists True if the tile dir exists
*                  Avoids an extra getTileFile call if it doesn't
* @return True if one or more tile requests are busy
*/
bool CursorImpl::requestTiles(TileCoordImpl tile, const TileCoordImpl neighbors[4], bool dirExists)
{
    bool requesting = requestTile(tile, dirExists);
    for (int i = 0; i < 4; i++)
        requesting |= requestTile(neighbors[i], dirExists);
    return requesting;
}

bool CursorImpl::requestTile(const TileCoordImpl &tile, bool dirExists)
{
    bool tileExists = false;

    // Tile doesn't exist
    Util::TAKErr code = layer->hasTile(&tileExists, tile.x, tile.y, tile.z);
    if (code != Util::TE_Ok || !tileExists)
        return false;

    bool startRequest = !dirExists;
    if (!startRequest) {
        Port::String fn;
        if (layer->getTileFilename(&fn, tile.x, tile.y, tile.z) != Util::TE_Ok)
            return false;
        bool fileExists;
        if (Util::IO_exists(&fileExists, fn.get()) != Util::TE_Ok)
            return false;
        startRequest = !fileExists;
    }
    if (startRequest) {
        code = layer->startDataRequest(tile.x, tile.y, tile.z);
        return code == Util::TE_Ok;
    }

    return false;
}

/**
* Calculate the maximum level of detail to query based on the extents
* We do not want to go full detail when the extents are huge or else
* you can easily end up with 10000+ tile queries per cursor
* @return Maximum level of detail
*/
int CursorImpl::calculateMaxLevel(const Feature::Envelope2 &b, const QMESourceLayer &layer) {
    double width = b.maxX - b.minX;
    double length = b.maxY - b.minY;
    int ret = 0;
    layer.getClosestLevel(&ret, atakmap::math::max(width, length));
    return ret;
}

