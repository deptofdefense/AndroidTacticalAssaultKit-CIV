
package com.atakmap.android.radiolibrary;

import android.content.Context;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.List;

/**
 * This is only used for swapping the video implementation for the Rover line 
 * of products.   Do not use for other video 
 */
public interface RoverInterface {

    int STATUS_OK = 1;
    int STATUS_TIMEOUT = 2;
    int STATUS_ERROR = 3;
    int STATUS_STOPPED = 4;

    String NONE = "NONE";
    String ANNEX_A = "Annex A";
    String ANNEX_B = "Annex B";

    String CDL = "CDL";
    String CDL_ALT = "CDL ALTERNATIVE";
    String VNW = "VNW";
    String ANALOG = "ANALOG";
    String BECDL = "BECDL";
    String DDL = "DDL";

    String M1 = "M1";
    String M2 = "M2";
    String M3 = "M3";
    String M4 = "M4";

    /**
     * Allows for a registered receiver to get command output,
     * input, status, and preset information when changed.
     */
    interface RadioListener {

        /**
         * Outgoing sending status for the Radio.   
         * This is only useful for describing the outgoing data from the 
         * end user device to the Rover.
         * OK=1, TIMEOUT=2, ERROR=3, STOPPED=4
         */
        void onMcoStatus(int status);

        /**
         * Incoming status for the Radio. 
         * Monitors the receipt of incoming packets and when there are 
         * consecutive losses where packets are expected, then the status
         * moves from TIMEOUT to ERROR.
         * OK=1, TIMEOUT=2, ERROR=3, STOPPED=4
         */
        void onMciStatus(int status);

        /**
         * Provides the heartbeat status for the device based on the solicited
         * response (automatically done with a static period after monitoring has
         * been started.
         */
        void onReceiverStatus(Receiver r);

        /**
         * Callback when the preset list is returned after a succesful 
         * command to solicit the presets.
         */
        void onReceivePresets(List<String> presets);

        /**
         * Provides log style messages from the library.
         */
        void log(String tag, String message);

    }

    /**
     * Receiver class, provides status information (hearbeat) for the rover.
     */
    class Receiver {
        // properties
        public final int number;
        public int frequency;
        public int autoreconnect;
        public int dataRate;
        public int fec;
        public int searchMode;
        public int searchProgress;
        public int searchIterationsComplete;
        public int searchStateLock;
        public int rssi;
        // Network Framing can be: "NONE", "Annex A", "Annex B"
        public String network_framing;
        public int maxRssi = 100;
        public int linkEstablished;
        public int dataLinkUtilization;
        // WaveformCategory can be "CDL", "CDL ALTERNATIVE", "VNW", "ANALOG", "BECDL", "DDL", "NONE" 
        public String waveformCategory;
        // ModuleType can be "M1", "M2", "M3", "M4"
        public String moduleType;
        public int channel;
        public double version;
        public int build;

        public Receiver(final int number) {
            this.number = number;
        }

        public String toString() {
            return "receiver: " + number + " frequency: " + frequency + " " +
                    "autoreconnect: " + autoreconnect + " " +
                    "dataRate: " + dataRate + " " +
                    "waveformcategory: " + waveformCategory + " " +
                    "channel: " + channel + " " +
                    "moduleType: " + moduleType + " " +
                    "fec: " + fec + " " +
                    "searchMode: " + searchMode + " " +
                    "searchProgress: " + searchProgress + " " +
                    "searchIterationsComplete: " + searchIterationsComplete
                    + " " +
                    "searchStateLock: " + searchStateLock + " " +
                    "rssi: " + rssi + " " +
                    "maxRssi: " + maxRssi + " " +
                    "linkEstablished: " + linkEstablished + " " +
                    "dataLinkUtilization: " + dataLinkUtilization + " " +
                    "version: " + version + " " +
                    "build: " + build;
        }
    }

    void setRadioListener(RadioListener listener);

    void initialize(Context context);

    boolean isMonitoring();

    void startMonitoring();

    void stopMonitoring();

    void startListening(String iface);

    boolean isListening();

    void stopListening();

    void enableReceiver();

    void enableTestVideo(boolean state);

    /**
     * Tries to connect to the web interface of the rover radio to verify that it is reachable.
     * @param ni the network interface to use
     * @param address the inetAddress to try to connect to
     * @param roverIP the rover address as a backup in case the address is not used.
     * @return
     */
    boolean testConnection(final NetworkInterface ni, InetAddress address,
            String roverIP);

    /**
     * If MPEG-2 is true, then attempt to deliver the encoded video as MPEG-2.   Will fall back to 
     * H.264.
     */
    void setEncodingParam(final int bitrate,
            boolean encodingHalf, boolean mpeg2);

    /**
     * ModuleType is one of "M1", "M2", "M3", "M4"
     */
    void setChannel(String m, int channel);

    void scanFrequency(final int startFrequency,
            final int endFrequency,
            final int stepSize, String type);

    void reaquire();

    void enableCoT(boolean enable);

    void setReceiverFrequency(final int freq);

    void getPresets(int offset, int count);

    void getPresetName(int presetNum);

    void loadPreset(byte presetNum);

    boolean requiresNetworkConfiguration();

    String getRoverIP();

    void videoRequested(boolean v);

}
