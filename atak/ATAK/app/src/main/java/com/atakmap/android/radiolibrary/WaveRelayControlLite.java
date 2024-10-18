/**
 * Implementation of the redirection logic for the Wave Relay Radio Line.    This is 
 * duplicated in the Wave Relay Plugin.   It is highly important that the Wave Relay
 * Plugin make sure that this code is not running when the Wave Relay plugin is loaded
 * because conflicts may occur.   It is also important to note that the wave relay 
 * plugin implementation might be more robust.
 * Date: 27 September 2019
 */

package com.atakmap.android.radiolibrary;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapView;

import com.atakmap.comms.CotServiceRemote;
import android.os.Bundle;

import com.atakmap.comms.SocketFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.NetworkUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class WaveRelayControlLite
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        CotServiceRemote.ConnectionListener {

    private Timer requestTimer;
    private static final int DEFAULT_WRGPS_PORT = 4349;
    private static final String connectString = "239.23.212.230:18999:udp";

    private static final int _DEFAULT_BUFFER_LENGTH = 1024 * 64;
    private static final int _MULTICAST_PORT = 14534;
    private static final byte[] _MULTICAST_ADDRESS = {
            (byte) 239, (byte) 24, (byte) 200, (byte) 39
    }; // "239.24.200.39";

    private final CotServiceRemote cotServiceRemote = new CotServiceRemote();

    private WRMulticastMonitor wrMulticastMonitor;

    private InetAddress _waverelay_InetAddress;
    private int _waverelay_port;

    private int listenPort;

    private final static Object lock = new Object();

    private final String TAG = "WaveRelayControlLite";

    private final SharedPreferences prefs;

    private static WaveRelayControlLite _instance;

    private WaveRelayControlLite(MapView mapView) {
        prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        boolean wrRedirect = prefs.getBoolean("waveRelayRedirect", false);
        if (wrRedirect) {
            Log.d(TAG, "redirect enabled");
            start();
        } else {
            Log.d(TAG, "redirect disabled");
        }
    }

    public synchronized static WaveRelayControlLite getInstance(
            final MapView view) {
        if (_instance == null)
            _instance = new WaveRelayControlLite(view);
        return _instance;

    }

    public void dispose() {
        boolean wrRedirect = prefs.getBoolean("waveRelayRedirect", false);
        if (wrRedirect) {
            stop();
        }
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("waveRelayRedirect")) {
            boolean wrRedirect = sharedPreferences.getBoolean(key, false);
            if (wrRedirect)
                start();
            else
                stop();
        }
    }

    private void addInput() {
        cotServiceRemote.connect(this);
        Bundle data = new Bundle();
        data.putString("connectString", connectString);
        data.putBoolean("enabled", true);
        data.putBoolean("temporary", true);
        data.putString("description", "Wave Relay SA Multicast");
        cotServiceRemote.addInput(connectString, data);
    }

    private void removeInput() {
        cotServiceRemote.removeInput(connectString);
        cotServiceRemote.disconnect();
    }

    @Override
    public void onCotServiceConnected(Bundle fullServiceState) {
    }

    @Override
    public void onCotServiceDisconnected() {
    }

    public synchronized void stop() {
        if (wrMulticastMonitor != null) {
            wrMulticastMonitor.cancel();
            wrMulticastMonitor = null;
        }

        if (requestTimer != null) {
            requestTimer.cancel();
            requestTimer = null;
        }

        _waverelay_InetAddress = null;
        removeInput();
    }

    public synchronized void start() {

        stop();

        boolean wrRedirect = prefs.getBoolean("waveRelayRedirect", false);

        if (!wrRedirect)
            return;

        try {
            listenPort = Integer.parseInt(prefs.getString(
                    "listenPort", DEFAULT_WRGPS_PORT
                            + ""));
        } catch (NumberFormatException e) {
            listenPort = DEFAULT_WRGPS_PORT;
        }

        int requestRate;
        try {
            requestRate = Integer.parseInt(prefs.getString("requestDelay",
                    "5000"));
        } catch (NumberFormatException e) {
            requestRate = 5000;
        }

        wrMulticastMonitor = new WRMulticastMonitor();
        wrMulticastMonitor.start();

        addInput();

        requestTimer = new Timer("RequestRedirect");
        requestTimer.schedule(new TimerTask() {
            @Override
            public void run() {

                // This device can be known by many different IP addresses - this will not
                // work properly on an EUD with more than one IP address.
                final String address = NetworkUtils.getIP();
                if (address == null || address.equals("0.0.0.0")) {
                    Log.d(TAG, "address not set or address not valid");
                    return;
                }
                final String xmlMsg = "<?xml version=\"1.0\" standalone=\"yes\"?><set_cot_dest ip='"
                        + address + "' port='" + listenPort + "' />";
                Log.d(TAG, "sending wr request for: " + address + ":"
                        + listenPort);

                try {
                    synchronized (lock) {
                        if (_waverelay_InetAddress != null) {
                            DatagramSocket redirectSendSocket = new DatagramSocket();
                            DatagramPacket datagramPacket = new DatagramPacket(
                                    xmlMsg.getBytes(),
                                    xmlMsg.length(),
                                    _waverelay_InetAddress,
                                    _waverelay_port);
                            redirectSendSocket.send(datagramPacket);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error occurred with redirect (error sending): "
                            + e);
                }

            }
        }, 0, requestRate);
    }

    private class WRMulticastMonitor extends Thread {
        boolean cancelled = false;
        MulticastSocket ms;

        public void cancel() {
            cancelled = true;
            try {
                ms.close();
            } catch (Exception ignored) {
            }
            interrupt();

        }

        @Override
        public void run() {
            while (!cancelled) {
                runImpl();
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
            }
        }

        private void runImpl() {
            try {
                ms = SocketFactory.getSocketFactory().createMulticastSocket(
                        _MULTICAST_PORT);
                if (ms == null) {
                    Log.e(TAG, "multicast socket creation failed");
                    return;
                }

                ms.joinGroup(InetAddress.getByAddress(_MULTICAST_ADDRESS));
                ms.setSoTimeout(10000);

                DatagramPacket pack = new DatagramPacket(
                        new byte[_DEFAULT_BUFFER_LENGTH],
                        _DEFAULT_BUFFER_LENGTH);

                while (!cancelled) {
                    pack.setLength(pack.getData().length);
                    ms.receive(pack);
                    // NOTE:
                    // check to make sure that the message length is actually greater than the
                    // data expected in the below loop. This would never error out because the
                    // construction of the ByteBuffer would always be the length of the entire
                    // packet.

                    // TODO: If someone has access to an MPU 4 and can restructure
                    // the below loop, we can wrap the pack.getData() outside of the loop,
                    // setPosition to 0 and limit to pack.getLength() before an attempt is
                    // made to process the data.

                    if (pack.getLength() > 9) {
                        byte[] InetBytes = new byte[4];
                        synchronized (lock) {
                            final byte[] packData = pack.getData();
                            final ByteBuffer bb = ByteBuffer.wrap(packData,
                                    0, packData.length);
                            bb.get(); // byte: TLV type (should be 6)
                            bb.get(); // byte: TLV size (should be 4)
                            bb.get(InetBytes); // read off 4 bytes of ip address
                            _waverelay_InetAddress = InetAddress
                                    .getByAddress(InetBytes);

                            // Port to send request to redirect COT.
                            bb.get(); // byte: TLV type (should be 7)
                            bb.get(); // byte: TLV size (should be 2)
                            _waverelay_port = bb.getShort();
                        }
                    }
                }
            } catch (SocketTimeoutException ste) {
                Log.e(TAG,
                        "error occurred with redirect (no information received): "
                                + ste);
            } catch (IOException e) {
                if (!cancelled)
                    Log.e(TAG, "error occurred with redirect (healing): " + e);
            } finally {
                if (ms != null) {
                    try {
                        ms.close();
                    } catch (Exception ignored) {
                    }
                }
            }

        }

    }

}
