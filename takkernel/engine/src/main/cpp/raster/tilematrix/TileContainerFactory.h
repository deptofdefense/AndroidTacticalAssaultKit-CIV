
#ifndef TAK_ENGINE_RASTER_TILECONTAINERFACTORY_H_INCLUDED
#define TAK_ENGINE_RASTER_TILECONTAINERFACTORY_H_INCLUDED

#include "util/Error.h"
#include "raster/tilematrix/TileContainer.h"
#include "port/Collection.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileMatrix {

                class ENGINE_API TileContainerSpi {
                public:
                    virtual ~TileContainerSpi() NOTHROWS;

                    /**
                     * Returns the provider name.
                     * @return
                     */
                    virtual const char *getName() const NOTHROWS = 0;

                    /**
                     * Returns the default extension for the associated file type.
                     * @return
                     */
                    virtual const char *getDefaultExtension() const NOTHROWS = 0;

                    /**
                     * Creates a new tile container at the specified location, modeling its own
                     * tile matrix definition from the supplied {@link TileMatrix} instance.
                     *
                     * @param name  The name for the container content; adopted from
                     *              <code>spec</code> if <code>null</code>
                     * @param path  The location for the new container
                     * @param spec  A <code>TileMatrix</code> that the new container's tile
                     *              matrix should be modeled after
                     * @return
                     */
                    virtual Util::TAKErr create(TileContainerPtr &result, const char *name, const char *path, const TileMatrix *spec) const NOTHROWS = 0;
            
                    /**
                     * Opens a new {@link TileContainer} instance that will read from (and
                     * possibly write to) the specified location.
                     *
                     * @param path      The location of the container
                     * @param spec      If non-<code>null</code> specifies a tile matrix
                     *                  definition that the container must be compatible with.
                     *                  For the read-only case, this parameter should always
                     *                  be <code>null</code>.
                     * @param readOnly  <code>true</code> for read-only, <code>false</code> for
                     *                  read-write
                     * @return
                     */
                    virtual Util::TAKErr open(TileContainerPtr &result, const char *path, const TileMatrix *spec, bool readOnly) const NOTHROWS = 0;

                    /**
                     * Returns <code>true</code> if this <code>TileContainerSpi</code> can
                     * create a {@link TileContainer} instance that can store tiles from the
                     * specified tile matrix definition.
                     *
                     * @param spec  A tile matrix definition
                     *
                     * @return  <code>true</code> if this <code>TileContainerSpi</code> can
                     *          create a {@link TileContainer} instance that can store tiles
                     *          from the specified tile matrix definition, <code>false</code>
                     *          otherwise.
                     */
                    virtual Util::TAKErr isCompatible(bool *result, const TileMatrix *spec) const NOTHROWS = 0;
                };
                typedef std::unique_ptr<TileContainerSpi, void (*)(const TileContainerSpi *)> TileContainerSpiPtr;

                /**
                 * Opens or creates a {@link TileContainer} at the specified location that
                 * will be able to
                 *
                 * @param path  The path of the target tile container
                 * @param spec  Describes the layout of the target tile container, may not
                 *              be <code>null</code>
                 * @param hint  The desired <I>provider</I>, or <code>null</code> to select
                 *              any compatible container provider
                 *
                 * @return  A new {@link TileContainer} capable of storing tile data
                 *          described by the specified matrix, or <code>null</code> if no
                 *          such container could be created.
                 */
                ENGINE_API Util::TAKErr TileContainerFactory_openOrCreateCompatibleContainer(TileContainerPtr &result, const char *path, const TileMatrix *spec, const char *hint) NOTHROWS;

                /**
                 * Opens an already existing tile container at the specified location.
                 *
                 * @param path      The path
                 * @param readOnly  <code>true</code> to open as read-only,
                 *                  <code>false</code> to allow read-write.
                 * @param hint      The desired <I>provider</I>, or <code>null</code> to
                 *                  select any container provider that can open the file at
                 *                  specified location.
                 *
                 * @return  A new {@link TileContainer} instance providing access to the
                 *          tile content at the specified location or <code>null</code> if
                 *          no such container could be opened.
                 */
                ENGINE_API Util::TAKErr TileContainerFactory_open(TileContainerPtr &result, const char *path, bool readOnly, const char *hint) NOTHROWS;

                /**
                 * Registers the specified spi.
                 * @param spi
                 */
                ENGINE_API Util::TAKErr TileContainerFactory_registerSpi(const std::shared_ptr<TileContainerSpi> &spi) NOTHROWS;

                /**
                 * Unregisters the specified spi.
                 * @param spi
                 */
                ENGINE_API Util::TAKErr TileContainerFactory_unregisterSpi(const TileContainerSpi *spi) NOTHROWS;

                /**
                 * Visits all registered spis.
                 *
                 * @param visitor   The callback that will be invoked when visiting the
                 *                  registered spis.
                 */
                ENGINE_API Util::TAKErr TileContainerFactory_visitSpis(Util::TAKErr (*visitor)(void *opaque, TileContainerSpi &), void* opaque) NOTHROWS;

                /**
                 * Visits all registered spis compatible with the specified tile matrix.
                 *
                 * @param visitor   The callback that will be invoked when visiting the
                 *                  compatible registered spis.
                 */
                ENGINE_API Util::TAKErr TileContainerFactory_visitCompatibleSpis(Util::TAKErr (*visitor)(void* opaque, TileContainerSpi &), void* opaque, const TileMatrix *spec) NOTHROWS;

            }
        }
    }
}

#endif
