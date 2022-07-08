
package com.atakmap.android.radiolibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.comms.NetworkManagerLite;
import com.atakmap.comms.SocketFactory;
import com.atakmap.coremap.log.Log;
import com.isrv.radio.KLV_L3;
import com.isrv.radio.MessageManager;
import com.isrv.radio.Radio;
import com.isrv.radio.Radio.RadioListener;
import com.isrv.radio.Sir;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.List;

public class DefaultRoverImpl implements
        RoverInterface, RadioListener, com.isrv.radio.SocketFactory {

    public static final String TAG = "DefaultRoverImpl";

    private Radio radio;
    private RadioListener listener;
    private String roverIP;

    /**
     * Implementation specific to the message manager, not to be confused with the
     * SocketFactory in ATAK.
     */

    @Override
    public MulticastSocket createMulticastSocket() throws IOException {
        return SocketFactory
                .getSocketFactory()
                .createMulticastSocket();
    }

    @Override
    public MulticastSocket createMulticastSocket(final int port)
            throws IOException {
        return SocketFactory
                .getSocketFactory()
                .createMulticastSocket(port);
    }

    @Override
    public DatagramSocket createDatagramSocket() throws IOException {
        return SocketFactory
                .getSocketFactory()
                .createDatagramSocket();
    }

    public DefaultRoverImpl() {
    }

    @Override
    public void initialize(final Context context) {

        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);

        MessageManager.setMulticastFactory(this);

        roverIP = sharedPreferences.getString("rover_ip_address",
                "192.168.80.1");

        if (sharedPreferences.getBoolean("rover_legacy", false)) {
            Log.d(TAG,
                    "creating a legacy communication connection (19024/19025) for:"
                            + roverIP);
            radio = new Sir("Default", roverIP, "230.77.68.76", 19024,
                    "230.77.68.76", 19025);
        } else {
            Log.d(TAG,
                    "creating a modern communication connection (6789/19025) for: "
                            + roverIP);
            radio = new Sir("Default", roverIP, roverIP, 6789,
                    "230.77.68.76", 19025);
        }

        radio.setRadioListener(this);
    }

    @Override
    public boolean requiresNetworkConfiguration() {
        return true;
    }

    @Override
    public String getRoverIP() {
        return roverIP;
    }

    @Override
    public void videoRequested(boolean v) {
    }

    @Override
    public void onMcoStatus(final MessageManager.Status status) {
        if (listener != null)
            listener.onMcoStatus(status.getValue());

    }

    @Override
    public void onMciStatus(final MessageManager.Status status) {
        if (listener != null)
            listener.onMciStatus(status.getValue());
    }

    @Override
    public void onReceiverStatus(final Radio.Receiver r) {
        if (listener != null) {
            Receiver retVal = new Receiver(r.number);
            retVal.frequency = r.frequency;
            retVal.autoreconnect = r.autoreconnect;
            retVal.dataRate = r.dataRate;
            retVal.fec = r.fec;
            retVal.searchMode = r.searchMode;
            retVal.searchProgress = r.searchProgress;
            retVal.searchIterationsComplete = r.searchIterationsComplete;
            retVal.searchStateLock = r.searchStateLock;
            retVal.rssi = r.rssi;
            retVal.build = r.build;
            retVal.version = r.version;

            if (r.network_framing != null)
                retVal.network_framing = r.network_framing.toString();
            else
                retVal.network_framing = RoverInterface.NONE;

            retVal.maxRssi = r.maxRssi;
            retVal.linkEstablished = r.linkEstablished;
            retVal.dataLinkUtilization = r.dataLinkUtilization;

            if (r.waveformCategory != null)
                retVal.waveformCategory = r.waveformCategory.toString();
            else
                retVal.waveformCategory = RoverInterface.NONE;

            if (r.moduleType != null)
                retVal.moduleType = r.moduleType.toString();
            else
                retVal.moduleType = null;

            retVal.channel = r.channel;
            listener.onReceiverStatus(retVal);
        }
    }

    @Override
    public void onReceivePresets(List<String> presets) {
        if (listener != null) {
            listener.onReceivePresets(presets);
        }
    }

    @Override
    public void log(String tag, String message) {
        if (listener != null) {
            listener.log(tag, message);
        }
    }

    @Override
    public void setRadioListener(RadioListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean isMonitoring() {
        return radio != null && radio.isMonitoring();
    }

    @Override
    public void startMonitoring() {
        if (radio == null)
            return;
        radio.startMonitoring();
    }

    @Override
    public void stopMonitoring() {
        if (radio == null)
            return;
        radio.stopMonitoring();
    }

    @Override
    public void startListening(final String iface) {
        if (radio == null)
            return;
        radio.startListening(iface);
    }

    @Override
    public boolean isListening() {
        return radio != null && radio.isListening();
    }

    @Override
    public void stopListening() {
        if (radio == null)
            return;
        radio.stopListening();
    }

    @Override
    public void enableReceiver() {
        if (radio == null)
            return;
        radio.enableReceiver();
    }

    @Override
    public void enableTestVideo(boolean state) {
        if (radio == null)
            return;
        radio.enableTestVideo(state);
    }

    @Override
    public boolean testConnection(final NetworkInterface ni,
            InetAddress address, String roverIP) {

        if (address == null)
            return false;

        Socket s = null;
        BufferedReader inFromClient = null;
        try {

            s = new Socket();
            Log.d(TAG,
                    "done constructing the socket, setting the timeout");
            s.setSoTimeout(3000);
            if (ni != null) {
                Log.d(TAG,
                        "constructing the socket with the interface");
                final String ethip = NetworkManagerLite
                        .getAddress(ni);
                if (ethip != null) {
                    s.bind(new InetSocketAddress(ethip, 0));
                } else {
                    Log.d(TAG,
                            "constructing the socket without the interface");
                }
            } else {
                Log.d(TAG,
                        "constructing the socket without the interface");
            }

            s.connect(
                    new InetSocketAddress(address, 80),
                    3000);

            Log.d(TAG, "causing the read");
            Log.d(TAG, "success: contacted the web interface");
            return true;
        } catch (IOException ioe) {
            Log.d(TAG,
                    "failed: unable to contact the web interface");
            return false;
        } finally {
            if (inFromClient != null)
                try {
                    inFromClient.close();
                } catch (IOException ignored) {
                }
            if (s != null)
                try {
                    s.close();
                } catch (IOException ignored) {
                }
        }
    }

    @Override
    public void setEncodingParam(final int bitrate, final boolean encodingHalf,
            final boolean mpeg2) {
        if (radio == null)
            return;

        if (encodingHalf) {
            radio.setEncodingParam(bitrate, KLV_L3.FrameSize.HALF,
                    (mpeg2) ? KLV_L3.VideoEncoder.MPEG2
                            : KLV_L3.VideoEncoder.H264);
        } else {
            radio.setEncodingParam(bitrate, KLV_L3.FrameSize.FULL,
                    (mpeg2) ? KLV_L3.VideoEncoder.MPEG2
                            : KLV_L3.VideoEncoder.H264);
        }
    }

    /**
     * ModuleType is one of "M1", "M2", "M3", "M4"
     */
    @Override
    public void setChannel(String m, int channel) {
        if (radio == null)
            return;
        radio.setChannel(KLV_L3.ModuleType.find(m), channel);
    }

    @Override
    public void scanFrequency(final int startFrequency,
            final int endFrequency,
            final int stepSize, String type) {
        if (radio == null)
            return;
        radio.scanFrequency(startFrequency, endFrequency, stepSize,
                KLV_L3.WaveformType.find(type));
    }

    @Override
    public void reaquire() {
        if (radio == null)
            return;
        radio.reaquire();
    }

    @Override
    public void enableCoT(final boolean enable) {
        if (radio == null)
            return;
        radio.enableCoT(enable);
    }

    @Override
    public void setReceiverFrequency(final int freq) {
        if (radio == null)
            return;
        radio.setReceiverFrequency(freq);
    }

    @Override
    public void getPresets(int offset, int count) {
        if (radio == null)
            return;
        radio.getPresets(offset, count);
    }

    @Override
    public void getPresetName(int presetNum) {
        if (radio == null)
            return;
        radio.getPresetName(presetNum);
    }

    @Override
    public void loadPreset(byte presetNum) {
        if (radio == null)
            return;
        radio.loadPreset(presetNum);
    }

}
