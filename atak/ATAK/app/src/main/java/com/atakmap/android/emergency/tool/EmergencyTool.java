
package com.atakmap.android.emergency.tool;

import android.Manifest;
import android.content.Context;
import android.content.Intent;

import com.atakmap.annotations.ModifierApi;
import com.atakmap.coremap.log.Log;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.TextView;
import android.graphics.Color;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * An ATAK plugin extension point that provides tooling for initiating emergency messages that will be
 * periodically re-broadcast by a TAK server once received.
 *  
 * 
 */
public class EmergencyTool extends DropDownReceiver implements
        EmergencyListener, DropDown.OnStateListener {
    public static final String TAG = "EmergencyTool";
    public static final String EMERGENCY_WIDGET = "EmergencyTool";
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected final MapView mapView;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected final Context context;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected final SharedPreferences sharedPrefs;

    private Spinner repeatTypeSpinner;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected Switch repeaterSwitch1;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected Switch repeaterSwitch2;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected CheckBox sendSMSCB;

    //private ListView otherBeaconsList;
    //private Timer timer = new Timer();

    public EmergencyTool(MapView mapView, Context context) {
        super(mapView);
        this.mapView = mapView;
        this.context = context;
        this.sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);
    }

    @Override
    public void onReceive(final Context ignoreCtx, Intent intent) {
        final Context context = mapView.getContext();

        LayoutInflater inflater = LayoutInflater.from(context);
        View viewRoot = inflater.inflate(R.layout.emergency_tool_layout, null);

        final EmergencyManager emergencyMgr = EmergencyManager.getInstance();

        emergencyMgr.registerEmergencyStateChangeListener(this);

        Log.i(TAG, emergencyMgr.toString());

        repeatTypeSpinner = viewRoot
                .findViewById(R.id.repeaterChoicesSpinner);
        repeaterSwitch1 = viewRoot.findViewById(R.id.emergencySwitch1);
        repeaterSwitch2 = viewRoot.findViewById(R.id.emergencySwitch2);

        sendSMSCB = viewRoot.findViewById(R.id.sendSMSCB);

        sendSMSCB.setChecked(sharedPrefs.getBoolean(
                EmergencyConstants.PREFERENCES_KEY_SMS_CHECKED, false));

        int res = context
                .checkCallingOrSelfPermission(Manifest.permission.SEND_SMS);
        if (res != PackageManager.PERMISSION_GRANTED) {
            sendSMSCB.setChecked(false);
            sendSMSCB.setVisibility(View.GONE);
            viewRoot.findViewById(R.id.sendSMSTitle).setVisibility(View.GONE);
        }

        repeaterSwitch1.setChecked(emergencyMgr.isEmergencyOn());
        repeaterSwitch2.setChecked(emergencyMgr.isEmergencyOn());
        if (emergencyMgr.isEmergencyOn()) {
            sendSMSCB.setEnabled(false);
            repeatTypeSpinner.setEnabled(false);
        }

        repeatTypeSpinner
                .setSelection(((ArrayAdapter<String>) repeatTypeSpinner
                        .getAdapter()).getPosition(emergencyMgr
                                .getEmergencyType().getDescription()));
        //repeatTypeSpinner.setEnabled(!emergencyMgr.isEmergencyOn());

        repeatTypeSpinner
                .setOnItemSelectedListener(new OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View view, int position, long id) {

                        if (view instanceof TextView)
                            ((TextView) view).setTextColor(Color.WHITE);

                        emergencyMgr.setEmergencyType(EmergencyType
                                .fromDescription(repeatTypeSpinner
                                        .getSelectedItem().toString()));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        emergencyMgr.setEmergencyType(EmergencyType
                                .getDefault());
                    }
                });

        // ** handle clicks on the emergency on/off toggle
        OnCheckedChangeListener checkChangeListener = new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {

                Object selectedItem = repeatTypeSpinner.getSelectedItem();
                sharedPrefs
                        .edit()
                        .putBoolean(
                                EmergencyConstants.PREFERENCES_KEY_SMS_CHECKED,
                                sendSMSCB.isChecked())
                        .apply();
                if (selectedItem != null) {
                    EmergencyType emergencyType = EmergencyType
                            .fromDescription(selectedItem.toString());

                    if (emergencyType != null) {
                        if (areRepeaterTogglesLocked()) {
                            initiateRepeat(emergencyType,
                                    sendSMSCB.isChecked());
                            sendSMSCB.setEnabled(false);
                        } else {
                            // ** if both switches were on and we just switched one off then cancel the beacon
                            if (!isChecked
                                    && (repeaterSwitch1.isChecked()
                                            || repeaterSwitch2
                                                    .isChecked())) {
                                cancelRepeat(emergencyType,
                                        sendSMSCB.isChecked());
                            }
                            sendSMSCB.setEnabled(true);
                        }
                        repeatTypeSpinner
                                .setEnabled(!areRepeaterTogglesLocked());

                        emergencyMgr.setEmergencyOn(areRepeaterTogglesLocked());
                        emergencyMgr.setEmergencyType(emergencyType);
                    } else {
                        // ** invalid selection, don't allow change
                        repeaterSwitch1.setChecked(!areRepeaterTogglesLocked());
                        repeaterSwitch2.setChecked(!areRepeaterTogglesLocked());
                        Log.e(TAG, "Unknown repeat type requested: "
                                + selectedItem);
                        sendSMSCB.setEnabled(true);
                    }
                } else {
                    // ** invalid selection, don't allow change
                    repeaterSwitch1.setChecked(!areRepeaterTogglesLocked());
                    repeaterSwitch2.setChecked(!areRepeaterTogglesLocked());
                    Toast.makeText(context,
                            R.string.must_select_emergency_type,
                            Toast.LENGTH_SHORT).show();
                    sendSMSCB.setEnabled(true);
                }
            }
        };

        repeaterSwitch1.setOnCheckedChangeListener(checkChangeListener);
        repeaterSwitch2.setOnCheckedChangeListener(checkChangeListener);
        sendSMSCB.setOnCheckedChangeListener(checkChangeListener);

        showDropDown(viewRoot, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                FULL_WIDTH, HALF_HEIGHT, this);
    }

    @Override
    public void disposeImpl() {
        EmergencyManager em = EmergencyManager.getInstance();
        if (em != null)
            em.dispose();

    }

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected boolean areRepeaterTogglesLocked() {
        return repeaterSwitch1.isChecked() && repeaterSwitch2.isChecked();
    }

    private void cancelRepeat(EmergencyType repeatType, boolean sendSms) {
        EmergencyManager em = EmergencyManager.getInstance();
        if (em != null)
            em.cancelRepeat(repeatType, sendSms);

        Toast.makeText(
                context,
                String.format(
                        context.getString(R.string.emergency_notice_canceled),
                        repeatType.getDescription()),
                Toast.LENGTH_SHORT).show();
    }

    private void initiateRepeat(EmergencyType repeatType, boolean sendSms) {
        EmergencyManager em = EmergencyManager.getInstance();
        if (em != null)
            em.initiateRepeat(repeatType, sendSms);

        Toast.makeText(
                context,
                String.format(
                        context.getString(R.string.emergency_notice_initiated),
                        repeatType.getDescription()),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Initiate a repeating message of the given type, optionally updating the UI to reflect the selection
     * @param //repeatType The type of message to repeat
     * @param //updateUI True to indicate the type selection widget and the toggle button should be updated to reflect
     * the new settings (of "on" for the toggle switch, and repeatType for the type selection spinner)
    
    public void initiateRepeat(EmergencyType repeatType, boolean updateUI) {
        if (updateUI) {
            repeaterSwitch1.setChecked(true);
            repeaterSwitch2.setChecked(true);
    
            // ** TODO: this could be dangerous, we're banking on the fact that this is an ArrayAdapter. That may
            // ** always be what we get since we initialize it with an array, but no guarantees...
            repeatTypeSpinner
                    .setSelection(((ArrayAdapter<String>) repeatTypeSpinner
                            .getAdapter()).getPosition("911"));
            repeatTypeSpinner.setEnabled(false);
        }
    
        initiateRepeat(repeatType);
    }
     */

    private String createMessage() {
        String s = "";
        PointMapItem selfMarker = getMapView().getSelfMarker();
        String deviceUID = "";
        String deviceLocation = "";
        DateFormat df = new SimpleDateFormat("K:mm a, z", LocaleUtil.US);
        String date = df.format(Calendar.getInstance().getTime());
        if (selfMarker != null) {
            deviceUID = selfMarker.getUID();
            deviceLocation = CoordinateFormatUtilities.formatToString(
                    getMapView().getSelfMarker().getPoint(),
                    CoordinateFormat.MGRS);
        }
        s += deviceUID + ", " + deviceLocation + ", ";
        s += date;
        s += " ";

        EmergencyManager em = EmergencyManager.getInstance();
        if (em != null) {
            try {
                s += em.getEmergencyType().toString();
            } catch (NullPointerException npe) {
                Log.e(TAG, "No alert type selected");
                s += "Emergency Type Unknown";
            }
        }
        return s;
    }

    @Override
    public void emergencyStateChanged(Boolean emergencyOn,
            EmergencyType emergencyType) {
        repeaterSwitch1.setChecked(emergencyOn);
        repeaterSwitch2.setChecked(emergencyOn);

        repeatTypeSpinner
                .setSelection(((ArrayAdapter<String>) repeatTypeSpinner
                        .getAdapter())
                                .getPosition(emergencyType.getDescription()));
        repeatTypeSpinner.setEnabled(!emergencyOn);
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }
}
