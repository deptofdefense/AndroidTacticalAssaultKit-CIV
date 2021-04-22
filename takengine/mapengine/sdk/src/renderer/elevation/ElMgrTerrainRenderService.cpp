#include "renderer/elevation/ElMgrTerrainRenderService.h"

#include <algorithm>
#include <vector>
#include <cassert>

#include "core/GeoPoint2.h"
#include "elevation/ElevationManager.h"
#include "elevation/ElevationSource.h"
#include "elevation/ElevationSourceManager.h"
#include "feature/GeometryTransformer.h"
#include "feature/Polygon2.h"
#include "math/AABB.h"
#include "math/Frustum2.h"
#include "math/Mesh.h"
#include "math/Point2.h"
#include "math/Rectangle.h"
#include "model/Mesh.h"
#include "model/MeshBuilder.h"
#include "model/MeshTransformer.h"
#include "model/SceneBuilder.h"
#include "port/STLVectorAdapter.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/GLTexture2.h"
#include "renderer/Skirt.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/ConfigOptions.h"
#include "util/BlockPoolAllocator.h"
#include "util/MathUtils.h"

using namespace TAK::Engine::Renderer::Elevation;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::math;
using namespace atakmap::renderer;

#define TERRAIN_LEVEL 8
#define NUM_TILE_FETCH_WORKERS 4u
#define MAX_LEVEL 19.0
#define USE_SSE_SELECT 1
// observation is that total mesh size has an upper limit around 300 tiles, but
// is most frequently circa 150-200 tiles. queue limit of 225 would assume that
// we are loading circa 75% of the upper-limit mesh.
#define MAX_TILE_QUEUE_SIZE 225u

namespace
{
    struct DeriveSource
    {
        std::size_t level{0u};
        int version{-1};
        std::shared_ptr<const TerrainTile> tile;
    };

    //https://stackoverflow.com/questions/1903954/is-there-a-standard-sign-function-signum-sgn-in-c-c
    template <typename T>
    int sgn(T val)
    {
        return (T(0) < val) - (val < T(0));
    }

    double estimateResolution(const GeoPoint2 &focus, const MapSceneModel2 &cmodel, const Envelope2 &bounds, GeoPoint2 *pclosest) NOTHROWS;
    double computeSSE(const MapSceneModel2 &scene, const Envelope2 &aabbWGS84_, const double geometricError) NOTHROWS
    {
        const int srid = scene.projection->getSpatialReferenceID();

        GeoPoint2 camlla;
        scene.projection->inverse(&camlla, scene.camera.location);
        // XXX - compute closest point on aabb
        Envelope2 aabbWGS84(aabbWGS84_);
        const double centroid = ((aabbWGS84.minX+aabbWGS84.maxX)/2.0);
        if(fabs(centroid-camlla.longitude) > 180.0 && (centroid*camlla.longitude < 0.0)) {
            // shift the AABB to the primary hemisphere
            const double hemiShift = (scene.camera.location.x >= 0.0) ? 360.0 : -360.0;
            aabbWGS84.minX += hemiShift;
            aabbWGS84.maxX += hemiShift;
        }

        GeoPoint2 closest(clamp(camlla.latitude, aabbWGS84.minY, aabbWGS84.maxY),
                          clamp(camlla.longitude, aabbWGS84.minX, aabbWGS84.maxX),
                          ((aabbWGS84.minZ + 500.0) + aabbWGS84.maxZ) / 2.0,
                          AltitudeReference::HAE);

        // compute distance to closet from camera
        TAK::Engine::Math::Point2<double> xyz;
        scene.projection->forward(&xyz, closest);
        xyz = Vector2_subtract(xyz, scene.camera.location);
        xyz.x *= scene.displayModel->projectionXToNominalMeters;
        xyz.y *= scene.displayModel->projectionYToNominalMeters;
        xyz.z *= scene.displayModel->projectionZToNominalMeters;
        const double d = Vector2_length(xyz);
        // project geometric error
        const double lambda = (scene.height / 2.0) / tan((scene.camera.fov / 2.0) / 180.0 * M_PI);
        return lambda * (geometricError / d);
    }
    std::size_t computeTargetLevelImpl(const GeoPoint2 &focus, const MapSceneModel2 &view, const Envelope2 &bounds, const double adj) NOTHROWS
    {
        if (std::min(fabs(bounds.minY), fabs(bounds.maxY)) > 84.0)
            return 0u;
        const double gsd = estimateResolution(focus, view, bounds, nullptr) * adj;
        auto level = (std::size_t)MathUtils_clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(gsd, 0.0), 0.0, MAX_LEVEL);

        // XXX - experimental
        if (false) {
            // check up to 8 neighbors, only allowing a step of one level with any neighbor
            const double dlat = (bounds.maxY - bounds.minY);
            const double dlng = (bounds.maxX - bounds.minX);
            const bool top = bounds.maxY < 90.0;
            const bool bottom = bounds.minY > -90.0;
            if (top) {
                double maxY = bounds.maxY + dlat;
                for (int i = -1; i <= 1; i++) {
                    const double minX = bounds.minX + (i*dlng);
                    Envelope2 nbounds(minX, maxY-dlat, minX+dlng, maxY);
                    const double top_gsd = estimateResolution(focus, view, bounds, nullptr) * 8.0;
                    const auto nlevel = (std::size_t)MathUtils_clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(top_gsd, 0.0), 0.0, MAX_LEVEL);
                    if (nlevel > level)
                        level = nlevel - 1u;
                }
            }
            // center, skipping self
            for (int i = -1; i <= 1; i += 2) {
                const double minX = bounds.minX + (i*dlng);
                Envelope2 nbounds(minX, bounds.minY, minX+dlng, bounds.maxY);
                const double center_gsd = estimateResolution(focus, view, bounds, nullptr) * 8.0;
                const auto nlevel = (std::size_t)MathUtils_clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(center_gsd, 0.0), 0.0, MAX_LEVEL);
                if (nlevel > level)
                    level = nlevel - 1u;
            }
            if (bottom) {
                double maxY = bounds.maxY - dlat;
                for (int i = -1; i <= 1; i++) {
                    const double minX = bounds.minX + (i*dlng);
                    Envelope2 nbounds(minX, maxY-dlat, minX+dlng, maxY);
                    const double bottom_gsd = estimateResolution(focus, view, bounds, nullptr) * 8.0;
                    const auto nlevel = (std::size_t)MathUtils_clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(bottom_gsd, 0.0), 0.0, 16.0);
                    if (nlevel > level)
                        level = nlevel - 1u;
                }
            }
        }

        return level;
    }
    std::size_t getNumEdgeVertices(const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
        // number of edge vertices is equal to perimeter length, plus one, to
        // close the linestring
        return ((numPostsLat-1u)*2u)+((numPostsLng-1u)*2u) + 1u;
    }
    TAKErr createEdgeIndices(MemBuffer2 &edgeIndices, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
        // NOTE: edges are computed in CCW order

        TAKErr code(TE_Ok);
        // top edge (right-to-left), exclude last
        for(int i = static_cast<int>(numPostsLng)-1; i > 0; i--) {
            code = edgeIndices.put<uint16_t>((uint16_t) i);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
        // left edge (bottom-to-top), exclude last
        for(int i = 0; i < static_cast<int>(numPostsLat-1u); i++) {
            const std::size_t idx = (static_cast<std::size_t>(i)*numPostsLng);
            code = edgeIndices.put<uint16_t>((uint16_t)idx);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
        // bottom edge (left-to-right), exclude last
        for(int i = 0; i < static_cast<int>(numPostsLng-1u); i++) {
            const std::size_t idx = ((numPostsLat-1u)*numPostsLng)+static_cast<std::size_t>(i);
            code = edgeIndices.put<uint16_t>((uint16_t)idx);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
        // right edge (top-to-bottom), exclude last
        for(int i = static_cast<int>(numPostsLat-1); i > 0; i--) {
            code = edgeIndices.put<uint16_t>((uint16_t) ((i * numPostsLng) + (numPostsLng - 1)));
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        // close the loop by adding first-point-as-last
        code = edgeIndices.put<uint16_t>((uint16_t)(numPostsLng-1u));
        TE_CHECKRETURN_CODE(code);

        edgeIndices.flip();
        return code;
    }
    TAKErr createHeightmapMeshIndices(MemBuffer2 &indices, const MemBuffer2 &edgeIndices, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const std::size_t numEdgeVertices = getNumEdgeVertices(numPostsLat, numPostsLng);

        std::size_t numSkirtIndices;
        code = Skirt_getNumOutputIndices(&numSkirtIndices, GL_TRIANGLE_STRIP, numEdgeVertices);
        const std::size_t numIndices = GLTexture2_getNumQuadMeshIndices(numPostsLat - 1u, numPostsLng - 1u)
                               + 2u // degenerate link to skirt
                               + numSkirtIndices;

        code = GLTexture2_createQuadMeshIndexBuffer(indices, GL_UNSIGNED_SHORT, numPostsLat - 1u, numPostsLng - 1u);
        TE_CHECKRETURN_CODE(code);

        const std::size_t skirtOffset = GLTexture2_getNumQuadMeshIndices(numPostsLat - 1u, numPostsLng - 1u);

        // insert the degenerate, last index of the mesh and first index
        // for the skirt
        code = indices.put<uint16_t>(*reinterpret_cast<const uint16_t *>(indices.get()+indices.position()-sizeof(uint16_t)));
        TE_CHECKRETURN_CODE(code);
        code = indices.put<uint16_t>(reinterpret_cast<const uint16_t *>(edgeIndices.get())[0u]);
        TE_CHECKRETURN_CODE(code);

        code = Skirt_createIndices<uint16_t>(
                indices,
                GL_TRIANGLE_STRIP,
                &edgeIndices,
                numEdgeVertices,
                static_cast<uint16_t>(numPostsLat*numPostsLng));
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    bool intersects(const Frustum2& frustum, const TAK::Engine::Feature::Envelope2& aabbWCS, const int srid, const double lng) NOTHROWS;

    std::size_t getTerrainMeshSize(const std::size_t numPosts) NOTHROWS
    {
        const std::size_t numEdgeVertices = getNumEdgeVertices(numPosts, numPosts);
        std::size_t numSkirtIndices;
        Skirt_getNumOutputIndices(&numSkirtIndices, GL_TRIANGLE_STRIP, numEdgeVertices);
        const std::size_t numIndices = GLTexture2_getNumQuadMeshIndices(numPosts - 1u, numPosts - 1u)
                               + 2u // degenerate link to skirt
                               + numSkirtIndices;

        const std::size_t skirtOffset = GLTexture2_getNumQuadMeshIndices(numPosts - 1u, numPosts - 1u);

        const std::size_t ib_size = numIndices * sizeof(uint16_t);
        const std::size_t vb_size = (numPosts*numPosts*3u + Skirt_getNumOutputVertices(numEdgeVertices) * 3u) * sizeof(float);
        const std::size_t buf_size = ib_size + vb_size;
        return buf_size;
    }
}

class ElMgrTerrainRenderService::SourceRefresh : public ElevationSource::OnContentChangedListener,
                                                 public ElevationSourcesChangedListener
{
public :
    SourceRefresh(ElMgrTerrainRenderService &service) NOTHROWS;
    ~SourceRefresh() NOTHROWS override;
public : // ElevationSource::OnContentChangedListener
    TAKErr onContentChanged(const ElevationSource &source) NOTHROWS override;
public : // ElevationSourcesChangedListener
    TAKErr onSourceAttached(const std::shared_ptr<ElevationSource> &src) NOTHROWS override;
    TAKErr onSourceDetached(const ElevationSource &src) NOTHROWS override;
private :
    ElMgrTerrainRenderService &service;
    Mutex mutex;
    std::set<ElevationSource *> sources;
};

class ElMgrTerrainRenderService::QuadNode
{
public:
    QuadNode(ElMgrTerrainRenderService &service, const std::weak_ptr<QuadNode> &parent, int srid, double minX, double minY, double maxX, double maxY) NOTHROWS;
public :
    static bool needsFetch(const std::weak_ptr<QuadNode> &node, const int srid, const int sourceVersion) NOTHROWS;
    bool collect(ElMgrTerrainRenderService::WorldTerrain &value, const GeoPoint2 &focus, const MapSceneModel2 &view, DeriveSource deriveFrom, const bool derive, const bool allowFetch) NOTHROWS;
    void reset(const bool data) NOTHROWS;
    void derive(const int srid, const DeriveSource &deriveFrom) NOTHROWS;
public :
    static void updateParentZBounds(QuadNode &node) NOTHROWS;
    static int getDerivedSourceVersion(const QuadNode& node, const DeriveSource& deriveFrom) NOTHROWS
    {
        return deriveFrom.version - (int)(node.level - deriveFrom.level);
    }
public :
    ElMgrTerrainRenderService &service;

    std::shared_ptr<QuadNode> ul;
    std::shared_ptr<QuadNode> ur;
    std::shared_ptr<QuadNode> lr;
    std::shared_ptr<QuadNode> ll;

    std::weak_ptr<QuadNode> self;

    Envelope2 bounds;
    std::size_t level;

    std::size_t lastRequestLevel;

    std::shared_ptr<TerrainTile> tile;
    bool queued;

    int sourceVersion {-1};
    int srid {-1};

    bool derived{ true };

    std::weak_ptr<QuadNode> parent;
};

namespace
{
    struct SubscribeOnContentChangedListenerBundle
    {
        ElevationSource::OnContentChangedListener *listener;
        Mutex *mutex;
    };

    bool getBoolOpt(const char *opt, bool defaultValue) NOTHROWS
    {
        do {
            TAKErr code(TE_Ok);
            TAK::Engine::Port::String val;
            if (ConfigOptions_getOption(val, opt) != TE_Ok)
                break;
            int v;
            if (TAK::Engine::Port::String_parseInteger(&v, val) != TE_Ok)
                break;
            return !!v;
        } while (false);
        return defaultValue;
    }
    double getDoubleOpt(const char *opt, double defaultValue) NOTHROWS
    {
        do {
            TAKErr code(TE_Ok);
            TAK::Engine::Port::String val;
            if (ConfigOptions_getOption(val, opt) != TE_Ok)
                break;
            double v;
            if (TAK::Engine::Port::String_parseDouble(&v, val) != TE_Ok)
                break;
            return v;
        } while (false);
        return defaultValue;
    }

    TAKErr fetch(std::shared_ptr<TerrainTile> &value, double *els, const double resolution, const Envelope2 &mbb, const int srid, const std::size_t numPostsLat, const std::size_t numPostsLng, const MemBuffer2 &edgeIndices, const bool fetchEl, const bool legacyEl, const bool constrainQueryRes, const bool fillWithHiRes, BlockPoolAllocator &allocator, PoolAllocator<TerrainTile> &tileAllocator) NOTHROWS;
    TAKErr derive(std::shared_ptr<TerrainTile> &value, const Envelope2 &mbb, const int srid, const std::size_t numPostsLat, const std::size_t numPostsLng, const MemBuffer2 &edgeIndices, const TerrainTile &deriveFrom, BlockPoolAllocator &allocator, PoolAllocator<TerrainTile> &tileAllocator) NOTHROWS;
    double estimateResolution(const GeoPoint2 &focus, const MapSceneModel2 &scene, const double ullat, const double ullng, const double lrlat, const double lrlng, GeoPoint2 *closest) NOTHROWS;
    TAKErr subscribeOnContentChangedListener(void *opaque, ElevationSource &src) NOTHROWS;
}

ElMgrTerrainRenderService::ElMgrTerrainRenderService(RenderContext &renderer_) NOTHROWS :
    renderer(renderer_),
    requestWorker(nullptr, nullptr),
    nodeCount(0u),
    reset(false),
    numPosts(32),
    fetchResolutionAdjustment(10.0),
    nodeSelectResolutionAdjustment(8.0),
    monitor(TEMT_Recursive),
    sticky(true),
    terminate(false),
    meshAllocator(getTerrainMeshSize(numPosts), 512),
    tileAllocator(256),
    edgeIndices(getNumEdgeVertices(numPosts, numPosts)*sizeof(uint16_t))
{
    east.reset(new QuadNode(*this, std::shared_ptr<QuadNode>(nullptr), -1, 0.0, -90.0, 180.0, 90.0));
    east->self = east;
    west.reset(new QuadNode(*this, std::shared_ptr<QuadNode>(nullptr), -1, -180.0, -90.0, 0.0, 90.0));
    west->self = west;

    // east
    roots[0u].reset(new QuadNode(*this, east, east->srid, 0.0, 0, 90.0, 90.0));
    roots[1u].reset(new QuadNode(*this, east, east->srid, 90.0, 0, 180.0, 90.0));
    roots[2u].reset(new QuadNode(*this, east, east->srid, 90.0, -90.0, 180.0, 0.0));
    roots[3u].reset(new QuadNode(*this, east, east->srid, 0.0, -90.0, 90.0, 0.0));
    // west
    roots[4u].reset(new QuadNode(*this, west, west->srid, -180.0, 0, -90.0, 90.0));
    roots[5u].reset(new QuadNode(*this, west, west->srid, -90.0, 0, 0.0, 90.0));
    roots[6u].reset(new QuadNode(*this, west, west->srid, -90.0, -90.0, 0.0, 0.0));
    roots[7u].reset(new QuadNode(*this, west, west->srid, -180.0, -90.0, -90.0, 0.0));

    for(std::size_t i = 0u; i < 8u; i++)
        roots[i]->self = roots[i];

    worldTerrain.reset(new WorldTerrain());
    worldTerrain->sourceVersion = -1;
    worldTerrain->srid = -1;
    worldTerrain->terrainVersion = -1;
    worldTerrain->sceneVersion = -1;

    sourceRefresh.reset(new SourceRefresh(*this));

    request.srid = -1;
    request.sceneVersion = -1;

#ifdef _MSC_VER
    const bool hiresMode = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 1);
#else
    const bool hiresMode = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 1);
#endif

    // default to legacy values if not using high-res mode
    if(hiresMode) {
        fetchResolutionAdjustment = 2.0;
#ifdef __ANDROID__
        nodeSelectResolutionAdjustment = 4.0;
#else
        nodeSelectResolutionAdjustment = 1.0;
#endif
    }

    fetchResolutionAdjustment = getDoubleOpt("terrain.resadj", fetchResolutionAdjustment);

    fetchOptions.constrainQueryRes = getBoolOpt("terrain.constrain-query-res", false);
    fetchOptions.fillWithHiRes = getBoolOpt("terrain.fill-with-hi-res", true);
#ifdef _MSC_VER
    fetchOptions.legacyElevationApi = getBoolOpt("terrain.legacy-elevation-api", true);
#else
    fetchOptions.legacyElevationApi = getBoolOpt("terrain.legacy-elevation-api", false);
#endif
    fetchOptions.fillWithHiRes = getBoolOpt("terrain.fill-with-hi-res", true);

    queue.entries.reserve(MAX_TILE_QUEUE_SIZE);

    createEdgeIndices(edgeIndices, numPosts, numPosts);
}

ElMgrTerrainRenderService::~ElMgrTerrainRenderService() NOTHROWS
{
    stop();

    // release `roots`
    for(std::size_t i = 0u; i < 8u; i++) {
        roots[i].reset();
    }
}

//public synchronized void lock(GLMapView view, Collection<GLMapView.TerrainTile> tiles) {
TAKErr ElMgrTerrainRenderService::lock(TAK::Engine::Port::Collection<std::shared_ptr<const TerrainTile>> &value, const MapSceneModel2 &view, const int srid, const int sceneVersion, const bool derive) NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock lock(monitor);
    TE_CHECKRETURN_CODE(lock.status);

    queue.sorted &= (sceneVersion == request.sceneVersion);

    request.srid = srid;
    request.sceneVersion = sceneVersion;
    request.scene = view;
    request.derive = derive;

    // data is invalid if SRID changed
    bool invalid = (worldTerrain->srid != srid);
    // if `front` is empty or content invalid repopulate with "root" tiles
    // temporarily until new data is fetched
    if(worldTerrain->tiles.empty() || invalid) {
        // drain the `front` buffer
        worldTerrain->tiles.clear();

        for(std::size_t i = 0; i < 8u; i++) {
            std::shared_ptr<TerrainTile> tile;
            code = fetch(tile,
                         nullptr,
                         atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(roots[i]->level))*fetchResolutionAdjustment,
                         roots[i]->bounds,
                         srid,
                         numPosts,
                         numPosts,
                         edgeIndices,
                         false,
                         fetchOptions.legacyElevationApi,
                         fetchOptions.constrainQueryRes,
                         fetchOptions.fillWithHiRes,
                         meshAllocator,
                         tileAllocator);
            if(code != TE_Ok) {
                worldTerrain->tiles.clear();
                TE_CHECKBREAK_CODE(code);
            }
            worldTerrain->tiles.push_back(tile);
        }
        worldTerrain->srid = srid;
    }
    TE_CHECKRETURN_CODE(code);

    // signal the request handler
    if(invalid || request.sceneVersion != worldTerrain->sceneVersion || version.quadtree != worldTerrain->terrainVersion) {
        if (!requestWorker.get()) {
            ThreadCreateParams params;
            params.name = "ElMgrTerrainRenderServiceBackgroundWorker-request-thread";
            params.priority = TETP_Normal;
            code = Thread_start(requestWorker, requestWorkerThread, this, params);
            TE_CHECKRETURN_CODE(code);
        }

        lock.broadcast();
    }

    // copy the tiles into the client collection. acquire new locks on each
    // tile. these locks will be relinquished by the client when it calls
    // `unlock`
    for(std::shared_ptr<const TerrainTile> tile : worldTerrain->tiles) {
        // acquire a new reference on the tile since it's being passed
        code = value.add(tile);
        TE_CHECKBREAK_CODE(code);

        assert(!!tile);
        assert(!!tile->data.value);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr ElMgrTerrainRenderService::lock(TAK::Engine::Port::Collection<std::shared_ptr<const TerrainTile>> &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock lock(monitor);
    TE_CHECKRETURN_CODE(lock.status);

    // copy the tiles into the client collection. acquire new locks on each
    // tile. these locks will be relinquished by the client when it calls
    // `unlock`
    for(std::shared_ptr<const TerrainTile> tile : worldTerrain->tiles) {
        // acquire a new reference on the tile since it's being passed
        code = value.add(tile);
        TE_CHECKBREAK_CODE(code);

        assert(!!tile);
        assert(!!tile->data.value);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

int ElMgrTerrainRenderService::getTerrainVersion() const NOTHROWS
{
    Monitor::Lock mlock(monitor);
    return (version.world + version.source + version.quadtree);
}

//public synchronized void unlock(Collection<GLMapView.TerrainTile> tiles)
TAKErr ElMgrTerrainRenderService::unlock(TAK::Engine::Port::Collection<std::shared_ptr<const TerrainTile>> &tiles) NOTHROWS
{
    return TE_Ok;
}

//public double getElevation(GeoPoint geo)
TAKErr ElMgrTerrainRenderService::getElevation(double *value, const double latitude, const double longitude) const NOTHROWS
{
    return ElevationManager_getElevation(value, nullptr, latitude, longitude, ElevationSource::QueryParameters());
}

TAKErr ElMgrTerrainRenderService::start() NOTHROWS
{
    {
        Monitor::Lock mlock(monitor);
        terminate = false;
    }
    return TE_Ok;
}

TAKErr ElMgrTerrainRenderService::stop() NOTHROWS
{
    {
        Monitor::Lock mlock(monitor);
        // signal to worker threads that we are terminating
        terminate = true;
        mlock.broadcast();
    }
    {
        Monitor::Lock mlock(queue.monitor);
        mlock.broadcast();
    }

    // wait for the worker thread to die
    queue.worker.reset();
    requestWorker.reset();

    return TE_Ok;
}

//synchronized void enqueue(QuadNode node)
TAKErr ElMgrTerrainRenderService::enqueue(const std::shared_ptr<QuadNode> &node) NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock mlock(queue.monitor);
    TE_CHECKRETURN_CODE(mlock.status);

    // already queued
    if(node->queued)
        return code;

    // queue is at max capacity
    if(queue.entries.size() == MAX_TILE_QUEUE_SIZE)
        return TE_Busy;

    if (!queue.worker.get()) {
        //ThreadCreateParams params;
        //params.name = "ElMgrTerrainRenderServiceBackgroundWorker-fetch-thread";
        //params.priority = TETP_Normal;
        code = ThreadPool_create(queue.worker, NUM_TILE_FETCH_WORKERS, fetchWorkerThread, this);
        TE_CHECKRETURN_CODE(code);
    }

    // enqueue the node
    node->queued = true;
    queue.entries.push_back(node);
    queue.sorted = false;
    code = mlock.signal();
    TE_CHECKRETURN_CODE(code);

    return code;
}

void *ElMgrTerrainRenderService::requestWorkerThread(void *opaque)
{
    ElMgrTerrainRenderService &owner = *static_cast<ElMgrTerrainRenderService *>(opaque);

    // signals that the front and back buffers should be flipped
    bool flip = false;
    // NOTE: `reset` is external here to allow for forcing tree rebuild
    // by toggling value in debugger
    bool reset = false;
    Request fetch;
    fetch.srid = -1;
    std::unique_ptr<WorldTerrain> fetchBuffer(new WorldTerrain());
    while(true) {
        {
            Monitor::Lock mlock(owner.monitor);
            if (owner.terminate)
                break;

            // if we're marked for flip and the SRID is the same, swap the front and back
            // buffers
            if(flip && fetchBuffer->srid == owner.request.srid) {
                // flip the front and back buffers, references are transferred
                std::unique_ptr<WorldTerrain> swap = std::move(owner.worldTerrain);
                owner.worldTerrain = std::move(fetchBuffer);
                fetchBuffer = std::move(swap);
                owner.version.world++;

                // flip was done
                flip = false;

                // clear back buffer for the next fetch
                fetchBuffer->tiles.clear();

                // request refresh
                owner.renderer.requestRefresh();
            }

            // if scene is unchanged and no new terrain, wait
            if(owner.request.sceneVersion == owner.worldTerrain->sceneVersion &&
               owner.version.quadtree == owner.worldTerrain->terrainVersion) {

                mlock.wait();

                flip = false;
                continue;
            }

#if 0
            fetch = owner.request;
#else
            fetch.sceneVersion = owner.request.sceneVersion;
            fetch.srid = owner.request.srid;
            fetch.scene = owner.request.scene;
            fetch.derive = owner.request.derive;
#endif
            const bool invalid = owner.east->srid != owner.request.srid;
            reset |= invalid;

            // synchronize quadtree SRID with current scene
            if(reset) {
                // SRID is changed, update the roots
                if(invalid) {
                    owner.east->srid = owner.request.srid;
                    owner.west->srid = owner.request.srid;
                }

                for(std::size_t i = 0; i < 8u; i++) {
                    std::shared_ptr<QuadNode> parent(owner.roots[i]->parent.lock());
                    owner.roots[i].reset(new QuadNode(owner, parent, parent->srid, owner.roots[i]->bounds.minX, owner.roots[i]->bounds.minY, owner.roots[i]->bounds.maxX, owner.roots[i]->bounds.maxY));
                    owner.roots[i]->self = owner.roots[i];
                }

                owner.queue.entries.clear();
                reset = false;
            }

            flip = true;
            fetchBuffer->srid = owner.request.srid;
            fetchBuffer->sourceVersion = owner.version.source;
            fetchBuffer->terrainVersion = owner.version.quadtree;
            fetchBuffer->sceneVersion = owner.request.sceneVersion;
            fetchBuffer->tiles = owner.worldTerrain->tiles;

            owner.renderer.requestRefresh();
        }

        GeoPoint2 focus;
        fetch.scene.projection->inverse(&focus, fetch.scene.camera.target);

        // clear the tiles in preparation for fetch
        fetchBuffer->tiles.clear();

        Matrix2 m(fetch.scene.camera.projection);
        m.concatenate(fetch.scene.camera.modelView);
        Frustum2 frustum(m);
        const int srid = fetch.scene.projection->getSpatialReferenceID();

        for(std::size_t i = 0; i < 8u; i++) {
            // compute AABB in WCS and check for intersection with the frustum
            TAK::Engine::Feature::Envelope2 aabbWCS(owner.roots[i]->bounds);
            TAK::Engine::Feature::GeometryTransformer_transform(&aabbWCS, aabbWCS, 4326, srid);
            if(intersects(frustum, aabbWCS, srid, focus.longitude)) {
                owner.roots[i]->collect(*fetchBuffer, focus, fetch.scene, DeriveSource(), fetch.derive, true);
            } else {
                // no intersection
                if(!owner.roots[i]->tile.get()) {
                    // there's no data, grab an empty tile
                    ::fetch(owner.roots[i]->tile,
                            nullptr,
                            atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(owner.roots[i]->level))*owner.fetchResolutionAdjustment,
                            owner.roots[i]->bounds,
                            fetchBuffer->srid,
                            owner.numPosts,
                            owner.numPosts,
                            owner.edgeIndices,
                            false,
                            owner.fetchOptions.legacyElevationApi,
                            owner.fetchOptions.constrainQueryRes,
                            owner.fetchOptions.fillWithHiRes,
                            owner.meshAllocator,
                            owner.tileAllocator);
                    owner.roots[i]->sourceVersion = fetchBuffer->sourceVersion;
                }

                owner.roots[i]->reset(false);

                // add a new reference to the tile to "back"
                fetchBuffer->tiles.push_back(owner.roots[i]->tile);
            }
        }
    }

    return nullptr;
}

void *ElMgrTerrainRenderService::fetchWorkerThread(void *opaque)
{
    ElMgrTerrainRenderService &service = *static_cast<ElMgrTerrainRenderService *>(opaque);

    std::shared_ptr<QuadNode> node;
    std::shared_ptr<TerrainTile> tile;
    std::size_t fetchedNodes = 0;
    int fetchSrcVersion = ~service.version.source;
    array_ptr<double> els(new double[service.numPosts*service.numPosts*3u]);
    MapSceneModel2 cmodel;
    while(true) {
        Envelope2 bounds;

        //synchronized(ElMgrTerrainRenderService.this)

        // execute the fetch loop
        {
            TAKErr code(TE_Ok);
            Monitor::Lock qlock(service.queue.monitor);
            code = qlock.status;

            const bool quadtreeUpdate = !!node;
            if(quadtreeUpdate) {
                // transfer the tile
                {
                    node->queued = false;
                    node->sourceVersion = fetchSrcVersion;
                    node->tile = tile;
                    node->derived = false;

                    //node->tile.info.minDisplayResolution = node.level;
                    if (node->level > TERRAIN_LEVEL) {
                        node->bounds.minZ = node->tile->aabb_wgs84.minZ;
                        node->bounds.maxZ = node->tile->aabb_wgs84.maxZ;
                        QuadNode::updateParentZBounds(*node);
                    }

                    node->tile->aabb_wgs84 = node->bounds;
                }
                node.reset();
                tile.reset();
            }

            // check for termination; capture state from service for fetch
            {
                Monitor::Lock slock(service.monitor);
                code = slock.status;

                if (service.terminate)
                    break;

                if(quadtreeUpdate) {
                    service.version.quadtree++;
                    service.renderer.requestRefresh();
                }

                cmodel = service.request.scene;
                fetchSrcVersion = service.version.source;
            }

            if(service.queue.entries.empty()) {
                code = qlock.wait();
                if (code == TE_Interrupted)
                    code = TE_Ok;
                TE_CHECKBREAK_CODE(code);

                continue;
            }

            // sort the queue if necessary
            if(!service.queue.sorted) {
                GeoPoint2 cam;
                cmodel.projection->inverse(&cam, cmodel.camera.location);

                // NOTE: sort into LIFO order
                struct TT_cmp
                {
                    TT_cmp(const GeoPoint2 &c) : cam(c) {}
                    bool operator()(const std::shared_ptr<ElMgrTerrainRenderService::QuadNode> &a, const std::shared_ptr<ElMgrTerrainRenderService::QuadNode> &b) NOTHROWS
                    {
                        // sort low to high resolution, then sort based on distance from camera
                        if(a->level < b->level)
                            return false;
                        else if(a->level > b->level)
                            return true;

                        double calat = (a->bounds.minY+a->bounds.maxY)/2.0;
                        double calng = (a->bounds.minX+a->bounds.maxX)/2.0;
                        if(cam.longitude*calng < 0 && fabs(cam.longitude-calng) > 180.0)
                            calng += (cam.longitude < 0.0) ? -360.0 : 360.0;

                        const double dalat = cam.latitude-calat;
                        const double dalng = cam.longitude-calng;
                        const double da2 = (dalat*dalat)+(dalng*dalng);

                        double cblat = (b->bounds.minY+b->bounds.maxY)/2.0;
                        double cblng = (b->bounds.minX+b->bounds.maxX)/2.0;
                        if(cam.longitude*cblng < 0 && fabs(cam.longitude-cblng) > 180.0)
                            cblng += (cam.longitude < 0.0) ? -360.0 : 360.0;

                        const double dblat = cam.latitude-cblat;
                        const double dblng = cam.longitude-cblng;
                        const double db2 = (dblat*dblat)+(dblng*dblng);

                        if(da2 < db2)
                            return false;
                        else if(da2 > db2)
                            return true;

                        // don't expect this to happen
                        return (intptr_t)a.get() < (intptr_t)b.get();
                    }
                    const GeoPoint2 cam;
                };
                std::sort(service.queue.entries.begin(), service.queue.entries.end(), TT_cmp(cam));
                service.queue.sorted = true;
            }

            node = service.queue.entries.back();
            service.queue.entries.pop_back();

            std::shared_ptr<QuadNode> parent(node->parent.lock());
            bounds = parent ? parent->bounds : node->bounds;
        }

        GeoPoint2 focus;
        cmodel.projection->inverse(&focus, cmodel.camera.target);
        Matrix2 m(cmodel.camera.projection);
        m.concatenate(cmodel.camera.modelView);
        Frustum2 frustum(m);
        // compute AABB in WCS and check for intersection with the frustum
        TAK::Engine::Feature::Envelope2 aabbWCS(bounds);
        const int srid = cmodel.projection->getSpatialReferenceID();
        TAK::Engine::Feature::GeometryTransformer_transform(&aabbWCS, aabbWCS, 4326, srid);
        if (!intersects(frustum, aabbWCS, srid, focus.longitude)) {
            node->queued = false;
            node.reset();
            continue;
        }

        const double res = atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(node->level))*service.fetchResolutionAdjustment;
        TAKErr code = fetch(tile, els.get(), res, node->bounds, node->srid, service.numPosts, service.numPosts, service.edgeIndices, node->level >= TERRAIN_LEVEL, service.fetchOptions.legacyElevationApi, service.fetchOptions.constrainQueryRes, service.fetchOptions.fillWithHiRes, service.meshAllocator, service.tileAllocator);
        TE_CHECKBREAK_CODE(code);

        fetchedNodes++;

        //Log.i("ElMgrTerrainRenderService", "fetched node, count=" + fetchedNodes);
    }


    return nullptr;
}

ElMgrTerrainRenderService::SourceRefresh::SourceRefresh(ElMgrTerrainRenderService &service_) NOTHROWS :
    service(service_)
{
    ElevationSourceManager_addOnSourcesChangedListener(this);
    SubscribeOnContentChangedListenerBundle arg;
    arg.listener = this;
    arg.mutex = &this->mutex;
    ElevationSourceManager_visitSources(subscribeOnContentChangedListener, &arg);
}
ElMgrTerrainRenderService::SourceRefresh::~SourceRefresh() NOTHROWS
{
    ElevationSourceManager_removeOnSourcesChangedListener(this);
    Lock lock(mutex);
    for(auto it = sources.begin(); it != sources.end(); it++) {
        (*it)->removeOnContentChangedListener(this);
    }
    sources.clear();
}
TAKErr ElMgrTerrainRenderService::SourceRefresh::onContentChanged(const ElevationSource &source) NOTHROWS
{
    service.version.source++;

    service.renderer.requestRefresh();
    return TE_Ok;
}
TAKErr ElMgrTerrainRenderService::SourceRefresh::onSourceAttached(const std::shared_ptr<ElevationSource> &src) NOTHROWS
{
    service.version.source++;

    service.renderer.requestRefresh();

    Lock lock(mutex);
    TE_CHECKRETURN_CODE(lock.status);
    if(sources.find(src.get()) == sources.end()) {
        sources.insert(src.get());
        src->addOnContentChangedListener(this);
    }

    return TE_Ok;
}
TAKErr ElMgrTerrainRenderService::SourceRefresh::onSourceDetached(const ElevationSource &src) NOTHROWS
{
    {
        Lock lock(mutex);
        TE_CHECKRETURN_CODE(lock.status);
        auto entry = sources.find(const_cast<ElevationSource *>(&src));
        if(entry != sources.end()) {
            (*entry)->removeOnContentChangedListener(this);
            sources.erase(entry);
        }
    }

    service.version.source++;

    service.renderer.requestRefresh();
    return TE_Ok;
}

ElMgrTerrainRenderService::QuadNode::QuadNode(ElMgrTerrainRenderService &service_, const std::weak_ptr<QuadNode> &parent_, int srid_, double minX, double minY, double maxX, double maxY) NOTHROWS :
    service(service_),
    parent(parent_),
    bounds(minX, minY, -415.0, maxX, maxY, 8850.0),
    level(0u),
    lastRequestLevel(0u),
    queued(false),
    srid(srid_)
{
    std::shared_ptr<QuadNode> p(parent.lock());
    if(p)
        level = p->level+1u;
#if 0
    nodeCount++;

    Log.i("ElMgrTerrainRenderService", "Create node " + nodeCount);
#endif
}

bool ElMgrTerrainRenderService::QuadNode::needsFetch(const std::weak_ptr<QuadNode> &ref, const int srid, const int sourceVersion) NOTHROWS
{
    std::shared_ptr<QuadNode> node(ref.lock());
    if (!node)
        return true;
    Monitor::Lock lock(node->service.queue.monitor);
    return
        (node->sourceVersion != sourceVersion) ||
        (!node->queued &&
        (!node->tile || node->tile->data.srid != srid));
}

bool ElMgrTerrainRenderService::QuadNode::collect(ElMgrTerrainRenderService::WorldTerrain &value, const GeoPoint2 &focus, const MapSceneModel2 &scene, DeriveSource deriveFrom, const bool allowDerive, const bool allowFetch) NOTHROWS
{
#if USE_SSE_SELECT
    struct {
        bool operator()(const MapSceneModel2 &s, const std::size_t l, const Envelope2 &b) NOTHROWS
        {
            if(l >= MAX_LEVEL)
                return false;
            double reslat = 0.0;
            // if non-planar projection, start adapting the geometric error per
            // the actual resolution at the edge of the tile closest to the
            // equator
            if(s.displayModel->earth->getGeomClass() != GeometryModel2::PLANE &&
                std::min(fabs(b.minY), fabs(b.maxY)) > 67.0) {

                // fudge the value we're feeding into the resolution
                // computation by doubling the distance (in degrees latitude)
                // from the centroid to the respective pole
                if(b.minY < 0.0) {
                    const double dpole = 90.0 + ((b.minY+b.maxY)/2.0);
                    reslat = -90.0 + (2.0*dpole);
                } else {
                    const double dpole = 90.0 - ((b.minY+b.maxY)/2.0);
                    reslat = 90.0 - (2.0*dpole);
                }
            }
            const double geometricError = atakmap::raster::osm::OSMUtils::mapnikTileResolution((int)l, reslat);
            const double sse = computeSSE(s, b, geometricError);
#ifdef __ANDROID__
            bool recurse = (sse > 5.0);
#else
            bool recurse = (sse > 2.0);
#endif
            if(recurse) {
                bool isect = false;
                if(MapSceneModel2_intersects(&isect, s, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ) == TE_Ok)
                    recurse &= isect;
            }
            return recurse;
        }
    } shouldRecurse;

    if(shouldRecurse(scene, this->level, this->bounds)) {
#else
    const double selectResAdj = (MapSceneModel2_getCameraMode() == MapCamera2::Scale) ? service.nodeSelectResolutionAdjustment : service.nodeSelectResolutionAdjustment / 2.0;
    const std::size_t target_level = computeTargetLevelImpl(
        focus,
        scene,
        this->bounds,
        selectResAdj);
    if(target_level > this->level) {
#endif

        const double centerX = (this->bounds.minX+this->bounds.maxX)/2.0;
        const double centerY = (this->bounds.minY+this->bounds.maxY)/2.0;

        // compute child intersections
        const bool recurseLL = shouldRecurse(scene, this->level+1u, Envelope2(bounds.minX, bounds.minY, bounds.minZ, centerX, centerY, bounds.maxZ));
        const bool recurseLR = shouldRecurse(scene, this->level+1u, Envelope2(centerX, bounds.minY, bounds.minZ, bounds.maxX, centerY, bounds.maxZ));
        const bool recurseUR = shouldRecurse(scene, this->level+1u, Envelope2(centerX, centerY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ));
        const bool recurseUL = shouldRecurse(scene, this->level+1u, Envelope2(bounds.minX, centerY, bounds.minZ, centerX, bounds.maxY, bounds.maxZ));

        const bool fetchul = needsFetch(ul, value.srid, value.sourceVersion);
        const bool fetchur = needsFetch(ur, value.srid, value.sourceVersion);
        const bool fetchlr = needsFetch(lr, value.srid, value.sourceVersion);
        const bool fetchll = needsFetch(ll, value.srid, value.sourceVersion);
        const bool fetchingul = (ul.get() && ul->queued);
        const bool fetchingur = (ur.get() && ur->queued);
        const bool fetchinglr = (lr.get() && lr->queued);
        const bool fetchingll = (ll.get() && ll->queued);

        // derive root
        if (allowDerive && level == 1u && !deriveFrom.tile) {
            Monitor::Lock lock(service.queue.monitor);
            if(this->tile) {
                deriveFrom.tile = this->tile;
                deriveFrom.level = this->level;
                deriveFrom.version = this->sourceVersion;
            }
        }

        // fetch tile nodes
        if(fetchll && !fetchingll) {
            if(!ll.get()) {
                ll.reset(new QuadNode(service, self, this->srid, this->bounds.minX, this->bounds.minY, centerX, centerY));
                ll->self = ll;
            }
            if(allowFetch)
                service.enqueue(ll);
        }
        if(fetchlr && !fetchinglr) {
            if(!lr.get()) {
                lr.reset(new QuadNode(service, self, this->srid, centerX, this->bounds.minY, this->bounds.maxX, centerY));
                lr->self = lr;
            }
            if(allowFetch)
                service.enqueue(lr);
        }
        if(fetchur && !fetchingur) {
            if(!ur.get()) {
                ur.reset(new QuadNode(service, self, this->srid, centerX, centerY, this->bounds.maxX, this->bounds.maxY));
                ur->self = ur;
            }
            if(allowFetch)
                service.enqueue(ur);
        }
        if(fetchul && !fetchingul) {
            if(!ul.get()) {
                ul.reset(new QuadNode(service, self, this->srid, this->bounds.minX, centerY, centerX, this->bounds.maxY));
                ul->self = ul;
            }
            if(allowFetch)
                service.enqueue(ul);
        }

        // only allow recursion if all nodes have been fetched
        const bool recurseAny = (recurseLL || recurseUL || recurseUR || recurseLR);
        const bool recurse = recurseAny &&
                             (((ll.get() && ll->tile.get()) &&
                              (lr.get() && lr->tile.get()) &&
                              (ur.get() && ur->tile.get()) &&
                              (ul.get() && ul->tile.get())) || deriveFrom.tile);

        // children may fetch recursively if they are all fetched for the
        // current source. This is employed to mitigate tile pops and provide
        // an experience more consistent with legacy while preserving the
        // ability to do derivation
        const bool recurseFetch = allowFetch &&
                recurseAny &&
                (ll.get() && ll->sourceVersion == value.sourceVersion) &&
                (lr.get() && lr->sourceVersion == value.sourceVersion) &&
                (ur.get() && ur->sourceVersion == value.sourceVersion) &&
                (ul.get() && ul->sourceVersion == value.sourceVersion);

        // descendents derive from `this` if we have tile data and there is no
        // derive root or we have a newer or same source version
        if(recurse && allowDerive) {
            Monitor::Lock lock(service.queue.monitor);
            if (tile &&
                ((!deriveFrom.tile && tile) ||
                 (deriveFrom.tile && sourceVersion >= deriveFrom.version))) {

                deriveFrom.tile = this->tile;
                deriveFrom.version = this->sourceVersion;
                deriveFrom.level = this->level;
            }
        }

        if(recurseLL) {
            if(recurse) {
                ll->collect(value, focus, scene, deriveFrom, allowDerive, allowFetch && recurseFetch);
            }
        } else if(ll.get()) {
            ll->reset(false);
            if (recurse) {
                if (!ll->tile || (deriveFrom.tile && ll->sourceVersion < getDerivedSourceVersion(*ll, deriveFrom)))
                    ll->derive(value.srid, deriveFrom);
                value.tiles.push_back(ll->tile);
            }
        }
        if(recurseLR) {
            if(recurse) {
                lr->collect(value, focus, scene, deriveFrom, allowDerive, allowFetch && recurseFetch);
            }
        } else if(lr.get()) {
            lr->reset(false);
            if (recurse) {
                if (!lr->tile || (deriveFrom.tile && lr->sourceVersion < getDerivedSourceVersion(*lr, deriveFrom)))
                    lr->derive(value.srid, deriveFrom);
                value.tiles.push_back(lr->tile);
            }
        }
        if(recurseUR) {
            if(recurse) {
                ur->collect(value, focus, scene, deriveFrom, allowDerive, allowFetch && recurseFetch);
            }
        } else if(ur.get()) {
            ur->reset(false);
            if (recurse) {
                if (!ur->tile || (deriveFrom.tile && ll->sourceVersion < getDerivedSourceVersion(*ur, deriveFrom)))
                    ur->derive(value.srid, deriveFrom);
                value.tiles.push_back(ur->tile);
            }
        }
        if(recurseUL) {
            if(recurse) {
                ul->collect(value, focus, scene, deriveFrom, allowDerive, allowFetch && recurseFetch);
            }
        } else if(ul.get()) {
            ul->reset(false);
            if (recurse) {
                if (!ul->tile || (deriveFrom.tile && ul->sourceVersion < getDerivedSourceVersion(*ul, deriveFrom)))
                    ul->derive(value.srid, deriveFrom);
                value.tiles.push_back(ul->tile);
            }
        }

        if(recurse)
            return true;
    }

    if(needsFetch(self, srid, value.sourceVersion)) {
        if(this->level <= 1) {
            this->sourceVersion = value.sourceVersion;
            fetch(this->tile,
                  nullptr,
                  atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(this->level))*service.fetchResolutionAdjustment,
                  this->bounds,
                  value.srid,
                  service.numPosts,
                  service.numPosts,
                  service.edgeIndices,
                  false,
                  service.fetchOptions.legacyElevationApi,
                  service.fetchOptions.constrainQueryRes,
                  service.fetchOptions.fillWithHiRes,
                  service.meshAllocator,
                  service.tileAllocator);
            this->derived = false;
        } else {
            // XXX - this is a little goofy
            service.enqueue(self.lock());
        }
    }
    if(!this->tile || (derived && deriveFrom.tile && sourceVersion < getDerivedSourceVersion(*this, deriveFrom))) {
        if (!deriveFrom.tile)
            return false; // should be illegal state
        derive(value.srid, deriveFrom);
    }

    assert(!!this->tile);
    value.tiles.push_back(this->tile);
    return true;
}
void ElMgrTerrainRenderService::QuadNode::derive(const int srid_, const DeriveSource& deriveFrom) NOTHROWS
{
    std::shared_ptr<TerrainTile> derivedTile;
    ::derive(derivedTile,
        this->bounds,
        srid_,
        service.numPosts,
        service.numPosts,
        service.edgeIndices,
        *deriveFrom.tile,
        service.meshAllocator,
        service.tileAllocator);

    // set the derived tile
    {
        Monitor::Lock lock(service.queue.monitor);
        // check for async fetch
        if (this->tile && this->sourceVersion > deriveFrom.version)
            return;
        this->tile = derivedTile;
        this->bounds.minZ = this->tile->aabb_wgs84.minZ;
        this->bounds.maxZ = this->tile->aabb_wgs84.maxZ;
        this->derived = true;
        this->sourceVersion = getDerivedSourceVersion(*this, deriveFrom);
    }
}

void ElMgrTerrainRenderService::QuadNode::reset(const bool data) NOTHROWS
{
    Monitor::Lock qlock(service.queue.monitor);

    // release all children. if queued, reset level to `0` to move out of the
    // queue as fast as possible
    if (ul.get()) {
        if(ul->queued)
            ul->level = 0u;
        ul->reset(true);
        ul.reset();
    }
    if (ur.get()) {
        if(ur->queued)
            ur->level = 0u;
        ur->reset(true);
        ur.reset();
    }
    if (lr.get()) {
        if(lr->queued)
            lr->level = 0u;
        lr->reset(true);
        lr.reset();
    }
    if (ll.get()) {
        if(ll->queued)
            ll->level = 0u;
        ll->reset(true);
        ll.reset();
    }

    if (data && this->tile.get() && this->tile->data.value) {
        this->tile.reset();
        this->derived = true;
    }
}

void ElMgrTerrainRenderService::QuadNode::updateParentZBounds(QuadNode &node) NOTHROWS
{
    std::shared_ptr<QuadNode> parent(node.parent.lock());
    if (parent) {
        const bool updated = (node.bounds.minZ < parent->bounds.minZ) || (node.bounds.maxZ > parent->bounds.maxZ);
        if (node.bounds.minZ < parent->bounds.minZ)
            parent->bounds.minZ = node.bounds.minZ;
        if (node.bounds.maxZ > parent->bounds.maxZ)
            parent->bounds.maxZ = node.bounds.maxZ;

        if (updated)
            updateParentZBounds(*parent);
    }
}

namespace
{
    //static GLMapView.TerrainTile fetch(double resolution, Envelope mbb, int srid, int numPostsLat, int numPostsLng, bool fetchEl)
    TAKErr fetch(std::shared_ptr<TerrainTile> &value_, double *els, const double resolution, const Envelope2 &mbb, const int srid, const std::size_t numPostsLat, const std::size_t numPostsLng, const MemBuffer2 &edgeIndices_, const bool fetchEl, const bool legacyEl, const bool constrainQueryRes, const bool fillWithHiRes, BlockPoolAllocator &allocator, PoolAllocator<TerrainTile> &tileAllocator) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::shared_ptr<TerrainTile> value;

        if (fetchEl && !legacyEl) {
            double *pts = els + (numPostsLat * numPostsLng);
            for (std::size_t postLat = 0u; postLat < numPostsLat; postLat++) {
                for (std::size_t postLng = 0u; postLng < numPostsLng; postLng++) {
                    std::size_t i = (postLat*numPostsLng)+postLng;
                    pts[i*2u] = mbb.minX + ((mbb.maxX - mbb.minX) / (numPostsLng-1)) * postLng;
                    pts[i*2u+1u] = mbb.minY + ((mbb.maxY - mbb.minY) / (numPostsLat-1)) * postLat;
                }
            }

            ElevationSource::QueryParameters params;
            params.order = TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>::Ptr(new TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>(), Memory_deleter_const<TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>, TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>>);
            code = params.order->add(ElevationSource::QueryParameters::ResolutionDesc);
            TE_CHECKRETURN_CODE(code);
            if(constrainQueryRes)
                params.maxResolution = resolution;
            code = Polygon2_fromEnvelope(params.spatialFilter, mbb);
            TE_CHECKRETURN_CODE(code);

            // try to fill all values using high-to-low res elevation chunks
            // covering the AOI
            if(ElevationManager_getElevation(els, (numPostsLat*numPostsLng), pts+1u, pts, 2u, 2u, 1u, params) == TE_Done && constrainQueryRes) {
                // if there are holes, fill in using low-to-high res elevation
                // chunks covering the AOI
                params.order = TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>::Ptr(new TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>(), Memory_deleter_const<TAK::Engine::Port::Collection<ElevationSource::QueryParameters::Order>, TAK::Engine::Port::STLVectorAdapter<ElevationSource::QueryParameters::Order>>);
                code = params.order->add(ElevationSource::QueryParameters::ResolutionAsc);
                params.maxResolution = NAN;

                std::size_t numpts2 = 0u;
                array_ptr<double> pts2(new double[(numPostsLat*numPostsLng)*3u]);
                for(std::size_t i = 0u; i < (numPostsLat*numPostsLng); i++) {
                    if (isnan(els[i])) {
                        pts2[numpts2*3u] = pts[i*2+1u];
                        pts2[numpts2*3u+1u] = pts[i*2+1u];
                        pts2[numpts2*3+2u] = NAN;

                        numpts2++;
                    }
                }

                // fetch the elevations
                ElevationManager_getElevation(pts2.get()+2u, (numPostsLat*numPostsLng), pts2.get()+1u, pts2.get(), 3u, 3u, 3u, params);

                // fill the holes
                std::size_t pts2Idx = 0;
                for(std::size_t i = 0u; i < (numPostsLat*numPostsLng); i++) {
                    if (isnan(els[i])) {
                        els[i] = pts2[pts2Idx*3u+2u];
                        pts2Idx++;
                        if(pts2Idx == numpts2)
                            break;
                    }
                }
            }
        } else if(fetchEl && legacyEl) {
            std::vector<GeoPoint2> pts;
            pts.reserve(numPostsLat * numPostsLng);
            for (std::size_t postLat = 0; postLat < numPostsLat; postLat++) {
                for (std::size_t postLng = 0; postLng < numPostsLng; postLng++) {
                    pts.push_back(GeoPoint2(mbb.minY + ((mbb.maxY - mbb.minY) / (numPostsLat - 1)) * postLat, mbb.minX + ((mbb.maxX - mbb.minX) / (numPostsLng - 1)) * postLng));
                }
            }

            ElevationManagerQueryParameters filter;
            Geometry2Ptr_const spatialFilter(nullptr, nullptr);
            code = Polygon2_fromEnvelope(spatialFilter, mbb);
            TE_CHECKRETURN_CODE(code);
            code = Geometry_clone(filter.spatialFilter, *spatialFilter);
            TE_CHECKRETURN_CODE(code);

            STLVectorAdapter<GeoPoint2> ptsAdapter(pts);
            Collection<GeoPoint2>::IteratorPtr ptsIter(nullptr, nullptr);
            code = ptsAdapter.iterator(ptsIter);
            TE_CHECKRETURN_CODE(code);
            code = ElevationManager_getElevation(els, ptsIter, filter, ElevationData::Hints());
            TE_CHECKRETURN_CODE(code);
        }

        // number of edge vertices is equal to perimeter length, plus one, to
        // close the linestring
        const std::size_t numEdgeVertices = ((numPostsLat-1u)*2u)+((numPostsLng-1u)*2u) + 1u;

        bool hasData = fetchEl && !isnan(els[0]);
        double minEl = !hasData ? 0.0 : els[0];
        double maxEl = !hasData ? 0.0 : els[0];
        if (fetchEl) {
            for (std::size_t i = 1; i < (numPostsLat*numPostsLng); i++) {
                hasData |= !isnan(els[i]);
                if (els[i] < minEl)
                    minEl = els[i];
                if (els[i] > maxEl)
                    maxEl = els[i];
            }
        }

        double localOriginX = (mbb.minX+mbb.maxX)/2.0;
        double localOriginY = (mbb.minY+mbb.maxY)/2.0;
        double localOriginZ = (minEl+maxEl)/2.0;

        {
            std::unique_ptr<TerrainTile, void(*)(const TerrainTile *)> tileptr(nullptr, nullptr);
            code = tileAllocator.allocate(tileptr);
            TE_CHECKRETURN_CODE(code);

            value = std::move(tileptr);
        }
        value->data.srid = 4326;
        value->data.localFrame.setToTranslate(localOriginX, localOriginY, localOriginZ);

        const float skirtHeight = 500.0;

        std::size_t numSkirtIndices;
        code = Skirt_getNumOutputIndices(&numSkirtIndices, GL_TRIANGLE_STRIP, numEdgeVertices);
        const std::size_t numIndices = GLTexture2_getNumQuadMeshIndices(numPostsLat - 1u, numPostsLng - 1u)
                               + 2u // degenerate link to skirt
                               + numSkirtIndices;

        const std::size_t skirtOffset = GLTexture2_getNumQuadMeshIndices(numPostsLat - 1u, numPostsLng - 1u);

        const std::size_t ib_size = numIndices * sizeof(uint16_t);
        const std::size_t vb_size = (numPostsLat*numPostsLng * 3u + Skirt_getNumOutputVertices(numEdgeVertices) * 3u) * sizeof(float);

        std::unique_ptr<void, void(*)(const void *)> buf(nullptr, nullptr);
        // allocate the mesh data from the pool
        code = allocator.allocate(buf);

        MemBuffer2 indices(static_cast<uint16_t *>(buf.get()), numIndices);
        code = createHeightmapMeshIndices(indices, edgeIndices_, numPostsLat, numPostsLng);
        TE_CHECKRETURN_CODE(code);

        // duplicate the `edgeIndices` buffer for independent position/limit
        MemBuffer2 edgeIndices(edgeIndices_.get(), edgeIndices_.size());
        edgeIndices.limit(edgeIndices_.limit());
        edgeIndices.position(edgeIndices_.position());

        MemBuffer2 positions(static_cast<uint8_t *>(buf.get())+ib_size, vb_size);
        for (std::size_t postLat = 0u; postLat < numPostsLat; postLat++) {
            // tile row
            for(std::size_t postLng = 0u; postLng < numPostsLng; postLng++) {
                const double lat = mbb.minY+((mbb.maxY-mbb.minY)/(numPostsLat-1))*postLat;
                const double lng = mbb.minX+((mbb.maxX-mbb.minX)/(numPostsLng-1))*postLng;
                const double hae = (!fetchEl || isnan(els[(postLat*numPostsLng)+postLng])) ? 0.0 : els[(postLat*numPostsLng)+postLng];

                const double x = lng-localOriginX;
                const double y = lat-localOriginY;
                const double z = hae-localOriginZ;

                code = positions.put<float>((float)x);
                TE_CHECKBREAK_CODE(code);
                code = positions.put<float>((float)y);
                TE_CHECKBREAK_CODE(code);
                code = positions.put<float>((float)z);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
        positions.flip();

        code = Skirt_createVertices<float, uint16_t>(positions,
                GL_TRIANGLE_STRIP,
                3u*sizeof(float),
                &edgeIndices,
                numEdgeVertices,
                skirtHeight);
        TE_CHECKRETURN_CODE(code);

        MeshPtr terrainMesh(nullptr, nullptr);

        VertexDataLayout layout;
        layout.interleaved = true;
        layout.attributes = TEVA_Position;
        layout.position.offset = 0u;
        layout.position.stride = 12u;
        layout.position.type = TEDT_Float32;
        Envelope2 aabb;
        aabb.minX = mbb.minX - localOriginX;
        aabb.minY = mbb.minY - localOriginY;
        aabb.minZ = (minEl-skirtHeight) - localOriginZ;
        aabb.maxX = mbb.maxX - localOriginX;
        aabb.maxY = mbb.maxY - localOriginY;
        aabb.maxZ = maxEl - localOriginZ;

        // create the mesh
        code = MeshBuilder_buildInterleavedMesh(
            terrainMesh,
            TEDM_TriangleStrip,
            TEWO_Clockwise,
            layout,
            0u,
            nullptr,
            aabb,
            positions.limit() / (3u * sizeof(float)),
            positions.get(),
            TEDT_UInt16,
            indices.limit() / sizeof(uint16_t),
            indices.get(),
            std::move(buf));

        TE_CHECKRETURN_CODE(code);

        value->data.srid = 4326;
        value->data.value = std::move(terrainMesh);
        value->data.localFrame.setToTranslate(localOriginX, localOriginY, localOriginZ);
        value->data.interpolated = true;
        value->skirtIndexOffset = skirtOffset;
        value->aabb_wgs84 = value->data.value->getAABB();
        value->aabb_wgs84.minX += localOriginX;
        value->aabb_wgs84.minY += localOriginY;
        value->aabb_wgs84.minZ += localOriginZ;
        value->aabb_wgs84.maxX += localOriginX;
        value->aabb_wgs84.maxY += localOriginY;
        value->aabb_wgs84.maxZ += localOriginZ;
        value->hasData = hasData;

        value->heightmap = true;
        value->posts_x = numPostsLng;
        value->posts_y = numPostsLat;
        value->invert_y_axis = true;

        if(srid != value->data.srid) {
            VertexDataLayout layout_update = value->data.value->getVertexDataLayout();
            MeshTransformOptions srcOpts;
            srcOpts.srid = value->data.srid;
            srcOpts.localFrame = Matrix2Ptr(&value->data.localFrame, Memory_leaker_const<Matrix2>);
            srcOpts.layout = VertexDataLayoutPtr(&layout_update, Memory_leaker_const<VertexDataLayout>);
            MeshTransformOptions dstOpts;
            dstOpts.srid = srid;

            MeshPtr transformed(nullptr, nullptr);
            MeshTransformOptions transformedOpts;
            code = Mesh_transform(transformed, &transformedOpts, *value->data.value, srcOpts, dstOpts, nullptr);
            TE_CHECKRETURN_CODE(code);

            value->data.value = std::move(transformed);
            value->data.srid = transformedOpts.srid;
            if(transformedOpts.localFrame.get())
                value->data.localFrame.set(*transformedOpts.localFrame);
            else
                value->data.localFrame.setToIdentity();
        }

        // XXX - small downstream "optimization" pending implementation of depth hittest
        if(srid == 4326) {
            ElevationChunk::Data &node = value->data;
            MeshPtr transformed(nullptr, nullptr);
            VertexDataLayout srcLayout(node.value->getVertexDataLayout());
            MeshTransformOptions transformedOpts;
            MeshTransformOptions srcOpts;
            srcOpts.layout = VertexDataLayoutPtr(&srcLayout, Memory_leaker_const<VertexDataLayout>);
            srcOpts.srid = node.srid;
            srcOpts.localFrame = Matrix2Ptr(&node.localFrame, Memory_leaker_const<Matrix2>);
            MeshTransformOptions dstOpts;
            dstOpts.srid = 4978;
            code = Mesh_transform(transformed, &transformedOpts, *node.value, srcOpts, dstOpts, nullptr);
            TE_CHECKRETURN_CODE(code);

            assert(!!transformed);

            value->data_proj.srid = transformedOpts.srid;
            if (transformedOpts.localFrame.get())
                value->data_proj.localFrame = *transformedOpts.localFrame;
            value->data_proj.value = std::move(transformed);
        } else {
            value->data_proj.value.reset();
            value->data_proj.srid = -1;
        }
        value_ = value;
        return code;
    }
    double getHeightMapElevation(const TerrainTile &tile, const double latitude, const double longitude) NOTHROWS
    {
        const Envelope2& aabb_wgs84 = tile.aabb_wgs84;
        // do a heightmap lookup
        const double postSpaceX = (aabb_wgs84.maxX-aabb_wgs84.minX) / ((tile).posts_x-1u);
        const double postSpaceY = (aabb_wgs84.maxY-aabb_wgs84.minY) / ((tile).posts_y-1u);

        const double postX = (longitude-aabb_wgs84.minX)/postSpaceX;
        const double postY = (tile).invert_y_axis ?
            (latitude-aabb_wgs84.minY)/postSpaceY :
            (aabb_wgs84.maxY-latitude)/postSpaceY ;

        const auto postL = static_cast<std::size_t>(MathUtils_clamp((int)postX, 0, (int)((tile).posts_x-1u)));
        const auto postR = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postX), 0, (int)((tile).posts_x-1u)));
        const auto postT = static_cast<std::size_t>(MathUtils_clamp((int)postY, 0, (int)((tile).posts_y-1u)));
        const auto postB = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postY), 0, (int)((tile).posts_y-1u)));

        TAK::Engine::Math::Point2<double> p;

        // obtain the four surrounding posts to interpolate from
        (tile).data.value->getPosition(&p, (postT*(tile).posts_x)+postL);
        const double ul = p.z;
        (tile).data.value->getPosition(&p, (postT*(tile).posts_x)+postR);
        const double ur = p.z;
        (tile).data.value->getPosition(&p, (postB*(tile).posts_x)+postR);
        const double lr = p.z;
        (tile).data.value->getPosition(&p, (postB*(tile).posts_x)+postL);
        const double ll = p.z;

        // interpolate the height
        p.z = MathUtils_interpolate(ul, ur, lr, ll,
                MathUtils_clamp(postX-(double)postL, 0.0, 1.0),
                MathUtils_clamp(postY-(double)postT, 0.0, 1.0));
        // transform the height back to HAE
        (tile).data.localFrame.transform(&p, p);
        return p.z;
    }
    TAKErr derive(std::shared_ptr<TerrainTile>& value_, const Envelope2& mbb, const int srid, const std::size_t numPostsLat, const std::size_t numPostsLng, const MemBuffer2& edgeIndices_, const TerrainTile& deriveFrom, BlockPoolAllocator& allocator, PoolAllocator<TerrainTile>& tileAllocator) NOTHROWS
    {
        TAKErr code(TE_Ok);
        std::shared_ptr<TerrainTile> value;

        // number of edge vertices is equal to perimeter length, plus one, to
        // close the linestring
        const std::size_t numEdgeVertices = ((numPostsLat-1u)*2u)+((numPostsLng-1u)*2u) + 1u;

        double localOriginX = (mbb.minX+mbb.maxX)/2.0;
        double localOriginY = (mbb.minY+mbb.maxY)/2.0;
        double localOriginZ = (deriveFrom.aabb_wgs84.minZ+deriveFrom.aabb_wgs84.maxZ)/2.0;

        {
            std::unique_ptr<TerrainTile, void(*)(const TerrainTile *)> tileptr(nullptr, nullptr);
            code = tileAllocator.allocate(tileptr);
            TE_CHECKRETURN_CODE(code);

            value = std::move(tileptr);
        }
        value->data.srid = 4326;
        value->data.localFrame.setToTranslate(localOriginX, localOriginY, localOriginZ);

        const float skirtHeight = 500.0;

        std::size_t numSkirtIndices;
        code = Skirt_getNumOutputIndices(&numSkirtIndices, GL_TRIANGLE_STRIP, numEdgeVertices);
        const std::size_t numIndices = GLTexture2_getNumQuadMeshIndices(numPostsLat - 1u, numPostsLng - 1u)
                               + 2u // degenerate link to skirt
                               + numSkirtIndices;

        const std::size_t skirtOffset = GLTexture2_getNumQuadMeshIndices(numPostsLat - 1u, numPostsLng - 1u);

        const std::size_t ib_size = numIndices * sizeof(uint16_t);
        const std::size_t vb_size = (numPostsLat*numPostsLng * 3u + Skirt_getNumOutputVertices(numEdgeVertices) * 3u) * sizeof(float);

        std::unique_ptr<void, void(*)(const void *)> buf(nullptr, nullptr);
        // allocate the mesh data from the pool
        code = allocator.allocate(buf);

        MemBuffer2 indices(static_cast<uint16_t *>(buf.get()), numIndices);
        code = createHeightmapMeshIndices(indices, edgeIndices_, numPostsLat, numPostsLng);
        TE_CHECKRETURN_CODE(code);

        // duplicate the `edgeIndices` buffer for independent position/limit
        MemBuffer2 edgeIndices(edgeIndices_.get(), edgeIndices_.size());
        edgeIndices.limit(edgeIndices_.limit());
        edgeIndices.position(edgeIndices_.position());

        double minEl = deriveFrom.hasData ? getHeightMapElevation(deriveFrom, mbb.minY, mbb.minX) : 0.0;
        double maxEl = deriveFrom.hasData ? getHeightMapElevation(deriveFrom, mbb.minY, mbb.minX) : 0.0;

        MemBuffer2 positions(static_cast<uint8_t *>(buf.get())+ib_size, vb_size);
        for (std::size_t postLat = 0u; postLat < numPostsLat; postLat++) {
            // tile row
            for(std::size_t postLng = 0u; postLng < numPostsLng; postLng++) {
                const double lat = mbb.minY+((mbb.maxY-mbb.minY)/(numPostsLat-1))*postLat;
                const double lng = mbb.minX+((mbb.maxX-mbb.minX)/(numPostsLng-1))*postLng;
                const double hae = deriveFrom.hasData ? getHeightMapElevation(deriveFrom, lat, lng) : 0.0;

                const double x = lng-localOriginX;
                const double y = lat-localOriginY;
                const double z = hae-localOriginZ;

                code = positions.put<float>((float)x);
                TE_CHECKBREAK_CODE(code);
                code = positions.put<float>((float)y);
                TE_CHECKBREAK_CODE(code);
                code = positions.put<float>((float)z);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
        positions.flip();

        code = Skirt_createVertices<float, uint16_t>(positions,
                GL_TRIANGLE_STRIP,
                3u*sizeof(float),
                &edgeIndices,
                numEdgeVertices,
                skirtHeight);
        TE_CHECKRETURN_CODE(code);

        MeshPtr terrainMesh(nullptr, nullptr);

        VertexDataLayout layout;
        layout.interleaved = true;
        layout.attributes = TEVA_Position;
        layout.position.offset = 0u;
        layout.position.stride = 12u;
        layout.position.type = TEDT_Float32;
        Envelope2 aabb;
        aabb.minX = mbb.minX - localOriginX;
        aabb.minY = mbb.minY - localOriginY;
        aabb.minZ = (minEl-skirtHeight) - localOriginZ;
        aabb.maxX = mbb.maxX - localOriginX;
        aabb.maxY = mbb.maxY - localOriginY;
        aabb.maxZ = maxEl - localOriginZ;

        // create the mesh
        code = MeshBuilder_buildInterleavedMesh(
            terrainMesh,
            TEDM_TriangleStrip,
            TEWO_Clockwise,
            layout,
            0u,
            nullptr,
            aabb,
            positions.limit() / (3u * sizeof(float)),
            positions.get(),
            TEDT_UInt16,
            indices.limit() / sizeof(uint16_t),
            indices.get(),
            std::move(buf));

        TE_CHECKRETURN_CODE(code);

        value->data.srid = 4326;
        value->data.value = std::move(terrainMesh);
        value->data.localFrame.setToTranslate(localOriginX, localOriginY, localOriginZ);
        value->data.interpolated = true;
        value->skirtIndexOffset = skirtOffset;
        value->aabb_wgs84 = value->data.value->getAABB();
        value->aabb_wgs84.minX += localOriginX;
        value->aabb_wgs84.minY += localOriginY;
        value->aabb_wgs84.minZ += localOriginZ;
        value->aabb_wgs84.maxX += localOriginX;
        value->aabb_wgs84.maxY += localOriginY;
        value->aabb_wgs84.maxZ += localOriginZ;
        value->hasData = deriveFrom.hasData;

        value->heightmap = true;
        value->posts_x = numPostsLng;
        value->posts_y = numPostsLat;
        value->invert_y_axis = true;

        if(srid != value->data.srid) {
            VertexDataLayout layout_update = value->data.value->getVertexDataLayout();
            MeshTransformOptions srcOpts;
            srcOpts.srid = value->data.srid;
            srcOpts.localFrame = Matrix2Ptr(&value->data.localFrame, Memory_leaker_const<Matrix2>);
            srcOpts.layout = VertexDataLayoutPtr(&layout_update, Memory_leaker_const<VertexDataLayout>);
            MeshTransformOptions dstOpts;
            dstOpts.srid = srid;

            MeshPtr transformed(nullptr, nullptr);
            MeshTransformOptions transformedOpts;
            code = Mesh_transform(transformed, &transformedOpts, *value->data.value, srcOpts, dstOpts, nullptr);
            TE_CHECKRETURN_CODE(code);

            value->data.value = std::move(transformed);
            value->data.srid = transformedOpts.srid;
            if(transformedOpts.localFrame.get())
                value->data.localFrame.set(*transformedOpts.localFrame);
            else
                value->data.localFrame.setToIdentity();
        }

        // XXX - small downstream "optimization" pending implementation of depth hittest
        if(srid == 4326) {
            ElevationChunk::Data &node = value->data;
            MeshPtr transformed(nullptr, nullptr);
            VertexDataLayout srcLayout(node.value->getVertexDataLayout());
            MeshTransformOptions transformedOpts;
            MeshTransformOptions srcOpts;
            srcOpts.layout = VertexDataLayoutPtr(&srcLayout, Memory_leaker_const<VertexDataLayout>);
            srcOpts.srid = node.srid;
            srcOpts.localFrame = Matrix2Ptr(&node.localFrame, Memory_leaker_const<Matrix2>);
            MeshTransformOptions dstOpts;
            dstOpts.srid = 4978;
            code = Mesh_transform(transformed, &transformedOpts, *node.value, srcOpts, dstOpts, nullptr);
            TE_CHECKRETURN_CODE(code);

            assert(!!transformed);

            value->data_proj.srid = transformedOpts.srid;
            if (transformedOpts.localFrame.get())
                value->data_proj.localFrame = *transformedOpts.localFrame;
            value->data_proj.value = std::move(transformed);
        } else {
            value->data_proj.value.reset();
            value->data_proj.srid = -1;
        }

        value_ = value;
        return code;
    }

    bool intersects(const Frustum2& frustum, const TAK::Engine::Feature::Envelope2& aabbWCS, const int srid, const double lng) NOTHROWS
    {
        return (frustum.intersects(AABB(TAK::Engine::Math::Point2<double>(aabbWCS.minX, aabbWCS.minY, aabbWCS.minZ), TAK::Engine::Math::Point2<double>(aabbWCS.maxX, aabbWCS.maxY, aabbWCS.maxZ))) ||
            ((srid == 4326) && lng * ((aabbWCS.minX + aabbWCS.maxX) / 2.0) < 0 &&
                frustum.intersects(
                    AABB(TAK::Engine::Math::Point2<double>(aabbWCS.minX - (360.0 * sgn((aabbWCS.minX + aabbWCS.maxX) / 2.0)), aabbWCS.minY, aabbWCS.minZ),
                        TAK::Engine::Math::Point2<double>(aabbWCS.maxX - (360.0 * sgn((aabbWCS.minX + aabbWCS.maxX) / 2.0)), aabbWCS.maxY, aabbWCS.maxZ)))));
    }

    double estimateResolution(const GeoPoint2 &focus, const MapSceneModel2 &cmodel, const Envelope2 &bounds, GeoPoint2 *pclosest) NOTHROWS
    {
        if(cmodel.camera.mode == MapCamera2::Perspective) {
            Matrix2 m(cmodel.camera.projection);
            m.concatenate(cmodel.camera.modelView);
            Frustum2 frustum(m);
            // compute AABB in WCS and check for intersection with the frustum
            TAK::Engine::Feature::Envelope2 aabbWCS(bounds);
            const int srid = cmodel.projection->getSpatialReferenceID();
            TAK::Engine::Feature::GeometryTransformer_transform(&aabbWCS, aabbWCS, 4326, srid);
            if (!intersects(frustum, aabbWCS, srid, focus.longitude))
                return atakmap::raster::osm::OSMUtils::mapnikTileResolution(0);

            const TAK::Engine::Math::Point2<double> c((aabbWCS.minX + aabbWCS.maxX) / 2.0,
                                                      (aabbWCS.minY + aabbWCS.maxY) / 2.0,
                                                      (aabbWCS.minZ + aabbWCS.maxZ) / 2.0);
            auto r = Vector2_subtract(TAK::Engine::Math::Point2<double>(aabbWCS.maxX, aabbWCS.maxY, aabbWCS.maxZ), c);
            r.x *= cmodel.displayModel->projectionXToNominalMeters;
            r.y *= cmodel.displayModel->projectionYToNominalMeters;
            r.z *= cmodel.displayModel->projectionZToNominalMeters;

            // compute line-of-sight from camera to tile
            auto los = Vector2_subtract(c, cmodel.camera.location);
            if(srid == 4326 && fabs(los.x) > 180.0) {
                if(los.x > 180.0)       los.x -= 360.0;
                else if(los.x < -180.0) los.x += 360.0;
            }
            los.x *= cmodel.displayModel->projectionXToNominalMeters;
            los.y *= cmodel.displayModel->projectionYToNominalMeters;
            los.z *= cmodel.displayModel->projectionZToNominalMeters;

            double gsd = cmodel.gsd;
            if(!isnan(cmodel.camera.agl)) {
                const double aglgsd = MapSceneModel2_gsd(cmodel.camera.agl, cmodel.camera.fov, cmodel.height);
                gsd = std::min(cmodel.gsd, aglgsd*2.0);
            }
            const double range = MapSceneModel2_range(gsd, cmodel.camera.fov, cmodel.height);
            const double losd = Vector2_length(los);
            if (losd <= (Vector2_length(r)+range))
                return gsd;

            // compute view vector
            auto vv = Vector2_subtract(cmodel.camera.target, cmodel.camera.location);
            if(srid == 4326 && fabs(vv.x) > 180.0) {
                if(vv.x > 180.0)       vv.x -= 360.0;
                else if(vv.x < -180.0) vv.x += 360.0;
            }
            vv.x *= cmodel.displayModel->projectionXToNominalMeters;
            vv.y *= cmodel.displayModel->projectionYToNominalMeters;
            vv.z *= cmodel.displayModel->projectionZToNominalMeters;

            // check if tile is behind camera
            if (Vector2_dot(vv, los) < 0.0)
                return atakmap::raster::osm::OSMUtils::mapnikTileResolution(0);

            const double gsdAtCentroid = MapSceneModel2_gsd(Vector2_length(los), cmodel.camera.fov, cmodel.height) * 8.0;
            return gsdAtCentroid;
        } else {
            const TAK::Engine::Core::GeoPoint2 tgt(focus);
            if (atakmap::math::Rectangle<double>::contains(bounds.minX, bounds.minY, bounds.maxX,
                                                           bounds.maxY, tgt.longitude,
                                                           tgt.latitude))
                return cmodel.gsd;

#ifdef __ANDROID__
            const double sceneHalfWidth = cmodel.width / 2.0;
            const double sceneHalfHeight = cmodel.height / 2.0;
#else
            const double sceneHalfWidth = (double)cmodel.width / 2.0;
            const double sceneHalfHeight = (double)cmodel.height / 2.0;
#endif
            const double sceneRadius = cmodel.gsd * sqrt((sceneHalfWidth * sceneHalfWidth) +
                                                         (sceneHalfHeight * sceneHalfHeight));

            const TAK::Engine::Core::GeoPoint2 aoiCentroid((bounds.minY + bounds.maxY) / 2.0,
                                                           (bounds.minX + bounds.maxX) / 2.0);
            double aoiRadius;
            {
                const double uld = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid,
                                                                         TAK::Engine::Core::GeoPoint2(
                                                                                 bounds.maxY,
                                                                                 bounds.minX),
                                                                         true);
                const double lrd = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid,
                                                                         TAK::Engine::Core::GeoPoint2(
                                                                                 bounds.minY,
                                                                                 bounds.maxX),
                                                                         true);
                aoiRadius = std::max(uld, lrd);
            }
            const double d = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, tgt, true);
            if ((d - aoiRadius) < sceneRadius)
                return cmodel.gsd;
            // observation is that the value returned here really does not matter
            return cmodel.gsd * pow(2.0, 1.0 + log((d - aoiRadius) - sceneRadius) / log(2.0));
        }
    }

    TAKErr subscribeOnContentChangedListener(void *opaque, ElevationSource &src) NOTHROWS
    {
        auto *arg = static_cast<SubscribeOnContentChangedListenerBundle *>(opaque);
        LockPtr lock(nullptr, nullptr);
        if(arg->mutex)
            Lock_create(lock, *arg->mutex);
        src.addOnContentChangedListener(arg->listener);
        return TE_Ok;
    }
}
