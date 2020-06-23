package transapps.maps.plugin.lifecycle;

import transapps.mapi.MapView;
import android.app.Activity;
import android.content.res.Configuration;

/**
 * An extension point that allows you to respond to maps lifecycle events.
 * You should not get any calls in any other extensions before you get a
 * call to {@link Lifecycle#onCreate(Activity, MapView)}
 * 
 * @author mriley
 */
public interface Lifecycle {

    /**
     * Called at the end of onCreate in maps, but before any other calls to
     * any other extensions are made
     * 
     * @param ctx
     * @param mapView
     */
    void onCreate(Activity activity, MapView mapView);
    
    /**
     * Called at the end of onStart in Maps.
     */
    void onStart();
    
    /**
     * Called at the end of onPause in Maps.
     */
    void onPause();
    
    /**
     * Called at the end of onDestroy in Maps.
     */
    void onDestroy();
    
    /**
     * Called at the end of onResume in Maps.
     */
    void onResume();
    
    /**
     * Called at the end of {@link Activity#finish()}
     */
    void onFinish();

    /**
     * Called at the end of {@link Activity#finish()}
     */
    void onStop();

    /**
     * Called at the end of {@link Activity#onConfigurationChanged(Configuration)}
     */
    void onConfigurationChanged( Configuration newConfig );
}
