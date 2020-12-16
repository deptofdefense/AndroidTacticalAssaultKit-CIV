
package com.atakmap.android.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.io.IOException;
import java.util.UUID;

public abstract class BluetoothConnection {

    private static final String TAG = "BluetoothConnection";

    public static final UUID MY_UUID_INSECURE = UUID
            .fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int NOTIFY_ID = 43987; //arbitrary
    private static final int RECONNECT_ATTEMPT_DELAY_MSEC = 2000;
    private static final int DEFAULT_RECONNECT_SECONDS = 180;

    private final BluetoothDevice device;
    private final UUID uuid;

    private boolean running = false;

    private Callback callback = null;

    private int reconnectSeconds;
    private int errors = 0;

    /**
     * Millis since epoch, that restart attempt begun
     */
    private long reconnectStartTime;

    BluetoothConnection(final BluetoothDevice device, final UUID uuid) {
        this.device = device;
        this.uuid = uuid;
        this.reconnectSeconds = DEFAULT_RECONNECT_SECONDS;
        reconnectStartTime = -1;
    }

    /**
     * @return a byte[] containing the next bit of data read
     */
    protected abstract byte[] read() throws IOException;

    protected abstract void onStart(BluetoothDevice device, UUID uuid)
            throws IOException;

    protected abstract void onStop() throws IOException;

    protected abstract boolean isConnected();

    @SuppressLint({
            "MissingPermission"
    })
    public synchronized void start() {
        setRunning(true);
        try {
            //throws IOException if connection fails
            onStart(device, uuid);
            Log.d(TAG, "Connected to device: " + device.getName());
            BluetoothManager.getInstance().fireConnected(device);

            //connected, now start read thread
            Thread myThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRunning()) {
                        try {
                            if (!isConnected()) {
                                BluetoothManager.getInstance()
                                        .fireDisconnected(device);
                                throw new IOException(
                                        "Socket not connected for device:"
                                                + device.getName());
                            }

                            //attempt read, and process the data
                            byte[] d = read();
                            if (d != null)
                                if (callback != null) {
                                    callback.onRead(d);
                                }
                            errors = 0;
                            reconnectStartTime = -1;
                        } catch (IOException e) {
                            if (!isRunning()) {
                                Log.d(TAG, "shutting down the connection");
                                continue;
                            }
                            Log.d(TAG, "error case occurred, start retry");
                            errors++;
                            if (reconnectStartTime < 0)
                                reconnectStartTime = new CoordinatedTime()
                                        .getMilliseconds();

                            final MapView view = MapView.getMapView();
                            if (view != null) {
                                SharedPreferences prefs = PreferenceManager
                                        .getDefaultSharedPreferences(view
                                                .getContext());
                                try {
                                    reconnectSeconds = Integer
                                            .parseInt(prefs
                                                    .getString(
                                                            "atakBluetoothReconnectSeconds",
                                                            String.valueOf(
                                                                    DEFAULT_RECONNECT_SECONDS)));
                                } catch (NumberFormatException nfe) {
                                    Log.w(TAG, nfe);
                                    prefs.edit()
                                            .putString(
                                                    "atakBluetoothReconnectSeconds",
                                                    String.valueOf(
                                                            DEFAULT_RECONNECT_SECONDS))
                                            .apply();
                                    reconnectSeconds = DEFAULT_RECONNECT_SECONDS;
                                }

                                if (reconnectSeconds < 0)
                                    reconnectSeconds = DEFAULT_RECONNECT_SECONDS;
                            }

                            if (view != null && errors == 1) {
                                //toast that we need to reconnect
                                BluetoothManager.getInstance()
                                        .fireError(device);
                                view.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(
                                                view.getContext(),
                                                "Attempting reconnect to Bluetooth device: "
                                                        + device.getName(),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            }

                            long reconnectTime = new CoordinatedTime()
                                    .getMilliseconds() - reconnectStartTime;
                            if (reconnectTime > (reconnectSeconds * 1000L)) {
                                Log.e(TAG, "Failed to start connection after "
                                        + reconnectSeconds + " seconds", e);
                                setRunning(false);
                                try {
                                    onStop();
                                } catch (IOException e1) {
                                    Log.e(TAG, "Failed to stop", e1);
                                }

                                BluetoothManager.getInstance()
                                        .fireError(device);
                                //notify that we failed to reconnect
                                NotificationUtil
                                        .getInstance()
                                        .postNotification(
                                                NOTIFY_ID,
                                                R.drawable.ic_network_error_notification_icon,
                                                NotificationUtil.RED,
                                                "Bluetooth Error",
                                                "Check connection and then toggle Bluetooth or restart app to reconnect. Failed to reconnect to device: "
                                                        + device.getName(),
                                                null, true);
                                return;
                            }

                            Log.e(TAG,
                                    "bluetooth connection read error attempt + "
                                            + errors
                                            + " since last good read. Current reconnect millis: "
                                            + reconnectTime,
                                    e);

                            try {
                                Thread.sleep(RECONNECT_ATTEMPT_DELAY_MSEC);
                            } catch (InterruptedException e1) {
                                Log.e(TAG, "Sleep interruption", e1);
                            }

                            Log.e(TAG, "stopping bluetooth connection");
                            try {
                                onStop();
                            } catch (IOException e1) {
                                Log.e(TAG, "Failed to stop", e1);
                            }

                            Log.e(TAG, "restarting bluetooth connection");
                            try {
                                onStart(device, uuid);
                                Log.d(TAG,
                                        "Reconnected to: " + device.getName());
                                BluetoothManager.getInstance().fireConnected(
                                        device);
                            } catch (IOException e1) {
                                Log.e(TAG, "Failed to start", e1);
                            }
                        }
                    }

                    Log.d(TAG,
                            "Finished read loop for device: "
                                    + device.getAddress());
                    BluetoothManager.getInstance().fireDisconnected(device);
                }
            }, "BluetoothReaderThread");
            myThread.setName("Bluetooth Reading - " + device.getAddress());
            myThread.start();
        } catch (IOException e1) {
            Log.e(TAG, "Failed to start connection", e1);
            setRunning(false);
        }
    }

    /**
     * Stops the current bluetooth connection threads.
     */
    @SuppressLint({
            "MissingPermission"
    })
    public void stop() {
        setRunning(false);
        Log.d(TAG, "calling to stop bluetooth: " + device.getName());
        synchronized (this) {
            try {
                onStop();
            } catch (IOException e) {
                Log.e(TAG, "Error stopping connection", e);
            }
        }
    }

    /**
     * The implementation of the callback to occur when the connection has data available.
     * @param callback the callback to handle data
     */
    public void setCallback(final Callback callback) {
        this.callback = callback;
    }

    /**
     * Check to see if the connection is running.
     * @return true if the connection is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Change the state of the connection, running or stopped.
     * @param r the running state, true if running.
     */
    public void setRunning(final boolean r) {
        this.running = r;
    }

    public interface Callback {
        /**
         * The byte array read from the Bluetooth connection.
         * @param data the bytes read from the bluetooth connection.
         */
        void onRead(byte[] data);
    }

    /**
     * Gets the bluetooth device associated with the bluetooth connection.
     * @return the bluetooth device
     */
    public BluetoothDevice getDevice() {
        return device;
    }
}
