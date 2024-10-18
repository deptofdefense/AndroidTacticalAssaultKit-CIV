
package com.atakmap.android.model;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.model.opengl.GLModelLayer;
import com.atakmap.app.R;
import com.atakmap.android.importexport.AbstractImporter;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.IconPointStyle;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelImporter extends AbstractImporter {

    private static final String TAG = "ModelImporter";

    // Special URI for deleting all models
    public static final String URI_ALL_MODELS = "model://*";

    private static final Set<String> mimeTypes = new HashSet<>();
    static {
        mimeTypes.add("application/octet-stream");
    }

    public final static String CONTENT_TYPE = "3D Model";

    private final Context ctx;
    private final FeatureDataStore2 modelDataStore;
    private final ModelContentResolver contentResolver;

    ModelImporter(Context ctx, FeatureDataStore2 modelDataStore,
            ModelContentResolver contentResolver) {
        super(CONTENT_TYPE);

        this.ctx = ctx;
        this.modelDataStore = modelDataStore;
        this.contentResolver = contentResolver;
    }

    public static synchronized void registerMIMEType(String type) {
        mimeTypes.add(type);
    }

    public static synchronized void unregisterMIMEType(String type) {
        mimeTypes.remove(type);
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        synchronized (ModelImporter.class) {
            return new HashSet<>(mimeTypes);
        }
    }

    @Override
    public CommsMapComponent.ImportResult importData(InputStream source,
            String mime, Bundle b) throws IOException {
        return CommsMapComponent.ImportResult.FAILURE;
    }

    @Override
    public CommsMapComponent.ImportResult importData(Uri uri, String mime,
            Bundle b) throws IOException {

        final int notificationId = NotificationUtil.getInstance()
                .reserveNotifyId();

        try {
            final String path = IOProviderFactory
                    .exists(new File(uri.getPath()))
                            ? uri.getPath()
                            : uri.toString();
            FeatureSetQueryParameters params = new FeatureSetQueryParameters();
            params.names = Collections.singleton(path);
            params.limit = 1;
            if (modelDataStore.queryFeatureSetsCount(params) > 0) {
                if (b != null && b.getBoolean("ignoreExisting"))
                    return CommsMapComponent.ImportResult.IGNORE;

                params.limit = 0;
                try {
                    modelDataStore.deleteFeatureSets(params);
                } catch (DataStoreException e) {
                    return CommsMapComponent.ImportResult.IGNORE;
                }
            }

            Set<ModelInfo> models = ModelInfoFactory.create(path);
            if (models == null || models.isEmpty())
                return CommsMapComponent.ImportResult.FAILURE;

            NotificationUtil
                    .getInstance()
                    .postNotification(
                            notificationId,
                            NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                            NotificationUtil.BLUE,
                            String.format(
                                    ctx.getString(
                                            R.string.importmgr_starting_import),
                                    getContentType()),
                            null, null, false);

            FeatureDataStore2 ds = modelDataStore;

            try {
                ds.acquireModifyLock(true);
            } catch (InterruptedException e) {
                throw new DataStoreException(e);
            }

            FeatureSet fs;
            Envelope.Builder bounds = new Envelope.Builder();
            try {
                // XXX - create feature set for file
                // XXX - display thresholds
                // XXX - name is URI
                ModelInfo first = models.iterator().next();
                fs = new FeatureSet(first.type, first.type, path,
                        Double.MAX_VALUE, 5d);

                final long fsid = ds.insertFeatureSet(fs);

                // Need to include the feature set ID in the set
                // XXX - expose adopt method?
                fs = new FeatureSet(fsid, first.type, first.type, path,
                        Double.MAX_VALUE, 5d,
                        FeatureDataStore.FEATURESET_VERSION_NONE);

                for (final ModelInfo model : models) {
                    // XXX - generate attribute data from descriptor
                    AttributeSet attributes = new AttributeSet();
                    AttributeSet modelAttr = new AttributeSet();
                    modelAttr.setAttribute("uri", model.uri);
                    if (model.type != null)
                        modelAttr.setAttribute("type", model.type);
                    if (model.localFrame != null) {
                        double[] mx = new double[16];
                        model.localFrame.get(mx);
                        modelAttr.setAttribute("localFrame", mx);
                    }
                    modelAttr.setAttribute("srid", model.srid);
                    modelAttr.setAttribute("altitudeMode",
                            model.altitudeMode.name());
                    if (model.location != null) {
                        AttributeSet modelLocation = new AttributeSet();
                        modelLocation.setAttribute("latitude",
                                model.location.getLatitude());
                        modelLocation.setAttribute("longitude",
                                model.location.getLongitude());
                        modelAttr.setAttribute("location", modelLocation);
                    }
                    if (model.resourceMap != null) {
                        AttributeSet modelResourceMap = new AttributeSet();
                        for (Map.Entry<String, String> resource : model.resourceMap
                                .entrySet()) {
                            modelResourceMap.setAttribute(resource.getKey(),
                                    resource.getValue());
                        }
                        modelAttr.setAttribute("resourceMap", modelResourceMap);
                    }
                    attributes.setAttribute("TAK.ModelInfo", modelAttr);
                    attributes.setAttribute(".ATTACHMENT_URIS", new String[] {
                            model.uri
                    });

                    // create point geometry at location (if georeferenced)
                    Geometry geometry = null;
                    if (model.location != null) {
                        Point p = new Point(model.location.getLongitude(),
                                model.location.getLatitude());
                        geometry = p;
                        bounds.add(p.getX(), p.getY());
                    }
                    // style per "3D maps" icon
                    Style style = new IconPointStyle(-1,
                            ModelMapComponent.getPointIconUri(ctx));
                    Feature f = new Feature(fsid,
                            FeatureDataStore2.FEATURE_ID_NONE,
                            model.name,
                            geometry,
                            style,
                            attributes,
                            FeatureDataStore2.TIMESTAMP_NONE,
                            FeatureDataStore2.FEATURE_VERSION_NONE);
                    ds.insertFeature(f);
                }
            } finally {
                ds.releaseModifyLock();
            }

            // Add a handler for this model for use in other tools
            this.contentResolver.addModelHandler(fs, bounds.build());

            Intent i = new Intent(ImportExportMapComponent.ZOOM_TO_FILE_ACTION);
            i.putExtra("filepath", uri.getPath());
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            notificationId,
                            NotificationUtil.GeneralIcon.SYNC_ORIGINAL
                                    .getID(),
                            NotificationUtil.BLUE,
                            String.format(
                                    ctx.getString(
                                            R.string.importmgr_finished_import),
                                    getContentType()),
                            null, i, true);

            return CommsMapComponent.ImportResult.SUCCESS;
        } catch (Throwable e) {
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            notificationId,
                            NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                            NotificationUtil.RED,
                            String.format(
                                    ctx.getString(
                                            R.string.importmgr_failed_import),
                                    getContentType()),
                            null, null, true);

            Log.e("ModelImporter", "Failed to import model", e);
            return CommsMapComponent.ImportResult.FAILURE;
        }
    }

    @Override
    public boolean deleteData(Uri uri, String mime) throws IOException {
        String path;
        if (uri.toString().equals(URI_ALL_MODELS))
            path = "*";
        else if (uri.getScheme() == null || uri.getScheme().equals("file"))
            path = uri.getPath();
        else
            path = uri.toString();
        if (FileSystemUtils.isEmpty(path))
            return false;

        FeatureSetQueryParameters params = null;
        if (!path.equals("*")) {
            // Delete a specific model ('*' = all)
            params = new FeatureSetQueryParameters();
            params.names = Collections.singleton(path);
            params.limit = 1;
        }

        List<File> removedFiles = new ArrayList<>();
        try {
            FeatureSetCursor c = null;
            try {
                c = this.modelDataStore.queryFeatureSets(params);
                while (c.moveToNext()) {
                    // Delete associated files
                    FeatureSet fs = c.get();
                    if (fs == null)
                        continue;
                    // clean up the cache dir
                    FeatureCursor features = null;
                    try {
                        FeatureDataStore2.FeatureQueryParameters fparams = new FeatureDataStore2.FeatureQueryParameters();
                        fparams.featureSetFilter = new FeatureSetQueryParameters();
                        fparams.featureSetFilter.ids = Collections
                                .singleton(fs.getId());
                        features = this.modelDataStore.queryFeatures(fparams);
                        while (features.moveToNext())
                            FileSystemUtils.delete(
                                    GLModelLayer.getCacheDir(features.get()));
                    } finally {
                        if (features == null)
                            features.close();
                    }
                    FileSystemUtils.delete(fs.getName());

                    removedFiles.add(new File(fs.getName()));
                }
            } finally {
                if (c != null)
                    c.close();
            }
            // Delete entry from database
            this.modelDataStore.deleteFeatureSets(params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete model: " + uri, e);
        }

        for (File removed : removedFiles)
            this.contentResolver.removeHandler(removed);

        return false;
    }
}
