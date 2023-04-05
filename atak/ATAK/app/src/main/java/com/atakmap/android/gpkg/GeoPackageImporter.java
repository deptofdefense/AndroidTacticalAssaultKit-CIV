
package com.atakmap.android.gpkg;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importfiles.sort.ImportInPlaceResolver;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapView.RenderStack;
import com.atakmap.android.maps.hittest.DeepHitTestControlQuery;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.util.NotificationUtil.GeneralIcon;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.content.CatalogCurrency;
import com.atakmap.content.CatalogCurrencyRegistry;
import com.atakmap.content.CatalogDatabase;
import com.atakmap.content.CatalogDatabase.CatalogCursor;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.StatementIface;
import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.map.hittest.HitTestControl;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.MultiLayer.OnLayersChangedListener;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.gpkg.GeoPackageFeatureDataStore;

import com.atakmap.annotations.ModifierApi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GeoPackageImporter implements Importer {

    public static final String CONTENT_TYPE = "GeoPackage";
    public static final String MIME_TYPE = "application/octet-stream";

    private static final String TAG = "GeoPackageImporter";
    private static final Set<String> supportedMIME_Types = Collections
            .singleton(MIME_TYPE);
    private static final ImportResolver importResolver = new GeoPackageImportResolver();

    private final CatalogCurrency currency = new Currency();
    private final GeoPackageImportDatabase importDB = new GeoPackageImportDatabase(
            currency);
    private final GeoPackageContentResolver contentResolver;

    private final MapView mapView;
    private final String parentName;
    private final String iconURI;
    private final GeoPackageMapOverlayParent parentOverlay;
    private MultiLayer parentLayer;

    private boolean isDisposed;

    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public GeoPackageImporter(MapView view,
            String layerName,
            String iconURI) {
        mapView = view;
        this.parentName = layerName;
        this.iconURI = iconURI;
        this.contentResolver = new GeoPackageContentResolver(view);
        this.parentOverlay = new GeoPackageMapOverlayParent();
        mapView.getMapOverlayManager().addFilesOverlay(parentOverlay);
        URIContentManager.getInstance().registerResolver(contentResolver);
        isDisposed = false;
    }

    public synchronized void dispose() {
        isDisposed = true;

        getParentLayer().removeAllLayers();
        mapView.removeLayer(RenderStack.VECTOR_OVERLAYS, getParentLayer());
        importDB.close();
        mapView.getMapOverlayManager().removeFilesOverlay(parentOverlay);
        URIContentManager.getInstance().unregisterResolver(contentResolver);
        contentResolver.dispose();
    }

    public static ImportResolver getImportResolver() {
        return importResolver;
    }

    public void loadImports() {
        final Collection<String> importPaths = importDB.queryFiles();

        ThreadPoolExecutor importPool = new ThreadPoolExecutor(0, 5, 0,
                TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>());

        for (String importPath : importPaths) {
            if (isDisposed)
                break;
            final File importFile = new File(
                    FileSystemUtils.sanitizeWithSpacesAndSlashes(importPath));
            importPool.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (GeoPackageImporter.this) {
                        if (isDisposed)
                            return;
                        if (IOProviderFactory.exists(importFile)
                                && importFile(importFile)) {
                            importDB.updateCurrency(importFile);
                        } else {
                            Log.w(TAG,
                                    "Failed to import " + importFile.getName());
                            importDB.deleteCatalog(importFile);
                        }
                    }
                }
            });
        }
    }

    public void loadOverlays(File[] overlayDirs) {
        for (File overlayDir : overlayDirs) {
            if (IOProviderFactory.exists(overlayDir)) {
                File[] overlayFiles = IOProviderFactory.listFiles(overlayDir);
                if (overlayFiles != null) {
                    for (File file : overlayFiles) {
                        if (importResolver.match(file))
                            importFile(file);
                    }
                }
            } else {
                Log.d(TAG, "The "
                        + overlayDir.getName()
                        + " directory is not present");
            }
        }
    }

    //==================================
    //  Importer INTERFACE
    //==================================

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        return supportedMIME_Types;
    }

    @Override
    public ImportResult importData(InputStream source,
            String mime,
            Bundle b)
            throws IOException {
        return ImportResult.FAILURE;
    }

    @Override
    public synchronized ImportResult importData(Uri uri,
            String mime,
            Bundle bundle)
            throws IOException {
        ImportResult result = ImportResult.FAILURE;

        if (!isDisposed
                && uri != null
                && "file".equals(uri.getScheme())
                && uri.getPath() != null) {
            File file = new File(FileSystemUtils
                    .sanitizeWithSpacesAndSlashes(uri.getPath()));
            boolean showNotifications = bundle != null && bundle.getBoolean(
                    ImportReceiver.EXTRA_SHOW_NOTIFICATIONS);
            boolean zoomToFile = bundle != null && bundle.getBoolean(
                    ImportReceiver.EXTRA_ZOOM_TO_FILE);
            boolean hideFile = bundle != null && bundle.getBoolean(
                    ImportReceiver.EXTRA_HIDE_FILE);
            int notificationID = showNotifications
                    ? NotificationUtil.getInstance().reserveNotifyId()
                    : 0;

            if (showNotifications) {
                NotificationUtil.getInstance().postNotification(notificationID,
                        GeneralIcon.SYNC_ORIGINAL.getID(),
                        NotificationUtil.BLUE,
                        "Starting Import: " + file.getName(),
                        null, null);
            }

            CatalogCursor cursor = importDB.queryCatalog(file);

            if (!cursor.moveToNext()) {
                if (importFile(file)) {
                    importDB.addCatalogEntry(file, currency);
                    result = ImportResult.SUCCESS;
                }
            } else if (!currency.isValidApp(file,
                    cursor.getAppVersion(),
                    cursor.getAppData())) {
                //
                // File has been updated since import, reimport it.
                //
                removeLayer(file.getName());
                if (importFile(file)) {
                    importDB.updateCurrency(file);
                    result = ImportResult.SUCCESS;
                } else {
                    importDB.deleteCatalog(file);
                    removeLayer(file.getName());
                    contentResolver.removeHandler(file);
                }
            } else // File is already imported.
            {
                result = ImportResult.IGNORE;
            }

            Intent i = new Intent(ImportExportMapComponent.ZOOM_TO_FILE_ACTION);
            i.putExtra("filepath", file.getAbsolutePath());
            if (showNotifications) {
                switch (result) {
                    case SUCCESS:

                        NotificationUtil.getInstance().postNotification(
                                notificationID,
                                GeneralIcon.SYNC_SUCCESS.getID(),
                                NotificationUtil.GREEN,
                                "Finished Import: " + file.getName(),
                                null, i, true);
                        break;

                    case IGNORE:

                        NotificationUtil.getInstance().postNotification(
                                notificationID,
                                GeneralIcon.SYNC_SUCCESS.getID(),
                                NotificationUtil.GREEN,
                                "Skipped Duplicate Import: "
                                        + file.getName(),
                                null, i, true);
                        break;

                    default: // DEFERRED & ERROR

                        NotificationUtil.getInstance().postNotification(
                                notificationID,
                                GeneralIcon.SYNC_ERROR.getID(),
                                NotificationUtil.RED,
                                "Failed Import: " + file.getName(),
                                null, null, true);
                        break;
                }
            }
            if (zoomToFile)
                AtakBroadcast.getInstance().sendBroadcast(i);
            if (hideFile) {
                URIContentHandler h = contentResolver.getHandler(file);
                if (h != null && h.isActionSupported(Visibility.class))
                    ((Visibility) h).setVisible(false);
            }
        }

        return result;
    }

    @Override
    public synchronized boolean deleteData(Uri uri,
            String mime) {
        boolean result = false;

        if (!isDisposed
                && uri != null
                && "file".equals(uri.getScheme())
                && uri.getPath() != null) {
            final File file = new File(FileSystemUtils
                    .sanitizeWithSpacesAndSlashes(uri.getPath()));

            removeLayer(file.getName());
            importDB.deleteCatalog(file);
            contentResolver.removeHandler(file);
            result = true;
        }

        return result;
    }

    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================

    //==================================
    //  PRIVATE NESTED TYPES
    //==================================

    private static class Currency
            implements CatalogCurrency {
        //==============================
        //
        //  PUBLIC INTERFACE
        //
        //==============================

        //==============================
        //  CatalogCurrency INTERFACE
        //==============================

        @Override
        public byte[] getAppData(File file) {
            return ByteBuffer.wrap(new byte[2 * Long.SIZE / 8])
                    .order(ByteOrder.BIG_ENDIAN)
                    .putLong(FileSystemUtils.getFileSize(file))
                    .putLong(FileSystemUtils.getLastModified(file))
                    .array();
        }

        @Override
        public int getAppVersion() {
            return 0;
        }

        @Override
        public String getName() {
            return "GeoPackageImporter.Currency";
        }

        @Override
        public boolean isValidApp(File f,
                int appVersion,
                byte[] appData) {
            boolean valid = false;

            if (appVersion == getAppVersion()) {
                ByteBuffer buffer = ByteBuffer.wrap(appData)
                        .order(ByteOrder.BIG_ENDIAN);

                valid = buffer.getLong() == FileSystemUtils.getFileSize(f)
                        && buffer.getLong() == FileSystemUtils
                                .getLastModified(f);
            }

            return valid;
        }
    }

    private static class GeoPackageImportDatabase
            extends CatalogDatabase {
        //==============================
        //
        //  PUBLIC INTERFACE
        //
        //==============================

        public GeoPackageImportDatabase(CatalogCurrency currency) {
            super(openImportDatabase(), new CatalogCurrencyRegistry());
            currencyRegistry.register(this.currency = currency);
            updateCurrencyStmt = database.compileStatement(updateCurrencyQuery);
        }

        public synchronized void updateCurrency(File file) {
            updateCurrencyStmt.bind(1, currency.getAppVersion());
            updateCurrencyStmt.bind(2, currency.getAppData(file));
            updateCurrencyStmt.bind(3, file.getAbsolutePath());
            updateCurrencyStmt.execute();
            updateCurrencyStmt.clearBindings();
        }

        //==============================
        //  CatalogDatabase INTERFACE
        //==============================

        @Override
        public synchronized void close() {
            updateCurrencyStmt.close();
            super.close();
        }

        //==============================
        //
        //  PRIVATE IMPLEMENTATION
        //
        //==============================

        //==============================
        //  PRIVATE METHODS
        //==============================

        //==============================
        //  PRIVATE REPRESENTATION
        //==============================

        private static final String updateCurrencyQuery = "UPDATE "
                + TABLE_CATALOG
                + " SET " + COLUMN_CATALOG_APP_VERSION + " = ?, "
                + COLUMN_CATALOG_APP_DATA + " = ?"
                + " WHERE " + COLUMN_CATALOG_PATH + " = ?";

        private final CatalogCurrency currency;
        private final StatementIface updateCurrencyStmt;
    }

    private static class GeoPackageImportResolver
            extends ImportInPlaceResolver {
        //==============================
        //
        //  PUBLIC INTERFACE
        //
        //==============================

        public GeoPackageImportResolver() {
            super("gpkg", null, true, false, true, "GeoPackage Feature Layer");
        }

        //==============================
        //  ImportResolver INTERFACE
        //==============================

        @Override
        public Drawable getIcon() {
            MapView mv = MapView.getMapView();
            return mv != null ? mv.getContext().getDrawable(R.drawable.gpkg)
                    : null;
        }

        @Override
        public boolean match(File file) {
            return super.match(file) && GeoPackage.isGeoPackage(file);
        }

        //==============================
        //
        //  PROTECTED INTERFACE
        //
        //==============================

        //==============================
        //  ImportResolver INTERFACE
        //==============================

        @Override
        protected void onFileSorted(File src,
                File dst,
                Set<SortFlags> flags) {
            Intent i = new Intent(
                    ImportExportMapComponent.ACTION_IMPORT_DATA)
                            .putExtra(ImportReceiver.EXTRA_CONTENT,
                                    CONTENT_TYPE)
                            .putExtra(ImportReceiver.EXTRA_MIME_TYPE, MIME_TYPE)
                            .putExtra(ImportReceiver.EXTRA_URI,
                                    Uri.fromFile(dst).toString());

            if (flags.contains(SortFlags.SHOW_NOTIFICATIONS))
                i.putExtra(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS, true);
            if (flags.contains(SortFlags.ZOOM_TO_FILE))
                i.putExtra(ImportReceiver.EXTRA_ZOOM_TO_FILE, true);
            if (flags.contains(SortFlags.HIDE_FILE))
                i.putExtra(ImportReceiver.EXTRA_HIDE_FILE, true);

            AtakBroadcast.getInstance().sendBroadcast(i);
        }

        @Override
        public Pair<String, String> getContentMIME() {
            return new Pair<>(CONTENT_TYPE, MIME_TYPE);
        }
    }

    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================

    //==================================
    //  PRIVATE METHODS
    //==================================

    private void addOverlay(FeatureLayer3 layer) {
        parentOverlay.add(new GeoPackageMapOverlay(mapView.getContext(),
                layer, iconURI));
    }

    private synchronized MultiLayer getParentLayer() {
        if (parentLayer == null) {
            for (Layer l : mapView.getLayers(RenderStack.VECTOR_OVERLAYS)) {
                if (l instanceof MultiLayer && l.getName().equals(parentName)) {
                    parentLayer = (MultiLayer) l;
                    break;
                }
            }
            if (parentLayer == null) {
                mapView.addLayer(RenderStack.VECTOR_OVERLAYS,
                        parentLayer = new MultiLayer(parentName));
                parentLayer.addOnLayersChangedListener(
                        new OnLayersChangedListener() {
                            @Override
                            public void onLayerAdded(MultiLayer parent,
                                    Layer layer) {
                                if (layer instanceof FeatureLayer3) {
                                    addOverlay((FeatureLayer3) layer);
                                }
                            }

                            @Override
                            public void onLayerPositionChanged(
                                    MultiLayer parent,
                                    Layer layer,
                                    int oldPosition,
                                    int newPosition) {
                            }

                            @Override
                            public void onLayerRemoved(MultiLayer parent,
                                    Layer layer) {
                                removeOverlay(layer.getName());
                                if (layer instanceof FeatureLayer3) {
                                    ((FeatureLayer3) layer).getDataStore()
                                            .dispose();
                                }
                            }
                        });
            }
        }

        return parentLayer;
    }

    private boolean importFile(File packageFile) {
        boolean result = false;

        try {
            final long s = SystemClock.elapsedRealtime();
            GeoPackageFeatureDataStore dataStore = new GeoPackageFeatureDataStore(
                    packageFile);
            final long e = SystemClock.elapsedRealtime();
            Log.d(TAG, "Loaded Geopackage " + packageFile.getName() + " in "
                    + (e - s) + "ms");
            FeatureLayer3 layer = new FeatureLayer3(packageFile.getName(),
                    Adapters.adapt(dataStore));

            getParentLayer().addLayer(layer);

            // Create content handler
            contentResolver.addHandler(dataStore);

            result = true;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load GeoPackage file: " + packageFile, t);
        }

        return result;
    }

    private static DatabaseIface openImportDatabase() {
        final File f = FileSystemUtils.getItem("Databases"
                + File.separator
                + "GeoPackageImports.sqlite");
        DatabaseIface db = IOProviderFactory.createDatabase(f,
                DatabaseInformation.OPTION_ENSURE_PARENT_DIRS);
        if (db == null && IOProviderFactory.exists(f)) {
            IOProviderFactory.delete(f, IOProvider.SECURE_DELETE);
            db = IOProviderFactory.createDatabase(f);
        }
        return db;
    }

    private void removeLayer(String packageName) {
        MultiLayer parent = getParentLayer();

        for (Layer layer : parent.getLayers()) {
            if (layer.getName().equals(packageName)) {
                parent.removeLayer(layer);
                break;
            }
        }
    }

    private void removeOverlay(final String packageName) {
        MapOverlay existingOverlay = parentOverlay.remove(packageName);
        if (existingOverlay != null)
            mapView.getMapOverlayManager().removeOverlay(existingOverlay);
    }

    private class GeoPackageMapOverlayParent extends MapOverlayParent {

        private final DefaultMapGroup mapGroup;
        private final GeoPackageMapItemQuery queryFunc;
        private final List<DeepMapItemQuery> childQueries = new ArrayList<>();

        GeoPackageMapOverlayParent() {
            super(mapView, parentName, parentName, iconURI, -1, false);
            this.queryFunc = new GeoPackageMapItemQuery();
            this.mapGroup = new DefaultMapGroup(getName());
            this.mapGroup.setMetaBoolean("customRenderer", true);
        }

        @Override
        public MapGroup getRootGroup() {
            return this.mapGroup;
        }

        @Override
        public DeepMapItemQuery getQueryFunction() {
            return this.queryFunc;
        }

        @Override
        public boolean add(MapOverlay overlay) {
            childQueries.add(overlay.getQueryFunction());
            return super.add(overlay);
        }

        @Override
        public void remove(MapOverlay overlay) {
            super.remove(overlay);
            childQueries.remove(overlay.getQueryFunction());
        }

        @Override
        public MapOverlay remove(String id) {
            MapOverlay overlay = super.remove(id);
            if (overlay != null)
                childQueries.remove(overlay.getQueryFunction());
            return overlay;
        }

        @Override
        public void clear() {
            super.clear();
            childQueries.clear();
        }
    }

    /**
     * This map item query redirects to several children queries since each
     * geopackage gets its own overlay/map group
     */
    private class GeoPackageMapItemQuery
            implements DeepMapItemQuery, DeepHitTestControlQuery {

        @Override
        public MapItem deepFindItem(Map<String, String> metadata) {
            for (DeepMapItemQuery query : parentOverlay.childQueries) {
                MapItem ret = query.deepFindItem(metadata);
                if (ret != null)
                    return ret;
            }
            return null;
        }

        @Override
        public List<MapItem> deepFindItems(Map<String, String> metadata) {
            List<MapItem> ret = new ArrayList<>();
            for (DeepMapItemQuery query : parentOverlay.childQueries) {
                List<MapItem> items = query.deepFindItems(metadata);
                if (items != null)
                    ret.addAll(items);
            }
            return ret;
        }

        @Override
        public MapItem deepFindClosestItem(GeoPoint location, double threshold,
                Map<String, String> metadata) {
            for (DeepMapItemQuery query : parentOverlay.childQueries) {
                MapItem ret = query.deepFindClosestItem(location, threshold,
                        metadata);
                if (ret != null)
                    return ret;
            }
            return null;
        }

        @Override
        public Collection<MapItem> deepFindItems(GeoPoint location,
                double radius, Map<String, String> metadata) {
            List<MapItem> ret = new ArrayList<>();
            for (DeepMapItemQuery query : parentOverlay.childQueries) {
                Collection<MapItem> items = query.deepFindItems(location,
                        radius, metadata);
                if (items != null)
                    ret.addAll(items);
            }
            return ret;
        }

        @Override
        public Collection<MapItem> deepFindItems(GeoBounds bounds,
                Map<String, String> metadata) {
            List<MapItem> ret = new ArrayList<>();
            for (DeepMapItemQuery query : parentOverlay.childQueries) {
                Collection<MapItem> items = query.deepFindItems(bounds,
                        metadata);
                if (items != null)
                    ret.addAll(items);
            }
            return ret;
        }

        @Override
        public SortedSet<MapItem> deepHitTest(MapView mapView,
                HitTestQueryParameters params,
                Map<Layer2, Collection<HitTestControl>> controls) {
            SortedSet<MapItem> ret = new TreeSet<>(
                    MapItem.ZORDER_HITTEST_COMPARATOR);
            for (DeepMapItemQuery query : parentOverlay.childQueries) {
                if (query instanceof DeepHitTestControlQuery) {
                    SortedSet<MapItem> items = ((DeepHitTestControlQuery) query)
                            .deepHitTest(mapView, params, controls);
                    if (items != null)
                        ret.addAll(items);
                }
            }
            return ret;
        }
    }
}
