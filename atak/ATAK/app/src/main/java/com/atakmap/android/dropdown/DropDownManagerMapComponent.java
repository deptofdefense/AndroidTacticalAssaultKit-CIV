
package com.atakmap.android.dropdown;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;

public class DropDownManagerMapComponent extends AbstractWidgetMapComponent {
    private DropDownManager _dropDownManagerSingleton;

    @Override
    protected void onCreateWidgets(Context context, Intent intent,
            MapView view) {
        _dropDownManagerSingleton = DropDownManager.getInstance();

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(DropDownManager.CLOSE_DROPDOWN);
        AtakBroadcast.getInstance().registerReceiver(_dropDownManagerSingleton,
                filter);
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        try {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _dropDownManagerSingleton);
        } catch (Exception ignored) {

        }
        _dropDownManagerSingleton.dispose();
        _dropDownManagerSingleton = null;
    }

}
