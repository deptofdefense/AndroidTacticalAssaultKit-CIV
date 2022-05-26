
package com.atakmap.android.video.overlay;

import android.graphics.drawable.Drawable;

import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListStateListener;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.ConnectionEntry.Protocol;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class VideoFolderHierarchyListItem extends VideoBrowserHierarchyListItem
        implements Visibility2, Search, GroupDelete,
        HierarchyListStateListener {

    protected final static int AUTO_HIDE_TIME_MS = 5000;

    protected boolean _vizSupported;

    // The UID of the entry that has its menu showing (null if none)
    protected String _showMenuUID;
    protected int _showMenuIndex;
    protected int _descendantCount;

    VideoFolderHierarchyListItem(MapView mapView, ConnectionEntry entry,
            VideoFolderHierarchyListItem parent) {
        super(mapView, entry, parent);
        this.asyncRefresh = true;
        this.reusable = false;
    }

    /**
     * Show the options menu for a specific item in the list
     * @param item Video item
     */
    void showMenu(VideoBrowserHierarchyListItem item) {
        // Show the menu
        _showMenuUID = item.getUID();
        notifyListener(false);

        // Automatically hide the menu after AUTO_HIDE_TIME milliseconds
        final int index = ++_showMenuIndex;
        _mapView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (index == _showMenuIndex) {
                    _showMenuUID = null;
                    notifyListener(false);
                }
            }
        }, AUTO_HIDE_TIME_MS);
    }

    boolean isShowingMenu(VideoBrowserHierarchyListItem item) {
        return FileSystemUtils.isEquals(_showMenuUID, item.getUID());
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public boolean isMultiSelectSupported() {
        return true;
    }

    @Override
    public int getDescendantCount() {
        return _descendantCount;
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public Drawable getIconDrawable() {
        return _context.getDrawable(R.drawable.ic_folder);
    }

    protected List<ConnectionEntry> getEntries() {
        if (_entry == null)
            return null;
        return _entry.getChildren();
    }

    @Override
    public void refreshImpl() {
        List<HierarchyListItem> folders = new ArrayList<>();
        List<HierarchyListItem> files = new ArrayList<>();
        boolean vizSupported = false;

        int descendantCount = 0;
        String uid = _entry != null ? _entry.getUID() : null;
        List<ConnectionEntry> entries = getEntries();
        if (entries != null) {
            for (ConnectionEntry e : entries) {
                if (!FileSystemUtils.isEquals(uid, e.getParentUID()))
                    continue;
                VideoBrowserHierarchyListItem item;
                boolean isDir = e.getProtocol() == Protocol.DIRECTORY;
                if (isDir)
                    item = new VideoFolderHierarchyListItem(_mapView, e, this);
                else
                    item = new VideoBrowserHierarchyListItem(_mapView, e, this);
                if (this.filter.accept(item)) {
                    if (isDir)
                        folders.add(item);
                    else
                        files.add(item);
                    item.syncRefresh(this.listener, this.filter);
                    descendantCount++;
                    if (item.isChildSupported())
                        descendantCount += item.getDescendantCount();
                }
            }
        }

        // Sort
        sortItems(folders);
        sortItems(files);

        // Update
        _vizSupported = vizSupported;
        _descendantCount = descendantCount;
        List<HierarchyListItem> filtered = new ArrayList<>();
        filtered.addAll(folders);
        filtered.addAll(files);
        updateChildren(filtered);
    }

    @Override
    public boolean onCloseList(HierarchyListAdapter om, boolean forceClose) {
        // Clear the opened menu when we leave the list
        _showMenuUID = null;
        return false;
    }

    @Override
    public List<Sort> getSorts() {
        List<Sort> sortModes = new ArrayList<>();
        sortModes.add(new SortAlphabet());
        sortModes.add(new SortProtocol());
        return sortModes;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(ItemClick.class))
            return null;
        if ((clazz.equals(Visibility.class)
                || clazz.equals(Visibility2.class)) && !_vizSupported)
            return null;
        return super.getAction(clazz);
    }

    @Override
    public boolean setVisible(boolean visible) {
        List<Visibility> actions = getChildActions(Visibility.class);
        boolean ret = !actions.isEmpty();
        for (Visibility viz : actions)
            ret &= viz.setVisible(visible);
        return ret;
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        Set<HierarchyListItem> retval = new HashSet<>();
        terms = terms.toLowerCase(LocaleUtil.getCurrent());
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem i : children) {
            Object o = i.getUserObject();
            if (!(o instanceof ConnectionEntry))
                continue;
            ConnectionEntry conn = (ConnectionEntry) o;
            if (find(conn.getAlias(), terms)
                    || find(conn.getAddress(), terms)
                    || find(conn.getPath(), terms))
                retval.add(i);
        }
        return retval;
    }

    private boolean find(String text, String terms) {
        return text != null && text.toLowerCase(
                LocaleUtil.getCurrent()).contains(terms);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {

        // Get exportable videos
        List<Exportable> exports = new ArrayList<>();
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            if (!(item instanceof Exportable))
                continue;
            Exportable e = (Exportable) item;
            if (!e.isSupported(target))
                continue;
            exports.add(e);
        }

        // Bulk Mission Package export
        if (MissionPackageExportWrapper.class.equals(target)) {
            MissionPackageExportWrapper mp = new MissionPackageExportWrapper();
            for (Exportable e : exports) {
                Object o = e.toObjectOf(target, filters);
                if (!(o instanceof MissionPackageExportWrapper))
                    continue;
                mp.getFilepaths().addAll(((MissionPackageExportWrapper) o)
                        .getFilepaths());
            }
            return mp;
        }

        return null;
    }

    private static final Comparator<HierarchyListItem> SORT_PROTOCOL = new Comparator<HierarchyListItem>() {
        @Override
        public int compare(HierarchyListItem o1, HierarchyListItem o2) {
            Protocol p1 = ((VideoBrowserHierarchyListItem) o1)
                    .getUserObject().getProtocol();
            Protocol p2 = ((VideoBrowserHierarchyListItem) o2)
                    .getUserObject().getProtocol();
            int comp;
            if (p1 == p2)
                comp = 0;
            else if (p1 == Protocol.FILE)
                comp = -1;
            else
                comp = 1;
            if (comp == 0)
                return HierarchyListAdapter.MENU_ITEM_COMP.compare(o1, o2);
            return comp;
        }
    };

    private class SortProtocol extends ComparatorSort {

        SortProtocol() {
            super(SORT_PROTOCOL, _context.getString(R.string.type),
                    ATAKUtilities.getResourceUri(R.drawable.file_sort));
        }
    }
}
