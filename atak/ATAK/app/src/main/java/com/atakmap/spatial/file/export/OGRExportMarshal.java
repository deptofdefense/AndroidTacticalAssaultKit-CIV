
package com.atakmap.spatial.file.export;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.importexport.ExportFileMarshal;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper.NamedGeometry;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.Driver;
import org.gdal.ogr.Feature;
import org.gdal.ogr.FeatureDefn;
import org.gdal.ogr.FieldDefn;
import org.gdal.ogr.Layer;
import org.gdal.ogr.ogr;
import org.gdal.ogr.ogrConstants;
import org.gdal.osr.SpatialReference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * Marshals <code>Export</code> instances to a an OGR supported dataset
 * 
 * 
 */
public abstract class OGRExportMarshal extends ExportFileMarshal {

    private static final String TAG = "OGRExportMarshal";

    //features organized by layer, also stores OGR Feature type as a
    //single Shapefile can only store a single feature type
    protected final Map<Pair<String, Integer>, List<NamedGeometry>> geometries;

    private final String driverName;

    public OGRExportMarshal(Context context, String contentType,
            String mimeType, String driverName, int iconId) {
        super(context, contentType.toUpperCase(LocaleUtil.getCurrent()),
                mimeType, iconId);
        geometries = new HashMap<>();
        this.driverName = driverName;
    }

    @Override
    public Class<? extends OGRFeatureExportWrapper> getTargetClass() {
        return OGRFeatureExportWrapper.class;
    }

    @Override
    protected boolean marshal(Exportable export)
            throws FormatNotSupportedException {
        if (export == null || !export.isSupported(getTargetClass())) {
            Log.d(TAG, "Skipping unsupported export "
                    + (export == null ? "" : export.getClass().getName()));
            return false;
        }

        OGRFeatureExportWrapper folder = (OGRFeatureExportWrapper) export
                .toObjectOf(getTargetClass(), getFilters());
        if (folder == null || folder.isEmpty()) {
            Log.d(TAG, "Skipping empty folder");
            return false;
        }
        Log.d(TAG, "Adding folder name: " + folder.getName());

        //gather all Features
        for (Entry<Integer, List<NamedGeometry>> e : folder.getGeometries()
                .entrySet()) {
            if (e.getValue() == null || e.getValue().size() < 1)
                continue;

            final List<NamedGeometry> fList = getOrCreateFeatureList(
                    folder.getName(), e.getKey());
            //TODO check for duplicates?
            fList.addAll(e.getValue());
        }

        return true;
    }

    /**
     * Get feature list based on folder name
     * This stores features from the specified folder in class member
     * folders to prep for export
     * 
     * @param layerName the layer name
     * @param featureType the feature type in the layer
     * @return a list of named geometries
     */
    protected List<NamedGeometry> getOrCreateFeatureList(
            final String layerName,
            final int featureType) {
        Pair<String, Integer> p = new Pair<>(layerName,
                featureType);

        List<NamedGeometry> f = geometries.get(p);

        if (f == null) {
            f = new ArrayList<>();
            geometries.put(p, f);
            Log.d(TAG, "Adding layer list: " + layerName + ", " + featureType);
        }

        return f;
    }

    @Override
    public void finalizeMarshal() throws IOException {
        if (geometries == null || geometries.size() < 1)
            throw new IOException("No features");

        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }

        // delete existing file, and then serialize KML out to file
        File file = getFile();
        if (IOProviderFactory.exists(file)) {
            FileSystemUtils.deleteFile(file);
        } else {
            File parentFile = file.getParentFile();

            if (!IOProviderFactory.mkdirs(parentFile)) {
                Log.w(TAG, "Failed to create directories"
                        + parentFile);
            }
        }

        //wrap dataset
        try {
            Driver driver = ogr.GetDriverByName(driverName);
            if (driver == null) {
                throw new IOException("Unable to create OGR driver: "
                        + driverName);
            }
            String path = file.getAbsolutePath();
            if (!IOProviderFactory.isDefault()) {
                path = VSIFileFileSystemHandler.PREFIX + path;
            }
            DataSource dataSource = driver.CreateDataSource(path);
            if (dataSource == null) {
                throw new IOException("Unable to create OGR dataSource: "
                        + file.getAbsolutePath());
            }

            SpatialReference srs4326 = new SpatialReference();
            srs4326.ImportFromEPSG(4326);

            //track all used layers names b/c they must be unique in a dataset
            List<String> layerNames = new ArrayList<>();

            int index = 0; //TODO increment uniquely across all layers?
            for (Entry<Pair<String, Integer>, List<NamedGeometry>> es : geometries
                    .entrySet()) {
                String layerName = es.getKey().first;
                if (FileSystemUtils.isEmpty(layerName))
                    layerName = "Layer";

                if (layerNames.contains(layerName)) {
                    layerName += UUID.randomUUID().toString();
                }
                layerNames.add(layerName);

                Log.d(TAG,
                        "Exporting features for: " + layerName + ", "
                                + es.getKey().second);

                //wrap layer
                Layer layer = dataSource.CreateLayer(layerName, srs4326,
                        es.getKey().second);
                if (layer == null) {
                    throw new IOException("Unable to create OGR layer: "
                            + es.getKey());
                }

                FeatureDefn layerDefinition = layer.GetLayerDefn();
                if (layerDefinition == null) {
                    throw new IOException("Unable to get layer definition: "
                            + es.getKey());
                }

                FieldDefn labelDefn = new FieldDefn("label",
                        ogrConstants.OFTString);
                labelDefn.SetWidth(32);
                layer.CreateField(labelDefn);
                //TODO some style/color convention?

                //now add the features, only once
                for (NamedGeometry g : es.getValue()) {
                    g.getGeometry().AssignSpatialReference(srs4326);

                    //add to layer
                    Feature feature = new Feature(layerDefinition);
                    feature.SetFID(index++);
                    feature.SetField(labelDefn.GetName(), g.getName());
                    feature.SetGeometry(g.getGeometry());
                    //Save feature and cleanup
                    layer.CreateFeature(feature);
                    g.getGeometry().delete();
                    feature.delete();
                } //end feature loop

                layer.delete();
                labelDefn.delete();
                layerDefinition.delete();
            } //end folder loop            

            Log.d(TAG, "Exporting " + dataSource.GetLayerCount()
                    + " layers to " + file.getAbsolutePath());
            dataSource.delete();
        } catch (RuntimeException e) {
            throw new IOException("OGR Runtime Exception", e);
        }
    }
}
