
package com.atakmap.android.overlay;

import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListItem2;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GroupDelete;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.Map;
import java.util.Set;

/**
 * Wrapper to group child overlays
 * 
 * 
 */
public class MapOverlayParent extends AbstractMapOverlay2 {

    private static final String TAG = "MapOverlayParent";

    private final List<MapOverlay> overlays;
    private final Map<MapOverlay, HierarchyListItem> itemMap;
    private final MapView mapView;
    private final String id;
    private final String name;
    private final String iconUri;
    private final int order;
    private final boolean alwaysVisible;
    private ListModelImpl listModel;

    public MapOverlayParent(MapView mapView, String id, String name,
            String iconUri, int order, boolean alwaysVisible) {
        this.mapView = mapView;
        this.id = id;
        this.name = name;
        this.iconUri = iconUri;
        this.order = order;
        this.alwaysVisible = alwaysVisible;
        this.overlays = new ArrayList<>();
        this.itemMap = new HashMap<>();
    }

    public boolean add(MapOverlay overlay) {
        synchronized (this.overlays) {
            if (this.overlays.contains(overlay))
                return false;

            return this.overlays.add(overlay);
        }
    }

    public MapOverlay get(String id) {
        synchronized (this.overlays) {
            MapOverlay overlay = null;
            for (MapOverlay o : this.overlays) {
                if (o.getIdentifier().equals(id)) {
                    overlay = o;
                    break;
                }
            }
            return overlay;
        }
    }

    public List<MapOverlay> getOverlays() {
        synchronized (this.overlays) {
            return new ArrayList<>(this.overlays);
        }
    }

    public void remove(MapOverlay overlay) {
        synchronized (this.overlays) {
            this.overlays.remove(overlay);
        }
    }

    public MapOverlay remove(String id) {
        synchronized (this.overlays) {
            MapOverlay overlay = get(id);
            this.overlays.remove(overlay);
            return overlay;
        }
    }

    public void clear() {
        synchronized (this.overlays) {
            this.overlays.clear();
        }
    }

    @Override
    public String getIdentifier() {
        return id;
    }

    @Override
    public String getName() {
        return name;
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
            long capabilities, HierarchyListFilter filter) {
        if (this.listModel == null)
            this.listModel = new ListModelImpl(adapter, capabilities, filter);
        else
            this.listModel.refresh(adapter, filter);
        return this.listModel;
    }

    public class ListModelImpl extends AbstractHierarchyListItem2 implements
            Visibility2, GroupDelete, Search, Export {

        private static final String TAG = "MapOverlayParent.ListModelImpl";
        private final long capabilities;
        private boolean noViz = false;
        private int deepCount = -1;

        public ListModelImpl(BaseAdapter adapter, long capabilities,
                HierarchyListFilter filter) {
            this.capabilities = capabilities;
            this.asyncRefresh = true;
            refresh(adapter, filter);
        }

        @Override
        public int getPreferredListIndex() {
            return MapOverlayParent.this.order;
        }

        @Override
        public String getIconUri() {
            return MapOverlayParent.this.iconUri;
        }

        @Override
        public String getTitle() {
            return MapOverlayParent.this.getName();
        }

        @Override
        public boolean setVisible(boolean visible) {
            boolean retval = false;
            List<Visibility> actions = getChildActions(Visibility.class);
            for (Visibility viz : actions)
                retval |= viz.setVisible(visible);
            return retval;
        }

        @Override
        public int getDescendantCount() {
            List<HierarchyListItem> children = getChildren();
            if (this.deepCount == -1) {
                //get descendant children
                int cnt = 0;
                for (HierarchyListItem item : children) {
                    if (item != null)
                        cnt += item.getDescendantCount();
                }
                this.deepCount = cnt;
            }
            return children.size() + this.deepCount;
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            // Remove visibility toggle if none of the children use one
            if (this.noViz && (clazz.equals(Visibility.class)
                    || clazz.equals(Visibility2.class)))
                return null;
            return super.getAction(clazz);
        }

        @Override
        public Object getUserObject() {
            return MapOverlayParent.this;
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        protected void refreshImpl() {
            Map<MapOverlay, HierarchyListItem> map;
            List<MapOverlay> removed;
            synchronized (itemMap) {
                map = new HashMap<>(itemMap);
                removed = new ArrayList<>(itemMap.keySet());
            }
            List<HierarchyListItem> filtered = new ArrayList<>();
            List<MapOverlay> overlays = getOverlays();
            for (MapOverlay child : overlays) {
                removed.remove(child);
                HierarchyListItem listModel = map.get(child);
                if (listModel == null) {
                    if (child instanceof MapOverlay2)
                        listModel = ((MapOverlay2) child).getListModel(
                                this.listener, this.capabilities,
                                this.filter);
                    else
                        listModel = child.getListModel(
                                this.listener, this.capabilities,
                                this.filter.sort);
                    if (listModel == null)
                        continue;
                    map.put(child, listModel);
                }
                if (this.filter.accept(listModel)) {
                    if (listModel instanceof AbstractHierarchyListItem2)
                        ((AbstractHierarchyListItem2) listModel)
                                .syncRefresh(this.listener, this.filter);
                    filtered.add(listModel);
                }
            }
            for (MapOverlay rm : removed) {
                HierarchyListItem item = map.remove(rm);
                if (item instanceof HierarchyListItem2)
                    ((HierarchyListItem2) item).dispose();
            }
            synchronized (itemMap) {
                itemMap.clear();
                itemMap.putAll(map);
            }
            Collections.sort(filtered, HierarchyListAdapter.MENU_ITEM_COMP);

            // Check if any children have visibility toggles
            // so we know when to hide the parent toggle
            boolean noViz = !filtered.isEmpty();
            for (HierarchyListItem item : filtered) {
                if (item.getAction(Visibility.class) != null)
                    noViz = false;
            }
            this.noViz = noViz;
            this.deepCount = -1;

            updateChildren(filtered);
        }

        @Override
        public void dispose() {
            disposeChildren();
            List<HierarchyListItem> items;
            synchronized (itemMap) {
                items = new ArrayList<>(itemMap.values());
                itemMap.clear();
            }
            disposeItems(items);
        }

        @Override
        protected void disposeChildren() {
            synchronized (this.children) {
                this.children.clear();
            }
        }

        @Override
        public boolean hideIfEmpty() {
            return !alwaysVisible;
        }

        /**********************************************************************/
        // Search

        @Override
        public Set<HierarchyListItem> find(String terms) {
            terms = terms.toLowerCase(LocaleUtil.getCurrent());

            Set<HierarchyListItem> retval = new HashSet<>();
            List<Search> actions = new ArrayList<>();

            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children)
                actions.add(item.getAction(Search.class));

            for (Search ser : actions) {
                if (ser != null) {
                    Set<HierarchyListItem> t = ser.find(terms);
                    if (t != null && !t.isEmpty())
                        retval.addAll(t);
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

        public Folder toKml(ExportFilters filters)
                throws FormatNotSupportedException {

            Folder f = new Folder();
            f.setName(MapOverlayParent.this.getName());
            f.setFeatureList(new ArrayList<Feature>());

            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Exportable
                        && ((Exportable) item).isSupported(Folder.class)) {
                    Folder itemFolder = (Folder) ((Exportable) item)
                            .toObjectOf(Folder.class, filters);
                    if (itemFolder != null
                            && itemFolder.getFeatureList() != null
                            && itemFolder.getFeatureList().size() > 0) {
                        f.getFeatureList().add(itemFolder);
                    }
                }
            }

            if (f.getFeatureList().size() < 1) {
                return null;
            }

            return f;
        }

        public Folder toKmz(ExportFilters filters)
                throws FormatNotSupportedException {

            KMZFolder f = new KMZFolder();
            f.setName(MapOverlayParent.this.getName());
            f.setFeatureList(new ArrayList<Feature>());

            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                //Attempt KMZ, fall back on KML
                if (item instanceof Exportable
                        && ((Exportable) item).isSupported(KMZFolder.class)) {
                    KMZFolder itemFolder = (KMZFolder) ((Exportable) item)
                            .toObjectOf(KMZFolder.class, filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.getFeatureList().add(itemFolder);
                        if (itemFolder.hasFiles()) {
                            f.getFiles().addAll(itemFolder.getFiles());
                        }
                    }
                } else if (item instanceof Exportable
                        && ((Exportable) item).isSupported(Folder.class)) {
                    Folder itemFolder = (Folder) ((Exportable) item)
                            .toObjectOf(Folder.class, filters);
                    if (itemFolder != null
                            && itemFolder.getFeatureList() != null
                            && itemFolder.getFeatureList().size() > 0) {
                        f.getFeatureList().add(itemFolder);
                    }
                }
            }

            if (f.getFeatureList().size() < 1) {
                return null;
            }

            return f;
        }

        private MissionPackageExportWrapper toMissionPackage(
                ExportFilters filters) throws FormatNotSupportedException {
            MissionPackageExportWrapper f = new MissionPackageExportWrapper();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Exportable
                        && ((Exportable) item)
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

            if (f.getUIDs().size() < 1) {
                return null;
            }

            return f;
        }

        private OGRFeatureExportWrapper toOgrGeomtry(ExportFilters filters)
                throws FormatNotSupportedException {
            OGRFeatureExportWrapper f = new OGRFeatureExportWrapper(
                    MapOverlayParent.this.getName());
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Exportable
                        && ((Exportable) item)
                                .isSupported(OGRFeatureExportWrapper.class)) {
                    OGRFeatureExportWrapper itemFolder = (OGRFeatureExportWrapper) ((Exportable) item)
                            .toObjectOf(OGRFeatureExportWrapper.class,
                                    filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.addGeometries(itemFolder);
                    }
                }
            }

            if (f.isEmpty()) {
                return null;
            }

            return f;
        }

        private GPXExportWrapper toGpx(ExportFilters filters)
                throws FormatNotSupportedException {
            GPXExportWrapper f = new GPXExportWrapper();
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                if (item instanceof Exportable
                        && ((Exportable) item)
                                .isSupported(GPXExportWrapper.class)) {
                    GPXExportWrapper itemFolder = (GPXExportWrapper) ((Exportable) item)
                            .toObjectOf(GPXExportWrapper.class, filters);
                    if (itemFolder != null && !itemFolder.isEmpty()) {
                        f.add(itemFolder);
                    }
                }
            }

            if (f.isEmpty()) {
                return null;
            }

            return f;
        }
    } //end ListModelImpl

    /**
     * Fetch and return MapOverlayParent with specified ID.
     * If it does not exists, then create it, add it, return it
     * 
     * @param mapView the map view needed for looking up the map overlay
     * @param id the identifier usedto identify the map overlay
     * @param name the parent name
     * @param iconUri the icon name for the parent
     * @param order the ordering as an integer
     * @param alwaysVisible true if the parent is always visible
     * @return the overlay parent
     */
    public static MapOverlayParent getOrAddParent(MapView mapView, String id,
            String name,
            String iconUri, int order, boolean alwaysVisible) {
        MapOverlay existing = mapView.getMapOverlayManager().getOverlay(id);
        if (existing == null) {
            //Log.d(TAG, "Creating MapOverlayParent: " + id);
            MapOverlayParent parent = new MapOverlayParent(mapView, id, name,
                    iconUri, order, alwaysVisible);
            mapView.getMapOverlayManager().addOverlay(parent);
            return parent;
        } else if (!(existing instanceof MapOverlayParent)) {
            Log.w(TAG, "Overlay already exists, but not a MapOverlayParent: "
                    + id);
            return null;
        } else {
            //Log.d(TAG, "MapOverlayParent already exists: " + id)
            return (MapOverlayParent) existing;
        }
    }

    public static MapOverlayParent getParent(MapView mapView, String id) {
        MapOverlay existing = mapView.getMapOverlayManager().getOverlay(id);
        if (existing == null) {
            return null;
        } else if (!(existing instanceof MapOverlayParent)) {
            Log.w(TAG, "Overlay already exists, but not a MapOverlayParent: "
                    + id);
            return null;
        } else {
            //Log.d(TAG, "MapOverlayParent already exists: " + id)
            return (MapOverlayParent) existing;
        }
    }
}
