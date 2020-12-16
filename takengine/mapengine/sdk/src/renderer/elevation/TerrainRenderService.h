#ifndef TAK_ENGINE_RENDERER_ELEVATION_TERRAINRENDERSERVICE_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_TERRAINRENDERSERVICE_H_INCLUDED

#include <memory>

#include "elevation/ElevationChunk.h"
#include "port/Collection.h"
#include "port/Platform.h"
#include "renderer/core/GLMapView2.h"
#include "renderer/elevation/TerrainTile.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                class TerrainRenderService
                {
                public :
                    virtual ~TerrainRenderService() NOTHROWS = 0;
                public :
                    /**
                     * Returns the current version of the terrain, monotonically increasing.
                     */
                    virtual int getTerrainVersion() const NOTHROWS = 0;
                    /**
                     * Returns the last set of tiles returned by the previous
                     * call to `lock(..., MapSceneModel2, ...)`. These tiles
                     * will not be eligible for destruction until being passed
                     * to a call of `unlock`.
                     */
                    virtual Util::TAKErr lock(Port::Collection<std::shared_ptr<const TerrainTile>> &value) NOTHROWS = 0;
                    /**
                     * Returns the current terrain tiles and specifies the
                     ( current state of the scene. This may cause the service
                     * to fetch new data. The returned tiles are considered
                     * "locked" and are not eligible for destruct until
                     * being passed to a call of `unlock`.
                     * 
                     * @param value         Returns the terrain tiles
                     * @param view          The scene
                     * @param srid          The SRID for the terrain tiles, may
                     *                      be different from the SRID for the
                     *                      scene
                     * @param sceneVersion  The version of the scene; used for
                     *                      bookkeeping. For two
                     *                      `MapSceneModel2` instances: this
                     *                      value should be the same if the
                     *                      instances are equal; this value
                     *                      MUST be different if the instances
                     *                      are not equal.
                     */
                    virtual Util::TAKErr lock(Port::Collection<std::shared_ptr<const TerrainTile>> &value, const TAK::Engine::Core::MapSceneModel2 &view, const int srid, const int sceneVersion) NOTHROWS = 0;
                    /**
                     * Unlocks the tiles returned by a previous call to `lock`
                     */
                    virtual Util::TAKErr unlock(Port::Collection<std::shared_ptr<const TerrainTile>> &tiles) NOTHROWS = 0;
                    virtual Util::TAKErr getElevation(double *value, const double latitude, const double longitude) const NOTHROWS = 0;
                    virtual Util::TAKErr start() NOTHROWS = 0;
                    virtual Util::TAKErr stop() NOTHROWS = 0;
                };

                typedef std::unique_ptr<TerrainRenderService, void(*)(const TerrainRenderService *)> TerrainRenderServicePtr;
            }
        }
    }
}
#endif
