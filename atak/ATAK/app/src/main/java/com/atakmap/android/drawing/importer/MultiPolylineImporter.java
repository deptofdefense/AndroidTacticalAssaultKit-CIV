
package com.atakmap.android.drawing.importer;

import android.os.Bundle;

import com.atakmap.android.cot.CotUtils;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.ArrayList;
import java.util.List;

public class MultiPolylineImporter extends EditablePolylineImporter {

    private final static String TAG = "DrawingShapeImporter";

    public MultiPolylineImporter(MapView mapView, MapGroup group) {
        super(mapView, group, "u-d-f-m");
    }

    @Override
    protected ImportResult importMapItem(MapItem existing, CotEvent event,
            Bundle extras) {
        if (existing != null && !(existing instanceof MultiPolyline))
            return ImportResult.FAILURE;

        CotDetail detail = event.getDetail();
        if (detail == null)
            return ImportResult.FAILURE;

        MultiPolyline mp = (MultiPolyline) existing;

        //Make sure all the details we want exist
        List<DrawingShape> lines = new ArrayList<>();

        ChildShapeImporter imp = new ChildShapeImporter(_mapView, _group,
                lines);

        //Loop through and get all of the individual lines
        List<CotDetail> children = detail.getChildrenByName("link");
        for (CotDetail d : children) {
            if (d.getAttribute("relation") != null)
                continue;
            String linkLine = d.getAttribute("line");
            //Individual lines are passed over as strings of COT Events
            CotEvent e = CotEvent.parse(linkLine);
            //Parse the lines COT message
            // This also adds it to our lines list
            imp.importMapItem(findItem(e), e, extras);
        }

        String title = CotUtils.getCallsign(event);

        // Overwrite existing map item when possible
        // so we properly reset lines, colors, etc.
        if (mp == null) {
            MapGroup childGroup = _group.addGroup(title);
            mp = new MultiPolyline(_mapView, childGroup, lines,
                    event.getUID());
        }
        mp.setLines(lines);

        return super.importMapItem(mp, event, extras);
    }

    @Override
    protected int getNotificationIcon(MapItem item) {
        return R.drawable.multipolyline;
    }

    private static class ChildShapeImporter extends DrawingShapeImporter {

        private final List<DrawingShape> lines;

        ChildShapeImporter(MapView mapView, MapGroup group,
                List<DrawingShape> lines) {
            super(mapView, group);
            this.lines = lines;
        }

        @Override
        protected void addToGroup(MapItem item) {
            if (item instanceof DrawingShape)
                this.lines.add((DrawingShape) item);
        }

        @Override
        protected boolean persist(MapItem item, Bundle extras) {
            return false;
        }
    }
}
