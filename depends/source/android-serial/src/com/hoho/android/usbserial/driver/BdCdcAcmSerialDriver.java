/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.hoho.android.usbserial.util.HexDump;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * USB CDC/ACM serial driver implementation.
 *
 * @author mike wakerly (opensource@hoho.com)
 * @see <a
 * href="http://www.usb.org/developers/devclass_docs/usbcdc11.pdf">Universal
 * Serial Bus Class Definitions for Communication Devices, v1.1</a>
 */
public class BdCdcAcmSerialDriver implements UsbSerialDriver {

    private final String TAG = BdCdcAcmSerialDriver.class.getSimpleName();

    private final UsbDevice mDevice;
    private List<UsbSerialPort> mPorts;

    //    private final UsbSerialPort mPort;

    public BdCdcAcmSerialDriver(UsbDevice device) {
        mDevice = device;
        //mPort = new BdCdcAcmSerialPort(device, 0);
    }

    @Override
    public UsbDevice getDevice() {
        return mDevice;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        if (mPorts == null) {
            synchronized (this) {
                if (mPorts == null) {
                    mPorts = enumeratePorts();
                }
            }
        }

        return mPorts;
    }

    private List<UsbSerialPort> enumeratePorts() {
        int count = mDevice.getInterfaceCount();
        //        Log.d("wtf", "ifc:" + count);

        List<UsbSerialPort> ports = new ArrayList<UsbSerialPort>();

        int i = 0;
        while (i < count - 1) {
            UsbInterface iface = mDevice.getInterface(i);
            // first interface must be COMM, or a control interface for the serial port
            if (iface.getInterfaceClass() != UsbConstants.USB_CLASS_COMM) {
                i += 1;
                continue;
            }

            iface = mDevice.getInterface(i + 1);
            // second interface must be CDC_DATA
            if (iface.getInterfaceClass() != UsbConstants.USB_CLASS_CDC_DATA) {
                i += 1;
                continue;
            }

            // we have a serial port!
            ports.add(new BdCdcAcmSerialPort(mDevice, i));

            i += 2;
        }

        return ports;
    }

    class BdCdcAcmSerialPort extends CommonUsbSerialPort {

        private final boolean mEnableAsyncReads;
        private UsbInterface mControlInterface;
        private UsbInterface mDataInterface;

        private UsbEndpoint mControlEndpoint;
        private UsbEndpoint mReadEndpoint;
        private UsbEndpoint mWriteEndpoint;

        private boolean DEBUG = false;

        private boolean mRts = false;
        private boolean mDtr = false;

        private static final int USB_RECIP_INTERFACE = 0x01;
        private static final int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS
                | USB_RECIP_INTERFACE;

        private static final int SET_LINE_CODING = 0x20; // USB CDC 1.1 section 6.2
        private static final int GET_LINE_CODING = 0x21;
        private static final int SET_CONTROL_LINE_STATE = 0x22;
        private static final int SEND_BREAK = 0x23;

        public BdCdcAcmSerialPort(UsbDevice device, int portNumber) {
            super(device, portNumber);
            //            mEnableAsyncReads = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1);
            mEnableAsyncReads = false;
        }

        @Override
        public UsbSerialDriver getDriver() {
            return BdCdcAcmSerialDriver.this;
        }

        //        public UsbDeviceConnection getConnection() { return mConnection; }

        @Override
        public void open(UsbDeviceConnection connection) throws IOException {
            if (mConnection != null) {
                throw new IOException("Already open");
            }

            mConnection = connection;
            boolean opened = false;
            try {
                Log.v(TAG,
                        "claiming interfaces, count="
                                + mDevice.getInterfaceCount());
                mControlInterface = mDevice.getInterface(mPortNumber);
                if (DEBUG)
                    Log.d(TAG, "Control iface=" + mControlInterface);
                // class should be USB_CLASS_COMM

                if (!mConnection.claimInterface(mControlInterface, true)) {
                    throw new IOException("Could not claim control interface.");
                }
                if (DEBUG)
                    Log.d(TAG,
                            String.format("i1epc: %d",
                                    mControlInterface.getEndpointCount()));
                mControlEndpoint = mControlInterface.getEndpoint(0);
                if (DEBUG)
                    Log.d(TAG, "Control endpoint direction: "
                            + mControlEndpoint.getDirection());

                if (DEBUG)
                    Log.d(TAG, "Claiming data interface.");
                mDataInterface = mDevice.getInterface(mPortNumber + 1);
                if (DEBUG)
                    Log.d(TAG, "data iface=" + mDataInterface);
                // class should be USB_CLASS_CDC_DATA

                if (!mConnection.claimInterface(mDataInterface, true)) {
                    throw new IOException("Could not claim data interface.");
                }
                if (DEBUG)
                    Log.d(TAG,
                            String.format("i2epc: %d",
                                    mDataInterface.getEndpointCount()));
                mReadEndpoint = mDataInterface.getEndpoint(1);
                if (DEBUG)
                    Log.d(TAG,
                            "Read endpoint direction: "
                                    + mReadEndpoint.getDirection());
                mWriteEndpoint = mDataInterface.getEndpoint(0);
                if (DEBUG)
                    Log.d(TAG,
                            "Write endpoint direction: "
                                    + mWriteEndpoint.getDirection());
                if (mEnableAsyncReads) {
                    Log.v(TAG, "Async reads enabled");
                } else {
                    Log.v(TAG, "Async reads disabled.");
                }
                opened = true;
            } finally {
                if (!opened) {
                    mConnection = null;
                }
            }
        }

        private int sendAcmControlMessage(int request, int value, byte[] buf) {
            String sbuf = buf != null ? HexDump.dumpHexString(buf) : "null";
            int len = buf != null ? buf.length : 0;

            if (DEBUG)
                Log.d(TAG, String.format("sending 0x%x,0x%x,0x%x,0x%x,%d; %s",
                        USB_RT_ACM, request, value, mPortNumber, len, sbuf));
            return mConnection.controlTransfer(
                    USB_RT_ACM, request, value, mPortNumber, buf, len, 5000);
        }

        @Override
        public void close() throws IOException {
            if (mConnection == null) {
                throw new IOException("Already closed");
            }
            mConnection.releaseInterface(mControlInterface);
            mConnection.releaseInterface(mDataInterface);
            mConnection.close();
            mConnection = null;
        }

        @Override
        public int read(byte[] dest, int timeoutMillis) throws IOException {
            if (mEnableAsyncReads) {
                final UsbRequest request = new UsbRequest();
                try {
                    request.initialize(mConnection, mReadEndpoint);
                    final ByteBuffer buf = ByteBuffer.wrap(dest);
                    if (!request.queue(buf, dest.length)) {
                        throw new IOException("Error queueing request.");
                    }

                    final UsbRequest response = mConnection.requestWait();
                    if (response == null) {
                        throw new IOException("Null response");
                    }

                    final int nread = buf.position();
                    if (nread > 0) {
                        //Log.d(TAG, HexDump.dumpHexString(dest, 0, Math.min(32, dest.length)));
                        return nread;
                    } else {
                        return 0;
                    }
                } finally {
                    request.close();
                }
            }

            final int numBytesRead;
            synchronized (mReadBufferLock) {
                final long rtime = System.currentTimeMillis();
                int readAmt = Math.min(dest.length, mReadBuffer.length);
                numBytesRead = mConnection.bulkTransfer(mReadEndpoint,
                        mReadBuffer, readAmt,
                        timeoutMillis);
                if (numBytesRead < 0) {
                    // This sucks: we get -1 on timeout, not 0 as preferred.
                    // We *should* use UsbRequest, except it has a bug/api oversight
                    // where there is no way to determine the number of bytes read
                    // in response :\ -- http://b.android.com/28023

                    // when the device is disconnected, bulkTransfer returns -1 instantly
                    // preventing us from determining if its a timeout or error, so we need
                    // to check if time has actually passed (otherwise we spinlock and android
                    // might reboot {on S6})
                    final long delta = System.currentTimeMillis() - rtime;
                    if (delta < 100) {
                        if (DEBUG)
                            Log.e(TAG, "error: got error reading" + delta);
                        SystemClock.sleep(timeoutMillis - delta);
                        return -1;
                    }
                    if (timeoutMillis == Integer.MAX_VALUE) {
                        // Hack: Special case "~infinite timeout" as an error.
                        return -1;
                    }
                    return 0;
                }
                if (numBytesRead > 0)
                    System.arraycopy(mReadBuffer, 0, dest, 0, numBytesRead);
            }
            return numBytesRead;
        }

        @Override
        public int write(byte[] src, int timeoutMillis) throws IOException {
            // TODO(mikey): Nearly identical to FtdiSerial write. Refactor.
            int offset = 0;

            while (offset < src.length) {
                final int writeLength;
                final int amtWritten;

                synchronized (mWriteBufferLock) {
                    final byte[] writeBuffer;

                    writeLength = Math.min(src.length - offset,
                            mWriteBuffer.length);
                    if (offset == 0) {
                        writeBuffer = src;
                    } else {
                        // bulkTransfer does not support offsets, make a copy.
                        System.arraycopy(src, offset, mWriteBuffer, 0,
                                writeLength);
                        writeBuffer = mWriteBuffer;
                    }

                    amtWritten = mConnection.bulkTransfer(mWriteEndpoint,
                            writeBuffer, writeLength,
                            timeoutMillis);

                    //                    Log.d(TAG, HexDump.dumpHexString(writeBuffer));

                }
                if (amtWritten <= 0) {
                    throw new IOException("Error writing " + writeLength
                            + " bytes at offset " + offset + " length="
                            + src.length);
                }

                //                Log.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
                offset += amtWritten;
            }
            return offset;
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits,
                int parity) {
            byte stopBitsByte;
            switch (stopBits) {
                case STOPBITS_1:
                    stopBitsByte = 0;
                    break;
                case STOPBITS_1_5:
                    stopBitsByte = 1;
                    break;
                case STOPBITS_2:
                    stopBitsByte = 2;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Bad value for stopBits: " + stopBits);
            }

            byte parityBitesByte;
            switch (parity) {
                case PARITY_NONE:
                    parityBitesByte = 0;
                    break;
                case PARITY_ODD:
                    parityBitesByte = 1;
                    break;
                case PARITY_EVEN:
                    parityBitesByte = 2;
                    break;
                case PARITY_MARK:
                    parityBitesByte = 3;
                    break;
                case PARITY_SPACE:
                    parityBitesByte = 4;
                    break;
                default:
                    throw new IllegalArgumentException("Bad value for parity: "
                            + parity);
            }

            byte[] msg = {
                    (byte) (baudRate & 0xff),
                    (byte) ((baudRate >> 8) & 0xff),
                    (byte) ((baudRate >> 16) & 0xff),
                    (byte) ((baudRate >> 24) & 0xff),
                    stopBitsByte,
                    parityBitesByte,
                    (byte) dataBits
            };
            sendAcmControlMessage(SET_LINE_CODING, 0, msg);
        }

        @Override
        public boolean getCD() throws IOException {
            return false; // TODO
        }

        @Override
        public boolean getCTS() throws IOException {
            return false; // TODO
        }

        @Override
        public boolean getDSR() throws IOException {
            return false; // TODO
        }

        @Override
        public boolean getDTR() throws IOException {
            return mDtr;
        }

        @Override
        public void setDTR(boolean value) throws IOException {
            mDtr = value;
            setDtrRts();
        }

        @Override
        public boolean getRI() throws IOException {
            return false; // TODO
        }

        @Override
        public boolean getRTS() throws IOException {
            return mRts;
        }

        @Override
        public void setRTS(boolean value) throws IOException {
            mRts = value;
            setDtrRts();
        }

        private void setDtrRts() {
            int value = (mRts ? 0x2 : 0) | (mDtr ? 0x1 : 0);
            sendAcmControlMessage(SET_CONTROL_LINE_STATE, value, null);
        }

        public void setDtrRts(boolean dtr, boolean rts) {
            mDtr = dtr;
            mRts = rts;
            setDtrRts();
        }

    }

    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();

        supportedDevices.put(Integer.valueOf(UsbId.VENDOR_BLACKDIAMOND),
                new int[] {
                        UsbId.BD_SERIAL, UsbId.BD_SERIAL_LEGACY, UsbId.BD_SERIAL_2
                });

        return supportedDevices;
    }

}
