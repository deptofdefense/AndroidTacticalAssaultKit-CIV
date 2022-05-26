
package com.atakmap.android.maps.tilesets;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.map.layer.raster.DatasetDescriptor;

/**
 * Interface providing access to tile pyramid data.
 * 
 * <P>Tile row and tile column refer to vertical and horizontal offsets,
 * respectively, into the tile grid. Pixel row and pixel column refer to
 * vertical and horizontal offsets, respectively, into the pixel grid. The pixel
 * grid may be relative to the image extent over the entire tile matrix for a
 * given resolution level or relative to an individual tile.
 * 
 * @author Developer
 */
public abstract class TilesetSupport {

    private final static Map<String, TilesetSupport.Spi> SPIS = new HashMap<String, TilesetSupport.Spi>();

    protected final GLBitmapLoader bitmapLoader;
    
    /**
     * Creates a new tileset support that will use the specified
     * {@link GLBitmapLoader} to load the tile data.
     * 
     * @param bitmapLoader  The asynchronous bitmap loader
     */
    protected TilesetSupport(GLBitmapLoader bitmapLoader) {
        this.bitmapLoader = bitmapLoader;
    }

    /**
     * Notifies that one or more tiles may be requested via
     * {@link #getTile(int, int, int, android.graphics.BitmapFactory.Options)}.
     * The tileset support may temporarily allocate and retain some additional
     * resources until {@link #stop()} is invoked in order to improve
     * performance over multiple tile requests.
     */
    public abstract void start();

    /**
     * Notifies the tileset support that the bulk tile request is complete. Any
     * temporarily allocated resources to support the bulk request should be
     * released.
     */
    public abstract void stop();

    /**
     * Returns the bounds of the tile, ordered as south, west, north, east.
     * 
     * @param latIndex  The latitude index of the tile
     * @param lngIndex  The longitude index of the tile
     * @param level     The level of the tile
     * @param swne      An optionally pre-allocated array to store the bounds.
     * 
     * @return  The bounds of the tile, ordered south, west, north, east. If
     *          <code>swne</code> is non-<code>null</code>, it will be updated
     *          with the bounds and returned.
     */
    public abstract double[] getTileBounds(int latIndex, int lngIndex, int level, double[] swne);

    public abstract int getTileZeroX(double lng, int gridX, int gridWidth);

    public abstract int getTileZeroY(double lat, int gridY, int gridHeight);

    /**
     * Returns the pixel column in the specified tile that corresponds to the
     * specified longitude.
     * 
     * @param latIndex  The tile latitude index
     * @param lngIndex  The tile longitude index
     * @param level     The tile level
     * @param lng       The longitude
     * 
     * @return  The pixel column in the specified tile that corresponds to the
     *          specified longitude. The value may be less than zero or greater
     *          than or equal to the tile pixel width in the event that the
     *          specified longitude does not intersect the tile.
     */
    public abstract double getTilePixelX(int latIndex, int lngIndex, int level, double lng);

    /**
     * Returns the pixel row in the specified tile that corresponds to the
     * specified latitude.
     * 
     * @param latIndex  The tile latitude index
     * @param lngIndex  The tile longitude index
     * @param level     The tile level
     * @param lat       The latitude
     * 
     * @return  The pixel row in the specified tile that corresponds to the
     *          specified latitude. The value may be less than zero or greater
     *          than or equal to the tile pixel height in the event that the
     *          specified latitude does not intersect the tile.
     */
    public abstract double getTilePixelY(int latIndex, int lngIndex, int level, double lat);

    /**
     * Returns the latitude for the specified pixel row in the specified tile.
     * 
     * @param latIndex  The tile latitude index
     * @param lngIndex  The tile longitude index
     * @param level     The tile level
     * @param y         The pixel row
     * 
     * @return  The latitude for the specified pixel row of the specified tile.
     */
    public abstract double getTilePixelLat(int latIndex, int lngIndex, int level, int y);

    /**
     * Returns the longitude for the specified pixel column in the specified
     * tile.
     * 
     * @param latIndex  The tile latitude index
     * @param lngIndex  The tile longitude index
     * @param level     The tile level
     * @param x         The pixel column
     * 
     * @return  The longitude for the specified pixel column of the specified
     *          tile
     */
    public abstract double getTilePixelLng(int latIndex, int lngIndex, int level, int x);

    /**
     * Creates a new asynchronous task to load the specified tile.
     * 
     * @param latIndex  The latitude index
     * @param lngIndex  The longitude index
     * @param level     The level
     * @param opts      The {@link BitmapFactory.Options} to be used when
     *                  loading the {@link SmartBitmap}. May be
     *                  <code>null</code>.
     * 
     * @return  The task created to read the tile.
     */
    public abstract FutureTask<Bitmap> getTile(int latIndex, int lngIndex, int level,
            BitmapFactory.Options opts);

    /**
     * Initializes the tileset support. This method will always be invoked prior
     * to any other methods.
     * 
     * <P>This method may be invoked following a call to {@link #release()},
     * allowing the tileset support to be reiniatialized.
     */
    public abstract void init();

    /**
     * Releases all resources allocated by the tileset support. No methods
     * should be invoked following the invocation of this method without the
     * {@link #init()} method being invoked first.
     */
    public abstract void release();
    
    /**
     * Returns the version for the tiles that will be returned via
     * {@link #getTile(int, int, int, android.graphics.BitmapFactory.Options)}.
     * This value may change over the duration of the runtime, indicating that
     * tiles previously read with a different version are not the most
     * up-to-date and should be reloaded.  Version numbers should be
     * monotonically increasing.
     * 

     * @param latIndex  The latitude index
     * @param lngIndex  The longitude index
     * @param level     The level
     * 
     * @return  The current tiles version. The default implementation always
     *          returns <code>0</code>
     */
    public int getTilesVersion(int latIndex, int lngIndex, int level) {
        return 0;
    }

    /**************************************************************************/

    public static synchronized void registerSpi(TilesetSupport.Spi spi) {
        SPIS.put(spi.getName(), spi);
    }

    public static synchronized void unregisterSpi(TilesetSupport.Spi spi) {
        SPIS.remove(spi.getName());
    }

    public static synchronized TilesetSupport create(TilesetInfo info, GLBitmapLoader loader) {
        final String spiClass = DatasetDescriptor.getExtraData(info.getInfo(), "supportSpi",
                SimpleUriTilesetSupport.Spi.INSTANCE.getName());
        TilesetSupport.Spi spi = SPIS.get(spiClass);
        if (spi != null)
            return spi.create(info, loader);
        return null;
    }

    /**************************************************************************/

    public static interface Spi {
        public String getName();
        public TilesetSupport create(TilesetInfo tsInfo, GLBitmapLoader bitmapLoader);
    }
}
