
package com.atakmap.android.fires.bridge;

import android.graphics.drawable.Drawable;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.Collection;

/**
 * Bridge component in support of the the flavor subsystem.
 */
public abstract class NineLineBroadcastReceiver extends DropDownReceiver
        implements DropDown.OnStateListener {

    final static public String NINE_LINE = "com.atakmap.baokit.NINE_LINE";

    static private NineLineBroadcastReceiver impl;

    /**
     * When a plugin wants to send a 9 Line digitally using a transport method other than CoT
     * (such as VMF), the plugin should register a runnable with this class.
     * In the future we will be deprecating this feature in favor of using the contact list.
     * @return true if processing was successful, false if it failed.
     */
    public interface ExternalNineLineProcessor {
        /**
         * Called when a CoT event is formed and ready to be sent.
         * @param ce the cot event
         * @return true if the cot event is able to be processed.
         */
        boolean processNineLine(CotEvent ce);
    }

    protected NineLineBroadcastReceiver(MapView mapView) {
        super(mapView);
    }

    synchronized public static NineLineBroadcastReceiver getInstance(
            MapView mapView,
            MapGroup mapGroup) {
        return impl;
    }

    /**
     * Used by the system plugin to register a concrete implementation of the
     * call for fire capability.
     * @param concreteImpl the concrete call for fire implementation
     */
    public static void registerImplementation(
            NineLineBroadcastReceiver concreteImpl) {
        if (impl == null)
            impl = concreteImpl;
    }

    /**
     * Removes an External Nine Line Processor.
     * @param enlp the External Nine Line Processor.
     * processor.
     */
    public abstract void removeExternalNineLineProcessor(
            final ExternalNineLineProcessor enlp);

    /**
     * Installs and external Nine Line Processor.
     * @param icon the icon used when the selection dialog is shown.
     * @param txt the text that appears under the icon.
     * @param enlp the External Nine Line Processor implementation.
     */
    public abstract void addExternalNineLineProcessor(final Drawable icon,
            String txt, final ExternalNineLineProcessor enlp);

    /**
     * Add a MapItem that can be used as an Initial Point or Egress Control Point.
     * @param point the map item that can be used.
     */
    public abstract void addPoint(final MapItem point);

    /**
     * Remove a MapItem that can be used as an Initial Point or Egress Control Point.
     * @param point the map item that can be used.
     */
    public abstract void removePoint(final MapItem point);

    /**
     * Retrieve a unmodificable set of points currently being considered as an Initial
     * Point or Egress Control Point.
     */
    public abstract Collection<MapItem> getPoints();

}
