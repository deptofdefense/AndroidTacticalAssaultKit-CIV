
package com.atakmap.android.hierarchy.items;

import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem2;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapGroupHierarchyListItem extends AbstractHierarchyListItem2
        implements
        MapGroup.OnGroupListChangedListener,
        MapGroup.OnItemListChangedListener,
        Visibility2, GroupDelete, Search, Export {

    private static final String TAG = "MapGroupHierarchyListItem";

    public final static MapGroup.MapItemsCallback ADD_TO_OBJ_LIST_FUNC = new MapGroup.MapItemsCallback() {
        @Override
        public boolean onItemFunction(MapItem item) {
            return item.getMetaBoolean("addToObjList", true);
        }
    };

    private final static Set<String> SEARCH_FIELDS = new HashSet<>();

    static {
        SEARCH_FIELDS.add("callsign");
        SEARCH_FIELDS.add("title");
        SEARCH_FIELDS.add("shapeName");
    }

    protected final MapGroupHierarchyListItem parent;
    protected final MapView mapView;
    protected final MapGroup group;
    protected final MapGroup.MapItemsCallback itemFilter;

    protected int deepCount;
    protected final Map<MapGroup, HierarchyListItem> groupMap = new HashMap<>();

    protected Comparator<HierarchyListItem> order;

    public MapGroupHierarchyListItem(MapGroupHierarchyListItem parent,
            MapView mapView,
            MapGroup group, HierarchyListFilter filter, BaseAdapter listener) {
        this(parent, mapView, group, ADD_TO_OBJ_LIST_FUNC, filter, listener);
    }

    public MapGroupHierarchyListItem(MapGroupHierarchyListItem parent,
            MapView mapView,
            MapGroup group, MapGroup.MapItemsCallback itemFilter,
            HierarchyListFilter filter, BaseAdapter listener) {
        this.parent = parent;
        this.mapView = mapView;
        this.group = group;
        if (itemFilter == null)
            itemFilter = ADD_TO_OBJ_LIST_FUNC;
        this.itemFilter = itemFilter;
        this.deepCount = -1;

        this.group.addOnItemListChangedListener(this);
        this.group.addOnGroupListChangedListener(this);
        this.asyncRefresh = true;
        this.reusable = true;

        syncRefresh(listener, filter);
    }

    @Override
    public void dispose() {
        synchronized (this.children) {
            if (!isDisposed()) {
                disposeChildren();
                this.group.removeOnItemListChangedListener(this);
                this.group.removeOnGroupListChangedListener(this);
                groupMap.clear();
                this.disposed = true;
            }
        }
    }

    protected HierarchyListItem createChild(MapGroup group) {
        return new MapGroupHierarchyListItem(this, this.mapView, group,
                this.itemFilter, this.filter,
                this.listener);
    }

    protected HierarchyListItem createChild(MapItem item) {
        return new MapItemHierarchyListItem(this.mapView, item);
    }

    protected void invalidate(boolean deep) {
        if (deep)
            this.deepCount = -1;
        if (this.parent != null)
            this.parent.deepCount = -1;
        // TODO: Use path to this map group instead of indiscreet refresh
        requestRefresh();
    }

    /**************************************************************************/
    // Hierarchy List Item

    @Override
    public int getPreferredListIndex() {
        return this.group.getMetaInteger("groupOverlayOrder", -1);
    }

    @Override
    public String getTitle() {
        final String name = group.getMetaString("omNameOverride", null);
        if (name != null)
            return name;
        else
            return this.group.getFriendlyName();
    }

    @Override
    public int getDescendantCount() {
        List<HierarchyListItem> children = getChildren();
        if (this.deepCount == -1) {
            int count = 0;
            for (HierarchyListItem child : children)
                count += child.getDescendantCount();
            this.deepCount = count;
        }
        return children.size() + this.deepCount;
    }

    @Override
    public String getIconUri() {
        return this.group.getMetaString("iconUri", null);
    }

    @Override
    public Object getUserObject() {
        return this.group;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    protected void refreshImpl() {
        // Get matching comparator
        if (!(this.filter.sort instanceof HierarchyListItem.SortDistanceFrom))
            this.order = HierarchyListAdapter.MENU_ITEM_COMP;
        else
            this.order = new HierarchyListAdapter.ItemDistanceComparator(
                    ((HierarchyListItem.SortDistanceFrom) this.filter.sort).location);

        // Filter and sort map groups
        List<HierarchyListItem> filteredGroups = new ArrayList<>();

        Map<MapGroup, HierarchyListItem> grpMap;
        synchronized (this.children) {
            grpMap = new HashMap<>(this.groupMap);
        }

        for (MapGroup child : this.group.getChildGroups()) {
            if (!addToObjList(child, this.itemFilter))
                continue;

            HierarchyListItem item = grpMap.get(child);
            if (item == null) {
                item = createChild(child);
                if (item == null)
                    continue;
                grpMap.put(child, item);
            } else if (item instanceof AbstractHierarchyListItem2)
                ((AbstractHierarchyListItem2) item).syncRefresh(
                        this.listener, this.filter);
            filteredGroups.add(item);
        }

        // Update the group mapping
        synchronized (this.children) {
            if (disposed) {
                disposeItems(filteredGroups);
                return;
            }
            for (Map.Entry<MapGroup, HierarchyListItem> e : grpMap.entrySet()) {
                MapGroup grp = e.getKey();
                HierarchyListItem newList = e.getValue();
                HierarchyListItem oldList = this.groupMap.get(grp);
                if (oldList != null && oldList != newList) {
                    // Group already created on another thread - dispose
                    if (newList instanceof HierarchyListItem2)
                        ((HierarchyListItem2) newList).dispose();
                    int idx = filteredGroups.indexOf(newList);
                    filteredGroups.set(idx, oldList);
                } else if (oldList == null) {
                    // Add new group
                    this.groupMap.put(grp, newList);
                }
            }
        }

        Collections.sort(filteredGroups, HierarchyListAdapter.MENU_ITEM_COMP);

        // Filter and sort map items
        List<HierarchyListItem> filteredItems = new ArrayList<>();
        for (MapItem child : this.group.getItems()) {
            if (!this.itemFilter.onItemFunction(child))
                continue;
            HierarchyListItem childItem = this.createChild(child);
            if (this.filter.accept(childItem))
                filteredItems.add(childItem);
        }

        Collections.sort(filteredItems, this.order);

        List<HierarchyListItem> filtered = new ArrayList<>(filteredGroups);
        filtered.addAll(filteredItems);

        // Update
        updateChildren(filtered);
    }

    @Override
    public boolean hideIfEmpty() {
        return true;
    }

    /**************************************************************************/
    // Visibility

    @Override
    public boolean isVisible() {
        return super.isVisible() && this.group.getVisible();
    }

    @Override
    public boolean setVisible(boolean visible) {
        Log.d(TAG, "*** MapGroupHierarchyItem [" + this.getTitle()
                + "] setVisible("
                + visible + ") group.getVisible()=" + this.group.getVisible());
        Visibility itemVisibility;
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            itemVisibility = item.getAction(Visibility.class);
            if ((itemVisibility != null)
                    && itemVisibility.isVisible() != visible)
                itemVisibility.setVisible(visible);
        }
        // TODO: Fix issue where setting group visibility to false then
        // setting a child's visibility to true and then deleting the
        // child item causes it to stay on screen until ATAK restart
        this.group.setVisible(visible);
        return true;
    }

    /**************************************************************************/
    // Delete

    @Override
    public List<Delete> getDeleteActions() {

        // Item deletion actions
        List<Delete> actions = super.getDeleteActions();

        // Delete non-permanent group if it's completely empty
        actions.add(new Delete() {
            @Override
            public boolean delete() {
                if (!group.getMetaBoolean("permaGroup", false)
                        && group.getParentGroup() != null
                        && group.getItemCount() == 0
                        && group.getGroupCount() == 0)
                    group.getParentGroup().removeGroup(group);
                return true;
            }
        });
        return actions;
    }

    /**************************************************************************/
    // Search

    @Override
    public Set<HierarchyListItem> find(String terms) {
        terms = "*" + terms + "*";

        Set<Long> found = new HashSet<>();
        Set<HierarchyListItem> retval = new HashSet<>();

        List<MapItem> results;
        for (String field : SEARCH_FIELDS) {
            results = this.group.deepFindItems(field, terms);
            if (results == null)
                continue;
            for (MapItem item : results) {
                if (found.contains(item.getSerialId()) || !itemContained(item))
                    continue;
                retval.add(new MapItemHierarchyListItem(this.mapView, item));
                found.add(item.getSerialId());
            }
        }

        return retval;
    }

    /**
     * Check whether a map item is in the children list
     * Used with find to avoid re-filtering
     * @param item Map item to look for
     * @return True if the map item is part of this list
     */
    private boolean itemContained(MapItem item) {
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem hli : children) {
            if (hli instanceof MapItemHierarchyListItem
                    && hli.getUID().equals(item.getUID())
                    || hli instanceof MapGroupHierarchyListItem
                            && ((MapGroupHierarchyListItem) hli)
                                    .itemContained(item))
                return true;
        }
        return false;
    }

    /**************************************************************************/

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        Log.d(TAG, "onItemAdded(" + MapItem.getUniqueMapItemName(item) + ", "
                + group + ")");
        this.invalidate(false);
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        Log.d(TAG, "onItemRemoved(" + MapItem.getUniqueMapItemName(item) + ", "
                + group + ")");
        this.invalidate(false);
    }

    @Override
    public void onGroupAdded(MapGroup group, MapGroup parent) {
        Log.d(TAG, "onGroupAdded(" + group + ", " + parent + ")");
        this.invalidate(true);
    }

    @Override
    public void onGroupRemoved(MapGroup group, MapGroup parent) {
        Log.d(TAG, "onGroupRemoved(" + group + ", " + parent + ")");
        this.invalidate(true);
    }

    /**************************************************************************/

    public static boolean addToObjList(MapGroup group) {
        return addToObjList(group, ADD_TO_OBJ_LIST_FUNC);
    }

    public static boolean addToObjList(MapGroup group,
            MapGroup.MapItemsCallback filter) {
        if (!group.getMetaBoolean("addToObjList", true))
            return false;

        // Group must have a valid name
        if (FileSystemUtils.isEmpty(group.getFriendlyName()))
            return false;

        // Let the refresh filter decide the rest
        return true;
    }

    private MissionPackageExportWrapper exportMissionPackageMapGroup(
            ExportFilters filters)
            throws FormatNotSupportedException {
        MissionPackageExportWrapper f = new MissionPackageExportWrapper();
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            if (item instanceof Exportable
                    && ((Exportable) item)
                            .isSupported(MissionPackageExportWrapper.class)) {
                MissionPackageExportWrapper itemFolder = (MissionPackageExportWrapper) ((Exportable) item)
                        .toObjectOf(MissionPackageExportWrapper.class,
                                filters);
                if (itemFolder != null && itemFolder.getUIDs() != null
                        && itemFolder.getUIDs().size() > 0)
                    f.getUIDs().addAll(itemFolder.getUIDs());
            }
        }

        if (f.getUIDs().isEmpty())
            return null;

        return f;
    }

    private OGRFeatureExportWrapper exportOgrGeomtry(ExportFilters filters)
            throws FormatNotSupportedException {
        OGRFeatureExportWrapper f = new OGRFeatureExportWrapper(
                group.getFriendlyName());
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            if (item instanceof Exportable
                    && ((Exportable) item)
                            .isSupported(OGRFeatureExportWrapper.class)) {
                OGRFeatureExportWrapper itemFolder = (OGRFeatureExportWrapper) ((Exportable) item)
                        .toObjectOf(OGRFeatureExportWrapper.class, filters);
                if (itemFolder != null && !itemFolder.isEmpty())
                    f.addGeometries(itemFolder);
            }
        }

        if (f.isEmpty())
            return null;

        return f;
    }

    private GPXExportWrapper exportGpx(ExportFilters filters)
            throws FormatNotSupportedException {
        GPXExportWrapper f = new GPXExportWrapper();
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            if (item instanceof Exportable
                    && ((Exportable) item)
                            .isSupported(GPXExportWrapper.class)) {
                GPXExportWrapper itemFolder = (GPXExportWrapper) ((Exportable) item)
                        .toObjectOf(GPXExportWrapper.class, filters);
                if (itemFolder != null && !itemFolder.isEmpty())
                    f.add(itemFolder);
            }
        }

        if (f.isEmpty())
            return null;

        return f;
    }

    private Folder exportKMLMapGroup(ExportFilters filters)
            throws FormatNotSupportedException {
        Folder f = new Folder();
        f.setName(group.getFriendlyName());
        f.setFeatureList(new ArrayList<Feature>());
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            if (item instanceof Exportable
                    && ((Exportable) item).isSupported(Folder.class)) {
                Folder itemFolder = (Folder) ((Exportable) item)
                        .toObjectOf(
                                Folder.class, filters);
                if (itemFolder != null
                        && itemFolder.getFeatureList() != null
                        && itemFolder.getFeatureList().size() > 0)
                    f.getFeatureList().add(itemFolder);
            }
        }

        if (f.getFeatureList().isEmpty())
            return null;

        return f;
    }

    private KMZFolder exportKMZMapGroup(
            ExportFilters filters) throws FormatNotSupportedException {
        final KMZFolder f = new KMZFolder();
        f.setName(group.getFriendlyName());
        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            //Attempt KMZ, fall back on KML
            if (item instanceof Exportable
                    && ((Exportable) item).isSupported(KMZFolder.class)) {
                KMZFolder itemFolder = (KMZFolder) ((Exportable) item)
                        .toObjectOf(KMZFolder.class, filters);
                if (itemFolder != null && !itemFolder.isEmpty()) {
                    f.getFeatureList().add(itemFolder);
                    if (itemFolder.hasFiles())
                        f.getFiles().addAll(itemFolder.getFiles());
                }
            } else if (item instanceof Exportable
                    && ((Exportable) item).isSupported(Folder.class)) {
                Folder itemFolder = (Folder) ((Exportable) item)
                        .toObjectOf(
                                Folder.class, filters);
                if (itemFolder != null
                        && itemFolder.getFeatureList() != null
                        && itemFolder.getFeatureList().size() > 0)
                    f.getFeatureList().add(itemFolder);
            }
        }

        if (f.isEmpty())
            return null;

        return f;
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return Folder.class.equals(target) ||
                KMZFolder.class.equals(target) ||
                MissionPackageExportWrapper.class.equals(target) ||
                GPXExportWrapper.class.equals(target) ||
                OGRFeatureExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {

        if (group == null || !isSupported(target)) {
            //nothing to export
            return null;
        }

        if (Folder.class.equals(target)) {
            return exportKMLMapGroup(filters);
        } else if (KMZFolder.class.equals(target)) {
            return exportKMZMapGroup(filters);
        } else if (MissionPackageExportWrapper.class.equals(target)) {
            return exportMissionPackageMapGroup(filters);
        } else if (GPXExportWrapper.class.equals(target)) {
            return exportGpx(filters);
        } else if (OGRFeatureExportWrapper.class.equals(target)) {
            return exportOgrGeomtry(filters);
        }

        return null;
    }
}
