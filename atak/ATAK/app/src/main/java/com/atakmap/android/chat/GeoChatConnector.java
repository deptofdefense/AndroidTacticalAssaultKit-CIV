
package com.atakmap.android.chat;

import com.atakmap.android.contact.Connector;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;

public class GeoChatConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.geochat";

    private final NetConnectString connectionEndpoint;

    public GeoChatConnector(NetConnectString netConnectString) {
        connectionEndpoint = netConnectString;
    }

    public void setCallsign(String callsign) {
        connectionEndpoint.setCallsign(callsign);
    }

    @Override
    public String getConnectionString() {
        if (connectionEndpoint != null)
            return connectionEndpoint.toString();
        return "";
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "Geo Chat";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.ic_menu_chat;
    }

    @Override
    public int getPriority() {
        return 1;
    }

}
