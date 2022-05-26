
package com.atakmap.comms;

import android.os.Build;
import android.os.Environment;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.annotations.DeprecatedApi;

/**
 * Contains a network device mapping that is created through an external Network learning
 * application and read when ATAK is started up. The format of the file is CSV and describes valid
 * network configurations and the services they provide. A major hurdle when starting ATAK with
 * multiple network resources is that there is really no way to tell which network resource is used
 * for CoT traffic and which one might provide a specialized service such as video. When these
 * devices are plugged in and before they are configured, there is no way to tell what they are
 * (eth0 vs eth1). Without knowing anything else, the video receiver might end up being the device
 * that cursor on target messages are sent over. And the ISRV tool might attempt to talk with a
 * PRC-152 incorrectly. The CSV file maps mac addresses to required configurations and capabilities.
 * This makes use of the CSV file in the format:
 * static -
 * macaddress,label,type,Configration.STATIC.getValue(),address,netmask,gateway
 * dhcp -
 * macaddress,label,type,Configration.DHCP.getValue()
 * none -
 * macaddress,label,type,Configuration.NONE.getValue()
 * <p>
 * or # at the begining indicates comment.
 * <p>
 * where type is one of Type.*.getValue() and if multiple types exist they are separated by a
 * semicolon ;
 * <p>
 * Additionally, if a gateway is set, this will become the default route for the system.   Currently,
 * the network configuration is not advanced enough to deal with static routing tables.
 * The short term use case assumes at most one routable network and several non-routable (flat) networks.
 * <p>
 * <p>
 * The NetworkDeviceManager can be used in an enabled or disabled state.   In the case that the NetworkDeviceManager
 * is enabled, the network.map file will be loaded and utilized to describe how to use the network interfaces.
 * @deprecated as of Android 11 the mac address can no longer be used.   Please see NetworkManagerLite
 * for code that is still used.
 */
@Deprecated
@DeprecatedApi(since = "4.5", removeAt = "4.8", forRemoval = true)
public class NetworkDeviceManager {

    public static final String TAG = "NetworkDeviceManager";

    public static final String CONFIG_ALTERED = "com.partech.networkmonitor.CONFIG_ALTERED";

    /**
     * Contains a mapping of mac addresses to network devices from the network.map file. if this is
     * empty, ATAK should assume all devices are configured externally and no network deconfliction
     * is needed.  Changes to this should be synchronized.
     */
    static private final List<NetworkDevice> devices = new ArrayList<>();

    /**
     * The location used for the network.map file.
     */
    static private File directory = null;

    /**
     * Context for use when on the S6
     */
    static private Context context;

    private static boolean filtersDropped = false;

    /**
     * The network device is described by its macaddress, label, type and the preferred network configuration,
     */
    static public class NetworkDevice {

        public enum Configuration {
            NONE(0, "none"),
            DHCP(1, "dhcp"),
            STATIC(2, "static");

            private final int value;
            private final String name;

            Configuration(final int value, final String name) {
                this.value = value;
                this.name = name;
            }

            public String getName() {
                return name;
            }

            public int getValue() {
                return value;
            }

            static public Configuration findFromValue(int value) {
                for (Configuration c : Configuration.values())
                    if (c.value == value)
                        return c;
                return null;
            }

            static public String getStringList() {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                for (Configuration t : Configuration.values())
                    sb.append(t.getName()).append(" value=")
                            .append(t.getValue()).append(",");
                sb.append("}");
                return sb.toString().replace(",}", "}");
            }
        }

        /**
         * Type of network devices, used within the system to allow for radio or network specific
         * traffic to be designated on a network by network case.
         */
        public enum Type {

            COT_INPUT(0, "cot_input", "CoT Input"),
            COT_OUTPUT(1, "cot_output", "CoT Output"),
            COT_BOTH(3, "cot_both", "CoT Bidirectional"),
            STREAMING_COT_BOTH(4,
                    "streamin_cot_both",
                    "CoT Bidirectional (Streaming)"),

            MPU_METADATA(20, "mpu_metadata", "MPU Metadata GPS/Health Status"),

            VMF_INPUT(30, "vmf_input", "VMF Input"),
            VMF_OUTPUT(31, "vmf_output", "VMF Output"),
            VMF_BOTH(32, "vmf_both", "VMF Bidirectional"),

            UNKNOWN(99, "unknown", "Generic Network Device"),
            PASSIVE(100, "passive_traffic", "Passive Traffic Only");

            private final int value;
            private final String name;
            private final String description;

            Type(final int value, final String name,
                    final String description) {
                this.value = value;
                this.name = name;
                this.description = description;
            }

            public String getName() {
                return name;
            }

            public int getValue() {
                return value;
            }

            public String getDescription() {
                return description;
            }

            public String toString() {
                return description;
            }

            static public Type findFromValue(int value) {
                for (Type t : Type.values())
                    if (t.value == value)
                        return t;
                return UNKNOWN;
            }

            static public String getStringList() {
                StringBuilder sb = new StringBuilder();
                sb.append("{");
                for (Type t : Type.values())
                    sb.append(t.getName()).append(" value=")
                            .append(t.getValue()).append(",");
                sb.append("}");
                return sb.toString().replace(",}", "}");
            }
        }

        public final String macaddr;
        public final String label;
        public final Configuration configuration;

        public final String prefAddress;
        public final String prefNetmask;
        public final String prefGateway;
        public final boolean prefCreateDefaultRoute;
        public final Type[] type;

        /**
         * Get the network interface of the device described in the network map file. The configuration
         * of the actual interface should only be obtained from the NetworkInterface and not from the
         * preferred values.
         *
         * @return the interface that describes the specific interface as defined by the network map.
         * The network interface may have a name such as eth0, eth1, eth2, wlan0, ppp0, rmnet0,
         * etc to use, or null if the interface is not present.  The interface should be examined
         * for the actual configuration of the device.
         */
        public NetworkInterface getInterface() {
            return NetworkDeviceManager.getInterface(macaddr);
        }

        /**
         * In a very narrow instance the network interface provided will fail
         * during a joinGroup().   In this case it is MANDATORY that you request
         * a clean interface.
         */
        public NetworkInterface getCleanInterface() {
            return NetworkDeviceManager.getInterface(macaddr, true);
        }

        /**
         * Test to see if the type is supported.
         *
         * @param t the type
         * @return true if the type is supported, false if not.
         */
        public boolean isSupported(final Type t) {
            for (Type aType : type) {
                if (aType == t)
                    return true;
            }
            return false;
        }

        /**
         * Construct a DHCP configured or UNCONFIGURABLE network device, address, netmask, and gateway of the
         * constructed object will be null.   These are desired values and not the values of the actual configured device.
         * If the configuration is set to static, no configuration will be performed.
         *
         * @param macaddr       is in the format XX:XX:XX:XX:XX:XX
         * @param label         freetext not including commas
         * @param type
         * @param configuration
         */
        public NetworkDevice(final String macaddr,
                final String label,
                final Type[] type,
                final Configuration configuration) {
            this(macaddr, label, type, configuration, null, null, null, false);
        }

        /**
         * Construct a STATIC configured network device.   These are desired values and
         * not the values of the actual configured device.  To view the actual values, use getInterface
         * and examine the actual values in that structure.
         *
         * @param macaddr        is in the format XX:XX:XX:XX:XX:XX
         * @param label          freetext not including commas, if commas are contained in the label they are
         *                       replaced with periods.
         * @param type           an array of all of the types of traffic that can be stupported by this network device.
         * @param configuration
         * @param prefAddress    is a valid preferred IPv4 address, null implies DHCP (netmask and gateway will be
         *                       ignored)
         * @param prefNetmask    is a valid preferred netmask address, null implies DHCP
         * @param prefGateway    is a valid preferred IPv4 address, null implies DHCP
         * @param prefCreateDefaultRoute is whether to create a default route for this network device
         */
        public NetworkDevice(final String macaddr,
                final String label,
                final Type[] type,
                final Configuration configuration,
                final String prefAddress,
                final String prefNetmask,
                final String prefGateway,
                final boolean prefCreateDefaultRoute) {
            this.macaddr = sanitize(macaddr, true);

            this.label = label.replace(',', '.');
            this.type = type;
            this.configuration = configuration;

            this.prefAddress = sanitize(prefAddress, false);
            this.prefNetmask = sanitize(prefNetmask, false);

            this.prefCreateDefaultRoute = prefCreateDefaultRoute;

            if (prefGateway == null)
                this.prefGateway = "";
            else
                this.prefGateway = sanitize(prefGateway, false);

        }

        /**
         * Sanitize user input based on an input string and a boolean indicating if it is
         * a machine address or not.
         * @param s the string to sanitize
         * @param macAddr if true, the string can contain A-F, a-f, numbers and ':',
         * if false the string can contain only numbers and '.'
         */
        private static String sanitize(final String s, boolean macAddr) {
            if (s == null)
                return s;

            if (macAddr)
                return s.trim().replaceAll("[^A-Fa-f0-9:]", "");
            else
                return s.trim().replaceAll("[^0-9.]", "");
        }

        /**
         * Given a string of numbers delineated by semicolons, construct a Type[]
         **/
        static private Type[] fromTypeString(String v) {
            String[] vlist = v.split(";");
            Type[] arr = new Type[vlist.length];
            for (int i = 0; i < arr.length; ++i) {
                try {
                    arr[i] = Type.findFromValue(Integer.parseInt(vlist[i]));
                } catch (NumberFormatException nfe) {
                    arr[i] = Type.UNKNOWN;
                }
            }
            return arr;
        }

        /**
         * Given a Type[] construct a type string delineated by semicolons.
         * in the form of type.value();type.value();type.value()
         **/
        static private String toTypeString(Type[] arr) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length; ++i) {
                if (i > 0)
                    sb.append(";");
                sb.append(arr[i].getValue());
            }
            return sb.toString();
        }

        /**
         * Provides a human readable string representation of the network device.
         */
        public String toString() {
            String name = "missing";

            NetworkInterface iface = getInterface();
            if (iface != null) {
                name = iface.getName();
            }

            if (configuration == Configuration.STATIC) {
                return macaddr + "," + label + "," + toTypeString(type) + ","
                        + prefAddress + "," + prefNetmask + ","
                        + prefGateway + "," + name;
            } else if (configuration == Configuration.DHCP) {
                return macaddr + "," + label + "," + toTypeString(type) + ","
                        + "dhcp, " + name;
            } else {
                return macaddr + "," + label + "," + toTypeString(type) + ","
                        + "none, " + name;
            }
        }

        /**
         * Provides a CSV representation of the network device.
         */
        private String toCSVLine() {
            if (configuration == Configuration.STATIC) {
                return macaddr + "," + label + "," + toTypeString(type) + ","
                        + configuration.getValue() + "," +
                        prefAddress + "," + prefNetmask + "," + prefGateway +
                        "," + prefCreateDefaultRoute;
            } else {
                return macaddr + "," + label + "," + toTypeString(type) + ","
                        + configuration.getValue();
            }
        }

        /**
         * Produces an instance of a NetworkDevice from a CSV formatted line.
         *
         * @return null if the line is malformed or a comment.
         */
        static NetworkDevice fromCSVLine(String line) {
            try {

                String[] slist = line.split(",");
                if (line.startsWith("#")) {
                    // comment
                    Log.d(TAG,
                            "comment discovered in network mapping: " + line);
                    return null;
                } else if (slist.length == 6) {
                    return new NetworkDevice(
                            slist[0].toUpperCase(Locale.US), // mac
                            slist[1], // label
                            fromTypeString(slist[2]), // Type
                            NetworkDevice.Configuration.findFromValue(Integer
                                    .parseInt(slist[3])), // Configuration
                            slist[4], // address
                            slist[5], // netmask
                            null,
                            false);
                } else if (slist.length == 7) {
                    // static address
                    return new NetworkDevice(
                            slist[0].toUpperCase(Locale.US), // mac
                            slist[1], // label
                            fromTypeString(slist[2]), // Type
                            NetworkDevice.Configuration.findFromValue(Integer
                                    .parseInt(slist[3])), // Configuration
                            slist[4], // address
                            slist[5], // netmask
                            slist[6], // gateway
                            true); // create default route
                } else if (slist.length == 8) {
                    // static address, create default route specified
                    return new NetworkDevice(
                            slist[0].toUpperCase(Locale.US), // mac
                            slist[1], // label
                            fromTypeString(slist[2]), // Type
                            NetworkDevice.Configuration.findFromValue(Integer
                                    .parseInt(slist[3])), // Configuration
                            slist[4], // address
                            slist[5], // netmask
                            slist[6], // gateway
                            Boolean.parseBoolean(slist[7])); // create default route
                } else if (slist.length == 4) {
                    // dhcp or unconfigured
                    return new NetworkDevice(
                            slist[0].toUpperCase(Locale.US), // mac
                            slist[1], // label
                            fromTypeString(slist[2]), // Type
                            NetworkDevice.Configuration.findFromValue(Integer
                                    .parseInt(slist[3]))); // Configuration
                } else {
                    // invalid
                    Log.d(TAG, "invalid line discovered in network mapping: "
                            + line);
                    return null;
                }
            } catch (Exception e) {
                Log.e(TAG, "unknown exception occurred, corrupted file", e);
                return null;
            }
        }
    }

    /**
     * Sets the directory for the network mapping.
     *
     * @param dir if the location does not exist, or the location is null
     *                  Environment.getExternalStorageDirectory(), "atak") is used when the NetworkDeviceManager
     *                  is enabled.
     */
    static public void setDirectory(final File dir) {
        directory = dir;
    }

    /**
     * Initializes the network mappings.    NetworkDeviceManager works when disabled, but no NetworkDevices will
     * be managed.    Attempts to call enable on an enabled manager will reread the network.map file.
     */
    static synchronized public void enable(boolean state) {

        if ((directory == null) || !IOProviderFactory.exists(directory))
            directory = new File(Environment.getExternalStorageDirectory(),
                    "atak");

        if (state) {
            Log.d(TAG,
                    "enabling/refreshing network device management for the map in: "
                            + directory);
            initialize();
        } else {
            Log.d(TAG, "disabling network device management");
            devices.clear();
        }
    }

    static public void setContext(Context c) {
        context = c;
    }

    /**
     * Loads the network map file and populates all of the Network devices configured for use with
     * this device. The map describes multi network configurations.
     *
     */
    static synchronized private void initialize() {
        devices.clear();
        try {
            File f = new File(directory, "network.map");
            if (IOProviderFactory.exists(f)) {
                try (InputStream is = IOProviderFactory.getInputStream(f);
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader br = new BufferedReader(isr)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        NetworkDevice nd = NetworkDevice.fromCSVLine(line);
                        if (nd != null)
                            devices.add(nd);
                    }
                }
            }
        } catch (IOException ioe) {
            // could not read the map file
            Log.e(TAG, "error: ", ioe);
        }

        for (NetworkDevice nd : devices) {
            Log.d(TAG, "discovered mapping: " + nd);
        }

        Collection<NetworkInterface> unmanaged = getUnmanagedInterfaces();
        for (NetworkInterface ni : unmanaged) {
            Log.d(TAG, "unmanaged device: " + getMacAddress(ni)
                    + " on interface " + ni.getName());
        }
    }

    /**
     * write the list of discovered networks to file
     *
     * @param directory should be consistent with the value of FileSystemUtils.getRoot()
     * @return - true if success
     */
    private static boolean writeList(final File directory) {
        if (devices == null)
            return false;
        try {
            // create the directory if needed
            if (!IOProviderFactory.exists(directory))
                if (!IOProviderFactory.mkdir(directory)) {
                    Log.e(TAG, "Failed to make dir at " + directory.getPath());
                }

            File f = new File(directory, "network.map");
            try (FileWriter bw = IOProviderFactory.getFileWriter(f)) {
                bw.write("#network map file created " + new Date() + "\r\n");
                bw.write("#type values  " + NetworkDevice.Type.getStringList()
                        + "\r\n");
                bw.write("#config values  "
                        + NetworkDevice.Configuration.getStringList() + "\r\n");
                for (NetworkDevice n : devices) {
                    bw.write(n.toCSVLine() + "\r\n");
                }
            }
        } catch (IOException ie) {
            return false;
        }
        return true;
    }

    /**
     * remove a device with the given mac address from the device list
     *
     * @param macaddr    the mac of the device to remove in the format XX:XX:XX:XX:XX:XX
     * @param directory should be consistent with FileSystemUtils.getRoot()
     */
    public static synchronized boolean removeNetworkDevice(final String macaddr,
            final File directory) {
        for (NetworkDevice n : devices) {
            if (n.macaddr.equalsIgnoreCase(macaddr)) {
                if (n.configuration != NetworkDevice.Configuration.NONE) {
                    unconfigure(n);
                }
                devices.remove(n);
                return writeList(directory);
            }
        }
        return false;
    }

    /**
     * add a discovered networkDevice to the list
     *
     * @param nd        the NetworkDevice to be added
     * @param directory should be consistent with FileSystemUtils.getRoot()
     * @return true if success
     */
    public static synchronized boolean addNetworkDevice(NetworkDevice nd,
            File directory) {
        if (nd != null) {
            NetworkDevice exists = null;
            for (NetworkDevice n : devices) {
                if (n.macaddr.equalsIgnoreCase(nd.macaddr)) {
                    exists = n;
                    break;
                }
            }
            if (exists != null) {
                devices.remove(exists);
            }
            devices.add(nd);
            return writeList(directory);
        }
        return false;
    }

    /**
     * Returns an unmodifiable list of network devices read from the network.map file. If this list
     * is empty, it is assumed that whatever networks ATAK needs are already configured and no
     * multinic netowork issues are expected. (Multicast pub/sub is subjected to the default route
     * of the system).
     *
     * @return all of the network devices that are in the network.map or empty if no devices are
     * defined. Only interfaces that are present will have a valid interface name defined
     * within the NetworkDevice iface field.   If the NetworkManager has not been enabled
     * is list will be empty.
     */
    public static synchronized List<NetworkDevice> getNetworkDevices() {
        return Collections.unmodifiableList(new ArrayList<>(devices));
    }

    /**
     * Returns a list of unmanaged network devices constructed by scanning the list of managed
     * network devices and the list of NetworkInterfaces from Android and returns the difference.
     * This method is selective on what it will consider an unmanaged interface { eth }, managed
     * interfaces are those that are externally managed by Android (wlan0, ppp0, rmnet0, csctun0,
     * tun0).
     */
    public static synchronized Collection<NetworkInterface> getUnmanagedInterfaces() {

        Map<String, NetworkInterface> map = new HashMap<>();
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface
                    .getNetworkInterfaces();
            if (nets != null) {
                for (NetworkInterface netint : Collections.list(nets)) {
                    String name = netint.getName();
                    if (name != null
                            && (name.startsWith("eth") ||
                                    name.startsWith("nw") ||
                                    name.startsWith("usb") ||
                                    name.startsWith("rndis") ||
                                    name.startsWith("tun") ||
                                    name.startsWith("csctun") ||
                                    name.startsWith("rmnet") ||
                                    name.startsWith("ppp"))) {

                        String macaddr = getMacAddress(netint);
                        if (macaddr != null)
                            map.put(macaddr, netint);
                    }
                }
            }
            for (int i = 0; i < devices.size(); ++i) {
                map.remove(devices.get(i).macaddr);
            }
        } catch (SocketException se) {
            Log.e(TAG, "error: ", se);
        }
        return map.values();
    }

    /**
     * Finds a network device in the network map specified by the macaddres.
     *
     * @param macaddr is in the format XX:XX:XX:XX:XX:XX
     * @return the NetworkDevice matching the specified mac address or null if no device is described.
     */
    public static synchronized NetworkDevice getDevice(final String macaddr) {
        for (NetworkDevice nd : devices) {
            if (nd.macaddr.equalsIgnoreCase(macaddr)) {
                return nd;
            }
        }
        return null;
    }

    /**
     * Find the preferred IPv4 address for a interface described by the macaddres.
     *
     * @param macaddr is in the format XX:XX:XX:XX:XX:XX
     * @return the stringified IPv4 interface otherwise, null if the mac address does not
     * resolve to a valid IPv4 interface.
     * @deprecated
     */
    @Deprecated
    public static String getIPv4Address(final String macaddr) {
        if (macaddr != null) {
            NetworkDevice nd = NetworkDeviceManager.getDevice(macaddr);
            if (nd != null && (nd.getInterface() != null)) {
                Enumeration<InetAddress> inetAddresses = nd.getInterface()
                        .getInetAddresses();
                for (InetAddress inetAddress : Collections
                        .list(inetAddresses)) {
                    if (inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Given a mac address, get the ip address for any unmanaged interfaces
     */
    public static String getUnmanagedIPv4Address(final String macaddr) {
        if (macaddr != null) {
            Collection<NetworkInterface> unmanaged = getUnmanagedInterfaces();
            for (NetworkInterface ni : unmanaged) {
                if (macaddr.equals(NetworkDeviceManager.getMacAddress(ni))) {
                    Enumeration<InetAddress> inetAddresses = ni
                            .getInetAddresses();
                    for (InetAddress inetAddress : Collections
                            .list(inetAddresses)) {
                        if (inetAddress instanceof Inet4Address) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        }
        return null;
    }

    // transient cache between mac addresses and network interface, if the network interface mac address
    // changes, the entire cache invalidated if the cache is older than 5000 miliseconds
    private static final Map<String, NetworkInterface> niCache = new HashMap<>();
    private static long lastCached = -1;
    private static final long AGE = 5000;

    /**
     * This calls getInterface with a macaddress and false so the network interface
     * will be checked to see if it exists in the cache and is up.   This will
     * allow for the return to be a dirty or cached network interface which
     * is only a problem when performing a multicast join.
     * @deprecated
     */
    @Deprecated
    static public NetworkInterface getInterface(final String macaddr) {
        return getInterface(macaddr, false);
    }

    /**
     * Get the network interface of the device described in the network map file. The configuration
     * of the actual interface should only be obtained from the NetworkInterface and not from the
     * preferred values.  The implementation of this method makes use of a cached mapping between
     * stringified mac addresses and the actual network interface.   When a request is made for a
     * mac address currently in the mac/network interface cache and the actual network interface does
     * not actually have that mac address, the entire cache is considered invalid and discarded.
     * The cache is then repopulated with all of the devices and if found, the new network interface is
     * returned.
     *
     * @param macaddr is in the format XX:XX:XX:XX:XX:XX
     * @return the interface that describes the specific interface as defined by the network map.
     * The network interface may have a name such as eth0, eth1, eth2, wlan0, ppp0, rmnet0,
     * etc to use, or null if the interface is not present.  The interface should be examined
     * for the actual configuration of the device.
     * <p>
     * <p>
     * If for any reason rhe Network Interface is believed to be bad, a call
     * *MUST* be made to request a clean interface.
     * <p>
     * Currently, we are observing a case where the NetworkInteface
     * seems perfectly fine and will work for most things, but a call to
     * joinAddress will FAIL.
     */
    static public NetworkInterface getInterface(final String macaddr,
            final boolean clean) {
        try {
            if (clean || (lastCached + AGE) < android.os.SystemClock
                    .elapsedRealtime())
                niCache.remove(macaddr);

            NetworkInterface ni = niCache.get(macaddr);
            if (ni != null && !clean) {
                final String ma = getMacAddress(ni);
                if ((ma != null) && ma.equalsIgnoreCase(macaddr)) {
                    return ni;
                }
            }

            lastCached = android.os.SystemClock.elapsedRealtime();

            // repopulate the cache because invalid data was encountered
            final Enumeration<NetworkInterface> interfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface != null) {
                    final String ma = getMacAddress(iface);
                    niCache.put(ma, iface);
                    if ((ma != null) && ma.equalsIgnoreCase(macaddr)) {
                        ni = iface;
                    }
                }
            }

            return ni;

        } catch (SocketException se) {
            Log.e(TAG, "error discovering interface given by macaddress: "
                    + macaddr);
        }
        return null;
    }

    static public boolean unconfigure(final NetworkDevice nd) {
        if (nd != null) {
            if (nd.configuration == NetworkDevice.Configuration.NONE)
                return true;

            final NetworkInterface iface = nd.getInterface(); // network device is present
            if (iface != null) {
                unconfigure(iface.getName());
            }
        }
        return true;
    }

    /**
     * Configure an interface just given the mac address. Could be written better to possibly ignore
     * interfaces that are already configured.
     */
    static public void configure(final NetworkDevice nd) {
        if (nd != null) {

            refreshFilter();

            final NetworkInterface iface = nd.getInterface(); // network device is present
            if (iface != null) {
                if (nd.configuration == NetworkDevice.Configuration.STATIC) // network device is static
                    configure(iface.getName(), nd.prefAddress, nd.prefNetmask,
                            nd.prefGateway, nd.prefCreateDefaultRoute, "");
                else if (nd.configuration == NetworkDevice.Configuration.DHCP) { // network device is static
                    // network device is dynamic
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            configure(iface.getName());
                        }
                    };
                    Thread t = new Thread(r, "config-" + iface.getName());
                    t.start();
                } else
                    Log.d(TAG, "no config: " + nd);
            }
        }
    }

    private static final Map<ByteBuffer, String> macMap = new HashMap<>();

    /**
     * Creates a stringified mac address in pseudo human readable form. This is usually printed on
     * the device.
     */
    static public String byteToMac(final byte[] mac) {
        if (mac == null || mac.length != 6)
            return "";

        ByteBuffer key = ByteBuffer.wrap(mac);

        String retval = macMap.get(key);
        if (retval == null) {
            // String.format is pretty inefficient.
            retval = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                    mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
            macMap.put(key, retval);
        }
        return retval;
    }

    /**
     * Given a NetworkInterface, return the MAC address in the XX:XX:XX:XX:XX:XX format
     * if no HardwareAddress is available, use a synthetically generated address based on the
     * device name.
     * otherwise returns null.
     */
    static public String getMacAddress(final NetworkInterface ni) {
        try {
            final byte[] macBytes = ni.getHardwareAddress();
            if (macBytes != null) {
                return byteToMac(macBytes);
            } else {
                //Log.e(TAG, "mac address is null for: " + ni);
                final String name = "000000" + ni.getName(); // pad it out with 0's
                //Log.d(TAG, "synthetic mac addr: " + ni.getName() + " = " + name.substring(name.length() - 6));
                return byteToMac(name.substring(name.length() - 6).getBytes());
            }

        } catch (SocketException se) {
            Log.e(TAG, "error occurred obtaining the hardware address for: "
                    + ni);
        }
        return null;
    }

    /**
     * Gets the ip address of an interface that is up, return null if interface is not up or has no
     * address.
     *
     * @param ni the interface to get the ip address of.
     */
    public static String getAddress(final NetworkInterface ni) {
        try {
            if (ni != null && ni.isUp()) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    if (!address.isLoopbackAddress()
                            && (address instanceof Inet4Address)) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return null;

    }

    static private void wait(Process proc) {

        if (proc == null) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    proc.getInputStream()));
            try {
                // intentional - used to consume the rest of the lines in the input stream.
                //noinspection StatementWithEmptyBody
                while ((reader.readLine()) != null) {
                }
            } catch (IOException ignored) {
            }
            proc.waitFor();
        } catch (InterruptedException e) {
            Log.e(TAG, "error: ", e);
        }
    }

    /**
     * This method manipulates the filters for all of the network activity on
     * Android 5 / 6.   Flushing the tables is ok - but it does break wlan0
     * routing lookup (so at the end, added back in a rule to lookup wlan0).
     * Also, flush does not seem to work after the first time - so I only execute
     * this once.
     * Need to verify this works with 3g/4g/LTE
     */
    static private void refreshFilter() {

        if (!filtersDropped) {
            filtersDropped = true;
            Log.d(TAG, "routing adjustments");

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                return;

            try {
                Runtime rt = Runtime.getRuntime();
                Process p;
                p = execSU(rt, "ip", "rule flush");
                wait(p);
                p = execSU(rt, "ip", "rule add from all table main");
                wait(p);
                p = execSU(rt, "ip", "rule add from all table default");
                wait(p);
                p = execSU(rt, "ip", "rule add from all lookup wlan0");
                wait(p);
            } catch (Exception ioe) {
                Log.e(TAG, "error: ", ioe);
            }
        }
    }

    /**
     * Wrapper for the runtime call.
     * @param rt the appropriate runtime for the private call to su
     * @param command the command { ip, setprop, dhcptool, netcfg, ndc }
     * @param args the arguments which have been validated prior to this call because they are command 
     *             dependent.
     */
    static private Process execSU(final Runtime rt, final String command,
            final String args) {

        if (command == null)
            return null;

        boolean validCommand = (command.equals("ip")
                || command.equals("setprop") ||
                command.equals("dhcptool") || command.equals("netcfg") ||
                command.equals("ndc"));
        if (!validCommand)
            return null;

        Log.d(TAG, "executing command: su -c " + command + " " + args);

        // For use with the Android S6 com.atakmap.Samservices
        Intent i = new Intent("com.atakmap.exec");
        i.putExtra("command", command);
        i.putExtra("args", args);
        // The SamService application does not specify a permission in the BroadcastRReceiver
        if (context != null) {
            context.sendBroadcast(i);
        }

        try {
            if (rt != null) {
                return rt.exec(new String[] {
                        "su", "-c", command + " " + args
                });
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static public boolean unconfigure(final String ifaceNameRaw) {

        String ifaceName = sanitizeInterfaceName(ifaceNameRaw);

        // bail if the interfaceName is null
        if (ifaceName == null)
            return false;

        Process proc;
        try {
            Runtime rt = Runtime.getRuntime();

            proc = execSU(rt, "ip", "link set dev " + ifaceName + " down");
            wait(proc);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
            return false;
        }
    }

    /**
     * Given the interfacename and the address, netmask, gateway this method switches to the root
     * user and configures the network. Replaces the default route if the network is of
     * NetworkType.NORMAL
     *
     * @param ifaceNameRaw the name of the interface to configure
     * @param addressRaw   in IPv4 notation.
     * @param netmaskRaw   in long form.
     * @param gatewayRaw   in IPv4 notation, can be null or the empty string to denote no gateway exists.
     *                  if no gateway exists, do not set the default route.
     * @param createDefaultRoute  create a default route
     * @param dnsRaw       the dns server, not set if dns is the empty string or null.
     */
    static public boolean configure(final String ifaceNameRaw,
            final String addressRaw,
            final String netmaskRaw,
            final String gatewayRaw,
            final boolean createDefaultRoute,
            final String dnsRaw) {

        String ifaceName = sanitizeInterfaceName(ifaceNameRaw);
        String address = sanitizeAddress(addressRaw);
        String netmask = sanitizeAddress(netmaskRaw);
        String gateway = sanitizeAddress(gatewayRaw);
        String dns = sanitizeAddress(dnsRaw);

        // bail if the address, netmask or interfaceName are null
        if ((ifaceName == null) || (address == null) || (netmask == null))
            return false;

        refreshFilter();

        Process proc;
        try {
            Runtime rt = Runtime.getRuntime();
            proc = execSU(rt, "ip", "link set dev " + ifaceName + " down");
            wait(proc);
            proc = execSU(rt, "ip", "addr flush dev " + ifaceName);
            wait(proc);
            proc = execSU(rt, "ip", "addr add " + address + "/"
                    + toShortNetmask(netmask) + " dev "
                    + ifaceName);
            wait(proc);
            proc = execSU(rt, "ip", "link set " + ifaceName + " up");
            wait(proc);
            if ((gateway != null) && (gateway.length() > 3)
                    && !gateway.equals("0.0.0.0")) {
                if (createDefaultRoute) {
                    proc = execSU(rt, "ip", "route delete default");
                    wait(proc);
                    proc = execSU(rt, "ip", "route add default via " + gateway);
                    wait(proc);
                } else {
                    String masked = maskedAddress(address, netmask);
                    proc = execSU(rt, "ip", "route change " +
                            masked + "/" + toShortNetmask(netmask)
                            + " via " + gateway);
                    wait(proc);
                }
                proc = execSU(rt, "ip", "route replace 224.0.0.0/4 dev "
                        + ifaceName);
                wait(proc);
            } else {
                /**
                 String trimmed_address = address.substring(0,
                 address.lastIndexOf('.'));
                 proc = execSU(rt, "ip", "route delete " + trimmed_address
                 + "/24");
                 wait(proc);
                 proc = execSU(rt, "ip", "route add " + trimmed_address
                 + "/24 dev " + ifaceName);
                 wait(proc);
                 **/
            }

            if ((dns != null) && (dns.length() > 3)) {
                proc = execSU(rt, "setprop", "net.dns1 " + dns);
                wait(proc);
                proc = execSU(rt, "setprop", "net.dns2 " + "8.8.8.8");
                wait(proc);
                // android 5, 6, 7
                proc = execSU(rt, "ndc", "resolver setnetdns " + ifaceName
                        + " \"\" " + dns + " 8.8.8.8");
                wait(proc);
            } else if ((gateway != null) && (gateway.length() > 3)
                    && !gateway.equals("0.0.0.0")) {
                //   https://github.com/bparmentier/DNSSetter/wiki/ndc-resolver-commands
                //   ndc resolver setnetdns <netId> <domains> <dns1> <dns2> ...
                //   ndc resolver clearnetdns <netId>
                //   ndc resolver flushnet <netId>

                // android 5, 6, 7
                final String defdnsAddr = gateway + " 8.8.8.8 8.8.8.4";
                proc = execSU(rt, "setprop", "net.dns1 " + gateway);
                wait(proc);
                proc = execSU(rt, "setprop", "net.dns2 " + "8.8.8.8");
                wait(proc);
                proc = execSU(rt, "ndc", "resolver setnetdns " + ifaceName
                        + " \"\" " + defdnsAddr);
                wait(proc);
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
            return false;
        }
    }

    /**
     * Given the interfacename switches to the root user and configures the network.
     */
    static public boolean configure(final String ifaceNameRaw) {

        refreshFilter();

        Process proc;

        String ifaceName = sanitizeInterfaceName(ifaceNameRaw);
        if (ifaceName == null)
            return false;

        try {
            Log.v(TAG, "starting dynamic configuration: " + ifaceName);
            Runtime rt = Runtime.getRuntime();
            File dhcptool = new File("/system/bin/dhcptool");
            if (IOProviderFactory.exists(dhcptool)) {
                Log.d(TAG, "using marshallow fallback");
                proc = execSU(rt, "dhcptool", ifaceName);
            } else {
                proc = execSU(rt, "netcfg", ifaceName + " dhcp");
            }

            wait(proc);
            proc = execSU(rt, "ip", "route replace 224.0.0.0/4 dev "
                    + ifaceName);

            wait(proc);
            /**
                        final String defdnsAddr = "8.8.8.8 8.8.8.4";
                        proc = execSU(rt, "setprop", "net.dns1 " + "8.8.8.8");
                        wait(proc);
                        proc = execSU(rt, "setprop", "net.dns2 " + "8.8.8.4");
                        wait(proc);
                        proc = execSU(rt, "ndc", "resolver setnetdns " + ifaceName + " \"\" " + defdnsAddr);
                        wait(proc);
            **/

            return true;
        } catch (Exception e) {
            Log.v(TAG, "error occurred: " + e);
            return false;
        }
    }

    /**
     * Helper function for the short netmask computation.
     */
    static private int setBits(int i) {
        i = i - ((i >> 1) & 0x55555555);
        i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
        return (((i + (i >> 4)) & 0x0F0F0F0F) * 0x01010101) >> 24;
    }

    /**
     * Responsible for removing spaces and other bad input.
     * @return returns null on bad input
     */
    static String sanitizeInterfaceName(final String s) {
        if (s == null)
            return s;

        final String retval = s.trim().replaceAll("[^A-Za-z0-9]", "");

        if (retval.equals(s)) {
            return retval;
        } else {
            Log.d(TAG, "failed check: " + s);
            return null;
        }
    }

    /**
     * Responsible for removing spaces and other bad input.
     * @return returns null on bad input
     */
    static String sanitizeAddress(final String s) {
        if (s == null)
            return null;

        try {
            String[] a = s.split("\\.");
            if (a.length != 4)
                return null;

            for (int i = 0; i < 4; ++i) {
                int lnum = Integer.parseInt(a[i]);
                if (lnum < 0 || lnum > 255)
                    return null;
            }
            return s;
        } catch (Exception ignored) {
        }

        return null;

    }

    /**
     * Given an IP address, return the appropriate byte array.
     */
    static private byte[] getByteAddress(final String s) {
        String[] arr = s.split("\\.");
        byte[] b = new byte[4];
        if (arr.length != 4)
            return null;
        try {
            for (int i = 0; i < 4; ++i) {
                int val = Integer.parseInt(arr[i]);
                b[i] = (byte) val;
            }
            return b;
        } catch (Exception e) {
            return null;
        }

    }

    /**
     * Computes a short netask given a fully expanded netmask.
     */
    static private String toShortNetmask(String s) {
        try {

            InetAddress netAddr = InetAddress.getByAddress(getByteAddress(s));
            byte[] bytes = netAddr.getAddress();
            return ""
                    + setBits((bytes[0] << 24 | (bytes[1] & 0xFF) << 16
                            | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF)));

        } catch (Exception e) {
            return "24";
        }
    }

    static private String maskedAddress(String address, String netmask) {
        String dflt = address.substring(0, address.lastIndexOf('.'));
        try {
            byte[] addrBytes = getByteAddress(address);
            if (addrBytes == null)
                return dflt;

            byte[] netAddrBytes = getByteAddress(netmask);
            if (netAddrBytes == null)
                return dflt;

            if (addrBytes.length != netAddrBytes.length) {
                return dflt;
            }

            for (int i = 0; i < addrBytes.length; i++) {
                addrBytes[i] = (byte) ((addrBytes[i] & netAddrBytes[i]) & 0xFF);
            }

            InetAddress masked = InetAddress.getByAddress(addrBytes);
            return masked.getHostAddress();
        } catch (Exception e) {
            return dflt;
        }
    }

}
