
package com.atakmap.android.emergency.tool;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;

import com.atakmap.annotations.ModifierApi;
import com.atakmap.coremap.log.Log;
import android.annotation.SuppressLint;
import com.atakmap.android.emergency.sms.SMSGenerator;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class EmergencyManager {
    private static EmergencyManager _instance;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected static final String TAG = "EmergencyManager";

    private final Context context;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected final MapView mapView;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected boolean emergencyOn;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected EmergencyType emergencyType = EmergencyType.NineOneOne;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected final AtomicInteger idCounter = new AtomicInteger(0);
    private final Set<EmergencyListener> emergencyListeners;
    private final Map<String, EmergencyBeacon> emergencyBeacons;
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected final SharedPreferences sharedPrefs;

    protected EmergencyManager(final MapView mapView) {
        Log.i(TAG, "Creating new EmergencyManager instance");

        this.mapView = mapView;
        this.context = mapView.getContext();

        emergencyListeners = new HashSet<>();
        emergencyBeacons = new HashMap<>();

        emergencyOn = Boolean.FALSE;
        emergencyType = EmergencyType.getDefault();

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPrefs.getBoolean(
                EmergencyConstants.PREFERENCES_KEY_BEACON_ENABLED, false)) {
            EmergencyType eType = EmergencyType.fromDescription(sharedPrefs
                    .getString(EmergencyConstants.PREFERENCES_KEY_BEACON_TYPE,
                            EmergencyType.NineOneOne.getDescription()));
            if (eType == null) {
                eType = EmergencyType.NineOneOne;
            }

            setEmergencyOn(true);
            setEmergencyType(eType);
        }

    }

    public void dispose() {
    }

    public static synchronized void initialize(MapView mapView) {
        if (_instance != null) {
            return;
        }
        _instance = new EmergencyManager(mapView);
    }

    public static synchronized EmergencyManager getInstance() {
        if (_instance == null) {
            Log.e(TAG,
                    "Attempted to access emergency manager before it was initialized");
            return null;
        }
        return _instance;
    }

    public Context getContext() {
        return context;
    }

    public List<EmergencyBeacon> getEmergencyBeacons() {
        return new ArrayList<>(emergencyBeacons.values());
    }

    private void addEmergencyBeacon(EmergencyBeacon beacon) {
        if (!emergencyBeacons.containsKey(beacon.getUid())) {
            emergencyBeacons.put(beacon.getUid(), beacon);
        }
    }

    private void removeEmergencyBeacon(String uid) {
        if (uid == null) {
            Log.e(TAG, "Received null UID to remove");
        } else {
            emergencyBeacons.remove(uid);
        }
    }

    public void initiateRepeat(EmergencyType repeatType, boolean sendSms) {
        if (!emergencyOn) {
            // ** if we're not already on then increment the counter
            // ** this value will be used in the UID
            idCounter.incrementAndGet();
        }

        Log.d(TAG, "Initiating " + repeatType);
        emergencyOn = Boolean.TRUE;
        emergencyType = repeatType;

        CotEvent event = generateInitiateMessage(repeatType);
        if (sendSms) {
            sendSMS(repeatType);
        }

        CotMapComponent.getExternalDispatcher().dispatch(event);
        CotMapComponent.getInternalDispatcher()
                .dispatch(event);
        sharedPrefs
                .edit()
                .putBoolean(EmergencyConstants.PREFERENCES_KEY_BEACON_ENABLED,
                        true)
                .apply();
        sharedPrefs
                .edit()
                .putString(EmergencyConstants.PREFERENCES_KEY_BEACON_TYPE,
                        repeatType.getDescription())
                .apply();

        notifyPlugins(event);

        notifyListenersOfStateChange();
    }

    public void cancelRepeat() {
        if (sharedPrefs.getBoolean(
                EmergencyConstants.PREFERENCES_KEY_BEACON_ENABLED, false)) {
            EmergencyType eType = EmergencyType.fromDescription(sharedPrefs
                    .getString(EmergencyConstants.PREFERENCES_KEY_BEACON_TYPE,
                            EmergencyType.NineOneOne.getDescription()));
            cancelRepeat(eType, false);
        }
    }

    public void cancelRepeat(EmergencyType repeatType, boolean sendSms) {
        Log.d(TAG, "Canceling " + repeatType);
        CotEvent event = generateCancelMessage(repeatType);

        emergencyOn = Boolean.FALSE;
        emergencyType = repeatType;

        if (sendSms) {
            sendSMS(EmergencyType.Cancel);
        }

        CotMapComponent.getExternalDispatcher().dispatch(event);
        CotMapComponent.getInternalDispatcher().dispatch(event);

        sharedPrefs
                .edit()
                .putBoolean(EmergencyConstants.PREFERENCES_KEY_BEACON_ENABLED,
                        false)
                .apply();

        notifyPlugins(event);

        notifyListenersOfStateChange();
    }

    protected void notifyListenersOfStateChange() {
        for (EmergencyListener listener : emergencyListeners) {
            listener.emergencyStateChanged(emergencyOn, emergencyType);
        }
    }

    private CotEvent generateInitiateMessage(EmergencyType repeatType) {
        final Marker self = mapView.getSelfMarker();
        final String callsign = self.getMetaString("callsign", self.getUID());
        GeoPoint location = self.getPoint();

        CotEvent event = new CotEvent();
        event.setUID(getSelfEmergencyUid(self.getUID()));
        event.setHow(self.getMetaString("how", "h-g-i-g-o"));

        CotPoint point = new CotPoint(location.getLatitude(),
                location.getLongitude(), location.getAltitude(),
                location.getCE(), location.getLE());
        event.setPoint(point);

        event.setStart(new CoordinatedTime());
        event.setTime(new CoordinatedTime());
        event.setStale(new CoordinatedTime().addMilliseconds(10000));

        event.setType(repeatType.getCoTType());

        CotDetail detail = new CotDetail();
        CotDetail link = new CotDetail("link");
        link.setAttribute("uid", self.getUID());
        link.setAttribute("type", mapView.getMapData().getString("deviceType",
                mapView.getContext().getString(R.string.default_cot_type)));
        link.setAttribute("relation", "p-p");
        detail.addChild(link);

        CotDetail contact = new CotDetail("contact");
        contact.setAttribute("callsign",
                self.getMetaString("callsign", self.getUID()) + "-Alert");
        detail.addChild(contact);

        CotDetail initiateRepeat = new CotDetail("emergency");
        initiateRepeat.setAttribute("type", repeatType.getDescription());
        initiateRepeat.setInnerText(callsign);
        detail.addChild(initiateRepeat);
        event.setDetail(detail);

        StringBuilder builder = new StringBuilder();
        event.buildXml(builder);
        return event;
    }

    private CotEvent generateCancelMessage(EmergencyType repeatType) {
        final Marker self = mapView.getSelfMarker();
        final String callsign = self.getMetaString("callsign", self.getUID());

        CotEvent event = new CotEvent();
        event.setUID(getSelfEmergencyUid(self.getUID()));
        event.setHow(self.getMetaString("how", "h-g-i-g-o"));

        GeoPoint location = self.getPoint();

        CotPoint point = new CotPoint(location.getLatitude(),
                location.getLongitude(), location.getAltitude(),
                location.getCE(), location.getLE());
        event.setPoint(point);

        event.setStart(new CoordinatedTime());
        event.setTime(new CoordinatedTime());
        event.setStale(new CoordinatedTime().addMilliseconds(10000));

        event.setType("b-a-o-can"); // Alarm/Other/Cancel

        CotDetail detail = new CotDetail();
        CotDetail cancelRepeat = new CotDetail("emergency");
        cancelRepeat.setAttribute("cancel", "true");
        cancelRepeat.setInnerText(callsign);
        detail.addChild(cancelRepeat);
        event.setDetail(detail);

        StringBuilder builder = new StringBuilder();
        event.buildXml(builder);
        return event;
    }

    public void sendSMS(EmergencyType repeatType) {
        final Marker self = mapView.getSelfMarker();
        final String callsign = self.getMetaString("callsign", self.getUID());
        boolean bHow = false; // default to human
        if (self.getMetaString("how", "h-g-i-g-o").startsWith("m")) {
            bHow = true;
        }
        GeoPoint location = self.getPoint();
        try {
            String alertMessage = SMSGenerator.formatSMS(sharedPrefs,
                    repeatType, callsign,
                    bHow, location.getLatitude(), location.getLongitude());

            String numbers = "";
            if (sharedPrefs.contains("sms_numbers")) {
                numbers = sharedPrefs.getString("sms_numbers", "");
            }
            if (!numbers.equals("")) {
                String[] parsedNumbers = numbers.split("-");
                for (String s : parsedNumbers) {
                    try {
                        if (sendSmsInterface != null) {
                            sendSmsInterface.sendTextMessage(s, null,
                                    alertMessage, null, null);
                        } else {
                            int res = getContext().checkCallingOrSelfPermission(
                                    Manifest.permission.SEND_SMS);
                            if (res == PackageManager.PERMISSION_GRANTED) {
                                // default behavior
                                SmsManager sms = SmsManager.getDefault();
                                if (sms != null)
                                    sms.sendTextMessage(s, null, alertMessage,
                                            null, null);
                            }
                        }
                    } catch (Exception e) {

                        Log.e(TAG, "unable to send SMS", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS Alert sending failed: " + e.getLocalizedMessage());
        }

    }

    @SuppressLint("HardwareIds")
    protected String getSelfEmergencyUid(String myUid) {
        TelephonyManager tm;
        tm = (TelephonyManager) getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        if (tm != null) {

            if (com.atakmap.app.Permissions.checkPermission(getContext(),
                    android.Manifest.permission.READ_PHONE_STATE) ||
                    com.atakmap.app.Permissions.checkPermission(getContext(),
                            Manifest.permission.READ_PHONE_NUMBERS)) {
                final String phoneNumber = tm.getLine1Number();
                if (!FileSystemUtils.isEmpty(phoneNumber)
                        && !phoneNumber.startsWith("00")) {
                    return phoneNumber.replace("+", "") + "-9-1-1";
                }
            }
        }
        return myUid + "-9-1-1";
    }

    public void registerEmergencyStateChangeListener(
            EmergencyListener listener) {
        emergencyListeners.add(listener);
    }

    public Boolean isEmergencyOn() {
        return emergencyOn;
    }

    public void setEmergencyOn(boolean emergencyOn) {
        this.emergencyOn = emergencyOn;
    }

    public EmergencyType getEmergencyType() {
        return emergencyType;
    }

    public void setEmergencyType(EmergencyType emergencyType) {
        this.emergencyType = emergencyType;
    }

    @Override
    public String toString() {
        final Marker self = mapView.getSelfMarker();
        final String uid = self.getUID();
        final String callsign = self.getMetaString("callsign", uid);
        return "Emergency: " + emergencyOn + "\nEmergency type: "
                + emergencyType + "\nCallsign: " + callsign + "\nUID: " + uid;
    }

    public synchronized void clearExpiredBeacons() {
        Iterator<String> iterator = emergencyBeacons.keySet().iterator();
        Date now = new Date();
        while (iterator.hasNext()) {
            String uid = iterator.next();
            //Log.i(TAG, "Comparing now [" + now + "] to stale [" + emergencyBeacons.get(uid).getStaleTime() +"]");
            if (now.after(emergencyBeacons.get(uid).getStaleTime())) {
                Log.d(TAG, "Removing emergency beacon for " + uid);
                iterator.remove();
            }
        }
    }

    protected void notifyPlugins(CotEvent event) {
        // Notify plugins to send an emergency message
        Intent intent = new Intent(EmergencyConstants.PLUGIN_SEND_EMERGENCY);
        intent.putExtra(EmergencyConstants.PLUGIN_SEND_EMERGENCY_EXTRA,
                event.toString());
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    public interface SendSmsInterface {
        /**
         * Send a text based SMS.
         *
         * @param destinationAddress the address to send the message to
         * @param scAddress is the service center address or null to use
         *  the current default SMSC
         * @param text the body of the message to send
         * @param sentIntent if not NULL this <code>PendingIntent</code> is
         *  broadcast when the message is successfully sent, or failed.
         *  The result code will be <code>Activity.RESULT_OK</code> for success,
         *  or one of these errors:<br>
         *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
         *  <code>RESULT_ERROR_RADIO_OFF</code><br>
         *  <code>RESULT_ERROR_NULL_PDU</code><br>
         *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
         *  the extra "errorCode" containing a radio technology specific value,
         *  generally only useful for troubleshooting.<br>
         *  The per-application based SMS control checks sentIntent. If sentIntent
         *  is NULL the caller will be checked against all unknown applications,
         *  which cause smaller number of SMS to be sent in checking period.
         * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
         *  broadcast when the message is delivered to the recipient.  The
         *  raw pdu of the status report is in the extended data ("pdu").
         *
         * @throws IllegalArgumentException if destinationAddress or text are empty
         */
        void sendTextMessage(
                String destinationAddress, String scAddress, String text,
                PendingIntent sentIntent, PendingIntent deliveryIntent);
    }

    private static SendSmsInterface sendSmsInterface;

    /**
     * Sets the Send SMS Interface to use instead of the default SMS Interface.
     * @param iface the implementation to use or null if the default is to be used.
     */
    public static void setSendSmsInterface(SendSmsInterface iface) {
        sendSmsInterface = iface;
    }

    /**
     * Used to let the AlertPreferenceFragment know that there is a Sms send Plugin.
     * @return true if one is registered.
     */
    public static boolean hasSmsSendPlugin() {
        return (sendSmsInterface != null);
    }
}
