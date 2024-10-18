
package com.atakmap.android.bluetooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

/**
 * Base hook into ATAK for Bluetooth initialization.<br/>
 * NOTE: the PLRF15c Bluetooth password is '0'.
 * 
 *
 */
public class BluetoothFragment implements OnSharedPreferenceChangeListener {

    public static final String TAG = "BluetoothFragment";
    public static final String ACTION_RESCAN = "com.atakmap.android.bluetooth.RESCAN";
    public static final String ACTION_RESCAN_COMPLETE = "com.atakmap.android.bluetooth.RESCAN_COMPLETE";

    private static final int START_BT_REQUEST_CODE = 17;
    private static final int NOTIFICATION_ID = 810070078;

    private Context context = null;
    private SharedPreferences prefs = null;
    private final BluetoothManager manager = new BluetoothManager();

    // per Matt's request to have ATAK turn off BT iff ATAK is the starter
    private boolean atakStartedBluetooth = false;

    public synchronized void onStart() {
        if (context == null) {
            this.context = MapView.getMapView().getContext();

            FileSystemUtils.ensureDataDirectory(BluetoothDevicesConfig.DIRNAME,
                    false);
            FileSystemUtils.copyFromAssetsToStorageFile(context,
                    BluetoothDevicesConfig.DEFAULT_BLUETOOTH_CONFIG,
                    BluetoothDevicesConfig.CONFIG_FILE_PATH, false);

            manager.setContext(context);

            this.prefs = PreferenceManager.getDefaultSharedPreferences(context);

            // this is icky, we have to do an initial register of
            // broadcast receivers to make sure stuff gets handled
            // by the Bluetooth broadcast receivers
            registerAllReceivers();

            prefs.registerOnSharedPreferenceChangeListener(this);

        }

        if (prefs.getBoolean("atakControlBluetooth", false)) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            onBluetooth();
            if (adapter == null) {
                Toast.makeText(
                        context,
                        "Serious bluetooth error: does your device have bluetooth?",
                        Toast.LENGTH_LONG).show();
                stopManager();
                Log.w(TAG,
                        "Serious bluetooth error: does your device have bluetooth?");

            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences prefs, final String key) {

        if (key == null)
            return;

        if (key.equals("atakControlBluetooth")) {
            if (prefs.getBoolean(key, false)) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                Log.d(TAG, "start the bluetooth connection");
                onBluetooth();
                if (adapter == null) {
                    Toast.makeText(
                            context,
                            "Serious bluetooth error: does your device have bluetooth?",
                            Toast.LENGTH_LONG).show();
                    stopManager();
                    Log.w(TAG,
                            "Serious bluetooth error: does your device have bluetooth?");

                }
            } else {
                stopManager();
                Log.d(TAG, "stop the bluetooth connection");
            }
        }
    }

    /**
     * Optionally, your application can also listen for the ACTION_STATE_CHANGED broadcast Intent,
     * which the system will broadcast whenever the Bluetooth state has changed. This broadcast
     * contains the extra fields EXTRA_STATE and EXTRA_PREVIOUS_STATE, containing the new and old
     * Bluetooth states, respectively.<br/>
     * Possible values for these extra fields are STATE_TURNING_ON, STATE_ON, STATE_TURNING_OFF, and
     * STATE_OFF. Listening for this broadcast can be useful to detect changes made to the Bluetooth
     * state while your app is running.
     */
    private Thread btScanner = null;

    @SuppressLint({
            "MissingPermission"
    })
    synchronized private void onBluetooth() {

        if (btScanner == null || !btScanner.isAlive()) {

            btScanner = new Thread() {
                @Override
                public void run() {
                    Log.d(TAG, "bluetooth scanning started");
                    BluetoothAdapter adapter = BluetoothAdapter
                            .getDefaultAdapter();

                    // guard against the state where bluetooth adapter is null
                    if (adapter == null)
                        return;

                    if (!adapter.isEnabled()) {
                        Intent i = new Intent(
                                BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        ((Activity) MapView.getMapView().getContext())
                                .startActivityForResult(i,
                                        START_BT_REQUEST_CODE);
                        atakStartedBluetooth = true;
                    } else {
                        manager.start();
                        toggleNotification(true);
                    }
                }
            };
            btScanner.start();

        } else {
            Log.d(TAG, "bluetooth scanning in progress");
        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    public void onDestroy() {
        stopManager();
        manager.dispose();

        BtLowEnergyManager.getInstance().dispose();

        if (atakStartedBluetooth) {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            if (adapter != null) {
                adapter.disable();
            }

            Toast.makeText(context, R.string.shutting_down_bt,
                    Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Shutting down Bluetooth");
        }
        unregisterAllReceivers();

    }

    private void registerAllReceivers() {
        AtakBroadcast.getInstance().registerReceiver(btRescanReceiver,
                new DocumentedIntentFilter(ACTION_RESCAN));
        AtakBroadcast.getInstance().registerSystemReceiver(
                btStateReceiver,
                new DocumentedIntentFilter(
                        BluetoothAdapter.ACTION_STATE_CHANGED));
        manager.registerReceiversAsNeeded();
    }

    private void unregisterAllReceivers() {
        AtakBroadcast.getInstance().unregisterSystemReceiver(btStateReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(btRescanReceiver);
        manager.unregisterReceivers();
    }

    private void stopManager() {
        Thread t = new Thread() {
            public void run() {
                toggleNotification(false);
                manager.interrupt();
                manager.stop();
            }
        };
        t.start();
    }

    /**
     * Toggle bluetooth rescan notification
     * @param enable Enable bluetooth recan notification
     */
    private void toggleNotification(boolean enable) {
        if (enable) {
            NotificationUtil.getInstance().postNotification(
                    NOTIFICATION_ID,
                    R.drawable.bluetooth_reconnect, NotificationUtil.WHITE,
                    context.getString(R.string.bt_rescan_title),
                    context.getString(R.string.bt_rescan_msg),
                    new Intent(ACTION_RESCAN), false);

            Log.d(TAG, "Displaying Bluetooth rescan notification");
        } else {
            NotificationUtil.getInstance().clearNotification(
                    NOTIFICATION_ID);
            Log.d(TAG, "Removing Bluetooth rescan notification");
        }
    }

    private final BroadcastReceiver btRescanReceiver = new BroadcastReceiver() {
        boolean inProgress = false;

        @Override
        public void onReceive(final Context context, final Intent intent) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (!inProgress) {
                        Log.d(TAG, "intent bt rescan started");
                        inProgress = true;
                        if (prefs.getBoolean("atakControlBluetooth", false)) {
                            manager.rescan();
                        } else if (intent.getExtras() != null
                                && intent.getExtras().getBoolean("enable")) {
                            Log.d(TAG,
                                    "processing request to enable bluetooth in atak");
                            prefs.edit()
                                    .putBoolean("atakControlBluetooth", true)
                                    .apply();
                        }
                        Intent i = new Intent(ACTION_RESCAN_COMPLETE);
                        AtakBroadcast.getInstance().sendBroadcast(i);
                        inProgress = false;
                        Log.d(TAG, "intent bt rescan ended");
                    } else {
                        Log.d(TAG, "scan already in progress");
                        MapView.getMapView().post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context,
                                        R.string.bt_scan_inprogress,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
            t.start();
        }
    };

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {

            int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);

            if (!prefs.getBoolean("atakControlBluetooth", false)) {
                Log.d(TAG, "Ignoring Bluetooth State Change: " + btState);
                return;
            }

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                // explicitly guard against crashes, it should be impossible
                // to get in here if there isn't a BT adapter, but you never know
                return;
            }

            if (btState == BluetoothAdapter.ERROR) {
                Log.d(TAG, "Unable to read state change from Bluetooth intent");
            } else if (btState == BluetoothAdapter.STATE_ON) {
                MapView.getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, R.string.bt_started,
                                Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "bluetooth started");
                    }
                });
                Intent i = new Intent(ACTION_RESCAN);
                AtakBroadcast.getInstance().sendBroadcast(i);
                toggleNotification(true);
            } else if (btState == BluetoothAdapter.STATE_OFF) {

                MapView.getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, R.string.bt_stopped,
                                Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "bluetooth stopped");
                    }
                });
                stopManager();
            } else {
                Log.d(TAG, "ignoring undefined Bluetooth change state: "
                        + btState);
            }
        }
    };
}
