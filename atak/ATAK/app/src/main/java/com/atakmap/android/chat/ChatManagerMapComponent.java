
package com.atakmap.android.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.contact.Connector;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.contact.ContactUtil;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.FilteredContactsManager;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.contact.PluginConnector;
import com.atakmap.android.contact.TadilJContact;
import com.atakmap.android.contact.TadilJContactDatabase;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.filters.EmptyListFilter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChatManagerMapComponent extends AbstractMapComponent implements
        ChatConvoFragCreateWatcher {

    public static final String TAG = "ChatManagerMapComponent";
    public static String REMOVE_MESSAGE;
    public static String NEW_CONTACT_MESSAGE;

    public static final String PLUGIN_SEND_MESSAGE_ALL_CHAT_ROOMS = "com.atakmap.android.chat.SEND_MESSAGE_ALL_CHAT_ROOMS";
    public static final String PLUGIN_SEND_MESSAGE_EXTRA = "MESSAGE";
    private static final String OPEN_GEOCHAT = "com.atakmap.android.OPEN_GEOCHAT";

    private static Map<String, ConversationFragment> fragmentMap;
    private static SharedPreferences chatPrefs;
    private MapView _mapView;

    private Context _context;

    private ChatMesssageRenderer chatMesssageRenderer;

    // Main groups
    private static GroupContact rootGroup, userGroups, teamGroups, tadilJGroup;

    private static IndividualContact _chatBroadcastContact = null;

    public static IndividualContact getChatBroadcastContact() {
        return _chatBroadcastContact;
    }

    static GeoChatService chatService = null;

    private static ChatManagerMapComponent _instance;

    public static ChatManagerMapComponent getInstance() {
        return _instance;
    }

    private GeoChatConnectorHandler _geoChatHandler;

    private final BroadcastReceiver newChatMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                long messageId = intent.getLongExtra("id", -1);
                long groupId = intent.getLongExtra("groupId", -1);
                String conversationId = intent
                        .getStringExtra("conversationId");
                Log.d(TAG, "Received incoming chat: " + messageId
                        + " in " + conversationId);

                if (messageId >= 0) {
                    try {
                        Bundle fullMsg = chatService
                                .getMessage(messageId, groupId);

                        if (fullMsg != null) {

                            // check to see if the message received 
                            // has a nullary sender uid, right now 
                            // this can only mean bad things for the 
                            // workflow.  The underlying reason might 
                            // be more insidius - see 
                            // comment #2 of 
                            // https://atakmap.com/bugz/show_bug.cgi?id=3991
                            // there are some funky charactors in the 
                            // message that may have triggered this.

                            if (getUidFromMessageBundle(fullMsg) == null) {
                                Log.e(TAG,
                                        "### BAD BAD BAD ##### could not determine the uid of for the sender of the message: "
                                                + fullMsg
                                                        .getString("message"));
                                //return;
                            }

                            broadcastChatMessageReceived(fullMsg);

                            boolean added = addMessageToConversation(
                                    fullMsg);

                            // If contact is filtered, then skip
                            if (FilteredContactsManager.getInstance()
                                    .anyContactsFiltered()) {
                                if (GeoChatService.DEFAULT_CHATROOM_NAME
                                        .equals(conversationId)) {
                                    String senderUid = fullMsg
                                            .getString("senderUid");
                                    if (senderUid != null) {
                                        if (FilteredContactsManager
                                                .getInstance()
                                                .isContactFiltered(senderUid))
                                            return;
                                    }
                                } else {
                                    if (FilteredContactsManager.getInstance()
                                            .isContactFiltered(conversationId))
                                        return;
                                }
                            }
                            boolean notify = PreferenceManager
                                    .getDefaultSharedPreferences(
                                            context)
                                    .getBoolean(
                                            "enableToast", true);
                            if (added) { // i.e., as opposed to ACK'ed
                                Contacts.getInstance()
                                        .updateTotalUnreadCount();

                                if (notify)
                                    notifyUserOfNewMessage(fullMsg,
                                            context);
                            } else {
                                Log.d(TAG,
                                        "ACK received, so not creating a notification");
                            }
                        } else {
                            Log.w(TAG,
                                    "Could not locate message in DB.");
                        }
                    } catch (Exception e) {
                        Log.w(TAG,
                                "Error querying Chat Service for message ID "
                                        + messageId,
                                e);
                    }
                } else {
                    Log.w(TAG, "Got an invalid message ID in " + intent);
                }
            } else {
                Log.w(TAG, "Got an unexpected NULL intent.");
            }
        }
    };

    public interface ChatMessageListener {
        /**
         * Replacement for the intent that current listens for the chat messages received
         * intents.
         * @param bundle a copy of the chat message bundle that represents the chat message.
         */
        void chatMessageReceived(Bundle bundle);
    }

    private final ConcurrentLinkedQueue<ChatMessageListener> chatMessageListeners = new ConcurrentLinkedQueue<>();

    /**
     * sends message intent out to plugin(ChatmessagePopups) when
     * a new chat message is received
     */
    private void broadcastChatMessageReceived(Bundle bundle) {
        Intent re = new Intent(
                "com.atakmap.android.ChatMessagePopups.NEW_CHAT_RECEIVED");
        re.putExtra("chat_bundle", bundle);
        AtakBroadcast.getInstance().sendBroadcast(re);

        fireChatMessageReceived(bundle);
    }

    public static String getTeamName() {
        if (chatPrefs != null)
            return chatPrefs.getString("locationTeam", "Cyan");
        return null;
    }

    public static String getRoleName() {
        if (chatPrefs != null)
            return chatPrefs.getString("atakRoleType", "Team Member");
        return null;
    }

    private String getSenderUidFromMessage(final Bundle message) {
        boolean messageExists = (message != null)
                && (message.getString("message") != null);

        if (messageExists &&
                !message.getString("message").equals(REMOVE_MESSAGE)) {

            return message.getString("senderUid");
        }
        return null;
    }

    private IndividualContact getIndividualContactFromCallsign(
            String callsign) {
        if (callsign == null || _mapView.getDeviceCallsign().equals(callsign))
            return null;

        Contact contact = Contacts.getInstance().getFirstContactWithCallsign(
                callsign);
        if (contact instanceof IndividualContact) {
            return (IndividualContact) contact;
        }

        return null;
    }

    private String getUidFromMessageBundle(Bundle message) {
        return message.getString("senderUid");
    }

    private String getCallsignFromContactUid(String uuid) {
        if (uuid != null) {
            Contact contact = Contacts.getInstance().getContactByUuid(uuid);
            if (contact != null) {
                return contact.getName();
            }
        }

        return null;

    }

    private String getConnectionStringFromMessageBundle(Bundle message) {
        String fromConnStr = message.getString("from");

        String senderUid = message.getString("senderUid");

        String callsign = getCallsignFromContactUid(senderUid);

        // if fromConnStr represents a streaming connection or multicast addr,
        // return it as-is!
        if ((isStreaming(fromConnStr) || isMulticast(fromConnStr))) {
            NetConnectString netConnStr = NetConnectString
                    .fromString(fromConnStr);
            netConnStr.setCallsign(callsign);
            Log.d(TAG, "using " + netConnStr
                    + " because this is a streaming or multicast msg");
            return netConnStr.toString();
        }

        // use the sender's name and do a lookup in the contact list
        IndividualContact nc = getIndividualContactFromCallsign(callsign);
        if (nc != null) {
            NetConnectString ncs = ContactUtil.getIpAddress(nc);
            if (ncs != null) {
                Log.d(TAG,
                        "using contact list IndvidualContact supplied host: "
                                + ncs.getHost()
                                + " (tcp:4242)");
                return ncs.toString();
            }
        }

        // if fromConnStr is NOT a multicast connection, return it as-is
        if (fromConnStr != null) {
            NetConnectString fromAddr = NetConnectString
                    .fromString(fromConnStr);
            // maybe a lookup in the contact list, then some defaults
            Log.d(TAG,
                    "using "
                            + fromAddr.getHost()
                            + " (tcp:4242) because this is not a streaming/multicast msg");
            return new NetConnectString(
                    CotServiceRemote.Proto.tcp.toString(),
                    fromAddr.getHost(), 4242).toString();
        }

        return null;
    }

    /**
     * Check whether or not we should acknowledge this message
     * @param msg Message bundle
     * @return True to ignore message, false to acknowledge
     */
    private boolean shouldIgnoreMessage(final Bundle msg) {
        String convId = msg.getString("conversationId",
                GeoChatService.DEFAULT_CHATROOM_NAME);
        String line = msg.getString("message");
        if (line == null)
            return false;

        Contact conv = Contacts.getInstance().getContactByUuid(convId);
        return line.equals(NEW_CONTACT_MESSAGE)
                && GroupContact.isGroup(conv)
                && ((GroupContact) conv).getUnmodifiable();
    }

    /**
     * @return true if added, false if ack'ed
     */
    private boolean addMessageToConversation(final Bundle message) {
        if (shouldIgnoreMessage(message))
            return false;

        String conversationId = message.getString("conversationId",
                GeoChatService.DEFAULT_CHATROOM_NAME);
        String conversationName = message.getString("conversationName",
                conversationId);

        Log.d(TAG, "adding message to " + conversationId + " from "
                + getUidFromMessageBundle(message)
                + " with the conversation name of " + conversationName);
        boolean fragExists = fragmentMap.get(conversationId) != null;

        Log.d(TAG, "Fragment for " + conversationId
                + (fragExists ? " exists" : " does NOT exist"));

        ArrayList<String> uuids = new ArrayList<>();
        if (isSpecialGroup(conversationId)) {
            uuids.add(conversationId);
        } else {
            uuids = getGroupMemberUidsFromMessage(message);
        }

        MessageDestination destination = new MessageDestination(uuids);

        ConversationFragment conversationFrag = getOrCreateFragment(
                conversationId, conversationName, destination);

        if (conversationFrag.getChatCount() == 0) {
            conversationFrag.populateHistory();

            /*
             * When a new message is received it is added to the DB before anything happens. 
             * So when the history is populated this new message is automatically added to 
             * the history even though it hasn't been viewed yet. So in order for the unread 
             * count to be updated appropriately, remove the latest and then add it again.
             */
            conversationFrag.removeLastChatLine();
        }

        return conversationFrag.addOrAckChatLine(ChatLine.fromBundle(message));
    }

    private ArrayList<String> getGroupMemberUidsFromMessage(Bundle message) {

        ArrayList<String> destinationArray = new ArrayList<>();

        String senderUid = getSenderUidFromMessage(message);
        String converstaionId = message.getString("conversationId",
                GeoChatService.DEFAULT_CHATROOM_NAME);
        if (isSpecialGroup(converstaionId)) {
            destinationArray.add(converstaionId);
            return destinationArray;
        } else {
            String destinationString = message.getString("destinations");
            if (destinationString != null
                    && !destinationString.equals("null")) {
                String[] destinations = destinationString
                        .split(ChatDatabase.ARRAY_DELIMITER);
                String myUid = _mapView.getSelfMarker().getUID();

                for (String str : destinations) {
                    if (!str.equals(myUid) && !str.isEmpty())
                        destinationArray.add(str);
                }
            }
        }
        if (!destinationArray.contains(senderUid))
            destinationArray.add(senderUid);

        return destinationArray;
    }

    private void notifyUserOfNewMessage(final Bundle message, Context context) {
        //Log.d(TAG, "Notifying user of new chat message.");

        if (shouldIgnoreMessage(message))
            return;

        String conversationId = message.getString("conversationId");
        ConversationFragment frag = getFragmentByConversationId(conversationId);

        if (frag != null && !frag.isVisible()) {

            Log.d(TAG, "conversationId notification: " + conversationId
                    + " for " + frag.getTitle());

            Intent notificationIntent = new Intent();
            notificationIntent.setAction(OPEN_GEOCHAT);
            notificationIntent.putExtra("message", message);

            NotificationUtil nUtil = NotificationUtil.getInstance();

            String title = context.getString(R.string.chat_text3)
                    + frag.getTitle();
            int notificationId = 0;
            //Log.i(TAG, "notification id: " + notificationId);
            //Log.d(TAG, "Posting the chat notification!");

            nUtil.postNotification(notificationId,
                    R.drawable.chatsmall,
                    NotificationUtil.WHITE,
                    title,
                    title,
                    message.getString("message"),
                    notificationIntent,
                    chatPrefs.getBoolean("vibratePhone", false),
                    chatPrefs.getBoolean("audibleNotify", false),
                    false,
                    true);
        }
    }

    private static boolean isMulticast(MessageDestination dest) {
        try {
            List<String> destinations = dest.getDestinationUids();
            if (destinations.size() == 1 && isMulticast(destinations.get(0)))
                return true;
        } catch (Exception ignore) {
        }
        return false;
    }

    private static boolean isMulticast(String uuid) {
        if (uuid != null) {
            try {
                IndividualContact contact = (IndividualContact) Contacts
                        .getInstance().getContactByUuid(uuid);
                if (contact != null
                        && ContactUtil.getIpAddress(contact) != null) {
                    return NetworkUtils.isMulticastAddress(ContactUtil
                            .getIpAddress(contact).getHost());
                }

            } catch (Exception ignored) {
                // Don't care...
            }
        }
        return false;
    }

    private static boolean isStreaming(String netConnectString) {
        if (netConnectString != null) {
            NetConnectString netConnStr = NetConnectString
                    .fromString(netConnectString);

            return netConnStr != null && netConnStr.getProto() != null &&
                    netConnStr.getProto().equalsIgnoreCase("stcp");
        }
        return false;
    }

    private void addPersistedGroups() {

        final Contacts contacts = Contacts.getInstance();
        rootGroup = contacts.getRootGroup();

        String address = chatPrefs.getString("chatAddress", "224.10.10.1");
        if (address.isEmpty())
            address = "224.10.10.1";

        String port = chatPrefs.getString("chatPort", "17012");
        if (port.isEmpty())
            port = "17012";

        NetConnectString ncs = NetConnectString.fromString(
                address + ":" + port + ":udp");
        //set up "All Chat" with only geochat connector
        if (_chatBroadcastContact == null) {
            _chatBroadcastContact = new IndividualContact(
                    _context.getString(R.string.all_chat_rooms),
                    GeoChatService.DEFAULT_CHATROOM_NAME);
            _chatBroadcastContact.addConnector(new GeoChatConnector(ncs));
            _chatBroadcastContact.getExtras().putBoolean("fakeGroup", true);
            _chatBroadcastContact.getExtras().putBoolean("metaGroup", true);
            contacts.addContact(rootGroup, _chatBroadcastContact);
        } else {
            _chatBroadcastContact.addConnector(new GeoChatConnector(ncs));
        }

        TadilJContactDatabase db = TadilJContactDatabase.getInstance();
        TadilJContact.updateChatConnector(NetConnectString.fromString(address
                + ":" + port
                + ":udp"), db);

        Resources res = _context.getResources();

        // Add all teams
        if (teamGroups == null) {
            String[] teamNames = res.getStringArray(R.array.squad_values);
            List<Contact> teams = new ArrayList<>();
            for (String name : teamNames)
                teams.add(new TeamGroup(name));
            teamGroups = new GroupContact("TeamGroups",
                    _context.getString(R.string.teams), teams, false);
            teamGroups.setIconUri("asset://icons/roles/team.png");
            contacts.addContact(rootGroup, teamGroups);
        }

        // Add all roles (except team member)
        String[] roles = res.getStringArray(R.array.role_values);
        for (String role : roles) {
            if (!role.equals("Team Member"))
                contacts.addContact(rootGroup, new RoleGroup(role));
        }

        List<Contact> tadilJContacts = db.getContacts();
        tadilJGroup = new GroupContact("TadilJGroup", "TadilJ", false);
        tadilJGroup.setIconUri("android.resource://" + _context
                .getPackageName() + "/" + R.drawable.tadilj_link);
        contacts.addContact(rootGroup, tadilJGroup);
        for (Contact c : tadilJContacts)
            contacts.addContact(tadilJGroup, c);

        if (userGroups == null) {
            userGroups = new GroupContact(Contacts.USER_GROUPS,
                    _context.getString(R.string.groups), false);
            userGroups.setHideIfEmpty(false);
            userGroups.setHideLockedGroups(true);
        }
        try {
            ncs = ContactUtil.getGeoChatIpAddress(_chatBroadcastContact);
            if (ncs != null) {
                chatService.setAllChatEndpoint(ncs.toString());

                // Convert database info to group contact
                List<String> conversationIds = chatService
                        .getPersistedConversationIds();
                Map<String, GroupContact> groups = new HashMap<>();
                for (String id : conversationIds) {
                    Contact existing = contacts.getContactByUuid(id);
                    if (existing == null && !id.equals(userGroups.getUID())) {
                        List<String> groupInfo = chatService.getGroupInfo(id);
                        if (groupInfo.size() < 4)
                            continue;
                        String groupName = groupInfo.get(0);
                        String childUIDs = groupInfo.get(1);
                        String parentUID = groupInfo.get(2);
                        String local = groupInfo.get(3);
                        List<String> gUIDs = new ArrayList<>(
                                Arrays.asList(childUIDs.split(",")));

                        // Convert names and UIDs to contacts
                        List<Contact> gContacts = new ArrayList<>();
                        for (String uid : gUIDs) {
                            if (uid.equals(userGroups.getUID()))
                                continue; // See ATAK-10641 - Post-issue fix
                            gContacts.add(new IndividualContact(uid, uid));
                        }

                        // Create user group with new contacts
                        // Chances are the contacts are not valid yet
                        // so they won't show up in the list immediately
                        GroupContact groupToAdd = new GroupContact(id,
                                groupName, gContacts, local.equals("true"));
                        if (parentUID != null && !parentUID.isEmpty())
                            groupToAdd.setParentUID(parentUID);
                        Log.d(TAG, "creating a group contact: "
                                + groupToAdd.getName() + " with uid "
                                + groupToAdd.getUID());
                        groups.put(id, groupToAdd);
                    }
                }
                // Set up hierarchy
                for (GroupContact gc : groups.values()) {
                    String parentUID = gc.getParentUID();
                    Contact parent = null;
                    // First check group list we just made
                    if (parentUID != null) {
                        parent = groups.get(parentUID);
                        // Then check existing groups
                        if (parent == null)
                            parent = Contacts.getInstance()
                                    .getContactByUuid(parentUID);
                    }
                    // Then default to User Groups
                    if (!GroupContact.isGroup(parent))
                        parent = userGroups;
                    if (parent != null)
                        ((GroupContact) parent).addContact(gc);
                }
            }
        } catch (Exception e) {
            //Do nothing
        }
        contacts.addContact(rootGroup, userGroups);

        // Update user groups locks after everything has been added
        for (Contact c : userGroups.getAllContacts(false)) {
            if (GroupContact.isGroup(c))
                ((GroupContact) c).updateLocks();
        }
    }

    private ConversationFragment getOrCreateFragment(
            final String conversationId,
            String conversationName,
            MessageDestination destination) {
        ConversationFragment toDisplay = fragmentMap.get(conversationId);

        if (toDisplay == null) {
            Log.d(TAG, "Creating ConversationFragment " + conversationId
                    + " to " + destination.getDestinationUids());

            toDisplay = new ConversationFragment();
            Bundle args = new Bundle();
            args.putString("id", conversationId);

            toDisplay.setArguments(args);

            if (conversationName == null) {
                // Look up group name
                Contact c = Contacts.getInstance().getContactByUuid(
                        conversationId);
                if (c != null)
                    conversationName = c.getName();
                if (conversationName == null)
                    return toDisplay;
            }

            if (conversationName.toLowerCase(LocaleUtil.getCurrent()).equals(
                    _mapView.getDeviceCallsign().toLowerCase(
                            LocaleUtil.getCurrent()))) {
                String name = "";
                Contact contact = Contacts.getInstance().getContactByUuid(
                        conversationId);

                if (contact != null) {
                    name = contact.getName();
                    //                    if (contact instanceof IndividualContact) {
                    //                        connector = ((IndividualContact) contact)
                    //                                .getConnector(IndividualContact.ConnectorType.PLUGIN);
                    //                    }
                    //} else {
                    // the sender is not in our contact list.  We should try to add him to the contact
                    // list when we get this, but we can't because we don't know the other properties
                    // (role, team).  So for now, punt.
                }

                //TODO: deal with null name by looking at the chat message
                toDisplay.setTitle(name);
            } else {
                toDisplay.setTitle(conversationName);
            }

            //TODO: isGroup ?
            final ConversationFragment finalToDisplay = toDisplay;
            finalToDisplay
                    .setMapView(_mapView)
                    .setDests(destination)
                    .setIsGroup(isMulticast(finalToDisplay.getDests()))
                    .setAckEnabled(isMulticast(finalToDisplay.getDests()))
                    .setSendBehavior(new ConversationFragment.SendBehavior() {
                        @Override
                        public void onSend(Bundle chatMessage) {
                            sendMessageToDests(chatMessage,
                                    finalToDisplay.getDests()
                                            .getDestinations());
                        }
                    })
                    .setHistoryBehavior(
                            new ConversationFragment.HistoryBehavior() {
                                @Override
                                public List<ChatLine> onHistoryRequest() {
                                    return getHistory(conversationId);
                                }
                            });

            // Cache the fragment in the lookup-map
            fragmentMap.put(conversationId, toDisplay);
        } else if (destination.getDestinationUids().size() == 1
                && destination.getDestinationUids().get(0) != null &&
                !destination.getDestinationUids().get(0)
                        .equals(MapView.getDeviceUid())) {
            /*
             * The only time that the destination list can have a count of one and have that one
             * destination be the device's UID is if the message was somehow looped back.  In that
             * case we don't want to update the destination list.  Update in ever other case.
             */
            toDisplay.setDests(destination);
        }

        Contact contact = Contacts.getInstance().getContactByUuid(
                conversationId);
        if (contact != null) {
            String name = contact.getName();
            Log.d(TAG, "conversation title now can be set to: " + name);
            toDisplay.setTitle(name);
        }
        return toDisplay;
    }

    private void sendMessageToDests(Bundle msg, List<Contact> dests) {
        if (chatService == null) {
            Log.w(TAG, "Non-existent chat service");
            return;
        }
        if (msg == null) {
            Log.w(TAG, "Non-existent chat message");
            return;
        }
        if (dests == null)
            Log.w(TAG, "Non-existent destination");

        // Replace sender's name with my device's callsign
        String originalSender = getConnectionStringFromMessageBundle(msg);
        if (originalSender == null) {
            // that function couldn't figure it out, so try just using fromAddr
            originalSender = msg.getString("from");
        }
        Log.d(TAG, "originalSender: " + originalSender);

        String selfUID = MapView.getDeviceUid();
        if (msg.get("sentTime") == null)
            msg.putLong("sentTime", (new CoordinatedTime()).getMilliseconds());
        msg.putString("senderCallsign", MapView.getMapView()
                .getDeviceCallsign());
        msg.putString("senderUid", selfUID);
        msg.putString("uid", selfUID);
        String ret = "a-f";
        if (_mapView != null && _mapView.getMapData() != null)
            ret = _mapView.getMapData().getString("deviceType", "a-f");
        msg.putString("deviceType", ret);
        msg.putString("protocol", "CoT");
        msg.putString("type", "GeoChat");
        Contact cont = Contacts.getInstance().getContactByUuid(
                msg.getString("conversationId"));
        if (dests == null)
            dests = new ArrayList<>();
        List<Contact> recipients = dests;
        if (cont != null) {
            if (GroupContact.isGroup(cont)) {
                GroupContact gc = (GroupContact) cont;
                if (gc.getUnmodifiable())
                    return;
                msg.putBoolean("groupOwner", gc.isUserCreated());
            } else if (cont instanceof TadilJContact)
                msg.putBoolean("tadilj", true);
            recipients = cont.getFiltered(true, true);
            msg.putString("parent", cont.getParentUID());
        }

        // Convert to strings
        Bundle paths = Contacts.buildPaths(cont);
        List<String> uids = new ArrayList<>();
        for (Contact c : recipients) {
            if (c == null)
                continue;
            String uid = c.getUID();
            if (c instanceof IndividualContact && !uids.contains(uid))
                uids.add(uid);
        }

        msg.putBundle("paths", paths);
        msg.putStringArray("destinations", uids.toArray(new String[0]));

        // Persist it in the DB
        try {
            chatService.persistMessage(msg);
        } catch (Exception e) {
            Log.w(TAG, "Error persisting message via Chat Service", e);
        }

        // Send it out to the network
        Log.d(TAG, "Sending to " + uids.size() + " destinations.");
        for (String destinationUid : uids) {
            if (destinationUid.equals(selfUID))
                continue;
            Log.d(TAG, "Sending to " + destinationUid);

            if (destinationUid.equals(GeoChatService.DEFAULT_CHATROOM_NAME)) {
                // Notify plugins to send a message to all chat rooms
                Intent intent = new Intent(PLUGIN_SEND_MESSAGE_ALL_CHAT_ROOMS);
                intent.putExtra(PLUGIN_SEND_MESSAGE_EXTRA, msg);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }

            Contact contact = Contacts.getInstance()
                    .getContactByUuid(destinationUid);
            if (contact instanceof IndividualContact) {
                IndividualContact individualContact = (IndividualContact) contact;
                Connector connector = individualContact.getConnector(
                        PluginConnector.CONNECTOR_TYPE);
                if (connector != null) {
                    String action = connector.getConnectionString();
                    Intent intent = new Intent(action);
                    intent.putExtra(PLUGIN_SEND_MESSAGE_EXTRA, msg);
                    AtakBroadcast.getInstance().sendBroadcast(intent);
                } else {
                    NetConnectString connection = ContactUtil
                            .getGeoChatIpAddress(individualContact);
                    Log.d(TAG, "Sending to destination: " + destinationUid
                            + " - " + (connection == null ? ""
                                    : connection
                                            .toString()));
                    try {
                        if (connection != null)
                            chatService.sendMessage(msg, individualContact);
                    } catch (Exception e) {
                        Log.w(TAG,
                                "Error sending message via Chat Service: ",
                                e);
                        Toast.makeText(_context, R.string.chat_text6,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Send a message to a list of different conversations
     * @param msg Plain-text message to send
     * @param convos List of conversations to send the message to
     *               Note: This is not necessarily the list of destination
     *               contacts (i.e. groups -> group members)
     */
    public void sendMessage(String msg, List<Contact> convos) {
        if (FileSystemUtils.isEmpty(convos)) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    ContactPresenceDropdown.SEND_LIST)
                            .putExtra("message", msg));
            return;
        }
        long sendTime = new CoordinatedTime().getMilliseconds();
        for (Contact c : convos) {
            if (c == null)
                continue;
            ChatLine toAdd = new ChatLine();
            // Each message requires a UUID per convo
            toAdd.messageId = UUID.randomUUID().toString();
            toAdd.timeSent = sendTime;
            toAdd.senderUid = MapView.getDeviceUid();
            toAdd.message = msg;
            toAdd.conversationId = c.getUID();
            toAdd.conversationName = c.getTitle();
            sendMessageToDests(toAdd.toBundle(), null);
        }
    }

    /**
     * Given a message, prompt the user for a contact to send it to.
     * Shortcut for sendMessage(msg, null).
     * @param msg the message to send
     */
    public void sendMessage(final String msg) {
        sendMessage(msg, null);
    }

    private List<ChatLine> getHistory(String conversationId) {
        List<ChatLine> ret = new LinkedList<>();
        // Add chat history...
        if (chatService != null) {
            List<Bundle> history;
            try {
                history = chatService.getHistory(conversationId);
                if (history != null) {
                    Iterator<Bundle> it = history.iterator();
                    while (it.hasNext()) {
                        Bundle msgBundle = it.next();
                        if (msgBundle != null) {
                            ChatLine chatLine = ChatLine.fromBundle(msgBundle);
                            // Mark all history as READ, but the last element
                            // was potentially just added.
                            if (it.hasNext()) {
                                chatLine.read = true;
                            }
                            ret.add(chatLine);
                        } else {
                            Log.w(TAG, "Got a null msg Bundle from history "
                                    + history);
                        }
                    }
                } else {
                    Log.w(TAG, "Received NULL history list for convo ID: "
                            + conversationId);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error looking-up chat history for "
                        + conversationId, e);
            }
        } else {
            Log.w(TAG, "Could not fetch history because chat service was NULL");
        }
        return ret;
    }

    public void openConversation(final String conversationId,
            final String conversationName,
            final String targetUID,
            boolean editable,
            MessageDestination destination) {

        // Make sure if we're opening a group chat that we refresh the current
        // list of contacts, but only if the contacts drop-down is closed
        // since we want to maintain active filters (see ATAK-13011)
        ContactPresenceDropdown contacts = CotMapComponent.getInstance()
                .getContactPresenceReceiver();
        if (contacts.isClosed()) {
            Contact c = Contacts.getInstance().getContactByUuid(targetUID);
            if (c instanceof GroupContact)
                c.syncRefresh(null, new EmptyListFilter());
        }

        ConversationFragment toDisplay = getOrCreateFragment(conversationId,
                conversationName,
                destination);
        toDisplay.setTargetUID(targetUID);

        //have to wait until the frag is created to update the badge
        toDisplay.addChatConvoFragCreateWatcher(this);

        toDisplay.setUserEntryAreaVisibility(editable);

        ChatDropDownReceiver cddr = new ChatDropDownReceiver();
        cddr.show(toDisplay);
    }

    private ConversationFragment getFragmentByConversationId(
            String conversationId) {
        return fragmentMap.get(conversationId);
    }

    public static boolean isSpecialGroup(String id) {
        return id != null
                &&
                (id.equals(GeoChatService.DEFAULT_CHATROOM_NAME) ||
                        id.equals(GeoChatService.DEFAULT_CHATROOM_NAME_LEGACY));
    }

    /**
     * Given an individual contact, open the chat drop down for that contact.
     * @param contact the individual contact
     * @param editable boolean if the user can use this drop down to chat or just view the
     *                 current chat.
     */
    public void openConversation(final IndividualContact contact,
            boolean editable) {
        // Open window for a specific contact
        if (contact != null
                && (ContactUtil.getIpAddress(contact) != null
                        || contact.hasConnector(PluginConnector.CONNECTOR_TYPE)
                        || !editable)) {
            Log.d(TAG,
                    "received an intent to open chat window for contact: "
                            + contact.getName() + " at "
                            + ContactUtil.getIpAddress(contact));
            openConversation(
                    contact.getUID(),
                    contact.getName(),
                    contact.getUID(),
                    editable,
                    new MessageDestination(contact.getUID()));
        }
    }

    /**
     * Provides the ability to open a conversation as a drop down.
     * @param title the title to provide the drop down
     * @param uid the uid of the conversation
     * @param contacts the contaxct list that is used to describe the conversation
     * @param editable if the user is allowed to send new chat in this opened drop down.
     */
    public void openConversation(String title, String uid, Contact[] contacts,
            boolean editable) {

        // Open window for a group (of contacts)
        if (contacts != null) {
            ArrayList<String> destinationUids = new ArrayList<>();
            for (Contact contact : contacts) {
                if (contact != null) {
                    destinationUids.add(contact.getUID());
                } else {
                    Log.w(TAG,
                            "Found a NULL (or wrong type) contact in multi-destination group: ");
                }
            }

            MessageDestination dest = new MessageDestination(destinationUids);
            openConversation(uid, title, null,
                    editable, dest);
        } else {
            openConversation(uid, editable);
        }
    }

    public void openConversation(String uid, boolean editable) {
        // opening from the radial menu and only have the uid
        if (!FileSystemUtils.isEmpty(uid)) {

            Contact newContact = Contacts.getInstance()
                    .getContactByUuid(uid);

            if (newContact != null) {
                openConversation(
                        newContact.getUID(),
                        newContact.getName(),
                        newContact.getUID(),
                        editable,
                        new MessageDestination(newContact
                                .getUID()));
            }
        }
    }

    private final BroadcastReceiver openChatWindowReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent != null) {
                Bundle message = intent.getParcelableExtra("message");
                boolean editable = intent.getBooleanExtra("editable",
                        true);
                // Open window from a message

                // we received a message and want to open it
                if (message != null) {
                    Log.d(TAG,
                            "received an intent to open chat window for message: "
                                    + message);

                    String conversationId = message
                            .getString("conversationId");
                    String conversationName = message
                            .getString("conversationName");

                    MessageDestination dest;
                    Contact c = Contacts.getInstance().getContactByUuid(
                            conversationId);
                    if (c != null) {
                        // Need to refresh team and role groups here
                        HierarchyListFilter filter = new HierarchyListFilter(
                                new HierarchyListItem.SortAlphabet());
                        c.refresh(filter);
                        dest = new MessageDestination(conversationId);
                    } else
                        dest = new MessageDestination(
                                getGroupMemberUidsFromMessage(message));

                    openConversation(conversationId, conversationName,
                            null, editable, dest);
                    // Don't know how to interpret this intent
                } else {
                    Log.w(TAG,
                            "received an intent to open chat window for unrecognized option...");
                }
            } else {
                Log.w(TAG, "received a NULL intent");
            }
        }
    };

    public void persistMessage(String message, GroupContact group,
            List<String> destinations) {
        try {
            final Bundle bundle = sendMessage(message, group, destinations);
            if (bundle != null)
                ChatManagerMapComponent.getChatService().persistMessage(bundle);
        } catch (Exception e) {
            Log.e(TAG, "error occurred", e);
        }
    }

    private Bundle sendMessage(String message, GroupContact group,
            List<String> destinations) {
        String[] dest = destinations.toArray(new String[0]);
        return sendMessage(message, group, dest, dest);
    }

    public Bundle sendMessage(String message, GroupContact group,
            String[] destinations, String[] recipients) {
        Bundle msg = buildMessage(message, group, destinations);
        if (!sendMessage(msg, recipients))
            return null;
        return msg;
    }

    public boolean sendMessage(Bundle msg, String[] recipients) {
        for (String dest : recipients) {
            Contact c = Contacts.getInstance().getContactByUuid(dest);
            if (c instanceof IndividualContact) {
                IndividualContact contact = (IndividualContact) c;
                try {
                    ChatManagerMapComponent.getChatService()
                            .sendMessage(msg, contact);
                } catch (Exception e) {
                    Log.w(TAG,
                            "Error sending message via Chat Service: "
                                    + e.getMessage());
                    Toast.makeText(_context,
                            _context.getString(R.string.chat_text6),
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        }
        return true;
    }

    public Bundle buildMessage(String message, GroupContact group,
            String[] destinations) {
        Bundle msg = new Bundle();

        String selfUID = MapView.getDeviceUid();
        String selfCallsign = MapView.getMapView().getDeviceCallsign();
        msg.putString("senderCallsign", selfCallsign);
        msg.putString("senderUid", selfUID);
        msg.putString("uid", selfUID);
        msg.putString("deviceType", MapView.getMapView().getMapData()
                .getString("deviceType"));
        msg.putString("protocol", "CoT");
        msg.putLong("sentTime", (new CoordinatedTime())
                .getMilliseconds());
        msg.putString("type", "GeoChat");
        msg.putString("message", message);
        msg.putString("conversationId", group.getUID());
        msg.putString("conversationName", group.getName());
        msg.putString("parent", group.getParentUID());
        msg.putBoolean("groupOwner", group.isUserCreated());

        if (destinations == null)
            destinations = new String[0];
        msg.putStringArray("destinations", destinations);
        msg.putBundle("paths", Contacts
                .buildPaths(group));
        return msg;
    }

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView view) {
        Log.d(TAG, "Starting ChatManagerMapComponent...");

        _instance = this;

        REMOVE_MESSAGE = MapView.getMapView()
                .getContext().getString(R.string.chat_text4);
        NEW_CONTACT_MESSAGE = MapView.getMapView()
                .getContext().getString(R.string.chat_text5);

        fragmentMap = new HashMap<>();
        _mapView = view;
        _context = context;
        chatService = GeoChatService.getInstance();

        // Get Chat User Preferences
        chatPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        // DocumentedIntentFilter for opening a chat window
        DocumentedIntentFilter openChatWindowFilter = new DocumentedIntentFilter();
        openChatWindowFilter.addAction(OPEN_GEOCHAT);
        AtakBroadcast.getInstance().registerReceiver(openChatWindowReceiver,
                openChatWindowFilter);

        // DocumentedIntentFilter for opening a chat window
        DocumentedIntentFilter modesEditedFilter = new DocumentedIntentFilter();
        modesEditedFilter.addAction("com.atakmap.android.MODES_EDITED");
        AtakBroadcast.getInstance().registerReceiver(modesEditedReceiver,
                modesEditedFilter);

        // DocumentedIntentFilter for incoming chat messages
        DocumentedIntentFilter newChatMessageFilter = new DocumentedIntentFilter();
        newChatMessageFilter
                .addAction("com.atakmap.android.chat.NEW_CHAT_MESSAGE");
        AtakBroadcast.getInstance().registerReceiver(newChatMessageReceiver,
                newChatMessageFilter);

        // DocumentedIntentFilter for incoming chat messages ie ChatMessagePopup Plugin
        DocumentedIntentFilter markMessageReadFilter = new DocumentedIntentFilter();
        markMessageReadFilter
                .addAction("com.atakmap.chat.markmessageread");
        markMessageReadFilter
                .addAction("com.atakmap.chatmessage.persistmessage");
        AtakBroadcast.getInstance().registerReceiver(markMessageReadReceiver,
                markMessageReadFilter);

        // Reset unread count when history is cleared
        AtakBroadcast.getInstance().registerReceiver(historyReceiver,
                new DocumentedIntentFilter(GeoChatService.HISTORY_UPDATE));

        // Create any dynamic groups (e.g., teams) conversations.
        // Needs to happen now so that, when we receive messages from
        // the team, they are recognized as TEAM messages
        //        String myTeamName = chatPrefs.getString("locationTeam", "Cyan");
        //        Log.d(TAG, "Creating conversation for TEAM: " + myTeamName);
        //        getOrCreateFragment(myTeamName, myTeamName, new TeamMessageDestination(myTeamName), context);
        //        
        //        getOrCreateFragment("Team Lead", "Team Lead", new RoleMessageDestination("Team Lead"), context);

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        context.getString(R.string.chatPreference),
                        context.getString(R.string.chat_text7),
                        "chatPreference",
                        context.getResources().getDrawable(
                                R.drawable.ic_menu_chat),
                        new ChatPrefsFragment()));

        addPersistedGroups();

        //Order of operations OK b/c this component starts up after CotMapComponent, per component.xml
        _geoChatHandler = new GeoChatConnectorHandler(_context);
        CotMapComponent.getInstance().getContactConnectorMgr()
                .addContactHandler(_geoChatHandler);

        Contacts.getInstance().updateTotalUnreadCount();
    }

    public static GeoChatService getChatService() {
        return chatService;
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        Log.d(TAG, "onDestroy()");
        AtakBroadcast.getInstance().unregisterReceiver(openChatWindowReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(newChatMessageReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(markMessageReadReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(historyReceiver);

        chatPrefs = null;
        if (chatService != null)
            chatService.dispose();
        if (FilteredContactsManager.getInstance() != null)
            FilteredContactsManager.getInstance().dispose();

    }

    @Override
    public void onChatConvoFragCreated(ConversationFragment frag) {
        Contacts.getInstance().updateTotalUnreadCount();
    }

    public static final String CHAT_ROOM_DROPDOWN_CLOSED = "com.atakmap.chat.chatroom_closed";

    public class ChatDropDownReceiver extends DropDownReceiver {

        private ConversationFragment _conversationFragment;

        public ChatDropDownReceiver() {
            super(_mapView);
        }

        public void show(ConversationFragment cf) {
            _conversationFragment = cf;
            showDropDown(cf, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    TWO_THIRDS_HEIGHT, true, true);
        }

        @Override
        public void disposeImpl() {
        }

        @Override
        protected boolean onBackButtonPressed() {
            //notify and tool in the application that the chatroom has been closed and
            //provide the chatroom uid as a model for determining if the tool catching this
            //event is the one they are interested in. Developers who listen for this should
            // unregister their receiver when they receive and process the return intent
            if (_conversationFragment.getArguments() != null
                    && _conversationFragment.getArguments().containsKey("id")) {
                Intent intent = new Intent(CHAT_ROOM_DROPDOWN_CLOSED);
                intent.putExtra("conversationId",
                        _conversationFragment.getArguments().getString("id"));
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
            closeDropDown();
            return true;
        }

        @Override
        public void onReceive(Context c, Intent i) {
        }
    }

    public static class MessageDestination {
        private final List<Contact> dest = new ArrayList<>();

        public MessageDestination(ArrayList<String> destinations) {
            if (destinations != null) {
                for (String uid : destinations)
                    dest.add(Contacts.getInstance().getContactByUuid(uid));
            }
        }

        public MessageDestination(String destination) {
            if (destination != null)
                dest.add(Contacts.getInstance()
                        .getContactByUuid(destination));
        }

        public List<Contact> getDestinations() {
            return this.dest;
        }

        public List<String> getDestinationUids() {
            List<String> ret = new ArrayList<>(dest.size());
            for (Contact c : dest) {
                if (c != null)
                    ret.add(c.getUID());
            }
            return ret;
        }
    }

    /**
     * Catches intent calls from specifically QuickChat Plugin
     * Receiver is used to mark a message received as "read" without
     * changing any other unread messages in the same ChatLineAdapter
     */
    private final BroadcastReceiver markMessageReadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;

            final Bundle chatBundle = intent.getBundleExtra("chat_bundle");
            if (chatBundle == null)
                return;

            if (action.equals("com.atakmap.chat.markmessageread")) {
                ConversationFragment convoFrag = getFragmentByConversationId(
                        chatBundle
                                .getString("conversationId"));

                //check @null on Conversation Fragment
                if (convoFrag != null) {
                    String messageId = chatBundle.getString("messageId");

                    //check @null on messageId
                    if (messageId == null) {
                        return;
                    }

                    //get ListAdapter From Convo Fragment
                    ConversationListAdapter adapter = convoFrag
                            .getChatAdapter();
                    if (adapter == null) {
                        return;
                    }
                    adapter.markSingleRead(messageId);
                }
                Contacts.getInstance().updateTotalUnreadCount(); //update chat AB subscript
            } else if (action.equals(
                    "com.atakmap.chatmessage.persistmessage")) {
                addMessageToConversation(chatBundle);
            }
        }
    };

    private final BroadcastReceiver modesEditedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                fragmentMap.clear();
            } else {
                Log.w(TAG, "received a NULL intent");
            }
        }
    };

    private final BroadcastReceiver historyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Clear all unread message counts
            List<Contact> all = Contacts.getInstance().getAllContacts();
            for (Contact c : all)
                c.setUnreadCount(0);
            Contacts.getInstance().updateTotalUnreadCount();
        }
    };

    /**
     * Set up a custom renderer for the chat line.
     * @param renderer the custom renderer or null if no renderer should be used.
     */
    public void registerChatMessageRenderer(ChatMesssageRenderer renderer) {
        chatMesssageRenderer = renderer;
    }

    /**
     * Package private ability for ConversationListAdapter to grab the renderer.
     * @return the renderer
     */
    ChatMesssageRenderer getChatMesssageRenderer() {
        return chatMesssageRenderer;
    }

    /**
     * Replacement for the intent currently in use for listenering to all chat messages
     * incoming to the system.  Adds a chat message listener to be notified.
     * Please note that the intent "com.atakmap.android.ChatMessagePopups.NEW_CHAT_RECEIVED"
     * will be deprecated as of 4.8
     * @param cml the chat message listener that is to be notified.
     */
    public void addChatMessageListener(ChatMessageListener cml) {
        chatMessageListeners.add(cml);
    }

    /**
     * Replacement for the intent currently in use for listenering to all chat messages
     * incoming to the system.  Removes a chat message listener to be notified.
     * Please note that the intent "com.atakmap.android.ChatMessagePopups.NEW_CHAT_RECEIVED"
     * will be deprecated as of 4.8
     * @param cml the chat message listener that is to be notified.
     */
    public void removeChatMessageListener(ChatMessageListener cml) {
        chatMessageListeners.remove(cml);
    }

    private void fireChatMessageReceived(Bundle bundle) {
        Bundle copy = new Bundle(bundle);
        for (ChatMessageListener cml : chatMessageListeners) {
            try {
                cml.chatMessageReceived(copy);
            } catch (Exception e) {
                Log.e(TAG, "error notifying: " + cml, e);
            }
        }
    }

}
