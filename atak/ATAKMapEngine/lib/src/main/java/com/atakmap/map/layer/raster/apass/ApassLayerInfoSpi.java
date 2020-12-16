
package com.atakmap.map.layer.raster.apass;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.DistanceCalculations;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import android.util.Pair;

import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.PrecisionImagery;
import com.atakmap.map.layer.raster.PrecisionImageryFactory;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.pfps.PfpsMapType;
import com.atakmap.map.layer.raster.pfps.PfpsMapTypeFrame;
import com.atakmap.map.layer.raster.pfps.PfpsUtils;
import com.atakmap.map.layer.raster.mosaic.ATAKMosaicDatabase3;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseBuilder2;
import com.atakmap.map.layer.raster.mosaic.MosaicUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.database.Databases;
import com.atakmap.map.layer.raster.AbstractDatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorSpi;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.spi.InteractiveServiceProvider;
/**
 * {@link DatasetDescriptorSpi} implementation for APASS datasets. Expected input is
 * the APASS catalog file (images.pfiva.sqlite3).
 * 
 * @author Developer
 */
public class ApassLayerInfoSpi extends AbstractDatasetDescriptorSpi {

    public static final String TAG = "ApassLayerInfoSpi";

    private final static double NOMINAL_METERS_PER_DEGREE = DistanceCalculations
            .metersFromAtSourceTarget(new GeoPoint(0.5d, 0.0d), new GeoPoint(-0.5d, 0.0d));

    private final static Set<String> IMAGES_TABLE_COLUMN_NAMES = new HashSet<String>();
    static {
        IMAGES_TABLE_COLUMN_NAMES.add("path");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_ul_y");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_ul_x");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_ur_y");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_ur_x");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_lr_y");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_lr_x");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_ll_y");
        IMAGES_TABLE_COLUMN_NAMES.add("corner_ll_x");
        IMAGES_TABLE_COLUMN_NAMES.add("gsd");
        IMAGES_TABLE_COLUMN_NAMES.add("targetting");
        IMAGES_TABLE_COLUMN_NAMES.add("grg");
        IMAGES_TABLE_COLUMN_NAMES.add("width");
        IMAGES_TABLE_COLUMN_NAMES.add("height");
    }

    public final static DatasetDescriptorSpi INSTANCE = new ApassLayerInfoSpi();

    private ApassLayerInfoSpi() {
        super("apass", 0);
    }

    @Override
    public Set<DatasetDescriptor> create(File f, File workingDir, InteractiveServiceProvider.Callback callback) {
        final File apassDatabaseFile = f;
        MosaicDatabase2 database = null;
        try {
            Map<String, String> extraData = new HashMap<String, String>();
            File atakDbFile = new File(workingDir, "mosaicdb.sqlite");

            final int count = convertDatabase(apassDatabaseFile, atakDbFile, callback); 
            if (count < 1)
                return null;

            extraData.put("spatialdb", (new File(workingDir, "spatialdb.sqlite"))
                    .getAbsolutePath());
            extraData.put("numFrames", String.valueOf(count));

            File tilecacheDir = new File(workingDir, "tilecache");
            FileSystemUtils.delete(tilecacheDir);
            if(IOProviderFactory.mkdirs(tilecacheDir))
                extraData.put("tilecacheDir", tilecacheDir.getAbsolutePath());

            database = new ATAKMosaicDatabase3();
            database.open(atakDbFile);
            
            Map<String, MosaicDatabase2.Coverage> dbCoverages = new HashMap<String, MosaicDatabase2.Coverage>(); 
            database.getCoverages(dbCoverages);
            if(dbCoverages.isEmpty())
                return null;

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

            DatasetDescriptor tsInfo = new MosaicDatasetDescriptor(f.getParentFile().getName(),
                                             GdalLayerInfo.getURI(f.getParentFile()).toString(),
                                             this.getType(),
                                             "native-mosaic",
                                             atakDbFile,
                                             database.getType(),
                                             dbCoverages.keySet(),
                                             resolutions,
                                             coverages,
                                             EquirectangularMapProjection.INSTANCE.getSpatialReferenceID(),
                                             false,
                                             workingDir,
                                             extraData);
            return Collections.singleton(tsInfo);
        } catch(Throwable t) {
            Log.e(TAG, "Failed to parse as APASS DB", t);
            return null;
        } finally {
            if (database != null)
                database.close();
        }
    }

    @Override 
    public boolean probe(File file, InteractiveServiceProvider.Callback callback){
        // If this file is a database, and it has a table with the right columns
        // and at least one row, then it will probably be able to produce a layer
        // from this Spi.

        if (!IOProviderFactory.isDatabase(file)){
            return false;
        }

        DatabaseIface apassDb = null;
        CursorIface result = null;
        try {
            apassDb = IOProviderFactory.createDatabase(file, DatabaseInformation.OPTION_READONLY);


            result = apassDb.query("SELECT 1 FROM images LIMIT 1", null);
            if(result.moveToNext()){
                return true;
            }else{
                return false;
            }
        }catch(Throwable e){
            return false;
        }finally {
            if (result != null){
                result.close();
            }
            if (apassDb != null){
                apassDb.close();
            }
        }
    }

    public static int convertDatabase(File apassDbPath, File atakDbPath) {
        return convertDatabase(apassDbPath, atakDbPath, null);
    }


    public static int convertDatabase(File apassDbPath, File atakDbPath, InteractiveServiceProvider.Callback callback) {
        if (!IOProviderFactory.isDatabase(apassDbPath)){
            return 0;
        }

        DatabaseIface apassDb = null;
        File apassDataDir = apassDbPath.getParentFile();

        MosaicDatabaseBuilder2 atakDb = ATAKMosaicDatabase3.create(atakDbPath);
        atakDb.beginTransaction();

        CursorIface result = null;
        try {
            apassDb = IOProviderFactory.createDatabase(new File(apassDbPath.getAbsolutePath()), DatabaseInformation.OPTION_READONLY);

            // Set<String> s = Databases.getColumnNames(apassDb, "images");
            // if(s == null || !s.containsAll(IMAGES_TABLE_COLUMN_NAMES))
            // return false;

            String[] cols = IMAGES_TABLE_COLUMN_NAMES
                    .toArray(new String[0]);
            StringBuilder sql = new StringBuilder("SELECT ");
            sql.append(cols[0]);
            for(int i = 1; i < cols.length; i++)
                sql.append(", ").append(cols[i]);
            sql.append(" FROM images");
            result = apassDb.query(sql.toString(), null);

            File frame;
            GeoPoint ul = GeoPoint.createMutable();
            GeoPoint ur = GeoPoint.createMutable();
            GeoPoint lr = GeoPoint.createMutable();
            GeoPoint ll = GeoPoint.createMutable();
            int colPath = -1;
            int colULLat = -1;
            int colULLng = -1;
            int colURLat = -1;
            int colURLng = -1;
            int colLRLat = -1;
            int colLRLng = -1;
            int colLLLat = -1;
            int colLLLng = -1;
            int colGsd = -1;
            int colTargetting = -1;
            int colGrg = -1;
            int colWidth = -1;
            int colHeight = -1;

            String path;
            double apassGsd;
            int width;
            int height;
            boolean grg;
            boolean targetting;
            String type;
            double gsd;
            String frameName;
            int count = 0;

            while (result.moveToNext()) {
                if(callback != null && (count % 100) == 0){
                    callback.progress(count);
                }
                count += 1;

                if (colPath == -1) {
                    colPath = result.getColumnIndex("path");
                    colULLat = result.getColumnIndex("corner_ul_y");
                    colULLng = result.getColumnIndex("corner_ul_x");
                    colURLat = result.getColumnIndex("corner_ur_y");
                    colURLng = result.getColumnIndex("corner_ur_x");
                    colLRLat = result.getColumnIndex("corner_lr_y");
                    colLRLng = result.getColumnIndex("corner_lr_x");
                    colLLLat = result.getColumnIndex("corner_ll_y");
                    colLLLng = result.getColumnIndex("corner_ll_x");
                    colGsd = result.getColumnIndex("gsd");
                    colTargetting = result.getColumnIndex("targetting");
                    colGrg = result.getColumnIndex("grg");
                    colWidth = result.getColumnIndex("width");
                    colHeight = result.getColumnIndex("height");
                }

                path = result.getString(colPath);

                try { 
                    frame = new File(apassDataDir, 
                                     FileSystemUtils.validityScan(path));
                } catch (IOException ioe) { 
                    Log.w(TAG, "validity check failed for: " + apassDataDir + "/" + path);
                    frame = null;
                }
                if (frame == null || !IOProviderFactory.exists(frame))
                    continue;

                ul.set(result.getDouble(colULLat), result.getDouble(colULLng));
                ur.set(result.getDouble(colURLat), result.getDouble(colURLng));
                lr.set(result.getDouble(colLRLat), result.getDouble(colLRLng));
                ll.set(result.getDouble(colLLLat), result.getDouble(colLLLng));

                apassGsd = result.getDouble(colGsd);
                grg = (result.getInt(colGrg) == 1);
                targetting = (result.getInt(colTargetting) == 1);
                width = result.getInt(colWidth);
                height = result.getInt(colHeight);

                gsd = Double.NaN;

                if (grg) {
                    type = "GRG";
                } else if (targetting) {
                    type = "Unknown";

                    PrecisionImagery img = PrecisionImageryFactory.create(frame.getAbsolutePath());
                    // don't trust the databases registration; compute the
                    // corner coordinates ourselves based off of the image
                    if(img != null) {
                        ImageInfo info = img.getInfo();
                        if(info != null) {
                            type = info.type;
                            ul.set(info.upperLeft);
                            ur.set(info.upperRight);
                            lr.set(info.lowerRight);
                            ll.set(info.lowerLeft);
                        }
                    }
                } else {
                    type = null;

                    frameName = frame.getName();
                    if (frameName.length() == 12) {
                        PfpsMapType pfpsType = PfpsUtils.getMapType(frameName);
                        if (pfpsType != null) {
                            type = PfpsMapTypeFrame.getRpfPrettyName(pfpsType.shortName);
                            if(type == null)
                                type = pfpsType.folderName;

                            gsd = pfpsType.scale;
                            if (pfpsType.scaleUnits == PfpsMapType.SCALE_UNIT_SCALE)
                                gsd = PfpsUtils.cadrgScaleToCibResolution(1.0d / gsd);
                        } else {
                            type = PfpsMapTypeFrame.getRpfPrettyName(frame);
                        }
                    }

                    if (type == null && frameName.lastIndexOf('.') > 0) {
                        type = frameName.substring(frameName.lastIndexOf('.') + 1);
                    } else if (type == null) {
                        type = "Unknown";
                    }
                }

                if (Double.isNaN(gsd))
                    gsd = apassGsd * NOMINAL_METERS_PER_DEGREE;

                final int numLevels = Math.min(MosaicUtils.DEFAULT_NUM_LEVELS, TileReader.getNumResolutionLevels(width, height, 512, 512));
                atakDb.insertRow(result.getString(colPath),
                        type,
                        targetting,
                        ul,
                        ur,
                        lr,
                        ll,
                        gsd * (double)(1<<numLevels),
                        gsd,
                        width,
                        height,
                        4326);
            }

            atakDb.createIndices();

            atakDb.setTransactionSuccessful();

            return count;
        } finally {
            if (result != null)
                result.close();
            atakDb.endTransaction();
            if (apassDb != null)
                apassDb.close();
            
            if(callback != null)
                callback.progress(-100);
            atakDb.close();
        }
    }

    @Override
    public int parseVersion() {
        return 9;
    }
}
