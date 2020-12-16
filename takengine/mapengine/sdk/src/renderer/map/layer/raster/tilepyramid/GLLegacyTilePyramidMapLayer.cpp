
#include "feature/Geometry.h"
#include "raster/DatasetDescriptor.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/gdal/GdalLibrary.h"
#include "raster/tilepyramid/TilesetInfo.h"
#include "renderer/GLRenderContext.h"
#include "renderer/map/layer/raster/tilepyramid/GLLegacyTilePyramidMapLayer.h"
#include "raster/tilepyramid/LegacyTilePyramidTileReader.h"
#include "renderer/AsyncBitmapLoader.h"
#include "raster/DefaultDatasetProjection.h"
#include "renderer/map/layer/raster/tilereader/GLQuadTileNode.h"
#include "renderer/GLRendererGlobals.h"

using namespace atakmap::core;

using namespace atakmap::raster;
using namespace atakmap::raster::tilepyramid;

using namespace atakmap::renderer;

using namespace atakmap::renderer::map;

using namespace atakmap::renderer::map::layer::raster;
using namespace atakmap::renderer::map::layer::raster::tilepyramid;

const char * const GLLegacyTilePyramidMapLayer::SUPPORTED_TYPES[] = {
    "mobac",
    "osmdroid",
    NULL
};

GLLegacyTilePyramidMapLayer::SPI::~SPI() throw() { }

GLMapLayer *GLLegacyTilePyramidMapLayer::SPI::createLayer(atakmap::renderer::GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info) {
    
    if (!checkSupportedTypes(SUPPORTED_TYPES, info->getDatasetType())) {
        return NULL;
    }
    
    const ImageDatasetDescriptor *imageDD = dynamic_cast<const ImageDatasetDescriptor *>(info);
    if (!imageDD) {
        return NULL;
    }
    
    DatasetDescriptorUniquePtr clonePtr(NULL, NULL);
    if (imageDD->clone(clonePtr) != TAK::Engine::Util::TE_Ok)
        return nullptr;
    
    return new GLLegacyTilePyramidMapLayer(context, new TilesetInfo(static_cast<ImageDatasetDescriptor *>(clonePtr.release()), clonePtr.get_deleter()));
}

GLLegacyTilePyramidMapLayer::GLLegacyTilePyramidMapLayer(atakmap::renderer::GLRenderContext *renderContext, atakmap::raster::tilepyramid::TilesetInfo *info)
: _tsInfo(info),
rootNode(nullptr),
renderContext(renderContext),
bounds(info->getInfo()->getCoverage(NULL)->getEnvelope()),
initialized(false)
{ }

GLLegacyTilePyramidMapLayer::~GLLegacyTilePyramidMapLayer() {
}

void GLLegacyTilePyramidMapLayer::init(const GLMapView *view) {
    
    /*
     PGSC::String key = LegacyTilePyramidTileReader::registerTilesetInfo(GLRendererGlobals::getAsyncBitmapLoader(),
                                                                        _tsInfo.get());
    
     LegacyTilePyramidTileReader::Spi spi;
     this->tileReader = static_cast<LegacyTilePyramidTileReader *>(spi.create(key, NULL));
     */
    
    this->tileReader = new LegacyTilePyramidTileReader(_tsInfo, GLRendererGlobals::getAsyncBitmapLoader());

    GeoPoint ll = _tsInfo->getInfo()->getLowerLeft();
    GeoPoint ur = _tsInfo->getInfo()->getUpperRight();
    GeoPoint ul = _tsInfo->getInfo()->getUpperLeft();
    GeoPoint lr = _tsInfo->getInfo()->getLowerRight();
    
    DatasetProjection *proj = new DefaultDatasetProjection(_tsInfo->getInfo()->getSpatialReferenceID(),
                                 (int)this->tileReader->getWidth(),
                                 (int)this->tileReader->getHeight(),
                                 ul, ur, lr, ll);
    this->datasetProj = std::unique_ptr<DatasetProjection>(proj);
    
    //System.out.println("tileset create proj " + ul + "," + ur + "," + lr + "," + ll);
    
    tilereader::GLTileNode::Options opts;
    opts.childTextureCopyResolvesParent = false;
    opts.forceLoResLoadOnError = true;
    opts.levelTransitionAdjustment = 0;
    if (view->pixelDensity > 1) {
        opts.levelTransitionAdjustment = -1.0;
    }
    
    
    opts.progressiveLoad = true;
    opts.textureCopyEnabled = false;
    
    this->rootNode = new tilereader::GLQuadTileNode(_tsInfo->getInfo()->getImageryType(), this->tileReader, proj, false, &opts);
        //TODO--this->rootNode->setTextureCache(this->surface.getTextureCache());
            //TODO--this->rootNode.setLoadingTextureEnabled(false);
    /*} catch(Throwable t) {
        Log.e(TAG, "Failed to initialize renderer for " + _tsInfo.getInfo().getName(), t);
    }*/
}

void GLLegacyTilePyramidMapLayer::release() {
    if(this->rootNode != NULL) {
        this->rootNode->release();
        if (!this->rootNode->deleteAfterRequestAction) {
            delete this->rootNode;
        }
        this->rootNode = NULL;
    }
    if(this->tileReader != NULL) {
        this->tileReader->dispose();
        // tilereader is deleted after the last asyncIO task
        this->tileReader = NULL;
    }
    this->initialized = false;
}

const char *GLLegacyTilePyramidMapLayer::getLayerUri() const {
    return _tsInfo->getInfo()->getURI();
}

void GLLegacyTilePyramidMapLayer::draw(const atakmap::renderer::map::GLMapView *view) {
    if (!this->initialized) {
        this->init(view);
        this->initialized = true;
    }
    
    if(this->rootNode != NULL) {
        this->tileReader->start();
        this->rootNode->draw(view);
        this->tileReader->stop();
    }
}

const ImageDatasetDescriptor *GLLegacyTilePyramidMapLayer::getInfo() const {
    return _tsInfo->getInfo();
}

void GLLegacyTilePyramidMapLayer::start() {
    
}

void GLLegacyTilePyramidMapLayer::stop() {
    
}
