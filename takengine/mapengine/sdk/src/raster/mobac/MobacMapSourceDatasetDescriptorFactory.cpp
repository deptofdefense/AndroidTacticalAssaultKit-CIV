
#include "raster/mobac/MobacMapSourceDatasetDescriptorFactory.h"
#include "raster/mobac/MobacMapSource.h"
#include "raster/mobac/CustomMobacMapSource.h"
#include "raster/mobac/CustomWmsMobacMapSource.h"
#include "raster/mobac/MobacMapSourceFactory.h"
#include "core/Projection.h"
#include "core/ProjectionFactory2.h"
#include "raster/tilepyramid/TilesetInfo.h"
#include "util/IO2.h"

using namespace atakmap::core;
using namespace atakmap::raster;
using namespace atakmap::raster::tilepyramid;
using namespace atakmap::raster::mobac;

MobacMapSourceDatasetDescriptorFactory::MobacMapSourceDatasetDescriptorFactory()
: DatasetDescriptor::Factory("mobac", 1) { }

MobacMapSourceDatasetDescriptorFactory::~MobacMapSourceDatasetDescriptorFactory() throw() {
    
}

MobacMapSourceDatasetDescriptorFactory::DescriptorSet *
MobacMapSourceDatasetDescriptorFactory::createImpl(const char* filePath, const char* workingDir, CreationCallback*) const {
    // Don't do anything with the callback here, the mobac files contain so little data
    // that it isn't worth reporting progress.
    
    std::auto_ptr<MobacMapSource> mapSource;
    try {
        mapSource = std::auto_ptr<MobacMapSource>(MobacMapSourceFactory::create(filePath));
    } catch (std::exception &e) {
    //TODO(bergeronj)--    Logger::log(Logger::Level::Error, TAG + ": IO error creating MOBAC Map Source layer");
    }
    if (!mapSource.get())
        return NULL;
    
    TAK::Engine::Core::ProjectionPtr layerProjection(TAK::Engine::Core::ProjectionFactory2::getProjection(3857));
    int gridZeroWidth = 1;
    int gridZeroHeight = 1;
    if (CustomWmsMobacMapSource *impl = dynamic_cast<CustomWmsMobacMapSource *>(mapSource.get())) {
        int srid = impl->getSRID();
        if (srid == 4326) {
            layerProjection = TAK::Engine::Core::ProjectionFactory2::getProjection(4326);
            gridZeroWidth = 2;
        } else if (srid != 3857) {
            throw std::runtime_error("Illegal State");
        }
    }
    
    TilesetInfo::Builder builder("mobac", "mobac");
    builder.setPathStructure("MOBAC_CUSTOM_MAP_SOURCE");
    builder.setIsOnline(true);
    //TODO(bergeronj)--builder.setSupportSpi(MobacMapSourceTilesetSupport::SPI->getName());
    builder.setSpatialReferenceID(layerProjection->getSpatialReferenceID());
    
    double north, west, south, east;
    feature::Envelope bounds;
    if (mapSource->getBounds(&bounds)) {
        north = bounds.maxY;
        west = bounds.minX;
        south = bounds.minY;
        east = bounds.maxX;
    } else {
        north = layerProjection->getMaxLatitude();
        west = layerProjection->getMinLongitude();
        south = layerProjection->getMinLatitude();
        east = layerProjection->getMaxLongitude();
    }
    builder.setFourCorners(GeoPoint(south, west),
                           GeoPoint(north, west),
                           GeoPoint(north, east),
                           GeoPoint(south, east));
    builder.setZeroHeight((layerProjection->getMaxLatitude() - layerProjection->getMinLatitude())
                          / gridZeroHeight);
    builder.setZeroWidth((layerProjection->getMaxLongitude() - layerProjection->getMinLongitude())
                         / gridZeroWidth);
    builder.setGridOriginLat(layerProjection->getMinLatitude());
    builder.setGridOffsetX(0);
    builder.setGridOffsetY(0);
    
    /*TODO(bergeronj)--ensure full path*/
    if (workingDir) {
        char *offlineCache = PGSC::catFilePathStrings(workingDir, "cache.sqlite");
        TAK::Engine::Port::String pathStr = offlineCache;
        TAK::Engine::Util::File_getStoragePath(pathStr);
        builder.setExtra("offlineCache", pathStr.get());
        PGSC::StringAllocator alloc;
        alloc.deallocate(offlineCache);
    }
        
    //TODO(bergeronj)--builder.setExtra("defaultExpiration", SystemClock::currentTimeMillis().ToString());
    
    builder.setUri(filePath);
    builder.setName(mapSource->getName());
    builder.setLevelOffset(mapSource->getMinZoom());
    builder.setLevelCount(mapSource->getMaxZoom() - mapSource->getMinZoom() + 1);
    builder.setGridWidth((1 << mapSource->getMinZoom()) * gridZeroWidth);
    builder.setGridHeight((1 << mapSource->getMinZoom()) * gridZeroHeight);
    std::string imageExt = ".";
    imageExt += mapSource->getTileType();
    builder.setImageExt(imageExt.c_str());
    // XXX - forcing everything to 256x256 in the client for 2.0
    builder.setTilePixelWidth(256);
    builder.setTilePixelHeight(256);
    // builder.setTilePixelWidth(mapSource.getTileSize());
    // builder.setTilePixelHeight(mapSource.getTileSize());
    
    DatasetDescriptor *desc = builder.build();
    DatasetDescriptor::DescriptorSet *result = new DatasetDescriptor::DescriptorSet();
    result->insert(desc);
    return result;
}

bool MobacMapSourceDatasetDescriptorFactory::probeFile(const char* filePath, CreationCallback&) const {
    
    std::auto_ptr<DatasetDescriptor::DescriptorSet> result(createImpl(filePath, NULL, NULL));
    if (result.get()) {
        DatasetDescriptor::DescriptorSet::iterator it = result->begin();
        while (it != result->end()) {
            delete *it;
            ++it;
        }
        return true;
    }
    return false;
}


unsigned short MobacMapSourceDatasetDescriptorFactory::getVersion() const throw() {
    return 2;
}
