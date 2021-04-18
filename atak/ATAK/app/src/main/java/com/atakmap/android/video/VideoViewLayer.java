
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
    final void init(final MediaProcessor processor, final MediaMetadataDecoder decoder,
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

    public void start(ConnectionEntry connectionEntry)  { }


    /**
     * Called when the video is panned by the user.
     * @param x the new center point x value
     * @param y the new center point y value
     */
    public void setPan(final int x, final int y) { }

    /**
     * Called when the video is scaled by the user
     * @param scale the new scale value
     */
    public void setScale(final double scale) { }

    /**
     * Called when the video is panned or scaled by the user
     * @param matrix Matrix used to transform a view so it matches the video
     *        Note: Any overlay view MUST match the size of the entire video
     *        surface. Drawing code should treat the video as if it's stretched
     *        to cover the entire surface, since this matrix includes aspect
     *        ratio scaling and centering.
     */
    public void setViewMatrix(Matrix matrix) { }

    /**
     * Called when the video size has changed
     * @param w New video width
     * @param h New video height
     */
    public void videoSizeChanged(final int w, final int h) { }

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
    }

    public VideoViewLayer(final String id, final View v,
            final RelativeLayout.LayoutParams rlp) {
        this(id, v, rlp, null);
    }
}
