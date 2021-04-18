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
#define MAX_LEVEL 21.0

namespace
{
    //https://stackoverflow.com/questions/1903954/is-there-a-standard-sign-function-signum-sgn-in-c-c
    template <typename T>
    int sgn(T val)
    {
        return (T(0) < val) - (val < T(0));
    }

    double estimateResolution(const GeoPoint2 &focus, const MapSceneModel2 &cmodel, const Envelope2 &bounds, GeoPoint2 *pclosest) NOTHROWS;
    double clamp(double v, double a, double b) NOTHROWS
    {
        if (v < a) return a;
        else if (v > b) return b;
        else return v;
    }
    std::size_t computeTargetLevelImpl(const GeoPoint2 &focus, const MapSceneModel2 &view, const Envelope2 &bounds, const double adj) NOTHROWS
    {
        if (std::min(fabs(bounds.minY), fabs(bounds.maxY)) > 84.0)
            return 0u;
        const double gsd = estimateResolution(focus, view, bounds, nullptr) * adj;
        auto level = (std::size_t)clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(gsd, 0.0), 0.0, MAX_LEVEL);

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
                    const auto nlevel = (std::size_t)clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(top_gsd, 0.0), 0.0, MAX_LEVEL);
                    if (nlevel > level)
                        level = nlevel - 1u;
                }
            }
            // center, skipping self
            for (int i = -1; i <= 1; i += 2) {
                const double minX = bounds.minX + (i*dlng);
                Envelope2 nbounds(minX, bounds.minY, minX+dlng, bounds.maxY);
                const double center_gsd = estimateResolution(focus, view, bounds, nullptr) * 8.0;
                const auto nlevel = (std::size_t)clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(center_gsd, 0.0), 0.0, MAX_LEVEL);
                if (nlevel > level)
                    level = nlevel - 1u;
            }
            if (bottom) {
                double maxY = bounds.maxY - dlat;
                for (int i = -1; i <= 1; i++) {
                    const double minX = bounds.minX + (i*dlng);
                    Envelope2 nbounds(minX, maxY-dlat, minX+dlng, maxY);
                    const double bottom_gsd = estimateResolution(focus, view, bounds, nullptr) * 8.0;
                    const auto nlevel = (std::size_t)clamp(atakmap::raster::osm::OSMUtils::mapnikTileLeveld(bottom_gsd, 0.0), 0.0, 16.0);
                    if (nlevel > level)
                        level = nlevel - 1u;
                }
            }
        }

        return level;
    }
    void computePostCount(std::size_t &numPostsLat,  std::size_t &numPostsLng, const Envelope2 &bounds, const std::size_t defaultPostCount) NOTHROWS
    {
        numPostsLat = defaultPostCount;
#if 0
        // XXX - not working quite right
        double adj = 1.0;
        if (std::min(fabs(bounds.minY), fabs(bounds.maxY)) > 84.0)
            adj = 0.125;
        numPostsLng = std::max(static_cast<std::size_t>(ceil((double)defaultPostCount*adj)), static_cast<std::size_t>(2u));
#else
        numPostsLng = defaultPostCount;
#endif
    }
    std::size_t getNumEdgeVertices(const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
        // number of edge vertices is equal to perimeter length, plus one, to
        // close the linestring
        return ((numPostsLat-1u)*2u)+((numPostsLng-1u)*2u) + 1u;
    }
    TAKErr createEdgeIndices(MemBuffer2 &edgeIndices, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
    {
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
    TAKErr createHeightmapMeshIndices(MemBuffer2 &indices, const std::size_t numPostsLat, const std::size_t numPostsLng) NOTHROWS
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

        // to achieve CW winding order, edge indices need to be specified
        // in CCW order
        MemBuffer2 edgeIndices(numEdgeVertices*sizeof(uint16_t));
        code = createEdgeIndices(edgeIndices, numPostsLat, numPostsLng);
        TE_CHECKRETURN_CODE(code);

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

    TAKErr estimateFocusPoint(GeoPoint2 *value, const MapSceneModel2 &scene, const std::vector<std::shared_ptr<const TerrainTile>> &tiles) NOTHROWS;

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
    QuadNode(ElMgrTerrainRenderService &service, QuadNode *parent, int srid, double minX, double minY, double maxX, double maxY) NOTHROWS;
public :
    static bool needsFetch(const QuadNode *node, const int srid, const int sourceVersion) NOTHROWS;
    bool collect(ElMgrTerrainRenderService::WorldTerrain &value, const GeoPoint2 &focus, const MapSceneModel2 &view) NOTHROWS;
    void reset(const bool data) NOTHROWS;
public :
    static void updateParentZBounds(QuadNode &node) NOTHROWS;
public :
    ElMgrTerrainRenderService &service;
    mutable Mutex mutex;

    std::shared_ptr<QuadNode> ul;
    std::shared_ptr<QuadNode> ur;
    std::shared_ptr<QuadNode> lr;
    std::shared_ptr<QuadNode> ll;

    Envelope2 bounds;
    std::size_t level;

    std::size_t lastRequestLevel;

    std::shared_ptr<TerrainTile> tile;
    bool queued;

    int sourceVersion {-1};
    int srid {-1};

    QuadNode *parent;
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
    double estimateResolution(const GeoPoint2 &focus, const MapSceneModel2 &scene, const double ullat, const double ullng, const double lrlat, const double lrlng, GeoPoint2 *closest) NOTHROWS;
    TAKErr subscribeOnContentChangedListener(void *opaque, ElevationSource &src) NOTHROWS;
}

ElMgrTerrainRenderService::ElMgrTerrainRenderService(RenderContext &renderer_) NOTHROWS :
    renderer(renderer_),
    fetchWorker(nullptr, nullptr),
    requestWorker(nullptr, nullptr),
    nodeCount(0u),
    terrainVersion(1),
    reset(false),
    numPosts(32),
    fetchResolutionAdjustment(10.0),
    nodeSelectResolutionAdjustment(8.0),
    monitor(TEMT_Recursive),
    sticky(true),
    sourceVersion(0),
    terminate(false),
    meshAllocator(getTerrainMeshSize(numPosts), 512),
    tileAllocator(256)
{
    east.reset(new QuadNode(*this, nullptr, -1, 0.0, -90.0, 180.0, 90.0));
    west.reset(new QuadNode(*this, nullptr, -1, -180.0, -90.0, 0.0, 90.0));

    // east
    roots[0u] = new QuadNode(*this, east.get(), east->srid, 0.0, 0, 90.0, 90.0);
    roots[1u] = new QuadNode(*this, east.get(), east->srid, 90.0, 0, 180.0, 90.0);
    roots[2u] = new QuadNode(*this, east.get(), east->srid, 90.0, -90.0, 180.0, 0.0);
    roots[3u] = new QuadNode(*this, east.get(), east->srid, 0.0, -90.0, 90.0, 0.0);
    // west
    roots[4u] = new QuadNode(*this, west.get(), west->srid, -180.0, 0, -90.0, 90.0);
    roots[5u] = new QuadNode(*this, west.get(), west->srid, -90.0, 0, 0.0, 90.0);
    roots[6u] = new QuadNode(*this, west.get(), west->srid, -90.0, -90.0, 0.0, 0.0);
    roots[7u] = new QuadNode(*this, west.get(), west->srid, -180.0, -90.0, -90.0, 0.0);

    worldTerrain.reset(new WorldTerrain());
    worldTerrain->sourceVersion = -1;
    worldTerrain->srid = -1;
    worldTerrain->terrainVersion = -1;
    worldTerrain->sceneVersion = -1;

    sourceRefresh.reset(new SourceRefresh(*this));

    request.srid = -1;
    request.sceneVersion = -1;

    const bool hiresMode = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 0);

    // default to legacy values if not using high-res mode
    if(hiresMode) {
        fetchResolutionAdjustment = 2.0;
#ifdef __ANDROID__
        nodeSelectResolutionAdjustment = 4.0;
#else
        nodeSelectResolutionAdjustment = 2.0;
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
}

ElMgrTerrainRenderService::~ElMgrTerrainRenderService() NOTHROWS
{
    stop();

    // release `roots`
    for(std::size_t i = 0u; i < 8u; i++) {
        delete roots[i];
        roots[i] = nullptr;
    }
}

//public synchronized void lock(GLMapView view, Collection<GLMapView.TerrainTile> tiles) {
TAKErr ElMgrTerrainRenderService::lock(TAK::Engine::Port::Collection<std::shared_ptr<const TerrainTile>> &value, const MapSceneModel2 &view, const int srid, const int sceneVersion) NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock lock(monitor);
    TE_CHECKRETURN_CODE(lock.status);

    request.srid = srid;
    request.sceneVersion = sceneVersion;
    request.scene = view;

    // data is invalid if SRID changed
    bool invalid = (worldTerrain->srid != srid);
    // if `front` is empty or content invalid repopulate with "root" tiles
    // temporarily until new data is fetched
    if(worldTerrain->tiles.empty() || invalid) {
        // drain the `front` buffer
        worldTerrain->tiles.clear();

        for(std::size_t i = 0; i < 8u; i++) {
            std::shared_ptr<TerrainTile> tile;
            std::size_t numPostsLat;
            std::size_t numPostsLng;
            computePostCount(numPostsLat, numPostsLng, roots[i]->bounds, numPosts);

            const std::size_t numEdgeVertices = getNumEdgeVertices(numPostsLat, numPostsLng);
            MemBuffer2 edgeIndices(numEdgeVertices*sizeof(uint16_t));
            code = createEdgeIndices(edgeIndices, numPostsLat, numPostsLng);
            TE_CHECKRETURN_CODE(code);

            code = fetch(tile,
                         nullptr,
                         atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(roots[i]->level))*fetchResolutionAdjustment,
                         roots[i]->bounds,
                         srid,
                         numPostsLat,
                         numPostsLng,
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
    if(invalid || request.sceneVersion != worldTerrain->sceneVersion || terrainVersion != worldTerrain->terrainVersion) {
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
    return terrainVersion;
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
        // signal to worker thread that we are terminating
        terminate = true;
        mlock.broadcast();
    }

    // wait for the worker thread to die
    fetchWorker.reset();
    requestWorker.reset();

    return TE_Ok;
}

//synchronized void enqueue(QuadNode node)
TAKErr ElMgrTerrainRenderService::enqueue(const std::shared_ptr<QuadNode> &node) NOTHROWS
{
    TAKErr code(TE_Ok);
    Monitor::Lock mlock(monitor);
    TE_CHECKRETURN_CODE(mlock.status);

    if (!fetchWorker.get()) {
        //ThreadCreateParams params;
        //params.name = "ElMgrTerrainRenderServiceBackgroundWorker-fetch-thread";
        //params.priority = TETP_Normal;
        code = ThreadPool_create(fetchWorker, NUM_TILE_FETCH_WORKERS, fetchWorkerThread, this);
        TE_CHECKRETURN_CODE(code);
    }

    if(node->queued)
        return code;
    node->queued = true;
    queue.push_back(node);
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

                // flip was done
                flip = false;

                // clear back buffer for the next fetch
                fetchBuffer->tiles.clear();

                // request refresh
                owner.renderer.requestRefresh();
            }

            // if scene is unchanged and no new terrain, wait
            if(owner.request.sceneVersion == owner.worldTerrain->sceneVersion &&
               owner.terrainVersion == owner.worldTerrain->terrainVersion) {

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
                    std::unique_ptr<QuadNode> disposing(owner.roots[i]);
                    owner.roots[i] = new QuadNode(owner, disposing->parent, disposing->parent->srid, disposing->bounds.minX, disposing->bounds.minY, disposing->bounds.maxX, disposing->bounds.maxY);
                }

                owner.queue.clear();
                reset = false;
            }

            flip = true;
            fetchBuffer->srid = owner.request.srid;
            fetchBuffer->sourceVersion = owner.sourceVersion;
            fetchBuffer->terrainVersion = owner.terrainVersion;
            fetchBuffer->sceneVersion = owner.request.sceneVersion;
            fetchBuffer->tiles = owner.worldTerrain->tiles;
        }

        // XXX - compute focus point based on scene and intersect with 'front' 
        GeoPoint2 focus;
        if (estimateFocusPoint(&focus, fetch.scene, fetchBuffer->tiles) != TE_Ok)
            fetch.scene.projection->inverse(&focus, fetch.scene.camera.target);

        // clear the tiles in preparation for fetch
        fetchBuffer->tiles.clear();

        for(std::size_t i = 0; i < 8u; i++) {
            bool isect = false;
            MapSceneModel2_intersects(&isect, fetch.scene, owner.roots[i]->bounds.minX, owner.roots[i]->bounds.minY, owner.roots[i]->bounds.minZ, owner.roots[i]->bounds.maxX, owner.roots[i]->bounds.maxY, owner.roots[i]->bounds.maxZ);
            if(!isect && fetch.scene.projection->getSpatialReferenceID() == 4326) {
                // check IDL crossing
                double shift;
                bool isectW;
                bool isectE;

                shift = -360.0;
                MapSceneModel2_intersects(&isectW, fetch.scene, owner.roots[i]->bounds.minX+shift, owner.roots[i]->bounds.minY, owner.roots[i]->bounds.minZ, owner.roots[i]->bounds.maxX+shift, owner.roots[i]->bounds.maxY, owner.roots[i]->bounds.maxZ);
                shift = 360.0;
                MapSceneModel2_intersects(&isectE, fetch.scene, owner.roots[i]->bounds.minX+shift, owner.roots[i]->bounds.minY, owner.roots[i]->bounds.minZ, owner.roots[i]->bounds.maxX+shift, owner.roots[i]->bounds.maxY, owner.roots[i]->bounds.maxZ);

                isect = (isectE||isectW);
            }
            if(isect) {
                owner.roots[i]->collect(*fetchBuffer, focus, fetch.scene);
            } else {
                // no intersection
                if(!owner.roots[i]->tile.get()) {
                    // there's no data, grab an empty tile
                    std::size_t numPostsLat;
                    std::size_t numPostsLng;
                    computePostCount(numPostsLat, numPostsLng, owner.roots[i]->bounds, owner.numPosts);
                    const std::size_t numEdgeVertices = getNumEdgeVertices(numPostsLat, numPostsLng);
                    MemBuffer2 edgeIndices(numEdgeVertices*sizeof(uint16_t));
                    createEdgeIndices(edgeIndices, numPostsLat, numPostsLng);
                    ::fetch(owner.roots[i]->tile,
                            nullptr,
                            atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(owner.roots[i]->level))*owner.fetchResolutionAdjustment,
                            owner.roots[i]->bounds,
                            fetchBuffer->srid,
                            numPostsLat,
                            numPostsLng,
                            edgeIndices,
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

    struct
    {
        std::size_t numPostsLat;
        std::size_t numPostsLng;
        std::unique_ptr<MemBuffer2> data;
    } edgeIndices;
    edgeIndices.numPostsLat = service.numPosts;
    edgeIndices.numPostsLng = service.numPosts;
    edgeIndices.data.reset(new MemBuffer2(getNumEdgeVertices(edgeIndices.numPostsLat, edgeIndices.numPostsLng)*sizeof(uint16_t)));
    createEdgeIndices(*edgeIndices.data, edgeIndices.numPostsLat, edgeIndices.numPostsLng);

    std::shared_ptr<QuadNode> node;
    std::shared_ptr<TerrainTile> tile;
    std::size_t fetchedNodes = 0;
    int fetchSrcVersion = ~service.sourceVersion;
    array_ptr<double> els;
    std::size_t elscap = 0u;
    while(true) {
        //synchronized(ElMgrTerrainRenderService.this)
        {
            TAKErr code(TE_Ok);
            Monitor::Lock mlock(service.monitor);
            code = mlock.status;

            if (service.terminate) {
                break;
            }

            if(node.get()) {
                service.terrainVersion++;

                {
                    Lock nlock(node->mutex);
                    node->queued = false;
                    node->sourceVersion = fetchSrcVersion;
                    node->tile = tile;

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

                service.renderer.requestRefresh();
            }

            if(service.queue.empty()) {
                code = mlock.wait();
                if (code == TE_Interrupted)
                    code = TE_Ok;
                TE_CHECKBREAK_CODE(code);

                continue;
            }

            std::list<std::shared_ptr<QuadNode>>::iterator head;
            head = service.queue.begin();
            node = *head;
            service.queue.erase(head);

            Envelope2 &testBounds = node->parent ? node->parent->bounds : node->bounds;

            bool isect = false;
            MapSceneModel2_intersects(&isect, service.request.scene, testBounds.minX, testBounds.minY, testBounds.minZ, testBounds.maxX, testBounds.maxY, testBounds.maxZ);
            if(!isect && service.request.scene.projection->getSpatialReferenceID() == 4326) {
                // check IDL crossing
                double shift;
                bool isectW;
                bool isectE;

                shift = -360.0;
                MapSceneModel2_intersects(&isectW, service.request.scene, testBounds.minX, testBounds.minY, testBounds.minZ, testBounds.maxX, testBounds.maxY, testBounds.maxZ);
                shift = 360.0;
                MapSceneModel2_intersects(&isectE, service.request.scene, testBounds.minX, testBounds.minY, testBounds.minZ, testBounds.maxX, testBounds.maxY, testBounds.maxZ);

                isect = (isectE||isectW);
            }
            if(!isect) {
                node->queued = false;
                node.reset();
                continue;
            }

            fetchSrcVersion = service.sourceVersion;
        }

        const double res = atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(node->level))*service.fetchResolutionAdjustment;
        std::size_t numPostsLat;
        std::size_t numPostsLng;
        computePostCount(numPostsLat, numPostsLng, node->bounds, service.numPosts);
        if(elscap < (numPostsLat*numPostsLng*3u)) {
            els.reset(new double[numPostsLat * numPostsLng * 3u]);
            elscap = (numPostsLat*numPostsLng * 3u);
        }
        if (numPostsLat != edgeIndices.numPostsLat || numPostsLng != edgeIndices.numPostsLng) {
            edgeIndices.numPostsLat = numPostsLat;
            edgeIndices.numPostsLng = numPostsLng;
            const std::size_t size = getNumEdgeVertices(edgeIndices.numPostsLat, edgeIndices.numPostsLng) * sizeof(uint16_t);
            if(edgeIndices.data->size() < size)
                edgeIndices.data.reset(new MemBuffer2(size));
            edgeIndices.data->reset();
            edgeIndices.data->limit(size);
            createEdgeIndices(*edgeIndices.data, edgeIndices.numPostsLat, edgeIndices.numPostsLng);
        }
        TAKErr code = fetch(tile, els.get(), res, node->bounds, node->srid, numPostsLat, numPostsLng, *edgeIndices.data, node->level >= TERRAIN_LEVEL, service.fetchOptions.legacyElevationApi, service.fetchOptions.constrainQueryRes, service.fetchOptions.fillWithHiRes, service.meshAllocator, service.tileAllocator);
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
    service.sourceVersion++;
    service.terrainVersion++;

    service.renderer.requestRefresh();
    return TE_Ok;
}
TAKErr ElMgrTerrainRenderService::SourceRefresh::onSourceAttached(const std::shared_ptr<ElevationSource> &src) NOTHROWS
{
    service.sourceVersion++;
    service.terrainVersion++;

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

    service.sourceVersion++;
    service.terrainVersion++;

    service.renderer.requestRefresh();
    return TE_Ok;
}

ElMgrTerrainRenderService::QuadNode::QuadNode(ElMgrTerrainRenderService &service_, QuadNode *parent_, int srid_, double minX, double minY, double maxX, double maxY) NOTHROWS :
    service(service_),
    parent(parent_),
    bounds(minX, minY, -900.0, maxX, maxY, 19000.0),
    level(parent_ ? parent_->level+1u : 0u),
    lastRequestLevel(0u),
    queued(false),
    srid(srid_)
{
#if 0
    nodeCount++;

    Log.i("ElMgrTerrainRenderService", "Create node " + nodeCount);
#endif
}

bool ElMgrTerrainRenderService::QuadNode::needsFetch(const QuadNode *node, const int srid, const int sourceVersion) NOTHROWS
{
    if (!node)
        return true;
    Lock lock(node->mutex);
    return
        (node->sourceVersion != sourceVersion) ||
        (!node->queued &&
        (!node->tile.get() || node->tile->data.srid != srid));
}

bool ElMgrTerrainRenderService::QuadNode::collect(ElMgrTerrainRenderService::WorldTerrain &value, const GeoPoint2 &focus, const MapSceneModel2 &scene) NOTHROWS
{
    const std::size_t target_level = computeTargetLevelImpl(
        focus,
        scene,
        this->bounds,
        service.nodeSelectResolutionAdjustment);
    if(target_level > this->level) {
        const double centerX = (this->bounds.minX+this->bounds.maxX)/2.0;
        const double centerY = (this->bounds.minY+this->bounds.maxY)/2.0;

        // compute child intersections
#if 0
        bool recurseLL = false;
        MapSceneModel2_intersects(&recurseLL,
                scene,
                bounds.minX, bounds.minY, bounds.minZ, centerX, centerY, bounds.maxZ);
        bool recurseLR = false;
        MapSceneModel2_intersects(&recurseLR,
                scene,
                centerX, bounds.minY, bounds.minZ, bounds.maxX, centerY, bounds.maxZ);
        bool recurseUR = false;
        MapSceneModel2_intersects(&recurseUR,
                scene,
                centerX, centerY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        bool recurseUL;
        MapSceneModel2_intersects(&recurseUL,
                scene,
                bounds.minX, centerY, bounds.minZ, centerX, bounds.maxY, bounds.maxZ);
#else
        bool recurseLL = 
        computeTargetLevelImpl(
                focus,
                scene,
                Envelope2(bounds.minX, bounds.minY, bounds.minZ, centerX, centerY, bounds.maxZ),
                service.nodeSelectResolutionAdjustment) >= (this->level+1u);
        bool recurseLR = 
        computeTargetLevelImpl(
                focus,
                scene,
                Envelope2(centerX, bounds.minY, bounds.minZ, bounds.maxX, centerY, bounds.maxZ),
                service.nodeSelectResolutionAdjustment) >= (this->level+1u);
        bool recurseUR = 
        computeTargetLevelImpl(
                focus,
                scene,
                Envelope2(centerX, centerY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ),
                service.nodeSelectResolutionAdjustment) >= (this->level+1u);
        bool recurseUL =
        computeTargetLevelImpl(
                focus,
                scene,
                Envelope2(bounds.minX, centerY, bounds.minZ, centerX, bounds.maxY, bounds.maxZ),
                service.nodeSelectResolutionAdjustment) >= (this->level+1u);
#endif
        const bool fetchul = needsFetch(ul.get(), value.srid, value.sourceVersion);
        const bool fetchur = needsFetch(ur.get(), value.srid, value.sourceVersion);
        const bool fetchlr = needsFetch(lr.get(), value.srid, value.sourceVersion);
        const bool fetchll = needsFetch(ll.get(), value.srid, value.sourceVersion);
        const bool fetchingul = (ul.get() && ul->queued);
        const bool fetchingur = (ur.get() && ur->queued);
        const bool fetchinglr = (lr.get() && lr->queued);
        const bool fetchingll = (ll.get() && ll->queued);

        // fetch tile nodes
        if(fetchll && !fetchingll) {
            if(!ll.get())
                ll.reset(new QuadNode(service, this, this->srid, this->bounds.minX, this->bounds.minY, centerX, centerY));
            service.enqueue(ll);
        }
        if(fetchlr && !fetchinglr) {
            if(!lr.get())
                lr.reset(new QuadNode(service, this, this->srid, centerX, this->bounds.minY, this->bounds.maxX, centerY));
            service.enqueue(lr);
        }
        if(fetchur && !fetchingur) {
            if(!ur.get())
                ur.reset(new QuadNode(service, this, this->srid, centerX, centerY, this->bounds.maxX, this->bounds.maxY));
            service.enqueue(ur);
        }
        if(fetchul && !fetchingul) {
            if(!ul.get())
                ul.reset(new QuadNode(service, this, this->srid, this->bounds.minX, centerY, centerX, this->bounds.maxY));
            service.enqueue(ul);
        }

        // only allow recursion if all nodes have been fetched
        const bool recurse = (recurseLL || recurseUL || recurseUR || recurseLR) &&
                             ((ll.get() && ll->tile.get()) &&
                              (lr.get() && lr->tile.get()) &&
                              (ur.get() && ur->tile.get()) &&
                              (ul.get() && ul->tile.get()));

        if(recurseLL) {
            if(recurse) {
                ll->collect(value, focus, scene);
            }
        } else if(ll.get()) {
            ll->reset(false);
            if(recurse)
                value.tiles.push_back(ll->tile);
        }
        if(recurseLR) {
            if(recurse) {
                lr->collect(value, focus, scene);
            }
        } else if(lr.get()) {
            lr->reset(false);
            if(recurse)
                value.tiles.push_back(lr->tile);
        }
        if(recurseUR) {
            if(recurse) {
                ur->collect(value, focus, scene);
            }
        } else if(ur.get()) {
            ur->reset(false);
            if(recurse)
                value.tiles.push_back(ur->tile);
        }
        if(recurseUL) {
            if(recurse) {
                ul->collect(value, focus, scene);
            }
        } else if(ul.get()) {
            ul->reset(false);
            if(recurse)
                value.tiles.push_back(ul->tile);
        }

        if(recurse)
            return true;
    }

    if(needsFetch(this, srid, value.sourceVersion)) {
        if(this->level <= 1) {
            this->sourceVersion = value.sourceVersion;
            std::size_t numPostsLat;
            std::size_t numPostsLng;
            computePostCount(numPostsLat, numPostsLng, this->bounds, service.numPosts);
            const std::size_t numEdgeVertices = getNumEdgeVertices(numPostsLat, numPostsLng);
            MemBuffer2 edgeIndices(numEdgeVertices*sizeof(uint16_t));
            createEdgeIndices(edgeIndices, numPostsLat, numPostsLng);
            fetch(this->tile,
                  nullptr,
                  atakmap::raster::osm::OSMUtils::mapnikTileResolution(static_cast<int>(this->level))*service.fetchResolutionAdjustment,
                  this->bounds,
                  value.srid,
                  numPostsLat,
                  numPostsLng,
                  edgeIndices,
                  false,
                  service.fetchOptions.legacyElevationApi,
                  service.fetchOptions.constrainQueryRes,
                  service.fetchOptions.fillWithHiRes,
                  service.meshAllocator,
                  service.tileAllocator);
            //this->tile.opaque = this;
        } else {
            // XXX - this is a little goofy
            std::shared_ptr<QuadNode> thisptr;
            if(this == parent->ul.get())
                thisptr = parent->ul;
            else if(this == parent->ur.get())
                thisptr = parent->ur;
            else if(this == parent->lr.get())
                thisptr = parent->lr;
            else if(this == parent->ll.get())
                thisptr = parent->ll;
            else
                return false;
            service.enqueue(thisptr);
            if(!this->tile.get())
                return false;
        }
    }
    if(!this->tile.get() && this->queued)
        return false;
//    if(!this->tile.get() || !this->tile.model.get())
//        throw new IllegalStateException("tile or tile.model is null");
    value.tiles.push_back(this->tile);
    return true;
}


void ElMgrTerrainRenderService::QuadNode::reset(const bool data) NOTHROWS
{
    if (ul.get()) {
        ul->reset(true);
        ul->parent = nullptr;
        ul.reset();
    }
    if (ur.get()) {
        ur->reset(true);
        ur->parent = nullptr;
        ur.reset();
    }
    if (lr.get()) {
        lr->reset(true);
        lr->parent = nullptr;
        lr.reset();
    }
    if (ll.get()) {
        ll->reset(true);
        ll->parent = nullptr;
        ll.reset();
    }

    if (data && this->tile.get() && this->tile->data.value) {
        this->tile.reset();
    }
}

void ElMgrTerrainRenderService::QuadNode::updateParentZBounds(QuadNode &node) NOTHROWS
{
    if (node.parent != nullptr) {
        const bool updated = (node.bounds.minZ < node.parent->bounds.minZ) || (node.bounds.maxZ > node.parent->bounds.maxZ);
        if (node.bounds.minZ < node.parent->bounds.minZ)
            node.parent->bounds.minZ = node.bounds.minZ;
        if (node.bounds.maxZ > node.parent->bounds.maxZ)
            node.parent->bounds.maxZ = node.bounds.maxZ;

        if (updated)
            updateParentZBounds(*node.parent);
    }
}

namespace
{
    //static GLMapView.TerrainTile fetch(double resolution, Envelope mbb, int srid, int numPostsLat, int numPostsLng, bool fetchEl)
    TAKErr fetch(std::shared_ptr<TerrainTile> &value, double *els, const double resolution, const Envelope2 &mbb, const int srid, const std::size_t numPostsLat, const std::size_t numPostsLng, const MemBuffer2 &edgeIndices_, const bool fetchEl, const bool legacyEl, const bool constrainQueryRes, const bool fillWithHiRes, BlockPoolAllocator &allocator, PoolAllocator<TerrainTile> &tileAllocator) NOTHROWS
    {
        TAKErr code(TE_Ok);

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
        code = createHeightmapMeshIndices(indices, numPostsLat, numPostsLng);
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
        return code;
    }

    double estimateResolution(const GeoPoint2 &focus, const MapSceneModel2 &cmodel, const Envelope2 &bounds, GeoPoint2 *pclosest) NOTHROWS
    {
#if 0
        // XXX - frustum based culling. currently pulls in way too many tiles
        Matrix2 m(cmodel.camera.projection);
        m.concatenate(cmodel.camera.modelView);
        Frustum2 frustum(m);
        // compute AABB in WCS and check for intersection with the frustum
        TAK::Engine::Feature::Envelope2 aabbWCS(bounds);
        const int srid = cmodel.projection->getSpatialReferenceID();
        TAK::Engine::Feature::GeometryTransformer_transform(&aabbWCS, aabbWCS, 4326, srid);
        if (frustum.intersects(AABB(TAK::Engine::Math::Point2<double>(aabbWCS.minX, aabbWCS.minY, aabbWCS.minZ), TAK::Engine::Math::Point2<double>(aabbWCS.maxX, aabbWCS.maxY, aabbWCS.maxZ))) ||
            ((srid == 4326) && focus.longitude*((aabbWCS.minX+aabbWCS.maxX)/2.0) < 0 &&
                frustum.intersects(
                    AABB(TAK::Engine::Math::Point2<double>(aabbWCS.minX-(360.0*sgn((aabbWCS.minX+aabbWCS.maxX)/2.0)), aabbWCS.minY, aabbWCS.minZ),
                            TAK::Engine::Math::Point2<double>(aabbWCS.maxX-(360.0*sgn((aabbWCS.minX+aabbWCS.maxX)/2.0)), aabbWCS.maxY, aabbWCS.maxZ))))) {

            return cmodel.gsd;
        } else {
            return atakmap::raster::osm::OSMUtils::mapnikTileResolution(0);
        }
#else
        const TAK::Engine::Core::GeoPoint2 tgt(focus);
        if (atakmap::math::Rectangle<double>::contains(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, tgt.longitude, tgt.latitude))
            return cmodel.gsd;

#ifdef __ANDROID__
        const double sceneHalfWidth = cmodel.width / 2.0;
        const double sceneHalfHeight = cmodel.height / 2.0;
#else
        const double sceneHalfWidth = (double)cmodel.width / 2.0;
        const double sceneHalfHeight = (double)cmodel.height / 2.0;
#endif
        const double sceneRadius = cmodel.gsd*sqrt((sceneHalfWidth*sceneHalfWidth) + (sceneHalfHeight*sceneHalfHeight));
    
        const TAK::Engine::Core::GeoPoint2 aoiCentroid((bounds.minY + bounds.maxY) / 2.0, (bounds.minX + bounds.maxX) / 2.0);
        double aoiRadius;
        {
            const double uld = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, TAK::Engine::Core::GeoPoint2(bounds.maxY, bounds.minX), true);
            const double lrd = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, TAK::Engine::Core::GeoPoint2(bounds.minY, bounds.maxX), true);
            aoiRadius = std::max(uld, lrd);
        }
        const double d = TAK::Engine::Core::GeoPoint2_distance(aoiCentroid, tgt, true);
        if((d-aoiRadius) < sceneRadius)
            return cmodel.gsd;
        // observation is that the value returned here really does not matter
        return cmodel.gsd*pow(2.0, 1.0+log((d-aoiRadius)-sceneRadius)/log(2.0));
#endif
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

    TAKErr intersectWithTerrainTileImpl(GeoPoint2 *value, const ElevationChunk::Data &node, const MapSceneModel2 &scene, const float x, const float y) NOTHROWS
    {
        TAK::Engine::Math::Mesh mesh(node.value, &node.localFrame);
        return scene.inverse(value, TAK::Engine::Math::Point2<float>(x, y), mesh);
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

                ElevationChunk::Data data_proj;
                data_proj.srid = transformedOpts.srid;
                if(transformedOpts.localFrame.get())
                    data_proj.localFrame = *transformedOpts.localFrame;
                data_proj.value = std::move(transformed);
                node = std::move(data_proj);

                // XXX - 
                const_cast<TerrainTile &>(tile).data_proj = node;
            }
        }

        TAK::Engine::Math::Mesh mesh(node.value, &node.localFrame);
        return scene.inverse(value, TAK::Engine::Math::Point2<float>(x, y), mesh);
    }
    bool intersectsAABB(GeoPoint2 *value, const MapSceneModel2 &scene, const TAK::Engine::Feature::Envelope2 &aabb_wgs84, float x, float y) NOTHROWS
    {
        TAK::Engine::Math::Point2<double> org(x, y, -1.0);
        TAK::Engine::Math::Point2<double> tgt(x, y, 1.0);

        if (scene.inverseTransform.transform(&org, org) != TE_Ok)
            return false;
        if (scene.inverseTransform.transform(&tgt, tgt) != TE_Ok)
            return false;

        TAK::Engine::Math::Point2<double> points[8];
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

        TAK::Engine::Math::AABB aabb(points, 8u);
#if false
        if (aabb.contains(org) && aabb.contains(tgt)) {
            return true;
        }
#endif

        return scene.inverse(value, TAK::Engine::Math::Point2<float>(x, y), aabb) == TE_Ok;
    }
    TAKErr estimateFocusPoint(GeoPoint2 *value, const MapSceneModel2 &scene, const std::vector<std::shared_ptr<const TerrainTile>> &terrainTiles) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (terrainTiles.empty())
        {
            code = scene.inverse(value, TAK::Engine::Math::Point2<float>(scene.focusX, scene.focusY));
            TE_CHECKRETURN_CODE(code);

            return code;
        }

        const int sceneSrid = scene.projection->getSpatialReferenceID();

        TAK::Engine::Math::Point2<double> camdir;
        Vector2_subtract<double>(&camdir, scene.camera.location, scene.camera.target);
        // scale by nominal display model meters
        camdir.x *= scene.displayModel->projectionXToNominalMeters;
        camdir.y *= scene.displayModel->projectionYToNominalMeters;
        camdir.z *= scene.displayModel->projectionZToNominalMeters;

        double mag;
        Vector2_length(&mag, camdir);
        mag = std::max(mag, 2000.0);

        // scale the direction vector
        Vector2_multiply(&camdir, camdir, mag*2.0);

        TAK::Engine::Math::Point2<double> loc(scene.camera.target);
        // scale by nominal display model meters
        loc.x *= scene.displayModel->projectionXToNominalMeters;
        loc.y *= scene.displayModel->projectionYToNominalMeters;
        loc.z *= scene.displayModel->projectionZToNominalMeters;

        // add the scaled camera direction
        Vector2_add(&loc, loc, camdir);

        GeoPoint2 candidate;
        double candidateDistSq = NAN;

        // compare all other tiles with the candidate derived from focus or earth surface
        for (std::size_t i = 0; i < terrainTiles.size(); i++) {
            const TerrainTile &tile = *terrainTiles[i];
            // if the tile doesn't have data, skip -- we've already computed surface intersection above
            if (!tile.hasData)
                continue;

            // check isect on AABB
            if (!intersectsAABB(&candidate, scene, tile.aabb_wgs84, scene.focusX, scene.focusY)) {
                // no AABB isect, continue
                continue;
            } else if(!isnan(candidateDistSq)) {               
                // if we have a candidate and the AABB intersection is further
                // than the candidate distance, any content intersect is going to
                // be further
                TAK::Engine::Math::Point2<double> proj;
                scene.projection->forward(&proj, candidate);
                // convert hit to nominal display model meters
                proj.x *= scene.displayModel->projectionXToNominalMeters;
                proj.y *= scene.displayModel->projectionYToNominalMeters;
                proj.z *= scene.displayModel->projectionZToNominalMeters;

                const double dx = proj.x - loc.x;
                const double dy = proj.y - loc.y;
                const double dz = proj.z - loc.z;
                const double distSq = ((dx*dx) + (dy*dy) + (dz*dz));

                if (distSq > candidateDistSq)
                    continue;
            }

            // do the raycast into the mesh
            code = intersectWithTerrainTileImpl(&candidate, tile, scene, scene.focusX, scene.focusY);
            if (code != TE_Ok)
                continue;

            TAK::Engine::Math::Point2<double> proj;
            scene.projection->forward(&proj, candidate);
            // convert hit to nominal display model meters
            proj.x *= scene.displayModel->projectionXToNominalMeters;
            proj.y *= scene.displayModel->projectionYToNominalMeters;
            proj.z *= scene.displayModel->projectionZToNominalMeters;

            const double dx = proj.x - loc.x;
            const double dy = proj.y - loc.y;
            const double dz = proj.z - loc.z;
            const double distSq = ((dx*dx) + (dy*dy) + (dz*dz));
            if (isnan(candidateDistSq) || distSq < candidateDistSq) {
                candidate.altitude = NAN;
                candidate.altitudeRef = TAK::Engine::Core::AltitudeReference::HAE;

                *value = candidate;
                candidateDistSq = distSq;
            }
        }

        return isnan(candidateDistSq) ? TE_Err : TE_Ok;
    }
}
