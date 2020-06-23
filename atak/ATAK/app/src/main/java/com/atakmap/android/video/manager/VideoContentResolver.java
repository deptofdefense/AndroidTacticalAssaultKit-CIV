
package com.atakmap.android.video.manager;

import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.ConnectionEntry;

import java.io.File;

public class VideoContentResolver extends FileContentResolver
        implements VideoManager.Listener {

    private final MapView _mapView;

    public VideoContentResolver(MapView mapView) {
        super(VideoFileWatcher.VIDEO_EXTS);
        _mapView = mapView;
    }

    @Override
    public void onEntryAdded(ConnectionEntry entry) {
        File file = entry.getLocalFile();
        if (file != null)
            addHandler(new VideoContentHandler(_mapView, entry));
    }

    @Override
    public void onEntryRemoved(ConnectionEntry entry) {
        removeHandler(entry.getLocalFile());
    }
}
