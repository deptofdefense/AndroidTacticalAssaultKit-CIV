
package com.atakmap.android.data;

import android.graphics.drawable.Drawable;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * Content recipient used with {@link URIContentSender}
 */
public abstract class URIContentRecipient {

    protected final String name, uid;

    protected URIContentRecipient(String name, String uid) {
        this.name = name;
        this.uid = uid;
    }

    protected URIContentRecipient(String name) {
        this(name, null);
    }

    /**
     * Get the displayable name for this recipient
     * @return Recipient name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the UID for this recipient
     * @return UID or null if N/A
     */
    public String getUID() {
        return this.uid;
    }

    /**
     * Get an icon that represents this recipient
     * @return Icon drawable or null if N/A
     */
    public Drawable getIcon() {
        return null;
    }

    @Override
    public String toString() {
        return "URIContentRecipient{name='" + name + '\'' + '}';
    }

    /**
     * Interface for a {@link URIContentSender} that supports an alternative workflow:
     * 1) User selects the sender they want to use
     * 2) User selects the recipients from that sender
     * 3) The sender notifies the {@link Callback} that recipients have been selected
     * 4) After some additional work, the {@link Callback} notifies the
     *    sender that content is ready to send
     */
    public interface Sender {

        /**
         * Select recipients from the sender
         * @param contentURI Content URI
         * @param callback Callback to fire when recipients have been selected
         */
        void selectRecipients(String contentURI, @NonNull Callback callback);

        /**
         * Request to sent content using this sender
         * @param contentURI Content URI
         * @param recipients List of recipients to send to, provided by
         *                   {@link #selectRecipients(String, Callback)}.
         *                   May be null (assumes empty; will prompt later).
         * @param callback Callback to fire when content has been sent
         * @return True if request successful, false on fail
         */
        boolean sendContent(String contentURI,
                List<? extends URIContentRecipient> recipients,
                URIContentSender.Callback callback);
    }

    /**
     * The {@link Sender} has provided a list of recipients to send to
     */
    public interface Callback {

        void onSelectRecipients(URIContentSender sender, String contentURI,
                List<? extends URIContentRecipient> recipients);
    }
}
