
package com.atakmap.android.features;

import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.android.coordoverlay.CoordOverlayMapReceiver;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.Location;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.user.FocusBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.Utils;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.BasicPointStyle;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.IconPointStyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FeatureHierarchyListItem extends
        AbstractChildlessListItem implements GoTo,
        Visibility, Location, Delete, View.OnClickListener {

    private final FeatureDataStore2 spatialDb;
    private final long fid;
    private final String name;
    private final GeoBounds bounds;
    private final boolean isPoint;
    private final GeoPointMetaData altitude;

    private final String iconUri;
    private final int iconColor;
    private final String uid;

    private final Set<Class<? extends Action>> actions;

    public FeatureHierarchyListItem(
            FeatureDataStore spatialDb,
            Feature feature) {
        this(Adapters.adapt(spatialDb), feature);

    }

    public FeatureHierarchyListItem(
            FeatureDataStore2 spatialDb,
            Feature feature) {

        this(
                spatialDb,
                feature.getId(),
                feature.getName(),
                iconUriFromStyle(feature.getStyle()),
                colorFromStyle(feature.getStyle()),
                feature.getGeometry() instanceof Point,
                feature.getGeometry().getEnvelope().minX,
                feature.getGeometry().getEnvelope().minY,
                feature.getGeometry().getEnvelope().maxX,
                feature.getGeometry().getEnvelope().maxY);
    }

    public FeatureHierarchyListItem(
            FeatureDataStore2 spatialDb,
            long fid,
            String name,
            String iconUri,
            int iconColor,
            boolean isPoint,
            double minX,
            double minY,
            double maxX,
            double maxY) {

        this.spatialDb = spatialDb;
        this.fid = fid;
        this.name = name;
        this.isPoint = isPoint;
        this.bounds = new GeoBounds(maxY, minX, minY, maxX);

        this.iconUri = iconUri;
        this.iconColor = iconColor;

        this.uid = "spatialdb::" + this.spatialDb.getUri()
                + "::" + this.fid;

        // XXX - icon

        this.actions = new HashSet<>();
        if ((spatialDb.getVisibilityFlags()
                & FeatureDataStore2.VISIBILITY_SETTINGS_FEATURE) == FeatureDataStore2.VISIBILITY_SETTINGS_FEATURE)
            this.actions.add(Visibility.class);
        if ((spatialDb.getModificationFlags()
                & FeatureDataStore2.MODIFY_FEATURESET_FEATURE_DELETE) == FeatureDataStore2.MODIFY_FEATURESET_FEATURE_DELETE)
            this.actions.add(Delete.class);

        this.actions.add(GoTo.class);

        GeoPointMetaData alt;
        try {
            Feature f = Utils.getFeature(spatialDb, fid);
            if (f.getGeometry() instanceof Point) {
                alt = FeatureHierarchyUtils
                        .getAltitude(((Point) f.getGeometry()),
                                f.getAltitudeMode());
            } else {
                GeoPoint center = this.bounds.getCenter(null);
                alt = new GeoPointMetaData(center);
            }
        } catch (DataStoreException dse) {
            alt = new GeoPointMetaData();
        }
        altitude = alt;

    }

    // XXX - Checking style is not the best way to get the icon type
    // i.e. Extruded open polylines may have a fill style, which here returns
    // the closed shape icon instead of the open polyline icon. If we really
    // wanted to get fancy we could have a multi-layer drawable icon that
    // shows both the stroke and fill color, but for now this method is satisfactory.
    public static String iconUriFromStyle(Style style) {
        if (style == null)
            return null;

        if (style instanceof CompositeStyle) {
            style = findBestIconStyle((CompositeStyle) style);
            if (style == null)
                return null;
        }

        if (style instanceof IconPointStyle) {
            String uri = ((IconPointStyle) style).getIconUri();
            return uri != null ? uri
                    : ATAKUtilities.getResourceUri(R.drawable.generic);
        } else if (style instanceof BasicPointStyle)
            return ATAKUtilities.getResourceUri(R.drawable.generic);
        else if (style instanceof BasicStrokeStyle)
            return ATAKUtilities.getResourceUri(R.drawable.polyline);
        else if (style instanceof BasicFillStyle)
            return ATAKUtilities.getResourceUri(R.drawable.shape);

        return null;
    }

    public static int colorFromStyle(Style style) {
        if (style instanceof CompositeStyle)
            style = findBestIconStyle((CompositeStyle) style);

        int color = Color.WHITE;
        if (style instanceof IconPointStyle)
            color = ((IconPointStyle) style).getColor();
        else if (style instanceof BasicPointStyle)
            color = ((BasicPointStyle) style).getColor();
        else if (style instanceof BasicStrokeStyle)
            color = ((BasicStrokeStyle) style).getColor();
        else if (style instanceof BasicFillStyle)
            color = ((BasicFillStyle) style).getColor();

        // Discard alpha since this color is used for icons
        color = (color & 0xFFFFFF) | 0xFF000000;

        return color;
    }

    /**
     * Get the best style for representing a feature in icon form
     * The order is fill > stroke > point/other
     * @param style Composite style
     * @return Dominant style or null if none found
     */
    private static Style findBestIconStyle(CompositeStyle style) {
        Style dominant = null;
        int numStyles = style.getNumStyles();
        for (int i = 0; i < numStyles; i++) {
            Style s = style.getStyle(i);
            if (s instanceof CompositeStyle)
                s = findBestIconStyle((CompositeStyle) s);
            if (s instanceof BasicFillStyle)
                return s;
            else if (s instanceof BasicStrokeStyle || dominant == null)
                dominant = s;
        }
        return dominant;
    }

    protected GeoBounds getBounds() {
        return bounds;
    }

    @Override
    public int getIconColor() {
        return this.iconColor;
    }

    @Override
    public String getIconUri() {
        return this.iconUri;
    }

    @Override
    public String getTitle() {
        return this.name;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        // XXX - legacy guard -- need to find out why we would get null bounds?
        if (clazz.equals(GoTo.class) && this.bounds == null)
            return null;

        if (this.actions.contains(clazz)) {
            return clazz.cast(this);
        } else {
            return null;
        }
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public View getExtraView(View convertView, ViewGroup parent) {

        // Get/create view holder
        FeatureExtraHolder h = FeatureExtraHolder.get(convertView, parent);
        if (h == null)
            return null;

        // Button for editing this feature
        h.edit.setVisibility(View.VISIBLE);
        h.edit.setOnClickListener(this);

        // Hide pan and send
        h.pan.setVisibility(View.GONE);
        h.send.setVisibility(View.GONE);

        return h.root;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.editButton) {
            // TODO: Prompt to edit feature
            Intent i = new Intent(FeatureEditDropdownReceiver.SHOW_EDIT);
            i.putExtra("fid", this.fid);
            i.putExtra("title", this.getTitle());
            AtakBroadcast.getInstance().sendBroadcast(i);
        }
    }

    /**************************************************************************/
    // Visibility

    @Override
    public boolean setVisible(boolean visible) {
        try {
            this.spatialDb.setFeatureVisible(this.fid, visible);
        } catch (DataStoreException dse) {
            return true;
        }
        return true;
    }

    @Override
    public boolean isVisible() {
        try {
            return Utils.isFeatureVisible(this.spatialDb, this.fid);
        } catch (DataStoreException dse) {
            return false;
        }
    }

    /**************************************************************************/
    // Go To

    @Override
    public boolean goTo(boolean select) {
        if (this.bounds == null)
            return false;

        ArrayList<Intent> intents = new ArrayList<>(3);
        intents.add(new Intent(FocusBroadcastReceiver.FOCUS)
                .putExtra("uid", uid).putExtra("useTightZoom", true));
        intents.add(new Intent(MapMenuReceiver.SHOW_MENU)
                .putExtra("uid", uid));
        intents.add(new Intent(CoordOverlayMapReceiver.SHOW_DETAILS)
                .putExtra("uid", uid));
        AtakBroadcast.getInstance().sendIntents(intents);
        return true;
    }

    /**************************************************************************/
    // Delete

    @Override
    public boolean delete() {
        try {
            this.spatialDb.deleteFeature(this.fid);
        } catch (DataStoreException ignored) {
        }
        return true;
    }

    /**************************************************************************/

    @Override
    public GeoPointMetaData getLocation() {
        GeoPoint location = GeoPoint.createMutable();
        location.set(Double.NaN, Double.NaN);
        location.set(altitude.get().getAltitude());
        if (this.bounds != null)
            this.bounds.getCenter(location);
        return GeoPointMetaData.wrap(location);
    }

    @Override
    public String getFriendlyName() {
        return this.getTitle();
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public MapOverlay getOverlay() {
        return null;
    }
}
