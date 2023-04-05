
package com.atakmap.android.radiolibrary;

import com.atakmap.coremap.log.Log;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

/**
 * Base for objects that use port communications
 */
abstract class PortCommunicator {
    /**
     * Tag for logging
     */
    private static final String TAG = "PortCommunicator";

    private final byte[] drainbuf = new byte[1024];

    /**
     * Drains the port
     *
     * @param port the port to drain
     */
    void drain(final UsbSerialPort port) {
        int bread = 0;
        do {
            try {
                // do not use the values in the drain buffer
                bread = port.read(drainbuf, 100);
            } catch (IOException ioe) {
                Log.d(TAG, "", ioe);
            }
        } while (bread > 0);
    }

    /**
     * Reads the contents from the port
     *
     * @param port the port to read from
     * @return the contents as a string from the port
     */
    String read(final UsbSerialPort port) {
        StringBuilder returnString = new StringBuilder();
        int bread = 0;
        do {
            byte[] buf = new byte[1024];
            try {
                bread = port.read(buf, 100);
                for (byte b : buf) {
                    returnString.append(new String(new byte[] {
                            b
                    }));
                }
            } catch (IOException ioe) {
                Log.d(TAG, "", ioe);
            }
        } while (bread > 0);

        return returnString.toString();
    }

    /**
     * Writes the command to the given port
     *
     * @param port    the port to write to
     * @param command the command to send to the port
     * @throws IOException if writing fails.
     */
    void write(final UsbSerialPort port, String command) throws IOException {
        if (command == null) {
            return;
        }

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
