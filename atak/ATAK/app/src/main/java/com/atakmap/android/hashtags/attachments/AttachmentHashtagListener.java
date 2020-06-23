
package com.atakmap.android.hashtags.attachments;

import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.AttachmentWatcher;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class AttachmentHashtagListener implements AttachmentWatcher.Listener,
        HashtagManager.OnUpdateListener {

    private final MapView _mapView;
    private final Map<File, AttachmentContent> _contentMap = new HashMap<>();

    public AttachmentHashtagListener(MapView mapView) {
        _mapView = mapView;
        AttachmentWatcher.getInstance().addListener(this);
        HashtagManager.getInstance().registerUpdateListener(this);

        for (File f : AttachmentWatcher.getInstance().getCache())
            onAttachmentAdded(f);
    }

    public void dispose() {
        AttachmentWatcher.getInstance().removeListener(this);
        HashtagManager.getInstance().unregisterUpdateListener(this);
    }

    @Override
    public void onAttachmentAdded(File attFile) {
        AttachmentContent content;
        synchronized (_contentMap) {
            content = _contentMap.get(attFile);
            if (content != null)
                return;
            _contentMap.put(attFile, content = new AttachmentContent(
                    _mapView, attFile));
            content.readHashtags();
        }
        HashtagManager.getInstance().registerContent(content);
    }

    @Override
    public void onAttachmentRemoved(File attFile) {
        AttachmentContent content;
        synchronized (_contentMap) {
            content = _contentMap.remove(attFile);
        }
        if (content != null)
            HashtagManager.getInstance().unregisterContent(content);
    }

    @Override
    public void onHashtagsUpdate(HashtagContent content) {
        if (content instanceof AttachmentContent)
            ((AttachmentContent) content).readHashtags();
    }
}
