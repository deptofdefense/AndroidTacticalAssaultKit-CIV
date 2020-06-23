
package com.atakmap.android.bluetooth;

import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Manages the actual Bluetooth channel and socket reading for LRF packets. Also contains glue code
 * for displaying icons on the map.
 * 
 * 
 */
public class BluetoothCotManager {

    private static final String TAG = "BluetoothCotManager";

    private final BluetoothReader reader;
    protected final MapView mapView;
    protected final String cotUID;
    protected final String name;
    private boolean stopCalled = false;

    private boolean running = false;

    public BluetoothCotManager(BluetoothReader reader,
            MapView mapView,
            String cotUID, String name) {
        this.cotUID = cotUID;
        this.reader = reader;
        this.mapView = mapView;
        this.name = name;
    }

    public void start() {
        if (running) {
            return;
        }

        stopCalled = false;

        reader.getConnection().start();
        running = reader.getConnection().isRunning();
        mapView.post(new Runnable() {
            @Override
            public void run() {
                if (running) {
                    Toast.makeText(mapView.getContext(),
                            "connected to " + name,
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Connected to " + cotUID);
                } else {
                    if (!stopCalled) {
                        Toast.makeText(
                                mapView.getContext(),
                                "unable to connect to "
                                        + name
                                        + "\ncheck that the device is on and available",
                                Toast.LENGTH_LONG).show();
                    }
                    Log.w(TAG, "Unable to connect to " + cotUID
                            + ". Is the device on and available?");
                }
            }
        });
    }

    public void stop() {
        stopCalled = true;
        if (!running) {
            return;
        }
        reader.getConnection().stop();
    }

    /**
     * Publish the message to a given address and port.
     * 
     * @param p is the payload to be published
     * @param address is the address to publish the payload to
     * @param port is the port to publish the payload to
     * @param ttl 0 for local device
     */
    static public void publish(String p, byte[] address, int port, int ttl) {
        if (p.length() == 0)
            return;

        try {
            MulticastSocket socket = new MulticastSocket();
            socket.setTimeToLive(ttl);
            InetAddress inetAddr = InetAddress.getByAddress(address);

            byte[] buf = p.getBytes(FileSystemUtils.UTF8_CHARSET);
            DatagramPacket packet = new DatagramPacket(buf, buf.length,
                    inetAddr, port);

            socket.send(packet);

        } catch (Exception e) {
            Log.e(TAG, "Failed to publish packet: " + p, e);
        }
    }
}
