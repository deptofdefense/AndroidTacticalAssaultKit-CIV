
package transapps.maps.plugin.lifecycle;

import android.app.Activity;
import android.content.res.Configuration;

import transapps.mapi.MapView;

public interface Lifecycle {

    /**
     * Called as part of the creation of the lifecycle.  Followed immediately by onStart.
     * @param activity The main ATAK activity.
     * @param mapView The transapp.mapi.MapView that contains the ATAK MapView.
     */
    void onCreate(Activity activity, MapView mapView);

    /**
     * Called after onCreate.
     */
    void onStart();

    /**
     * Called when the application is paused.
     */
    void onPause();

    /**
     * Called when the application is resumed.
     */
    void onResume();

    /**
     * Called right before onDestroy.
     */
    void onStop();

    /**
     * Called during lifecycle destruction.
     */
    void onDestroy();

    /**
     * Called if there is a configuration change.
     * @param c
     */
    void onConfigurationChanged(Configuration c);

    /**
     * Called when the destruction is finished.
     */
    void onFinish();

}
