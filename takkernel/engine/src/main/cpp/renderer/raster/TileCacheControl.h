#ifndef TAK_ENGINE_RENDERER_CORE_TILECACHECONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_TILECACHETCONTROL_H_INCLUDED

#include "core/GeoPoint2.h"
#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                class ENGINE_API TileCacheControl
                {
                public :
                    class OnTileUpdateListener;
                public :
                    virtual ~TileCacheControl() NOTHROWS = 0;

                    /**
                     * Requests that the cache queue be prioritized to satisfy tiles nearest
                     * to the specified point of interest.
                     * @param p
                     */
                    virtual void prioritize(const TAK::Engine::Core::GeoPoint2 &p) NOTHROWS = 0;
                
                    /**
                     * Requests that any operation to cache the specified tile be aborted. If
                     * the specified tile is not currently queued for caching, this call is
                     * ignored.
                     * @param level
                     * @param x
                     * @param y
                     */
                    virtual void abort(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS = 0;
                    virtual bool isQueued(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS = 0;
                    virtual void setOnTileUpdateListener(OnTileUpdateListener *l) NOTHROWS = 0;
                
                    /**
                     * Any tile with a timestamp less than the expiry time will be refetched
                     * when requested.
                     * @param expiry    Measured in epoch milliseconds
                     */
                    virtual void expireTiles(const int64_t expiry) NOTHROWS = 0;
                };
                
                class ENGINE_API TileCacheControl::OnTileUpdateListener
                {
                public :
                    virtual ~OnTileUpdateListener() NOTHROWS = 0;
                    virtual void onTileUpdated(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS = 0;
                };

                ENGINE_API const char *TileCacheControl_getType() NOTHROWS;
            }
        }
    }
}

#endif