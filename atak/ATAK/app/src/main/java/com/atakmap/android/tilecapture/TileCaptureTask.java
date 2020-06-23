
package com.atakmap.android.tilecapture;

import android.os.AsyncTask;

/**
 * Async task for capturing map tiles
 * {@link TileCapture#capture(TileCaptureParams, TileCapture.Callback)} cannot
 * (and should not) be run on the main thread, so this task serves as a base
 * for invocation
 */
public abstract class TileCaptureTask
        extends AsyncTask<TileCaptureParams, Integer, Boolean>
        implements TileCapture.Callback {

    protected final TileCapture _tileCapture;

    public TileCaptureTask(TileCapture capture) {
        _tileCapture = capture;
    }

    @Override
    public Boolean doInBackground(TileCaptureParams... params) {
        // Begin the capture
        _tileCapture.capture(params[0], this);

        // Finished
        return true;
    }
}
