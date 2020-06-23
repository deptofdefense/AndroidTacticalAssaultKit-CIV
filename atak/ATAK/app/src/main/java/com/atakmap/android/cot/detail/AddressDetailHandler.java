
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * Detail handler for marker address.  Since the address is cached against the 
 * geopoint, go ahead and perform a validity check before producing the address
 * detail.
 */
class AddressDetailHandler extends CotDetailHandler {

    private static final String TAG = "AddressDetailHandler";

    AddressDetailHandler() {
        super("__address");
    }

    @Override
    public boolean toCotDetail(MapItem m, CotEvent event, CotDetail detail) {

        String address_geopoint = m.getMetaString("address_geopoint", null);
        String address_text = m.getMetaString("address_text", null);
        String address_geocoder = m.getMetaString("address_geocoder",
                "unknown");
        String address_lookuptime = m.getMetaString("address_lookuptime", null);

        GeoPointMetaData pm = getPoint(m);
        if (pm != null && address_text != null && address_geopoint != null
                && address_geopoint.equals(pm.get().toString())) {
            CotDetail address = new CotDetail("__address");
            address.setAttribute("text", address_text);
            address.setAttribute("geocoder", address_geocoder);
            address.setAttribute("time", address_lookuptime);
            detail.addChild(address);
            return true;
        }
        return false;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        GeoPointMetaData point = getPoint(item);
        if (point == null)
            return ImportResult.IGNORE;

        String address_geopoint = point.get().toString();
        String address_text = detail.getAttribute("text");
        String address_geocoder = detail.getAttribute("geocoder");
        String address_lookuptime = detail.getAttribute("time");

        if (address_text != null) {
            item.setMetaString("address_geopoint", address_geopoint);
            item.setMetaString("address_text", address_text);
            item.setMetaString("address_geocoder", address_geocoder);
            item.setMetaString("address_lookuptime", address_lookuptime);
            return ImportResult.SUCCESS;
        }
        return ImportResult.FAILURE;
    }
}
