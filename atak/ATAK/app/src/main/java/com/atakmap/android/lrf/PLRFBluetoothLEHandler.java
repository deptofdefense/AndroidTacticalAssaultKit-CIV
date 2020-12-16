
package com.atakmap.android.lrf;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.bluetooth.BtLowEnergyManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PLRFBluetoothLEHandler
        implements BtLowEnergyManager.BluetoothLEHandler {

    private static final String TAG = "PLRFBluetoothLEHandler";
    private final Context context;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice device;

    private boolean vD, vA, vE, vRecordId;
    private int recordId;
    private double d, a, e;

    // according to the documentation, all of the important uids end with the below identifier.
    private final static String IDENTIFIER = "A4E8-41A6-951E-C83048EDF1A6";

    private final BluetoothGattCallback gattCallback = new CustomBluetoothGattCallback();

    private final GenericLRFCallback callback;

    private BtLowEnergyManager.BluetoothLEConnectionStatus connectionStatus;

    PLRFBluetoothLEHandler(final Context context,
            final GenericLRFCallback callback) {
        this.context = context;
        this.callback = callback;
        final AtakBroadcast.DocumentedIntentFilter bondFilter = new AtakBroadcast.DocumentedIntentFilter(
                BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        AtakBroadcast.getInstance().registerSystemReceiver(searchDevices,
                bondFilter);
    }

    @Override
    public void setConnectionListener(
            BtLowEnergyManager.BluetoothLEConnectionStatus connectionStatus) {
        this.connectionStatus = connectionStatus;
    }

    @SuppressLint({
            "MissingPermission"
    })
    @Override
    public boolean onScanResult(final ScanResult result) {
        final BluetoothDevice d = result.getDevice();
        final int rssi = result.getRssi();
        ScanRecord sr = result.getScanRecord();
        final byte[] srb;
        if (sr != null)
            srb = sr.getBytes();
        else
            srb = new byte[0];

        final String name = d.getName();
        if (name != null && name.startsWith("PLRF")) {
            device = d;

            Log.d(TAG, device + " " + rssi + " " + Arrays.toString(srb));
            Log.d(TAG, "--> " + device.getName() + " " + device.getType());

            return true;
        }
        return false;
    }

    @SuppressLint({
            "MissingPermission"
    })
    @Override
    public void connect() {
        if (device == null) {
            Log.d(TAG,
                    "cannot connect, no device associated via a scan session");
            return;
        }

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "-----> device bonded already");
            // NOTES:
            // 1) don't make use of the autoConnect at this time, discussions on the intertubes seem to think this is broken on
            // many android devices and it is suggested to manually perform the connect after a disconnect.
            // 2) investigate the use of BluetoothDevice.TRANSPORT_LE
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
            if (bluetoothGatt == null) {
                Log.d(TAG, "error occurred during the gatt connection");
            }

        } else {
            if (device.createBond()) {
                Log.d(TAG, "-----> bonding started");
            } else {
                Log.d(TAG, "-----> bonding failed, is PLRF in Pairing mode?");
            }
        }
    }

    class CustomBluetoothGattCallback extends BluetoothGattCallback {

        List<BluetoothGattCharacteristic> chars = new ArrayList<>();
        int index = 0;

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onConnectionState: not successful");
            }
            Log.d(TAG, "onConnectionStateChange: " + status + " " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "starting gatt discover services");
                gatt.discoverServices();
            } else {
                Log.d(TAG, "reconnecting...");
                gatt.connect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServicesDiscovered: not successful");
            }

            final List<BluetoothGattService> gattServices = gatt.getServices();
            Log.d(TAG, "services #: " + gattServices.size());

            // clear the queue
            chars.clear();
            index = 0;

            for (BluetoothGattService gattService : gattServices) {
                if (gattService.getUuid().toString()
                        .toUpperCase(LocaleUtil.getCurrent())
                        .endsWith(IDENTIFIER)) {
                    Log.d(TAG,
                            "service: " + gattService.getUuid().toString() + " "
                                    +
                                    ((gattService.getType() == 0) ? "PRIMARY"
                                            : "SECONDARY"));

                    final List<BluetoothGattCharacteristic> gattCharacteristics = gattService
                            .getCharacteristics();

                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                        Log.d(TAG,
                                "\tcharacteristic (uuid): "
                                        + gattCharacteristic.getUuid()
                                                .toString()
                                        + " permission mask: "
                                        + gattCharacteristic.getPermissions()
                                        + " properties mask: "
                                        + gattCharacteristic.getProperties());
                        if ((gattCharacteristic.getProperties()
                                & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            gattCharacteristic.setWriteType(
                                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            gatt.setCharacteristicNotification(
                                    gattCharacteristic, true);
                            chars.add(gattCharacteristic);
                            List<BluetoothGattDescriptor> descriptors = gattCharacteristic
                                    .getDescriptors();
                            for (BluetoothGattDescriptor dp : descriptors) {
                                Log.d(TAG,
                                        "\tdescriptor (uuid): "
                                                + dp.getUuid().toString()
                                                + " permission mask: "
                                                + dp.getPermissions());
                                dp.setValue(
                                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                                gatt.writeDescriptor(dp);
                            }
                        } else if ((gattCharacteristic.getProperties()
                                & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                            Log.d(TAG,
                                    "\tcharacteristic (uuid): "
                                            + gattCharacteristic.getUuid()
                                                    .toString()
                                            + " write only");
                        } else if ((gattCharacteristic.getProperties()
                                & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            Log.d(TAG,
                                    "\tcharacteristic (uuid): "
                                            + gattCharacteristic.getUuid()
                                                    .toString()
                                            + " notification only");
                        } else if ((gattCharacteristic.getProperties()
                                & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                            Log.d(TAG,
                                    "\tcharacteristic (uuid): "
                                            + gattCharacteristic.getUuid()
                                                    .toString()
                                            + " indication only");
                        } else {
                            Log.d(TAG, "\tcharacteristic (uuid): "
                                    + gattCharacteristic.getUuid().toString()
                                    + " other");
                        }
                    }
                    gatt.setCharacteristicNotification(chars.get(index), true);
                    Log.d(TAG, "\tlistening for notification: "
                            + chars.get(index).getUuid().toString());
                }
            }
        }

        @Override
        public void onCharacteristicChanged(final BluetoothGatt gatt,
                final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            bluetoothGatt.readCharacteristic(chars.get(index));

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            //Log.d(TAG, "onCharacteristicWrite: " + gatt + " " + characteristic + " " + status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            final byte[] data = characteristic.getValue();
            set(characteristic.getUuid().toString(), data);

            if (index < chars.size()) {

                gatt.readCharacteristic(chars.get(index++));
            } else {
                index = 0;
                gatt.setCharacteristicNotification(chars.get(index), true);
                Log.d(TAG, "\tlistening for notification: "
                        + chars.get(index).getUuid().toString());
            }

        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            //Log.d(TAG, "onDescriptorRead: " + gatt + " " + descriptor.getUuid().toString() + " " + status + Arrays.toString(descriptor.getValue()));
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            //Log.d(TAG, "onDescriptorWrite: " + gatt + " " + descriptor.getUuid().toString() + " " + status + Arrays.toString(descriptor.getValue()));

        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            //Log.d(TAG, "onReliableWriteCompleted: " + gatt + " " + status);
        }
    }

    private void set(String uuid, byte[] data) {
        uuid = uuid.toUpperCase(LocaleUtil.getCurrent());
        if (uuid.startsWith("C564806C")) { // measurement identification value
            recordId = ByteBuffer.wrap(data, 0, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt();
            vRecordId = true;
        } else if (uuid.startsWith("C5648B10")) { // slope distance value
            if (data[4] != 0)
                d = Double.NaN;
            else
                d = (ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
                        .getInt() / 1000f);
            vD = true;
        } else if (uuid.startsWith("C564A281")) { // magnetic azimuth value
            if (data[4] != 0)
                a = Double.NaN;
            else
                a = toDegrees(((ByteBuffer.wrap(data, 0, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt()) / 10000f));
            vA = true;
        } else if (uuid.startsWith("C5640C4F")) { // elevation value
            if (data[4] != 0)
                e = Double.NaN;
            else
                e = toDegrees(((ByteBuffer.wrap(data, 0, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).getInt() - 31416)
                        / 10000f));
            vE = true;
        }
        if (vA && vD && vE && vRecordId) {

            Log.d(TAG, a + " " + d + " " + e);
            callback.onRangeFinderInfo(a, e, d);

            vA = vD = vE = vRecordId = false;

        }

    }

    private double toDegrees(double d) {
        return d * 180 / Math.PI;
    }

    @Override
    public void close() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
    }

    @Override
    public BluetoothDevice getDevice() {
        if (bluetoothGatt != null)
            return bluetoothGatt.getDevice();
        else
            return null;
    }

    @Override
    public void dispose() {
        close();
        AtakBroadcast.getInstance().unregisterSystemReceiver(searchDevices);
    }

    @SuppressLint({
            "MissingPermission"
    })
    private final BroadcastReceiver searchDevices = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                final String name = device.getName();
                if (name != null && name.startsWith("PLRF")) {
                    Log.d(TAG, "device is bonded: " + name);
                } else {
                    Log.d(TAG, "spurious bonding, ignore: " + name);
                    return;
                }
                Log.d(TAG, "bondState " + device.getBondState());
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "bond failed");
                        if (connectionStatus != null)
                            connectionStatus.pairingError(device);
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "bonding");
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "bonded");
                        // NOTES:
                        // 1) don't make use of the autoConnect at this time, discussions on the intertubes seem to think this is broken on
                        // many android devices and it is suggested to manually perform the connect after a disconnect.
                        // 2) investigate the use of BluetoothDevice.TRANSPORT_LE
                        bluetoothGatt = device.connectGatt(context, false,
                                gattCallback);
                        if (bluetoothGatt == null) {
                            Log.d(TAG,
                                    "error occurred during the gatt connection");
                        }
                        break;

                }
            }

        }

    };
}
