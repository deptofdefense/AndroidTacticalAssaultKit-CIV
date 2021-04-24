package com.atakmap.android.cot.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.atakmap.android.bluetooth.BluetoothASCIIClientConnection;
import com.atakmap.android.bluetooth.BluetoothConnection;
import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.bluetooth.BluetoothReader;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import java.nio.charset.StandardCharsets;

/**
 * Manages the connection and reading from specific supported CoT over LoRaWAN to Bluetooth providers.
 */
public class BluetoothSerialCoTReader extends BluetoothReader {

    private static final String TAG = "BluetoothSerialCoTReader";
    private BluetoothSerialCoTManager bgcm;

    public BluetoothSerialCoTReader(BluetoothDevice device) {
        super(device);
    }

    void register(BluetoothSerialCoTManager bgcm) {
        this.bgcm = bgcm;
    }

    @Override
    public BluetoothCotManager getCotManager(MapView mapView) {
        BluetoothDevice device = connection.getDevice();
        String deviceName = device.getName();

        // ATAK-8437 BluetoothGPSNMEAReader NPE
        // I have never observed a case where the deviceName is null.
        if (deviceName == null) {
            deviceName = "error";
        }

        return new BluetoothSerialCoTManager(this, mapView,
                deviceName.replace(" ", "").trim()
                        + "." + device.getAddress(),
                deviceName);
    }

    @Override
    protected BluetoothConnection onInstantiateConnection(BluetoothDevice device) {
        return new BluetoothASCIIClientConnection(device,
                BluetoothConnection.MY_UUID_INSECURE);
    }

    @Override
    public void onRead(byte[] data) {
        String ascii = new String(data, StandardCharsets.UTF_8);
        try {
            bgcm.publish(ascii);

        } catch (Exception e) {
            Log.e(TAG, "Unable to process CoT string: " + ascii, e);
        }
    }
}
