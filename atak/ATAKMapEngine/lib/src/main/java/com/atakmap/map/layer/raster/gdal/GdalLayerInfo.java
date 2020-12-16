
package com.atakmap.map.layer.raster.gdal;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.concurrent.atomic.AtomicInteger;

import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.raster.mosaic.ATAKMosaicDatabase3;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseBuilder2;
import com.atakmap.map.layer.raster.nativeimagery.NativeImageryRasterLayer2;

import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.raster.pfps.PfpsMapTypeFrame;
import com.atakmap.map.layer.raster.AbstractDatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorSpi;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.ImageryFileType;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.PrecisionImagery;
import com.atakmap.map.layer.raster.PrecisionImageryFactory;
import com.atakmap.map.layer.raster.ProjectiveTransformProjection2;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;
import com.atakmap.spi.InteractiveServiceProvider;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdal.Driver;
import org.gdal.gdalconst.gdalconst;

public class GdalLayerInfo extends AbstractDatasetDescriptorSpi {

    static {
        NativeImageryRasterLayer2.registerDatasetType("native");
        NativeImageryRasterLayer2.registerDatasetType("native-mosaic");
        NativeImageryRasterLayer2.registerDatasetType("PFI");
        NativeImageryRasterLayer2.registerDatasetType("PRI");
    }

    public final static String PROVIDER_NAME = "gdal";

    public static final String TAG = "GdalLayerInfo";

    public final static DatasetDescriptorSpi INSTANCE = new GdalLayerInfo();

    private final static int GDAL = 0;
    private final static int RPF = 4;
    private final static int PRECISION = 5;

    private final static Map<String, String> TABLE_OF_CONTENTS_SPEC = new HashMap<String, String>();
    static {
        TABLE_OF_CONTENTS_SPEC.put("RPF", "A.TOC");
        TABLE_OF_CONTENTS_SPEC.put("EPF", "TOC.XML");
    }

    private final static FileFilter TOC_FILE_FILTER = new CaseInsensitiveFileNameFileFilter(
            TABLE_OF_CONTENTS_SPEC.values(), false);


    private GdalLayerInfo() {
        super(PROVIDER_NAME, 2);
    }

    @Override
    public int parseVersion() {
        return 8;
    }

    @Override
    public Set<DatasetDescriptor> create(File file, File workingDir, InteractiveServiceProvider.Callback callback) {
        if (!(file instanceof ZipVirtualFile) &&
            IOProviderFactory.isFile(file) &&
            file.getAbsolutePath().toUpperCase(LocaleUtil.getCurrent()).endsWith(".ZIP")) {

            try {
                file = new ZipVirtualFile(file);
            } catch (IllegalArgumentException iae) { 
                Log.w(TAG, "Failed to open zip: " + file.getAbsolutePath(), iae);
            } catch (Exception e) { 
                Log.w(TAG, "Failed to open zip: " + file.getAbsolutePath(), e);
            }
        }
        
        if (IOProviderFactory.isFile(file)) {
            Set<Frame> frames = new HashSet<Frame>();
            createImpl(file, workingDir, false, (file instanceof ZipVirtualFile), frames);
            if(frames.isEmpty())
                return null;
            Set<DatasetDescriptor> retval = new HashSet<DatasetDescriptor>();
            Map<String, String> extraData;
            File descWorkDir;
            int num = 0;
            for(Frame frame : frames) {
                extraData = new HashMap<String, String>();

                descWorkDir = new File(workingDir, String.valueOf(num));
                num++;
                if (!IOProviderFactory.mkdirs(descWorkDir)) {
                    Log.e(TAG, "could not make the directory: " + 
                               descWorkDir);
                }

                try {
                    File tilecacheDatabaseFile = IOProviderFactory.createTempFile("tilecache", ".sqlite", descWorkDir);
                    extraData.put("tilecache", tilecacheDatabaseFile.getAbsolutePath());
                } catch(IOException ignored) {}

                if (frame.gdalSubDataset != null)
                    extraData.put("gdalSubdataset", frame.gdalSubDataset);

                String uri = frame.path;
                if (!uri.contains("://")) {
                    // Assume path is a file if scheme is not specified
                    uri = getURI(new File(frame.path)).toString();
                }
                retval.add(new ImageDatasetDescriptor(frame.name,
                                                      uri,
                                                      PROVIDER_NAME,
                                                      frame.typeIsDatasetType ? frame.type : "native",
                                                      frame.type,
                                                      frame.width,
                                                      frame.height,
                                                      frame.numLevels,
                                                      frame.upperLeft,
                                                      frame.upperRight,
                                                      frame.lowerRight,
                                                      frame.lowerLeft,
                                                      frame.srid,
                                                      false,
                                                      frame.isPrecisionImagery,
                                                      descWorkDir,
                                                      extraData));
            }
            return retval;
        } else if(IOProviderFactory.isDirectory(file)) {
            MosaicDatabase2 database = null;
            DatasetDescriptor tsInfo = null;
            try {
                File mosaicDatabaseFile = new File(workingDir, "mosaicdb.sqlite");
                Log.d(TAG, "creating mosaic database file " + mosaicDatabaseFile.getName()
                        + " for " + file.getName());

                AtomicInteger count = new AtomicInteger(0);

                long mosaicdb = SystemClock.elapsedRealtime();
                long insertRows = SystemClock.elapsedRealtime();
                long close = 0L;
                
                MosaicDatabaseBuilder2 dbbuilder = ATAKMosaicDatabase3.create(mosaicDatabaseFile);
                dbbuilder.beginTransaction();
                try {
                    Set<Frame> layers = new MosaickingSet(dbbuilder);
                    File tempWorkDir = null;
                    try {
                        tempWorkDir = FileSystemUtils.createTempDir("gdal", "workdir", workingDir);
                        createRecursive(file, (file instanceof ZipVirtualFile), layers, tempWorkDir, count, callback);
                    } catch (IOException e) {
                        Log.e(TAG, "Unexpected IO error creating mosaic layer", e);
                        return null;
                    }  finally {
                        if(tempWorkDir != null)
                            FileSystemUtils.delete(tempWorkDir);
                    }

                    if (layers.size() < 1)
                        return null;
                    dbbuilder.setTransactionSuccessful();                    
                } finally {
                    try { 
                        dbbuilder.endTransaction();
                        insertRows = (SystemClock.elapsedRealtime()-insertRows);

                        if(callback != null)
                            callback.progress(-100);
                    
                        close = SystemClock.elapsedRealtime();
                    } finally { 
                        dbbuilder.close();
                        close = (SystemClock.elapsedRealtime()-close);
                    }
                }
                mosaicdb = (SystemClock.elapsedRealtime()-mosaicdb);

                Log.d(TAG, "mosaic scan file: " + file);
                Log.d(TAG, "Generated Mosaic Database in " + mosaicdb + " ms {insert=" + insertRows + ",close=" + close + "}");

                database = new ATAKMosaicDatabase3();
                database.open(mosaicDatabaseFile);

                Map<String, String> extraData = new HashMap<String, String>();
                extraData.put("numFrames", String.valueOf(count.get()));
                extraData.put("relativePaths", "false");

                File tilecacheDir = new File(workingDir, "tilecache");
                FileSystemUtils.delete(tilecacheDir);
                if (IOProviderFactory.exists(tilecacheDir)) {
                   Log.d(TAG, "unable to remove the tile cache dir: " + tilecacheDir);
                }
                if(IOProviderFactory.mkdirs(tilecacheDir))
                    extraData.put("tilecacheDir", tilecacheDir.getAbsolutePath());

                Map<String, MosaicDatabase2.Coverage> dbCoverages = new HashMap<String, MosaicDatabase2.Coverage>();
                database.getCoverages(dbCoverages);
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
            
                tsInfo = new MosaicDatasetDescriptor(file.getName(),
                                   GdalLayerInfo.getURI(file).toString(),
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
        } else {
            return null;
        }
    }

    @Override
    public boolean probe(File file, InteractiveServiceProvider.Callback callback){
        boolean isZip = false;
        if (file instanceof ZipVirtualFile) {
             isZip = true;
        } else if (IOProviderFactory.isFile(file) && file.getAbsolutePath().toUpperCase(LocaleUtil.getCurrent()).endsWith(".ZIP")) {
            try {
                file = new ZipVirtualFile(file);
                isZip = true;
            } catch (IllegalArgumentException iae) {
                Log.w(TAG, "Failed to open zip: " + file.getAbsolutePath(), iae);
            } catch (Exception e) { 
                Log.w(TAG, "Failed to open zip: " + file.getAbsolutePath(), e);
            }
        }

        return test(file, isZip, new AtomicInteger(1), callback);
    }
    
    private static boolean test(File file, boolean isZip, AtomicInteger count, InteractiveServiceProvider.Callback callback){
        // Recursively check the file, and any children if it is a directory,
        // for files that can be opened by GDAL. If one is found, this
        // is probably a directory that could make a GDAL layer.

        if(IOProviderFactory.isDirectory(file)){
            final String name = file.getName();
            if(name.toLowerCase(LocaleUtil.getCurrent()).equals("dted"))
                return false;

            File[] files = IOProviderFactory.listFiles(file);
            if (files != null) { 
                for(File child : files){
                    if(count.getAndIncrement() > callback.getProbeLimit()){
                        return false;
                    }
                    if(test(child, isZip, count, callback)){
                        return true;
                    }
                }
            }

            return false;
        }else{
            Dataset dataset = null;
            try{
                dataset = GdalLibrary.openDatasetFromFile(file);
                if (dataset == null){
                    return false;
                }else{
                    return true;
                }
            }finally{
                if(dataset != null){
                    dataset.delete();
                }
            }
        }
    }

    private static void createRecursive(File file, boolean isZip, Set<Frame> retval,
            File workingDir, AtomicInteger count, InteractiveServiceProvider.Callback callback) {
        int tmpCount = count.getAndIncrement();
        if(callback != null && (tmpCount % 10) == 0){
            callback.progress(tmpCount);
        }

        if (IOProviderFactory.isFile(file) && !(file instanceof ZipVirtualFile) &&
            file.getAbsolutePath().toUpperCase(LocaleUtil.getCurrent()).endsWith(".ZIP")) {
            try {
                file = new ZipVirtualFile(file);
                isZip = true;
            } catch (IllegalArgumentException iae) {
                Log.w(TAG, "Failed to open zip: " + file.getAbsolutePath(), iae);
                return;
            } catch (Exception e) { 
                Log.w(TAG, "Failed to open zip: " + file.getAbsolutePath(), e);
                return;
            }

            createRecursive(file, isZip, retval, workingDir, count, callback);
        } else if (IOProviderFactory.isFile(file)) {
            createImpl(file, workingDir, true, isZip, retval);
        } else if(!file.getName().toLowerCase(LocaleUtil.getCurrent()).equals("dted")) {
            // NOTE: the code below appears to be pretty redundant, and the
            //       first switch could be applied to all directories. However,
            //       in practice the latter case has been observed to be a
            //       pretty significant micro-optimization for the non-zip case
            //       when dealing with very large directories.
            boolean treatAsFile = false;
            if(isZip) {
                File[] children = IOProviderFactory.listFiles(file);
                if(children != null) {
                    for (int i = 0; i < children.length; i++)
                        createRecursive(children[i], isZip, retval, workingDir, count, callback);
                } else {
                    treatAsFile = true;
                }
            } else {
                String[] children = IOProviderFactory.list(file);
                if(children != null) {
                    for (int i = 0; i < children.length; i++)
                        createRecursive(new File(file, children[i]), isZip, retval, workingDir, count, callback);
                } else {
                    treatAsFile = true;
                }
            }            
            if(treatAsFile) {
                Log.w(TAG, "Unexpected null listing for " + file.getAbsolutePath() + ", attempting to open as file.");
                createImpl(file, workingDir, true, isZip, retval);
            }
        }

    }

    private static void createImpl(File file, File workingDir, boolean mosaic, boolean isZip, Set<Frame> retval) {
        int type = GDAL;

        // check for special types
        if (!IOProviderFactory.isDirectory(file)) {
            boolean subtyped = false;

            try {
                // we'll try to short circuit RPF frame files by doing name
                // analysis. if that fails, we'll process as normal later on
                if (!subtyped && file.getName().length() == 12 && PfpsMapTypeFrame.getRpfPrettyName(file) != null) {
                    type = RPF;
                    subtyped = true;
                }
            } catch(Exception ignored) {
                // process as normal GDAL file
                Log.d(TAG, "Unexpected general error during RPF check for " + file.getName()
                        + "; processing as normal GDAL file.");
            }
            // XXX - not sure if this guard against the zip is actually needed
            //       here -- seems like something we could let the providers
            //       determine themselves...
            if (!isZip) {
                try {
                    if (!subtyped && PrecisionImageryFactory.isSupported(file.getAbsolutePath())) {
                        type = PRECISION;
                        subtyped = true;
                    }
                } catch (Exception ignored) {
                    // process as normal GDAL file
                    Log.d(TAG, "Unexpected general error during precision imagery check for " + file.getName()
                            + "; processing as normal GDAL file.");
                }
            }
        }

        try {
            createLayer(file, workingDir, type, mosaic, isZip, retval);
        } catch (IOException e) {
            Log.e(TAG, "IO error creating GDAL layer", e);
        }
    }

    private static void createLayer(File file,File workingDir, int type, boolean forMosaic, boolean isZip, Set<Frame> retval)
            throws IOException {

        do {
            switch (type) {
                case RPF : {
                    String uri = file.getAbsolutePath();
                    if (isZip)
                    //if (file instanceof ZipVirtualFile)
                        uri = "/vsizip" + uri;
                    try {
                        Frame tsInfo = createRpfLayer(file, workingDir, file.getName(),
                                uri);
                        if(tsInfo != null) {
                            retval.add(tsInfo);
                            return;
                        } else {
                            Log.w(TAG, "Failed to create RPF layer for " + file.getName() + " treat as NITF.");
                        }
                    } catch(Exception e) {
                        Log.e(TAG, "Unexpected general error during RPF layer creation.", e);
                    }
                    type = GDAL;
                    continue;
                }
                case GDAL: {
                    createGdalLayer(file, workingDir, file.getName(), null,
                                new HashMap<String, String>(), forMosaic, retval);
                    return;
                }
                case PRECISION :
                {
                    try {
                        Frame tsInfo = createPrecisionLayerImpl(file, workingDir);
                        if(tsInfo != null) {
                            retval.add(tsInfo);
                            return;
                        } else {
                            Log.w(TAG, "Failed to create precision imagery layer for " + file.getName() + " treat as NITF.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected general error during precision imagery layer creation.", e);
                    }
                    type = GDAL;
                    continue;
                }
                default:
                    throw new IllegalStateException();
            }
        } while(true);
    }
    
    private static Frame createRpfLayer(File baseFile, File workingDir,
            String name, String uri) throws IOException {

        final String rpfType = PfpsMapTypeFrame.getRpfPrettyName(baseFile);

        Frame frame = new Frame();
        if (PfpsMapTypeFrame.coverageFromFilename(baseFile, frame.upperLeft, frame.upperRight, frame.lowerRight, frame.lowerLeft)) {
            frame.name = name;
            frame.path = uri;
            frame.type = rpfType;
            frame.width = 1536;
            frame.height = 1536;
            frame.resolution = DatasetDescriptor.computeGSD(frame.width, frame.height, frame.upperLeft, frame.upperRight, frame.lowerRight, frame.lowerLeft);
            frame.numLevels = 4;
            frame.srid = 4326;

            return frame;
        } else {
            return null;
        }
    }

    private static void createGdalLayer(File baseFile, File workingDir,
            String name, String uri, Map<String, String> extraData, boolean forMosaic, Set<Frame> retval) throws IOException {

        if(TOC_FILE_FILTER.accept(baseFile))
            return;

        Dataset dataset;
        if(uri != null)
            dataset = gdal.Open(uri);
        else
            dataset = GdalLibrary.openDatasetFromFile(baseFile);
        if (dataset == null)
            return;

        // look for subdatasets
        Map<String, String> subdatasets = (Map<String, String>) dataset
                .GetMetadata_Dict("SUBDATASETS");
        if (subdatasets.size() > 0) {
            // build the list of subdatasets
            Set<String> uris = new LinkedHashSet<String>();
            Iterator<Map.Entry<String, String>> subdatasetIter = subdatasets.entrySet().iterator();
            Map.Entry<String, String> entry;
            while (subdatasetIter.hasNext()) {
                entry = subdatasetIter.next();
                if (entry.getKey().matches("SUBDATASET\\_\\d+\\_NAME")) {
                    uris.add(entry.getValue());
                }
            }

            // close parent
            dataset.delete();

            // XXX - pretty names for subdatasets
            int subdatasetNum = 1;
            Iterator<String> uriIter = uris.iterator();
            String subdatasetUri;
            Map<String, String> subdatasetExtraData;
            while (uriIter.hasNext()) {
                subdatasetUri = uriIter.next();
                subdatasetExtraData = new LinkedHashMap<String, String>(extraData);
                subdatasetExtraData.put("gdalSubdataset", subdatasetUri);
                try {
                    createGdalLayer(baseFile, workingDir, name + "["
                            + (subdatasetNum++) + "]", subdatasetUri, subdatasetExtraData, forMosaic, retval);
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        } else {
            Frame info = createGdalLayerImpl(baseFile, workingDir, name,
                    getURI(baseFile), dataset, extraData);
            if (info == null)
                return;
            info.gdalSubDataset = uri;
            
            retval.add(info);
        }
    }

    private static Frame createGdalLayerImpl(File derivedFrom, File workingDir,
            String name, URI uri, Dataset dataset, Map<String, String> extraData)
            throws IOException {
        
        try {
            if (!isDisplayable(dataset, true))
                return null;

            final int width = dataset.GetRasterXSize();
            final int height = dataset.GetRasterYSize();
            final GdalDatasetProjection2 proj = GdalDatasetProjection2.getInstance(dataset);

            if (proj == null)
                return null;

            final GeoPoint ul = GeoPoint.createMutable();
            proj.imageToGround(new PointD(0, 0), ul);
            final GeoPoint ur = GeoPoint.createMutable();
            proj.imageToGround(new PointD(width-1, 0), ur);
            final GeoPoint lr = GeoPoint.createMutable();
            proj.imageToGround(new PointD(width-1, height-1), lr);
            final GeoPoint ll = GeoPoint.createMutable();
            proj.imageToGround(new PointD(0, height-1), ll);
            final int spatialReferenceID = proj.getNativeSpatialReferenceID();

            File tilecacheDatabaseFile = IOProviderFactory.createTempFile("tilecache", ".sqlite", workingDir);
            extraData.put("tilecache", tilecacheDatabaseFile.getAbsolutePath());

//            Log.d(TAG, "Create GdalLayer " + name + " " + width + "x" + height);
//            Log.d(TAG, "ul=" + ul);
//            Log.d(TAG, "ur=" + ur);
//            Log.d(TAG, "lr=" + lr);
//            Log.d(TAG, "ll=" + ll);

            final double gsd = DatasetDescriptor.computeGSD(width, height, ul, ur, lr, ll);
            final int numLevels = getNumLevels(dataset, width, height, 5);

            String type = "Unknown";
            boolean appendResolution = false;
            ImageryFileType.AbstractFileType t = ImageryFileType.getFileType(derivedFrom);
            if(t != null) {
                if(t.getID() == ImageryFileType.RPF) {
                    type = PfpsMapTypeFrame.getRpfPrettyName(derivedFrom);
                    appendResolution = false;
                } else if(t.getID() == ImageryFileType.GDAL) {
                    type = "NITF";
                    appendResolution = true;
                } else {
                    type = t.getDescription();
                    appendResolution = true;
                }
            } else {
                if(derivedFrom.getName().length() == 12) {
                    String rpfType = PfpsMapTypeFrame.getRpfPrettyName(derivedFrom);
                    if(rpfType != null)
                        type = rpfType;
                }
            }

            if(appendResolution)
                type += (" " + DatasetDescriptor.formatResolution(gsd)); 

            Frame retval = new Frame();
            retval.name = name;
            retval.path = uri.toString();
            retval.type = type;
            retval.width = width;
            retval.height = height;
            retval.resolution = gsd;
            retval.numLevels = numLevels;
            retval.upperLeft.set(ul);
            retval.upperRight.set(ur);
            retval.lowerRight.set(lr);
            retval.lowerLeft.set(ll);
            retval.srid = spatialReferenceID;
            
            return retval;
        } finally {
            dataset.delete();
        }
    }

    private static Frame createPrecisionLayerImpl(File file, File workingDir)
            throws Exception {
        
        final PrecisionImagery img = PrecisionImageryFactory.create(file.getAbsolutePath());
        if(img == null) {
            Log.d(TAG, "Failed to parse " + file.getName()
                    + " as precision imagery source");
            throw new IllegalArgumentException("File " + file.getName() + " doesn't contain a precision imagery source!");
        }
        
        final ImageInfo info = img.getInfo();
        if(info == null) {
            Log.d(TAG, "Failed to parse image information for " +
                    file.getName());
            throw new IllegalArgumentException("Unable to obtain precision imagery information for " + file.getName());
        }

        Frame retval = new Frame();
        
        retval.isPrecisionImagery = true;

        retval.upperLeft.set(info.upperLeft);
        retval.upperRight.set(info.upperRight);
        retval.lowerRight.set(info.lowerRight);
        retval.lowerLeft.set(info.lowerLeft);
        retval.width = info.width;
        retval.height = info.height;

        retval.srid = info.srid;
        
        retval.resolution = info.maxGsd;
        retval.numLevels = getNumLevels(null, info.width, info.height, 4);

        retval.name = file.getName();
        
        // XXX - ChinaLake2???
        retval.path = info.path;//getURI(file).toString();
        retval.type = info.type;
        retval.typeIsDatasetType = true;
        
        // check open as GDAL dataset
        Dataset dataset = null;
        try {
            dataset = GdalLibrary.openDatasetFromFile(file, gdalconst.GA_ReadOnly);
            if (dataset == null)
                return null;
/*            
            // look for subdatasets
            Map<String, String> subdatasets = (Map<String, String>) dataset
                    .GetMetadata_Dict("SUBDATASETS");
            if (subdatasets != null && !subdatasets.isEmpty()) {
                Map<String, String> extraData = new HashMap<String, String>();

                // build the list of subdatasets
                Set<String> uris = new LinkedHashSet<String>();
                String subdatasetUri;
                int subdatasetNum = 1;
                do {
                    subdatasetUri = (String) subdatasets.get("SUBDATASET_"
                            + String.valueOf(subdatasetNum++) + "_NAME");
                    if (subdatasetUri == null)
                        break;
                    uris.add(subdatasetUri);
                } while (true);

                // close parent
                dataset.delete();
                dataset = null;

                Dataset baseDisplayableImage = null;
                Iterator<String> iter = uris.iterator();
                while (iter.hasNext()) {
                    subdatasetUri = iter.next();
                    dataset = null;
                    try {
                        dataset = gdal.Open(subdatasetUri);
                        if (dataset == null)
                            continue;

                        // if the dataset is displayable, identify it as an
                        // overview or the main image
                        if (isDisplayable(dataset, false)) {
                            // base image should always be first image
                            baseDisplayableImage = dataset;
                            retval.gdalSubDataset = baseDisplayableImage.GetDescription();
                            break;
                        }
                    } finally {
                        if (dataset != null) 
                            dataset.delete();
                    }
                }

                // no displayable images
                if (baseDisplayableImage == null)
                    return null;
            }
*/            
        } finally {
            if (dataset != null) 
                dataset.delete();
        }

        return retval;
    }

    /*************************************************************************/
    // Utility

    /**
     * Computes the number of resolution levels for a given image.
     *
     * @param dataset       The image dataset (may be <code>null</code>)
     * @param width         The width of the image
     * @param height        The height of the image
     * @param maxSubsample  The maximum render subsmaple allowed
     * @return
     */
    private static int getNumLevels(Dataset dataset, int width, int height, int maxSubsample) {
        final int datasetOverviews = (dataset != null) ? dataset.GetRasterBand(1).GetOverviewCount() : 0;
        final int rrCount = GdalTileReader.getNumResolutionLevels(width, height, 512, 512);

        return Math.max(datasetOverviews, Math.min(rrCount, maxSubsample))+1;
    }

    private static boolean isDisplayable(Dataset dataset, boolean checkForProjection) {
        final Driver driver = dataset.GetDriver();

        if (driver == null)
            return false;

        if (driver.GetDescription().equals("NITF")) {
            final String irep = dataset.GetMetadataItem("NITF_IREP");
            if (irep != null && irep.equals("NODISPLY"))
                return false;
        }

        return !checkForProjection || (GdalDatasetProjection2.getInstance(dataset) != null);
    }

    public static URI getURI(File sourceFile) {
        String protocol = "file";
        if (sourceFile instanceof ZipVirtualFile)
            protocol = "zip";

        LinkedList<Map.Entry<String, String>> params = new LinkedList<Map.Entry<String, String>>();

        String path = sourceFile.getAbsolutePath();

        StringBuilder paramString = new StringBuilder();
        Iterator<Map.Entry<String, String>> iter = params.iterator();
        Map.Entry<String, String> entry;
        while (iter.hasNext()) {
            if (paramString.length() < 1)
                paramString.append("?");
            else
                paramString.append("&");
            entry = iter.next();
            try {
                paramString.append(URLEncoder.encode(entry.getKey(),
                        DatasetDescriptor.URI_CHARACTER_ENCODING.name()));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
            paramString.append("=");
            try {
                paramString.append(URLEncoder.encode(entry.getValue(),
                        DatasetDescriptor.URI_CHARACTER_ENCODING.name()));
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }

        try {
            path = FileSystemUtils.sanitizeURL(path);
            return new URI(protocol + "://" + path + paramString.toString());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String getGdalFriendlyUri(DatasetDescriptor info) {
        if (info.getExtraData("gdalSubdataset") != null)
            return info.getExtraData("gdalSubdataset");

        Uri uri = Uri.parse(FileSystemUtils.sanitizeURLKeepSpaces(info.getUri()));
        String scheme = uri.getScheme();
        String path = uri.getPath().replace("%20", " ").
                                    replace("%23", "#").    
                                    replace("%5B", "[").    
                                    replace("%5D", "]");

        String prefix = "";
        if (scheme != null && scheme.equals("zip"))
            prefix = "/vsizip";

        return prefix + path;
    }
    
    public static DatasetProjection2 createDatasetProjection2(Matrix img2proj)
            throws NoninvertibleTransformException {
        return new ProjectiveTransformProjection2(img2proj);
    }

    /**************************************************************************/

    private static class CaseInsensitiveFileNameFileFilter implements FileFilter {
        private final boolean isDirectory;
        private final Collection<String> names;

        public CaseInsensitiveFileNameFileFilter(Collection<String> names, boolean isDirectory) {
            this.names = new HashSet<String>();
            for (String name : names)
                this.names.add(name.toUpperCase(LocaleUtil.getCurrent()));
            this.isDirectory = isDirectory;
        }

        @Override
        public boolean accept(File f) {
            if (IOProviderFactory.isDirectory(f) != this.isDirectory)
                return false;
            return this.names.contains(f.getName().toUpperCase(LocaleUtil.getCurrent()));
        }
    }
    
    /**************************************************************************/    
    
    private static class MosaickingSet extends AbstractSet<Frame> {

        private MosaicDatabaseBuilder2 database;
        private int count;
        
        public MosaickingSet(MosaicDatabaseBuilder2 database) {
            this.database = database;
            this.count = 0;
        }
        
        @Override
        public boolean add(Frame val) {
            String uri;
            if (val.gdalSubDataset != null)
                uri = val.gdalSubDataset;
            else
                uri = val.path;

            this.database.insertRow(uri,
                                    val.type,
                                    val.isPrecisionImagery,
                                    val.upperLeft,
                                    val.upperRight,
                                    val.lowerRight,
                                    val.lowerLeft,
                                    val.resolution * (1<<(val.numLevels)-1),
                                    val.resolution,
                                    val.width,
                                    val.height,
                                    val.srid);
            this.count++;
            return true;
        }

        @Override
        public Iterator<Frame> iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return this.count;
        }
    }
    
    private static class Frame {
        public String name;
        public String path;
        public String gdalSubDataset;
        public String type;
        public boolean typeIsDatasetType;
        public int width;
        public int height;
        public double resolution;
        public int numLevels;
        public final GeoPoint upperLeft;
        public final GeoPoint upperRight;
        public final GeoPoint lowerRight;
        public final GeoPoint lowerLeft;
        public int srid;
        public boolean isPrecisionImagery;
        
        public Frame() {
            this.name = null;
            this.path = null;
            this.gdalSubDataset = null;
            this.type = null;
            this.typeIsDatasetType = false;
            this.width = 0;
            this.height = 0;
            this.resolution = 0.0d;
            this.numLevels = 0;
            this.upperLeft = GeoPoint.createMutable();
            this.upperRight = GeoPoint.createMutable();
            this.lowerRight = GeoPoint.createMutable();
            this.lowerLeft = GeoPoint.createMutable();
            this.srid = 0;
            this.isPrecisionImagery = false;
        }
    }
}
