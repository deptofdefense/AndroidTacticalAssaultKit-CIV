
package com.atakmap.map.layer.raster.mosaic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.pfps.PfpsUtils;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;
import com.atakmap.android.maps.tilesets.TilesetInfo;

public class MosaicUtils {

    public final static String TAG = "MosaicUtils";

    public final static String SUBDIR_NITF = "nitf";
    public final static String SUBDIR_GEOTIFF = "geotiff";
    public final static String SUBDIR_MRSID = "mrsid";
    public final static String SUBDIR_RPF = "rpf";



    private final static int INTERVAL = 50;

    private final static Set<String> SUBDIRS = new HashSet<String>();
    static {
        SUBDIRS.add(SUBDIR_NITF);
        SUBDIRS.add(SUBDIR_GEOTIFF);
        SUBDIRS.add(SUBDIR_MRSID);
    }

    public final static int DEFAULT_NUM_LEVELS = 3;

    private MosaicUtils() {
    }

    public interface BuildMosaicCallback{
        public void onProgressUpdate(int itemsProcessed);
    }

    public static boolean isMosaicDir(File f) {
        return isMosaicDir(f, Integer.MAX_VALUE);
    }
    
    public static boolean isMosaicDir(File f, int limit){
        if (!f.isDirectory())
            return false;
        if (PfpsUtils.isPfpsDataDir(f))
            return true;
        // didn't look like PFPS, check for our directories
        int hits = 0;
        File[] children = f.listFiles();
        if (children != null) { 
            int checkLimit = Math.min(children.length, limit);
            for (int i = 0; i < checkLimit; i++) {
                if (SUBDIRS.contains(children[i].getName().toLowerCase(LocaleUtil.getCurrent()))) {
                    if (!children[i].isDirectory())
                        continue;

                    File[] files = children[i].listFiles();
                    if (files != null && files.length > 0)
                        hits++;
                }
            }
        }

        return (hits > 0);
    }

    public static int buildMosaicDatabase(File mosaicDir, File databaseFile) {
        return buildMosaicDatabase(mosaicDir, databaseFile, null);
    }

    public static int buildMosaicDatabase(File mosaicDir, File databaseFile, BuildMosaicCallback callback) {
        AtomicInteger count = new AtomicInteger(0);

        MosaicDatabaseBuilder2 database = null;
        try {
            database = ATAKMosaicDatabase3.create(databaseFile);

            URI relativeTo = mosaicDir.toURI();
    
            
    
            database.beginTransaction();
            try {
                File[] subdirs = mosaicDir.listFiles();
                if (subdirs != null) { 
                    for (int i = 0; i < subdirs.length; i++) {
                        if (!subdirs[i].isDirectory())
                            continue;
                        if (subdirs[i].getName().equalsIgnoreCase(SUBDIR_RPF)) {
                            PfpsUtils.createRpfDataDatabase2(database, subdirs[i]);
                        } else if (SUBDIRS.contains(subdirs[i].getName().toLowerCase(LocaleUtil.getCurrent()))) {
                            buildGenericSubDatabase(database, subdirs[i], relativeTo, count, callback);
                        }
                    }
                }

                if(callback != null)
                    callback.onProgressUpdate(-100);

                database.createIndices();
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();                
            }
        } finally {
            if(database != null)
                database.close();
        }
        
        return count.get();
    }

    private static void buildGenericSubDatabase(MosaicDatabaseBuilder2 database, File subdir,
            URI relativeTo, AtomicInteger count, BuildMosaicCallback callback) {
        
        final String genericType = subdir.getName().toLowerCase(LocaleUtil.getCurrent());
        final boolean nitfChecks = genericType.equals("nitf");
        File[] children = subdir.listFiles();
        if (children == null) 
            children = new File[0];

       
        Dataset dataset;
        DatasetProjection2 proj;
        int width;
        int height;
        PointD scratch = new PointD(0, 0);
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        String type;
        boolean isDefined;
        String path;
        int srid;
        boolean isPrecisionImagery;

        for (int i = 0; i < children.length; i++) {
            int tempCount = count.getAndIncrement();
            if(callback != null && (tempCount % INTERVAL) == 0){
                callback.onProgressUpdate(tempCount);
            }

            if (children[i].isDirectory()) {
                Log.w(TAG, "Skipping " + subdir.getName() + " subdirectory, " + children[i]);
                continue;
            }

            type = null;
            width = 0;
            height = 0;
            isDefined = false;
            isPrecisionImagery = false;
            srid = -1;

            path = children[i].getAbsolutePath();
            if(nitfChecks) {
            }

            if(!isDefined) {
                dataset = null;
                try {
                    dataset = gdal.Open(path);
                    if (dataset == null) {
                        Log.w(TAG, "Unable to open mosaic frame: " + children[i]);
                        continue;
                    }

                    proj = GdalDatasetProjection2.getInstance(dataset);

                    if (proj == null) {
                        Log.w(TAG, "Unable to create dataset projection: " + children[i]);
                        continue;
                    }

                    width = dataset.GetRasterXSize();
                    height = dataset.GetRasterYSize();

                    scratch.x = 0;
                    scratch.y = 0;
                    proj.imageToGround(scratch, ul);
                    scratch.x = width;
                    scratch.y = 0;
                    proj.imageToGround(scratch, ur);
                    scratch.x = width;
                    scratch.y = height;
                    proj.imageToGround(scratch, lr);
                    scratch.x = 0;
                    scratch.y = height;
                    proj.imageToGround(scratch, ll);
                
                    srid = ((GdalDatasetProjection2)proj).getNativeSpatialReferenceID();

                    type = genericType;
                } finally {
                    if (dataset != null)
                        dataset.delete();
                }
            }
            final double gsd = DatasetDescriptor.computeGSD(width, height, ul, ur, lr, ll);
            database.insertRow(relativeTo.relativize(children[i].toURI()).getPath(),
                               type,
                               isPrecisionImagery,
                               ul,
                               ur,
                               lr,
                               ll,
                               gsd * (double)(1<<DEFAULT_NUM_LEVELS),
                               gsd,
                               width,
                               height,
                               srid);
            }
        }
}
