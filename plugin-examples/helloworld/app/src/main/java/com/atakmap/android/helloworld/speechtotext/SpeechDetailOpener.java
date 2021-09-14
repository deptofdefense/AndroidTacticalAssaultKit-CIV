
package com.atakmap.android.helloworld.speechtotext;

import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

/**
 * User says "open goose details" and it opens the CoT marker goose's detail menu
 * Needs to be its own class because you can't access AtakBroadcast inside HelloWorldDropDownReceiver
 * Uses a different style of speech input, where the used words are stripped out in SpeechToActivity
 * before being sent here.
 */
public class SpeechDetailOpener {
    /**
     * Gets the speech in, finds the map Item based on that. If its not found, it asks for manual input.
     * Broadcasts intent to the CoTInfoBroadcastReceiver
     * @param speech - input from users voice, stripped of open and details, in theory should be a title of an CoT marker.
     * @param view - view from the HelloWorldDropDownReceiver. Needed to get the self marker. And to get the mapGroups
     */
    public SpeechDetailOpener(String speech, MapView view) {
        MapGroup cotGroup = view.getRootGroup()
                .findMapGroup("Cursor on Target");
        MapItem item = cotGroup.deepFindItem("callsign", speech);
        Intent detailOpener = new Intent()
                .setAction(CoTInfoBroadcastReceiver.COTINFO_DETAILS);
        boolean self = false;
        if (speech.contains("my")) {
            detailOpener.putExtra("targetUID", view.getSelfMarker().getUID());
            self = true;
        }
        if (item == null && !self) {
            Toast.makeText(view.getContext(), "Callsign not found",
                    Toast.LENGTH_SHORT).show();
        } else if (item != null) {
            detailOpener.putExtra("targetUID", item.getUID());
        }
        AtakBroadcast.getInstance().sendBroadcast(detailOpener);

    }
}
