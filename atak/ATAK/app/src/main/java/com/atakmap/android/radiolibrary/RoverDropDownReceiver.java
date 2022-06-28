
package com.atakmap.android.radiolibrary;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.app.Notification;

import com.atakmap.android.util.SimpleItemSelectedListener;

import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.VideoBrowserDropDownReceiver;
import com.atakmap.comms.NetworkManagerLite;
import com.atakmap.comms.NetworkManagerLite.NetworkDevice;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.android.network.TrafficRecorder;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.video.AddEditAlias;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.StreamManagementUtils;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.DecimalFormat;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoverDropDownReceiver extends DropDownReceiver implements
        OnClickListener, OnLongClickListener,
        RoverInterface.RadioListener, OnStateListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public interface RadioStatusListener {
        void roverStatusChanged(int color, String text, String subtext);
    }

    private final List<RadioStatusListener> rslList = new ArrayList<>();

    public enum RoverStatusState {
        OFF,
        DISCONNECTED,
        ZERO_BARS,
        ONE_BAR,
        TWO_BARS,
        THREE_BARS
    }

    private final static int NOTIFY_ID = 80200;

    private final SharedPreferences sharedPreferences;

    private static final SimpleDateFormat recDateFormatter = new SimpleDateFormat(
            "yyyyMMMdd_HHmmss", LocaleUtil.getCurrent());

    private final View _layout;
    public static final String TAG = "RoverDropDownReceiver";
    public final String CONNECTED;
    public final String SEARCHING;
    public final String DISCONNECTED;
    public final String OFF;
    public final String INVALID_FREQ;

    private static final String PREF_KEY_DIG_PORT = "rover_port_digital";
    private static final String PREF_KEY_ANALOG_IP = "rover_ip_analog";
    private static final String PREF_KEY_ANALOG_PORT = "rover_port_analog";
    private static final String PREF_KEY_DIG_IP = "rover_ip_digital";

    public enum Frequency {
        INVALID,
        UHF,
        LBAND,
        SBAND,
        CBANDLOW,
        CBANDHIGH,
        KUBANDLOW,
        KUBANDLOW2,
        KUBANDUPPER
    }

    private final String[] modules = new String[] {
            "M1", "M2", "M3", "M4"
    };

    private final String[] waveforms = new String[] {
            "TACTICAL", "ANALOG", "VNW", "CDL", "CDL ALTERNATIVE", "BECDL",
            "DDL", "NONE"
    };

    // UI elements
    private final TextView statusText;
    private final ImageView statusImage;
    private final Spinner presetSpinner;
    private final Button freqBtn;
    private final Button chanmodBtn;
    private final Button scanFreqBtn;
    private final Button nextFreqBtn;
    private final Button pcBtn;
    private final Button favBtn;
    private final Button watchBtn;

    // Add ROVER vs. DDL button
    private final Button modeBtn;

    private boolean sideband = false;
    private boolean ignoreKLV = false;
    private boolean disableCOT = true;
    private int sideband_port = 3002;

    private String urlAnalog;
    private String urlDigital;

    private int frequencyCode = 0;
    private int endFrequencyCode = 0;
    private String wfc;
    private String nft;

    private String roverVersion = "";

    private String moduleType;
    private int channel;

    private boolean attemptReaquire = false;

    private int inCurrentStatus;
    private int outCurrentStatus;

    private final RecentlyUsed ru;

    private RoverInterface.Receiver lastStatus;

    private final Context context;
    /**
     * Frequency Scanning can be performed only in certain ranges. UHF 400MHz - 470MHz L-Band
     * 1625Mhz - 1850Mhz S-Band 2200Mhz - 2500Mhz C-Band 4400Mhz - 4950Mhz C-Band 5250Mhz - 5850Mhz
     * less likely (not addressed in this implementation); 
     * Ku-Band Lower 14.4Ghz - 14.83Ghz 
     * Ku-Band Lower 14.7-14.835 
     * Ku-Band Upper 15.15Ghz - 15.35Ghz 
     */
    private static final Object lock = new Object();

    private static RoverInterface radio;

    private static RoverDropDownReceiver _instance;

    public static void setRoverImpl(
            final RoverInterface _radio) {

        synchronized (lock) {
            if (radio != null) {
                radio.stopListening();
                radio.stopMonitoring();
            }
            Log.d(TAG, "swapping out rover implementation for: " + _radio);
            if (_radio == null)
                radio = new DefaultRoverImpl();
            else
                radio = _radio;
        }
    }

    /**
     * initialize with the mapView
     */
    public RoverDropDownReceiver(MapView mapView) {
        super(mapView);
        context = mapView.getContext();
        CONNECTED = context.getString(R.string.connected);
        SEARCHING = context.getString(R.string.searching_no_space);
        DISCONNECTED = context.getString(R.string.radio_not_connected);
        OFF = context.getString(R.string.radio_not_monitoring);
        INVALID_FREQ = context.getString(R.string.radio_invalid_frequency);

        synchronized (lock) {
            if (radio == null)
                radio = new DefaultRoverImpl();
        }

        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        ru = new RecentlyUsed(context);

        // in case the network monitor is running, the internal send to address
        // should match the network network provided by the network monitor.   
        NetworkDevice nd = getNetworkMapDevice();
        if (nd != null) {
            String prefAddress = nd.getPreferredAddress();
            if (prefAddress != null) {
                if (prefAddress.equals("192.168.50.200")) {
                    sharedPreferences.edit().putString("rover_ip_address",
                            "192.168.50.1").apply();
                } else if (prefAddress.equals("192.168.80.200")) {
                    sharedPreferences.edit().putString("rover_ip_address",
                            "192.168.80.1").apply();
                }
            }
        }

        updateURLAnalog();
        updateURLDigital();

        sideband = sharedPreferences.getBoolean("rover_sideband", false);
        ignoreKLV = sharedPreferences.getBoolean("rover_ignoreKLV", false);
        disableCOT = sharedPreferences.getBoolean("rover_disableCOT", true);
        sideband_port = sharedPreferences.getInt("rover_sideband_port", 3002);

        // inflate the layout
        LayoutInflater inflater = LayoutInflater.from(context);
        _layout = inflater.inflate(R.layout.isrv_radio_dropdown, null);

        statusText = _layout.findViewById(R.id.roverStatus_tv);
        statusImage = _layout.findViewById(R.id.roverStatus_iv);
        presetSpinner = _layout
                .findViewById(R.id.roverPreset_spinner);
        presetSpinner.setPrompt(context.getString(R.string.preset));

        presetSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent, View v,
                            int position, long id) {
                        if (position == 0)
                            return;

                        if (v instanceof TextView)
                            ((TextView) v).setTextColor(Color.WHITE);

                        // hand jamming in 1 so it can be initially set to none.
                        radio.loadPreset((byte) (position - 1));
                    }
                });

        // setup buttons
        pcBtn = _layout.findViewById(R.id.nc_btn);
        pcBtn.setOnClickListener(this);
        favBtn = _layout.findViewById(R.id.fav_btn);
        favBtn.setOnClickListener(this);

        freqBtn = _layout.findViewById(R.id.freq_btn);
        freqBtn.setOnClickListener(this);

        chanmodBtn = _layout.findViewById(R.id.chan_mod_btn);
        chanmodBtn.setOnClickListener(this);

        scanFreqBtn = _layout.findViewById(R.id.scanFreq_btn);
        scanFreqBtn.setOnClickListener(this);
        nextFreqBtn = _layout.findViewById(R.id.nextFreq_btn);
        nextFreqBtn.setOnClickListener(this);
        watchBtn = _layout.findViewById(R.id.watchRover_btn);

        watchBtn.setOnClickListener(this);
        watchBtn.setOnLongClickListener(this);

        // Setup ROVER vs. DDL button
        modeBtn = _layout.findViewById(R.id.mode_btn);
        modeBtn.setOnClickListener(this);

        setRoverMode();

        setEnabled(false);

        _instance = this;
    }

    private void setEnabled(boolean enabled) {
        freqBtn.setEnabled(enabled);
        chanmodBtn.setEnabled(enabled);
        presetSpinner.setEnabled(enabled);
        watchBtn.setEnabled(enabled);
        scanFreqBtn.setEnabled(enabled);
        nextFreqBtn.setEnabled(enabled);
        pcBtn.setEnabled(enabled);
        favBtn.setEnabled(enabled);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
    }

    @Override
    public void log(final String tag, final String message) {
        Log.d(tag, message);
    }

    void run(String action) {

        if (action.equalsIgnoreCase(
                "com.atakmap.radiocontrol.ROVER_SHOW_CONFIG")) {
            Log.d(TAG, "rover show config");
            config();
        } else if (action.equalsIgnoreCase(
                "com.atakmap.radiocontrol.ROVER_CONTROL_START")) {
            Log.d(TAG, "start rover monitoring");
            start();
        } else if (action.equalsIgnoreCase(
                "com.atakmap.radiocontrol.ROVER_CONTROL_STOP")) {
            Log.d(TAG, "stop rover monitoring");
            stop();
        } else if (action.equalsIgnoreCase(
                "com.atakmap.radiocontrol.ROVER_CONTROL")) {
            HintDialogHelper
                    .showHint(
                            getMapView().getContext(),
                            "ROVER - IMPORTANT DETAILS",
                            "ROVER & DDL modes are toggled using the button just left of the frequency. \n\n"
                                    +
                                    "If you successfully connect to a frequency using the TacRover-E but video is not playing "
                                    +
                                    "toggle the TacRover-E Quirks mode in the ROVER Configuration Screen. \n\n"
                                    +
                                    " ",
                            "tre.quirksmode");

            // set the status text and icon to match the values in the Radio Dropdown
            setAssociationKey("isrvNetworkPreference");
            showDropDown(_layout, FIVE_TWELFTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, this);
            setRetain(true);

        }

    }

    @Override
    public void onMcoStatus(final int status) {
        if (outCurrentStatus == status)
            return;

        outCurrentStatus = status;
        if (status == RoverInterface.STATUS_OK) {
            setStatusText(Color.GREEN,
                    context.getString(R.string.radio_network_sending_normal));
        } else if (status == RoverInterface.STATUS_TIMEOUT) {
            setStatusText(
                    Color.YELLOW,
                    context.getString(R.string.radio_communication_timeout),
                    context.getString(
                            R.string.radio_communication_timeout_subtext1));
        } else if (status == RoverInterface.STATUS_ERROR) {
            setStatusText(
                    Color.RED,
                    context.getString(
                            R.string.radio_communication_sending_error));
        }
    }

    @Override
    public void onMciStatus(final int status) {
        if (inCurrentStatus == status)
            return;

        inCurrentStatus = status;

        // switched states to connected,
        // perform initial configuration as if it is the
        // first time and request the presets.

        if (status == RoverInterface.STATUS_OK) {
            radio.enableReceiver();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }

            radio.getPresets(0, 30);

            attemptReaquire = true;

        }

        getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (status == RoverInterface.STATUS_OK) {
                    setStatusIcon(RoverStatusState.ZERO_BARS);
                    setStatusText(Color.GREEN, CONNECTED);
                } else if (status == RoverInterface.STATUS_TIMEOUT) {
                    NetworkDevice nd = getNetworkMapDevice();
                    if (nd == null || nd.getInterface() == null) {
                        setStatusIcon(RoverStatusState.DISCONNECTED);
                        setStatusText(Color.RED, DISCONNECTED);
                    } else {

                        setStatusIcon(RoverStatusState.DISCONNECTED);
                        setStatusText(
                                Color.YELLOW,
                                context.getString(
                                        R.string.radio_communication_timeout),
                                context.getString(
                                        R.string.radio_communication_timeout_subtext2));
                    }
                } else if (status == RoverInterface.STATUS_ERROR) {
                    setStatusIcon(RoverStatusState.DISCONNECTED);
                    setStatusText(Color.RED, DISCONNECTED);
                }
            }
        });
    }

    @Override
    public void onReceiverStatus(final RoverInterface.Receiver r) {

        lastStatus = r;

        if (r.version > 0) {
            roverVersion = "Device Firmware\n" + r.version + "." + r.build;
        } else {
            roverVersion = "";
        }

        if (attemptReaquire && r.linkEstablished == 0) {
            attemptReaquire = false;
            Log.d(TAG, "issuing a reaquire command");
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            context,
                            R.string.radio_attempt_reaquire_message,
                            Toast.LENGTH_SHORT)
                            .show();
                }
            });
            radio.reaquire();
            radio.enableCoT(!disableCOT);
        }
        // clear the attempt reaquire flag
        attemptReaquire = false;

        //Log.d(TAG, "current receiver monitoring(" + radio.isMonitoring() + ") status: " + r);
        //Log.d(TAG, "current receiver status: " + r.network_framing);
        if (radio.isMonitoring()) {
            // Log.d(TAG, r.toString());
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    updateFreqButtonText((double) r.frequency / 1000);
                    //Log.d(TAG, "channel: " + r.channel + " module: " + r.moduleType);
                    updateModChanText(r.channel, r.moduleType);
                    moduleType = r.moduleType;
                    channel = r.channel;
                    wfc = r.waveformCategory;
                    nft = r.network_framing;
                    if (r.dataLinkUtilization != 0) {
                        setStatusIcon(RoverStatusState.THREE_BARS);
                        setStatusText(Color.GREEN, CONNECTED + " ("
                                + r.waveformCategory + ")");
                    } else if (r.linkEstablished == 1
                            || r.linkEstablished == 2) {
                        String post = "";
                        if (r.linkEstablished == 2) {
                            post = "\n-check key- ";
                        }

                        if (r.rssi < 40) {
                            setStatusIcon(RoverStatusState.ZERO_BARS);
                            setStatusText(Color.GREEN, CONNECTED + " ("
                                    + r.waveformCategory + ")" + post);
                        } else if (r.rssi < 90) {
                            setStatusIcon(RoverStatusState.ONE_BAR);
                            setStatusText(Color.GREEN, CONNECTED + " ("
                                    + r.waveformCategory + ")" + post);
                        } else if (r.rssi < 165) {
                            setStatusIcon(RoverStatusState.TWO_BARS);
                            setStatusText(Color.GREEN, CONNECTED + " ("
                                    + r.waveformCategory + ")" + post);
                        } else {
                            setStatusIcon(RoverStatusState.THREE_BARS);
                            setStatusText(Color.GREEN, CONNECTED + " ("
                                    + r.waveformCategory + ")" + post);
                        }
                    } else if (r.linkEstablished == 0) {
                        setStatusIcon(RoverStatusState.ZERO_BARS);
                        setStatusText(Color.GREEN, SEARCHING + " ("
                                + r.waveformCategory + ")");
                    }
                }
            });

        }
    }

    /**
     * Computes the closest high frequency for a given range.
     */
    static private int floor(final int frequency) {
        if ((frequency > 15150) && (frequency < 15350))
            return 15350;
        else if ((frequency > 14700) && (frequency < 14835))
            return 14835;
        else if ((frequency > 14400) && (frequency < 14830))
            return 14830;
        else if ((frequency > 5250) && (frequency < 5850))
            return 5850;
        else if ((frequency > 4950) && (frequency < 5250))
            return 4950;
        else if ((frequency > 2500) && (frequency < 4400))
            return 2500;
        else if ((frequency > 1850) && (frequency < 2250))
            return 1850;
        else if ((frequency > 470) && (frequency < 1650))
            return 470;
        else if (frequency < 400)
            return 0;
        else
            return frequency;
    }

    /**
     * Given a 3 or 4 digit frequency code, determine if it is valid.
     */
    static private Frequency validFrequency(final int frequency) {
        if (frequency >= 400 && frequency <= 470) {
            return Frequency.UHF;
        } else if (frequency >= 1625 && frequency <= 1850) {
            return Frequency.LBAND;
        } else if (frequency >= 2200 && frequency <= 2500) {
            return Frequency.SBAND;
        } else if (frequency >= 4400 && frequency <= 4950) {
            return Frequency.CBANDLOW;
        } else if (frequency >= 5250 && frequency <= 5850) {
            return Frequency.CBANDHIGH;
        } else if (frequency >= 14400 && frequency <= 14830) {
            return Frequency.KUBANDLOW;
        } else if (frequency >= 14700 && frequency <= 14835) {
            return Frequency.KUBANDLOW2;
        } else if (frequency >= 15150 && frequency <= 15350) {
            return Frequency.KUBANDUPPER;
        } else
            return Frequency.INVALID;
    }

    @Override
    public void onReceivePresets(final List<String> presets) {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                presets.add(0, context.getString(R.string.no_preset_selected));
                String[] p = new String[presets.size()];
                presets.toArray(p);
                // Log.d(TAG, "onReceivePresets: " + Arrays.toString(p));
                populatePresets(p);
                presetSpinner.setEnabled(true);
            }
        });
    }

    /**
     * set up the preset spinner
     * 
     * @param presets - the list of presets
     */
    private void populatePresets(final String[] presets) {

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                R.layout.spinner_text_view,
                presets);

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
    }

    private void updateModChanText(int channel, String moduleType) {
        if (moduleType != null && !moduleType.equals("NONE")) {
            chanmodBtn.setText(moduleType + " "
                    + String.format(LocaleUtil.getCurrent(), "%03d", channel));
        } else {
            chanmodBtn.setText("M- ---");
        }

    }

    private void updateFreqButtonText(final double freqCode) {
        if (freqCode == 0)
            freqBtn.setText("-.--- GHz");
        else {
            frequencyCode = (int) freqCode;
            DecimalFormat df = LocaleUtil.getDecimalFormat("#.000");
            freqBtn.setText(df.format(freqCode / 1000d) + " GHz");
        }
    }

    /**
     * UI safe mechanism for just setting the status text.
     */
    private void setStatusText(final int color, final String text) {
        setStatusText(color, text, "");
    }

    private void setStatusText(final int color, final String text,
            final String subtext) {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                statusText.setTextColor(color);
                statusText.setText(text);
            }
        });
        notifyRadioStatusListeners(color, text, subtext);
    }

    /**
     * set the status image button icon and status text to reflect the current state
     * 
     * @param state - the icon state to change to
     */
    private void setStatusIcon(final RoverStatusState state) {
        switch (state) {
            case OFF:
                statusImage.setImageResource(R.drawable.rover_reception_off);
                setEnabled(false);
                break;
            case DISCONNECTED:
                statusImage
                        .setImageResource(R.drawable.rover_reception_disabled);
                setEnabled(false);
                break;
            case ZERO_BARS:
                statusImage
                        .setImageResource(R.drawable.rover_reception_enabled);
                setEnabled(true);
                break;
            case ONE_BAR:
                statusImage
                        .setImageResource(R.drawable.rover_reception_enabled_1);
                setEnabled(true);
                break;
            case TWO_BARS:
                statusImage
                        .setImageResource(R.drawable.rover_reception_enabled_2);
                setEnabled(true);
                break;
            case THREE_BARS:
                statusImage
                        .setImageResource(R.drawable.rover_reception_enabled_3);
                setEnabled(true);
                break;
            default:
                break;
        }

    }

    View getView() {
        return _layout;
    }

    public void close() {
        closeDropDown();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v)
            radio.videoRequested(false);
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        radio.videoRequested(false);
    }

    @Override
    public void disposeImpl() {
        if (radio != null) {
            radio.videoRequested(false);
            radio.stopListening();
            radio.stopMonitoring();
        }
        stopNetworkChecking();
    }

    /**
     * Given a string, attempt to obtain the a positive number. If the number is 
     * not positive or if the string is not a number returns the default value.
     */
    private int getPosInt(final String value, final int def) {
        try {
            final int retval = Integer.parseInt(value);

            if (retval < 0)
                return def;

            return retval;

        } catch (NumberFormatException nfe) {
            return def;
        }

    }

    @Override
    public boolean onLongClick(final View v) {
        if (v.getId() == R.id.watchRover_btn) {
            watchVideo(true);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onClick(final View v) {
        if (v.getId() == R.id.fav_btn) {
            ru.commit(frequencyCode, moduleType, channel);
        } else if (v.getId() == R.id.nc_btn) {

            if (radio != null)
                ru.showDialog(radio, v, context);
            else
                Toast.makeText(context,
                        R.string.radio_no_connection_established_yet,
                        Toast.LENGTH_SHORT)
                        .show();

        } else if (v.getId() == R.id.chan_mod_btn) {

            createChanModDialog();

        } else if (v.getId() == R.id.freq_btn) {

            // Display a dialog for the user to enter a frequency
            final EditText input = new EditText(context);
            input.setRawInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setFilters(new InputFilter[] {
                    new InputFilter.LengthFilter(6)
            });
            AlertDialog.Builder ad = new AlertDialog.Builder(context);
            ad.setMessage(R.string.radio_enter_frequency_code);
            if (frequencyCode != 0)
                input.setText(String.valueOf(frequencyCode));
            ad.setView(input);
            input.selectAll();

            ad.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                int whichButton) {
                            String inputText = input.getText().toString()
                                    .trim();
                            try {
                                double freq = Double.parseDouble(inputText);
                                // if the freq was entered in GHz convert it to MHz
                                if (freq >= 1.6 && freq <= 16)
                                    freq *= 1000d;

                                // if the freq was entered in MHz save it as is
                                if (validFrequency(
                                        (int) freq) != Frequency.INVALID) {
                                    radio.setReceiverFrequency(
                                            (int) (freq * 1000));
                                } else {
                                    Log.d(TAG, INVALID_FREQ);
                                    Toast.makeText(context, INVALID_FREQ,
                                            Toast.LENGTH_LONG)
                                            .show();
                                }

                            } catch (NumberFormatException nfe) {
                                Log.d(TAG, INVALID_FREQ);
                                Toast.makeText(context, INVALID_FREQ,
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        }
                    });
            ad.setNegativeButton(R.string.cancel, null);
            AlertDialog dialog = ad.create();
            dialog.getWindow()
                    .setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            dialog.show();
        } else if (v.getId() == R.id.scanFreq_btn) {
            // start scan dialog
            createScanDialog();
        } else if (v.getId() == R.id.nextFreq_btn) {
            if (wfc != null)
                radio.scanFrequency(frequencyCode * 1000 + 1000,
                        endFrequencyCode * 1000, 1000, wfc);
            else
                Toast.makeText(context, R.string.radio_no_scan_in_progress,
                        Toast.LENGTH_LONG).show();
        } else if (v.getId() == R.id.watchRover_btn) {
            // open the video player to the rover URL
            watchVideo(false);
        } else if (v.getId() == R.id.mode_btn) {
            sharedPreferences.edit().putBoolean("mode_rover",
                    !sharedPreferences.getBoolean("mode_rover", true)).apply();
            setRoverMode();
        }
    }

    /**
     *  visually flips the mode from rover to ddl
     */
    void setRoverMode() {

        if (sharedPreferences.getBoolean("mode_rover", true)) {
            freqBtn.setVisibility((View.VISIBLE));
            chanmodBtn.setVisibility(View.GONE);
            scanFreqBtn.setVisibility(View.VISIBLE);
            nextFreqBtn.setVisibility(View.VISIBLE);
            modeBtn.setText("ROVER");
        } else {
            freqBtn.setVisibility((View.GONE));
            chanmodBtn.setVisibility(View.VISIBLE);
            scanFreqBtn.setVisibility(View.GONE);
            nextFreqBtn.setVisibility(View.GONE);
            modeBtn.setText(" DDL ");
        }

    }

    void config() {
        // open a dialog to edit the rover stream
        AlertDialog.Builder ad = new AlertDialog.Builder(context);
        ad.setTitle(context.getString(R.string.rover)
                + context.getString(R.string.radio_configuration));

        LayoutInflater inflater = LayoutInflater.from(context);
        final View urlView = inflater
                .inflate(R.layout.isrv_video_url, null);

        final TextView roverVersionTV = urlView
                .findViewById(R.id.rover_version);
        if (radio.isMonitoring()) {
            String lastVer = sharedPreferences.getString("rover.Version", "");
            if (roverVersion.length() != 0) {
                if (!roverVersion.equals(lastVer))
                    Toast.makeText(
                            context,
                            R.string.radio_changed_firmware_device_message,
                            Toast.LENGTH_LONG).show();
                sharedPreferences.edit()
                        .putString("rover.Version", roverVersion).apply();
            }

            roverVersionTV.setText(roverVersion);
        } else {
            roverVersionTV.setText("");
        }

        final CheckBox autoRoverCB = urlView
                .findViewById(R.id.autoRoverAddress);
        autoRoverCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                sharedPreferences.edit()
                        .putBoolean("autoRoverAddress", isChecked).apply();
                sharedPreferences.edit()
                        .putBoolean("autoRoverAddress", isChecked).apply();
                final View manualRoverAddress = urlView
                        .findViewById(R.id.manualRoverIP);
                if (isChecked) {
                    manualRoverAddress.setVisibility(View.GONE);
                } else {
                    manualRoverAddress.setVisibility(View.VISIBLE);
                }
            }
        });

        //autoRoverCB.setChecked(sharedPreferences.getBoolean("autoRoverAddress", true));

        /** 
         * SHB / BH
         * currently disable any automated detection
         *
         * If enabled and the rover correctly announced itself on ui.com / rover.com, this 
         * could potentially allow for automatic discovery of the device address.
         * This is currently not able to be done so the visibility of this checkbox is set
         * to gone and the autoRoverCB is set to false.
         */
        autoRoverCB.setChecked(false);
        autoRoverCB.setVisibility(View.GONE);

        final EditText roverIPET = urlView
                .findViewById(R.id.roverIP_et);
        final String roverIP = sharedPreferences.getString("rover_ip_address",
                "192.168.80.1");
        roverIPET.setText(roverIP);

        urlView.findViewById(R.id.rover_common_btn)
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        roverIPET.setText("192.168.80.1");
                        sharedPreferences.edit()
                                .putString("rover_ip_address", "192.168.80.1")
                                .apply();

                    }
                });
        urlView.findViewById(R.id.rover_alt_btn)
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        roverIPET.setText("192.168.50.1");
                        sharedPreferences.edit()
                                .putString("rover_ip_address", "192.168.50.1")
                                .apply();
                    }
                });
        urlView.findViewById(R.id.rover_alt1_btn)
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        roverIPET.setText("192.168.80.1");
                        sharedPreferences.edit()
                                .putString("rover_ip_address", "192.168.80.1")
                                .apply();
                    }
                });

        final CheckBox legacy = urlView
                .findViewById(R.id.sirlegacy);
        legacy.setChecked(sharedPreferences.getBoolean("rover_legacy", false));

        legacy.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {

                sharedPreferences.edit()
                        .putBoolean("rover_legacy", isChecked).apply();
            }
        });

        final CheckBox legacyswap = urlView
                .findViewById(R.id.sirlegacyswap);
        legacyswap.setChecked(sharedPreferences.getBoolean("rover_legacy_swap",
                false));

        legacyswap.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {

                sharedPreferences.edit()
                        .putBoolean("rover_legacy_swap", isChecked).apply();
            }
        });

        final EditText roverUrlAET = urlView
                .findViewById(R.id.roverUrlA_et);
        final EditText roverPortAET = urlView
                .findViewById(R.id.roverPortA_et);
        final EditText roverUrlDET = urlView
                .findViewById(R.id.roverUrlD_et);
        final EditText roverPortDET = urlView
                .findViewById(R.id.roverPortD_et);
        final CheckBox roversidebandCB = urlView
                .findViewById(R.id.roversideband_cb);
        final EditText roversidebandportET = urlView
                .findViewById(R.id.roversideband_port_et);
        final CheckBox roverignoreKLVCB = urlView
                .findViewById(R.id.ignoreklv_cb);
        final CheckBox roverdisableCOTCB = urlView
                .findViewById(R.id.disablecot_cb);
        final CheckBox halfSizeCB = urlView
                .findViewById(R.id.halfSize_cb);
        final CheckBox mpeg2CB = urlView
                .findViewById(R.id.mpeg2_cb);
        final CheckBox roverWifiCB = urlView
                .findViewById(R.id.roverWifi_cb);
        final EditText roverBitRateET = urlView
                .findViewById(R.id.roverBitRate);

        halfSizeCB.setChecked(sharedPreferences.getBoolean(
                "roverEncodingHalfSize", false));

        halfSizeCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                sharedPreferences.edit()
                        .putBoolean("roverEncodingHalfSize", isChecked).apply();
            }
        });

        mpeg2CB.setChecked(sharedPreferences.getBoolean("roverEncodingMpeg2",
                false));

        mpeg2CB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                sharedPreferences.edit()
                        .putBoolean("roverEncodingMpeg2", isChecked).apply();
            }
        });

        roverWifiCB.setChecked(sharedPreferences.getBoolean("roverWifi",
                false));

        roverWifiCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                sharedPreferences.edit()
                        .putBoolean("roverWifi", isChecked).apply();
                if (isChecked) {
                    Toast.makeText(
                            context,
                            R.string.radio_rover_over_wifi_checked_message,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        final Button reset = urlView
                .findViewById(R.id.reset);

        reset.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                roverUrlAET.setText("239.255.0.1");
                roverPortAET.setText("1841");
                roverUrlDET.setText("239.255.0.1");
                roverPortDET.setText("11841");
                roversidebandCB.setChecked(false);
                roversidebandportET.setText("3002");
                roverignoreKLVCB.setChecked(false);
                roverdisableCOTCB.setChecked(true);
                roverIPET.setText("192.168.80.1");
                roverBitRateET.setText("1500");
                halfSizeCB.setChecked(false);
                legacy.setChecked(false);
                legacyswap.setChecked(false);
                autoRoverCB.setChecked(true);
            }
        });

        final Button b = urlView
                .findViewById(R.id.rover_test_btn);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (radio != null) {

                    Toast.makeText(
                            context,
                            R.string.radio_starting_internal_test_video,
                            Toast.LENGTH_SHORT).show();
                    radio.enableTestVideo(true);
                }
            }
        });

        final Button web = urlView
                .findViewById(R.id.webAdmin_btn);
        web.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                // start web admin intent

                final String roverIP = sharedPreferences.getString(
                        "rover_ip_address",
                        "192.168.80.1");

                String address = "http://" + roverIP;

                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(address));

                try {
                    context.startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(
                            context,
                            "no web browser application found",
                            Toast.LENGTH_SHORT).show();
                }

            }
        });

        final Button ping = urlView
                .findViewById(R.id.rover_ping_btn);
        ping.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                final String roverIP = sharedPreferences.getString(
                        "rover_ip_address",
                        "192.168.80.1");

                Thread t = new Thread(TAG + "-Ping") {
                    @Override
                    public void run() {
                        urlView.post(new Runnable() {
                            @Override
                            public void run() {
                                ping.setEnabled(false);
                                ping.setText("in progress");
                            }
                        });
                        NetworkInterface ni = getEthInterface();
                        InetAddress inetAddress = null;
                        try {
                            inetAddress = toInetAddress(
                                    urlView, roverIP);
                        } catch (Exception ignored) {
                        }
                        if (radio.testConnection(ni, inetAddress, roverIP)) {
                            toast(urlView,
                                    "Success: able to contact the Rover");
                        } else {
                            toast(urlView,
                                    "Error: unable to contact the Rover");
                        }
                        radio.testConnection(ni, inetAddress, roverIP);
                        urlView.post(new Runnable() {
                            @Override
                            public void run() {
                                ping.setEnabled(true);
                                ping.setText("Ping");
                            }
                        });
                    }
                };
                t.start();
            }
        });

        final Button rec = urlView
                .findViewById(R.id.raw_record_btn);
        rec.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {

                final File f = new File(VideoBrowserDropDownReceiver.VIDEO_DIR);
                if (!IOProviderFactory.mkdirs(f)) {
                    Log.e(TAG, "Failed to make dir at " + f.getAbsolutePath());
                }
                File file = new File(f, "raw_"
                        + recDateFormatter
                                .format(new CoordinatedTime().getMilliseconds())
                        + ".ts");
                String address = sharedPreferences.getString(
                        "rover_ip_analog", "239.255.0.1");
                int port = sharedPreferences.getInt("rover_port_analog",
                        1841);
                if (wfc != null
                        && wfc.equalsIgnoreCase(RoverInterface.ANALOG)) {
                } else if (nft != null
                        && nft.equalsIgnoreCase(RoverInterface.NONE)) {
                } else {
                    address = sharedPreferences.getString(
                            "rover_ip_digital", "239.255.0.1");
                    port = sharedPreferences.getInt("rover_port_digital",
                            11841);
                }

                NetworkInterface ni = getEthInterface();
                if (ni == null) {
                    try {
                        ni = NetworkInterface.getByName("wlan0");
                    } catch (Exception ignored) {
                    }
                }

                TrafficRecorder tf = new TrafficRecorder(address, port,
                        ni,
                        file, getMapView());
                Thread t = new Thread(tf, "RawVideoRecordingThread");
                t.start();

            }
        });

        if (radio != null) {
            b.setEnabled(radio.isMonitoring());
        } else {
            b.setEnabled(false);
        }

        roversidebandCB
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (isChecked) {
                            roversidebandCB.setText(context
                                    .getString(R.string.rover)
                                    + " "
                                    + context.getString(R.string.klv)
                                    + context.getString(R.string.radio_port));
                            roversidebandportET.setVisibility(View.VISIBLE);
                        } else {
                            roversidebandCB.setText(context
                                    .getString(R.string.rover)
                                    + context
                                            .getString(R.string.radio_sideband)
                                    + context.getString(R.string.klv));
                            roversidebandportET.setVisibility(View.GONE);
                        }
                    }
                });

        final String ipA = sharedPreferences.getString(PREF_KEY_ANALOG_IP,
                "239.255.0.1");
        final int portA = sharedPreferences.getInt(PREF_KEY_ANALOG_PORT,
                1841);

        final String ipD = sharedPreferences.getString(PREF_KEY_DIG_IP,
                "239.255.0.1");
        final int portD = sharedPreferences.getInt(PREF_KEY_DIG_PORT,
                11841);

        roverIPET.setText(roverIP);

        sideband = sharedPreferences.getBoolean("rover_sideband", false);
        sideband_port = sharedPreferences.getInt("rover_sideband_port",
                3002);

        roverUrlAET.setText(ipA);
        roverPortAET.setText(String.valueOf(portA));

        roverUrlDET.setText(ipD);
        roverPortDET.setText(String.valueOf(portD));

        roverBitRateET.setText(sharedPreferences.getInt("roverBitRate", 1500)
                + "");

        roversidebandCB.setChecked(sideband);
        roverignoreKLVCB.setChecked(ignoreKLV);
        roverdisableCOTCB.setChecked(disableCOT);

        if (sideband) {
            roversidebandCB.setText(context.getString(
                    R.string.rover)
                    + " "
                    + context.getString(R.string.klv)
                    + context.getString(R.string.radio_port));
            roversidebandportET.setVisibility(View.VISIBLE);
        } else {
            roversidebandCB.setText(context.getString(
                    R.string.rover)
                    + context.getString(R.string.radio_sideband)
                    + context.getString(R.string.klv));
            roversidebandportET.setVisibility(View.GONE);
        }

        roversidebandportET.setText("" + sideband_port);
        ad.setView(urlView);

        ad.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int whichButton) {

                        final String roverIP = roverIPET.getText().toString();
                        sharedPreferences.edit()
                                .putString("rover_ip_address", roverIP)
                                .apply();

                        if (roverIP.startsWith("192.168.50")) {
                            sharedPreferences
                                    .edit()
                                    .putString("network_static_ip_address",
                                            "192.168.50.200")
                                    .apply();
                            sharedPreferences
                                    .edit()
                                    .putString("network_static_gateway",
                                            "")
                                    .apply();

                        } else if (roverIP.startsWith("192.168.80")) {
                            sharedPreferences
                                    .edit()
                                    .putString("network_static_ip_address",
                                            "192.168.80.200")
                                    .apply();
                            sharedPreferences
                                    .edit()
                                    .putString("network_static_gateway",
                                            "")
                                    .apply();
                        } else {
                            Toast.makeText(
                                    context,
                                    R.string.radio_verify_network_addressing,
                                    Toast.LENGTH_SHORT).show();
                        }

                        final String ip_analog = roverUrlAET.getText()
                                .toString();
                        final String port_analog = roverPortAET.getText()
                                .toString();

                        final String ip_digital = roverUrlDET.getText()
                                .toString();
                        final String port_digital = roverPortDET.getText()
                                .toString();

                        sideband = roversidebandCB.isChecked();
                        final String sideband_port_string = roversidebandportET
                                .getText()
                                .toString();
                        ignoreKLV = roverignoreKLVCB.isChecked();

                        disableCOT = roverdisableCOTCB.isChecked();

                        sharedPreferences.edit()
                                .putBoolean("rover_sideband", sideband).apply();

                        sharedPreferences
                                .edit()
                                .putInt("roverBitRate",
                                        Math.min(4000, Math.max(
                                                500,
                                                getPosInt(roverBitRateET
                                                        .getText()
                                                        .toString(), 1500))))
                                .apply();

                        sideband_port = getPosInt(sideband_port_string, 3002);

                        sharedPreferences.edit()
                                .putInt("rover_sideband_port", sideband_port)
                                .apply();

                        String url = createURL(ip_analog, port_analog);

                        if (url != null) {
                            sharedPreferences.edit()
                                    .putString(PREF_KEY_ANALOG_IP, ip_analog)
                                    .apply();

                            sharedPreferences.edit()
                                    .putInt(PREF_KEY_ANALOG_PORT,
                                            getPosInt(port_analog, 1841))
                                    .apply();
                        } else {
                            Toast.makeText(context,
                                    R.string.radio_invalid_analog_ip_or_port,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        url = createURL(ip_digital, port_digital);

                        if (url != null) {
                            sharedPreferences.edit()
                                    .putString(PREF_KEY_DIG_IP, ip_digital)
                                    .apply();

                            sharedPreferences.edit().putInt(PREF_KEY_DIG_PORT,
                                    getPosInt(port_digital, 11841))
                                    .apply();
                        } else {
                            Toast.makeText(context,
                                    R.string.radio_invalid_digital_ip_or_port,
                                    Toast.LENGTH_SHORT).show();
                        }

                        //Toast.makeText(context, "configuration saved", Toast.LENGTH_SHORT)
                        //            .show();
                    }
                });
        ad.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = ad.create();
        dialog.show();
    }

    private void createChanModDialog() {
        LayoutInflater li = ((Activity) context).getLayoutInflater();
        final View chanmodView = li.inflate(R.layout.isrv_chanmod, null);
        AlertDialog.Builder ad = new AlertDialog.Builder(context);
        ad.setTitle(R.string.radio_ddl_tuner);
        ad.setView(chanmodView);

        final Spinner moduleSpinner = chanmodView
                .findViewById(R.id.module_spin);

        moduleSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view,
                            int position, long id) {

                        if (view instanceof TextView)
                            ((TextView) view).setTextColor(Color.WHITE);
                    }
                });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                R.layout.spinner_text_view,
                modules);

        final EditText channelText = chanmodView
                .findViewById(R.id.channel_et);
        channelText.setRawInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        channelText.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(3)
        });

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        moduleSpinner.setAdapter(adapter);
        if (moduleType != null) {
            moduleSpinner.setSelection(Arrays.asList(modules).indexOf(
                    moduleType));
            channelText.setText(Integer.toString(channel));
            channelText.selectAll();
        }

        ad.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        try {
                            int channel = Integer.parseInt(channelText
                                    .getText()
                                    .toString());
                            String mt = moduleSpinner.getSelectedItem()
                                    .toString();
                            radio.setChannel(mt, channel);
                        } catch (Exception e) {
                            Log.d(TAG,
                                    "error obtaining the module type and channel",
                                    e);
                        }

                    }
                });

        ad.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = ad.create();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    /**
     * create the dialog for setting up a frequency scan L-Band 1625Mhz - 1850Mhz S-Band 2200Mhz -
     * 2500Mhz C-Band 4400Mhz - 4950Mhz C-Band 5250Mhz - 5850Mhz
     * Ku-Band Lower 14.4Ghz - 14.83Ghz 
     * Ku-Band Lower 14.7-14.835 
     * Ku-Band Upper 15.15Ghz - 15.35Ghz 
     */
    private void createScanDialog() {
        LayoutInflater li = ((Activity) context).getLayoutInflater();
        final View freqView = li.inflate(R.layout.isrv_freq_scan, null);

        final EditText startFreq = freqView
                .findViewById(R.id.roverStartFreq_et);
        final EditText endFreq = freqView
                .findViewById(R.id.roverEndFreq_et);
        if (frequencyCode != 0)
            startFreq.setText(String.valueOf(frequencyCode));
        startFreq.selectAll();

        if (endFrequencyCode == 0) {
            endFreq.setText(String.valueOf(floor(frequencyCode + 50)));
        } else {
            endFreq.setText(String.valueOf(endFrequencyCode));
        }

        // set up waveform spinner
        final Spinner waveformSpinner = freqView
                .findViewById(R.id.waveform_spin);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                R.layout.spinner_text_view,
                waveforms);
        waveformSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view,
                            int position, long id) {

                        if (view instanceof TextView)
                            ((TextView) view).setTextColor(Color.WHITE);
                    }
                });

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        waveformSpinner.setAdapter(adapter);
        if (wfc != null) {
            waveformSpinner.setSelection(Arrays.asList(waveforms).indexOf(
                    wfc));
        }

        // assign band buttons
        Button sBand = freqView.findViewById(R.id.sBand_btn);
        Button lBand = freqView.findViewById(R.id.lBand_btn);
        Button cBandHigh = freqView.findViewById(R.id.cBandHigh_btn);
        Button cBandLow = freqView.findViewById(R.id.cBandLow_btn);
        Button kuBandLow = freqView.findViewById(R.id.kuBandLow_btn);
        Button kuBandLow2 = freqView.findViewById(R.id.kuBandLow2_btn);
        Button kuBandUpper = freqView
                .findViewById(R.id.kuBandUpper_btn);

        sBand.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("2200");
                endFreq.setText("2500");
            }
        });

        lBand.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("1625");
                endFreq.setText("1850");
            }
        });

        cBandHigh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("5250");
                endFreq.setText("5850");
            }
        });

        cBandLow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("4400");
                endFreq.setText("4950");
            }
        });

        cBandLow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("4400");
                endFreq.setText("4950");
            }
        });

        kuBandLow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("14400");
                endFreq.setText("14830");
            }
        });

        kuBandLow2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("14700");
                endFreq.setText("14835");
            }
        });
        kuBandUpper.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startFreq.setText("15150");
                endFreq.setText("15350");
            }
        });

        AlertDialog.Builder ad = new AlertDialog.Builder(context);
        ad.setTitle(R.string.radio_scan_frequency_range);
        ad.setView(freqView);

        ad.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        // get waveform
                        if (startFreq.getText().length() > 0) {
                            int start = Integer.parseInt(startFreq.getText()
                                    .toString());
                            int end = Integer.parseInt(endFreq.getText()
                                    .toString());

                            if ((validFrequency(start) != Frequency.INVALID)
                                    &&
                                    (validFrequency(end) != Frequency.INVALID)
                                    &&
                                    (validFrequency(start) == validFrequency(
                                            end))) {
                                wfc = waveformSpinner.getSelectedItem()
                                        .toString();
                                radio.scanFrequency(start * 1000, end * 1000,
                                        1000, wfc);
                                String response = String.format(
                                        context.getString(
                                                R.string.radio_background_scanning_frequencies),
                                        start, end, wfc);
                                Log.d(TAG, response);
                                Toast.makeText(context, response,
                                        Toast.LENGTH_SHORT)
                                        .show();
                                endFrequencyCode = end;
                            } else {
                                Toast.makeText(context, INVALID_FREQ,
                                        Toast.LENGTH_SHORT)
                                        .show();
                            }
                        } else {
                            Toast.makeText(context, INVALID_FREQ,
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                });
        ad.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = ad.create();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    /**
     * validate and save the rover video stream IP and port (if they are both valid)
     * 
     * @param ip - multicast address of the video
     * @param port - video port
     * @return - url if the ip and port are valid, null if not.
     */
    private static String createURL(final String ip, final String port) {
        int pv = Integer.MAX_VALUE;
        try {
            if (!port.equals(""))
                pv = Integer.parseInt(port);

            if (pv > AddEditAlias.MAX_PORT_VAL || pv < 0)
                return null;

        } catch (NumberFormatException nfe) {
            return null;
        }

        if (ip == null || ip.length() == 0 || ip.equals("127.0.0.1")) {
            // valid unicast cases
            return "udp://" + ip + ":" + port;
        } else {
            Pattern pattern = Pattern.compile(AddEditAlias.MCAST_IP_PATTERN);
            // tack on an additional : just in case it is just
            // the IP and nothing else.
            Matcher matcher = pattern.matcher(ip + ":");
            if (!matcher.find()) {
                return null;
            } else {
                return "udp://" + ip + ":" + port;
            }
        }
    }

    /**
     * Broadcasts the video intent to watch a video.
     * @param external true will launch the external video player.
     */
    public void watchVideo(boolean external) {

        ConnectionEntry ce;
        boolean swap = sharedPreferences.getBoolean("rover_legacy_swap", false);

        Log.d(TAG, "user selected swap: " + swap + " rover wfc: " + wfc
                + " rover nft: " + nft);
        Log.d(TAG, "last status from rover: " + lastStatus);

        String connection = "";
        if (!chanmodBtn.getText().toString().endsWith("-")) {
            connection = chanmodBtn.getText().toString();
        } else {
            connection = freqBtn.getText().toString();
        }
        final String label = wfc + " (" + connection + ")";

        if (wfc != null && wfc.equalsIgnoreCase(RoverInterface.ANALOG)) {

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    // this hits a sweet spot of parameter values
                    // for use on the Android devices.
                    radio.setEncodingParam(
                            Math.min(4000, Math.max(500, sharedPreferences
                                    .getInt("roverBitRate", 1500))) * 1000,
                            sharedPreferences.getBoolean(
                                    "roverEncodingHalfSize", false),
                            sharedPreferences.getBoolean("roverEncodingMpeg2",
                                    false));
                }
            }, TAG + "-WatchVideo");

            t.start();

            Log.d(TAG, "connecting to[" + wfc + "]: "
                    + ((swap) ? urlDigital : urlAnalog));
            ce = StreamManagementUtils
                    .createConnectionEntryFromUrl(label, (swap) ? urlDigital
                            : urlAnalog);
            Toast.makeText(
                    context,
                    R.string.radio_issuing_encoding_parameters,
                    Toast.LENGTH_SHORT)
                    .show();
        } else if (wfc != null && wfc.equalsIgnoreCase(RoverInterface.DDL)) {
            Log.d(TAG, "connecting to[" + wfc + ":" + nft + "]: " + urlDigital);
            ce = StreamManagementUtils
                    .createConnectionEntryFromUrl(label, urlDigital);
            Toast.makeText(context,
                    R.string.radio_initiating_digital_connection,
                    Toast.LENGTH_SHORT)
                    .show();
        } else if (nft != null && nft.equalsIgnoreCase(RoverInterface.NONE)) {
            Log.d(TAG, "connecting to[" + nft + "]: " + urlAnalog);
            ce = StreamManagementUtils
                    .createConnectionEntryFromUrl(label, urlAnalog);
            Toast.makeText(context,
                    R.string.radio_initiating_analog_connection,
                    Toast.LENGTH_SHORT)
                    .show();
        } else {
            Log.d(TAG, "connecting to[" + wfc + ":" + nft + "]: " + urlDigital);
            ce = StreamManagementUtils
                    .createConnectionEntryFromUrl(label, urlDigital);
            Toast.makeText(context,
                    R.string.radio_initiating_digital_connection,
                    Toast.LENGTH_SHORT)
                    .show();
        }

        if (ce == null) {
            return;
        }

        // the sideband is for the metadata translated down port 3002 usually from a L3 Communication 
        // product.
        Log.d(TAG, "sideband metadata enabled: " + sideband);
        if (sideband) {
            Log.d(TAG, "sideband metadata port: " + sideband_port);
            ce.setRoverPort(sideband_port);
        }

        NetworkDevice nd = getNetworkMapDevice();
        if (nd != null && nd.getInterface() != null)
            ce.setPreferredInterfaceAddress(
                    NetworkManagerLite.getAddress(nd.getInterface()));
        else {
            NetworkInterface eth = getEthInterface();
            try {
                if (eth != null && eth.isUp()) {
                    ce.setPreferredInterfaceAddress(
                            NetworkManagerLite.getAddress(eth));
                }
            } catch (IOException ioe) {
                Log.d(TAG, "error occurred getting the address");
            }
        }

        ce.setIgnoreEmbeddedKLV(ignoreKLV);

        radio.videoRequested(true);

        Intent i = new Intent("com.atakmap.maps.video.DISPLAY");
        i.putExtra("CONNECTION_ENTRY", ce);
        if (external) {
            i.putExtra("standalone", true);
        }
        i.putExtra("cancelClose", "true");
        AtakBroadcast.getInstance().sendBroadcast(i);

    }

    /**
     * Is the rover described as a network mapped device.
     */
    private NetworkDevice getNetworkMapDevice() {
        List<NetworkDevice> devices = NetworkManagerLite.getNetworkDevices();
        NetworkDevice retval = null;

        // An individual may have accidentally configured more than one rover.   There are
        // two cases - either no rover is plugged in meaning all are down, in this case
        // we will return the last known rover device so that ATAK does not try to 
        // configure one on it's very own.
        // 
        // Second case, one of them is up, so just return the one that is up.

        for (NetworkDevice nd : devices) {
            if (nd.isSupported(NetworkDevice.Type.ISRV_SIR) ||
                    nd.isSupported(NetworkDevice.Type.ISRV_ROVER6) ||
                    nd.isSupported(NetworkDevice.Type.ISRV_VORTEX) ||
                    nd.isSupported(NetworkDevice.Type.ISRV_TRE)) {
                Log.d(TAG, "found ISRV entry in network.map file: " + nd);
                retval = nd;

                // short circuit the search
                // we found one that is plugged in, no matter the state return that one.
                if (nd.getInterface() != null)
                    return nd;
            }
        }
        // return the last found rover
        return retval;
    }

    /**
     * Responsible for retrieving only the eth interface so the ISRVToolbarExtension can attempt to
     * use it directly.
     */
    private NetworkInterface getEthInterface() {

        NetworkDevice nd = getNetworkMapDevice();
        if (nd != null) {
            NetworkInterface ni = nd.getInterface();
            if (ni != null) {
                return ni;
            } else {
                // if the NetworkDevice is described in the network.map file - 
                // but does not exist do not attempt to configure it
                Log.d(TAG,
                        "network monitor currently describes the radio and the radio is missing");
                return null;
            }
        }

        /**
         * older way of grabbing the first network interface.
         */
        NetworkInterface eth = null;

        try {
            Enumeration<NetworkInterface> nets = NetworkInterface
                    .getNetworkInterfaces();
            if (nets != null) {
                for (NetworkInterface netint : Collections.list(nets)) {
                    if (netint.getName().startsWith("eth")) {
                        eth = netint;
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "error: ", e);
        }

        return eth;
    }

    private void showInvalidStaticNetworkSetting(String setting) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.radio_static_network_settings);
        builder.setMessage(String
                .format(
                        context.getString(
                                R.string.radio_static_network_settings_invalid_message),
                        setting));
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    private void stop() {
        if (radio != null) {
            radio.stopListening();
            radio.stopMonitoring();
            stopNetworkChecking();
            setStatusIcon(RoverStatusState.OFF);
            setStatusText(Color.WHITE, OFF);
            lastStatus = null;
        }
    }

    private void start() {

        stop();

        radio.setRadioListener(this);

        radio.initialize(context);

        if (!radio.requiresNetworkConfiguration() ||
                sharedPreferences.getBoolean("roverWifi", false)) {
            setStatusText(Color.WHITE, context.getString(R.string.radio_using)
                    + bestDisplayableIP());
            setStatusIcon(RoverStatusState.ZERO_BARS);
            radio.startListening("any");
            radio.startMonitoring();

        } else if (configureNetwork()) {
            setStatusText(Color.WHITE, "" + bestDisplayableIP());
            NetworkInterface ni = getEthInterface();
            setStatusIcon(RoverStatusState.ZERO_BARS);
            setStatusText(Color.WHITE,
                    context.getString(R.string.radio_connecting_ellipses));

            if (ni != null) {
                radio.startListening(NetworkManagerLite.getAddress(ni));
            } else {
                radio.startListening("any");
            }

            radio.startMonitoring();

        } else {
            setStatusText(
                    Color.RED,
                    context.getString(
                            R.string.radio_error_configuring_ethernet));
        }

    }

    /**
     * Very simple thread that polls every 5 seconds to see if the 
     * network is down, and if it is down attempts to bring it back up.
     */

    public class NetworkCheckerRunnable implements Runnable {
        private final String name;
        private final String address;
        private final String subnet;
        private final String gateway;
        private final int polling;

        private boolean cancelled = false;

        /**
         * Provided a network configuration, attempt to configure the 
         * network if the network goes down.
         * @param name the network interface name
         * @param address the network address (null for DHCP)
         * @param subnet the network subnet
         * @param gateway the gateway
         * @param polling the time in milliseconds to poll.
         */
        public NetworkCheckerRunnable(final String name,
                final String address,
                final String subnet,
                final String gateway,
                int polling) {
            this.name = name;
            this.address = address;
            this.subnet = subnet;
            this.gateway = gateway;
            this.polling = polling;

        }

        public void cancel() {
            cancelled = true;
        }

        @Override
        public void run() {

            NetworkInterface eth = null;

            while (!cancelled) {
                try {
                    eth = NetworkInterface.getByName(name);
                } catch (IOException e) {
                    Log.d(TAG, "error occurred finding interface " + name);
                    eth = null;
                }
                try {
                    Notification.Builder builder = NotificationUtil
                            .getInstance()
                            .getNotificationBuilder(NOTIFY_ID);
                    if (builder == null) {
                        NotificationUtil
                                .getInstance()
                                .postNotification(
                                        NOTIFY_ID,
                                        R.drawable.rover_nw_internal,
                                        NotificationUtil.WHITE,
                                        context.getString(
                                                R.string.rover)
                                                + context
                                                        .getString(
                                                                R.string._network),
                                        null,
                                        context.getString(
                                                R.string.radio_physical_connection_controlled_internally),
                                        null, false);
                        builder = NotificationUtil.getInstance()
                                .getNotificationBuilder(NOTIFY_ID);
                    }
                    if (eth == null) {
                        builder.setSmallIcon(R.drawable.rover_nw_inactive);
                        builder.setContentTitle(context
                                .getString(R.string.rover)
                                + context
                                        .getString(
                                                R.string.radio_network_missing));
                        builder.setContentText(String.format(
                                context.getString(
                                        R.string.radio_physical_connection_between_rover_and_eud_disappeared),
                                context.getString(R.string.rover)));

                        setStatusText(
                                Color.RED,
                                context.getString(
                                        R.string.radio_physical_connection_disappeared));
                    } else if (!eth.isUp()) {
                        builder.setSmallIcon(R.drawable.rover_nw_configure);
                        builder.setContentTitle(context
                                .getString(R.string.rover)
                                + context
                                        .getString(
                                                R.string.radio_network_problem));
                        builder.setContentText(String.format(
                                context.getString(
                                        R.string.radio_trying_to_configure_connection),
                                context.getString(R.string.rover),
                                (address != null) ? address
                                        : "dhcp"));
                        setStatusText(
                                Color.RED,
                                context.getString(
                                        R.string.radio_error_network_down));

                        if (address != null) {
                            // set ip, subnet mask, and default gateway
                            NetworkManagerLite.configure(name,
                                    address,
                                    subnet,
                                    gateway, true, null);
                        } else {
                            NetworkManagerLite.configure(name);
                        }
                    } else {
                        Log.d(TAG, "network health ok for: " + name);
                        builder.setSmallIcon(R.drawable.rover_nw_active);
                        builder.setContentTitle(context
                                .getString(R.string.rover)
                                + context
                                        .getString(
                                                R.string.radio_network_healthy));
                        builder.setContentText(String.format(
                                context.getString(
                                        R.string.radio_rover_using_network_interface_with_address),
                                context.getString(R.string.rover),
                                name,
                                NetworkManagerLite.getAddress(eth)));

                    }
                    NotificationUtil.getInstance().postNotification(
                            NOTIFY_ID, builder.build(), false);
                } catch (IOException ioe) {
                    Log.d(TAG, "error occurred determining if " + name
                            + " is up");
                }
                try {
                    Thread.sleep(polling);
                } catch (InterruptedException ignored) {
                }
            }

            Log.d(TAG, "monitoring network health finished for: " + name);
            NotificationUtil.getInstance().clearNotification(NOTIFY_ID);
        }
    }

    private NetworkCheckerRunnable networkChecker;

    /**
     * For the record, internal management of the first network interface by ATAK is 
     * not a great idea.   Having an external application monitor the network makes a 
     * lot more sense.   
     * Having said this, people have become comfortable with the network configuration 
     * just happening within ATAK.   This allows the network to be rekicked if it happens 
     * to go down.
     */
    synchronized public void startNetworkChecking(final String name,
            final String address,
            final String subnet,
            final String gateway,
            final int polling) {
        stopNetworkChecking();
        NetworkManagerLite.unconfigure(name);
        networkChecker = new NetworkCheckerRunnable(name, address, subnet,
                gateway, polling);
        new Thread(networkChecker, "networkchecker-" + name).start();
    }

    synchronized public void stopNetworkChecking() {
        if (networkChecker != null)
            networkChecker.cancel();
        networkChecker = null;
    }

    private boolean configureNetwork() {

        boolean attemptConfigure = sharedPreferences.getBoolean(
                "network_config", true);
        Log.d(TAG, "ethernet configuration attempt: " + attemptConfigure);

        /**
         * When using a network.map, these should point to the same information. When not using a
         * network.map file, the isrvNetworkDevice should be null.
         */
        NetworkInterface eth = getEthInterface();
        NetworkDevice isrvNetworkDevice = getNetworkMapDevice();

        if (!attemptConfigure) {

            setStatusText(
                    Color.YELLOW,
                    context.getString(
                            R.string.radio_skipping_ethernet_configuration));
            Log.i(TAG, "skipping ethernet configuration");
            return true;

        } else if (isrvNetworkDevice != null) {
            if (eth == null) {
                setStatusText(Color.RED,
                        context.getString(R.string.radio_ethernet_not_found));
                return false;
            }
            try {
                setStatusText(
                        Color.GREEN,
                        context.getString(
                                R.string.radio_configuring_ethernet_on_network_map));
                if (!eth.isUp())
                    NetworkManagerLite.configure(isrvNetworkDevice);

                automaticDiscoveryAttempt();
            } catch (Exception e) {
                setStatusText(
                        Color.RED,
                        context.getString(
                                R.string.radio_error_configuring_network_map));
            }

        } else {

            // use the legacy configuration
            if (eth == null) {
                setStatusText(Color.RED,
                        context.getString(R.string.radio_ethernet_not_found));
                return false;
            }
            Log.d(TAG, "discovered: " + eth);
            try {
                boolean isDhcp = sharedPreferences.getBoolean(
                        "network_dhcp",
                        false);
                if (isDhcp) {
                    setStatusText(
                            Color.GREEN,
                            context.getString(
                                    R.string.radio_configuring_ethernet_dhcp));

                    startNetworkChecking(eth.getName(), null, null, null,
                            5000);
                } else {
                    setStatusText(
                            Color.GREEN,
                            context.getString(
                                    R.string.radio_configuring_ethernet_static));
                    // default invalid address
                    final String invalidAddress = "0.0.0.0";

                    // ip address
                    String ipAddress = sharedPreferences.getString(
                            "network_static_ip_address", "192.168.80.200");
                    if (ipAddress.equalsIgnoreCase(invalidAddress)) {
                        // notify
                        showInvalidStaticNetworkSetting("IP Address");
                        return false;
                    }

                    // subnet mask
                    String subnetMask = sharedPreferences.getString(
                            "network_static_subnet_mask", "255.255.255.0");
                    if (subnetMask.equalsIgnoreCase(invalidAddress)) {
                        // notify
                        showInvalidStaticNetworkSetting("Subnet Mask");
                        return false;
                    }

                    // default gateway
                    String defaultGateway = sharedPreferences
                            .getString("network_static_gateway", "");
                    if (defaultGateway.equalsIgnoreCase(invalidAddress)) {
                        // notify
                        showInvalidStaticNetworkSetting("Default Gateway");
                        return false;
                    }

                    Log.d(TAG, "reconfiguring with ipAddress: " + ipAddress);
                    startNetworkChecking(eth.getName(), ipAddress,
                            subnetMask, defaultGateway, 5000);

                }
            } catch (Exception e) {
                setStatusText(
                        Color.RED,
                        context.getString(
                                R.string.radio_error_configuring_ethernet));
                Log.d(TAG, "error occurred configuring the radio network. ", e);
            }
        }

        // wait a second before pulling the information.
        int retries = 5;
        for (int i = 0; i < retries; ++i) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }

            eth = getEthInterface();

            try {
                if (eth != null && eth.isUp()
                        && NetworkManagerLite.getAddress(eth) != null) {
                    setStatusText(
                            Color.GREEN,
                            context.getString(R.string.config_complete_)
                                    + NetworkManagerLite.getAddress(eth)
                                    + "...");

                    automaticDiscoveryAttempt();
                    Log.d(TAG,
                            eth.getName() + " is up with ip "
                                    + NetworkManagerLite.getAddress(eth)
                                    + ", exiting");
                    return true;
                }
            } catch (SocketException ignored) {
            }
            setStatusText(
                    Color.YELLOW,
                    context.getString(
                            R.string.radio_configuration_pending_retries)
                            + " ["
                            + (i + 1) + "/" + retries
                            + "]...");
            Log.d(TAG, "ethernet is still down try [ + " + (i + 1) + "/"
                    + retries
                    + "]...");
        }

        return false;
    }

    private void automaticDiscoveryAttempt() {
        if (sharedPreferences.getBoolean("autoRoverAddress", true)) {
            Log.d(TAG, "attempt to automatically find the rover address");
            try {
                // This is used to for DNS resolution of the hostname.   This case cannot be 
                // mitigated without breaking functionality.  
                InetAddress address = InetAddress.getByName("rover.com");
                if (address != null) {
                    String pastAddress = sharedPreferences.getString(
                            "rover_ip_address",
                            "192.168.80.1");
                    final String addrString = address.getHostAddress();

                    if (!pastAddress.equalsIgnoreCase(addrString)) {
                        Log.d(TAG, "address change from: " + pastAddress
                                + " to: " + addrString);
                        sharedPreferences.edit().putString("rover_ip_address",
                                addrString).apply();
                        Log.d(TAG, "reinitialize the radio");
                        radio.initialize(context);
                    }
                } else {
                    Log.d(TAG, "rover.com failed to resolve to anything");
                    Toast.makeText(context,
                            "failed to automatically discover the rover",
                            Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ioe) {
                Log.d(TAG, "error occurred looking up an automatic address");
            }

        }
    }

    private String bestDisplayableIP() {
        String ethip = NetworkManagerLite.getAddress(getEthInterface());
        if (ethip != null) {
            return ethip;
        } else {
            return NetworkUtils.getIP();
        }
    }

    private void updateURLAnalog() {
        final String ip_analog = sharedPreferences.getString(
                PREF_KEY_ANALOG_IP,
                "239.255.0.1");
        final int port_analog = sharedPreferences.getInt(PREF_KEY_ANALOG_PORT,
                1841);
        urlAnalog = createURL(ip_analog, String.valueOf(port_analog));
    }

    private void updateURLDigital() {
        final String ip_digital = sharedPreferences.getString(
                PREF_KEY_DIG_IP, "239.255.0.1");
        final int port_digital = sharedPreferences.getInt(PREF_KEY_DIG_PORT,
                11841);
        urlDigital = createURL(ip_digital, String.valueOf(port_digital));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals(PREF_KEY_ANALOG_IP)
                || key.equals(PREF_KEY_ANALOG_PORT)) {
            updateURLAnalog();
        } else if (key.equals(PREF_KEY_DIG_IP)
                || key.equals(PREF_KEY_DIG_PORT)) {
            updateURLDigital();
        }
    }

    public static void addRadioStatusListener(RadioStatusListener rsl) {
        if (_instance != null)
            synchronized (_instance.rslList) {
                _instance.rslList.add(rsl);
            }
    }

    public static void removeRadioStatusListener(RadioStatusListener rsl) {
        if (_instance != null)
            synchronized (_instance.rslList) {
                _instance.rslList.remove(rsl);
            }
    }

    public static void notifyRadioStatusListeners(final int color,
            final String text, final String subtext) {
        if (_instance != null)
            synchronized (_instance.rslList) {
                for (RadioStatusListener rsl : _instance.rslList) {
                    rsl.roverStatusChanged(color, text, subtext);
                }
            }
    }

    /**
     * Expansion to allow for the lean services TV guide to make use of the Radio Control
     */

    /**
     * Frequency change in megahertz (1810 for 1.810GHZ, etc).  
     */
    public static boolean setFrequency(final int freqMhz) {
        // radio.setReceiverFrequency(1810000);
        if (_instance != null && radio != null
                && radio.isMonitoring()) {
            radio.setReceiverFrequency(freqMhz * 1000);
            return true;
        }
        return false;
    }

    /**
     * DDL support for Module/Channel (Module can be the string "M1, "M2", "M3", or "M4")
     */
    public static boolean setModuleChannel(final String mt, final int channel) {
        if (_instance != null && radio != null
                && radio.isMonitoring()) {
            radio.setChannel(mt, channel);
            return true;
        }
        return false;
    }

    /**
     * Last Status received from the rover.   If the rover is no longer monitoring 
     * or no status has been received, null is returned.
     */
    public static RoverInterface.Receiver getLastStatus() {
        if (_instance != null && radio != null
                && radio.isMonitoring()) {
            return _instance.lastStatus;
        }
        return null;
    }

    /**
     * Allows for the video to be watched, if the radio is already under the control of ATAK 
     * or the network monitor.
     */
    public static boolean watch() {
        if (_instance != null && radio != null
                && radio.isMonitoring()) {
            _instance.watchVideo(false);
            return true;
        }
        return false;
    }

    private InetAddress toInetAddress(final View v, final String ip)
            throws UnknownHostException {
        String[] address = ip.split("\\.");
        try {
            int a = Integer.parseInt(address[0]);
            int b = Integer.parseInt(address[1]);
            int c = Integer.parseInt(address[2]);
            int d = Integer.parseInt(address[3]);
            if (a < 255 && b < 255 && c < 255 && d < 255)
                return InetAddress.getByAddress(new byte[] {
                        (byte) a, (byte) b, (byte) c, (byte) d
                });
        } catch (Exception e) {
            Log.d(TAG, "error occurred resolving: " + ip, e);
        }
        toast(v, "attempting to resolve rover (slow)");
        return InetAddress.getByName(ip);
    }

    private void toast(View v, final String text) {
        v.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
