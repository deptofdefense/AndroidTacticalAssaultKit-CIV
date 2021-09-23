package com.atakmap.map.layer.raster.mbtiles;

import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;


/**
 * This class provides the Tile Reader SPI for handling the MOMAP data type
 *
 */
public final class MOMAPTilesTileReader
{
    /**
     * This is the SPI for supporting the MOMAP Tiles
     */
    public final static TileReaderSpi SPI = new TileReaderSpi( )
    {
        @Override
        public String getName( )
        {
            return "momap";
        }

        @Override
        public TileReader create( String uri, Options options )
        {
            return MBTilesTileReader.SPI.create( uri, options );
        }

        @Override
        public boolean isSupported( String uri )
        {
            return MBTilesTileReader.SPI.isSupported( uri );
        }
    };

    private MOMAPTilesTileReader( )
    {
    }
}
