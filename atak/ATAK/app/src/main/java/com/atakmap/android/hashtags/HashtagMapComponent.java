
package com.atakmap.android.hashtags;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.hashtags.attachments.AttachmentHashtagListener;
import com.atakmap.android.hashtags.overlay.HashtagMapOverlay;
import com.atakmap.android.maps.MapView;

/**
 * Hashtags main component
 */
public class HashtagMapComponent extends DropDownMapComponent {

    protected HashtagMapOverlay _overlay;
    private StickyHashtags _stickyTagListener;
    private AttachmentHashtagListener _attListener;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);

        _overlay = new HashtagMapOverlay(view);
        view.getMapOverlayManager().addOverlay(_overlay);

        _stickyTagListener = new StickyHashtags(view);
        _attListener = new AttachmentHashtagListener(view);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);

        _stickyTagListener.dispose();
        _attListener.dispose();

        view.getMapOverlayManager().removeOverlay(_overlay);
        _overlay.dispose();
    }
}
