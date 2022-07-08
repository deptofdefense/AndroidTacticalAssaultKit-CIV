/***************************************************************************
 *  Copyright 2013 PAR Government Systems
 *
 * Restricted Rights:
 * Use, reproduction, or disclosure of executable code, application interface
 * (API), source code, or related information is subject to restrictions set
 * forth in the contract and/or license agreement.    The Government's rights
 * to use, modify, reproduce, release, perform, display, or disclose this
 * software are restricted as identified in the purchase contract. Any
 * reproduction of computer software or portions thereof marked with this
 * legend must also reproduce the markings. Any person who has been provided
 * access to this software must be aware of the above restrictions.
 * 
 * Permission has been granted for use within the ATAK application.
 */

package com.atakmap.android.radiolibrary;

import android.content.pm.PackageManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.preference.PreferenceManager;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Responsible for controlling the ppp connection.
 */
public class HarrisHelper {

    final static String TAG = "HarrisHelper";
    private File root;
    private Process proc;
    final AssetManager assetManager;
    final Context context;
    private static boolean displayed = false;
    private boolean started = false;

    private final ControllerPppd cPppd;

    HarrisHelper(Context c, HarrisSaRadioManager harrisSaRadioManager) {
        context = c;
        assetManager = c.getAssets();
        cPppd = new ControllerPppd(c, harrisSaRadioManager);
    }

    public void start() {
        synchronized (this) {
            if (started) {
                stop();
            }
            started = true;
            check();
        }
    }

    public void stop() {
        synchronized (this) {
            started = false;
            write(TAG, "stopping the ppp link with an invalid devices name");
            if (root != null) {
                runppp("STOP");
            }
        }
    }

    public boolean isStarted() {
        synchronized (this) {
            return started;
        }
    }

    /**
     * runs the ppp script with a specific device name, otherwise STOP all upper case will 
     * stop any existing pppd process.
     */
    synchronized private void runppp(String device) {

        final SharedPreferences _controlPrefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        String cable = _controlPrefs.getString("prc_cable_style",
                "PRC-152A");
        if (cable == null)
            cable = "unknown";

        try {
            write(TAG, "execution of the ppp with: " + device + " with cable "
                    + cable);
            Runtime rt = Runtime.getRuntime();

            if (isInstalled(context, "com.atakmap.samservices")
                    || isInstalled(context, "com.partech.samservices")) {
                Log.d(TAG, "utilizing the samservices to control");
                if (device.equals("STOP"))
                    cPppd.stop();
                else
                    cPppd.start();
            } else if (cable.startsWith("PRC-152A")) {
                proc = rt.exec("sh " + root.getPath() + "/connect.sh "
                        + root.getPath() + " " + device + " " + "HARRIS");
            } else if (cable.startsWith("BDAT")) {
                proc = rt.exec("sh " + root.getPath() + "/connect.sh "
                        + root.getPath() + " " + device + " " + "BDAT");
            } else if (cable.startsWith("PPP")) {
                proc = rt
                        .exec("sh " + root.getPath() + "/connect.sh "
                                + root.getPath() + " " + device + " " + "PPP");
            } else if (cable.startsWith("RDA")) {
                proc = rt
                        .exec("sh " + root.getPath() + "/connect.sh "
                                + root.getPath() + " " + device + " " + "RDA");
            } else {
                proc = rt.exec("sh " + root.getPath() + "/connect.sh "
                        + root.getPath() + " " + device + " " + "NORMAL");
            }
        } catch (Exception e) {
            write(TAG, "error occurred: " + e);
            Log.e(TAG, "error: ", e);
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {

        }
    }

    final void decode(final InputStream in, final OutputStream out,
            String version) throws IOException {
        final String lic = "/*************************************************************************** \n"
                + " *  Copyright 2013 PAR Government Systems \n"
                + " * \n"
                + " * Restricted Rights: \n"
                + " * Use, reproduction, or disclosure of executable code, application interface \n"
                + " * (API), source code, or related information is subject to restrictions set \n"
                + " * forth in the contract and/or license agreement.    The Government's rights \n"
                + " * to use, modify, reproduce, release, perform, display, or disclose this \n"
                + " * software are restricted as identified in the purchase contract. Any \n"
                + " * reproduction of computer software or portions thereof marked with this \n"
                + " * legend must also reproduce the markings. Any person who has been provided \n"
                + " * access to this software must be aware of the above restrictions. \n"
                + " *  \n"
                + " * Permission has been granted for use within the ATAK application. \n"
                + " * Attempts to decompile or use outside of ATAK is not permitted. \n"
                + " * This restriction applies to all files requiring decode using this algorithm. \n"
                + " */ \n";
        if (!displayed) {
            displayed = true;
            write(TAG, lic);
        }

        int b;
        byte[] magic;
        int verlen = version.length();
        int i = 0;
        String mz = "TAK";

        if (in.read(magic = new byte[mz.length()]) < 2) {
            return;
        }

        if (new String(magic, FileSystemUtils.UTF8_CHARSET).equals(mz)) {
            for (int j = 0; (b = in.read()) >= 0; j++) {
                out.write(b ^ version.charAt(i++ % verlen));
            }
        }
        in.close();
        out.close();
    }

    private void extract(String assetRoot, String[] files, File dest) {
        for (String name : files) {
            File out = new File(dest, name.substring(0, name.length() - 3));
            try {
                Log.d(TAG, "extracting: " + assetRoot + "/" + name);
                InputStream inStream = assetManager
                        .open(assetRoot + "/" + name);
                boolean b = push(inStream, out);
                if (!b)
                    Log.d(TAG, "failed to extract: " + name);
            } catch (IOException ioe) {
                Log.d(TAG, "missing asset in the apk: " + assetRoot + "/"
                        + name);
            }
        }
    }

    String getModelName() {
        return Build.MODEL;
    }

    private boolean isInstalled(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Package not installed: " + pkg);
        }
        return false;
    }

    /**
     * Substandard check to see if a device is rooted.
     */
    boolean isRooted() {

        if (isInstalled(context, "com.atakmap.samservices")) {
            return true;
        }

        if (isInstalled(context, "com.partech.samservices")) {
            return true;
        }

        String sSU = context
                .getString(R.string.FalconSA_su);
        String sSU2 = context
                .getString(R.string.FalconSA_su2);
        String sPPPD = context
                .getString(R.string.FalconSA_pppd);
        File su = new File(sSU);
        File su2 = new File(sSU2);
        File pppd = new File(sPPPD);

        return ((su.exists() || su2.exists()) && pppd.exists());
    }

    /**
     * Retrieves the appropriate model name used by the shell script based on the 
     * Android Build Model.
     */
    String getScriptModel() {
        final File nwfile = new File("/proc/NettWarrior/nwplatform");
        boolean nwdevice = nwfile.exists();

        if (Build.MODEL.indexOf("G900T") > 0 && nwdevice) {
            return "sm-g900t";
        } else if (Build.MODEL.indexOf("G900F") > 0 && nwdevice) {
            return "sm-g900t";
        } else if (Build.MODEL.indexOf("G900FD") > 0 && nwdevice) {
            return "sm-g900t";
        } else if (Build.MODEL.indexOf("G900I") > 0 && nwdevice) {
            return "sm-g900t";
        } else {
            return "generic";
        }
    }

    /**
     * If the model is supported. 
     * NOTE: For the time being, the only thing we check for is if the device 
     * is rooted, but this could change.
     */
    boolean isModelSupported() {
        return isRooted() && (getScriptModel() != null);
    }

    private void check() {

        root = new File(context.getFilesDir(), "ppp");

        if (!root.mkdir()) {
            Log.e(TAG, "Failed to make dir " + root.getAbsolutePath());
        }

        final String model = getScriptModel();
        if (!isModelSupported())
            return;

        write(TAG, "model: " + model);

        try {
            String[] files = assetManager.list("radiocontrol/" + model);
            extract("radiocontrol/" + model, files, root);
        } catch (IOException ioe) {
            write(TAG,
                    "model unsupported at this time, error occurred: " + ioe);
            Log.e(TAG, "error: ", ioe);
            return;
        }

        try {
            String[] files = assetManager.list("radiocontrol/" + "ppp");
            extract("radiocontrol/" + "ppp", files, root);
        } catch (IOException ioe) {
            write(TAG,
                    "model unsupported at this time, error occurred: " + ioe);
            Log.e(TAG, "error: ", ioe);
            return;

        }

        runppp(model);

    }

    public void dispose() {
        cPppd.dispose();
    }

    /**
     * Returns the address for the point to point link, otherwise null if no address is discovered.
     */
    public InetAddress getAddress() {
        try {

            NetworkInterface ni = NetworkInterface.getByName("ppp0");
            if (ni != null && ni.isUp()) {
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress()
                            && (address instanceof Inet4Address)) {
                        write(TAG,
                                "discovered falcon interface: "
                                        + ni.getDisplayName() +
                                        " IP address: "
                                        + address.getHostAddress());
                        return address;
                    }
                }
            }
        } catch (Exception s) {
            Log.e(TAG, "error: ", s);

        }
        return null;

    }

    /**
     */
    final void write(String tag, final String msg) {
        Log.d(tag, msg);
    }

    /**
     * Given an inputstream, write it to a location specified by the callee.
     */
    private boolean push(final InputStream is, final File out) {
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        try {
            bis = new BufferedInputStream(is);

            fos = new FileOutputStream(out);
            bos = new BufferedOutputStream(fos);

            decode(bis, bos, "phantom");

            return true;
        } catch (Exception ex) {
            write(TAG, "ppp file extract error occurred extracting [" + out
                    + "]: " + ex);
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignore) {
                }
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException ignore) {
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

}
