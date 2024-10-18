#include "renderer/feature/GLBatchGeometryRenderer4.h"

#include <cassert>
#include <map>
#include <math.h>
#include <string>

#include "core/MapSceneModel2.h"
#include "feature/GeometryTransformer.h"
#include "math/AABB.h"
#include "renderer/GL.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLSLUtil.h"
#include "renderer/core/GLMapRenderGlobals.h"
#include "renderer/elevation/GLTerrainTile.h"
#include "renderer/feature/GLBatchGeometryRenderer4.h"
#include "renderer/GLMatrix.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Logging.h"

using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;


namespace {
    struct {
        Mutex mutex;
        std::map<const RenderContext*, std::weak_ptr<GLOffscreenFramebuffer>> pixel;
        std::map<const RenderContext*, std::weak_ptr<GLOffscreenFramebuffer>> tile;
    } sharedFbos;

    TAKErr getSharedFbo(std::shared_ptr<GLOffscreenFramebuffer> &fbo, std::map<const RenderContext*, std::weak_ptr<GLOffscreenFramebuffer>> &cache, const RenderContext &ctx, const std::size_t width, const std::size_t height, const GLOffscreenFramebuffer::Options &opts) NOTHROWS
    {
        Lock lock(sharedFbos.mutex);
        do {
            const auto& entry = cache.find(&ctx);
            if (entry != cache.end()) {
                fbo = entry->second.lock();
                if (fbo)
                    break;
                // reference is cleared, evict entry
                cache.erase(entry);
            }

            // create offscreen FBO
            GLOffscreenFramebufferPtr tile(nullptr, nullptr);
            const TAKErr code = GLOffscreenFramebuffer_create(
                tile,
                (int)width, (int)height,
                opts);
            TE_CHECKRETURN_CODE(code);

            fbo = std::move(tile);
            cache[&ctx] = fbo;
        } while (false);

        return TE_Ok;
    }
}

GLBatchGeometryRenderer4::GLBatchGeometryRenderer4(const RenderContext &ctx) NOTHROWS :
    context(ctx)
{
    pointShader.handle = 0u;
    lineShader.base.handle = 0u;
    polygonsShader.handle = 0u;
}

TAKErr GLBatchGeometryRenderer4::setBatchState(const BatchState &surface, const BatchState& sprites) NOTHROWS
{
    batchState.sprites = sprites;
    batchState.surface = surface;
    return TE_Ok;
}
TAKErr GLBatchGeometryRenderer4::addBatchBuffer(const Program program, const PrimitiveBuffer &buffer, const BatchGeometryBufferLayout &layout, const int renderPass) NOTHROWS
{
    const bool surface = !!(renderPass & GLGlobeBase::Surface);
    const bool sprites = !!(renderPass & GLGlobeBase::Sprites);

    VertexBuffer buf;
    buf.primitive = buffer;
    buf.layout = layout;
    switch (program) {
    case Program::Points:
        pointsBuffers.push_back(buf);
        pointsNeedSort = true;
        break;
    case Program::AntiAliasedLines:
        if (surface)
            surfaceLineBuffers.push_back(buf);
        if (sprites)
            spriteLineBuffers.push_back(buf);
        break;
    case Program::Polygons:
        if (surface)
            surfacePolygonBuffers.push_back(buf);
        if (sprites)
            spritePolygonBuffers.push_back(buf);
        break;
    default:
        // XXX - other
        return TE_InvalidArg;
    }
    return TE_Ok;
}
void GLBatchGeometryRenderer4::markForRelease() NOTHROWS
{
    for (const auto& buf : surfaceLineBuffers) {
        if (buf.primitive.vbo)
            markedForRelease.push_back(buf.primitive.vbo);
        if (buf.primitive.ibo)
            markedForRelease.push_back(buf.primitive.ibo);
    }
    surfaceLineBuffers.clear();
    for (const auto &buf : spriteLineBuffers) {
        if (buf.primitive.vbo)
            markedForRelease.push_back(buf.primitive.vbo);
        if (buf.primitive.ibo)
            markedForRelease.push_back(buf.primitive.ibo);
    }
    spriteLineBuffers.clear();
    for (const auto& buf : surfacePolygonBuffers) {
        if (buf.primitive.vbo)
            markedForRelease.push_back(buf.primitive.vbo);
        if (buf.primitive.ibo)
            markedForRelease.push_back(buf.primitive.ibo);
    }
    surfacePolygonBuffers.clear();
    for (const auto &buf : spritePolygonBuffers) {
        if (buf.primitive.vbo)
            markedForRelease.push_back(buf.primitive.vbo);
        if (buf.primitive.ibo)
            markedForRelease.push_back(buf.primitive.ibo);
    }
    spritePolygonBuffers.clear();
    for (const auto &buf : pointsBuffers) {
        if (buf.primitive.vbo)
            markedForRelease.push_back(buf.primitive.vbo);
        if (buf.primitive.ibo)
            markedForRelease.push_back(buf.primitive.ibo);
    }
    pointsBuffers.clear();
}

void GLBatchGeometryRenderer4::hitTest(Port::Collection<uint32_t> &featureIds, const Renderer::Core::GLGlobeBase &view, const float screenX, const float screenY) 
{
    if (!fbo.pixel) {
        GLOffscreenFramebuffer::Options opts;
        opts.bufferMask = GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT;
        opts.colorFormat = GL_RGBA;
        opts.colorType = GL_UNSIGNED_BYTE;
        if (getSharedFbo(fbo.pixel, sharedFbos.pixel, context, 1u, 1u, opts) != TE_Ok)
            return;
    }

    struct RestoreState {
        RestoreState() NOTHROWS
        {
            glGetIntegerv(GL_FRAMEBUFFER_BINDING, &framebuffer);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
            glGetFloatv(GL_DEPTH_CLEAR_VALUE, &clearDepth);
            glGetIntegerv(GL_VIEWPORT, viewport);

            glGetIntegerv(GL_DEPTH_FUNC, &depthFunc);
            glGetBooleanv(GL_DEPTH_WRITEMASK, &depthMask);
            depthEnabled = glIsEnabled(GL_DEPTH_TEST);
        }
        ~RestoreState() NOTHROWS
        {
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            glClearDepthf(clearDepth);

            glDepthFunc(depthFunc);
            glDepthMask(depthMask);
            if (depthEnabled)
                glEnable(GL_DEPTH_TEST);
            else
                glDisable(GL_DEPTH_TEST);
        }

        GLint depthFunc;
        GLboolean depthMask;
        GLboolean depthEnabled;
        GLfloat clearColor[4u];
        GLint viewport[4u];
        GLfloat clearDepth{ 0.f };
        GLint framebuffer{ GL_NONE };
    } restoreState;

    fbo.pixel->bind();

    // translate viewport to pixel
    glViewport((GLint)-screenX, (GLint)-screenY, restoreState.viewport[2u], restoreState.viewport[3u]);

    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);
    glEnable(GL_DEPTH_TEST);

    // for each terrain tile that intersects the ray render the surface features
    if (!surfaceLineBuffers.empty() && !surfacePolygonBuffers.empty()) {
        if (!fbo.tile) {
            GLOffscreenFramebuffer::Options opts;
            opts.bufferMask = GL_COLOR_BUFFER_BIT;
            opts.colorFormat = GL_RGBA;
            opts.colorType = GL_UNSIGNED_BYTE;
            if (getSharedFbo(fbo.tile, sharedFbos.tile, context, 512u, 512u, opts) != TE_Ok)
                return;
        }

        for (std::size_t i = 0u; i < view.renderPasses[0u].renderTiles.count; i++) {
            const auto& surfaceTile = view.renderPasses[0u].renderTiles.value[i];
            const auto& tile = *surfaceTile.tile;

            // check for ray intersection
            TAK::Engine::Feature::Envelope2 aabb(tile.aabb_wgs84);
            if(view.renderPasses[0u].drawSrid == 4978) {
                const auto localaabb = tile.data_proj.value->getAABB();
                Point2<double> min(localaabb.minX, localaabb.minY, localaabb.minZ);
                Point2<double> max(localaabb.maxX, localaabb.maxY, localaabb.maxZ);
                tile.data_proj.localFrame.transform(&min, min);
                tile.data_proj.localFrame.transform(&max, max);
                aabb.minX = min.x;
                aabb.minY = min.y;
                aabb.minZ = min.z;
                aabb.maxX = max.x;
                aabb.maxY = max.y;
                aabb.maxZ = max.z;
            } else if (view.renderPasses[0].drawSrid != 4326) {
                TAK::Engine::Feature::GeometryTransformer_transform(&aabb, aabb, 4326, view.renderPasses[0u].drawSrid);
            }

            GeoPoint2 isect;
            if (view.renderPasses[0u].scene.inverse(
                &isect,
                Point2<float>(screenX, screenY, 0.0),
                AABB(
                    Point2<double>(aabb.minX, aabb.minY, aabb.minZ),
                    Point2<double>(aabb.maxX, aabb.maxY, aabb.maxZ))) != TE_Ok) {

                continue;
            }

            // create ortho scene
            MapSceneModel2 surfaceScene;
            MapSceneModel2_createOrtho(
                &surfaceScene,
                (unsigned)fbo.tile->width,
                (unsigned)fbo.tile->height,
                GeoPoint2(surfaceTile.tile->aabb_wgs84.maxY, surfaceTile.tile->aabb_wgs84.minX),
                GeoPoint2(surfaceTile.tile->aabb_wgs84.minY, surfaceTile.tile->aabb_wgs84.maxX));
            {
                // capture state for restore after generating surface tile
                RestoreState surfaceRestore;

                // bind the surface tile FBO
                fbo.tile->bind();

                // reset the viewport to the tile dimensions
                glViewport(0, 0, (GLsizei)fbo.tile->width, (GLsizei)fbo.tile->height);

                // XXX - render surface 
                GLGlobeBase::State pumpState(view.renderPasses[0]);

                // set the state that will be passed to the renderable
                pumpState.scene = surfaceScene;
                pumpState.drawSrid = 4326;
                pumpState.left = 0;
                pumpState.right = fbo.tile->width;
                pumpState.bottom = 0;
                pumpState.top = fbo.tile->height;
                pumpState.focusx = surfaceScene.focusX;
                pumpState.focusy = surfaceScene.focusY;
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
                pumpState.drawMapResolution = surfaceScene.gsd;
                pumpState.drawMapScale = atakmap::core::AtakMapView_getMapScale(view.renderPasses[0u].scene.displayDpi, pumpState.drawMapResolution);

                // XXX - depends on implementation detail of `GLMegaTexture`
                pumpState.viewport.x = 0;
                pumpState.viewport.y = 0;
                pumpState.viewport.width = (float)fbo.tile->width;
                pumpState.viewport.height = (float)fbo.tile->height;

                // XXX - should retrieve actual `GLTerrainTile` as mac requires VBO
                pumpState.renderTiles.value = view.renderPasses[0u].renderTiles.value + i;
                pumpState.renderTiles.count = 1u;

                // reset the scene model to map to the tile
                {
                    double mx[16u];
                    pumpState.scene.forwardTransform.get(mx, Matrix2::COLUMN_MAJOR);
                    for (int ix = 0; ix < 16; ix++)
                        pumpState.sceneModelForwardMatrix[ix] = (float)mx[ix];
                }

                atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION);
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glOrthof((float)pumpState.left, (float)pumpState.right, (float)pumpState.bottom, (float)pumpState.top, (float)pumpState.scene.camera.near, (float)pumpState.scene.camera.far);
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_MODELVIEW);

                draw(pumpState, GLGlobeBase::Surface, true);

                atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION);
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_MODELVIEW);
            }

            // draw terrain tile with hit-test surface texture
            const auto surfaceShaders = GLTerrainTile_getColorShader(view.context, view.renderPasses[0].scene.projection->getSpatialReferenceID(), 0);
            auto surfacectx = GLTerrainTile_begin(view.renderPasses[0u].scene, surfaceShaders);

            GLTerrainTile_bindTexture(surfacectx, fbo.tile->colorTexture, fbo.tile->textureWidth, fbo.tile->textureHeight);
            GLTerrainTile_drawTerrainTiles(surfacectx, surfaceScene.forwardTransform, &surfaceTile, 1);

            GLTerrainTile_end(surfacectx);
        }
    }

    // use scissor to restrict render to single pixel
    glEnable(GL_SCISSOR_TEST);
    glScissor(0, 0, fbo.pixel->width, fbo.pixel->height);
    
    // render the sprites
    draw(view.renderPasses[0u], GLGlobeBase::Sprites, true);

    uint32_t id;
    glReadPixels(
        0, 0,
        1, 1,
        GL_RGBA, GL_UNSIGNED_BYTE,
        &id);
    if (id != 0)
        featureIds.add(static_cast<int64_t>(id));

    glDisable(GL_SCISSOR_TEST);
}

void GLBatchGeometryRenderer4::draw(const GLGlobeBase &view, const int renderPass) NOTHROWS
{
    draw(*view.renderPass, renderPass, false);
}

void GLBatchGeometryRenderer4::draw(const Core::GLGlobeBase::State &view, const int renderPass, const bool drawForHitTest)
{
    // delete all buffers marked for release
    if (!markedForRelease.empty()) {
        glDeleteBuffers((GLsizei)markedForRelease.size(), markedForRelease.data());
        markedForRelease.clear();
    }

    const bool surface = !!(renderPass & GLGlobeBase::Surface);
    const bool sprites = !!(renderPass & GLGlobeBase::Sprites);

    if (drawForHitTest) {
        glDisable(GL_BLEND);
    } else {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    if (surface)
        drawSurface(view, drawForHitTest);
    if (sprites)
        drawSprites(view, drawForHitTest);

    glDisable(GL_BLEND);
}

void GLBatchGeometryRenderer4::drawSurface(const GLGlobeBase::State &view, const bool drawForHitTest) NOTHROWS
{
#if 0
    if (!surfaceCoverage.intersects(TAK::Engine::Feature::Envelope2(view.renderPass->westBound, view.renderPass->southBound, 0.0, view.renderPass->eastBound, view.renderPass->northBound, 0.0)))
        return;
#endif

    // polygons
    if (!this->surfacePolygonBuffers.empty()) {
        this->drawPolygonBuffers(view, batchState.surface, surfacePolygonBuffers, drawForHitTest);
    }
    // lines
    if (!this->surfaceLineBuffers.empty()) {
        this->drawLineBuffers(view, batchState.surface, surfaceLineBuffers, drawForHitTest);
    }
}
void GLBatchGeometryRenderer4::drawSprites(const GLGlobeBase::State &view, const bool drawForHitTest) NOTHROWS
{
    // XXX - depth does not appear to be enabled by default during sprites pass with nadir camera
    struct DepthRestore
    {
        DepthRestore() NOTHROWS
        {
            glGetIntegerv(GL_DEPTH_FUNC, &depthFunc);
            glGetBooleanv(GL_DEPTH_WRITEMASK, &depthMask);
            depthEnabled = glIsEnabled(GL_DEPTH_TEST);
        }
        ~DepthRestore() NOTHROWS
        {
            glDepthFunc(depthFunc);
            glDepthMask(depthMask);
            if (depthEnabled)
                glEnable(GL_DEPTH_TEST);
            else
                glDisable(GL_DEPTH_TEST);
        }
        GLint depthFunc;
        GLboolean depthMask;
        GLboolean depthEnabled;
    } depthRestore;

    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);
    glEnable(GL_DEPTH_TEST);

    // polygons
    if(!spritePolygonBuffers.empty()) {
        this->drawPolygonBuffers(view, batchState.sprites, spritePolygonBuffers, drawForHitTest);
    }
    // lines
    if(!spriteLineBuffers.empty()) {
        this->drawLineBuffers(view, batchState.sprites, spriteLineBuffers, drawForHitTest);
    }
    // points
    if (!this->pointsBuffers.empty()) {
        this->batchDrawPoints(view, batchState.sprites, drawForHitTest);
    }
}
int GLBatchGeometryRenderer4::getRenderPass() NOTHROWS
{
    return GLGlobeBase::Surface | GLGlobeBase::Sprites;
}

TAKErr GLBatchGeometryRenderer4::drawLineBuffers(const GLGlobeBase::State &view, const BatchState &ctx, const std::vector<VertexBuffer> &buf, bool drawForHitTest) NOTHROWS {
    TAKErr code(TE_Ok);

    if (!this->lineShader.base.handle) {
        this->lineShader = GLBatchGeometryShaders_getAntiAliasedLinesShader(context);
        assert(this->lineShader.base.handle);
    }

    glUseProgram(lineShader.base.handle);

    // MVP
    {
        Matrix2 mvp;
        // projection
        float matrixF[16u];
        atakmap::renderer::GLMatrix::orthoM(matrixF, (float)view.left, (float)view.right, (float)view.bottom, (float)view.top, (float)view.scene.camera.near, (float)view.scene.camera.far);
        for(std::size_t i = 0u; i < 16u; i++)
            mvp.set(i%4, i/4, matrixF[i]);
        // model-view
        mvp.concatenate(view.scene.forwardTransform);
        mvp.translate(ctx.centroidProj.x, ctx.centroidProj.y, ctx.centroidProj.z);
        for (std::size_t i = 0u; i < 16u; i++) {
            double v;
            mvp.get(&v, i % 4, i / 4);
            matrixF[i] = (float)v;
        }
        glUniformMatrix4fv(lineShader.u_mvp, 1u, false, matrixF);
    }
    // viewport size
    {
        GLint viewport[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        glUniform2f(lineShader.u_viewportSize, (float)viewport[2] / 2.0f, (float)viewport[3] / 2.0f);
    }

    glUniform1i(lineShader.u_hitTest, drawForHitTest);

    glEnableVertexAttribArray(lineShader.a_vertexCoord0);
    glEnableVertexAttribArray(lineShader.a_vertexCoord1);
    glEnableVertexAttribArray(lineShader.a_color);
    glEnableVertexAttribArray(lineShader.a_normal);
    glEnableVertexAttribArray(lineShader.a_halfStrokeWidth);
    glEnableVertexAttribArray(lineShader.a_dir);
    glEnableVertexAttribArray(lineShader.a_pattern);
    glEnableVertexAttribArray(lineShader.a_factor);

    for (const auto &buffer : buf) {
        glBindBuffer(GL_ARRAY_BUFFER, buffer.primitive.vbo);
        // XXX - VAOs
        glVertexAttribPointer(lineShader.a_vertexCoord0, buffer.layout.vertex.antiAliasedLines.position0);
        glVertexAttribPointer(lineShader.a_vertexCoord1, buffer.layout.vertex.antiAliasedLines.position1);
        if (drawForHitTest) {
            glVertexAttribPointer(lineShader.a_color, buffer.layout.vertex.antiAliasedLines.id);
        } else {
            glVertexAttribPointer(lineShader.a_color, buffer.layout.vertex.antiAliasedLines.color);
        }
        glVertexAttribPointer(lineShader.a_normal, buffer.layout.vertex.antiAliasedLines.normal);
        glVertexAttribPointer(lineShader.a_halfStrokeWidth, buffer.layout.vertex.antiAliasedLines.halfStrokeWidth);
        glVertexAttribPointer(lineShader.a_dir, buffer.layout.vertex.antiAliasedLines.dir);
        glVertexAttribPointer(lineShader.a_pattern, buffer.layout.vertex.antiAliasedLines.pattern);
        glVertexAttribPointer(lineShader.a_factor, buffer.layout.vertex.antiAliasedLines.factor);
        glDrawArrays(buffer.primitive.mode, 0u, buffer.primitive.count);
    }

    glDisableVertexAttribArray(lineShader.a_vertexCoord0);
    glDisableVertexAttribArray(lineShader.a_vertexCoord1);
    glDisableVertexAttribArray(lineShader.a_color);
    glDisableVertexAttribArray(lineShader.a_normal);
    glDisableVertexAttribArray(lineShader.a_halfStrokeWidth);
    glDisableVertexAttribArray(lineShader.a_dir);
    glDisableVertexAttribArray(lineShader.a_pattern);
    glDisableVertexAttribArray(lineShader.a_factor);

    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
    glUseProgram(GL_NONE);

    return code;
}
TAKErr GLBatchGeometryRenderer4::drawPolygonBuffers(const GLGlobeBase::State &view, const BatchState &ctx, const std::vector<VertexBuffer> &buf, bool drawForHitTest) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!this->polygonsShader.handle) {
        this->polygonsShader = GLBatchGeometryShaders_getPolygonsShader(context);
        assert(this->polygonsShader.handle);
    }

    glUseProgram(polygonsShader.handle);

    // MVP
    {
        Matrix2 mvp;
        // projection
        float matrixF[16u];
        atakmap::renderer::GLMatrix::orthoM(matrixF, (float)view.left, (float)view.right, (float)view.bottom, (float)view.top, (float)view.scene.camera.near, (float)view.scene.camera.far);
        for(std::size_t i = 0u; i < 16u; i++)
            mvp.set(i%4, i/4, matrixF[i]);
        // model-view
        mvp.concatenate(view.scene.forwardTransform);
        mvp.translate(ctx.centroidProj.x, ctx.centroidProj.y, ctx.centroidProj.z);
        for (std::size_t i = 0u; i < 16u; i++) {
            double v;
            mvp.get(&v, i % 4, i / 4);
            matrixF[i] = (float)v;
        }
        glUniformMatrix4fv(polygonsShader.u_mvp, 1u, false, matrixF);
    }

    glUniform4f(polygonsShader.uColor, 1.f, 1.f, 1.f, 1.f);

    glEnableVertexAttribArray(polygonsShader.aPosition);
    glEnableVertexAttribArray(polygonsShader.a_color);
    glEnableVertexAttribArray(polygonsShader.aOutlineWidth);
    glEnableVertexAttribArray(polygonsShader.aExteriorVertex);

    for (const auto &buffer : buf) {
        glBindBuffer(GL_ARRAY_BUFFER, buffer.primitive.vbo);
        // XXX - VAOs
        glVertexAttribPointer(polygonsShader.aPosition, buffer.layout.vertex.polygons.position);
        glVertexAttribPointer(polygonsShader.aOutlineWidth, buffer.layout.vertex.polygons.outlineWidth);
        glVertexAttribPointer(polygonsShader.aExteriorVertex, buffer.layout.vertex.polygons.exteriorVertex);
        if (drawForHitTest) {
            glVertexAttribPointer(polygonsShader.a_color, buffer.layout.vertex.polygons.id);
        } else {
            glVertexAttribPointer(polygonsShader.a_color, buffer.layout.vertex.polygons.color);
        }
        glDrawArrays(buffer.primitive.mode, 0u, buffer.primitive.count);
    }

    glDisableVertexAttribArray(polygonsShader.aPosition);
    glDisableVertexAttribArray(polygonsShader.a_color);
    glDisableVertexAttribArray(polygonsShader.aOutlineWidth);
    glDisableVertexAttribArray(polygonsShader.aExteriorVertex);

    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
    glUseProgram(GL_NONE);

    return code;
}
TAKErr GLBatchGeometryRenderer4::batchDrawPoints(const GLGlobeBase::State &view, const BatchState &ctx, bool drawForHitTest) NOTHROWS
{
    TAKErr code(TE_Ok);

    struct
    {
        unsigned int color{ 0xFFFFFFFFu };
        GLuint texId{ GL_NONE };
        GLenum textureUnit{ GL_TEXTURE0 };
    } state;

    glEnable(GL_POLYGON_OFFSET_FILL);
    glPolygonOffset(1.f, 1.f);

    if (pointShader.handle == 0) {
        pointShader = GLBatchGeometryShaders_getPointsShader(context);
        assert(pointShader.handle);
    } else {
        glUseProgram(pointShader.handle);
    }

    float proj[16];
    atakmap::renderer::GLMatrix::orthoM(proj, (float)view.left, (float)view.right, (float)view.bottom, (float)view.top, (float)view.scene.camera.near, (float)view.scene.camera.far);

    Matrix2 mvp(
        proj[0], proj[4], proj[8], proj[12],
        proj[1], proj[5], proj[9], proj[13],
        proj[2], proj[6], proj[10], proj[14],
        proj[3], proj[7], proj[11], proj[15]);
    mvp.concatenate(view.scene.forwardTransform);
    // we will concatenate the local frame to the MapSceneModel's Model-View matrix to transform
    // from the Local Coordinate System into world coordinates before applying the model view.
    // If we do this all in double precision, then cast to single-precision, we'll avoid the
    // precision issues with trying to cast the world coordinates to float
    mvp.concatenate(ctx.localFrame);
    double mvpd[16];
    mvp.get(mvpd, Matrix2::COLUMN_MAJOR);
    float mvpf[16];
    for (std::size_t i = 0u; i < 16u; i++) mvpf[i] = (float)mvpd[i];

    glUniformMatrix4fv(pointShader.uMVP, 1, false, mvpf);

    // work with texture0
    glActiveTexture(state.textureUnit);
    glUniform1i(pointShader.uTexture, state.textureUnit - GL_TEXTURE0);

    // sync the current color with the shader
    glUniform4f(pointShader.uColor, ((state.color >> 16) & 0xFF) / (float)255, ((state.color >> 8) & 0xFF) / (float)255,
                (state.color & 0xFF) / (float)255, ((state.color >> 24) & 0xFF) / (float)255);

    double angle = (view.scene.camera.azimuth * M_PI) / 180.0;
    glUniform2f(pointShader.uMapRotation, (float)angle, (float)angle);
    glUniform1f(pointShader.uDrawTilt, (float)((90.0 + view.scene.camera.elevation)*M_PI/180.0));
    glUniform3f(pointShader.uWcsScale,
        (float)view.scene.displayModel->projectionXToNominalMeters,
        (float)view.scene.displayModel->projectionYToNominalMeters,
        (float)view.scene.displayModel->projectionZToNominalMeters);
    glUniform3f(pointShader.uCameraRtc,
        (float)(view.scene.camera.location.x-ctx.centroidProj.x),
        (float)(view.scene.camera.location.y-ctx.centroidProj.y),
        (float)(view.scene.camera.location.z-ctx.centroidProj.z));
    glUniform1f(pointShader.uTanHalfFov, (float)tan((view.scene.camera.fov / 2.0)* M_PI / 180.0));
    glUniform1f(pointShader.uViewportHeight, (float)view.viewport.height);

    GLTextureAtlas *iconAtlas;
    GLMapRenderGlobals_getIconAtlas(&iconAtlas, context);

    glEnableVertexAttribArray(pointShader.aVertexCoords);
    glEnableVertexAttribArray(pointShader.spriteBottomLeft);
    glEnableVertexAttribArray(pointShader.spriteDimensions);
    glEnableVertexAttribArray(pointShader.aAbsoluteRotationFlag);
    glEnableVertexAttribArray(pointShader.aRotation);
    glEnableVertexAttribArray(pointShader.aPointSize);
    glEnableVertexAttribArray(pointShader.aColor);

    for (auto &buf : pointsBuffers) {
        // set the texture for this batch
        state.texId = buf.primitive.texid;
        if (!state.texId)
            continue;

        if (drawForHitTest) {
            GLuint hitTestTexture;
            GLMapRenderGlobals_getWhitePixel(&hitTestTexture, context);
            glBindTexture(GL_TEXTURE_2D, hitTestTexture);
        } else {
            glBindTexture(GL_TEXTURE_2D, state.texId);
        }

        // set the color for this batch
        // XXX - 
        state.color = 0xFFFFFFFFu;

            
        if (drawForHitTest) {
            glUniform4f(pointShader.uColor, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            glUniform4f(pointShader.uColor, ((state.color >> 16) & 0xFF) / (float)255,
                        ((state.color >> 8) & 0xFF) / (float)255, (state.color & 0xFF) / (float)255,
                        ((state.color >> 24) & 0xFF) / (float)255);
        }

        glBindBuffer(GL_ARRAY_BUFFER, buf.primitive.vbo);

        glVertexAttribPointer(pointShader.aVertexCoords, buf.layout.vertex.points.position);
        glVertexAttribPointer(pointShader.spriteBottomLeft, buf.layout.vertex.points.spriteBottomLeft);
        glVertexAttribPointer(pointShader.spriteDimensions, buf.layout.vertex.points.spriteDimensions);
        glVertexAttribPointer(pointShader.aAbsoluteRotationFlag, buf.layout.vertex.points.absoluteRotationFlag);
        glVertexAttribPointer(pointShader.aRotation, buf.layout.vertex.points.rotation);
        glVertexAttribPointer(pointShader.aPointSize, buf.layout.vertex.points.pointSize);
        if (drawForHitTest) {
            glVertexAttribPointer(pointShader.aColor, buf.layout.vertex.points.id);
        } else {
            glVertexAttribPointer(pointShader.aColor, buf.layout.vertex.points.color);
        }

        glDrawArrays(buf.primitive.mode, 0, buf.primitive.count);
    }

    glDisableVertexAttribArray(pointShader.aVertexCoords);
    glDisableVertexAttribArray(pointShader.spriteBottomLeft);
    glDisableVertexAttribArray(pointShader.spriteDimensions);
    glDisableVertexAttribArray(pointShader.aAbsoluteRotationFlag);
    glDisableVertexAttribArray(pointShader.aRotation);
    glDisableVertexAttribArray(pointShader.aPointSize);
    glDisableVertexAttribArray(pointShader.aColor);

    glBindBuffer(GL_ARRAY_BUFFER, 0);

    if (state.texId != 0) {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
    glUseProgram(GL_NONE);

    glDisable(GL_POLYGON_OFFSET_FILL);
    glPolygonOffset(0.f, 0.f);

    return code;
}
void GLBatchGeometryRenderer4::start() NOTHROWS
{}
void GLBatchGeometryRenderer4::stop() NOTHROWS
{}
void GLBatchGeometryRenderer4::release() NOTHROWS
{
    markForRelease();

    if (!markedForRelease.empty()) {
        glDeleteBuffers((GLsizei)markedForRelease.size(), markedForRelease.data());
        markedForRelease.clear();
    }

    fbo.pixel.reset();
    fbo.tile.reset();
}


