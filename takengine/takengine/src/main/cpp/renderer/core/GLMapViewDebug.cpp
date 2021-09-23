//
// Created by GeoDev on 1/10/2021.
//

#include "renderer/core/GLMapViewDebug.h"

#include "feature/SpatialCalculator2.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLGlobeSurfaceRenderer.h"
#include "renderer/core/GLMapView2.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Renderer::Elevation;

namespace
{
    Envelope2 computeVisibleOffscreenBounds(const GLGlobe &view, const GLTerrainTile *tiles, const std::size_t count) NOTHROWS;
    void drawOrthoLines(const GLGlobe &view, const GLenum mode, const std::size_t size, const float *pos, const std::size_t count, const float r, const float g, const float b, const float a) NOTHROWS;
}

void TAK::Engine::Renderer::Core::GLMapViewDebug_drawVisibleSurface(GLGlobe &view) NOTHROWS
{
    // compute MBB of all visible tiles
    Envelope2 xmbb = computeVisibleOffscreenBounds(view, &view.offscreen.visibleTiles.value.at(0), view.offscreen.visibleTiles.value.size());

    // construct ortho MapSceneModel
    MapSceneModel2 ortho = view.renderPasses[0].scene;
    MapSceneModel2_createOrtho(&ortho, view.renderPasses[0].scene.width, view.renderPasses[0].scene.height, GeoPoint2(xmbb.maxY, xmbb.minX), GeoPoint2(xmbb.minY, xmbb.maxX));

    // reset RP0 to ortho for surface render
    GLGlobe::State rp0(view.renderPasses[0]);
    view.renderPasses[0].drawSrid = 4326;
    view.renderPasses[0].crossesIDL = (xmbb.minX < -180.0 || xmbb.maxX > 180.0);
    view.renderPasses[0].northBound = xmbb.maxY;
    view.renderPasses[0].westBound = xmbb.minX;
    view.renderPasses[0].southBound = xmbb.minY;
    view.renderPasses[0].eastBound = xmbb.maxX;
    view.renderPasses[0].upperLeft = GeoPoint2(view.renderPasses[0].northBound, view.renderPasses[0].westBound);
    view.renderPasses[0].upperRight = GeoPoint2(view.renderPasses[0].northBound, view.renderPasses[0].eastBound);
    view.renderPasses[0].lowerRight = GeoPoint2(view.renderPasses[0].southBound, view.renderPasses[0].eastBound);
    view.renderPasses[0].lowerLeft = GeoPoint2(view.renderPasses[0].southBound, view.renderPasses[0].westBound);
    view.renderPasses[0].drawRotation = 0.0;
    view.renderPasses[0].drawTilt = 0.0;
    view.renderPasses[0].drawLat = (xmbb.maxY + xmbb.minY) / 2.0;
    view.renderPasses[0].drawLng = (xmbb.maxX + xmbb.minX) / 2.0;
    view.renderPasses[0].scene = ortho;
    view.renderPasses[0].drawMapScale = atakmap::core::AtakMapView_getMapScale(view.renderPasses[0].scene.displayDpi, view.renderPasses[0].drawMapResolution);

    // render surface using ortho
    view.getSurfaceRenderer().draw();

    // restore RP0
    view.renderPasses[0] = rp0;
}
void TAK::Engine::Renderer::Core::GLMapViewDebug_drawCameraRangeRing(const GLGlobe &view, const std::size_t size, const double radiusMeters) NOTHROWS
{
    // cam LLA
    GeoPoint2 cam;
    view.renderPasses[0].scene.projection->inverse(&cam, view.renderPasses[0].scene.camera.location);
    if(TE_ISNAN(cam.altitude) || size == 2u)
        cam.altitude = 0.0;

    std::vector<float> xyz;
    xyz.reserve(180u*size);

    for(std::size_t i = 0u; i < 180u; i++) {
        GeoPoint2 lla = GeoPoint2_pointAtDistance(GeoPoint2(cam.latitude, cam.longitude), i*2, radiusMeters, true);
        TAK::Engine::Math::Point2<double> p;
        view.renderPass->scene.forward(&p, lla);
        xyz.push_back((float)p.x);
        xyz.push_back((float)p.y);
        if(size == 3u)
            xyz.push_back((float)p.z);
    }
    drawOrthoLines(view, GL_LINE_LOOP,2u, &xyz.at(0), xyz.size()/2u, 1.f, 0.f, 0.f, 1.f);
}
void GLMapViewDebug_drawFrustum(const GLGlobe &view, const std::size_t size) NOTHROWS
{
    TAK::Engine::Math::Point2<double> inverse[] {
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].scene.focusX,view.renderPasses[0].scene.focusY,0), // cam
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].left,view.renderPasses[0].top,view.renderPasses[0].scene.camera.far), // UL
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].scene.focusX,view.renderPasses[0].scene.focusY,0), // cam
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].right,view.renderPasses[0].top,view.renderPasses[0].scene.camera.far), // UR
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].scene.focusX,view.renderPasses[0].scene.focusY,0), // cam
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].right,view.renderPasses[0].bottom,view.renderPasses[0].scene.camera.far), // LR
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].scene.focusX,view.renderPasses[0].scene.focusY,0), // cam
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].left,view.renderPasses[0].bottom,view.renderPasses[0].scene.camera.far), // LL

        TAK::Engine::Math::Point2<double>(view.renderPasses[0].left,view.renderPasses[0].top,view.renderPasses[0].scene.camera.far), // UL
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].right,view.renderPasses[0].top,view.renderPasses[0].scene.camera.far), // UR

        TAK::Engine::Math::Point2<double>(view.renderPasses[0].right,view.renderPasses[0].top,view.renderPasses[0].scene.camera.far), // UR
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].right,view.renderPasses[0].bottom,view.renderPasses[0].scene.camera.far), // LR

        TAK::Engine::Math::Point2<double>(view.renderPasses[0].right,view.renderPasses[0].bottom,view.renderPasses[0].scene.camera.far), // LR
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].left,view.renderPasses[0].bottom,view.renderPasses[0].scene.camera.far), // LL

        TAK::Engine::Math::Point2<double>(view.renderPasses[0].left,view.renderPasses[0].bottom,view.renderPasses[0].scene.camera.far), // LL
        TAK::Engine::Math::Point2<double>(view.renderPasses[0].left,view.renderPasses[0].top,view.renderPasses[0].scene.camera.far), // UL
    };
    GeoPoint2 frustum[16u];
    for(std::size_t i = 0u; i < 16u; i++)
        view.renderPasses[0u].scene.projection->inverse(frustum+i, inverse[i]);
    GLMapViewDebug_drawLine(view, GL_LINES, size, frustum, 16);
}
void TAK::Engine::Renderer::Core::GLMapViewDebug_drawLine(const GLGlobe &view, const GLenum mode, const std::size_t size, const TAK::Engine::Core::GeoPoint2 *points, const std::size_t count) NOTHROWS
{
    std::vector<float> xyz;
    xyz.reserve(count*size);

    for(std::size_t i = 0; i < 16; i++) {
        TAK::Engine::Math::Point2<double> p;
        view.renderPass->scene.forward(&p, points[i]);
        xyz.push_back((float)p.x);
        xyz.push_back((float)p.y);
        if(size == 3u)
            xyz.push_back((float)p.z);
    }

    drawOrthoLines(view, mode,size, &xyz.at(0), 8u, 0.f, 1.f, 1.f, 1.f);
}

namespace
{
    Envelope2 computeVisibleOffscreenBounds(const GLGlobe &view, const GLTerrainTile *tiles, const std::size_t count) NOTHROWS
    {
        // XXX - handle IDL cross
        struct {
            TAK::Engine::Feature::Envelope2 east;
            TAK::Engine::Feature::Envelope2 west;
            bool e{false};
            bool w{false};

            void push_back(const TAK::Engine::Feature::Envelope2 &x) NOTHROWS
            {
                const double cx = (x.minX+x.maxX) / 2.0;
                if(cx >= 0.0) {
                    if(e)   east = TAK::Engine::Feature::SpatialCalculator_union(east, x);
                    else    east = x;
                    e = true;
                } else {
                    if(w)   west = TAK::Engine::Feature::SpatialCalculator_union(west, x);
                    else    west = x;
                    w = true;
                }
            }
        } mbb;
        for(std::size_t i = 0u; i < count; i++)
            mbb.push_back(tiles[i].tile->aabb_wgs84);
        if(mbb.e && mbb.w) {
            // crosses IDL
            if(mbb.east.maxX >= 180.0 && mbb.west.minX <= -180.0) {
                if(view.renderPasses[0].drawLng >= 0.0) {
                    mbb.push_back(TAK::Engine::Feature::Envelope2(mbb.west.minX + 360.0,
                                                                  mbb.west.minY, 0.0,
                                                                  mbb.west.maxX + 360.0,
                                                                  mbb.west.maxY, 0.0));
                    mbb.w = false;
                } else {
                    mbb.push_back(TAK::Engine::Feature::Envelope2(mbb.east.minX - 360.0,
                                                                  mbb.east.minY, 0.0,
                                                                  mbb.east.maxX - 360.0,
                                                                  mbb.east.maxY, 0.0));
                    mbb.e = false;
                }
            } else {
                // both hemis are populated, but sharing IDL, should share prime meridian
                mbb.east = TAK::Engine::Feature::SpatialCalculator_union(mbb.east, mbb.west);
                mbb.w = false;
            }
        }
        return mbb.e ? mbb.east : mbb.west;
    }
    void drawOrthoLines(const GLGlobe &view, const GLenum mode, const std::size_t size, const float *pos, const std::size_t count, const float r, const float g, const float b, const float a) NOTHROWS
    {
        glEnable(GL_BLEND);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glOrthof((float)view.renderPasses[0u].left, (float)view.renderPass[0u].right, (float)view.renderPasses[0u].bottom, (float)view.renderPasses[0u].top, (float)view.renderPasses[0u].scene.camera.near,(float)view.renderPasses[0u].scene.camera.far);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_MODELVIEW);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glLoadIdentity();

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glVertexPointer(size, GL_FLOAT, 0, pos);

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glDrawArrays(mode, 0, count);

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(atakmap::renderer::GLES20FixedPipeline::MM_GL_MODELVIEW);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
        glDisable(GL_BLEND);
    }
}
