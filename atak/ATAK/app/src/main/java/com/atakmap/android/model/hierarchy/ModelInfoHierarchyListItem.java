
package com.atakmap.android.model.hierarchy;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapCoreIntentsComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.model.opengl.GLModelLayer;
import com.atakmap.android.model.viewer.DetailedModelViewerDropdownReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.model.ModelInfo;

import java.util.ArrayList;
import java.util.Collections;

public class ModelInfoHierarchyListItem extends AbstractChildlessListItem
        implements Visibility, ItemClick, Delete, Export, FOVFilter.Filterable {

    private static final String TAG = "ModelInfoListItem";

    private final Context context;
    private final FeatureDataStore2 dataStore;
    private final Feature feature;
    private final ModelFileHierarchyListItem parent;
    //private HierarchyListAdapter om;

    public ModelInfoHierarchyListItem(MapView view, FeatureDataStore2 dataStore,
            Feature feature, ModelFileHierarchyListItem parent) {
        this.context = view.getContext();
        this.dataStore = dataStore;
        this.feature = feature;
        this.parent = parent;
    }

    @Override
    public String getTitle() {
        return feature.getName();
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        // Only show the send and delete buttons for standalone models
        // For model folders there's no need to show the buttons on every item
        if (!this.parent.hasSingleChild())
            return null;

        return this.parent.getExtraView(v, parent);
    }

    @Override
    public Drawable getIconDrawable() {
        return context.getDrawable(R.drawable.ic_model_building);
    }

    @Override
    public int getIconColor() {
        ModelInfo info = GLModelLayer.getModelInfo(this.feature);
        if (info == null)
            return Color.RED;
        else if (info.srid == -1)
            return Color.YELLOW;
        else
            return Color.WHITE;
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public boolean isMultiSelectSupported() {
        return false;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (clazz.equals(Visibility.class)) {
            // Visibility not supported for models that aren't geo-referenced
            ModelInfo info = GLModelLayer.getModelInfo(this.feature);
            if (info == null || info.srid == -1)
                return null;
        }
        return super.getAction(clazz);
    }

    @Override
    public boolean setVisible(boolean visible) {
        try {
            dataStore.setFeatureVisible(feature.getId(), visible);
            return visible;
        } catch (DataStoreException e) {
            return visible;
        }
    }

    @Override
    public boolean isVisible() {
        FeatureDataStore2.FeatureQueryParameters params = new FeatureDataStore2.FeatureQueryParameters();
        params.ids = Collections.singleton(feature.getId());
        params.visibleOnly = true;
        params.limit = 1;
        try {
            return dataStore.queryFeaturesCount(params) > 0;
        } catch (DataStoreException e) {
            return true;
        }
    }

    @Override
    public boolean onClick() {
        // if is has a location, zoom to otherwise open details/model viewer
        ModelInfo info = GLModelLayer.getModelInfo(this.feature);
        ArrayList<Intent> intents = new ArrayList<>(3);

        final String uid = "spatialdb::" + this.dataStore.getUri()
                + "::" + this.feature.getId();

        if (info != null) {
            // XXX - post 3.10, show details with attachments
            //intents.add(new Intent("com.atakmap.android.model.SHOW_DETAILS")
            //        .putExtra("targetUID", uid));
            if (info.srid == -1) {
                Intent intent = new Intent(
                        DetailedModelViewerDropdownReceiver.SHOW_3D_VIEW);
                intent.putExtra(DetailedModelViewerDropdownReceiver.EXTRAS_URI,
                        info.uri);
                intents.add(intent);
            }
        }
        if (info != null && info.location != null) {
            Intent intent = new Intent(MapCoreIntentsComponent.ACTION_PAN_ZOOM);
            intent.putExtra("point", info.location.toStringRepresentation());
            intent.putExtra("adjustForTerrain", true);
            if (MapView.getMapView().getMapResolution() > 5d)
                intent.putExtra("scale",
                        MapView.getMapView().mapResolutionAsMapScale(5d));
            intents.add(intent);
        } else if (feature.getGeometry() != null) {
            Geometry g = this.feature.getGeometry();
            Envelope bounds = g.getEnvelope();
            Intent intent = new Intent(MapCoreIntentsComponent.ACTION_PAN_ZOOM);
            if (g instanceof Point) {
                intent.putExtra("point",
                        (new GeoPoint(((Point) g).getY(), ((Point) g).getX()))
                                .toStringRepresentation());
                intent.putExtra("adjustForTerrain", true);
                if (MapView.getMapView().getMapResolution() > 5d)
                    intent.putExtra("scale",
                            MapView.getMapView().mapResolutionAsMapScale(5d));
            } else {
                intent.putExtra(
                        "shape",
                        new String[] {
                                new GeoPoint(bounds.maxY, bounds.minX)
                                        .toStringRepresentation(),
                                new GeoPoint(bounds.minY, bounds.maxX)
                                        .toStringRepresentation()
                        });
                intent.putExtra("adjustForTerrain", true);
            }
            intents.add(intent);
        }

        if (!intents.isEmpty())
            AtakBroadcast.getInstance().sendIntents(intents);

        return true;
    }

    @Override
    public boolean onLongClick() {
        return false;
    }

    @Override
    public boolean delete() {
        return this.parent.delete();
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return this.parent.isSupported(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        return this.parent.toObjectOf(target, filters);
    }

    @Override
    public boolean accept(FOVFilter.MapState fov) {
        Geometry geo = this.feature.getGeometry();
        if (geo == null)
            return false;
        Envelope e = geo.getEnvelope();
        return fov.intersects(new GeoBounds(e.minY, e.minX, e.maxY, e.maxX));
    }
}
