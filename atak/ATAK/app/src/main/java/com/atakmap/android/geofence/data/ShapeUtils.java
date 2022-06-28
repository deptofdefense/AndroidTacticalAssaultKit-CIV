
package com.atakmap.android.geofence.data;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapCoreIntentsComponent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Helper class to handle various shape types
 * Each currently with their own implementation nuances
 *
 *
 */
public class ShapeUtils {

    private static final String TAG = "ShapeUtils";

    /**
     * Given an intent, get the uid for the shape.
     * @param intent the intent that references a shape uid
     * @return the uid for the shape
     */
    public static String getShapeUID(Intent intent) {
        if (!FileSystemUtils.isEmpty(intent.getStringExtra("shapeUID")))
            return intent.getStringExtra("shapeUID");
        else if (!FileSystemUtils.isEmpty(intent.getStringExtra("assocSetUID")))
            return intent.getStringExtra("assocSetUID");
        else
            return intent.getStringExtra("uid");
    }

    /**
     * Given a map item, return the uid for the associated shape
     * @param item the map item
     * @return the uid for the associated shape
     */
    public static String getShapeUID(MapItem item) {
        if (item != null && item.hasMetaValue("shapeUID"))
            return item.getMetaString("shapeUID", null);
        else if (item != null && item.hasMetaValue("assocSetUID"))
            return item.getMetaString("assocSetUID", null);
        else
            return null;
    }

    /**
     * XXX - For 3.0 - Given a MapItem which would be the center point for a shape,
     * return either the center point if no associated shape is found or return 
     * the associated shape.
     * Resolves the shape given a map item.
     * @param item the map item to resolve the shape from.
     */
    public static MapItem resolveShape(final MapItem item) {
        String suid = getShapeUID(item);
        MapGroup grp = item.getGroup();
        if (suid != null && grp != null) {
            while (grp.getParentGroup() != null)
                grp = grp.getParentGroup();

            MapItem sitem = grp.deepFindUID(suid);
            if (sitem != null) {
                Log.d(TAG, "found a shape: " + suid);
                return sitem;
            } else {
                Log.d(TAG, "did not find a shape: " + suid);
            }
        }
        return item;
    }

    /**
     * Given a Map Item produce an intent to zoom to the shape.
     * @param item the map item
     * @return the intent to zoom to the shape.
     */
    public static Intent getZoomShapeIntent(MapItem item) {

        item = resolveShape(item);

        if (item instanceof Shape) {
            //Log.d(TAG, "goTo Zooming to: " + item.getClass().getName());
            Intent intent = new Intent(MapCoreIntentsComponent.ACTION_PAN_ZOOM);
            GeoPointMetaData[] pts = ((Shape) item).getMetaDataPoints();
            String[] spts = new String[pts.length];
            for (int i = 0; i < pts.length; i++)
                spts[i] = pts[i].get().toStringRepresentation();

            intent.putExtra("shape", spts);
            return intent;
        } else {
            Log.w(TAG, "Unable to find goTo shape: " + item.getUID());

            if (item instanceof PointMapItem) {
                GeoPoint zoomLoc = ((PointMapItem) item).getPoint();
                Intent intent = new Intent(
                        "com.atakmap.android.maps.ZOOM_TO_LAYER");
                intent.putExtra("point", zoomLoc.toString());
                return intent;
            }
        }

        return null;
    }

    /**
     * Note, this is messy, but have to break this out by type due to inconsistent
     * behavior across shape types, and their center markers
     *
     * Find the reference shape
     *  Circle: the center marker (u-d-c-c)
     *  Rectangle: the Rectangle (u-d-r)
     *  DrawingShape
     *
     * @param view the mapview to use for getting the reference shape
     * @param bToast    True to toast GeoFence messages to user
     * @param uid the uid of the reference shape
     * @param group the group to use
     * @param shapeUID allow a shapeUID to be passed in optionally, e.g. from a radial menu intent
     * @return returns the reference shape map item.
     */
    public static MapItem getReferenceShape(final MapView view, boolean bToast,
            final String uid, final MapGroup group, final String shapeUID) {
        Log.d(TAG, "Looking for reference shape: " + uid);

        final MapItem item = group.deepFindUID(uid);
        if (item == null) {
            Log.v(TAG, "Unable to find initial map item: " + uid + " in: "
                    + group);
            return null;
        }

        if (item instanceof Shape)
            return item;

        MapItem shape = ATAKUtilities.findAssocShape(item);
        if (shape instanceof Shape)
            return shape;

        if (bToast)
            toast(view, R.string.geofence_unsupported_shape);

        return null;
    }

    private static void toast(MapView view, final int stringId) {
        final Context ctx = view.getContext();
        view.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, stringId, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Note, this is messy, but have to break this out by type due to inconsistent
     * behavior across shape types
     *
     * @param item the center point of the shape
     * @return a valid geopoint or null if no center exists.
     */
    public static GeoPointMetaData getShapeCenter(MapItem item) {
        item = resolveShape(item);
        if (item instanceof Shape)
            return ((Shape) item).getCenter();
        return null;
    }

    /**
     * Note, this is messy, but have to break this out by type due to inconsistent
     * behavior across shape types
     *
     * @param item the item to get the center marker for
     * @return the map item that is the anchor (center marker)
     */
    public static MapItem getShapeCenterMarker(MapItem item) {
        item = resolveShape(item);
        if (item instanceof AnchoredMapItem)
            return ((AnchoredMapItem) item).getAnchorItem();
        return null;
    }

}
