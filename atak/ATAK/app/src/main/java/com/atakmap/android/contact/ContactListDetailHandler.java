
package com.atakmap.android.contact;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.chat.ChatDatabase;
import com.atakmap.android.chat.GeoChatConnector;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.missionpackage.export.MissionPackageConnector;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.time.CoordinatedTime;

/**
 * Contact handler which manages the list of current network contacts. Bridges into the
 * CotMapComponent.
 * 
 */
public class ContactListDetailHandler
        implements
        com.atakmap.android.contact.IntentReceiver,
        com.atakmap.android.cot.MarkerDetailHandler {
    public static final String TAG = "ContactListDetailHandler";

    private final Contacts contacts;
    private final String name;

    public ContactListDetailHandler(Contacts contacts, final String name) {
        this.contacts = contacts;
        this.name = name;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    @Override
    public String getName() {
        return name;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    @Override
    public void onReceive(Context context, Intent intent) {
    }

    @Override
    public void toCotDetail(final Marker marker, final CotDetail detail) {

    }

    @Override
    public void toMarkerMetadata(final Marker marker, final CotEvent event,
            final CotDetail detail) {

        String type = event.getType();
        if (type == null || !type.startsWith("a-f"))
            return;

        String callsign = detail.getAttribute("callsign");
        if (callsign == null)
            return;

        String uid = event.getUID();
        if (uid == null)
            return;

        String phoneNumber = detail.getAttribute("phone");
        if (!FileSystemUtils.isEmpty(phoneNumber)) {
            marker.setMetaString("phoneNumber", phoneNumber);
        } else {
            marker.removeMetaData("phoneNumber");
        }

        String sipAddress = detail.getAttribute("sipAddress");
        if (!FileSystemUtils.isEmpty(sipAddress)) {
            marker.setMetaString("sipAddress", sipAddress);
        } else {
            marker.removeMetaData("sipAddress");
        }

        String emailAddress = detail.getAttribute("emailAddress");
        if (!FileSystemUtils.isEmpty(emailAddress)) {
            marker.setMetaString("emailAddress", emailAddress);
        } else {
            marker.removeMetaData("emailAddress");
        }

        String xmppUsername = detail.getAttribute("xmppUsername");
        if (!FileSystemUtils.isEmpty(xmppUsername)) {
            marker.setMetaString("xmppUsername", xmppUsername);
        } else {
            marker.removeMetaData("xmppUsername");
        }

        CotDetail group = event.getDetail().getFirstChildByName(0, "__group");
        String team = null;
        String role = null;
        if (group != null) {
            team = group.getAttribute("name");
            role = group.getAttribute("role");
        } else {
            //Log.w(TAG, " Contact " + uid + ": NO TEAM");
        }

        String connectionEndpoint = detail.getAttribute("endpoint");
        if (connectionEndpoint != null) {
            marker.setMetaString("endpoint", connectionEndpoint);
        }

        if (connectionEndpoint != null && callsign != null) {
            NetConnectString ncs = NetConnectString
                    .fromString(connectionEndpoint + ":" + callsign);

            Contact existingContact = contacts.getContactByUuid(uid);

            if (existingContact instanceof GroupContact) {
                contacts.removeContact(existingContact);
                ChatDatabase.getInstance(MapView.getMapView().getContext())
                        .removeGroup(uid);
                existingContact = null;
            }

            if (existingContact == null && ncs != null) {
                IndividualContact newContact = new IndividualContact(callsign,
                        uid, marker, ncs);

                if (group != null) {
                    newContact.getExtras().putString("team", team);
                    newContact.getExtras().putString("role", role);
                }

                existingContact = newContact;
                contacts.addContact(newContact);
            } else if (ncs != null) {

                if (!existingContact.getName().equals(callsign)) {
                    existingContact.setName(callsign);
                }

                if (existingContact instanceof IndividualContact) {
                    if (role != null
                            && !role.equals(existingContact.getExtras().get(
                                    "role"))) {
                        Bundle extras = existingContact.getExtras();
                        extras.putString("role", role);
                        existingContact.setExtras(extras);
                    }

                    if (team != null
                            && !team.equals(existingContact.getExtras().get(
                                    "team"))) {
                        Bundle extras = existingContact.getExtras();
                        extras.putString("team", team);
                        existingContact.setExtras(extras);
                    }

                    IndividualContact castedContact = (IndividualContact) existingContact;
                    castedContact.setMapItem(marker);

                    //Update the time if the saved and the new endpoint are equal - a refresh
                    NetConnectString address = ContactUtil
                            .getIpAddress(castedContact);

                    // if the ncs address is not equal to *:-1:stcp, then this is a local
                    // network endpoint, set the local_network_endpoint_recv time.

                    if (!ncs.matches("stcp", "*", -1)) {
                        marker.setMetaLong("local_network_endpoint_recv",
                                new CoordinatedTime().getMilliseconds());

                        // set the last unreliable stale out time -
                        // ignore any reliable stale out times because they 
                        // could be longer
                        Bundle tempBundle = existingContact.getExtras();
                        tempBundle.putLong("staleTime", marker.getMetaLong(
                                "autoStaleDuration", 15000L));
                        existingContact.setExtras(tempBundle);

                    }

                    // determine the last state time only set for unreliable traffic
                    // since reliable updates have a much larger stale out time.
                    final long stale = castedContact.getExtras().getLong(
                            "staleTime",
                            15000L);

                    final long lastLocalUpdate = marker.getMetaLong(
                            "local_network_endpoint_recv", -1);

                    if ((lastLocalUpdate + stale) > new CoordinatedTime()
                            .getMilliseconds()) {

                        // if the status has not staled out and we have received a server
                        // endpoint, then use the last local one received.

                        if (ncs.matches("stcp", "*", -1)) {
                            ncs = ContactUtil
                                    .getIpAddress(castedContact);
                        }

                        //Log.d(TAG, "contact local endpoint considered not stale, not changing the endpoint: " + callsign + " last seen: " + lastLocalUpdate + " stale: " + stale + "keeping: " + ncs);
                    } else {
                        //Otherwise, we have a stale contact.  Update the connection info, the
                        //update time, and the stale time associated with this new connection.
                        //Make sure to refresh the contact in case we were fully stale.
                        //Log.d(TAG,
                        //        "contact local endpoint considered stale: "
                        //               + callsign
                        //               + " local endpoint last seen: "
                        //               + lastLocalUpdate
                        //               + " stale:"
                        //               + stale
                        //               + ", switching over to streaming: "
                        //               + ncs);

                    }
                }
            } //end else ncs != null

            if (existingContact instanceof IndividualContact) {
                IndividualContact castedContact = (IndividualContact) existingContact;
                //Log.d(TAG, "Updating connectors for: " + castedContact.toString());

                castedContact.addConnector(new IpConnector(ncs));
                castedContact.addConnector(new GeoChatConnector(ncs));
                castedContact.addConnector(new MissionPackageConnector(ncs));

                if (!FileSystemUtils.isEmpty(phoneNumber)) {
                    castedContact.addConnector(new TelephoneConnector(
                            phoneNumber));
                    castedContact.addConnector(new SmsConnector(phoneNumber));
                } else {
                    castedContact
                            .removeConnector(TelephoneConnector.CONNECTOR_TYPE);
                    castedContact.removeConnector(SmsConnector.CONNECTOR_TYPE);
                }

                if (!FileSystemUtils.isEmpty(sipAddress)) {
                    castedContact.addConnector(new VoIPConnector(sipAddress));
                } else {
                    castedContact.removeConnector(VoIPConnector.CONNECTOR_TYPE);
                }

                if (!FileSystemUtils.isEmpty(emailAddress)) {
                    castedContact
                            .addConnector(new EmailConnector(emailAddress));
                } else {
                    castedContact
                            .removeConnector(EmailConnector.CONNECTOR_TYPE);
                }

                if (!FileSystemUtils.isEmpty(xmppUsername)) {
                    castedContact.addConnector(new XmppConnector(xmppUsername));
                } else {
                    castedContact.removeConnector(XmppConnector.CONNECTOR_TYPE);
                }

                castedContact.current();
            }
        } //end has endpoint & callsign
    }
}
