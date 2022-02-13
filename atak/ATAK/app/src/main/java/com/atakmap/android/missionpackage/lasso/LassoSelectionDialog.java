
package com.atakmap.android.missionpackage.lasso;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.DataSetObserver;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIFilter;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIQueryParameters;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.gui.drawable.CheckBoxDrawable;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dialog for selecting which content to include from a lasso selection
 */
public class LassoSelectionDialog implements DialogInterface.OnDismissListener {

    private static final String TAG = "LassoSelectionDialog";

    /**
     * Callback invoked when content is selected in this dialog
     */
    public interface Callback {
        /**
         * Content has been selected by the user
         * @param uris List of content URIs
         */
        void onContentSelected(List<String> uris);
    }

    private final MapView _mapView;
    private final Context _context;
    private final LayoutInflater _inflater;

    private DrawingShape _lasso;
    private URIFilter _filter;
    private Callback _callback;
    private CheckBoxDrawable _selectAllIcon;
    private View _selectAllBtn;
    private ExpandableListView _list;
    private LassoSelectionAdapter _adapter;

    public LassoSelectionDialog(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _inflater = LayoutInflater.from(_context);
    }

    /**
     * Set the shape used for lassoing content
     * @param shape Lasso shape
     * @return Dialog
     */
    public LassoSelectionDialog setLassoShape(DrawingShape shape) {
        _lasso = shape;
        return this;
    }

    /**
     * Set the URI-based filter for accepting content in the lasso
     * @param filter URI filter
     * @return Dialog
     */
    public LassoSelectionDialog setFilter(URIFilter filter) {
        _filter = filter;
        return this;
    }

    /**
     * Set the callback to be invoked when the selection is finished
     * @param cb Callback
     * @return Dialog
     */
    public LassoSelectionDialog setCallback(Callback cb) {
        _callback = cb;
        return this;
    }

    /**
     * Show the dialog
     */
    public void show() {
        if (_lasso == null)
            return;

        // Find map items and files within lasso
        ContentMap contentMap = buildContents();

        // If there's nothing we can select then stop
        if (contentMap.isEmpty()) {
            _lasso.removeFromGroup();
            Toast.makeText(_context, R.string.no_content_selectable,
                    Toast.LENGTH_LONG).show();
            return;
        }

        View v = LayoutInflater.from(_context).inflate(
                R.layout.lasso_selection_dialog, _mapView, false);

        _selectAllBtn = v.findViewById(R.id.select_all);
        ImageView selectAll = v.findViewById(R.id.select_all_icon);
        selectAll.setImageDrawable(_selectAllIcon = new CheckBoxDrawable());

        _list = v.findViewById(R.id.list);
        _list.setAdapter(_adapter = new LassoSelectionAdapter(contentMap));

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.select_items);
        b.setView(v);
        b.setPositiveButton(R.string.select, new OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                if (_callback != null)
                    _callback.onContentSelected(_adapter.getSelectedURIs());
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        AlertDialog d = b.show();
        d.setOnDismissListener(this);
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (_lasso != null)
            _lasso.removeFromGroup();
    }

    private ContentMap buildContents() {

        ContentMap ret = new ContentMap();

        // Get bounds and closed area vectors
        GeoPoint[] points = _lasso.getPoints();
        GeoBounds bounds = _lasso.getBounds(null);
        double unwrap = bounds.crossesIDL() ? 360 : 0;
        Vector2D[] lassoPoly = new Vector2D[points.length];
        for (int i = 0; i < points.length; i++)
            lassoPoly[i] = FOVFilter.geo2Vector(points[i], unwrap);

        // Gather map items based on bounds
        List<ContentEntry> markers = new ArrayList<>();
        List<ContentEntry> shapes = new ArrayList<>();
        List<ContentEntry> vehicles = new ArrayList<>();
        List<ContentEntry> navigation = new ArrayList<>();
        Collection<MapItem> roughItems = _mapView.getRootGroup()
                .deepFindItems(bounds, null);

        // Filter down using lasso shape
        for (MapItem mi : roughItems) {

            // Skip non-archivable items
            if (mi.hasMetaValue("nevercot") || mi.hasMetaValue("atakRoleType")
                    || mi.hasMetaValue("emergency")
                    || !mi.getMetaBoolean("addToObjList", true))
                continue;

            // Skip SPIs and self marker
            String type = mi.getType();
            if (type.equals("b-m-p-s-p-i") || type.equals("self"))
                continue;

            if (mi instanceof PointMapItem) {
                GeoPoint gp = ((PointMapItem) mi).getPoint();
                Vector2D v = FOVFilter.geo2Vector(gp, unwrap);
                if (!Vector2D.polygonContainsPoint(v, lassoPoly))
                    continue;
            } else if (mi instanceof Shape) {
                Shape shp = (Shape) mi;
                GeoPoint gp = shp.getCenter().get();
                Vector2D v = FOVFilter.geo2Vector(gp, unwrap);
                if (!Vector2D.polygonContainsPoint(v, lassoPoly)) {
                    points = shp.getPoints();
                    Vector2D[] segs = new Vector2D[points.length];
                    for (int i = 0; i < points.length; i++)
                        segs[i] = FOVFilter.geo2Vector(points[i], unwrap);
                    if (!Vector2D.segmentArrayIntersectsOrContainedByPolygon(
                            segs, lassoPoly))
                        continue;
                }
            }

            ContentEntry e = new ContentEntry(mi);

            // Check if filter accepts this content
            if (!acceptContent(e))
                continue;

            // Filter into groups
            if (NAV_TYPES.contains(type))
                navigation.add(e);
            else if (mi instanceof PointMapItem)
                markers.add(e);
            else if (mi instanceof VehicleModel)
                vehicles.add(e);
            else
                shapes.add(e);
        }

        ret.put(_context.getString(R.string.civ_s2525C), markers);
        ret.put(_context.getString(R.string.vehicles), vehicles);
        ret.put(_context.getString(R.string.shapes), shapes);
        ret.put(_context.getString(R.string.navigation), navigation);

        // Gather URI-based content
        URIQueryParameters params = new URIQueryParameters();
        params.fov = new FOVFilter(bounds);
        params.visibleOnly = true;
        List<URIContentHandler> handlers = URIContentManager.getInstance()
                .query(params);

        // Convert URI handlers to files if applicable
        List<ContentEntry> files = new ArrayList<>();
        for (URIContentHandler h : handlers) {
            File f = URIHelper.getFile(h.getURI());
            if (f == null)
                continue;

            ContentEntry e = new ContentEntry(h, f);

            // Check if filter accepts this content
            if (!acceptContent(e))
                continue;

            // If the bounds of this content exceed the bounds of the lasso
            // then it's likely this wasn't intentionally selected
            // Set selected to false by default
            if (h instanceof ILocation) {
                GeoBounds hBounds = ((ILocation) h).getBounds(null);
                if (!bounds.contains(hBounds))
                    e.selected = false;
            }

            if (h instanceof FileContentHandler) {
                String cType = ((FileContentHandler) h).getContentType();
                if (!FileSystemUtils.isEmpty(cType)) {
                    List<ContentEntry> entries = ret.get(cType);
                    if (entries == null)
                        ret.put(cType, entries = new ArrayList<>());
                    entries.add(e);
                    continue;
                }
            }

            files.add(e);
        }

        ret.put(_context.getString(R.string.files), files);

        return ret;
    }

    /**
     * Check if the filter accepts some content
     *
     * @param e Content entry
     * @return True if filter isn't set or the content is deemed acceptable
     */
    private boolean acceptContent(ContentEntry e) {
        return _filter == null || _filter.accept(e.uri);
    }

    /**
     * Maps contents by group names
     */
    private static class ContentMap
            extends HashMap<String, List<ContentEntry>> {

        @Override
        public boolean isEmpty() {
            if (super.isEmpty())
                return true;

            // Check lists too
            for (List<ContentEntry> list : values()) {
                if (!list.isEmpty())
                    return false;
            }

            return true;
        }
    }

    /**
     * Row entry for content
     */
    private static class ContentEntry {

        GroupEntry group;
        final String uri;
        final String title;
        final Drawable icon;
        final int color;
        final long size;
        boolean selected;

        ContentEntry(MapItem item) {
            uri = URIHelper.getURI(item);
            title = ATAKUtilities.getDisplayName(item);
            icon = item.getIconDrawable();
            color = item.getIconColor();
            size = MissionPackageManifest.estimateMapItemSize(item);
            selected = item.getVisible();
        }

        ContentEntry(URIContentHandler handler, File file) {
            uri = URIHelper.getURI(file);
            title = handler.getTitle();
            icon = handler.getIcon();
            color = handler.getIconColor();
            size = file.length();
            selected = !(handler instanceof FileContentHandler)
                    || ((FileContentHandler) handler).isVisible();
        }

        void select(boolean s) {
            if (this.selected != s) {
                this.selected = s;
                if (this.group != null)
                    this.group.selected += s ? 1 : -1;
            }
        }
    }

    /**
     * Row entry for groups
     */
    private static class GroupEntry {

        final String name;
        final List<ContentEntry> contents;
        int selected;

        GroupEntry(String name, List<ContentEntry> contents) {
            this.name = name;
            this.contents = contents;
            for (ContentEntry e : contents) {
                if (e.selected)
                    this.selected++;
                e.group = this;
            }
        }

        void select(boolean s) {
            if (s)
                selected = contents.size();
            else
                selected = 0;
            for (ContentEntry c : contents)
                c.selected = s;
        }

        boolean allSelected() {
            return selected == contents.size();
        }
    }

    private static final Set<String> NAV_TYPES = new HashSet<>(Arrays.asList(
            "b-m-r", "b-m-p-w-GOTO", "b-m-p-i", "b-m-t-h"));

    private static final Comparator<String> SORT_NAME = new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
            return s1.compareToIgnoreCase(s2);
        }
    };

    private static final Comparator<ContentEntry> SORT_CONTENT = new Comparator<ContentEntry>() {
        @Override
        public int compare(ContentEntry o1, ContentEntry o2) {
            return o1.title.compareToIgnoreCase(o2.title);
        }
    };

    private class RowHolder {

        View root;
        ImageView checkbox, icon;
        CheckBoxDrawable checkboxIcon;
        TextView name, size;
        ContentEntry content;
        GroupEntry group;

        RowHolder(ViewGroup parent) {
            this.root = _inflater.inflate(R.layout.lasso_selection_row,
                    parent, false);
            this.root.setTag(this);
            this.icon = this.root.findViewById(R.id.icon);
            this.name = this.root.findViewById(R.id.name);
            this.size = this.root.findViewById(R.id.size);
            this.checkbox = this.root.findViewById(R.id.checkbox);
            this.checkbox.setImageDrawable(
                    checkboxIcon = new CheckBoxDrawable());
            this.checkbox.setTag(this);
        }
    }

    private class LassoSelectionAdapter implements ExpandableListAdapter,
            View.OnClickListener, ExpandableListView.OnChildClickListener {

        private final List<GroupEntry> _groups;

        LassoSelectionAdapter(ContentMap groups) {
            _groups = new ArrayList<>();
            List<String> names = new ArrayList<>(groups.keySet());
            Collections.sort(names, SORT_NAME);
            for (String name : names) {
                List<ContentEntry> entries = groups.get(name);
                if (!FileSystemUtils.isEmpty(entries)) {
                    Collections.sort(entries, SORT_CONTENT);
                    _groups.add(new GroupEntry(name, entries));
                }
            }
            updateSelectAll();
            _selectAllBtn.setOnClickListener(this);
            _list.setOnChildClickListener(this);
        }

        List<String> getSelectedURIs() {
            List<String> uris = new ArrayList<>();
            for (GroupEntry group : _groups) {
                for (ContentEntry e : group.contents) {
                    if (e.selected)
                        uris.add(e.uri);
                }
            }
            return uris;
        }

        /**
         * Update the state of the "Select All" button
         */
        void updateSelectAll() {
            if (_selectAllIcon != null)
                _selectAllIcon.setChecked(getSelectAllState());
        }

        /**
         * Get the "Select All" button state
         * @return State (ALL_SELECTED, SOME_SELECTED, or NONE_SELECTED)
         */
        int getSelectAllState() {
            boolean allSelected = true;
            boolean someSelected = false;
            for (GroupEntry g : _groups) {
                if (!g.allSelected())
                    allSelected = false;
                if (g.selected > 0)
                    someSelected = true;
            }
            return allSelected ? CheckBoxDrawable.CHECKED
                    : (someSelected
                            ? CheckBoxDrawable.SEMI_CHECKED
                            : CheckBoxDrawable.UNCHECKED);
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
        }

        @Override
        public int getGroupCount() {
            return _groups.size();
        }

        @Override
        public int getChildrenCount(int groupPos) {
            return getGroup(groupPos).contents.size();
        }

        @Override
        public GroupEntry getGroup(int groupPos) {
            return _groups.get(groupPos);
        }

        @Override
        public ContentEntry getChild(int groupPos, int childPos) {
            return getGroup(groupPos).contents.get(childPos);
        }

        @Override
        public long getGroupId(int groupPos) {
            return 0;
        }

        @Override
        public long getChildId(int groupPos, int childPos) {
            return 0;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPos, boolean isExpanded, View row,
                ViewGroup parent) {

            RowHolder h = row != null ? (RowHolder) row.getTag() : null;
            if (h == null)
                h = new RowHolder(parent);

            GroupEntry group = getGroup(groupPos);
            List<ContentEntry> contents = group.contents;
            ContentEntry c = contents.get(0);

            setStartMargin(h.checkbox, R.dimen.list_item_title_icon_size);
            h.name.setText(group.name);
            h.icon.setImageDrawable(c.icon);
            h.icon.setColorFilter(c.color, PorterDuff.Mode.MULTIPLY);
            h.size.setText(_context.getString(R.string.items, contents.size()));
            h.checkbox.setOnClickListener(this);
            h.group = group;

            h.checkboxIcon.setChecked(group.allSelected()
                    ? CheckBoxDrawable.CHECKED
                    : (group.selected > 0
                            ? CheckBoxDrawable.SEMI_CHECKED
                            : CheckBoxDrawable.UNCHECKED));

            return h.root;
        }

        @Override
        public View getChildView(int groupPos, int childPos,
                boolean isLastChild, View row, ViewGroup parent) {

            RowHolder h = row != null ? (RowHolder) row.getTag() : null;
            if (h == null)
                h = new RowHolder(parent);

            ContentEntry c = getChild(groupPos, childPos);

            setStartMargin(h.checkbox, R.dimen.list_item_action_icon_size);
            h.name.setText(c.title);
            h.icon.setImageDrawable(c.icon);
            h.icon.setColorFilter(c.color, PorterDuff.Mode.MULTIPLY);
            h.size.setText(MathUtils.GetLengthString(c.size));
            h.checkboxIcon.setChecked(c.selected);
            h.checkbox.setOnClickListener(this);
            h.content = c;

            return h.root;
        }

        @Override
        public boolean isChildSelectable(int groupPos, int childPos) {
            return true;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return _groups.isEmpty();
        }

        @Override
        public void onGroupExpanded(int groupPos) {
        }

        @Override
        public void onGroupCollapsed(int groupPos) {
        }

        @Override
        public long getCombinedChildId(long groupId, long childId) {
            return 0;
        }

        @Override
        public long getCombinedGroupId(long groupId) {
            return 0;
        }

        @Override
        public void onClick(View v) {
            if (v == _selectAllBtn) {
                // Select all button
                boolean none = getSelectAllState() == CheckBoxDrawable.UNCHECKED;
                for (GroupEntry g : _groups)
                    g.select(none);
            } else {
                // Row checkbox
                RowHolder h = (RowHolder) v.getTag();
                if (h.content != null)
                    h.content.select(!h.content.selected);
                else if (h.group != null)
                    h.group.select(h.group.selected == 0);
            }
            refreshView();
        }

        @Override
        public boolean onChildClick(ExpandableListView p, View v, int gPos,
                int cPos, long id) {
            ContentEntry entry = getChild(gPos, cPos);
            if (entry != null) {
                entry.select(!entry.selected);
                refreshView();
            }
            return false;
        }

        private void refreshView() {
            updateSelectAll();
            _list.invalidateViews();
        }

        /**
         * Set the start margin usig a dimension resource
         * @param v View
         * @param dimenId Dimension resource ID
         */
        private void setStartMargin(View v, int dimenId) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v
                    .getLayoutParams();
            lp.setMarginStart(_context.getResources()
                    .getDimensionPixelSize(dimenId));
            v.setLayoutParams(lp);
        }
    }
}
