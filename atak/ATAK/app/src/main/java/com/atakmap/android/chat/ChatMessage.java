
package com.atakmap.android.chat;

import android.os.Bundle;

import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat message Class
 */
class ChatMessage {

    private String conversationName;
    private String conversationId;
    private String messageId;
    private String protocol = "CoT"; //how we got it (i.e. CotDispatcher)
    private CoordinatedTime receiveTime;
    private CoordinatedTime sentTime;
    private String senderUid;
    private String senderName;
    private String parentUid;
    private String deleteGroup;
    private boolean groupOwner = false;
    private Bundle destinationPaths;
    private ArrayList<String> destinations = new ArrayList<>();
    private boolean tadilJ = false;

    private String message;
    private ChatVersion messageVersion = ChatVersion.CHAT3; //how we should talk to this contact 

    enum ChatVersion {
        GEO_CHAT,
        CHAT3
    }

    public boolean isValid() {
        boolean conversationNamePresent = (conversationName != null);
        boolean conversationIdPresent = (conversationId != null
                && !conversationId
                        .trim().isEmpty());
        boolean messageIdPresent = (messageId != null && !messageId.trim()
                .isEmpty());
        boolean protocolPresent = (protocol != null);

        boolean receiveTimePresent = (receiveTime != null);
        boolean sentTimePresent = (sentTime != null);
        boolean timePresent = receiveTimePresent || sentTimePresent;

        boolean senderUidPresent = (senderUid != null);
        boolean senderNamePresent = (senderName != null);

        boolean messagePresent = (message != null);
        boolean destinationsPresent = (!destinations.isEmpty());

        return conversationNamePresent && conversationIdPresent
                && messageIdPresent && protocolPresent && timePresent
                && (senderUidPresent || senderNamePresent) && messagePresent
                && destinationsPresent;
    }

    private void validate() throws InvalidChatMessageException {
        if (isValid())
            return;

        if (conversationName == null || conversationName.isEmpty()) {
            throw new InvalidChatMessageException(
                    "conversationName is not assigned");
        }

        if (conversationId == null || conversationId.isEmpty()) {
            throw new InvalidChatMessageException("conversationId is null");
        }

        if (messageId == null || messageId.isEmpty()) {
            throw new InvalidChatMessageException("messageId is not set");
        }

        if (protocol == null) {
            throw new InvalidChatMessageException("prototcol is null");
        }

        if (receiveTime == null && sentTime == null) {
            throw new InvalidChatMessageException(
                    "neither sent nor receive time is set");
        }

        //TODO: look at the version to determine this instead
        if (senderName == null || senderUid == null) {
            throw new InvalidChatMessageException(
                    "neither senderName or senderUid is set");
        }

        if (message == null) {
            throw new InvalidChatMessageException("message is null");
        }

        if (destinations.isEmpty()) {
            throw new InvalidChatMessageException(
                    "no destinations for this message");
        }

    }

    Bundle toBundle() throws InvalidChatMessageException {
        validate();

        Bundle bundleToReturn = new Bundle();

        bundleToReturn.putString("conversationName", conversationName);
        bundleToReturn.putString("conversationId", conversationId);
        bundleToReturn.putString("messageId", messageId);
        bundleToReturn.putString("protocol", protocol);

        if (receiveTime != null) {
            bundleToReturn
                    .putLong("receiveTime", receiveTime.getMilliseconds());
        }

        if (sentTime != null) {
            bundleToReturn.putLong("sentTime", sentTime.getMilliseconds());
        }

        bundleToReturn.putString("senderUid", senderUid);
        bundleToReturn.putString("senderCallsign", senderName);

        bundleToReturn.putString("parent", parentUid);
        bundleToReturn.putBundle("paths", destinationPaths);
        bundleToReturn.putString("deleteChild", deleteGroup);
        bundleToReturn.putBoolean("groupOwner", groupOwner);
        bundleToReturn.putBoolean("tadilj", tadilJ);

        //TODO: change this
        bundleToReturn.putString("type", messageVersion.name());

        bundleToReturn.putString("message", message);

        if (!destinations.isEmpty())
            bundleToReturn.putStringArray("destinations",
                    destinations.toArray(new String[0]));

        return bundleToReturn;
    }

    /**
     * Gets the conversation identifier
     * @return the conversation identifier
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Sets the conversation identifier
     * @param conversationId the conversation identifier to use.
     */
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    /**
     * Returns the identifier associated with the chat message.
     * @return the message identifier
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Allows for setting of the the identifier associated with the chat message.
     * @param messageId  the message identifier
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * Returns the protocol that was used to deliver this message.
     * @return the protocol
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol that was used to deliver this message.
     * @param protocol  the protocol
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    void setReceiveTime(CoordinatedTime receiveTime) {
        this.receiveTime = receiveTime;
    }

    /**
     * Returns the sent time for the message
     * @return the sent time as a coordinated time.
     */
    public CoordinatedTime getSentTime() {
        return sentTime;
    }

    /**
     * Set the sent time for the message.
     * @param sentTime the snt time as a coordinated time
     */
    public void setSentTime(CoordinatedTime sentTime) {
        this.sentTime = sentTime;
    }

    /**
     * Gets the sender uid for the message.
     * @return the sender uid
     */
    public String getSenderUid() {
        return senderUid;
    }

    /**
     * Set the sender uid for the message
     * @param senderUid the sender uid
     */
    public void setSenderUid(String senderUid) {
        this.senderUid = senderUid;
    }

    /**
     * Get a copy of the destinations used for the chat message
     * @return a copy of the destinations
     */
    public ArrayList<String> getDestinations() {
        return new ArrayList<>(destinations);
    }

    /**
     * Set the destinations used for the chat message
     * @param destinations the destinations
     */
    public void setDestinations(List<String> destinations) {
        this.destinations = new ArrayList<>(destinations);
    }

    /**
     * Return the message.
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set the message
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    void setParentUid(String uid) {
        this.parentUid = uid;
    }

    public Bundle getPaths() {
        return this.destinationPaths;
    }

    public void setPaths(Bundle paths) {
        this.destinationPaths = paths;
    }

    void setMessageVersion(ChatVersion messageVersion) {
        this.messageVersion = messageVersion;
    }

    public String getConversationName() {
        return conversationName;
    }

    public void setConversationName(String conversationName) {
        this.conversationName = conversationName;
    }

    void setSenderName(String sender) {
        this.senderName = sender;
    }

    void deleteChild(String uid) {
        deleteGroup = uid;
    }

    void setGroupOwner(boolean isOwner) {
        groupOwner = isOwner;
    }

    void setTadilJ(boolean tadilJ) {
        this.tadilJ = tadilJ;
    }
}
