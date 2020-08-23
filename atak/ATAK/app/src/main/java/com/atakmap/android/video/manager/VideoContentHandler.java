
package com.atakmap.android.video.manager;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.ConnectionEntry.Protocol;
import com.atakmap.android.video.VideoDropDownReceiver;
import com.atakmap.android.video.legacy.Gv2FMobilePlayer;
import com.atakmap.app.R;

public class VideoContentHandler extends FileContentHandler
        implements GoTo, ItemClick {

    private final Context _context;
    private final ConnectionEntry _entry;

    VideoContentHandler(MapView mapView, ConnectionEntry entry) {
        super(entry.getLocalFile());
        _context = mapView.getContext();
        _entry = entry;
    }

    @Override
    public String getTitle() {
        return _entry.getAlias();
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(_entry.getProtocol() == Protocol.DIRECTORY
                ? R.drawable.ic_folder
                : R.drawable.ic_video_alias);
    }

    @Override
    public String getContentType() {
        return "Video";
    }

    @Override
    public String getMIMEType() {
        if (_entry.isRemote())
            return "video/xml";
        ResourceFile.MIMEType mt = ResourceFile.getMIMETypeForFile(
                _file.getName());
        if (mt != null)
            return mt.MIME;
        return ResourceFile.UNKNOWN_MIME_TYPE;
    }

    @Override
    public void importContent() {
        VideoManager.getInstance().addEntry(_entry);
    }

    @Override
    public void deleteContent() {
        VideoManager.getInstance().removeEntry(_entry);
    }

    @Override
    public boolean goTo(boolean select) {
        ConnectionEntry connectionEntry = _entry.copy();
        final VideoDropDownReceiver.AlternativeVideoPlayer avp = VideoDropDownReceiver
                .getAlternativeVideoPlayer();
        if (avp != null && avp.launchLongPress(connectionEntry))
            return false;

        Intent i = new Intent(VideoDropDownReceiver.DISPLAY);
        i.putExtra("CONNECTION_ENTRY", connectionEntry);
        AtakBroadcast.getInstance().sendBroadcast(i);
        return false;
    }

    @Override
    public boolean onClick() {
        return false;
    }

    @Override
    public boolean onLongClick() {
        ConnectionEntry connectionEntry = _entry.copy();
        final VideoDropDownReceiver.AlternativeVideoPlayer avp = VideoDropDownReceiver
                .getAlternativeVideoPlayer();

        if (avp != null && avp.launchLongPress(connectionEntry))
            return true;

        Intent i = new Intent(_context, Gv2FMobilePlayer.class);
        i.putExtra("CONNECTION_ENTRY", connectionEntry);
        _context.startActivity(i);
        return true;
    }
}
