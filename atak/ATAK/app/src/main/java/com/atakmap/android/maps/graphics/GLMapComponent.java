
package com.atakmap.android.maps.graphics;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import com.atakmap.app.R;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.FileIOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;

import java.io.File;

public class GLMapComponent extends AbstractMapComponent {

    private static final String TAG = "GLMapComponent";

    private MapView _mapView;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        initializeIconCacheDB(context);

        Log.v(TAG, "creating map render surface");

        _mapView = view;

        int bgColor = Color.BLACK;
        String bgColorString = intent.getStringExtra("bgColor");
        try {
            if (bgColorString != null) {
                bgColor = Color.parseColor(bgColorString);
            }
        } catch (Exception ex) {
            Log.w(TAG, "invalid bgColor: " + bgColorString);
        }

        // initialize the renderer state
        final int finalBgColor = bgColor;
        _mapView.getGLSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                GLMapView r = _mapView.getGLSurface().getGLMapView();
                r.setOnAnimationSettledCallback(_animCallback);
                _mapView.getGLSurface().getRenderer().setBgColor(finalBgColor);

                // prefetch a bunch of assets
                GLImageCache cache = GLRenderGlobals.get(r).getImageCache();
                /*
                 * cache.prefetch(new ResourceMapDataRef(R.drawable.nine_patch_large));
                 * cache.prefetch(new ResourceMapDataRef(R.drawable.nine_patch_med));
                 * cache.prefetch(new ResourceMapDataRef(R.drawable.nine_patch_small));
                 */
                cache.prefetch("resource://"
                        + R.drawable.nine_patch_large, false);
                cache.prefetch("resource://"
                        + R.drawable.nine_patch_med, false);
                cache.prefetch("resource://"
                        + R.drawable.nine_patch_small, false);
            }
        });
    }

    private final GLMapView.OnAnimationSettledCallback _animCallback = new GLMapView.OnAnimationSettledCallback() {
        @Override
        public void onAnimationSettled() {
            if (_mapView != null)
                _mapView.getMapEventDispatcher()
                        .dispatch(
                                new MapEvent.Builder(MapEvent.MAP_SETTLED)
                                        .build());
            else
                Log.w(TAG,
                        "onAnimationSettled callback received but MapView instance was null!");
        }
    };

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        // remove view observer
        _mapView = null;

    }

    @Override
    public void onStart(Context context, MapView view) {
        // _glSurface.getRenderer().setFrameRate(30.0f);
    }

    @Override
    public void onStop(Context context, MapView view) {
    }

    @Override
    public void onPause(Context context, MapView view) {
        view.pause();
    }

    @Override
    public void onResume(Context context, MapView view) {
        view.resume();
    }

    private static void initializeIconCacheDB(Context context) {
        final String assetPath = "dbs" + File.separatorChar
                + "iconcache.sqlite";
        final String outputPath = "Databases" + File.separatorChar
                + "iconcache.sqlite";
        final File output = FileSystemUtils.getItem(outputPath);
        if (FileIOProviderFactory.exists(output)) {
            // try to open the icon cache and check its version. if the version
            // does not match the version provided by the application, replace
            DatabaseIface db = null;
            try {
                db = Databases.openDatabase(
                        output.getAbsolutePath(),
                        true);
                if (db.getVersion() == GLBitmapLoader.ICON_CACHE_DB_VERSION)
                    return;
            } finally {
                if (db != null)
                    db.close();
            }
        }

        Log.d(TAG, "Deploying asset iconcache database");

        if (!FileSystemUtils.copyFromAssetsToStorageFile(context,
                assetPath, outputPath, true)) {
            Log.w(TAG, "Failed to extract iconcache database: " + outputPath);
        }
    }

}
