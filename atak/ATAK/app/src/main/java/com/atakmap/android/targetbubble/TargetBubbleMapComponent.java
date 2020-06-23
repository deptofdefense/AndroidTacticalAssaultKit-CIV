
package com.atakmap.android.targetbubble;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

public class TargetBubbleMapComponent extends AbstractMapComponent {

    @Override
    public void onCreate(Context context, Intent intent, final MapView view) {
        DocumentedIntentFilter bubbleFilter = new DocumentedIntentFilter();
        bubbleFilter.addAction("com.atakmap.android.maps.FOCUS");
        bubbleFilter.addAction("com.atakmap.android.maps.UNFOCUS");
        bubbleFilter.addAction(TargetBubbleReceiver.FINE_ADJUST);
        bubbleFilter.addAction(TargetBubbleReceiver.MGRS_ENTRY);
        bubbleFilter.addAction(TargetBubbleReceiver.GENERAL_ENTRY);
        this.registerReceiver(context, new TargetBubbleReceiver(view),
                bubbleFilter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
    }
}
