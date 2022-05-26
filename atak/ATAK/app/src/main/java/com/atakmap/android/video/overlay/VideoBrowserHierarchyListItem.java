
package com.atakmap.android.video.overlay;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.data.URIHelper;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.video.AddEditAlias;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.ConnectionEntry.Protocol;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.util.List;

/**
 * List item representing a video alias
 */
class VideoBrowserHierarchyListItem extends AbstractHierarchyListItem2
        implements Delete, Export, View.OnClickListener, FOVFilter.Filterable {

    private final static String TAG = "VideoBrowserHierarchyListItem";

    protected final MapView _mapView;
    protected final Context _context;
    protected final ConnectionEntry _entry;
    protected final URIContentHandler _handler;
    protected final VideoFolderHierarchyListItem _parent;

    VideoBrowserHierarchyListItem(MapView mapView, ConnectionEntry entry,
            VideoFolderHierarchyListItem parent) {
        _mapView = mapView;
        _context = mapView.getContext();
        _entry = entry;
        _parent = parent;

        URIContentHandler h = null;
        if (entry != null)
            h = URIContentManager.getInstance().getHandler(
                    entry.getLocalFile(), "Video");
        _handler = h;
    }

    @Override
    public String getTitle() {
        return _entry.getAlias();
    }

    @Override
    public String getDescription() {
        return _entry.getAddress(true);
    }

    @Override
    public String getUID() {
        return _entry.getUID();
    }

    @Override
    public boolean isMultiSelectSupported() {
        return false;
    }

    @Override
    public Drawable getIconDrawable() {
        return _context.getDrawable(_entry.getProtocol() == Protocol.FILE
                ? R.drawable.ic_video_alias
                : R.drawable.ic_video_remote);
    }

    @Override
    public ConnectionEntry getUserObject() {
        return _entry;
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                ? (ExtraHolder) v.getTag()
                : null;
        if (h == null) {
            h = new ExtraHolder();
            v = LayoutInflater.from(_context).inflate(
                    R.layout.video_list_item_extra, parent, false);
            h.menu = v.findViewById(R.id.video_menu);
            h.menu_open = v.findViewById(R.id.video_menu_open);
            h.send = v.findViewById(R.id.send);
            h.edit = v.findViewById(R.id.edit);
            h.save = v.findViewById(R.id.save);
            h.delete = v.findViewById(R.id.delete);
            v.setTag(h);
        }
        h.send.setOnClickListener(this);
        h.edit.setOnClickListener(this);
        h.save.setOnClickListener(this);
        h.delete.setOnClickListener(this);
        h.menu.setOnClickListener(this);

        boolean temp = _entry.isTemporary();
        boolean selecting = isSelecting();
        boolean showMenu = !selecting && _parent != null
                && _parent.isShowingMenu(this);
        boolean isFolder = _entry.getProtocol() == Protocol.DIRECTORY;
        boolean isFile = _entry.getProtocol() == Protocol.FILE;

        h.menu_open.setVisibility(showMenu ? View.VISIBLE : View.GONE);
        h.menu.setVisibility(selecting || showMenu ? View.GONE : View.VISIBLE);
        h.send.setVisibility(selecting || temp ? View.GONE : View.VISIBLE);
        h.save.setVisibility(temp ? View.VISIBLE : View.GONE);
        h.delete.setVisibility(selecting ? View.GONE : View.VISIBLE);
        h.delete.setEnabled(!temp);
        h.edit.setVisibility(selecting || isFolder || isFile || temp
                ? View.GONE
                : View.VISIBLE);
        return v;
    }

    private static class ExtraHolder {
        ImageButton menu, send, edit, save, delete;
        LinearLayout menu_open;
    }

    private boolean isSelecting() {
        return this.listener instanceof HierarchyListAdapter
                && ((HierarchyListAdapter) this.listener)
                        .getSelectHandler() != null;
    }

    @Override
    protected void refreshImpl() {
    }

    @Override
    public boolean hideIfEmpty() {
        return true;
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public boolean isChildSupported() {
        return false;
    }

    /**************************************************************************/

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Send video alias
        if (id == R.id.send) {
            SendDialog.Builder b = new SendDialog.Builder(_mapView);
            b.setName(getTitle());
            b.setIcon(getIconDrawable());
            if (_entry.isRemote()) {
                // If this is a remote entry we only need the XML
                b.setURI(URIHelper.getURI(_entry));
            } else {
                // Otherwise if this is a file or folder we need to wrap in MP
                addFiles(b, _entry);
            }
            b.show();
        }

        // Edit entry details
        else if (id == R.id.edit) {
            AddEditAlias dialog = new AddEditAlias(_context);
            dialog.addEditConnection(_entry);
        }

        // Save temporary video alias
        else if (id == R.id.save) {
            _entry.setTemporary(false);
            VideoManager.getInstance().addEntry(_entry);
            notifyListener(false);
        }

        // Delete alias
        else if (id == R.id.delete)
            promptDelete();

        // Expand out video options
        else if (id == R.id.video_menu) {
            if (_parent != null)
                _parent.showMenu(this);
        }
    }

    protected static void addFiles(SendDialog.Builder b,
            ConnectionEntry entry) {
        Protocol p = entry.getProtocol();
        List<ConnectionEntry> children = entry.getChildren();
        if (p != Protocol.DIRECTORY || children == null) {
            File f;
            if (p == Protocol.FILE)
                f = new File(entry.getPath());
            else
                f = entry.getLocalFile();
            if (FileSystemUtils.isFile(f))
                b.addFile(f);
        } else {
            for (ConnectionEntry c : children)
                addFiles(b, c);
        }
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(GoTo.class) && isSelecting()) {
            // Do not open the video while multi-selecting (ATAK-10970)
            return null;
        }
        if (_handler != null && _handler.isActionSupported(clazz))
            return clazz.cast(_handler);
        return super.getAction(clazz);
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        if (_entry == null)
            return null;

        if (MissionPackageExportWrapper.class.equals(target)) {
            MissionPackageExportWrapper mp = new MissionPackageExportWrapper();
            File file = _entry.getLocalFile();
            if (!FileSystemUtils.isFile(file))
                return mp;
            if (IOProviderFactory.isDirectory(file)) {
                File[] files = IOProviderFactory.listFiles(file);
                if (!FileSystemUtils.isEmpty(files)) {
                    for (File f : files)
                        mp.addFile(f);
                }
            } else
                mp.addFile(file);
            return mp;
        }
        return null;
    }

    @Override
    public boolean delete() {
        // Remove the entry / file
        VideoManager.getInstance().removeEntry(_entry);
        return true;
    }

    protected void promptDelete() {
        if (!_entry.isRemote()) {
            // Check if the file can be deleted
            File f = _entry.getLocalFile();
            if (f == null || !FileSystemUtils.canWrite(f.getParentFile())) {
                Toast.makeText(_context,
                        R.string.cannot_delete_sdcard_file_msg,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }
        Protocol proto = _entry.getProtocol();
        String title;
        String message = _context.getString(R.string.are_you_sure_delete)
                + getTitle();
        if (proto.equals(Protocol.FILE)) {
            title = _context.getString(R.string.remove_file);
            message += _context.getString(R.string.video_text17);
        } else if (proto.equals(Protocol.DIRECTORY)) {
            title = _context.getString(R.string.video_text18);
            message += _context.getString(R.string.video_text19);
        } else {
            title = _context.getString(R.string.alias_del_title);
            message += _context.getString(R.string.video_text20);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(title);
        b.setMessage(message);
        b.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        delete();
                    }
                });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    @Override
    public boolean accept(FOVFilter.MapState fov) {
        // Don't show videos when FOV filter is active
        // unless we have a way of getting an associated point from a video
        return false;
    }
}
