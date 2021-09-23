#ifndef TAK_ENGINE_RENDERER_CORE_TILECLIENTCONTROL_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_TILECLIENTCONTROL_H_INCLUDED

#include "port/Platform.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {
                class ENGINE_API TileClientControl {
                    virtual void setOfflineOnlyMode(bool offlineOnly) = 0;
                    virtual bool isOfflineOnlyMode() = 0;
                    virtual void refreshCache() = 0;
                    virtual void setCacheAutoRefreshInterval(int64_t milliseconds) = 0;
                    virtual int64_t getCacheAutoRefreshInterval() = 0;

                };
            }
        }
    }
}

#endif
