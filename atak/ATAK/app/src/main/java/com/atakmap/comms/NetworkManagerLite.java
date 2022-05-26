
package com.atakmap.comms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkManagerLite extends BroadcastReceiver {

    public static final String TAG = "NetworkManagerLite";

    private static boolean filtersDropped = false;
    private static final Object lock = new Object();
    private final static Map<String, NetworkDevice> devices = new HashMap<>();

    public static final String NETWORK_STATUS_CHANGED = "com.partech.networkmonitor.STATUS_CHANGED";

    public static final String LIST_NETWORK = "com.partech.networkmonitor.LIST_NETWORK";
    public static final String NETWORK_LIST = "com.partech.networkmonitor.NETWORK_LIST";

    public static class NetworkDevice {
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

            static public NetworkDevice.Configuration findFromValue(int value) {
                for (NetworkDevice.Configuration c : NetworkDevice.Configuration
                        .values())
                    if (c.value == value)
                        return c;
                return null;
            }

        }

        public enum Type {
            COT_INPUT(0, "cot_input", "CoT Input"),
            COT_OUTPUT(1, "cot_output", "CoT Output"),
            COT_BOTH(3, "cot_both", "CoT Bidirectional"),
            ISRV_SIR(10, "isrv_sir", "Soldier ISR Receiver (SIR)"),
            ISRV_TRE(11, "isrv_tre", "Tactical Rover Enhanced (TRE)"),
            ISRV_ROVER6(12, "isrv_rover6", "Rover 6"),
            ISRV_VORTEX(13, "isrv_vortex", "Vortex"),
            POCKET_DDL(16, "pocket_ddl", "PocketDDL"),
            UNKNOWN(99, "unknown", "Generic Network Device");

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
                for (NetworkDevice.Type t : NetworkDevice.Type.values())
                    if (t.value == value)
                        return t;
                return UNKNOWN;
            }

            static public Type[] fromTypeString(String v) {
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
        }

        private final Configuration configuration;
        private final List<Type> types = new ArrayList<>();
        private final String interfaceName;
        private final String prefAddress;
        private final String prefNetmask;
        private final String prefGateway;
        private final boolean prefCreateDefaultRoute;
        private final String label;

        /**
         * Test to see if the type is supported.
         *
         * @param t the type
         * @return true if the type is supported, false if not.
         */
        public boolean isSupported(final NetworkDevice.Type t) {
            for (NetworkDevice.Type aType : types) {
                if (aType == t)
                    return true;
            }
            return false;
        }

        /**
         * Return the interface name for the Network Device based on information
         * provided from Network Monitor
         * @return the string
         */
        public String getInterfaceName() {
            return interfaceName;
        }

        /**
         * Based on the interface name, get the interface otherwise if no interface name
         * is provided, return null.
         * @return the interface or null
         */
        public NetworkInterface getInterface() {
            try {
                if (interfaceName != null)
                    return NetworkInterface.getByName(interfaceName);
            } catch (Exception ignored) {
            }

            return null;
        }

        /**
         * Gets the preferred address for the device for static configuration.
         * @return the preferred static ip address
         */
        public String getPreferredAddress() {
            return prefAddress;
        }

        /**
         * Gets the preferred label for the device
         * @return the label
         */
        public String getLabel() {
            return label;
        }

        /**
         * Constructor for a dummy network device in support of the video alias list
         * @param label the label to use for the dummy network device which is also used
         *              as the interface name.
         */
        public NetworkDevice(String label) {
            this(label, new Type[] {
                    Type.UNKNOWN
            }, Configuration.NONE,
                    label, null, null, null, false);
        }

        /**
         * Construct a network device which contains the current mapping information for the
         * best identifying the device.
         * @param label the label for the device
         * @param types the types of traffic supported
         * @param c the configuration type
         * @param interfaceName the name of the interface
         * @param prefAddress the preferred address when used in static configuration
         * @param prefNetmask the preferred netmask when used in static configuration
         * @param prefGateway the preferred gateway when used in static configuration
         * @param prefCreateDefaultRoute if a default route should be created.
         */
        public NetworkDevice(@NonNull String label, @NonNull Type[] types,
                @NonNull Configuration c, String interfaceName,
                String prefAddress, String prefNetmask, String prefGateway,
                boolean prefCreateDefaultRoute) {

            this.label = label;
            this.types.addAll(Arrays.asList(types));
            this.configuration = c;
            this.interfaceName = interfaceName;
            this.prefAddress = prefAddress;
            this.prefNetmask = prefNetmask;
            this.prefGateway = prefGateway;
            this.prefCreateDefaultRoute = prefCreateDefaultRoute;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        if (action == null)
            return;

        // parse one of the following:
        //         macaddress,label,type,address,netmask,gateway,name,usbVendorId,usbProductId,usbProductName,usbSerianNumber,usbName
        //         macaddress,label,type,"dhcp",name,usbVendorId,usbProductId,usbProductName,usbSerianNumber,usbName
        //         macaddress,label,type,"none",name,usbVendorId,usbProductId,usbProductName,usbSerianNumber,usbName
        if (action.equals(NETWORK_STATUS_CHANGED)
                || action.equals(NETWORK_LIST)) {
            String[] networks = intent.getStringArrayExtra("networks");
            if (networks == null) {
                String device = intent.getStringExtra("device");
                if (device != null)
                    networks = new String[] {
                            device
                    };
            }

            if (networks == null)
                return;

            for (String network : networks) {
                String[] arr = network.split(",");
                if (arr.length > 6) {
                    NetworkDevice nd = null;

                    try {
                        NetworkDevice.Configuration c;
                        if (arr[4].equals("dhcp")) {
                            c = NetworkDevice.Configuration.DHCP;
                        } else if (arr[4].equals("none")) {
                            c = NetworkDevice.Configuration.NONE;
                        } else {
                            c = NetworkDevice.Configuration.STATIC;
                        }

                        NetworkDevice.Type[] types = NetworkDevice.Type
                                .fromTypeString(arr[2]);
                        switch (c) {
                            case DHCP:
                            case NONE:
                                nd = new NetworkDevice(arr[1], types,
                                        c, arr[4], null, null, null, false);
                                break;
                            case STATIC:
                                nd = new NetworkDevice(arr[1], types,
                                        c, arr[6], arr[3], arr[4], arr[5],
                                        false);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "error parsing line");
                    }
                    synchronized (lock) {
                        if (nd != null)
                            devices.put(arr[0], nd);
                    }

                }
            }
        }
    }

    /**
     * Retrieve all of the network devices that are being tracked by network monitor.
     * If the list is empty,
     * @return a copy of the network devices currently being managed by Network Monitor
     */
    static public List<NetworkDevice> getNetworkDevices() {
        synchronized (lock) {
            return new ArrayList<>(devices.values());
        }
    }

    /**
     * Configure an interface just given a network device.
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
        AtakBroadcast.getInstance().sendSystemBroadcast(i);

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
                } else {
                    String masked = maskedAddress(address, netmask);
                    proc = execSU(rt, "ip", "route change " +
                            masked + "/" + toShortNetmask(netmask)
                            + " via " + gateway);
                }
                wait(proc);
                proc = execSU(rt, "ip", "route replace 224.0.0.0/4 dev "
                        + ifaceName);
                wait(proc);
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
     * Given the interface name configures the network.
     * @param ifaceNameRaw the raw interface name
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
            return null;

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
