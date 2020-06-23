
package com.atakmap.android.track;

/**
 *
 */
public interface TrackChangedListener {

    /**
     * Notify that the track has changed
     *
     * @param track the track that has changed.
     */
    void onTrackChanged(TrackDetails track);
}
