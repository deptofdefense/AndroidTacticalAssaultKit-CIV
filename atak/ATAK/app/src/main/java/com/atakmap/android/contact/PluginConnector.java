
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class PluginConnector extends Connector {

    //TODO review how this is used by plugins and contact list
    //May instead want to make this abstract, or replace it all together with more specific implementations

    public final static String CONNECTOR_TYPE = "connector.plugin";

    private String _sendMessageIntent;

    public PluginConnector(String sendMessageIntent) {
        _sendMessageIntent = sendMessageIntent;
    }

    public void setIntent(String intent) {
        _sendMessageIntent = intent;
    }

    @Override
    public String getConnectionString() {
        return _sendMessageIntent;
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "Plugin";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.ic_menu_plugins;
    }
}
