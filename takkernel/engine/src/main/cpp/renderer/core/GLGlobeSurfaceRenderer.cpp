#include "renderer/core/GLGlobeSurfaceRenderer.h"

#include <algorithm>
#include <cassert>
#include <sstream>

#include "core/Datum2.h"
#include "feature/SpatialCalculator2.h"
#include "math/Rectangle.h"
#include "port/STLVectorAdapter.h"
#include "raster/osm/OSMUtils.h"
#include "raster/tilematrix/TileMatrix.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLGlobe.h"
#include "renderer/elevation/TerrainRenderService.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Raster::TileMatrix;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define MT_TILE_SIZE 512
#define DEFAULT_SURFACE_REFRESH_INTERVAL 3000
#define FORCE_PAUSE 0

namespace
{
    GLMegaTexture::TileIndex getIndex(const TerrainTile &tile) NOTHROWS;
    GLMegaTexture::TileIndex getIndex(const GLTerrainTile &tile) NOTHROWS;
    Envelope2 getBounds(const GLMegaTexture::TileIndex &index) NOTHROWS;

    struct MT_IndexCompare
    {
        bool operator()(const GLMegaTexture::TileIndex& a, const GLMegaTexture::TileIndex& b) const NOTHROWS
        {
            if(a.level < b.level)
                return true;
            else if(a.level > b.level)
                return false;
            else if(a.column < b.column)
                return true;
            else if(a.column > b.column)
                return false;
            else if(a.row < b.row)
                return true;
            else if(a.row > b.row)
                return false;
            else // a.row == b.row
                return false;
        }
    };
    struct MT_TileCompare
    {
        bool operator()(const GLTerrainTile &a, const GLTerrainTile &b) const NOTHROWS
        {
            MT_IndexCompare cmp;
            return cmp(getIndex(a), getIndex(b));
        }
    };

    GLMegaTexture::TileIndex zoomIn(const GLMegaTexture::TileIndex index) NOTHROWS
    {
        GLMegaTexture::TileIndex zoomed(index);
        zoomed.level++;
        zoomed.column *= 2u;
        zoomed.row *= 2u;
        return zoomed;
    }
    GLMegaTexture::TileIndex zoomOut(const GLMegaTexture::TileIndex index) NOTHROWS
    {
        GLMegaTexture::TileIndex zoomed(index);
        zoomed.level--;
        zoomed.column /= 2u;
        zoomed.row /= 2u;
        return zoomed;
    }
}

GLGlobeSurfaceRenderer::GLGlobeSurfaceRenderer(GLGlobe &owner_) NOTHROWS :
    owner(owner_),
    front((1u<<22u)*MT_TILE_SIZE, (1u<<21u)*MT_TILE_SIZE, MT_TILE_SIZE, false),
    back((1u<<22u)*MT_TILE_SIZE, (1u<<21u)*MT_TILE_SIZE, MT_TILE_SIZE, false),
    paused(false),
    dirty(true),
    streamDirty(false),
    refreshInterval(DEFAULT_SURFACE_REFRESH_INTERVAL),
    lastRefresh(Platform_systime_millis())
{}
GLGlobeSurfaceRenderer::~GLGlobeSurfaceRenderer() NOTHROWS
{}

void GLGlobeSurfaceRenderer::update(const std::size_t limitMillis) NOTHROWS
{
    // XXX - this is _really_ slow on windows...
#ifdef __ANDROID__
    {
        std::size_t total = 0, shared, pool = 0;
        {
            GLMegaTexture *mt[2];
            mt[0] = &front;
            mt[1] = &back;
            for (std::size_t i = 0; i < 2u; i++) {
                std::size_t numTiles, numSharedTiles, totalSharedTiles, mtpool;
                mt[i]->getStats(&numTiles, &numSharedTiles, &mtpool, &totalSharedTiles);

                shared = totalSharedTiles;
                total += numTiles + mtpool;
                pool += mtpool;
            }
            total += shared;
        }
        {
            std::ostringstream strm;
            strm << "MT storage " << total/1024 << "kb [shared=" << shared/1024 << ",pool=" << pool/1024  << "]";
            owner.addRenderDiagnosticMessage(strm.str().c_str());
        }
    }
#endif
    if (paused)
        return;

    if(!updateContext.pc.visible.pc) {
        {
            Lock lock(mutex);

            // if the refresh interval has elapsed mark as dirty
            dirty |= refreshInterval && (refreshInterval < (std::size_t)(Platform_systime_millis()-lastRefresh));

            // XXX - mark dirty on unconfirmed
            dirty |= !owner.offscreen.visibleTiles.confirmed;

            // if nothing is dirty, there's no update to do
            if (front.getNumAvailableTiles() && dirtyRegions.empty() && !dirty)
                return;
            updateContext.dirtyRegions.clear();
            // XXX - should we ignore client specified regions when dirty?
            if(!dirty)
                updateContext.dirtyRegions = dirtyRegions;

            // all dirty regions have been queued for update
            dirtyRegions.clear();

            // capture streaming
            updateContext.stream = streamDirty;
            streamDirty = false;
        }

        updateContext.level0 = 0u;
        for (auto& gltt : owner.offscreen.visibleTiles.value) {
            const auto idx = getIndex(gltt);
            if(idx.level > updateContext.level0)
                updateContext.level0 = idx.level;
        }

        // if global dirty flag is set, assume all visible tiles are dirty
        if (dirty) {
            for (auto& gltt : owner.offscreen.visibleTiles.value)
                updateContext.dirtyRegions.push_back(gltt.tile->aabb_wgs84);
        }
        // `dirty` handling is complete
        dirty = false;

        // buffer is not populated, entire buffer is dirty
        if (!front.getNumAvailableTiles())
            updateContext.dirtyRegions.push_back(Envelope2(-180.0, -90.0, 0.0, 180.0, 90.0, 0.0));

        // update the resolve tiles list
        TAK::Engine::Port::STLVectorAdapter<std::shared_ptr<const TerrainTile>> tiles_a(updateContext.resolveTiles);
        owner.terrain->lock(tiles_a);

        updateContext.dirtyTiles.visible.reserve(updateContext.resolveTiles.size());

        // for selected tiles, check with dirty region. if not dirty, attempt
        // to share from the front buffer, else queue it for update
        for (std::size_t i = 0; i < updateContext.resolveTiles.size(); i++) {
            // determine if the selected tile is dirty
            bool isDirty = updateContext.dirtyRegions.intersects(updateContext.resolveTiles[i]->aabb_wgs84);

            // force population of all if there is no fully populated surface texture
            isDirty |= !front.getNumAvailableTiles();

            // if the tile does not intersect any dirty region, check the
            // `front` buffer to see if it is already available. If not, mark
            // it as an offscreen dirty region
            if (!isDirty && !front.getTile(getIndex(*updateContext.resolveTiles[i]))) {
                updateContext.dirtyTiles.offscreen.push_back(i);
            }

            // if dirty, queue it for update
            if(isDirty) {
                updateContext.dirtyTiles.visible.push_back(i);

                const auto idx = getIndex(*updateContext.resolveTiles[i]);
                if(idx.level > updateContext.level0)
                    updateContext.level0 = idx.level;
            }
        }
        updateContext.limit = updateContext.dirtyTiles.visible.size()+updateContext.dirtyTiles.offscreen.size();

        // XXX - we could potentially sort the `dirtyTiles` lists here,
        //       however, time is very limited and an additional MS could be
        //       the difference between the surface update completing in this
        //       frame versus the next. sorting would be beneficlal in the
        //       event that there are many tiles that need to be updated and we
        //       end up doing several in-progress transfers. mitigation may be
        //       to only sort if the `dirtyTiles` list exceeds some heuristic;
        //       an extra MS for an update that is expected to span several
        //       frames is not as significant of an impact.

        updateContext.resadj = pow(2.0, atakmap::raster::osm::OSMUtils::mapnikTileLeveld(owner.renderPasses[0].scene.gsd, 0.0)-(double)atakmap::raster::osm::OSMUtils::mapnikTileLevel(owner.renderPasses[0].scene.gsd));

        lastRefresh = Platform_systime_millis();
        updateContext.pump++;

        updateContext.updateStart = lastRefresh;
        updateContext.frames = 0u;
    } else {
        Lock lock(mutex);
        if(streamDirty && !updateContext.stream) {
            // XXX - need explicit region management to improve rates
            updateContext.stream = true;
            // force restart to stream in entire visible scene
            updateContext.pc.visible.pc = 0;
            updateContext.pc.visible.interrupted = false;
            streamDirty = false;
        }
    }

    updateContext.frames++;

    GLint fbo;
    GLint viewport[4];
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbo);

    // x,y,width,height
    glGetIntegerv(GL_VIEWPORT, viewport);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

    // capture the state to restore later
    struct GLMapView2Restore
    {
        GLMapView2::State state;
        GLMapView2::State renderPass1;
        GLMapView2::State renderPass2;
        const GLMapView2::State* renderPass;
        std::size_t numPasses;
        bool multiPartPass;
        int drawVersion;
    };

    GLMapView2Restore restore;
    restore.state = GLMapView2::State(owner);
    restore.renderPass1 = owner.renderPasses[1u];
    restore.renderPass2 = owner.renderPasses[2u];
    restore.renderPass = owner.renderPass;
    restore.numPasses = owner.numRenderPasses;
    restore.multiPartPass = owner.multiPartPass;
    restore.drawVersion = owner.drawVersion;

    const int64_t timesup = limitMillis ? (Platform_systime_millis()+limitMillis) : 0LL;

    bool transfer = false;
    if(updateContext.pc.visible.pc < updateContext.dirtyTiles.visible.size())
        transfer |= updateTiles(updateContext.pc.visible, updateContext.dirtyTiles.visible, false, !updateContext.stream ? timesup : 0LL);
    if((timesup && Platform_systime_millis() < timesup) && updateContext.pc.offscreen.pc < updateContext.dirtyTiles.offscreen.size())
        transfer |= updateTiles(updateContext.pc.offscreen, updateContext.dirtyTiles.offscreen, true, timesup);

    // reset the map render state
    owner.renderPasses[1u] = restore.renderPass1;
    owner.renderPasses[2u] = restore.renderPass2;
    owner.renderPass = restore.renderPass;
    owner.numRenderPasses = restore.numPasses;
    owner.multiPartPass = restore.multiPartPass;
    owner.drawVersion = restore.drawVersion;

    // reset the FBO to display
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    // reset the viewport
    glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

    // check if work is complete
    const bool workComplete = ((updateContext.pc.visible.pc+updateContext.pc.offscreen.pc) == updateContext.limit);

    // transfer data if available
    if(transfer) {
        // if the work is complete and was not interrupted, the megatexture is
        // fully resolved and we can evict all stale entries
        syncTexture(workComplete &&
                    !updateContext.pc.visible.interrupted &&
                    !updateContext.pc.offscreen.interrupted);
    }

    if (!workComplete) {
        // not complete, signal the renderer to refresh
        owner.context.requestRefresh();
    } else {
        // release the tiles
        TAK::Engine::Port::STLVectorAdapter<std::shared_ptr<const TerrainTile>> tiles_a(updateContext.resolveTiles);
        owner.terrain->unlock(tiles_a);
        updateContext.resolveTiles.clear();

        // prepare for next update

        // reset the program counter
        updateContext.pc.visible.pc = 0u;
        updateContext.pc.visible.interrupted = false;
        updateContext.pc.offscreen.pc = 0u;
        updateContext.pc.offscreen.interrupted = false;

        // clear previous dirty regions
        updateContext.dirtyTiles.visible.clear();
        updateContext.dirtyTiles.offscreen.clear();

        if(updateContext.pump == FORCE_PAUSE)
            paused = true;

        // surface was marked as dirty while populating
        if(dirty || !dirtyRegions.empty())
            owner.context.requestRefresh();
    }
}
bool GLGlobeSurfaceRenderer::updateTiles(ProgramCounter &pc, const std::vector<std::size_t> &updateIndices, const bool allowInterrupt, const int64_t limit) NOTHROWS
{
    const GLMapView2::State &rp0 = owner.renderPasses[0];
    GLMapView2::State pumpState(owner);

    // surface rendering is _always_ done using ortho projection
    pumpState.scene = MapSceneModel2(
        rp0.scene.displayDpi,
        (rp0.right - rp0.left),
        (rp0.top - rp0.bottom),
        4326,
        GeoPoint2(rp0.drawLat, rp0.drawLat),
        (float)(rp0.right - rp0.left) / 2.f,
        (float)(rp0.top - rp0.bottom) / 2.f,
        0.0,
        0.0,
        0.0,
        MapCamera2::Scale);

    int64_t tick = updateContext.updateStart;

    // update the megatexture
    while(pc.pc < updateIndices.size()) {
        // check interrupted. this is done up front so we can mark the
        // multi-part pass flag
        pc.interrupted |= (allowInterrupt && dirty);

        const auto ptile = updateContext.resolveTiles[updateIndices[pc.pc++]];
        const auto &tile = *ptile;
        const GLMegaTexture::TileIndex key = getIndex(tile);

        // bind the megatexture tile
        back.bindTile(key.level, key.column, key.row, std::min<unsigned>((unsigned)(updateContext.level0-key.level)/2u, 4u));

        // configure multi-pass and render pump for consumption by renderable
        owner.multiPartPass = !isRenderPumpComplete();
        pumpState.renderPump = (int)updateContext.pump;
        pumpState.drawVersion = updateContext.version++;

#define MT_SURFACE_RELATIVE_SCALE 1
        const std::size_t tileSize = back.getTileSize(key);

        // reset the viewport to the tile dimensions
        glViewport(0, 0, (GLsizei)tileSize, (GLsizei)tileSize);

        // set the state that will be passed to the renderable
        pumpState.drawSrid = 4326;
        pumpState.left = 0;
        pumpState.right = (int)tileSize * MT_SURFACE_RELATIVE_SCALE;
        pumpState.right = (int)(pumpState.right*updateContext.resadj);
        pumpState.bottom = 0;
        pumpState.top = (int)tileSize * MT_SURFACE_RELATIVE_SCALE;
        pumpState.top = (int)(pumpState.top*updateContext.resadj);
        pumpState.northBound = tile.aabb_wgs84.maxY;
        pumpState.westBound = tile.aabb_wgs84.minX;
        pumpState.southBound = tile.aabb_wgs84.minY;
        pumpState.eastBound = tile.aabb_wgs84.maxX;
        pumpState.upperLeft = GeoPoint2(pumpState.northBound, pumpState.westBound);
        pumpState.upperRight = GeoPoint2(pumpState.northBound, pumpState.eastBound);
        pumpState.lowerRight = GeoPoint2(pumpState.southBound, pumpState.eastBound);
        pumpState.lowerLeft = GeoPoint2(pumpState.southBound, pumpState.westBound);
        pumpState.drawRotation = 0.0;
        pumpState.drawTilt = 0.0;
        pumpState.drawLat = (tile.aabb_wgs84.maxY + tile.aabb_wgs84.minY) / 2.0;
        pumpState.drawLng = (tile.aabb_wgs84.maxX + tile.aabb_wgs84.minX) / 2.0;
        pumpState.crossesIDL = false;

        // compute resolution based on the tile that is being textured
        pumpState.drawMapResolution = atakmap::raster::osm::OSMUtils::mapnikTileResolution((int)(log(180.0 / (tile.aabb_wgs84.maxY - tile.aabb_wgs84.minY)) / log(2.0))) / 8.0;
        // apply some adjustment when we get close to the poles
        if(std::min(fabs(tile.aabb_wgs84.minY), fabs(tile.aabb_wgs84.maxY)) > 67.0) {
            double dpole;
            if(tile.aabb_wgs84.minY < 0.0) {
                dpole = 90.0 + tile.aabb_wgs84.maxY;
            } else {
                dpole = 90.0 - tile.aabb_wgs84.minY;
            }
            // linear interpolation of 2x-8x subsample based on distance from
            // far edge of tile to pole
            pumpState.drawMapResolution *= std::max(pow(2.0, 3.0-(dpole/11.5)), 1.0);
        }
        pumpState.drawMapScale = atakmap::core::AtakMapView_getMapScale(rp0.scene.displayDpi, pumpState.drawMapResolution);

        // XXX - depends on implementation detail of `GLMegaTexture`
        pumpState.viewport.x = 0;
        pumpState.viewport.y = 0;
        pumpState.viewport.width = (float)tileSize;
        pumpState.viewport.height = (float)tileSize;

        // XXX - should retrieve actual `GLTerrainTile` as mac requires VBO
        GLTerrainTile gltt;
        gltt.tile = ptile;
        pumpState.renderTiles.value = &gltt;
        pumpState.renderTiles.count = 1u;

        // reset the scene model to map to the tile
        {
            MapSceneModel2_createOrtho(&pumpState.scene, (pumpState.right-pumpState.left), (pumpState.top-pumpState.bottom), GeoPoint2(tile.aabb_wgs84.maxY, tile.aabb_wgs84.minX), GeoPoint2(tile.aabb_wgs84.minY, tile.aabb_wgs84.maxX));
            pumpState.scene.gsd = pumpState.drawMapResolution;

            double mx[16u];
            pumpState.scene.forwardTransform.get(mx, Matrix2::COLUMN_MAJOR);
            for(int i = 0; i < 16; i++)
                pumpState.sceneModelForwardMatrix[i] = (float)mx[i];
        }

        // copy the render pump parameters back to the renderer
        owner.renderPasses[2] = pumpState;
        owner.renderPass = owner.renderPasses+2u;
        owner.numRenderPasses = 1u;
        owner.drawVersion = pumpState.drawVersion;

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glOrthof((float)pumpState.left, (float)pumpState.right, (float)pumpState.bottom, (float)pumpState.top, (float)pumpState.scene.camera.near, (float)pumpState.scene.camera.far);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_MODELVIEW);
        if(owner.basemap)
            owner.basemap->draw(owner, GLMapView2::Surface2|GLMapView2::Surface);
        for(auto r : owner.renderables)
            (*r).draw(owner, GLMapView2::Surface2|GLMapView2::Surface);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_MODELVIEW);
        if (false) {
            if(updateContext.pump == FORCE_PAUSE)
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glColor4f(1.f, 0.f, 0.f, 1.f);
            else
            if(updateContext.pump%2u)
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glColor4f(0.f, 1.f, 0.f, 1.f);
            else
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glColor4f(0.f, 0.f, 1.f, 1.f);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glLineWidth(3.f);
            float fb[12];
            fb[0] = (float) 0;
            fb[1] = (float) 0;
            fb[2] = (float) 0;
            fb[3] = (float) tileSize;
            fb[4] = (float) tileSize;
            fb[5] = (float) tileSize;
            fb[6] = (float) tileSize;
            fb[7] = (float) 0;
            fb[8] = (float) 0;
            fb[9] = (float) 0;
            fb[10] = (float) tileSize;
            fb[11] = (float) tileSize;

            atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glOrthof(0, (float)tileSize, 0, (float)tileSize, 1, -1);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, fb);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, 6);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_MODELVIEW);
        }

        // don't bother incurring the overhead if no limit
        tick = limit ? TAK::Engine::Port::Platform_systime_millis() : tick;

        // if the limit is exhausted or we're interrupted, yield
        if ((limit && (tick > limit)) || pc.interrupted)
            break;
    }

    // if interrupted, we are done
    if(pc.interrupted)
        pc.pc = updateIndices.size();

    // return whether data is ready for transfer
    return (pc.pc == updateIndices.size()) || (tick-updateContext.updateStart) > 250LL;
}
void GLGlobeSurfaceRenderer::syncTexture(const bool evictStale) NOTHROWS
{
    // no tiles fetched, nothing to do
    if(!back.getNumAvailableTiles())
        return;

    // iterate through newly available tiles. eject any higher res tiles
    // that intersect any new tiles. share loaded tiles with the front
    // buffer
    std::vector<GLMegaTexture::TileIndex> backIndices;
    backIndices.resize(back.getNumAvailableTiles());
    back.getAvailableTiles(&backIndices.at(0), backIndices.size());
    if(evictStale) {
        std::vector<GLMegaTexture::TileIndex> resolved;
        resolved.reserve(updateContext.resolveTiles.size());
        for(const auto &t : updateContext.resolveTiles)
            resolved.push_back(getIndex(*t));
        MT_IndexCompare cmp;
        std::sort(resolved.begin(), resolved.end(), cmp);
        auto entry = resolved.begin();
        for(const auto &k : frontIndices) {
            // look for the entry
            entry = std::lower_bound(entry, resolved.end(), k, cmp);
            // if the entry exists, continue
            if(entry != resolved.end() && (*entry).level == k.level && (*entry).column == k.column && (*entry).row == k.row)
                continue;
            // tile is not in resolve set; evict
            front.releaseTile(k, false);
        }
    } else {
        for (const auto &bk : backIndices) {
            MT_IndexCompare cmp;
            GLMegaTexture::TileIndex search_key(bk);
            GLMegaTexture::TileIndex limit;
            limit.level = bk.level;
            limit.column = bk.column+1u;
            limit.row = bk.row+1u;
            auto idx = std::lower_bound(frontIndices.begin(), frontIndices.end(), search_key, cmp);
            if(idx != frontIndices.end()) {
                while(true) {
                    idx = std::lower_bound(idx, frontIndices.end(), search_key, cmp);
                    if(idx == frontIndices.end())
                        break;

                    // walk the tiles and evict any that intersect
                    for( ; idx != frontIndices.end(); idx++) {
                        // no more possibility of intersecting tiles
                        if ((*idx).level > limit.level ||
                            (*idx).column >= limit.column ||
                            ((*idx).column == (limit.column-1u) && (*idx).row >= limit.row)) {

                            break;
                        }

                        // check intersection
                        if((*idx).column < search_key.column ||
                           (*idx).column >= limit.column ||
                           (*idx).row < search_key.row ||
                           (*idx).row >= limit.row) {

                            continue;
                        }

                        // evict
                        front.releaseTile(*idx, false);
                    }
                    // bump to the next level
                    search_key = zoomIn(search_key);
                    limit = zoomIn(limit);
                }
            }
        }
    }

    // transfer the newly loaded tiles to the front buffer
    for (const auto &bk : backIndices)
        back.transferTile(front, bk);
    // data is transfered, clear the back buffer
    back.clear();
    validateFrontIndices();
}
void GLGlobeSurfaceRenderer::validateFrontIndices() NOTHROWS
{
    frontIndices.clear();
    const std::size_t numAvailable = front.getNumAvailableTiles();
    assert(numAvailable > 0);
    frontIndices.reserve(numAvailable);
    for(std::size_t i = 0u; i < numAvailable; i++)
        frontIndices.push_back(GLMegaTexture::TileIndex());
    if(numAvailable) {
        front.getAvailableTiles(&frontIndices.at(0), numAvailable);
        MT_IndexCompare cmp;
        std::sort(frontIndices.begin(), frontIndices.end(), cmp);
    }
}
bool GLGlobeSurfaceRenderer::isRenderPumpComplete() const NOTHROWS
{
    return (updateContext.pc.visible.pc == updateContext.dirtyTiles.visible.size() || updateContext.pc.visible.interrupted) &&
           (updateContext.pc.offscreen.pc == updateContext.dirtyTiles.offscreen.size() || updateContext.pc.offscreen.interrupted);
}
void GLGlobeSurfaceRenderer::draw() NOTHROWS
{
    // if no texture, return
    if (!front.getNumAvailableTiles())
        return;

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    const unsigned int color = owner.getColor(Model::TEDM_Triangles);
    float r = (float)((color>>16u)&0xFFu) / 255.f;
    float g = (float)((color>>8u)&0xFFu) / 255.f;
    float b = (float)(color&0xFFu) / 255.f;
    float a = (float)((color>>24u)&0xFFu) / 255.f;

    // sort the tiles to be drawn
    drawTiles.clear();
    drawTiles.reserve(owner.offscreen.visibleTiles.value.size());
    for (std::size_t i = 0u; i < owner.offscreen.visibleTiles.value.size(); i++)
        drawTiles.push_back(owner.offscreen.visibleTiles.value[i]);
    {
        MT_TileCompare cmp;
        std::sort(drawTiles.begin(), drawTiles.end(), cmp);
    }

    // RENDER MEGATEXTURE
    TerrainTileRenderContext ctx = GLTerrainTile_begin(owner.renderPasses[0u].scene, GLTerrainTile_getColorShader(owner.context, owner.renderPasses[0].drawSrid, TECSO_Lighting));
    Controls::IlluminationControlImpl *illuminationControl = owner.getIlluminationControl();

    ctx.numLightSources = 0; // default to illumination disabled until determined otherwise
    if (illuminationControl->getEnabled())
    {
        illuminationControl->configureIllumination(ctx);
    }

    const char *shader = "???";
    {
        auto shaders = GLTerrainTile_getColorShader(owner.context, owner.renderPasses[0].drawSrid, TECSO_Lighting);
        if(ctx.shader.base.handle == shaders.hi.base.handle)
            shader = "HI";
        else if(ctx.shader.base.handle == shaders.md.base.handle)
            shader = "MD";
        else if(ctx.shader.base.handle == shaders.lo.base.handle)
            shader = "LO";
    }

    GLTerrainTile_setElevationScale(ctx, static_cast<float>(owner.elevationScaleFactor));

    auto from = frontIndices.begin();
    std::size_t misses = 0u;
    std::size_t iterations = 0u;
    std::size_t draws = 0u;
    std::size_t totalMisses = 0;
    for(std::size_t i = 0u; i < drawTiles.size(); i++) {
        iterations++;

        GLTerrainTile &tile = drawTiles[i];
        GLMegaTexture::TileIndex tt_key = getIndex(*tile.tile);

        std::size_t drawsThisTile = 0u;

        struct {
            GLMegaTexture::TileIndex key;
            struct {
                GLuint id{GL_NONE};
                std::size_t size {0u};
            } texture;
        } mt_lo;
        mt_lo.key = tt_key;
        mt_lo.texture.id = front.getTile(mt_lo.key);
        while(!mt_lo.texture.id && mt_lo.key.level) {
            mt_lo.key = zoomOut(mt_lo.key);
            mt_lo.texture.id = front.getTile(mt_lo.key);
        }
        if(mt_lo.texture.id)
            mt_lo.texture.size = front.getTileSize(mt_lo.key);

        // perform exact match or low-res tile texturing
        if(mt_lo.texture.id) {
            Envelope2 mt_aabb = getBounds(mt_lo.key);

            Matrix2 mt_xform;
            mt_xform.setToScale((double)mt_lo.texture.size / (mt_aabb.maxX-mt_aabb.minX), (double)mt_lo.texture.size / (mt_aabb.maxY-mt_aabb.minY), 1.0);
            mt_xform.translate(-mt_aabb.minX, -mt_aabb.minY, 0.0);

            draws++;
            GLTerrainTile_bindTexture(ctx, front.getTile(mt_lo.key.level, mt_lo.key.column, mt_lo.key.row), mt_lo.texture.size, mt_lo.texture.size);
            drawsThisTile++;

            GLTerrainTile_drawTerrainTiles(ctx, mt_xform, &tile, 1u, r, g, b, a);

            // exact match, done
            if(mt_lo.key.level == tt_key.level && mt_lo.key.column == tt_key.column && mt_lo.key.row == tt_key.row)
                continue;
        }

        // no exact match
        misses++;

        // we should have already obtained (and rendered) a lower res texture
        // over the tile. we'll now look to fill in any higher res
        GLMegaTexture::TileIndex search_key(tt_key);
        GLMegaTexture::TileIndex limit;
        limit.level = tt_key.level;
        limit.row = tt_key.row+1u;
        limit.column = tt_key.column+1u;

        auto idx = frontIndices.begin();
        while(true) {
            MT_IndexCompare cmp;
            idx = std::lower_bound(idx, frontIndices.end(), search_key, cmp);
            // exhausted
            if(idx == frontIndices.end())
                break;

            // walk the tiles and render any that intersect
            for( ; idx != frontIndices.end(); idx++) {
                // no more possibility of intersecting tiles
                if((*idx).level > limit.level ||
                   (*idx).column > limit.column ||
                   ((*idx).column == limit.column && (*idx).row > limit.row)) {

                    break;
                }

                // check for intersect
                const Envelope2 mt_aabb = getBounds(*idx);
                if(!atakmap::math::Rectangle<double>::intersects(
                                            mt_aabb.minX+(mt_aabb.maxX-mt_aabb.minX)/front.getTileSize(),
                                            mt_aabb.minY+(mt_aabb.maxY-mt_aabb.minY)/front.getTileSize(),
                                            mt_aabb.maxX-(mt_aabb.maxX-mt_aabb.minX)/front.getTileSize(),
                                            mt_aabb.maxY-(mt_aabb.maxY-mt_aabb.minY)/front.getTileSize(),
                                            tile.tile->aabb_wgs84.minX+(tile.tile->aabb_wgs84.maxX-tile.tile->aabb_wgs84.minX)/front.getTileSize(),
                                            tile.tile->aabb_wgs84.minY+(tile.tile->aabb_wgs84.maxY-tile.tile->aabb_wgs84.minY)/front.getTileSize(),
                                            tile.tile->aabb_wgs84.maxX-(tile.tile->aabb_wgs84.maxX-tile.tile->aabb_wgs84.minX)/front.getTileSize(),
                                            tile.tile->aabb_wgs84.maxY-(tile.tile->aabb_wgs84.maxY-tile.tile->aabb_wgs84.minY)/front.getTileSize())) {
                    continue;
                }

                // draw the tile
                const std::size_t tileSize = front.getTileSize(*idx);

                Matrix2 mt_xform;
                mt_xform.setToScale((double)tileSize / (mt_aabb.maxX-mt_aabb.minX), (double)tileSize / (mt_aabb.maxY-mt_aabb.minY), 1.0);
                mt_xform.translate(-mt_aabb.minX, -mt_aabb.minY, 0.0);

                draws++;
                GLTerrainTile_bindTexture(ctx, front.getTile((*idx).level, (*idx).column, (*idx).row), tileSize, tileSize);
                drawsThisTile++;

                GLTerrainTile_drawTerrainTiles(ctx, mt_xform, &tile, 1u, r, g, b, a);
            }

            // bump to the next level
            search_key = zoomIn(search_key);
            limit = zoomIn(limit);
        }

        if (!drawsThisTile) {
            totalMisses++;
        }
    }
    GLTerrainTile_end(ctx);
    {
        std::ostringstream strm;
        strm << "terrain texturing shader= " << shader << " iterations=" << iterations << " draws=" << draws << " misses=" << misses << " total misses=" << totalMisses;
        owner.addRenderDiagnosticMessage(strm.str().c_str());
    }

    //paused  = misses == 0;

    // terrain mesh is no longer matched with megatexture tiles

    //view.addRenderDiagnostic(String.format("Offscreen FPS %1.1f", diagnostics.eventFramerate()));

    glDisable(GL_BLEND);
}
void GLGlobeSurfaceRenderer::release() NOTHROWS
{
    front.release();
    back.release();
}
void GLGlobeSurfaceRenderer::markDirty() NOTHROWS
{
    Lock lock(mutex);
    if (lock.status != TE_Ok) {
        Logger_log(TELL_Warning, "Failed to acquire lock, dirty regions not updated.");
        return;
    }

    dirty = true;
    owner.context.requestRefresh();
}
void GLGlobeSurfaceRenderer::markDirty(const Envelope2& region, const bool streaming) NOTHROWS
{
    Lock lock(mutex);
    if (lock.status != TE_Ok) {
        Logger_log(TELL_Warning, "Failed to acquire lock, dirty regions not updated.");
        return;
    }

    // XXX - 
    //dirtyRegions.push_back(region);
    streamDirty |= streaming;
    dirty = true;
    owner.context.requestRefresh();
}
void GLGlobeSurfaceRenderer::setMinimumRefreshInterval(const std::size_t millis) NOTHROWS
{
    refreshInterval = millis;
}
std::size_t GLGlobeSurfaceRenderer::getMinimumRefreshInterval() const NOTHROWS
{
    return refreshInterval;
}

namespace
{
    GLMegaTexture::TileIndex getIndex(const TerrainTile& tile) NOTHROWS
    {
        const int level = (int)(log(180.0/(tile.aabb_wgs84.maxY-tile.aabb_wgs84.minY)) / log(2.0));
        if (level < 0)
            return GLMegaTexture::TileIndex{ 0u, 0u, 0u };

        TileMatrix::ZoomLevel zoom;
        zoom.level = level;
        zoom.tileWidth = MT_TILE_SIZE;
        zoom.tileHeight = MT_TILE_SIZE;
        zoom.pixelSizeX = (180.0 / (double)(1ULL << (std::size_t)level)) / (double)zoom.tileWidth;
        zoom.pixelSizeY = (180.0 / (double)(1ULL << (std::size_t)level)) / (double)zoom.tileHeight;

        TAK::Engine::Math::Point2<double> index;
        TileMatrix_getTileIndex(&index, -180.0, 90.0, zoom, (tile.aabb_wgs84.minX+tile.aabb_wgs84.maxX)/2.0, (tile.aabb_wgs84.minY+tile.aabb_wgs84.maxY)/2.0);

        if (index.x < 0.0)
            index.x = 0;
        if (index.y < 0.0)
            index.y = 0;

        return GLMegaTexture::TileIndex{ (std::size_t)level, (std::size_t)index.x, (std::size_t)index.y };

    }
    GLMegaTexture::TileIndex getIndex(const GLTerrainTile& tile) NOTHROWS
    {
        return getIndex(*tile.tile);
    }
    Envelope2 getBounds(const GLMegaTexture::TileIndex& index) NOTHROWS
    {
        const double tileSizeX = (180.0 / (double)(1 << index.level));
        const double tileSizeY = (180.0 / (double)(1 << index.level));

        const double minX = -180.0 + (tileSizeX * index.column);
        const double maxY = 90.0 - (tileSizeY * index.row);

        return Envelope2(minX, maxY - tileSizeY, 0.0, minX + tileSizeX, maxY, 0.0);
    }
}
