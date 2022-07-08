
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;

/**
 * IP Connector is currently used for sending supported
 * by core ATAK including sending CoT events E.g routes, Mission Packages
 *
 * This is the primary connector for data sharing and is not exposed to the end user
 *
 */
public final class IpConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.ip";

    // Connection endpoint IP, host, and protocol
    private NetConnectString connectionEndpoint = null;

    // Custom intent action sent when sending item to contact
    private String sendIntent;

    public IpConnector(NetConnectString netConnectString) {
        this.connectionEndpoint = netConnectString;
    }

    public IpConnector(String sendIntent) {
        this.sendIntent = sendIntent;
    }

    public void setCallsign(String callsign) {
        if (connectionEndpoint != null)
            connectionEndpoint.setCallsign(callsign);
    }

    public String getSendIntent() {
        return this.sendIntent;
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
        return "IP";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.ip_on;
    }

    /**
     * This connector is used internal e.g. for PTP comms
     *
     * @return always return false
     */
    @Override
    public boolean isUserConnector() {
        return false;
    }
}
