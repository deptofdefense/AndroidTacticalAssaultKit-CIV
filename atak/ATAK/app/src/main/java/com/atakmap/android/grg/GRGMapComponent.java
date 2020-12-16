
package com.atakmap.android.grg;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.widget.Toast;

import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.ExternalLayerDataImporter;
import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.android.layers.OutlinesFeatureDataStore;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapView.RenderStack;
import com.atakmap.android.widgets.SeekBarControl;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.ogr.SchemaDefinitionRegistry;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.DatasetRasterLayer2;
import com.atakmap.map.layer.raster.LocalRasterDataStore;
import com.atakmap.map.layer.raster.PersistentRasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalMapLayer2;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import java.io.File;
import java.util.Collections;

/**
 * Provides for GRG layers to be handled on the screen.   This is not 
 * related to the Layer Manager which controls GRG visibility.
 */
public class GRGMapComponent extends AbstractMapComponent {

    private ExternalLayerDataImporter _externalGRGDataImporter;

    public static final String IMPORTER_CONTENT_TYPE = "External GRG Data";
    public static final String IMPORTER_DEFAULT_MIME_TYPE = "application/octet-stream";
    public static final String IMPORTER_TIFF_MIME_TYPE = "image/tiff";

    private final String[] _importerMimeTypes = new String[] {
            IMPORTER_DEFAULT_MIME_TYPE,
            IMPORTER_TIFF_MIME_TYPE,
            KmlFileSpatialDb.KMZ_FILE_MIME_TYPE,
            "image/nitf",
            "inode/directory"
    };
    private final String[] _importerHints = new String[] {
            null
    };

    private final static GLMapLayerSpi3 GL_MCIA_GRG_SPI = new GLMapLayerSpi3() {
        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;
            if (info.getDatasetType().equals("mcia-grg"))
                return new GLGdalMapLayer2(surface, info);
            return null;
        }

    };
    private final static File DATABASE_FILE = FileSystemUtils
            .getItem("Databases/GRGs2.sqlite");

    private LocalRasterDataStore grgDatabase;
    private GRGMapOverlay overlay;
    private GRGContentResolver contentResolver;
    private FeatureDataStore coverageDataStore;
    private FeatureLayer coveragesLayer;
    private DatasetRasterLayer2 rasterLayer;
    private MapView _mapView;
    private MCIAGRGMapOverlay mciagrgMapOverlay;
    private GRGDiscovery grgDiscovery;

    private MultiLayer grgLayer;

    @Override
    public void onCreate(Context context, Intent intent, final MapView view) {
        DatasetDescriptorFactory2.register(new MCIAGRGLayerInfoSpi());

        //GLMapItemFactory.registerSpi(GLImageOverlay.SPI);
        GLMapLayerFactory.registerSpi(GL_MCIA_GRG_SPI);

        SchemaDefinitionRegistry.register(MCIA_GRG.SECTIONS_SCHEMA_DEFN);
        SchemaDefinitionRegistry.register(MCIA_GRG.SUBSECTIONS_SCHEMA_DEFN);
        SchemaDefinitionRegistry.register(MCIA_GRG.BUILDINGS_SCHEMA_DEFN);

        this.grgDatabase = new PersistentRasterDataStore(DATABASE_FILE,
                LayersMapComponent.LAYERS_PRIVATE_DIR);

        _externalGRGDataImporter = new ExternalLayerDataImporter(context,
                this.grgDatabase,
                IMPORTER_CONTENT_TYPE, _importerMimeTypes, _importerHints);
        ImporterManager.registerImporter(_externalGRGDataImporter);

        _mapView = view;

        // TODO: Marshal for grg layers?

        // validate catalog contents
        this.grgDatabase.refresh();

        rasterLayer = new DatasetRasterLayer2("GRG rasters",
                this.grgDatabase, 0);

        coverageDataStore = new OutlinesFeatureDataStore(rasterLayer, 0, false);
        this.coveragesLayer = new FeatureLayer("GRG Outlines",
                coverageDataStore);

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        final boolean outlines = prefs
                .getBoolean("grgs.outlines-visible", true);
        this.coverageDataStore.setFeatureSetsVisible(null, outlines);

        LayersMapComponent.loadLayerState(
                prefs,
                "grgs",
                this.rasterLayer,
                this.coverageDataStore,
                false);

        // Used to map GRG files to associated metadata
        this.contentResolver = new GRGContentResolver(view, this.grgDatabase,
                this.rasterLayer, this.coverageDataStore);
        URIContentManager.getInstance().registerResolver(this.contentResolver);

        // Overlay Manager
        this.overlay = new GRGMapOverlay(view, rasterLayer, this.grgDatabase,
                this.coveragesLayer);

        this.grgLayer = new MultiLayer("GRG");
        this.grgLayer.addLayer(rasterLayer);
        this.grgLayer.addLayer(this.coveragesLayer);

        view.addLayer(MapView.RenderStack.RASTER_OVERLAYS, this.grgLayer);

        view.getMapOverlayManager().addOverlay(this.overlay);
        view.getMapOverlayManager().addOverlay(
                mciagrgMapOverlay = new MCIAGRGMapOverlay(context,
                        this.grgDatabase));

        startGrgDiscoveryThread(context);

        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction("com.atakmap.android.grg.OUTLINE_VISIBLE");
        this.registerReceiver(context, visibilityReceiver, intentFilter);

        ClearContentRegistry.getInstance().registerListener(dataMgmtReceiver);

        intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction("com.atakmap.android.grg.TRANSPARENCY");
        this.registerReceiver(context, transparencyReceiver, intentFilter);

        intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction("com.atakmap.android.grg.TOGGLE_VISIBILITY",
                "Toggle the visibility of the GRGs");
        this.registerReceiver(context, grgVisibilityReceiver, intentFilter);

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (this.rasterLayer != null) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);

            LayersMapComponent.saveLayerState(prefs,
                    "grgs",
                    this.rasterLayer,
                    this.coverageDataStore);

            SharedPreferences.Editor edit = prefs.edit();

            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.visibleOnly = true;
            params.limit = 1;

            edit.putBoolean("grgs.outlines-visible",
                    (this.coverageDataStore.queryFeaturesCount(params) > 0));
            edit.apply();
        }

        URIContentManager.getInstance().unregisterResolver(
                this.contentResolver);
        this.contentResolver.dispose();

        if (this.overlay != null) {
            view.getMapOverlayManager().removeOverlay(this.overlay);
            view.removeLayer(RenderStack.RASTER_OVERLAYS, this.grgLayer);

            this.overlay.dispose();
            this.overlay = null;
        }

        ClearContentRegistry.getInstance().unregisterListener(dataMgmtReceiver);
        if (visibilityReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(visibilityReceiver);
            visibilityReceiver = null;
        }

        this.grgDatabase.dispose();
    }

    // IO Abstraction

    private void startGrgDiscoveryThread(Context context) {

        // XXX - periodic discovery/refresh?

        Thread t = new Thread(
                grgDiscovery = new GRGDiscovery(context, this.grgDatabase));
        t.setName("GRG-discovery-thread");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ----

    public RasterLayer2 getLayer() {
        return this.rasterLayer;
    }

    public FeatureLayer getCoverageLayer() {
        return this.coveragesLayer;
    }

    private BroadcastReceiver visibilityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            boolean b = intent.getBooleanExtra("visible", true);
            Log.d(TAG, "setting visibility for grg call: " + b);
            coverageDataStore.setFeatureSetsVisible(null, b);
        }
    };

    private final BroadcastReceiver transparencyReceiver = new TransparencyReceiver();

    private final class TransparencyReceiver extends BroadcastReceiver
            implements MapEventDispatchListener {
        private boolean showing;

        @Override
        public void onReceive(final Context context, final Intent intent) {
            // find that GRG that was selected
            String uid = intent.getStringExtra("uid");
            if (uid == null) {
                Toast.makeText(_mapView.getContext(),
                        R.string.unable_adjust_transparency_grg,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            final MapItem item = overlay.getQueryFunction().deepFindItem(
                    Collections.singletonMap("uid", uid));
            if (item == null) {
                Toast.makeText(_mapView.getContext(),
                        R.string.unable_adjust_transparency_grg,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            final String selection = item.getMetaString("layerName", null);
            if (selection == null) {
                Toast.makeText(_mapView.getContext(),
                        R.string.unable_adjust_transparency_grg,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // show the seek bar control widget
            SeekBarControl.show(new SeekBarControl.Subject() {
                @Override
                public int getValue() {
                    return (int) (rasterLayer.getTransparency(selection) * 100);
                }

                @Override
                public void setValue(int value) {
                    rasterLayer.setTransparency(selection, value / 100f);
                }

                @Override
                public void onControlDismissed() {
                    // the seekbar is being dismissed for this GRG, remove our
                    // listener
                    _mapView.getMapEventDispatcher().removeMapEventListener(
                            MapEvent.MAP_CLICK, TransparencyReceiver.this);
                    showing = false;
                }
            }, 5000L);
            // add a click listener to dismiss the slider
            _mapView.getMapEventDispatcher().addMapEventListenerToBase(
                    MapEvent.MAP_CLICK, TransparencyReceiver.this);
            showing = true;
        }

        @Override
        public void onMapEvent(MapEvent event) {
            // the user clicked the map while the bar is showing, remove the
            // slider
            if (event.getType().equals(MapEvent.MAP_CLICK) && showing) {
                SeekBarControl.dismiss();
                showing = false;
            }
        }
    }

    // refactor all of these broadcast receivers into a single
    // broadcast receiver
    private final BroadcastReceiver grgVisibilityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String uid = intent.getExtras().getString("uid", null);
            if (FileSystemUtils.isEmpty(uid))
                return;

            MapItem item = _mapView.getRootGroup().deepFindUID(uid);
            if (!(item instanceof ImageOverlay))
                return;

            String layerName = item.getMetaString("layerName", null);
            if (FileSystemUtils.isEmpty(layerName))
                return;

            rasterLayer.setVisible(layerName, !rasterLayer.isVisible(
                    layerName));

            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    HierarchyListReceiver.REFRESH_HIERARCHY));
        }
    };

    private final ClearContentRegistry.ClearContentListener dataMgmtReceiver = new ClearContentRegistry.ClearContentListener() {
        @Override
        public void onClearContent(boolean clearmaps) {
            Log.d(TAG, "Deleting GRGs");
            //remove from database
            try {
                grgDatabase.clear();
            } catch (Exception e) {
                Log.d(TAG, "database error during clear");
            }

            //remove from UI
            MapGroup mapGroup = overlay.getRootGroup();
            if (mapGroup != null) {
                Log.d(TAG, "Clearing map group: " + mapGroup.getFriendlyName());
                mapGroup.clearGroups();
                mapGroup.clearItems();
            }

            //remove files
            final String[] mountPoints = FileSystemUtils.findMountPoints();
            for (String mountPoint : mountPoints) {
                File scanDir = new File(mountPoint, "grg");
                if (IOProviderFactory.exists(scanDir)
                        && IOProviderFactory.isDirectory(scanDir))
                    FileSystemUtils.deleteDirectory(scanDir, true);
            }

        }
    };
}
