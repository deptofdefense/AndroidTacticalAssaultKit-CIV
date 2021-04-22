#include "renderer/raster/tilematrix/GLTile.h"

#include "feature/GeometryTransformer.h"
#include "renderer/core/controls/SurfaceRendererControl.h"
#include "renderer/raster/tilematrix/GLTilePatch.h"
#include "renderer/raster/tilematrix/GLZoomLevel.h"
#include "math/Point2.h"
#include "math/Rectangle.h"
#include "math/Utils.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLText2.h"
#include "util/MathUtils.h"
#include "util/Logging2.h"

#include <list>
#include <sstream>
#include <string>

using namespace TAK::Engine::Renderer::Raster::TileMatrix;

GLTile::GLTile(GLTiledLayerCore *core, GLTilePatch *patch, int tileX, int tileY)
    : texturePtr(nullptr, TAK::Engine::Util::Memory_deleter_const<GLTexture2>),
      proj2uv(),
      projMinX(0),
      projMinY(0),
      projMaxX(0),
      projMaxY(0),
      state(State::UNRESOLVED),
      vertexCount(0),
      textureCoordsPtr(nullptr, TAK::Engine::Util::Memory_array_deleter_const<float>),
      vertexCoordsPtr(nullptr, TAK::Engine::Util::Memory_array_deleter_const<float>),
      core(core),
      patch(patch),
      mesh(nullptr),
      tileX(tileX),
      tileY(tileY),
      tileZ(patch->getParent()->info.level),
      textureKey(),
      borrowers(),
      tileVersion(-1),
      lastPumpDrawn(-1) {
    Feature::Envelope2 tileBounds;
    TAK::Engine::Raster::TileMatrix::TileMatrix_getTileBounds(&tileBounds, *(core->matrix), tileZ, tileX, tileY);
    projMinX = tileBounds.minX;
    projMinY = tileBounds.minY;
    projMaxX = tileBounds.maxX;
    projMaxY = tileBounds.maxY;

    textureKey = getTileTextureKey(*core, tileZ, tileX, tileY);

    // XXX - better way to do this
    GLTexture2 scratchTex(patch->getParent()->info.tileWidth, patch->getParent()->info.tileHeight, 0, 0);

    const float u0 = 0.0f;
    const float v0 = 0.0f;
    const float u1 = (float)patch->getParent()->info.tileWidth / (float)scratchTex.getTexWidth();
    const float v1 = (float)patch->getParent()->info.tileHeight / (float)scratchTex.getTexHeight();

    Math::Matrix2_mapQuads(&proj2uv, projMinX, projMaxY, projMaxX, projMaxY, projMaxX, projMinY, projMinX, projMinY, u0, v0, u1, v0, u1, v1,
                           u0, v1);

    mesh = new GLTileMesh(Math::Point2<double>(projMinX, projMaxY), Math::Point2<double>(projMaxX, projMaxY),
                          Math::Point2<double>(projMaxX, projMinY), Math::Point2<double>(projMinX, projMinY), proj2uv, core->proj2geo,
                          patch->getParent()->tileMeshSubdivisions);
}

GLTile::~GLTile() NOTHROWS {
    unborrow();
    delete mesh;
}

bool GLTile::hasBorrowers() { return !borrowers.empty(); }

bool GLTile::hasTexture() { return texturePtr.get() != nullptr; }

void GLTile::tryBorrow() {
    typedef atakmap::math::Rectangle<double> RectD;

    // the candidates we will try to borrow from
    std::set<std::shared_ptr<GLTile>> candidates;

    // a list of the "holes" that need to be filled via texture borrowing
    std::list<RectD> holes;

    // seed with the bounds of this tile
    holes.push_back(RectD(projMinX, projMinY, projMaxX - projMinX, projMaxY - projMinY));

    // a list of "bits" left over from holes that we have subtracted the
    // intersection with a candidate from
    std::list<RectD> bits;

    // the remainders of subtracting a candidates bounds from a hole
    RectD remainders[4];

    // we will start borrowing from the previous zoom level
    GLZoomLevel *borrowLvl = patch->getParent()->previous;

    // while there are holes to be filled and we have a level to gather
    // candidates from, search for textures to borrow
    while (!holes.empty() && borrowLvl != nullptr) {
        // discover all tiles in the level that intersect this tile's bounds
        borrowLvl->getTiles(&candidates, projMinX, projMinY, projMaxX, projMaxY);

        std::set<std::shared_ptr<GLTile>>::iterator iter;
        for (iter = candidates.begin(); iter != candidates.end(); iter++) {
            std::shared_ptr<GLTile> candidate = *iter;

            // ensure we can borrow from the tile
            if (candidate->state == UNRESOLVABLE) continue;

            bits.clear();

            auto holesIter = holes.begin();
            while (holesIter != holes.end()) {
                RectD hole = *holesIter;
                auto curHolesIter = holesIter;
                holesIter++;

                if (RectD::intersects(hole.x, hole.y, hole.x + hole.width, hole.y + hole.height, candidate->projMinX, candidate->projMinY,
                                      candidate->projMaxX, candidate->projMaxY)) {
                    // compute the intersection
                    const double isectMinX = atakmap::math::max(hole.x, candidate->projMinX);
                    const double isectMinY = atakmap::math::max(hole.y, candidate->projMinY);
                    const double isectMaxX = atakmap::math::min(hole.x + hole.width, candidate->projMaxX);
                    const double isectMaxY = atakmap::math::min(hole.y + hole.height, candidate->projMaxY);

                    // if the intersection is less than a pixel in either
                    // dimension, ignore it
                    if ((isectMaxX - isectMinX) <= candidate->patch->getParent()->info.pixelSizeX ||
                        (isectMaxY - isectMinY) < candidate->patch->getParent()->info.pixelSizeY)
                        continue;

                    // subtract the coverage of the candidate tile from the
                    // hole. each remainder will become a new hole.
                    int num = RectD::subtract(hole.x, hole.y, hole.x + hole.width, hole.y + hole.height, candidate->projMinX,
                                              candidate->projMinY, candidate->projMaxX, candidate->projMaxY, remainders);
                    for (int i = 0; i < num; i++) {
                        // ignore any remainders less than a pixel in either
                        // dimension
                        if (remainders[i].width <= candidate->patch->getParent()->info.pixelSizeX ||
                            remainders[i].height <= candidate->patch->getParent()->info.pixelSizeY)
                            continue;
                        bits.push_back(remainders[i]);
                    }

                    // add a borrow record for the intersection
                    borrowRecords.insert(new BorrowRecord(this, candidate, isectMinX, isectMinY, isectMaxX, isectMaxY));

                    // remove the old hole
                    holes.erase(curHolesIter);
                }
            }

            // add all the remainders
            holes.insert(holes.end(), bits.begin(), bits.end());
        }

        // clear the candidates list for the current level and move to the
        // previous level
        candidates.clear();
        borrowLvl = borrowLvl->previous;
    }
}

void GLTile::unborrow() {
    std::set<BorrowRecord *>::iterator iter;
    for (iter = borrowRecords.begin(); iter != borrowRecords.end(); ++iter) {
        BorrowRecord *record = *iter;
        delete record;
    }
    borrowRecords.clear();
}

bool GLTile::checkForCachedTexture() {
    if (core->textureCache == nullptr) return false;
    GLTextureCache2::EntryPtr entry(nullptr, nullptr);
    Util::TAKErr err = core->textureCache->remove(entry, textureKey.c_str());
    if (err != Util::TE_Ok) return false;
    texturePtr = std::move(entry->texture);
    textureCoordsPtr = std::move(entry->textureCoordinates);
    vertexCoordsPtr = std::move(entry->vertexCoordinates);
    vertexCount = entry->numVertices;
    if (entry->opaqueSize >= sizeof(int)) {
        tileVersion = *(static_cast<const int *>(entry->opaque.get()));
    } else {
        tileVersion = -1;
    }
    state = RESOLVED;
    return true;
}

bool GLTile::renderCommon(const TAK::Engine::Renderer::Core::GLGlobeBase &view, int renderPass) {
    if (!TAK::Engine::Util::MathUtils_hasBits(renderPass, getRenderPass())) return false;

    lastPumpDrawn = view.renderPass->renderPump;

    do {
        if (this->state == State::UNRESOLVED) {
            // check cache
            if (checkForCachedTexture()) break;

            // kick off tile load
            pendingTextureContext.reset(new BitmapLoadContext(true, core->matrix, view, tileX, tileY, tileZ, core->tileDrawVersion));
            pendingTextureTask.reset(new atakmap::util::FutureTask<std::shared_ptr<Bitmap2>>(
                BitmapLoadContext::load, new std::shared_ptr<BitmapLoadContext>(pendingTextureContext)));
            core->bitmapLoader->loadBitmapTask(pendingTextureTask, "REMOTE");

            this->state = State::RESOLVING;

            tryBorrow();
            continue;
        } else if (this->state == State::RESOLVING) {
            // check if loading is completed, transition to RESOLVED or
            // UNRESOLVABLE as appropriate
            if (pendingTextureTask->getFuture().isDone()) {
                try {
                    std::shared_ptr<Bitmap2> bitmap = pendingTextureTask->getFuture().get();
                    if (bitmap.get() != nullptr) {
                        if (hasTexture()) {
                            texturePtr->release();
                            texturePtr.reset();
                        }

                        texturePtr.reset(new GLTexture2(static_cast<int>(bitmap->getWidth()), static_cast<int>(bitmap->getHeight()), bitmap->getFormat()));
                        texturePtr->load(*bitmap);
                        tileVersion = pendingTextureContext->tileDrawVersion;

                        float u0 = 0.0f;
                        float v0 = 0.0f;
                        float u1 = (float)bitmap->getWidth() / texturePtr->getTexWidth();
                        float v1 = (float)bitmap->getHeight() / texturePtr->getTexHeight();

                        auto *buf = new float[8];

                        buf[0] = u0;  // lower-left
                        buf[1] = v1;
                        buf[2] = u0;  // upper-left
                        buf[3] = v0;
                        buf[4] = u1;  // lower-right
                        buf[5] = v1;
                        buf[6] = u1;  // upper-right
                        buf[7] = v0;
                        textureCoordsPtr.reset(buf);

                        buf = new float[12];
                        vertexCoordsPtr.reset(buf);
                        vertexCount = 4;

                        unborrow();

                        this->state = State::RESOLVED;
                    } else {
                        this->state = pendingTextureTask->getCancelationToken().isCanceled() ? State::UNRESOLVED : State::UNRESOLVABLE;
                    }
                } catch (...) {
                    Util::Logger_log(Util::LogLevel::TELL_Warning, "Failed to load tile [%d,%d,%d] from %s", tileZ, tileX, tileY,
                                     core->matrix->getName());
                    this->state = State::UNRESOLVABLE;
                    this->tileVersion = pendingTextureContext->tileDrawVersion;
                } 
                pendingTextureTask.reset();
                pendingTextureContext.reset();
            }
        }
        break;
    } while (true);

    // XXX - allow for texture borrowing
    if ((this->state == State::UNRESOLVABLE || this->state == State::RESOLVED) && this->tileVersion != core->tileDrawVersion) {
        // kick off tile load
        pendingTextureContext.reset(new BitmapLoadContext(false, core->matrix, view, tileX, tileY, tileZ, core->tileDrawVersion));
        pendingTextureTask.reset(new atakmap::util::FutureTask<std::shared_ptr<Bitmap2>>(
            BitmapLoadContext::load, new std::shared_ptr<BitmapLoadContext>(pendingTextureContext)));
        core->bitmapLoader->loadBitmapTask(pendingTextureTask, "REMOTE");

        this->state = State::RESOLVING;
    }

    if (this->state != State::RESOLVED) {
        return hasTexture();
    }

    // XXX -  as necessary
    // XXX - mesh

    // update vertex coords
    TAK::Engine::Core::GeoPoint2 geo;
    Math::Point2<double> pointD;
    float *vCoords = vertexCoordsPtr.get();

    pointD.x = projMinX;  // lower-left
    pointD.y = projMinY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointD, geo);
    *vCoords = static_cast<float>(pointD.x);
    vCoords++;
    *vCoords = static_cast<float>(pointD.y);
    vCoords++;
    *vCoords = static_cast<float>(pointD.z);
    vCoords++;
    pointD.x = projMinX;  // upper-left
    pointD.y = projMaxY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointD, geo);
    *vCoords = static_cast<float>(pointD.x);
    vCoords++;
    *vCoords = static_cast<float>(pointD.y);
    vCoords++;
    *vCoords = static_cast<float>(pointD.z);
    vCoords++;
    pointD.x = projMaxX;  // lower-right
    pointD.y = projMinY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointD, geo);
    *vCoords = static_cast<float>(pointD.x);
    vCoords++;
    *vCoords = static_cast<float>(pointD.y);
    vCoords++;
    *vCoords = static_cast<float>(pointD.z);
    vCoords++;
    pointD.x = projMaxX;  // upper-right
    pointD.y = projMaxY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointD, geo);
    *vCoords = static_cast<float>(pointD.x);
    vCoords++;
    *vCoords = static_cast<float>(pointD.y);
    vCoords++;
    *vCoords = static_cast<float>(pointD.z);
    vCoords++;

    return true;
}

TAK::Engine::Util::TAKErr GLTile::batch(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass,
                                        TAK::Engine::Renderer::GLRenderBatch2 &batch) NOTHROWS {
    if (!renderCommon(view, renderPass)) {
        // XXX - borrowed texture support

        return Util::TE_Ok;
    }

    // XXX - mesh

    return batch.batch(texturePtr->getTexId(), GL_TRIANGLE_STRIP, vertexCount, 3, 0, vertexCoordsPtr.get(), 0, textureCoordsPtr.get(), core->r,
                core->g, core->b, core->a);
}

void GLTile::debugDraw(const TAK::Engine::Renderer::Core::GLGlobeBase &view) {
    float dbg[8];
    TAK::Engine::Core::GeoPoint2 geo;
    Math::Point2<double> pointD;
    Math::Point2<float> pointF;
    pointD.x = projMinX;
    pointD.y = projMinY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[0] = pointF.x;
    dbg[1] = pointF.y;
    pointD.x = projMinX;
    pointD.y = projMaxY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[2] = pointF.x;
    dbg[3] = pointF.y;
    pointD.x = projMaxX;
    pointD.y = projMaxY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[4] = pointF.x;
    dbg[5] = pointF.y;
    pointD.x = projMaxX;
    pointD.y = projMinY;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    dbg[6] = pointF.x;
    dbg[7] = pointF.y;

    atakmap::renderer::GLES20FixedPipeline *fixedPipe = atakmap::renderer::GLES20FixedPipeline::getInstance();
    fixedPipe->glColor4f(0.0f, 1.0f, 0.0f, 1.0f);

    fixedPipe->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
    fixedPipe->glLineWidth(2.0f);
    fixedPipe->glVertexPointer(2, GL_FLOAT, 0, dbg);
    fixedPipe->glDrawArrays(GL_LINE_LOOP, 0, 4);
    fixedPipe->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);

    pointD.x = (projMinX + projMaxX) / 2.0;
    pointD.y = (projMinY + projMaxY) / 2.0;
    pointD.z = 0.0;
    core->proj->inverse(&geo, pointD);
    view.renderPass->scene.forward(&pointF, geo);
    fixedPipe->glPushMatrix();
    fixedPipe->glTranslatef(pointF.x + 5, pointF.y + 20, 0.0f);

    TextFormat2Ptr txtfmt(nullptr, nullptr);
    TextFormat2_createDefaultSystemTextFormat(txtfmt, 14);
    std::shared_ptr<TextFormat2> txtfmts(std::move(txtfmt));
    GLText2 *txt = GLText2_intern(txtfmts);

    std::stringstream ss;
    ss << "tile " << tileZ << "," << tileX << "," << tileY;
    std::string s = ss.str();
    txt->draw(s.c_str(), 0.0f, 1.0f, 0.0f, 1.0f);

    fixedPipe->glPopMatrix();
}

void GLTile::draw(const TAK::Engine::Renderer::Core::GLGlobeBase &view, const int renderPass) NOTHROWS {
    if (!renderCommon(view, renderPass)) {
        if (TAK::Engine::Util::MathUtils_hasBits(renderPass, getRenderPass())) {
            do {
                bool rebuildBorrowList = false;
                std::set<BorrowRecord *>::iterator iter;
                for (iter = borrowRecords.begin(); iter != borrowRecords.end(); ++iter) {
                    BorrowRecord *record = *iter;
                    if (!record->from->hasTexture()) {
                        if (record->from->state != State::UNRESOLVABLE) {
                            // check for a cached texture, and kick off a
                            // tile load if necessary
                            if (!record->from->checkForCachedTexture() &&
                                (state == State::UNRESOLVABLE && record->from->state == State::UNRESOLVED)) {
                                record->from->renderCommon(view, renderPass);
                            }
                        } else {
                            // if the tile we are borrowing from is
                            // unresolvable, we need to rebuild the list
                            rebuildBorrowList = true;
                            break;
                        }
                        if (!record->from->hasTexture()) continue;
                    }

                    record->mesh->drawMesh(view, record->from->texturePtr->getTexId(), core->r, core->g, core->b, core->a);
                }
                if (rebuildBorrowList) {
                    // rebuild the list and loop through again
                    unborrow();
                    tryBorrow();
                } else {
                    // list is current; all borrowed textures have been
                    // drawn
                    break;
                }
            } while (true);

            if (core->debugDraw) debugDraw(view);
        }
        return;
    }

    // draw the textured mesh
    mesh->drawMesh(view, texturePtr->getTexId(), core->r, core->g, core->b, core->a);
    if (core->debugDraw) debugDraw(view);
}

void GLTile::release() NOTHROWS {
    if (hasTexture()) {
        if (core->textureCache != nullptr) {
            int *i = new int(tileVersion);
            GLTextureCache2::Entry::OpaquePtr opaque(i, Util::Memory_void_deleter_const<int>);
            GLTextureCache2::EntryPtr entry(new GLTextureCache2::Entry(std::move(texturePtr), std::move(textureCoordsPtr),
                                                                       std::move(vertexCoordsPtr), vertexCount, 0, std::move(opaque)),
                                            Util::Memory_deleter_const<GLTextureCache2::Entry>);
            core->textureCache->put(textureKey.c_str(), std::move(entry));
        } else {
            texturePtr->release();
            texturePtr.reset();
        }
    }

    if (pendingTextureTask.get() != nullptr) {
        pendingTextureTask.reset();
        pendingTextureContext->veto();
        pendingTextureContext.reset();
    }

    // XXX - do we want to make unresolvable sticky?
    // if(this.state != State.UNRESOLVABLE)
    state = State::UNRESOLVED;

    textureCoordsPtr.reset();
    vertexCoordsPtr.reset();
    vertexCount = 0;

    mesh->release();

    unborrow();
}

int GLTile::getRenderPass() NOTHROWS { return Core::GLMapView2::Surface; }

void GLTile::start() NOTHROWS {}
void GLTile::stop() NOTHROWS {}


GLTile::State GLTile::getState() { return state; }

void GLTile::suspend() {
    if (state == State::UNRESOLVED) state = State::SUSPENDED;
}

void GLTile::resume() {
    if (state == State::SUSPENDED) state = State::UNRESOLVED;
}

std::string GLTile::getTileTextureKey(const GLTiledLayerCore &core, int zoom, int tileX, int tileY) {
    std::stringstream ss;
    ss << core.clientSourceUri << "{" << zoom << "," << tileX << "," << tileY << "}";
    return ss.str();
}

GLTile::BitmapLoadContext::BitmapLoadContext(bool refreshOnComplete, std::shared_ptr<TAK::Engine::Raster::TileMatrix::TileMatrix> &matrix,
                                             const TAK::Engine::Renderer::Core::GLGlobeBase &view, int tileX, int tileY, int tileZ,
                                             int tileDrawVersion)
    : vetoed(false),
      refreshOnComplete(refreshOnComplete),
      matrix(matrix),
      view(view),
      tileX(tileX),
      tileY(tileY),
      tileZ(tileZ),
      tileDrawVersion(tileDrawVersion)
{}

void GLTile::BitmapLoadContext::veto()
{
    vetoed = true;
}

std::shared_ptr<TAK::Engine::Renderer::Bitmap2> GLTile::BitmapLoadContext::load(void *opaque)
{
    std::shared_ptr<GLTile::BitmapLoadContext> *ctxPtr = (std::shared_ptr<GLTile::BitmapLoadContext> *)opaque;
    auto *ctx = ctxPtr->get();

    Renderer::BitmapPtr bitmap(nullptr, Util::Memory_deleter_const<Bitmap2>);

    if (!ctx->vetoed) {
        TAK::Engine::Util::TAKErr err = ctx->matrix->getTile(bitmap, ctx->tileZ, ctx->tileX, ctx->tileY);

        // post a refresh
        if (ctx->refreshOnComplete) {
            ctx->view.getRenderContext().requestRefresh();
            
            auto ctrl = ctx->view.getSurfaceRendererControl();
            if (ctrl) {
                TAK::Engine::Feature::Envelope2 tileBounds;
                TAK::Engine::Raster::TileMatrix::TileMatrix_getTileBounds(&tileBounds, *ctx->matrix, ctx->tileZ, ctx->tileX, ctx->tileY);
                TAK::Engine::Feature::GeometryTransformer_transform(&tileBounds, tileBounds, ctx->matrix->getSRID(), 4326);
                ctrl->markDirty(tileBounds, false);
            }
        }
        if (err != TAK::Engine::Util::TE_Ok)
            throw std::exception("Error loading tile bitmap");
    }
    // Clean up
    delete ctxPtr;

    std::shared_ptr<TAK::Engine::Renderer::Bitmap2> result(std::move(bitmap));
    return result;
}

GLTile::BorrowRecord::BorrowRecord(GLTile *owner, const std::shared_ptr<GLTile> &from, double borrowMinX, double borrowMinY,
                                   double borrowMaxX, double borrowMaxY)
    : owner(owner), from(from), projMinX(borrowMinX), projMinY(borrowMinY), projMaxX(borrowMaxX), projMaxY(borrowMaxY), mesh(nullptr) {
    if (!from->hasTexture()) from->checkForCachedTexture();

    Math::Point2<double> p(0, 0);

    p.x = projMinX;
    p.y = projMaxY;
    from->proj2uv.transform(&p, p);
    const auto u0 = (float)p.x;
    const auto v0 = (float)p.y;
    p.x = projMaxX;
    p.y = projMinY;
    from->proj2uv.transform(&p, p);
    const auto u1 = (float)p.x;
    const auto v1 = (float)p.y;

    Math::Matrix2 img2uv;
    Math::Matrix2_mapQuads(&img2uv, projMinX, projMaxY, projMaxX, projMaxY, projMaxX, projMinY, projMinX, projMinY, u0, v0, u1, v0, u1, v1,
                           u0, v1);

    mesh = new GLTileMesh(Math::Point2<double>(projMinX, projMaxY), Math::Point2<double>(projMaxX, projMaxY),
                          Math::Point2<double>(projMaxX, projMinY), Math::Point2<double>(projMinX, projMinY), img2uv, owner->core->proj2geo,
                          owner->patch->getParent()->tileMeshSubdivisions);

    from->borrowers.insert(owner);
}

GLTile::BorrowRecord::~BorrowRecord() {
    from->borrowers.erase(owner);
    delete mesh;
}
