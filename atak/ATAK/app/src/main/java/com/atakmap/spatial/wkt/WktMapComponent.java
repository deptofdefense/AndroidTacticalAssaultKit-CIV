
package com.atakmap.spatial.wkt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.features.FeatureEditDropdownReceiver;
import com.atakmap.android.features.FeatureEditListUserSelect;
import com.atakmap.android.hierarchy.HierarchySelectHandler;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import com.atakmap.android.data.DataMgmtReceiver;
import com.atakmap.android.features.FeatureDataStoreDeepMapItemQuery;
import com.atakmap.android.features.FeaturesDetailsDropdownReceiver;
import com.atakmap.android.importexport.ExporterManager;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.importexport.MarshalManager;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.update.AppVersionUpgrade;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataSourceContentFactory;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.PersistentDataSourceFeatureDataStore2;
import com.atakmap.map.layer.feature.ogr.OgrFeatureDataSource;
import com.atakmap.spatial.file.FalconViewContentResolver;
import com.atakmap.spatial.file.FalconViewSpatialDb;
import com.atakmap.spatial.file.FileDatabase;
import com.atakmap.spatial.file.GMLContentResolver;
import com.atakmap.spatial.file.GMLSpatialDb;
import com.atakmap.spatial.file.GpxContentResolver;
import com.atakmap.spatial.file.GpxFileSpatialDb;
import com.atakmap.spatial.file.KmlContentResolver;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.spatial.file.MvtContentResolver;
import com.atakmap.spatial.file.MvtSpatialDb;
import com.atakmap.spatial.file.ShapefileContentResolver;
import com.atakmap.spatial.file.ShapefileMarshal;
import com.atakmap.spatial.file.ShapefileSpatialDb;
import com.atakmap.spatial.file.SpatialDbContentResolver;
import com.atakmap.spatial.file.SpatialDbContentSource;
import com.atakmap.spatial.file.export.GPXExportMarshal;
import com.atakmap.spatial.file.export.KMLExportMarshal;
import com.atakmap.spatial.file.export.KMZExportMarshal;
import com.atakmap.spatial.file.export.SHPExportMarshal;

import org.gdal.gdal.gdal;
import org.gdal.ogr.ogr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class WktMapComponent extends AbstractMapComponent {

    private final static File SPATIAL_DB_FILE = FileSystemUtils
            .getItem("Databases/spatial.sqlite");
    private final static File SPATIAL_DB_DIR = FileSystemUtils
            .getItem("Databases/spatial2.sqlite");

    private static final String TAG = "WktMapComponent";

    private DataSourceFeatureDataStore spatialDb;
    private Set<FileDatabase> fileDatabases;
    private Map<String, ContentSource> contentSources;
    private FeatureLayer layer;
    private MapView mapView;
    private BroadcastReceiver detailsDropdownReceiver;
    private BroadcastReceiver featureEditDropdownReceiver;
    private FeatureEditListUserSelect featureEditSelectHandler;

    /**
     * Token indicating that previously installed providers were invalidated.
     * On provider change, this flag should be set to <code>true</code>, then
     * assigned a new <code>false</code> instance. Background tasks may take a
     * local reference to track changes.
     */
    private AtomicBoolean providerInvalidatedToken = new AtomicBoolean(false);

    private void initComponents(Context context, MapView view) {
        FeatureDataSourceContentFactory
                .register(ShapefileSpatialDb.ZIPPED_SHP_DATA_SOURCE);
        FeatureDataSourceContentFactory
                .register(GMLSpatialDb.ZIPPED_GML_DATA_SOURCE);

        this.mapView = view;

        // Initialize the Spatial DB
        Log.d(TAG, "Initializing SpatialDb");

        ClearContentRegistry.getInstance().registerListener(dataMgmtReceiver);

        refreshPersistedComponents(view);

        // `dataMgmtReceiver` works off the current references, no persisted state
        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(DataMgmtReceiver.ZEROIZE_CONFIRMED_ACTION);

        //register Overlay Manager exporters
        ExporterManager.registerExporter(
                KmlFileSpatialDb.KML_TYPE.toUpperCase(LocaleUtil.getCurrent()),
                KmlFileSpatialDb.KML_FILE_ICON_ID, KMLExportMarshal.class);
        ExporterManager.registerExporter(
                KmlFileSpatialDb.KMZ_TYPE.toUpperCase(LocaleUtil.getCurrent()),
                KmlFileSpatialDb.KML_FILE_ICON_ID, KMZExportMarshal.class);
        ExporterManager.registerExporter(ShapefileSpatialDb.SHP_CONTENT_TYPE,
                ShapefileSpatialDb.SHP_FILE_ICON_ID, SHPExportMarshal.class);
        ExporterManager.registerExporter(
                GpxFileSpatialDb.GPX_CONTENT_TYPE.toUpperCase(LocaleUtil
                        .getCurrent()),
                GpxFileSpatialDb.GPX_FILE_ICON_ID, GPXExportMarshal.class);
    }

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        Log.d(TAG, "Creating WktMapComponent");
        final Context appCtx = context;
        final MapView mapView = view;

        // Register all configured GDAL/OGR drivers
        gdal.SetConfigOption("LIBKML_RESOLVE_STYLE", "yes");
        ogr.RegisterAll();

        initComponents(appCtx, mapView);

        MarshalManager.registerMarshal(ShapefileMarshal.INSTANCE);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (dataMgmtReceiver != null) {
            ClearContentRegistry.getInstance()
                    .unregisterListener(dataMgmtReceiver);
            dataMgmtReceiver = null;
        }

        mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, layer);

        this.spatialDb.dispose();

        synchronized (this) {
            for (FileDatabase db : fileDatabases)
                db.close();
            fileDatabases.clear();

            if (contentSources != null) {
                for (ContentSource entry : this.contentSources.values()) {
                    // remove the overlay
                    view.getMapOverlayManager()
                            .removeFilesOverlay(entry.overlay);
                    // dispose the source
                    entry.impl.dispose();
                }
                contentSources.clear();
            }
        }
    }

    /**************************************************************************/

    private synchronized void refreshPersistedComponents(MapView view) {
        final Context context = view.getContext();

        // reset the invalidation token (stops any on going scans)
        providerInvalidatedToken.set(true);
        providerInvalidatedToken = new AtomicBoolean(false);

        int layerIdx = 0;

        // unregister details dropdown receiver
        if (this.detailsDropdownReceiver != null) {
            this.unregisterReceiver(context, this.detailsDropdownReceiver);
            ((FeaturesDetailsDropdownReceiver) this.detailsDropdownReceiver)
                    .dispose();
            this.detailsDropdownReceiver = null;
        }
        if (this.featureEditDropdownReceiver != null) {
            this.unregisterReceiver(context, this.featureEditDropdownReceiver);
            ((FeaturesDetailsDropdownReceiver) this.featureEditDropdownReceiver)
                    .dispose();
            this.featureEditDropdownReceiver = null;
        }

        HierarchySelectHandler.unregister(FeatureEditListUserSelect.class);

        // remove feature layer
        if (layer != null) {
            List<Layer> layers = view
                    .getLayers(MapView.RenderStack.VECTOR_OVERLAYS);
            layerIdx = layers.indexOf(this.layer);
            if (layerIdx < 0)
                layerIdx = 0;
            mapView.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, layer);
        }
        // close content sources
        if (contentSources != null) {
            for (ContentSource entry : this.contentSources.values()) {
                // remove importers
                ImporterManager.unregisterImporter(entry.typeImporter);
                ImporterManager.unregisterImporter(entry.contentTypeImporter);
                // remove the overlay
                view.getMapOverlayManager().removeFilesOverlay(entry.overlay);
                // dispose the source
                entry.impl.dispose();
            }
            contentSources.clear();
        }
        // close spatial DB
        if (this.spatialDb != null) {
            this.spatialDb.dispose();
        }
        // close file databases
        if (fileDatabases != null) {
            for (FileDatabase db : fileDatabases) {
                ImporterManager.unregisterImporter(db);
                db.close();
            }
            fileDatabases.clear();
        }

        // see if the atak/overlays exists, and, if not, create it
        File overlaysDir = FileSystemUtils.getItem("overlays");
        if (!IOProviderFactory.exists(overlaysDir) &&
                !IOProviderFactory.mkdirs(overlaysDir)) {

            Log.e(TAG, "Failure to make directory "
                    + overlaysDir.getAbsolutePath());
        }
        // open spatial DB
        if (DeveloperOptions.getIntOption("feature-metadata-enabled", 1) == 0) {
            if (IOProviderFactory.exists(SPATIAL_DB_DIR))
                FileSystemUtils.deleteDirectory(SPATIAL_DB_DIR, false);

            if (DeveloperOptions.getIntOption("force-overlays-rebuild",
                    0) == 1) {
                FileSystemUtils.deleteFile(SPATIAL_DB_FILE);
            }

            this.spatialDb = new PersistentDataSourceFeatureDataStore(
                    SPATIAL_DB_FILE);
        } else {
            if (IOProviderFactory.exists(SPATIAL_DB_FILE))
                FileSystemUtils.deleteFile(SPATIAL_DB_FILE);

            if (DeveloperOptions.getIntOption("force-overlays-rebuild", 0) == 1)
                FileSystemUtils.deleteDirectory(SPATIAL_DB_DIR, true);

            OgrFeatureDataSource.metadataEnabled = true;
            this.spatialDb = new PersistentDataSourceFeatureDataStore2(
                    SPATIAL_DB_DIR);
        }

        this.layer = new FeatureLayer("Spatial Database", this.spatialDb);
        // open content sources
        if (this.contentSources == null)
            this.contentSources = new HashMap<>();

        FeatureDataStoreDeepMapItemQuery query = new FeatureDataStoreDeepMapItemQuery(
                this.layer);

        // KML files
        addContentSource(context, new KmlFileSpatialDb(this.spatialDb),
                new KmlContentResolver(view, this.spatialDb), query);

        // SHP files
        addContentSource(context, new ShapefileSpatialDb(this.spatialDb),
                new ShapefileContentResolver(view, this.spatialDb), query);

        // GPX files
        addContentSource(context, new GpxFileSpatialDb(this.spatialDb),
                new GpxContentResolver(view, this.spatialDb), query);

        // GML files
        addContentSource(context, new GMLSpatialDb(this.spatialDb),
                new GMLContentResolver(view, this.spatialDb), query);

        // FalconView files
        FalconViewContentResolver fvResolver = new FalconViewContentResolver(
                view, this.spatialDb);
        addContentSource(context, new FalconViewSpatialDb(this.spatialDb,
                FalconViewSpatialDb.LPT), fvResolver, query);
        addContentSource(context, new FalconViewSpatialDb(this.spatialDb,
                FalconViewSpatialDb.DRW), fvResolver, query);

        // Mapbox Vector Tiles files
        MvtContentResolver mvtResolver = new MvtContentResolver(
                view, this.spatialDb);
        addContentSource(context, new MvtSpatialDb(this.spatialDb),
                mvtResolver, query);

        // add the overlays
        for (ContentSource entry : this.contentSources
                .values()) {
            mapView.getMapOverlayManager().addFilesOverlay(entry.overlay);
        }

        for (ContentSource source : this.contentSources.values()) {
            ImporterManager.registerImporter(source.typeImporter);
            ImporterManager.registerImporter(source.contentTypeImporter);
        }
        // XXX - open file DBs
        if (this.fileDatabases == null)
            this.fileDatabases = new HashSet<>();

        // Register details dropdown receiver
        this.detailsDropdownReceiver = new FeaturesDetailsDropdownReceiver(view,
                this.spatialDb);
        DocumentedIntentFilter i = new DocumentedIntentFilter();
        i.addAction(FeaturesDetailsDropdownReceiver.SHOW_DETAILS,
                "Show feature details");
        i.addAction(FeaturesDetailsDropdownReceiver.TOGGLE_VISIBILITY,
                "Toggle feature visibility");
        this.registerReceiver(context,
                detailsDropdownReceiver, i);

        // Register the feature edit dropdown receiver
        this.featureEditDropdownReceiver = new FeatureEditDropdownReceiver(view,
                this.spatialDb);
        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(FeatureEditDropdownReceiver.SHOW_EDIT,
                "Show feature edit drop down");
        this.registerReceiver(context, featureEditDropdownReceiver, f);

        // Used for bulk feature editing
        featureEditSelectHandler = new FeatureEditListUserSelect(view);
        HierarchySelectHandler.register(FeatureEditListUserSelect.class,
                featureEditSelectHandler);

        // start background initialization
        this.backgroundInit(this.spatialDb);
    }

    private void addContentSource(Context context, SpatialDbContentSource src,
            SpatialDbContentResolver res,
            FeatureDataStoreDeepMapItemQuery query) {
        src.setContentResolver(res);
        this.contentSources.put(src.getType(),
                new ContentSource(context, src, query));
    }

    private void backgroundInit(final DataSourceFeatureDataStore fdb) {
        final AtomicBoolean cancelToken = this.providerInvalidatedToken;

        // Spawn a new thread to initialize this component,
        // so ATAK can continue to run in the background as
        // we're doing the FS/KML/DB operations.
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    long s = SystemClock.elapsedRealtime();

                    // if any files were moved by AppVersionUpgrade, make the data
                    // store do a refresh prior to the full scan to reduce the
                    // number of changes in the transaction
                    if (AppVersionUpgrade.OVERLAYS_MIGRATED) {
                        fdb.beginBulkModification();
                        try {
                            fdb.refresh();
                        } finally {
                            fdb.endBulkModification(true);
                        }
                    }

                    synchronized (WktMapComponent.this) {
                        // check for provider change
                        if (cancelToken.get())
                            return;

                        if (DeveloperOptions.getIntOption(
                                "feature-metadata-enabled", 1) != 0)
                            mapView.addLayer(
                                    MapView.RenderStack.VECTOR_OVERLAYS,
                                    0,
                                    layer);

                        // TODO: add this back when GeoJson actually parses the files
                        //db = new GeoJsonFileDatabase(appCtx, mapView);
                        //ImporterManager.registerImporter(db);
                    }

                    fullScan(cancelToken);

                    Log.d(TAG, "initialized: "
                            + (SystemClock.elapsedRealtime() - s) + "ms");
                } catch (NullPointerException e) {
                    Log.e(TAG,
                            "Initialization failed, application may have exited prior to completion",
                            e);
                }
            }
        }, "WktMapInitThread");
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    private void fullScan(AtomicBoolean cancelToken) {
        synchronized (this) {

            try {
                // XXX - legacy implementation needs bulk modification for
                //       transaction, new implementation does not
                if (this.spatialDb instanceof PersistentDataSourceFeatureDataStore)
                    this.spatialDb.beginBulkModification();
                boolean success = false;
                try {
                    // check if cancelled
                    if (cancelToken.get())
                        return;

                    // refresh the data store and drop all invalid entries
                    this.spatialDb.refresh();

                    // check if cancelled
                    if (cancelToken.get())
                        return;

                    Collection<SpatialDbContentSource> sources = new ArrayList<>(
                            this.contentSources.size());
                    for (ContentSource entry : this.contentSources
                            .values()) {
                        sources.add(entry.impl);
                    }

                    // Add existing content to the resolvers
                    for (SpatialDbContentSource s : sources) {
                        if (cancelToken.get())
                            return;
                        SpatialDbContentResolver res = s.getContentResolver();
                        if (res != null)
                            res.scan(s);
                    }

                    final String[] mountPoints = FileSystemUtils
                            .findMountPoints();

                    if (cancelToken.get())
                        return;

                    // single "Overlays" directory scan
                    File overlaysDir;
                    for (String mountPoint : mountPoints) {
                        overlaysDir = new File(mountPoint, "overlays");
                        OverlaysScanner.scan(this.spatialDb,
                                overlaysDir,
                                sources,
                                this.fileDatabases,
                                cancelToken);
                    }

                    success = true;
                } finally {
                    // XXX - legacy implementation needs bulk modification for
                    //       transaction, new implementation does not
                    if (this.spatialDb instanceof PersistentDataSourceFeatureDataStore)
                        this.spatialDb.endBulkModification(success);
                }

            } catch (SQLiteException e) {
                Log.e(TAG, "Scan failed.", e);
            }
        }

        if (DeveloperOptions.getIntOption("feature-metadata-enabled", 1) == 0)
            this.mapView.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, 0,
                    this.layer);
    }

    /**************************************************************************/

    private class WktImporter implements Importer {
        private final String contentType;
        private final SpatialDbContentSource source;

        WktImporter(String contentType, SpatialDbContentSource source) {
            this.contentType = contentType;
            this.source = source;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public Set<String> getSupportedMIMETypes() {
            return this.source.getSupportedMIMETypes();
        }

        @Override
        public ImportResult importData(InputStream source, String mime,
                Bundle bundle) {
            return ImportResult.FAILURE;
        }

        @Override
        public ImportResult importData(Uri uri, String mime, Bundle bundle)
                throws IOException {
            if (uri.getScheme() != null && !uri.getScheme().equals("file"))
                return ImportResult.FAILURE;

            ImportResult retval;
            synchronized (WktMapComponent.this) {
                WktMapComponent.this.spatialDb.beginBulkModification();
                boolean success = false;
                try {
                    retval = this.source.importData(uri, mime, bundle);
                    success = true;
                } finally {
                    WktMapComponent.this.spatialDb.endBulkModification(success);
                }
            }
            return retval;
        }

        @Override
        public boolean deleteData(Uri uri, String mime) {
            if (uri.getScheme() != null && !uri.getScheme().equals("file"))
                return false;

            synchronized (WktMapComponent.this) {
                WktMapComponent.this.spatialDb.beginBulkModification();
                boolean success = false;
                try {
                    this.source.deleteData(uri, mime);
                    success = true;
                } finally {
                    WktMapComponent.this.spatialDb.endBulkModification(success);
                }
            }
            return true;
        }
    }

    ClearContentRegistry.ClearContentListener dataMgmtReceiver = new ClearContentRegistry.ClearContentListener() {
        @Override
        public void onClearContent(boolean clearmaps) {
            Log.d(TAG, "Deleting spatial data");

            //Clean up Spatial DBs
            final String[] mountPoints = FileSystemUtils.findMountPoints();

            // build out the list of all files in the features database
            Set<File> spatialDbFilesToDelete = new HashSet<>();

            DataSourceFeatureDataStore.FileCursor result = null;
            try {
                result = spatialDb.queryFiles();
                if (result != null) {
                    while (result.moveToNext())
                        spatialDbFilesToDelete.add(result.getFile());
                }
            } finally {
                if (result != null)
                    result.close();
            }

            // clear out the feature DB, per content
            for (ContentSource entry : contentSources.values()) {
                SpatialDbContentSource contentSource = entry.impl;
                // delete from DB
                contentSource.deleteAll();

                //delete data directory
                for (String mountPoint : mountPoints) {
                    File scanDir = new File(mountPoint,
                            contentSource.getFileDirectoryName());
                    if (IOProviderFactory.exists(scanDir)
                            && IOProviderFactory.isDirectory(scanDir))
                        FileSystemUtils.deleteDirectory(scanDir, true);
                }
            }

            // delete any remaining files that may have been externally imported
            for (File overlayFile : spatialDbFilesToDelete)
                if (IOProviderFactory.exists(overlayFile))
                    FileSystemUtils.deleteFile(overlayFile);

            //Clean up File Databases
            for (FileDatabase fileDatabase : fileDatabases) {

                List<String> files = fileDatabase.queryFiles();

                //delete from DB
                fileDatabase.deleteAll();

                //delete from UI
                if (fileDatabase.getRootGroup() != null) {
                    fileDatabase.getRootGroup().clearItems();
                    fileDatabase.getRootGroup().clearGroups();
                }

                //delete files from file system (atak dir and external/linked files)
                for (String filepath : files) {
                    FileSystemUtils.deleteFile(new File(FileSystemUtils
                            .sanitizeWithSpacesAndSlashes(filepath)));
                }

                //delete data directory
                for (String mountPoint : mountPoints) {
                    File scanDir = new File(mountPoint, fileDatabase
                            .getFileDirectory().getName());
                    if (IOProviderFactory.exists(scanDir)
                            && IOProviderFactory.isDirectory(scanDir))
                        FileSystemUtils.deleteDirectory(scanDir, true);
                }
            }
        }
    };

    private final class ContentSource {
        final SpatialDbContentSource impl;
        final MapOverlay overlay;
        final Importer typeImporter;
        final Importer contentTypeImporter;

        ContentSource(Context context, SpatialDbContentSource src,
                FeatureDataStoreDeepMapItemQuery query) {
            this.impl = src;

            this.overlay = impl.createOverlay(context, query);

            // XXX - resolve type v. content type post 3.3
            this.typeImporter = new WktImporter(impl.getType(), impl);
            this.contentTypeImporter = new WktImporter(impl.getContentType(),
                    impl);

        }
    }
}
