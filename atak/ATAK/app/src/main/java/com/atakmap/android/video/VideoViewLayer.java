
package com.atakmap.android.video;

import android.graphics.Matrix;
import android.view.View;
import android.widget.RelativeLayout;

import com.atakmap.annotations.DeprecatedApi;
import com.partech.pgscmedia.MediaProcessor;
import com.partech.pgscmedia.frameaccess.DecodedMetadataItem;
import com.partech.pgscmedia.frameaccess.KLVData;
import com.partech.pgscmedia.frameaccess.MediaMetadataDecoder;

import java.util.Map;

/**
 * Representation of an Android Layer that would be dropped over the top of the Video View pane.
 */
public class VideoViewLayer {

    final String id;
    final View v;
    final RelativeLayout.LayoutParams rlp;
    final MetadataCallback mcb;

    private boolean alwaysOn;
    MediaMetadataDecoder decoder;
    MediaProcessor processor;
    ConnectionEntry entry;

    /**
     * This interface has been deprecated in favor of subclassing the same method.
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.2.1", forRemoval = true, removeAt = "4.5")
    public interface MetadataCallback {
        /**
         * The metadata change listener.  This supplies both decoded and raw data 
         * @param rawData The raw metadata wrapped in a KLVData object.
         * @param items the decoded values as ids and items.
         */
        void metadataChanged(KLVData rawData,
                Map<DecodedMetadataItem.MetadataItemIDs, DecodedMetadataItem> items);
    }

    /**
     * Called by core TAK when the video player has started to notify the video view listener that
     * the video processor has started for a connection entry.
     * @param processor the processor currently in use
     * @param decoder the decoder being used
     * @param entry the connection entry
     */
    final void init(final MediaProcessor processor,
            final MediaMetadataDecoder decoder,
            final ConnectionEntry entry) {
        this.processor = processor;
        this.decoder = decoder;
        this.entry = entry;
        start(entry);
    }

    /**
     * Enables more advanced structure decoding required by specifications such as VMTI.   When
     * a video is started, this is off by default.
     */
    public final void enableStructuredDecoding() {
        if (processor != null) {
            decoder.setStructuredItemDecodeEnabled(true);
        }
    }

    public void start(ConnectionEntry connectionEntry) {
    }

    /**
     * Called when the video is panned by the user.
     * @param x the new center point x value
     * @param y the new center point y value
     */
    public void setPan(final int x, final int y) {
    }

    /**
     * Called when the video is scaled by the user
     * @param scale the new scale value
     */
    public void setScale(final double scale) {
    }

    /**
     * Called when the video is panned or scaled by the user
     * @param matrix Matrix used to transform a view so it matches the video
     *        Note: Any overlay view MUST match the size of the entire video
     *        surface. Drawing code should treat the video as if it's stretched
     *        to cover the entire surface, since this matrix includes aspect
     *        ratio scaling and centering.
     */
    public void setViewMatrix(Matrix matrix) {
    }

    /**
     * Called when the video size has changed
     * @param w New video width
     * @param h New video height
     */
    public void videoSizeChanged(final int w, final int h) {
    }

    /**
     * The metadata change listener.  This supplies both decoded and raw data
     * @param rawData The raw metadata wrapped in a KLVData object.
     * @param items the decoded values as ids and items.
     */
    public void metadataChanged(final KLVData rawData,
            final Map<DecodedMetadataItem.MetadataItemIDs, DecodedMetadataItem> items) {
        if (mcb != null)
            mcb.metadataChanged(rawData, items);
    }

    /**
     * Create a video view layer as a set <id, view, layout, metadatacallback>.
     * @param id the unique identifier that describes the layer.
     * @param v the view that is to be displayed
     * @param rlp the layout parameters associated with the view.
     * @param mcb the metadata callback associated with the view.
     * This interface has been deprecated in favor of subclassing the same method.
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.2.1", forRemoval = true, removeAt = "4.5")
    public VideoViewLayer(final String id, final View v,
            final RelativeLayout.LayoutParams rlp, MetadataCallback mcb) {
        this.id = id;
        this.v = v;
        this.rlp = rlp;
        this.mcb = mcb;
        this.alwaysOn = true;
    }

    /**
     * Construct a Video View Layer with a provided identifier and the view to be drawn over the
     * top of the video being played.
     * @param id the id of the video view
     * @param v the view
     * @param rlp the layout parameters for the view
     * @param alwaysOn if the view needs to be explicitly defined in the layers intent string array
     *                 extra (false) or if it is always going to be shown (true).
     */
    public VideoViewLayer(final String id, final View v,
            final RelativeLayout.LayoutParams rlp,
            final boolean alwaysOn) {
        this(id, v, rlp, null);
        this.alwaysOn = alwaysOn;
    }

    /**
     * Construct a Video View Layer with a provided identifier and the view to be drawn over the
     * top of the video being played.
     * @param id the id of the video view
     * @param v the view
     * @param rlp the layout parameters for the view
     */
    public VideoViewLayer(final String id, final View v,
            final RelativeLayout.LayoutParams rlp) {
        this(id, v, rlp, true);
    }

    /**
     * Return true if the layer is always on .   In the case that the layer is not always on,
     * it will need to be explicity requested in order to be shown.  See the intent used to
     * launch the video with the extra intent.getStringArrayExtra("layers");
     * @return true if the layer is always on or false if the layer needs to be explitly mentioned
     * in the layers string array.
     */
    public boolean isAlwaysOn() {
        return alwaysOn;
    }
}
