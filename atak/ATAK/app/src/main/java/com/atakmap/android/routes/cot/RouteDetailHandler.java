
package com.atakmap.android.routes.cot;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Handler for a route detail under the "__routeinfo" node
 */
public abstract class RouteDetailHandler extends CotDetailHandler {

    private static final String DETAIL_TAG = "__routeinfo";

    protected final String _subName;

    protected RouteDetailHandler(String subName) {
        super(DETAIL_TAG);
        _subName = subName;
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Route;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        CotDetail routeInfo = event.findDetail(DETAIL_TAG);
        if (routeInfo == null) {
            routeInfo = new CotDetail(DETAIL_TAG);
            detail.addChild(routeInfo);
        }
        CotDetail child = toCotDetail((Route) item);
        if (child != null) {
            routeInfo.addChild(child);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        CotDetail subNode = detail.getFirstChildByName(0, _subName);
        if (subNode == null)
            return ImportResult.FAILURE;
        return toRouteMetadata((Route) item, subNode);
    }

    /**
     * Create route detail element (under "__routeInfo")
     *
     * @param route Route
     * @return New detail to add
     */
    protected abstract CotDetail toCotDetail(Route route);

    /**
     * Convert sub-detail node to route metadata
     *
     * @param route Route
     * @param subNode The sub-detail node (same name as passed into ctor)
     * @return Import result
     */
    protected abstract ImportResult toRouteMetadata(Route route,
            CotDetail subNode);
}
