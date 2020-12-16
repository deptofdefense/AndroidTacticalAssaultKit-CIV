
package com.atakmap.android.radiolibrary;

import android.content.BroadcastReceiver;

import android.app.PendingIntent;
import java.io.IOException;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.content.Context;
import android.content.Intent;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import java.util.List;

public class HarrisConfigurator {

    private final String TAG = "HarrisConfigurator";
    private String callsign;
    private boolean localreport = false;
    private final Context context;

    public HarrisConfigurator(Context context) {
        this.context = context;
        AtakBroadcast
                .getInstance()
                .registerSystemReceiver(attached, new DocumentedIntentFilter(
                        "com.partech.harrisconfigurator.ACTION_USB_PERMISSION"));
    }

    public void dispose() {
        context.unregisterReceiver(attached);
    }

    private final BroadcastReceiver attached = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;

            if (action
                    .equals("com.partech.harrisconfigurator.ACTION_USB_PERMISSION")) {
                Log.d(TAG, "permission granted for the usb device");
                final UsbManager usbManager = (UsbManager) context
                        .getSystemService(Context.USB_SERVICE);
                UsbDevice dev = intent
                        .getParcelableExtra(UsbManager.EXTRA_DEVICE);
                final boolean granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted)
                    configure(usbManager, dev, callsign, localreport);

            }
        }

    };

    public void configure(final String callsign, boolean localreport) {
        this.callsign = callsign;
        this.localreport = localreport;
        if (callsign != null) {
            scan(context, callsign);
        }
    }

    private void scan(Context context, String callsign) {

        final UsbManager usbManager = (UsbManager) context
                .getSystemService(Context.USB_SERVICE);

        if (usbManager == null)
            return;

        for (final UsbDevice dev : usbManager.getDeviceList().values()) {

            PendingIntent mPI = PendingIntent
                    .getBroadcast(
                            context,
                            0,
                            new Intent(
                                    "com.partech.harrisconfigurator.ACTION_USB_PERMISSION"),
                            0);

            usbManager.requestPermission(dev, mPI);

            if (!usbManager.hasPermission(dev)) {
                return;
            } else {
                configure(usbManager, dev, callsign, localreport);
            }
        }
    }

    synchronized private void configure(UsbManager usbManager, UsbDevice dev,
            String callsign, boolean localreport) {
        if (dev.getVendorId() == 6565) {
            // Find the first available driver.
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber()
                    .probeDevice(dev);
            if (driver == null)
                return;

            UsbSerialPort port;

            List<UsbSerialPort> lusb = driver.getPorts();
            for (int i = 0; i < lusb.size(); i++) {
                if (i > 0)
                    return;
                port = lusb.get(i);
                configure(port, usbManager, dev, callsign, localreport);
                try {
                    port.close();
                } catch (IOException ignored) {
                }
            }
        }

    }

    private void configure(final UsbSerialPort port, final UsbManager manager,
            final UsbDevice device, String callsign, boolean localreport) {
        try {
            port.open(manager.openDevice(device));

            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE);
            write(port, "PROGRAM\r");
            write(port, "DATA_PORT LEVEL USB\r");
            Log.d(TAG, "changing sa config name to: " + callsign);
            write(port, "SA CFG NAME NAME " + "." + callsign + "\r");

            write(port, "DATA_PORT AUTOSWITCH OFF\r");
            write(port, "DATA_PORT MODE PPP\r");
            write(port, "PORT 1 CONFIG BAUD 115200\r");
            write(port, "PORT 1 CONFIG DATA 8\r");
            write(port, "PORT 1 CONFIG PARITY NONE\r");
            write(port, "PORT 1 CONFIG STOP 1\r");
            write(port, "PORT 1 CONFIG FLOW NONE\r");
            write(port, "SA ENABLE\r");
            write(port, "SA CFG RECEIVE MODE ON\r");
            write(port, "SA CFG RECEIVE PACKET_TYPE CURSOR_ON_TRGT\r");
            write(port, "SA CFG TRANSMITMODE AUTO\r");
            write(port, "SA CFG NAME FORMAT NAME\r");
            write(port, "SA CFG RECEIVE DESTINATION_IP PPP_PEER\r");
            write(port, "SA CFG RECEIVE CUSTOM_IP 10.0.0.2\r");
            write(port, "SA CFG RECEIVE PORT 10011\r");
            write(port, "SA CFG RECEIVE STALE_TIME 10\r");
            if (localreport)
                write(port, "SA CFG RECEIVE LOCAL_REPORT ON\r");
            else
                write(port, "SA CFG RECEIVE LOCAL_REPORT OFF\r");
            write(port, "NETDEVICE PPP DATA_PORT CFG IPADDR 10.0.0.1\r");
            write(port, "NETDEVICE PPP DATA_PORT CFG PEERIP 10.0.0.2\r");
            write(port, "NETDEVICE PPP DATA_PORT CFG SNMASK 255.255.0.0\r");

            write(port, "NORMAL\r");
        } catch (Exception e) {
            Log.e(TAG, "error occurred during configuration", e);
        }
    }

    private void drain(final UsbSerialPort port) {
        int bread = 0;
        do {
            byte[] buf = new byte[1024];
            try {
                bread = port.read(buf, 100);
            } catch (IOException ioe) {
                Log.d(TAG, "", ioe);
            }
        } while (bread > 0);
    }

    private void write(final UsbSerialPort port, final String command)
            throws IOException {
        if (command == null)
            return;

        drain(port);

        byte[] ascii = command.getBytes();
        int amtWritten;
        amtWritten = port.write(ascii, 1000);
        if (amtWritten != -1) {
            Log.d(TAG, "port.write success, amtWritten=" + amtWritten);
        } else {
            Log.d(TAG, "error writing to port");
        }
    }

}
