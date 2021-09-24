package com.atakmap.map.layer.raster.osm;

import java.io.File;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.projection.WebMercatorProjection;

import com.atakmap.coremap.log.Log;

public class OSMDroidZipLayerInfoSpi {

    public final static String TAG = "OSMDroidZipLayerInfoSpi";

    private OSMDroidZipLayerInfoSpi() {}

    public static DatasetDescriptor parseOSMDroidZipTilecache(File cache, File basedir) {
        File[] levels = IOProviderFactory.listFiles(basedir); /* NUMBER_FILE_FILTER */
        int maxLevel = -1;
        int minLevel = -1;
        int level;
        // south, west, north, east
        double[] swne = new double[] {
                Double.NaN, Double.NaN, Double.NaN, Double.NaN
        };
        String imgExtension = null;

        if (levels != null) { 
            for (int i = 0; i < levels.length; i++) {
                if (levels[i].getName().matches("\\d+")) {
                    level = Integer.parseInt(levels[i].getName());
    
                    if (level > maxLevel)
                        maxLevel = level;
                    if (minLevel == -1 || level < minLevel)
                        minLevel = level;
                    if (level == minLevel)
                        imgExtension = fillMapnikBounds(level, IOProviderFactory.listFiles(levels[i]), swne);
                }
            }
        }
        if (maxLevel < 0)
            return null;
        for (int i = 0; i < swne.length; i++)
            if (Double.isNaN(swne[i]))
                return null;

        final double tileWidth = (360.0d / (double) (1 << minLevel));
        final double tileHeight = 170.1022d / (double) (1 << minLevel);

        TilesetInfo.Builder builder = new TilesetInfo.Builder("osmdroid-zip", "tileset");
        builder.setSpatialReferenceID(WebMercatorProjection.INSTANCE.getSpatialReferenceID());
        builder.setUri(cache.getAbsolutePath());
        builder.setName(cache.getName());
        builder.setTilePixelWidth(256);
        builder.setTilePixelHeight(256);
        builder.setLevelCount((maxLevel - minLevel) + 1);
        builder.setZeroWidth(tileWidth);
        builder.setZeroHeight(tileHeight);
        builder.setOverview(false);
        builder.setImageExt(imgExtension);
        builder.setFourCorners(new GeoPoint(swne[0], swne[1]),
                new GeoPoint(swne[2], swne[1]),
                new GeoPoint(swne[2], swne[3]),
                new GeoPoint(swne[0], swne[3]));

        builder.setPathStructure("OSM_DROID");
        builder.setSubpath(basedir.getName() + File.separator);
        builder.setLevelOffset(minLevel);
        builder.setGridOriginLat(OSMUtils.mapnikTileLat(0, 1));

        final int gridMinX = OSMUtils.mapnikTileX(minLevel, swne[1]);
        final int gridMinY = OSMUtils.mapnikTileY(minLevel, swne[2]);
        final int gridMaxX = Math.max(OSMUtils.mapnikTileX(minLevel, swne[3]) - 1, gridMinX);
        final int gridMaxY = Math.max(OSMUtils.mapnikTileY(minLevel, swne[0]) - 1, gridMinY);

        Log.d(TAG, "osm grid min=" + gridMinX + "," + gridMinY + " max=" + gridMaxX + ","
                + gridMaxY);

        builder.setGridOffsetX(gridMinX);
        // int tmsLatIndex = (1 << level) - latIndex - 1;
        builder.setGridOffsetY((1 << minLevel) - gridMaxY - 1);
        builder.setGridWidth(gridMaxX - gridMinX + 1);
        builder.setGridHeight(gridMaxY - gridMinY + 1);

        return builder.build();
    }

    private static String fillMapnikBounds(int level, File[] tileDirs, double[] swne) {
        if (tileDirs == null || tileDirs.length < 1)
            return null;

        File[] tileImageFiles;
        String imageName;
        int xtile;
        int ytile;
        double tileLat;
        double tileLng;
        String imgExtension = null;
        for (int i = 0; i < tileDirs.length; i++) {
            if (!tileDirs[i].getName().matches("\\d+"))
                continue;
            xtile = Integer.parseInt(tileDirs[i].getName());
            // eastern bound
            tileLng = OSMUtils.mapnikTileLng(level, xtile);
            if (Double.isNaN(swne[1]) || tileLng < swne[1])
                swne[1] = tileLng;
            if (Double.isNaN(swne[3]) || tileLng > swne[3])
                swne[3] = tileLng;
            // western bound
            tileLng = OSMUtils.mapnikTileLng(level, xtile + 1);
            if (Double.isNaN(swne[1]) || tileLng < swne[1])
                swne[1] = tileLng;
            if (Double.isNaN(swne[3]) || tileLng > swne[3])
                swne[3] = tileLng;
            tileImageFiles = IOProviderFactory.listFiles(tileDirs[i]);

            if (tileImageFiles != null) { 
                for (int j = 0; j < tileImageFiles.length; j++) {
                    imageName = tileImageFiles[j].getName();
                    if (imageName.indexOf('.') > 0) {
                        if (imgExtension == null)
                            imgExtension = imageName.substring(imageName.indexOf('.'));
                        imageName = imageName.substring(0, imageName.indexOf('.'));
                    }
                    if (!imageName.matches("\\d+"))
                        continue;
                    ytile = Integer.parseInt(imageName);
    
                    // northern bound
                    tileLat = OSMUtils.mapnikTileLat(level, ytile);
                    if (Double.isNaN(swne[0]) || tileLat < swne[0])
                        swne[0] = tileLat;
                    if (Double.isNaN(swne[2]) || tileLat > swne[2])
                        swne[2] = tileLat;
                    // southern bound
                    tileLat = OSMUtils.mapnikTileLat(level, ytile + 1);
                    if (Double.isNaN(swne[0]) || tileLat < swne[0])
                        swne[0] = tileLat;
                    if (Double.isNaN(swne[2]) || tileLat > swne[2])
                        swne[2] = tileLat;
                }
            }
        }
        return imgExtension;
    }

}
