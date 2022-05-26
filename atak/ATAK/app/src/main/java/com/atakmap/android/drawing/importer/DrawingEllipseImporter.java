
package com.atakmap.android.drawing.importer;

import android.graphics.Color;
import android.os.Bundle;

import com.atakmap.android.drawing.mapItems.DrawingEllipse;
import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DrawingEllipseImporter extends DrawingImporter {

    public DrawingEllipseImporter(MapView mapView, MapGroup group,
            String type) {
        super(mapView, group, type);
    }

    public DrawingEllipseImporter(MapView mapView, MapGroup group) {
        this(mapView, group, DrawingEllipse.COT_TYPE);
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        if (existing != null && !(existing instanceof DrawingEllipse))
            return ImportResult.FAILURE;

        DrawingEllipse ellipse = (DrawingEllipse) existing;

        // Failed to create an empty ellipse
        if (ellipse == null)
            ellipse = new DrawingEllipse(_mapView, event.getUID());

        GeoPointMetaData center = new GeoPointMetaData(event.getGeoPoint());
        ellipse.setCenterPoint(center);

        // Requires the "shape" detail
        CotDetail shape = event.findDetail("shape");
        if (shape == null)
            return ImportResult.FAILURE;

        List<Ellipse> rings = new ArrayList<>();
        List<CotDetail> children = shape.getChildrenByName("ellipse");
        for (CotDetail d : children) {
            double minor = parseDouble(d.getAttribute("minor"), 0);
            double major = parseDouble(d.getAttribute("major"), 0);
            double angle = parseDouble(d.getAttribute("angle"), 0);

            // Extra property used to maintain the same display dimensions
            // across restarts when the width > length
            if (FileSystemUtils.isEquals(d.getAttribute("swapAxis"), "true")) {
                double length = minor;
                minor = major;
                major = length;
                angle = AngleUtilities.wrapDeg(angle - 90);
            }

            Ellipse e = new Ellipse(UUID.randomUUID().toString());
            e.setDimensions(center, minor / 2, major / 2, angle);
            rings.add(e);
        }
        // Failed to create any rings
        if (rings.isEmpty())
            return ImportResult.FAILURE;

        ellipse.setEllipses(rings);

        // Scan for links (style/color info and marker relations)
        boolean deferImport = false;
        List<CotDetail> links = shape.getChildrenByName("link");
        for (CotDetail link : links) {
            // Stroke color, stroke weight, fill color
            String type = link.getAttribute("type");
            if (type != null && type.equals("b-x-KmlStyle")) {
                CotDetail style = link.getFirstChildByName(0, "Style");
                parseStyleDetail(style, ellipse);
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
                    ellipse.setCenterMarker((Marker) mi);
            }
        }

        ImportResult res = super.importMapItem(ellipse, event, extras);
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
     * @param ellipse Ellipse
     */
    protected void parseStyleDetail(CotDetail style, DrawingEllipse ellipse) {
        if (style == null || ellipse == null)
            return;
        CotDetail ls = style.getFirstChildByName(0, "LineStyle");
        if (ls != null) {
            CotDetail color = ls.getFirstChildByName(0, "color");
            if (color != null)
                ellipse.setStrokeColor(parseColor("#" + color.getInnerText(),
                        Color.WHITE));
            CotDetail width = ls.getFirstChildByName(0, "width");
            if (width != null)
                ellipse.setStrokeWeight(parseDouble(
                        width.getInnerText(), 4));
        }
        CotDetail ps = style.getFirstChildByName(0, "PolyStyle");
        if (ps != null) {
            CotDetail color = ps.getFirstChildByName(0, "color");
            if (color != null)
                ellipse.setFillColor(parseColor("#" + color.getInnerText(),
                        0xFFFFFF));
        }
    }
}
