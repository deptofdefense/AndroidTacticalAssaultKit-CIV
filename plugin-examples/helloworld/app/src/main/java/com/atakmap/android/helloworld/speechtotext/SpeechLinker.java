
package com.atakmap.android.helloworld.speechtotext;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.helloworld.speechtotext.SpeechActivity;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;

import java.util.UUID;

/**
 * This class creates a Range and bearing line between 2 map items.
 * Speech format would be something like : Link x and y
 * You can see the actual words used in ATAK\plugins\helloworld\res\values\strings.xml
 */
public class SpeechLinker extends SpeechActivity {

    private final PointMapItem[] items = new PointMapItem[2];
    private final String[] selfArray;

    public SpeechLinker(String input, MapView view, Context pluginContext) {
        super(view, pluginContext);
        selfArray = getPluginContext().getResources()
                .getStringArray(R.array.self_array);
        analyzeSpeech(input);
        startActivity();

    }

    /**
     * This gets x and y out of things like:
     * Link x and y
     * Then sends them into the mapItem finder.
     *
     * @param input - The speech input
     */
    @Override
    void analyzeSpeech(String input) {
        String[] prep1Array = getPluginContext().getResources()
                .getStringArray(R.array.link_preposition_position1);
        String[] prep2Array = getPluginContext().getResources()
                .getStringArray(R.array.link_preposition_position2);
        String[] titles = new String[2];
        int indexPreposition1 = -1, indexPreposition2 = -1;
        String[] inputArr = input.split(" ");
        //First find the indexes of preposition 1 and 2
        for (int i = 0; i < inputArr.length; i++) {
            for (String s : prep1Array) {
                if (inputArr[i].equalsIgnoreCase(s)) {
                    indexPreposition1 = i;
                    break;
                }
            }
            for (String s : prep2Array) {
                if (inputArr[i].equalsIgnoreCase(s)) {
                    indexPreposition2 = i;
                    break;
                }
            }
        }
        //Now construct the names of the markers after the prepositions
        StringBuilder item1 = new StringBuilder();
        StringBuilder item2 = new StringBuilder();
        for (int i = indexPreposition1 + 1; i < indexPreposition2; i++) {
            item1.append(inputArr[i]);
            item1.append(" ");
        }
        titles[0] = item1.toString().trim();
        for (int i = indexPreposition2 + 1; i < inputArr.length; i++) {
            item2.append(inputArr[i]);
            item2.append(" ");
        }
        titles[1] = item2.toString().trim();
        itemFinder(titles);
    }

    /**
     * Checks to see if the two items are the same.
     * Then creates the RangeAndBearingMapItem (the line).
     * Then adds it to the Range & Bearing MapGroup.
     */
    @Override
    void startActivity() {
        if (items[0] != null && items[1] != null) {
            if (items[0].equals(items[1]))
                Toast.makeText(getView().getContext(),
                        "Can't link an object to itself", Toast.LENGTH_SHORT)
                        .show();
            else {
                RangeAndBearingMapItem rab = RangeAndBearingMapItem
                        .createOrUpdateRABLine(UUID.randomUUID().toString(),
                                items[0], items[1], true);
                rab.setVisible(true);
                final MapGroup _linkGroup = getView().getRootGroup()
                        .findMapGroup(
                                "Range & Bearing");
                _linkGroup.addItem(rab);
            }
        }
    }

    /**
     * Searches through the Cursor on Target MapGroup for both titles.
     * Then they get casted into PointMapItems because thats what the R&BItem creator uses.
     * Link x and y
     *
     * @param titles - String array of the x and y terms in "Link x to y"
     */
    private void itemFinder(String[] titles) {
        MapGroup cotGroup = getView().getRootGroup()
                .findMapGroup("Cursor on Target");
        for (int i = 0; i < items.length; i++) {
            for (String s : selfArray) {
                if (titles[i].contentEquals(s))
                    items[i] = getView().getSelfMarker();
            }
            if (items[i] == null)
                items[i] = (PointMapItem) cotGroup.deepFindItem("callsign",
                        titles[i]);
        }
        if (items[0] == null && items[1] == null)
            Toast.makeText(getView().getContext(), "Items not found",
                    Toast.LENGTH_SHORT).show();
        else if (items[0] == null)
            Toast.makeText(getView().getContext(), "Item 1 not found",
                    Toast.LENGTH_SHORT).show();
        else if (items[1] == null)
            Toast.makeText(getView().getContext(), "Item 2 not found",
                    Toast.LENGTH_SHORT).show();

    }
}
