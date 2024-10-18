
package com.atakmap.android.radiolibrary;

import android.content.BroadcastReceiver;

import java.io.IOException;

import com.atakmap.app.R;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.coremap.log.Log;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import java.util.List;

public class HarrisConfigurator extends PortCommunicator {

    private final String TAG = "HarrisConfigurator";
    private final static String ACTION = "com.partech.harrisconfigurator.ACTION_USB_PERMISSION";
    private String callsign;
    private boolean localreport = false;
    private final Context context;
    private final HarrisSaRadioManager harrisSaRadioManager;

    public HarrisConfigurator(Context context,
            HarrisSaRadioManager harrisSaRadioManager) {
        this.context = context;
        AtakBroadcast
                .getInstance()
                .registerSystemReceiver(attached, new DocumentedIntentFilter(
                        ACTION));

        this.harrisSaRadioManager = harrisSaRadioManager;
    }

    public void dispose() {
        AtakBroadcast.getInstance().unregisterSystemReceiver(attached);
    }

    private final BroadcastReceiver attached = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(ACTION)) {
                Log.d(TAG, "permission granted for the usb device");
                final UsbManager usbManager = (UsbManager) context
                        .getSystemService(Context.USB_SERVICE);
                final boolean granted = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted)
                    configure(usbManager, harrisSaRadioManager
                            .getSelectedRadio().getUsbDevice(), callsign,
                            localreport);
            }
        }

    };

    public void configure(final String callsign, boolean localreport) {
        this.callsign = callsign;
        this.localreport = localreport;
        if (callsign != null) {
            final UsbManager usbManager = (UsbManager) context
                    .getSystemService(Context.USB_SERVICE);
            if (harrisSaRadioManager.getSelectedRadio() != null) {
                if (harrisSaRadioManager.getSelectedRadio().hasPermission()) {
                    configure(usbManager, harrisSaRadioManager
                            .getSelectedRadio().getUsbDevice(), callsign,
                            localreport);
                } else {
                    harrisSaRadioManager.getSelectedRadio()
                            .requestPermission(ACTION);
                }
            } else {
                Log.e(TAG, "selected radio is null, cannot configure radio.");
                Toast.makeText(context, R.string.harris_sa_failed_to_configure,
                        Toast.LENGTH_SHORT).show();
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
}
