
package com.atakmap.android.nightvision;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.view.Gravity;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.update.ApkUpdateReceiver;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.log.Log;

/**
 *
 * a Map Widget used to display an icon that catches press events and long press events
 * when a user pressed the widget the current dim value of Night Vision application is engaged, when
 * long pressing widget open a seekbar in the center bottom of the current map view. This allow a user to adjust the current dim value
 * by sliding the indicator right or left. If the Night Vision application is not installed the widget will not show
 */
public class NightVisionMapWidgetComponent extends AbstractWidgetMapComponent
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "NightVisionMapWidgetComponent";

    private NightVisionMapWidget widget, selfWidget;
    private NightVisionReceiver nightVisionReceiver;
    private SharedPreferences _prefs;
    private NightVisionNotification nightVisionNotification;
    private boolean nvInstalled;
    private MapView view;

    @Override
    protected void onCreateWidgets(final Context context, Intent intent,
            MapView view) {

        this.view = view;

        _prefs = PreferenceManager.getDefaultSharedPreferences(view
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);

        testForNightVisionApplication(false);

        DocumentedIntentFilter packageFilter = new DocumentedIntentFilter();
        packageFilter.addAction(ApkUpdateReceiver.APP_ADDED);
        packageFilter.addAction(ApkUpdateReceiver.APP_REMOVED);
        AtakBroadcast.getInstance().registerReceiver(packageReceiver,
                packageFilter);

        RootLayoutWidget root = (RootLayoutWidget) view.getComponentExtra(
                "rootLayoutWidget");
        LinearLayoutWidget left = root
                .getLayout(RootLayoutWidget.LEFT_EDGE)
                .getOrCreateLayout("LE_VE");
        LinearLayoutWidget selfTray = root
                .getLayout(RootLayoutWidget.BOTTOM_RIGHT)
                .getOrCreateLayout("SelfLocTray_H");

        left.setGravity(Gravity.CENTER_VERTICAL);
        left.setLayoutParams(LinearLayoutWidget.WRAP_CONTENT,
                LinearLayoutWidget.WRAP_CONTENT);

        //wrap map widget for left side
        widget = new NightVisionMapWidget(view);
        widget.setName("NightVision Left Edge");
        widget.setMargins(16f, 0f, 0f, 0f);
        widget.setIcon(widget.createIcon(55));
        widget.addOnClickListener(widget);
        widget.addOnLongPressListener(widget);
        widget.setVisible(false);
        left.addChildWidgetAt(0, widget);

        //wrap map widget for self widget location
        selfWidget = new NightVisionMapWidget(view);
        selfWidget.setName("NightVision Self Loc");
        selfWidget.setMargins(0f, 0f, 32f, 0f);
        selfWidget.setIcon(selfWidget.createIcon(32));
        selfWidget.addOnClickListener(selfWidget);
        selfWidget.addOnLongPressListener(selfWidget);
        selfWidget.setVisible(false);
        selfTray.addWidget(selfWidget);

        nightVisionNotification = new NightVisionNotification();
        DocumentedIntentFilter iFilter = new DocumentedIntentFilter();
        iFilter.addAction(NightVisionNotification.NOTIFICATION_CONTROL);
        AtakBroadcast.getInstance().registerSystemReceiver(
                nightVisionNotification, iFilter);

        AtakBroadcast.getInstance().registerReceiver(
                nightVisionNotification, iFilter);

        //receiver used to adjust brightness value inside atak
        nightVisionReceiver = new NightVisionReceiver(view);
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(NightVisionReceiver.ADJUST_NIGHT_VISION_VALUE);
        filter.addAction(NightVisionReceiver.UPDATE_DIM_VALUE);
        filter.addAction(NightVisionReceiver.GET_DIM_VALUE);

        //register an in house receiver and a system receiver
        AtakBroadcast.getInstance().registerReceiver(nightVisionReceiver,
                filter);
        AtakBroadcast.getInstance().registerSystemReceiver(nightVisionReceiver,
                filter);

        nvInstalled = AppMgmtUtils.isInstalled(view.getContext(),
                "com.atak.nightvision");
        launchNightVisionLocation(_prefs);//add the NV type if available onStart()

        //need to handle map resizes due to dropdowns and other tools being used
        Log.d(TAG, "onCreateWidgets");
    }

    private void testForNightVisionApplication(boolean changeFromInstaller) {

        final MapView mapView = MapView.getMapView();

        Resources res = mapView.getResources();

        //test the installer for the NV app,
        //if NVG app is installed then post a preference to launch settings if selected
        if (AppMgmtUtils.isInstalled(mapView.getContext(),
                "com.atak.nightvision")) {
            Drawable nvIcon;
            try {
                nvIcon = (mapView.getContext().getPackageManager()
                        .getApplicationIcon("com.atak.nightvision"));
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "error occurred", e);
                nvIcon = null;
                //no icon found no icon on preference simple! :)
            }
            ToolsPreferenceFragment
                    .register(new ToolsPreferenceFragment.ToolPreference(
                            res.getString(
                                    R.string.preferences_access_night_vision_settings),
                            res.getString(
                                    R.string.preferences_night_vision_settings_summary),
                            "nv_preferences",
                            nvIcon, new NightVisionPreferenceFragment()));

            if (changeFromInstaller)
                launchNVExternal();

        } else {
            //if nv was uninstalled outside of this activity with same return
            ToolsPreferenceFragment.unregister("nv_preferences");
        }
    }

    /**
     * listen for when app/plugins are installed /uninstalled
     * we only care about the night vision app here so filter that out
     */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received Night Vision event " + action);
            if (action != null && (action.equals(ApkUpdateReceiver.APP_ADDED)
                    || action.equals(ApkUpdateReceiver.APP_REMOVED))) {
                if (intent.hasExtra("package")) {
                    String s = intent.getStringExtra("package");
                    if (s != null && s.equals("com.atak.nightvision")) {
                        testForNightVisionApplication(true);
                    }
                }
            }
        }
    };

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        if (nightVisionReceiver != null) {
            nightVisionReceiver.onDestroy();
            AtakBroadcast.getInstance().unregisterSystemReceiver(
                    nightVisionReceiver);
        }

        if (nightVisionNotification != null) {
            nightVisionNotification.cancelNotification();
            AtakBroadcast.getInstance().unregisterSystemReceiver(
                    nightVisionNotification);
        }
        _prefs.unregisterOnSharedPreferenceChangeListener(this);
        AtakBroadcast.getInstance().unregisterReceiver(packageReceiver);
    }

    /*
        in case the user hard closes ATAK onDestroy might not get called
        so lets make sure we handle this notification Just in case -SA
     */
    @Override
    public void onPause(Context context, MapView view) {
        nightVisionNotification.onPause();
    }

    @Override
    public void onResume(Context context, MapView view) {
        Log.d(TAG, "onResume()");
        loadOrRemove();
    }

    private void loadOrRemove() {
        //if app was uninstalled outside of atak then remove all instance of the night
        //vision locations by setting false to the parent preference causes all locational
        //widgets to be dismissed and nulled

        //was the app uninstalled then reinstalled? IF so we need to rerun the
        // mainactivity code for NV
        if (!nvInstalled
                && AppMgmtUtils.isInstalled(view.getContext(),
                        "com.atak.nightvision")) {
            launchNVExternal();
        }
        nvInstalled = AppMgmtUtils.isInstalled(view.getContext(),
                "com.atak.nightvision");
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!AppMgmtUtils.isInstalled(view.getContext(),
                        "com.atak.nightvision")) {
                    _prefs.edit().putBoolean("night_vision_widget", false)
                            .apply();
                    return;
                }
                launchNightVisionLocation(_prefs);
            }
        }, 99);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals("night_vision_widget")) {
            if (p.getBoolean(key, false))
                launchNightVisionLocation(p);
            else
                removeAllLocations();
        } else if (key.equals("loc_notification")
                || key.equals("loc_self_overlay_box") ||
                key.equals("loc_map_widget")) {
            launchNightVisionLocation(p);
        }
    }

    private void removeAllLocations() {
        this.widget.setVisible(false);
        this.selfWidget.setVisible(false);
        nightVisionNotification.cancelNotification();
    }

    /**
     * other classes should not be calling this, this is reserved for binding the receivers
     * used to connect ATAk with Night Vision application
     */
    private static void launchNVExternal() {
        if (!AppMgmtUtils.isAppRunning(MapView
                .getMapView().getContext(), "com.atak.nightvision")) {
            Intent launchIntent = MapView.getMapView().getContext()
                    .getPackageManager()
                    .getLaunchIntentForPackage("com.atak.nightvision");
            if (launchIntent != null) {//null pointer check in case package name was not found
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                launchIntent.putExtra("setup_receivers", true);
                MapView.getMapView().getContext()
                        .startActivity(launchIntent);
            } else {
                Log.d(TAG, "Package Name Not Found");
            }
        }
    }

    private void launchNightVisionLocation(SharedPreferences preferences) {

        if (!nvInstalled || !preferences.getBoolean("night_vision_widget",
                false)) {
            removeAllLocations();
            return;
        }

        //handle the side map widget
        this.widget.setVisible(preferences.getBoolean("loc_map_widget", true));
        this.selfWidget.setVisible(preferences.getBoolean(
                "loc_self_overlay_box", false));

        //handle notification
        if (preferences.getBoolean("loc_notification", false))
            nightVisionNotification.dispatchNotification();
        else
            nightVisionNotification.cancelNotification();
    }
}
