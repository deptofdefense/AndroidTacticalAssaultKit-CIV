#pragma once

#include "raster/RasterDataAccess2.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "core/GeoPoint2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Raster {

                /**
                 * Control to acquire the RasterDataAccess for the associated RasterLayer2.
                 */
                class ENGINE_API RasterDataAccessControl
                {
                   public:
                    virtual ~RasterDataAccessControl() NOTHROWS = 0;

                    /**
                     * Requests access to a RasterDataAccess at
                     * the specified point or errors if there is no data at the
                     * specified point or if it is not accessible. The provided visitor is
                     * is synchronously called back with the relevant RasterDataAccess instance
                     * for the caller to use.
                     * The RasterDataAccess instance provided to the callback on a successful invocation is
                     * only valid for the duration of the callback.
                     *
                     * @param accessCallback callback to be invoked with requester RasterDataAccess
                     * @param point The point
                     *
                     * @return  TE_Ok if raster is populated; if other value is returned, 
                     *          there is no data at the specified point or if it is not accessible
                     *          and reaster is not populated.
                     */
                    virtual Util::TAKErr accessRasterData(
                        void (*accessVisitor)(void *opaque, TAK::Engine::Raster::RasterDataAccess2 *access), void *opaque, const TAK::Engine::Core::GeoPoint2 &point) NOTHROWS = 0; 
                };

                ENGINE_API const char *RasterDataAccessControl_getType() NOTHROWS;
            }
        }
    }
}

