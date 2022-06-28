
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;

public class TadilJChatConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.tadilj";

    private final NetConnectString connectionEndpoint;

    public TadilJChatConnector(NetConnectString netConnectString) {
        connectionEndpoint = netConnectString;
    }

    public void setCallsign(String callsign) {
        if (connectionEndpoint != null)
            connectionEndpoint.setCallsign(callsign);
    }

    @Override
    public String getConnectionString() {
        return String.valueOf(connectionEndpoint);
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "TADIL-J Chat";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.tadilj_link;
    }
}
