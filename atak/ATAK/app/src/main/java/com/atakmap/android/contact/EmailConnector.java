
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class EmailConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.email";

    private final String _emailAddress;

    public EmailConnector(String emailAddress) {
        _emailAddress = emailAddress;
    }

    @Override
    public String getConnectionString() {
        return this._emailAddress;
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "Email";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.email_icon;
    }
}
