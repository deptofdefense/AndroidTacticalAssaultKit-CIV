
package com.atakmap.android.video;

import com.atakmap.android.video.VideoMetadata;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.layer.AbstractLayer;

import android.graphics.SurfaceTexture;

public class VideoOverlayLayer extends AbstractLayer {

    private static final String TAG = "VideoOverlayLayer";

    private boolean enabled = false;

    private final GeoPoint upperLeft;
    private final GeoPoint upperRight;
    private final GeoPoint lowerRight;
    private final GeoPoint lowerLeft;

    private final VideoMetadata vmd;

    private VideoFrameListener listener;
    private SurfaceTexture tex;

    public VideoOverlayLayer(final String name, final VideoMetadata vmd) {
        super(name);

        this.upperLeft = GeoPoint.createMutable();
        this.upperRight = GeoPoint.createMutable();
        this.lowerRight = GeoPoint.createMutable();
        this.lowerLeft = GeoPoint.createMutable();
        this.vmd = vmd;
    }

    public void surfaceTextureReady(SurfaceTexture tex) {
        this.tex = tex;
        tex.setOnFrameAvailableListener(
                new SurfaceTexture.OnFrameAvailableListener() {

                    @Override
                    public void onFrameAvailable(
                            SurfaceTexture surfaceTexture) {
                        dispatch();
                        Log.i(TAG, "Overlay frame avail!");

                    }
                });
    }

    public SurfaceTexture getDestinationTexture() {
        return tex;
    }

    public synchronized void dispose() {
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        setVisible(enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void dispatchVideoFrameNoSync() {
        if (!enabled || this.listener == null
                || !vmd.hasFourCorners()) {
            if (isVisible())
                setVisible(false);

            return;
        } else {
            if (!isVisible())
                setVisible(true);
        }

        // obtain the corner coordinates
        upperLeft.set(vmd.corner1lat, vmd.corner1lon);
        upperRight.set(vmd.corner2lat, vmd.corner2lon);
        lowerRight.set(vmd.corner3lat, vmd.corner3lon);
        lowerLeft.set(vmd.corner4lat, vmd.corner4lon);

        // if we have a listener installed and we've received both metadata and
        // video, dispatch the frame (and the metadata has 4 corners filled in.
        if (this.listener != null && vmd.hasFourCorners())
            try {
                this.listener.videoFrame(this.upperLeft, this.upperRight,
                        this.lowerRight, this.lowerLeft);
            } catch (Exception ignored) {
            }
    }

    /**
     * Sets the current {@link VideoFrameListener}. If <code>null</code> the
     * current listener is cleared.
     * 
     * @param l A {@link VideoFrameListener} or <code>null</code>.
     */
    public void setVideoFrameListener(VideoFrameListener l) {
        this.listener = l;
    }

    /**
     * get the frame bounds for the image.   All of the GeoPoints passed in must be mutable.
     * @param ul upper left mutable geopoint
     * @param ur upper right mutable geopoint
     * @param lr lower right mutable geopoint
     * @param ll lower left mutable geopoint
     * @return false if there was an issue setting the geopoints, true if there was no issue.
     */
    public boolean getFrameBounds(GeoPoint ul,
            GeoPoint ur, GeoPoint lr, GeoPoint ll) {
        if (!ul.isMutable() || !ur.isMutable() || !lr.isMutable()
                || !ll.isMutable())
            return false;

        ul.set(this.upperLeft);
        ur.set(this.upperRight);
        lr.set(this.lowerRight);
        ll.set(this.lowerLeft);

        return (!Double.isNaN(ul.getLatitude()) && !Double.isNaN(ul
                .getLongitude()))
                &&
                (!Double.isNaN(ur.getLatitude()) && !Double.isNaN(ur
                        .getLongitude()))
                &&
                (!Double.isNaN(lr.getLatitude()) && !Double.isNaN(lr
                        .getLongitude()))
                &&
                (!Double.isNaN(ll.getLatitude()) && !Double.isNaN(ll
                        .getLongitude()));
    }

    public synchronized void dispatch() {
        this.dispatchVideoFrameNoSync();
    }

    public void dispatchSizeChange(int w, int h) {
        if (this.listener == null)
            return;
        listener.videoFrameSizeChange(w, h);
    }

    /**
     * Callback interface for receipt of frame with corner coordinates.
     */
    public interface VideoFrameListener {

        /**
         * The video frame callback function. The listener is supplied with the
         * frame's corner coordinates. The values
         * for any of the corner coordinates may be {@link Double#NaN},
         * indicating that the information is not available.
         * 
         * <P><B>IMPORTANT:</B> The object contents are only valid during
         * invocation of the callback; if any of the
         * coordinate objects are going to be used outside of the scope of the
         * callback copies must be created.
         * 
         * @param upperLeft     The coordinate associated with the upper-left
         *                      corner of the frame; may not be
         *                      <code>null</code>. Either or both latitude and
         *                      longitude may be {@link Double#NaN} if the data
         *                      is not available.
         * @param upperRight    The coordinate associated with the upper-right
         *                      corner of the frame; may not be
         *                      <code>null</code>. Either or both latitude and
         *                      longitude may be {@link Double#NaN} if the data
         *                      is not available.
         * @param lowerRight    The coordinate associated with the lower-right
         *                      corner of the frame; may not be
         *                      <code>null</code>. Either or both latitude and
         *                      longitude may be {@link Double#NaN} if the data
         *                      is not available.
         * @param lowerLeft     The coordinate associated with the lower-left
         *                      corner of the frame; may not be
         *                      <code>null</code>. Either or both latitude and
         *                      longitude may be {@link Double#NaN} if the data
         *                      is not available.
         */
        void videoFrame(
                GeoPoint upperLeft, GeoPoint upperRight, GeoPoint lowerRight,
                GeoPoint lowerLeft);

        /**
         * Inform listener that source video size has changed.
         * @param w video width
         * @param h video height
         */
        void videoFrameSizeChange(int w, int h);
    } // VideoFrameListener
} // VideoOverlayLayer
