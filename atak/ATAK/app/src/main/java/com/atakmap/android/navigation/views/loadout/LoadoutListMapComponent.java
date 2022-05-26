
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.MapView;

import android.content.Context;
import android.content.Intent;

/**
 * Map component that handles intents for showing the list of loadouts
 */
public class LoadoutListMapComponent extends DropDownMapComponent {

    private LoadoutListDropDown _dropDown;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);
        _dropDown = new LoadoutListDropDown(view);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        _dropDown.dispose();
        super.onDestroyImpl(context, view);
    }
}
