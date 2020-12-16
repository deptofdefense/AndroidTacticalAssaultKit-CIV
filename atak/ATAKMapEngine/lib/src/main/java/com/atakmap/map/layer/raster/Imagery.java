package com.atakmap.map.layer.raster;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.Layer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

public final class Imagery {
    /**
     * Creates a new tiled imagery layer given the specified URL. The tile indices should be escaped as
     * <UL>
     *     <LI>{$x} - the tile column</LI>
     *     <LI>{$y} - the tile row</LI>
     *     <LI>{$z} - the tile zoom level</LI>
     * </UL>
     * And will be substituted at render time.
     *
     * <P>This method assumes that the tiles are using the OSM/Google slippy
     * tile grid scheme with a Web Mercator projection.
     *
     * @param name      The name for the layer
     * @param url       The tile server URL
     * @param cacheDir  An optionally specified cache directory
     *
     * @return  A new Layer to be added to the map or <code>null</code> if none could be created from the URL
     */
    public static Layer createTiledImageryLayer(String name, String url, File cacheDir) throws IOException {
        return createTiledImageryLayer(name, url, 0, 21, cacheDir);
    }

    public static Layer createTiledImageryLayer(String name, String url, int minZoom, int maxZoom, File cacheDir) throws IOException {
        if(url == null)
            return null;
        url = url.replace("&", "&amp;");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<customMapSource>");
        sb.append("    <name>").append(name).append("</name>");
        sb.append("    <minZoom>").append(minZoom).append("</minZoom>");
        sb.append("    <maxZoom>").append(maxZoom).append("</maxZoom>");
        sb.append("    <tileType>image/png</tileType>");
        sb.append("    <url>" + url + "</url>");
        sb.append("    <tileUpdate>false</tileUpdate>");
        sb.append("    <backgroundColor>#000000</backgroundColor>");
        sb.append("    <ignoreErrors>false</ignoreErrors>");
        sb.append("    <serverParts></serverParts>");
        sb.append("</customMapSource>");

        File config;
        if(cacheDir == null)
            config = IOProviderFactory.createTempFile("config", ".xml", null);
        else
            config = new File(cacheDir, name + ".xml");

        try(FileOutputStream fos = new FileOutputStream(config)) {
            FileSystemUtils.write(fos, sb.toString());
        }

        RuntimeRasterDataStore rds = new RuntimeRasterDataStore();
        Set<DatasetDescriptor> descs = DatasetDescriptorFactory2.create(config, cacheDir, null, null);
        if(descs != null) {
            for (DatasetDescriptor desc : descs)
                rds.add(desc);
        }

        return new DatasetRasterLayer2(name, rds, 1);
    }
}
