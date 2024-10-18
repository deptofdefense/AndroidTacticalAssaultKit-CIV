
package com.atakmap.android.metricreport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cot.detail.TakVersionDetailHandler;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.metricreport.anrwatchdog.ANRError;
import com.atakmap.android.metricreport.anrwatchdog.ANRWatchDog;
import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.FixedQueue;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.ATAKApplication;
import com.atakmap.app.CrashListener;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.comms.CommsLogger;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.DispatchFlags;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import com.atakmap.util.zip.IoUtils;
import org.acra.util.ReportUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Simple reporting capability for metrics within ATAK.
 */
public class MetricReportMapComponent extends AbstractMapComponent
        implements OnSharedPreferenceChangeListener, CrashListener {

    public static final String TAG = "MetricReportMapComponent";

    private static final String GPU_STRING = "gpu";
    private static final String LIFECYCLE_STRING = "lifecycle";
    private static final String TYPE_STRING = "type";
    private static final String MAPEVENT_STRING = "mapevent";

    // for the json reports
    private static final String EXT_METRIC = ".json";
    private static final String PREFIX_METRIC = "metric-";

    private static final String EXT_COTDUMP = ".xml";

    /**
     * How often to capture device and app stats in the metrics
     */
    private static final long PERIODIC_METRIC = 15000;

    /**
     * How often to send live/realtime metrics over network
     * Currently every other time metrics are logged. every 30 seconds
     */
    private static final long REALTIME_METRIC_MOD = 2;

    /**
     * CoT stale is the realtime metric period, plus small buffer
     */
    private static final int REALTIME_METRIC_STALE = (int) (PERIODIC_METRIC
            * REALTIME_METRIC_MOD + PERIODIC_METRIC);

    private static final String REALTIME_METRICS_TYPE = "t-x-c-m";

    /**
     * Threshold for determining if screen/UI is locked/delayed
     */
    private static final int ANR_TIMEOUT = 5000;

    public Context pluginContext;
    public MapView view;
    private SharedPreferences prefs;
    private FileOutputStream fos;
    private String sessionid;
    private final FixedQueue<String> tail = new FixedQueue<>(25);
    private ANRWatchDog anrWatchDog;
    private Timer _timer;
    private long startTime;
    private long startUsageDataRx;
    private long startUsageDataTx;
    private List<String> bundleFilter;
    private CommsLogger commslogger;
    private boolean collecting = false;
    private DeviceStats stats;
    private boolean _realtime_metrics;
    private int periodicCnt;
    private CotDispatcher metricDispatcher;
    private boolean _hideLocation;

    private BroadcastReceiver _exportCrashLogRec;

    @Override
    public void onCreate(final Context context, Intent intent,
            final MapView view) {

        //context.setTheme(R.style.ATAKPluginTheme);

        // keep this around because we could ship this back off to a 
        // plugin in 3.12

        this.pluginContext = context;
        this.view = view;

        //do not record these fields
        bundleFilter = new ArrayList<>();
        bundleFilter.add("username");
        bundleFilter.add("password");
        bundleFilter.add("caPassword");
        bundleFilter.add("clientPassword");

        //Realtime metrics sent only to TAK Server
        metricDispatcher = new CotDispatcher();
        metricDispatcher.setDispatchFlags(
                DispatchFlags.EXTERNAL | DispatchFlags.RELIABLE);

        this.periodicCnt = 0;
        this.stats = new DeviceStats();

        // for custom preferences
        ToolsPreferenceFragment
                .register(
                        new ToolsPreferenceFragment.ToolPreference(
                                context.getString(R.string.metricreport_pref),
                                context.getString(
                                        R.string.metricreport_summary),
                                "metricsPreferences",
                                context.getResources().getDrawable(
                                        R.drawable.metrics),
                                new MetricReportPreferenceFragment(context)));

        this.prefs = PreferenceManager
                .getDefaultSharedPreferences(view.getContext());
        this._realtime_metrics = prefs.getBoolean("realtime_metrics", false);
        this._hideLocation = prefs.getBoolean("dispatchLocationHidden", false);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(
                "com.atakmap.app.ExportCrashLogsTask",
                "Triggered when the ExportCrashLogsTask is completed");

        AtakBroadcast.getInstance().registerReceiver(
                _exportCrashLogRec = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context,
                            Intent intent) {
                        if (prefs.getBoolean("collect_metrics", false)) {
                            synchronized (MetricReportMapComponent.this) {
                                stopMetricsCollection();
                                startMetricsCollection();
                            }
                        }
                    }
                }, filter);

        if (prefs.getBoolean("collect_metrics", false)) {
            startMetricsCollection();
        }

        // listen for all preference changes
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private synchronized void startMetricsCollection() {

        if (collecting)
            return;

        Log.d(TAG, "starting metrics collection");

        startTime = SystemClock.elapsedRealtime();

        // this is for all of the apps on the device not just this app
        startUsageDataRx = android.net.TrafficStats.getTotalRxBytes();
        startUsageDataTx = android.net.TrafficStats.getTotalTxBytes();

        setupLog();
        beginLog();

        // listen for all preference changes
        prefs.registerOnSharedPreferenceChangeListener(this);

        MetricsApi.register(receiver);

        AtakBroadcast.getInstance().registerSystemReceiver(
                batteryChangedReceiver,
                new DocumentedIntentFilter(Intent.ACTION_BATTERY_CHANGED));

        view.getMapEventDispatcher().addMapEventListener(mapEventListener);

        csr = new CotServiceRemote();
        csr.setOutputsChangedListener(_outputsChangedListener);

        csr.connect(cl);

        csl = new CotStreamListener(view.getContext(), TAG, null) {

            @Override
            public void onCotOutputRemoved(Bundle bundle) {
                record("stream", bundle, bundleFilter, prop("removed", "true"));
            }

            @Override
            protected void enabled(CotPortListActivity.CotPort port,
                    boolean enabled) {
                record("stream", port.getData(), bundleFilter,
                        prop("enabled", String.valueOf(enabled)));
            }

            @Override
            protected void connected(CotPortListActivity.CotPort port,
                    boolean connected) {
                record("stream", port.getData(), bundleFilter,
                        prop("connected", String.valueOf(connected)));
            }

            @Override
            public void onCotOutputUpdated(Bundle descBundle) {
                record("stream", descBundle, bundleFilter,
                        prop("updated", "true"));
            }
        };

        ATAKApplication.addCrashListener(this);

        //detect ANRs (UI thread starving)
        anrWatchDog = new ANRWatchDog(ANR_TIMEOUT);
        anrWatchDog.setANRListener(new ANRWatchDog.ANRListener() {
            @Override
            public void onAppNotResponding(ANRError error) {
                logAnr(error);
            }
        });

        anrWatchDog.start();

        //start time to periodcally capture resource usage stat
        _timer = new Timer("MertricMonitorThread");

        _timer.schedule(new TimerTask() {
            @Override
            public void run() {
                periodicMetric();
            }
        }, 0, PERIODIC_METRIC);

        // Only show hint if we're in the main activity
        // Workaround for displaying this hint properly when the preference is
        // modified within the settings activity
        Context ctx = view.getContext();
        if (ctx instanceof ATAKActivity && ((ATAKActivity) ctx).isActive()) {
            HintDialogHelper.showHint(ctx,
                    pluginContext.getString(R.string.metric_plugin_hint),
                    pluginContext
                            .getString(R.string.metric_plugin_hint_message),
                    "tak.hint.logging.metric");
        }

        CommsMapComponent.getInstance()
                .registerCommsLogger(commslogger = new CommsLogger() {

                    @Override
                    public void logSend(CotEvent cotEvent, String s) {
                        writeCotEventDetails(cotEvent);
                        writeCotEvent(cotEvent);

                    }

                    @Override
                    public void logSend(CotEvent cotEvent, String[] strings) {
                        writeCotEventDetails(cotEvent);
                        writeCotEvent(cotEvent);
                    }

                    @Override
                    public void logReceive(CotEvent cotEvent, String s,
                            String s1) {
                        writeCotEventDetails(cotEvent);
                        writeCotEvent(cotEvent);
                    }

                    @Override
                    public void dispose() {
                    }
                });
        collecting = true;

    }

    /**
     * Only used to record details so that there is a large group of them to generate
     * an XSD file from.  This should not be enabled by default.  Uses a rolling 
     * number to generate the last 250 received variations of that specific detail tag.
     *
     * @param ce the cot event for to use for the detail extraction.   
     */
    private void writeCotEventDetails(final CotEvent ce) {
        if (!prefs.getBoolean("generate_detail_pool", false))
            return;

        final CotDetail cd = ce.getDetail();
        for (int i = 0; i < cd.childCount(); i++) {
            final CotDetail child = cd.getChild(i);
            final String name = child.getElementName();
            int roll = prefs.getInt("metrics.detailscount." + name, 0);
            roll++;

            File f = FileSystemUtils.getItem("tools/metrics/details/" + name);
            if (!IOProviderFactory.exists(f))
                if (!IOProviderFactory.mkdirs(f))
                    return;

            f = new File(f, FileSystemUtils.sanitizeWithSpacesAndSlashes(
                    name + "-" + String.format(LocaleUtil.US, "%03d", roll)
                            + EXT_COTDUMP));
            try (OutputStream os = IOProviderFactory.getOutputStream(f)) {
                FileSystemUtils.write(os, child.toString());
            } catch (IOException ioe) {
                Log.e(TAG, "unable to write file: " + f, ioe);
            }
            prefs.edit().putInt("metrics.detailscount." + name, (roll % 250))
                    .apply();
        }
    }

    /**
     * Record the whole CotEvent in a directory described by its type.
     * @param ce the cot event to write
     */
    private void writeCotEvent(final CotEvent ce) {
        if (!prefs.getBoolean("generate_full_pool", false))
            return;

        final String type = ce.getType();
        File f = FileSystemUtils.getItem("tools/metrics/cot/" + type);
        if (!IOProviderFactory.exists(f))
            if (!IOProviderFactory.mkdirs(f))
                return;

        f = new File(f, type + "-" + System.currentTimeMillis()
                + EXT_COTDUMP);
        try (OutputStream os = IOProviderFactory.getOutputStream(f)) {
            FileSystemUtils.write(os, ce.toString());
        } catch (IOException ioe) {
            Log.e(TAG, "unable to write file: " + f, ioe);
        }
    }

    /**
     * Convert a list of props to a CoT detail
     *
     * @param label the label to turn into a cot detail
     * @param props the properties to set on the cot detail
     * @return a cot detail object with the label and attributes set based on
     * the properties passed in.
     */
    private CotDetail toDetail(String label, Prop[] props) {
        if (FileSystemUtils.isEmpty(label) || props == null) {
            Log.w(TAG, "toDetail invalid");
            return null;
        }

        CotDetail detail = new CotDetail(label);
        for (Prop p : props)
            detail.setAttribute(p.key, p.value);

        //Log.d(TAG, "toDetail " + label + " of size " + detail.getAttributeCount());
        return detail;
    }

    /**
     * Largely copied from CotMapComponent.sendSelfSA
     *
     * @param stats the detail version of the stats to be sent in real time.
     */
    private void sendRealtimeMetrics(CotDetail stats) {

        if (stats == null) {
            Log.w(TAG, "skipping invalid realtime metrics");
            return;
        }

        CotEvent cotEvent = getStatsEvent(stats);
        if (cotEvent == null || !cotEvent.isValid()) {
            Log.w(TAG, "sendRealtimeMetrics cot info not ready");
            return;
        }

        metricDispatcher.dispatch(cotEvent);
        //Log.d(TAG, "Sending realtime stats: " + cotEvent.toString());
    }

    /**
     * Get baseline self stats cot event
     * Largely a subset of CotMapComponent.getSelfEvent, but adds stats detail,
     * links to this ATAK, and changes type
     *
     *
     * @return the cot event based on the current self statistics much like a ppli message.
     * @param stats
     */
    private CotEvent getStatsEvent(CotDetail stats) {
        final Marker self = ATAKUtilities.findSelfUnplaced(view);
        final MapData mapData = view.getMapData();
        if (self == null || mapData == null) {
            Log.w(TAG, "getStatsEvent not ready");
            return null;
        }

        final String selfUid = self.getUID();
        final String selfType = mapData.getString("deviceType");
        if (FileSystemUtils.isEmpty(selfUid)
                || FileSystemUtils.isEmpty(selfType)) {
            Log.w(TAG, "getStatsEvent my info not ready");
            return null;
        }

        CotEvent cotEvent = new CotEvent();
        cotEvent.setVersion("2.0");

        cotEvent.setUID(UUID.randomUUID().toString());
        cotEvent.setType(REALTIME_METRICS_TYPE);

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addMilliseconds(REALTIME_METRIC_STALE));

        //Log.d(TAG, " time: " + time.getMilliseconds() + " stale: " + time.addSeconds(offset).getMilliseconds());

        CotDetail detail = new CotDetail("detail");
        cotEvent.setDetail(detail);

        detail.addChild(stats);

        final GeoPoint p = self.getPoint();

        if (_hideLocation)
            cotEvent.setPoint(CotPoint.ZERO);
        else
            cotEvent.setPoint(new CotPoint(p));

        if (mapData.getBoolean("fineLocationAvailable", false) &&
                (mapData.getParcelable("fineLocation") != null)) {
            // from the GPS

            if (SystemClock.elapsedRealtime()
                    - self.getMetaLong("gpsUpdateTick",
                            0) > LocationMapComponent.GPS_TIMEOUT_MILLIS) {
                cotEvent.setHow(mapData.getString("how", "h-e"));
            } else {
                cotEvent.setHow(mapData.getString("how", "m-g"));
            }
        } else if (mapData.getBoolean("mockLocationAvailable", false) &&
                (mapData.getParcelable("mockLocation") != null)) {
            // from an external source
            cotEvent.setHow(mapData.getString("how", "m-g"));
        } else if (ATAKUtilities.findSelf(view) != null) {
            // hand entered and on the map
            cotEvent.setHow(mapData.getString("how", "h-e"));
        } else {
            // not on the map
            cotEvent.setHow(mapData.getString("how", "h-g-i-g-o"));
        }

        //link to this ATAK
        CotDetail linkInfo = new CotDetail("link");
        detail.addChild(linkInfo);

        linkInfo.setAttribute("relation", "p-s"); // Parent of this object, source (this
        // object was derived from parent)
        linkInfo.setAttribute("uid", selfUid);
        linkInfo.setAttribute("type", selfType);

        return cotEvent;
    }

    private void logAnr(ANRError error) {
        Log.e(TAG, "detected application not responding");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        error.printStackTrace(pw);
        String trace = sw.toString();
        // sanitize the string for usage within json
        trace = sanitize(trace);
        record("anr", prop("thread_trace", trace));
    }

    private void periodicMetric() {

        //TODO what else network (using proto or not), how many connections to servers, running encrypted mesh, etc
        CotMapComponent cmc = CotMapComponent.getInstance();
        if (cmc == null || stats == null)
            return; //prevent crash during shut down.

        //update device stats. Note battery temp is set by OS on its own schedule
        stats.update(
                ReportUtils.getAvailableInternalMemorySize(),
                ReportUtils.getTotalInternalMemorySize(),
                Runtime.getRuntime().totalMemory(),
                Runtime.getRuntime().maxMemory(),
                Runtime.getRuntime().freeMemory(),
                view.getFramerate(),
                cmc.getBatteryPercent(),
                android.net.TrafficStats.getTotalRxBytes() - startUsageDataRx,
                android.net.TrafficStats.getTotalTxBytes() - startUsageDataTx,
                NetworkUtils.getIP());

        Prop[] props = stats.toProp();
        if (_realtime_metrics && (++periodicCnt % REALTIME_METRIC_MOD == 0)) {
            sendRealtimeMetrics(toDetail("stats", props));
        }

        //now log stats in metrics
        record("stats", props);
    }

    private void beginLog() {
        sessionid = prefs.getString("core_sessionid", "unknown");

        //capture HW/SW versions
        record(LIFECYCLE_STRING,
                prop(TYPE_STRING, "start"),
                prop(TakVersionDetailHandler.ATTR_PLATFORM,
                        ATAKConstants.getBrand()),
                prop(TakVersionDetailHandler.ATTR_VERSION,
                        ATAKConstants.getFullVersionName()),
                prop("TAK.uid", MapView.getDeviceUid()),
                prop(TakVersionDetailHandler.ATTR_DEVICE,
                        TakVersionDetailHandler.getDeviceDescription()),
                prop("plugin-api", ATAKConstants.getPluginApi(false)),
                prop(TakVersionDetailHandler.ATTR_OS,
                        ATAKConstants.getDeviceOS()));

        record(GPU_STRING, view.getGPUInfo());

        //capture plugins installed/loaded
        AtakPluginRegistry registry = AtakPluginRegistry.get();
        if (registry != null) {
            Set<AtakPluginRegistry.PluginDescriptor> allP = registry
                    .getPluginDescriptors();
            Set<String> loadedP = registry.getPluginsLoaded();

            for (AtakPluginRegistry.PluginDescriptor plugin : allP) {
                record(plugin, loadedP.contains(plugin.getPackageName()));
            }
        }
    }

    @Override
    public void onPause(Context context, MapView view) {
        if (collecting)
            record(LIFECYCLE_STRING, prop(TYPE_STRING, "pause"));
    }

    @Override
    public void onResume(Context context, MapView view) {
        if (collecting)
            record(LIFECYCLE_STRING, prop(TYPE_STRING, "resume"));
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        try {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _exportCrashLogRec);
        } catch (Exception e) {
            Log.e(TAG, "error unregistering exportCrashLogRec: ", e);
        }

        stopMetricsCollection();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    private synchronized void stopMetricsCollection() {
        if (!collecting)
            return;
        Log.d(TAG, "stopping metrics collection");

        collecting = false;
        MetricsApi.unregister(receiver);
        CommsMapComponent.getInstance().unregisterCommsLogger(commslogger);
        AtakBroadcast.getInstance()
                .unregisterSystemReceiver(batteryChangedReceiver);

        record(LIFECYCLE_STRING, prop(TYPE_STRING, "stop"), prop(
                "session_length_ms",
                String.valueOf(SystemClock.elapsedRealtime() - startTime)));
        ATAKApplication.removeCrashListener(this);

        view.getMapEventDispatcher().removeMapEventListener(mapEventListener);

        anrWatchDog.interrupt();

        if (_timer != null) {
            try {
                _timer.cancel();
            } catch (Exception ignored) {
            }
        }
        IoUtils.close(fos);
        fos = null;

    }

    @Override
    public CrashListener.CrashLogSection onCrash() {
        StringBuilder sb = new StringBuilder();
        synchronized (tail) {
            if (tail.size() < 1)
                return null;

            boolean first = true;
            for (String record : tail) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(record);
                sb.append("\n");
                first = false;
            }
        }
        return new CrashLogSection("Metrics", sb.toString());
    }

    private CotServiceRemote csr;

    private CotServiceRemote.ConnectionListener cl = new CotServiceRemote.ConnectionListener() {
        @Override
        public void onCotServiceConnected(Bundle fullServiceState) {
            record("stream_state", fullServiceState, bundleFilter,
                    prop("connected", "true"));
        }

        @Override
        public void onCotServiceDisconnected() {
            record("stream_state", prop("connected", "false"));
        }

    };

    CotStreamListener csl;

    private CotServiceRemote.OutputsChangedListener _outputsChangedListener = new CotServiceRemote.OutputsChangedListener() {
        @Override
        public void onCotOutputRemoved(Bundle descBundle) {
            record("stream", descBundle, bundleFilter, prop("removed", "true"));
        }

        @Override
        public void onCotOutputUpdated(Bundle descBundle) {
            record("stream", descBundle, bundleFilter, prop("added", "true"));
        }
    };

    private BroadcastReceiver batteryChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float batteryTemp = (float) (intent
                    .getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)) / 10;
            int status = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);

            String batteryStatus;
            if (status == BatteryManager.BATTERY_HEALTH_COLD)
                batteryStatus = "BATTERY_HEALTH_COLD";
            else if (status == BatteryManager.BATTERY_HEALTH_DEAD)
                batteryStatus = "BATTERY_HEALTH_DEAD";
            else if (status == BatteryManager.BATTERY_HEALTH_GOOD)
                batteryStatus = "BATTERY_HEALTH_GOOD";
            else if (status == BatteryManager.BATTERY_HEALTH_OVERHEAT)
                batteryStatus = "BATTERY_HEALTH_OVERHEAT";
            else if (status == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE)
                batteryStatus = "BATTERY_HEALTH_OVER_VOLTAGE";
            else if (status == BatteryManager.BATTERY_HEALTH_UNKNOWN)
                batteryStatus = "BATTERY_HEALTH_OVER_UNKNOWN";
            else if (status == BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE)
                batteryStatus = "BATTERY_HEALTH_UNSPECIFIED_FAILURE";
            else
                batteryStatus = "NO_STATUS";

            if (stats != null)
                stats.update(batteryTemp, batteryStatus);
        }
    };

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences prefs, final String key) {

        if (key == null)
            return;

        if (key.startsWith("collect_metrics")) {
            if (prefs.getBoolean("collect_metrics", false)) {
                startMetricsCollection();
            } else {
                stopMetricsCollection();
            }
        } else if (key.startsWith(AtakPluginRegistry.SHOULD_LOAD)) {
            String packageStr = key.substring(
                    AtakPluginRegistry.SHOULD_LOAD.length());
            AtakPluginRegistry.PluginDescriptor plugin = AtakPluginRegistry
                    .get().getPlugin(packageStr);
            if (plugin != null) {
                record(plugin, prefs.getBoolean(key, false));
            } else {
                record("plugin",
                        prop("package", packageStr),
                        prop("isLoaded",
                                String.valueOf(prefs.getBoolean(key, false))));
            }
        } else if (key.equals("realtime_metrics")) {
            _realtime_metrics = prefs.getBoolean("realtime_metrics", false);
        } else if (key.equals("dispatchLocationHidden")) {
            this._hideLocation = prefs.getBoolean("dispatchLocationHidden",
                    false);
        }

        // tricky because we do not know the type of preference represented by this key.
        String value = null;

        try {
            if (value == null)
                value = "" + prefs.getString(key, "");
        } catch (Exception ignored) {
        }
        try {
            if (value == null)
                value = "" + prefs.getInt(key, Integer.MAX_VALUE);
        } catch (Exception ignored) {
        }
        try {
            value = "" + prefs.getBoolean(key, false);
        } catch (Exception ignored) {
        }
        try {
            if (value == null)
                value = "" + prefs.getFloat(key, Float.NaN);
        } catch (Exception ignored) {
        }
        try {
            if (value == null)
                value = "" + prefs.getLong(key, Long.MAX_VALUE);
        } catch (Exception ignored) {
        }

        record("preference", prop("key", key), prop("value", value));
    }

    private void record(AtakPluginRegistry.PluginDescriptor plugin,
            boolean isLoaded) {
        if (plugin == null)
            return;

        Bundle cur = new Bundle();
        cur.putString("package", plugin.getPackageName());
        cur.putString("name", com.atakmap.android.update.AppMgmtUtils
                .getAppNameOrPackage(view.getContext(),
                        plugin.getPackageName()));
        cur.putString("versionName", com.atakmap.android.update.AppMgmtUtils
                .getAppVersionName(view.getContext(), plugin.getPackageName()));
        cur.putInt("versionCode", com.atakmap.android.update.AppMgmtUtils
                .getAppVersionCode(view.getContext(), plugin.getPackageName()));
        cur.putString("plugin-api", plugin.getPluginApi());
        cur.putBoolean("isLoaded", isLoaded);
        record("plugin", cur);
    }

    private final MapEventDispatcher.MapEventDispatchListener mapEventListener = new MapEventDispatcher.MapEventDispatchListener() {

        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getItem() != null) {
                record(MAPEVENT_STRING, prop(TYPE_STRING, event.getType()),
                        prop("item", event.getItem().getUID()));
            } else {
                record(MAPEVENT_STRING, prop(TYPE_STRING, event.getType()));
            }
        }
    };

    /**
     * General intent processing capability that can be used to show when a specific 
     * intent has been fired.
     */
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent i = intent.getParcelableExtra("intent");
            if (i != null) {
                i.setExtrasClassLoader(MetricReportMapComponent.this.getClass()
                        .getClassLoader());
                record("intent", i.getExtras(), null,
                        prop("action", i.getAction()));
                return;
            }

            String category = intent.getStringExtra("category");
            Bundle b = intent.getBundleExtra("bundle");
            if (b != null) {
                if (FileSystemUtils.isEmpty(category))
                    category = "intent";

                record(category, b, null, prop("action", intent.getAction()));
            }
        }
    };

    private void reapLogs() {
        int numOfDays = prefs.getInt("metricNumberOfDays", 30);
        long numOfMillis = numOfDays * 1000L * 60 * 60 * 24;

        long cutOff = CoordinatedTime.currentDate().getTime() - numOfMillis;

        File logFile = FileSystemUtils
                .getItem(FileSystemUtils.SUPPORT_DIRECTORY
                        + File.separatorChar + "logs");

        File[] files = IOProviderFactory.listFiles(logFile);
        if (files != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    LocaleUtil.getCurrent());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith(PREFIX_METRIC)
                        && name.endsWith(EXT_METRIC)) {
                    String s = name.substring(7, name.indexOf(EXT_METRIC));
                    try {
                        final Date d = sdf.parse(s,
                                new java.text.ParsePosition(0));
                        if (d != null) {
                            long lDate = d.getTime();
                            if (lDate < cutOff) {
                                if (!IOProviderFactory.delete(f))
                                    Log.d(TAG, "error deleting the file: " + f);
                            }
                        }

                    } catch (Exception e) {
                        Log.d(TAG,
                                "error determining the date the log was written: "
                                        + s);
                    }
                }
            }
        }

    }

    private void setupLog() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    LocaleUtil.getCurrent());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

            File logFile = FileSystemUtils
                    .getItem(FileSystemUtils.SUPPORT_DIRECTORY
                            + File.separatorChar + "logs"
                            + File.separatorChar + PREFIX_METRIC
                            + sdf.format(CoordinatedTime.currentDate())
                            + EXT_METRIC);
            if (logFile.getParentFile() != null
                    && !IOProviderFactory.exists(logFile.getParentFile())) {
                if (!IOProviderFactory.mkdirs(logFile.getParentFile())) {
                    Log.e(TAG, "Failed to make dir at: "
                            + logFile.getParentFile().getPath());
                }
            }
            fos = IOProviderFactory.getOutputStream(logFile);
            Log.d(TAG, "creating metrics report: " + logFile);

            reapLogs();

        } catch (Exception e) {
            Log.w(TAG, "Error creating metric log file", e);
        }
    }

    private static class Prop {
        String key;
        String value;

        Prop(String k, String v) {
            key = k;
            value = v;
        }
    }

    private static Prop prop(String key, String value) {
        return new Prop(key, value);
    }

    private static String sanitize(String s) {
        if (FileSystemUtils.isEmpty(s))
            return s;

        return s.replaceAll("\n", "\\\\n\\\\t").replaceAll("\t", "")
                .replaceAll("\"", "'");
    }

    private void record(String label, Prop... props) {
        record(label, null, props);
    }

    private void record(String label, List<String> filter, Prop... props) {
        if (props == null)
            return;

        Bundle b = new Bundle();
        for (Prop p : props) {
            b.putString(p.key, p.value);
        }

        record(label, b, filter);
    }

    private void record(String label, Bundle b, List<String> filter,
            Prop... props) {
        Bundle bundle = b;
        if (bundle == null)
            bundle = new Bundle();
        bundle.setClassLoader(
                MetricReportMapComponent.this.getClass().getClassLoader());

        if (props != null) {
            for (Prop p : props) {
                bundle.putString(p.key, p.value);
            }
        }

        record(label, bundle, filter);
    }

    private void record(String label, Bundle b) {
        record(label, b, null);
    }

    private void record(String label, Bundle b, List<String> filter) {
        String message = "{\"category\":\"" + sanitize(label) + "\""
                + ",\"sessionid\":\"" + sessionid + "\""
                + ",\"time\":\"" + new CoordinatedTime().getMilliseconds()
                + "\""
                + ",\"system_rtclock\":\"" + SystemClock.elapsedRealtime()
                + "\"";
        message += ", ";
        message += com.atakmap.android.util.BundleUtils.bundleToString(b,
                filter) + "}";
        record(message);
    }

    private void record(final String message) {
        if (FileSystemUtils.isEmpty(message)) {
            Log.w(TAG, "Skipping empty message");
            return;
        }

        try {
            if (fos != null) {
                fos.write((message + "\n")
                        .getBytes(FileSystemUtils.UTF8_CHARSET));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not record: " + e.getMessage() + ", "
                    + message);
        }

        synchronized (tail) {
            tail.add(message);
        }
    }

    /**
     * Tracking moving average of device stats
     */
    private static class DeviceStats {
        //variable
        long storage_available;
        long heap_current_size;
        long heap_free_size;
        double app_framerate;
        int battery;
        float battery_temp = -1;
        String battery_status = "NO_STATUS";
        long deviceDataRx;
        long deviceDataTx;
        String ip_address;

        //static
        long storage_total;
        long heap_max_size;

        void update(long storage_available,
                long storage_total,
                long heap_current_size,
                long heap_max_size,
                long heap_free_size,
                double app_framerate,
                int battery,
                long deviceDataRx,
                long deviceDataTx,
                String ip_address) {
            //TODO we currently store latest value, may update to track moving averages
            this.storage_available = storage_available;
            this.heap_current_size = heap_current_size;
            this.heap_free_size = heap_free_size;
            this.app_framerate = app_framerate;
            this.battery = battery;
            this.deviceDataRx = deviceDataRx;
            this.deviceDataTx = deviceDataTx;
            this.ip_address = ip_address;

            //static
            this.storage_total = storage_total;
            this.heap_max_size = heap_max_size;
        }

        public void update(float batteryTemp, String batteryStatus) {
            this.battery_temp = batteryTemp;
            this.battery_status = batteryStatus;
        }

        public Prop[] toProp() {
            String batteryLevel = "" + battery + "%";

            return new Prop[] {
                    prop("storage_available",
                            String.valueOf(
                                    ReportUtils
                                            .getAvailableInternalMemorySize())),
                    prop("storage_total",
                            String.valueOf(
                                    ReportUtils.getTotalInternalMemorySize())),
                    prop("heap_current_size",
                            String.valueOf(
                                    Runtime.getRuntime().totalMemory())),
                    prop("heap_max_size",
                            String.valueOf(
                                    Runtime.getRuntime().maxMemory())),
                    prop("heap_free_size",
                            String.valueOf(
                                    Runtime.getRuntime().freeMemory())),
                    prop("app_framerate",
                            String.valueOf(Math.round(app_framerate))),
                    prop("battery", batteryLevel),
                    prop("battery_temp",
                            String.valueOf(Math.round(battery_temp))),
                    prop("battery_status", battery_status),
                    prop("deviceDataRx", String.valueOf(deviceDataRx)),
                    prop("deviceDataTx", String.valueOf(deviceDataTx)),
                    prop("ip_address", ip_address)
            };
        }
    }

}
