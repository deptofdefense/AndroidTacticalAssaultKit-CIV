
package com.atakmap.android.warning;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.MotionEvent;

import com.atakmap.android.emergency.tool.EmergencyTool;
import com.atakmap.android.geofence.alert.GeoFenceAlerting;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget.OnClickListener;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.android.util.NotificationIdRecycler;
import com.atakmap.map.CameraController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The primary types of alerts:
 *  Danger Close (Fires Tools) Alerts
 *  Geo Fence Alerts
 *  911/Emergency Alerts
 *
 *  Additional alert types are supported in Overlay Manager using the group name
 *  as the overlay identifier. If plugins would like to implement their own
 *  custom alert overlay over the default, they must use the group name as the
 *  identifier and remove the default overlay.
 */
public class WarningComponent extends AbstractWidgetMapComponent implements
        OnClickListener {

    private static final String TAG = "WarningComponent";
    private static final int NOTIF_ID = 34245; //arbitrary

    private static TextWidget alertWidget;

    private static MapView _mapView;
    private static SharedPreferences _prefs;
    private static NotificationIdRecycler _notificationId;
    private static MapOverlayParent _overlayParent;

    /**
     * Primary alert type overlay identifiers
     */
    private static final Set<String> _primaryGroups = new HashSet<>();

    /**
     * Prevent alerts being triggered after shutdown/destroy has already cleared alerts
     */
    private static boolean _active;

    /**
     * Abstract alert base class
     */
    public static abstract class Alert {

        boolean displayed = false;

        /**
         * Pan to a point on the map
         * @param closeMenu Close any opened radial and un-focus
         */
        protected void panTo(GeoPoint gp, boolean closeMenu) {
            MapView mv = MapView.getMapView();
            if (mv == null || gp == null)
                return;
            if (closeMenu) {
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        MapMenuReceiver.HIDE_MENU));
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        "com.atakmap.android.maps.HIDE_DETAILS"));
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent("com.atakmap.android.maps.UNFOCUS"));
            }
            CameraController.Programmatic.panTo(
                    mv.getRenderer3(), gp, false);
        }

        /**
         * Message to display in alert widget
         * @return the message that represents the alert
         */
        public abstract String getMessage();

        /**
         * Invoked when alert widget is clicked, if this is the sole alert
         * Also invoked if alert is clicked in the overlay manager
         */
        public abstract void onClick();

        /**
         * Used to navigate within Overlay Manager when alert widget is clicked
         * @return the alert group name
         */
        public abstract String getAlertGroupName();
    }

    /**
     * Current list of alerts
     */
    private static final List<Alert> alerts;

    private static final Map<String, MapOverlay> overlays;

    static {
        alerts = new ArrayList<>();
        overlays = new HashMap<>();
    }

    @Override
    protected void onCreateWidgets(final Context context, final Intent intent,
            final MapView view) {
        _active = true;
        _mapView = view;
        _prefs = PreferenceManager.getDefaultSharedPreferences(context);

        _notificationId = new NotificationIdRecycler(49765, 5);

        _primaryGroups.add(context.getString(R.string.geo_fences));
        _primaryGroups.add(context.getString(R.string.danger_close));
        _primaryGroups.add(context.getString(R.string.emergency));

        _overlayParent = MapOverlayParent.getOrAddParent(_mapView,
                "alertoverlays",
                _mapView.getContext().getString(R.string.alerts),
                "asset://icons/emergency.png", 1, false);

        RootLayoutWidget root = (RootLayoutWidget) _mapView.getComponentExtra(
                "rootLayoutWidget");
        LinearLayoutWidget layoutV = root
                .getLayout(RootLayoutWidget.BOTTOM_LEFT)
                .getOrCreateLayout("BL_H/BL_V");

        //setup Danger Close
        boolean isTablet = view.getContext().getResources().getBoolean(
                com.atakmap.app.R.bool.isTablet);
        alertWidget = new TextWidget("", MapView.getTextFormat(
                Typeface.DEFAULT_BOLD, isTablet ? 5 : 2));
        alertWidget.addOnClickListener(this);
        alertWidget.setVisible(false);
        alertWidget.setName(EmergencyTool.EMERGENCY_WIDGET);
        alertWidget.setColor(0xFFFF0000);
        alertWidget.setMargins(16f, 0f, 0f, 16f);
        layoutV.addWidget(alertWidget);

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        _mapView.getContext().getString(
                                R.string.alertPreferences),
                        "Adjust the Alert Preferences",
                        "alertPreference",
                        _mapView.getContext().getResources().getDrawable(
                                R.drawable.emergency),
                        new AlertPreferenceFragment()));
    }

    @Override
    public void onMapWidgetClick(final MapWidget widget,
            final MotionEvent event) {
        if (widget == alertWidget) {
            List<Alert> as;
            synchronized (alerts) {
                as = new ArrayList<>(alerts);
            }

            //default to display list of alerts in overlay mgr
            ArrayList<String> overlayPaths = new ArrayList<>();
            overlayPaths.add(_mapView.getContext().getString(R.string.alerts));

            if (!as.isEmpty()) {
                // Find common parent path and sub-path
                String commonPath = null, commonSubPath = null;
                for (Alert a : as) {
                    String parentPath = a.getAlertGroupName();
                    String subPath = null;
                    if (a instanceof GeoFenceAlerting.Alert) {
                        // Geo-fence alert sub-path is the monitor item UID
                        MapItem mi = ((GeoFenceAlerting.Alert) a)
                                .getMonitorItem();
                        subPath = mi != null ? mi.getUID() : "";
                    }
                    if (FileSystemUtils.isEmpty(parentPath)
                            || commonPath != null
                                    && !parentPath.equals(commonPath)) {
                        commonPath = null;
                        break;
                    }
                    if (FileSystemUtils.isEmpty(subPath)
                            || commonSubPath != null
                                    && !subPath.equals(commonSubPath))
                        commonSubPath = "";
                    if (commonPath == null)
                        commonPath = parentPath;
                    if (commonSubPath == null)
                        commonSubPath = subPath;
                }
                if (!FileSystemUtils.isEmpty(commonPath)) {
                    overlayPaths.add(commonPath);
                    if (!FileSystemUtils.isEmpty(commonSubPath))
                        overlayPaths.add(commonSubPath);
                }
                // Select alert if there's only one
                if (as.size() == 1)
                    as.get(0).onClick();
            }

            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    HierarchyListReceiver.MANAGE_HIERARCHY)
                            .putStringArrayListExtra("list_item_paths",
                                    overlayPaths)
                            .putExtra("isRootList", true));
        }
    }

    static private String pastMessage = "";

    /**
     * Produces an individual alert when the option in enabled by the user.
     * This alert is in addition to the regular roll up of alerts.
     * @param alert the alert to be individually shown.
     */
    private static void alert(final Alert alert) {

        if (_prefs.getBoolean("alert_notification", true) &&
                _prefs.getBoolean("individual_alert_notification", false)) {

            // prevent the alert from being displayed more than once
            if (alert.displayed)
                return;
            alert.displayed = true;

            ArrayList<String> overlayPaths = new ArrayList<>();
            overlayPaths.add(_mapView.getContext().getString(
                    R.string.alerts));
            Intent pending = new Intent(
                    HierarchyListReceiver.MANAGE_HIERARCHY)
                            .putStringArrayListExtra("list_item_paths",
                                    overlayPaths)
                            .putExtra("isRootList", true);

            int indx = _notificationId.getNotificationId();

            String message = alert.getMessage();
            boolean vibrate = _prefs.getBoolean("alert_vibration", true);
            boolean chime = _prefs.getBoolean("alert_audible", false);
            boolean blink = chime;

            if (message != null) {
                Log.d(TAG, "call to individual alert: " + message);
                NotificationUtil.getInstance().postNotification(indx,
                        R.drawable.ic_menu_emergency,
                        NotificationUtil.WHITE,
                        message, null,
                        alert.getAlertGroupName(), pending, vibrate, chime,
                        blink, true);
            }

        }

    }

    /**
     * Update the widget and the notification text
     * Optionally play sound/vibrate (e.g. for new alerts, not alerts being removed)
     *
     * @param verbose provide additional detail in the message.
     */
    private static void alert(boolean verbose) {

        //always update the widget
        String message = refreshWidget();

        if (_prefs.getBoolean("alert_notification", true)) {
            if (FileSystemUtils.isEmpty(message)) {
                NotificationUtil.getInstance().clearNotification(NOTIF_ID);
                pastMessage = "";
            } else {
                ArrayList<String> overlayPaths = new ArrayList<>();
                overlayPaths.add(_mapView.getContext().getString(
                        R.string.alerts));
                Intent pending = new Intent(
                        HierarchyListReceiver.MANAGE_HIERARCHY)
                                .putStringArrayListExtra("list_item_paths",
                                        overlayPaths)
                                .putExtra("isRootList", true);

                //sound/vibrate/flash only if configured and 'verbose'
                boolean vibrate = verbose
                        && _prefs.getBoolean("alert_vibration", true);
                boolean chime = verbose
                        && _prefs.getBoolean("alert_audible", false);
                boolean blink = chime;

                if (verbose
                        || (message != null && !message.equals(pastMessage))) {
                    Log.d(TAG, "call to alert: " + message);
                    NotificationUtil.getInstance().postNotification(NOTIF_ID,
                            R.drawable.ic_menu_emergency,
                            NotificationUtil.WHITE,
                            _mapView.getContext().getString(R.string.alert),
                            message, message, pending, vibrate, chime,
                            blink, true);
                    pastMessage = message;
                }
            }
        }

        // Refresh Overlay Manager any time an alert is added or removed
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.REFRESH_HIERARCHY));
    }

    /**
     * Add or update the alert
     * @param alert the alert to add or update
     */
    static public void addAlert(final Alert alert) {
        if (alert == null) {
            Log.w(TAG, "Skipping invalid alert");
            return;
        }

        if (alertWidget == null) {
            Log.d(TAG, "alerting still being initialized");
            return;
        }

        boolean bAlert = false;
        synchronized (alerts) {
            if (!_active) {
                Log.w(TAG, "Skipping add, not active");
                return;
            }

            int index = alerts.indexOf(alert);
            if (index >= 0) {
                Log.d(TAG, "Updating alert: " + alert);
                alerts.remove(alert);
                alerts.add(alert);
            } else {
                Log.d(TAG, "Adding alert: " + alert);
                alerts.add(alert);
                bAlert = true;
            }
        }

        updateOverlay(alert);
        alert(bAlert);
        alert(alert);
    }

    /**
     * Add or update the specified alerts
     * @param list the list of alerts to add
     */
    static private void addAlerts(Collection<? extends Alert> list) {
        if (list == null || list.size() < 1) {
            //Log.d(TAG, "No alerts to add");
            return;
        }

        boolean bAlert = false;
        synchronized (alerts) {
            if (!_active) {
                Log.w(TAG, "Skipping add, not active");
                return;
            }
            for (Alert alert : list) {
                int index = alerts.indexOf(alert);
                if (index >= 0) {
                    //Log.d(TAG, "Updating alert: " + alert.toString());
                    alerts.remove(index);
                    alerts.add(alert);
                } else {
                    Log.d(TAG, "Adding alert: " + alert.toString());
                    alerts.add(alert);
                    bAlert = true;
                }
                alert(alert);
            }
        }

        for (Alert alert : list)
            updateOverlay(alert);

        alert(bAlert);
    }

    /**
     * Add or update the specified alerts
     * Remove all other alerts of the specified clazz
     * This effectively replaces an entire category of alerts
     *
     * @param clazz the alert class
     * @param list the list of alerts to add
     */
    public static void addAlerts(Class<? extends Alert> clazz,
            Collection<? extends Alert> list) {

        // Nothing to do if there's no alerts
        if (FileSystemUtils.isEmpty(list))
            return;

        //first add/update alerts
        addAlerts(list);

        //now remove existing alerts not in the 'list'
        if (clazz != null) {
            synchronized (alerts) {
                if (!_active) {
                    Log.w(TAG, "Skipping add, not active");
                    return;
                }

                List<Alert> toRemove = new ArrayList<>();
                for (Alert a : alerts) {
                    if (clazz.isInstance(a) && !list.contains(a)) {
                        toRemove.add(a);
                    }
                }

                for (Alert a : toRemove) {
                    Log.d(TAG, "Removing alert: " + a.toString());
                    alerts.remove(a);
                }
            }
        }

        alert(false);
    }

    /**
     * Remove the specified alert
     * @param alert the specific alert to remove from the system
     */
    static public void removeAlert(Alert alert) {
        if (alert == null) {
            Log.w(TAG, "Skipping invalid alert");
            return;
        }

        synchronized (alerts) {
            if (!_active) {
                Log.w(TAG, "Skipping add, not active");
                return;
            }

            if (!alerts.remove(alert)) {
                Log.w(TAG, "Not alerting: " + alert);
                return;
            }

            Log.d(TAG, "Removing alert: " + alert);
        }

        alert(false);
    }

    /**
     * Remove all alerts of the specified clazz
     * @param clazz the class of alert to remove
     */
    static public void removeAlerts(final Class<? extends Alert> clazz) {
        if (clazz == null) {
            Log.w(TAG, "Skipping invalid alert class");
            return;
        }

        synchronized (alerts) {
            if (!_active) {
                Log.w(TAG, "Skipping add, not active");
                return;
            }

            List<Alert> toRemove = new ArrayList<>();
            for (Alert a : alerts) {
                if (clazz.isInstance(a)) {
                    toRemove.add(a);
                }
            }

            for (Alert a : toRemove) {
                Log.d(TAG, "Removing alert: " + a.toString());
                alerts.remove(a);
            }
        }

        alert(false);
    }

    /**
     * Remove the specified alerts
     *
     * @param list the list of alerts to remove
     */
    public static void removeAlerts(List<? extends Alert> list) {

        synchronized (alerts) {
            if (!_active) {
                Log.w(TAG, "Skipping add, not active");
                return;
            }

            List<Alert> toRemove = new ArrayList<>();
            for (Alert a : alerts) {
                if (list.contains(a)) {
                    toRemove.add(a);
                }
            }

            for (Alert a : toRemove) {
                Log.d(TAG, "Removing alert: " + a.toString());
                alerts.remove(a);
            }
        }

        alert(false);
    }

    /**
     * Get the list of active alerts
     * @return Active alerts
     */
    public static List<Alert> getAlerts() {
        synchronized (alerts) {
            return new ArrayList<>(alerts);
        }
    }

    /**
     * Get all alerts with the given group name
     * @param groupName Group name
     * @return List of alerts
     */
    public static List<Alert> getAlerts(String groupName) {
        List<Alert> ret = new ArrayList<>();
        synchronized (alerts) {
            for (Alert alert : alerts) {
                if (FileSystemUtils.isEquals(groupName,
                        alert.getAlertGroupName()))
                    ret.add(alert);
            }
        }
        return ret;
    }

    static private String refreshWidget() {
        String text;
        synchronized (alerts) {
            if (alerts.size() < 1) {
                text = null;
            } else if (alerts.size() == 1) {
                text = alerts.get(0).getMessage();
            } else {
                text = alerts.size()
                        + _mapView.getContext().getString(
                                R.string.notification_text27);
            }
        }

        //TODO limit max length of text...
        final String fText = text;
        MapView.getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (alertWidget == null)
                    return;
                if (FileSystemUtils.isEmpty(fText)) {
                    alertWidget.setVisible(false);
                } else {
                    alertWidget.setText(fText);
                    alertWidget.setVisible(true);
                }
            }
        });

        return fText;
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        synchronized (alerts) {
            _active = false;
            alerts.clear();
        }

        NotificationUtil.getInstance().clearNotification(NOTIF_ID);
        pastMessage = "";
    }

    private static void updateOverlay(Alert alert) {
        if (_overlayParent == null || alert == null)
            return;

        String groupName = alert.getAlertGroupName();
        if (FileSystemUtils.isEmpty(groupName)
                || _primaryGroups.contains(groupName))
            return;

        MapOverlay overlay = _overlayParent.get(groupName);
        if (overlay == null)
            _overlayParent.add(new AlertMapOverlay(_mapView, groupName));
    }
}
