
package com.atakmap.android.chat;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.chat.ChatMessage.ChatVersion;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactUtil;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.GroupContact;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.contact.TadilJContact;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotAttribute;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.CotServiceRemote;

import java.util.ArrayList;

/**
 * The ability to parse a message into a bundle that is used by the ChatDatabase to insert
 * chat messages.
 */
public class ChatMessageParser {

    private static final String TAG = "ChatMessageParser";

    private final MapView _mapView;
    private final Context _context;
    private ChatMessage message;

    public ChatMessageParser(final MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    /**
     * Used to construct the CotMessage object.   Called prior to parser.getBundle() which will
     * return the event as a Bundle.   Calls to parse and getBundle must be guarded and should be
     * used together in a single thread.
     * @param event the CotEvent that represents the message.
     */
    public void parseCotEvent(final CotEvent event) {
        message = new ChatMessage();

        CotDetail detail = event.getDetail();

        String messageId = getMessageId(event);
        message.setMessageId(messageId);

        String protocol = "CoT"; //cotevent so cot
        message.setProtocol(protocol);

        CoordinatedTime time = event.getTime();
        message.setReceiveTime(time);

        CotDetail chatChild = event.findDetail("__chat");
        if (chatChild != null) {
            Contacts cts = Contacts.getInstance();
            ChatVersion version = getMessageVersionFromCotDetail(chatChild);
            message.setMessageVersion(version);

            String convId = getConversationUid(version, chatChild);
            if (FileSystemUtils.isEmpty(convId))
                convId = getFallbackConversationId(event);
            if (convId.equals(GeoChatService.DEFAULT_CHATROOM_NAME_LEGACY))
                convId = GeoChatService.DEFAULT_CHATROOM_NAME;
            message.setConversationId(convId);

            String senderUid = getSenderUid(version, event);
            message.setSenderUid(senderUid);

            String senderName = getSenderName(version, event, chatChild);
            message.setSenderName(senderName);

            String convName = chatChild.getAttribute("chatroom");
            if (convName.equals(GeoChatService.DEFAULT_CHATROOM_NAME_LEGACY))
                convName = GeoChatService.DEFAULT_CHATROOM_NAME;
            if (convId.equals(MapView.getDeviceUid())) {
                //TODO: make sure of what version we are running here
                Contact contact = cts.getContactByUuid(senderUid);
                if (contact != null) {
                    convName = contact.getName();
                    convId = contact.getUID();
                    message.setConversationId(convId);
                }
            }

            message.setConversationName(convName);

            //TODO: is the self uid in here?
            ArrayList<String> destinationArray = getDestinationsFromCotDetail(
                    chatChild);
            if (destinationArray.isEmpty())
                destinationArray = getDestinationsFromServerDestination(detail);

            message.setDestinations(destinationArray);

            String tj = chatChild.getAttribute("tadilj");
            boolean isTadilJ = tj != null && tj.equals("true");

            Contact conv = cts.getContactByUuid(convId);
            if (conv == null) {
                // Redirect TADIL-J message to receiver
                String myTadilJ = _mapView.getMapData().getString("tadiljId");
                if (!FileSystemUtils.isEmpty(myTadilJ)
                        && myTadilJ.equals(convId)) {
                    Contact sender = cts.getContactByUuid(senderUid);
                    if (sender instanceof IndividualContact) {
                        MapItem mi = ((IndividualContact) sender).getMapItem();
                        if (mi != null && mi.hasMetaValue("tadilj")) {
                            String tadilJ = mi.getMetaString("tadilj", "");
                            conv = cts.getContactByUuid(tadilJ);
                            if (conv == null)
                                conv = new TadilJContact(sender.getName(),
                                        tadilJ);
                            message.setConversationName(conv.getName());
                            message.setConversationId(conv.getUID());
                            isTadilJ = true;
                        }
                    }
                } else if (isTadilJ)
                    // Ignore message
                    message.setSenderUid(null);
            }
            message.setTadilJ(isTadilJ);

            String parentUID = chatChild.getAttribute("parent");
            String deleteUID = chatChild.getAttribute("deleteChild");
            CotDetail cotHier = chatChild.getFirstChildByName(0,
                    "hierarchy");
            if (!isTadilJ && (conv == null || conv instanceof GroupContact)
                    && parentUID == null && deleteUID == null
                    && cotHier == null && !senderUid.equals(convId)) {
                // Legacy group chat
                message.setParentUid(Contacts.USER_GROUPS);
                message.deleteChild(null);
                message.setGroupOwner(true);

                if (!destinationArray.contains(senderUid))
                    destinationArray.add(senderUid);
                final String groupsName = _context.getString(R.string.groups);
                // User groups
                Bundle hier = new Bundle();
                hier.putString("name", groupsName);
                hier.putString("uid", Contacts.USER_GROUPS);
                hier.putString("type", "group");
                // Top-level group
                Bundle gBundle = new Bundle();
                gBundle.putString("name", convName);
                gBundle.putString("uid", convId);
                gBundle.putString("type", "group");
                for (int i = 0; i < destinationArray.size(); i++) {
                    String dest = destinationArray.get(i);
                    String name = dest;
                    Contact c = cts.getContactByUuid(dest);
                    if (c != null)
                        name = c.getName();
                    Bundle cBundle = new Bundle();
                    cBundle.putString("name", name);
                    cBundle.putString("uid", dest);
                    cBundle.putString("type", "contact");
                    gBundle.putBundle(dest, cBundle);
                }
                hier.putBundle(convId, gBundle);
                message.setPaths(hier);
            } else {
                if (cotHier != null)
                    message.setPaths(getPaths(cotHier.getChild(0)));
                message.setParentUid(parentUID);
                message.deleteChild(deleteUID);
                String isOwner = chatChild.getAttribute("groupOwner");
                message.setGroupOwner(
                        isOwner != null && isOwner.equals("true"));
            }
        } else {

            CotDetail remarks = detail.getFirstChildByName(0, "remarks");
            if (remarks != null) {
                String id = remarks.getAttribute("source");

                message.setConversationName(id);
                message.setConversationId(id);
                message.setSenderName(id);
                message.setSenderUid(id);
                ArrayList<String> destinationArray = new ArrayList<>();

                NetConnectString ip = ContactUtil
                        .getGeoChatIpAddress(ChatManagerMapComponent
                                .getChatBroadcastContact());
                if (ip != null)
                    destinationArray.add(ip.toString());

                message.setDestinations(destinationArray);
            }
        }

        String messageContents = getMessageContents(detail);
        message.setMessage(messageContents);

    }

    /**
     * Turns the CotMessage set up by parse into a bundle.
     * @return a bundle representing the CotMessage.
     * @throws InvalidChatMessageException when the message is malformed.
     */
    public Bundle getBundle() throws InvalidChatMessageException {
        return message.toBundle();
    }

    private String getConversationUid(ChatVersion version, CotDetail chatNode) {
        String uid = "";
        switch (version) {
            case CHAT3:
                uid = chatNode.getAttribute("id");
                if (uid.equals("Streaming"))
                    uid = GeoChatService.DEFAULT_CHATROOM_NAME_LEGACY;
                break;
            case GEO_CHAT:
                String callsign = chatNode.getAttribute("chatroom");
                Contact contact = Contacts.getInstance()
                        .getFirstContactWithCallsign(callsign);
                if (contact != null) {
                    uid = contact.getUID();
                }
                break;
            default:
                Log.d(TAG,
                        "couldn't figure out chat version? \n"
                                + chatNode.toString());
        }
        return uid;
    }

    private ChatVersion getMessageVersionFromCotDetail(CotDetail detail) {
        CotDetail chatGroup = detail.getFirstChildByName(0, "chatgrp");

        if (chatGroup != null)
            return ChatVersion.CHAT3;
        else {
            return ChatVersion.GEO_CHAT;
        }
    }

    private String getMessageContents(CotDetail detail) {
        String message = "";

        if (detail.getFirstChildByName(0, "remarks") != null) {
            message = detail.getFirstChildByName(0, "remarks").getInnerText();
            if (message == null)
                message = "";
        }
        return message;
    }

    private ArrayList<String> getDestinationsFromCotDetail(CotDetail detail) {
        ArrayList<String> uidsOfDestinations = new ArrayList<>();
        CotDetail chatgrp = detail.getFirstChildByName(0, "chatgrp");
        if (chatgrp != null) {
            CotAttribute[] grpAttrs = chatgrp.getAttributes();
            for (CotAttribute grpAttr : grpAttrs) {
                if (grpAttr.getName().startsWith("uid")) {
                    String currentUid = grpAttr.getValue();
                    if (!uidsOfDestinations.contains(currentUid))
                        uidsOfDestinations.add(currentUid);
                }
            }
        }

        return uidsOfDestinations;
    }

    private ArrayList<String> getDestinationsFromServerDestination(
            CotDetail detail) {
        //TODO: this is in the format of an ip address so we need to look through this and find a UID
        ArrayList<String> uidsOfDestinations = new ArrayList<>();

        String serverdestination = null;

        final CotDetail serverChild = detail.getFirstChildByName(0,
                "__serverdestination");

        if (serverChild != null) {
            serverdestination = serverChild.getAttribute("destinations");
        }
        if (serverdestination != null) {
            String[] dests = serverdestination.split(",");
            for (String dest : dests) {
                if (dest.startsWith(CotServiceRemote.Proto.udp.toString())) {
                    if (!dest.contains("127.0.0.1")) { //Added for geochat
                        NetConnectString ip = ContactUtil
                                .getGeoChatIpAddress(ChatManagerMapComponent
                                        .getChatBroadcastContact());
                        if (ip != null) {
                            if (dest.equals(ip.toString())) {
                                uidsOfDestinations.add(
                                        GeoChatService.DEFAULT_CHATROOM_NAME);
                            } else {
                                //TODO: this is ip so we need to do something with it
                                String[] components = dest.split(":");
                                final String destination = components[1] + ":"
                                        + components[2] + ":" + components[0];
                                uidsOfDestinations.add(destination);
                                Log.d(TAG, "adding: " + destination);
                            }
                        }
                    }
                } else {
                    //TODO: this is probably ip so we need to do something with it
                    String[] components = dest.split(":"); //Have to split for backwards compatability with 2.2
                    if (components.length == 4)
                        uidsOfDestinations.add(components[3]);
                    else
                        uidsOfDestinations.add(dest);
                }
            }
        } else {
            uidsOfDestinations.add(GeoChatService.DEFAULT_CHATROOM_NAME);
        }

        return uidsOfDestinations;

    }

    private static String getMessageId(CotEvent event) {

        // Explicitly stated message ID
        CotDetail chatDt = event.findDetail("__chat");
        if (chatDt != null) {
            String msgId = chatDt.getAttribute("messageId");
            if (msgId != null)
                return msgId;
        }

        // Fallback parse from CoT event UID
        // This isn't as reliable because if the format of this changes or
        // one of the UIDs happen to contain a dot then this parser will fail
        String messageId = "";
        String uid = event.getUID();
        if (uid != null && uid.split("\\.").length > 0) {
            String[] split = uid.split("\\.");
            messageId = split[split.length - 1];
        }
        return messageId;
    }

    private String getFallbackConversationId(CotEvent event) {
        String convId = "";
        String uid = event.getUID();
        if (uid != null && uid.contains(".")) {
            String[] split = uid.split("\\.");
            if (event.toString().contains("<origin uid=")) //it's an APASS message
                convId = split[1];
            else if (split.length > 3) //Everything else *should* be in the right order
                convId = split[2];
        }
        if (FileSystemUtils.isEmpty(convId))
            convId = GeoChatService.DEFAULT_CHATROOM_NAME;
        return convId;
    }

    private static String getSenderUid(ChatVersion version, CotEvent event) {
        String senderSection = "";
        String senderUid = "";
        String uid = event.getUID();
        if (uid != null) //This is kinda wrong, do it some other way
        {
            String[] uidComponents = uid.split("\\.");
            if (uidComponents.length > 1) {
                senderSection = uidComponents[1];
            }
        }

        switch (version) {
            case CHAT3:
                //do nothing since the senderSection is already the uuid
                senderUid = senderSection;

                //in wintak chat the senderSection is actually the callsign, but they also put a sourceID so use that
                CotDetail detail = event.getDetail();
                CotDetail remarksDetail = detail.getFirstChildByName(0,
                        "remarks");

                String sourceID = null;
                String source = null;

                if (remarksDetail != null) {
                    sourceID = remarksDetail.getAttribute("sourceID");
                    source = remarksDetail.getAttribute("source");
                }

                if (sourceID != null && !sourceID.isEmpty()) {
                    senderUid = sourceID;
                } else if (source != null && !source.isEmpty()) {
                    if (source.startsWith("BAO.F.ATAK.")) {
                        senderUid = source.replace("BAO.F.ATAK.", "");
                    } else if (source.startsWith("BAO.F.WinTAK.")) {
                        senderUid = source.replace("BAO.F.WinTAK.", "");
                    } else {
                        senderUid = source;
                    }
                }

                break;
            case GEO_CHAT:
                //Geochat uses the callsign instead of its uuid, so hopefully we can find it in our contact list
                Contact contact = Contacts.getInstance()
                        .getFirstContactWithCallsign(senderSection);
                if (contact != null) {
                    senderUid = contact.getUID();
                }
                break;
            default:
                //do nothing
        }

        return senderUid;
    }

    private String getSenderName(ChatVersion version,
            CotEvent event, CotDetail chatDetail) {

        String senderSection = "";
        String senderName = chatDetail != null ? chatDetail
                .getAttribute("senderCallsign") : "";

        // Extract uid from sender section (why not just read senderUid? legacy?)
        String uid = event.getUID();
        if (uid != null) {
            String[] uidComponents = uid.split("\\.");
            if (uidComponents.length > 1)
                senderSection = uidComponents[1];
        }

        switch (version) {
            case CHAT3:
                Contact contact = Contacts.getInstance().getContactByUuid(
                        senderSection);
                if (contact != null)
                    // Use active contact name if available
                    senderName = contact.getName();
                break;
            case GEO_CHAT:
                //do nothing since the senderSection is already the callsign
                break;
            default:
                //do nothing
        }
        if (senderName == null || senderName.isEmpty())
            senderName = senderSection;

        return senderName;
    }

    private Bundle getPaths(CotDetail cotHier) {
        Bundle ret = new Bundle();
        if (cotHier == null)
            return ret;
        String uid = cotHier.getAttribute("uid");
        String name = cotHier.getAttribute("name");
        if (uid == null || name == null)
            return ret;
        ret.putString("uid", uid);
        ret.putString("name", name);
        ret.putString("type", cotHier.getElementName());
        for (int i = 0; i < cotHier.childCount(); i++) {
            CotDetail child = cotHier.getChild(i);
            if (child == null)
                continue;
            String cUID = child.getAttribute("uid");
            if (cUID == null)
                continue;
            ret.putBundle(cUID, getPaths(child));
        }
        return ret;
    }
}
