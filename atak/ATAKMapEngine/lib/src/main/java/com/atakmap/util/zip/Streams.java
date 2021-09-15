/*
 * Internally implemented support class fulfilling contract for imported Android sources.
 */

package com.atakmap.util.zip;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

final class Streams {
    private Streams() {
    }

    public static void readFully(InputStream stream, byte[] arr, int off,
            int len) throws IOException {
        while (len > 0) {
            int numRead = stream.read(arr, off, len);
            if (numRead < 0)
                throw new EOFException();
            off += numRead;
            len -= numRead;
        }
    }

    public static int readSingleByte(InputStream rafStream) throws IOException {
        byte[] buf = new byte[1];
        final int numRead = rafStream.read(buf);
        if (numRead < 1)
            return -1;
        return buf[0] & 0xFF;
    }
}
