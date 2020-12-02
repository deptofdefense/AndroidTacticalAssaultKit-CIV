
package com.atakmap.android.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

public class BluetoothASCIIClientConnection extends BluetoothClientConnection {

    private static final String TAG = "BluetoothASCIIClientConnection";

    // for the blocking mechanism (only one can be active)
    private BufferedReader bufferedreader = null;

    public BluetoothASCIIClientConnection(
            final BluetoothDevice device, final UUID uuid) {
        super(device, uuid);
    }

    /**
     * Reads a line of text. A line is considered to be terminated by any one of a line feed ('\n'),
     * a carriage return ('\r'), or a carriage return followed immediately by a line feed.
     * 
     * @return a String containing the contents of the line, not including any line-termination
     *         characters, or null if the end of the stream has been reached
     */
    @Override
    protected byte[] read() throws IOException {
        if (bufferedreader == null) {
            bufferedreader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(),
                    FileSystemUtils.UTF8_CHARSET));
        }

        try {
            String value = bufferedreader.readLine();
            if (value == null)
                throw new IOException("error reading the line");
            return value.getBytes();
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
        if (bufferedreader != null) {
            try {
                bufferedreader.close();
            } catch (Exception e) {
                Log.w(TAG, "Failed to close buffered reader", e);
            }

            bufferedreader = null;
        }

    }
}
