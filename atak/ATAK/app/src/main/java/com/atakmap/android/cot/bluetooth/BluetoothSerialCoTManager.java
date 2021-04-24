package com.atakmap.android.cot.bluetooth;

import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.maps.MapView;

/**
 * Manages the actual Bluetooth channel and socket reading for CoT string received over LoRaWAN.
 */
public class BluetoothSerialCoTManager extends BluetoothCotManager {

    private static final String TAG = "BluetoothSerialCoTManager";

    private static final byte[] address = new byte[] {
            (byte) 239, (byte) 2, (byte) 3, (byte) 1
    };
    private static final int port = 6969;
    private static final int ttl = 1;

    public BluetoothSerialCoTManager(BluetoothSerialCoTReader reader, MapView mapView, String cotUID, String name) {
        super(reader, mapView, cotUID, name);
        reader.register(this);
    }

    public void publish(String xml) {
        publish(xml, address, port, ttl);
    }
}
