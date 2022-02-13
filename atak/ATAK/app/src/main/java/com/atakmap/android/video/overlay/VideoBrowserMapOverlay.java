
package com.atakmap.android.video.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.android.video.AddEditAlias;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.VideoBrowserDropDownReceiver;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.android.video.http.VideoSyncClient;
import com.atakmap.app.R;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Used for managing video aliases in Overlay Manager
 */
public class VideoBrowserMapOverlay extends AbstractMapOverlay2
        implements View.OnClickListener, VideoManager.Listener {

    private static final String TAG = "VideoBrowserMapOverlay";

    protected static final int ORDER = 6;

    private final MapView _mapView;
    private final Context _context;
    protected View _listHeader;

    private boolean _vizSupported = false;
    private ListModel _listModel;
    protected HierarchyListAdapter _om;

    public VideoBrowserMapOverlay(MapView view) {
        _mapView = view;
        _context = view.getContext();

        LayoutInflater inf = LayoutInflater.from(_context);
        _listHeader = inf.inflate(R.layout.video_list_header, _mapView, false);
        _listHeader.findViewById(R.id.gallery).setOnClickListener(this);
        _listHeader.findViewById(R.id.add_video).setOnClickListener(this);
        _listHeader.findViewById(R.id.query_videos).setOnClickListener(this);

        VideoManager.getInstance().addListener(this);
    }

    public void dispose() {
        VideoManager.getInstance().removeListener(this);
    }

    @Override
    public String getIdentifier() {
        return _context.getString(R.string.video);
    }

    @Override
    public String getName() {
        return getIdentifier();
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter prefFilter) {
        if (_listModel == null)
            _listModel = new ListModel(_mapView);
        if (adapter instanceof HierarchyListAdapter)
            _om = (HierarchyListAdapter) adapter;
        _listModel.refresh(adapter, prefFilter);
        return _listModel;
    }

    @Override
    public void onEntryAdded(ConnectionEntry entry) {
        if (_om != null && _om.isActive())
            _om.refreshList();
    }

    @Override
    public void onEntryRemoved(ConnectionEntry entry) {
        if (_om != null && _om.isActive()) {
            // Check if a folder we're in has been removed
            if (entry.getProtocol() == ConnectionEntry.Protocol.DIRECTORY)
                checkFolderRemoved();
            _om.refreshList();
        }
    }

    protected void checkFolderRemoved() {
        if (_om == null || !_om.isActive())
            return;
        HierarchyListItem list = _om.getCurrentList(true);
        Object o = list.getUserObject();
        if (!(o instanceof ConnectionEntry))
            return;
        ConnectionEntry entry = (ConnectionEntry) o;
        File xmlFile = entry.getLocalFile();
        if (!FileSystemUtils.isFile(xmlFile)) {
            // Folder has been removed - jump to the root list
            _om.navigateTo(Collections.singletonList(getIdentifier()), true);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Add video dialog
        if (id == R.id.add_video)
            new AddEditAlias(_context).addEditConnection(null);

        // Query videos off the server
        else if (id == R.id.query_videos) {
            TAKServer[] servers = TAKServerListener.getInstance()
                    .getConnectedServers();
            ServerListDialog d = new ServerListDialog(_mapView);
            d.show(_context.getString(R.string.video_text15),
                    servers, new ServerListDialog.Callback() {
                        @Override
                        public void onSelected(TAKServer server) {
                            if (server == null)
                                return;
                            VideoSyncClient client = new VideoSyncClient(
                                    _context);
                            client.query(server.getURL(false));
                        }
                    });
        }

        // Show video snapshot gallery
        else if (id == R.id.gallery) {
            File[] snapshots = IOProviderFactory.listFiles(new File(
                    VideoBrowserDropDownReceiver.SNAPSHOT_DIR));
            if (FileSystemUtils.isEmpty(snapshots)) {
                Toast.makeText(_context, R.string.gallery_no_items,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(ImageGalleryReceiver.IMAGE_GALLERY);
            i.putExtra("title", _context.getString(R.string.video_snapshots));
            i.putExtra("directory", VideoBrowserDropDownReceiver.SNAPSHOT_DIR);
            AtakBroadcast.getInstance().sendBroadcast(i);
        }
    }

    public class ListModel extends VideoFolderHierarchyListItem {

        public ListModel(MapView mapView) {
            super(mapView, null, null);
            this.asyncRefresh = true;
            this.reusable = true;
        }

        @Override
        protected List<ConnectionEntry> getEntries() {
            return VideoManager.getInstance().getEntries();
        }

        @Override
        public String getTitle() {
            return getName();
        }

        @Override
        public String getUID() {
            return getIdentifier();
        }

        @Override
        public Drawable getIconDrawable() {
            return _context.getDrawable(R.drawable.ic_video_alias);
        }

        public int getPreferredListIndex() {
            return ORDER;
        }

        @Override
        public ConnectionEntry getUserObject() {
            return null;
        }

        @Override
        public View getExtraView(View v, ViewGroup parent) {
            return null;
        }

        @Override
        public View getHeaderView() {
            return _listHeader;
        }
    }
}
