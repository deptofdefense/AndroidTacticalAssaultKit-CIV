
package com.atakmap.android.helloworld.speechtotext;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.gui.EditText;
import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.helloworld.speechtotext.SpeechActivity;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapCoreIntentsComponent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

/**
 * Takes in user input. Then decides what mapGroup to look in based on target.
 * Then it deletes that object.
 */
public class SpeechItemRemover extends SpeechActivity {
    private final String TAG = "SPEECH_ITEM_REMOVER";
    private final String[] callsignArray;
    private final String[] drawingObjectArray;
    private final String[] routeArray;
    private final String[] wordNumberArray;
    private MapGroup mapGroup;
    private MapItem targetItem;
    private String target;
    private String mapGroupType = "null";

    public SpeechItemRemover(String input, final MapView view,
            final Context context) {
        super(view, context);
        callsignArray = context.getResources()
                .getStringArray(R.array.callsign_array);
        drawingObjectArray = context.getResources()
                .getStringArray(R.array.drawing_objects_array);
        routeArray = context.getResources().getStringArray(R.array.route_array);
        wordNumberArray = context.getResources()
                .getStringArray(R.array.word_number_array);
        analyzeSpeech(input);
        startActivity();
    }

    /**
     * Finds out what type of item the user is trying to remove
     * based on their speech input
     * input: Remove shape x| remove callsign x| remove route x|
     * words used can be found in strings.xml in HelloWorld
     * @param input - The speech input
     */
    @Override
    void analyzeSpeech(String input) {
        input = input.replace("call sign", "callsign");
        String[] inputArr = input.split(" ");
        for (String s : callsignArray) {
            if (inputArr[1].equalsIgnoreCase(s)) {
                mapGroupType = "Cursor on Target";
                mapGroup = getView().getRootGroup().findMapGroup(mapGroupType);
                targetGetter(input);
            }
        }
        if (mapGroupType.contentEquals("null")) {
            for (String s : routeArray) {
                if (inputArr[1].equalsIgnoreCase(s)) {
                    mapGroupType = "Route";
                    mapGroup = getView().getRootGroup()
                            .findMapGroup(mapGroupType);
                    targetGetter(input);
                }
            }
        }
        if (mapGroupType.contentEquals("null")) {
            for (String s : drawingObjectArray) {
                if (inputArr[1].equalsIgnoreCase(s)) {
                    mapGroupType = "Drawing Objects";
                    mapGroup = getView().getRootGroup()
                            .findMapGroup(mapGroupType);
                    targetGetter(input);
                }
            }
        } else if (mapGroupType.contentEquals("null")) {
            Toast.makeText(getView().getContext(),
                    "Please say the type of item before it's title",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Searches the group found in analyze speech
     * for the title/callsign found in targetGetter
     * When found, asks user for confirmation.
     * If not found, asks user for manual input
     */
    @Override
    void startActivity() {
        {
            if (mapGroupType.equals("Cursor on Target"))
                targetItem = mapGroup.deepFindItem("callsign", target);
            else
                targetItem = mapGroup.deepFindItem("title", target);
            if (targetItem != null) {
                AlertDialog.Builder alert = new AlertDialog.Builder(
                        getView().getContext());
                alert.setTitle(getPluginContext().getResources()
                        .getString(R.string.Remove_item_warn));
                alert.setNegativeButton(getPluginContext().getResources()
                        .getString(R.string.cancel_btn), null);
                alert.setPositiveButton(
                        getPluginContext().getResources()
                                .getString(R.string.confirm_btn),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Long serialID = targetItem.getSerialId();
                                AtakBroadcast.getInstance()
                                        .sendBroadcast(new Intent()
                                                .setAction(
                                                        MapCoreIntentsComponent.ACTION_DELETE_ITEM)
                                                .putExtra("serialId",
                                                        serialID));

                            }
                        });
                alert.show();

            } else {
                final EditText input = new EditText(getView().getContext());
                input.setSingleLine(true);
                input.setText(target);
                input.selectAll();
                AlertDialog.Builder manualInput = new AlertDialog.Builder(
                        getView().getContext());
                manualInput.setTitle(getPluginContext().getResources()
                        .getString(R.string.Manual_mode));
                manualInput.setView(input);
                manualInput.setNegativeButton(getPluginContext().getResources()
                        .getString(R.string.cancel_btn), null);
                manualInput.setPositiveButton(
                        getPluginContext().getResources()
                                .getString(R.string.confirm_btn),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                String newName = input.getText().toString()
                                        .trim();
                                targetItem = mapGroup.deepFindItem("title",
                                        newName);
                                if (targetItem != null) {
                                    Long serialID = targetItem.getSerialId();
                                    AtakBroadcast.getInstance()
                                            .sendBroadcast(new Intent()
                                                    .setAction(
                                                            MapCoreIntentsComponent.ACTION_DELETE_ITEM)
                                                    .putExtra("serialId",
                                                            serialID));
                                } else {
                                    Toast.makeText(getView().getContext(),
                                            "Item not found",
                                            Toast.LENGTH_SHORT).show();
                                }

                            }
                        });
                AlertDialog alert = manualInput.create();
                alert.show();
            }
        }
    }

    /**
     * This searches for the name of the target the user wants to remove
     * Removes "remove x" from input, then builds whats left into the target.
     *
     * @param target - Something like "Remove callsign Goose"
     */
    private void targetGetter(String target) {
        target = target.replace("call sign", "callsign");
        StringBuilder targetBuilder = new StringBuilder();
        String[] targetArr = target.split(" ");
        targetArr[0] = "";
        targetArr[1] = "";
        for (String s : targetArr) {
            for (String w : wordNumberArray) {
                String[] numberWord = w.split(",");
                s = s.replace(numberWord[1], numberWord[0]);
            }
            targetBuilder.append(s);
            targetBuilder.append(" ");
        }
        this.target = targetBuilder.toString().trim();
    }
}
