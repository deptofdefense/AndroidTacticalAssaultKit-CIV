
package com.atakmap.comms;

import android.util.Patterns;
import android.webkit.URLUtil;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Common place for network related functionality that seems to be used in several areas
 */
public class NetworkUtils {

    public static final String TAG = "NetworkUtils";

    private static final String ANY_ADDRESS = "0.0.0.0";
    private static final String TETHER_ADDR = "192.168.42.129";
    static public final String ALL_CONNECTIONS = "*";

    /**
     * Blocking this entire method for refactoring. I will place a band aid around this so that it
     * will be called less frequently, but still provide an ip address if changed while ATAK is
     * running. XXX - Calling out for refactoring. Ten seconds must elapse between calls in order
     * for a new IP address will be searched for. The granularity of checking for a changing IP
     * address on every call is just sick! I am keeping these fields local to this mess instead of
     * following convention.
     */
    private static final long PERIOD_MS = 10000;
    private static String lastAddress = ANY_ADDRESS;
    private static long lastTime = 0;
    private static AtomicInteger lastNetmask = new AtomicInteger(-1);
    private static boolean vpnAddress = false;

    synchronized public static String getIP() {
        String addressBeforeCall = lastAddress;
        getIP_helperFunction();
        String addressAfterCall = lastAddress;
        if (addressBeforeCall != null &&
                addressAfterCall != null &&
                !addressBeforeCall.equals(addressAfterCall))
            notifyListenersOfNetRestartNotification(ALL_CONNECTIONS);
        return addressAfterCall;
    }

    /**
     * Determine if the address supplied is a multicast address
     */
    public static boolean isMulticastAddress(String address) {
        if (address == null)
            return false;

        try {
            String[] a = address.split("\\.");
            if (a.length != 4)
                return false;

            int num = Integer.parseInt(a[0]);
            if (num >= 224 && num <= 239) {
                for (int i = 1; i < 4; ++i) {
                    int lnum = Integer.parseInt(a[i]);
                    if (lnum < 0 || lnum > 255)
                        return false;
                }
            } else
                return false;

            return true;
        } catch (Exception ignored) {
        }

        return false;
    }

    /**
    * Given an IP address, return the appropriate byte array.
    */
    static public byte[] getByteAddress(final String s) {
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
     * Obtains an IP address local to the device. This does not take into account that there could
     * be multiple valid IP address on a phone. (Code is not equipted to deal with multiple valid IP
     * addresses at a higher level).
     */
    synchronized private static String getIP_helperFunction() {

        if ((android.os.SystemClock.elapsedRealtime() - lastTime) < PERIOD_MS)
            return lastAddress;

        // Block to use a user supplied endpoint by looking at the preferences.
        lastTime = android.os.SystemClock.elapsedRealtime();

        // SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(_context);
        // String userSupplied = preferences.getString("userSuppliedEndpoint", "");
        // check if the ip is valid
        // set the ip to the lastDiscovered
        // return;

        // Quickly check to see that the network interface is up and has not changed address. This
        // should reduce the calls to the more costly search routine that seems to degrade in
        // performance over time.
        try {
            vpnAddress = (NetworkInterface.getByName("cscotun0") != null) ||
                    (NetworkInterface.getByName("tun0") != null);
        } catch (Exception ignored) {
        }

        // XXX: this does not work when we're using VPN's - i.e., we acquire an
        // address, then shortly thereafter, we acquire a VPN address, but
        // still have the old addr.

        if (!lastAddress.equals(ANY_ADDRESS) && !vpnAddress) {
            try {
                NetworkInterface ni = NetworkInterface
                        .getByInetAddress(InetAddress
                                .getByName(lastAddress));
                if (ni != null && ni.isUp()
                        && !lastAddress.equals(TETHER_ADDR)) {
                    Log.v(TAG, "checked interface: " + ni.getDisplayName() +
                            " ip address remains the same: " + lastAddress);
                    return lastAddress;
                }
            } catch (Exception ignored) {
            }
        }

        boolean useWifi = false;
        String wifiAddr = ANY_ADDRESS;
        AtomicInteger wifiMask = new AtomicInteger(-1);

        try {
            Enumeration<NetworkInterface> inets = NetworkInterface
                    .getNetworkInterfaces();
            while (inets != null && inets.hasMoreElements()) {
                NetworkInterface inter = inets.nextElement();
                // check the state of the tether.
                if (inter.isUp() && !inter.getDisplayName().equals("ppp0")) {
                    for (InterfaceAddress ifaceAddress : inter
                            .getInterfaceAddresses()) {
                        InetAddress address = ifaceAddress.getAddress();
                        if (!address.isLoopbackAddress()
                                && (address instanceof Inet4Address)
                                && !address.getHostAddress()
                                        .equals("0.0.0.0")) {
                            lastAddress = address.getHostAddress();
                            lastNetmask.set(ifaceAddress
                                    .getNetworkPrefixLength());
                            Log.v(TAG,
                                    "discovered from interface: "
                                            + inter.getDisplayName() +
                                            " ip address: " + lastAddress
                                            + " /" + lastNetmask.get());
                            // XXX HACK TO FORCE VPN ADDRESS
                            if (inter.getName().compareTo("cscotun0") == 0
                                    || inter.getName().compareTo("tun0") == 0) {
                                Log.d(TAG, "found VPN address: " + lastAddress);
                                return lastAddress;
                            } else if (inter.getName()
                                    .compareTo("wlan0") == 0) {//keep iterating through but use wifi if it exists
                                wifiAddr = lastAddress;
                                wifiMask = lastNetmask;
                                useWifi = true;
                            }
                        }
                    }
                    if (useWifi) {//iterated through, no VPNs, but wifi
                        lastAddress = wifiAddr;
                        lastNetmask = wifiMask;
                    }
                }
            }
        } catch (SocketException | NullPointerException e) {
            Log.d(TAG, "error opening socket" + e);
        }

        return lastAddress;
    }

    public interface NetRestartNotification {
        void onNetRestartNotification(final String newAddress, int newMask);
    }

    private static final Map<String, NetRestartNotification> netRestartListeners = new HashMap<>();

    public static void registerNetRestartNotification(String key,
            NetRestartNotification callback) {
        //Log.d(TAG, "onNetRestartNotification key=" + key);
        synchronized (netRestartListeners) {
            netRestartListeners.put(key, callback);
        }
    }

    /**
     * Notify listener with specified key to restart
     * Can use key = '*' to restart all streaming connections
     */
    private static void notifyListenersOfNetRestartNotification(
            final String key) {
        if (FileSystemUtils.isEmpty(key))
            return;

        synchronized (netRestartListeners) {
            for (Map.Entry<String, NetRestartNotification> listener : netRestartListeners
                    .entrySet()) {
                if (ALL_CONNECTIONS.equals(key)
                        || key.equals(listener.getKey())) {
                    Log.d(TAG, "notifyListenersOfNetRestartNotification key="
                            + key);
                    listener.getValue().onNetRestartNotification(lastAddress,
                            lastNetmask.get());
                }
            }
        }
    }

    /**
     * Return true if the network address passed in is considered valid
     * @param address the string address either in IP or named format
     * @return true if the address is considered valid.
     */
    public static boolean isValid(final String address) {
        try {
            String urlString = "https://" + address + ":" + 80;
            URL url = new URL(urlString);
            return URLUtil.isValidUrl(urlString)
                    && Patterns.WEB_URL.matcher(urlString).matches();
        } catch (MalformedURLException ignored) {
        }
        return false;
    }

}
