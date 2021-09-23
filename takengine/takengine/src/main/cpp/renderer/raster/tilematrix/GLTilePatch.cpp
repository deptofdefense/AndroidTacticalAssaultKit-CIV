#include "renderer/raster/tilematrix/GLTilePatch.h"
#include "core/GeoPoint2.h"
#include "math/Point2.h"
#include "math/Rectangle.h"
#include "math/Utils.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLText2.h"
#include "util/MathUtils.h"

#include <sstream>
#include <string>

using namespace TAK::Engine::Renderer::Raster::TileMatrix;

GLTilePatch::GLTilePatch(GLTiledLayerCore *core, GLZoomLevel *parent, int gridOffsetX, int gridOffsetY, int gridColumns, int gridRows)
    : tiles_(nullptr),
      num_tiles_(gridColumns * gridRows),
      core_(core),
      parent_(parent),
      patchMinLat(0),
      patchMinLng(0),
      patchMaxLat(0),
      patchMaxLng(0),
      gridOffsetX(gridOffsetX),
      gridOffsetY(gridOffsetY),
      gridColumns(gridColumns),
      gridRows(gridRows),
      lastPumpDrawn(-1) {
    tiles_ = new std::shared_ptr<GLTile> [num_tiles_];

    double lodProjTileWidth = parent->info.pixelSizeX * parent->info.tileWidth;
    double lodProjTileHeight = parent->info.pixelSizeY * parent->info.tileHeight;

    double patchProjMinX = core->matrix->getOriginX() + (gridOffsetX * lodProjTileWidth);
    double patchProjMinY = core->matrix->getOriginY() - ((gridOffsetY * lodProjTileWidth) + (lodProjTileHeight * gridRows));
    double patchProjMaxX = core->matrix->getOriginX() + ((gridOffsetX * lodProjTileHeight) + (lodProjTileWidth * gridColumns));
    double patchProjMaxY = core->matrix->getOriginY() - (gridOffsetY * lodProjTileHeight);

    TAK::Engine::Math::Point2<double> scratchD(0, 0, 0);
    TAK::Engine::Core::GeoPoint2 scratchG;

    scratchD.x = patchProjMinX;
    scratchD.y = patchProjMinY;
    core->proj->inverse(&scratchG, scratchD);
    double lat0 = scratchG.latitude;
    double lng0 = scratchG.longitude;
    scratchD.x = patchProjMinX;
    scratchD.y = patchProjMaxY;
    core->proj->inverse(&scratchG, scratchD);
    double lat1 = scratchG.latitude;
    double lng1 = scratchG.longitude;
    scratchD.x = patchProjMaxX;
    scratchD.y = patchProjMaxY;
    core->proj->inverse(&scratchG, scratchD);
    double lat2 = scratchG.latitude;
    double lng2 = scratchG.longitude;
    scratchD.x = patchProjMaxX;
    scratchD.y = patchProjMinY;
    core->proj->inverse(&scratchG, scratchD);
    double lat3 = scratchG.latitude;
    double lng3 = scratchG.longitude;

    patchMinLat = atakmap::math::min(lat0, lat1, lat2, lat3);
    patchMinLng = atakmap::math::min(lng0, lng1, lng2, lng3);
    patchMaxLat = atakmap::math::max(lat0, lat1, lat2, lat3);
    patchMaxLng = atakmap::math::max(lng0, lng1, lng2, lng3);
}

GLTilePatch::~GLTilePatch() {
    release(false, -1);
    delete[] tiles_;
}

TAK::Engine::Util::TAKErr GLTilePatch::batch(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass,
                                             TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS {
    if (!TAK::Engine::Util::MathUtils_hasBits(renderPass, getRenderPass())) return TAK::Engine::Util::TAKErr::TE_Ok;

    double lodProjTileWidth = parent_->info.pixelSizeX * parent_->info.tileWidth;
    double lodProjTileHeight = parent_->info.pixelSizeY * parent_->info.tileHeight;

    // compute patch geo intersect with view
    if (!atakmap::math::Rectangle<double>::intersects(patchMinLng, patchMinLat, patchMaxLng, patchMaxLat, view.renderPass->westBound, view.renderPass->southBound,
                                                      view.renderPass->eastBound, view.renderPass->northBound)) {
        return TAK::Engine::Util::TAKErr::TE_Ok;
    }

    lastPumpDrawn = view.renderPass->renderPump;

    double isectMinLat = atakmap::math::max(patchMinLat, view.renderPass->southBound);
    double isectMinLng = atakmap::math::max(patchMinLng, view.renderPass->westBound);
    double isectMaxLat = atakmap::math::min(patchMinLat, view.renderPass->northBound);
    double isectMaxLng = atakmap::math::min(patchMinLng, view.renderPass->eastBound);

    TAK::Engine::Math::Point2<double> scratchD(0, 0, 0);
    TAK::Engine::Core::GeoPoint2 scratchG;

    // transform patch intersect to proj
    scratchG = TAK::Engine::Core::GeoPoint2(isectMinLat, isectMinLng);
    core_->proj->forward(&scratchD, scratchG);
    double projIsectMinX = scratchD.x;
    double projIsectMinY = scratchD.y;
    scratchG = TAK::Engine::Core::GeoPoint2(isectMaxLat, isectMaxLng);
    core_->proj->forward(&scratchD, scratchG);
    double projIsectMaxX = scratchD.x;
    double projIsectMaxY = scratchD.y;

    // calculate tiles in view
    int minTileDrawCol = (int)((projIsectMinX - core_->matrix->getOriginX()) / lodProjTileWidth);
    int minTileDrawRow = (int)((core_->matrix->getOriginY() - projIsectMinY) / lodProjTileHeight);
    int maxTileDrawCol = (int)((projIsectMaxX - core_->matrix->getOriginX()) / lodProjTileWidth);
    int maxTileDrawRow = (int)((core_->matrix->getOriginY() - projIsectMaxY) / lodProjTileHeight);

    for (int row = minTileDrawRow; row <= maxTileDrawRow; row++) {
        if (row < gridOffsetY || row >= (gridOffsetY + gridRows)) continue;
        for (int col = minTileDrawCol; col <= maxTileDrawCol; col++) {
            if (col < gridOffsetX || col >= (gridOffsetX + gridColumns)) continue;

            int idx = ((row - gridOffsetY) * gridColumns) + (col - gridOffsetX);
            if (tiles_[idx].get() == nullptr) {
                // init tile
                tiles_[idx] = std::make_shared<GLTile>(core_, this, col, row);
            }

            tiles_[idx]->batch(view, renderPass, batch);
        }
    }

    return TAK::Engine::Util::TAKErr::TE_Ok;
}

void GLTilePatch::draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS {
    if (!TAK::Engine::Util::MathUtils_hasBits(renderPass, getRenderPass())) return;

    double lodProjTileWidth = parent_->info.pixelSizeX * parent_->info.tileWidth;
    double lodProjTileHeight = parent_->info.pixelSizeY * parent_->info.tileHeight;

    // compute patch geo intersect with view
    if (!atakmap::math::Rectangle<double>::intersects(patchMinLng, patchMinLat, patchMaxLng, patchMaxLat, view.renderPass->westBound, view.renderPass->southBound,
                                                      view.renderPass->eastBound, view.renderPass->northBound)) {
        return;
    }

    lastPumpDrawn = view.renderPass->renderPump;
    if (core_->debugDraw) debugDraw(view);

    double isectMinLat = atakmap::math::max(patchMinLat, view.renderPass->southBound);
    double isectMinLng = atakmap::math::max(patchMinLng, view.renderPass->westBound);
    double isectMaxLat = atakmap::math::min(patchMaxLat, view.renderPass->northBound);
    double isectMaxLng = atakmap::math::min(patchMaxLng, view.renderPass->eastBound);

    TAK::Engine::Math::Point2<double> scratchD(0, 0, 0);
    TAK::Engine::Core::GeoPoint2 scratchG;

    // transform patch intersect to proj
    scratchG = TAK::Engine::Core::GeoPoint2(isectMinLat, isectMinLng);
    core_->proj->forward(&scratchD, scratchG);
    double projIsectMinX = scratchD.x;
    double projIsectMinY = scratchD.y;
    scratchG = TAK::Engine::Core::GeoPoint2(isectMaxLat, isectMaxLng);
    core_->proj->forward(&scratchD, scratchG);
    double projIsectMaxX = scratchD.x;
    double projIsectMaxY = scratchD.y;

    // calculate tiles in view
    int minTileDrawCol = (int)((projIsectMinX - core_->matrix->getOriginX()) / lodProjTileWidth);
    int minTileDrawRow = (int)((core_->matrix->getOriginY() - projIsectMaxY) / lodProjTileHeight);
    int maxTileDrawCol = (int)((projIsectMaxX - core_->matrix->getOriginX()) / lodProjTileWidth);
    int maxTileDrawRow = (int)((core_->matrix->getOriginY() - projIsectMinY) / lodProjTileHeight);

    for (int row = minTileDrawRow; row <= maxTileDrawRow; row++) {
        if (row < gridOffsetY || row >= (gridOffsetY + gridRows)) continue;
        for (int col = minTileDrawCol; col <= maxTileDrawCol; col++) {
            if (col < gridOffsetX || col >= (gridOffsetX + gridColumns)) continue;

            int idx = ((row - gridOffsetY) * gridColumns) + (col - gridOffsetX);
            if (tiles_[idx].get() == nullptr) {
                // init tile
                tiles_[idx] = std::make_shared<GLTile>(core_, this, col, row);
            }

            tiles_[idx]->draw(view, renderPass);
        }
    }
}

void GLTilePatch::debugDraw(const TAK::Engine::Renderer::Core::GLGlobeBase &view) {
    float dbg[8];
    TAK::Engine::Math::Point2<float> scratchF(0, 0, 0);
    TAK::Engine::Core::GeoPoint2 scratchG;

    scratchG = TAK::Engine::Core::GeoPoint2(patchMinLat, patchMinLng);
    view.renderPass->scene.forward(&scratchF, scratchG);
    dbg[0] = scratchF.x;
    dbg[1] = scratchF.y;
    scratchG = TAK::Engine::Core::GeoPoint2(patchMaxLat, patchMinLng);
    view.renderPass->scene.forward(&scratchF, scratchG);
    dbg[2] = scratchF.x;
    dbg[3] = scratchF.y;
    scratchG = TAK::Engine::Core::GeoPoint2(patchMaxLat, patchMaxLng);
    view.renderPass->scene.forward(&scratchF, scratchG);
    dbg[4] = scratchF.x;
    dbg[5] = scratchF.y;
    scratchG = TAK::Engine::Core::GeoPoint2(patchMinLat, patchMaxLng);
    view.renderPass->scene.forward(&scratchF, scratchG);
    dbg[6] = scratchF.x;
    dbg[7] = scratchF.y;

    atakmap::renderer::GLES20FixedPipeline *fixedPipe = atakmap::renderer::GLES20FixedPipeline::getInstance();
    fixedPipe->glColor4f(1.0f, 0.0f, 0.0f, 1.0f);

    fixedPipe->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
    fixedPipe->glLineWidth(3.0f);
    fixedPipe->glVertexPointer(2, GL_FLOAT, 0, dbg);
    fixedPipe->glDrawArrays(GL_LINE_LOOP, 0, 4);
    fixedPipe->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);

    scratchG = TAK::Engine::Core::GeoPoint2(patchMaxLat, patchMinLng);
    view.renderPass->scene.forward(&scratchF, scratchG);
    fixedPipe->glPushMatrix();
    fixedPipe->glTranslatef(scratchF.x + 5, scratchF.y - 40, 0.0f);

    TextFormat2Ptr txtfmt(nullptr, nullptr);
    TextFormat2_createDefaultSystemTextFormat(txtfmt, 14);
    GLText2 txt(std::move(txtfmt));
    std::stringstream ss;
    ss << "patch " << parent_->info.level << "," << gridOffsetX << "," << gridOffsetY;
    std::string s = ss.str();
    txt.draw(s.c_str(), 1.0f, 0.0f, 0.0f, 1.0f);
    fixedPipe->glPopMatrix();
}

bool GLTilePatch::release(bool unusedOnly, int renderPump) {
    bool cleared = true;
    for (int i = 0; i < num_tiles_; i++) {
        if (tiles_[i].get() != nullptr && (!unusedOnly || !tiles_[i]->hasBorrowers()) && renderPump != tiles_[i]->lastPumpDrawn) {
            tiles_[i]->release();
            tiles_[i].reset();
        } else if (tiles_[i].get() != nullptr) {
            cleared = false;
        }
    }
    return cleared;
}

void GLTilePatch::release() NOTHROWS { release(false, -1); }

int GLTilePatch::getRenderPass() NOTHROWS { return Core::GLGlobeBase::Surface; }

void GLTilePatch::start() NOTHROWS {}
void GLTilePatch::stop() NOTHROWS {}

void GLTilePatch::getTiles(std::set<std::shared_ptr<GLTile>> *tiles, double minX, double minY, double maxX, double maxY) {
    double lodProjTileWidth = parent_->info.pixelSizeX * parent_->info.tileWidth;
    double lodProjTileHeight = parent_->info.pixelSizeY * parent_->info.tileHeight;

    // calculate tiles in view
    int minTileDrawCol = (int)((minX - core_->matrix->getOriginX()) / lodProjTileWidth);
    int minTileDrawRow = (int)((core_->matrix->getOriginY() - maxY) / lodProjTileHeight);
    int maxTileDrawCol = (int)((maxX - core_->matrix->getOriginX()) / lodProjTileWidth);
    int maxTileDrawRow = (int)((core_->matrix->getOriginY() - minY) / lodProjTileHeight);

    for (int row = minTileDrawRow; row <= maxTileDrawRow; row++) {
        if (row < gridOffsetY || row >= (gridOffsetY + gridRows)) continue;
        for (int col = minTileDrawCol; col <= maxTileDrawCol; col++) {
            if (col < gridOffsetX || col >= (gridOffsetX + gridColumns)) continue;

            int idx = ((row - gridOffsetY) * gridColumns) + (col - gridOffsetX);
            if (this->tiles_[idx].get() == nullptr) {
                // XXX - check if texture is cached
                std::string textureKey = GLTile::getTileTextureKey(*core_, parent_->info.level, col, row);
                const GLTextureCache2::Entry *cacheEnt;
                if (core_->textureCache->get(&cacheEnt, textureKey.c_str()) == TAK::Engine::Util::TE_Ok) {
                    // init tile
                    this->tiles_[idx] = std::make_shared<GLTile>(core_, this, col, row);
                    tiles->insert(this->tiles_[idx]);
                }
            } else if (this->tiles_[idx]->hasTexture()) {
                tiles->insert(this->tiles_[idx]);
            }
        }
    }
}

const GLZoomLevel *GLTilePatch::getParent() {
    return parent_;
}
