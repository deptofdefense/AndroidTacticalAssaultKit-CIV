package com.atakmap.map.layer.raster.mobileimagery;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import android.net.Uri;

import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Objects;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.projection.Projection;

final class MobileImageryTilesetSupport {

    
    final static Map<String, Collection<TilesetInfo>> descriptorCache = new HashMap<String, Collection<TilesetInfo>>();

    /**************************************************************************/

    static DatasetDescriptor reserveAggregateDescriptor(String name, Collection<DatasetDescriptor> descs) {
        
        LinkedList<TilesetInfo> infos = new LinkedList<TilesetInfo>();
        
        for(DatasetDescriptor desc : descs) {
            // XXX - need more elegant way of determining tileset
            if(!(desc instanceof ImageDatasetDescriptor))
                continue;
            // XXX -
            try {
                infos.add(new TilesetInfo((ImageDatasetDescriptor)desc));
            } catch(Throwable ignored) {}
        }

        final String key = UUID.randomUUID().toString(); 
        final TilesetInfo retval = createTilesetInfoImpl(key, name, infos);
        
        synchronized(descriptorCache) {
            descriptorCache.put(key, infos);
        }
        
        return (retval!=null) ? retval.getInfo() : null;
    }
    
    static void releaseAggregateDescriptor(DatasetDescriptor desc) {
        try {
            Uri uri = Uri.parse(desc.getUri());
            if(!Objects.equals(uri.getScheme(), "mobileimagery"))
                return;
            final String key = uri.getFragment();
            if(key != null) {
                synchronized(descriptorCache) {
                    descriptorCache.remove(key);
                }
            }
        } catch(Exception ignored) {}
    }

    private static TilesetInfo createTilesetInfoImpl(String key, String name, Collection<TilesetInfo> infos) {
        if(infos.size() == 1)
            return infos.iterator().next();

        int minLevel = Integer.MAX_VALUE;
        // max level is INCLUSIVE
        int maxLevel = 0;
        
        int gridMinX;
        int gridMinY;
        int gridMaxX;
        int gridMaxY;

        boolean first = true;

        boolean online = false;
        int srid = -1;

        double minLat = Double.POSITIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double maxLng = Double.NEGATIVE_INFINITY;

        int levelOffset;
        for(TilesetInfo info : infos) {
            levelOffset = Integer.parseInt(DatasetDescriptor.getExtraData(info.getInfo(), "levelOffset",
                    "0"));
            if(levelOffset < minLevel)
                minLevel = levelOffset;
            if((levelOffset+info.getLevelCount()-1) > maxLevel)
                maxLevel = (levelOffset+info.getLevelCount()-1);
            
            online |= info.getInfo().isRemote();
            
            if(first) {
                srid = info.getInfo().getSpatialReferenceID();

                first = false;
            }
        }
        
        // XXX - quickfix for local only imagery
        minLevel = 0;
        
        TilesetInfo.Builder builder = new TilesetInfo.Builder("mobile-imagery", "mobile-imagery");
        builder.setPathStructure("MOBILE_IMAGERY");
        builder.setIsOnline(true);
        //builder.setSupportSpi(SPI.getName());
        builder.setSpatialReferenceID(srid);
        builder.setIsOnline(online);

        gridMinX = 0;
        gridMinY = 0;
        gridMaxX = 0;
        gridMaxY = 0;

        Projection layerProjection = MobileImageryRasterLayer2.getProjection(srid);
        if(layerProjection != null) {
            maxLat = layerProjection.getMaxLatitude();
            minLng = layerProjection.getMinLongitude();
            minLat = layerProjection.getMinLatitude();
            maxLng = layerProjection.getMaxLongitude();
        } else {
            maxLat = 90d;
            minLng = -180d;
            minLat = -90d;
            maxLng = 180d;
        }
        
        if(srid == 4326)
            gridMaxX = 1;

        builder.setFourCorners(new GeoPoint(minLat, minLng),
                new GeoPoint(maxLat, minLng),
                new GeoPoint(maxLat, maxLng),
                new GeoPoint(minLat, maxLng));

        builder.setExtra("gridMinX", String.valueOf(gridMinX));
        builder.setExtra("gridMinY", String.valueOf(gridMinY));
        builder.setExtra("gridMaxX", String.valueOf(gridMaxX));
        builder.setExtra("gridMaxY", String.valueOf(gridMaxY));
        builder.setExtra("minLevel", String.valueOf(minLevel));
        builder.setExtra("maxLevel", String.valueOf(maxLevel));

        final int gridWidth = (gridMaxX-gridMinX) + 1;
        final int gridHeight = (gridMaxY-gridMinY) + 1;

        builder.setZeroHeight((maxLat-minLat) / gridHeight);
        builder.setZeroWidth((maxLng-minLng) / gridWidth);
        builder.setGridOriginLat(minLat);
        builder.setGridOriginLng(minLng);

        builder.setGridOffsetX(gridMinX);
        builder.setGridOffsetY(gridMinY);

        builder.setUri("mobileimagery://#" + key);
        builder.setName(name);
        builder.setLevelOffset(minLevel);
        builder.setLevelCount(maxLevel - minLevel + 1);
        builder.setGridWidth(gridWidth);
        builder.setGridHeight(gridHeight);

        // XXX - forcing everything to 256x256 in the client for 2.0
        builder.setTilePixelWidth(256);
        builder.setTilePixelHeight(256);

        builder.setImageryType(name);
        
        builder.setComputeDimensionFromCoverage(false);
        
        //TilesetInfo tsInfo = new TilesetInfo((ImageDatasetDescriptor)builder.build());
        //System.out.println("AGGREGATE name=" + name);
        //System.out.println("AGGREGATE gridoffset=" + tsInfo.getGridOffsetX() + "," + tsInfo.getGridOffsetY());
        //System.out.println("AGGREGATE gridsize=" + tsInfo.getGridWidth() + "," + tsInfo.getGridHeight());
        //System.out.println("AGGREGATE tile0=" + tsInfo.getZeroWidth() + "," + tsInfo.getZeroHeight());
        //System.out.println("AGGREGATE origin=" + tsInfo.getGridOriginLat() + "," + tsInfo.getGridOriginLng());
        //System.out.println("AGGREGATE minLat=" + minLat + ",minLng=" + minLng + ",maxLat=" + maxLat + ",maxLng=" + maxLng);
        return new TilesetInfo((ImageDatasetDescriptor)builder.build());
    }
}
