
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class XmppConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.xmpp";

    private final String _xmppAddress;

    public XmppConnector(final String xmppAddress) {
        _xmppAddress = xmppAddress;
    }

    @Override
    public String getConnectionString() {
        return this._xmppAddress;
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "XMPP";
    }

    @Override
    public String getIconUri() {
        return GetIconUri();
    }

    public static String GetIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.xmpp_icon;
    }

    @Override
    public int getPriority() {
        return 2;
    }
}
