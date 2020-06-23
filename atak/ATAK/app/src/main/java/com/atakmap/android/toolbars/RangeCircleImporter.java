
package com.atakmap.android.toolbars;

import com.atakmap.android.drawing.importer.DrawingCircleImporter;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotEvent;

public class RangeCircleImporter extends DrawingCircleImporter {

    public RangeCircleImporter(MapView mapView, MapGroup group) {
        super(mapView, group, RangeCircle.COT_TYPE);
    }

    @Override
    protected DrawingCircle createCircle(CotEvent event) {
        return new RangeCircle(_mapView, event.getUID());
    }
}
