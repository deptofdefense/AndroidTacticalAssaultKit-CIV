
package com.atakmap.android.maps.graphics;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.StatementIface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

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
        final String iconCachePath = "Databases" + File.separatorChar
                + "iconcache.sqlite";
        GLBitmapLoader.setIconCacheDb(FileSystemUtils.getItem(iconCachePath),
                new IconCacheSeeder(context));
    }

    private static class IconCacheSeeder
            implements GLBitmapLoader.IconCacheSeed {

        final Context context;

        IconCacheSeeder(Context context) {
            this.context = context;
        }

        /**
         * Load assets packaged in APK as asset file and adds to the icon cache database
         */
        @Override
        public void seed(DatabaseIface db) {
            Log.d(TAG, "Deploying asset iconcache database");

            final String assetPath = "dbs" + File.separatorChar
                    + "iconcache.sqlite";

            File tmpdir = null;
            try {
                tmpdir = FileSystemUtils.createTempDir("iconcache", "",
                        context.getCacheDir());
                final File seedDbPath = new File(tmpdir, "iconcache.sqlite");

                InputStream assetInputStream = FileSystemUtils
                        .getInputStreamFromAsset(context, assetPath);
                if (assetInputStream == null) {
                    Log.w(TAG, "Unable to obtain asset " + assetPath);
                    return;
                }
                // copy the APK asset using naked IO to the context cache directory
                FileSystemUtils.copyStream(
                        assetInputStream,
                        true,
                        new FileOutputStream(seedDbPath.getAbsolutePath()),
                        true);

                DatabaseIface assetsDb = null;
                try {
                    assetsDb = Databases.openDatabase(
                            seedDbPath.getAbsolutePath(),
                            true);
                    CursorIface cursor = null;

                    db.beginTransaction();
                    try {
                        cursor = assetsDb
                                .query("SELECT url, bitmap FROM cache;", null);
                        while (cursor.moveToNext()) {
                            String statement = "INSERT INTO cache (url, bitmap) VALUES (?,?);";
                            StatementIface insert = null;
                            try {
                                insert = db.compileStatement(statement);
                                insert.bind(1, cursor.getString(0));
                                insert.bind(2, cursor.getBlob(1));
                                insert.execute();
                            } finally {
                                if (insert != null)
                                    insert.close();
                            }
                        }

                        db.setTransactionSuccessful();
                    } catch (Exception e) {
                        Log.e(TAG, "Error during initial population. "
                                + e.getMessage());
                    } finally {
                        if (cursor != null)
                            cursor.close();
                        db.endTransaction();
                    }
                } finally {
                    assetsDb.close();
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to seed the iconcache DB", t);
            } finally {
                if (tmpdir != null)
                    IOProviderFactory.delete(tmpdir);
            }
        }
    }
}
