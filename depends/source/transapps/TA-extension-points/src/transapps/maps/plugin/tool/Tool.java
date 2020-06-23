package transapps.maps.plugin.tool;


import transapps.mapi.MapView;
import transapps.maps.plugin.tool.ActivationRule.Result;
import transapps.maps.plugin.tool.ActivationRule.Type;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;


/**
 * The thing that owns the callbacks on the map.  This
 * should be used for things like drawing, bearing, creating
 * spots, creating tigr content, etc.
 * 
 * @author mriley
 */
public abstract class Tool {

    private boolean autoClose = true;
    private ActivationRule activationRule;
    
    /**
     * Invoked from the tool
     * 
     * @author mriley
     */
    public static interface ToolCallback {
        /**
         * Called when a tool has completed deactivation
         * @param tool
         */
        void onToolDeactivated(Tool tool);

        /**
         * Called when a tool requests an invalidation
         */
        void onInvalidate(Tool tool);
    }
    
    /**
     * Helps the tool manager determine what to do when another
     * tool wants to become active when this tool is active.
     * 
     * @return an exclusion that will determine the appropriate
     * exclusion action
     */
    public ActivationRule getActivationRule() {
        if( activationRule == null ) {
            activationRule = createActivationRule();
        }
        return activationRule;
    }

    /**
     * Override to change the exclusion action
     * 
     * @return
     */
    protected ActivationRule createActivationRule() {
        return new ActivationRule.TypeExclusion(Type.FULL_SCREEN, Result.ACTIVATE_CLOSE_ACTIVE);
    }
    
    /**
     * Activate this tool.  Here you can start doing whatever you 
     * like.  You may add overlays to the map, override other map
     * view stuff.  Just make sure that whatever you do is UNdone
     * when {@link Tool#onDeactivate(ToolCallback)} is 
     * called
     * 
     * @param activity the main activity.
     * @param view the mapView
     * @param extras extra data.  This will be different depending
     *     one the tool {@link Group}
     * @param callback invoke {@link ToolCallback#onToolDeactivated(Tool)} to deactivate
     */
    public abstract void onActivate(Activity activity, MapView mapView, ViewGroup viewRoot, Bundle extras, ToolCallback callback);
    
    /**
     * Deactivate this tool, potentially prompting for
     * something like save.  You should invoke the callback
     * when you are done deactivating.  This allows the map
     * to wait until you are done to continue on or start
     * another tool
     * 
     * @param callback invoke {@link ToolCallback#onToolDeactivated(Tool)} when you're done, please!
     */
    public abstract void onDeactivate(ToolCallback callback);
    
    /**
     * This can tell maps if this tool supports editing the content
     * described by the given intent.  This allows other apps to send
     * content to maps and for a given tool to handle editing that
     * content
     * 
     * @param intent
     * @return true if this can edit
     */
    public boolean isToolFor( Intent intent ) {
        return false;
    }        
    
    /**
     * Handle any sort of key events...*cough*BACK_BUTTON*cough* 
     * *cough*MENU*cough*
     * 
     * @param keyCode
     * @param event
     * @return true of you handled it
     */
    public boolean onKeyDown( int keyCode, KeyEvent event ) {
        return false;
    }
    
    /**
     * If you asked the activity to start another activity for result, this
     * will be called.  <b>YO...MAKE SURE TO CHECK requestCode and resultCode as
     * I will determine if I should call you by, well, calling you</b>.  If its
     * your result you need to return true
     * 
     * @param requestCode the requestCode you passed when you called 
     *     {@link Activity#startActivityForResult(Intent, int)}
     * @param resultCode The result code returned from your activity
     * @param data
     * @return true if this is your result and you handled it
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }
    
    /**
     * Handles device configuration changes
     * 
     * @param newConfig The new device configuration
     */
    public void onConfigurationChanged(Configuration newConfig) {
        // by default, do nothing 
    }
    
    /**
     * let's the tool save it's instance state.
     * this method will be called when the configuration changes
     * @param savedInstanceState
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        //by default do nothing
    }
    
    /**
     * let's the tool restore the instance state.  
     * @param savedInstanceState
     */
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        //by default do nothing
    }
    


    /**
     * Set to true if you want your tool to auto-close when the back-button is uncaught.
     * true by default
     */
    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    /**
     * true if you want your tool to auto-close when the back-button is uncaught.
     * true by default
     */
    public boolean isAutoClose() {
        return autoClose;
    }
}
