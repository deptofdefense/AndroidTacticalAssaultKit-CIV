
package com.atakmap.android.video.manager;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;
import java.util.List;

public class VideoContentResolver extends FileContentResolver
        implements VideoManager.Listener {

    private final MapView _mapView;

    public VideoContentResolver(MapView mapView) {
        super(VideoFileWatcher.VIDEO_EXTS);
        _mapView = mapView;
    }

    @Override
    public FileContentHandler getHandler(String tool, String uri) {
        FileContentHandler handler = super.getHandler(tool, uri);
        if (handler != null)
            return handler;

        // See if we can find an existing video handler with the same UID
        // as this file and then associate this file with that handler, or
        // otherwise just associate the file with the video alias we read

        File f = URIHelper.getFile(uri);
        if (f == null)
            return null;

        // Video aliases are XML-based
        if (!FileSystemUtils.checkExtension(f, "xml"))
            return null;

        // Attempt to read XML into video alias
        VideoXMLHandler xml = new VideoXMLHandler();
        List<ConnectionEntry> entries = xml.parse(f);
        if (entries.size() != 1)
            return null;

        // Create handler for the entry we just read
        ConnectionEntry entry = entries.get(0);
        VideoContentHandler backupHandler = new VideoContentHandler(_mapView,
                entry);

        // Check for existing handler
        ConnectionEntry existing = VideoManager.getInstance()
                .getEntry(entry.getUID());

        if (existing != null) {
            File lf = existing.getLocalFile();
            if (FileSystemUtils.isFile(lf))
                handler = getHandler(tool, URIHelper.getURI(lf));
        }

        if (handler == null) {
            // Add the entry we just read to a handler if there's no existing one
            addHandler(handler = backupHandler);
        } else {
            // Associate this file with an existing handler
            synchronized (this) {
                _handlers.put(f.getAbsolutePath(), handler);
            }
        }

        return handler;
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
