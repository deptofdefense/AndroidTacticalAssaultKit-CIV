
package com.atakmap.android.maps;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.widgets.AbstractWidgetMapComponent;
import com.atakmap.android.widgets.LayoutWidget;

public class MultiplePairingLineMapComponent
        extends AbstractWidgetMapComponent {

    @Override
    protected void onCreateWidgets(Context context, Intent intent,
            MapView view) {
        _containerLayout = new LayoutWidget();
        _containerLayout.setName("Multiple Pairing Line");
        getRootLayoutWidget().addWidget(_containerLayout);
        MapGroup rootGroup = MapGroup.findMapGroup(view.getRootGroup(),
                "Cursor on Target");
        if (rootGroup != null) {
            _pairingMapReceiver = MultiplePairingLineMapReceiver.getInstance();
            _pairingMapReceiver.initialize(view, rootGroup, _containerLayout);
        }
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        _pairingMapReceiver = null;
        if (_containerLayout != null)
            getRootLayoutWidget().removeWidget(_containerLayout);
    }

    @SuppressWarnings("unused")
    private MultiplePairingLineMapReceiver _pairingMapReceiver;
    private LayoutWidget _containerLayout;

}
