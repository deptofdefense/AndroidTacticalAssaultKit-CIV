
package com.atakmap.android.chat;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.contact.ContactPresenceDropdown;
import android.os.Bundle;

import com.atakmap.android.chat.ChatLine.Status;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.contact.TadilJContact;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotServiceRemote.ConnectionListener;
import com.atakmap.comms.CotServiceRemote.CotEventListener;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GeoChatService implements
        CotEventListener,
        ConnectionListener {

    private static final Object lock = new Object();

    public static final String TAG = "GeoChatService";

    /**
     * The translation of these is only used in one place, but all of the backend business logic 
     * requires these to be in english
     */
    public static final String DEFAULT_CHATROOM_NAME = "All Chat Rooms";
    public static final String DEFAULT_CHATROOM_NAME_LEGACY = "All Streaming";

    static final String HISTORY_UPDATE = "com.atakmap.android.chat.HISTORY_UPDATE";

    private static String storedStreamingContactEndpoint = null;

    // Connectivity to CotService
    private final CotServiceRemote cotRemote;

    // DB
    private final ChatDatabase chatDb;

    private static GeoChatService _instance;

    private final MapView _mapView;
    private final Context _context;
    private final SharedPreferences _prefs;

    private GeoChatService() {
        _mapView = MapView.getMapView();
        _context = _mapView.getContext();
        chatDb = ChatDatabase.getInstance(_context);

        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        cotRemote = new CotServiceRemote();
        cotRemote.setCotEventListener(this);
        cotRemote.connect(this);
    }

    public synchronized static GeoChatService getInstance() {
        if (_instance == null)
            _instance = new GeoChatService();
        return _instance;
    }

    @Override
    public void onCotEvent(CotEvent cotEvent, Bundle bundle) {
        if (!isGeoChat(cotEvent))
            return;

        //Log.d(TAG, "CoT Event with Bundle: " + bundle);

        // Update delivery receipt for the message with the given UID
        Status status = Status.forCotType(cotEvent.getType());
        if (status != Status.NONE) {
            Bundle b = chatDb.getChatMessage(cotEvent.getUID());
            if (b == null) {
                Log.e(TAG,
                        "Received receipt for unknown chat message with UID: "
                                + cotEvent.getUID());
                return;
            }
            ChatLine line = ChatLine.fromBundle(b);
            if (line.status.ordinal() >= status.ordinal()) {
                // The status update we received is a lower/equal state to the
                // current status - ignore
                return;
            }
            b.putString("status", status.name());

            // Update receive/read time
            long time = cotEvent.getTime().getMilliseconds();
            if (status == Status.DELIVERED)
                b.putLong("receiveTime", time);
            else if (status == Status.READ)
                b.putLong("readTime", time);

            chatDb.addChat(b);
            sendToUiLayer(b);
            return;
        }

        try {
            ChatMessageParser parser = new ChatMessageParser(_mapView);
            parser.parseCotEvent(cotEvent);
            bundle.putAll(parser.getBundle());
            String senderUid = bundle.getString("senderUid");
            String convId = bundle.getString("conversationId");

            if (bundle.getBoolean("tadilj")) {
                // Ignore TADIL-J messages that weren't sent to us
                if (senderUid == null)
                    return;
                // Otherwise create one-way contact
                String convName = bundle.getString("conversationName");
                Contact tc = Contacts.getInstance()
                        .getContactByUuid(convId);
                Contact group = Contacts.getInstance()
                        .getContactByUuid("TadilJGroup");
                if (tc == null && group instanceof GroupContact) {
                    tc = new TadilJContact(convName, convId);
                    tc.getExtras().putBoolean("editable", false);
                    Contacts.getInstance().addContact((GroupContact) group,
                            tc);
                }
            }

            Contact sender = Contacts.getInstance().getContactByUuid(senderUid);

            if (sender == null) {
                //Sender isn't publishing their SA message
                String senderString = bundle.getString("from");
                String senderIp = null;
                /*int senderPort = -1;*/
                if (senderString != null && !senderString.isEmpty()) {
                    String[] parts = senderString.split(":");
                    senderIp = parts[0];
                    /*try {
                        senderPort = Integer.parseInt(parts[1]);
                    } catch (Exception e) {
                        Log.w(TAG, "Couldn't parse port!");
                    }*/
                }

                else
                    Log.e(TAG,
                            "Received message from unknown sender.  Possibly through the TAK server");
                if (senderUid == null) {
                    CotDetail link = cotEvent.findDetail("link");
                    if (link != null && FileSystemUtils.isEquals(
                            link.getAttribute("relation"), "p-p"))
                        senderUid = link.getAttribute("uid");
                }
                if (senderIp != null && senderUid != null
                        && !senderUid.equals(MapView.getDeviceUid())) {
                    String senderCallsign = bundle
                            .getString("senderCallsign");
                    String senderName = bundle
                            .getString("senderName");

                    if (FileSystemUtils.isEmpty(senderCallsign)
                            && senderName != null) {
                        senderCallsign = senderName;
                    }
                    if (FileSystemUtils.isEmpty(senderCallsign)) {
                        senderCallsign = senderUid;
                    }

                    if (senderUid.isEmpty()) {
                        senderUid = bundle.getString("senderCallsign");
                    }

                    if (senderIp.startsWith("224")) {
                        int port = 17012;
                        sender = new IndividualContact(
                                senderCallsign,
                                senderUid,
                                new NetConnectString("udp", senderIp,
                                        port));
                    } else {
                        int port = 4242;
                        sender = new IndividualContact(
                                senderCallsign,
                                senderUid,
                                new NetConnectString("tcp", senderIp,
                                        port));
                    }
                    Contacts.getInstance().addContact(sender);
                    //Make sure the chats go to the right place
                    bundle.putString("senderUid", senderUid);
                    if (convId == null || convId.equals(MapView.getDeviceUid()))
                        bundle.putString("conversationId", senderUid);
                }
            }

            // if the chat message has been delivered go ahead and respond back to the sender
            if (sender instanceof IndividualContact && FileSystemUtils.isEquals(
                    bundle.getString("conversationId"), senderUid)) {
                bundle.putString("status", Status.DELIVERED.name());
                sendStatusMessage(bundle, Status.DELIVERED,
                        (IndividualContact) sender);
            }

            //Log.d(TAG, "Persist Chat message: " + bundle);
            chatDb.addChat(bundle);
            sendToUiLayer(bundle);

            //Log.d(TAG, "bundle contents\n" + bundle);

        } catch (InvalidChatMessageException e) {
            Log.e(TAG, "Couldn't derive chat event from CoT: " + cotEvent,
                    e);
        }
    }

    /**
     * Only to be used as a shortcut for the Chat Line to generate a READ receipt.
     * Receipts are NOT sent for chat messages that are either null, belong
     * to the local user, or are part of a group chat (convo ID must equal sender ID).
     * @param line Chat line
     */
    void sendReadStatus(ChatLine line) {
        if (line != null && !line.isSelfChat() && line.messageId != null
                && line.senderUid != null
                && line.senderUid.equals(line.conversationId)) {
            Contact contact = Contacts.getInstance()
                    .getContactByUuid(line.senderUid);
            if (contact instanceof IndividualContact)
                sendStatusMessage(line.toBundle(), Status.READ,
                        (IndividualContact) contact);
        }
    }

    /**
     * Provide a delivery response for a given chat message
     * @param chatBundle Contains chat message data (including message UID)
     * @param sender the individual contact to respond back to
     */
    private void sendStatusMessage(Bundle chatBundle, Status receipt,
            IndividualContact sender) {

        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        // Don't send empty status or message to self
        if (receipt == Status.NONE || sender.getUID().equals(
                MapView.getDeviceUid()))
            return;

        // Message UID is required
        String messageId = chatBundle.getString("messageId");
        if (FileSystemUtils.isEmpty(messageId))
            return;

        CotEvent cotEvent = new CotEvent();

        cotEvent.setType(receipt.cotType);
        cotEvent.setUID(messageId);

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addDays(1));

        cotEvent.setVersion("2.0");
        cotEvent.setHow("m-g");

        // Reverse sender and receiver
        String toUID = sender.getUID();
        String selfUID = MapView.getDeviceUid();
        chatBundle = new Bundle(chatBundle);
        chatBundle.putString("senderUid", selfUID);
        chatBundle.putString("senderCallsign", mv.getDeviceCallsign());
        chatBundle.putString("uid", selfUID);
        chatBundle.putStringArray("destinations", new String[] {
                toUID
        });
        chatBundle.putString("deviceType", mv.getMapData()
                .getString("deviceType"));
        chatBundle.putString("parent", Contacts.getInstance().getRootGroup()
                .getUID());
        addSendDetails("__chatreceipt", chatBundle, cotEvent);

        CotMapComponent.getExternalDispatcher().dispatchToContact(
                cotEvent, sender);
    }

    /**
     * Add CoT details required for sending CoT messages
     * @param chatElemName The name of the main chat element
     * @param chatMessage Chat message bundle
     * @param cotEvent CoT event to add to
     */
    private void addSendDetails(String chatElemName, Bundle chatMessage,
            CotEvent cotEvent) {
        String from = chatMessage.getString("senderUid");
        if (from == null)
            from = "Android";
        String fromCallsign = chatMessage.getString("senderCallsign");
        if (fromCallsign == null)
            fromCallsign = from;
        String type = chatMessage.getString("deviceType");
        if (type == null)
            type = "a-f";
        String uid = chatMessage.getString("uid");
        if (uid == null)
            uid = "Android";

        String connectionSettings = NetworkUtils.getIP() + ":4242:tcp:" + from;
        String room = chatMessage.getString("conversationName");
        String id = chatMessage.getString("conversationId");
        if (room == null)
            room = DEFAULT_CHATROOM_NAME;
        if (id == null)
            id = UUID.randomUUID().toString();
        String messageId = chatMessage.getString("messageId");
        if (messageId == null)
            messageId = UUID.randomUUID().toString();

        if (_prefs.getBoolean("dispatchLocationCotExternal", true)) {
            if (_prefs.getBoolean("dispatchLocationHidden", false)) {
                cotEvent.setPoint(CotPoint.ZERO);
            } else {
                cotEvent.setPoint(new CotPoint(_mapView.getSelfMarker()
                        .getPoint()));
            }

        } else {
            cotEvent.setPoint(CotPoint.ZERO);
        }

        CotDetail detail = new CotDetail("detail");
        cotEvent.setDetail(detail);

        CotDetail __chat = new CotDetail(chatElemName);
        __chat.setAttribute("id", id);
        __chat.setAttribute("messageId", messageId);

        String[] dests = chatMessage.getStringArray("destinations");
        if (dests == null) {
            dests = new String[] {};
            chatMessage.putStringArray("destinations", dests);
        }

        // Legacy chat group info - read from hierarchy node when possible
        CotDetail __chatContact = new CotDetail("chatgrp");
        __chatContact.setAttribute("id", id);
        int i = 0;
        __chatContact.setAttribute("uid" + i++,
                _mapView.getSelfMarker().getUID());
        for (String dest : dests)
            __chatContact.setAttribute("uid" + i++, dest);

        __chat.setAttribute("senderCallsign", fromCallsign);
        __chat.setAttribute("chatroom", room);
        __chat.setAttribute("parent", chatMessage.getString("parent", null));
        __chat.setAttribute("groupOwner", String.valueOf(
                chatMessage.getBoolean("groupOwner", false)));
        String deleteUID = chatMessage.getString("deleteChild", null);
        if (deleteUID != null)
            __chat.setAttribute("deleteChild", deleteUID);
        boolean tadilJ = chatMessage.getBoolean("tadilj", false);
        if (tadilJ)
            __chat.setAttribute("tadilj", "true");
        __chat.addChild(__chatContact);

        Bundle pathsBundle = chatMessage.getBundle("paths");
        if (pathsBundle != null && !pathsBundle.isEmpty()) {
            CotDetail hierarchy = new CotDetail("hierarchy");
            hierarchy.addChild(parseHierarchy(null, pathsBundle));
            __chat.addChild(hierarchy);
        }

        detail.addChild(__chat);

        CotDetail link = new CotDetail("link");
        link.setAttribute("uid", uid);
        link.setAttribute("type", type);
        link.setAttribute("relation", "p-p");
        detail.addChild(link);

        CotDetail __serverdestination = new CotDetail("__serverdestination");
        __serverdestination.setAttribute("destinations", connectionSettings);
        detail.addChild(__serverdestination);
    }

    private void sendToUiLayer(Bundle chatMessageBundle) {
        //Log.d(TAG, "Sending Chat message to UI layer: " + chatMessageBundle);
        Intent gotNewChat = new Intent();
        gotNewChat.setAction("com.atakmap.android.chat.NEW_CHAT_MESSAGE");
        gotNewChat.putExtra("id", chatMessageBundle.getLong("id"));
        gotNewChat.putExtra("groupId", chatMessageBundle.getLong("groupId"));
        gotNewChat.putExtra("conversationId",
                chatMessageBundle.getString("conversationId"));
        AtakBroadcast.getInstance().sendBroadcast(gotNewChat);

        // Refresh chat drop-down (if it's open)
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ContactPresenceDropdown.REFRESH_LIST));
    }

    /**
     * @return CotEvent from the info in chatMessage Bundle
     * @param chatMessage conversationId -> String (reference this to send messages) messageId ->
     *            String (uuid for this message) protocol -> String (e.g., xmpp, geochat, etc.) type
     *            -> String (relevant info about the message type) conversationName -> String
     *            (display name for conversation) receiveTime -> Long (time message was received)
     *            senderName -> String (display name for sender) message -> String (text sent as
     *            message) callsign -> String (this device's callsign) deviceType -> String (this
     *            device's CoT type) uid -> String (this device's CoT uid)
     */
    private CotEvent bundleToCot(Bundle chatMessage) {
        //Log.d(TAG, "Converting Bundle to CoT Event.");

        CotEvent cotEvent = new CotEvent();

        cotEvent.setType("b-t-f");

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addDays(1));

        cotEvent.setVersion("2.0");
        cotEvent.setHow("h-g-i-g-o");

        String from = chatMessage.getString("senderUid");
        if (from == null)
            from = "Android";
        String id = chatMessage.getString("conversationId");
        if (id == null)
            id = UUID.randomUUID().toString();
        String message = chatMessage.getString("message");
        if (message == null)
            message = "";
        String messageId = chatMessage.getString("messageId");
        if (messageId == null)
            messageId = UUID.randomUUID().toString();

        cotEvent.setUID("GeoChat." + from + "." + id + "." + messageId);

        // Add details required for sending
        addSendDetails("__chat", chatMessage, cotEvent);

        String[] dests = chatMessage.getStringArray("destinations");
        CotDetail remarks = new CotDetail("remarks");
        remarks.setAttribute("source", "BAO.F.ATAK." + from);
        for (String dest : dests) {
            if (!dest.equals(MapView.getDeviceUid())) {
                if (dest.equals(id))
                    remarks.setAttribute("to", dest);
                else {
                    remarks.removeAttribute("to");
                    break;
                }
            }
        }
        remarks.setAttribute("time", time.toString());
        remarks.setInnerText(message);
        cotEvent.getDetail().addChild(remarks);

        //Log.d(TAG, "chat: " + cotEvent);

        return cotEvent;
    }

    /**
     * @return true if the CoT event is a GeoChat message
     */
    private static boolean isGeoChat(CotEvent cotEvent) {
        return cotEvent != null
                && cotEvent.isValid()
                && cotEvent.getDetail() != null
                // (KYLE) non-ATAK devices don't necessarily specify this... looking at cot type
                // instead (Aug 1, 2014)
                // && cotEvent.getDetail().getFirstChildByName(0, "__chat") != null)
                && cotEvent.getType().startsWith("b-t-f");
    }

    /**
     * only allowed to be called by the MapComponent.  Marking package private.
     */
    void dispose() {
        //Log.d(TAG, "onDestroy()");
        setStreamingContactEndpoint(null);
        cotRemote.disconnect();

        if (chatDb != null) //should never happen, but let's be safe
            chatDb.close();
    }

    @Override
    public void onCotServiceConnected(Bundle fullServiceState) {
        Log.d(TAG, "Connected to CotService.");

        setStreamingContactEndpoint(storedStreamingContactEndpoint);
    }

    @Override
    public void onCotServiceDisconnected() {
        //Log.d(TAG, "Disconnected to CotService.");
    }

    private void setStreamingContactEndpoint(String endpoint) {
        synchronized (lock) {
            if (cotRemote != null) {
                if (storedStreamingContactEndpoint != null) {
                    cotRemote.removeInput(storedStreamingContactEndpoint);
                    cotRemote.removeOutput(storedStreamingContactEndpoint);
                }
                if (endpoint != null) {
                    Bundle geoChatMulticastInputChannel = new Bundle();
                    geoChatMulticastInputChannel.putString("description",
                            "Chat input");
                    Bundle geoChatMulticastOutputChannel = new Bundle();
                    geoChatMulticastOutputChannel.putString("description",
                            "Chat output");
                    cotRemote.addInput(endpoint,
                            geoChatMulticastInputChannel);
                    // The chat service manages this output - do not persist it
                    geoChatMulticastOutputChannel.putBoolean("noPersist", true);
                    geoChatMulticastOutputChannel.putBoolean("isChat", true);
                    cotRemote
                            .addOutput(endpoint, geoChatMulticastOutputChannel);
                }
            }
            storedStreamingContactEndpoint = endpoint;
        }
    }

    /** 
     * Given a cot message bundle and a destination, send the message out to
     * to the appropriate destination.
     * @param cotMessage, the chat message in a bundle form.
     * @param destination individual contact destination to send the chat message to.
     */
    public void sendMessage(Bundle cotMessage, IndividualContact destination) {
        if (cotMessage != null && destination != null) {
            CotEvent cotEvent = bundleToCot(cotMessage);
            if (cotEvent != null) {
                // Special case out all chat destination
                if (destination.getExtras().getBoolean("fakeGroup", false)) {
                    Log.d(TAG, "Broadcasting message: " + cotMessage);
                    CotMapComponent.getExternalDispatcher()
                            .dispatchToBroadcast(cotEvent);

                } else if (destination instanceof TadilJContact) {
                    //Log.d(TAG, "Sending TADIL-J message: " + cotMessage
                    //        + " to " + destination.getUID() + " callsign "
                    //        + destination.getName());
                    CotMapComponent.getExternalDispatcher()
                            .dispatchToContact(cotEvent, null);
                } else {
                    //Log.d(TAG, "Sending message: " + cotMessage + " to "
                    //        + destination.getUID() + " callsign "
                    //        + destination.getName());
                    CotMapComponent.getExternalDispatcher()
                            .dispatchToContact(
                                    cotEvent, destination);
                }
            } else {
                Log.w(TAG, "Could not create CotEvent from Bundle: "
                        + cotMessage);
            }
        } else {
            Log.w(TAG,
                    "CoT Dispatcher, Destination, or Message is NULL.  Cannot send GeoChat message: "
                            + cotMessage + " to " + destination);
        }
    }

    public Bundle getMessage(long rowIdOfMessage, long rowIdOfGroup) {

        Log.d(TAG, "rowIdOfMessage: " + rowIdOfMessage + " rowIdOfGroup: "
                + rowIdOfMessage);
        Bundle bundle = new Bundle();
        if (rowIdOfMessage != -1) {
            final Bundle b = chatDb.getMessage(rowIdOfMessage,
                    ChatDatabase.TABLE_CHAT);
            if (b != null)
                bundle.putAll(b);

            String convId = bundle.getString("conversationId");
            if (convId != null
                    && (convId.equals(DEFAULT_CHATROOM_NAME)
                            || convId.equals(DEFAULT_CHATROOM_NAME_LEGACY)
                            || convId.equals(
                                    ChatManagerMapComponent.getTeamName())
                            || convId.equals(
                                    ChatManagerMapComponent.getTeamName())))
                bundle.putString("conversationName", convId);
            else {
                Contact sender = Contacts.getInstance().getContactByUuid(
                        bundle.getString("senderUid"));
                if (sender != null)
                    bundle.putString("conversationName", sender.getName());
            }
        }
        if (rowIdOfGroup != -1) {
            final Bundle b = chatDb.getMessage(rowIdOfGroup,
                    ChatDatabase.TABLE_GROUPS);
            if (b != null)
                bundle.putAll(b);
        }
        return bundle;
    }

    List<Bundle> getHistory(String conversationName) {
        return chatDb.getHistory(conversationName);
    }

    List<String> getPersistedConversationIds() {
        return chatDb.getPersistedConversationIds();
    }

    List<String> getGroupInfo(String groupName) {
        return chatDb.getGroupInfo(groupName);
    }

    /**
     * Given a CoT message in bundle form, add the message to the chat database and return the list
     * of ids in string form.
     * @param cotMessage the message bundle
     * @return the list of database identifiers in string form.
     */
    public List<String> persistMessage(Bundle cotMessage) {
        final List<String> ret = new ArrayList<>();

        //Log.d(TAG, "Persist Chat message: " + chatMessageBundle);
        List<Long> ids = chatDb.addChat(cotMessage);
        for (Long id : ids) {
            ret.add(Long.toString(id));
        }

        return ret;
    }

    void clearMessageDB() {
        chatDb.clearAll();
    }

    private void clearOlderThan(long time) {
        chatDb.clearOlderThan(time);
    }

    public void exportHistory(String filename) {
        chatDb.exportHistory(filename);
    }

    void setAllChatEndpoint(String endpoint) {
        setStreamingContactEndpoint(endpoint);
    }

    private static CotDetail parseHierarchy(Contact contact, Bundle hier) {
        Contacts cts = Contacts.getInstance();
        if (contact == null)
            contact = cts.getContactByUuid(Contacts.USER_GROUPS);
        CotDetail cotHier = new CotDetail(
                contact instanceof GroupContact ? "group" : "contact");
        cotHier.setAttribute("uid", contact.getUID());
        cotHier.setAttribute("name", contact.getName());
        if (hier == null)
            return cotHier;
        for (String key : hier.keySet()) {
            Object child = hier.get(key);
            if (!(child instanceof Bundle))
                continue;
            Bundle cBundle = (Bundle) child;
            String cUID = cBundle.getString("uid");
            Contact c = cts.getContactByUuid(cUID);
            if (c == null) {
                if (cUID != null && cUID.equals(MapView.getDeviceUid()))
                    c = CotMapComponent.getInstance().getSelfContact(false);
                else
                    continue;
            }
            CotDetail childHier = parseHierarchy(c, cBundle);
            if (c != contact)
                cotHier.addChild(childHier);
            else
                cotHier = childHier;
        }
        return cotHier;
    }
}
