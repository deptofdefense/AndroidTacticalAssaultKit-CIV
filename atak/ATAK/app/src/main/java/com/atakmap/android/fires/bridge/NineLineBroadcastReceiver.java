package com.atakmap.android.fires.bridge;

import android.graphics.drawable.Drawable;
import android.view.View;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.cot.event.CotEvent;

import java.util.HashMap;
import java.util.Map;

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


    public static void registerImplementation(NineLineBroadcastReceiver concreteImpl) {
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
}
