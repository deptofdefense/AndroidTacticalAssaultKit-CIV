
package com.atakmap.app.system;

import android.view.View;

import java.io.InputStream;

public interface FlavorProvider {

    /**
     * Modifies the splash screen based on information directly from the flavor.   This will include
     * the splash screen graphic and the asociated Text.   The provided view is currently the splash
     * screen view instantiated by the core tak application.
     * @param splashView the view to modify
     * @param orientation the orientation of the view
     */
    void installCustomSplashScreen(View splashView, int orientation);

    /**
     * Returns true if the flavor Supplies Military Capabilities
     */
    boolean hasMilCapabilities();

    /**
     * Allows for an input stream to be constructed from a asset that may or may not be provided
     * by the flavor.
     * @param name The location of the file and the name in the asset directory.
     */
    InputStream getAssetInputStream(String name);

    /**
     * Unroll out additional action bars.
     */
    void rolloutActionBars();

    /**
     * Called when the app in first run or upgraded or when the flavor has changed.
     * This will roll out all user documentation specific to the flavor.
     */
    void deployDocumentation();

    /**
     * Deploy WMS Pointers specific to the flavor.
     */
    void deployWMSPointers();
}
