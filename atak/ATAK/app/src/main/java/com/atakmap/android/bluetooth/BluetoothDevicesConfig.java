
package com.atakmap.android.bluetooth;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * List of supported Bluetooth Device Configurations
 * 
 * 
 */
@Root(name = "devices")
public class BluetoothDevicesConfig {

    private static final String TAG = "BluetoothDevicesConfig";

    public static final String DIRNAME = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "bluetooth";
    static final String CONFIG_FILE_PATH = DIRNAME + File.separatorChar
            + "bluetooth_devices.xml";
    static final String DEFAULT_BLUETOOTH_CONFIG = "bluetooth/default_bluetooth_devices.xml";
    public static final String VERSION = "v2";

    @Attribute(required = false)
    private String version;

    public String getVersion() {
        return version;
    }

    /**
     * Bluetooth Device Configuration
     * 
     * 
     */
    static class BluetoothDeviceConfig {

        @Attribute(name = "name", required = true)
        private String name;

        @Attribute(name = "readerClass", required = true)
        private String readerClass;

        public String getName() {
            return name;
        }

        public String getReaderClass() {
            return readerClass;
        }

        public boolean isValid() {
            return !FileSystemUtils.isEmpty(name)
                    && !FileSystemUtils.isEmpty(readerClass);
        }
    }

    @ElementList(entry = "device", inline = true, required = false)
    private List<BluetoothDeviceConfig> devices;

    public boolean hasDevices() {
        return devices != null && devices.size() > 0;
    }

    public List<BluetoothDeviceConfig> getDevices() {
        if (!hasDevices())
            return new ArrayList<>();

        return Collections.unmodifiableList(devices);
    }

    /**
     * Get the list of configured devices from file
     * 
     * @return the list of configured devices
     */
    public static BluetoothDevicesConfig getConfiguredDevices() {
        File config = FileSystemUtils.getItem(CONFIG_FILE_PATH);

        Serializer serializer = new Persister();
        try (FileInputStream fis = IOProviderFactory.getInputStream(config)) {
            BluetoothDevicesConfig contents = serializer.read(
                    BluetoothDevicesConfig.class, fis);
            Log.d(TAG, "Loading Bluetooth Devices Config version "
                    + contents.version
                    + ", from file: "
                    + config.getAbsolutePath());
            return contents;
        } catch (Exception e) {
            Log.e(TAG,
                    "Failed to load Supported Bluetooth Devices from: "
                            + config.getAbsolutePath(),
                    e);
            return null;
        }
    }

    public static BluetoothDevicesConfig getConfiguredDevices(String xml) {
        Serializer serializer = new Persister();
        try {
            BluetoothDevicesConfig data = serializer.read(
                    BluetoothDevicesConfig.class, xml);
            Log.d(TAG, "Loading Bluetooth Config version " + data.version
                    + ", from xml of length "
                    + xml.length());
            return data;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Bluetooth Config from xml", e);
            return null;
        }
    }

    public boolean save() {
        File config = FileSystemUtils.getItem(CONFIG_FILE_PATH);
        Log.d(TAG,
                "Saving Bluetooth Config to file: " + config.getAbsolutePath()
                        + ", contents=" + this);

        Serializer serializer = new Persister();
        try (FileOutputStream fos = IOProviderFactory.getOutputStream(config)) {
            serializer.write(this, fos);
            return true;
        } catch (Exception e) {
            Log.e(TAG,
                    "Failed to write Bluetooth Config to: "
                            + config.getAbsolutePath(),
                    e);
            return false;
        }
    }
}
