
package com.atakmap.coremap.filesystem;

import com.atakmap.coremap.log.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.UUID;

import android.os.SystemClock;

/**
 * Secure deletion thread
 */
public class SecureDeleteThread implements Runnable {

    private static final String TAG = "SecureDeleteThread";

    private final static int dataLength = 128 * 1024;
    private final static SecureRandom random = new SecureRandom();
    private final static byte[] data = new byte[dataLength];
    private static long lastfilled = 0;

    private final File _file;
    private final long _fileLength;
    private FileOutputStream _fos;
    private final String _uid;
    private final SecureDeleteEvent _event;

    static private void refill() {
        final long curr = SystemClock.elapsedRealtime();

        if (curr - lastfilled > 50) {
            lastfilled = curr;
            // random and possibly garbage because I am using a static array 
            random.nextBytes(data);
        }

    }

    SecureDeleteThread(final File file, final long fileLength,
            final FileOutputStream fos,
            final SecureDeleteEvent event) {
        _file = file;
        _fileLength = fileLength;
        _fos = fos;
        _uid = UUID.randomUUID().toString();
        _event = event;
        //Log.d(TAG, "Created new thread (" + _uid + ")");
    }

    public String getUID() {
        return _uid;
    }

    /**
     * Generates a random pattern for writing over the file to be deleted prior to deleting
     * the file.  Return true if it writes over the contents, false if it just did a java
     * delete.
     */
    @Override
    public void run() {
        //Log.d(TAG, "Running (" + _uid + ")");

        // Measuring performance
        long elapsed = System.nanoTime();

        boolean success = false;
        try {
            if (_fos == null)
                _fos = new FileOutputStream(_file.getAbsolutePath());

            long pos = 0;
            try {
                // Replace contents with random byte data
                while (pos < _fileLength) {
                    refill();
                    // this is guaranteed to be an int.
                    int left = (int) Math.min(dataLength, _fileLength - pos);
                    _fos.write(data, 0, left);
                    pos += dataLength;
                }
                _fos.flush();
            } catch (Exception e) {
                Log.w(TAG, e);
            } finally {
                try {
                    _fos.close();
                } catch (IOException ioe) {
                    // Avoid thread failure
                    Log.w(TAG, "file stream already closed.");
                }
                success = true;
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }

        // Elapsed time in nanoseconds
        elapsed = System.nanoTime() - elapsed;

        //Log.d(TAG, "finished (" + _uid + "): " + (success ? "SUCCESS" : "FAILED"));

        // Log elapsed time in milliseconds
        //if(success)
        //    Log.d(TAG, "elapsed (" + _file + "): " + elapsed/1000000.0 + "ms");

        SecureDelete.checkAndRemoveFromList(_uid);

        // Callback event
        if (_event != null)
            _event.onFinish(_file, success, elapsed);
    }
}
