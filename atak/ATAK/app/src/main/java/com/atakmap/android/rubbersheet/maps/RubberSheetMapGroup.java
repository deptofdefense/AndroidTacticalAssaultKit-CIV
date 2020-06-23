
package com.atakmap.android.rubbersheet.maps;

import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLMapItemFactory;

public class RubberSheetMapGroup extends DefaultMapGroup {

    public static final String GROUP_NAME = "Rubber Sheets";

    private final MapView _mapView;

    public RubberSheetMapGroup(MapView mapView) {
        _mapView = mapView;

        // Map item -> GL renderer
        GLMapItemFactory.registerSpi(GLRubberImage.SPI);

        // Add to the drawing group since the sheets extend off DrawingRectangle
        setFriendlyName(GROUP_NAME);
        setMetaBoolean("addToObjList", false);
        MapGroup drawingGroup = _mapView.getRootGroup().findMapGroup(
                "Drawing Objects");
        drawingGroup.addGroup(this);
    }

    public void dispose() {
        if (getParentGroup() != null)
            getParentGroup().removeGroup(this);

        GLMapItemFactory.unregisterSpi(GLRubberImage.SPI);
    }

    public void add(AbstractSheet s) {
        if (s != null) {
            addItem(s);
            addGroup(s.getChildMapGroup());
        }
    }
}
