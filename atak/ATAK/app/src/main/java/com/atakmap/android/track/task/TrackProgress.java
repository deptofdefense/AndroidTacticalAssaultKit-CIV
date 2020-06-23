
package com.atakmap.android.track.task;

/**
 * Interface for progress reporting from async tasks
 */
public interface TrackProgress {
    void onProgress(int p);

    boolean cancelled();
}
