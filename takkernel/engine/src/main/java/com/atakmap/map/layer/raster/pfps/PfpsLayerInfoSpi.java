
package com.atakmap.map.layer.raster.pfps;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.mosaic.ATAKMosaicDatabase3;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicUtils;
import com.atakmap.map.layer.raster.AbstractDatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptorSpiArgs;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.spi.InteractiveServiceProvider;
import com.atakmap.spi.StrategyOnlyServiceProvider;
import com.atakmap.coremap.log.Log;

public class PfpsLayerInfoSpi extends AbstractDatasetDescriptorSpi implements StrategyOnlyServiceProvider<Set<DatasetDescriptor>, DatasetDescriptorSpiArgs, String> {

    public static final String TAG = "PfpsLayerInfoSpi";

    public final static DatasetDescriptorSpi INSTANCE = new PfpsLayerInfoSpi();

    private PfpsLayerInfoSpi() {
        super("pfps", 3);
    }

    @Override
    protected Set<DatasetDescriptor> create(File f, File workingDir, final InteractiveServiceProvider.Callback callback) {
        if (!IOProviderFactory.isDirectory(f))
            return null;

        MosaicDatabase2 database = null;
        DatasetDescriptor tsInfo = null;
        try {
            File mosaicDatabaseFile = new File(workingDir, "mosaicdb.sqlite");
            Log.d(TAG, "creating mosaic database file " + mosaicDatabaseFile.getName() + " for "
                    + f.getName());
            
            int count;
            long s = SystemClock.elapsedRealtime();
            if(callback != null){
                count = MosaicUtils.buildMosaicDatabase(f, mosaicDatabaseFile, new MosaicUtils.BuildMosaicCallback(){
                    @Override
                    public void onProgressUpdate(int itemsProcessed) {
                        callback.progress(itemsProcessed);
                    }
                });
            }else{
            count = MosaicUtils.buildMosaicDatabase(f, mosaicDatabaseFile);
            }
            long e = SystemClock.elapsedRealtime();

            Log.d(TAG, "mosaic scan file: " + f);
            Log.d(TAG, "Generated Mosaic Database in " + (e - s) + " ms");

            database = new ATAKMosaicDatabase3();
            database.open(mosaicDatabaseFile);

            Map<String, MosaicDatabase2.Coverage> dbCoverages = new HashMap<String, MosaicDatabase2.Coverage>();
            database.getCoverages(dbCoverages);
            if(dbCoverages.isEmpty()){
                database.close();
                database = null;
                FileSystemUtils.deleteFile(mosaicDatabaseFile);
                return null;
            }

            Map<String, String> extraData = new HashMap<String, String>();
            extraData.put("spatialdb", (new File(workingDir, "spatialdb.sqlite")).getAbsolutePath());
            extraData.put("numFrames", String.valueOf(count));

            File tilecacheDir = new File(workingDir, "tilecache");
            FileSystemUtils.delete(tilecacheDir);
            if (IOProviderFactory.exists(tilecacheDir)) {
                Log.d(TAG, "could not delete tile cache directory: " + tilecacheDir);
            }
            if(IOProviderFactory.mkdirs(tilecacheDir))
                extraData.put("tilecacheDir", tilecacheDir.getAbsolutePath());

            Set<String> types = dbCoverages.keySet();
            Map<String, Pair<Double, Double>> resolutions = new HashMap<String, Pair<Double, Double>>();
            Map<String, Geometry> coverages = new HashMap<String, Geometry>();
            
            MosaicDatabase2.Coverage coverage;
            for(String type : types) {
                coverage = database.getCoverage(type);
                resolutions.put(type, Pair.create(Double.valueOf(coverage.minGSD), Double.valueOf(coverage.maxGSD)));
                coverages.put(type, coverage.geometry);
            }
            coverages.put(null, database.getCoverage().geometry);

            tsInfo = new MosaicDatasetDescriptor(f.getName(),
                                   GdalLayerInfo.getURI(f).toString(),
                                   this.getType(),
                                   "native-mosaic",
                                   mosaicDatabaseFile,
                                   database.getType(),
                                   dbCoverages.keySet(),
                                   resolutions,
                                   coverages,
                                   EquirectangularMapProjection.INSTANCE.getSpatialReferenceID(),
                                   false,
                                   workingDir,
                                   extraData);

            return Collections.singleton(tsInfo);
        } finally {
            if (database != null)
                database.close();
        }
    }
    
    @Override
    public boolean probe(File file, InteractiveServiceProvider.Callback callback){
        return MosaicUtils.isMosaicDir(file, callback.getProbeLimit());
    }
    
    @Override
    public int parseVersion() {
        return 3;
    }
}
