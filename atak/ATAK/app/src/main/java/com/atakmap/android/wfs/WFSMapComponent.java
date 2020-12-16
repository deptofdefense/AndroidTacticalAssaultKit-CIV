
package com.atakmap.android.wfs;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import android.util.Pair;

import com.atakmap.android.contentservices.ServiceFactory;
import com.atakmap.android.contentservices.ogc.WFSQuery;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.importexport.MarshalManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.opengl.GLBatchGeometryFeatureDataStoreRenderer;
import com.atakmap.map.layer.feature.wfs.WFSFeatureDataStore3;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Implements the WFS capability with the system.   Responsible for creating
 * a WFS layer and importing the installed configs and their corresponding 
 * WFS content
 */
public class WFSMapComponent extends AbstractMapComponent {
    private final static String TAG = "WFSMapComponent";

    private final static GLLayerSpi2 WFS_GLLAYER_SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // WFSFeatureDataStore3 : FeatureDataStore
            // FeatureLayer : Layer
            return 3;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (!(layer instanceof FeatureLayer))
                return null;
            FeatureDataStore dataStore = ((FeatureLayer) layer).getDataStore();
            if (dataStore instanceof WFSFeatureDataStore3)
                return new GLBatchGeometryFeatureDataStoreRenderer(surface,
                        (FeatureLayer) layer);
            return null;
        }
    };

    private MapView view;
    private WFSManager wfs;
    private WFSImporter importer;
    private WFSBroadcastReceiver receiver;

    @Override
    public void onCreate(final Context context, Intent compIntent,
            final MapView view) {

        this.view = view;

        ServiceFactory.registerServiceQuery(WFSQuery.INSTANCE);

        GLLayerFactory.register(WFS_GLLAYER_SPI2);

        this.wfs = new WFSManager(view);
        this.importer = new WFSImporter(this.wfs);

        MarshalManager.registerMarshal(WFSMarshal.INSTANCE);
        ImporterManager.registerImporter(this.importer);

        view.getMapOverlayManager().addOverlay(this.wfs);

        register();

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        unregister();

        if (this.wfs != null) {
            view.getMapOverlayManager().removeOverlay(this.wfs);
            this.wfs = null;
        }
    }

    private void register() {
        File wfsDir = FileSystemUtils.getItem("wfs");
        if (!IOProviderFactory.exists(wfsDir)) {
            if (!IOProviderFactory.mkdir(wfsDir)) {
                Log.e(TAG, "Error creating directories");
            }
        }
        this.receiver = new WFSBroadcastReceiver(view, wfsDir);
        ClearContentRegistry.getInstance().registerListener(this.receiver);

        // import the installed configs and their corresponding WFS content
        File[] configs = IOProviderFactory.listFiles(wfsDir,
                new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        return filename.endsWith(".xml");
                    }
                });
        if (configs != null) {
            for (File config : configs) {
                Intent intent = new Intent(
                        ImportExportMapComponent.ACTION_IMPORT_DATA);
                intent.putExtra(ImportReceiver.EXTRA_CONTENT,
                        WFSImporter.CONTENT);
                intent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                        WFSImporter.MIME_XML);
                intent.putExtra(ImportReceiver.EXTRA_URI,
                        config.getAbsolutePath());
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        }

    }

    private void unregister() {
        ClearContentRegistry.getInstance().unregisterListener(this.receiver);
    }

}
