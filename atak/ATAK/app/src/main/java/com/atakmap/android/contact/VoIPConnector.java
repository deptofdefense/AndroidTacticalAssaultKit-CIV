
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class VoIPConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.voip";

    private final String _address;

    public VoIPConnector(final String address) {
        _address = address;
    }

    @Override
    public String getConnectionString() {
        return _address;
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "VoIP";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.voip_icon;
    }
}
