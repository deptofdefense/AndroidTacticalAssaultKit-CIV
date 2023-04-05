
package com.atakmap.android.layers;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;

import com.atakmap.android.contentservices.ServiceFactory;
import com.atakmap.android.contentservices.ogc.WMSQuery;
import com.atakmap.android.contentservices.ogc.WMTSQuery;
import com.atakmap.android.grg.GRGMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.kmz.KMZContentResolver;
import com.atakmap.android.layers.kmz.KMZPackageImporter;
import com.atakmap.android.layers.overlay.MapControlsOverlay;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapView.RenderStack;
import com.atakmap.android.user.VolumeSwitchManager;
import com.atakmap.android.wfs.WFSImporter;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.layer.ProxyLayer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.raster.AbstractDataStoreRasterLayer2;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.PersistentRasterDataStore;
import com.atakmap.map.layer.raster.RasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.nativeimagery.NativeImageryRasterLayer2;
import com.atakmap.math.Rectangle;
import com.atakmap.android.importfiles.resource.RemoteResourceImporter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LayersMapComponent extends AbstractMapComponent
        implements
        ProxyLayer.OnProxySubjectChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final static File SAVED_DATABASE_FILE = FileSystemUtils
            .getItem("/Databases/layers3.sqlite");

    public final static File LAYERS_PRIVATE_DIR = FileSystemUtils
            .getItem("/Databases/.layersprivate");

    public static final String TAG = "LayersMapComponent";

    private static RasterDataStore.DatasetQueryParameters NATIVE_QUERY_PARAMS;
    private final static RasterDataStore.DatasetQueryParameters MOBILE_QUERY_PARAMS2;
    static {
        if (!IOProviderFactory.exists(LAYERS_PRIVATE_DIR)) {
            if (!IOProviderFactory.mkdirs(LAYERS_PRIVATE_DIR)) {
                Log.e(TAG, "Error creating directories");
            }
        }

        // use default filter
        NATIVE_QUERY_PARAMS = null;

        // use default filter
        MOBILE_QUERY_PARAMS2 = null;
    }

    static {
        // registration is internal as layer/renderer are package private
        GLLayerFactory.register(GLLayerOutlinesLayer.SPI2);
    }

    /* may only go from null to non-null */
    private static LocalRasterDataStore layersDb;

    private VolumeSwitchManager _volumeKeySwitcher = null;
    private Handler _contentHandler;
    private ContentObserver _contentObserver;
    private Map<String, LayerSelection> _layerSelections;
    protected LayerSelectionAdapter _nativeLayersAdapter;
    protected MobileLayerSelectionAdapter _mobileLayersAdapter2;
    protected LayersManagerBroadcastReceiver _layersManagerReceiver;
    private ZoomToLayerReceiver _zoomToLayerReceiver;
    private MapView _mapView;
    private Context _context;
    private final List<ImportResolver> _importResolvers = new ArrayList<>();
    private final List<Importer> _importers = new ArrayList<>();
    private final List<FileContentResolver> _contentResolvers = new ArrayList<>();
    protected MobileOutlinesDataStore mobileOutlines;
    protected OutlinesFeatureDataStore nativeOutlines;
    private FeatureLayer mobileOutlinesLayer;
    private FeatureLayer nativeOutlinesLayer;

    public static final String IMPORTER_CONTENT_TYPE = "External Native Data";
    public static final String IMPORTER_DEFAULT_MIME_TYPE = "application/octet-stream";

    private final static String[] IMPORTER_MIME_TYPES = new String[] {
            IMPORTER_DEFAULT_MIME_TYPE,
            WFSImporter.MIME_XML,
            "inode/directory"
    };
    private final static String[] IMPORTER_HINTS = new String[] {
            "native", null
    };

    protected AbstractDataStoreRasterLayer2 nativeLayers;
    protected AbstractDataStoreRasterLayer2 mobileLayers2;

    protected CardLayer rasterLayers;

    private Map<RasterLayer2, LayerSelectionAdapter> layerToAdapter;

    private final GRGMapComponent grgs;

    private MapControlsOverlay mapControls;

    private WMSQuery wmsq;
    private WMTSQuery wmtsq;

    private final OnSharedPreferenceChangeListener controlPrefsChangedListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {

            if (key == null)
                return;

            if (key.equals("volumemapswitcher")) {
                _volumeKeySwitcher.setEnabled(sharedPreferences.getBoolean(
                        "volumemapswitcher",
                        true));
            }
        }
    };

    public LayersMapComponent() {
        this.grgs = new GRGMapComponent();
    }

    @Override
    public void onCreate(final Context context, Intent intent, MapView view) {

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        //if (prefs.getBoolean("mobacInImagery", false)) {
        // XXX - should be controlled by DatasetDescriptor subclass 
        NATIVE_QUERY_PARAMS = new RasterDataStore.DatasetQueryParameters();
        NATIVE_QUERY_PARAMS.remoteLocalFlag = RasterDataStore.DatasetQueryParameters.RemoteLocalFlag.LOCAL;
        NATIVE_QUERY_PARAMS.datasetTypes = new HashSet<>();
        NativeImageryRasterLayer2
                .getDefaultDatasetTypes(NATIVE_QUERY_PARAMS.datasetTypes);
        NATIVE_QUERY_PARAMS.datasetTypes.add("osmdroid");
        NATIVE_QUERY_PARAMS.datasetTypes.add("mbtiles");
        NATIVE_QUERY_PARAMS.datasetTypes.add("momap");
        NATIVE_QUERY_PARAMS.datasetTypes.add("gpkg");
        //}

        try {
            ImageryScanner.setDepth(prefs.getInt("imageryScannerDepth", 3));
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
        ScanLayersService.getInstance().registerScannerSpi(ImageryScanner.SPI);

        LayersNotificationManager.initialize(context);

        ServiceFactory.registerServiceQuery(wmsq = new WMSQuery());
        ServiceFactory.registerServiceQuery(wmtsq = new WMTSQuery());

        this.grgs.onCreate(context, intent, view);

        _mapView = view;
        _context = context;
        _layerSelections = new HashMap<>();

        buildUpLayers(context, view, prefs);

        prefs.registerOnSharedPreferenceChangeListener(this);
        prefs.registerOnSharedPreferenceChangeListener(
                controlPrefsChangedListener);

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        "Map Data Preferences",
                        "Adjust the Map Data Preferences",
                        "wmsPreferences",
                        context.getResources().getDrawable(
                                R.drawable.ic_menu_maps),
                        new WMSPreferenceFragment()));
    }

    private void buildUpLayers(Context context, MapView view,
            SharedPreferences prefs) {
        initLayersDatabase();

        // create the layers
        this.rasterLayers = new CardLayer("Raster Layers");
        this.nativeLayers = new NativeImageryRasterLayer2("Native", layersDb,
                NATIVE_QUERY_PARAMS, true);
        this.mobileLayers2 = new MobileImageryRasterLayer2("Mobile", layersDb,
                MOBILE_QUERY_PARAMS2, true);

        _contentResolvers
                .add(new LayerContentResolver(view, layersDb, rasterLayers));
        _contentResolvers.add(new KMZContentResolver(view));
        for (FileContentResolver resolver : _contentResolvers)
            URIContentManager.getInstance().registerResolver(resolver);

        // add the online/saved layers to the proxy layer
        this.rasterLayers.add(this.nativeLayers);
        this.rasterLayers.add(this.mobileLayers2);

        // flip to the saved layers by default
        this.rasterLayers.first();

        this.rasterLayers.addOnProxySubjectChangedListener(this);

        mobileOutlines = new MobileOutlinesDataStore(
                (MobileImageryRasterLayer2) this.mobileLayers2,
                0);
        this.mobileOutlinesLayer = new FeatureLayer("Mobile Layer Outlines",
                mobileOutlines);
        this.nativeOutlines = new OutlinesFeatureDataStore(this.nativeLayers,
                0, false);
        this.nativeOutlinesLayer = new FeatureLayer(
                "Native Layer Outlines", nativeOutlines);

        // create the layers adapters
        _nativeLayersAdapter = new NativeLayerSelectionAdapter(
                (NativeImageryRasterLayer2) this.nativeLayers,
                this.nativeOutlines,
                view,
                context);
        _mobileLayersAdapter2 = new MobileLayerSelectionAdapter(
                rasterLayers, (MobileImageryRasterLayer2) this.mobileLayers2,
                mobileOutlines,
                view,
                context);

        this.layerToAdapter = new HashMap<>();

        this.layerToAdapter.put(this.nativeLayers, _nativeLayersAdapter);
        this.layerToAdapter.put(this.mobileLayers2, _mobileLayersAdapter2);

        // add the layers group
        RasterDataStore.DatasetQueryParameters outlinesFilter = new RasterDataStore.DatasetQueryParameters();
        outlinesFilter.remoteLocalFlag = RasterDataStore.DatasetQueryParameters.RemoteLocalFlag.LOCAL;

        _mapView.addLayer(RenderStack.VECTOR_OVERLAYS, 0,
                nativeOutlinesLayer);
        _mapView.addLayer(RenderStack.VECTOR_OVERLAYS, 1,
                mobileOutlinesLayer);

        _zoomToLayerReceiver = new ZoomToLayerReceiver(view,
                _mobileLayersAdapter2);

        DocumentedIntentFilter zoomToLayerFilter = new DocumentedIntentFilter();
        zoomToLayerFilter
                .addAction("com.atakmap.android.maps.ZOOM_TO_LAYER");
        AtakBroadcast.getInstance().registerReceiver(_zoomToLayerReceiver,
                zoomToLayerFilter);

        _layersManagerReceiver = new LayersManagerBroadcastReceiver(
                view,
                this.rasterLayers,
                new LayersManagerBroadcastReceiver.AdapterSpec(
                        _nativeLayersAdapter,
                        LayersManagerBroadcastReceiver.AdapterSpec.IMAGERY_TYPE,
                        LayersManagerBroadcastReceiver.AdapterSpec.ZOOM_ZOOM,
                        true,
                        null),
                // XXX - outlines toggle button for mobile tab takes up a lot of
                //       real estate
                new LayersManagerBroadcastReceiver.AdapterSpec(
                        _mobileLayersAdapter2,
                        LayersManagerBroadcastReceiver.AdapterSpec.IMAGERY_TYPE,
                        LayersManagerBroadcastReceiver.AdapterSpec.ZOOM_ZOOM_CENTER,
                        false,
                        null));

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.maps.MANAGE_LAYERS");
        filter.addAction("com.atakmap.android.maps.ERROR_LOADING_LAYERS");
        filter.addAction(LayersManagerBroadcastReceiver.ACTION_GRG_DELETE);
        filter.addAction(LayersManagerBroadcastReceiver.ACTION_SELECT_LAYER);
        filter.addAction(
                LayersManagerBroadcastReceiver.ACTION_REFRESH_LAYER_MANAGER);
        filter.addAction(LayersManagerBroadcastReceiver.ACTION_ADD_FAV);
        filter.addAction(LayersManagerBroadcastReceiver.ACTION_VIEW_FAV);
        filter.addAction(LayersManagerBroadcastReceiver.ACTION_TOOL_FINISHED,
                "Region select tool finished");
        AtakBroadcast.getInstance().registerReceiver(_layersManagerReceiver,
                filter);

        ClearContentRegistry.getInstance()
                .registerListener(_layersManagerReceiver.dataMgmtReceiver);

        _volumeKeySwitcher = VolumeSwitchManager
                .getInstance(_mapView);
        _volumeKeySwitcher.setActiveAdapter(this.layerToAdapter
                .get(this.rasterLayers.get()));

        _mapView.addOnKeyListener(_volumeKeySwitcher);

        _mapView.addOnMapMovedListener(_mapMovedListener);

        mobileOutlines.setOutlineColor(Color.parseColor(prefs.getString(
                "pref_layer_outline_color", "#ff00ff00")));

        final boolean mobileOutlinesVisible = prefs.getBoolean(
                "prefs_layer_outlines_default_show", false);

        setFeaturesVisible(mobileOutlines, null, mobileOutlinesVisible);

        _volumeKeySwitcher.setEnabled(prefs.getBoolean("volumemapswitcher",
                true));

        _importers.add(new ExternalLayerDataImporter(context, layersDb,
                IMPORTER_CONTENT_TYPE, IMPORTER_MIME_TYPES, IMPORTER_HINTS));
        _importers.add(new KMZPackageImporter());
        for (Importer importer : _importers)
            ImporterManager.registerImporter(importer);
        // TODO: Marshal for external layers?

        _mapView.addLayer(RenderStack.MAP_LAYERS, 0, this.rasterLayers);

        // Start layers up when map is resized
        if (_mapView.getWidth() == 0) {
            _mapView.addOnMapViewResizedListener(
                    new AtakMapView.OnMapViewResizedListener() {
                        @Override
                        public void onMapViewResized(AtakMapView view) {
                            if (view.getWidth() != 0 && view.getHeight() != 0) {
                                _initializeLayers();
                                _mapView.removeOnMapViewResizedListener(this);
                            }
                        }
                    });
        } else {
            _initializeLayers();
        }

        this.mapControls = new MapControlsOverlay(_mapView);

        _importResolvers.add(new RemoteResourceImporter(view));
        for (ImportResolver resolver : _importResolvers)
            ImportExportMapComponent.getInstance().addImporterClass(resolver);
    }

    private void _initializeLayers() {

        this.onProxySubjectChanged(this.rasterLayers);

        // Scan for tilesets
        Log.d(TAG, "starting the layer scanner");
        Intent scanIntent = new Intent(
                ScanLayersService.START_SCAN_LAYER_ACTION);
        AtakBroadcast.getInstance().sendBroadcast(scanIntent);

        _contentHandler = new Handler();
        refreshRasterDataStores(true);

        // XXX - is content observer needed ???
        _contentObserver = new ContentObserver(_contentHandler) {
            @Override
            public void onChange(boolean selfChange) {
                Log.d(TAG, "Database changed, refershing tilesets");
                refreshRasterDataStores(false);
            }
        };

        try {
            _context.getContentResolver().registerContentObserver(
                    Uri.parse("content://com.atakmap.android.layers"),
                    false, _contentObserver);
        } catch (Exception ignored) {
            // added in by developer in 986.  Since then alot has changed
            // with the arch.  Do not revive if no bugs or breaks are filed.
        }

        _mapView.addOnMapMovedListener(_mapMovedListener);

        loadLayerState(
                PreferenceManager.getDefaultSharedPreferences(_context),
                "native",
                nativeLayers, nativeOutlines, true);
        loadLayerState(
                PreferenceManager.getDefaultSharedPreferences(_context),
                "mobile",
                mobileLayers2, mobileOutlines, true);

    }

    private void refreshRasterDataStores(final boolean selectLast) {
        //long s = SystemClock.elapsedRealtime();
        if (DeveloperOptions.getIntOption("force-layer-rebuild", 0) == 1)
            layersDb.clear();
        else
            layersDb.refresh();
        //long e = SystemClock.elapsedRealtime();

        //Log.d(TAG, "layers db refresh in " + (e - s) + "ms");

        if (selectLast)
            this.showLastViewedLayer();
    }

    private final AtakMapView.OnMapMovedListener _mapMovedListener = new AtakMapView.OnMapMovedListener() {
        @Override
        public void onMapMoved(AtakMapView v,
                boolean animate) {
            // TODO see if this is getting called when MapController.panTo() is called
            // if so, don't try and autoselect a map unless autozoom is enabled.
            /*
                        // if not locked, autoselect the best layer
                        if (_mobileLayersAdapter.isLocked()) {
                            final LayerSelection selection = _mobileLayersAdapter.getSelected();
                            if (selection == null || !selection.isInView(v, true))
                                _mobileLayersAdapter.setLocked(false);
                        }
            
                        ((Activity) _context).runOnUiThread(new Runnable() {
                            public void run() {
                                _mobileLayersAdapter.notifyDataSetChanged();
                            }
                        });
            */
        }
    };

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        for (FileContentResolver resolver : _contentResolvers) {
            URIContentManager.getInstance().unregisterResolver(resolver);
            resolver.dispose();
        }

        for (ImportResolver resolver : _importResolvers)
            ImportExportMapComponent.getInstance()
                    .removeImporterClass(resolver);

        for (Importer importer : _importers)
            ImporterManager.unregisterImporter(importer);

        this.mapControls.dispose();

        ClearContentRegistry.getInstance()
                .unregisterListener(_layersManagerReceiver.dataMgmtReceiver);

        _layersManagerReceiver.dispose();
        AtakBroadcast.getInstance().unregisterReceiver(_layersManagerReceiver);
        _layersManagerReceiver = null;

        AtakBroadcast.getInstance().unregisterReceiver(_zoomToLayerReceiver);
        _zoomToLayerReceiver = null;

        try {
            if (_contentObserver != null) {
                context.getContentResolver().unregisterContentObserver(
                        _contentObserver);
                _contentObserver = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "unable to unregister content resolver during cleanup");
        }

        if (wmsq != null)
            ServiceFactory.unregisterServiceQuery(wmsq);
        if (wmtsq != null)
            ServiceFactory.unregisterServiceQuery(wmtsq);

        _saveLastViewedLayer(context);

        _mapView.removeOnKeyListener(_volumeKeySwitcher);
        _volumeKeySwitcher = null;

        destroyMapViewLayers();

        PreferenceManager.getDefaultSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(
                        controlPrefsChangedListener);

        saveLayerState(
                PreferenceManager.getDefaultSharedPreferences(context),
                "native",
                this.nativeLayers, nativeOutlines);
        saveLayerState(
                PreferenceManager.getDefaultSharedPreferences(context),
                "mobile",
                this.mobileLayers2, mobileOutlines);

        if (this.nativeOutlines != null) {
            this.nativeOutlines.dispose();
            this.nativeOutlines = null;
        }
        if (this.mobileOutlines != null) {
            this.mobileOutlines.dispose();
            this.mobileOutlines = null;
        }

        cancelScanLayersService(250L);

        if (this.grgs != null)
            this.grgs.onDestroy(context, view);

        _mapView.removeOnMapMovedListener(_mapMovedListener);
    }

    private void destroyMapViewLayers() {
        _layerSelections.clear();
        _layerSelections = null;

        _nativeLayersAdapter.dispose();
        _nativeLayersAdapter = null;
        _mobileLayersAdapter2.dispose();
        _mobileLayersAdapter2 = null;

        if (this.nativeOutlinesLayer != null) {
            _mapView.removeLayer(RenderStack.VECTOR_OVERLAYS,
                    nativeOutlinesLayer);
            this.nativeOutlinesLayer = null;
        }
        if (this.mobileOutlinesLayer != null) {
            _mapView.removeLayer(RenderStack.VECTOR_OVERLAYS,
                    mobileOutlinesLayer);
            this.mobileOutlinesLayer = null;
        }
        if (this.rasterLayers != null) {
            _mapView.removeLayer(RenderStack.MAP_LAYERS, this.rasterLayers);
        }
    }

    private void showLastViewedLayer() {
        try {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(_context);

            final String active = prefs.getString(
                    "lastViewedLayer.active", null);
            final String layerName = prefs.getString("lastViewedLayer.name",
                    null);
            final boolean autoSelect = prefs.getBoolean(
                    "lastViewedLayer.autoselect", false);

            if (active == null)
                return;

            this.rasterLayers.show(active);
            final LayerSelectionAdapter lsa = this.layerToAdapter
                    .get(this.rasterLayers.get());

            if (lsa == null)
                return;

            if (layerName != null)
                lsa.setSelected(layerName);

            lsa.setLocked(!autoSelect);
        } catch (Exception ex) {
            Log.e(TAG, "error: ", ex);
        }
    }

    private void _saveLastViewedLayer(Context context) {
        try {
            if (rasterLayers != null) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(context);
                SharedPreferences.Editor e = prefs.edit();

                final String active = this.rasterLayers.get().getName();
                LayerSelectionAdapter lsa = this.layerToAdapter.get(
                        this.rasterLayers.get());

                if (lsa == null)
                    return;

                final LayerSelection ls = lsa.getSelected();

                if (ls == null)
                    e.putString("lastViewedLayer.name", null);
                else
                    e.putString("lastViewedLayer.name", ls.getName());
                e.putString("lastViewedLayer.active", active);
                e.putBoolean("lastViewedLayer.autoselect",
                        ((RasterLayer2) this.rasterLayers.get())
                                .isAutoSelect());
                e.apply();
            }
        } catch (Exception ex) {
            Log.e(TAG, "error: ", ex);
        }
    }

    @Override
    public void onStart(Context context, MapView view) {
        this.grgs.onStart(context, view);
    }

    @Override
    public void onStop(Context context, MapView view) {
        this.grgs.onStop(context, view);
    }

    @Override
    public void onPause(Context context, MapView view) {
        this.grgs.onPause(context, view);
    }

    @Override
    public void onResume(Context context, MapView view) {
        this.grgs.onResume(context, view);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("pref_layer_outline_color")) {
            mobileOutlines.setOutlineColor(Color
                    .parseColor(sharedPreferences.getString(
                            "pref_layer_outline_color", "#ff00ff00")));
        }
        if (key.equals("prefs_layer_outlines_default_show")) {
            Log.d("LayersMapComponent", "showOutlines changed");

            setFeaturesVisible(mobileOutlines, null, sharedPreferences
                    .getBoolean("prefs_layer_outlines_default_show", false));
        }
    }

    /**************************************************************************/
    // On Proxy Subject Changed Listener

    @Override
    public void onProxySubjectChanged(ProxyLayer layer) {
        final LayerSelectionAdapter active = this.layerToAdapter
                .get(this.rasterLayers.get());

        // the layer was toggled, update the active adapter for the volume key
        // switcher
        _volumeKeySwitcher.setActiveAdapter(active);
    }

    private void cancelScanLayersService(long mainThreadWaitMS) {
        // dispose of the data store
        synchronized (LayersMapComponent.class) {
            final RasterDataStore oldConn = layersDb;
            layersDb = null;

            if (ScanLayersService.getInstance().cancel(mainThreadWaitMS)) {
                // cancel the scan and dispose the datastore
                oldConn.dispose();
            } else {
                // XXX - not sure what the implications are of this continuing
                //       to spin after the activity is destroyed -- need to make
                //       use of InteractiveServiceProvider API to issue cancel
                //       the SPIs for orderly cancellation handling

                // spin off shutdown into a background thread to prevent the
                // app from getting hung on destroy
                Thread t = new Thread(TAG + "-Destroy") {
                    @Override
                    public void run() {
                        // force any scan to stop and join
                        ScanLayersService.getInstance().cancel(0L);

                        oldConn.dispose();
                    }
                };
                t.start();
            }
        }
    }

    /**************************************************************************/

    private static synchronized void initLayersDatabase() {
        // XXX - in case the static gets held onto between an onCreate/onDestroy
        //       cycle, create new instance if previously disposed
        if (layersDb == null || !layersDb.isAvailable())
            layersDb = new PersistentRasterDataStore(SAVED_DATABASE_FILE,
                    LAYERS_PRIVATE_DIR);
    }

    public static LocalRasterDataStore getLayersDatabase() {
        initLayersDatabase();
        return layersDb;
    }

    /**
     * Tests if the coverage of the selection is in view, not respecting minimum
     * or maximum resolution of the dataset.
     *  
     * @param view
     * @param ls
     * @return
     */
    static boolean isInView(MapView view, LayerSelection ls) {
        // quick check
        if (Rectangle.contains(ls.getWest(),
                ls.getSouth(),
                ls.getEast(),
                ls.getNorth(),
                view.getLongitude(),
                view.getLatitude())) {

            return true;
        }

        final GeoBounds bnds = view.getBounds();
        Envelope[] aois = new Envelope[2];
        aois[0] = new Envelope(bnds.getWest(), bnds.getSouth(), 0d,
                bnds.getEast(), bnds.getNorth(), 0d);

        // if any of the bounds are missing, attempt to reconsitute
        if (Double.isNaN(bnds.getNorth()) ||
                Double.isNaN(bnds.getWest()) ||
                Double.isNaN(bnds.getSouth()) ||
                Double.isNaN(bnds.getEast())) {

            // XXX - create a crude bounding box based on the focus point, the
            //       nominal resolution and the dimensions
            final double halfWidth = view.getWidth() / 2d;
            final double halfHeight = view.getHeight() / 2d;
            final double gsd = view.getMapResolution();
            final double metersPerDegreeLat = GeoCalculations
                    .approximateMetersPerDegreeLatitude(view.getLatitude());
            final double metersPerDegreeLng = GeoCalculations
                    .approximateMetersPerDegreeLongitude(view.getLatitude());
            aois[0].minX = view.getLongitude()
                    - ((gsd / metersPerDegreeLng) * halfWidth);
            aois[0].maxX = view.getLongitude()
                    + ((gsd / metersPerDegreeLng) * halfWidth);
            aois[0].minY = Math.max(view.getLatitude()
                    - ((gsd / metersPerDegreeLat) * halfHeight), -90d);
            aois[0].maxY = Math.min(view.getLatitude()
                    + ((gsd / metersPerDegreeLat) * halfHeight), 90d);

            final boolean eastWrap = (aois[0].maxX > 180d);
            final boolean westWrap = (aois[0].minX < -180d);
            if (eastWrap && westWrap) {
                aois[0].minX = -180d;
                aois[0].maxX = 180d;
            } else if (westWrap) {
                aois[1] = new Envelope(360d + aois[0].minX, aois[0].minY,
                        aois[0].minZ, 180d, aois[0].maxX, aois[0].maxZ);
            } else if (eastWrap) {
                aois[1] = new Envelope(-180d, aois[0].minY, aois[0].minZ,
                        aois[0].maxX - 360d, aois[0].maxX, aois[0].maxZ);
            }
        }
        // XXX - IDL crossing

        // the bounds are only an estimation, so this test may return false
        // negatives for non-planar projections (generally) or planar
        // projections when the view vector is not nadir
        return intersects(aois,
                ls.getBounds());
    }

    static boolean intersects(Envelope[] aois, Geometry geom) {
        Envelope mbb = geom.getEnvelope();
        boolean aoiIsect = false;
        for (Envelope aoi : aois) {
            if (aoi == null)
                continue;
            aoiIsect |= Rectangle.intersects(aoi.minX, aoi.minY,
                    aoi.maxX, aoi.maxY,
                    mbb.minX, mbb.minY,
                    mbb.maxX, mbb.maxY);
        }
        if (!aoiIsect)
            return false;

        if (geom instanceof GeometryCollection) {
            Collection<Geometry> children = ((GeometryCollection) geom)
                    .getGeometries();
            for (Geometry child : children)
                if (intersects(aois, child))
                    return true;
            return false;
        } else {
            // XXX - perform linestring/polygon intersection
            return true;
        }
    }

    public static void loadLayerState(SharedPreferences prefs,
            String id,
            RasterLayer2 layer,
            FeatureDataStore layerOutlines,
            boolean adoptParentVisibility) {

        final int count = prefs
                .getInt("num-" + id + "-type-coverage-colors", 0);

        if (layerOutlines != null) {
            String type;
            int color;
            boolean visible;
            Map<Long, Pair<BasicStrokeStyle, Boolean>> fids = new HashMap<>();
            FeatureCursor result;
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            Feature f;
            BasicStrokeStyle style;
            synchronized (layerOutlines) {
                for (int i = 0; i < count; i++) {
                    type = prefs.getString(
                            id + "-type-coverage-type" + i,
                            null);
                    color = prefs.getInt(
                            id + "-type-coverage-color" + i, 0);
                    visible = prefs.getBoolean(id + "-type-coverage-visible"
                            + i, false);

                    Log.d(TAG, id + "coverage load type=" + type
                            + " color=" + color + " visible=" + visible);

                    if (type == null)
                        continue;

                    params.featureNames = Collections.singleton(type);
                    fids.clear();
                    result = null;
                    try {
                        result = layerOutlines.queryFeatures(params);
                        while (result.moveToNext()) {
                            f = result.get();
                            if (f.getStyle() instanceof BasicStrokeStyle) {
                                style = (BasicStrokeStyle) f.getStyle();
                                if (adoptParentVisibility)
                                    visible |= layerOutlines.isFeatureVisible(f
                                            .getId());

                                fids.put(
                                        f.getId(),
                                        Pair
                                                .create(
                                                        new BasicStrokeStyle(
                                                                color,
                                                                style
                                                                        .getStrokeWidth()),
                                                        visible));
                            }
                        }
                    } finally {
                        if (result != null)
                            result.close();
                    }

                    for (Map.Entry<Long, Pair<BasicStrokeStyle, Boolean>> entry : fids
                            .entrySet()) {
                        try {
                            layerOutlines.updateFeature(entry.getKey(),
                                    entry.getValue().first);
                            layerOutlines.setFeatureVisible(
                                    entry.getKey(),
                                    entry.getValue().second);
                        } catch (Exception e) {
                            Log.e(TAG, "error occurred, feature not found", e);
                        }
                    }
                }
            }
        }

        // XXX - legacy
        if (prefs.getInt("num-" + id + "-type-visibility", -1) != -1) {
            final int numVisibilitySettings = prefs.getInt(
                    "num-" + id + "-type-visibility", 0);
            String type;
            boolean visible;
            for (int i = 0; i < numVisibilitySettings; i++) {
                type = prefs.getString(
                        id + "-visibility.type" + i, null);
                if (type == null)
                    continue;
                visible = prefs.getBoolean(
                        id + "-visibility.value" + i, true);
                layer.setVisible(type, visible);
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("num-" + id + "-type-visibility");
            for (int i = 0; i < numVisibilitySettings; i++) {
                editor.remove(
                        id + "-visibility.type" + i);
                editor.remove(
                        id + "-visibility.value" + i);
            }
            editor.apply();
        }

        RasterUtils.loadSelectionVisibility(layer, true, prefs);
    }

    public static void saveLayerState(SharedPreferences prefs,
            String id, RasterLayer2 layer, FeatureDataStore layerOutlines) {

        SharedPreferences.Editor editor = prefs.edit();

        if (layerOutlines != null) {
            Map<String, Pair<Integer, Boolean>> colors = new HashMap<>();
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            Feature f;
            FeatureCursor result = null;
            try {
                result = layerOutlines.queryFeatures(params);
                while (result.moveToNext()) {
                    f = result.get();
                    if (f.getStyle() instanceof BasicStrokeStyle) {
                        colors.put(f.getName(), Pair.create(
                                ((BasicStrokeStyle) f.getStyle()).getColor(),
                                layerOutlines.isFeatureVisible(f.getId())));
                    }
                }
            } finally {
                if (result != null)
                    result.close();
            }

            editor.putInt("num-" + id + "-type-coverage-colors", colors.size());
            int i = 0;
            for (Map.Entry<String, Pair<Integer, Boolean>> entry : colors
                    .entrySet()) {
                editor.putString(
                        id + "-type-coverage-type" + i,
                        entry.getKey());
                editor.putInt(id + "-type-coverage-color" + i,
                        entry.getValue().first);
                editor.putBoolean(
                        id + "-type-coverage-visible" + i,
                        entry.getValue().second);
                i++;
            }
        }

        RasterUtils.saveSelectionVisibility(layer, editor);
        editor.apply();
    }

    private static class DeleteDatasetLongClickListener implements
            OnItemLongClickListener {

        private final Context context;

        public DeleteDatasetLongClickListener(Context context) {
            this.context = context;
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent,
                View view,
                int position, long id) {
            final LayerSelection layerSelection = (LayerSelection) parent
                    .getItemAtPosition(position);
            final String layerName = layerSelection.getName();
            AlertDialog.Builder alt_bld = new AlertDialog.Builder(
                    context);
            alt_bld.setTitle("Remove Map")
                    .setMessage(
                            "Are you sure you want to delete '"
                                    + layerName
                                    + "' from this device? Clicking YES will delete this file.")
                    .setPositiveButton(R.string.yes,
                            new DialogInterface.OnClickListener() {

                                // remove the layer
                                @Override
                                public void onClick(
                                        DialogInterface dialog,
                                        int id) {
                                    File layer = null;
                                    // query for layer in database
                                    final LocalRasterDataStore dataStore = LayersMapComponent
                                            .getLayersDatabase();
                                    RasterDataStore.DatasetQueryParameters params = new RasterDataStore.DatasetQueryParameters();
                                    params.names = Collections
                                            .singleton(layerName);

                                    RasterDataStore.DatasetDescriptorCursor c = null;
                                    try {
                                        c = dataStore
                                                .queryDatasets(params);
                                        if (c.moveToNext()) {
                                            // remove the layer from the data store
                                            layer = dataStore
                                                    .getFile(c
                                                            .get());
                                            if (layer != null)
                                                dataStore
                                                        .remove(layer);

                                            // fall back, walk directorys; maybe not
                                            // needed
                                        }
                                    } finally {
                                        if (c != null)
                                            c.close();
                                    }

                                    // delete associated journal
                                    if (layerName
                                            .contains(".sqlite")
                                            && layer != null) {
                                        File layerJournal = new File(
                                                layer.getAbsolutePath()
                                                        + "-journal");
                                        if (IOProviderFactory
                                                .exists(layerJournal))
                                            FileSystemUtils
                                                    .delete(layerJournal);
                                    }

                                    // delete the physical file for the layer
                                    if (layer != null
                                            && IOProviderFactory.exists(layer))
                                        FileSystemUtils.delete(layer);

                                    // rescan for new layers
                                    Log.d(TAG,
                                            "rescanning for new layers created after caching operation");
                                    Intent scanIntent = new Intent(
                                            ScanLayersService.START_SCAN_LAYER_ACTION);
                                    scanIntent.putExtra("forceReset", true);
                                    AtakBroadcast.getInstance()
                                            .sendBroadcast(scanIntent);

                                }
                            })
                    .setNegativeButton(R.string.no, null);

            AlertDialog confirm = alt_bld.create();
            confirm.show();
            return true;
        }
    }

    static void setFeatureSetsVisible(FeatureDataStore dataStore,
            FeatureDataStore.FeatureSetQueryParameters params,
            boolean visible) {
        synchronized (dataStore) {
            LinkedList<Long> fsids = new LinkedList<>();

            FeatureDataStore.FeatureSetCursor result = null;
            try {
                result = dataStore.queryFeatureSets(params);
                while (result.moveToNext())
                    fsids.add(result.get().getId());
            } finally {
                if (result != null)
                    result.close();
            }

            for (Long fid : fsids)
                dataStore.setFeatureSetVisible(fid, visible);
        }
    }

    static void setFeaturesVisible(FeatureDataStore dataStore,
            FeatureDataStore.FeatureQueryParameters params, boolean visible) {
        synchronized (dataStore) {
            LinkedList<Long> fids = new LinkedList<>();

            FeatureCursor result = null;
            try {
                result = dataStore.queryFeatures(params);
                while (result.moveToNext())
                    fids.add(result.get().getId());
            } finally {
                if (result != null)
                    result.close();
            }

            for (Long fid : fids)
                dataStore.setFeatureVisible(fid, visible);
        }
    }
}
