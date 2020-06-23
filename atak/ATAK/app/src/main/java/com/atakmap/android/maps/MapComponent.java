
package com.atakmap.android.maps;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Engine feature-set or major component
 * 
 * 
 */
public interface MapComponent {

    /**
     * Called when the component is first created
     * @link android.app.Activity#onCreate(Bundle)
     *
     * @param context the context of the component (the MapActivity instance)
     * @param intent the intent of the MapActivity
     * @param view the main MapView
     */
    void onCreate(Context context, Intent intent, MapView view);

    /**
     * Called when the component must be destroyed
     * @link android.app.Activity#onDestroy()
     * @param context the context of the component (the MapActivity instance)
     * @param view the main MapView
     */
    void onDestroy(Context context, MapView view);

    /**
     * Implementation for an abstract map component which corresponds to the onStart
     * state transition.
     * @link android.app.Activity#onStart()
     * @param context the context of the component (the MapActivity instance)
     * @param view the main MapView
     */
    void onStart(Context context, MapView view);

    /**
     * Implementation for an abstract map component which corresponds to the onStop
     * state transition.
     * @link android.app.Activity#onStop()
     * @param context the context of the component (the MapActivity instance)
     * @param view the main MapView
     */
    void onStop(Context context, MapView view);

    /**
     * Implementation for an abstract map component which corresponds to the onPause
     * state transition.
     * @link android.app.Activity#onPause()
     * @param context the context of the component (the MapActivity instance)
     * @param view the main MapView
     */
    void onPause(Context context, MapView view);

    /**
     * Implementation for an abstract map component which corresponds to the onResume
     * state transition.
     * @link android.app.Activity#onResume()
     * @param context the context of the component (the MapActivity instance)
     * @param view the main MapView
     */
    void onResume(Context context, MapView view);

    /**
     * Implementation of onConfigurationChanged used to handle configuration changes for each
     * mapComponent.
     * @see android.app.Activity#onConfigurationChanged(Configuration)
     * @param newConfiguration called when the configuration changed
     */
    void onConfigurationChanged(Configuration newConfiguration);

    /**
     * Is called on all Map Components when the onCreateOptionsMenu is called on the Map Activity.
     * @see android.app.Activity#onCreateOptionsMenu(Menu)
     *
     * @param context the context of the component (the MapActivity instance)
     * @param menu The options menu in which you place your items.
     * @return true if there is something to show
     */
    boolean onCreateOptionsMenu(Context context, Menu menu);

    /**
     * Called to Update the Options Menu on all MapComponents in the MapActivity.
     *
     * @param context the context of the component (the MapActivity instance)
     * @param menu The options menu in which you place your items.
     * @return true if there is something to show
     */
    boolean onPrepareOptionsMenu(Context context, Menu menu);

    /**
     * Called on a onOptionsItemSelected is called and not found in regular activity.
     * @see android.app.Activity#onOptionsItemSelected(MenuItem)
     *
     * @param context the context of the component (the MapActivity instance)
     * @param item The menu item that was selected.
     * @return true if mapcomponent consumes/uses the activity
     */
    boolean onOptionsItemSelected(Context context, MenuItem item);

    /**
     * Option menu has been closed in MapActivity.
     * @see android.app.Activity#onOptionsMenuClosed(Menu)
     *
     * @param context the context of the component (the MapActivity instance)
     * @param menu The options menu in which you place your items.
     */
    void onOptionsMenuClosed(Context context, Menu menu);
}
