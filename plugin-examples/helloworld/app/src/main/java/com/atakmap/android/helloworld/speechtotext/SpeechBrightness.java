
package com.atakmap.android.helloworld.speechtotext;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.brightness.BrightnessComponent;
import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.helloworld.speechtotext.SpeechActivity;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;

/**
 * Class that takes in speech input: Change brightness to xxx
 * Gets x then sends it to the brightness receiver to change the brightness
 */
public class SpeechBrightness extends SpeechActivity {
    private final String[] highArray;
    private final String[] mediumArray;
    private final String[] lowArray;

    private int value = -1;
    private final Intent returnIntent = new Intent();

    public SpeechBrightness(MapView view, Context pluginContext, String input) {
        super(view, pluginContext);
        highArray = getPluginContext().getResources()
                .getStringArray(R.array.high_array);
        mediumArray = getPluginContext().getResources()
                .getStringArray(R.array.medium_array);
        lowArray = getPluginContext().getResources()
                .getStringArray(R.array.low_array);
        analyzeSpeech(input);
    }

    /**
     * The input needs to be in the format "x brightness to y"
     * It then checks y to see if it's a high/med/low synonym
     * If not it checks to see if y is a integer
     * If not then it does nothing
     * Value is assigned based on y's value
     * @param input - The speech input
     */
    @Override
    void analyzeSpeech(String input) {
        final int MAX = 40;
        final int MIN = 0;
        final int MED = 20;
        String[] inputArr = input.split(" ");
        if (inputArr.length == 4) {
            String x = inputArr[3];
            for (String s : highArray) {
                if (s.equalsIgnoreCase(x)) {
                    value = MAX;
                    startActivity();
                }
            }
            for (String s : mediumArray) {
                if (s.equalsIgnoreCase(x)) {
                    value = MED;
                    startActivity();
                }
            }
            for (String s : lowArray) {
                if (s.equalsIgnoreCase(x)) {
                    value = MIN;
                    startActivity();
                }

            }
            if (value == -1) {
                try {
                    value = Integer.parseInt(x);
                } catch (NumberFormatException e) {
                    Toast.makeText(getView().getContext(), "Invalid brightness",
                            Toast.LENGTH_SHORT).show();
                }
                startActivity();
            }
        } else
            Toast.makeText(getView().getContext(),
                    "Example: Change brightness to high", Toast.LENGTH_SHORT)
                    .show();
    }

    /**
     * Puts the value from analyzeSpeech into an intent
     * Sets the action to be SHOW_BRIGHTNESS_TOOL
     * Which gets picked up by the BrightnessReceiver
     * The value is then applied to the brightness
     */
    @Override
    void startActivity() {
        returnIntent.setAction(BrightnessComponent.SHOW_BRIGHTNESS_TOOL);
        returnIntent.putExtra("value", value);
        AtakBroadcast.getInstance().sendBroadcast(returnIntent);
    }
}
