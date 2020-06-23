
package com.atakmap.android.missionpackage.export;

import com.atakmap.android.contact.Connector;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;

public class MissionPackageConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.missionpackage";

    private NetConnectString connectionEndpoint = null;

    public MissionPackageConnector(NetConnectString netConnectString) {
        connectionEndpoint = netConnectString;
    }

    public void setCallsign(String callsign) {
        connectionEndpoint.setCallsign(callsign);
    }

    @Override
    public String getConnectionString() {
        return connectionEndpoint.toString();
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return MapView.getMapView().getContext()
                .getString(R.string.mission_package_name);
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.missionpackage_icon;
    }
}
