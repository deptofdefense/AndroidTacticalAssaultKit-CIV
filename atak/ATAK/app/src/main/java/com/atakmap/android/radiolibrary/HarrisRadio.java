
package com.atakmap.android.radiolibrary;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.NonNull;

import com.atakmap.coremap.log.Log;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a valid harris radio, it's USB device ID as well as all tty ACM ports
 * associated with the device. Has the ability to query the radio for basic preset info.
 */
public class HarrisRadio extends PortCommunicator {
    /**
     * Tag for logging
     */
    private static final String TAG = "HarrisRadio";

    /**
     * command for getting current present values
     */
    private static final String HARRIS_GET_CURRENT_PRESET = "VULOS PRESET CURRENT\r";

    /**
     * The preset's current name field
     */
    private static final String PRESET_NAME_FIELD = "VULOS PRESET CURRENT NAME";

    /**
     * The preset's current transmission frequency field
     */
    private static final String TX_FREQ_FIELD = "VULOS PRESET CURRENT TXFREQ";

    /**
     * The undetermined place holder text
     */
    private static final String UNDETERMINED_PLACEHOLDER = "Undetermined";

    /**
     * Intent action for USB permissions
     */
    public static final String ACTION = "com.atakmap.android.radiolibrary.HarrisRadio.ACTION_USB_PERMISSION";

    /**
     * Holds the radio's USB device object
     */
    private final UsbDevice usbDevice;

    /**
     * Holds the present name of the currently set preset
     */
    private String presetName = UNDETERMINED_PLACEHOLDER;

    /**
     * Gets the transmitting frequency
     */
    private String frequency = UNDETERMINED_PLACEHOLDER;

    /**
     * Holds associated tty ACM ports
     */
    private final List<Integer> ttyAcmPorts = new ArrayList<>();

    /**
     * The Android USB Manager
     */
    private final UsbManager usbManager;

    private final Context context;

    /**
     * Constructor
     *
     * @param context    the context to use
     * @param usbManager the usb manager
     * @param usbDevice  The USB device associated with this radio
     */
    public HarrisRadio(Context context, UsbManager usbManager,
            UsbDevice usbDevice) {
        this.context = context;
        this.usbDevice = usbDevice;
        this.usbManager = usbManager;
    }

    /**
     * Gets the Device ID from the USB Manager
     *
     * @return the Device ID
     */
    public int getDeviceId() {
        return usbDevice.getDeviceId();
    }

    /**
     * Determines whether or not this radio has USB device permission.
     *
     * @return True if you can access this USB device, false otherwise.
     */
    public boolean hasPermission() {
        return usbManager.hasPermission(this.usbDevice);
    }

    /**
     * Requests access to the USB device associated with this radio
     *
     * @param action The android callback action for the intent.
     */
    public void requestPermission(String action) {
        if (!usbManager.hasPermission(usbDevice)) {
            PendingIntent mPI = PendingIntent
                    .getBroadcast(context, 0, new Intent(action), 0);
            usbManager.requestPermission(usbDevice, mPI);
        }
    }

    /**
     * Determines whether or not the radio has been queried and the values have been set.
     *
     * @return True if the values have been set, false otherwise.
     */
    public boolean valuesQueriedAndSet() {
        return !this.presetName.equals(UNDETERMINED_PLACEHOLDER)
                && !this.frequency.equals(UNDETERMINED_PLACEHOLDER);
    }

    /**
     * Gets the USB device associated with this radio
     *
     * @return the USB device
     */
    public UsbDevice getUsbDevice() {
        return usbDevice;
    }

    /**
     * Adds a tty ACM port number if not already added
     *
     * @param port the port number to add
     */
    public void addTtyAcmPort(int port) {
        if (!ttyAcmPorts.contains(port)) {
            ttyAcmPorts.add(port);
        }
        Collections.sort(ttyAcmPorts);
    }

    /**
     * Gets the last detected serial port, because that is what was originally happening with the
     * removed findDevNode method in ControllerPppd
     *
     * @return The last port of all the ports associated with this radio.
     */
    public int getLastPort() {
        return ttyAcmPorts.get(ttyAcmPorts.size() - 1);
    }

    /**
     * Queries the radio for basic present info, and sets the radio's fields.
     *
     * @return True if success, false if doesn't have permissions or not successful.
     */
    public boolean queryAndSet() {
        if (!usbManager.hasPermission(usbDevice)) {
            PendingIntent mPI = PendingIntent
                    .getBroadcast(context, 0, new Intent(ACTION), 0);
            usbManager.requestPermission(usbDevice, mPI);
            return false;
        }

        UsbSerialDriver driver = UsbSerialProber.getDefaultProber()
                .probeDevice(usbDevice);

        if (driver != null) {
            List<UsbSerialPort> usbSerialPortList = driver.getPorts();
            UsbSerialPort port = usbSerialPortList.get(0); //only use first port (console port)
            try {
                port.open(usbManager.openDevice(usbDevice));
                port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1,
                        UsbSerialPort.PARITY_NONE);

                write(port, HARRIS_GET_CURRENT_PRESET);
                String read = read(port);

                if (read.contains(PRESET_NAME_FIELD)) {
                    String newS = read
                            .substring(read.indexOf(PRESET_NAME_FIELD) +
                                    PRESET_NAME_FIELD.length());
                    newS = newS.substring(0, newS.indexOf("\r"));
                    presetName = newS;
                }

                if (read.contains(TX_FREQ_FIELD)) {
                    String newS = read.substring(read.indexOf(TX_FREQ_FIELD) +
                            TX_FREQ_FIELD.length());
                    newS = newS.substring(0, newS.indexOf("\r"));
                    frequency = newS.substring(0, newS.length() - 2);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to query Harris radio", e);
            }

            try {
                port.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close connection", e);
            }
        }

        return true;
    }

    /**
     * Returns the String representation of the object.
     *
     * @return String containing preset name and frequency
     */
    @NonNull
    @Override
    public String toString() {
        return presetName + "\n" + frequency;
    }

}
