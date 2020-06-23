
package com.atakmap.android.video;

import android.view.View;
import android.widget.RelativeLayout;
import com.partech.pgscmedia.frameaccess.DecodedMetadataItem;
import com.partech.pgscmedia.frameaccess.KLVData;

import java.util.Map;

/**
 * Representation of an Android Layer that would be dropped over the top of the Video View pane.
 */
public class VideoViewLayer {

    final String id;
    final View v;
    final RelativeLayout.LayoutParams rlp;
    final MetadataCallback mcb;

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
     * Create a video view layer as a set <id, view, layout, metadatacallback>.
     * @param id the unique identifier that describes the layer.
     * @param v the view that is to be displayed
     * @param rlp the layout parameters associated with the view.
     * @param mcb the metadata callback associated with the view.
     */
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
