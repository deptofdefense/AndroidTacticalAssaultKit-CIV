
package com.atakmap.android.medline;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Provides for a MedEVAC or CasEVAC capability within TAK.   This 
 * class is responsible for providing the 9-Line for this capability 
 * with the optional ZMIST information.
 */
public class MedicalLineMapComponent extends DropDownMapComponent {

    final public static String TAG = "MedicalLineMapComponent";

    private MedicalLineBroadcastReceiver _medLineReceiver;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _medLineReceiver = new MedicalLineBroadcastReceiver(view);

        DocumentedIntentFilter medFilter = new DocumentedIntentFilter();
        medFilter.addAction("com.atakmap.android.MED_LINE");
        registerDropDownReceiver(_medLineReceiver, medFilter);

        CotDetailManager.getInstance().registerHandler("_medevac_",
                new MedicalDetailHandler());

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
    }

}
