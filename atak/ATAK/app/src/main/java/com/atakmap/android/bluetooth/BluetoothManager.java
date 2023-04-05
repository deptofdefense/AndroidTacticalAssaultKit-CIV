
package com.atakmap.android.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.bluetooth.BluetoothDevicesConfig.BluetoothDeviceConfig;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Manages the Bluetooth Laser Range Finder (LRF) connections. This class assumes Bluetooth is
 * already enabled on the device.
 * 
 *
 */
public class BluetoothManager {

    private static final String TAG = "BluetoothManager";

    private Context context = null;
    private boolean running = false;
    private boolean interrupt = false;

    private final Map<BluetoothDevice, BluetoothCotManager> connectionMap = new HashMap<>();

    private final List<BluetoothDevice> classicConnected = new ArrayList<>();
    private final List<BluetoothDevice> tempDeviceList = new ArrayList<>();

    public interface BluetoothConnectionListener {
        void connected(BluetoothDevice bd);

        void disconnected(BluetoothDevice bd);

        void error(BluetoothDevice bd);
    }

    private final List<BluetoothConnectionListener> bcListeners = new ArrayList<>();
    private final List<BluetoothReaderFactory> externalBTRFactoryList = new ArrayList<>();

    private static BluetoothManager _instance = null;

    BluetoothManager() {
        _instance = this;
    }

    public static BluetoothManager getInstance() {
        return _instance;
    }

    /**
     * This interface allows for external BluetoothReaders to be registered by a plugin.
     * If a match with this fails, then the internal readers are used.
     */
    public interface BluetoothReaderFactory {
        boolean matches(BluetoothDevice device);

        BluetoothReader create(BluetoothDevice device);
    }

    /**
     * Allows for plugins to create a reader for a specific bluetooth device.   This will allow for
     * a plugin to override the current bluetooth behavior.
     * @param brf the bluetooth reader factory.
     */
    public void addExternalBluetoothReader(BluetoothReaderFactory brf) {
        synchronized (externalBTRFactoryList) {
            externalBTRFactoryList.add(brf);
        }
        Intent i = new Intent(BluetoothFragment.ACTION_RESCAN);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * Removes a previously registered bluetooth reader factory.
     * @param brf the bluetooth reader factory to remove.
     */
    public void removeExternalBluetoothReader(BluetoothReaderFactory brf) {
        synchronized (externalBTRFactoryList) {
            externalBTRFactoryList.remove(brf);
        }
        Intent i = new Intent(BluetoothFragment.ACTION_RESCAN);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * Initiates a rescan of all the bluetooth devices.
     */
    void rescan() {
        stop();
        start();
    }

    /**
     * Lists all Bluetooth devices currently known by the bluetooth manager.
     * This includes both classic bluetooth devices and bt low energy devices.
     * @return a list of BluetoothDevice's known by the bluetooth manager.
     */
    @SuppressLint({
            "MissingPermission"
    })
    synchronized public List<BluetoothDevice> getAllDevices() {
        List<BluetoothDevice> retval = new ArrayList<>();

        for (BluetoothDevice device : connectionMap.keySet()) {
            try {
                Log.d(TAG, "connectionMap: " + device.getName());
            } catch (Exception ignored) {
            }
            retval.add(device);
        }
        retval.addAll(BtLowEnergyManager.getInstance().getConnected());
        return retval;
    }

    /**
     * Lists all Bluetooth devices currently connected through the bluetooth manager.
     * This includes both classic bluetooth devices and bt low energy devices.
     * @return a list of BluetoothDevice's currently considered connected.
     */
    public List<BluetoothDevice> getConnections() {
        List<BluetoothDevice> connections = new ArrayList<>(classicConnected);
        connections.addAll(BtLowEnergyManager.getInstance().getConnected());
        return connections;

    }

    /**
     * Adds a connection listener for classic bluetooth devices.
     * @param bcl the connection listener
     */
    public void addConnectionListener(BluetoothConnectionListener bcl) {
        synchronized (bcListeners) {
            bcListeners.add(bcl);
        }
    }

    /**
     * Remove a connection listener for classic bluetooth devices.
     * @param bcl the connection listener
     */
    public void removeConnectionListener(BluetoothConnectionListener bcl) {
        synchronized (bcListeners) {
            bcListeners.remove(bcl);
        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    void fireConnected(BluetoothDevice device) {
        try {
            Log.d(TAG, "status bt classic connected: " + device.getName());
            classicConnected.add(device);
            synchronized (bcListeners) {
                for (BluetoothConnectionListener bcl : bcListeners) {
                    try {
                        bcl.connected(device);
                    } catch (Exception e) {
                        Log.d(TAG, "error using callback: " + bcl, e);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    void fireDisconnected(BluetoothDevice device) {
        try {
            Log.d(TAG, "status bt classic disconnected: " + device.getName());
            classicConnected.remove(device);
            synchronized (bcListeners) {
                for (BluetoothConnectionListener bcl : bcListeners) {
                    try {
                        bcl.disconnected(device);
                    } catch (Exception e) {
                        Log.d(TAG, "error using callback: " + bcl, e);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    void fireError(BluetoothDevice device) {
        try {
            Log.d(TAG, "status bt classic error: " + device.getName());
            classicConnected.remove(device);
            synchronized (bcListeners) {
                for (BluetoothConnectionListener bcl : bcListeners) {
                    try {
                        bcl.error(device);
                    } catch (Exception e) {
                        Log.d(TAG, "error using callback: " + bcl, e);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Map of supported Bluetooth device names to reader implementations. Make sure to use
     * <code>LRFManager.conditionName(String)</code> whenever accessing this map.
     */
    private final HashMap<String, Class<? extends BluetoothReader>> DEVICE_MAP = new HashMap<>();

    /**
     * In order for the legacy ATSK to work, we need to programatically disable
     * internal support for the R10 and R8 RTK units.   This will be removed if
     * ATSK is retired.
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    synchronized public void disableForATSK() {
        Object o = DEVICE_MAP.remove("R10");
        DEVICE_MAP.remove("R8");

        if (o != null) {
            Thread t = new Thread() {
                @Override
                public void run() {
                    rescan();
                }
            };
            t.start();
        }
    }

    private void loadConfig() {
        // load supported devices and their reader class from config file
        BluetoothDevicesConfig config = BluetoothDevicesConfig
                .getConfiguredDevices();
        String configVersion = "na";
        if (config == null || !config.hasDevices()) {
            Log.w(TAG,
                    "Could not load any supported Bluetooth Device Configurations");
            config = null;
        } else {
            configVersion = config.getVersion();
        }

        //check BT config version in APK, if not same as local version, then
        //throw local version away
        BluetoothDevicesConfig apkMenus = BluetoothManager
                .getApkConfiguredDevices(this.context);
        if ((config == null && apkMenus != null)
                || (apkMenus != null && apkMenus.hasDevices() &&
                        !FileSystemUtils.isEquals(apkMenus.getVersion(),
                                configVersion))) {
            Log.d(TAG, "Updating Bluetooth Config version: " + configVersion
                    + " to " + apkMenus.getVersion());
            config = apkMenus;
            config.save();
        }

        if (config == null) {
            Log.w(TAG,
                    "Failed not load any supported Bluetooth Device Configurations");
            return;
        }

        for (BluetoothDeviceConfig device : config.getDevices()) {
            if (device == null || !device.isValid()) {
                Log.w(TAG, "Skipping invalid device");
                continue;
            }

            try {
                Class<?> clazz = Class.forName(device.getReaderClass());
                if (clazz == null
                        || !BluetoothReader.class.isAssignableFrom(clazz)) {
                    Log.w(TAG,
                            "Skipping invalid device reader class: "
                                    + device.getReaderClass());
                } else {
                    Class<? extends BluetoothReader> readerClass = (Class<? extends BluetoothReader>) clazz;
                    DEVICE_MAP.put(device.getName(), readerClass);
                }
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Failed to find Bluetooth Reader Class: "
                        + device.getReaderClass(),
                        e);
            }
        }

        Log.d(TAG, "Loaded " + DEVICE_MAP.size()
                + " supported Bluetooth Device Configurations");
    }

    private static BluetoothDevicesConfig getApkConfiguredDevices(
            Context context) {
        try {
            InputStream is = context.getAssets().open(
                    BluetoothDevicesConfig.DEFAULT_BLUETOOTH_CONFIG);
            byte[] data;
            try {
                int length = is.available();
                data = new byte[length];
                int r = is.read(data);
                if (r != data.length)
                    Log.d(TAG,
                            "data, read: " + r + " expected: " + data.length);
            } finally {
                is.close();
            }
            String menuString = new String(data, FileSystemUtils.UTF8_CHARSET);

            BluetoothDevicesConfig btConfig = BluetoothDevicesConfig
                    .getConfiguredDevices(menuString);
            if (btConfig == null || !btConfig.hasDevices()) {
                Log.e(TAG, "Failed to load Bluetooth Devices");
                return null;
            } else {
                return btConfig;
            }
        } catch (Exception e1) {
            Log.e(TAG, "Failed to read Bluetooth Devices", e1);
            return null;

        }
    }

    private BluetoothReader getExternallySupportedReader(
            BluetoothDevice device) {
        synchronized (externalBTRFactoryList) {
            for (final BluetoothReaderFactory brf : externalBTRFactoryList) {
                try {
                    if (brf.matches(device))
                        return brf.create(device);
                } catch (Exception e) {
                    final MapView mapView = MapView.getMapView();
                    Log.e(TAG,
                            "Failed to match using an external bluetooth implementation "
                                    + brf,
                            e);
                    mapView.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    mapView.getContext(),
                                    context.getString(
                                            R.string.plugin_btreader_failed)
                                            + brf,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }
        return null;
    }

    @SuppressLint({
            "MissingPermission"
    })
    private BluetoothReader getInternallySupportedReader(BluetoothDevice device)
            throws BluetoothReaderException {
        Class<? extends BluetoothReader> readerClass = getReader(device);
        if (readerClass == null) {
            throw new BluetoothReaderException("Failed to find reader for: "
                    + device.getName());
        }

        try {
            Constructor<? extends BluetoothReader> constructor = readerClass
                    .getConstructor(BluetoothDevice.class);
            return constructor.newInstance(device);
        } catch (NoSuchMethodException | InvocationTargetException
                | InstantiationException | IllegalAccessException
                | RuntimeException e) {
            throw new BluetoothReaderException(
                    "Internally Supported Reader Failed", e);
        }
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
        loadConfig();
    }

    /**
     * Sets the interrupt flag which can be used by other methods within the Bluetooth
     * Manager.  Most notably will allow the start() mechanism to break out early.
     */
    public void interrupt() {
        interrupt = true;
    }

    /**
     * Start the scanning process.   Once scanning has begun, it will go through a
     * period of Bluetooth Low Energy Scanning followed by a period of classic bluetooth
     * scanning.   At any point along the way, if interrupt is called, it will only interrupt
     * the next stage of scanning.  If interrupt is processed, the user still will need to call
     * stop before starting again.
     */
    @SuppressLint({
            "MissingPermission"
    })
    public synchronized void start() {

        stop();

        interrupt = false;

        Log.d(TAG, "starting the bluetooth scanning process");
        if (running) {
            Log.w(TAG, "already running");
            return;
        }
        if (context == null) {
            Log.w(TAG, "error, not initialized");
            return;
        }
        final MapView mapView = MapView.getMapView();

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null)
            return;

        running = true;

        if (adapter.isDiscovering())
            adapter.cancelDiscovery();

        if (BtLowEnergyManager.getInstance().scan()) {
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            context,
                            R.string.bt_le_time,
                            Toast.LENGTH_LONG).show();
                    Log.d(TAG,
                            "Starting scan for supported BT Low Energy devices");
                }
            });
            BtLowEnergyManager.getInstance().waitForScanToFinish();

        } else {
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context,
                            R.string.bt_le_error,
                            Toast.LENGTH_SHORT)
                            .show();
                    Log.w(TAG, "Unable to start BT Low Energy scan");
                }
            });
        }

        if (interrupt) {
            return;
        }

        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        if (devices == null)
            return;

        for (BluetoothDevice d : devices) {
            Log.d(TAG, "bonded: " + d.getName());
        }

        int supportedDevices = checkBondedDevices(devices);
        if (supportedDevices == 0) {

            Log.d(TAG, "no supported bluetooth devices are paired");
            if (adapter.startDiscovery()) {
                mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                context,
                                R.string.bt_classic_time,
                                Toast.LENGTH_LONG).show();
                        Log.d(TAG,
                                "Starting scan for supported BT devices");
                    }
                });
            } else {
                mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, R.string.bt_classic_error,
                                Toast.LENGTH_SHORT)
                                .show();
                        Log.w(TAG, "Unable to start BT scan");
                    }
                });
            }

        } else {
            Log.d(TAG, "attempting connect to " + devices.size()
                    + " bt devices");
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            context,
                            R.string.bt_classic_time,
                            Toast.LENGTH_SHORT).show();
                }
            });

            for (final BluetoothDevice d : devices) {
                try {
                    Log.d(TAG,
                            "attempting connect to paired bt device: "
                                    + d.getName());
                    checkStartNewReadThread(d);
                } catch (BluetoothReaderException e) {
                    Log.d(TAG,
                            "unsupported bluetooth device: "
                                    + d.getName()
                                    + ", " + d
                                    + ". " + e.getMessage());
                }
            }
        }
    }

    /**
     * @param devices set of devices
     * @return number of supported bonded devices
     */
    private int checkBondedDevices(Set<BluetoothDevice> devices) {
        int count = 0;
        for (BluetoothDevice d : devices) {
            if (isSupported(d)) {
                count++;
            }
        }

        return count;
    }

    public synchronized void stop() {

        if (!running) {
            Log.d(TAG, "already stopped");
            return;
        }

        for (BluetoothDevice device : connectionMap.keySet()) {
            BluetoothCotManager t = connectionMap.get(device);
            if (t != null)
                t.stop();
        }

        connectionMap.clear();

        BtLowEnergyManager.getInstance().closeAllHandlers();

        running = false;
    }

    public void dispose() {
        unregisterReceivers();
    }

    void registerReceiversAsNeeded() {
        DocumentedIntentFilter filter1 = new DocumentedIntentFilter();
        filter1.addAction(BluetoothDevice.ACTION_FOUND);
        AtakBroadcast.getInstance().registerReceiver(deviceInfoRx, filter1);

        DocumentedIntentFilter filter2 = new DocumentedIntentFilter();
        filter2.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter2.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        AtakBroadcast.getInstance().registerReceiver(adapterDiscoveryRx,
                filter2);
    }

    void unregisterReceivers() {
        try {
            AtakBroadcast.getInstance().unregisterReceiver(deviceInfoRx);
        } catch (Exception ignored) {
            // chance this is not registered
        }
        try {
            AtakBroadcast.getInstance().unregisterReceiver(
                    adapterDiscoveryRx);
        } catch (Exception ignored) {
            // chance this is not registered
        }
    }

    /**
     * Checks if there is already a read thread for the supplied device. If there is not it starts a
     * new thread to read from this device.
     * 
     * @param d the device to possibly start a read thread for
     *
     */
    @SuppressLint({
            "MissingPermission"
    })
    private void checkStartNewReadThread(final BluetoothDevice d)
            throws BluetoothReaderException {
        BluetoothCotManager t;
        synchronized (this) {
            if (!connectionMap.containsKey(d)) {
                Log.d(TAG, "Creating CoT Manager for device: " + d.getName());

                BluetoothReader reader;

                reader = getExternallySupportedReader(d);

                if (reader == null)
                    reader = getInternallySupportedReader(d);

                t = reader.getCotManager(MapView.getMapView());
                connectionMap.put(d, t);

            } else {
                Log.d(TAG,
                        "CoT Manager already created for device: "
                                + d.getName());
                t = connectionMap.get(d);
            }
        }
        if (t != null)
            t.start();
    }

    @SuppressLint({
            "MissingPermission"
    })
    private Class<? extends BluetoothReader> getReader(
            BluetoothDevice device) {
        for (Entry<String, Class<? extends BluetoothReader>> entry : DEVICE_MAP
                .entrySet()) {
            if (!FileSystemUtils.isEmpty(entry.getKey())
                    && device.getName().startsWith(entry.getKey()))
                return entry.getValue();
        }

        return null;
    }

    @SuppressLint({
            "MissingPermission"
    })
    private boolean isSupported(final BluetoothDevice device) {

        if (device == null) {
            Log.w(TAG, "error trying to determine if a device is supported");
            return false;
        }

        final String name = device.getName();
        if (name == null) {
            Log.w(TAG,
                    "error communicating with bluetooth device without name");
            return false;
        }

        synchronized (externalBTRFactoryList) {
            for (final BluetoothReaderFactory brf : externalBTRFactoryList) {
                try {
                    if (brf.matches(device))
                        return true;
                } catch (Exception e) {
                    Log.w(TAG, "error with matcher on:" + name, e);
                }
            }
        }

        for (String key : DEVICE_MAP.keySet()) {
            if (!FileSystemUtils.isEmpty(key)
                    && device.getName().startsWith(key))
                return true;
        }

        return false;
    }

    @SuppressLint({
            "MissingPermission"
    })
    private final BroadcastReceiver deviceInfoRx = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice d = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (isSupported(d)) {
                Toast.makeText(context, "Found " + d.getName(),
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "Found supported device" + d.getName());
                tempDeviceList.add(d);
            } else {
                Log.d(TAG, "Ignoring unsupported device" + d.getName());
            }
        }
    };

    @SuppressLint({
            "MissingPermission"
    })
    private final BroadcastReceiver adapterDiscoveryRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Toast.makeText(context, R.string.scanning_supported_bt_devices,
                        Toast.LENGTH_SHORT)
                        .show();
                Log.d(TAG, "Scanning for supported BT devices");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
                    .equals(action)) {
                Log.d(TAG,
                        "Done searching for supported BT devices, count: "
                                + tempDeviceList.size());
                if (tempDeviceList.size() > 0) {
                    Toast.makeText(
                            context,
                            "Done searching, found " + tempDeviceList.size()
                                    + " BT devices",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context,
                            R.string.done_searching_no_bt,
                            Toast.LENGTH_SHORT).show();
                }

                ArrayList<BluetoothDevice> threadList = new ArrayList<>(
                        tempDeviceList);
                for (BluetoothDevice d : threadList) {
                    try {
                        checkStartNewReadThread(d);
                    } catch (BluetoothReaderException e) {
                        Log.e(TAG,
                                "Failed to start new read thread for discovered device: "
                                        + d.getName(),
                                e);
                    }
                }

                tempDeviceList.clear();
            }
        }
    };
}
