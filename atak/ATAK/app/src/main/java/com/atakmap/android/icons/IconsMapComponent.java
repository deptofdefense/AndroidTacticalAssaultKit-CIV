
package com.atakmap.android.icons;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;

import java.io.File;

/**
 * Manages all aspects of icons and specifically iconsets within the
 * system.   Some internal examples include the google and osm iconsets.
 */
public class IconsMapComponent extends AbstractMapComponent {

    public static final String TAG = "IconsMapComponent";

    private IconsMapAdapter _adapter;
    private IconManagerDropdown _iconsetDropdown;
    private MapView _mapView;
    private static IconsMapComponent _instance;

    public static synchronized IconsMapComponent getInstance() {
        return _instance;
    }

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _instance = this;
        _mapView = view;

        _adapter = new IconsMapAdapter(context);
        _iconsetDropdown = new IconManagerDropdown(view);

        // adapt existing markers
        MapGroup.deepMapItems(view.getRootGroup(),
                new MapGroup.OnItemCallback<Marker>(Marker.class) {
                    @Override
                    public boolean onMapItem(Marker item) {
                        _adapter.adaptMarkerIcon(item);
                        return false;
                    }
                });

        // listen for added markers and adapt their icons
        view.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_ADDED,
                _itemListener);
        view.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_REFRESH,
                _itemListener);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(IconsMapAdapter.ADD_ICONSET);
        filter.addAction(IconsMapAdapter.REMOVE_ICONSET);
        AtakBroadcast.getInstance().registerReceiver(_adapter, filter);

        filter = new DocumentedIntentFilter();
        filter.addAction(IconManagerDropdown.DISPLAY_DROPDOWN);
        filter.addAction(IconsMapAdapter.ICONSET_ADDED);
        filter.addAction(IconsMapAdapter.ICONSET_REMOVED);
        AtakBroadcast.getInstance().registerReceiver(_iconsetDropdown, filter);

        filter = new DocumentedIntentFilter();
        filter.addAction(IconsMapAdapter.ICONSET_ADDED);
        filter.addAction(IconsMapAdapter.ICONSET_REMOVED);
        filter.addAction(IconManagerView.DEFAULT_MAPPING_CHANGED);
        AtakBroadcast.getInstance().registerReceiver(_iconsetRefresh, filter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        view.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_ADDED, _itemListener);
        view.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REFRESH, _itemListener);

        if (_adapter != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_adapter);
            _adapter.dispose();
            _adapter = null;
        }

        if (_iconsetDropdown != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_iconsetDropdown);
            _iconsetDropdown.dispose();
            _iconsetDropdown = null;
        }

        if (_iconsetRefresh != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_iconsetRefresh);
        }

        synchronized (this) {
            if (urlIconCacheDatabase != null) {
                if (queryUrlIconBitmapStatement != null) {
                    queryUrlIconBitmapStatement.close();
                    queryUrlIconBitmapStatement = null;
                }
                urlIconCacheDatabase.close();
                urlIconCacheDatabase = null;
            }
        }

        _mapView = null;
    }

    private final MapEventDispatcher.MapEventDispatchListener _itemListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getItem() instanceof Marker) {
                _adapter.adaptMarkerIcon((Marker) event.getItem());
            }
        }
    };

    /**
     * Refresh relevant markers when an iconset is added or removed
     */
    private final BroadcastReceiver _iconsetRefresh = new BroadcastReceiver() {

        @Override
        public void onReceive(Context arg0, Intent intent) {
            new DefaultIconsetChangeTask(intent.getStringExtra("uid"))
                    .execute();
        }

        private void refresh(final String uid) {
            MapGroup.deepMapItems(_mapView.getRootGroup(),
                    new MapGroup.OnItemCallback<Marker>(Marker.class) {
                        @Override
                        public boolean onMapItem(Marker item) {
                            //if no UID, just revisit all markers
                            if (FileSystemUtils.isEmpty(uid)) {
                                ///Log.d(TAG, "Re-adapting " + item.getTitle());
                                _adapter.adaptMarkerIcon(item);
                                return false;
                            }

                            //if we do have a UID, only revisit relevant markers
                            String iconsetPath = item.getMetaString(
                                    UserIcon.IconsetPath, null);
                            if (FileSystemUtils.isEmpty(iconsetPath)) {
                                return false;
                            }

                            //check iconsetpath to see if relevant
                            if (iconsetPath.startsWith(uid)) {
                                //Log.d(TAG, "Re-adapting " + item.getTitle() + " for iconset: " + uid);
                                _adapter.adaptMarkerIcon(item);
                            }
                            return false;
                        }
                    });
        }

        /**
         * Simple background task refresh icons based on user preference
         * 
         * 
         */
        final class DefaultIconsetChangeTask extends
                AsyncTask<Void, Void, Void> {

            private static final String TAG = "DefaultIconsetChangeTask";

            private ProgressDialog _progressDialog;
            private final String _uid;

            public DefaultIconsetChangeTask(String uid) {
                _uid = uid;
            }

            @Override
            protected void onPreExecute() {
                _progressDialog = new ProgressDialog(_mapView.getContext());
                _progressDialog.setIcon(
                        com.atakmap.android.util.ATAKConstants.getIconId());
                _progressDialog.setTitle(_mapView.getContext().getString(
                        R.string.point_dropper_text31));
                _progressDialog.setMessage(_mapView.getContext().getString(
                        R.string.point_dropper_text32));
                _progressDialog.setIndeterminate(true);
                _progressDialog.setCancelable(false);
                _progressDialog.show();
            }

            @Override
            protected Void doInBackground(Void... params) {
                Thread.currentThread().setName(TAG);
                refresh(_uid);
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                Log.d(TAG, "Finished refreshing: " + _uid);

                if (_progressDialog != null) {
                    _progressDialog.dismiss();
                    _progressDialog = null;
                }
            }
        }
    };

    private DatabaseIface urlIconCacheDatabase;
    private QueryIface queryUrlIconBitmapStatement;

    /**
     * Retrieve an icon from the icon cache database
     * If the icon hasn't been loaded yet this will return null
     * @param url URL to the icon
     * @return Icon bitmap (null if failed to load)
     */
    public synchronized Bitmap getRemoteIcon(String url) {
        if (urlIconCacheDatabase == null) {
            final File cacheFile = GLBitmapLoader.getIconCacheDb();
            if (cacheFile != null) {

                try {
                    urlIconCacheDatabase = IOProviderFactory.createDatabase(
                            new DatabaseInformation(Uri.fromFile(cacheFile),
                                    DatabaseInformation.OPTION_READONLY));
                } catch (Exception e) {
                    Log.e(TAG, "error opening cacheFile: " + cacheFile + " for "
                            + url);
                }
            }
        }
        if (urlIconCacheDatabase == null) {
            Log.w(TAG, "Failed to open URL icon database.");
            return null;
        }
        if (queryUrlIconBitmapStatement == null)
            queryUrlIconBitmapStatement = urlIconCacheDatabase
                    .compileQuery(
                            "SELECT bitmap FROM cache WHERE url = ? LIMIT 1");
        try {
            queryUrlIconBitmapStatement.bind(1, url);
            if (queryUrlIconBitmapStatement.moveToNext()) {
                final byte[] blob = queryUrlIconBitmapStatement.getBlob(0);
                return BitmapFactory.decodeByteArray(blob, 0, blob.length);
            }
        } finally {
            queryUrlIconBitmapStatement.clearBindings();
        }
        return null;
    }
}
