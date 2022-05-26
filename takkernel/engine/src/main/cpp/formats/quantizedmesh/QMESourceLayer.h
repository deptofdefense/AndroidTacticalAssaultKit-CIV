#ifndef TAK_ENGINE_FORMATS_QUANTIZEDMESH_QMLAYER_H_INCLUDED
#define TAK_ENGINE_FORMATS_QUANTIZEDMESH_QMLAYER_H_INCLUDED

#include "port/Platform.h"
#include "port/String.h"
#include "port/Vector.h"
#include "util/Error.h"

#include "formats/quantizedmesh/TileExtents.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace QuantizedMesh {

/**
 * Interface representing a source "layer" of Quantized Mesh elevation data.
 * The QME Elevation provider can utilize such a source to produce elevation data.
 */
class ENGINE_API QMESourceLayer
{
public:

    /**
     * Returns the minimum zoom level the source can provide.
     * @param value returns the minimum zoom level
     * @return TE_Ok if value is populated, TE_Err otherwise
     */
    virtual Util::TAKErr getMinZoom(int *value) const NOTHROWS = 0;

    /**
     * Returns the maximum zoom level the source can provide.
     * @param value returns the maximum zoom level
     * @return TE_Ok if value is populated, TE_Err otherwise
     */
    virtual Util::TAKErr getMaxZoom(int *value) const NOTHROWS = 0;

    /**
    * Indicates if the local storage location exists and appears to be usable
    * @param value on successful return, is set to true if local storage location exists and appears to be usable, false otherwise
    * @return TE_Ok if value is populated, TE_Err otherwise
    */
    virtual Util::TAKErr isLocalDirectoryValid(bool *value) const NOTHROWS = 0;

    /**
    * Get the closest level that can be provided by the source layer given a geodetic span
    * @param geodeticSpan Geodetic span, in degrees
    * @param value on successful return, is set to closed level to the provided span
    * @return TE_Ok if value is populated, TE_Err otherwise
    */
    virtual Util::TAKErr getClosestLevel(int *value, double geodeticSpan) const NOTHROWS = 0;

    /**
    * Get the max level of detail available to the source layer
    * @param value on successful return, is set to maximum level of detail
    * @return TE_Ok if value is populated, TE_Err otherwise
    */
    virtual Util::TAKErr getMaxLevel(int *value) const NOTHROWS = 0;

    /**
     * Obtain the local directory that houses all tiles for the entire source layer.
     * The implementation must provide 'dirname' as an absolute path string.
     * @param dirname String to receive the directory name
     * @return TE_Ok if the dirname is populated, TE_Err if an error occurs
     */
    virtual Util::TAKErr getDirectory(Port::String *dirname) const NOTHROWS = 0;

    /**
     * Obtain the local directory that houses tiles for the given z level in this source layer.
     * The implementation must provide 'dirname' as an absolute path string, and it is expected
     * that dirname be somewhere below the location returned by getDirectory().
     * @param dirname String to receive the directory name
     * @param z the z level to get the directory name for
     * @return TE_Ok if the dirname is populated, TE_Err if an error occurs
     */
    virtual Util::TAKErr getLevelDirName(Port::String *dirname, int z) const NOTHROWS = 0;

    /**
     * Obtain the local filename representing the tile for the given x, y, z coordinates in this source layer.
     * The implementation must provide 'filename' as an absolute path string.
     * @param filename String to receive the directory name
     * @param x the x coordinate of the tile to get the local filename for
     * @param y the y coordinate of the tile to get the local filename for
     * @param z the z level of the tile to get the local filename for
     * @return TE_Ok if the filename is populated, TE_Err if an error occurs
     */
    virtual Util::TAKErr getTileFilename(Port::String *filename, int x, int y, int z) const NOTHROWS = 0;

    /**
    * Check if this layer is valid. A backing layer is valid if it has
    * a valid source from which to make requests for tiles to
    * @param value on successful return, is set to true if valid, false otherwise
    * @return TE_Ok if value is populated, TE_Err otherwise
    */
    virtual Util::TAKErr isValid(bool *value) const NOTHROWS = 0;

    /**
     * Check if this source layer is presently enabled
    * @param value on successful return, is set to true if source layer is enabled, false otherwise
    * @return TE_Ok if value is populated, TE_Err otherwise
     */
    virtual Util::TAKErr isEnabled(bool *value) const NOTHROWS = 0;

    /**
    * Check if a tile with specific coordinates is available for this layer.
    *
    * @param value on successful return, is set to true if layer has tile for specified coordinates
    * @param x X coordinate
    * @param y Y coordinate
    * @param level Level
    * @return TE_Ok if value is populated, TE_Err otherwise
    */
    virtual Util::TAKErr hasTile(bool *value, int x, int y, int level) const NOTHROWS = 0;

    /**
     * Get a list of TileExtents available to this source layer at the given level.
     * @param extents Vector to receive the TileExtents
     * @param level the level to query
     * @return TE_Ok if extents is populated, TE_Err otherwise
     */
    virtual Util::TAKErr getAvailableExtents(Port::Vector<TileExtents> *extents, int level) const NOTHROWS = 0;

   /**
    * Start an asynchronous data request for the tile at the given x, y, z location.
    * The Layer implementation will request and populate the tile to the appropriate local
    * file store location (see getTileFilename()).
    *
    * @param x x coordinate
    * @param y y coordinate
    * @param z level
    * @return TE_Ok if request is submitted successfully, TE_Err otherwise
    */
    virtual Util::TAKErr startDataRequest(int x, int y, int z) NOTHROWS = 0;


    virtual ~QMESourceLayer() NOTHROWS {};

protected:
    QMESourceLayer() NOTHROWS {};
};
                

/**
 * Attach a Quantized mesh elevation source layer.  The layer will be registered as an Elevation Source 
 * and data from the source layer will be decoded and requested as needed.
 * @param source the source layer to attach
 * @return TE_Ok if attached successfully
 */
ENGINE_API Util::TAKErr QMESourceLayer_attach(std::shared_ptr<QMESourceLayer> &source);

/**
 * Detach a previously attached Quantized mesh elevation source layer. 
 * 
 * @param source the source layer to detach
 * @return TE_Ok if detached successfully
 */
ENGINE_API Util::TAKErr QMESourceLayer_detach(const QMESourceLayer &source);

}
}
}
}

#endif
