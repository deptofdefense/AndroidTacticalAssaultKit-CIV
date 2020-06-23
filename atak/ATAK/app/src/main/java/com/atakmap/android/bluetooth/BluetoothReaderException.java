
package com.atakmap.android.bluetooth;

public class BluetoothReaderException extends Exception {

    private static final long serialVersionUID = -8285051930806217578L;

    public BluetoothReaderException(String detailMessage) {
        super(detailMessage);
    }

    public BluetoothReaderException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

}
