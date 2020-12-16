
package com.atakmap.android.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.atakmap.coremap.log.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.Arrays;

public class BluetoothBinaryClientConnection extends BluetoothClientConnection {

    private static final String TAG = "BluetoothBinaryClientConnection";

    // for the blocking mechanism (only one can be active)
    private BufferedInputStream bis = null;
    private final byte[] buffer = new byte[8092];

    public BluetoothBinaryClientConnection(
            final BluetoothDevice device, final UUID uuid) {
        super(device, uuid);
    }

    /**
     * Reads a block of data
     * 
     * @return a byte array containing the contents. 
     */
    @Override
    protected byte[] read() throws IOException {
        if (bis == null) {
            bis = new BufferedInputStream(socket.getInputStream());
        }

        try {
            int len = bis.read(buffer, 0, buffer.length);
            if (len != -1) {
                return Arrays.copyOfRange(buffer, 0, len);
            }
            throw new IOException("error reading the line");
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    protected void onStop() throws IOException {
        Log.d(TAG, "call to onStop for: " + name);
        super.onStop();

        // this will block if the socket is still open, not sure why on my
        // android os.
        if (bis != null) {
            try {
                bis.close();
            } catch (Exception e) {
                Log.w(TAG, "Failed to close buffered reader", e);
            }

            bis = null;
        }

    }
}
