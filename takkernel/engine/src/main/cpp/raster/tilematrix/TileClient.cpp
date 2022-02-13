#include "raster/tilematrix/TileClient.h"

using namespace TAK::Engine::Raster::TileMatrix;

CacheRequest::CacheRequest()
    : minResolution(0.0),
    maxResolution(0.0),
    region(nullptr, nullptr),
    timeSpanStart(0),
    timeSpanEnd(0),
    mode(TECM_Create),
    canceled(false),
    countOnly(0),
    maxThreads(0),
    expirationOffset(0) {}

CacheRequest::CacheRequest(const CacheRequest &other)
    : minResolution(other.minResolution),
    maxResolution(other.maxResolution),
    region(nullptr, nullptr),
    timeSpanStart(other.timeSpanStart),
    timeSpanEnd(other.timeSpanEnd),
    mode(other.mode),
    canceled(other.canceled),
    countOnly(other.countOnly),
    maxThreads(other.maxThreads),
    expirationOffset(other.expirationOffset)
{
    TAK::Engine::Feature::Geometry_clone(region, *other.region);
}

CacheRequest::~CacheRequest() NOTHROWS
{}

CacheRequestListener::~CacheRequestListener() NOTHROWS
{}

TileClient::~TileClient() NOTHROWS
{}