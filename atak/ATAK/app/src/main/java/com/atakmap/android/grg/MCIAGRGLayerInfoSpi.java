
package com.atakmap.android.grg;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore.FeatureQueryParameters;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.ogr.SchemaDefinition;
import com.atakmap.map.layer.raster.AbstractDatasetDescriptorSpi;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.math.PointD;
import com.atakmap.spatial.file.ShapefileSpatialDb;
import com.atakmap.spi.InteractiveServiceProvider;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MCIAGRGLayerInfoSpi extends AbstractDatasetDescriptorSpi {

    private final static String KEY_GRG = "grg";
    private final static String KEY_SECTIONS = "sections";
    private final static String KEY_SUBSECTIONS = "subsections";
    private final static String KEY_BUILDINGS = "buildings";

    private final static String PROVIDER_NAME = "mcia-grg";

    /**************************************************************************/

    MCIAGRGLayerInfoSpi() {
        super(PROVIDER_NAME, 1);
    }

    @Override
    protected Set<DatasetDescriptor> create(File file, File workingDir,
            InteractiveServiceProvider.Callback callback) {
        // Don't do anything with the callback in this method. This create method just
        // Reads data from a single item in the database, and it will be fast
        // enough that it isn't worth reporting the completion of individual items.

        Map<String, File> mciaGRGDef = discoverMCIAGRG(file);
        if (mciaGRGDef.size() < 2 || !mciaGRGDef.containsKey(KEY_GRG))
            return null;

        final String prefix;
        if (file instanceof ZipVirtualFile)
            prefix = "/vsizip";
        else if (!IOProviderFactory.isDefault())
            prefix = VSIFileFileSystemHandler.PREFIX;
        else
            prefix = "";

        Map<String, String> extraData = new HashMap<>();

        final File buildingsFile = mciaGRGDef.get(KEY_BUILDINGS);
        final File sectionsFile = mciaGRGDef.get(KEY_SECTIONS);
        final File subsectionsFile = mciaGRGDef.get(KEY_SUBSECTIONS);

        DataSourceFeatureDataStore spatialdb = null;
        try {
            File spatialdbFile = IOProviderFactory.createTempFile("layer",
                    ".private",
                    workingDir);
            spatialdb = new PersistentDataSourceFeatureDataStore(spatialdbFile);

            // XXX -- no means to obtain context !!!!
            ContentSource mciaContentSource = new ContentSource(spatialdb);

            spatialdb.beginBulkModification();
            boolean success = false;
            try {
                if (buildingsFile != null
                        && mciaContentSource.processFile(buildingsFile)) {
                    extraData.put("buildingsGroup", buildingsFile.getName());
                    extraData.put("buildingsUri",
                            prefix + buildingsFile.getAbsolutePath());
                }
                if (sectionsFile != null
                        && mciaContentSource.processFile(sectionsFile)) {
                    extraData.put("sectionsGroup", sectionsFile.getName());
                    extraData.put("sectionsUri",
                            prefix + sectionsFile.getAbsolutePath());
                }
                if (subsectionsFile != null
                        && mciaContentSource.processFile(subsectionsFile)) {
                    extraData
                            .put("subsectionsGroup", subsectionsFile.getName());
                    extraData.put("subsectionsUri",
                            prefix + subsectionsFile.getAbsolutePath());
                }
                success = true;
            } finally {
                spatialdb.endBulkModification(success);
            }

            extraData.put("spatialdb", spatialdbFile.getAbsolutePath());
            extraData.put("numFeatures", String.valueOf(spatialdb
                    .queryFeaturesCount(new FeatureQueryParameters())));

        } catch (IOException e) {
            Log.e("MCIAGRGLayerInfoSpi", "Failed to create spatial database",
                    e);
        } finally {
            if (spatialdb != null)
                spatialdb.dispose();
        }

        Dataset dataset = null;
        int width;
        int height;
        GeoPoint ul;
        GeoPoint ur;
        GeoPoint lr;
        GeoPoint ll;
        int srid;
        final String type;
        final double gsd;
        try {
            dataset = gdal.Open(prefix
                    + mciaGRGDef.get(KEY_GRG).getAbsolutePath());

            width = dataset.GetRasterXSize();
            height = dataset.GetRasterYSize();

            GdalDatasetProjection2 proj = GdalDatasetProjection2
                    .getInstance(dataset);

            // higher up, null return is already used to denote an invalid state
            if (proj == null)
                return null;

            ul = GeoPoint.createMutable();
            proj.imageToGround(new PointD(0, 0), ul);
            ur = GeoPoint.createMutable();
            proj.imageToGround(new PointD(width, 0), ur);
            lr = GeoPoint.createMutable();
            proj.imageToGround(new PointD(width, height), lr);
            ll = GeoPoint.createMutable();
            proj.imageToGround(new PointD(0, height), ll);

            srid = proj.getNativeSpatialReferenceID();

            gsd = DatasetDescriptor.computeGSD(width, height, ul, ur, lr, ll);

            Driver driver = dataset.GetDriver();
            if (driver != null) {
                type = DatasetDescriptor.formatImageryType(driver
                        .GetDescription(), gsd);
            } else {
                type = "unknown";
            }

            // XXX - if multiple georeferenced subdatasets, create mosaic
            //       else create single image layer
        } finally {
            if (dataset != null) {
                dataset.delete();
            }
        }

        final int numLevels = Math.min(
                GdalTileReader.getNumResolutionLevels(width, height, 512, 512),
                4);

        return Collections
                .<DatasetDescriptor> singleton(new ImageDatasetDescriptor(file
                        .getName(),
                        GdalLayerInfo.getURI(mciaGRGDef.get(KEY_GRG))
                                .toString(),
                        PROVIDER_NAME,
                        "native",
                        type,
                        width,
                        height,
                        gsd,
                        numLevels,
                        ul, ur, lr, ll,
                        srid,
                        false,
                        workingDir,
                        extraData));
    }

    @Override
    protected boolean probe(File file,
            InteractiveServiceProvider.Callback callback) {
        return isMCIAGRG(file, callback.getProbeLimit());
    }

    @Override
    public int parseVersion() {
        return 2;
    }

    /**************************************************************************/

    public static boolean isMCIAGRG(File f) {
        return isMCIAGRG(f, DatasetDescriptorFactory2.DEFAULT_PROBE);
    }

    private static boolean isMCIAGRG(File f, int limit) {
        final Map<String, File> mciaGRGDef = discoverMCIAGRG(f, limit);

        // we'll consider it an MCIA GRG if it contains the GRG raster as well
        // as at least one of the shapefiles
        return (mciaGRGDef.containsKey(KEY_GRG) && mciaGRGDef.size() > 1);
    }

    private static Map<String, File> discoverMCIAGRG(File dir) {
        return discoverMCIAGRG(dir, Integer.MAX_VALUE);
    }

    private static Map<String, File> discoverMCIAGRG(File dir, int limit) {
        if (!IOProviderFactory.isDirectory(dir))
            return Collections.emptyMap();

        final String prefix;
        if (dir instanceof ZipVirtualFile)
            prefix = "/vsizip";
        else
            prefix = "";

        Map<String, SchemaDefinition> schemas = new HashMap<>();
        // XXX - ignore buildings for the time being -- too much clutter and
        //       ambiguity
        //schemas.put(KEY_BUILDINGS, ShapefileSpatialDb.MCIA_BUILDINGS_DBF_SCHEMA);
        schemas.put(KEY_SECTIONS, MCIA_GRG.SECTIONS_SCHEMA_DEFN);
        schemas.put(KEY_SUBSECTIONS, MCIA_GRG.SUBSECTIONS_SCHEMA_DEFN);

        Map<String, File> retval = new HashMap<>();

        File[] children = IOProviderFactory.listFiles(dir);
        String extension;
        DataSource dataSource;
        Layer layer;
        FeatureDefn layerDefn;
        Iterator<Map.Entry<String, SchemaDefinition>> schemaIter;
        Map.Entry<String, SchemaDefinition> schemaEntry;
        Dataset dataset;

        if (children == null)
            children = new File[0];

        int testLimit = Math.min(children.length, limit);

        for (int i = 0; i < testLimit; i++) {
            extension = FileSystemUtils.getExtension(children[i], true, false);
            if (extension.equals("SHP") && schemas.size() > 0) {
                dataSource = null;
                try {
                    String path = prefix + children[i].getAbsolutePath();
                    if (!IOProviderFactory.isDefault())
                        path = VSIFileFileSystemHandler.PREFIX + path;
                    dataSource = ogr.Open(path);
                    if (dataSource != null) {
                        final int numLayers = dataSource.GetLayerCount();
                        layer_loop: for (int j = 0; j < numLayers; j++) {
                            layer = null;
                            try {
                                layer = dataSource.GetLayer(j);
                                layerDefn = layer.GetLayerDefn();

                                schemaIter = schemas.entrySet().iterator();
                                while (schemaIter.hasNext()) {
                                    schemaEntry = schemaIter.next();
                                    if (schemaEntry.getValue().matches(
                                            children[i], layerDefn)) {
                                        retval.put(schemaEntry.getKey(),
                                                children[i]);
                                        schemaIter.remove();
                                        break layer_loop;
                                    }
                                }
                            } finally {
                                if (layer != null)
                                    layer.delete();
                            }
                        }
                    }
                } finally {
                    if (dataSource != null)
                        dataSource.delete();
                }
            } else if (!GRGDiscovery.EXTENSION_BLACKLIST.contains(extension)
                    && !retval.containsKey(KEY_GRG)) {
                dataset = null;
                try {
                    dataset = gdal.Open(prefix + children[i].getAbsolutePath());
                    if (dataset != null) {
                        // XXX - subdatasets
                        if (GdalDatasetProjection2.getInstance(dataset) != null)
                            retval.put(KEY_GRG, children[i]);
                    }
                } finally {
                    if (dataset != null)
                        dataset.delete();
                }
            } else if (retval.containsKey(KEY_GRG) && schemas.size() < 1) {
                // everything has been found
                break;
            }
        }

        return retval;
    }

    /**************************************************************************/

    private static class ContentSource extends ShapefileSpatialDb {

        protected ContentSource(DataSourceFeatureDataStore spatialDb) {
            super(spatialDb);
        }

        @Override
        public boolean processFile(File f) {
            return super.processFile(f);
        }

        @Override
        public String getContentType() {
            return PROVIDER_NAME;
        }
    }
}
