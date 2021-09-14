
package com.atakmap.android.helloworld.speechtotext;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.helloworld.speechtotext.SpeechActivity;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

/**
 * This class simply finds the target described in the speech input
 * and drops a 9-line on it. Can probably get rid of this class
 * and just put it all in the HelloWorldDropDownReceiver since
 * it is so small.
 */
public class SpeechNineLine extends SpeechActivity {
    private final String TAG = "SPEECH_NINE_LINE";
    private String target;
    private final MapGroup cotGroup;

    /**
     * Finds the described marker
     * @param input - the marker wanted
     * @param view - needed to get map groups
     */
    public SpeechNineLine(String input, MapView view, Context pluginContext) {
        super(view, pluginContext);
        cotGroup = view.getRootGroup().findMapGroup("Cursor on Target");
        analyzeSpeech(input);
        startActivity();
    }

    /**
     * Speech format: 9-Line on x : where x is the name of a CoT marker
     * Removes the stuff before x and stores x in the target variable.
     * @param input - The speech input
     */
    @Override
    void analyzeSpeech(String input) {
        String[] onArray = getPluginContext().getResources()
                .getStringArray(R.array.at_array);
        int indexOn = -1;
        String[] inputArr = input.split(" ");
        //Get the index of "on" style word in input
        for (int i = 0; i < inputArr.length; i++) {
            for (String s : onArray) {
                if (s.equalsIgnoreCase(inputArr[i])) {
                    indexOn = i;
                    break;
                }
            }
        }
        //Now build the words after "on" into the callsign
        StringBuilder targetBuilder = new StringBuilder();
        for (int i = indexOn + 1; i < inputArr.length; i++) {
            targetBuilder.append(inputArr[i]);
            targetBuilder.append(" ");
        }
        target = targetBuilder.toString().trim();
    }

    /**
     * Finds the marker with the callsign's UID.
     * Puts it in an intent and broadcasts it to the 9LineReceiver
     *
     */
    @Override
    void startActivity() {
        MapItem marker = cotGroup.deepFindItem("callsign", target);
        if (marker != null) {
            Intent intent = new Intent()
                    .setAction("com.atakmap.baokit.NINE_LINE");
            intent.putExtra("targetUID", marker.getUID());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        } else {
            Toast.makeText(getView().getContext(), "Callsign not found",
                    Toast.LENGTH_SHORT).show();
        }

    }

}
