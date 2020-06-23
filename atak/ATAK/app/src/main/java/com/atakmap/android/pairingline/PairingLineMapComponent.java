
package com.atakmap.android.pairingline;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;

/**
 * Provides for the pairing line capability in the system.
 */
public class PairingLineMapComponent extends AbstractWidgetMapComponent {

    private PairingLineMapReceiver _pairingMapReceiver;

    @Override
    protected void onCreateWidgets(Context context, Intent intent,
            MapView view) {

        _pairingMapReceiver = new PairingLineMapReceiver();
        DocumentedIntentFilter showFilter = new DocumentedIntentFilter();
        showFilter.addAction(PairingLineMapReceiver.ACTION);
        AtakBroadcast.getInstance().registerReceiver(_pairingMapReceiver,
                showFilter);

    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        AtakBroadcast.getInstance().unregisterReceiver(_pairingMapReceiver);
        _pairingMapReceiver = null;
    }

}
