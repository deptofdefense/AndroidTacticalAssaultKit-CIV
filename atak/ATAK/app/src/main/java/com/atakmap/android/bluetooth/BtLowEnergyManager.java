
package com.atakmap.android.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BtLowEnergyManager {

    private final List<BluetoothLEHandler> btleHandlers = new ArrayList<>();

    private final List<BluetoothLEHandler> discovered = new ArrayList<>();
    private static BtLowEnergyManager _instance;
    private final static String TAG = "BtLowEnergyManager";
    private Timer t = null;
    private boolean mScanning = false;

    private final static int SCAN_DELAY = 10000;

    private BtLowEnergyManager() {
    }

    /**
     * Retrieve the bluetooth low energy manager instance.  Used to register and unregister
     * BtLeHandlers.
     * @return the instance of the BtLowEnergyManager
     */
    public synchronized static BtLowEnergyManager getInstance() {
        if (_instance == null)
            _instance = new BtLowEnergyManager();
        return _instance;
    }

    public interface BluetoothLEConnectionStatus {
        /**
         * Provides for connection status callback from the BluetoothLE Handler
         * @param device the device that has the pairing error.
         */
        void pairingError(BluetoothDevice device);
    }

    public interface BluetoothLEHandler {
        /**
         * Is called back during the scanning process.   The handler is responsible for handling
         * the results of the scan to include bonding, handling passing of the appropriate data
         * to the TAK architecture.
         * @param scanResult the scan result to see if it is handled.
         * @return boolean if the handler will handle the btle device
         */
        boolean onScanResult(ScanResult scanResult);

        /**
         * Connects to the bluetooth device.
         */
        void connect();

        /**
         * Closes the connection but the handler can be reused
         */
        void close();

        /**
         * Clean up the handler, close connections and unregister associated callbacks.  After dispose
         * is called, the handler can no longer be used correctly without recreating it.
         */
        void dispose();

        /**
         * Returns the underlying BluetoothDevice associated with this handler.
         * @return the bluetooth device.
         */
        BluetoothDevice getDevice();

        /**
         * Register the Bluetooth Connection listener with the handler.
         */
        void setConnectionListener(
                BluetoothLEConnectionStatus connectionStatus);

    }

    /**
     * Manages a bluetooth low energy connection.   Since the implementation is on a per device
     * basis, the handler will need to perform all actions to start the scanning process,   Any
     * registered handlers will be automatically disposed during shutdown.
     *
     * @param btleHandler the handler for the btle device.
     */
    public void addExternalBtleHandler(BluetoothLEHandler btleHandler) {
        synchronized (this.btleHandlers) {
            btleHandlers.add(btleHandler);
        }
        Intent i = new Intent(BluetoothFragment.ACTION_RESCAN);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * Remove the BtleHandler from the external list of btleHandlers.
     * @param btleHandler the btle handler.
     */
    public void removeExternalBtleHandler(BluetoothLEHandler btleHandler) {
        synchronized (this.btleHandlers) {
            btleHandlers.remove(btleHandler);
        }
        Intent i = new Intent(BluetoothFragment.ACTION_RESCAN);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    @SuppressLint({
            "MissingPermission"
    })
    synchronized boolean scan() {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Log.d(TAG, "no bluetooth adapter found");
            return false;
        }
        closeAllHandlers();

        if (t != null)
            t.cancel();

        // Stops scanning after a pre-defined scan period.
        t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!mScanning)
                    return;
                BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
                if (scanner != null) {
                    scanner.stopScan(leScanCallback);
                    scanner.flushPendingScanResults(leScanCallback);
                }
                mScanning = false;
            }
        }, SCAN_DELAY);

        mScanning = true;
        Log.d(TAG, "starting bt low energy scan");
        synchronized (btleHandlers) {
            discovered.clear();
        }

        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner != null)
            scanner.startScan(leScanCallback);
        Log.d(TAG, "started bt low energy scan");

        return true;
    }

    public List<BluetoothDevice> getConnected() {
        List<BluetoothDevice> retval = new ArrayList<>();
        synchronized (btleHandlers) {
            for (BluetoothLEHandler btleHandler : discovered)
                retval.add(btleHandler.getDevice());
        }
        return retval;
    }

    /**
     * Polls to see if the scanning has finished.
     */
    synchronized void waitForScanToFinish() {
        int i = 0;
        while (mScanning) {
            try {
                Thread.sleep(1000);
                Log.d(TAG, "waiting for scan to finish: " + ++i);

            } catch (Exception ignored) {
            }
        }
        synchronized (btleHandlers) {
            for (BluetoothLEHandler btleHandler : btleHandlers)
                btleHandler.connect();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }

    }

    /**
     * Used to issue a clean up to all associated btleHandlers.
     */
    void closeAllHandlers() {
        synchronized (btleHandlers) {
            for (BluetoothLEHandler btleHandler : btleHandlers)
                btleHandler.close();
        }
    }

    /**
     * Used to issue a clean up to all associated btleHandlers.
     */
    void dispose() {
        synchronized (btleHandlers) {
            for (BluetoothLEHandler btleHandler : btleHandlers)
                btleHandler.dispose();
            btleHandlers.clear();
        }
    }

    // Device scan callback.
    private final ScanCallback leScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG, "scan results found");
            onLeScan(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results)
                onLeScan(result);
            Log.d(TAG, "scan results found");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG, "scan results failed");
        }

        @SuppressLint({
                "MissingPermission"
        })
        private void onLeScan(final ScanResult result) {
            synchronized (btleHandlers) {
                for (BluetoothLEHandler btleHandler : btleHandlers) {
                    if (btleHandler.onScanResult(result)) {
                        discovered.add(btleHandler);

                        // if all of the handlers are in use, end early
                        if (discovered.size() == btleHandlers.size()) {
                            Log.d(TAG, "scan completed, ending early...");
                            if (t != null) {
                                t.cancel();
                                final BluetoothAdapter adapter = BluetoothAdapter
                                        .getDefaultAdapter();
                                if (adapter != null) {
                                    BluetoothLeScanner scanner = adapter
                                            .getBluetoothLeScanner();
                                    if (scanner != null) {
                                        scanner.stopScan(leScanCallback);
                                        scanner.flushPendingScanResults(
                                                leScanCallback);
                                    }
                                }
                                mScanning = false;
                            }
                        }
                        return;
                    }
                }
            }
        }
    };

}
