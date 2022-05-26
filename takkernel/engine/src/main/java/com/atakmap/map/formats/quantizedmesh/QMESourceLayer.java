package com.atakmap.map.formats.quantizedmesh;

import java.io.File;
import java.util.List;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface QMESourceLayer {

    /**
     * Returns the minimum zoom level the source can provide.
     * @return minimum zoom level
     */
    public int getMinZoom();

    /**
     * Returns the maximum zoom level the source can provide.
     * @return maximum zoom level
     */
    public int getMaxZoom();

    /**
     * Indicates if the local storage location exists and appears to be usable
     * @return true if local storage location exists and appears to be usable, false otherwise
     */
    public boolean isLocalDirectoryValid();

    /**
     * Get the closest level that can be provided by the source layer given a geodetic span
     * @param geodeticSpan Geodetic span, in degrees
     * @return Closest level to the provided span
     */
    public int getClosestLevel(double geodeticSpan);

    /**
     * Get the max level of detail available to the source layer
     * @return Maximum LOD
     */
    public int getMaxLevel();

    /**
     * Obtain the local directory that houses all tiles for the entire source layer.
     * @return the directory for tiles in the source layer
     */
    public File getDirectory();

    /**
     * Obtain the local directory that houses tiles for the given z level in this source layer.
     * It is expected that the returned directory be somewhere below the location returned by getDirectory().
     * @param z the z level to get the directory name for
     * @return the directory for the given level
     */
    public File getLevelDirectory(int z);

    /**
     * Obtain the local file location representing the
     * tile for the given x, y, z coordinates in this source layer.
     * @param x the x coordinate of the tile to get the local filename for
     * @param y the y coordinate of the tile to get the local filename for
     * @param z the z level of the tile to get the local filename for
     * @return the tile file
     */
    public File getTileFile(int x, int y, int z);

    /**
     * Check if this layer is valid. A backing layer is valid if it has
     * a valid source from which to make requests for tiles to
     * @return True if valid, false otherwise
     */
    public boolean isValid();

    /**
     * Check if this source layer is presently enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled();

    /**
     * Check if a tile is available for this layer
     * @param x X coordinate
     * @param y Y coordinate
     * @param level Level
     * @return True if this tile is available
     */
    public boolean hasTile(int x, int y, int level);

    /**
     * Get a list of TileExtents available to this source layer at the given level.
     * @param level the level to query
     * @return list of TileExtents
     */
    public List<TileExtents> getAvailableExtents(int level);

    /**
     * Start an asynchronous data request for the tile at the given x, y, z location.
     * The Layer implementation will request and populate the tile to the appropriate local
     * file store location (see getTileFilename()).
     * @param x x coordinate
     * @param y y coordinate
     * @param z level
     */
    public void startDataRequest(int x, int y, int z);

}
