
package com.atakmap.android.helloworld.speechtotext;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Calls upon the android google speech to text function.
 * Decides what the user wants to do from the speech.
 * Puts that into an intent and sends it back out
 * to the HelloWorldDropDownReceiver.
 */
public class SpeechToActivity extends Activity {
    //These are the intents for HelloWorldDropDownReceiver
    public static final int NAVIGATE_INTENT = 0;
    public static final int PLOT_INTENT = 1;
    public static final int BLOODHOUND_INTENT = 2;
    public static final int NINE_LINE_INTENT = 3;
    public static final int COMPASS_INTENT = 4;
    public static final int BRIGHTNESS_INTENT = 5;
    public static final int DELETE_INTENT = 6;
    public static final int LINK_INTENT = 7;
    public static final int SHOW_HOSTILES_INTENT = 8;
    public static final int OPEN_DETAILS_INTENT = 9;
    public static final int EMERGENCY_INTENT = 10;
    public static final int CAMERA_INTENT = 11;
    //These are the extra intents for greater details in the activities bundle
    public static final String QUICK_INTENT = "com.atakmap.android.helloworld.QUICKINTENT";
    public static final String NAVIGATE_SPEECH_INFO = "com.atakmap.android.helloworld.NAVIGATESPEECHINFO";
    public static final String ACTIVITY_INFO_BUNDLE = "com.atakmap.android.helloworld.ACTIVITYINFOBUNDLE";
    public static final String DESTINATION = "com.atakmap.android.helloworld.DESTINATION";
    public static final String EMERGENCY_TYPE = "com.atakmap.android.helloworld.EMERGENCYTYPE";
    public static final String ACTIVITY_INTENT = "com.atakmap.android.helloworld.ACTIVITY";

    private final int REQ_CODE_SPEECH_INPUT = 100;

    private static final String TAG = "SpeechToActivity";
    //These are the arrays of synonyms
    private String[] dropArray;
    private String[] quickArray;
    private String[] navArray;
    private String[] bloodHoundArray;
    private String[] nineLineArray;
    private String[] compassArray;
    private String[] brightnessArray;
    private String[] deleteArray;
    private String[] hostileSynonyms;
    private String[] openArray;
    private String[] detailsArray;
    private String[] emergencyArray;
    private String[] nineOneOneArray;
    private String[] cancelArray;
    private String[] ringTheBellArray;
    private String[] troopsInContactArray;
    private String[] linkArray;
    private String[] cameraArray;

    private Intent returnIntent;
    private final Bundle activities = new Bundle();

    /**
     * This is what gets triggered when a user hits the Speech to Activity button.
     * It loads in all the synonym arrays, creates the intent and calls the promptSpeechInput()
     * @param savedInstanceState - What the user was doing before hitting the button.
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources resources = this.getResources();
        dropArray = resources.getStringArray(R.array.drop_a_array);
        quickArray = resources.getStringArray(R.array.quick_array);
        navArray = resources.getStringArray(R.array.navigate_array);
        bloodHoundArray = resources.getStringArray(R.array.bloodhound_array);
        nineLineArray = resources.getStringArray(R.array.nine_line_array);
        compassArray = resources.getStringArray(R.array.compass_array);
        brightnessArray = resources.getStringArray(R.array.brightness_array);
        deleteArray = resources.getStringArray(R.array.delete_array);
        hostileSynonyms = resources.getStringArray(R.array.hostile_array);
        openArray = resources.getStringArray(R.array.open_array);
        detailsArray = resources.getStringArray(R.array.details_array);
        emergencyArray = resources.getStringArray(R.array.emergency_array);
        nineOneOneArray = resources.getStringArray(R.array.NineOneOne_Array);
        cancelArray = resources.getStringArray(R.array.Cancel_Array);
        ringTheBellArray = resources.getStringArray(R.array.RingTheBell_Array);
        troopsInContactArray = resources
                .getStringArray(R.array.TroopsInContact);
        linkArray = resources.getStringArray(R.array.link_array);
        cameraArray = resources.getStringArray(R.array.camera_array);

        returnIntent = new Intent(NAVIGATE_SPEECH_INFO);
        promptSpeechInput();
    }

    /**
     * Showing google speech input dialog
     */
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt_Activity));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * This is what receives the speech input after the speech to text is finished.
     *
     * @param requestCode - The code for speech input
     * @param resultCode  - If the result is OK then it succeeded
     * @param data        - The actual text converted from speech
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(
                                    RecognizerIntent.EXTRA_RESULTS);

                    activityDecider(result.get(0));
                } else {
                    finish();
                }
                break;
            }
            default:
                finish();

        }
    }

    /**
     * Decides what activity to do based on the speech.
     * Looks through arrays of synonyms.
     * Puts the activity intent into the activities bundle.
     * activities bundle gets sent back to HelloWorldDropDownReceiver
     * @param input -The unformatted speech input
     */
    private void activityDecider(String input) {
        activities.putString(ACTIVITY_INTENT, "null");

        for (String s : dropArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, PLOT_INTENT);
                activities.putString(DESTINATION, input);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : navArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, NAVIGATE_INTENT);

                for (String q : quickArray) {
                    if (input.contains(q))
                        activities.putBoolean(QUICK_INTENT, true);
                }
                activities.putString(DESTINATION, input);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : bloodHoundArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, BLOODHOUND_INTENT);
                activities.putString(DESTINATION, input);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : nineLineArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, NINE_LINE_INTENT);
                activities.putString(DESTINATION, input);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : compassArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, COMPASS_INTENT);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : brightnessArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, BRIGHTNESS_INTENT);
                activities.putString(DESTINATION, input);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : deleteArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, DELETE_INTENT);
                activities.putString(DESTINATION, input);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : cameraArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, CAMERA_INTENT);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : openArray) {
            if (input.contains(s)) {
                for (String w : detailsArray) {
                    if (input.contains(w)) {
                        activities.putInt(ACTIVITY_INTENT, OPEN_DETAILS_INTENT);
                        input = input.replace(w, "").replace(s, "")
                                .replace("'s", "").trim();
                        activities.putString(DESTINATION, input);
                        returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                        broadcast();
                    }
                }
                for (String h : hostileSynonyms) {
                    if (input.contains(h)) {
                        activities.putInt(ACTIVITY_INTENT,
                                SHOW_HOSTILES_INTENT);
                        returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                        broadcast();
                    }
                }
            }
        }
        for (String s : emergencyArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, EMERGENCY_INTENT);
                String em = "911 Alert";
                for (String w : nineOneOneArray) {
                    if (input.contains(w)) {
                        activities.putString(EMERGENCY_TYPE, em);
                        returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                        broadcast();
                    }
                }
                for (String w : cancelArray) {
                    if (input.contains(w)) {
                        em = "Cancel Alert";
                        activities.putString(EMERGENCY_TYPE, em);
                        returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                        broadcast();
                    }
                }
                for (String w : ringTheBellArray) {
                    if (input.contains(w)) {
                        em = "Ring The Bell";
                        activities.putString(EMERGENCY_TYPE, em);
                        returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                        broadcast();
                    }
                }
                for (String w : troopsInContactArray) {
                    if (input.contains(w)) {
                        em = "Troops In Contact";
                        activities.putString(EMERGENCY_TYPE, em);
                        returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                        broadcast();
                    }
                }
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        for (String s : linkArray) {
            if (input.contains(s)) {
                activities.putInt(ACTIVITY_INTENT, LINK_INTENT);
                activities.putString(DESTINATION, input);
                returnIntent.putExtra(ACTIVITY_INFO_BUNDLE, activities);
                broadcast();
            }
        }
        finish();
    }

    /**
     * Sends out returnIntent back to HelloWorldDropDownReceiver
     */
    private void broadcast() {
        sendBroadcast(returnIntent);
        finish();
    }

    public interface SpeechDataReceiver {
        void onSpeechDataReceived(Bundle activityInfoBundle);
    }

    /**
     * Broadcast Receiver that is responsible for getting the data back to the
     * plugin.
     * Registers receiver with NAVIGATE_SPEECH_INFO. All intents with that action will end up
     * here(or there in HelloWorldDropDownReceiver rather)
     *
     */
    public static class SpeechDataListener extends BroadcastReceiver {
        private boolean registered = false;
        private SpeechToActivity.SpeechDataReceiver sdra = null;

        synchronized public void register(Context context,
                SpeechToActivity.SpeechDataReceiver sdra) {
            if (!registered)
                context.registerReceiver(this,
                        new IntentFilter(NAVIGATE_SPEECH_INFO));

            this.sdra = sdra;
            registered = true;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                try {
                    Bundle activityInfoBundle = intent
                            .getBundleExtra(ACTIVITY_INFO_BUNDLE);
                    if (activityInfoBundle != null && sdra != null)
                        sdra.onSpeechDataReceived(activityInfoBundle);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                if (registered) {
                    context.unregisterReceiver(this);
                    registered = false;
                }
            }
        }
    }
}
