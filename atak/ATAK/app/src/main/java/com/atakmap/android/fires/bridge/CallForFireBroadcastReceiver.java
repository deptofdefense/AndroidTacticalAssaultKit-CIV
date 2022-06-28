
package com.atakmap.android.fires.bridge;

import android.graphics.drawable.Drawable;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Bridge component in support of the the flavor subsystem.
 */
public abstract class CallForFireBroadcastReceiver extends DropDownReceiver
        implements
        DropDown.OnStateListener {

    static private CallForFireBroadcastReceiver impl;

    /**
     * When a plugin wants to send a 5 Line digitally using a transport method other than CoT
     * (such as VMF), the plugin should register a runnable with this class.
     * In the future we will be deprecating this feature in favor of using the contact list.
     */
    public interface ExternalFiveLineProcessor {
        /**
         * Called when a CoT event is formed and ready to be sent.
         * @param ce the cot event
         * @return true if the cot event is able to be processed.
         */
        boolean processFiveLine(CotEvent ce);
    }

    protected CallForFireBroadcastReceiver(MapView mapView) {
        super(mapView);
    }

    synchronized public static CallForFireBroadcastReceiver getInstance(
            MapView mapView,
            MapGroup mapGroup) {
        return impl;
    }

    public static void registerImplementation(
            CallForFireBroadcastReceiver concreteImpl) {
        impl = concreteImpl;
    }

    /**
     * Installs and external Five Line Processor.
     * @param icon the icon used when the selection dialog is shown.
     * @param txt the text that appears under the icon.
     * @param eflp the External Five Line Processor implementation.
     */
    public abstract void addExternalFiveLineProcessor(final Drawable icon,
            String txt, final ExternalFiveLineProcessor eflp);

    /**
     * Removes an External Five Line Processor.
     * @param eflp the External Five Line Processor.
     * processor.
     */
    public abstract void removeExternalFiveLineProcessor(
            final ExternalFiveLineProcessor eflp);
}
