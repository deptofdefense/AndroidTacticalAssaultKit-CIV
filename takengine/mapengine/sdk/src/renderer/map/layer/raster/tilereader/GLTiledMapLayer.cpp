
#include "gdal_priv.h"

#include "core/GeoPoint.h"

#include "math/Utils.h"

#include "feature/Geometry.h"

#include "raster/DatasetDescriptor.h"
#include "raster/DatasetProjection.h"

#include "renderer/map/layer/raster/tilereader/GLQuadTileNode.h"
#include "renderer/GLTexture.h"
#include "renderer/GLRenderContext.h"
#include "renderer/map/layer/raster/tilereader/GLTiledMapLayer.h"

using namespace atakmap::core;
using namespace atakmap::math;
using namespace atakmap::raster;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::raster;

namespace {
    DatasetDescriptor *cloneDatasetDescriptor(const DatasetDescriptor *info) {
        DatasetDescriptor *result = nullptr;
        if (info) {
            DatasetDescriptorUniquePtr ptr(NULL, NULL);
            info->clone(ptr);
            return ptr.release();
        }
        return result;
    }
}

GLTiledMapLayer::GLTiledMapLayer(GLRenderContext *context, const DatasetDescriptor *info)
: context(context),
  info(::cloneDatasetDescriptor(info)),
  quadTree(nullptr),
  minimumBoundingBox(info->getCoverage()->getEnvelope()),
  initialized(false) { }

GLTiledMapLayer::~GLTiledMapLayer() {
    DatasetDescriptor::deleteDatasetDescriptor(info);
}

void GLTiledMapLayer::draw(const GLMapView *view) {
    if (!this->initialized) {
        this->init();
        this->initialized = true;
    }
    
    if (this->quadTree != nullptr)
        this->quadTree->draw(view);
}

void GLTiledMapLayer::releaseImpl() {
    // nothing
}

void GLTiledMapLayer::release() {
    if (this->quadTree != nullptr) {
        this->quadTree->release();
        this->quadTree = nullptr;
    }
    
    this->releaseImpl();
}

const char *GLTiledMapLayer::getLayerUri() const {
    return this->info->getURI();
}

const atakmap::raster::DatasetDescriptor *GLTiledMapLayer::getInfo() const {
    return this->info;
}

GLTexture *GLTiledMapLayer::getLoadingTexture() {
    static GLTexture *loadingTexture = nullptr;
    if (loadingTexture == nullptr) {
        int w = 64;
        int h = 64;
        int sw = 16;
        int sw2 = sw * 2;
        uint32_t px[] = {
            0xFF7F7F7F, 0xFFAAAAAA
        };
        
        loadingTexture = new GLTexture(w, h, GL_RGBA, GL_UNSIGNED_BYTE);
        size_t intCount = 64 * 64;
        uint32_t *argb = new uint32_t[intCount];
        int r;
        int c;
        for (int i = 0; i < intCount; i++) {
            c = (i % w);
            r = (i / w);
            
            argb[i] = px[(((c % sw2) + (r % sw2)) / sw) % 2];
        }
        
        loadingTexture->setWrapS(GL_REPEAT);
        loadingTexture->setWrapT(GL_REPEAT);
        loadingTexture->load(argb, 0, 0, w, h);
        delete[] argb;
    }
    
    return loadingTexture;
}

atakmap::math::Rectangle<float> GLTiledMapLayer::getRasterROI(const atakmap::renderer::map::GLMapView *view,
                                                              GDALDataset *dataset,
                                                              const atakmap::raster::DatasetProjection *proj) {
    
    int rasterWidth = dataset->GetRasterXSize();
    int rasterHeight = dataset->GetRasterYSize();
    GeoPoint ul;
    proj->imageToGround(PointD(0, 0), &ul);
    
    GeoPoint ur;
    proj->imageToGround(PointD(rasterWidth - 1, 0), &ur);
    GeoPoint lr;
    proj->imageToGround(PointD(rasterWidth - 1, rasterHeight - 1), &lr);
    GeoPoint ll;
    proj->imageToGround(PointD(0, rasterHeight - 1), &ll);
    
    return getRasterROI(view,
                        rasterWidth,
                        rasterHeight,
                        proj,
                        ul, ur, lr, ll);
}

atakmap::math::Rectangle<float> GLTiledMapLayer::getRasterROI(const atakmap::renderer::map::GLMapView *view, int rasterWidth, int rasterHeight,
                                                    const atakmap::raster::DatasetProjection *proj) {
    
    GeoPoint ul;
    proj->imageToGround(PointD(0, 0), &ul);
    GeoPoint ur;
    proj->imageToGround(PointD(rasterWidth - 1, 0), &ur);
    GeoPoint lr;
    proj->imageToGround(PointD(rasterWidth - 1, rasterHeight - 1), &lr);
    GeoPoint ll;
    proj->imageToGround(PointD(0, rasterHeight - 1), &ll);
    
    return getRasterROI(view,
                        rasterWidth,
                        rasterHeight,
                        proj,
                        ul, ur, lr, ll);
}

atakmap::math::Rectangle<float> GLTiledMapLayer::getRasterROI(const atakmap::renderer::map::GLMapView *view,
                                                    long rasterWidth, long rasterHeight,
                                                    const atakmap::raster::DatasetProjection *proj,
                                                    const atakmap::core::GeoPoint &ulG_R, const atakmap::core::GeoPoint &urG_R, const atakmap::core::GeoPoint &lrG_R, const atakmap::core::GeoPoint &llG_R) {
    
    double minLat = atakmap::math::min(ulG_R.latitude, urG_R.latitude, lrG_R.latitude, llG_R.latitude);
    double minLng = atakmap::math::min(ulG_R.longitude, urG_R.longitude, lrG_R.longitude, llG_R.longitude);
    double maxLat = atakmap::math::max(ulG_R.latitude, urG_R.latitude, lrG_R.latitude, llG_R.latitude);
    double maxLng = atakmap::math::max(ulG_R.longitude, urG_R.longitude, lrG_R.longitude, llG_R.longitude);
    
    double roiMinLat = atakmap::math::clamp(minLat, view->southBound, view->northBound);
    double roiMinLng = atakmap::math::clamp(minLng, view->westBound, view->eastBound);
    double roiMaxLat = atakmap::math::clamp(maxLat, view->southBound, view->northBound);
    double roiMaxLng = atakmap::math::clamp(maxLng, view->westBound, view->eastBound);
    
    Rectangle<float> roi;
    if(roiMinLat != roiMaxLat && roiMinLng != roiMaxLng) {
        GeoPoint scratchGeo(roiMaxLat, roiMinLng);
        PointD roiUL(0, 0);
        proj->groundToImage(scratchGeo, &roiUL);
        scratchGeo.set(roiMaxLat, roiMaxLng);
        PointD roiUR(0, 0);
        proj->groundToImage(scratchGeo, &roiUR);
        scratchGeo.set(roiMinLat, roiMaxLng);
        PointD roiLR(0, 0);
        proj->groundToImage(scratchGeo, &roiLR);
        scratchGeo.set(roiMinLat, roiMinLng);
        PointD roiLL(0, 0);
        proj->groundToImage(scratchGeo, &roiLL);
        
        roi.x = (float)atakmap::math::clamp(atakmap::math::min(roiUL.x, roiUR.x, roiLR.x, roiLL.x), 0.0, (double)rasterWidth);
        roi.y = (float)atakmap::math::clamp(atakmap::math::min(roiUL.y, roiUR.y, roiLR.y, roiLL.y), 0.0, (double)rasterHeight);
        float right = (float)atakmap::math::clamp(atakmap::math::max(roiUL.x, roiUR.x, roiLR.x, roiLL.x), 0.0, (double)rasterWidth);
        float bottom  = (float)atakmap::math::clamp(atakmap::math::max(roiUL.y, roiUR.y, roiLR.y, roiLL.y), 0.0, (double)rasterHeight);
        roi.width = right - roi.x;
        roi.height = bottom - roi.y;
        
    //    if(roi.width == 0 || roi.height == 0)
    //        roi = null;
    }
    return roi;
}
