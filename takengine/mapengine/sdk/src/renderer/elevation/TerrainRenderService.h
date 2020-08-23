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
                    virtual int getTerrainVersion() const NOTHROWS = 0;
                    virtual Util::TAKErr lock(Port::Collection<std::shared_ptr<const TerrainTile>> &value, const TAK::Engine::Core::MapSceneModel2 &view, const int srid, const int sceneVersion) NOTHROWS = 0;
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
