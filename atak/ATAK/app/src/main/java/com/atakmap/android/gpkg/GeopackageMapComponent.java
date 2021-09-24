
package com.atakmap.android.gpkg;

import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.opengl.GLBatchGeometryFeatureDataStoreRenderer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

public class GeopackageMapComponent extends AbstractMapComponent {

    public static final String TAG = "GeopackageMapComponent";
    private GeoPackageImporter packageImporter;
    private MapView mapView;

    @Override
    public void onCreate(Context context,
            Intent intent,
            MapView view) {

        this.mapView = view;

        // register the layer renderer
        GLLayerFactory.register(new GLLayerSpi2() {
            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
                if (!(arg.second instanceof FeatureLayer3))
                    return null;
                FeatureLayer3 layer = (FeatureLayer3) arg.second;
                if (!layer.getDataStore().getUri().endsWith(".gpkg"))
                    return null;
                return new GLBatchGeometryFeatureDataStoreRenderer(arg.first,
                        layer);
            }

            @Override
            public int getPriority() {
                return 3;
            }
        });

        // importer framework registration
        ImportExportMapComponent.getInstance()
                .addImporterClass(GeoPackageImporter.getImportResolver());
        initDb();
    }

    @Override
    protected void onDestroyImpl(Context context,
            MapView view) {
        if (packageImporter != null) {
            ImporterManager.unregisterImporter(packageImporter);
            packageImporter.dispose();
            packageImporter = null;
        }
    }

    @Override
    public void onStart(Context context,
            MapView view) {
    }

    @Override
    public void onStop(Context context,
            MapView view) {
    }

    @Override
    public void onPause(Context context,
            MapView view) {
    }

    @Override
    public void onResume(Context context,
            MapView view) {
    }

    private void initDb() {
        // flush any stored content/state
        if (packageImporter != null) {
            ImporterManager.unregisterImporter(packageImporter);
            packageImporter.dispose();
            packageImporter = null;
        }

        //
        // Load prior imports and any available overlays.  Register for imports.
        //
        packageImporter = new GeoPackageImporter(this.mapView, "Geopackage",
                "resource://" + R.drawable.gpkg);
        packageImporter.loadImports();
        packageImporter.loadOverlays(FileSystemUtils.getItems("overlays"));
        ImporterManager.registerImporter(packageImporter);
    }

}
