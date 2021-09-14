
package com.atakmap.android.helloworld;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.content.ActivityNotFoundException;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.helloworld.plugin.R;

public class SpeechToTextActivity extends Activity {
    private final static int NORTHING_INTENT = 1;
    private final static int EASTING_INTENT = 2;
    private final static int SQUAREID_INTENT = 3;
    private final static int ALPHA_GRID_INTENT = 4;
    private final static int NUMERIC_GRID_INTENT = 5;
    private final static int MARKER_TYPE_INTENT = 6;
    private final static int NO_INTENT = 0;
    private final static int NORTHING_LENGTH = 5;
    private final static int EASTING_LENGTH = 5;
    private final static int SQUAREID_LENGTH = 2;
    private final static int ALPHA_GRID_LENGTH = 1;
    private final static int NUMERIC_GRID_LENGTH = 2;
    private final static int MARKER_LENGTH = 8;
    private final int REQ_CODE_SPEECH_INPUT = 100;
    private static final String SPEECH_INFO = "com.atackmap.android.helloworld.SPEECHINFO";
    private static final String ALPHA_VALIDATOR = "com.atackmap.android.helloworld.ALPHA_VALIDATOR";
    private static final String DIGIT_VALIDATOR = "com.atackmap.android.helloworld.DIGIT_VALIDATOR";
    private static final String MARKER_VALIDATOR = "com.atackmap.android.helloworld.MARKER_VALIDATOR";

    private final HashMap<String, String> mgrsData = new HashMap<>();
    private TextView txtSpeechInput;
    private TextView txtNorthing;
    private TextView txtEasting;
    private TextView txtSquareID;
    private TextView txtNumericGrid;
    private TextView txtAlphaGrid;
    private TextView txtMarkerType;
    private TextView txtMarker;
    private int nextIntent;
    private int currentIntent;
    private boolean fullRun;
    private Button btnMGRS;
    private Button btnDropMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.speech_to_text);

        btnMGRS = findViewById(R.id.speakMGRS_btn);
        btnDropMarker = findViewById(R.id.dropMarker_btn);
        txtNorthing = findViewById(R.id.northing_txt);
        txtEasting = findViewById(R.id.easting_txt);
        txtSquareID = findViewById(R.id.squareID_txt);
        txtNumericGrid = findViewById(R.id.numericGrid_txt);
        txtAlphaGrid = findViewById(R.id.alphaGrid_txt);
        txtMarkerType = findViewById(R.id.marker_txt);
        txtMarker = findViewById(R.id.marker_type);
        // hide the action bar
        ActionBar bar = getActionBar();
        if (bar != null)
            bar.hide();

        btnMGRS.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //set full run every time the user clicks the speakMGRS button
                fullRun = true;
                promptNumericGridZone();

            }
        });

        txtMarker.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptMarkerType();
            }
        });
        txtMarkerType.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptMarkerType();
            }
        });

        btnDropMarker.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                createMarker();

            }
        });

        txtNorthing.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptNorthing();
            }

        });
        txtEasting.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptEasting();
            }

        });
        txtSquareID.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptSquareId();
            }

        });
        txtAlphaGrid.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptAlphaGridZone();
            }

        });
        txtNumericGrid.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptNumericGridZone();
            }

        });

    }

    //different methods to hold track our intents prompt for speech
    protected void promptMarkerType() {
        currentIntent = MARKER_TYPE_INTENT;
        nextIntent = NO_INTENT;
        promptSpeechInput("Please state the marker type");
        fullRun = false;
    }

    protected void promptNorthing() {
        currentIntent = NORTHING_INTENT;
        if (fullRun) {
            nextIntent = MARKER_TYPE_INTENT;
        } else {
            nextIntent = NO_INTENT;
        }
        promptSpeechInput(getString(R.string.speech_prompt_northing));
    }

    protected void promptEasting() {
        currentIntent = EASTING_INTENT;
        if (fullRun) {
            nextIntent = NORTHING_INTENT;
        } else {
            nextIntent = NO_INTENT;
        }
        promptSpeechInput(getString(R.string.speech_prompt_easting));
    }

    protected void promptSquareId() {
        currentIntent = SQUAREID_INTENT;
        if (fullRun) {
            nextIntent = EASTING_INTENT;
        } else {
            nextIntent = NO_INTENT;
        }
        promptSpeechInput(getString(R.string.speech_prompt_square_id));
    }

    protected void promptAlphaGridZone() {
        currentIntent = ALPHA_GRID_INTENT;
        if (fullRun) {
            nextIntent = SQUAREID_INTENT;
        } else {
            nextIntent = NO_INTENT;
        }
        promptSpeechInput(getString(R.string.speech_prompt_alpha_grid_zone));
    }

    protected void promptNumericGridZone() {
        currentIntent = NUMERIC_GRID_INTENT;
        if (fullRun) {
            nextIntent = ALPHA_GRID_INTENT;
        } else {
            nextIntent = NO_INTENT;
        }
        promptSpeechInput(getString(R.string.speech_prompt_numeric_grid_zone));
    }

    /**
    * Showing google speech input dialog
    * */
    private void promptSpeechInput(String prompt) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);//create a new intent that will start the activity of prompting the user for speech and send it through the speech recognizer
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);//set the recognizer to use a free form model
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());//feed it the default language
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);//prompt the user for some speech

        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receiving speech input
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> results = data
                            .getStringArrayListExtra(
                                    RecognizerIntent.EXTRA_RESULTS);
                    dataChecker(results);
                }
                break;
            }
        }

        //switch to decide the next activity to launch
        //launch the next speech activity from the get results to avoid the activities from stacking
        switch (nextIntent) {
            case NORTHING_INTENT:
                promptNorthing();
                break;
            case EASTING_INTENT:
                promptEasting();
                break;
            case SQUAREID_INTENT:
                promptSquareId();
                break;
            case ALPHA_GRID_INTENT:
                promptAlphaGridZone();
                break;
            case NUMERIC_GRID_INTENT:
                promptNumericGridZone();
                break;
            case MARKER_TYPE_INTENT:
                promptMarkerType();
                break;
        }
    }

    protected ArrayList<String> speechVerifyLength(
            ArrayList<String> resultsToTest, int expectedLength) {
        ArrayList<String> lengthVerifiedResults = new ArrayList<>();
        for (String s : resultsToTest) {
            if (currentIntent == SQUAREID_INTENT) {
                if (s.length() == expectedLength) {
                    lengthVerifiedResults.add(s.toUpperCase());
                }
            } else {
                if (s.length() <= expectedLength && s.length() > 0) {
                    lengthVerifiedResults.add(s.toUpperCase());
                }
            }
        }
        return lengthVerifiedResults;
    }

    //check out data we got from the speech
    //we verify expected length ,expected alphanumeric and if it fits the MGRS scheme
    protected void dataChecker(ArrayList<String> speech) {
        String result;
        int expectedLength = 0;
        String validatorType = null;
        switch (currentIntent) {
            case NORTHING_INTENT:
                txtSpeechInput = findViewById(R.id.northing_txt);
                expectedLength = NORTHING_LENGTH;
                validatorType = DIGIT_VALIDATOR;
                break;

            case EASTING_INTENT:
                txtSpeechInput = findViewById(R.id.easting_txt);
                expectedLength = EASTING_LENGTH;
                validatorType = DIGIT_VALIDATOR;
                break;
            case SQUAREID_INTENT:
                txtSpeechInput = findViewById(R.id.squareID_txt);
                expectedLength = SQUAREID_LENGTH;
                validatorType = ALPHA_VALIDATOR;
                break;
            case ALPHA_GRID_INTENT:
                txtSpeechInput = findViewById(R.id.alphaGrid_txt);
                expectedLength = ALPHA_GRID_LENGTH;
                validatorType = ALPHA_VALIDATOR;
                break;

            case NUMERIC_GRID_INTENT:
                txtSpeechInput = findViewById(R.id.numericGrid_txt);
                expectedLength = NUMERIC_GRID_LENGTH;
                validatorType = DIGIT_VALIDATOR;
                break;
            case MARKER_TYPE_INTENT:
                txtSpeechInput = findViewById(R.id.marker_txt);
                expectedLength = MARKER_LENGTH;
                validatorType = MARKER_VALIDATOR;
                break;

        }

        //call our validators with the set flags
        //first call length then the alphanumeric
        result = dataValidator(speechVerifyLength(speech, expectedLength),
                validatorType);
        if (result != null) {
            txtSpeechInput.setText(result);
        }
    }

    protected String dataValidator(ArrayList<String> lengthValidatedResults,
            String validatorType) {
        //verify any alpha values
        //the letter portion of the grid reference must be between C and X
        //the Second letter in the square id must be between A and V
        //no letters can be O or I
        for (String s : lengthValidatedResults) {
            switch (validatorType) {
                case ALPHA_VALIDATOR: {
                    char[] chars = s.toCharArray();
                    for (char c : chars) {
                        if (!(Character.isLetter(c))) {
                            return null;
                        }
                        if (c == 'O' || c == 'I') {
                            return null;
                        }
                    }
                    if (currentIntent == ALPHA_GRID_INTENT) {
                        if (chars[0] >= 'C' && chars[0] <= 'X') {
                            return s;
                        }
                    } else {
                        if (chars[1] >= 'A' && chars[1] <= 'V') {
                            return s;
                        }
                    }

                    //Validate Digits
                    //the only special case is that the numeric portion of the grid must be 1-60
                    break;
                }
                case DIGIT_VALIDATOR: {
                    char[] chars = s.toCharArray();
                    for (char c : chars) {
                        if (!Character.isDigit(c)) {
                            return null;
                        }
                    }
                    if (currentIntent == NUMERIC_GRID_INTENT) {
                        if (Integer.parseInt(s) >= 1
                                && Integer.parseInt(s) <= 60) {
                            return s;
                        }
                    } else {
                        return s;
                    }

                    //Ensure our marker type is the correct word and case
                    break;
                }
                case MARKER_VALIDATOR:

                    if (s.equalsIgnoreCase("hostile")) {
                        return "Hostile";
                    } else if (s.equalsIgnoreCase("friendly")) {
                        return "Friendly";
                    } else if (s.equalsIgnoreCase("unknown")) {
                        return "Unknown";
                    } else if (s.equalsIgnoreCase("neutral")) {
                        return "Neutral";
                    }
                    break;
            }
        }
        //we return null so that we do not continue with any bad data
        return null;
    }

    //we have our data stick it into a hashmap and broadcast
    // it so that the main activity can listen for it and pick it up
    protected void createMarker() {

        mgrsData.put("numericGrid", txtNumericGrid.getText().toString());
        mgrsData.put("alphaGrid", txtAlphaGrid.getText().toString());
        mgrsData.put("squareID", txtSquareID.getText().toString());
        mgrsData.put("easting", txtEasting.getText().toString());
        mgrsData.put("northing", txtNorthing.getText().toString());

        if (txtMarkerType.getText().toString().isEmpty()) {
            mgrsData.put("markerType", "Hostile");
        } else {
            mgrsData.put("markerType", txtMarkerType.getText().toString());
        }

        Intent returnIntent = new Intent(SPEECH_INFO);
        returnIntent.putExtra("mgrsData", mgrsData);
        sendBroadcast(returnIntent);
        finish();

    }

    public interface SpeechDataReceiver {
        void onSpeechDataReceived(HashMap<String, String> s);
    }

    /**
     * Broadcast Receiver that is responsible for getting the data back to the
     * plugin.
     */
    static class SpeechDataListener extends BroadcastReceiver {
        private boolean registered = false;
        private SpeechDataReceiver sdr = null;

        synchronized public void register(Context context,
                SpeechDataReceiver sdr) {
            if (!registered)
                context.registerReceiver(this, new IntentFilter(SPEECH_INFO));

            this.sdr = sdr;
            registered = true;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (this) {
                try {
                    Bundle extras = intent
                            .getExtras();
                    if (extras != null) {
                        HashMap<String, String> s = (HashMap<String, String>) extras
                                .get("mgrsData");
                        if (s != null && sdr != null)
                            sdr.onSpeechDataReceived(s);
                    }
                } catch (Exception ignored) {
                }
                if (registered) {
                    context.unregisterReceiver(this);
                    registered = false;
                }
            }
        }
    }
}
