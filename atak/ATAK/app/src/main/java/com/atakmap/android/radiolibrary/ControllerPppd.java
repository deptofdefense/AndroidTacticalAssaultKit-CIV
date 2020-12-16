
package com.atakmap.android.radiolibrary;

import java.io.File;
import java.io.IOException;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.NetworkDeviceManager;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.BroadcastReceiver;
import java.net.NetworkInterface;
import com.atakmap.coremap.filesystem.FileSystemUtils;

public class ControllerPppd extends BroadcastReceiver implements Runnable {

    public static final String TAG = "ControllerPppd";
    Thread t;
    private boolean cancelled = false;
    @ModifierApi(since = "4.2", target = "4.5", modifiers = {
            "private", "final"
    })
    Context context;
    @ModifierApi(since = "4.2", target = "4.5", modifiers = {
            "private", "final"
    })
    File root;

    public ControllerPppd(Context c) {
        context = c;
        // For use with the Android S6 com.atakmap.Samservices
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.pppd");
        AtakBroadcast.getInstance().registerSystemReceiver(this, filter);
        root = new File(context.getFilesDir(), "ppp");

    }

    synchronized public void start() {
        cancelled = false;
        try {
            FileSystemUtils.copyFile(new File(root, "options"),
                    FileSystemUtils.getItem("tools/.options"));
        } catch (Exception e) {
            Log.e(TAG, "error copying file over", e);
        }
        t = new Thread(this);
        t.start();
    }

    @Override
    public void run() {
        while (!cancelled) {
            NetworkInterface nd = NetworkDeviceManager
                    .getInterface("30:30:70:70:70:30");
            try {
                if (nd == null || !nd.isUp()) {
                    String s = findDevNode();

                    // For use with the Android S6 com.atakmap.Samservices

                    if (s != null) {
                        Intent i = new Intent("com.atakmap.exec");
                        i.putExtra("command", "pppd");
                        i.putExtra(
                                "args",
                                s
                                        + " file "
                                        + FileSystemUtils
                                                .getItem("tools/.options"));
                        i.putExtra("return", "com.atakmap.pppd");
                        AtakBroadcast.getInstance().sendSystemBroadcast(i);
                    } else {
                        Log.d(TAG, "no connection to a Harris radio found");
                    }
                }
            } catch (Exception ie) {
                Log.d(TAG, "error determining if ppp0 is up", ie);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
        }

    }

    synchronized public void stop() {
        Log.d(TAG, "stopping the polling for Harris SA");
        cancelled = true;
        if (t != null)
            t.interrupt();
        t = null;
        FileSystemUtils.delete(FileSystemUtils.getItem("tools/.options"));
    }

    /**
     * Constructs a dev node with the largest ttyACM which is of type 
     * 19a5/4/0.  Returns null if no dev node found matching that 
     * criteria.
     */
    private String findDevNode() {

        int acm = -1;

        for (int i = 0; i < 25; ++i) {
            boolean found = find("19a5/4/0",
                    new File("/sys/class/tty/ttyACM" + i + "/device/uevent"));
            if (found) {
                // do not early break here, we need to find the last one.
                acm = i;
            }
        }

        if (acm < 0)
            return null;

        Log.d(TAG, "found attached Harris radio at: /dev/ttyACM" + acm);
        return "/dev/ttyACM" + acm;

    }

    /**
     * Finds a string within a file.
     * @param term term the terms to search for in the file
     * @param f file the file.
     * @return true if the term exists
     */
    private boolean find(final String term, final File f) {
        try {
            String s = FileSystemUtils.copyStreamToString(f);
            if (s != null && s.contains(term))
                return true;
        } catch (IOException ioe) {
            // error occurred reading the file.
            return false;
        }

        return false;
    }

    public void dispose() {

        try {
            AtakBroadcast.getInstance().unregisterSystemReceiver(this);
        } catch (Exception e) {
            // protect against double call to dispose
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "result received: " + action);
    }

}
