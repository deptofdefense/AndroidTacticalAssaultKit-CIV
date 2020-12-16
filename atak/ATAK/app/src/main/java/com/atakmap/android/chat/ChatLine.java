
package com.atakmap.android.chat;

import android.os.Bundle;

import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Date;

public class ChatLine {
    public String conversationId = null;
    public String conversationName = null;
    public String protocol = null;
    public String type = null;
    public Long timeReceived = null;
    public Long timeSent = null;
    public String senderUid = null;
    public String senderName = null;
    public String message = null;
    public String messageId = null;
    public boolean acked = false;
    public transient boolean read = false;
    public String[] destinations = null;

    /**
     * Typically, only one of these is set, so get the one that is not null. Returns NULL if both
     * are unset.
     */
    Long getTimeSentOrReceived() {
        return timeSent == null ? timeReceived : timeSent;
    }

    public String toString() {
        return "("
                + (timeReceived == null
                        ? new Date(timeSent).toString()
                        : new Date(timeReceived).toString())
                + ") "
                + senderUid + ": " + message;
    }

    /**
     * @param chatBundle with the following properties: conversationId -> String (reference this ID
     *            to send messages back to sender/grp) conversationName -> String (display name for
     *            conversation) protocol -> String (e.g., xmpp, geochat, etc.) type -> String
     *            (relevant info about the message type) receiveTime -> Long (time message was
     *            received) sendTime -> Long (time message was sent) senderName -> String (display
     *            name for sender) message -> String (text sent as message) messageId -> String
     *            (uuid for this message)
     */
    static ChatLine fromBundle(Bundle chatBundle) {

        ChatLine ret = new ChatLine();
        ret.conversationId = chatBundle.getString("conversationId");
        ret.conversationName = chatBundle.getString("conversationName");
        ret.protocol = chatBundle.getString("protocol");
        ret.type = chatBundle.getString("type");
        ret.timeReceived = chatBundle.getLong("receiveTime", -1);
        if (ret.timeReceived < 0)
            ret.timeReceived = null;
        ret.timeSent = chatBundle.getLong("sentTime", -1);
        if (ret.timeSent < 0)
            ret.timeSent = null; //possible case for both time sent and recieved being null!
        if (ret.timeSent == null && ret.timeReceived == null) //just in case
            ret.timeReceived = new CoordinatedTime().getMilliseconds(); //set it to now just to be on the safe side.
        ret.senderUid = chatBundle.getString("senderUid");
        ret.senderName = chatBundle.getString("senderCallsign");
        ret.message = chatBundle.getString("message");
        ret.messageId = chatBundle.getString("messageId");
        ret.destinations = getDestinations(chatBundle);
        ret.read = chatBundle.getBoolean("read", false);
        return ret;
    }

    private static String[] getDestinations(final Bundle chatBundle) {
        String destinationsString = chatBundle.getString("destinations");
        if (destinationsString == null)
            destinationsString = "";

        return destinationsString.split(ChatDatabase.ARRAY_DELIMITER);
    }

    /**
     * @return Bundle with the following properties: conversationId -> String (reference this ID to
     *         send messages back to sender/grp) conversationName -> String (display name for
     *         conversation) protocol -> String (e.g., xmpp, geochat, etc.) type -> String (relevant
     *         info about the message type) receiveTime -> Long (time message was received) sendTime
     *         -> Long (time message was sent) senderName -> String (display name for sender)
     *         message -> String (text sent as message) messageId -> String (uuid for this message)
     */
    Bundle toBundle() {
        Bundle ret = new Bundle();
        ret.putString("conversationId", conversationId);
        ret.putString("conversationName", conversationName);
        ret.putString("protocol", protocol);
        ret.putString("type", type);
        ret.putString("messageId", messageId);
        ret.putString("senderUid", senderUid);
        ret.putString("message", message);
        if (timeReceived != null) // need to do this for Longs because they will
                                  // be auto-boxed
            ret.putLong("receiveTime", timeReceived);
        if (timeSent != null) // need to do this for Longs because they will be
                              // auto-boxed
            ret.putLong("sentTime", timeSent);
        ret.putStringArray("destinations", destinations);
        return ret;
    }

}
