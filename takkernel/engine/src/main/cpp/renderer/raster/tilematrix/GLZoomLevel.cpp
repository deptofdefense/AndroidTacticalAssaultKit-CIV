#include "math/Utils.h"
#include "math/Rectangle.h"
#include "util/MathUtils.h"
#include "raster/tilematrix/TileMatrix.h"
#include "renderer/raster/tilematrix/GLZoomLevel.h"
#include "renderer/raster/tilematrix/GLTilePatch.h"

using namespace TAK::Engine::Renderer::Raster::TileMatrix;


GLZoomLevel::GLZoomLevel(GLZoomLevel *prev, GLTiledLayerCore *core, const TAK::Engine::Raster::TileMatrix::TileMatrix::ZoomLevel &lod) : 
            core(core), patches(), patchRows(4), patchCols(4), 
            patchesGridOffsetX(0), patchesGridOffsetY(0), numPatchesX(0), numPatchesY(0),
            info(lod),
            previous(prev),
            tileMeshSubdivisions(0),
            lastPumpDrawn(-1)
{
    Math::Point2<double> minTile, maxTile;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&minTile, *(core->matrix), info.level, core->fullExtent.minX,
                                                             core->fullExtent.maxY);
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&maxTile, *(core->matrix), info.level, core->fullExtent.maxX,
                                                             core->fullExtent.minY);

    int minPatchGridCol = static_cast<int>(minTile.x / patchCols);
    int minPatchGridRow = static_cast<int>(minTile.y / patchRows);
    int maxPatchGridCol = static_cast<int>((maxTile.x / patchCols) + 1);
    int maxPatchGridRow = static_cast<int>((maxTile.y / patchRows) + 1);
                
    patchesGridOffsetX = minPatchGridCol;
    patchesGridOffsetY = minPatchGridRow;
    numPatchesX = maxPatchGridCol-minPatchGridCol;
    numPatchesY = maxPatchGridRow-minPatchGridRow;
        
    int ntx = static_cast<int>(maxTile.x - minTile.x + 1);
    int nty = static_cast<int>(maxTile.y - minTile.y + 1);
        
    tileMeshSubdivisions = (int)ceil((double)GLTileMesh::estimateSubdivisions(core->fullExtentMaxLat, core->fullExtentMinLng,
                                                                              core->fullExtentMinLat, core->fullExtentMinLng) /
                                     (double)atakmap::math::max(ntx, nty));
}

GLZoomLevel::~GLZoomLevel() { release(); }

TAK::Engine::Util::TAKErr GLZoomLevel::batch(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass,
                                TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS {
    if (!TAK::Engine::Util::MathUtils_hasBits(renderPass, getRenderPass()))
        return TAK::Engine::Util::TE_Ok;
        
    // compute patch geo intersect with view
    if (!atakmap::math::Rectangle<double>::intersects(core->fullExtentMinLng, core->fullExtentMinLat, core->fullExtentMaxLng,
                                              core->fullExtentMaxLat,
                                view.renderPass->westBound, view.renderPass->southBound,
                                view.renderPass->eastBound, view.renderPass->northBound)) {

        return TAK::Engine::Util::TE_Ok;
    }

    lastPumpDrawn = -1;
        
    const double isectMinLat = atakmap::math::max(core->fullExtentMinLat, view.renderPass->southBound);
    const double isectMinLng = atakmap::math::max(core->fullExtentMinLng, view.renderPass->westBound);
    const double isectMaxLat = atakmap::math::min(core->fullExtentMinLat, view.renderPass->northBound);
    const double isectMaxLng = atakmap::math::min(core->fullExtentMinLng, view.renderPass->eastBound);
        
    // transform patch intersect to proj
    Math::Point2<double> pointD;
    TAK::Engine::Core::GeoPoint2 geo;

    geo = TAK::Engine::Core::GeoPoint2(isectMinLat, isectMinLng);
    core->proj->forward(&pointD, geo);
    double projIsectMinX = pointD.x;
    double projIsectMinY = pointD.y;
    geo = TAK::Engine::Core::GeoPoint2(isectMaxLat, isectMaxLng);
    core->proj->forward(&pointD, geo);
    double projIsectMaxX = pointD.x;
    double projIsectMaxY = pointD.y;

    // calculate tiles in view
    Math::Point2<double> minPatch;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&minPatch, 
                                                 core->matrix->getOriginX(),
                                                 core->matrix->getOriginY(),
                                                 info,
                                                 projIsectMinX,
                                                 projIsectMaxY);
    minPatch.x /= patchCols;
    minPatch.y /= patchRows;
    Math::Point2<double> maxPatch;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&maxPatch,
                                                 core->matrix->getOriginX(),
                                                 core->matrix->getOriginY(),
                                                 info,
                                                 projIsectMaxX,
                                                 projIsectMinY);
    maxPatch.x /= patchCols;
    maxPatch.y /= patchRows;

    std::map<int, GLTilePatch *>::iterator iter;
    for (int patchY = static_cast<int>(minPatch.y); patchY <= maxPatch.y; patchY++) {
        if(patchY < patchesGridOffsetY || patchY >= (patchesGridOffsetY+numPatchesY))
            continue;
        for(int patchX = static_cast<int>(minPatch.x); patchX <= maxPatch.x; patchX++) {
            if(patchX < patchesGridOffsetX || patchX >= (patchesGridOffsetX+numPatchesX))
                continue;
                
            int idx = ((patchY-patchesGridOffsetY)*numPatchesX) + (patchX-patchesGridOffsetX);
            iter = patches.find(idx);
            GLTilePatch *patch;
            if (iter == patches.end()) {
                patch = new GLTilePatch(core,
                        this,
                        patchesGridOffsetX+patchX*patchCols,
                        patchesGridOffsetY+patchY*patchRows,
                        atakmap::math::min(patchCols, (patchesGridOffsetX+numPatchesX)-(patchX*patchCols)),
                        atakmap::math::min(patchRows, (patchesGridOffsetY + numPatchesY) - (patchY * patchRows)));
                patches[idx] = patch;
            } else {
                patch = iter->second;
            }

            patch->batch(view, renderPass, batch);
        }
    }

    return TAK::Engine::Util::TE_Ok;
}

void GLZoomLevel::draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS {
    if (!TAK::Engine::Util::MathUtils_hasBits(renderPass, getRenderPass()))
        return;

    // compute patch geo intersect with view
    if (!atakmap::math::Rectangle<double>::intersects(core->fullExtentMinLng, core->fullExtentMinLat,
                                core->fullExtentMaxLng, core->fullExtentMaxLat,
                                view.renderPass->westBound, view.renderPass->southBound,
                                view.renderPass->eastBound, view.renderPass->northBound)) {

        return;
    }
        

    lastPumpDrawn = view.renderPass->renderPump;

    const double isectMinLat = atakmap::math::max(core->fullExtentMinLat, view.renderPass->southBound);
    const double isectMinLng = atakmap::math::max(core->fullExtentMinLng, view.renderPass->westBound);
    const double isectMaxLat = atakmap::math::min(core->fullExtentMaxLat, view.renderPass->northBound);
    const double isectMaxLng = atakmap::math::min(core->fullExtentMaxLng, view.renderPass->eastBound);
        
    // transform patch intersect to proj
    Math::Point2<double> pointD;
    TAK::Engine::Core::GeoPoint2 geo;
    geo = TAK::Engine::Core::GeoPoint2(isectMinLat, isectMinLng);
    core->proj->forward(&pointD, geo);
    double projIsectMinX = pointD.x;
    double projIsectMinY = pointD.y;
    geo = TAK::Engine::Core::GeoPoint2(isectMaxLat, isectMaxLng);
    core->proj->forward(&pointD, geo);
    double projIsectMaxX = pointD.x;
    double projIsectMaxY = pointD.y;

    // calculate tiles in view
    Math::Point2<double> minPatch;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&minPatch, 
                                                    core->matrix->getOriginX(),
                                                    core->matrix->getOriginY(),
                                                    info,
                                                    projIsectMinX,
                                                    projIsectMaxY);
    minPatch.x /= patchCols;
    minPatch.y /= patchRows;
    Math::Point2<double> maxPatch;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&maxPatch,
                                                    core->matrix->getOriginX(),
                                                    core->matrix->getOriginY(),
                                                    info,
                                                    projIsectMaxX,
                                                    projIsectMinY);
    maxPatch.x /= patchCols;
    maxPatch.y /= patchRows;

    std::map<int, GLTilePatch *>::iterator iter;
    for(int patchY = static_cast<int>(minPatch.y); patchY <= maxPatch.y; patchY++) {
        if(patchY < patchesGridOffsetY || patchY >= (patchesGridOffsetY+numPatchesY))
            continue;
        for(int patchX = static_cast<int>(minPatch.x); patchX <= maxPatch.x; patchX++) {
            if(patchX < patchesGridOffsetX || patchX >= (patchesGridOffsetX+numPatchesX))
                continue;
                
            int idx = ((patchY-patchesGridOffsetY)*numPatchesX) + (patchX-patchesGridOffsetX); 
            GLTilePatch *patch;
            iter = patches.find(idx);
            if(iter == patches.end()) {
                // XXX - should clamp number of patches against full extent?
                patch = new GLTilePatch(core,
                        this,
                        patchX*patchCols,
                        patchY*patchRows,
                        patchCols, patchRows);
                patches[idx] = patch;
            } else {
                patch = iter->second;
            }

            patch->draw(view, renderPass);
        }
    }
}

void GLZoomLevel::release() NOTHROWS { release(false, -1); }

int GLZoomLevel::getRenderPass() NOTHROWS { return Core::GLMapView2::Surface; }

void GLZoomLevel::start() NOTHROWS {}
void GLZoomLevel::stop() NOTHROWS {}


void GLZoomLevel::getTiles(std::set<std::shared_ptr<GLTile>> *tiles, double minX, double minY, double maxX, double maxY) {
    // calculate tiles in view
    Math::Point2<double> minPatch;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&minPatch, 
                                                    core->matrix->getOriginX(),
                                                    core->matrix->getOriginY(),
                                                    info,
                                                    minX,
                                                    maxY);
    minPatch.x /= patchCols;
    minPatch.y /= patchRows;
    Math::Point2<double> maxPatch;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileIndex(&maxPatch, 
                                                    core->matrix->getOriginX(),
                                                    core->matrix->getOriginY(),
                                                    info,
                                                    maxX,
                                                    minY);
    maxPatch.x /= patchCols;
    maxPatch.y /= patchRows;
                
    for (int patchY = static_cast<int>(minPatch.y); patchY <= maxPatch.y; patchY++) {
        if(patchY < patchesGridOffsetY || patchY >= (patchesGridOffsetY+numPatchesY))
            continue;
        for(int patchX = static_cast<int>(minPatch.x); patchX <= maxPatch.x; patchX++) {
            if(patchX < patchesGridOffsetX || patchX >= (patchesGridOffsetX+numPatchesX))
                continue;
                
            int idx = ((patchY-patchesGridOffsetY)*numPatchesX) + (patchX-patchesGridOffsetX); 
            GLTilePatch *patch = nullptr;
            auto iter = patches.find(idx);
            if (iter == patches.end()) {
                patch = new GLTilePatch(core,
                        this,
                        patchX*patchCols,
                        patchY*patchRows,
                        patchCols, patchRows);
                patches[idx] = patch;
            } else {
                patch = iter->second;
            }
            if(patch != nullptr)
                patch->getTiles(tiles, minX, minY, maxX, maxY);
        }
    }
}

bool GLZoomLevel::release(bool unusedOnly, int renderPump) {
    std::map<int, GLTilePatch *>::iterator iter;
    if (!unusedOnly) {
        for (iter = patches.begin(); iter != patches.end(); ++iter) {
            iter->second->release();
            delete iter->second;
        }
        patches.clear();
        return true;
    } else {
        iter = patches.begin();
        while (iter != patches.end()) {
            auto curIter = iter;
            ++iter;
            if (curIter->second->release(unusedOnly, renderPump)) {
                delete curIter->second;
                patches.erase(curIter);
            }
        }
        return patches.empty();
    }
}
