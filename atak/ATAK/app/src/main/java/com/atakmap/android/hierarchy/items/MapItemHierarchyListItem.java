
package com.atakmap.android.hierarchy.items;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.util.HashtagSet;
import com.atakmap.android.maps.Arrow;
import com.atakmap.android.maps.ILocation;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.hierarchy.action.Delete;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.tools.AtakLayerDrawableUtil;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import java.util.ArrayList;
import java.util.Collection;

public class MapItemHierarchyListItem extends AbstractChildlessListItem
        implements Visibility, GoTo, Delete, ILocation, Export,
        MapItemUser, HashtagContent {

    public static final String TAG = "MapItemHierarchyListItem";

    protected final MapItem item;
    protected final MapView mapView;

    public MapItemHierarchyListItem(MapView mapView, MapItem item) {
        this.mapView = mapView;
        this.item = item;
        setLocalData("showLocation",
                item.getMetaBoolean("omShowLocation", true));
    }

    /**************************************************************************/
    // Delete

    @Override
    public boolean delete() {
        if (this.item.getMetaBoolean("removable", true)) {
            // Remove from map group
            return this.item.removeFromGroup();
        } else if (this.item.hasMetaValue("deleteAction")) {
            // Special delete action
            Intent delete = new Intent(this.item
                    .getMetaString("deleteAction", ""));
            delete.putExtra("targetUID", this.item.getUID());
            AtakBroadcast.getInstance().sendBroadcast(delete);
            return true;
        }
        return false;
    }

    @Override
    public boolean goTo(boolean select) {
        // Mark this item as selected by overlay manager
        if (this.item != null)
            this.item.setMetaBoolean("overlay_manager_select", true);
        // Focus and bring up radial
        if (!MapTouchController.goTo(this.item, select)) {
            GeoPoint zoomLoc = getPoint(null);
            Intent intent = new Intent(
                    "com.atakmap.android.maps.ZOOM_TO_LAYER");
            intent.putExtra("point", zoomLoc.toString());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }

        return true;
    }

    /**
     * Show or hide the light gray highlight behind the marker for emphasis
     * 
     * @param highlight - true if the highlight should be shown, false if it should be hidden
     */
    public void highlight(boolean highlight) {
        if (this.item instanceof Marker) {
            Marker m = (Marker) item;
            if (highlight)
                m.setState(Marker.STATE_PRESSED_MASK);
            else
                m.setState(Marker.STATE_CANCELED_MASK);
        }
    }

    @Override
    public MapItem getMapItem() {
        return this.item;
    }

    @Override
    public String getTitle() {
        return ATAKUtilities.getDisplayName(this.item);
    }

    @Override
    public String getUID() {
        return this.item.getUID();
    }

    @Override
    public boolean isVisible() {
        return this.item.getVisible();
    }

    @Override
    public boolean setVisible(boolean visible) {
        Log.d(TAG, "*** ITEM [" + this.getTitle() + "] setVisible(" + visible
                + ")");
        if (visible != item.getVisible()) {
            this.item.setVisible(visible);
            this.item.persist(mapView.getMapEventDispatcher(), null,
                    getClass());
        }
        return true;
    }

    /**************************************************************************/
    // Location

    @Override
    public GeoPoint getPoint(GeoPoint point) {
        GeoPoint loc = null;

        if (this.item instanceof Arrow) {
            Arrow arrow = ((Arrow) this.item);
            loc = arrow.getPoint2().get();
        } else if (this.item instanceof Shape) {
            loc = ((Shape) this.item).getCenter().get();
        } else if (this.item instanceof PointMapItem) {
            loc = ((PointMapItem) this.item).getGeoPointMetaData().get();
        } else if (this.item instanceof AnchoredMapItem) {
            loc = ((AnchoredMapItem) this.item).getAnchorItem()
                    .getGeoPointMetaData().get();
        }

        // XXX -
        if (loc == null) {
            Log.w(TAG, "null location for " + item + ", default to 0, 0");
            loc = GeoPoint.ZERO_POINT;
        }

        if (point != null && point.isMutable()) {
            point.set(loc);
            return point;
        }
        return loc;
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        if (this.item instanceof Shape)
            return ((Shape) this.item).getBounds(bounds);
        else {
            GeoPoint p = getPoint(null);
            double lat = p.getLatitude();
            double lng = p.getLongitude();
            if (bounds != null) {
                bounds.set(lat, lng, lat, lng);
                return bounds;
            } else
                return new GeoBounds(lat, lng, lat, lng);
        }
    }

    @Override
    public Drawable getIconDrawable() {
        return item.getIconDrawable();
    }

    @Override
    public int getIconColor() {
        return item.getIconColor();
    }

    @Override
    public Object getUserObject() {
        return this.item;
    }

    @Override
    public View getExtraView(View v, ViewGroup parent) {
        ExtraHolder h = v != null && v.getTag() instanceof ExtraHolder
                ? (ExtraHolder) v.getTag()
                : null;
        if (h == null) {
            h = new ExtraHolder();
            v = h.attachments = new ImageButton(this.mapView.getContext(),
                    null, 0, R.style.darkButton);
            v.setPadding(5, 5, 5, 5);
            h.badge = (LayerDrawable) mapView.getContext().getDrawable(
                    R.drawable.attachment_badge);
            h.attachments.setImageDrawable(h.badge);
            v.setTag(h);
        }
        int count = this.item != null ? AttachmentManager
                .getNumberOfAttachments(this.item.getUID()) : 0;
        if (count > 0) {
            AtakLayerDrawableUtil.getInstance(this.mapView.getContext())
                    .setBadgeCount(h.badge, count);
            h.attachments.setVisibility(View.VISIBLE);
        } else {
            h.attachments.setVisibility(View.GONE);
        }
        h.attachments.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (item == null)
                    return;
                ArrayList<Intent> i = new ArrayList<>(5);
                i.add(new Intent("com.atakmap.android.maps.UNFOCUS"));
                i.add(new Intent("com.atakmap.android.maps.HIDE_DETAILS"));
                i.add(new Intent("com.atakmap.android.maps.FOCUS")
                        .putExtra("uid", item.getUID())
                        .putExtra("useTightZoom", true));
                i.add(new Intent("com.atakmap.android.maps.SHOW_DETAILS")
                        .putExtra("uid", item.getUID()));
                i.add(new Intent(ImageGalleryReceiver.VIEW_ATTACHMENTS)
                        .putExtra("uid", item.getUID()));
                AtakBroadcast.getInstance().sendIntents(i);
            }
        });
        return v;
    }

    private static class ExtraHolder {
        ImageButton attachments;
        LayerDrawable badge;
    }

    /**************************************************************************/
    // Object
    @Override
    public int hashCode() {
        int result = (item == null) ? 0 : item.hashCode();
        result = 31 * result + ((mapView == null) ? 0 : mapView.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MapItemHierarchyListItem) {
            MapItemHierarchyListItem other = (MapItemHierarchyListItem) o;
            return this.item.equals(other.item);
        } else {
            return false;
        }
    }

    @Override
    public boolean isSupported(Class<?> target) {
        if (item instanceof Exportable)
            return ((Exportable) item).isSupported(target);

        return false;
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        if (item instanceof Exportable) {
            return ((Exportable) item).toObjectOf(target, filters);
        }

        return null;
    }

    @Override
    public String getURI() {
        return item.getURI();
    }

    @Override
    public void setHashtags(Collection<String> tags) {
        if (item != null)
            item.setHashtags(tags);
    }

    @Override
    public HashtagSet getHashtags() {
        return item != null ? item.getHashtags() : null;
    }
}
