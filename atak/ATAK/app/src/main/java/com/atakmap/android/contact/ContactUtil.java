
package com.atakmap.android.contact;

import com.atakmap.android.chat.GeoChatConnector;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

public class ContactUtil {

    private static final String TAG = "ContactUtil";

    public static final String TAK_SERVER_CONNECTION_STRING = "*:-1:stcp";

    public static boolean isTakContact(MapItem item) {
        return item instanceof PointMapItem
                && item.hasMetaValue("team");
    }

    /**
     * Helper to get NetConnectString from IpConnector, if available
     *
     * @param contact the individual contact to obtain the ip address from
     * @return the fully formed netconnectstring pointing to the individual contact's machine.
     */
    public static NetConnectString getIpAddress(IndividualContact contact) {
        if (contact == null)
            return null;

        Connector ipConnector = contact
                .getConnector(IpConnector.CONNECTOR_TYPE);
        if (ipConnector != null) {
            String connectString = ipConnector.getConnectionString();
            return NetConnectString.fromString(connectString);
        } else {
            return null;
        }

    }

    /**
     * Helper to get NetConnectString in following order:
     *  if TadilJContact, then only use TadilJChatConnector
     *  Use GeoChatConnector, if available
     *  Use IpConnector, if available
     *
     * TODO is this special case for TADILJ Connector still necessary?
     *
     * @param contact the individual contact that should be used to get the specific connector.
     * @return the well formed NetConnectString.
     */
    public static NetConnectString getGeoChatIpAddress(
            IndividualContact contact) {
        if (contact == null)
            return null;

        if (contact instanceof TadilJContact) {
            Connector connector = contact
                    .getConnector(TadilJChatConnector.CONNECTOR_TYPE);
            if (connector != null) {
                String connectString = connector.getConnectionString();
                return NetConnectString.fromString(connectString);
            } else {
                return null;
            }
        }

        Connector connector = contact
                .getConnector(GeoChatConnector.CONNECTOR_TYPE);
        if (connector != null) {
            String connectString = connector.getConnectionString();
            return NetConnectString.fromString(connectString);
        } else {
            return getIpAddress(contact);
        }
    }

    /**
     * Get array of UIDs from array of contacts
     *
     * @param contacts provides list of contacts
     * @return an array of strings containing the contact uid's in the same order, if the contact list is empty, just returns null.
     */
    public static String[] getUIDs(Contact[] contacts) {
        String[] toUIDs = null;
        if (!FileSystemUtils.isEmpty(contacts)) {
            toUIDs = new String[contacts.length];
            for (int i = 0; i < contacts.length; i++) {
                toUIDs[i] = contacts[i].getUID();
            }
        }

        return toUIDs;
    }

    /**
     * Get array of stringified NetConnectString, from array of contacts
     * @param contacts the list of contacts
     * @param bFixupForTakServer if needing fixup for usage with a takserver.
     * @return the NetConnectString array based on the array of contacts.
     */
    public static String[] getConnectStrings(IndividualContact[] contacts,
            boolean bFixupForTakServer) {
        String[] toConnectStrings = null;
        if (!FileSystemUtils.isEmpty(contacts)) {
            toConnectStrings = new String[contacts.length];
            for (int i = 0; i < contacts.length; i++) {
                NetConnectString address = ContactUtil
                        .getIpAddress(contacts[i]);
                if (address != null) {
                    toConnectStrings[i] = address.toString();
                    if (bFixupForTakServer
                            && toConnectStrings[i]
                                    .compareTo(
                                            TAK_SERVER_CONNECTION_STRING) == 0) {
                        toConnectStrings[i] = contacts[i].getName();
                    }
                    //                    Log.d(TAG, "Writing " + uid + " to contact: "
                    //                            + toUIDs[i] + ", " + toConnectStrings[i]);
                } else {
                    Log.d(TAG,
                            "Missing IP Connector: " + contacts[i].toString());
                }
            }
        }

        return toConnectStrings;
    }
}
