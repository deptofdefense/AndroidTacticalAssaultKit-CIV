
package com.atakmap.android.grg;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.URIContentManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.layers.ExternalLayerDataImporter;
import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.android.layers.OutlinesFeatureDataStore;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapView.RenderStack;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.ogr.SchemaDefinitionRegistry;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.DatasetRasterLayer2;
import com.atakmap.map.layer.raster.PersistentRasterDataStore;
import com.atakmap.map.layer.raster.RasterLayer2;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalMapLayer2;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import java.io.File;

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

    private PersistentRasterDataStore grgDatabase;
    private GRGMapOverlay overlay;
    private GRGContentResolver contentResolver;
    private FeatureDataStore coverageDataStore;
    private FeatureLayer3 coveragesLayer;
    private DatasetRasterLayer2 rasterLayer;
    private MCIAGRGMapOverlay mciagrgMapOverlay;
    private GRGMapReceiver mapReceiver;

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

        // TODO: Marshal for grg layers?

        // validate catalog contents
        this.grgDatabase.refresh();

        rasterLayer = new DatasetRasterLayer2("GRG rasters",
                this.grgDatabase, 0);

        coverageDataStore = new OutlinesFeatureDataStore(rasterLayer, 0, false);
        this.coveragesLayer = new FeatureLayer3("GRG Outlines",
                Adapters.adapt(coverageDataStore));

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

        this.mapReceiver = new GRGMapReceiver(view, coverageDataStore,
                grgDatabase, rasterLayer, overlay);
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
            view.getMapOverlayManager().removeOverlay(this.mciagrgMapOverlay);
            view.removeLayer(RenderStack.RASTER_OVERLAYS, this.grgLayer);

            this.overlay.dispose();
            this.overlay = null;
        }

        if (this.mapReceiver != null)
            this.mapReceiver.dispose();

        this.grgDatabase.dispose();
    }

    // IO Abstraction

    private void startGrgDiscoveryThread(Context context) {

        // XXX - periodic discovery/refresh?

        Thread t = new Thread(new GRGDiscovery(context, this.grgDatabase));
        t.setName("GRG-discovery-thread");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ----

    public RasterLayer2 getLayer() {
        return this.rasterLayer;
    }

    public FeatureLayer3 getCoverageLayer() {
        return this.coveragesLayer;
    }
}
