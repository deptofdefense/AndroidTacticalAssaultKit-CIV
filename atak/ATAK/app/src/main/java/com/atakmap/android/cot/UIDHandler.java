
package com.atakmap.android.cot;

import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import java.util.List;
import java.util.ArrayList;

/**
 * UID detail
 */
public class UIDHandler implements MarkerDetailHandler {

    private static UIDHandler _instance;
    private final List<AttributeInjector> injectors = new ArrayList<>();

    /**
     * Used to inject Attributes into the UIDHandler for third party 
     * plugins.   Namely the Nett-T specification.
     * This can't this be done by simply registering a new detail handler.
     * There are provisions in the UIDHandler to have many attribute/value pairs based on how
     * a item is known across the enterprise.    This would not be able to be done via additional
     * custom details.
     */
    public interface AttributeInjector {
        /**
         * Inject information from the marker into the 
         * UID tag.
         */
        void injectIntoDetail(Marker marker, CotDetail detail);

        /**
         * Inject the information from the UID tag into 
         * the Marker.
         */
        void injectIntoMarker(CotDetail detail, Marker marker);

    }

    public UIDHandler() {
        _instance = this;
    }

    /**
     * Retrieve an instance of the UIDHandler so that one can augment 
     * the behavior by adding attribute injectors. 
     */
    public static UIDHandler getInstance() {
        return _instance;
    }

    /**
     * Provided a custom Attribute Injector to the UID tag
     */
    public void addAttributeInjector(final AttributeInjector ai) {
        synchronized (injectors) {
            injectors.add(ai);
        }
    }

    /**
     * Remove a custom Attribute Injector to the UID tag
     */
    public void removeAttributeInjector(final AttributeInjector ai) {
        synchronized (injectors) {
            injectors.remove(ai);
        }
    }

    @Override
    public void toCotDetail(final Marker marker, final CotDetail detail) {

        // Set to true when one or more attributes exists, so that the 
        // uid detail is then correctly added to the detail block.
        boolean addDetail = false;

        final CotDetail uidElement = new CotDetail("uid");

        if (marker.getType().equals("self")) {
            uidElement.setAttribute("Droid",
                    marker.getMetaString("callsign", marker.getTitle()));
            addDetail = true;
        }
        synchronized (injectors) {
            for (AttributeInjector ai : injectors) {
                addDetail = true;
                ai.injectIntoDetail(marker, uidElement);
            }
        }

        if (addDetail)
            detail.addChild(uidElement);

    }

    @Override
    public void toMarkerMetadata(Marker marker, CotEvent event,
            CotDetail detail) {
        synchronized (injectors) {
            for (AttributeInjector ai : injectors) {
                ai.injectIntoMarker(detail, marker);
            }
        }
    }
}
