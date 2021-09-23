
package com.atakmap.android.cot.detail;

import android.graphics.Color;

import com.atakmap.android.drawing.importer.EditablePolylineImporter;
import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Detail handler for markers with associated shapes
 * TAK shapes do not typically use this detail
 */
public class ShapeDetailHandler extends CotDetailHandler {

    public static final String TAG = "ShapeDetailHandler";
    private static final String STYLE_LINE_WIDTH = "3";

    private final MapView _mapView;
    private final MapGroup _group;

    ShapeDetailHandler(MapView mapView) {
        super("shape");
        _mapView = mapView;
        _group = _mapView.getRootGroup().findMapGroup("Drawing Objects");
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Marker;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        // No longer export circles this way
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        Marker marker = (Marker) item;
        CotDetail polyline = detail.getFirstChildByName(0, "polyline");
        if (polyline != null) {
            buildPolyline(marker, event, polyline);
            return ImportResult.SUCCESS;
        } else
            buildEllipse(marker, event, detail);
        return ImportResult.SUCCESS;
    }

    /**
     * Build an ellipse based on its CoT detail
     * An ellipse is different from a typical ATAK drawing circle
     * since it can have different major and minor axis
     * @param marker Center marker
     * @param event CoT event
     * @param detail CoT detail
     */
    private void buildEllipse(Marker marker, CotEvent event, CotDetail detail) {
        List<CotDetail> ellipses = detail.getChildrenByName("ellipse");
        for (CotDetail d : ellipses) {
            Ellipse ell = (Ellipse) _group.findItem("ownerUID",
                    event.getUID());
            if (ell == null) {
                ell = new Ellipse(UUID.randomUUID().toString());
                ell.setStyle(
                        Shape.STYLE_STROKE_MASK | Polyline.STYLE_CLOSED_MASK);
                ell.setMetaString("ownerUID", event.getUID());
                ell.setMetaBoolean("addToObjList", false);
            }

            double major = parseDouble(d.getAttribute("major"), 0.0);
            double minor = parseDouble(d.getAttribute("minor"), 0.0);
            double angle = parseDouble(d.getAttribute("angle"), 0.0);

            ell.setDimensions(getPoint(marker), minor / 2, major / 2, angle);

            //set color, default to white
            int color = _getColorAttr(d, "color");
            ell.setStrokeColor(color);

            //see if we have a fill color
            String fillColorStr = d.getAttribute("fillColor");
            if (!FileSystemUtils.isEmpty(fillColorStr)) {
                int fillColor = _getColorAttr(d, "fillColor");
                ell.setFillColor(fillColor);
                ell.setFillStyle(Shape.STYLE_FILLED_MASK);
            } else {
                ell.setFillColor(0);
                ell.setFillStyle(0);
            }

            //see if we have a line width
            String lineWidth = d.getAttribute("lineWidth");
            if (!FileSystemUtils.isEmpty(lineWidth)) {
                try {
                    double width = parseDouble(lineWidth, 3);
                    ell.setStrokeWeight(width);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Ignoring width: " + lineWidth);
                    ell.setStrokeWeight(1);
                }
            } else {
                ell.setStrokeWeight(1);
            }

            if (ell.getGroup() == null) {
                addListeners(marker, _group, ell);
                _group.addItem(ell);
            }
        }
    }

    /**
     * Build polyline based on "polyline" detail
     * For editable polylines see {@link EditablePolylineImporter}
     * @param marker Center marker
     * @param event CoT event
     * @param polyline Polyline CoT detail
     */
    private void buildPolyline(Marker marker, CotEvent event,
            CotDetail polyline) {
        Polyline poly = (Polyline) _group.findItem("ownerUID", event.getUID());
        if (poly == null) {
            poly = new Polyline(UUID.randomUUID().toString());
            poly.setMetaString("ownerUID", event.getUID());
            poly.setMetaBoolean("addToObjList", false);
            poly.setClickable(false);
        }

        int polyStyle = 0;

        String closed = polyline.getAttribute("closed");
        if (closed != null && (closed.toLowerCase(LocaleUtil.getCurrent())
                .equals("true") || closed.equals("1")))
            polyStyle |= Polyline.STYLE_CLOSED_MASK
                    | Polyline.STYLE_FILLED_MASK;

        ArrayList<GeoPointMetaData> points = new ArrayList<>();
        for (int i = 0; i < polyline.childCount(); ++i) {
            CotDetail v = polyline.getChild(i);
            if (v == null || !v.getElementName().equals("vertex"))
                continue;
            double lat = parseDouble(v.getAttribute("lat"), 0);
            double lng = parseDouble(v.getAttribute("lon"), 0);
            if (Double.isNaN(lat) || Double.isInfinite(lat) ||
                    Double.isNaN(lng) || Double.isInfinite(lng))
                return;

            String altString = v.getAttribute("hae");
            GeoPoint point;
            if (altString != null) {
                double alt = parseDouble(altString, GeoPoint.UNKNOWN);
                point = new GeoPoint(lat, lng, alt);
            } else
                point = new GeoPoint(lat, lng);
            points.add(GeoPointMetaData.wrap(point));
        }

        final int color = _getColorAttr(polyline, "color");
        poly.setStrokeColor(color);

        final String fillColorStr = polyline.getAttribute("fillColor");
        if (!FileSystemUtils.isEmpty(fillColorStr)) {
            int fillColor = _getColorAttr(polyline, "fillColor");
            poly.setFillColor(fillColor);
        } else {
            // legacy behavior
            poly.setFillColor(Color.argb(45, Color.red(color),
                    Color.green(color),
                    Color.blue(color)));
        }

        poly.setStyle(polyStyle | Polyline.STYLE_OUTLINE_STROKE_MASK);
        poly.setStrokeWeight(2d);
        poly.setPoints(points.toArray(new GeoPointMetaData[0]));
        Log.d(TAG, "Modified polyline for " + poly.getUID());

        if (poly.getGroup() == null) {
            addListeners(marker, _group, poly);
            _group.addItem(poly);
        }
    }

    private void addListeners(final Marker marker, final MapGroup group,
            final Shape shape) {
        marker.addOnGroupChangedListener(new MapItem.OnGroupChangedListener() {
            @Override
            public void onItemAdded(MapItem item, MapGroup markerGroup) {
            }

            @Override
            public void onItemRemoved(MapItem item, MapGroup markerGroup) {
                if (shape != null) {
                    Log.d(TAG, "Item " + item.getUID()
                            + " was removed, removing associated "
                            + shape.getClass().getSimpleName());
                    if (group != null)
                        group.removeItem(shape);
                    else
                        Log.w(TAG,
                                "Problem removing shape associated with item "
                                        + item.getUID());
                }
                item.removeOnGroupChangedListener(this);
            }
        });
    }

    private static int _getColorAttr(CotDetail detail, String attrName) {
        int color = Color.WHITE;
        String colorString = detail.getAttribute(attrName);
        if (colorString != null) {
            try {
                color = Color.parseColor(colorString);
            } catch (Exception ignored) {
                try {
                    color = Integer.parseInt(colorString);
                } catch (Exception ignore) {
                }
            }
        }

        return color;
    }
}
