
package com.atakmap.android.viewshed;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Provides the entry point for the View Shed Tool.
 */
public class ViewshedMapComponent extends DropDownMapComponent {

    private ViewshedDropDownReceiver _vsdRec;
    public final static String UPDATE_CONTOUR_PROGRESS = "com.atakmap.viewshed.UPDATE_CONTOUR_PROGRESS";
    public final static String UPDATE_CONTOUR_GEN_ENABLED = "com.atakmap.viewshed.UPDATE_CONTOUR_GEN_ENABLED";
    public final static String UPDATE_VIS_MAJOR_CHECKBOX = "com.atakmap.viewshed.UPDATE_VIS_MAJOR_CHECKBOX";
    public final static String UPDATE_VIS_MINOR_CHECKBOX = "com.atakmap.viewshed.SUPDATE_VIS_MINOR_CHECKBOX";
    public final static String CONTOUR_PROGRESS = "progress";
    public final static String CONTOUR_GEN_ENABLED = "genEnabled";

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _vsdRec = new ViewshedDropDownReceiver(view);
        DocumentedIntentFilter showDropdownFilter = new DocumentedIntentFilter();
        showDropdownFilter.addAction("com.atakmap.viewshed.VIEWSHED_TOOL");
        showDropdownFilter.addAction(UPDATE_CONTOUR_PROGRESS);
        showDropdownFilter.addAction(UPDATE_CONTOUR_GEN_ENABLED);
        showDropdownFilter.addAction(UPDATE_VIS_MAJOR_CHECKBOX);
        showDropdownFilter.addAction(UPDATE_VIS_MINOR_CHECKBOX);
        showDropdownFilter.addAction("c");
        registerDropDownReceiver(_vsdRec,
                showDropdownFilter);
    }

}
