
package com.atakmap.comms;

import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.*;
import java.security.cert.Certificate;
import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.net.CertificateManager;

import android.content.BroadcastReceiver;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import android.net.wifi.WifiManager;

import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import com.atakmap.android.ipc.AtakBroadcast;

import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.network.ui.CredentialsPreference;
import com.atakmap.app.R;
import com.atakmap.app.preferences.NetworkConnectionPreferenceFragment;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackagePreferenceListener;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.http.MissionPackageDownloader;

import com.atakmap.android.http.rest.ServerVersion;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.comms.app.CotPortListActivity.CotPort;
import com.atakmap.comms.missionpackage.MPReceiveInitiator;
import com.atakmap.comms.missionpackage.MPReceiver;
import com.atakmap.comms.missionpackage.MPSendListener;
import com.atakmap.comms.missionpackage.MPSendListener.UploadStatus;

import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import com.atakmap.app.CrashListener;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.filesharing.android.service.WebServer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;

import java.util.concurrent.ConcurrentLinkedQueue;
import com.atakmap.commoncommo.*;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.util.zip.IoUtils;

public class CommsMapComponent extends AbstractMapComponent implements
        CoTMessageListener, ContactPresenceListener, InterfaceStatusListener,
        CoTSendFailureListener, OnSharedPreferenceChangeListener,
        CrashListener {
    private static final String TAG = "CommsMapComponent";

    private static boolean commoNativeInitComplete = false;

    private static CommsMapComponent _instance;
    private final NetworkManagerLite networkManagerLite = new NetworkManagerLite();

    private final int SCAN_WAIT = 45 * 1000; // 45 seconds

    /**
     *  Status of an import, either one of
     *     SUCCESS - no problems
     *     FAILURE - problems not importable.
     *     DEFERRED - waiting on another item, so try again at the end.
     *     IGNORE - no problems, but also not handled
     */
    public enum ImportResult {
        SUCCESS(1),
        FAILURE(3),
        DEFERRED(2),
        IGNORE(0);

        private final int priority;

        ImportResult(int priority) {
            this.priority = priority;
        }

        /**
         * Compare this import result with another and return the result with
         * higher priority. This is useful when performing multiple import
         * operations in a method where a single result is returned.
         * IGNORE < SUCCESS < DEFERRED < FAILURE
         *
         * @param other Other result
         * @return The result with higher priority
         */
        public ImportResult getHigherPriority(ImportResult other) {
            return this.priority >= other.priority ? this : other;
        }
    }

    /**
     * Interface for <code>AbstractInput</code> & <code>AbstractStreaming</code> to directly
     * process an event rather than using a <code>CotDispatcher</code>
     *
     * If set, this is used prior rather than dispatching
     */
    public interface DirectCotProcessor {
        ImportResult processCotEvent(CotEvent event, Bundle extra);
    }

    /**
     * Interface for a single plugin to post process a CotEvent provided a list of uids.   
     * The specific use case is for using a NettWarrior system where the Cursor on Target 
     * message requires outgoing routing information not unlike what is being added for 
     * communication through a TAK Server.   Care should be taken not to break the outgoing 
     * CotEvent Object.
     */
    public interface PreSendProcessor {

        /**
         * Processes a CotEvent and a list of uids as the final step before the event is sent 
         * to the common communication library.
         * Please be warned that no attempt should be made send the CotEvent from this method 
         * and this should only be used for fixing up the CotEvent based on NW requirements.
         * @param event the event that has been created for sending.
         * @param uids the list of uids for sending.  Can be null for broadcast.
         */
        void processCotEvent(CotEvent event, String[] uids);
    }

    private MapView mapView;
    private Commo commo;
    private CotService cotService;
    private DirectCotProcessor directProcessor;
    private PreSendProcessor preSendProcessor;
    private String errStatus;
    private TAKServerListener takServerListener;
    private File httpsCertFile;

    // CoT messages received before the map components finished loading
    private boolean componentsLoaded;
    private final List<Pair<String, String>> deferredMessages = new ArrayList<>();

    // used for recording bi-directional communications to and from the system
    // should not be used for anything more than that.
    private final ConcurrentLinkedQueue<CommsLogger> loggers = new ConcurrentLinkedQueue<>();

    private final Set<String> hwAddressesIn;
    private final Set<String> hwAddressesOut;

    private static class InputPortInfo {
        CotPort inputPort;
        int netPort;
        String mcast; // might be null for no mcast
        boolean isTcp;

        InputPortInfo(CotPort input, int port, String mcastAddr) {
            inputPort = input;
            netPort = port;
            mcast = mcastAddr;
            isTcp = false;
        }

        InputPortInfo(CotPort input, int port) {
            inputPort = input;
            netPort = port;
            mcast = null;
            isTcp = true;
        }
    }

    private static class OutputPortInfo {
        CotPort outputPort;
        int netPort;
        String mcast;

        OutputPortInfo(CotPort output, int port, String mcastAddr) {
            outputPort = output;
            netPort = port;
            mcast = mcastAddr;
        }
    }

    private MasterMPIO mpio;
    private MasterFileIO masterFileIO;
    // Contains all ports, registered or not
    private final Map<String, InputPortInfo> inputPorts;
    // Only the addresses from the enabled inputs 
    private final Map<Integer, Set<String>> mcastAddrsByPort;

    private final Map<String, OutputPortInfo> outputPorts;
    private final Map<String, CotPort> streamPorts;
    private final Map<String, String> streamKeys;
    private final Map<String, Integer> streamNotificationIds;

    private final Map<Integer, List<PhysicalNetInterface>> inputIfaces;
    private final Map<String, TcpInboundNetInterface> tcpInputIfaces;
    private final Map<String, List<PhysicalNetInterface>> broadcastIfaces;
    private final Map<String, StreamingNetInterface> streamingIfaces;

    private final Map<String, Contact> uidsToContacts;
    private final ConcurrentLinkedQueue<CotServiceRemote.CotEventListener> cotEventListeners = new ConcurrentLinkedQueue<>();

    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager wifi;
    private Thread scannerThread;
    private boolean rescan = true;
    private boolean pppIncluded = false;
    private OutboundLogger outboundLogger;

    private volatile boolean nonStreamsEnabled;
    private volatile boolean filesharingEnabled;
    private volatile int localWebServerPort;
    private volatile int secureLocalWebServerPort;

    private static class Logger implements CommoLogger {
        private final String tag;

        Logger(String tag) {
            this.tag = tag;
        }

        @Override
        public synchronized void log(Level level, Type type, String s,
                LoggingDetail detail) {
            int priority;
            switch (level) {
                case DEBUG:
                    priority = Log.DEBUG;
                    break;
                case ERROR:
                    priority = Log.ERROR;
                    break;
                case INFO:
                    priority = Log.INFO;
                    break;
                case VERBOSE:
                    priority = Log.VERBOSE;
                    break;
                case WARNING:
                    priority = Log.WARN;
                    break;
                default:
                    priority = Log.INFO;
                    break;
            }
            Log.println(priority, tag, s);
        }
    }

    public CommsMapComponent() {
        inputIfaces = new HashMap<>();
        tcpInputIfaces = new HashMap<>();
        broadcastIfaces = new HashMap<>();
        streamingIfaces = new HashMap<>();
        uidsToContacts = new HashMap<>();
        outputsChangedListeners = new HashSet<>();
        inputsChangedListeners = new HashSet<>();
        streamPorts = new HashMap<>();
        streamKeys = new HashMap<>();
        outputPorts = new HashMap<>();
        inputPorts = new HashMap<>();
        mcastAddrsByPort = new HashMap<>();
        streamNotificationIds = new HashMap<>();
        hwAddressesIn = new HashSet<>();
        hwAddressesOut = new HashSet<>();
    }

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        httpsCertFile = new File(context.getFilesDir(), "httpscert.p12");
        if (!commoNativeInitComplete) {
            try {
                com.atakmap.coremap.loader.NativeLoader
                        .loadLibrary("commoncommojni");
                Commo.initThirdpartyNativeLibraries();
                commoNativeInitComplete = true;
            } catch (Error e) {
                Log.e(TAG, "common communication native lib load failure", e);
                errStatus = "commo native load fail";
            } catch (CommoException e) {
                Log.e(TAG,
                        "common communication native lib initialization failure",
                        e);
                errStatus = "commo native init fail";
            }
            Log.d(TAG,
                    "initialized the common communication layer native libraries");
        }

        this.outboundLogger = new OutboundLogger(context);
        loggers.add(this.outboundLogger);
        try {
            Log.d(TAG,
                    "acquire the multicast lock so the wifi does not deep sleep");
            wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            multicastLock = wifi
                    .createMulticastLock("mulicastLock in CommsMapComponent");
            multicastLock.acquire();
            Log.d(TAG, "acquired multicast lock...");
        } catch (Exception e) {
            Log.d(TAG, "failed to acquired multicast lock...");
        }

        try {
            wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                wifiLock = wifi.createWifiLock(
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                        "wifi_lock");
                wifiLock.acquire();
            }
            Log.d(TAG, "acquired wifi lock...");

        } catch (Exception e) {
            Log.d(TAG, "failed to acquired wifi lock...");
        }

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        if (!prefs.contains("enableNonStreamingConnections")) {
            Log.d(TAG, "setting the default mesh networking state to " +
                    BuildConfig.MESH_NETWORK_DEFAULT);
            prefs.edit().putBoolean("enableNonStreamingConnections",
                    BuildConfig.MESH_NETWORK_DEFAULT).apply();
        }
        if (!prefs.contains("mockingOption")) {
            Log.d(TAG, "setting the default gps listening state to: " +
                    (BuildConfig.MESH_NETWORK_DEFAULT ? "WRGPS" : "LocalGPS"));
            prefs.edit().putString("mockingOption",
                    BuildConfig.MESH_NETWORK_DEFAULT ? "WRGPS" : "LocalGPS")
                    .apply();
        }
        if (!prefs.contains("nonBluetoothLaserRangeFinder")) {
            Log.d(TAG,
                    "setting the default non-bluetooth laser range finder listening state to "
                            +
                            BuildConfig.MESH_NETWORK_DEFAULT);
            prefs.edit().putBoolean("nonBluetoothLaserRangeFinder",
                    BuildConfig.MESH_NETWORK_DEFAULT).apply();

        }

        prefs.registerOnSharedPreferenceChangeListener(this);

        if (commoNativeInitComplete && commo == null) {

            mapView = view;
            nonStreamsEnabled = true;
            filesharingEnabled = false;
            localWebServerPort = Commo.MPIO_LOCAL_PORT_DISABLE;
            secureLocalWebServerPort = WebServer.DEFAULT_SECURE_SERVER_PORT;

            final String uid = view.getSelfMarker().getUID();
            final String callsign = view.getSelfMarker().getMetaString(
                    "callsign", "nocallsign");

            try {
                commo = new Commo(new Logger(TAG + "Commo"), uid, callsign,
                        NetInterfaceAddressMode.NAME);
                commo.addCoTMessageListener(this);
                commo.addInterfaceStatusListener(this);
                commo.addContactPresenceListener(this);
                commo.addCoTSendFailureListener(this);
                commo.setStreamMonitorEnabled(prefs.getBoolean(
                        "monitorServerConnections", true));
                commo.setMulticastLoopbackEnabled(prefs.getBoolean(
                        "network_multicast_loopback", false));

                rekey();

                // Adjust if below the minimum supported value.  Pre-commo support default
                // was lower than the minimum supported value
                int xferTimeout = MissionPackagePreferenceListener.getInt(
                        prefs,
                        MissionPackagePreferenceListener.filesharingTransferTimeoutSecs,
                        MissionPackageReceiver.DEFAULT_TRANSFER_TIMEOUT_SECS);
                if (xferTimeout < MissionPackageReceiver.DEFAULT_TRANSFER_TIMEOUT_SECS) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(
                            MissionPackagePreferenceListener.filesharingTransferTimeoutSecs,
                            String.valueOf(
                                    MissionPackageReceiver.DEFAULT_TRANSFER_TIMEOUT_SECS));
                    editor.apply();
                    xferTimeout = MissionPackageReceiver.DEFAULT_TRANSFER_TIMEOUT_SECS;
                }

                try {
                    commo.setMissionPackageNumTries(
                            MissionPackagePreferenceListener.getInt(
                                    prefs,
                                    MissionPackagePreferenceListener.fileshareDownloadAttempts,
                                    MissionPackageDownloader.DEFAULT_DOWNLOAD_RETRIES));
                    commo.setMissionPackageConnTimeout(
                            MissionPackagePreferenceListener.getInt(
                                    prefs,
                                    MissionPackagePreferenceListener.filesharingConnectionTimeoutSecs,
                                    MissionPackageReceiver.DEFAULT_CONNECTION_TIMEOUT_SECS));
                    commo.setMissionPackageTransferTimeout(xferTimeout);
                    commo.setMissionPackageHttpsPort(SslNetCotPort
                            .getServerApiPort(SslNetCotPort.Type.SECURE));
                    commo.setMissionPackageHttpPort(SslNetCotPort
                            .getServerApiPort(SslNetCotPort.Type.UNSECURE));
                } catch (CommoException ex) {
                    // if these fail, ignore error - not fatal
                    Log.e(TAG,
                            "Unable to setup mission package parameters in preferences during startup - invalid values found");
                }
                commo.setEnableAddressReuse(true);

                mpio = new MasterMPIO();
                commo.setupMissionPackageIO(mpio);
                // Do this now to force sync between commo state and our initial preference state
                mpio.reconfigFileSharing();

                masterFileIO = new MasterFileIO();
                commo.enableSimpleFileIO(masterFileIO);

                // check for the existance of the software on the MAGTAB 
                // com.kranzetech.tvpn
                if (AppMgmtUtils.isInstalled(view.getContext(),
                        "com.kranzetech.tvpn")) {
                    Log.d(TAG, "detected Tactical VPN by Kranze Technologies");
                    commo.setMagtabWorkaroundEnabled(true);
                } else {
                    Log.d(TAG, "did not detected Tactical VPN software");
                }

                if (prefs.getBoolean("ppp0_highspeed_capable", false)) {
                    setPPPIncluded(true);
                }

                Log.d(TAG,
                        "initialized the common communication layer instance");
            } catch (CommoException e) {

                Log.d(TAG,
                        "failed initialized the common communication layer instance",
                        e);
                NotificationUtil.getInstance().postNotification(
                        31345,
                        NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                        NotificationUtil.RED,
                        context.getString(R.string.network_library_error),
                        context.getString(R.string.comms_error), null,
                        false);

                errStatus = "commo initialization failed";
            }

            // likely not needed but for semantic symmetry
            hwAddressesOut.clear();
            hwAddressesIn.clear();
            hwAddressesIn.addAll(scanInterfaces(true));
            hwAddressesOut.addAll(scanInterfaces(true));
            scannerThread = new Thread("CommsMapComponent iface scan") {
                @Override
                public void run() {
                    while (rescan) {
                        try {
                            Thread.sleep(SCAN_WAIT);
                        } catch (InterruptedException ignored) {
                        }
                        rescanInterfaces();
                    }
                }
            };
            scannerThread.setPriority(Thread.NORM_PRIORITY);
            scannerThread.start();
        }

        setPreferStreamEndpoint(
                prefs.getBoolean("autoDisableMeshSAWhenStreaming", false));

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(CredentialsPreference.CREDENTIALS_UPDATED);
        filter.addAction(
                NetworkConnectionPreferenceFragment.CERTIFICATE_UPDATED);
        this.registerReceiver(context, _credListener, filter);

        // Listen for when all the other map components have finished loading
        filter = new DocumentedIntentFilter(
                "com.atakmap.app.COMPONENTS_CREATED");
        registerReceiver(context, _componentsListener, filter);

        // listen for network changes from network monitor
        DocumentedIntentFilter networkFilter = new DocumentedIntentFilter();
        networkFilter.addAction(NetworkManagerLite.NETWORK_STATUS_CHANGED);
        networkFilter.addAction(NetworkManagerLite.NETWORK_LIST);
        AtakBroadcast.getInstance().registerSystemReceiver(networkManagerLite,
                networkFilter);
        AtakBroadcast.getInstance().sendSystemBroadcast(
                new Intent(NetworkManagerLite.LIST_NETWORK));

        commo.registerFileIOProvider(new com.atakmap.comms.FileIOProvider());
        _instance = this;
        cotService = new CotService(context);

        // TAK server singleton for developer convenience
        this.takServerListener = new TAKServerListener(view);

    }

    /**
     * Registers a FileIOProvider with Commo
     * @param provider The provider to register
     */
    public void registerFileIOProvider(FileIOProvider provider) {
        if (commo != null) {
            commo.registerFileIOProvider(provider);
        }
    }

    /**
     * Unregisters a FileIOProvider from Commo
     * @param provider The provider to unregister
     */
    public void unregisterFileIOProvider(FileIOProvider provider) {
        if (commo != null) {
            commo.unregisterFileIOProvider(provider);
        }
    }

    /**
     * Registers a communication logger with the communication map component.
     * Please note that doing could potentially have serious performance ramifications
     * on the rest of ATAK and this should only be done for the purposes of gathering
     * metric or information gathering.    The callback should be shortlived since it will 
     * cause a delay in the rest of the processing chain.
     * @param logger the implementation of the comms logger.
     */
    public void registerCommsLogger(CommsLogger logger) {
        if (logger == null)
            return;

        if (!loggers.contains(logger)) {
            Log.w(TAG, "CommsLogger has been registered with the system: "
                    + logger.getClass() +
                    "\n         *** This will likely impact system performance ***");
            loggers.add(logger);
        }
    }

    /**
     * Allows a communication logger to be removed from the communication map component.
     */
    public void unregisterCommsLogger(CommsLogger logger) {
        loggers.remove(logger);
    }

    @Override
    public CrashLogSection onCrash() {
        Log.d(TAG, "shutting down commo due to crash");
        commo.shutdown();
        Log.d(TAG, "commo shutdown due to crash complete");
        commo = null;
        return null;
    }

    /**
     * Set the preferred endpoint for responding to calls to be streaming
     * over mesh.
     */
    public void setPreferStreamEndpoint(final boolean preferStream) {
        if (commo != null)
            commo.setPreferStreamEndpoint(preferStream);
    }

    /**
     * Used by plugins to trigger a rescan ahead of the wait period.
     */
    public void triggerIfaceRescan() {
        scannerThread.interrupt();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs,
            final String key) {

        if (key == null)
            return;

        if (key.equals("ppp0_highspeed_capable")) {
            setPPPIncluded(prefs.getBoolean(key, false));
        } else if (key.equals("monitorServerConnections")) {
            commo.setStreamMonitorEnabled(prefs.getBoolean(key, true));
        } else if (FileSystemUtils.isEquals(key,
                "autoDisableMeshSAWhenStreaming")) {
            setPreferStreamEndpoint(
                    prefs.getBoolean("autoDisableMeshSAWhenStreaming", false));
        } else if (key.equals("network_multicast_loopback")) {
            commo.setMulticastLoopbackEnabled(prefs.getBoolean(key, false));
        }
        try {
            switch (key) {
                case MissionPackagePreferenceListener.fileshareDownloadAttempts:
                    commo.setMissionPackageNumTries(getInt(prefs,
                            MissionPackagePreferenceListener.fileshareDownloadAttempts,
                            MissionPackageDownloader.DEFAULT_DOWNLOAD_RETRIES));
                    break;
                case MissionPackagePreferenceListener.filesharingConnectionTimeoutSecs:
                    commo.setMissionPackageConnTimeout(getInt(prefs,
                            MissionPackagePreferenceListener.filesharingConnectionTimeoutSecs,
                            MissionPackageReceiver.DEFAULT_CONNECTION_TIMEOUT_SECS));
                    break;
                case MissionPackagePreferenceListener.filesharingTransferTimeoutSecs:
                    commo.setMissionPackageTransferTimeout(getInt(prefs,
                            MissionPackagePreferenceListener.filesharingTransferTimeoutSecs,
                            MissionPackageReceiver.DEFAULT_TRANSFER_TIMEOUT_SECS));
                    break;
                case CotMapComponent.PREF_API_SECURE_PORT:
                    commo.setMissionPackageHttpsPort(SslNetCotPort
                            .getServerApiPort(SslNetCotPort.Type.SECURE));
                    break;
                case CotMapComponent.PREF_API_UNSECURE_PORT:
                    commo.setMissionPackageHttpPort(SslNetCotPort
                            .getServerApiPort(SslNetCotPort.Type.UNSECURE));
                    break;
                case "networkMeshKey":
                    String meshKey = prefs.getString(key, null);
                    if (meshKey != null) {
                        if (meshKey.length() != 64) {
                            meshKey = "";
                        }
                        AtakAuthenticationDatabase.saveCredentials(
                                AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                                "com.atakmap.app.meshkey", "atakuser", meshKey,
                                false);
                        rekey();
                        prefs.edit().remove(key).apply();
                    }
                    break;
            }
        } catch (CommoException e) {
            Log.e(TAG, "Error setting Mission package related settings");
        }
    }

    private void rekey() {
        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                        "com.atakmap.app.meshkey");
        if (credentials != null
                && !FileSystemUtils.isEmpty(credentials.password)
                && credentials.password.length() == 64) {
            byte[] akey = credentials.password.substring(0, 32)
                    .getBytes(FileSystemUtils.UTF8_CHARSET);
            byte[] ckey = credentials.password.substring(32, 64)
                    .getBytes(FileSystemUtils.UTF8_CHARSET);
            try {
                //Log.d(TAG, "setting key");
                commo.setCryptoKeys(akey, ckey);
            } catch (CommoException ce) {
                Log.e(TAG, "error during setkey with key");
            }
        } else {
            try {
                //Log.d(TAG, "unsetting key");
                commo.setCryptoKeys(null, null);
            } catch (CommoException ce) {
                Log.e(TAG, "error during setkey without key");
            }
        }
    }

    private int getInt(SharedPreferences prefs, String key, int defVal) {
        try {
            return prefs.getInt(key, defVal);
        } catch (Exception e) {
            try {
                return Integer.parseInt(prefs.getString(key,
                        String.valueOf(defVal)));
            } catch (Exception ignore) {
            }
        }
        return defVal;
    }

    public static CommsMapComponent getInstance() {
        return _instance;
    }

    public CotService getCotService() {
        return cotService;
    }

    private final HashSet<CotServiceRemote.InputsChangedListener> inputsChangedListeners;
    private final HashSet<CotServiceRemote.OutputsChangedListener> outputsChangedListeners;

    public void addInputsChangedListener(
            CotServiceRemote.InputsChangedListener listener) {
        synchronized (inputsChangedListeners) {
            inputsChangedListeners.add(listener);
        }
    }

    public void removeInputsChangedListener(
            CotServiceRemote.InputsChangedListener listener) {
        synchronized (inputsChangedListeners) {
            inputsChangedListeners.remove(listener);
        }
    }

    public void addOutputsChangedListener(
            CotServiceRemote.OutputsChangedListener listener) {
        synchronized (outputsChangedListeners) {
            outputsChangedListeners.add(listener);
        }
    }

    public void removeOutputsChangedListener(
            CotServiceRemote.OutputsChangedListener listener) {
        synchronized (outputsChangedListeners) {
            outputsChangedListeners.remove(listener);
        }
    }

    private void fireOutputUpdated(final CotPort port) {
        HashSet<CotServiceRemote.OutputsChangedListener> fire;
        synchronized (outputsChangedListeners) {
            fire = new HashSet<>(
                    outputsChangedListeners);
        }

        final HashSet<CotServiceRemote.OutputsChangedListener> fireFinal = fire;
        mapView.post(new Runnable() {
            @Override
            public void run() {
                for (CotServiceRemote.OutputsChangedListener listener : fireFinal) {
                    listener.onCotOutputUpdated(port.getData());
                }
            }
        });
    }

    private void fireOutputRemoved(final CotPort port) {
        HashSet<CotServiceRemote.OutputsChangedListener> fire;
        synchronized (outputsChangedListeners) {
            fire = new HashSet<>(
                    outputsChangedListeners);
        }

        final HashSet<CotServiceRemote.OutputsChangedListener> fireFinal = fire;
        mapView.post(new Runnable() {
            @Override
            public void run() {
                for (CotServiceRemote.OutputsChangedListener listener : fireFinal) {
                    listener.onCotOutputRemoved(port.getData());
                }
            }
        });
    }

    private void fireInputAdded(final CotPort port) {
        HashSet<CotServiceRemote.InputsChangedListener> fire;
        synchronized (inputsChangedListeners) {
            fire = new HashSet<>(
                    inputsChangedListeners);
        }

        final HashSet<CotServiceRemote.InputsChangedListener> fireFinal = fire;
        mapView.post(new Runnable() {
            @Override
            public void run() {
                for (CotServiceRemote.InputsChangedListener listener : fireFinal) {
                    listener.onCotInputAdded(port.getData());
                }
            }
        });
    }

    private void fireInputRemoved(final CotPort port) {
        HashSet<CotServiceRemote.InputsChangedListener> fire;
        synchronized (inputsChangedListeners) {
            fire = new HashSet<>(
                    inputsChangedListeners);
        }

        final HashSet<CotServiceRemote.InputsChangedListener> fireFinal = fire;
        mapView.post(new Runnable() {
            @Override
            public void run() {
                for (CotServiceRemote.InputsChangedListener listener : fireFinal) {
                    listener.onCotInputRemoved(port.getData());
                }
            }
        });
    }

    /**
     * Retrieve a listing of all the networks inputs/outputs/streaming connections
     * as an uber bundle.  The returned bundle is going to contain a parcelableArray of 
     * streams, outputs, and inputs.   
     * <pre>
     *  Bundle b = getAllPortsBundle();
     *  Bundle[] streams = b.getParcelableArray("streams", null);
     *  Bundle[] outputs = b.getParcelableArray("outputs", null);
     *  Bundle[] inputs = b.getParcelableArray("inputs", null);
     *  
     * where an output bundle contains the following
     *    CotPort.DESCRIPTION_KEY
     *    CotPort.ENABLED_KEY
     *    CotPort.CONNECTED_KEY
     *    CotPort.CONNECT_STRING_KEY
     *  </pre>
     */
    public Bundle getAllPortsBundle() {
        Bundle[] outputs, inputs, streams;
        int i;

        synchronized (outputPorts) {
            outputs = new Bundle[outputPorts.size()];
            i = 0;
            for (OutputPortInfo p : outputPorts.values())
                outputs[i++] = p.outputPort.getData();
        }
        synchronized (inputPorts) {
            inputs = new Bundle[inputPorts.size()];
            i = 0;
            for (InputPortInfo p : inputPorts.values())
                inputs[i++] = p.inputPort.getData();
        }
        synchronized (streamPorts) {
            streams = new Bundle[streamPorts.size()];
            i = 0;
            for (CotPort p : streamPorts.values())
                streams[i++] = p.getData();
        }

        Bundle full = new Bundle();
        full.putParcelableArray("streams", streams);
        full.putParcelableArray("outputs", outputs);
        full.putParcelableArray("inputs", inputs);
        return full;

    }

    void setNonStreamsEnabled(boolean en) {
        if (en != nonStreamsEnabled) {
            nonStreamsEnabled = en;

            recreateAllIns();
            recreateAllOuts();
            mpio.reconfigLocalWebServer(httpsCertFile);
        }
    }

    private void recreateAllOuts() {
        Map<String, OutputPortInfo> localOuts;
        synchronized (outputPorts) {
            localOuts = new HashMap<>(outputPorts);
        }
        for (Map.Entry<String, OutputPortInfo> e : localOuts.entrySet()) {
            addOutput(e.getKey(), e.getValue().outputPort.getData(),
                    e.getValue().netPort, e.getValue().mcast);
        }
    }

    private void recreateAllIns() {
        Map<String, InputPortInfo> localIns;
        synchronized (inputPorts) {
            localIns = new HashMap<>(inputPorts);
        }
        for (Map.Entry<String, InputPortInfo> e : localIns.entrySet()) {
            if (e.getValue().isTcp)
                addTcpInput(e.getKey(), e.getValue().inputPort.getData(),
                        e.getValue().netPort);
            else
                addInput(e.getKey(), e.getValue().inputPort.getData(),
                        e.getValue().netPort, e.getValue().mcast);
        }
    }

    /**
     * Registers an additional CoT event listener to be used if the CoT Event is completely unknown by the
     * system.   Note that a base type of the CoTEvent might be supported by the system and not the
     * subtype.   In this case the system will still process the CoTEvent and the supplied CoTEventListener
     * will not be called.
     * @param cel the cot event listener
     */
    public void addOnCotEventListener(CotServiceRemote.CotEventListener cel) {
        cotEventListeners.add(cel);
    }

    /**
     * Unregisters the previously registered additional CoT event listener.
     * @param cel the cot event listener
     */
    public void removeOnCotEventListener(
            CotServiceRemote.CotEventListener cel) {
        cotEventListeners.remove(cel);
    }

    @Override
    public void onDestroyImpl(Context context, MapView view) {
        rescan = false;
        if (multicastLock != null)
            multicastLock.release();

        if (wifiLock != null)
            wifiLock.release();

        if (_credListener != null) {
            this.unregisterReceiver(context, _credListener);
            _credListener = null;
        }

        if (_componentsListener != null) {
            unregisterReceiver(context, _componentsListener);
            _componentsListener = null;
        }

        AtakBroadcast.getInstance()
                .unregisterSystemReceiver(networkManagerLite);

        // dispose of the registered loggers
        for (CommsLogger logger : loggers) {
            try {
                logger.dispose();
            } catch (Exception e) {
                Log.e(TAG, "error disposing of a logger", e);
            }
        }
        loggers.clear();

        if (this.takServerListener != null)
            this.takServerListener.dispose();
    }

    @Override
    public void onStart(Context context, MapView view) {
        reacquireLock();
    }

    @Override
    public void onStop(Context context, MapView view) {
        reacquireLock();
    }

    @Override
    public void onPause(Context context, MapView view) {
        reacquireLock();
    }

    private void reacquireLock() {
        if (!multicastLock.isHeld()) {
            Log.d(TAG, "re-acquiring wifi multicast lock");
            multicastLock = wifi
                    .createMulticastLock("mulicastLock in CotService onReset");
            multicastLock.acquire();
        }
    }

    @Override
    public void onResume(Context context, MapView view) {
        reacquireLock();
    }

    /**
     * Used internally by ATAK to set up the direct processing workflow for 
     * CotEvent messages.   Do not use for external development application.
     * Any external calls to this method will silently fail.
     *
     * @param dp implementation of the DirectCotProcessor which is CotMapAdapter only.
     */
    public synchronized void registerDirectProcessor(
            final DirectCotProcessor dp) {
        if (directProcessor == null)
            directProcessor = dp;
    }

    /**
     * Allows a enternal plugin to provide a capability to have one last look at a CotEvent 
     * and uid list used.
     * @param psp the Presend Processor triggered right before the message is sent down into the
     *            sending libraries.
     */
    public void registerPreSendProcessor(final PreSendProcessor psp) {
        preSendProcessor = psp;
    }

    /**
     * Enabled PPP as a tunnel for use with the trellisware wireless connector.   When called, it
     * will trigger a rescan of the interfaces with ppp[0..4] included or excluded.
     * @param included true if ppp[0..4] should be enabled or false if it should be disabled.
     */
    public void setPPPIncluded(final boolean included) {
        if (included) {
            Log.d(TAG,
                    "user has indicated that Point-to-Point (ppp) links are high speed capable");
        } else {
            Log.d(TAG,
                    "user has indicated that Point-to-Point (ppp) links are not high speed capable");
        }
        pppIncluded = included;
        rescanInterfaces();
    }

    private void rescanInterfaces() {
        Set<String> in = scanInterfaces(false);
        Set<String> out = scanInterfaces(false);
        in.removeAll(hwAddressesIn);
        out.removeAll(hwAddressesOut);

        if (!in.isEmpty()) {
            Log.i(TAG,
                    "Set of input network interfaces changed - rebuilding inputs!");
            in = scanInterfaces(true);
            synchronized (hwAddressesIn) {
                hwAddressesIn.clear();
                hwAddressesIn.addAll(in);
            }
            recreateAllIns();
        }
        if (!out.isEmpty()) {
            Log.i(TAG,
                    "Set of output network interfaces changed - rebuilding outputs!");
            out = scanInterfaces(true);
            synchronized (hwAddressesOut) {
                hwAddressesOut.clear();
                hwAddressesOut.addAll(out);
            }
            recreateAllOuts();
        }
    }

    private Set<String> scanInterfaces(boolean doLog) {
        final Set<String> ret = new HashSet<>();

        try {
            Enumeration<NetworkInterface> enifs = NetworkInterface
                    .getNetworkInterfaces();
            if (enifs != null) {
                List<NetworkInterface> nifs = Collections.list(enifs);
                for (NetworkInterface nif : nifs) {
                    // nwradioX and usbX are both interface names assigned to the HHL16 radio
                    // nwradioX is a renamed variant of usbX found on NettWarrior devices.
                    // the MPU5 uses waverelay as the network device name
                    final String name = nif.getName();
                    if (name != null
                            && (name.startsWith("wlan")
                                    || name.startsWith("tun")
                                    || name.startsWith("waverelay")
                                    || name.startsWith("rndis")
                                    || name.startsWith("eth")
                                    || name.startsWith("nwradio")
                                    || name.startsWith("usb")
                                    || (pppIncluded
                                            && name.startsWith("ppp")))) {
                        if (doLog)
                            Log.d(TAG, "network device registering: "
                                    + nif.getName());

                        ret.add(name);
                    } else {
                        if (doLog)
                            Log.d(TAG, "network device skipping: "
                                    + nif.getName());
                    }
                }
            }
        } catch (NullPointerException | SocketException e) {
            // ^ NPE observed on some less-prevalent devices coming
            // out of internal impl of android
            // NetworkInterface.getNetworkInterfaces(). See ATAK-15755
            if (doLog)
                Log.d(TAG,
                        "exception occurred trying to build the interface list",
                        e);
        }
        return ret;
    }

    private List<String> getInterfaceNames(boolean input) {
        ArrayList<String> ret;
        Set<String> addrSet = input ? hwAddressesIn : hwAddressesOut;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (addrSet) {
            ret = new ArrayList<>(addrSet);
        }
        return ret;
    }

    void setTTL(int ttl) {
        commo.setTTL(ttl);
    }

    /**
     * Value in full seconds till the socket it torn down and recreated.
     */
    void setUdpNoDataTimeout(int seconds) {
        commo.setUdpNoDataTimeout(seconds);
    }

    void setTcpConnTimeout(int millseconds) {
        commo.setTcpConnTimeout(millseconds / 1000);
    }

    // Assumes holding of netIfaces lock
    private List<PhysicalNetInterface> addInputsForMcastSet(
            Set<String> mcastSet, int port, List<String> ifaceNames) {
        // remove null unicast-only entry, return null if that null is only one
        Set<String> nonNullSet = new HashSet<>();
        for (String s : mcastSet) {
            if (s != null)
                nonNullSet.add(s);
        }
        String[] mcastAddresses = null;
        if (!nonNullSet.isEmpty()) {
            mcastAddresses = mcastSet.toArray(new String[0]);
        }

        List<PhysicalNetInterface> netIfaces = new ArrayList<>();
        for (String name : ifaceNames) {
            PhysicalNetInterface iface;
            try {
                iface = commo.addInboundInterface(name, port, mcastAddresses,
                        false);
                netIfaces.add(iface);
            } catch (CommoException e) {
                Log.d(TAG, "Could not create input for " + name + " mcast: "
                        + Arrays.toString(mcastAddresses) + ":" + port, e);
            }
        }
        return netIfaces;
    }

    public void addInput(String uniqueKey, Bundle portBundle, int port,
            String mcastAddress) {
        CotPort cotport = new CotPort(portBundle);
        boolean isNowEnabled = cotport.isEnabled() && nonStreamsEnabled;

        if (commo == null)
            return;

        synchronized (inputIfaces) {
            List<String> ifaceNames = getInterfaceNames(true);

            // Remove interfaces associated with this input's network port
            if (inputIfaces.containsKey(port)) {
                // need to remove it to change parameters/disable
                List<PhysicalNetInterface> ifaces = inputIfaces.remove(port);
                if (ifaces != null)
                    for (PhysicalNetInterface iface : ifaces) {
                        try {
                            commo.removeInboundInterface(iface);
                        } catch (IllegalArgumentException iae) {
                            Log.e(TAG,
                                    "unable to remove inbound interface for: "
                                            + iface);
                        }
                    }
            }

            // If this Input existed before....
            InputPortInfo portInfo;
            synchronized (inputPorts) {
                portInfo = inputPorts.remove(uniqueKey);
            }

            if (portInfo != null && !portInfo.isTcp) {
                // Remove any existing interfaces associated with the old port
                if (inputIfaces.containsKey(portInfo.netPort)) {
                    List<PhysicalNetInterface> ifaces = inputIfaces
                            .remove(portInfo.netPort);
                    if (ifaces != null) {
                        for (PhysicalNetInterface iface : ifaces) {
                            try {
                                commo.removeInboundInterface(iface);
                            } catch (IllegalArgumentException iae) {
                                Log.e(TAG,
                                        "unable to remove inbound interface for: "
                                                + iface);
                            }
                        }
                    }
                }

                // Now adjust the old port's mcast set and re-add the ifaces if needed
                Set<String> mcast = mcastAddrsByPort.get(portInfo.netPort);
                if (mcast != null) {
                    mcast.remove(portInfo.mcast);
                    if (mcast.isEmpty()) {
                        mcastAddrsByPort.remove(portInfo.netPort);
                    } else if (portInfo.netPort != port) {
                        // Add old port back with remaining enabled mcasts
                        List<PhysicalNetInterface> netIfaces = addInputsForMcastSet(
                                mcast, portInfo.netPort, ifaceNames);
                        inputIfaces.put(portInfo.netPort, netIfaces);
                    }
                }
            }

            Set<String> mcastSet = mcastAddrsByPort.get(port);
            if (isNowEnabled) {
                // Add to mcast list of new port
                if (mcastSet == null) {
                    mcastSet = new HashSet<>();
                    mcastAddrsByPort.put(port, mcastSet);
                }
                mcastSet.add(mcastAddress);
            }

            // If there are any addresses left enabled for our net port, add the ifaces with that set of addrs
            if (mcastSet != null && !mcastSet.isEmpty()) {
                List<PhysicalNetInterface> netIfaces = addInputsForMcastSet(
                        mcastSet, port, ifaceNames);
                inputIfaces.put(port, netIfaces);
            }
        }
        InputPortInfo ipi;
        synchronized (inputPorts) {
            inputPorts.put(uniqueKey, ipi = new InputPortInfo(
                    new CotPort(portBundle), port, mcastAddress));
        }
        fireInputAdded(ipi.inputPort);
    }

    public void addTcpInput(String uniqueKey, Bundle portBundle, int port) {
        CotPort cotport = new CotPort(portBundle);
        boolean isNowEnabled = cotport.isEnabled() && nonStreamsEnabled;

        synchronized (tcpInputIfaces) {
            if (tcpInputIfaces.containsKey(uniqueKey)) {
                // need to remove it to change parameters/disable
                TcpInboundNetInterface iface = tcpInputIfaces.remove(uniqueKey);
                if (iface != null)
                    commo.removeTcpInboundInterface(iface);
            }

            if (isNowEnabled) {
                try {
                    TcpInboundNetInterface iface = commo
                            .addTcpInboundInterface(port);
                    tcpInputIfaces.put(uniqueKey, iface);
                } catch (CommoException e) {
                    // do not do anything but log the error and do not add the tcp port
                    Log.d(TAG, "Could not create iface " + uniqueKey + " for: "
                            + cotport, e);
                    return;
                }
            }
        }
        InputPortInfo ipi;
        synchronized (inputPorts) {
            inputPorts.put(uniqueKey, ipi = new InputPortInfo(
                    new CotPort(portBundle), port));
        }
        fireInputAdded(ipi.inputPort);
    }

    public void removeInput(String uniqueKey) {
        // If this Input existed before....
        InputPortInfo portInfo;
        synchronized (inputPorts) {
            portInfo = inputPorts.remove(uniqueKey);
        }

        if (commo == null)
            return;

        synchronized (inputIfaces) {
            if (portInfo != null && !portInfo.isTcp) {
                // Remove any existing interfaces associated with the old port
                if (inputIfaces.containsKey(portInfo.netPort)) {
                    List<PhysicalNetInterface> ifaces = inputIfaces
                            .remove(portInfo.netPort);
                    if (ifaces != null) {
                        for (PhysicalNetInterface iface : ifaces)
                            commo.removeInboundInterface(iface);
                    }
                }

                // Now adjust the old port's mcast set and re-add the ifaces if needed
                Set<String> mcast = mcastAddrsByPort.get(portInfo.netPort);
                if (mcast != null) {
                    mcast.remove(portInfo.mcast);
                    if (mcast.isEmpty()) {
                        mcastAddrsByPort.remove(portInfo.netPort);
                    } else {
                        List<String> ifaceNames = getInterfaceNames(true);
                        List<PhysicalNetInterface> netIfaces = addInputsForMcastSet(
                                mcast, portInfo.netPort, ifaceNames);
                        inputIfaces.put(portInfo.netPort, netIfaces);
                    }
                }
            }
        }
        synchronized (tcpInputIfaces) {
            TcpInboundNetInterface iface = tcpInputIfaces.remove(uniqueKey);
            if (iface != null)
                commo.removeTcpInboundInterface(iface);
        }
        if (portInfo != null)
            fireInputRemoved(portInfo.inputPort);
    }

    public void addOutput(String uniqueKey, Bundle portBundle,
            int port, String mcastAddress) {
        CotPort cotport = new CotPort(portBundle);
        boolean isNowEnabled = cotport.isEnabled() && nonStreamsEnabled;
        boolean isForChat = cotport.isChat();

        synchronized (broadcastIfaces) {
            if (broadcastIfaces.containsKey(uniqueKey)) {
                // remove the ifaces from commo but leave them in our list of ports
                List<PhysicalNetInterface> netIfaces = broadcastIfaces
                        .remove(uniqueKey);
                if (netIfaces != null)
                    for (PhysicalNetInterface iface : netIfaces)
                        commo.removeBroadcastInterface(iface);
            }

            if (isNowEnabled) {
                List<PhysicalNetInterface> netIfaces = new ArrayList<>();
                CoTMessageType[] types = new CoTMessageType[] {
                        isForChat ? CoTMessageType.CHAT
                                : CoTMessageType.SITUATIONAL_AWARENESS
                };

                boolean isReallyMcast = true;
                try {
                    if (!NetworkUtils.isMulticastAddress(mcastAddress))
                        isReallyMcast = false;
                } catch (Exception ignored) {
                }

                if (!isReallyMcast) {
                    PhysicalNetInterface iface;
                    try {
                        iface = commo.addBroadcastInterface(types,
                                mcastAddress, port);
                        netIfaces.add(iface);
                    } catch (CommoException e) {
                        Log.d(TAG, "Could not create output iface " + uniqueKey
                                + " for mcast: " + mcastAddress
                                + ":" + port, e);
                    }

                } else {
                    List<String> ifaceNames = getInterfaceNames(false);
                    for (String name : ifaceNames) {
                        PhysicalNetInterface iface;
                        try {
                            iface = commo.addBroadcastInterface(name, types,
                                    mcastAddress, port);
                            netIfaces.add(iface);
                        } catch (CommoException e) {

                            Log.d(TAG, "Could not create output iface "
                                    + uniqueKey
                                    + " for " + name + " mcast: "
                                    + mcastAddress
                                    + ":" + port, e);
                        }
                    }
                }
                broadcastIfaces.put(uniqueKey, netIfaces);
            }
        }
        synchronized (outputPorts) {
            OutputPortInfo out = new OutputPortInfo(cotport, port,
                    mcastAddress);
            outputPorts.put(uniqueKey, out);
        }
        fireOutputUpdated(cotport);
    }

    public void removeOutput(String uniqueKey) {
        synchronized (broadcastIfaces) {
            List<PhysicalNetInterface> ifaces = broadcastIfaces
                    .remove(uniqueKey);
            if (ifaces != null) {
                for (PhysicalNetInterface iface : ifaces)
                    commo.removeBroadcastInterface(iface);
            } // else this was disabled
        }
        OutputPortInfo cotport;
        synchronized (outputPorts) {
            cotport = outputPorts.remove(uniqueKey);
        }
        if (cotport != null)
            fireOutputRemoved(cotport.outputPort);
    }

    // null for clientCert, caCert, and certPassword if plain tcp; for SSL all 3 must be non-null
    // null for authUser and authPass to not use auth; specify both to use auth - ignored for TCP 
    // Set missingParams = true to forgo attempting to connect as caller knows it won't meet
    // the desired result due to one or more missing/misconfigured parameters (certs, cert passwords,
    // user, password, etc)
    public void addStreaming(String uniqueKey, Bundle portBundle,
            boolean missingParams,
            String hostname, int port, byte[] clientCert, byte[] caCert,
            String certPassword, String caCertPassword, String authUser,
            String authPass) {
        CotPort cotport = new CotPort(portBundle);
        boolean isNowEnabled = cotport.isEnabled();
        String streamId = null;
        String oldStreamId = null;
        String errReason = null;

        Integer notifyId;
        synchronized (streamPorts) {
            notifyId = streamNotificationIds.get(uniqueKey);
            if (notifyId == null) {
                notifyId = NotificationUtil.getInstance().reserveNotifyId();
                streamNotificationIds.put(uniqueKey, notifyId);
            }
        }
        NotificationUtil.getInstance().clearNotification(notifyId);

        synchronized (streamingIfaces) {
            // Also mark it disconnected
            cotport.setConnected(false);
            if (streamingIfaces.containsKey(uniqueKey)) {
                // Need to remove it to "disable" or change params
                StreamingNetInterface iface = streamingIfaces.remove(uniqueKey);
                if (iface != null) {
                    oldStreamId = iface.streamId;
                    commo.removeStreamingInterface(iface);
                }
            }

            if (isNowEnabled && !missingParams) {
                StreamingNetInterface netIface;
                CoTMessageType[] types = new CoTMessageType[] {
                        CoTMessageType.CHAT,
                        CoTMessageType.SITUATIONAL_AWARENESS
                };
                try {
                    netIface = commo.addStreamingInterface(hostname, port,
                            types, clientCert, caCert, certPassword,
                            caCertPassword, authUser, authPass);
                    streamingIfaces.put(uniqueKey, netIface);
                    streamId = netIface.streamId;
                } catch (CommoException e) {
                    errReason = e.getMessage();
                    Log.d(TAG, "Could not create streaming iface " + uniqueKey
                            + " for host: " + hostname + ":"
                            + port + " types: " + Arrays.toString(types), e);
                }
            }
        }

        synchronized (streamPorts) {
            streamPorts.put(uniqueKey, cotport);
            if (oldStreamId != null)
                streamKeys.remove(oldStreamId);

            if (streamId != null)
                streamKeys.put(streamId, uniqueKey);
        }

        fireOutputUpdated(cotport);

        if (errReason != null) {
            NotificationUtil.getInstance().postNotification(
                    notifyId,
                    NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                    NotificationUtil.RED,
                    mapView.getContext().getString(
                            R.string.connection_config_error),
                    mapView.getContext().getString(R.string.unable_to_config)
                            + cotport.getDescription() + " ("
                            + errReason + ")",
                    null,
                    true);
        }
    }

    public CotPort removeStreaming(String uniqueKey) {
        String id = null;
        synchronized (streamingIfaces) {
            StreamingNetInterface iface = streamingIfaces.remove(uniqueKey);
            if (iface != null) {
                id = iface.streamId;
                commo.removeStreamingInterface(iface);
            } // else it just wasn't enabled or didn't exist....

        }
        CotPort cotport;
        synchronized (streamPorts) {
            cotport = streamPorts.remove(uniqueKey);
            streamKeys.remove(id);
            Integer notifierId = streamNotificationIds.remove(uniqueKey);
            if (notifierId != null)
                NotificationUtil.getInstance().clearNotification(
                        notifierId);
        }
        if (cotport != null)
            fireOutputRemoved(cotport);
        return cotport;
    }

    // DEPRECATED
    // Supports only tcp endpoints for backwards compatibility
    public void sendCoTToEndpoint(CotEvent e, String endpoint) {

        if (endpoint == null) {
            Log.d(TAG, "no endpoint supplied", new Exception());
            return;
        }

        String[] s = endpoint.split(":");
        if (s.length != 3) {
            Log.d(TAG, "Unsupported endpoint string: " + endpoint);
            return;
        }
        if (!s[2].toLowerCase(LocaleUtil.getCurrent()).equals("tcp")) {
            Log.d(TAG, "Unsupported endpoint protocol: " + s[2]);
            return;
        }
        int port;
        try {
            port = Integer.parseInt(s[1]);
        } catch (NumberFormatException ex) {
            Log.d(TAG, "Invalid endpoint string " + endpoint
                    + " - port number invalid");
            return;
        }

        if (e == null) {
            Log.e(TAG,
                    "Empty CotEvent received while trying to send (ignore).");
            return;
        }

        try {
            final String event = e.toString();
            commo.sendCoTTcpDirect(s[0], port, event);

            for (CommsLogger logger : loggers) {
                try {
                    logger.logSend(e, endpoint);
                } catch (Exception err) {
                    Log.e(TAG, "error occurred with a logger", err);
                }
            }

        } catch (CommoException ex) {
            Log.e(TAG,
                    "Invalid cot message or destination for tcp direct send to "
                            + endpoint + " msg = " + e);
        }
    }

    /**
     *
     * if failedContacts is non-null, fill it with those contacts who are not known via the specified method
     * or who are invalid or missing on the network
     *
     * @param failedContactUids a list that will be filled with the list of sending contacts that failed.
     * @param e event to send
     * @param toUIDs  Destination UIDs, null for broadcast
     * @param method    method for sending
     */
    void sendCoT(
            List<String> failedContactUids,
            CotEvent e,
            String[] toUIDs,
            CoTSendMethod method) {
        if (failedContactUids != null)
            failedContactUids.clear();

        if (e == null) {
            Log.e(TAG,
                    "empty CotEvent received while trying to send (ignore).");
            return;
        }

        try {
            if (preSendProcessor != null) {
                preSendProcessor.processCotEvent(e, toUIDs);
            }
        } catch (Exception ex) {
            Log.e(TAG, "preSendProcessor failed", ex);
        }

        if (toUIDs == null) {
            try {
                final String event = e.toString();
                if (commo != null)
                    commo.broadcastCoT(event, method);

                for (CommsLogger logger : loggers) {
                    try {
                        logger.logSend(e, "broadcast");
                    } catch (Exception err) {
                        Log.e(TAG, "error occurred with a logger", err);
                    }
                }

            } catch (CommoException ex) {
                Log.e(TAG, "Invalid cot message for broadcast " + e);
            }
        } else {

            Vector<Contact> commoContacts = new Vector<>();
            synchronized (uidsToContacts) {
                for (String uid : toUIDs) {
                    Contact commoContact = uidsToContacts.get(uid);
                    if (commoContact == null) {
                        Log.e(TAG, "Send to unknown contact " + uid,
                                new Exception());
                        continue;
                    }
                    commoContacts.add(commoContact);
                }
            }

            try {
                final String event = e.toString();
                if (commo != null)
                    commo.sendCoT(commoContacts, event, method);

                for (CommsLogger logger : loggers) {
                    try {
                        logger.logSend(e, toUIDs);
                    } catch (Exception err) {
                        Log.e(TAG, "error occurred with a logger", err);
                    }
                }

            } catch (CommoException ex) {
                Log.e(TAG, "Invalid cot message for unicast " + e);
            }

            if (failedContactUids != null) {
                for (Contact commoContact : commoContacts) {
                    for (String uid : toUIDs) {
                        if (uid.equals(commoContact.contactUID)) {
                            failedContactUids.add(uid);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Send the specified CotEvent to all configured and connected TAK servers.
     * They will be routed to the specified mission on those servers.
     * Destination/routing information will be added during the send and should
     * not be specified in the CotEvent itself. 
     *  
     * @param uniqueIfaceKey the unique key identifier specifying a configured
     *                       streaming interface, or null to send to all active
     *                       streaming interfaces
     * @param mission the mission identifier used to route the message
     * @param e valid CotEvent to send
     */
    public void sendCoTToServersByMission(String uniqueIfaceKey,
            String mission, CotEvent e) {
        String id = null;
        if (uniqueIfaceKey != null) {
            synchronized (streamingIfaces) {
                StreamingNetInterface iface = streamingIfaces
                        .get(uniqueIfaceKey);
                if (iface != null) {
                    id = iface.streamId;
                } else {
                    Log.e(TAG,
                            "Invalid interface key specified for sending to server: "
                                    + uniqueIfaceKey);
                    return;
                }
            }
        }

        try {
            final String event = e.toString();
            if (commo != null)
                commo.sendCoTToServerMissionDest(id, mission, event);

            for (CommsLogger logger : loggers) {
                try {
                    logger.logSend(e, "mission " + mission);
                } catch (Exception err) {
                    Log.e(TAG, "error occurred with a logger", err);
                }
            }

        } catch (CommoException ex) {
            Log.e(TAG, "Invalid cot message for send to mission");
        }
    }

    /**
     * Send the specified CotEvent to all configured and connected TAK servers.
     * They will be routed to the "server only contact", being processed only by
     * the server itself and not send to any other clients.
     * Destination/routing information will be added during the send and should
     * not be specified in the CotEvent itself. 
     *  
     * @param uniqueIfaceKey the unique key identifier specifying a configured
     *                       streaming interface, or null to send to all active
     *                       streaming interfaces
     * @param e valid CotEvent to send
     */
    public void sendCoTToServersOnly(String uniqueIfaceKey, CotEvent e) {
        String id = null;
        if (uniqueIfaceKey != null) {
            synchronized (streamingIfaces) {
                StreamingNetInterface iface = streamingIfaces
                        .get(uniqueIfaceKey);
                if (iface != null) {
                    id = iface.streamId;
                } else {
                    Log.e(TAG,
                            "Invalid interface key specified for sending to server: "
                                    + uniqueIfaceKey);
                    return;
                }
            }
        }

        try {
            final String event = e.toString();
            if (commo != null)
                commo.sendCoTServerControl(id, event);

            for (CommsLogger logger : loggers) {
                try {
                    logger.logSend(e, CotService.SERVER_ONLY_CONTACT);
                } catch (Exception err) {
                    Log.e(TAG, "error occurred with a logger", err);
                }
            }

        } catch (CommoException ex) {
            Log.e(TAG, "Invalid cot message for send to cot servers only");
        }
    }

    @Override
    public void sendCoTFailure(String host, int port, String errorReason) {

        NotificationUtil.getInstance().postNotification(
                31345,
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                mapView.getContext().getString(R.string.notification_text8),
                mapView.getContext().getString(R.string.notification_text9)
                        + host + ":" + port + "/" + errorReason,
                null, true);

        Log.d(TAG, "error sending message to: " + host + " " + port + " "
                + errorReason);
    }

    /**
     * Invoked when a CoT Message has been received.  The message
     * is provided without modification. Some basic validity checking
     * is done prior to passing it off to listeners, but it is limited
     * and should not be relied upon for anything specific.
     *
     * @param message the CoT message that was received
     * @param rxEndpointId identifier of NetworkInterface upon which
     *                     the message was received, if known, or null
     *                     if not known
     */
    @Override
    public void cotMessageReceived(final String message,
            final String rxEndpointId) {

        // Check if the map components have finished loading before processing
        if (!componentsLoaded) {
            synchronized (deferredMessages) {
                // Check again inside the sync block just in case it changed
                if (!componentsLoaded) {
                    deferredMessages.add(new Pair<>(message, rxEndpointId));
                    return;
                }
            }
        }

        CotEvent cotEvent = CotEvent.parse(message);
        Bundle extras = new Bundle();
        extras.putString("from", cotEvent.getUID());
        if (rxEndpointId != null) {
            synchronized (streamPorts) {
                String appsStreamEndpoint = streamKeys.get(rxEndpointId);
                if (appsStreamEndpoint != null)
                    extras.putString("serverFrom", appsStreamEndpoint);
            }
        }

        if (cotEvent != null)
            sendCoTInternally(cotEvent, extras);

        for (CommsLogger logger : loggers) {
            try {

                String appsStreamEndpoint = (rxEndpointId != null)
                        ? streamKeys.get(rxEndpointId)
                        : null;
                logger.logReceive(cotEvent, rxEndpointId, appsStreamEndpoint);
            } catch (Exception err) {
                Log.e(TAG, "error occurred with a logger", err);
            }
        }

    }

    public void sendCoTInternally(final CotEvent cotEvent, Bundle extras) {
        ImportResult result = ImportResult.FAILURE;

        if (extras == null) {
            extras = new Bundle();
            extras.putString("from", "internal");
        }

        if (directProcessor != null) {
            //Log.d(TAG, "received for processing: " + cotEvent);
            result = directProcessor.processCotEvent(cotEvent,
                    extras);
        }

        if (result != ImportResult.SUCCESS) {
            //Log.d(TAG, "failed to process, redispatch received: " + cotEvent);
            // iterate over all of the CotEvent listeners and fire (cotEvent, extras);
            for (CotServiceRemote.CotEventListener cel : cotEventListeners) {
                try {
                    cel.onCotEvent(cotEvent, extras);
                } catch (Exception e) {
                    Log.e(TAG, "internal dispatching error: ", e);
                }
            }
        }
    }

    public void syncFileTransfer(CommsFileTransferListener listener,
            boolean forUpload,
            URI uri, byte[] caCert, String caCertPassword,
            String username, String password, File localFile)
            throws CommoException,
            IOException,
            InterruptedException {
        masterFileIO.doFileTransfer(listener, forUpload, uri, caCert,
                caCertPassword,
                username, password, localFile);
    }

    public void setMissionPackageReceiveInitiator(MPReceiveInitiator rxInit) {
        mpio.setRxInitiator(rxInit);
    }

    public boolean setMissionPackageEnabled(boolean enabled, int localPort,
            int secureLocalPort) {
        this.filesharingEnabled = enabled;
        if (enabled) {
            this.localWebServerPort = localPort;
            this.secureLocalWebServerPort = secureLocalPort;
        }
        return mpio.reconfigFileSharing();
    }

    // file must exist until transfer ends
    public void sendMissionPackage(String[] contactUuids, File file,
            String remoteFileName, String transferName,
            MPSendListener listener)
            throws UnknownServiceException, CommoException {
        Set<Contact> contacts = new HashSet<>();
        synchronized (uidsToContacts) {
            for (String uid : contactUuids) {
                Contact commoContact = uidsToContacts.get(uid);
                if (commoContact == null) {
                    Log.e(TAG, "Mission Package send to unknown contact " + uid,
                            new Exception());
                    continue;
                }
                contacts.add(commoContact);
            }
        }

        if (contacts.isEmpty())
            throw new UnknownServiceException(
                    "Specified contacts are not available");

        try {
            mpio.sendMissionPackage(contacts, file,
                    remoteFileName, transferName, listener);
        } catch (CommoException ex) {
            Log.e(TAG, "Failed to send mission package "
                    + file.getAbsolutePath() + "  " + ex.getMessage());
            throw ex;
        }
    }

    // Sends direct to a server identified by the server 
    // file must exist until transfer ends
    public void sendMissionPackage(String uniqueServerIfaceKey,
            File file, String transferFilename,
            MPSendListener listener)
            throws UnknownServiceException, CommoException {
        // Translate Net connect string/unique key to
        // stream id
        String streamingId;
        synchronized (streamingIfaces) {
            StreamingNetInterface iface = streamingIfaces
                    .get(uniqueServerIfaceKey);
            if (iface != null) {
                streamingId = iface.streamId;
            } else {
                Log.e(TAG,
                        "Invalid interface key specified for uploading file to server: "
                                + uniqueServerIfaceKey);
                throw new UnknownServiceException(
                        "Unknown server identifier specified");
            }
        }

        mpio.sendMissionPackage(streamingId,
                file, transferFilename,
                listener);

    }

    public com.atakmap.android.contact.Contact createKnownEndpointContact(
            String name, String ipAddr, int port) {
        IndividualContact contact = new IndividualContact(name);
        String id = contact.getUID();

        try {
            if (commo != null)
                commo.configKnownEndpointContact(id, name, ipAddr, port);
            return contact;
        } catch (CommoException e) {
            return null;
        }
    }

    @Override
    public void contactAdded(Contact c) {
        synchronized (uidsToContacts) {
            uidsToContacts.put(c.contactUID, c);
        }

    }

    @Override
    public void contactRemoved(Contact c) {
        synchronized (uidsToContacts) {
            uidsToContacts.remove(c.contactUID);
        }
        CotMapComponent.getInstance().stale(new String[] {
                c.contactUID
        });
    }

    private void interfaceChange(NetInterface iface, boolean up) {
        if (!(iface instanceof StreamingNetInterface))
            return;

        StreamingNetInterface siface = (StreamingNetInterface) iface;
        String key = siface.streamId;
        Integer notifierId;
        CotPort port;
        synchronized (streamPorts) {
            String appsKey = streamKeys.get(key);
            if (appsKey == null)
                return;

            port = streamPorts.get(appsKey);
            if (port == null)
                return;
            port.setConnected(up);

            notifierId = streamNotificationIds.get(appsKey);
        }
        fireOutputUpdated(port);
        if (up && notifierId != null)
            NotificationUtil.getInstance().clearNotification(
                    notifierId);
    }

    @Override
    public void interfaceDown(NetInterface iface) {
        interfaceChange(iface, false);
    }

    @Override
    public void interfaceUp(NetInterface iface) {
        interfaceChange(iface, true);
    }

    @Override
    public void interfaceError(NetInterface iface,
            NetInterfaceErrorCode errCode) {
        if (!(iface instanceof StreamingNetInterface))
            return;

        StreamingNetInterface siface = (StreamingNetInterface) iface;
        String key = siface.streamId;
        Integer notifierId;
        CotPort port;
        synchronized (streamPorts) {
            String appsKey = streamKeys.get(key);
            if (appsKey == null)
                return;

            notifierId = streamNotificationIds.get(appsKey);
            if (notifierId == null)
                return;

            port = streamPorts.get(appsKey);
            if (port == null)
                return;
        }

        String errMsg;
        switch (errCode) {
            case CONN_HOST_UNREACHABLE:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text10);
                break;
            case CONN_NAME_RES_FAILED:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text11);
                break;
            case CONN_REFUSED:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text12);
                break;
            case CONN_SSL_HANDSHAKE:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text13);
                break;
            case CONN_SSL_NO_PEER_CERT:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text14);
                break;
            case CONN_SSL_PEER_CERT_NOT_TRUSTED:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text15);
                break;
            case CONN_TIMEOUT:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text16);
                break;
            case CONN_OTHER:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text17);
                break;
            case INTERNAL:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text18);
                break;
            case IO_RX_DATA_TIMEOUT:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text19);
                break;
            case IO:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text20);
                break;
            case OTHER:
            default:
                errMsg = mapView.getContext().getString(
                        R.string.notification_text21);
                break;
        }

        NotificationUtil.getInstance().postNotification(
                notifierId,
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                mapView.getContext().getString(R.string.connection_error),
                mapView.getContext().getString(R.string.error_connecting)
                        + port.getDescription() + " (" + errMsg
                        + ")",
                null,
                true);

        port.setErrorString(errMsg);
    }

    public void setServerVersion(String connectString, ServerVersion version) {
        CotPort port;
        synchronized (streamPorts) {
            port = streamPorts.get(connectString);
        }

        if (port == null) {
            Log.w(TAG, "setServerVersion, not found: " + connectString);
            return;
        }

        port.setServerVersion(version);
    }

    public String generateKey(String password) {

        final int DEFAULT_KEY_LENGTH = 4096;

        int keyLength = DEFAULT_KEY_LENGTH;
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        try {
            keyLength = Integer.parseInt(prefs.getString(
                    "apiCertEnrollmentKeyLength",
                    Integer.toString(DEFAULT_KEY_LENGTH)));
            if (keyLength < 1) {
                keyLength = DEFAULT_KEY_LENGTH;
            }
        } catch (Exception e) {
            Log.w(TAG,
                    "Failed to parse apiCertEnrollmentKeyLength: " + keyLength);
            keyLength = DEFAULT_KEY_LENGTH;
        }

        return commo.generateKeyCryptoString(password, keyLength);
    }

    public String generateKeystore(String certPem,
            List<String> caPem, String privateKeyPem,
            String password, String friendlyName) {
        return commo.generateKeystoreCryptoString(certPem,
                caPem, privateKeyPem, password, friendlyName);
    }

    public String generateCSR(Map<String, String> dnEntries,
            String privateKey, String password) {
        return commo.generateCSRCryptoString(dnEntries,
                privateKey, password);
    }

    public CloudClient createCloudClient(CloudIO io, CloudIOProtocol proto,
            String host, int port, String basePath, String user,
            String password) {
        try {
            byte[] caCerts;
            final X509Certificate[] certs = CertificateManager.getInstance()
                    .getCertificates(CertificateManager.getInstance()
                            .getSystemTrustManager());
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            int n = 1;
            for (X509Certificate c : certs) {
                ks.setCertificateEntry(String.valueOf(n), c);
                n++;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String s = "";
            ks.store(baos, s.toCharArray());
            caCerts = baos.toByteArray();

            return createCloudClient(io, proto, host, port, basePath,
                    user, password, caCerts, s);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create cloud client for " + host, e);
            return null;
        }
    }

    public CloudClient createCloudClient(CloudIO io, CloudIOProtocol proto,
            String host, int port, String basePath, String user,
            String password, byte[] caCerts, String s) {
        try {
            return commo.createCloudClient(io, proto, host, port, basePath,
                    user, password, caCerts, s);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create cloud client for " + host, e);
            return null;
        }
    }

    public void destroyCloudClient(CloudClient client) {
        try {
            commo.destroyCloudClient(client);
        } catch (Exception e) {
            Log.e(TAG, "Failed to destroy cloud client", e);
        }
    }

    private BroadcastReceiver _credListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CredentialsPreference.CREDENTIALS_UPDATED.equals(intent
                    .getAction())) {
                String credType = intent.getStringExtra("type");
                //see if password for TAK Server CA truststore or client store were updated
                if (AtakAuthenticationCredentials.TYPE_clientPassword
                        .equals(credType)
                        || AtakAuthenticationCredentials.TYPE_caPassword
                                .equals(credType)) {
                    // Inform communications system to re-acquire new credentials
                    CommsMapComponent.getInstance().getCotService()
                            .reconnectStreams();
                }
            } else if (NetworkConnectionPreferenceFragment.CERTIFICATE_UPDATED
                    .equals(intent.getAction())) {
                String certType = intent.getStringExtra("type");
                //see if TAK Server CA truststore or client store were updated
                if (AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA
                        .equals(certType)
                        || AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE
                                .equals(certType)) {
                    CommsMapComponent.getInstance().getCotService()
                            .reconnectStreams();
                }
            }
        }
    };

    // Listen for map components finished loading and then process deferred events
    private BroadcastReceiver _componentsListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Check if already loaded
            if (componentsLoaded)
                return;

            // Get deferred messages
            componentsLoaded = true;
            final List<Pair<String, String>> msgs;
            synchronized (deferredMessages) {
                msgs = new ArrayList<>(deferredMessages);
                deferredMessages.clear();
            }

            // Nothing to process
            if (msgs.isEmpty())
                return;

            // Process on separate thread to avoid UI lockup
            Thread thr = new Thread(TAG + " Deferred CoT") {
                @Override
                public void run() {
                    Log.d(TAG, "Processing " + msgs.size()
                            + " deferred CoT messages");
                    for (Pair<String, String> msg : msgs)
                        cotMessageReceived(msg.first, msg.second);
                }
            };
            thr.setPriority(Thread.NORM_PRIORITY);
            thr.start();
        }
    };

    private static class MPTransferInfo {
        final int xferId;
        // if null, then was xfer to server
        final Set<Contact> remainingRecipients;
        final MPSendListener listener;

        MPTransferInfo(int xferId, Set<Contact> recipients,
                MPSendListener listener) {
            this.listener = listener;
            this.xferId = xferId;
            this.remainingRecipients = recipients;
        }

        MPTransferInfo(int xferId, MPSendListener listener) {
            this(xferId, null, listener);
        }
    }

    public class MasterMPIO implements MissionPackageIO {
        private final Map<Integer, MPTransferInfo> activeOutboundTransfers;
        private final Map<File, MPReceiver> activeInboundTransfers;

        private volatile MPReceiveInitiator receiveInitiator;

        MasterMPIO() {
            activeOutboundTransfers = new HashMap<>();
            activeInboundTransfers = new HashMap<>();
            receiveInitiator = null;
        }

        public void setRxInitiator(MPReceiveInitiator rxInit) {
            receiveInitiator = rxInit;
        }

        public boolean reconfigFileSharing() {
            if (commo == null)
                return false;

            boolean ret = reconfigLocalWebServer(httpsCertFile);
            // Now make MPs via server match
            commo.setMissionPackageViaServerEnabled(ret);
            return ret;
        }

        private byte[] getHttpsServerCert() throws CommoException {
            if (IOProviderFactory.exists(httpsCertFile)) {
                FileInputStream fis = null;
                try {
                    Log.d(TAG, "HttpsCert examining existing cert file");
                    KeyStore p12 = KeyStore.getInstance("pkcs12");
                    fis = IOProviderFactory.getInputStream(httpsCertFile);
                    p12.load(fis, "atakatak".toCharArray());
                    Enumeration<String> aliases = p12.aliases();
                    int n = 0;
                    String alias = null;
                    while (aliases.hasMoreElements()) {
                        alias = aliases.nextElement();
                        n++;
                        Log.d(TAG, "HttpsCert Alias is \"" + alias + "\"");
                    }
                    if (n != 1)
                        throw new IllegalArgumentException(
                                "Certificate contains " + n
                                        + " aliases - we can only use files with one alias!");

                    Certificate cert = p12.getCertificate(alias);
                    X509Certificate xcert = (X509Certificate) cert;
                    xcert.checkValidity();

                    // All good
                    fis.close();
                    Log.d(TAG,
                            "HttpsCert existing file looks valid, re-using it");
                    fis = IOProviderFactory.getInputStream(httpsCertFile);
                    long len = IOProviderFactory.length(httpsCertFile);
                    if (len > Integer.MAX_VALUE)
                        throw new IllegalArgumentException(
                                "Existing cert is way too large!");
                    byte[] ret = new byte[(int) len];
                    len = 0;
                    while (len < ret.length) {
                        int r = fis.read(ret);
                        if (r == -1)
                            throw new IOException(
                                    "Could not fully read cert file");
                        len += r;
                    }
                    fis.close();
                    return ret;
                } catch (Exception ex) {
                    Log.d(TAG,
                            "HttpsCert problem with existing certificate file, generating new one",
                            ex);
                    if (fis != null)
                        try {
                            fis.close();
                        } catch (IOException ignored) {
                        }
                } finally {
                    IoUtils.close(fis);
                }
            }

            if (!IOProviderFactory.exists(httpsCertFile.getParentFile()))
                IOProviderFactory.mkdirs(httpsCertFile.getParentFile());

            // Generate new cert
            byte[] cert = commo.generateSelfSignedCert("atakatak");
            try (OutputStream fos = IOProviderFactory
                    .getOutputStream(httpsCertFile)) {
                fos.write(cert);
                Log.d(TAG, "HttpsCert new cert stored for later use");
            } catch (IOException ex) {
                Log.e(TAG, "Could not write https certificate file", ex);
            }
            return cert;

        }

        private boolean reconfigLocalWebServer(File certFile) {
            if (filesharingEnabled) {
                if (!nonStreamsEnabled) {
                    // Disable the local web server but return true anyway
                    // because we overall file shares are still enabled.
                    // Emulates the old pre-commo behavior of WebServer class
                    try {
                        commo.setMissionPackageLocalPort(
                                Commo.MPIO_LOCAL_PORT_DISABLE);
                        commo.setMissionPackageLocalHttpsParams(
                                Commo.MPIO_LOCAL_PORT_DISABLE, null, null);
                    } catch (CommoException ex) {
                        Log.e(TAG, "Error disabling local web server", ex);
                    }
                    return true;
                }

                try {
                    commo.setMissionPackageLocalPort(localWebServerPort);
                } catch (CommoException ex) {
                    Log.e(TAG, "Error setting local web server port "
                            + localWebServerPort
                            + " port may already be in use.  Local web server is disabled.",
                            ex);
                    return false;
                }

                try {
                    byte[] cert = getHttpsServerCert();
                    commo.setMissionPackageLocalHttpsParams(
                            secureLocalWebServerPort, cert, "atakatak");
                } catch (CommoException ex) {
                    Log.e(TAG, "Error setting local https server port "
                            + secureLocalWebServerPort
                            + " port may already be in use or certs are invalid.  Local https server is disabled.",
                            ex);
                    // Delete the https certificate in case it was invalid
                    if (!FileSystemUtils.deleteFile(certFile)) {
                        Log.e(TAG, "could not delete certificate file: "
                                + certFile);
                    }
                    // Not considering this fatal error for now - it will be in the future
                }
                return true;

            } else {
                try {
                    commo.setMissionPackageLocalPort(
                            Commo.MPIO_LOCAL_PORT_DISABLE);
                    commo.setMissionPackageLocalHttpsParams(
                            Commo.MPIO_LOCAL_PORT_DISABLE, null, null);
                } catch (CommoException ex) {
                    Log.e(TAG, "Error disabling local web server", ex);
                }
                return false;
            }
        }

        @Override
        public String createUUID() {
            return UUID.randomUUID().toString();
        }

        @Override
        public CoTPointData getCurrentPoint() {
            GeoPoint point = mapView.getSelfMarker().getPoint();
            // XXY what about AGL
            return new CoTPointData(point.getLatitude(), point.getLongitude(),
                    point.getAltitude(), point.getCE(), point.getLE());
        }

        @Override
        public void missionPackageReceiveStatusUpdate(
                MissionPackageReceiveStatusUpdate update) {
            MPReceiver rx;
            synchronized (activeInboundTransfers) {
                rx = activeInboundTransfers.get(update.localFile);
            }
            if (rx == null)
                return;

            switch (update.status) {
                case FINISHED_SUCCESS:
                    rx.receiveComplete(true, null, update.attempt);
                    break;
                case FINISHED_FAILED:
                    rx.receiveComplete(false, update.errorDetail,
                            update.attempt);
                    break;
                case ATTEMPT_FAILED:
                    rx.attemptFailed(update.errorDetail, update.attempt,
                            update.maxAttempts);
                    break;
                case ATTEMPT_IN_PROGRESS:
                    rx.receiveProgress(update.totalBytesReceived,
                            update.totalBytesExpected,
                            update.attempt,
                            update.maxAttempts);
                    break;
                default:
                    Log.w(TAG, "Unknown mp receive status code!");
            }
        }

        @Override
        public File missionPackageReceiveInit(String fileName,
                String transferName,
                String sha256hash,
                long byteLen,
                String senderCallsign)
                throws MissionPackageTransferException {
            MPReceiveInitiator rxInit = receiveInitiator;

            if (!filesharingEnabled || rxInit == null)
                throw new MissionPackageTransferException(
                        MissionPackageTransferStatus.FINISHED_DISABLED_LOCALLY);

            try {
                MPReceiver rx = rxInit.initiateReceive(fileName, transferName,
                        sha256hash,
                        byteLen, senderCallsign);
                if (rx == null)
                    throw new MissionPackageTransferException(
                            MissionPackageTransferStatus.FINISHED_FILE_EXISTS);

                File f = rx.getDestinationFile();
                synchronized (activeInboundTransfers) {
                    activeInboundTransfers.put(f, rx);
                }
                return f;

            } catch (IOException e) {
                throw new MissionPackageTransferException(
                        MissionPackageTransferStatus.FINISHED_FAILED);
            }
        }

        @Override
        public void missionPackageSendStatusUpdate(
                MissionPackageSendStatusUpdate statusUpdate) {
            // One of the transfers had some status change
            MPTransferInfo info;
            MPSendListener.UploadStatus uploadStatus = null;
            boolean sendComplete = false;
            boolean success = false;
            boolean isToServer = statusUpdate.recipient == null;
            String contactUid = isToServer ? null
                    : statusUpdate.recipient.contactUID;

            synchronized (activeOutboundTransfers) {
                info = activeOutboundTransfers.get(statusUpdate.transferId);

                if (info == null) {
                    Log.w(TAG, "MP Transfer update for unknown transfer id "
                            + statusUpdate.transferId + " - ignoring");
                    return;
                }

                switch (statusUpdate.status) {
                    case FINISHED_SUCCESS:
                        success = true;
                        sendComplete = true;
                        break;
                    case FINISHED_TIMED_OUT:
                    case FINISHED_FAILED:
                    case FINISHED_CONTACT_GONE:
                    case FINISHED_DISABLED_LOCALLY:
                        sendComplete = true;
                        break;
                    case SERVER_UPLOAD_FAILED:
                        uploadStatus = UploadStatus.FAILED;
                        break;
                    case SERVER_UPLOAD_IN_PROGRESS:
                        uploadStatus = UploadStatus.IN_PROGRESS;
                        break;
                    case SERVER_UPLOAD_PENDING:
                        uploadStatus = UploadStatus.PENDING;
                        break;
                    case SERVER_UPLOAD_SUCCESS:
                        if (statusUpdate.totalBytesTransferred == 0)
                            uploadStatus = UploadStatus.FILE_ALREADY_ON_SERVER;
                        else
                            uploadStatus = UploadStatus.COMPLETE;
                        break;
                    case ATTEMPT_IN_PROGRESS:
                        // default case in handling below
                        break;
                    default:
                        Log.w(TAG, "Unknown mp send status code!");
                        return;
                }

                if (sendComplete) {
                    if (info.remainingRecipients != null)
                        info.remainingRecipients.remove(statusUpdate.recipient);

                    if (info.remainingRecipients == null
                            || info.remainingRecipients.isEmpty()) {
                        activeOutboundTransfers.remove(statusUpdate.transferId);
                    }
                }
            }

            if (info.listener == null)
                return;

            if (sendComplete) {
                if (success) {
                    if (!isToServer)
                        info.listener.mpAckReceived(contactUid,
                                statusUpdate.additionalDetail,
                                statusUpdate.totalBytesTransferred);
                } else
                    info.listener.mpSendFailed(contactUid,
                            statusUpdate.additionalDetail,
                            statusUpdate.totalBytesTransferred);

            } else if (uploadStatus != null) {
                info.listener.mpUploadProgress(
                        contactUid,
                        uploadStatus,
                        statusUpdate.additionalDetail,
                        statusUpdate.totalBytesTransferred);
            } else if (!isToServer) {
                // Progress notification
                info.listener
                        .mpSendInProgress(statusUpdate.recipient.contactUID);
            }
        }

        void sendMissionPackage(String streamingId,
                File file, String transferFilename,
                MPSendListener listener) throws CommoException {
            int id = commo.sendMissionPackageInit(streamingId,
                    file, transferFilename);

            MPTransferInfo info = new MPTransferInfo(id, listener);
            synchronized (activeOutboundTransfers) {
                activeOutboundTransfers.put(id, info);
            }

            try {
                commo.startMissionPackageSend(id);
            } catch (CommoException ex) {
                synchronized (activeOutboundTransfers) {
                    activeOutboundTransfers.remove(id);
                }
                throw ex;
            }

        }

        void sendMissionPackage(Set<Contact> contacts, File file,
                String transferFilename,
                String transferName,
                MPSendListener listener) throws CommoException {

            List<Contact> contactList = new ArrayList<>(contacts);
            int id = commo.sendMissionPackageInit(contactList, file,
                    transferFilename, transferName);

            // Remove the invalid contacts
            Set<Contact> sentTo = new HashSet<>(contacts);
            sentTo.removeAll(contactList);

            String[] sentToArray = new String[sentTo.size()];
            int i = 0;
            for (Contact c : sentTo)
                sentToArray[i++] = c.contactUID;

            MPTransferInfo info = new MPTransferInfo(id, sentTo, listener);
            synchronized (activeOutboundTransfers) {
                activeOutboundTransfers.put(id, info);
            }

            // Tell the listener about who actually is getting
            // this thing before starting up the transfer
            listener.mpSendRecipients(sentToArray);

            try {
                commo.startMissionPackageSend(id);
            } catch (CommoException ex) {
                synchronized (activeOutboundTransfers) {
                    activeOutboundTransfers.remove(id);
                }
                throw ex;
            }
        }

    }

    public class MasterFileIO implements SimpleFileIO {
        private final Map<Integer, CommsFileTransferListener> clientListeners;
        private final Map<Integer, SimpleFileIOUpdate> transferResults;

        public MasterFileIO() {
            clientListeners = new HashMap<>();
            transferResults = new HashMap<>();
        }

        @Override
        public void fileTransferUpdate(SimpleFileIOUpdate update) {
            if (update.status == SimpleFileIOStatus.INPROGRESS) {
                CommsFileTransferListener target;
                synchronized (clientListeners) {
                    target = clientListeners.get(update.transferId);
                }

                // Update listener
                if (target != null)
                    target.bytesTransferred(update.bytesTransferred,
                            update.totalBytesToTransfer);
            } else {
                // Finished transfer with either success or failure
                synchronized (transferResults) {
                    transferResults.put(update.transferId, update);
                    transferResults.notifyAll();
                }
            }
        }

        /// Wrapping around commo's simple file transfer for synchronous transfers with a callback. See Commo.simpleFileTransferInit()
        public void doFileTransfer(CommsFileTransferListener listener,
                boolean forUpload,
                URI uri, byte[] caCert, String caCertPassword,
                String username, String password, File localFile)
                throws CommoException, IOException, InterruptedException {
            int id = commo.simpleFileTransferInit(forUpload, uri, caCert,
                    caCertPassword, username, password, localFile);
            // Add pending transfer to our listener
            if (listener != null) {
                synchronized (clientListeners) {
                    clientListeners.put(id, listener);
                }
            }
            // Start the transfer 
            commo.simpleFileTransferStart(id);
            // Wait for completion callback
            SimpleFileIOUpdate r;
            synchronized (transferResults) {
                while ((r = transferResults.get(id)) == null) {
                    transferResults.wait();
                }
                transferResults.remove(id);
            }

            // Finished transfer
            String s;
            switch (r.status) {
                case AUTH_ERROR:
                case ACCESS_DENIED:
                    s = "Valid user credentials required";
                    break;
                case CONNECT_FAIL:
                    s = "Could not connect to server";
                    break;
                case HOST_RESOLUTION_FAIL:
                    s = "Unable to resolve server hostname";
                    break;
                case LOCAL_FILE_OPEN_FAILURE:
                    s = "Could not open local file";
                    break;
                case LOCAL_IO_ERROR:
                    s = "I/O error read/writing local file";
                    break;
                case SSL_OTHER_ERROR:
                    s = "SSL protocol error";
                    break;
                case SSL_UNTRUSTED_SERVER:
                    s = "Server cert not trusted - check trust store";
                    break;
                case TRANSFER_TIMEOUT:
                    s = "Transfer timed out";
                    break;
                case URL_INVALID:
                    s = "Invalid server path";
                    break;
                case URL_NO_RESOURCE:
                    s = "Path not accessible on server";
                    break;
                case URL_UNSUPPORTED:
                    s = "Protocol not supported";
                    break;

                case SUCCESS:
                    s = null;
                    break;
                case OTHER_ERROR:
                default:
                    s = "File transfer failed";
                    break;

            }
            if (s != null)
                throw new IOException(s);
        }
    }

    public void setMissionPackageHttpsPort(int missionPackageHttpsPort) {
        try {
            commo.setMissionPackageHttpsPort(missionPackageHttpsPort);
        } catch (CommoException e) {
            Log.e(TAG,
                    "setMissionPackageHttpsPort failed!",
                    e);
        }
    }

    public void setMissionPackageHttpPort(int missionPackageHttpPort) {
        try {
            commo.setMissionPackageHttpPort(missionPackageHttpPort);
        } catch (CommoException e) {
            Log.e(TAG,
                    "setMissionPackageHttpPort failed!",
                    e);
        }
    }
}
