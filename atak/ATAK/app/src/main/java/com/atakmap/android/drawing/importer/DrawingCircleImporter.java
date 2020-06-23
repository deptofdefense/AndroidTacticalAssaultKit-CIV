
package com.atakmap.android.drawing.importer;

import android.graphics.Color;
import android.os.Bundle;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.mapItems.DrawingEllipse;
import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.List;

public class DrawingCircleImporter extends DrawingImporter {

    // Used for drawing circles not made with ATAK or WinTAK (non-regular rings)
    private final DrawingEllipseImporter _ellipseImporter;

    public DrawingCircleImporter(MapView mapView, MapGroup group, String type) {
        super(mapView, group, type);
        _ellipseImporter = new DrawingEllipseImporter(mapView, group);
    }

    public DrawingCircleImporter(MapView mapView, MapGroup group) {
        this(mapView, group, DrawingCircle.COT_TYPE);
    }

    /**
     * Create a new empty circle
     * May also perform some other operations here, although any written
     * attributes may be overwritten later
     * @param event CoT event
     * @return New circle
     */
    protected DrawingCircle createCircle(CotEvent event) {
        return new DrawingCircle(_mapView, event.getUID(), event.getType());
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {

        // Defer to ellipse importer
        if (existing instanceof DrawingEllipse)
            return _ellipseImporter.importMapItem(existing, event, extras);

        if (existing != null && !(existing instanceof DrawingCircle))
            return ImportResult.FAILURE;

        DrawingCircle circle = (DrawingCircle) existing;

        // Failed to create an empty circle
        if (circle == null)
            circle = createCircle(event);
        if (circle == null)
            return ImportResult.FAILURE;

        // Requires the "shape" detail
        CotDetail shape = event.findDetail("shape");
        if (shape == null)
            return ImportResult.FAILURE;

        // Obtain the number of rings and base ring radius
        double minRadius = Double.MAX_VALUE;
        int numRings = 0;
        boolean isEllipse = false;
        List<CotDetail> children = shape.getChildrenByName("ellipse");
        for (CotDetail d : children) {
            double minor = parseDouble(d.getAttribute("minor"), 0);
            double major = parseDouble(d.getAttribute("major"), 0);
            double angle = CanvasHelper.deg360(parseDouble(
                    d.getAttribute("angle"), 0));

            // Check for irregular ellipse
            if (Double.compare(minor, major) != 0
                    || Double.compare(angle, 0) != 0) {
                isEllipse = true;
                break;
            }

            minRadius = Math.min(minRadius, minor);
            numRings++;
        }

        // Defer to ellipse importer
        if (isEllipse)
            return _ellipseImporter.importMapItem(existing, event, extras);

        // Failed to create any rings
        if (numRings <= 0)
            return ImportResult.FAILURE;

        // Prevent persists
        boolean archive = circle.hasMetaValue("archive");
        circle.toggleMetaData("archive", false);

        circle.setCenterPoint(new GeoPointMetaData(event.getGeoPoint()));
        circle.setRadius(minRadius);
        circle.setNumRings(numRings);

        // Scan for links (style/color info and marker relations)
        boolean deferImport = false;
        List<CotDetail> links = shape.getChildrenByName("link");
        for (CotDetail link : links) {
            // Stroke color, stroke weight, fill color
            String type = link.getAttribute("type");
            if (type != null && type.equals("b-x-KmlStyle")) {
                CotDetail style = link.getFirstChildByName(0, "Style");
                parseStyleDetail(style, circle);
            }

            // Linked items
            String uid = link.getAttribute("uid");
            String relation = link.getAttribute("relation");
            if (FileSystemUtils.isEmpty(uid)
                    || FileSystemUtils.isEmpty(relation))
                continue;

            // Hook center marker
            if (relation.equals("p-p-CenterAnchor")) {
                MapItem mi = findItem(uid);
                if (mi == null)
                    deferImport = true;
                else if (mi instanceof Marker)
                    circle.setCenterMarker((Marker) mi);
            }

            // Hook radius marker
            else if (relation.equals("p-p-RadiusAnchor")) {
                MapItem mi = findItem(uid);
                if (mi == null)
                    deferImport = true;
                else if (mi instanceof Marker)
                    circle.setRadiusMarker((Marker) mi);
            }
        }
        circle.toggleMetaData("archive", archive);

        ImportResult res = super.importMapItem(circle, event, extras);
        return res.getHigherPriority(deferImport ? ImportResult.DEFERRED
                : ImportResult.SUCCESS);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.ic_circle;
    }

    /**
     * Parse the color and weight style detail
     * Note that although 3.12 stores color details the same way as other
     * shapes, we still need to be backwards compatible
     * @param style Style detail
     * @param circle Circle
     */
    protected void parseStyleDetail(CotDetail style, DrawingCircle circle) {
        if (style == null || circle == null)
            return;
        CotDetail ls = style.getFirstChildByName(0, "LineStyle");
        if (ls != null) {
            CotDetail color = ls.getFirstChildByName(0, "color");
            if (color != null)
                circle.setStrokeColor(parseColor("#" + color.getInnerText(),
                        Color.WHITE));
            CotDetail width = ls.getFirstChildByName(0, "width");
            if (width != null)
                circle.setStrokeWeight(parseDouble(
                        width.getInnerText(), 4));
        }
        CotDetail ps = style.getFirstChildByName(0, "PolyStyle");
        if (ps != null) {
            CotDetail color = ps.getFirstChildByName(0, "color");
            if (color != null)
                circle.setFillColor(parseColor("#" + color.getInnerText(),
                        0xFFFFFF));
        }
    }
}
