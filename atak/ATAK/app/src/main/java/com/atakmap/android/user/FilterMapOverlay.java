
package com.atakmap.android.user;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.BaseAdapter;

import com.atakmap.android.config.FiltersConfig;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapGroupHierarchyListItem;
import com.atakmap.android.hierarchy.items.MapItemHierarchyListItem;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.android.routes.RouteListModel;
import com.atakmap.android.user.filter.MapOverlayConfig;
import com.atakmap.android.user.filter.MapOverlayFilter;
import com.atakmap.android.user.filter.MapOverlayFilters;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;

import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;

public class FilterMapOverlay extends AbstractMapOverlay2
        implements OnSharedPreferenceChangeListener {

    public static final String TAG = "FilterMapOverlay";

    private static MapGroup.MapItemsCallback rejectFilter = null;
    private static MapOverlayFilters overlayFilters = null;
    private static FiltersConfig _colorFilters;

    private final MapOverlayConfig config;

    /**
     * Map Items for this overlay/level
     */
    private final Map<MapItem, HierarchyListItem> items;

    /**
     * Nested/children overlays
     */
    private final List<FilterMapOverlay> overlays;

    private final MapView mapView;
    private final FilterMapOverlay parent;
    private final SharedPreferences prefs;
    private ListModelImpl _listModel;
    private HierarchyListAdapter overlayManager;

    public FilterMapOverlay(FilterMapOverlay parent, MapView mapView,
            MapOverlayConfig config,
            SharedPreferences prefs, FiltersConfig colorFilters) {
        this.parent = parent;
        this.mapView = mapView;
        this.config = config;
        this.prefs = prefs;
        this.items = new HashMap<>();
        this.overlays = new ArrayList<>();

        this.init(colorFilters);
        this.legacyInit();
    }

    /**************************************************************************/
    // Map Overlay

    @Override
    public String getIdentifier() {
        return this.config.getId();
    }

    @Override
    public String getName() {
        return this.config.getTitle();
    }

    public String getParentId() {
        return this.config.getParent();
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
    public HierarchyListItem getListModel(BaseAdapter adapter, long actions,
            HierarchyListFilter filter) {
        if (adapter instanceof HierarchyListAdapter)
            this.overlayManager = (HierarchyListAdapter) adapter;
        if (_listModel == null) {
            if (getIdentifier().equals("routes"))
                _listModel = new RouteListModel(this, adapter, filter);
            else
                _listModel = new ListModelImpl(this, adapter, filter);
        }
        _listModel.refresh(adapter, filter);
        return _listModel;
    }

    private boolean getVisible() {
        return this.prefs.getBoolean(getIdentifier()
                + "_visible", true);
    }

    private boolean setVisible(boolean visible) {
        this.prefs.edit().putBoolean(getIdentifier()
                + "_visible", visible).apply();
        return visible;
    }

    /**********************************************************************/
    // Hierarchy List Item

    public static class ListModelImpl extends AbstractHierarchyListItem2
            implements
            Visibility2, GroupDelete, Search, Export {

        private static final String TAG = "FilterMapOverlay.ListModelImpl";

        protected final FilterMapOverlay _overlay;
        protected final MapOverlayConfig _config;
        protected final MapView _mapView;

        public ListModelImpl(FilterMapOverlay overlay, BaseAdapter listener,
                HierarchyListFilter filter) {
            _overlay = overlay;
            _config = overlay.config;
            _mapView = overlay.mapView;
            this.filter = filter;
            this.listener = listener;
            this.asyncRefresh = true;
        }

        @Override
        public int getPreferredListIndex() {
            return _config.getOrder();
        }

        @Override
        public String getIconUri() {
            return _config.getIcon();
        }

        @Override
        public int getIconColor() {
            if (_config.hasIconColor()) {
                return _config.getIconColor(_overlay.prefs);
            }

            return super.getIconColor();
        }

        @Override
        public String getTitle() {
            return _config.getTitle();
        }

        @Override
        public boolean setVisible(boolean visible) {
            List<Visibility> actions = getChildActions(Visibility.class);

            boolean ret = false;
            for (Visibility viz : actions)
                ret |= viz.setVisible(visible);
            // Store visibility preference
            _overlay.setVisible(visible);
            return ret;
        }

        @Override
        public int getDescendantCount() {
            //get direct children
            List<HierarchyListItem> children = getChildren();
            int cnt = children.size();

            //get descendant children
            for (HierarchyListItem item : children) {
                if (item instanceof FilterMapOverlay.ListModelImpl)
                    cnt += item.getDescendantCount();
            }

            return cnt;
        }

        @Override
        public boolean hideIfEmpty() {
            return true;
        }

        @Override
        public Object getUserObject() {
            return _overlay;
        }

        @Override
        protected void refreshImpl() {
            // Get matching comparator
            Comparator<HierarchyListItem> order;
            if (!(this.filter.sort instanceof HierarchyListItem.SortDistanceFrom)) {
                order = HierarchyListAdapter.MENU_ITEM_COMP;
                this.filter.sort = new SortAlphabet();
            } else {
                order = new HierarchyListAdapter.ItemDistanceComparator(
                        (HierarchyListItem.SortDistanceFrom) filter.sort);
            }

            //add all child overlays, and sort
            List<HierarchyListItem> filtered = new ArrayList<>();
            List<FilterMapOverlay> overlays = _overlay.getOverlays();
            for (FilterMapOverlay child : overlays)
                filtered.add(child.getListModel(this.listener, 0,
                        this.filter));
            Collections.sort(filtered, HierarchyListAdapter.MENU_ITEM_COMP);

            // Filter and sort
            List<HierarchyListItem> items = _overlay.getListItems();
            for (HierarchyListItem item : items) {
                if (this.filter.accept(item))
                    filtered.add(item);
            }
            Collections.sort(filtered.subList(overlays.size(),
                    filtered.size()), order);

            updateChildren(filtered);
        }

        @Override
        protected void disposeChildren() {
            synchronized (this.children) {
                this.children.clear();
            }
        }

        @Override
        public void dispose() {
            super.disposeChildren();
        }

        public HierarchyListFilter getFilter() {
            return this.filter;
        }

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            terms = terms.toLowerCase(LocaleUtil.getCurrent());
            Set<HierarchyListItem> retval = new HashSet<>();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                String title = item.getTitle();
                if (title != null
                        && !title.isEmpty()
                        && title.toLowerCase(LocaleUtil.getCurrent())
                                .contains(terms))
                    retval.add(item);
                else if (item instanceof ListModelImpl) {
                    Set<HierarchyListItem> childretval = ((ListModelImpl) item)
                            .find(terms);
                    if (childretval != null && childretval.size() > 0)
                        retval.addAll(childretval);
                }
            }
            return retval;
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
            if (super.getChildCount() < 1 || !isSupported(target)) {
                //nothing to export
                return null;
            }

            if (Folder.class.equals(target)) {
                return toKml(filters);
            } else if (KMZFolder.class.equals(target)) {
                return toKmz(filters);
            } else if (MissionPackageExportWrapper.class.equals(target)) {
                return toMissionPackage(filters);
            } else if (GPXExportWrapper.class.equals(target)) {
                return toGpx(filters);
            } else if (OGRFeatureExportWrapper.class.equals(target)) {
                return toOgrGeomtry(filters);
            }

            return null;
        }

        private Folder toKml(ExportFilters filters)
                throws FormatNotSupportedException {
            Folder f = new Folder();
            f.setName(_config.getTitle());
            f.setFeatureList(new ArrayList<Feature>());
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                toKml(f, item, filters);

            if (f.getFeatureList().size() < 1) {
                return null;
            }

            return f;
        }

        private void toKml(Folder f, HierarchyListItem item,
                ExportFilters filters) throws FormatNotSupportedException {
            if (item instanceof ListModelImpl) {
                //recurse to get child overlay items
                List<HierarchyListItem> children = ((ListModelImpl) item)
                        .getChildren();
                for (HierarchyListItem c : children)
                    toKml(f, c, filters);
            } else if (item.getUserObject() != null
                    && item.getUserObject() instanceof MapItem) {
                MapItem mapItem = (MapItem) item.getUserObject();
                if (mapItem instanceof Exportable
                        && ((Exportable) mapItem).isSupported(Folder.class)) {
                    Folder itemFolder = (Folder) ((Exportable) item)
                            .toObjectOf(Folder.class, filters);
                    if (itemFolder != null
                            && itemFolder.getFeatureList() != null
                            && itemFolder.getFeatureList().size() > 0) {
                        f.getFeatureList().add(itemFolder);
                    }
                }
            }
        }

        private KMZFolder toKmz(ExportFilters filters)
                throws FormatNotSupportedException {
            final KMZFolder f = new KMZFolder();
            f.setName(_config.getTitle());

            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                toKmz(f, item, filters);

            if (f.isEmpty()) {
                return null;
            }

            return f;
        }

        private void toKmz(KMZFolder f, HierarchyListItem item,
                ExportFilters filters) throws FormatNotSupportedException {
            if (item instanceof ListModelImpl) {
                //recurse to get child overlay items
                List<HierarchyListItem> children = ((ListModelImpl) item)
                        .getChildren();
                for (HierarchyListItem c : children)
                    toKmz(f, c, filters);
            } else if (item.getUserObject() != null
                    && item.getUserObject() instanceof MapItem) {
                MapItem mapItem = (MapItem) item.getUserObject();
                if (mapItem instanceof Exportable
                        && ((Exportable) mapItem)
                                .isSupported(KMZFolder.class)) {
                    KMZFolder itemFolder = (KMZFolder) ((Exportable) item)
                            .toObjectOf(KMZFolder.class, filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.getFeatureList().add(itemFolder);
                        if (itemFolder.hasFiles()) {
                            f.getFiles().addAll(itemFolder.getFiles());
                        }
                    }
                } else if (mapItem instanceof Exportable
                        && ((Exportable) mapItem).isSupported(Folder.class)) {
                    Folder itemFolder = (Folder) ((Exportable) item)
                            .toObjectOf(Folder.class, filters);
                    if (itemFolder != null
                            && itemFolder.getFeatureList() != null
                            && itemFolder.getFeatureList().size() > 0) {
                        f.getFeatureList().add(itemFolder);
                    }
                }
            }
        }

        private MissionPackageExportWrapper toMissionPackage(
                ExportFilters filters) throws FormatNotSupportedException {
            MissionPackageExportWrapper f = new MissionPackageExportWrapper();

            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                toMissionPackage(f, item, filters);

            if (f.getUIDs().size() < 1) {
                return null;
            }

            return f;
        }

        private void toMissionPackage(MissionPackageExportWrapper f,
                HierarchyListItem item,
                ExportFilters filters) throws FormatNotSupportedException {
            if (item instanceof ListModelImpl) {
                //recurse to get child overlay items
                List<HierarchyListItem> children = ((ListModelImpl) item)
                        .getChildren();
                for (HierarchyListItem c : children)
                    toMissionPackage(f, c, filters);
            } else if (item.getUserObject() != null
                    && item.getUserObject() instanceof MapItem) {
                MapItem mapItem = (MapItem) item.getUserObject();
                if (mapItem instanceof Exportable
                        && ((Exportable) mapItem)
                                .isSupported(
                                        MissionPackageExportWrapper.class)) {
                    MissionPackageExportWrapper itemFolder = (MissionPackageExportWrapper) ((Exportable) item)
                            .toObjectOf(MissionPackageExportWrapper.class,
                                    filters);
                    if (itemFolder != null && itemFolder.getUIDs() != null
                            && itemFolder.getUIDs().size() > 0) {
                        f.getUIDs().addAll(itemFolder.getUIDs());
                    }
                }
            }
        }

        private OGRFeatureExportWrapper toOgrGeomtry(ExportFilters filters)
                throws FormatNotSupportedException {
            OGRFeatureExportWrapper f = new OGRFeatureExportWrapper(
                    this.getTitle());
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                toOgrGeometry(f, item, filters);

            if (f.getGeometries().size() < 1) {
                return null;
            }

            return f;
        }

        private void toOgrGeometry(OGRFeatureExportWrapper f,
                HierarchyListItem item,
                ExportFilters filters) throws FormatNotSupportedException {
            if (item instanceof ListModelImpl) {
                //recurse to get child overlay items
                List<HierarchyListItem> children = ((ListModelImpl) item)
                        .getChildren();
                for (HierarchyListItem c : children) {
                    toOgrGeometry(f, c, filters);
                }
            } else if (item.getUserObject() != null
                    && item.getUserObject() instanceof MapItem) {
                MapItem mapItem = (MapItem) item.getUserObject();
                if (mapItem instanceof Exportable
                        && ((Exportable) mapItem)
                                .isSupported(OGRFeatureExportWrapper.class)) {
                    OGRFeatureExportWrapper itemFolder = (OGRFeatureExportWrapper) ((Exportable) item)
                            .toObjectOf(OGRFeatureExportWrapper.class, filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.addGeometries(itemFolder);
                    }
                }
            }
        }

        private GPXExportWrapper toGpx(ExportFilters filters)
                throws FormatNotSupportedException {
            GPXExportWrapper f = new GPXExportWrapper();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                toGpx(f, item, filters);

            if (f.isEmpty()) {
                return null;
            }

            return f;
        }

        private void toGpx(GPXExportWrapper f, HierarchyListItem item,
                ExportFilters filters) throws FormatNotSupportedException {
            if (item instanceof ListModelImpl) {
                //recurse to get child overlay items
                List<HierarchyListItem> children = ((ListModelImpl) item)
                        .getChildren();
                for (HierarchyListItem c : children) {
                    toGpx(f, c, filters);
                } //end view loop
            } else if (item.getUserObject() != null
                    && item.getUserObject() instanceof MapItem) {
                MapItem mapItem = (MapItem) item.getUserObject();
                if (mapItem instanceof Exportable
                        && ((Exportable) mapItem)
                                .isSupported(GPXExportWrapper.class)) {
                    GPXExportWrapper itemFolder = (GPXExportWrapper) ((Exportable) item)
                            .toObjectOf(GPXExportWrapper.class, filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.add(itemFolder);
                    }
                }
            }
        }
    }

    /**
     * Initialize child overlays
     * @param colorFilters the filters file for colors.
     */
    private void init(FiltersConfig colorFilters) {
        this.config.init(this.mapView.getContext(), this.prefs, colorFilters);
        List<MapOverlayConfig> configs = this.config.getOverlays();
        List<FilterMapOverlay> overlays = new ArrayList<>();
        for (MapOverlayConfig child : configs)
            overlays.add(new FilterMapOverlay(this, this.mapView, child,
                    this.prefs, colorFilters));
        synchronized (this.overlays) {
            this.overlays.clear();
            this.overlays.addAll(overlays);
        }
    }

    /**************************************************************************/
    // Hierarchy List Adapter _Overlay legacy

    private void legacyInit() {
        //only top level overlays are map listeners
        if (parent != null)
            return;

        Log.d(TAG, "Registering map listeners for " + this);
        final MapGroup _baseGroup = this.mapView.getRootGroup();
        MapGroup.deepMapItems(_baseGroup, new MapGroup.MapItemsCallback() {

            @Override
            public boolean onItemFunction(MapItem item) {
                addItem(item);
                return false;
            }

        });

        this.mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED,
                new MapEventDispatcher.MapEventDispatchListener() {
                    @Override
                    public void onMapEvent(MapEvent event) {
                        //Log.d(TAG, "XXX: removing a marker: " + event.getItem().getUID());
                        // XXX - previous behavior was to call this method.
                        //addRemoveItem(view, event.getItem(), true);

                        // since this is legacy behavior, we will need to remove the item 
                        // from the items map and notify the listener.   XXX: It is assumed
                        // based on the comment in the ITEM_ADDED part below that adaptitem
                        // will update 'it'.
                        // XXX - check with Chris on review
                        //                        synchronized (items) { 
                        //                            items.remove(event.getItem());
                        //                        }
                        //                        mapView.post(new Runnable() {
                        //                            public void run() {
                        //                                if (listener != null)
                        //                                    listener.notifyDataSetChanged();
                        //                            }
                        //                        });

                        removeItem(event.getItem());
                    }

                });

        this.mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, _refreshItem);
        this.mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_GROUP_CHANGED, _refreshItem);
        this.mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REFRESH, _refreshItem);

        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private final MapEventDispatchListener _refreshItem = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            // Re-categorize map item
            boolean updateVis = !event.getType().equals(MapEvent.ITEM_REFRESH);
            final MapItem item = event.getItem();
            if (item != null && item.getGroup() != null) {
                removeItem(item);
                addItem(item, updateVis);
            }
        }
    };

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPrefs, String key) {

        if (key == null)
            return;

        if (onPrefChanged(key)) {
            /*Log.d(TAG, "Relevant pref \"" + key + "\" changed for "
                    + getIdentifier());*/
            MapGroup.deepMapItems(mapView.getRootGroup(),
                    new MapGroup.MapItemsCallback() {
                        @Override
                        public boolean onItemFunction(MapItem item) {
                            removeItem(item);
                            addItem(item);
                            return false;
                        }
                    });
        }
    }

    private boolean onPrefChanged(String key) {
        boolean changed = false;
        for (MapOverlayFilter f : this.config.getFilters()) {
            if (f.hasPrefStringName() && f.getPrefStringName().equals(key))
                changed = true;
        }
        // Let children know as well
        List<FilterMapOverlay> overlays = getOverlays();
        for (FilterMapOverlay child : overlays)
            changed |= child.onPrefChanged(key);
        return changed;
    }

    /**
     * Should this item's visibility be dependant upon the overlay?
     * @param item Map item to check
     * @return True if the item visibility should be controlled
     */
    private boolean changeVisibility(MapItem item) {
        boolean stateSaver = !item.getMetaBoolean("transient", true);
        boolean archived = item.getMetaBoolean("archive", false);
        String entry = item.getMetaString("entry", "");
        String from = item.getMetaString("from", "");
        return !(stateSaver && archived
                || FileSystemUtils.isEquals(entry, "user")
                || FileSystemUtils.isEquals(from, "StateSaver"));
    }

    /**
     * Test and add item if it passes filters. If not, then allow a child
     * overlay to accept
     *
     * @param item Map item to add
     * @param updateVis True to change visibility
     * @return True if item added to this overlay
     */
    private boolean addItem(MapItem item, boolean updateVis) {

        if (!preAccept(item))
            return false;

        boolean changed = false;
        if (accept(item)) {
            // Control visibility for received items (visible upon arrival)
            if (updateVis && !getVisible() && changeVisibility(item))
                item.setVisible(false);
            changed = true;
            MapItemHierarchyListItem listItem = new MapItemHierarchyListItem(
                    mapView, item);
            synchronized (this.items) {
                this.items.put(item, listItem);
            }
        }
        if (changed && this.overlayManager != null)
            this.overlayManager.refreshList();

        //if not handled, give children an opportunity to accept
        if (!changed) {
            List<FilterMapOverlay> children = getOverlays();
            for (FilterMapOverlay child : children) {
                if (child.addItem(item, updateVis))
                    break;
            }
        }
        return this.config.isExclusive() && changed;
    }

    private boolean addItem(MapItem item) {
        return addItem(item, true);
    }

    /**
     * Run preliminary acceptance tests that would exclude the specified
     * map item from this and all child overlays
     *
     * @param item the map item to test
     * @return true if the map item could be accepted.
     */
    private boolean preAccept(MapItem item) {
        //Log.d(TAG, "addRemoveItem: " + item.toString());
        if (!item.getMetaBoolean("addToObjList", true))
            return false;

        if (item.hasMetaValue("filterOverlayId") && this.config.isExclusive()) {
            //already accepted by a Filter Overlay
            /*Log.d(TAG + toString(), item.toString()
                    + " already accepted by overlay: "
                    + item.getMetaString("filterOverlayId", null));*/
            return false;
        }

        if (item instanceof Marker) {
            String path = item.getMetaString(UserIcon.IconsetPath, null);
            if (UserIcon.IsValidIconsetPath(path, false,
                    mapView.getContext())) {
                //this is a user icon, lets skip it here and void the expense of
                //filter matching since it may very well match on CoT type
                //Log.d(TAG + toString(), toString() + " not accepting usericon");
                return false;
            }
        }

        //run litmus test prior to self/child filters
        if (this.config != null &&
                this.config.hasLitmusFilter() &&
                !this.config.getLitmusFilter().onItemFunction(item)) {
            //Log.d(TAG + toString(), item.toString() + " did not pass litmus test " + this.config.getLitmusFilterString());
            return false;
        }

        return true;
    }

    /**
     * Run configured filters for this specific overlay
     *
     * @param item
     * @return
     */
    protected boolean accept(MapItem item) {
        if (!this.config.hasFilter())
            return false;

        if (this.config.getFilter().onItemFunction(item)) {
            //Log.d(TAG + toString(), "accept " + item.toString());
            if (this.config.isExclusive())
                item.setMetaString("filterOverlayId",
                        this.getIdentifier());
            return true;
        }

        //not accepted
        return false;
    }

    private boolean removeItem(MapItem item) {

        //Log.d(TAG, "addRemoveItem: " + item.toString());
        if (!item.getMetaBoolean("addToObjList", true))
            return false;

        boolean changed = false;
        synchronized (this.items) {
            if (this.items.containsKey(item)) {
                item.removeMetaData("filterOverlayId");
                changed = true;
                this.items.remove(item);
            }
        }
        if (changed && this.overlayManager != null)
            this.overlayManager.refreshList();

        //if not handled, give children an opportunity to accept
        if (!changed) {
            List<FilterMapOverlay> children = getOverlays();
            for (FilterMapOverlay child : children) {
                if (child.removeItem(item))
                    break;
            }
        }

        return this.config.isExclusive() && changed;
    }

    /**************************************************************************/

    public synchronized static MapGroup.MapItemsCallback getRejectFilter(
            MapView mapView) {
        try {
            if (rejectFilter == null)
                loadOverlayFilters(mapView);
            return rejectFilter;
        } catch (IOException e) {
            Log.e("FilterMapOverlay", "Unexpected IO error loading filters.",
                    e);
            return null;
        }
    }

    public static synchronized Collection<FilterMapOverlay> createDefaultFilterOverlays(
            MapView mapView) throws IOException, XmlPullParserException {
        if (overlayFilters == null)
            loadOverlayFilters(mapView);

        if (overlayFilters == null) {
            throw new IOException("Unable to load overlay filters");
        }

        if (_colorFilters == null) {
            throw new IOException("Unable to load color filters");
        }

        //add top level overlays
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        Collection<FilterMapOverlay> retval = new LinkedList<>();
        for (MapOverlayConfig config : overlayFilters.getOverlays()) {
            retval.add(new FilterMapOverlay(null, mapView, config, prefs,
                    _colorFilters));
        }

        return retval;
    }

    private static void loadOverlayFilters(MapView mapView) throws IOException {

        try (InputStream inputStream = ((MapActivity) mapView.getContext())
                .getMapAssets()
                .getInputStream(
                        Uri.parse("filters/overlays_hier.xml"))) {
            overlayFilters = MapOverlayFilters.Load(inputStream);

        }

        try {
            _colorFilters = FiltersConfig.parseFromStream(
                    mapView.getContext().getAssets()
                            .open("filters/team_filters.xml"));
            _colorFilters.setComparator("team",
                    new FiltersConfig.StringStartsWithComparator());
        } catch (SAXException e) {
            Log.e(TAG, "Failed to parse team color filters", e);
        }

        rejectFilter = MapGroupHierarchyListItem.ADD_TO_OBJ_LIST_FUNC;

        if (overlayFilters == null || !overlayFilters.isValid()) {
            Log.w(TAG, "Unable to load overlay filters");
            return;
        }

        //Other overlays will reject types being accepted by these overlays
        MapGroup.MapItemsCallback filter;
        for (MapOverlayFilter config : overlayFilters.getAllFilters()) {
            //entry = iter.next();
            if (config == null || !config.hasType())
                continue;

            filter = new TypeFilter(config.getType());
            rejectFilter = new MapGroup.MapItemsCallback.And(rejectFilter,
                    new MapGroup.MapItemsCallback.Not(filter));
        }
    }

    /**************************************************************************/
    public static class TypeFilter implements MapGroup.MapItemsCallback {
        private final Set<String> filters;

        public TypeFilter(String filter) {
            this.filters = new HashSet<>();
            this.filters.add(filter);
        }

        public TypeFilter(Set<String> filters) {
            this.filters = filters;
        }

        public TypeFilter(String[] filters) {
            this.filters = new HashSet<>();
            if (filters != null && filters.length > 0) {
                Collections.addAll(this.filters, filters);
            }
        }

        @Override
        public boolean onItemFunction(MapItem item) {
            final String type = item.getType();
            for (String filter : this.filters)
                if (type.startsWith(filter))
                    return true;
            return false;
        }
    }

    /**
     * Match a map item meta string against a string literal
     * 
     */
    public static class MetaStringFilter implements MapGroup.MapItemsCallback {
        private final String metaStringName;
        private final String metaStringValue;

        public MetaStringFilter(String name, String value) {
            this.metaStringName = name;
            this.metaStringValue = value;
        }

        @Override
        public boolean onItemFunction(MapItem item) {
            if (FileSystemUtils.isEmpty(metaStringName) ||
                    FileSystemUtils.isEmpty(metaStringValue) ||
                    !item.hasMetaValue(metaStringName))
                return false;

            //see if item matches specified string literal
            String itemValue = item.getMetaString(metaStringName, null);
            return metaStringValue.equals(itemValue);
        }
    }

    /**
     * Match a map item if it has a non-empty meta string
     * 
     */
    public static class MetaStringExistsFilter implements
            MapGroup.MapItemsCallback {
        private final String metaStringName;

        public MetaStringExistsFilter(String name) {
            this.metaStringName = name;
        }

        @Override
        public boolean onItemFunction(MapItem item) {
            if (FileSystemUtils.isEmpty(metaStringName))
                return false;

            return item.hasMetaValue(metaStringName);
        }
    }

    /**
     * Match a map item meta to a pref string
     * 
     */
    public static class PrefStringFilter implements MapGroup.MapItemsCallback {
        private final static String TAG = "PrefStringFilter";

        private final String metaStringName;
        private final String prefStringName;
        private final SharedPreferences prefs;

        public PrefStringFilter(SharedPreferences prefs, String metaStringName,
                String prefStringName) {
            this.prefs = prefs;
            this.metaStringName = metaStringName;
            this.prefStringName = prefStringName;
        }

        @Override
        public boolean onItemFunction(MapItem item) {
            if (FileSystemUtils.isEmpty(metaStringName) ||
                    FileSystemUtils.isEmpty(prefStringName) ||
                    !item.hasMetaValue(metaStringName) ||
                    !prefs.contains(prefStringName))
                return false;

            //see if item matches pref
            String prefValue = prefs.getString(prefStringName, null);
            if (FileSystemUtils.isEmpty(prefValue))
                return false;

            return prefValue.equals(item.getMetaString(metaStringName, null));
        }

        @Override
        public String toString() {
            return metaStringName + "," + prefStringName + ", "
                    + prefs.getString(prefStringName, null);
        }
    }

    /**
     * Filter to reject item if it has a valid iconsetPath. Used by FilterMapOverlay
     * to not include these markers
     */
    public static class IconsetMapItemsCallback implements
            MapGroup.MapItemsCallback {

        final Context _context;

        public IconsetMapItemsCallback(Context context) {
            _context = context;
        }

        @Override
        public boolean onItemFunction(MapItem item) {
            if (!(item instanceof Marker)
                    || !item.hasMetaValue(UserIcon.IconsetPath))
                return false;

            return UserIcon.IsValidIconsetPath(
                    item.getMetaString(UserIcon.IconsetPath, ""), _context);
        }
    }

    public List<MapItem> getItems() {
        synchronized (this.items) {
            return new ArrayList<>(this.items.keySet());
        }
    }

    public List<HierarchyListItem> getListItems() {
        synchronized (this.items) {
            return new ArrayList<>(this.items.values());
        }
    }

    public List<FilterMapOverlay> getOverlays() {
        synchronized (this.overlays) {
            return new ArrayList<>(this.overlays);
        }
    }

    /**
     * Get filtered child count, only those that have unfiltered leaf nodes
     *
     * @param filters
     * @return
     */
    //TODO should these but in higher level interface? e.g. MapGroupHierarchyListItem knows
    //nothing about the filters so the getChildCount and getDescendantCount don't always match
    // what is displayed when user drills down in
    public int getChildCount(ExportFilters filters) {
        int count = 0;
        List<MapItem> items = getItems();
        for (MapItem item : items) {
            if (!filters.filter(item))
                count++;
        }
        List<FilterMapOverlay> overlays = getOverlays();
        for (FilterMapOverlay child : overlays) {
            if (child.getDescendantCount(filters, false) > 0)
                count++;
        }
        return count;
    }

    /**
     * Get filtered descendant count. Optionally include child FilterMapOverlays in the count
     *
     * @param filters
     * @param bCountAllChildOverlays true to count child overlays and children
     *      false to count only child overlays which have descendant map items
     * @return
     */
    //TODO should these but in higher level interface? e.g. MapGroupHierarchyListItem knows
    //nothing about the filters so the getChildCount and getDescendantCount don't always match
    // what is displayed when user drills down in
    public int getDescendantCount(ExportFilters filters,
            boolean bCountAllChildOverlays) {
        int count = 0;
        List<MapItem> items = getItems();
        for (MapItem item : items) {
            if (!filters.filter(item))
                count++;
        }

        int childrenWithLeaf = 0, tempCount = 0;
        List<FilterMapOverlay> overlays = getOverlays();
        for (FilterMapOverlay child : overlays) {
            tempCount = child.getDescendantCount(filters,
                    bCountAllChildOverlays);
            if (tempCount > 0)
                childrenWithLeaf++;
            count += tempCount;
        }
        if (bCountAllChildOverlays)
            count += overlays.size();
        else
            count += childrenWithLeaf;

        return count;
    }

    @Override
    public String toString() {
        return this.getIdentifier();
    }
}
