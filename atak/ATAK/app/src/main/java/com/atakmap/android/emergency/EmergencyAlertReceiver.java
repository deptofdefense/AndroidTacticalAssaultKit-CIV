
package com.atakmap.android.emergency;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.emergency.tool.EmergencyManager;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.warning.WarningComponent;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class EmergencyAlertReceiver extends BroadcastReceiver {

    private static final String TAG = "EmergencyAlertReceiver";

    final static String ALERT_EVENT = "com.atakmap.android.emergency.ALERT_EVENT";
    final static String CANCEL_EVENT = "com.atakmap.android.emergency.CANCEL_EVENT";
    final static String REMOVE_ALERT = "com.atakmap.android.emergency.REMOVE_ALERT";
    private static final long MONITOR_RATE = 2000; //2 seconds

    /**
     * Simple container for an ongoing alert
     * Note, we could track additional data if necessary, e.g. first/last report time, CoT type, etc
     */
    static class EmergencyAlert extends WarningComponent.Alert {
        private String type;
        private String message;
        private GeoPoint point;
        private PointMapItem item;
        private String itemUid;
        private String eventUid;

        String getEventUid() {
            return eventUid;
        }

        public PointMapItem getItem() {
            return item;
        }

        public GeoPoint getPoint() {
            return point;
        }

        public boolean isValid() {
            if (FileSystemUtils.isEmpty(type))
                return false;

            return point != null;//message and item are optional
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EmergencyAlert))
                return super.equals(o);

            EmergencyAlert a = (EmergencyAlert) o;
            return FileSystemUtils.isEquals(type, a.type) &&
                    FileSystemUtils.isEquals(message, a.message) &&
                    FileSystemUtils.isEquals(itemUid, a.itemUid) &&
                    FileSystemUtils.isEquals(eventUid, a.eventUid);
        }

        @Override
        public int hashCode() {
            int hashcode = 31;
            if (!FileSystemUtils.isEmpty(type))
                hashcode *= type.hashCode();
            if (!FileSystemUtils.isEmpty(message))
                hashcode *= message.hashCode();
            if (itemUid != null)
                hashcode *= itemUid.hashCode();

            return hashcode;
        }

        @Override
        public String toString() {
            return type + " " + message + " " + itemUid;
        }

        @Override
        public String getMessage() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid getMessage");
                return null;
            }

            if (this.point != null) {
                //get R&B to self if available
                Marker self = ATAKUtilities.findSelf(MapView.getMapView());
                String callsign;
                if (this.item == null) {
                    MapItem alertEvent = MapView.getMapView().getRootGroup()
                            .deepFindUID(eventUid);
                    if (alertEvent == null)
                        return "";
                    callsign = alertEvent.getMetaString("title", "");
                    if (!FileSystemUtils.isEmpty(callsign)
                            && callsign.contains("-"))
                        callsign = callsign.substring(0, callsign
                                .lastIndexOf("-"));
                } else {
                    callsign = ATAKUtilities
                            .getDisplayName(this.item);
                }
                String rAndB = "(No GPS)";
                if (self != null) {
                    double distance = self.getPoint().distanceTo(this.point);
                    double bearing = self.getPoint().bearingTo(this.point);
                    String direction = DirectionType.getDirection(bearing)
                            .getAbbreviation();
                    if (!Double.isNaN(distance)
                            && !FileSystemUtils.isEmpty(direction)) {
                        if (distance > 1000) {
                            rAndB = (int) Math.round(distance / 1000) + "km "
                                    + direction;
                        } else {
                            rAndB = (int) Math.round(distance) + "m "
                                    + direction;
                        }
                    }
                }

                return this.type + "\n" + callsign + " " + rAndB;
            } else {
                //alert/point details not available
                return "Alert";
            }
        }

        @Override
        public void onClick() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid onClick");
                return;
            }

            if (this.item != null &&
                    this.item.getPoint() != null) {
                //slew to item's current point if location is available and still on map
                //i.e. still in a MapGroup
                panTo(this.item.getPoint(),
                        MapMenuReceiver.getCurrentItem() != this.item);
            } else if (this.point != null) {
                //slew to point of emergency
                panTo(this.point, true);
            }
        }

        @Override
        public String getAlertGroupName() {
            return MapView.getMapView().getContext().getString(
                    R.string.emergency);
        }
    }

    private final MapView _mapView;
    private final Context _context;

    /**
     * List of ongoing alerts
     */
    private final List<EmergencyAlert> _alerts;
    private final List<OnAlertChangedListener> _listeners;

    private Timer _monitorTimer;
    private boolean _initialized;
    private final MapGroup _alertGroup;

    EmergencyAlertReceiver(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _alerts = new ArrayList<>();
        _listeners = new ArrayList<>();
        _initialized = false;

        _alertGroup = _mapView.getRootGroup().findMapGroup("Emergency");
    }

    @Override
    public void onReceive(Context ignoreCtx, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (ALERT_EVENT.equals(action)) {
            CotEvent event = intent.getParcelableExtra("cotevent");
            if (event == null) {
                Log.w(TAG, "Failed to process w/out CoT event");
                return;
            }

            EmergencyAlert alert = fromCot(event, true);
            if (alert == null || !alert.isValid()) {
                Log.w(TAG, "Invalid alert for CoT: " + event.getUID());
                return;
            }

            if (!add(alert)) {
                Log.w(TAG, "Failed to add alert for CoT: " + event.getUID());

            }
        } else if (CANCEL_EVENT.equals(action)) {
            CotEvent event = intent.getParcelableExtra("cotevent");
            if (event == null) {
                Log.w(TAG, "Failed to process w/out CoT event");
                return;
            }

            String message = "";
            EmergencyAlert alert = fromCot(event, false);
            if (alert != null)
                message = alert.message;

            if (!remove(event.getUID(), message)) {
                Log.w(TAG, "Failed to remove alert for CoT: " + event.getUID());

            }
        } else if (REMOVE_ALERT.equals(action)) {
            String uid = intent.getStringExtra("uid");
            if (FileSystemUtils.isEmpty(uid))
                return;

            final EmergencyAlert alert = getAlert(uid);
            if (alert == null)
                return;

            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(R.string.confirm_delete);
            b.setIcon(R.drawable.ic_menu_delete_32);
            b.setMessage(_context.getString(R.string.delete_emergency_alert,
                    alert.getMessage()));
            b.setPositiveButton(R.string.delete2,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int id) {
                            Log.d(TAG, "Dismissing alert for: "
                                    + alert);
                            remove(alert.getEventUid(), alert.getMessage());
                            if (alert.message != null
                                    && _mapView.getSelfMarker()
                                            .getMetaString("callsign", "")
                                            .contains(alert.message)) {
                                EmergencyManager em = EmergencyManager
                                        .getInstance();
                                if (em != null)
                                    em.cancelRepeat();
                            }
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            HierarchyListReceiver.REFRESH_HIERARCHY));
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }
    }

    private synchronized boolean add(EmergencyAlert alert) {
        if (alert == null || !alert.isValid()) {
            Log.w(TAG, "Cannot add invalid alert");
            return false;
        }

        int index = _alerts.indexOf(alert);
        if (index < 0) {
            Log.d(TAG, "Adding alert: " + alert);
        } else {
            //Log.d(TAG, "Updating alert: " + alert);
            _alerts.remove(index);
        }
        _alerts.add(alert);

        WarningComponent.addAlert(alert);
        for (OnAlertChangedListener l : _listeners)
            l.onAlertAdded(alert);
        initialize();
        return true;
    }

    private synchronized void initialize() {
        if (_initialized)
            return;

        Log.d(TAG, "Initializing...");

        _monitorTimer = new Timer("EmergencyAlertUpdater");
        _monitorTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateAlerts();
            }
        }, 0, MONITOR_RATE);

        _initialized = true;
    }

    private synchronized void updateAlerts() {
        //first see if we can find items we didn't have when the alert came out
        for (EmergencyAlert alert : _alerts) {
            if (alert.item == null) {
                MapItem item = _mapView.getRootGroup().deepFindUID(
                        alert.itemUid);
                if (item instanceof PointMapItem) {
                    Log.d(TAG, "Now found item for alert: " + alert);
                    alert.item = (PointMapItem) item;
                    WarningComponent.addAlert(alert);
                }
            }
        }
    }

    /**
     * @param eventUid the uid identifying the event for the emergency
     * @param message the message to be sent describing the event
     * @return true if the event uid was removed.
     */
    synchronized boolean remove(final String eventUid,
            final String message) {
        if (FileSystemUtils.isEmpty(eventUid)) {
            Log.w(TAG, "Cannot remove invalid alert");
            return false;
        }

        int index = -1;
        EmergencyAlert alert = null;
        for (int i = 0; i < _alerts.size(); i++) {
            if (_alerts.get(i) != null
                    && eventUid.equals(_alerts.get(i).eventUid)) {
                index = i;
                alert = _alerts.get(i);
                break;
            }
        }

        if (index < 0) {
            Log.w(TAG, "Cannot remove alert: " + eventUid);
            return false;
        } else {
            Log.d(TAG, "Removing alert: " + eventUid);
            _alerts.remove(index);

            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(_mapView.getContext(),
                            String.format(
                                    _mapView.getContext().getString(
                                            R.string.emergency_removing_alert),
                                    message),
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        //attempt to remove alert marker
        if (_alertGroup != null) {
            MapItem alertMarker = _alertGroup.deepFindUID(eventUid);
            if (alertMarker != null) {
                Log.d(TAG, "Removing alert marker: " + eventUid);
                _alertGroup.removeItem(alertMarker);
            }
        }

        if (alert != null) {
            WarningComponent.removeAlert(alert);
            for (OnAlertChangedListener l : _listeners)
                l.onAlertRemoved(alert);
        }

        return true;
    }

    private EmergencyAlert fromCot(CotEvent event, boolean requireLink) {
        if (!event.getType().startsWith(
                EmergencyDetailHandler.EMERGENCY_TYPE_PREFIX)) {
            Log.w(TAG, "Invalid CoT type: " + event.getType());
            return null;
        }

        CotDetail detail = event.getDetail();
        CotDetail emergency = detail.getFirstChildByName(0,
                EmergencyDetailHandler.EMERGENCY_TYPE_META_FIELD);
        if (emergency == null) {
            Log.w(TAG, "Missing CoT emergency detail");
            return null;
        }

        EmergencyAlert alert = getAlert(event.getUID());
        if (alert == null) {
            alert = new EmergencyAlert();
            alert.eventUid = event.getUID();
        }

        if (requireLink) {
            CotDetail link = detail.getFirstChildByName(0, "link");
            if (link == null) {
                Log.w(TAG, "Missing CoT link detail");
                return null;
            }

            String relation = link.getAttribute("relation");
            String type = link.getAttribute("type");
            String uid = link.getAttribute("uid");

            //don't deepFindUID unless necessary
            alert.itemUid = uid;
            if (alert.item == null) {
                MapGroup mg = _mapView.getRootGroup();
                if (mg == null) {
                    Log.d(TAG, "bad event occuring during shutdown, ignoring");
                    return null;
                }

                MapItem item = mg.deepFindUID(uid);
                if (item instanceof PointMapItem &&
                        FileSystemUtils.isEquals(type, item.getType()) &&
                        FileSystemUtils.isEquals(relation, "p-p")) {
                    Log.d(TAG, "Setting item for alert: " + uid);
                    alert.item = (PointMapItem) item;
                } else {
                    Log.w(TAG, "No item found for alert: " + uid);
                }
            }
        }

        alert.type = emergency.getAttribute("type");
        alert.message = emergency.getInnerText();
        alert.point = event.getGeoPoint();
        if (requireLink && !alert.isValid()) {
            Log.w(TAG, "Invalid alert");
            return null;
        }

        return alert;
    }

    private synchronized EmergencyAlert getAlert(final String eventUID) {
        for (int i = 0; i < _alerts.size(); i++) {
            EmergencyAlert alert = _alerts.get(i);
            if (alert != null && eventUID.equals(alert.eventUid)) {
                return alert;
            }
        }

        return null;
    }

    synchronized int getAlertCount() {
        return _alerts.size();
    }

    /**
     * Get list of Emergency alerts
     * @return the list of emergency alerts
     */
    synchronized List<EmergencyAlert> getAlerts() {
        return new ArrayList<>(_alerts);
    }

    public synchronized void dispose() {
        if (_monitorTimer != null) {
            _monitorTimer.cancel();
            _monitorTimer.purge();
            _monitorTimer = null;
        }

        if (_alerts != null) {
            _alerts.clear();
        }
    }

    public interface OnAlertChangedListener {
        void onAlertAdded(EmergencyAlert alert);

        void onAlertRemoved(EmergencyAlert alert);
    }

    public void addOnAlertChangedListener(OnAlertChangedListener l) {
        if (!_listeners.contains(l))
            _listeners.add(l);
    }

    public void removeOnAlertChangedListener(OnAlertChangedListener l) {
        _listeners.remove(l);
    }
}
