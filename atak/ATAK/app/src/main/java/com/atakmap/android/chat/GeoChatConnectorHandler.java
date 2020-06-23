
package com.atakmap.android.chat;

import android.content.Context;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactConnectorManager;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.contact.TadilJChatConnector;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Handle GeoChat connectors
 *
 */
public class GeoChatConnectorHandler extends
        ContactConnectorManager.ContactConnectorHandler {

    private final static String TAG = "GeoChatConnectorHandler";
    private final Context _context;

    public GeoChatConnectorHandler(Context context) {
        _context = context;
    }

    @Override
    public boolean isSupported(String type) {
        return FileSystemUtils.isEquals(type, GeoChatConnector.CONNECTOR_TYPE)
                || FileSystemUtils.isEquals(type,
                        TadilJChatConnector.CONNECTOR_TYPE);
    }

    @Override
    public boolean hasFeature(
            ContactConnectorManager.ConnectorFeature feature) {
        return feature == ContactConnectorManager.ConnectorFeature.NotificationCount
                || feature == ContactConnectorManager.ConnectorFeature.Presence;
    }

    @Override
    public String getName() {
        return _context.getString(R.string.connector_geochat);
    }

    @Override
    public String getDescription() {
        return _context.getString(R.string.app_name)
                + " provides point and group Geo Chat messages";
    }

    @Override
    public Object getFeature(String connectorType,
            ContactConnectorManager.ConnectorFeature feature,
            String contactUID, String connectorAddress) {

        if (feature == ContactConnectorManager.ConnectorFeature.NotificationCount) {
            Contact c = Contacts.getInstance().getContactByUuid(contactUID);
            if (c != null)
                return c.getExtras().getInt("unreadMessageCount", 0);
        } else if (feature == ContactConnectorManager.ConnectorFeature.Presence) {
            Contact c = Contacts.getInstance().getContactByUuid(contactUID);
            if (c != null)
                return c.getUpdateStatus();
        }

        return null;
    }

    @Override
    public boolean handleContact(String connectorType, String contactUID,
            String address) {
        //TODO sometimes editable? Can that be determined later?
        //boolean editable = (mode != ContactListAdapter.ViewMode.HISTORY);

        if (!FileSystemUtils.isEmpty(contactUID)) {
            Log.d(TAG, "handleContact: " + contactUID + ", " + address);
            Contact list = Contacts.getInstance().getContactByUuid(contactUID);
            boolean editable = list == null || list.getExtras()
                    .getBoolean("editable", !(list instanceof GroupContact))
                    || list instanceof GroupContact
                            && !((GroupContact) list).getUnmodifiable();
            ChatManagerMapComponent.getInstance().openConversation(contactUID,
                    editable);

            //TODO is this step necessary?
            Contacts.getInstance().updateTotalUnreadCount();
            return true;
        }

        Log.w(TAG, "Unable to handleContact: " + contactUID + ", " + address);
        return false;
    }
}
