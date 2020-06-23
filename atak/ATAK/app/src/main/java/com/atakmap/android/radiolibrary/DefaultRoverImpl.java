
package com.atakmap.android.radiolibrary;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.comms.NetworkDeviceManager;
import com.atakmap.comms.SocketFactory;
import com.atakmap.coremap.log.Log;

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
        RoverInterface {

    public static final String TAG = "DefaultRoverImpl";

    private RadioListener listener;
    private String roverIP;

    public DefaultRoverImpl() {
    }

    @Override
    public void initialize(final Context context) {

    }

    @Override
    public boolean requiresNetworkConfiguration() {
       return true;
    }

    @Override
    public String getRoverIP() {
       return "";
    }

    @Override
    public void videoRequested(boolean v) {
    }

    @Override
    public void setRadioListener(RadioListener listener) {
    }

    @Override
    public boolean isMonitoring() {
       return false;
    }

    @Override
    public void startMonitoring() {
    }

    @Override
    public void stopMonitoring() {
    }

    @Override
    public void startListening(final String iface) {
    }

    @Override
    public boolean isListening() {
       return false;
    }

    @Override
    public void stopListening() {
    }

    @Override
    public void enableReceiver() {
    }

    @Override
    public void enableTestVideo(boolean state) {
    }

    @Override
    public boolean testConnection(final NetworkInterface ni,
            InetAddress address, String roverIP) {
       return false;
    }

    @Override
    public void setEncodingParam(final int bitrate, final boolean encodingHalf,
            final boolean mpeg2) {

    }

    @Override
    public void setChannel(String m, int channel) {
    }

    @Override
    public void scanFrequency(final int startFrequency,
            final int endFrequency,
            final int stepSize, String type) {
    }

    @Override
    public void reaquire() {
    }

    @Override
    public void enableCoT(final boolean enable) {
    }

    @Override
    public void setReceiverFrequency(final int freq) {
    }

    @Override
    public void getPresets(int offset, int count) {
    }

    @Override
    public void getPresetName(int presetNum) {
    }

    @Override
    public void loadPreset(byte presetNum) {
    }

}
