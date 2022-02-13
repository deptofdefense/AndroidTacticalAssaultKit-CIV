#include "renderer/core/GLGlobe.h"

#include <algorithm>
#include <cmath>
#include <list>
#include <sstream>
#include <unordered_map>

#include <GLES2/gl2.h>

#include "core/Ellipsoid2.h"
#include "core/GeoPoint.h"
#include "core/LegacyAdapters.h"
#include "core/ProjectionFactory.h"
#include "core/ProjectionFactory3.h"
#include "feature/GeometryTransformer.h"
#include "feature/LineString2.h"
#include "feature/Envelope2.h"
#include "feature/SpatialCalculator2.h"
#include "math/AABB.h"
#include "math/Statistics.h"
#include "math/Rectangle.h"
#include "math/Ellipsoid2.h"
#include "math/Frustum2.h"
#include "math/Frustum2.h"
#include "math/Ray2.h"
#include "math/Sphere2.h"
#include "math/Mesh.h"
#include "model/MeshTransformer.h"
#include "port/Platform.h"
#include "port/STLVectorAdapter.h"
#include "port/STLListAdapter.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/GLDepthSampler.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLOffscreenFramebuffer.h"
#include "renderer/GLTexture2.h"
#include "renderer/GLSLUtil.h"
#include "renderer/GLWireframe.h"
#include "renderer/GLWorkers.h"
#include "renderer/Shader.h"
#include "renderer/core/GLAtmosphere.h"
#include "renderer/core/GLGlobeSurfaceRenderer.h"
#include "renderer/core/GLOffscreenVertex.h"
#include "renderer/core/GLLayerFactory2.h"
#include "renderer/core/GLLabelManager.h"
#include "renderer/core/GLMapViewDebug.h"
#include "renderer/elevation/GLTerrainTile.h"
#include "renderer/elevation/ElMgrTerrainRenderService.h"
#include "thread/Monitor.h"
#include "util/ConfigOptions.h"
#include "util/Memory.h"
#include "util/MathUtils.h"
#include "util/Distance.h"
#include "GLGlobe.h"

using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Renderer::Core::Controls;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::raster::osm;
using namespace atakmap::renderer;

#define GLMAPVIEW2_DEPTH_ENABLED 1
#define TILT_WIDTH_ADJUST_FACTOR 1.05
#define DEBUG_DRAW_MESH_SKIRT 0

#define DEFAULT_TILT_SKEW_OFFSET 1.2
#define DEFAULT_TILT_SKEW_MULT 4.0

#define SAMPLER_SIZE 8u

namespace {

    const double recommendedGridSampleDistance = 0.125;
    double maxeldiff = 1000.0;
    double maxSkewAdj = 1.25;

    struct AsyncAnimateBundle
    {
        double lat;
        double lon;
        double alt;
        double scale;
        double rot;
        double tilt;
        double factor;
    };

    struct AsyncRenderableRefreshBundle
    {
        std::list<std::shared_ptr<GLLayer2>> renderables;
        std::list<std::shared_ptr<GLLayer2>> releaseables;
    };

    struct AsyncResizeBundle
    {
        std::size_t width;
        std::size_t height;
    };

    struct AsyncSurfaceIntersectBundle
    {
        float x;
        float y;
        GLGlobe *view;
        GeoPoint2 result;
        TAKErr code;
        bool done;
        Monitor *monitor;
    };

    struct AsyncSurfacePickBundle
    {
        float x;
        float y;
        GLGlobe *view;
        std::shared_ptr<const TerrainTile> result[9u];
        TAKErr code;
        bool done;
        Monitor *monitor;
    };

    class DebugTimer
    {
    public :
        DebugTimer(const char *text, GLGlobe &view_, const bool enabled_) NOTHROWS:
            start(Platform_systime_millis()),
            view(view_),
            enabled(enabled_)
        {
            if(enabled)
                msg << text;
        }
    public :
        void stop() NOTHROWS
        {
            if(enabled) {
                msg << " " << (Platform_systime_millis() - start) << "ms";
                view.addRenderDiagnosticMessage(msg.str().c_str());
            }
        }
    private :
        std::ostringstream msg;
        GLGlobe &view;
        int64_t start;
        bool enabled;
    };

    GeoPoint2 getPoint(const AtakMapView &view) NOTHROWS
    {
        GeoPoint legacy;
        view.getPoint(&legacy, false);
        GeoPoint2 retval;
        GeoPoint_adapt(&retval, legacy);
        return retval;
    }

    float getFocusX(const AtakMapView &view) NOTHROWS
    {
        atakmap::math::Point<float> focus;
        view.getController()->getFocusPoint(&focus);
        return focus.x;
    }

    float getFocusY(const AtakMapView &view) NOTHROWS
    {
        atakmap::math::Point<float> focus;
        view.getController()->getFocusPoint(&focus);
        return focus.y;
    }

    void asyncSetBaseMap(void *opaque) NOTHROWS;

    bool intersectsAABB(GeoPoint2 *value, const MapSceneModel2 &scene, const TAK::Engine::Feature::Envelope2 &aabb_wgs84, float x, float y) NOTHROWS
    {
        Point2<double> org(x, y, -1.0);
        Point2<double> tgt(x, y, 1.0);

        if (scene.inverseTransform.transform(&org, org) != TE_Ok)
            return false;
        if (scene.inverseTransform.transform(&tgt, tgt) != TE_Ok)
            return false;

        Point2<double> points[8];
        if (scene.projection->forward(&points[0], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.minX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
            return false;
        if(scene.projection->forward(&points[1], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.maxX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[2], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.maxX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[3], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.minX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[4], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.minX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[5], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.maxX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[6], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.maxX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[7], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.minX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
            return false;

        AABB aabb(points, 8u);
#if false
        if (aabb.contains(org) && aabb.contains(tgt)) {
            return true;
        }
#endif

        return scene.inverse(value, Point2<float>(x, y), aabb) == TE_Ok;
    }

    struct MapRendererRunnable
    {
        MapRendererRunnable(std::unique_ptr<void, void(*)(void *)> &&opaque_, void(*run_)(void *)) :
            opaque(std::move(opaque_)),
            run(run_)
        {}

        std::unique_ptr<void, void(*)(void *)> opaque;
        void(*run)(void *);
    };

    //https://stackoverflow.com/questions/1903954/is-there-a-standard-sign-function-signum-sgn-in-c-c
    template <typename T>
    int sgn(T val)
    {
        return (T(0) < val) - (val < T(0));
    }



    TAKErr intersectWithTerrainTiles(GeoPoint2 *value, const MapSceneModel2 &map_scene, std::shared_ptr<const TerrainTile> &focusTile, const std::shared_ptr<const TerrainTile> *tiles, const std::size_t numTiles, const float x, const float y) NOTHROWS;

    void drawTerrainMeshes(const GLGlobe::State &view, const float elevationScaleFactor, const std::vector<GLTerrainTile> &terrainTile, const TerrainTileShaders &ecef, const TerrainTileShaders &planar, const GLTexture2 &whitePixel, const float r, const float g, const float b, const float a) NOTHROWS;
    void drawTerrainMeshesImpl(const GLGlobe::State &renderPass, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const std::vector<GLTerrainTile> &terrainTiles, const float r, const float g, const float b, const float a) NOTHROWS;
    void drawTerrainMeshImpl(const GLGlobe::State &state, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTerrainTile &tile, const float r, const float g, const float b, const float a) NOTHROWS;
    TAKErr lla2ecef_transform(Matrix2 *value, const Projection2 &ecef, const Matrix2 *localFrame) NOTHROWS;

    float meshColors[13][4] =
    {
        {1.f, 1.f, 1.f, 1.f},
        {1.f, 0.f, 0.f, 1.f},
        {0.f, 1.f, 0.f, 1.f},
        {0.f, 0.f, 1.f, 1.f},
        {1.f, 1.f, 0.f, 1.f},
        {1.f, 0.f, 1.f, 1.f},
        {0.f, 1.f, 1.f, 1.f},
        {1.f, 0.5f, 0.5f, 1.f},
        {0.5f, 1.0f, 0.5f, 1.f},
        {0.5f, 0.5f, 1.0f, 1.f},
        {0.5f, 1.0f, 1.0f, 1.f},
        {1.0f, 1.0f, 0.5f, 1.f},
        {1.0f, 0.5f, 1.0f, 1.f},
    };

    void bindTerrainTile(GLTerrainTile &gltile) NOTHROWS
    {
        GLuint bufs[2u];
        glGenBuffers(2u, bufs);

        const TAK::Engine::Model::Mesh &mesh =  *gltile.tile->data.value;

        // VBO
        do {
            const void* buf = nullptr;
            const VertexDataLayout vertexDataLayout = mesh.getVertexDataLayout();

            glBindBuffer(GL_ARRAY_BUFFER, bufs[0u]);
            if(mesh.getVertices(&buf, TEVA_Position) != TE_Ok)
                break;
            std::size_t size = mesh.getNumVertices() * vertexDataLayout.position.stride;

            if (vertexDataLayout.interleaved)
                VertexDataLayout_requiredInterleavedDataSize(&size, vertexDataLayout, mesh.getNumVertices());
            glBufferData(GL_ARRAY_BUFFER, size, buf, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);

            gltile.vbo = bufs[0u];
            bufs[0u] = GL_NONE;
        } while(false);

        // IBO
        if(mesh.isIndexed()) {
            do {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufs[1u]);
                DataType indexType;
                if(mesh.getIndexType(&indexType) != TE_Ok)
                    break;
                std::size_t size = mesh.getNumIndices() * DataType_size(indexType);
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, size, static_cast<const uint8_t *>(mesh.getIndices()) + mesh.getIndexOffset(), GL_STATIC_DRAW);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);

                gltile.ibo = bufs[1u];
                bufs[1u] = GL_NONE;
            } while(false);
        }
        // used have been zero'd; delete any unused
        glDeleteBuffers(2u, bufs);
    }

    /**
     * @param scene                 Render scene
     * @param frustum               Render frustum
     * @param drawSrid              SRID
     * @param handleIdlCrossing
     * @param drawLng
     * @param tile
     * @return
     */
    bool cull(const MapSceneModel2 &scene, const Frustum2 &frustum, const int drawSrid, const bool handleIdlCrossing, const double drawLng, const TerrainTile &tile) NOTHROWS
    {
        TAK::Engine::Feature::Envelope2 aabbWCS(tile.aabb_wgs84);
        TAK::Engine::Feature::GeometryTransformer_transform(&aabbWCS, aabbWCS, 4326, drawSrid);

        const bool isect =
            (frustum.intersects(AABB(Point2<double>(aabbWCS.minX, aabbWCS.minY, aabbWCS.minZ),
                                     Point2<double>(aabbWCS.maxX, aabbWCS.maxY, aabbWCS.maxZ))) ||
            (handleIdlCrossing && drawLng * ((aabbWCS.minX + aabbWCS.maxX) / 2.0) < 0 &&
                frustum.intersects(
                    AABB(Point2<double>(aabbWCS.minX - (360.0 * sgn((aabbWCS.minX + aabbWCS.maxX) / 2.0)), aabbWCS.minY, aabbWCS.minZ),
                        Point2<double>(aabbWCS.maxX - (360.0 * sgn((aabbWCS.minX + aabbWCS.maxX) / 2.0)), aabbWCS.maxY, aabbWCS.maxZ)))));

        // does not intersect frustum
        if(!isect)
            return true;

        // check far plane
        Point2<double> tilectr((aabbWCS.maxX+aabbWCS.minX)/2.0, (aabbWCS.maxY+aabbWCS.minY)/2.0, (aabbWCS.maxZ+aabbWCS.minZ)/2.0);
        Point2<double> tilecnr(aabbWCS.maxY, aabbWCS.maxY, aabbWCS.maxZ);
        if(drawSrid == 4326 && fabs(tilectr.x-scene.camera.location.x) > 180.0) {
            const double hemishift = (scene.camera.location.x >= 0.0) ? 360.0 : -360.0;
            tilectr.x += hemishift;
            tilecnr.x += hemishift;
        }

        // scale to nominal display meters
        tilectr.x *= scene.displayModel->projectionXToNominalMeters;
        tilectr.y *= scene.displayModel->projectionYToNominalMeters;
        tilectr.z *= scene.displayModel->projectionZToNominalMeters;

        tilecnr.x *= scene.displayModel->projectionXToNominalMeters;
        tilecnr.y *= scene.displayModel->projectionYToNominalMeters;
        tilecnr.z *= scene.displayModel->projectionZToNominalMeters;

        const double radius = Vector2_length(Point2<double>(tilecnr.x-tilectr.x, tilecnr.y-tilectr.y, tilecnr.z-tilectr.z));
        const double distance =
                Vector2_length(Point2<double>(
                        tilectr.x-scene.camera.location.x*scene.displayModel->projectionXToNominalMeters,
                        tilectr.y-scene.camera.location.y*scene.displayModel->projectionYToNominalMeters,
                        tilectr.z-scene.camera.location.z*scene.displayModel->projectionZToNominalMeters
                        ));
        // bounding sphere outside of far meters
        return (distance > (radius+scene.camera.farMeters));
    }

    void computeSurfaceBounds(GLGlobe &view, const std::vector<GLTerrainTile> &visibleTiles) NOTHROWS
    {
        // compute MBB of all visible tiles
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
        for(auto t : visibleTiles)
            mbb.push_back(t.tile->aabb_wgs84);
        if(mbb.e && mbb.w) {
            // determine if the current view crosses the IDL -- if we have
            // tiles straddling both sides, we're crossing
            bool crossesIdl = (mbb.east.maxX >= 180.0 && mbb.west.minX <= -180.0 && view.renderPasses[0].drawLng != 0.0);
            // if globe mode and pole is in view, IDL crossing is ambiguous
            if(view.renderPasses[0].drawSrid == 4978)
                crossesIdl &= !(mbb.east.maxY == 90.0 || mbb.east.minY == -90.0);

            // crosses IDL
            if(crossesIdl) {
#define TE_IDL_EPSILON 1e-8
                if(mbb.east.minX == 0.0 && mbb.west.maxX == 0.0) {
                    // sharing both IDL and prime meridian
                    mbb.east = TAK::Engine::Feature::SpatialCalculator_union(mbb.east, mbb.west);
                    mbb.w = false;
                    mbb.east.maxX -= TE_IDL_EPSILON;
                    mbb.east.minX += TE_IDL_EPSILON;
                    mbb.east.minX += view.renderPasses[0].drawLng;
                    mbb.east.maxX += view.renderPasses[0].drawLng;
                } else
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
                // both hemis are populated, but not sharing IDL, should share
                // prime meridian
                mbb.east = TAK::Engine::Feature::SpatialCalculator_union(mbb.east, mbb.west);
                mbb.w = false;
            }
        }
        TAK::Engine::Feature::Envelope2 xmbb = mbb.e ? mbb.east : mbb.west;
        if(xmbb.minX == 0.0 || xmbb.maxX == -0.0)
            xmbb.minX += TE_IDL_EPSILON;
        if(fabs(xmbb.maxX) == 360.0)
            xmbb.maxX -= sgn(xmbb.maxX)*TE_IDL_EPSILON;

        // compute visible radius constrained mbb
        // cam LLA
        GeoPoint2 cam;
        view.renderPasses[0].scene.projection->inverse(&cam, view.renderPasses[0].scene.camera.location);
        if(TE_ISNAN(cam.altitude))
            cam.altitude = 0.0;

        Point2<double> los = Vector2_subtract<double>(view.renderPasses[0].scene.camera.target, view.renderPasses[0].scene.camera.location);
        los.x *= view.renderPasses[0].scene.displayModel->projectionXToNominalMeters;
        los.y *= view.renderPasses[0].scene.displayModel->projectionYToNominalMeters;
        los.z *= view.renderPasses[0].scene.displayModel->projectionZToNominalMeters;
        const double farMeters = view.renderPasses[0].scene.camera.farMeters;

#define HVFOV 22.5
        const double nominalGsd = (cam.altitude*std::tan(HVFOV*M_PI/180.0)) / ((double)view.renderPasses[0].scene.height / 2.0);

        // http://blog.zacharyabel.com/2012/01/slicing-spheres/
        // compute far radius on surface
        //const double surfaceRadius = Sphere2_getRadius(farMeters, cam.altitude);
        const double surfaceRadius = std::max(cam.altitude*32.0, 2000.0);

        GeoPoint2 north = GeoPoint2_pointAtDistance(GeoPoint2(cam.latitude, cam.longitude), 0.0, surfaceRadius, true);
        if(GeoPoint2_distance(GeoPoint2(90.0, cam.longitude), GeoPoint2(cam.latitude, cam.longitude), true) < surfaceRadius)
            north.latitude = 90.0;
        GeoPoint2 west = GeoPoint2_pointAtDistance(GeoPoint2(cam.latitude, cam.longitude), 270.0, surfaceRadius, true);
        GeoPoint2 south = GeoPoint2_pointAtDistance(GeoPoint2(cam.latitude, cam.longitude), 180.0, surfaceRadius, true);
        if(GeoPoint2_distance(GeoPoint2(-90.0, cam.longitude), GeoPoint2(cam.latitude, cam.longitude), true) < surfaceRadius)
            south.latitude = -90.0;
        GeoPoint2 east = GeoPoint2_pointAtDistance(GeoPoint2(cam.latitude, cam.longitude), 90.0, surfaceRadius, true);

        TAK::Engine::Feature::Envelope2 vismbb(xmbb);
        if(surfaceRadius < M_PI*TAK::Engine::Core::Ellipsoid2::WGS84.semiMinorAxis) {
            struct {
                double operator()(const MapSceneModel2 &scene, const GeoPoint2 &g) NOTHROWS {
                    GeoPoint2 cam;
                    scene.projection->inverse(&cam, scene.camera.location);
                    return GeoPoint2_distance(cam, g, true);
                }
            } camdist;

            if (camdist(view.renderPasses[0].scene, GeoPoint2(vismbb.maxY, cam.longitude)) >
                surfaceRadius)
                vismbb.maxY = std::min(vismbb.maxY, north.latitude);
            if (camdist(view.renderPasses[0].scene, GeoPoint2(cam.latitude, vismbb.minX)) >
                surfaceRadius)
                vismbb.minX = std::max(vismbb.minX, west.longitude);
            if (camdist(view.renderPasses[0].scene, GeoPoint2(vismbb.minY, cam.longitude)) >
                surfaceRadius)
                vismbb.minY = std::max(vismbb.minY, south.latitude);
            if (camdist(view.renderPasses[0].scene, GeoPoint2(cam.latitude, vismbb.maxX)) >
                surfaceRadius)
                vismbb.maxX = std::min(vismbb.maxX, east.longitude);
        }

        view.renderPasses[0].crossesIDL = (vismbb.minX < -180.0 || vismbb.maxX > 180.0);
        view.renderPasses[0].northBound = vismbb.maxY;
        view.renderPasses[0].southBound = vismbb.minY;
        view.renderPasses[0].westBound = vismbb.minX;
        if(view.renderPasses[0].westBound < -180.0)
            view.renderPasses[0].westBound += 360.0;
        view.renderPasses[0].eastBound = vismbb.maxX;
        if(view.renderPasses[0].eastBound > 180.0)
            view.renderPasses[0].eastBound -= 360.0;

        view.renderPasses[0].upperLeft = GeoPoint2(view.renderPasses[0].northBound, view.renderPasses[0].westBound);
        view.renderPasses[0].upperRight = GeoPoint2(view.renderPasses[0].northBound, view.renderPasses[0].eastBound);
        view.renderPasses[0].lowerRight = GeoPoint2(view.renderPasses[0].southBound, view.renderPasses[0].eastBound);
        view.renderPasses[0].lowerLeft = GeoPoint2(view.renderPasses[0].southBound, view.renderPasses[0].westBound);
        {
            std::ostringstream dbg;
            dbg << "COMPUTED BOUNDS " << view.renderPasses[0].northBound << "," << view.renderPasses[0].westBound << " " << view.renderPasses[0].southBound << "," << view.renderPasses[0].eastBound << " IDL=" << view.renderPasses[0].crossesIDL;
            view.addRenderDiagnosticMessage(dbg.str().c_str());
        }

        {
            std::ostringstream dbg;
            dbg << "constrain " << (surfaceRadius < M_PI*TAK::Engine::Core::Ellipsoid2::WGS84.semiMinorAxis) << " radius=" << surfaceRadius << "(" << farMeters << "@" << cam.altitude << ") n=" << north.latitude << " w=" << west.longitude << " s=" << south.latitude << " e=" << east.longitude;
            view.addRenderDiagnosticMessage(dbg.str().c_str());
        }
    }
}

void GLGlobe::cullTerrainTiles_cpu() NOTHROWS
{
    std::set<intptr_t> vis;
    for (auto& gltt : offscreen.visibleTiles.value)
        vis.insert((intptr_t)(void *)gltt.tile.get());

    const std::size_t read_idx = offscreen.tileCullFboReadIdx % 2u;

    offscreen.visibleTiles.value.clear();
    offscreen.visibleTiles.value.reserve(offscreen.computeContext[read_idx].terrainTiles.size());

    offscreen.visibleTiles.lastConfirmed.clear();
    offscreen.visibleTiles.lastConfirmed.reserve(offscreen.computeContext[read_idx].terrainTiles.size());

    GeoPoint2 focus;
    offscreen.computeScene.projection->inverse(&focus, offscreen.computeScene.camera.target);
    const int srid = offscreen.computeScene.projection->getSpatialReferenceID();

    bool crossesIDL = false;
    // compute `crossesIDL`
    if(srid == 4326) {
        // construct segment in screen space along the IDL
        Point2<double> idlNxy;
        offscreen.computeScene.forward(&idlNxy, GeoPoint2(90.0, sgn(focus.longitude) * 180.0));
        Point2<double> idlSxy;
        offscreen.computeScene.forward(&idlSxy, GeoPoint2(-90.0, sgn(focus.longitude) * 180.0));
        Point2<double> idlxy;

        // find distance from focus xy to IDL screenspace segment
        //https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
        const double x2 = idlSxy.x;
        const double x1 = idlNxy.x;
        const double y2 = idlSxy.y;
        const double y1 = idlNxy.y;
        const double x0 = offscreen.computeScene.focusX;
        const double y0 = offscreen.computeScene.focusY;
        const double test = fabs(((x2-x1)*(y1-y0))-((x1-x0)*(y2-y1))) / sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));

        crossesIDL = test <= Vector2_length(Point2<double>(offscreen.computeScene.width/2.0, offscreen.computeScene.height/2.0, 0.0));
    }
    const bool handleIdlCrossing = offscreen.computeScene.displayModel->earth->getGeomClass() ==
                                   GeometryModel2::PLANE && crossesIDL;

    Matrix2 m;
    m.set(offscreen.computeScene.camera.projection);
    m.concatenate(offscreen.computeScene.camera.modelView);
    Frustum2 frustum(m);
    for (std::size_t i = 0u; i < offscreen.computeContext[read_idx].terrainTiles.size(); i++) {
        if (!cull(offscreen.computeScene, frustum, srid, handleIdlCrossing, focus.longitude, *offscreen.computeContext[read_idx].terrainTiles[i])) {
            const auto &tile = offscreen.computeContext[read_idx].terrainTiles[i];
            const intptr_t p = (intptr_t)(void*)tile.get();
            if (vis.find(p) == vis.end())
                surfaceRenderer->markDirty(tile->aabb_wgs84, false);

            auto entry = offscreen.gltiles.find(offscreen.computeContext[read_idx].terrainTiles[i].get());
            if(entry == offscreen.gltiles.end()) {
                GLTerrainTile gltile;
                gltile.tile = tile;

                bindTerrainTile(gltile);
                offscreen.visibleTiles.value.push_back(gltile);
                offscreen.gltiles[tile.get()] = gltile;
            } else {
                offscreen.visibleTiles.value.push_back(entry->second);
            }
            offscreen.visibleTiles.lastConfirmed.push_back((intptr_t)(const void *)offscreen.computeContext[read_idx].terrainTiles[i].get());
        }
    }

    offscreen.visibleTiles.confirmed = true;
}

void GLGlobe::cullTerrainTiles_pbo() NOTHROWS
{
    const unsigned subsamplex = 8;
    const unsigned subsampley = 8;
    const std::size_t render_width = (std::size_t)offscreen.computeScene.width / subsamplex;
    const std::size_t render_height = (std::size_t)offscreen.computeScene.height / subsampley;

    const std::size_t read_idx = offscreen.tileCullFboReadIdx % 2u;
    const std::size_t write_idx = (offscreen.tileCullFboReadIdx + 1u) % 2u;

    GLOffscreenFramebuffer &fbo_r = offscreen.computeContext[read_idx].tileCullFbo;
    GLOffscreenFramebuffer &fbo_w = offscreen.computeContext[write_idx].tileCullFbo;
    GLuint &pbo_r = offscreen.computeContext[read_idx].tileCullPbo;
    GLuint &pbo_w = offscreen.computeContext[write_idx].tileCullPbo;

    // ensure FBO is sufficiently large
    if (fbo_w.handle && (fbo_w.textureWidth < render_width || fbo_w.textureHeight < render_height)) {
        GLOffscreenFramebuffer_release(fbo_w);
        glDeleteBuffers(1u, &pbo_w);
        pbo_w = GL_NONE;
    }

    // initialize as necessary
    if (!fbo_w.handle) {
        GLOffscreenFramebuffer::Options fbo_opts;
        fbo_opts.colorFormat = GL_RGBA;
        fbo_opts.colorType = GL_UNSIGNED_BYTE;

        GLOffscreenFramebuffer_create(&fbo_w, (int)render_width, (int)render_height, fbo_opts);

        glGenBuffers(1u, &pbo_w);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo_w);
        glBufferData(GL_PIXEL_PACK_BUFFER, fbo_w.textureWidth*fbo_w.textureHeight*sizeof(uint32_t), nullptr, GL_DYNAMIC_READ);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, GL_NONE);
    }

    fbo_w.width = (int)render_width;
    fbo_w.height = (int)render_height;

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glOrthof(0.f, 0.f, (float)offscreen.computeScene.width, (float)offscreen.computeScene.height, (float)offscreen.computeScene.camera.near, (float)offscreen.computeScene.camera.far);

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);

    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);

    GLint viewport[4];
    GLint boundFbo;
    glGetIntegerv(GL_VIEWPORT, viewport);
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, (GLint *)&boundFbo);
    GLboolean blend_enabled = glIsEnabled(GL_BLEND);

    // clear to zero
    glClearColor(0.f, 0.f, 0.f, 0.f);

    fbo_w.bind();

    glViewport(0, 0, (GLsizei)fbo_w.width, (GLsizei)fbo_w.height);

    glDisable(GL_BLEND);

    offscreen.computeContext[write_idx].visIndices.clear();
    offscreen.computeContext[write_idx].visIndices.reserve(offscreen.computeContext[write_idx].terrainTiles.size());

    DebugTimer dt("Cull [draw tiles]", *this, diagnosticMessagesEnabled);

    Matrix2 m;
    m.set(offscreen.computeScene.camera.projection);
    m.concatenate(offscreen.computeScene.camera.modelView);
    Frustum2 frustum(m);

    GeoPoint2 focus;
    offscreen.computeScene.projection->inverse(&focus, offscreen.computeScene.camera.target);
    const int srid = offscreen.computeScene.projection->getSpatialReferenceID();

    bool crossesIDL = false;
    // compute `crossesIDL`
    if(srid == 4326) {
        // construct segment in screen space along the IDL
        Point2<double> idlNxy;
        offscreen.computeScene.forward(&idlNxy, GeoPoint2(90.0, sgn(focus.longitude) * 180.0));
        Point2<double> idlSxy;
        offscreen.computeScene.forward(&idlSxy, GeoPoint2(-90.0, sgn(focus.longitude) * 180.0));
        Point2<double> idlxy;

        // find distance from focus xy to IDL screenspace segment
        //https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line
        const double x2 = idlSxy.x;
        const double x1 = idlNxy.x;
        const double y2 = idlSxy.y;
        const double y1 = idlNxy.y;
        const double x0 = offscreen.computeScene.focusX;
        const double y0 = offscreen.computeScene.focusY;
        const double test = fabs(((x2-x1)*(y1-y0))-((x1-x0)*(y2-y1))) / sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));

        crossesIDL = test <= Vector2_length(Point2<double>(offscreen.computeScene.width/2.0, offscreen.computeScene.height/2.0, 0.0));
    }
    const bool handleIdlCrossing = offscreen.computeScene.displayModel->earth->getGeomClass() ==
                                   GeometryModel2::PLANE && crossesIDL;

    auto ctx = GLTerrainTile_begin(offscreen.computeScene,
                                    (srid == 4978) ? offscreen.ecef.pick : offscreen.planar.pick);

    Matrix2 lla2tex(0.0, 0.0, 0.0, 0.5,
                    0.0, 0.0, 0.0, 0.5,
                    0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 1.0);
    GLTerrainTile_bindTexture(ctx, offscreen.whitePixel->getTexId(), 1u, 1u);
    for(std::size_t i = 0u; i < offscreen.computeContext[write_idx].terrainTiles.size(); i++) {
        const auto &tile = offscreen.computeContext[write_idx].terrainTiles[i];
        auto gltile = offscreen.gltiles.find(tile.get());
        if (cull(offscreen.computeScene, frustum, srid, handleIdlCrossing, focus.longitude, *offscreen.computeContext[write_idx].terrainTiles[i]))
            continue;

        // encode ID as RGBA
        const std::size_t id = offscreen.computeContext[write_idx].visIndices.size();
        const float a = (((id+1u) >> 24) & 0xFF) / 255.f;
        const float b = (((id+1u) >> 16) & 0xFF) / 255.f;
        const float g = (((id+1u) >> 8) & 0xFF) / 255.f;
        const float r = ((id+1u) & 0xFF) / 255.f;
        // draw the tile
        GLTerrainTile &gltt = offscreen.gltiles[tile.get()];
        if(!gltt.tile) {
            gltt.tile = tile;
            bindTerrainTile(gltt);
        }
        GLTerrainTile_drawTerrainTiles(ctx, lla2tex, &gltt, 1u, r, g, b, a);
        // mark visible
        offscreen.computeContext[write_idx].visIndices.push_back(i);
    }
    GLTerrainTile_end(ctx);
    dt.stop();

    const std::vector<std::size_t> *visIndices = &offscreen.computeContext[write_idx].visIndices;
    bool confirmed = true;

    if (fbo_r.handle) { // read from the back buffer
        // read the pixels
        DebugTimer mb("Cull [map buffer]", *this, diagnosticMessagesEnabled);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo_r);
        const uint32_t *rgba = (const uint32_t *)glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, sizeof(uint32_t)*(fbo_r.textureWidth*fbo_r.height), GL_MAP_READ_BIT);
        mb.stop();
        if (rgba) {
            DebugTimer pt("Cull [process texture]", *this, diagnosticMessagesEnabled);
            // 16kb -- L1 cache size for lower end supported devices (S5, Tab S)
#define PROCESS_BUF_SIZE 4096u
            uint32_t visb[PROCESS_BUF_SIZE];
            // reserve a chunk for the visible IDs
            const std::size_t idReserved = offscreen.computeContext[read_idx].visIndices.size()+1u;
            const std::size_t idReservedOff = (PROCESS_BUF_SIZE-idReserved);
            memset(visb+idReservedOff, 0u, idReserved*sizeof(uint32_t));

            // scan the readback buffer and toggle indices that are present (offset by one)
            const std::size_t readbackLimit = (fbo_r.width*fbo_r.height);
            // compute the maximum number of pixels that can be processed per
            // pass -- this is the amount of room in our process buffer, minus
            // the section reserved for IDs
            const std::size_t maxProcessPerPass = std::min((std::size_t)PROCESS_BUF_SIZE-idReserved, readbackLimit);
            // number of pixels remaining to be processed
            std::size_t rem = readbackLimit;
            if(fbo_r.width == fbo_r.textureWidth) {
                while (rem) {
                    // compute the number of pixels to be processed this pass
                    const std::size_t processThisPass = std::min(maxProcessPerPass, rem);
                    // copy from the pixel buffer into the process buffer
                    memcpy(visb, rgba, processThisPass * sizeof(uint32_t));
                    // bump the pixel buffer pointer; update the remaining count
                    rgba += processThisPass;
                    rem -= processThisPass;
                    // process the pixels for this pass
                    for (std::size_t i = 0u; i < processThisPass; i++) {
                        const auto id = visb[i];
                        // toggle the ID in the reserved section
                        visb[idReservedOff + id] = 0x1;
                    }
                }
            } else {
                const std::size_t numScansPerProcess = (maxProcessPerPass/fbo_r.textureWidth);
                while (rem > fbo_r.textureWidth) {
                    // compute the number of pixels to be processed this pass
                    const std::size_t numScansThisPass = std::min(
                            numScansPerProcess,
                            (rem/fbo_r.textureWidth));
                    // copy scans from the pixel buffer into the process buffer
                    for(std::size_t i = 0u; i < numScansThisPass; i++) {
                        memcpy(visb+(fbo_r.width*i), rgba, fbo_r.width*sizeof(uint32_t));
                        rgba += fbo_r.textureWidth;
                    }
                    // bump the pixel buffer pointer; update the remaining count
                    rem -= (numScansThisPass*fbo_r.width);
                    // process the pixels for this pass
                    const std::size_t processThisPass = numScansThisPass*fbo_r.width;
                    for (std::size_t i = 0u; i < processThisPass; i++) {
                        const auto id = visb[i];
                        // toggle the ID in the reserved section
                        visb[idReservedOff + id] = 0x1;
                    }
                }
                if (rem) {
                    // copy from the pixel buffer into the process buffer
                    memcpy(visb, rgba, rem * sizeof(uint32_t));
                    // process the pixels for this pass
                    for (std::size_t i = 0u; i < rem; i++) {
                        const auto id = visb[i];
                        // toggle the ID in the reserved section
                        visb[idReservedOff + id] = 0x1;
                    }
                }
            }
            pt.stop();
            DebugTimer vi("Cull [compute visindices]", *this, diagnosticMessagesEnabled);
            std::vector<std::size_t> visResolved;
            visResolved.reserve(idReserved-1u);
            // iterate the ID section and update the front, omitting ID 0
            for(std::size_t i = 1u; i < idReserved; i++) {
                if(!visb[idReservedOff+i])
                    continue;
                // reconstruct the index from the ID
                const std::size_t visIndicesIdx = i-1u;
                // toggle the global ID
                visResolved.push_back(offscreen.computeContext[read_idx].visIndices[visIndicesIdx]);
            }
            // compact the visible indices, marking previously not visible tiles as dirty
            offscreen.computeContext[read_idx].visIndices.clear();
            const intptr_t *lastConfirmed = &offscreen.visibleTiles.lastConfirmed.at(0);
            for (std::size_t i = 0u; i < visResolved.size(); i++) {
                offscreen.computeContext[read_idx].visIndices.push_back(visResolved[i]);

                const TerrainTile& tt = *offscreen.computeContext[read_idx].terrainTiles[visResolved[i]];
                const intptr_t p = (intptr_t)(void*)&tt;
                bool isDirty = true;
                // we could use a map or set here, however, given the size of
                // data being processed, the brute force search over the array
                // probably wins out due to CPU cache
                for(std::size_t j = 0u; j < offscreen.visibleTiles.lastConfirmed.size(); j++) {
                    if(p == lastConfirmed[j]) {
                        isDirty = false;
                        break;
                    }
                }
                if(isDirty)
                    surfaceRenderer->markDirty(tt.aabb_wgs84, false);
            }

            // confirmed
            visIndices = &offscreen.computeContext[read_idx].visIndices;
            vi.stop();
        } else {
            // the read buffer is out of sync or the read buffer could not be
            // mapped. If out of sync, we are not confirmed; if not mapped
            // we'll mark confirmed
            confirmed = !rgba;
        }
        glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, GL_NONE);
    }
    DebugTimer ct("Cull [set confirmed tiles]", *this, diagnosticMessagesEnabled);
    if(!visIndices->empty())
        setVisibleTerrainTiles(&visIndices->at(0), visIndices->size(), confirmed);
    ct.stop();

    // we do all of this last to give the GPU a chance to complete the render. If the render has not completed when we call `glReadPixels`, we may stall

    // start the pixel buffer read
    DebugTimer rp("Cull [read pixels]", *this, diagnosticMessagesEnabled);
    glReadBuffer(GL_COLOR_ATTACHMENT0);
    glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo_w);
    glReadPixels(0, 0, fbo_w.textureWidth, fbo_w.textureHeight, GL_RGBA, GL_UNSIGNED_BYTE, (void*)0);
    glBindBuffer(GL_PIXEL_PACK_BUFFER, GL_NONE);
    rp.stop();

    // restore GL state
    glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

    if (blend_enabled)
        glEnable(GL_BLEND);

    GLenum invalid = GL_DEPTH_BUFFER_BIT;
    glInvalidateFramebuffer(GL_FRAMEBUFFER, 1u, &invalid);
    glBindFramebuffer(GL_FRAMEBUFFER, boundFbo);
}
void GLGlobe::setVisibleTerrainTiles(const std::size_t* visIndices, const std::size_t count, const bool confirmed) NOTHROWS
{
    const std::size_t read_idx = offscreen.tileCullFboReadIdx % 2u;

    offscreen.visibleTiles.value.clear();
    offscreen.visibleTiles.value.reserve(count);
    if(confirmed) {
        offscreen.visibleTiles.lastConfirmed.clear();
        offscreen.visibleTiles.lastConfirmed.reserve(count);
    }
    for (std::size_t i = 0; i < count; i++) {
        const std::size_t visidx = visIndices[i];
        auto entry = offscreen.gltiles.find(offscreen.computeContext[read_idx].terrainTiles[visidx].get());
        if(entry == offscreen.gltiles.end()) {
            GLTerrainTile gltile;
            gltile.tile = offscreen.computeContext[read_idx].terrainTiles[visidx];

            bindTerrainTile(gltile);
            offscreen.visibleTiles.value.push_back(gltile);
            offscreen.gltiles[offscreen.computeContext[read_idx].terrainTiles[visidx].get()] = gltile;
        } else {
            offscreen.visibleTiles.value.push_back(entry->second);
        }
        if(confirmed)
            offscreen.visibleTiles.lastConfirmed.push_back((intptr_t)(const void *)offscreen.computeContext[read_idx].terrainTiles[visidx].get());
    }
    // resolved the compute set
    offscreen.visibleTiles.confirmed = confirmed;
    offscreen.computeContext[read_idx].processed = true;
}

GLGlobe::GLGlobe(RenderContext &ctx, AtakMapView &aview,
                       int left, int bottom,
                       int right, int top) NOTHROWS :
    GLGlobeBase(ctx, aview.getDisplayDpi(), MapCamera2::Perspective),
    terrain(new ElMgrTerrainRenderService(ctx), Memory_deleter_const<TerrainRenderService, ElMgrTerrainRenderService>),
    view(aview),
    enableMultiPassRendering(true),
    debugDrawBounds(false),
    numRenderPasses(0u),
    terrainBlendFactor(1.0f),
    debugDrawOffscreen(false),
    dbgdrawflags(4),
    debugDrawMesh(false),
    debugDrawDepth(false),
    suspendMeshFetch(false),
    tiltSkewOffset(DEFAULT_TILT_SKEW_OFFSET),
    tiltSkewMult(DEFAULT_TILT_SKEW_MULT),
    diagnosticMessagesEnabled(false),
    inRenderPump(false)
{
    elevationScaleFactor = aview.getElevationExaggerationFactor();

    sceneModelVersion = drawVersion - 1;

    renderPasses[0].left = 0;
    renderPasses[0].bottom = 0;
    renderPasses[0].right = (int)view.getWidth();
    renderPasses[0].top = (int)view.getHeight();

    renderPasses[0].scene.set(aview.getDisplayDpi(),
        static_cast<int>(aview.getWidth()),
        static_cast<int>(aview.getHeight()),
        aview.getProjection(),
        getPoint(aview),
        getFocusX(aview),
        getFocusY(aview),
        aview.getMapRotation(),
        aview.getMapTilt(),
        aview.getMapResolution(),
        cammode);
    GLGlobeBase_glScene(renderPasses[0].scene);
    {
        double forwardmx[16];
        renderPasses[0].scene.forwardTransform.get(forwardmx, Matrix2::COLUMN_MAJOR);
        for(std::size_t i = 0u; i < 16u; i++)
            renderPasses[0].sceneModelForwardMatrix[i] = (float)forwardmx[i];
    }
    renderPasses[0].drawSrid = renderPasses[0].scene.projection->getSpatialReferenceID();
    atakmap::core::GeoPoint oldCenter;
    GeoPoint2 center;
    view.getPoint(&oldCenter);
    atakmap::core::GeoPoint_adapt(&center, oldCenter);
    renderPasses[0].drawLat = center.latitude;
    renderPasses[0].drawLng = center.longitude;
    renderPasses[0].drawAlt = center.altitude;
    renderPasses[0].drawRotation = view.getMapRotation();
    renderPasses[0].drawTilt = view.getMapTilt();
    renderPasses[0].drawMapScale = view.getMapScale();
    renderPasses[0].drawMapResolution = view.getMapResolution(renderPasses[0].drawMapScale);
    atakmap::math::Point<float> p;
    view.getController()->getFocusPoint(&p);
    renderPasses[0].focusx = p.x;
    renderPasses[0].focusy = p.y;
    renderPasses[0].near = (float)renderPasses[0].scene.camera.near;
    renderPasses[0].far = (float)renderPasses[0].scene.camera.far;

    animation.target.point.latitude = renderPasses[0].drawLat;
    animation.target.point.longitude = renderPasses[0].drawLng;
    animation.target.point.altitude = renderPasses[0].drawAlt;
    animation.target.mapScale = renderPasses[0].drawMapScale;
    animation.target.rotation = renderPasses[0].drawRotation;
    animation.target.tilt = renderPasses[0].drawTilt;
    animation.target.focusx = renderPasses[0].focusx;
    animation.target.focusy = renderPasses[0].focusy;
    animation.settled = false;
    animationFactor = 1.f;
    settled = false;
    animation.last = animation.target;
    animation.current = animation.target;

    this->renderPasses[0u].renderPass = GLGlobe::Sprites | GLGlobe::Surface | GLGlobe::Scenes | GLGlobe::XRay;
    this->renderPasses[0u].texture = 0;
    this->renderPasses[0u].basemap = true;
    this->renderPasses[0u].debugDrawBounds = this->debugDrawBounds;
    this->renderPasses[0u].viewport.x = static_cast<float>(left);
    this->renderPasses[0u].viewport.y = static_cast<float>(bottom);
    this->renderPasses[0u].viewport.width = static_cast<float>(right-left);
    this->renderPasses[0u].viewport.height = static_cast<float>(top-bottom);

    state.focus.geo.latitude = renderPasses[0].drawLat;
    state.focus.geo.longitude = renderPasses[0].drawLng;
    state.focus.geo.altitude = renderPasses[0].drawAlt;
    state.focus.x = renderPasses[0].focusx;
    state.focus.y = renderPasses[0].focusy;
    state.srid = renderPasses[0].drawSrid;
    state.resolution = renderPasses[0].drawMapResolution;
    state.rotation = renderPasses[0].drawRotation;
    state.tilt = renderPasses[0].drawTilt;
    state.width = (renderPasses[0].right-renderPasses[0].left);
    state.height = (renderPasses[0].top-renderPasses[0].bottom);

    this->diagnosticMessagesEnabled = !!ConfigOptions_getIntOptionOrDefault("glmapview.render-diagnostics", 0);
#ifdef __ANDROID__
    this->gpuTerrainIntersect = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 0);
#else
    this->gpuTerrainIntersect = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 1);
#endif

    surfaceRenderer.reset(new GLGlobeSurfaceRenderer(*this));

    MeshColor triangles; triangles.mode = TEDM_Triangles; triangles.enabled = true;
    MeshColor lines; lines.mode = TEDM_Lines; lines.enabled = false;
    MeshColor points; points.mode = TEDM_Points; points.enabled = false;
    meshDrawModes.push_back(triangles);
    meshDrawModes.push_back(lines);
    meshDrawModes.push_back(points);

    offscreen.terrainTiles2 = &offscreen.computeContext[0u].terrainTiles;
}

GLGlobe::~GLGlobe() NOTHROWS
{
    GLGlobe::stop();
}

TAKErr GLGlobe::start() NOTHROWS
{
    this->terrain->start();

    this->view.addLayersChangedListener(this);
    std::list<Layer *> layers;
    view.getLayers(layers);
    refreshLayers(layers);

    this->view.addMapElevationExaggerationFactorListener(this);
    mapElevationExaggerationFactorChanged(&this->view, this->view.getElevationExaggerationFactor());

    this->view.addMapProjectionChangedListener(this);
    mapProjectionChanged(&this->view);

    this->view.addMapMovedListener(this);
    mapMoved(&this->view, false);

    this->view.getController()->addFocusPointChangedListener(this);
    atakmap::math::Point<float> focus;
    this->view.getController()->getFocusPoint(&focus);
    mapControllerFocusPointChanged(this->view.getController(), &focus);

    this->view.addMapResizedListener(this);
    {
        WriteLock wlock(this->renderPasses0Mutex);
        renderPasses[0].left = 0;
        renderPasses[0].right = static_cast<int>(view.getWidth());
        renderPasses[0].top = static_cast<int>(view.getHeight());
        renderPasses[0].bottom = 0;
    }
    this->tiltSkewOffset = ConfigOptions_getDoubleOptionOrDefault("glmapview.tilt-skew-offset", DEFAULT_TILT_SKEW_OFFSET);
    this->tiltSkewMult = ConfigOptions_getDoubleOptionOrDefault("glmapview.tilt-skew-mult", DEFAULT_TILT_SKEW_MULT);

    this->displayDpi = view.getDisplayDpi();

    return TE_Ok;
}

TAKErr GLGlobe::stop() NOTHROWS
{
    this->view.removeLayersChangedListener(this);
    this->view.removeMapElevationExaggerationFactorListener(this);
    this->view.removeMapProjectionChangedListener(this);
    this->view.removeMapMovedListener(this);
    this->view.removeMapResizedListener(this);
    this->view.getController()->removeFocusPointChangedListener(this);

    GLGlobeBase::stop();

    this->terrain->stop();

    return TE_Ok;
}
bool GLGlobe::isRenderDiagnosticsEnabled() const NOTHROWS
{
    return diagnosticMessagesEnabled;
}
void GLGlobe::setRenderDiagnosticsEnabled(const bool enabled) NOTHROWS
{
    diagnosticMessagesEnabled = enabled;
}
void GLGlobe::addRenderDiagnosticMessage(const char *msg) NOTHROWS
{
    if (!msg)
        return;
    if(diagnosticMessagesEnabled)
        diagnosticMessages.push_back(msg);
}
bool GLGlobe::isContinuousScrollEnabled() const NOTHROWS
{
    return this->continuousScrollEnabled;
}
void GLGlobe::initOffscreenShaders() NOTHROWS
{
    GLOffscreenFramebuffer::Options opts;
    opts.colorFormat = GL_RGBA;
    opts.colorType = GL_UNSIGNED_BYTE;
    GLOffscreenFramebuffer_create(&offscreen.depthSamplerFbo, SAMPLER_SIZE, SAMPLER_SIZE, opts);

    this->offscreen.whitePixel.reset(new GLTexture2(1u, 1u, Bitmap2::RGB565));
    this->offscreen.whitePixel->setMinFilter(GL_NEAREST);
    this->offscreen.whitePixel->setMagFilter(GL_NEAREST);
    {
        const uint16_t px = 0xFFFFu;
        this->offscreen.whitePixel->load(&px, 0, 0, 1, 1);
    }

    // set up the offscreen shaders
    offscreen.ecef.color = GLTerrainTile_getColorShader(this->context, 4978, TECSO_Lighting);
    offscreen.ecef.pick = GLTerrainTile_getColorShader(this->context, 4978, 0u);
    offscreen.ecef.depth = GLTerrainTile_getDepthShader(this->context, 4978);
    offscreen.planar.color = GLTerrainTile_getColorShader(this->context, 4326, TECSO_Lighting);
    offscreen.planar.pick = GLTerrainTile_getColorShader(this->context, 4326, 0u);
    offscreen.planar.depth = GLTerrainTile_getDepthShader(this->context, 4326);
}
void GLGlobe::mapMoved(atakmap::core::AtakMapView* map_view, bool animate)
{
    atakmap::core::GeoPoint p;
    map_view->getPoint(&p);

    GeoPoint2 p2;
    lookAt(GeoPoint2(p.latitude, p.longitude, p.altitude, TAK::Engine::Core::AltitudeReference::HAE),
            map_view->getMapResolution(),
            map_view->getMapRotation(),
            map_view->getMapTilt(),
            CameraCollision::Ignore,
            animate);
}

TAKErr GLGlobe::createLayerRenderer(GLLayer2Ptr &value, Layer2 &subject) NOTHROWS
{
    return GLLayerFactory2_create(value, *this, subject);
}

void GLGlobe::mapProjectionChanged(atakmap::core::AtakMapView* map_view)
{
    int srid = map_view->getProjection();
    switch(srid) {
        case 4978 :
            setDisplayMode(MapRenderer::Globe);
            break;
        case 4326 :
            setDisplayMode(MapRenderer::Flat);
            break;
        default :
            break;
    }
}

void GLGlobe::mapResized(atakmap::core::AtakMapView *mapView)
{
    const int w = (int)mapView->getWidth();
    const int h = (int)mapView->getHeight();
    if(w <= 0 || h <= 0)
        return;
    setSurfaceSize((std::size_t)w, (std::size_t)h);
}

void GLGlobe::mapControllerFocusPointChanged(atakmap::core::AtakMapController *controller, const atakmap::math::Point<float> * const focus)
{
    setFocusPoint(focus->x, focus->y);
}

void GLGlobe::mapElevationExaggerationFactorChanged(atakmap::core::AtakMapView *map_view, const double factor)
{
    std::unique_ptr<std::pair<GLGlobe &, double>> opaque(new std::pair<GLGlobe &, double>(*this, factor));
    if (context.isRenderThread())
        glElevationExaggerationFactorChanged(opaque.release());
    else
        context.queueEvent(glElevationExaggerationFactorChanged, std::unique_ptr<void, void(*)(const void *)>(opaque.release(), Memory_leaker_const<void>));
}

void GLGlobe::glElevationExaggerationFactorChanged(void *opaque) NOTHROWS
{
    std::unique_ptr<std::pair<GLGlobe &, double>> arg(static_cast<std::pair<GLGlobe &, double> *>(opaque));
    arg->first.elevationScaleFactor = arg->second;
}

void GLGlobe::render() NOTHROWS
{
    const int64_t tick = Platform_systime_millis();
    this->renderPasses[0].renderPump++;
    GLGlobeBase::render();
    const int64_t renderPumpElapsed = Platform_systime_millis()-tick;

    if (diagnosticMessagesEnabled) {
        glDepthFunc(GL_ALWAYS);
        glDepthMask(GL_FALSE);
        glDisable(GL_DEPTH_TEST);

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glOrthof((float)renderPasses[0].left, (float)renderPasses[0].right, (float)renderPasses[0].bottom, (float)renderPasses[0].top, 1.f, -1.f);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glLoadIdentity();
        {
            std::ostringstream dbg;
            dbg << "Render Pump " << renderPumpElapsed << "ms";
            diagnosticMessages.push_back(dbg.str());
        }
        {
            std::ostringstream dbg;
            dbg << "Near " << renderPasses[0].scene.camera.nearMeters << " Far " << renderPasses[0].scene.camera.farMeters;
            diagnosticMessages.push_back(dbg.str());
        }
        {
            // XXX - print slippy zoom level
            //value->camera.location.z = (value->gsd*((float)height / 2.0) / std::tan((HVFOV)*M_PI / 180.0));
            GeoPoint2 cam;
            renderPasses[0].scene.projection->inverse(&cam, renderPasses[0].scene.camera.location);
            const double gsd = (cam.altitude*std::tan(22.5*M_PI/180.0)) / (renderPasses[0].scene.height / 2.0);
            const double lod = atakmap::raster::osm::OSMUtils::mapnikTileLeveld(gsd, 0.0);
            std::ostringstream dbg;
            double el = 0.0;
            getTerrainMeshElevation(&el, cam.latitude, cam.longitude);
            dbg << "Camera  " << cam.altitude << "m HAE " << (cam.altitude-el) << "m AGL";
            diagnosticMessages.push_back(dbg.str());
        }
#if 0
        {
            addRenderDiagnosticMessage(gldbgGenerateReport());
        }
#endif
        GLText2 *text = GLText2_intern(TextFormatParams(14));
        if (text) {
            TextFormat2 &fmt = text->getTextFormat();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glTranslatef(16, renderPasses[0].top - 64 - fmt.getCharHeight(), 0);

            text->draw("Renderer Diagnostics", 1, 0, 1, 1);
            

            for (auto it = diagnosticMessages.begin(); it != diagnosticMessages.end(); it++) {
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glTranslatef(0, -fmt.getCharHeight() + 4, 0);
                text->draw((*it).c_str(), 1, 0, 1, 1);
            }
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
        }
        diagnosticMessages.clear();
    }
}
void GLGlobe::release() NOTHROWS
{
    GLGlobeBase::release();
    surfaceRenderer->release();
}

void GLGlobe::prepareScene() NOTHROWS
{
    GLGlobeBase::prepareScene();

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();
    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    fixedPipe->glLoadIdentity();
}
void GLGlobe::computeBounds() NOTHROWS
{
    // determine and assign camera AGL
    GeoPoint2 camlla;
    renderPasses[0u].scene.projection->inverse(&camlla, renderPasses[0u].scene.camera.location);
    double localel = NAN;
    if(getTerrainMeshElevation(&localel, camlla.latitude, camlla.longitude) == TE_Ok && !TE_ISNAN(localel))
        renderPasses[0u].scene.camera.agl = std::max(0.10, fabs(camlla.altitude-localel));
    computeSurfaceBounds(*this, offscreen.visibleTiles.value);
}

int GLGlobe::getTerrainVersion() const NOTHROWS
{
    do {
        ReadLock lock(this->offscreenMutex);
        TE_CHECKBREAK_CODE(lock.status);
        return this->offscreen.lastVersion.terrain;
    } while(false);

    return 0;
}

TAKErr GLGlobe::visitTerrainTiles(TAKErr(*visitor)(void *opaque, const std::shared_ptr<const TerrainTile> &tile) NOTHROWS, void *opaque) NOTHROWS
{
    TAKErr code(TE_Ok);
    ReadLock lock(this->offscreenMutex);
    TE_CHECKRETURN_CODE(lock.status);

    for (auto tile = offscreen.terrainTiles2->cbegin(); tile != offscreen.terrainTiles2->cend(); tile++) {
        code = visitor(opaque, *tile);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
GLGlobeSurfaceRenderer& GLGlobe::getSurfaceRenderer() const NOTHROWS
{
    return *surfaceRenderer;
}
TAKErr GLGlobe::getSurfaceBounds(TAK::Engine::Port::Collection<TAK::Engine::Feature::Envelope2> &value) const NOTHROWS
{
    if(!context.isRenderThread())
        return TE_IllegalState;
    TAKErr code(TE_Ok);
    for(auto mbb : offscreen.visibleTiles.value) {
        code = value.add(mbb.tile->aabb_wgs84);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
bool GLGlobe::isAtmosphereEnabled() const NOTHROWS
{
    return atmosphere.enabled;
}
void GLGlobe::setAtmosphereEnabled(const bool enabled) NOTHROWS
{
    atmosphere.enabled = enabled;
}
void GLGlobe::drawRenderables() NOTHROWS
{
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

    this->numRenderPasses = 0u;

    GLint currentFbo = GL_NONE;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &currentFbo);

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();
    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_TEXTURE);
    fixedPipe->glLoadIdentity();

    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    fixedPipe->glOrthof(static_cast<float>(renderPasses[0].left), static_cast<float>(renderPasses[0].right), static_cast<float>(renderPasses[0].bottom), static_cast<float>(renderPasses[0].top), renderPasses[0].near, renderPasses[0].far);

    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    fixedPipe->glLoadIdentity();

    // last render pass is always remainder
    {
        WriteLock wlock(renderPasses0Mutex);

        this->renderPasses[this->numRenderPasses].renderPass =
                GLGlobe::Sprites | GLGlobe::Scenes | GLGlobe::XRay;
        this->renderPasses[this->numRenderPasses].texture = 0;
        this->renderPasses[this->numRenderPasses].basemap = false;
        this->renderPasses[this->numRenderPasses].debugDrawBounds = this->debugDrawBounds;
        this->renderPasses[this->numRenderPasses].viewport.x = static_cast<float>(renderPasses[0].left);
        this->renderPasses[this->numRenderPasses].viewport.y = static_cast<float>(renderPasses[0].bottom);
        this->renderPasses[this->numRenderPasses].viewport.width = static_cast<float>(renderPasses[0].right - renderPasses[0].left);
        this->renderPasses[this->numRenderPasses].viewport.height = static_cast<float>(renderPasses[0].top - renderPasses[0].bottom);
        this->renderPasses[this->numRenderPasses].renderTiles.value = offscreen.visibleTiles.value.empty() ? nullptr : &offscreen.visibleTiles.value.at(0);
        this->renderPasses[this->numRenderPasses].renderTiles.count = offscreen.visibleTiles.value.size();
        this->numRenderPasses++;
    }
    this->renderPass = this->renderPasses;

    if(isDrawModeEnabled(TEDM_Points)) {
        if(!offscreen.debugFrustum.enabled && !offscreen.debugFrustum.capture)
            offscreen.debugFrustum.capture = true;
    } else {
        offscreen.debugFrustum.enabled = false;
    }


    if (offscreen.debugFrustum.capture) {
        // record the current viewing frustum
        Matrix2 m;
        if (renderPasses[0].scene.camera.mode == MapCamera2::Scale) {
            m.set(renderPasses[0].scene.inverseTransform);
        } else {
            m.set(renderPasses[0].scene.camera.projection);
            m.concatenate(renderPasses[0].scene.camera.modelView);
            m.createInverse(&m);
        }

        const double cnear = renderPasses[0].scene.camera.near;
        const double cfar = renderPasses[0].scene.camera.far;

        m.transform(&offscreen.debugFrustum.frustum[0], Point2<double>(renderPasses[0].left, renderPasses[0].bottom, cnear));
        m.transform(&offscreen.debugFrustum.frustum[1], Point2<double>(renderPasses[0].right, renderPasses[0].bottom, cnear));
        m.transform(&offscreen.debugFrustum.frustum[2], Point2<double>(renderPasses[0].right, renderPasses[0].top, cnear));
        m.transform(&offscreen.debugFrustum.frustum[3], Point2<double>(renderPasses[0].left, renderPasses[0].top, cnear));
        m.transform(&offscreen.debugFrustum.frustum[4], Point2<double>(renderPasses[0].left, renderPasses[0].bottom, cfar));
        m.transform(&offscreen.debugFrustum.frustum[5], Point2<double>(renderPasses[0].right, renderPasses[0].bottom, cfar));
        m.transform(&offscreen.debugFrustum.frustum[6], Point2<double>(renderPasses[0].right, renderPasses[0].top, cfar));
        m.transform(&offscreen.debugFrustum.frustum[7], Point2<double>(renderPasses[0].left, renderPasses[0].top, cfar));

        offscreen.debugFrustum.enabled = true;
        offscreen.debugFrustum.capture = false;
    }

    if (renderPasses[0].drawTilt != 0.0) {
        glDepthMask(GL_FALSE);
        glDisable(GL_DEPTH_TEST);
    } else {
        glDepthFunc(GL_ALWAYS);
    }

    inRenderPump = true;

    // update the surface
    DebugTimer sru_timer("Surface Update", *this, diagnosticMessagesEnabled);
    surfaceRenderer->update(5LL);
    sru_timer.stop();

    // record depth state before rendering skybox
    GLint depthFunc;
    glGetIntegerv(GL_DEPTH_FUNC, &depthFunc);
    GLboolean depthMask;
    glGetBooleanv(GL_DEPTH_WRITEMASK, &depthMask);
    GLboolean depthEnabled = glIsEnabled(GL_DEPTH_TEST);

    // reset FBO to display
    glBindFramebuffer(GL_FRAMEBUFFER, currentFbo);

    // XXX - ideally this gets done after surface render, with application of
    //       depth test to only fill that portion above the terrain

    glViewport(static_cast<GLint>(renderPasses[0u].viewport.x), static_cast<GLint>(renderPasses[0u].viewport.y), static_cast<GLsizei>(renderPasses[0u].viewport.width), static_cast<GLsizei>(renderPasses[0u].viewport.height));

    // render atmosphere
    if(atmosphere.enabled && (getColor(TEDM_Triangles)&0xFF000000u) == 0xFF000000) {
        // fill without writing to the depth buffer
        glDepthFunc(GL_ALWAYS);
        glDepthMask(GL_FALSE);

        atmosphere.renderer.draw(*this);
    }

    // render the globe surface
    {
        glDepthMask(GL_TRUE);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);


        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
        GLES20FixedPipeline::getInstance()->glOrthof(static_cast<float>(renderPasses[0u].left), static_cast<float>(renderPasses[0u].right), static_cast<float>(renderPasses[0u].bottom), static_cast<float>(renderPasses[0u].top), renderPasses[0u].near, renderPasses[0u].far);

        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);

        {
            DebugTimer terrainDepth_timer("Render Terrain", *this, diagnosticMessagesEnabled);
            // XXX - implement points drawing
            if(offscreen.debugFrustum.enabled) {
                drawTerrainMeshes();
            }
            else {
            if (isDrawModeEnabled(TEDM_Points)) {
                // TODO
            }
            if (isDrawModeEnabled(TEDM_Triangles)) {
                surfaceRenderer->draw();
                // XXX - quickfix for supporting subterranean objects
                if((getColor(TEDM_Triangles)&0xFF000000) != 0xFF000000)
                    glClear(GL_DEPTH_BUFFER_BIT);
            }
            if (isDrawModeEnabled(TEDM_Lines))
                drawTerrainMeshes();
            }
            terrainDepth_timer.stop();
        }
    }

    if(renderPasses[0].drawTilt == 0.0) {
        // restore depth state
        glDepthFunc(depthFunc);
        glDepthMask(depthMask);
        if (depthEnabled)
            glEnable(GL_DEPTH_TEST);
        else
            glDisable(GL_DEPTH_TEST);
    }

#if 1
    DebugTimer onscreenPasses_timer("On Screen Render Passes", *this, diagnosticMessagesEnabled);
    // execute all on screen passes
    for (std::size_t i = this->numRenderPasses; i > 0u; i--) {
        if (renderPasses[i - 1u].texture)
            continue;
        this->multiPartPass = false;
        this->drawRenderables(this->renderPasses[i - 1u]);
    }
    onscreenPasses_timer.stop();

    DebugTimer labels_timer("Labels", *this, diagnosticMessagesEnabled);
    if (getLabelManager())
        getLabelManager()->draw(*this, RenderPass::Sprites);
    labels_timer.stop();

    DebugTimer uipass_timer("UI Pass", *this, diagnosticMessagesEnabled);
    // execute UI pass
    {
        // clear the depth buffer
        glClear(GL_DEPTH_BUFFER_BIT);

        State uipass(this->renderPasses[0u]);
        uipass.basemap = false;
        uipass.texture = GL_NONE;
        uipass.renderPass = RenderPass::UserInterface;
        this->multiPartPass = false;
        this->drawRenderables(uipass);
    }
    uipass_timer.stop();
#endif
    inRenderPump = false;
    this->renderPass = this->renderPasses;
}
void GLGlobe::drawRenderables(const GLGlobe::State &renderState) NOTHROWS
{
    // load the render state
    this->renderPass = &renderState;
    this->idlHelper.update(*this);

    GLGlobeBase::drawRenderables(renderState);

    // restore the view state
    this->renderPass = renderPasses;
    this->idlHelper.update(*this);
}
void GLGlobe::drawTerrainTiles(const GLTexture2 &tex, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight) NOTHROWS
{
    TerrainTileShaders *shaders = 
        debugDrawDepth ?
            ((this->renderPasses[0].drawSrid == 4978) ? &offscreen.ecef.depth : &offscreen.planar.depth) :
            ((this->renderPasses[0].drawSrid == 4978) ? &offscreen.ecef.color : &offscreen.planar.color);

    if (debugDrawDepth) {
        GLTerrainTile_drawTerrainTiles(this->renderPasses, this->numRenderPasses, &offscreen.visibleTiles.value.at(0), offscreen.visibleTiles.value.size(), *shaders, tex, (float)elevationScaleFactor, 1.f, 1.f, 1.f, 1.f);
    } else {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GLTerrainTile_drawTerrainTiles(this->renderPasses, this->numRenderPasses, &offscreen.visibleTiles.value.at(0), offscreen.visibleTiles.value.size(), *shaders, tex, (float)elevationScaleFactor, 1.f, 1.f, 1.f, (float)terrainBlendFactor);
        glDisable(GL_BLEND);
    }
}
void GLGlobe::drawTerrainMeshes() NOTHROWS
{
    // XXX - draw AABBs
    if (offscreen.debugFrustum.enabled) {
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glEnableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);

        float aabb[3 * 8];
        uint8_t aabb_idx[24u] =
        {
            // bottom face
            0, 1, 1, 2, 2, 3, 3, 0,
            // top face
            4, 5, 5, 6, 6, 7, 7, 4,
            // front->back edges
            0, 4, 1, 5, 2, 6, 3, 7,
        };

        for (std::size_t i = 0u; i < 8u; i++) {
            Point2<double> xyz;
            renderPasses[0].scene.forwardTransform.transform(&xyz, offscreen.debugFrustum.frustum[i]);
            aabb[i*3] = (float)xyz.x; aabb[i*3+1] = (float)xyz.y; aabb[i*3+2] = (float)xyz.z; 
        }

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glLineWidth(8.0f);

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glColor4f(1.f, 0.f, 0.f, 1.f);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glVertexPointer(3, GL_FLOAT, 0, aabb);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glDrawElements(GL_LINES, 24, GL_UNSIGNED_BYTE, aabb_idx);
        
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glLineWidth(1.0f);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glColor4f(1.f, 1.f, 1.f, 1.f);
        std::size_t idx = 0u;
        for (auto& t : offscreen.visibleTiles.value) {
            float *color = meshColors[idx%13u];
            idx++;
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glColor4f(color[0], color[1], color[2], color[3]);

            // compute AABB
            TAK::Engine::Feature::Envelope2 aabbWCS(t.tile->aabb_wgs84);
            TAK::Engine::Feature::GeometryTransformer_transform(&aabbWCS, aabbWCS, 4326, renderPasses[0].drawSrid);

            Point2<double> xyz;
            std::size_t i = 0u;

            xyz.x = aabbWCS.minX; xyz.y = aabbWCS.minY; xyz.z = aabbWCS.minZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            xyz.x = aabbWCS.maxX; xyz.y = aabbWCS.minY; xyz.z = aabbWCS.minZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            xyz.x = aabbWCS.maxX; xyz.y = aabbWCS.maxY; xyz.z = aabbWCS.minZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            xyz.x = aabbWCS.minX; xyz.y = aabbWCS.maxY; xyz.z = aabbWCS.minZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            xyz.x = aabbWCS.minX; xyz.y = aabbWCS.minY; xyz.z = aabbWCS.maxZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            xyz.x = aabbWCS.maxX; xyz.y = aabbWCS.minY; xyz.z = aabbWCS.maxZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            xyz.x = aabbWCS.maxX; xyz.y = aabbWCS.maxY; xyz.z = aabbWCS.maxZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            xyz.x = aabbWCS.minX; xyz.y = aabbWCS.maxY; xyz.z = aabbWCS.maxZ;
            renderPasses[0].scene.forwardTransform.transform(&xyz, xyz);
            aabb[i++] = (float)xyz.x; aabb[i++] = (float)xyz.y; aabb[i++] = (float)xyz.z; 

            atakmap::renderer::GLES20FixedPipeline::getInstance()->glVertexPointer(3, GL_FLOAT, 0, aabb);
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glDrawElements(GL_LINES, 24, GL_UNSIGNED_BYTE, aabb_idx);
        }
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glDisableClientState(atakmap::renderer::GLES20FixedPipeline::CS_GL_VERTEX_ARRAY);
    }

    unsigned int color = getColor(TEDM_Lines);
    float r = (float)((color>>16u)&0xFFu) / 255.f;
    float g = (float)((color>>8u)&0xFFu) / 255.f;
    float b = (float)(color&0xFFu) / 255.f;
    float a = (float)((color>>24u)&0xFFu) / 255.f;

    ::drawTerrainMeshes(this->renderPasses[0], (float)this->elevationScaleFactor, offscreen.visibleTiles.value, offscreen.ecef.pick, offscreen.planar.pick, *offscreen.whitePixel, r, g, b, a);
}

bool GLGlobe::animate() NOTHROWS {
    const std::size_t terrain_read_idx = offscreen.tileCullFboReadIdx%2u;
    const std::size_t terrain_write_idx = (offscreen.tileCullFboReadIdx+1u)%2u;

    bool retval = GLGlobeBase::animate();
    // adjust near based on cam AGL
    MapSceneModel2 sm;
    if(retval) {
        // the animation was updated, pull `nearMeters` off of new scene
        sm.set(displayDpi,
               (std::size_t)view.getWidth(),
               (std::size_t)view.getHeight(),
               renderPasses[0].drawSrid,
               animation.current.point,
               animation.current.focusx,
               animation.current.focusy,
               animation.current.rotation,
               animation.current.tilt,
               AtakMapView_getMapResolution(displayDpi, animation.current.mapScale),
               NAN,
               NAN,
               cammode);
    } else {
        // use previously computed `nearMeters`
        sm = offscreen.computeScene;
    }

    GeoPoint2 camlla;
    sm.projection->inverse(&camlla, sm.camera.location);

    double localEl;
    if(getTerrainMeshElevation(&localEl, camlla.latitude, camlla.longitude) != TE_Ok || TE_ISNAN(localEl))
        localEl = 0.0;

    // verify AGL exceeds `nearMeters`
    {
        const double dist = std::max(fabs(camlla.altitude-localEl), 0.0000001);
        if(dist < sm.camera.nearMeters) {
            animation.current.clip.near = dist*0.2;
            animation.current.clip.far = NAN;
            animation.current.clip.override = true;
        } else {
            animation.current.clip.near = NAN;
            animation.current.clip.far = NAN;
            animation.current.clip.override = false;
        }
    }

    // the animation is updated or the computed `nearMeters` value has changed; update `offscreen.computeScene` and signal that
    if(retval) {
        offscreen.computeScene.set(
            displayDpi,
            (std::size_t)view.getWidth(),
            (std::size_t)view.getHeight(),
            renderPasses[0].drawSrid,
            animation.current.point,
            animation.current.focusx,
            animation.current.focusy,
            animation.current.rotation,
            animation.current.tilt,
            AtakMapView_getMapResolution(displayDpi, animation.current.mapScale),
            animation.current.clip.near,
            animation.current.clip.far,
            cammode
        );
        GLGlobeBase_glScene(offscreen.computeScene);

        offscreen.computeScene.camera.agl = std::max(0.10, fabs(camlla.altitude-localEl));

        // scene updated, mark dirty
        offscreen.computeContext[terrain_write_idx].processed = false;
    }

    {
        if (!this->offscreen.whitePixel)
            this->initOffscreenShaders();
    }

    // update terrain

    bool terrainUpdate = false;
    // update if terrain version has changed
    terrainUpdate |= (offscreen.lastVersion.terrain != terrain->getTerrainVersion()) || (offscreen.lastVersion.scene != sceneModelVersion);
    if (!offscreen.computeContext[terrain_write_idx].terrainTiles.empty()) {
        // check for update suspend
        terrainUpdate &= !suspendMeshFetch;
        // pause updates while debugging frustum
        terrainUpdate &= !offscreen.debugFrustum.enabled;
    }

    DebugTimer te_timer("Terrain Update", *this, diagnosticMessagesEnabled);
    if (terrainUpdate) {
        std::list<std::shared_ptr<const TerrainTile>> terrainTiles;
        STLListAdapter<std::shared_ptr<const TerrainTile>> tta(terrainTiles);
        const int terrainTilesVersion = terrain->getTerrainVersion();

        // lock terrain using target of animation
        MapSceneModel2 terrainScene(
            offscreen.computeScene.displayDpi,
            offscreen.computeScene.width, offscreen.computeScene.height,
            state.srid,
            animation.target.point,
            animation.target.focusx, animation.target.focusy,
            animation.target.rotation,
            animation.target.tilt,
            atakmap::core::AtakMapView_getMapResolution(offscreen.computeScene.displayDpi, animation.target.mapScale),
            offscreen.computeScene.camera.mode
        );
        terrain->lock(tta, terrainScene, 4326, this->renderPasses[0u].drawVersion, true);

        std::unordered_map<const TerrainTile *, GLTerrainTile> staleTiles(offscreen.gltiles);
        {
            WriteLock lock(this->offscreenMutex);

            if (!offscreen.computeContext[terrain_write_idx].terrainTiles.empty()) {
                STLVectorAdapter<std::shared_ptr<const TerrainTile>> toUnlock(offscreen.computeContext[terrain_write_idx].terrainTiles);
                this->terrain->unlock(toUnlock);
            }

            this->offscreen.computeContext[terrain_write_idx].terrainTiles.clear();
            this->offscreen.computeContext[terrain_write_idx].terrainTiles.reserve(terrainTiles.size());
            for (const auto tile : terrainTiles) {
                this->offscreen.computeContext[terrain_write_idx].terrainTiles.push_back(tile);
                staleTiles.erase(tile.get());
            }
            if(this->offscreen.computeContext[terrain_read_idx].terrainTiles.empty()) {
                // read buffer is not initialized, fill with content from write buffer
                this->offscreen.computeContext[terrain_read_idx].terrainTiles = offscreen.computeContext[terrain_write_idx].terrainTiles;
            } else {
                // remove entries from the read buffer from the stale list
                for(const auto tile : this->offscreen.computeContext[terrain_read_idx].terrainTiles)
                    staleTiles.erase(tile.get());
            }
            // remove entries from the visible list from the stale list
            for(const auto gltile : this->offscreen.visibleTiles.value)
                staleTiles.erase(gltile.tile.get());
            this->offscreen.terrainTiles2 = &this->offscreen.computeContext[terrain_read_idx].terrainTiles;
            this->offscreen.lastVersion.terrain = terrainTilesVersion;
            this->offscreen.lastVersion.scene = sceneModelVersion;
            this->offscreen.terrainFetch++;
        }

        // evict all stale tiles
        std::vector<GLuint> ids;
        ids.reserve(staleTiles.size()*2u);
        for(auto it = staleTiles.begin(); it != staleTiles.end(); it++) {
            offscreen.gltiles.erase(it->first);
            if(it->second.vbo)
                ids.push_back(it->second.vbo);
            if(it->second.ibo)
                ids.push_back(it->second.ibo);
        }

        if(ids.size())
            glDeleteBuffers((GLsizei)ids.size(), &ids.at(0));

        // terrain updated, mark dirty
        offscreen.computeContext[terrain_write_idx].processed = false;
    }
    te_timer.stop();


    if(diagnosticMessagesEnabled) {
        std::ostringstream strm;
        strm << "Terrain tiles " << offscreen.terrainTiles2->size()
             << " (instances " << TerrainTile::getLiveInstances() << "/" << TerrainTile::getTotalInstances()
             << " allocs " << TerrainTile::getHeapAllocations() << ")";
        addRenderDiagnosticMessage(strm.str().c_str());
    }
    // frustum cull the visible tiles
    if(!offscreen.debugFrustum.enabled) {
        DebugTimer vistiles_timer("Visible Terrain Tile Culling", *this, diagnosticMessagesEnabled);
        const bool wasConfirmed = offscreen.visibleTiles.confirmed;

        // if the visible tile compute pipe is not fully processed, push data through
        if(!offscreen.computeContext[terrain_write_idx].processed || !offscreen.computeContext[terrain_read_idx].processed) {
#ifndef __APPLE__
            cullTerrainTiles_pbo();
#else
            cullTerrainTiles_cpu();
#endif
            offscreen.tileCullFboReadIdx++;
        }

        // post a refresh if the visible tiles are not yet confirmed, or if
        // they were previously unconfirmed and have been confirmed during
        // this pump
        if(!offscreen.visibleTiles.confirmed || (!wasConfirmed && offscreen.visibleTiles.confirmed))
            context.requestRefresh();
        vistiles_timer.stop();
    }

    if (illuminationControl.getEnabled()) {
        illuminationControl.setLocation(renderPasses[0u].drawLat, renderPasses[0u].drawLng);
        illuminationControl.computeIlluminance();
    }

    return retval;
}

TAKErr GLGlobe::getTerrainMeshElevation(double *value, const double latitude, const double longitude_) const NOTHROWS
{
    TAKErr code(TE_InvalidArg);

    *value = NAN;

    // wrap the longitude if necessary
    double longitude = longitude_;
    if(longitude > 180.0)
        longitude -= 360.0;
    else if(longitude < -180.0)
        longitude += 360.0;

    double elevation = NAN;
    {
        ReadLock lock(offscreenMutex);
        TE_CHECKRETURN_CODE(lock.status);

        {
            const double altAboveSurface = 30000.0;
            for (auto tile = this->offscreen.terrainTiles2->cbegin(); tile != this->offscreen.terrainTiles2->cend(); tile++) {
                // AABB/bounds check
                const TAK::Engine::Feature::Envelope2 aabb_wgs84 = (*tile)->aabb_wgs84;
                if (!atakmap::math::Rectangle<double>::contains(aabb_wgs84.minX,
                                                                aabb_wgs84.minY,
                                                                aabb_wgs84.maxX,
                                                                aabb_wgs84.maxY,
                                                                longitude, latitude)) {
                    continue;
                }

                // the tile has no elevation data values...
                if (!(*tile)->hasData) {
                    // if on a border, continue as other border tile may have data, else break
                    if(aabb_wgs84.minX < longitude && aabb_wgs84.maxX > longitude &&
                       aabb_wgs84.minY < latitude && aabb_wgs84.maxY > latitude) {

                        elevation = 0.0;
                        code = TE_Ok;
                        break;
                    } else {
                        continue;
                    }
                }
                if(!(*tile)->heightmap) {
                    // if there's no heightmap, shoot a nadir ray into the
                    // terrain tile mesh and obtain the height at the
                    // intersection
                    Projection2Ptr proj(nullptr, nullptr);
                    if (ProjectionFactory3_create(proj, (*tile)->data.srid) != TE_Ok)
                        continue;

                    Matrix2 invLocalFrame;
                    (*tile)->data.localFrame.createInverse(&invLocalFrame);

                    // obtain the ellipsoid surface point
                    Point2<double> surface;
                    if (proj->forward(&surface, GeoPoint2(latitude, longitude)) != TE_Ok)
                        continue;
                    invLocalFrame.transform(&surface, surface);

                    // obtain the point at altitude
                    Point2<double> above;
                    if (proj->forward(&above, GeoPoint2(latitude, longitude, 30000.0, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
                        continue;
                    invLocalFrame.transform(&above, above);

                    // construct the geometry model and compute the intersection
                    TAK::Engine::Math::Mesh model((*tile)->data.value, nullptr);

                    Point2<double> isect;
                    if (!model.intersect(&isect, Ray2<double>(above, Vector4<double>(surface.x - above.x, surface.y - above.y, surface.z - above.z))))
                        continue;

                    (*tile)->data.localFrame.transform(&isect, isect);
                    GeoPoint2 geoIsect;
                    if (proj->inverse(&geoIsect, isect) != TE_Ok)
                        continue;

                    elevation = geoIsect.altitude;
                    code = TE_Ok;
                } else {
                    // do a heightmap lookup
                    const double postSpaceX = (aabb_wgs84.maxX-aabb_wgs84.minX) / ((*tile)->posts_x-1u);
                    const double postSpaceY = (aabb_wgs84.maxY-aabb_wgs84.minY) / ((*tile)->posts_y-1u);

                    const double postX = (longitude-aabb_wgs84.minX)/postSpaceX;
                    const double postY = (*tile)->invert_y_axis ?
                        (latitude-aabb_wgs84.minY)/postSpaceY :
                        (aabb_wgs84.maxY-latitude)/postSpaceY ;

                    const auto postL = static_cast<std::size_t>(MathUtils_clamp((int)postX, 0, (int)((*tile)->posts_x-1u)));
                    const auto postR = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postX), 0, (int)((*tile)->posts_x-1u)));
                    const auto postT = static_cast<std::size_t>(MathUtils_clamp((int)postY, 0, (int)((*tile)->posts_y-1u)));
                    const auto postB = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postY), 0, (int)((*tile)->posts_y-1u)));

                    TAK::Engine::Math::Point2<double> p;

                    // obtain the four surrounding posts to interpolate from
                    (*tile)->data.value->getPosition(&p, (postT*(*tile)->posts_x)+postL);
                    const double ul = p.z;
                    (*tile)->data.value->getPosition(&p, (postT*(*tile)->posts_x)+postR);
                    const double ur = p.z;
                    (*tile)->data.value->getPosition(&p, (postB*(*tile)->posts_x)+postR);
                    const double lr = p.z;
                    (*tile)->data.value->getPosition(&p, (postB*(*tile)->posts_x)+postL);
                    const double ll = p.z;

                    // interpolate the height
                    p.z = MathUtils_interpolate(ul, ur, lr, ll,
                            MathUtils_clamp(postX-(double)postL, 0.0, 1.0),
                            MathUtils_clamp(postY-(double)postT, 0.0, 1.0));
                    // transform the height back to HAE
                    (*tile)->data.localFrame.transform(&p, p);
                    elevation = p.z;
                    code = TE_Ok;
                }

                break;
            }
        }
    }

    // if lookup failed and lookup off mesh is allowed, query the terrain
    // service
    if (TE_ISNAN(elevation)) {
        if(this->terrain->getElevation(&elevation, latitude, longitude) != TE_Ok)
            elevation = 0.0;
        code = TE_Ok;
    }

    *value = elevation;

    return code;
}
TerrainRenderService &GLGlobe::getTerrainRenderService() const NOTHROWS
{
    return *this->terrain;
}

TAKErr GLGlobe::intersectWithTerrain2(GeoPoint2 *value, const MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS
{
#if 0
    GLGlobe *t = const_cast<GLGlobe *>(this);
    t->debugDrawOffscreen = !t->debugDrawOffscreen;
#endif
    std::shared_ptr<const TerrainTile> ignored;
    return intersectWithTerrainImpl(value, ignored, map_scene, x, y);
}
TAKErr GLGlobe::inverse(MapRenderer::InverseResult *result, GeoPoint2 *value, const MapRenderer::InverseMode mode, const unsigned int hints, const Point2<double> &screen, const MapRenderer::DisplayOrigin origin) NOTHROWS
{
    if(!result)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;
    Point2<double> xyz(screen);
    MapSceneModel2 sm;
    {
        ReadLock rlock(renderPasses0Mutex);
        sm = renderPasses[0].scene;
    }
    if(origin == MapRenderer::UpperLeft)
        xyz.y = (sm.height - xyz.y);
    switch(mode) {
        case InverseMode::Transform :
        {
            Point2<double> proj;
            sm.inverseTransform.transform(&proj, xyz);
            *result = (sm.projection->inverse(value, proj) == TE_Ok) ? MapRenderer::Transformed : MapRenderer::None;
            return TE_Ok;
        }
        case InverseMode::RayCast :
        {
            if (!(hints & MapRenderer::IgnoreTerrainMesh) &&
                intersectWithTerrain2(value, sm, (float) xyz.x, (float) xyz.y) == TE_Ok) {
                *result = MapRenderer::TerrainMesh;
                return TE_Ok;
            }
            if (sm.inverse(value, Point2<float>((float) xyz.x, (float) xyz.y, (float) xyz.z)) ==
                TE_Ok) {
                *result = MapRenderer::GeometryModel;
                return TE_Ok;
            }
            *result = MapRenderer::None;
            return TE_Ok;
        }
        default :
            return TE_InvalidArg;
    }
}
TAKErr intersectWithTerrainTileImpl(GeoPoint2 *value, const TerrainTile &tile, const MapSceneModel2 &scene, const float x, const float y) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!tile.hasData)
        return TE_Done;

    const int sceneSrid = scene.projection->getSpatialReferenceID();
    ElevationChunk::Data node(tile.data);
    if (node.srid != sceneSrid) {
        if (tile.data_proj.value && tile.data_proj.srid == sceneSrid) {
            node = tile.data_proj;
        } else {
            ElevationChunk::Data data_proj;

            MeshPtr transformed(nullptr, nullptr);
            VertexDataLayout srcLayout(node.value->getVertexDataLayout());
            MeshTransformOptions transformedOpts;
            MeshTransformOptions srcOpts;
            srcOpts.layout = VertexDataLayoutPtr(&srcLayout, Memory_leaker_const<VertexDataLayout>);
            srcOpts.srid = node.srid;
            srcOpts.localFrame = Matrix2Ptr(&node.localFrame, Memory_leaker_const<Matrix2>);
            MeshTransformOptions dstOpts;
            dstOpts.srid = sceneSrid;
            code = Mesh_transform(transformed, &transformedOpts, *node.value, srcOpts, dstOpts, nullptr);
            TE_CHECKRETURN_CODE(code);

            data_proj.srid = transformedOpts.srid;
            if(transformedOpts.localFrame.get())
                data_proj.localFrame = *transformedOpts.localFrame;
            data_proj.value = std::move(transformed);
            node = data_proj;

            // XXX - 
            const_cast<TerrainTile &>(tile).data_proj = data_proj;
        }
    }

    TAK::Engine::Math::Mesh mesh(node.value, &node.localFrame, (sceneSrid == 4978));
    return scene.inverse(value, Point2<float>(x, y), mesh);
}

TAKErr GLGlobe::intersectWithTerrainImpl(GeoPoint2 *value, std::shared_ptr<const TerrainTile> &focusTile, const MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS
{
    if(gpuTerrainIntersect) {
        AsyncSurfacePickBundle pick;
        pick.done = false;
        pick.x = x;
        pick.y = y;
        pick.code = TE_Done;
        pick.view = const_cast<GLGlobe*>(this);

        pick.code = renderPasses[0].scene.inverse(value, Point2<float>(x, y));
        if (context.isRenderThread()) {
            const bool current = context.isAttached();
            if (!current)
                context.attach();
            pick.code = glPickTerrainTile(pick.result, const_cast<GLGlobe *>(this), map_scene, x, y);
            if (!current)
                context.detach();
        } else {
            Monitor monitor;
            pick.monitor = &monitor;

            context.queueEvent(GLGlobe::glPickTerrainTile2, std::unique_ptr<void, void(*)(const void *)>(&pick, Memory_leaker_const<void>));

            Monitor::Lock lock(monitor);
            if (!pick.done)
                lock.wait();
        }

        // missed all tiles
        if(pick.code == TE_Done)
            return pick.code;

        // we hit something
        if(pick.code == TE_Ok) {
            // check intersect with first pick tile as that one should have contained x,y
            if (pick.code == TE_Ok && intersectWithTerrainTileImpl(value, *pick.result[0], map_scene, x, y) == TE_Ok) {
                focusTile = pick.result[0];
                return TE_Ok;
            }

            // check neighbors
            std::size_t numNeighbors = 8u;
            while (numNeighbors > 0u && !pick.result[numNeighbors])
                numNeighbors--;

            if(numNeighbors && intersectWithTerrainTiles(value, map_scene, focusTile, pick.result+1u, numNeighbors, x, y) == TE_Ok)
                return TE_Ok;
        }

        // failed to find hit on computed tiles, fall through to legacy
    }

    TAKErr code(TE_Ok);
    ReadLock lock(this->offscreenMutex);
    TE_CHECKRETURN_CODE(lock.status);

    if (this->offscreen.terrainTiles2->empty()) {
        code = map_scene.inverse(value, Point2<float>(x, y));
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    return intersectWithTerrainTiles(value, map_scene, focusTile, &this->offscreen.terrainTiles2->at(0), this->offscreen.terrainTiles2->size(), x, y);
}
void GLGlobe::glPickTerrainTile2(void* opaque) NOTHROWS
{
    auto arg = static_cast<AsyncSurfacePickBundle*>(opaque);
    arg->code = GLGlobe::glPickTerrainTile(arg->result, arg->view, arg->view->renderPasses[0].scene, arg->x, arg->y);

    Monitor::Lock lock(*arg->monitor);
    arg->done = true;
    lock.signal();
}
TAKErr GLGlobe::glPickTerrainTile(std::shared_ptr<const Elevation::TerrainTile> *value, GLGlobe* pview, const TAK::Engine::Core::MapSceneModel2& map_scene, const float x, const float y) NOTHROWS
{
    GLGlobe& view = *pview;
    glViewport(static_cast<GLint>(view.renderPasses[0u].viewport.x), static_cast<GLint>(view.renderPasses[0u].viewport.y), static_cast<GLsizei>(view.renderPasses[0u].viewport.width), static_cast<GLsizei>(view.renderPasses[0u].viewport.height));

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glOrthof(static_cast<float>(view.renderPasses[0u].left), static_cast<float>(view.renderPasses[0u].right), static_cast<float>(view.renderPasses[0u].bottom), static_cast<float>(view.renderPasses[0u].top), view.renderPasses[0u].near, view.renderPasses[0u].far);

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);

    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);

    GLint viewport[4];
    GLint boundFbo;
    glGetIntegerv(GL_VIEWPORT, viewport);
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, (GLint *)&boundFbo);
    GLboolean blend_enabled = glIsEnabled(GL_BLEND);

    // clear to zero
    glClearColor(0.f, 0.f, 0.f, 0.f);

    GLOffscreenFramebuffer fbo = view.offscreen.depthSamplerFbo;
    fbo.bind();

    const unsigned subsamplex = 8;
    const unsigned subsampley = 8;
    glViewport(-(int)(x/subsamplex) + fbo.width/2 - 1, -(int)(y/subsampley) + fbo.height/2 - 1, viewport[2]/subsamplex, viewport[3]/subsampley);

    glEnable(GL_SCISSOR_TEST);
    glScissor(0, 0, fbo.width, fbo.height);

    glDisable(GL_BLEND);

#if 0
                    float r, g, b, a;
                    if (color.id) {
                        r = (((idx+1u) >> 24) & 0xFF) / 255.f;
                        g = (((idx+1u) >> 16) & 0xFF) / 255.f;
                        b = (((idx+1u) >> 8) & 0xFF) / 255.f;
                        a = ((idx+1u) & 0xFF) / 255.f;
                    } else {
                        r = color.r;
                        g = color.g;
                        b = color.b;
                        a = color.a;
                    }
#endif
    auto ctx = GLTerrainTile_begin(view.renderPasses[0u].scene,
                                   (view.renderPasses[0].drawSrid == 4978) ? view.offscreen.ecef.pick : view.offscreen.planar.pick);

    Matrix2 lla2tex(0.0, 0.0, 0.0, 0.5,
                    0.0, 0.0, 0.0, 0.5,
                    0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 1.0);
    GLTerrainTile_bindTexture(ctx, view.offscreen.whitePixel->getTexId(), 1u, 1u);
    for(std::size_t i = 0u; i < view.offscreen.visibleTiles.value.size(); i++) {
        // encode ID as RGBA
        const float r = (((i+1u) >> 24) & 0xFF) / 255.f;
        const float g = (((i+1u) >> 16) & 0xFF) / 255.f;
        const float b = (((i+1u) >> 8) & 0xFF) / 255.f;
        const float a = ((i+1u) & 0xFF) / 255.f;
        // draw the tile
        GLTerrainTile_drawTerrainTiles(ctx, lla2tex, &view.offscreen.visibleTiles.value[i], 1u, r, g, b, a);
    }
    GLTerrainTile_end(ctx);

    // read the pixels
    uint32_t rgba[(SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)];
    memset(rgba, 0, sizeof(uint32_t)*(SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u));
    glReadPixels(0, 0, fbo.width-1u, fbo.height-1u, GL_RGBA, GL_UNSIGNED_BYTE, rgba);

    // swap the center pixel with the first. The first return result is assumed
    // to be the tile contianing the specified x,y; remainder are neighbors
    {
        const uint32_t centerPixel = rgba[((SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)) / 2u];
        const uint32_t firstPixel = rgba[0];
        rgba[0] = centerPixel;
        rgba[((SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)) / 2u] = firstPixel;
    }
    
    // build list of unique IDs
    std::size_t unique = 0u;
    uint32_t pixel[(SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)];
    {
        // seek out the first value ID
        std::size_t i;
        for(i = 0u; i < (fbo.width-1u)*(fbo.height-1u); i++) {
            if(rgba[i]) {
                pixel[unique++] = rgba[0];
                break;
            }
        }
        // populate remaining unique IDs
        for( ; i < (fbo.width-1u)*(fbo.height-1u); i++) {
            bool u = true;
            for(std::size_t j = 0u; j < unique; j++) {
                u &= (pixel[j] != rgba[i]);
                if(!u)
                    break;
            }
            if(u)
               pixel[unique++] = rgba[i];
        }
    }

    // need to flip pixel if GPU endianness != CPU endianness
    for(std::size_t i = 0u; i < unique; i++) {
        const uint8_t b0 = ((pixel[i] >> 24) & 0xFF);
        const uint8_t b1 = ((pixel[i] >> 16) & 0xFF);
        const uint8_t b2 = ((pixel[i] >> 8) & 0xFF);
        const uint8_t b3 = (pixel[i] & 0xFF);

        pixel[i] = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    // restore GL state
    glDisable(GL_SCISSOR_TEST);
    glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

    if (blend_enabled)
        glEnable(GL_BLEND);

    glBindFramebuffer(GL_FRAMEBUFFER, boundFbo);

    // if the click was a miss, return none
    if (!unique)
        return TE_Done;

    // emit the candidate tiles
    std::size_t out = 0u;
    for(std::size_t i = 0u; i < unique; i++) {
        if(pixel[i] && pixel[i] <= view.offscreen.visibleTiles.value.size())
        if(out < 9u)
            value[out++] = view.offscreen.visibleTiles.value[pixel[i]-1u].tile;
    }

    return out ? TE_Ok : TE_Done;
}
void GLGlobe::enableDrawMode(const TAK::Engine::Model::DrawMode mode) NOTHROWS
{
    for(std::size_t i = 0u; i < meshDrawModes.size(); i++) {
        if(meshDrawModes[i].mode == mode) {
            meshDrawModes[i].enabled = true;
            context.requestRefresh();
            break;
        }
    }
}
void GLGlobe::disableDrawMode(const TAK::Engine::Model::DrawMode mode) NOTHROWS
{
    for(std::size_t i = 0u; i < meshDrawModes.size(); i++) {
        if(meshDrawModes[i].mode == mode) {
            meshDrawModes[i].enabled = false;
            context.requestRefresh();
            break;
        }
    }
}
bool GLGlobe::isDrawModeEnabled(const TAK::Engine::Model::DrawMode mode) const NOTHROWS
{
    for(std::size_t i = 0u; i < meshDrawModes.size(); i++) {
        if(meshDrawModes[i].mode == mode)
            return meshDrawModes[i].enabled;
    }
    return false;
}
void GLGlobe::setColor(const TAK::Engine::Model::DrawMode mode, const unsigned int color, const ColorControl::Mode colorMode) NOTHROWS
{
    for(std::size_t i = 0u; i < meshDrawModes.size(); i++) {
        if(meshDrawModes[i].mode == mode) {
            meshDrawModes[i].color.argb = color;
            meshDrawModes[i].color.a = ((color>>24u)&0xFF)/255.f;
            meshDrawModes[i].color.r = ((color>>16u)&0xFF)/255.f;
            meshDrawModes[i].color.g = ((color>>8u)&0xFF)/255.f;
            meshDrawModes[i].color.b = (color&0xFF)/255.f;
            meshDrawModes[i].color.mode = colorMode;
            context.requestRefresh();
            break;
        }
    }
}
unsigned int GLGlobe::getColor(const TAK::Engine::Model::DrawMode mode) const NOTHROWS
{
    for(std::size_t i = 0u; i < meshDrawModes.size(); i++) {
        if(meshDrawModes[i].mode == mode)
            return meshDrawModes[i].color.argb;
    }
    return 0xFFFFFFFFu;
}
ColorControl::Mode GLGlobe::getColorMode(const TAK::Engine::Model::DrawMode mode)
{
    for(std::size_t i = 0u; i < meshDrawModes.size(); i++) {
        if(meshDrawModes[i].mode == mode)
            return meshDrawModes[i].color.mode;
    }
    return ColorControl::Modulate;
}

double GLGlobe::getRecommendedGridSampleDistance() NOTHROWS
{
    return recommendedGridSampleDistance;
}

Controls::IlluminationControlImpl *GLGlobe::getIlluminationControl() const NOTHROWS {
    return const_cast<IlluminationControlImpl *>(&illuminationControl);
}

TAKErr handleCollision(GeoPoint2 *cam, GeoPoint2 *focus, double *tilt, const GLGlobe &view, const MapSceneModel2 &sm,
    const double collideRadius, const MapRenderer::CameraCollision collision) 
{
    const double tilt_ = *tilt;
    double el = 0;
    if(view.getTerrainMeshElevation(&el, cam->latitude, cam->longitude) != TE_Ok || TE_ISNAN(el))
        el = 0.0;
    // if the camera is within the collide radius, adjust
    const bool isCollide = (collideRadius > 0.0 && (cam->altitude - collideRadius) < el);
    if(isCollide && (collision == MapRenderer::Abort)) 
    {
        return TE_Done;
    }
    else if(isCollide && (collision == MapRenderer::AdjustFocus)) 
    {
        // adjust the focus to avoid the collision; the camera<->target view
        // vector direction and magnitude is maintained by translating both the
        // camera and the target away from the terrain.
        Point2<double> vv = Vector2_subtract(sm.camera.target, sm.camera.location);
        vv.x *= sm.displayModel->projectionXToNominalMeters;
        vv.y *= sm.displayModel->projectionYToNominalMeters;
        vv.z *= sm.displayModel->projectionZToNominalMeters;
        const double camtgt = Vector2_length(vv);

        const double camGsdRange = camtgt + focus->altitude;
        const double adj = (el - (cam->altitude - collideRadius));
        focus->altitude += adj;

        // XXX - resolution adjustment here doesn't seem to make a noticeable
        //       difference and is resulting in the camera getting "stuck" when
        //       localel < 0.0. disabling for time being.
#if 0
        if (focus.altitude > 0.0)
            resolution = sm.gsd*(camtgt + fabs(focus.altitude)) / camGsdRange;
        else
            resolution = sm.gsd*camGsdRange / (camtgt + fabs(focus.altitude));
#endif
    }
    else if(isCollide && (collision == MapRenderer::AdjustCamera)) 
    {
        // adjust the camera to avoid the collision; the target point location
        // is maintained and the elevation angle is adjusted to avoid the
        // collision
        Point2<double> vv = Vector2_subtract(sm.camera.target, sm.camera.location);
        vv.x *= sm.displayModel->projectionXToNominalMeters;
        vv.y *= sm.displayModel->projectionYToNominalMeters;
        vv.z *= sm.displayModel->projectionZToNominalMeters;
        const double camtgt = Vector2_length(vv);

        const double adj = (el - (cam->altitude - collideRadius));

        // adjust camera altitude
        cam->altitude += adj;
        // compute new tilt for adjusted camera position
        // Note: resolution remains constant to maintain view vector magnitude
        GeoPoint2 origFocus;
        sm.projection->inverse(&origFocus, sm.camera.target);
        Point2<double> focusup_xyz;
        sm.projection->forward(&focusup_xyz, GeoPoint2(origFocus.latitude, origFocus.longitude, origFocus.altitude + 100.0, origFocus.altitudeRef));
        Point2<double> target_up = Vector2_subtract(focusup_xyz, sm.camera.target);
        target_up.x *= sm.displayModel->projectionXToNominalMeters;
        target_up.y *= sm.displayModel->projectionYToNominalMeters;
        target_up.z *= sm.displayModel->projectionZToNominalMeters;
        Vector2_normalize(&target_up, target_up);
        Point2<double> adjcamloc;
        sm.projection->forward(&adjcamloc, *cam);
        Point2<double> los = Vector2_subtract(adjcamloc, sm.camera.target);
        los.x *= sm.displayModel->projectionXToNominalMeters;
        los.y *= sm.displayModel->projectionYToNominalMeters;
        los.z *= sm.displayModel->projectionZToNominalMeters;
        Vector2_normalize(&los, los);
        // angle is the dot product between the view and up vectors at the target
        const double dot = Vector2_dot(los, target_up);
        *tilt = (acos(dot) / M_PI * 180.0);
        // tilt should never increase. due to precision issues, small tilt is
        // periodically observed to be introduced when collision occurs in
        // nadir.
        if(TE_ISNAN(*tilt) || *tilt > tilt_) 
            *tilt = tilt_;
    }
    return TE_Ok;
}
TAKErr TAK::Engine::Renderer::Core::GLGlobe_lookAt(GLGlobe &view, const GeoPoint2 &focus_, const double resolution_, const double rotation, const double tilt_, const double collideRadius, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS {
    GeoPoint2 focus(focus_);
    if(TE_ISNAN(focus.altitude))
        focus.altitude = 0.0;
    double resolution = resolution_;
    double tilt = tilt_;

    //    sm = new MapSceneModel(getSurface().getDpi(), sm.width, sm.height, sm.mapProjection, new GeoPoint(at.getLatitude(), at.getLongitude(), alt), sm.focusx, sm.focusy, sm.camera.azimuth, sm.camera.elevation+90d, sm.gsd, true);
    //    final double range0 = (sm.gsd/(sm.height/2d)) / Math.tan(Math.toRadians(sm.camera.fov/2d));
    //    final double CT0 = range0 - sm.mapProjection.inverse(sm.camera.target, null).getAltitude();    
    atakmap::math::Point<float> focusxy;
    view.view.getController()->getFocusPoint(&focusxy);
    MapSceneModel2 sm(
            view.view.getDisplayDpi(),
            (std::size_t)view.view.getWidth(),
            (std::size_t)view.view.getHeight(),
            view.view.getProjection(),
            focus,
            focusxy.x, focusxy.y,
            view.view.getMapRotation(),
            view.view.getMapTilt(),
            view.view.getMapResolution(),
            MapCamera2::Perspective);    // obtain local mesh elevation at camera location
    GeoPoint2 cam;
    sm.projection->inverse(&cam, sm.camera.location);

    TAKErr code = handleCollision(&cam, &focus, &tilt, view, sm, collideRadius, collision);
    TE_CHECKRETURN_CODE(code);

    const double mapScale = atakmap::core::AtakMapView_getMapScale(view.view.getDisplayDpi(), resolution);
    view.view.updateView(GeoPoint2(focus), mapScale, rotation, tilt, NAN, NAN, animate);
    return TE_Ok;
}
TAKErr TAK::Engine::Renderer::Core::GLGlobe_lookFrom(GLGlobe &view, const TAK::Engine::Core::GeoPoint2 &from_, const double rotation,
    const double elevation, const double collideRadius, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS 
{
    GeoPoint2 from(from_);
    if(TE_ISNAN(from.altitude)) 
        from.altitude = 0.0;

    double gsd = MapSceneModel2_gsd(from.altitude, HVFOV*2, (std::size_t)view.view.getHeight());

    double tilt = 90 + elevation;

    //    sm = new MapSceneModel(getSurface().getDpi(), sm.width, sm.height, sm.mapProjection, new GeoPoint(at.getLatitude(),
    //    at.getLongitude(), alt), sm.focusx, sm.focusy, sm.camera.azimuth, sm.camera.elevation+90d, sm.gsd, true); final double range0 =
    //    (sm.gsd/(sm.height/2d)) / Math.tan(Math.toRadians(sm.camera.fov/2d)); final double CT0 = range0 -
    //    sm.mapProjection.inverse(sm.camera.target, null).getAltitude();
    atakmap::math::Point<float> focusxy;
    view.view.getController()->getFocusPoint(&focusxy);
    MapSceneModel2 sm(
            view.view.getDisplayDpi(),
            (std::size_t)view.view.getWidth(),
            (std::size_t)view.view.getHeight(),
            view.view.getProjection(),
            from,
            focusxy.x, focusxy.y,
            rotation,
            tilt,
            gsd, 
            NAN,
            NAN,
            MapCamera2::Perspective,
            true);    // obtain local mesh elevation at camera location


    // obtain local mesh elevation at camera location
    GeoPoint2 cam, target;
    sm.projection->inverse(&cam, sm.camera.location);
    sm.projection->inverse(&target, sm.camera.target);
    double temp = tilt;
    TAKErr code = handleCollision(&cam, &from, &temp, view, sm, collideRadius, collision);
    TE_CHECKRETURN_CODE(code);

    GeoPoint2 origFocus;
    sm.projection->inverse(&origFocus, sm.camera.target);
    
    gsd = MapSceneModel2_gsd(cam.altitude, HVFOV * 2, (std::size_t)view.view.getHeight());
    double scale = AtakMapView_getMapScale(view.view.getDisplayDpi(), gsd);

    view.view.updateView(origFocus, scale, rotation, tilt, origFocus.altitude, NAN, animate);
    return TE_Ok;
}

namespace
{
    void glUniformMatrix4(GLint location, const Matrix2 &matrix) NOTHROWS
    {
        double matrixD[16];
        float matrixF[16];
        matrix.get(matrixD, Matrix2::COLUMN_MAJOR);
        for (std::size_t i = 0u; i < 16u; i++)
            matrixF[i] = (float)matrixD[i];
        glUniformMatrix4fv(location, 1, false, matrixF);
    }

    void glUniformMatrix4v(GLint location, const Matrix2 *matrix, const std::size_t count) NOTHROWS
    {
#define MAX_UNIFORM_MATRICES 16u
        double matrixD[MAX_UNIFORM_MATRICES*16];
        float matrixF[MAX_UNIFORM_MATRICES*16];

        const std::size_t limit = std::min(count, (std::size_t)MAX_UNIFORM_MATRICES);
        if(limit < count)
            Logger_log(TELL_Warning, "Max uniform matrices exceeded, %u", (unsigned)count);

        for (std::size_t i = 0u; i < limit; i++)
            matrix[i].get(matrixD+(i*16u), Matrix2::COLUMN_MAJOR);
        for (std::size_t i = 0u; i < (limit*16u); i++)
            matrixF[i] = (float)matrixD[i];

        glUniformMatrix4fv(location, static_cast<GLsizei>(limit), false, matrixF);
    }

    TAKErr intersectWithTerrainTiles(GeoPoint2 *value, const MapSceneModel2 &map_scene, std::shared_ptr<const TerrainTile> &focusTile, const std::shared_ptr<const TerrainTile> *tiles, const std::size_t numTiles, const float x, const float y) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const int sceneSrid = map_scene.projection->getSpatialReferenceID();

        // direction from camera to target
        Point2<double> camdir;
        Vector2_subtract<double>(&camdir, map_scene.camera.target, map_scene.camera.location);

        // scale by nominal display model meters
        camdir.x *= map_scene.displayModel->projectionXToNominalMeters;
        camdir.y *= map_scene.displayModel->projectionYToNominalMeters;
        camdir.z *= map_scene.displayModel->projectionZToNominalMeters;

        // camera location, in nominal display meters
        Point2<double> loc;
        if(map_scene.camera.mode == MapCamera2::Scale) {
            double mag;
            Vector2_length(&mag, camdir);
            camdir.x /= mag;
            camdir.y /= mag;
            camdir.z /= mag;
            mag = std::max(mag, 2000.0);

            // scale the direction vector
            Vector2_multiply(&camdir, camdir, mag * 2.0);

            loc = Point2<double>(map_scene.camera.target);
            // scale by nominal display model meters
            loc.x *= map_scene.displayModel->projectionXToNominalMeters;
            loc.y *= map_scene.displayModel->projectionYToNominalMeters;
            loc.z *= map_scene.displayModel->projectionZToNominalMeters;

            // subtract the scaled camera direction
            Vector2_subtract(&loc, loc, camdir);
        } else {
            loc = Point2<double>(map_scene.camera.location);
            loc.x *= map_scene.displayModel->projectionXToNominalMeters;
            loc.y *= map_scene.displayModel->projectionYToNominalMeters;
            loc.z *= map_scene.displayModel->projectionZToNominalMeters;
        }

        GeoPoint2 candidate;
        double candidateDistSq = NAN;

        // check the previous tile containing the focus point first to obtain an initial candidate
        if (focusTile.get() && intersectsAABB(&candidate, map_scene, focusTile->aabb_wgs84, x, y)) {
            const ElevationChunk::Data& node = focusTile->data;

            TAK::Engine::Math::Mesh mesh(node.value, &node.localFrame);
            if (intersectWithTerrainTileImpl(&candidate, *focusTile, map_scene, x, y) == TE_Ok) {
                Point2<double> proj;
                map_scene.projection->forward(&proj, candidate);
                // convert hit to nominal display model meters
                proj.x *= map_scene.displayModel->projectionXToNominalMeters;
                proj.y *= map_scene.displayModel->projectionYToNominalMeters;
                proj.z *= map_scene.displayModel->projectionZToNominalMeters;

                const double dx = proj.x - loc.x;
                const double dy = proj.y - loc.y;
                const double dz = proj.z - loc.z;
                candidateDistSq = ((dx * dx) + (dy * dy) + (dz * dz));

                *value = candidate;
            }
        }

        // the tile indices to be tested. contains only those tiles that
        // intersect the view frustum. the vector will be sorted by ascending
        // distance from camera
        std::vector<std::size_t> testTilesIndices;
        testTilesIndices.reserve(numTiles);

        // frustum intersect all tiles
        GeoPoint2 focus;
        map_scene.projection->inverse(&focus, map_scene.camera.target);
        GeoPoint2 camera;
        map_scene.projection->inverse(&camera, map_scene.camera.location);
        Matrix2 m;
        m.set(map_scene.camera.projection);
        m.concatenate(map_scene.camera.modelView);
        Frustum2 frustum(m);
        for (std::size_t i = 0u; i < numTiles; i++) {

            // test for ray intersection with bounding sphere
            TAK::Engine::Feature::Envelope2 aabb;
            TAK::Engine::Feature::GeometryTransformer_transform(&aabb, tiles[i]->aabb_wgs84, 4326, sceneSrid);
            aabb.minX *= map_scene.displayModel->projectionXToNominalMeters;
            aabb.minY *= map_scene.displayModel->projectionYToNominalMeters;
            aabb.minZ *= map_scene.displayModel->projectionZToNominalMeters;
            aabb.maxX *= map_scene.displayModel->projectionXToNominalMeters;
            aabb.maxY *= map_scene.displayModel->projectionYToNominalMeters;
            aabb.maxZ *= map_scene.displayModel->projectionZToNominalMeters;

            const Point2<double> centroid
            (
                (aabb.minX+aabb.maxX) / 2.0,
                (aabb.minY+aabb.maxY) / 2.0,
                (aabb.minZ+aabb.maxZ) / 2.0
            );
            const double radius = Vector2_length(
                Point2<double>(
                    (aabb.maxX-centroid.x),
                    (aabb.maxY-centroid.y),
                    (aabb.maxZ-centroid.z)
                )
            );

            Point2<double> dir(x, y, 1.0);
            map_scene.inverseTransform.transform(&dir, dir);
            dir.x -= map_scene.camera.location.x;
            dir.y -= map_scene.camera.location.y;
            dir.z -= map_scene.camera.location.z;

            // pre-normalize
            dir.x *= map_scene.displayModel->projectionXToNominalMeters;
            dir.y *= map_scene.displayModel->projectionYToNominalMeters;
            dir.z *= map_scene.displayModel->projectionZToNominalMeters;
            Vector2_normalize(&dir, dir);

            Point2<double> camloc(map_scene.camera.location);
            camloc.x *= map_scene.displayModel->projectionXToNominalMeters;
            camloc.y *= map_scene.displayModel->projectionYToNominalMeters;
            camloc.z *= map_scene.displayModel->projectionZToNominalMeters;

            Sphere2 bs(centroid, radius);
            bool intersects = false;
            Point2<double> scratch;
            intersects |= bs.intersect(&scratch,
                    Ray2<double>(camloc, Vector4<double>(dir.x, dir.y, dir.z)));

            // shift ray for IDL cross check
            if(map_scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE && (centroid.x*map_scene.camera.location.x) < 0.0) {
                double hemiShift = (map_scene.camera.location.x >= 0.0) ? 360.0 : -360.0;
                // shift bounding sphere into other hemisphere
                Sphere2 shifted_bs(Point2<double>(centroid.x+hemiShift, centroid.y, centroid.z), radius);
                intersects |= shifted_bs.intersect(&scratch,
                        Ray2<double>(camloc,
                                Vector4<double>(dir.x, dir.y, dir.z)));
            }
            if(!intersects)
                continue;

            testTilesIndices.push_back(i);
        }

        // sort by distance
        struct TileDistFn {
            const std::shared_ptr<const TerrainTile> *tiles;
            Point2<double> cam;
            Point2<double> metersScale;

            bool operator ()(const std::size_t a, const std::size_t b) {
                const TerrainTile &tile_a = *tiles[a];
                const Point2<double> centroid_a
                (
                    (tile_a.aabb_wgs84.minX+tile_a.aabb_wgs84.maxX) / 2.0,
                    (tile_a.aabb_wgs84.minY+tile_a.aabb_wgs84.maxY) / 2.0,
                    (tile_a.aabb_wgs84.minZ+tile_a.aabb_wgs84.maxZ) / 2.0
                );

                const double dist2_a =
                        Vector2_length(
                            Point2<double>(
                                    (centroid_a.x-cam.x)*metersScale.x,
                                    (centroid_a.x-cam.y)*metersScale.y,
                                    (centroid_a.x-cam.z)*metersScale.z
                            )
                        );

                const TerrainTile &tile_b = *tiles[b];
                const Point2<double> centroid_b
                (
                    (tile_b.aabb_wgs84.minX+tile_b.aabb_wgs84.maxX) / 2.0,
                    (tile_b.aabb_wgs84.minY+tile_b.aabb_wgs84.maxY) / 2.0,
                    (tile_b.aabb_wgs84.minZ+tile_b.aabb_wgs84.maxZ) / 2.0
                );

                const double dist2_b =
                        Vector2_length(
                            Point2<double>(
                                    (centroid_b.x-cam.x)*metersScale.x,
                                    (centroid_b.y-cam.y)*metersScale.y,
                                    (centroid_b.z-cam.z)*metersScale.z
                            )
                        );

                if(dist2_a < dist2_b)
                    return true;
                else if(dist2_a > dist2_b)
                    return false;
                else
                    return a < b;
            }
        };
        TileDistFn sort;
        sort.tiles = tiles;
        sort.cam = Point2<double>(camera.longitude, camera.latitude, camera.altitude);
        std::sort(testTilesIndices.begin(), testTilesIndices.end(), sort);

        // compare all other tiles with the candidate derived from focus or earth surface
        for (std::size_t i = 0; i < testTilesIndices.size(); i++) {
            const TerrainTile& tile = *tiles[testTilesIndices[i]];
            // skip checking focus twice
            if (focusTile.get() && focusTile.get() == &tile)
                continue;

            // if the tile doesn't have data, skip -- we've already computed surface intersection above
            if (!tile.hasData)
                continue;

            // check isect on AABB
            if (!intersectsAABB(&candidate, map_scene, tile.aabb_wgs84, x, y)) {
                // no AABB isect, continue
                continue;
            } else if (!TE_ISNAN(candidateDistSq)) {
                // if we have a candidate and the AABB intersection is further
                // than the candidate distance, any content intersect is going to
                // be further
                Point2<double> proj;
                map_scene.projection->forward(&proj, candidate);
                // convert hit to nominal display model meters
                proj.x *= map_scene.displayModel->projectionXToNominalMeters;
                proj.y *= map_scene.displayModel->projectionYToNominalMeters;
                proj.z *= map_scene.displayModel->projectionZToNominalMeters;

                const double dx = proj.x - loc.x;
                const double dy = proj.y - loc.y;
                const double dz = proj.z - loc.z;
                const double distSq = ((dx * dx) + (dy * dy) + (dz * dz));

                if (distSq > candidateDistSq)
                    continue;
            }

            // do the raycast into the mesh
            code = intersectWithTerrainTileImpl(&candidate, tile, map_scene, x, y);
            if (code != TE_Ok)
                continue;

            Point2<double> proj;
            map_scene.projection->forward(&proj, candidate);
            // convert hit to nominal display model meters
            proj.x *= map_scene.displayModel->projectionXToNominalMeters;
            proj.y *= map_scene.displayModel->projectionYToNominalMeters;
            proj.z *= map_scene.displayModel->projectionZToNominalMeters;

            const double dx = proj.x - loc.x;
            const double dy = proj.y - loc.y;
            const double dz = proj.z - loc.z;
            const double distSq = ((dx * dx) + (dy * dy) + (dz * dz));
            if (TE_ISNAN(candidateDistSq) || distSq < candidateDistSq) {
                *value = candidate;
                candidateDistSq = distSq;
                focusTile = tiles[i];
            }
        }

        if (TE_ISNAN(candidateDistSq)) {
            map_scene.inverse(value, Point2<float>(x, y));
            // no focus found
            focusTile.reset();
        }

        return TE_ISNAN(candidateDistSq) ? TE_Err : TE_Ok;
    }

    void drawTerrainMeshes(const GLGlobe::State &view, const float elevationScaleFactor, const std::vector<GLTerrainTile> &terrainTiles, const TerrainTileShaders &ecef, const TerrainTileShaders &planar, const GLTexture2 &whitePixel, const float clr_r, const float clr_g, const float clr_b, const float clr_a) NOTHROWS
    {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(1.f);

        // select shader
        const TerrainTileShaders *shaders;
        Matrix2 localFrame[TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS];
        std::size_t numLocalFrames = 0u;
        if(view.scene.displayModel->earth->getGeomClass() == TAK::Engine::Math::GeometryModel2::ELLIPSOID) {
            shaders = &ecef;
            if(view.drawMapResolution <= shaders->hi_threshold) {
                Matrix2 tx;
                tx.setToTranslate(view.drawLng, view.drawLat, 0.0);
                lla2ecef_transform(&localFrame[0], *view.scene.projection, &tx);
                localFrame[0].translate(-view.drawLng, -view.drawLat, 0.0);
                numLocalFrames++;
            } else if (view.drawMapResolution <= shaders->md_threshold) {
                const auto &ellipsoid = static_cast<const TAK::Engine::Math::Ellipsoid2 &>(*view.scene.displayModel->earth);

                const double a = ellipsoid.radiusX;
                const double b = ellipsoid.radiusZ;

                const double cosLat0d = cos(view.drawLat*M_PI/180.0);
                const double cosLng0d = cos(view.drawLng*M_PI/180.0);
                const double sinLat0d = sin(view.drawLat*M_PI/180.0);
                const double sinLng0d = sin(view.drawLng*M_PI/180.0);

                const double a2_b2 = (a*a)/(b*b);
                const double b2_a2 = (b*b)/(a*a);
                const double cden = sqrt((cosLat0d*cosLat0d) + (b2_a2 * (sinLat0d*sinLat0d)));
                const double lden = sqrt((a2_b2 * (cosLat0d*cosLat0d)) + (sinLat0d*sinLat0d));

                // scale by ellipsoid radii
                localFrame[2].setToScale(a/cden, a/cden, b/lden);
                // calculate coefficients for lat/lon => ECEF conversion, using small angle approximation
                localFrame[2].concatenate(Matrix2(
                        -cosLat0d*sinLng0d, -cosLng0d*sinLat0d, sinLat0d*sinLng0d, cosLat0d*cosLng0d,
                        cosLat0d*cosLng0d, -sinLat0d*sinLng0d, -sinLat0d*cosLng0d, cosLat0d*sinLng0d,
                        0, cosLat0d, 0, sinLat0d,
                        0, 0, 0, 1
                ));
                // convert degrees to radians
                localFrame[2].scale(M_PI/180.0, M_PI/180.0, M_PI/180.0*M_PI/180.0);
                numLocalFrames++;

                // degrees are relative to focus
                localFrame[1].setToTranslate(-view.drawLng, -view.drawLat, 0);
                numLocalFrames++;

                // degrees are relative to focus
                localFrame[0].setToIdentity();
                numLocalFrames++;
            }
        } else {
            shaders = &planar;
        }

        const TerrainTileShader &shader = (view.drawMapResolution <= shaders->hi_threshold) ?
                                        shaders->hi :
                                        (view.drawMapResolution <= shaders->md_threshold) ?
                                        shaders->md : shaders->lo;

        glUseProgram(shader.base.handle);
        int activeTexture[1];
        glGetIntegerv(GL_ACTIVE_TEXTURE, activeTexture);
        glBindTexture(GL_TEXTURE_2D, whitePixel.getTexId());

        glUniform1i(shader.base.uTexture, activeTexture[0] - GL_TEXTURE0);
        glUniform4f(shader.base.uColor, 1.0, 1.0, 1.0, 1.0);
        glUniform1f(shader.uTexWidth, static_cast<float>(whitePixel.getTexWidth()));
        glUniform1f(shader.uTexHeight, static_cast<float>(whitePixel.getTexHeight()));

        // XXX - terrain enabled
        glUniform1f(shader.uElevationScale, elevationScaleFactor);

        // first pass
        {
            // construct the MVP matrix
            Matrix2 mvp;
            // projectino
            float matrixF[16u];
            atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, matrixF);
            for(std::size_t i = 0u; i < 16u; i++)
                mvp.set(i%4, i/4, matrixF[i]);
            // model-view
            mvp.concatenate(view.scene.forwardTransform);

            drawTerrainMeshesImpl(view, shader, mvp, localFrame, numLocalFrames, terrainTiles, clr_r, clr_g, clr_b, clr_a);
        }

        if(view.crossesIDL &&
            ((view.scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE) ||
             (&shader != &shaders->lo))) {

            GLGlobe::State view2(view);

            if(view2.scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE) {
                // reconstruct the scene model in the secondary hemisphere
                if (view2.drawLng < 0.0)
                    view2.drawLng += 360.0;
                else
                    view2.drawLng -= 360.0;

                // XXX - duplicated code from now inaccessible `validateSceneModel`
                view2.scene.set(
                        view2.scene.displayDpi,
                        view2.scene.width,
                        view2.scene.height,
                        view2.drawSrid,
                        GeoPoint2(view2.drawLat, view2.drawLng, view2.drawAlt, TAK::Engine::Core::AltitudeReference::HAE),
                        view2.scene.focusX,
                        view2.scene.focusY,
                        view2.scene.camera.azimuth,
                        90.0+view2.scene.camera.elevation,
                        view2.scene.gsd,
                        view2.scene.camera.mode);
                GLGlobeBase_glScene(view2.scene);
                {
                    double mx[16];
                    view2.scene.forwardTransform.get(mx, Matrix2::COLUMN_MAJOR);
                    for(std::size_t i = 0u; i < 16u; i++)
                        view2.sceneModelForwardMatrix[i] = (float)mx[i];
                }
            } else if(&shader == &shaders->hi) {
                // reconstruct the scene model in the secondary hemisphere
                if (view2.drawLng < 0.0)
                    localFrame[0].translate(-360.0, 0.0, 0.0);
                else
                    localFrame[0].translate(360.0, 0.0, 0.0);
            } else if(&shader == &shaders->md) {
                // reconstruct the scene model in the secondary hemisphere
                if (view2.drawLng < 0.0)
                    localFrame[1].translate(-360.0, 0.0, 0.0);
                else
                    localFrame[1].translate(360.0, 0.0, 0.0);
            }

            // construct the MVP matrix
            Matrix2 mvp;
            // projection
            float matrixF[16u];
            atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, matrixF);
            for(std::size_t i = 0u; i < 16u; i++)
                mvp.set(i%4, i/4, matrixF[i]);
            // model-view
            mvp.concatenate(view2.scene.forwardTransform);

            drawTerrainMeshesImpl(view2, shader, mvp, localFrame, numLocalFrames, terrainTiles, clr_r, clr_g, clr_b, clr_a);
        }

        glUseProgram(0);
    }
    void drawTerrainMeshesImpl(const GLGlobe::State &renderPass, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const std::vector<GLTerrainTile> &terrainTiles, const float r, const float g, const float b, const float a) NOTHROWS
    {
        glUniform4f(shader.base.uColor, r, g, b, a);

        glEnableVertexAttribArray(shader.base.aVertexCoords);

        // draw terrain tiles
        std::size_t idx = 0u;
        for (auto tile = terrainTiles.begin(); tile != terrainTiles.end(); tile++) {
#if 0
            float *color = meshColors[idx%13u];
            drawTerrainMeshImpl(renderPass, shader, mvp, local, numLocal, *tile, color[0], color[1], color[2], color[3]);
#else
            drawTerrainMeshImpl(renderPass, shader, mvp, local, numLocal, *tile, r, g, b, a);
#endif
            idx++;
        }

        glDisableVertexAttribArray(shader.base.aVertexCoords);
    }

    void drawTerrainMeshImpl(const GLGlobe::State &state, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTerrainTile &gltile, const float r, const float g, const float b, const float a) NOTHROWS
    {
        if(!gltile.tile)
            return;

        TAKErr code(TE_Ok);
        const TerrainTile &tile = *gltile.tile;

        int drawMode;
        switch (tile.data.value->getDrawMode()) {
            case TEDM_Triangles:
                drawMode = GL_TRIANGLES;
                break;
            case TEDM_TriangleStrip:
                drawMode = GL_TRIANGLE_STRIP;
                break;
            default:
                Logger_log(TELL_Warning, "GLGlobe: Undefined terrain model draw mode");
                return;
        }

        // set the local frame
        Matrix2 matrix;

        if (atakmap::math::Rectangle<double>::contains(tile.aabb_wgs84.minX, tile.aabb_wgs84.minY, tile.aabb_wgs84.maxX, tile.aabb_wgs84.maxY, state.drawLat, state.drawLng)) {
            matrix.setToIdentity();
        }
        matrix.set(mvp);
        if(shader.uLocalTransform < 0) {
            for(std::size_t i = numLocal; i >= 1; i--)
                matrix.concatenate(local[i-1u]);
            matrix.concatenate(tile.data.localFrame);
        } else {
            Matrix2 mx[TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS];
            for(std::size_t i = numLocal; i >= 1; i--)
                mx[i-1u].set(local[i-1u]);
            mx[0].concatenate(tile.data.localFrame);
            glUniformMatrix4v(shader.uLocalTransform, mx, numLocal ? numLocal : 1u);
        }

        glUniformMatrix4(shader.base.uMVP, matrix);

        Matrix2 lla2tex(0.0, 0.0, 0.0, 0.5,
                0.0, 0.0, 0.0, 0.5,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0);

        glUniformMatrix4(shader.uModelViewOffscreen, lla2tex);

        glUniform4f(shader.base.uColor, r, g, b, a);

#if 0
        if (depthEnabled) {
#else
        if(true) {
#endif
            glDepthFunc(GL_LEQUAL);
        }

        // render offscreen texture
        const VertexDataLayout &layout = tile.data.value->getVertexDataLayout();

        if(gltile.vbo) {
            // VBO
            glBindBuffer(GL_ARRAY_BUFFER, gltile.vbo);
            glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), (void *)layout.position.offset);
            glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        } else {
            const void *vertexCoords;
            code = tile.data.value->getVertices(&vertexCoords, TEVA_Position);
            if (code != TE_Ok) {
                Logger_log(TELL_Error, "GLGlobe::drawTerrainTile : failed to obtain vertex coords, code=%d", code);
                return;
            }

            glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), static_cast<const uint8_t *>(vertexCoords) + layout.position.offset);
        }

        std::size_t numIndicesWireframe = 0u;
        array_ptr<uint8_t> wireframeIndices;
        int glIndexType = GL_NONE;

        if (tile.data.value->isIndexed()) {
            const GLuint numMeshIndices = DEBUG_DRAW_MESH_SKIRT ? static_cast<GLuint>(tile.data.value->getNumIndices()) : static_cast<GLuint>(tile.skirtIndexOffset);
            GLWireframe_getNumWireframeElements(&numIndicesWireframe, drawMode, numMeshIndices);

            const void *srcIndices = static_cast<const uint8_t *>(tile.data.value->getIndices()) + tile.data.value->getIndexOffset();

            DataType indexType;
            tile.data.value->getIndexType(&indexType);
            switch(indexType) {
                case TEDT_UInt8 :
                    glIndexType = GL_UNSIGNED_BYTE;
                    // XXX - no support for uint8_t indices
                    wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint16_t)]);
                    GLWireframe_deriveIndices(reinterpret_cast<uint16_t *>(wireframeIndices.get()), drawMode, numMeshIndices);
                    glIndexType = GL_UNSIGNED_SHORT;
                    break;
                case TEDT_UInt16 :
                    glIndexType = GL_UNSIGNED_SHORT;
                    wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint16_t)]);
                    GLWireframe_deriveIndices(reinterpret_cast<uint16_t *>(wireframeIndices.get()), &numIndicesWireframe, reinterpret_cast<const uint16_t *>(srcIndices), drawMode, numMeshIndices);
                    break;
                case TEDT_UInt32 :
                    glIndexType = GL_UNSIGNED_INT;
                    wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint32_t)]);
                    GLWireframe_deriveIndices(reinterpret_cast<uint32_t *>(wireframeIndices.get()), &numIndicesWireframe, reinterpret_cast<const uint32_t *>(srcIndices), drawMode, numMeshIndices);
                    break;
                default :
                    Logger_log(TELL_Error, "GLGlobe::drawTerrainTile : index type not supported by GL %d", indexType);
                    return;
            }
        } else {
            GLWireframe_getNumWireframeElements(&numIndicesWireframe, drawMode, static_cast<GLuint>(tile.data.value->getNumVertices()));

            DataType indexType;
            tile.data.value->getIndexType(&indexType);
            if (tile.data.value->getNumVertices() < 0xFFFFu) {
                glIndexType = GL_UNSIGNED_SHORT;
                wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint16_t)]);
                GLWireframe_deriveIndices(reinterpret_cast<uint16_t *>(wireframeIndices.get()), drawMode, static_cast<GLuint>(tile.data.value->getNumVertices()));
            } else {
                glIndexType = GL_UNSIGNED_INT;
                wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint32_t)]);
                GLWireframe_deriveIndices(reinterpret_cast<uint32_t *>(wireframeIndices.get()), drawMode, static_cast<GLuint>(tile.data.value->getNumVertices()));
            }
        }

        glDrawElements(GL_LINES, static_cast<GLsizei>(numIndicesWireframe), glIndexType, wireframeIndices.get());
    }

    TAKErr lla2ecef_transform(Matrix2 *value, const Projection2 &ecef, const Matrix2 *localFrame) NOTHROWS
    {
        TAKErr code(TE_Ok);

        Matrix2 mx;

        Point2<double> pointD(0.0, 0.0, 0.0);
        GeoPoint2 geo;

        // if draw projection is ECEF and source comes in as LLA, we can
        // transform from LLA to ECEF by creating a local ENU CS and
        // chaining the following conversions (all via matrix)
        // 1. LCS -> LLA
        // 2. LLA -> ENU
        // 3. ENU -> ECEF
        // 4. ECEF -> NDC (via MapSceneModel 'forward' matrix)

        // obtain origin as LLA
        pointD.x = 0;
        pointD.y = 0;
        pointD.z = 0;
        if(localFrame)
            localFrame->transform(&pointD, pointD);
        // transform origin to ECEF
        geo.latitude = pointD.y;
        geo.longitude = pointD.x;
        geo.altitude = pointD.z;
        geo.altitudeRef = TAK::Engine::Core::AltitudeReference::HAE;

        code = ecef.forward(&pointD, geo);
        TE_CHECKRETURN_CODE(code);

        // construct ENU -> ECEF
#define __RADIANS(x) ((x)*M_PI/180.0)
        const double phi = __RADIANS(geo.latitude);
        const double lambda = __RADIANS(geo.longitude);

        mx.translate(pointD.x, pointD.y, pointD.z);

        Matrix2 enu2ecef(
                -sin(lambda), -sin(phi)*cos(lambda), cos(phi)*cos(lambda), 0.0,
                cos(lambda), -sin(phi)*sin(lambda), cos(phi)*sin(lambda), 0.0,
                0, cos(phi), sin(phi), 0.0,
                0.0, 0.0, 0.0, 1.0
        );

        mx.concatenate(enu2ecef);

        // construct LLA -> ENU
        const double metersPerDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(geo.latitude);
        const double metersPerDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(geo.latitude);

        mx.scale(metersPerDegLng, metersPerDegLat, 1.0);

        value->set(mx);

        return code;
    }
}
