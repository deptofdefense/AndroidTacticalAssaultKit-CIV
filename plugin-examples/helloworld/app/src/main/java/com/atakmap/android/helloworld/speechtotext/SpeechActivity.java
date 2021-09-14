
package com.atakmap.android.helloworld.speechtotext;

import android.content.Context;

import com.atakmap.android.maps.MapView;

/**
 * Parent class to be used by Speech activities.
 * All basically need to: -find the string title of what they're looking for
 *                        -broadcast that target to some receiver
 */
abstract class SpeechActivity {
    private final MapView view;
    private final Context pluginContext;

    SpeechActivity(MapView view, Context pluginContext) {
        this.view = view;
        this.pluginContext = pluginContext;
    }

    /**
     * This will analyze the speech String, taking out data and put it where it needs to be.
     * @param input - The speech input
     */
    abstract void analyzeSpeech(String input);

    /**
     * This is where the activity will be triggered by the speech class.
     * Whether it is ATAKBroadcast or something else.
     */
    abstract void startActivity();

    /**
     * Gets the view out of the class
     * @return - should be the map view from HelloWorldDropDownReceiver
     */
    MapView getView() {
        return view;
    }

    /**
     * Gets the pluginContext out of the class
     * This is needed for loading resources
     * Note* for things like toast you need to use getView().getContext()
     *      because its a different context.
     * @return - the pluginContext from HelloWorldDropDownReceiver
     */
    Context getPluginContext() {
        return pluginContext;
    }

}
