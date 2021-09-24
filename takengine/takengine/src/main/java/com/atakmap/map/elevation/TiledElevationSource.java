package com.atakmap.map.elevation;

import android.graphics.Point;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.projection.Projection;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.math.PointD;
import com.atakmap.spatial.GeometryTransformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class TiledElevationSource extends ElevationSource {
    public enum TileScheme {
        /** Google/OSM mercator based quadtree tiling scheme */
        WebMercator,
        /** WGS84 based tiling scheme, with each hemisphere divided into its own quadtree*/
        Flat,
        /** WGS84 based quadtree tiling scheme, with level 0 bounds[-180,-180:180,180]*/
        FlatQuad,
        /** Custom defined tiling scheme */
        Custom,
    }

    private String name;
    private int tileGridSrid;
    private Envelope bounds;
    private double tileGridOriginX;
    private double tileGridOriginY;
    private TileScheme scheme;
    private TileMatrix.ZoomLevel[] zoomLevels;
    private boolean authoritative;
    private Projection projection;

    protected TiledElevationSource(String name,
                                   int tileGridSrid,
                                   Envelope bounds,
                                   double tileGridOriginX,
                                   double tileGridOriginY,
                                   TileScheme scheme,
                                   TileMatrix.ZoomLevel[] zoomLevels,
                                   boolean authoritative) {
        this.name = name;
        this.tileGridSrid = tileGridSrid;
        this.bounds = GeometryTransformer.transform(GeometryFactory.fromEnvelope(bounds), this.tileGridSrid, 4326).getEnvelope();
        this.tileGridOriginX = tileGridOriginX;
        this.tileGridOriginY = tileGridOriginY;
        this.scheme = scheme;
        this.zoomLevels = zoomLevels;
        this.authoritative = authoritative;
        this.projection = ProjectionFactory.getProjection(this.tileGridSrid);
    }

    public abstract ElevationChunk getTerrainTile(int zoom, int x, int y);

    /**
     * Returns the name of the tiled content.
     *
     * @return  The name of the tilted content.
     */
    @Override
    public String getName() {
        return this.name;
    }
    /**
     * Returns the definition of the zoom levels associated with the content.
     *
     * @return  The definition of the zoom levels associated with the content.
     */
    public TileMatrix.ZoomLevel[] getZoomLevel(){
        return this.zoomLevels;
    }

    public TileScheme getTileScheme() {
        return this.scheme;
    }

    public boolean isAuthoritative() {
        return this.authoritative;
    }

    public double getElevation(double latitude, double longitude, String[] type) {
        PointD p = this.projection.forward(new GeoPoint(latitude, longitude), null);

        for(int i = this.zoomLevels.length-1; i >= 0; i--) {
            Point tilexy = TileMatrix.Util.getTileIndex(this.tileGridOriginX, this.tileGridOriginY, this.zoomLevels[i], p.x, p.y);
            ElevationChunk tile = this.getTerrainTile(this.zoomLevels[i].level, tilexy.x, tilexy.y);
            if(tile == null)
                continue;
            final double el = tile.sample(latitude, longitude);
            if(Double.isNaN(el))
                continue;
            if(type != null)
                type[0] = tile.getType();
            return el;
        }

        return Double.NaN;
    }

    /**
     * Returns the approximate bounds of the content, in WGS84 coordinates. The
     * 'x' component shall refer to Longitude and the 'y' component shall refer
     * to Latitude.
     * @return
     */
    @Override
    public Envelope getBounds() {
        return this.bounds;
    }

    @Override
    public Cursor query(QueryParameters params) {
        return new CursorImpl(params);
    }

    /*************************************************************************/

    public static class Factory {
        public static interface TileFetcher {
            public ElevationChunk get(int zoom, int x, int y);
        }

        public static TiledElevationSource createTerrainSource(String name,
                                                               TileScheme scheme,
                                                               boolean authoritative,
                                                               int minZoom,
                                                               int maxZoom,
                                                               final TileFetcher fetcher) {

            return createTerrainSource(name, scheme, authoritative, minZoom, maxZoom, null, fetcher);
        }

        /**
         *
         * @param name          The name of the source
         * @param scheme        The tiling scheme
         * @param authoritative <code>true</code> if the elevation source data is authoritative,
         *                      <code>false</code> otherwise
         * @param minZoom       The minimum tile zoom level present in the source data
         * @param maxZoom       The maximum tile zoom level present in the source data
         * @param dataBounds    Specifies the data containing region in the tile grid, expressed
         *                      as x=longitude, y=latitude.
         * @param fetcher       The tile fetcher implementation
         * @return
         */
        public static TiledElevationSource createTerrainSource(String name,
                                                               TileScheme scheme,
                                                               boolean authoritative,
                                                               int minZoom,
                                                               int maxZoom,
                                                               Envelope dataBounds,
                                                               final TileFetcher fetcher) {
            TileMatrix.ZoomLevel min;
            Envelope gridBounds;
            int srid;
            switch(scheme) {
                case WebMercator:
                    min = new TileMatrix.ZoomLevel();
                    min.level = minZoom;
                    min.resolution = OSMUtils.mapnikTileResolution(minZoom);
                    min.pixelSizeX = min.resolution;
                    min.pixelSizeY = min.resolution;
                    min.tileWidth = 256;
                    min.tileHeight = 256;
                    gridBounds = new Envelope(
                            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 1), OSMUtils.mapnikTileLng(0, 0)), null).x,
                            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 1), OSMUtils.mapnikTileLng(0, 0)), null).y,
                            0d,
                            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 0), OSMUtils.mapnikTileLng(0, 1)), null).x,
                            WebMercatorProjection.INSTANCE.forward(new GeoPoint(OSMUtils.mapnikTileLat(0, 0), OSMUtils.mapnikTileLng(0, 1)), null).y,
                            0d);
                    srid = 3857;
                    break;
                case Flat:
                    min = new TileMatrix.ZoomLevel();
                    min.level = minZoom;
                    min.resolution = OSMUtils.mapnikTileResolution(minZoom+1);
                    min.pixelSizeX = 180d / (double)(256<<minZoom);
                    min.pixelSizeY = 180d / (double)(256<<minZoom);
                    min.tileWidth = 256;
                    min.tileHeight = 256;
                    gridBounds = new Envelope(-180d, -90d, 0d, 180d, 90d, 0d);
                    srid = 4326;
                    break;
                case FlatQuad:
                    min = new TileMatrix.ZoomLevel();
                    min.level = minZoom;
                    min.resolution = OSMUtils.mapnikTileResolution(minZoom);
                    min.pixelSizeX = 360d / (double)(256<<minZoom);
                    min.pixelSizeY = 360d / (double)(256<<minZoom);
                    min.tileWidth = 256;
                    min.tileHeight = 256;
                    gridBounds = new Envelope(-180d, -180d, 0d, 180d, 180d, 0d);
                    srid = 4326;
                    break;
                default :
                    throw new IllegalArgumentException();
            }

            if(dataBounds == null)
                dataBounds = gridBounds;
            else if(srid != 4326)
                dataBounds = GeometryTransformer.transform(GeometryFactory.fromEnvelope(dataBounds), 4326, srid).getEnvelope();

            TileMatrix.ZoomLevel[] levels = TileMatrix.Util.createQuadtree(min, maxZoom-minZoom+1);

            return new TiledElevationSource(name, srid, dataBounds, gridBounds.minX, gridBounds.maxY, scheme, levels, authoritative) {
                @Override
                public ElevationChunk getTerrainTile(int zoom, int x, int y) {
                    return fetcher.get(zoom, x, y);
                }

                @Override
                public void addOnContentChangedListener(OnContentChangedListener l) {}
                @Override
                public void removeOnContentChangedListener(OnContentChangedListener l) {}
            };
        }
    }

    private class TileIterator implements Iterator<Point> {

        int x;
        int y;
        /** start tile index, inclusive */
        final Point start;
        /** end tile index, exclusive */
        final Point end;

        TileIterator(TileMatrix.ZoomLevel level, Geometry aoi) {
            Envelope aoib;
            if(aoi == null) {
                aoib = getBounds();
            } else {
                aoib = aoi.getEnvelope();
                aoib.minX = Math.max(bounds.minX, aoib.minX);
                aoib.minY = Math.max(bounds.minY, aoib.minY);
                aoib.maxX = Math.min(bounds.maxX, aoib.maxX);
                aoib.maxY = Math.min(bounds.maxY, aoib.maxY);
            }

            // transform to native SRID
            if(tileGridSrid != 4326) {
                aoib = GeometryTransformer.transform(GeometryFactory.fromEnvelope(aoib), 4326, tileGridSrid).getEnvelope();
            }

            start = TileMatrix.Util.getTileIndex(tileGridOriginX, tileGridOriginY, level, aoib.minX, aoib.maxY);
            end = TileMatrix.Util.getTileIndex(tileGridOriginX, tileGridOriginY, level, aoib.maxX, aoib.minY);
            x = start.x;
            y = start.y;

            // make exclusive
            end.x = end.x+1;
            end.y = end.y+1;
        }

        @Override
        public boolean hasNext() {
            return (y<end.y) && (x<end.x);
        }

        @Override
        public Point next() {
            if(!hasNext())
                throw new NoSuchElementException();
            final Point retval = new Point(x++, y);
            if(x == end.x) {
                x = start.x;
                y++;
            } else if(x > end.x) {
                throw new IllegalStateException();
            }
            return retval;
        }

        @Override
        public void remove() {}
    }

    protected class CursorImpl implements Cursor {

        ArrayList<TileMatrix.ZoomLevel> levels;
        Iterator<TileMatrix.ZoomLevel> levelIter;
        int zoomLevel;
        Point tile;
        Iterator<Point> tileIter;
        QueryParameters filter;

        protected CursorImpl(QueryParameters filter) {
            this.levelIter = null;
            this.zoomLevel = -1;
            this.tile = null;
            this.tileIter = null;
            this.filter = filter;

            this.levels = new ArrayList<TileMatrix.ZoomLevel>(Arrays.asList(zoomLevels));
            Collections.sort(this.levels, new Comparator<TileMatrix.ZoomLevel>() {
                @Override
                public int compare(TileMatrix.ZoomLevel lhs, TileMatrix.ZoomLevel rhs) {
                    return rhs.level-lhs.level;
                }
            });

            // cap to minimum resolution
            if(filter != null && !Double.isNaN(filter.minResolution)) {
                while(!this.levels.isEmpty()) {
                    if(this.levels.get(this.levels.size()-1).resolution > filter.minResolution)
                        this.levels.remove(this.levels.size()-1);
                    else
                        break;
                }
            }
            // cap to maximum resolution
            if(filter != null && !Double.isNaN(filter.maxResolution)) {
                while(!this.levels.isEmpty()) {
                    if(this.levels.get(0).resolution < filter.maxResolution)
                        this.levels.remove(0);
                    else
                        break;
                }
            }

            // if ascending, reverse
            if(filter != null && filter.order != null) {
                for(QueryParameters.Order order : filter.order) {
                    switch(order) {
                        case ResolutionAsc:
                        case ResolutionDesc:
                            break;
                        case CEAsc:
                        case CEDesc:
                        case LEAsc:
                        case LEDesc:
                        default :
                            continue;
                    }
                    if (order == QueryParameters.Order.ResolutionAsc)
                        Collections.reverse(this.levels);
                    break;
                }
            }
        }

        @Override
        public ElevationChunk get() {
            return getTerrainTile(zoomLevel, tile.x, tile.y);
        }

        @Override
        public double getResolution() {
            return get().getResolution();
        }

        @Override
        public boolean isAuthoritative() {
            return get().isAuthoritative();
        }

        @Override
        public double getCE() {
            return get().getCE();
        }

        @Override
        public double getLE() {
            return get().getLE();
        }

        @Override
        public String getUri() {
            return get().getUri();
        }

        @Override
        public String getType() {
            return get().getType();
        }

        @Override
        public Geometry getBounds() {
            return get().getBounds();
        }

        @Override
        public int getFlags() {
            return get().getFlags();
        }

        @Override
        public boolean moveToNext() {
            if(this.levelIter == null) {
                if(this.levels.isEmpty())
                    return false;
                this.levelIter = this.levels.iterator();
                TileMatrix.ZoomLevel level = this.levelIter.next();
                this.zoomLevel = level.level;
                this.tileIter = new TileIterator(level, this.filter != null ? this.filter.spatialFilter : null);
            }
            do {
                while (!this.tileIter.hasNext()) {
                    if (!this.levelIter.hasNext())
                        return false;
                    TileMatrix.ZoomLevel level = this.levelIter.next();
                    this.zoomLevel = level.level;
                    this.tileIter = new TileIterator(level, this.filter != null ? this.filter.spatialFilter : null);
                }

                this.tile = this.tileIter.next();
                if(this.get() == null)
                    continue;
                if(!accept(this, this.filter))
                    continue;

                return true;
            } while(true);
        }

        @Override
        public void close() {

        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
