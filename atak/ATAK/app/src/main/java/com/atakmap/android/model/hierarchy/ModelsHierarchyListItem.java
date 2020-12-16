
package com.atakmap.android.model.hierarchy;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.model.ModelImporter;

import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.model.ModelMapComponent;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.OnDataStoreContentChangedListener;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.map.layer.feature.FeatureLayer3;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModelsHierarchyListItem extends AbstractHierarchyListItem2
        implements Visibility2, Delete, Export, Search,
        OnDataStoreContentChangedListener {

    private static final String TAG = "ModelsHierarchyListItem";

    private final MapView view;
    private final FeatureLayer3 layer;
    private final FeatureDataStore2 dataStore;
    private boolean vizSupported;
    private final List<HierarchyListItem> baseChildren = new ArrayList<>();

    public ModelsHierarchyListItem(MapView view, FeatureLayer3 layer,
            BaseAdapter listener) {
        this.view = view;
        this.layer = layer;
        this.dataStore = layer.getDataStore();
        this.asyncRefresh = true;
        this.reusable = true;
        this.listener = listener;
        refreshBaseChildren();
        this.dataStore.addOnDataStoreContentChangedListener(this);
    }

    @Override
    public void dispose() {
        this.dataStore.removeOnDataStoreContentChangedListener(this);
    }

    @Override
    public String getTitle() {
        return ModelMapComponent.NAME;
    }

    @Override
    public int getPreferredListIndex() {
        return 10;
    }

    @Override
    public int getDescendantCount() {
        int retval = 0;
        try {
            retval += this.dataStore.queryFeaturesCount(null);
        } catch (DataStoreException ignored) {

        }
        return retval;
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public Drawable getIconDrawable() {
        return this.view.getContext().getDrawable(
                R.drawable.ic_model_building);
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    private void refreshBaseChildren() {
        List<HierarchyListItem> ret = new ArrayList<>();
        FeatureSetCursor result = null;
        try {
            result = dataStore.queryFeatureSets(null);
            while (result.moveToNext()) {
                ModelFileHierarchyListItem file = new ModelFileHierarchyListItem(
                        view,
                        dataStore, result.get(), listener);
                if (file.hasSingleChild())
                    ret.add(file.getSingleChild());
                else
                    ret.add(file);
            }
        } catch (DataStoreException e) {
            Log.e(TAG, "Failed to refresh models", e);
        } finally {
            if (result != null)
                result.close();
        }
        synchronized (this) {
            this.baseChildren.clear();
            this.baseChildren.addAll(ret);
        }
    }

    @Override
    protected void refreshImpl() {
        // Grab base children
        List<HierarchyListItem> baseChildren;
        synchronized (this) {
            baseChildren = new ArrayList<>(this.baseChildren);
        }

        // Filter by FOV (or any other filters)
        boolean vizSupported = false;
        List<HierarchyListItem> filtered = new ArrayList<>();
        for (HierarchyListItem item : baseChildren) {
            if (this.filter.accept(item)) {
                vizSupported |= item.getAction(Visibility.class) != null;
                filtered.add(item);
            }
        }

        // Sort
        sortItems(filtered);

        // Post to UI
        this.vizSupported = vizSupported;
        updateChildren(filtered);
    }

    @Override
    public boolean hideIfEmpty() {
        return true;
    }

    @Override
    public boolean isMultiSelectSupported() {
        return true;
    }

    /************************************************************************/

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if ((clazz.equals(Visibility.class)
                || clazz.equals(Visibility2.class)) && !this.vizSupported) {
            // Hide the visibility toggle if none of the children
            // support visibility
            return null;
        }
        return super.getAction(clazz);
    }

    @Override
    public boolean setVisible(boolean visible) {
        try {
            this.dataStore.setFeatureSetsVisible(null, visible);
            return visible;
        } catch (DataStoreException e) {
            return visible;
        }
    }

    @Override
    public boolean delete() {
        Intent i = new Intent(
                ImportExportMapComponent.ACTION_DELETE_DATA);
        i.putExtra(ImportReceiver.EXTRA_CONTENT,
                ModelImporter.CONTENT_TYPE);
        i.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                "application/octet-stream");
        i.putExtra(ImportReceiver.EXTRA_URI, ModelImporter.URI_ALL_MODELS);
        AtakBroadcast.getInstance().sendBroadcast(i);
        return true;
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        if (getChildCount() == 0 || !isSupported(target))
            return null;

        if (MissionPackageExportWrapper.class.equals(target)) {
            MissionPackageExportWrapper f = new MissionPackageExportWrapper();
            List<String> paths = f.getFilepaths();
            for (HierarchyListItem item : getChildren()) {
                if (!(item instanceof Export))
                    continue;
                Object o = ((Export) item).toObjectOf(target, filters);
                if (!(o instanceof MissionPackageExportWrapper))
                    continue;
                List<String> itemPaths = ((MissionPackageExportWrapper) o)
                        .getFilepaths();
                if (FileSystemUtils.isEmpty(itemPaths))
                    continue;
                paths.addAll(itemPaths);
            }
            if (FileSystemUtils.isEmpty(paths))
                return null;
            return f;
        }

        return null;
    }

    /*************************************************************************/

    @Override
    public Set<HierarchyListItem> find(String terms) {
        Set<HierarchyListItem> ret = new HashSet<>();
        terms = terms.toLowerCase(LocaleUtil.getCurrent());

        List<HierarchyListItem> children = getChildren();
        for (HierarchyListItem item : children) {
            // Search by title
            String title = item.getTitle().toLowerCase(LocaleUtil.getCurrent());
            if (title.contains(terms))
                ret.add(item);

            // Check model file folders
            else if (item instanceof Search && !((Search) item).find(terms)
                    .isEmpty())
                ret.add(item);
        }

        return ret;
    }

    @Override
    public void onDataStoreContentChanged(final FeatureDataStore2 dataStore) {
        this.view.post(new Runnable() {
            @Override
            public void run() {
                refreshBaseChildren();
                requestRefresh();
            }
        });
    }

    @Override
    public void onFeatureInserted(FeatureDataStore2 dataStore, long fid,
            FeatureDefinition2 def, long version) {

    }

    @Override
    public void onFeatureUpdated(FeatureDataStore2 dataStore, long fid,
            int modificationMask, String name, Geometry geom, Style style,
            AttributeSet attribs, int attribsUpdateType) {

    }

    @Override
    public void onFeatureDeleted(FeatureDataStore2 dataStore, long fid) {

    }

    @Override
    public void onFeatureVisibilityChanged(FeatureDataStore2 dataStore,
            long fid, boolean visible) {

    }
}
