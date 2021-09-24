
package com.atakmap.android.contact;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.log.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * TADIL-J individual contact
 */
public class TadilJContact extends IndividualContact {

    public static final String TAG = "TadilJContact";

    public static final NetConnectString DEFAULT_CHAT_NCS = new NetConnectString(
            "udp", "224.10.10.1", 17012);
    public static final NetConnectString DEFAULT_POINT_NCS = new NetConnectString(
            "udp", "239.2.3.1", 6969);

    private final static Map<String, TadilJContact> _tadiljContacts = new HashMap<>();

    public static void updateChatConnector(NetConnectString string,
            TadilJContactDatabase db) {
        Connector chatConnector = new IpConnector(string);
        synchronized (_tadiljContacts) {
            for (TadilJContact contact : _tadiljContacts.values()) {
                // Only update connectors which are default
                NetConnectString ncs = ContactUtil.getGeoChatIpAddress(contact);
                if (ncs != null && ncs.equals(DEFAULT_CHAT_NCS)) {
                    contact.addConnector(chatConnector);
                    db.addContact(contact);
                }
            }
        }
    }

    private boolean _enabled;

    public TadilJContact(String name, String uuid) {
        this(name, uuid, true);
    }

    public TadilJContact(String name, String uuid, boolean enabled) {
        this(name, uuid, enabled, new IpConnector(DEFAULT_POINT_NCS),
                new TadilJChatConnector(DEFAULT_CHAT_NCS));
    }

    public TadilJContact(String name, String uuid, boolean enabled,
            IpConnector pointConn) {
        this(name, uuid, enabled, pointConn, ChatManagerMapComponent
                .getChatBroadcastContact().getConnector(
                        IpConnector.CONNECTOR_TYPE));
    }

    public TadilJContact(String name, String uuid, boolean enabled,
            IpConnector pointConn, Connector chatConn) {
        super(name, uuid);
        _enabled = enabled;
        addConnector(pointConn);
        if (chatConn != null) {
            addConnector(new TadilJChatConnector(
                    NetConnectString
                            .fromString(chatConn.getConnectionString())));
        } else {
            Log.e(TAG, "chatConnector was null for: " + name);
        }
        synchronized (_tadiljContacts) {
            _tadiljContacts.put(uuid, this);
        }
    }

    public boolean isEnabled() {
        return _enabled;
    }

    public void setEnabled(boolean newValue) {
        _enabled = newValue;
    }

    @Override
    public Drawable getIconDrawable() {
        return MapView.getMapView().getContext()
                .getDrawable(R.drawable.tadilj_link);
    }

    @Override
    public Connector getDefaultConnector(SharedPreferences prefs) {
        return getConnector(TadilJChatConnector.CONNECTOR_TYPE);
    }

    @Override
    public String getParentUID() {
        return "TadilJGroup";
    }
}
