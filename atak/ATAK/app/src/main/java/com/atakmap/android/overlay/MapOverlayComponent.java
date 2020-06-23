
package com.atakmap.android.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.Menu;
import android.view.MenuItem;

import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;

public class MapOverlayComponent implements MapComponent {

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

    }

    @Override
    public void onDestroy(Context context, MapView view) {
    }

    @Override
    public void onStart(Context context, MapView view) {
    }

    @Override
    public void onStop(Context context, MapView view) {
    }

    @Override
    public void onPause(Context context, MapView view) {
    }

    @Override
    public void onResume(Context context, MapView view) {
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public boolean onCreateOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(Context context, MenuItem item) {
        return false;
    }

    @Override
    public void onOptionsMenuClosed(Context context, Menu menu) {
    }
}
