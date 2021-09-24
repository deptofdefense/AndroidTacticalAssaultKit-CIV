
package com.atakmap.android.gps.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.atakmap.android.bluetooth.BluetoothASCIIClientConnection;
import com.atakmap.android.bluetooth.BluetoothConnection;
import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.bluetooth.BluetoothReader;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import gnu.nmea.Packet;
import gnu.nmea.PacketGGA;
import gnu.nmea.PacketRMC;
import gnu.nmea.PacketPTNL;
import gnu.nmea.SentenceHandler;

/**
 * Manages the connection and reading from specific supported GPS (NMEA) providers.
 * 
 * 
 */
public class BluetoothGPSNMEAReader extends BluetoothReader {

    private static final String TAG = "BluetoothGPSNMEAReader";

    private BluetoothGPSCotManager bgcm;
    private PacketGGA gga;
    private PacketRMC rmc;
    private PacketPTNL ptnl;

    public BluetoothGPSNMEAReader(BluetoothDevice device) {
        super(device);

        gga = null;
        rmc = null;
        ptnl = null;
    }

    @Override
    protected BluetoothConnection onInstantiateConnection(
            BluetoothDevice device) {
        return new BluetoothASCIIClientConnection(device,
                BluetoothConnection.MY_UUID_INSECURE);
    }

    @Override
    public void onRead(byte[] data) {

        String ascii = new String(data, FileSystemUtils.UTF8_CHARSET);
        try {
            if (FileSystemUtils.isEmpty(ascii)) {
                Log.w(TAG, "Ignoring empty NMEA sentence");
                return;
            }

            if (ascii.charAt(0) != '$') {
                Log.w(TAG, "Ignoring invalid NMEA sentence");
                return;
            }

            // parse packet
            //Log.e(TAG, "shb debug: " + ascii);

            Packet p = null;
            try {
                p = SentenceHandler.makePacket(ascii, false);
            } catch (Exception ignored) {
            }

            if (p == null) {
                Log.e(TAG, "Unable to process NMEA string: " + ascii);
                return;
            }

            if (p instanceof PacketRMC) {
                rmc = (PacketRMC) p;
            } else if (p instanceof PacketGGA) {
                gga = (PacketGGA) p;
            } else if (p instanceof PacketPTNL) {
                ptnl = (PacketPTNL) p;
            }
            if (ptnl != null) {
                if (ptnl.isActive())
                    bgcm.publish(NMEAMessageHelper.createMessage(ptnl, "BT"));
                ptnl = null;
            } else if ((rmc != null) && (gga != null)) {
                if (rmc.isActive())
                    bgcm.publish(NMEAMessageHelper
                            .createMessage(rmc, gga, "BT"));
                rmc = null;
                gga = null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Unable to process NMEA string: " + ascii, e);
        }
    }

    void register(BluetoothGPSCotManager bgcm) {
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

        return new BluetoothGPSCotManager(this, mapView,
                deviceName.replace(" ", "").trim()
                        + "." + device.getAddress(),
                deviceName);
    }
}
