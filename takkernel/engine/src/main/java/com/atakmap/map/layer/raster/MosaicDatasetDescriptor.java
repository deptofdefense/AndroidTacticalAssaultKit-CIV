package com.atakmap.map.layer.raster;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import android.util.Pair;

import com.atakmap.map.layer.feature.geometry.Geometry;

public class MosaicDatasetDescriptor extends DatasetDescriptor {

    private final File mosaicDbFile;
    private final String mosaicDbProvider;

    public MosaicDatasetDescriptor(String name,
                                   String uri,
                                   String provider,
                                   String datasetType,
                                   File mosaicDbFile,
                                   String mosaicDbProvider,
                                   Collection<String> imageryTypes,
                                   Map<String, Pair<Double, Double>> resolutions,
                                   Map<String, Geometry> coverages,
                                   int srid, 
                                   boolean isRemote,
                                   File workingDir,
                                   Map<String, String> extraData) {
        
        this(0L,
             name,
             uri,
             provider,
             datasetType,
             mosaicDbFile,
             mosaicDbProvider,
             imageryTypes,
             resolutions,
             coverages,
             srid, 
             isRemote,
             workingDir,
             extraData);
    }

    MosaicDatasetDescriptor(long layerId,
                            String name,
                            String uri,
                            String provider,
                            String datasetType,
                            File mosaicDbFile,
                            String mosaicDbProvider,
                            Collection<String> imageryTypes,
                            Map<String, Pair<Double, Double>> resolutions,
                            Map<String, Geometry> coverages,
                            int srid, 
                            boolean isRemote,
                            File workingDir,
                            Map<String, String> extraData) {
        
        super(layerId,
              name,
              uri,
              provider,
              datasetType,
              imageryTypes,
              resolutions,
              coverages,
              srid,
              isRemote,
              workingDir,
              extraData);

        this.mosaicDbFile = mosaicDbFile;
        this.mosaicDbProvider = mosaicDbProvider;
    }
    
    public File getMosaicDatabaseFile() {
        return this.mosaicDbFile;
    }
    
    public String getMosaicDatabaseProvider() {
        return this.mosaicDbProvider;
    }
}
