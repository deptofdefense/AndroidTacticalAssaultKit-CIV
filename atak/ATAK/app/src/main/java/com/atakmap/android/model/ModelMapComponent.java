
package com.atakmap.android.model;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.widget.BaseAdapter;

import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.features.FeatureDataStoreDeepMapItemQuery;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
// XXX - post 3.10
//import com.atakmap.android.image.GalleryItemFactory;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.importexport.MarshalManager;
import com.atakmap.android.importfiles.sort.ImportInPlaceResolver;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.model.hierarchy.ModelsHierarchyListItem;
import com.atakmap.android.model.opengl.GLModelLayer;
import com.atakmap.android.model.opengl.MemoryMappedModel;
import com.atakmap.android.model.viewer.DetailedModelViewerDropdownReceiver;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.RendererRefreshControl;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.datastore.FeatureSetDatabase2;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.model.ModelFactory;
import com.atakmap.map.layer.model.ModelInfoFactory;
import com.atakmap.map.layer.model.assimp.AssimpModelSpi;
import com.atakmap.map.layer.model.contextcapture.ContextCaptureModelInfoSpi;
import com.atakmap.map.layer.model.contextcapture.GLContextCaptureScene;
import com.atakmap.map.layer.model.dae.DaeModelInfoSpi;
import com.atakmap.map.layer.model.obj.ObjModelInfoSpi;
import com.atakmap.map.layer.model.obj.ObjModelSpi;
import com.atakmap.map.layer.model.opengl.GLSceneFactory;
import com.atakmap.map.layer.model.pix4d.Pix4dGeoreferencer;
import com.atakmap.map.layer.model.pointcloud.LocalToGpsJsonGeoreferencer;
import com.atakmap.map.layer.model.pointcloud.PlyModelInfoSpi;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.SortedSet;
import java.util.TreeSet;

import android.content.BroadcastReceiver;
import com.atakmap.android.data.DataMgmtReceiver;

import com.atakmap.coremap.log.Log;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.Visitor;

import jassimp.AiBufferAllocator;
import jassimp.Jassimp;

public class ModelMapComponent extends AbstractMapComponent {

    public static final String ACTION_SCENE_RENDERER_REFRESH = "com.atakmap.android.model.SCENE_RENDERER_REFRESH";

    public static final String TAG = "ModelMapComponent";

    public static final String NAME = "3D Models";

    private FeatureLayer3 modelLayer;
    private FeatureDataStore2 modelDataStore;
    MapView view;
    ModelDetailsDropdownReceiver detailsReceiver;
    private ModelContentResolver contentResolver;
    private ModelImporter importer;
    private OverlayImpl overlay;

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        view.getMapOverlayManager().removeOverlay(this.overlay);
        URIContentManager.getInstance().unregisterResolver(
                this.contentResolver);
        this.contentResolver.dispose();

        view.removeLayer(MapView.RenderStack.VECTOR_OVERLAYS, modelLayer);
        modelDataStore.dispose();

        if (dataMgmtReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(dataMgmtReceiver);
            dataMgmtReceiver = null;
        }
    }

    @Override
    public void onCreate(final Context context, Intent intent, MapView view) {
        this.view = view;

        ConfigOptions.setOption("TAK.Engine.Model.default-icon",
                "resource://" + R.drawable.icon_3d_map);
        Jassimp.setBufferAllocator(new AiBufferAllocator() {
            @Override
            public ByteBuffer allocate(int i) {
                return Unsafe.allocateDirect(i);
            }
        });

        ModelInfoFactory.registerGeoreferencer(Pix4dGeoreferencer.INSTANCE);
        ModelInfoFactory
                .registerGeoreferencer(LocalToGpsJsonGeoreferencer.INSTANCE);
        ModelInfoFactory.registerSpi(ObjModelInfoSpi.INSTANCE);
        ModelInfoFactory.registerSpi(DaeModelInfoSpi.INSTANCE);
        ModelInfoFactory.registerSpi(PlyModelInfoSpi.INSTANCE);
        ModelInfoFactory.registerSpi(ContextCaptureModelInfoSpi.INSTANCE);

        ModelFactory.registerSpi(AssimpModelSpi.INSTANCE);
        ModelFactory.registerSpi(ObjModelSpi.INSTANCE);
        ModelFactory.registerSpi(MemoryMappedModel.SPI);

        GLSceneFactory.registerSpi(GLContextCaptureScene.SPI);

        // XXX - post 3.10
        //GalleryItemFactory.registerSpi(ModelGalleryItem.SPI);
        File dbFile = FileSystemUtils
                .getItem("Databases/models.db/catalog.sqlite");
        if (!dbFile.getParentFile().exists()) {
            if (!dbFile.getParentFile().mkdirs()) {
                Log.d(TAG, "could not make the directory: "
                        + dbFile.getParentFile());
            }
        }
        modelDataStore = new FeatureSetDatabase2(dbFile);
        modelLayer = new FeatureLayer3(NAME, modelDataStore);

        // Used to map model files to associated metadata
        this.contentResolver = new ModelContentResolver(view, modelDataStore);
        URIContentManager.getInstance().registerResolver(this.contentResolver);

        GLLayerFactory.register(new GLLayerSpi2() {
            @Override
            public int getPriority() {
                return 2;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> object) {
                if (object.second != modelLayer)
                    return null;

                return new GLModelLayer(object.first,
                        (FeatureLayer3) object.second);
            }
        });

        view.addLayer(MapView.RenderStack.VECTOR_OVERLAYS, modelLayer);

        this.overlay = new OverlayImpl();
        view.getMapOverlayManager().addOverlay(this.overlay);

        // details dropdown
        detailsReceiver = new ModelDetailsDropdownReceiver(view,
                modelDataStore);
        registerReceiver(context, detailsReceiver,
                new AtakBroadcast.DocumentedIntentFilter(
                        ModelDetailsDropdownReceiver.SHOW_DETAILS));
        registerReceiver(context, detailsReceiver,
                new AtakBroadcast.DocumentedIntentFilter(
                        ModelDetailsDropdownReceiver.TOGGLE_VISIBILITY));

        // detailed model viewer
        DetailedModelViewerDropdownReceiver ddr = new DetailedModelViewerDropdownReceiver(
                view, context);

        AtakBroadcast.DocumentedIntentFilter ddFilter = new AtakBroadcast.DocumentedIntentFilter();
        ddFilter.addAction(DetailedModelViewerDropdownReceiver.SHOW_3D_VIEW,
                "Show the 3D Model");
        registerReceiver(context, ddr, ddFilter);

        // importer
        importer = new ModelImporter(context, modelDataStore, contentResolver);
        ImporterManager.registerImporter(importer);
        MarshalManager.registerMarshal(ModelMarshal.INSTANCE);
        ImportExportMapComponent.getInstance().addImporterClass(
                ImportInPlaceResolver.fromMarshal(ModelMarshal.INSTANCE));
        ImportFilesTask.registerExtension(".obj");
        ImportFilesTask.registerExtension(".zip");

        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(DataMgmtReceiver.ZEROIZE_CONFIRMED_ACTION);
        this.registerReceiver(context, dataMgmtReceiver, intentFilter);

        DocumentedIntentFilter refreshRendererFilter = new DocumentedIntentFilter();
        refreshRendererFilter.addAction(ACTION_SCENE_RENDERER_REFRESH,
                "Signal to refresh the renderers used for scene rendering. Broadcast after modifications are made to GLSceneFactory.");
        this.registerReceiver(context, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ModelMapComponent.this.view.getGLSurface().getGLMapView()
                        .visitControl(modelLayer,
                                new Visitor<RendererRefreshControl>() {
                                    @Override
                                    public void visit(
                                            RendererRefreshControl object) {
                                        object.requestRefresh();
                                    }
                                }, RendererRefreshControl.class);
            }
        }, refreshRendererFilter);

        this.scanForModels();
    }

    private void scanForModels() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                // first clean out any invalid models
                try {
                    FeatureSetCursor result = null;
                    try {
                        result = modelDataStore.queryFeatureSets(null);
                        while (result.moveToNext()) {
                            FeatureSet fs = result.get();
                            File f = new File(fs.getName());
                            if (!f.exists())
                                modelDataStore.deleteFeatureSet(fs.getId());
                            else
                                contentResolver.addModelHandler(fs);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to resolve models", e);
                    } finally {
                        if (result != null)
                            result.close();
                    }
                } catch (Throwable ignored) {

                }
                // next load all new models
                File[] dirs = FileSystemUtils.getItems("overlays");
                if (dirs != null) {
                    for (File d : dirs) {
                        loadFiles(d);
                    }
                }
            }

            private void loadFiles(File dir) {
                File[] listing = dir.listFiles();
                if (listing == null)
                    return;
                Bundle b = new Bundle();
                b.putBoolean("ignoreExisting", true);
                for (final File f : listing) {
                    if (f.isDirectory())
                        loadFiles(f);
                    else
                        try {
                            importer.importData(Uri.fromFile(f), null, b);
                        } catch (IOException e) {
                            Log.e(TAG, "error occured", e);
                        }
                }
            }
        }, TAG + "-ScanForModels");
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    @SuppressLint("ResourceType")
    static String getPointIconUri(Context ctx) {
        File f = new File(ctx.getCacheDir(), "icon_3d_map.png");
        if (!f.exists()) {
            try {
                FileSystemUtils.copyStream(
                        ctx.getResources()
                                .openRawResource(R.drawable.icon_3d_map),
                        true, new FileOutputStream(f), true);
            } catch (Throwable t) {
                return "resource://" + R.drawable.icon_3d_map;
            }
        }
        return "file://" + f.getAbsolutePath();
    }

    private class OverlayImpl extends AbstractMapOverlay2 {

        final MapGroup mapGroup = new DefaultMapGroup();
        final DeepMapItemQuery queryFunction = new FeatureDataStoreDeepMapItemQuery(
                modelLayer,
                modelDataStore) {
            @Override
            protected MapItem featureToMapItem(Feature f) {
                if (f.getGeometry() == null)
                    f = new Feature(f.getFeatureSetId(),
                            f.getId(),
                            f.getName(),
                            new Point(0d, 0d),
                            f.getStyle(),
                            f.getAttributes(),
                            f.getTimestamp(),
                            f.getVersion());

                // XXX - post 3.10 -- install custom menu and mark models as attachments
                MapItem retval = super.featureToMapItem(f);
                //if(retval != null)
                //    retval.setMetaString("menu", "menus/model_menu.xml");
                //ModelInfo info = GLModelLayer.getModelInfo(f);
                //if(info != null)
                //    retval.setMetaStringArrayList("attachments", new ArrayList<String>(Collections.singleton(info.uri)));
                return retval;
            }

            @Override
            protected SortedSet<MapItem> deepHitTestItemsImpl(int xpos,
                    int ypos, GeoPoint point, MapView view, int limit) {
                return new TreeSet<>(MapItem.ZORDER_HITTEST_COMPARATOR);
            }
        };

        @Override
        public String getIdentifier() {
            return NAME;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public MapGroup getRootGroup() {
            return mapGroup;
        }

        @Override
        public DeepMapItemQuery getQueryFunction() {
            return queryFunction;
        }

        @Override
        public HierarchyListItem getListModel(BaseAdapter adapter,
                long capabilities, HierarchyListFilter filter) {
            return new ModelsHierarchyListItem(view, modelLayer, adapter);
        }
    }

    private BroadcastReceiver dataMgmtReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.d(TAG, "Deleting models");
            try {
                modelDataStore.clearCache();
            } catch (Exception e) {
                Log.d(TAG, "error during clear cache");
            }

            final File scanDir = FileSystemUtils.getItem("Databases/models.db");
            if (scanDir.exists() && scanDir.isDirectory())
                FileSystemUtils.deleteDirectory(scanDir, true);
        }
    };
}
