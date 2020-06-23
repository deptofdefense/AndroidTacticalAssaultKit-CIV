
package com.atakmap.android.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.atakmap.android.maps.MapView;

/**
 * Manages the connection and reading from specific supported laser range finders.
 * 
 *
 */
public abstract class BluetoothReader implements BluetoothConnection.Callback {

    protected final BluetoothConnection connection;

    public BluetoothReader(BluetoothDevice device) {
        connection = onInstantiateConnection(device);
        connection.setCallback(this);
    }

    public final BluetoothConnection getConnection() {
        return connection;
    }

    public abstract BluetoothCotManager getCotManager(MapView mapView);

    protected abstract BluetoothConnection onInstantiateConnection(
            BluetoothDevice device);
}
