
package com.atakmap.android.model.hierarchy;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hierarchy.HierarchyListAdapter;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.HierarchyListStateListener;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.Search;
import com.atakmap.android.hierarchy.action.Send;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.model.ModelImporter;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Visibility2;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModelFileHierarchyListItem extends AbstractHierarchyListItem2
        implements Visibility2, Delete, Export, Search, View.OnClickListener,
        FOVFilter.Filterable, HierarchyListStateListener {

    private static final String TAG = "ModelFileListItem";

    private final MapView view;
    private final Context context;
    private final FeatureDataStore2 dataStore;
    private final FeatureSet featureSet;
    private String title;
    private int childCount;
    private boolean opened;
    private final List<ModelInfoHierarchyListItem> baseChildren = new ArrayList<>();

    public ModelFileHierarchyListItem(MapView view, FeatureDataStore2 dataStore,
            FeatureSet featureSet, BaseAdapter listener) {
        this.view = view;
        this.context = view.getContext();
        this.dataStore = dataStore;
        File f = new File(featureSet.getName());
        title = f.getName();
        childCount = -1;
        this.featureSet = featureSet;
        this.asyncRefresh = true;
        this.reusable = true;
        this.listener = listener;
    }

    private FeatureQueryParameters getQueryParams() {
        FeatureQueryParameters params = new FeatureQueryParameters();
        params.featureSetFilter = new FeatureDataStore2.FeatureSetQueryParameters();
        params.featureSetFilter.ids = Collections.singleton(featureSet.getId());
        return params;
    }

    private void refreshBaseChildren() {
        List<ModelInfoHierarchyListItem> ret = new ArrayList<>();
        FeatureCursor result = null;
        try {
            result = dataStore.queryFeatures(getQueryParams());
            while (result.moveToNext()) {
                ret.add(new ModelInfoHierarchyListItem(view,
                        dataStore, result.get(), this));
            }
        } catch (DataStoreException e) {
            // XXX -
            childCount = 0;
        } finally {
            if (result != null)
                result.close();
        }
        synchronized (this) {
            this.baseChildren.clear();
            this.baseChildren.addAll(ret);
        }
    }

    // Get the single item this model feature set contains
    public ModelInfoHierarchyListItem getSingleChild() {
        FeatureCursor result = null;
        try {
            FeatureQueryParameters params = getQueryParams();
            params.limit = 1;
            result = dataStore.queryFeatures(params);
            if (result.moveToNext())
                return new ModelInfoHierarchyListItem(view, dataStore,
                        result.get(), this);
        } catch (DataStoreException ignored) {
        } finally {
            if (result != null)
                result.close();
        }
        return null;
    }

    @Override
    protected void refreshImpl() {
        // Grab base children
        List<ModelInfoHierarchyListItem> baseChildren;
        synchronized (this) {
            baseChildren = new ArrayList<>(this.baseChildren);
        }

        // Filter by FOV (or any other filters)
        List<HierarchyListItem> filtered = new ArrayList<>();
        for (ModelInfoHierarchyListItem item : baseChildren) {
            if (this.filter.accept(item))
                filtered.add(item);
        }

        // Sort
        sortItems(filtered);

        // Post to UI
        updateChildren(filtered);
    }

    @Override
    public String getUID() {
        return featureSet.getName();
    }

    @Override
    public String getTitle() {
        return this.title;
    }

    @Override
    public int getChildCount() {
        // Use the filtered count if the list is open
        if (this.opened)
            return super.getChildCount();

        // Total count
        return queryChildCount();
    }

    private int queryChildCount() {
        if (this.childCount == -1) {
            try {
                childCount = dataStore.queryFeaturesCount(getQueryParams());
            } catch (DataStoreException e) {
                // XXX -
                childCount = 0;
            }
        }
        return this.childCount;
    }

    public boolean hasSingleChild() {
        return queryChildCount() == 1;
    }

    @Override
    public int getDescendantCount() {
        return getChildCount();
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public Drawable getIconDrawable() {
        return context.getDrawable(R.drawable.import_folder_icon);
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                ? (ExtraHolder) v.getTag()
                : null;
        if (h == null) {
            h = new ExtraHolder();
            v = LayoutInflater.from(view.getContext()).inflate(
                    R.layout.model_list_item_extra, parent, false);
            h.delete = v.findViewById(R.id.model_delete);
            h.send = v.findViewById(R.id.model_send);
            v.setTag(h);
        }
        if (this.listener instanceof HierarchyListAdapter
                && ((HierarchyListAdapter) this.listener)
                        .getSelectHandler() != null) {
            // Hide send and delete buttons when selecting
            v.setVisibility(View.GONE);
            h.delete.setOnClickListener(null);
            h.send.setOnClickListener(null);
        } else {
            v.setVisibility(View.VISIBLE);
            h.delete.setOnClickListener(this);
            h.send.setOnClickListener(this);
        }
        return v;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Send model via Mission Package
        if (id == R.id.model_send) {
            File f = new File(this.featureSet.getName());
            if (!IOProviderFactory.exists(f))
                return;

            // Allow the handler to perform the send if available
            URIContentHandler handler = URIContentManager.getInstance()
                    .getHandler(f, ModelImporter.CONTENT_TYPE);
            if (handler != null && handler.isActionSupported(Send.class)
                    && ((Send) handler).promptSend())
                return;

            // Fallback
            SendDialog.Builder b = new SendDialog.Builder(view);
            b.setName(getTitle());
            b.setIcon(getIconDrawable());
            b.addFile(f);
            b.show();
        }

        // Remove model from local storage
        else if (id == R.id.model_delete)
            promptDelete();
    }

    private void promptDelete() {
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(R.string.are_you_sure);
        b.setIcon(R.drawable.ic_menu_delete);
        b.setMessage(context.getString(R.string.are_you_sure_delete2,
                getTitle()));
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        delete();
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                HierarchyListReceiver.REFRESH_HIERARCHY));
                    }
                });
        b.show();
    }

    private static class ExtraHolder {
        View delete, send;
    }

    @Override
    public boolean hideIfEmpty() {
        return true;
    }

    @Override
    public boolean isMultiSelectSupported() {
        return false;
    }

    /*************************************************************************/

    @Override
    public boolean onOpenList(HierarchyListAdapter om) {
        // Load children items when we enter the list
        refreshBaseChildren();
        this.opened = true;
        return false;
    }

    @Override
    public boolean onCloseList(HierarchyListAdapter om, boolean forceClose) {
        // Dispose children items when we exit in order to free up memory
        synchronized (this) {
            this.baseChildren.clear();
        }
        this.opened = false;
        return false;
    }

    @Override
    public int getVisibility() {
        FeatureDataStore2.FeatureQueryParameters params = getQueryParams();
        params.visibleOnly = true;
        int visibleCount;
        try {
            visibleCount = dataStore.queryFeaturesCount(params);
        } catch (DataStoreException e) {
            // XXX - ???
            return Visibility2.VISIBLE;
        }

        if (visibleCount == 0)
            return Visibility2.INVISIBLE;
        else if (visibleCount < getChildCount())
            return Visibility2.SEMI_VISIBLE;
        else
            return Visibility2.VISIBLE;
    }

    @Override
    public boolean setVisible(boolean visible) {
        FeatureSetQueryParameters params = new FeatureSetQueryParameters();
        params.ids = Collections.singleton(featureSet.getId());
        try {
            this.dataStore.setFeatureSetsVisible(params, visible);
            return visible;
        } catch (DataStoreException e) {
            return visible;
        }
    }

    @Override
    public boolean isVisible() {
        return (getVisibility() != Visibility2.INVISIBLE);
    }

    @Override
    public boolean delete() {
        String uri = this.featureSet.getName();
        if (!uri.startsWith("http:") && !uri.startsWith("https:")) {
            uri = Uri.fromFile(new File(uri)).toString();
        }

        Intent i = new Intent(
                ImportExportMapComponent.ACTION_DELETE_DATA);
        i.putExtra(ImportReceiver.EXTRA_CONTENT,
                ModelImporter.CONTENT_TYPE);
        i.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                "application/octet-stream");
        i.putExtra(ImportReceiver.EXTRA_URI, uri);
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

        File f = new File(this.featureSet.getName());
        if (!IOProviderFactory.exists(f))
            return null;

        URIContentHandler handler = URIContentManager.getInstance()
                .getHandler(f, ModelImporter.CONTENT_TYPE);
        if (handler != null && handler.isActionSupported(Export.class)
                && ((Export) handler).isSupported(target))
            return ((Export) handler).toObjectOf(target, filters);

        if (MissionPackageExportWrapper.class.equals(target)) {
            MissionPackageExportWrapper mp = new MissionPackageExportWrapper();
            mp.addFile(f);
            return mp;
        }
        return null;
    }

    @Override
    public boolean accept(FOVFilter.MapState fov) {
        FeatureQueryParameters params = getQueryParams();
        params.spatialFilter = GeometryFactory.fromEnvelope(new Envelope(
                fov.westBound, fov.southBound, 0,
                fov.eastBound, fov.northBound, 0));
        params.limit = 1;
        try {
            return dataStore.queryFeaturesCount(params) > 0;
        } catch (DataStoreException e) {
            return false;
        }
    }

    @Override
    public Set<HierarchyListItem> find(String terms) {
        Set<HierarchyListItem> ret = new HashSet<>();
        terms = terms.toLowerCase(LocaleUtil.getCurrent());
        if (this.opened) {
            // Search is occurring inside the list - add each item
            List<HierarchyListItem> children = getChildren();
            for (HierarchyListItem item : children) {
                // Search by title
                String title = item.getTitle()
                        .toLowerCase(LocaleUtil.getCurrent());
                if (title.contains(terms))
                    ret.add(item);
            }
        } else {
            // Search is occurring outside the list - add this file
            FeatureQueryParameters params = getQueryParams();
            params.names = Collections.singleton("%" + terms + "%");
            params.limit = 1;
            try {
                if (dataStore.queryFeaturesCount(params) > 0)
                    ret.add(this);
            } catch (DataStoreException ignored) {
            }
        }
        return ret;
    }
}
