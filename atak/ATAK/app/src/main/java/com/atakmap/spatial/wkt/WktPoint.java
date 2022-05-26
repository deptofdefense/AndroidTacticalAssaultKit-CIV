
package com.atakmap.spatial.wkt;

import android.graphics.Rect;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyle;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class WktPoint extends WktGeometry {

    private static final String TAG = "WktPoint";
    final List<Double> coords;

    private WktPoint(List<Double> coords) {
        this.coords = coords;
    }

    public static WktPoint fromText(String text) {
        String[] coords = text.replaceAll("[^\\d,\\s-.]", "").trim()
                .split("\\s");
        List<Double> ret = new LinkedList<>();
        for (String coord : coords) {
            if (!coord.isEmpty()) {
                try {
                    Double d = Double.parseDouble(coord);
                    ret.add(d);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }
        if (ret.isEmpty())
            return null;
        return new WktPoint(ret);
    }

    static GeoPointMetaData getPoint(final double lat, final double lon) {
        try {
            return ElevationManager.getElevationMetadata(lat, lon, null);
        } catch (Exception e) {
            return GeoPointMetaData.wrap(new GeoPoint(lat, lon));
        }

    }

    public static int fromSpatiaLiteBlobGeometryClass(String name,
            ByteBuffer blob,
            FeatureStyle style, List<MapItem> items) {
        final double x = blob.getDouble();
        final double y = blob.getDouble();

        final GeoPointMetaData gp = getPoint(y, x);

        final Marker m = new Marker(gp, getUUID());

        m.setType("u-d-wkt");
        m.setMetaString("menu", "menus/immutable_point.xml");
        if (name != null) {
            m.setTitle(name);

            // XXX - hack to support search
            m.setMetaString("callsign", name);
        }
        if (style != null) {
            int iconColor = -1;
            String iconUri = null;

            if (style.symbol != null) {
                iconUri = style.symbol.id;
                iconColor = style.symbol.color;

                // XXX -
                if (iconUri != null
                        && (iconUri.contains("http://") || iconUri
                                .contains("https://"))) {
                    m.setMetaString("backupIconUri",
                            "asset:/icons/reference_point.png");
                }
            }
            if (iconUri == null || iconUri.trim().isEmpty()) {
                iconUri = "asset:/icons/reference_point.png";
            }

            Icon.Builder mib = new Icon.Builder()
                    .setImageUri(0, iconUri)
                    .setSize(32, 32)
                    .setAnchor(Icon.ANCHOR_CENTER, Icon.ANCHOR_CENTER)
                    .setColor(0, iconColor);
            m.setIcon(mib.build());
        } else {
            // Log.d(TAG, "Style was null");
        }
        items.add(m);
        return 1;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " " + coords;
    }

    public GeoPoint[] getCoordinates() {
        GeoPoint[] ret = new GeoPoint[1];
        if (coords.size() > 2)
            ret[0] = new GeoPoint(coords.get(1), coords.get(0),
                    coords.get(2));
        else if (coords.size() > 1)
            ret[0] = getPoint(coords.get(1), coords.get(0)).get();
        return ret;
    }

    @Override
    public void toMapItems(List<MapItem> items) {
        if (coords != null && coords.size() > 1) {
            GeoPoint gp;
            if (coords.size() > 2)
                gp = new GeoPoint(coords.get(1), coords.get(0), coords.get(2));
            else
                gp = getPoint(coords.get(1), coords.get(0)).get();

            Marker m = new Marker(gp, getUUID());
            m.setType("u-d-wkt");
            m.setMetaString("menu", "menus/immutable_point.xml");
            m.setMarkerHitBounds(new Rect(-32, -32, 32, 32));
            m.setClickable(true);
            if (name != null) {
                m.setTitle(name);
                m.setMetaString("callsign", name);
            }
            if (style != null) {
                int iconColor = -1;
                String iconUri = null;

                if (style.symbol != null) {
                    iconUri = style.symbol.id;
                    iconColor = style.symbol.color;

                    // XXX -
                    if (iconUri != null
                            && (iconUri.contains("http://") || iconUri
                                    .contains("https://"))) {
                        m.setMetaString("backupIconUri",
                                "asset:/icons/reference_point.png");
                    }
                }
                if (iconUri == null || iconUri.trim().isEmpty()) {
                    iconUri = "asset:/icons/reference_point.png";
                }

                Icon.Builder mib = new Icon.Builder()
                        .setImageUri(0, iconUri)
                        .setSize(32, 32)
                        .setAnchor(Icon.ANCHOR_CENTER, Icon.ANCHOR_CENTER)
                        .setColor(0, iconColor);
                m.setIcon(mib.build());
            } else {
                // Log.d(TAG, "Style was null");
            }
            items.add(m);
        }
    }

}
