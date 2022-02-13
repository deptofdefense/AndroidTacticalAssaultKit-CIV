package com.atakmap.map.layer.raster.tilematrix;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.math.PointD;
import com.atakmap.util.Disposable;

public interface TileMatrix extends Disposable {
    public final class ZoomLevel {
        /**
         * The level.
         */
        public int level;
        /**
         * Informative value indicating the nominal resolution of a tile at the
         * zoom level in meters per pixel.
         */
        public double resolution;
        /**
         * The horizontal spacing between adjacent pixels, in projection units.
         */
        public double pixelSizeX;
        /**
         * The vertical spacing between adjacent pixels, in projection units.
         */
        public double pixelSizeY;
        /**
         * The width of a tile at the zoom level, in pixels.
         */
        public int tileWidth;
        /**
         * The height of a tile at the zoom level, in pixels.
         */
        public int tileHeight;
    }
    
    /**
     * Returns the name of the tiled content.
     * 
     * @return  The name of the tilted content.
     */
    public String getName();
    /**
     * Returns the Spatial Reference ID associated with the tiled content. EPSG
     * code values shall be used.
     * 
     * @return  The Spatial Reference ID associated with the tiled content.
     */
    public int getSRID();
    /**
     * Returns the definition of the zoom levels associated with the content.
     * 
     * @return  The definition of the zoom levels associated with the content.
     */
    public ZoomLevel[] getZoomLevel();
    /**
     * Returns the x-component of the tile matrix origin (upper-left) in the
     * native spatial reference of the content.
     * 
     * @return  The x-component of the tile matrix origin (upper-left) in the
     *          native spatial reference of the content.
     */
    public double getOriginX();
    /**
     * Returns the y-component of the tile matrix origin (upper-left) in the
     * native spatial reference of the content.
     * 
     * @return  The y-component of the tile matrix origin (upper-left) in the
     *          native spatial reference of the content.
     */
    public double getOriginY();
    /**
     * Returns the tile for the specified zoom level, tile row and tile column
     * as a decoded {@link Bitmap}.
     * 
     * @param zoom      The zoom level
     * @param x         The tile column
     * @param y         The tile row
     * @param error     If non-<code>null</code>, returns the error that
     *                  occurred when trying to obtain and decode the tile. This
     *                  will be used for informational purposes only;
     *                  implementations are not required to return a value.
     *                   
     * @return  The decoded tile for the specified zoom, tile row and tile
     *          column, or <code>null</code> if there is no tile available or
     *          tile decode failed.
     */
    public Bitmap getTile(int zoom, int x, int y, Throwable[] error);
    /**
     * Returns the tile data for the specified zoom level, tile row and tile
     * column.
     * 
     * @param zoom      The zoom level
     * @param x         The tile column
     * @param y         The tile row
     * @param error     If non-<code>null</code>, returns the error that
     *                  occurred when trying to obtain and decode the tile. This
     *                  will be used for informational purposes only;
     *                  implementations are not required to return a value.
     *                   
     * @return  The tile data for the specified zoom, tile row and tile column,
     *          or <code>null</code> if there is no tile available.
     */
    public byte[] getTileData(int zoom, int x, int y, Throwable[] error);
    /**
     * Returns the minimum bounding box of the data containing region per the
     * native spatial reference of the content.
     * 
     * <P>Note that the returned value may not <I>snap</I> to the tile grid.
     * 
     * @return
     * 
     * @see #getSpatialReferenceId()
     */
    public Envelope getBounds();
    
    /**************************************************************************/

    public final static class Util {
        private Util() {}
        
        private static ZoomLevel findLevelOrThrow(TileMatrix matrix, int level) {
            final ZoomLevel retval = findZoomLevel(matrix, level);
            if(retval == null)
                throw new IllegalArgumentException();
            return retval;
        }
        
        /**
         * Finds the specified zoom level of the matrix.
         * 
         * @param matrix    A tile matrix
         * @param level     The desired zoom level
         * 
         * @return  The desired zoom level or <code>null</code> if no such zoom
         *          level exists for the matrix.
         */
        public static ZoomLevel findZoomLevel(TileMatrix matrix, int level) {
            final ZoomLevel[] levels = matrix.getZoomLevel();
            for(int i = 0; i < levels.length; i++)
                if(levels[i].level == level)
                    return levels[i];
            return null;
        }

        /**
         * Returns the index of the tile (column and row) at the specified zoom
         * level that contains location specified in native spatial reference
         * units
         *  
         * @param matrix    The tile matrix
         * @param level     The zoom level
         * @param x         The x-component of the location, in native spatial
         *                  reference units
         * @param y         The y-component of the location, in native spatial
         *                  reference units
         *                  
         * @return  The index of the tile (column and row) that contains the
         *          specified location at the specified zoom level
         */
        public static Point getTileIndex(TileMatrix matrix, int level, double x, double y) {
            ZoomLevel zoom = findLevelOrThrow(matrix, level);
            return getTileIndex(matrix.getOriginX(), matrix.getOriginY(), zoom, x, y);
        }
        
        /**
         * Returns the index of the tile (column and row) at the specified zoom
         * level that contains location specified in native spatial reference
         * units
         *  
         * @param originX   The x-component of the origin of the tile matrix,
         *                  in native spatial reference units
         * @param originY   The y-component of the origin of the tile matrix,
         *                  in native spatial reference units 
         * @param zoom      The definition of the zoom level
         * @param x         The x-component of the location, in native spatial
         *                  reference units
         * @param y         The y-component of the location, in native spatial
         *                  reference units
         *                  
         * @return  The index of the tile (column and row) that contains the
         *          specified location at the specified zoom level
         */
        public static Point getTileIndex(double originX, double originY, ZoomLevel zoom, double x, double y) {
            int tileX = (int)((x-originX)/(zoom.pixelSizeX*zoom.tileWidth));
            int tileY = (int)((originY-y)/(zoom.pixelSizeY*zoom.tileHeight));
            return new Point(tileX, tileY);
        }
        
        /**
         * Returns the bounds of the specified tile in native spatial reference
         * units.
         * 
         * @param matrix    The tile matrix
         * @param level     The zoom level
         * @param tileX     The tile column
         * @param tileY     The tile row
         * 
         * @return  The bounds of the specified tile in native spatial reference
         *          units.
         */
        public static Envelope getTileBounds(TileMatrix matrix, int level, int tileX, int tileY) {
            return getTileBounds(findLevelOrThrow(matrix, level), matrix.getOriginX(), matrix.getOriginY(), tileX, tileY);
        }

        public static Envelope getTileBounds(ZoomLevel zoom, double originX, double originY, int tileX, int tileY) {
            Envelope retval = new Envelope(Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN, 0);

            PointD point;

            point = getTilePointImpl(originX, originY, zoom, tileX, tileY, 0, zoom.tileHeight);
            retval.minX = point.x;
            retval.minY = point.y;
            point = getTilePointImpl(originX, originY, zoom, tileX, tileY, zoom.tileWidth, 0);
            retval.maxX = point.x;
            retval.maxY = point.y;

            return retval;
        }
        
        /**
         * Returns the relative pixel x,y for the specified location, in native
         * spatial reference units, within the specified tile.
         *  
         * @param matrix    The tile matrix
         * @param level     The zoom level
         * @param tileX     The tile column
         * @param tileY     The tile row
         * @param projX     The x-component of the location in native spatial
         *                  reference units
         * @param projY     The x-component of the location in native spatial
         *                  reference units
         *                  
         * @return  The relative pixel x,y within the specified tile
         */
        public static PointD getTilePixel(TileMatrix matrix, int level, int tileX, int tileY, double projX, double projY) {
            ZoomLevel zoom = findLevelOrThrow(matrix, level);
            
            double tileOriginX = matrix.getOriginX() + (tileX*zoom.pixelSizeX*zoom.tileWidth);
            double tileOriginY = matrix.getOriginY() - (tileY*zoom.pixelSizeY*zoom.tileHeight);
            
            return new PointD((projX-tileOriginX)/zoom.pixelSizeX, (tileOriginY-projY)/zoom.pixelSizeY);
        }
        
        /**
         * Returns the location, in native spatial reference units, of the
         * specified pixel in the specified tile.
         * 
         * @param matrix    The tile matrix
         * @param level     The zoom level
         * @param tileX     The tile column
         * @param tileY     The tile row
         * @param pixelX    The x-pixel offset, relative to the tile
         * @param pixelY    The x-pixel offset, relative to the tile
         * 
         * @return  The location of the pixel in native spatial reference units
         */
        public static PointD getTilePoint(TileMatrix matrix, int level, int tileX, int tileY, int pixelX, int pixelY) {
            return getTilePointImpl(matrix.getOriginX(), matrix.getOriginY(), findLevelOrThrow(matrix, level), tileX, tileY, pixelX, pixelY);
        }

        private static PointD getTilePointImpl(double originX, double originY, ZoomLevel zoom, int tileX, int tileY, int pixelX, int pixelY) {
            double x = originX+((tileX*zoom.pixelSizeX*zoom.tileWidth) + (zoom.pixelSizeX*pixelX));
            double y = originY-((tileY*zoom.pixelSizeY*zoom.tileHeight) + (zoom.pixelSizeY*pixelY));
            return new PointD(x, y);
        }
        
        public static boolean isQuadtreeable(TileMatrix matrix) {
            return false;
        }
        
        /**
         * Creates a quadtree based matrix, given the definition of the root
         * level and a number of levels.
         * 
         * @param level0    The root level of the quadtree.
         * @param numLevels The number of levels in the quadtree (including the
         *                  root).
         *                  
         * @return A quadtree based zoom level specification
         */
        public static ZoomLevel[] createQuadtree(ZoomLevel level0, int numLevels) {
            ZoomLevel[] retval = new ZoomLevel[numLevels];
            retval[0] = level0;
            for(int i = 1; i < numLevels; i++) {
                retval[i] = new ZoomLevel();
                retval[i].level = retval[i-1].level+1;
                retval[i].resolution = retval[i-1].resolution/2d;
                retval[i].tileWidth = retval[i-1].tileWidth;
                retval[i].tileHeight = retval[i-1].tileHeight;
                retval[i].pixelSizeX = retval[i-1].pixelSizeX/2d;
                retval[i].pixelSizeY = retval[i-1].pixelSizeY/2d;
            }
            return retval;
        }
    }
    
}
