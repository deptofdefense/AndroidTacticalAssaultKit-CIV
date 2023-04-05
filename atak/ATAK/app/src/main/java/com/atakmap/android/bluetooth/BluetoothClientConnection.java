
package com.atakmap.android.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;

import com.atakmap.coremap.log.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

abstract class BluetoothClientConnection extends BluetoothConnection {

    private static final String TAG = "BluetoothClientConnection";

    private final BluetoothAdapter adapter;
    protected BluetoothSocket socket = null;
    protected final String name;

    @SuppressLint({
            "MissingPermission"
    })
    BluetoothClientConnection(final BluetoothDevice device,
            final UUID uuid) {
        super(device, uuid);
        adapter = BluetoothAdapter.getDefaultAdapter();
        name = device.getName();
    }

    @Override
    protected boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    /**
     * onStart is responsible for cancelling any high bandwidth discovery and creating a secure or
     * insecure connection with the bluetooth device. First starting out with a secure connection,
     * and then upon failure, resorts to trying to connect insecurely.
     */
    @SuppressWarnings("JavaReflectionMemberAccess")
    @Override
    @SuppressLint({
            "MissingPermission"
    })
    protected void onStart(final BluetoothDevice device, final UUID uuid)
            throws IOException {
        BluetoothSocket s = null;
        adapter.cancelDiscovery();

        if (socket != null) {
            try {
                Log.d(TAG, "Closing previous socket: " + socket);
                socket.close();
            } catch (IOException ioe) {
                Log.w(TAG, "socket close exception", ioe);
            }
            socket = null;
        }

        if (!isRunning())
            return;

        try {
            ParcelUuid[] uuids = device.getUuids();
            if (uuids != null && uuids.length > 0) {
                final UUID supplied_uuid = device.getUuids()[0].getUuid();
                Log.d(TAG,
                        "trying to bond securely with the device (using provided UUID): "
                                + device
                                + " and the uuid: " + supplied_uuid);
                s = device
                        .createInsecureRfcommSocketToServiceRecord(
                                supplied_uuid);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }

                s.connect();
                if (!s.isConnected())
                    throw new IOException(
                            "Connect completed, but not connected");

                socket = s;
                return;
            }
        } catch (NullPointerException | IOException ioe) {
            Log.w(TAG,
                    "error encountered trying to bond (securely with provided UUUID)",
                    ioe);
        }

        try {
            if (s != null)
                s.close();
        } catch (IOException ioe) {
            Log.w(TAG, "Error closing insecure socket", ioe);
        }

        s = null;
        if (!isRunning())
            return;

        Log.d(TAG, "trying to bond using reflection: " + device
                + " and the uuid: " + uuid);
        Method m;
        try {
            m = device.getClass().getMethod("createRfcommSocket", int.class);
            s = (BluetoothSocket) m.invoke(device, 1);
            if (s == null)
                throw new IOException("could not create a socket");
            s.connect();
            if (!s.isConnected())
                throw new IOException("Connect completed, but not connected");

            socket = s;
            return;
        } catch (SecurityException | NoSuchMethodException
                | IllegalArgumentException | IllegalAccessException
                | InvocationTargetException e) {
            Log.e(TAG, "wrap() failed", e);
        } catch (IOException ioe) {
            Log.w(TAG, "error encountered trying to bond (reflection)", ioe);
        }

        //reset
        try {
            if (s != null)
                s.close();
        } catch (IOException ioe) {
            Log.w(TAG, "Error closing reflection socket", ioe);
        }

        s = null;
        if (!isRunning())
            return;

        Log.d(TAG, "trying to bond securely with the device: " + device
                + " and the uuid: " + uuid);
        try {
            s = device.createRfcommSocketToServiceRecord(uuid);
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }

            s.connect();
            if (!s.isConnected())
                throw new IOException("Connect completed, but not connected");

            socket = s;
            return;
        } catch (IOException ioe) {
            Log.w(TAG, "error encountered trying to bond (securely)", ioe);
        }

        //reset
        try {
            if (s != null)
                s.close();
        } catch (IOException ioe) {
            Log.w(TAG, "Error closing secure socket", ioe);
        }

        s = null;
        if (!isRunning())
            return;

        Log.d(TAG, "trying to bond insecurely with the device: " + device
                + " and the uuid: "
                + uuid);
        try {
            s = device.createInsecureRfcommSocketToServiceRecord(uuid);
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }

            s.connect();
            if (!s.isConnected())
                throw new IOException("Connect completed, but not connected");

            socket = s;
            return;
        } catch (IOException ioe) {
            Log.w(TAG, "error encountered trying to bond (insecurely)", ioe);
        }

        try {
            if (s != null)
                s.close();
        } catch (IOException ioe) {
            Log.w(TAG, "Error closing insecure socket", ioe);
        }

        socket = null;

        //failed to connect, bail out
        throw new IOException("Failed to bond to device: " + device.getName());
    }

    @Override
    protected void onStop() throws IOException {
        Log.d(TAG, "closing the bluetooth socket for: " + name);
        if (socket != null) {
            try {
                socket.close();
                Log.d(TAG, "closed the bluetooth socket for: " + name);
            } catch (IOException ioe) {
                Log.d(TAG, "error closing the socket for: " + name);
            }
            socket = null;
        }
    }
}
