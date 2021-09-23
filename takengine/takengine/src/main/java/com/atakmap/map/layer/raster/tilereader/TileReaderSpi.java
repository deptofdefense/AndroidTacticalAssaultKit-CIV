package com.atakmap.map.layer.raster.tilereader;

public interface TileReaderSpi {
    public String getName();
    public TileReader create(String uri, TileReaderFactory.Options options);
    public boolean isSupported(String uri);
}
