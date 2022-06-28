
package com.atakmap.android.drawing.details;

import android.graphics.Color;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.drawing.mapItems.MsdShape;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CoT detail for {@link MsdShape}
 */
public class ShapeMsdDetailHandler extends CotDetailHandler {

    private final MapView _mapView;

    public ShapeMsdDetailHandler(MapView mapView) {
        super("msd");
        _mapView = mapView;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event,
            CotDetail detail) {
        if (!(item instanceof Shape))
            return false;

        final MsdShape msd = getMsd((Shape) item);
        if (msd == null)
            return false;

        CotDetail cd = new CotDetail("msd");
        cd.setAttribute("range", String.valueOf(msd.getRange()));
        cd.setAttribute("color", String.valueOf(msd.getStrokeColor()));
        detail.addChild(cd);

        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        if (!(item instanceof Shape))
            return ImportResult.IGNORE;

        final Shape shape = (Shape) item;

        MsdShape msd = getMsd(shape);
        double range = parseDouble(detail.getAttribute("range"), 0);
        int color = parseInt(detail.getAttribute("color"), Color.RED);

        if (range > 0) {
            if (msd == null)
                msd = new MsdShape(_mapView, shape);
            msd.setRange(range);
            msd.setColor(color);
            msd.addToShapeGroup();
        } else if (msd != null)
            msd.removeFromGroup();

        return ImportResult.SUCCESS;
    }

    /**
     * Get the MSD boundary shape for a given parent shape
     * @param shape Parent shape
     * @return MSD boundary shape or null if it doesn't exist
     */
    @Nullable
    private MsdShape getMsd(@NonNull Shape shape) {
        MapItem mi = _mapView.getMapItem(shape.getUID() + ".msd");
        return mi instanceof MsdShape ? (MsdShape) mi : null;
    }
}
