
package com.atakmap.android.radiolibrary;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.preference.PreferenceManager;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DefaultIOProvider;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Harris SA Radio Manager for querying the devices and getting radio info.
 */
class HarrisSaRadioManager {
    /**
     * Tag for logging
     */
    private static final String TAG = "HarrisSaRadioManager";

    /**
     * The DEVNAME property key for the uevent file
     */
    private static final String DEV_NAME_PROPERTY_KEY = "DEVNAME";

    /**
     * The Harris radio product ID
     */
    private static final String HARRIS_PRODUCT_ID = "19a5/4/0";

    private static final String PREFERENCE_KEY = "selectedHarrisSaDevice";

    /**
     * The Android USB manager
     */
    private final UsbManager usbManager;
    /**
     * Listeners for {@link RadiosQueriedListener}
     */
    private final List<RadiosQueriedListener> listeners = new ArrayList<>();

    /**
     * Holds the radios that were found with ports
     */
    private final Map<Integer, HarrisRadio> radios = new HashMap<>();

    /**
     * the shared preferences
     */
    private final SharedPreferences sharedPreferences;

    /**
     * The radio to use for PPP
     */
    private HarrisRadio selectedRadio;

    private final Context context;

    /**
     * The receiver for USB device permissions
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                if (action.equals(HarrisRadio.ACTION)) {
                    UsbDevice dev = intent
                            .getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    final boolean granted = intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (dev != null && granted) {
                        boolean hasPermissions = true;
                        for (HarrisRadio radio : radios.values()) {
                            if (radio.hasPermission()) {
                                if (!radio.valuesQueriedAndSet()) {
                                    radio.queryAndSet();
                                }
                                continue;
                            }

                            boolean per = radio.queryAndSet();
                            hasPermissions &= per;
                        }

                        if (hasPermissions) {
                            notifyListeners();
                        }
                    } else {
                        Log.d(TAG, "need permission to get data");
                    }
                }
            }
        }
    };

    HarrisSaRadioManager(Context context) {
        this.context = context;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        AtakBroadcast
                .getInstance()
                .registerSystemReceiver(receiver,
                        new AtakBroadcast.DocumentedIntentFilter(
                                HarrisRadio.ACTION));

        String deviceName = sharedPreferences.getString(PREFERENCE_KEY, null);
        if (deviceName != null && pppConnectionExists()) {
            UsbDevice harrisDevice = usbManager.getDeviceList().get(deviceName);
            selectedRadio = new HarrisRadio(context, usbManager, harrisDevice);
        }
    }

    /**
     * Adds a listener for when all radios are queried
     *
     * @param listener The listener to add
     */
    public synchronized void addListener(RadiosQueriedListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a listener for when all radios are queried
     *
     * @param listener The listener to remove
     */
    public synchronized void removeListener(RadiosQueriedListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all listeners with the found radios
     */
    private synchronized void notifyListeners() {
        for (RadiosQueriedListener listener : listeners) {
            listener.radiosQueried(new ArrayList<>(radios.values()));
        }
    }

    /**
     * Loads the radios map with {@link HarrisRadio} objects based on what is currently connected
     * to this android device.
     *
     * @throws IOException if {@link FileSystemUtils#copyStreamToString(File, IOProvider)} fails.
     */
    synchronized void queryRadios() throws IOException {
        radios.clear();

        //Go through every surface level device, and check the uevent for DEVNAME
        File devices = new File("/sys/bus/usb/devices");
        File[] devicesList = devices.listFiles();
        if (devicesList != null) {
            for (File device : devicesList) {
                File uevent = new File(device, "uevent");
                if (uevent.exists()) {
                    String contents = FileSystemUtils.copyStreamToString(uevent,
                            new DefaultIOProvider());
                    //Check if the uevent has a DEVNAME (That means it is a device and not just
                    // an end point or sub device). Also check if it's a Harris radio.
                    if (contents.contains(DEV_NAME_PROPERTY_KEY) && contents
                            .contains(HARRIS_PRODUCT_ID)) {
                        //Cut the contents down to just the device path
                        contents = contents.substring(
                                contents.indexOf(DEV_NAME_PROPERTY_KEY)
                                        + DEV_NAME_PROPERTY_KEY.length() + 1);
                        contents = contents.substring(0,
                                contents.indexOf("\n"));
                        String deviceName = "/dev/" + contents;

                        //get or create the radio
                        HarrisRadio radio = null;
                        UsbDevice usbDevice = usbManager.getDeviceList()
                                .get(deviceName);
                        if (usbDevice != null) {
                            int deviceId = usbDevice.getDeviceId();
                            radio = radios.get(deviceId);
                            if (radio == null) {
                                radio = new HarrisRadio(context, usbManager,
                                        usbDevice);
                                radios.put(deviceId, radio);
                            }
                        }

                        if (radio != null) {
                            //Get all sub directories
                            File[] subDirFiles = device.listFiles();
                            if (subDirFiles != null) {
                                for (File subDir : subDirFiles) {
                                    File ttyAcmFile = new File(subDir, "tty");
                                    //If they do have tty, add them to the radio
                                    if (ttyAcmFile.exists()
                                            && ttyAcmFile.isDirectory()) {
                                        File[] ttyAcmFileList = ttyAcmFile
                                                .listFiles();
                                        if (ttyAcmFileList != null) {
                                            for (File ttyAcmPort : ttyAcmFileList) {
                                                String name = ttyAcmPort
                                                        .getName();
                                                name = name.substring(name
                                                        .indexOf("ttyACM")
                                                        + "ttyACM".length());
                                                int port = MathUtils
                                                        .parseInt(name, -1);
                                                if (port != -1) {
                                                    radio.addTtyAcmPort(port);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //If we have more than one radio and the ppp connection doesn't exist,
            //then query for basic radio details.
            if (radios.size() > 1 && !pppConnectionExists()) {
                boolean hasPermissions = true;
                for (HarrisRadio radio : radios.values()) {
                    boolean per = radio.queryAndSet();
                    hasPermissions &= per;
                }

                if (hasPermissions) {
                    notifyListeners();
                }
            } else {
                notifyListeners();
            }
        }
    }

    void dispose() {
        try {
            AtakBroadcast.getInstance().unregisterSystemReceiver(receiver);
        } catch (Exception ignored) {
        }
    }

    /**
     * Checks if ppp connection already exists.
     *
     * @return True if ppp0 exists, false otherwise.
     */
    boolean pppConnectionExists() {
        try {
            NetworkInterface ni = NetworkInterface.getByName("ppp0");
            return ni != null && ni.isUp();
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Gets the selected Harris SA radio
     *
     * @return the selected Harris SA radio
     */
    public HarrisRadio getSelectedRadio() {
        return selectedRadio;
    }

    /**
     * Sets the selected Harris SA radio, and saves the device name to shared prefs
     *
     * @param selectedRadio The newly selected radio.
     */
    public void setSelectedRadio(HarrisRadio selectedRadio) {
        this.selectedRadio = selectedRadio;
        if (selectedRadio != null && selectedRadio.getUsbDevice() != null) {
            sharedPreferences.edit().putString(PREFERENCE_KEY,
                    selectedRadio.getUsbDevice().getDeviceName()).apply();
        }
    }
}
