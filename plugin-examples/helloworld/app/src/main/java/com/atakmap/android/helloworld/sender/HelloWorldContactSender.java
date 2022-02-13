/*
 * Copyright 2021 PAR Government Systems
 *
 * Unlimited Rights:
 * PAR Government retains ownership rights to this software.  The Government has Unlimited Rights
 * to use, modify, reproduce, release, perform, display, or disclose this
 * software as identified in the purchase order contract. Any
 * reproduction of computer software or portions thereof marked with this
 * legend must also reproduce the markings. Any person who has been provided
 * access to this software must be aware of the above restrictions.
 */

package com.atakmap.android.helloworld.sender;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.URIContentRecipient;
import com.atakmap.android.data.URIContentSender;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.android.importexport.send.TAKContactSender;
import com.atakmap.android.maps.MapView;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * {@link URIContentSender} example that uses {@link TAKContactSender} as a base
 * Allows the user to send files or items to a TAK contact selectable from
 * the contact list.
 *
 * Also see {@link URIContentRecipient.Sender}
 */
public class HelloWorldContactSender extends TAKContactSender {

    private final Context _plugin;

    public HelloWorldContactSender(MapView mapView, Context plugin) {
        super(mapView);
        _plugin = plugin;
    }

    /**
     * Get the display name for this sender, which is typically shown in a
     * {@link TileButtonDialog} along with other sender options.
     *
     * @return Sender display name
     */
    @Override
    public String getName() {
        return _plugin.getString(R.string.hello_world);
    }

    /**
     * Get the display icon for this sender
     *
     * @return Icon drawable
     */
    @Override
    public Drawable getIcon() {
        return _plugin.getDrawable(R.drawable.ic_launcher);
    }

    /**
     * Check if a given URI is supported by this sender
     *
     * In this example we use the URIs {@link TAKContactSender} supports,
     * which includes map items, videos, files, and data packages
     *
     * @param uri URI to check
     * @return True if supported
     */
    @Override
    public boolean isSupported(String uri) {
        return super.isSupported(uri);
    }

    /**
     * Prompt to select recipients to send to
     *
     * This is an interface method provided by {@link URIContentRecipient.Sender}
     * which is not required. By default senders will take a file and handle
     * the recipient selection by themselves, but in certain workflows
     * (i.e. sending a high-res image attachment) it's ideal for the user to
     * select the recipients beforehand so potentially slow post-processing
     * doesn't require the user's attention for the entire send process.
     *
     * In this example the user is prompted to select contacts from the
     * contact list drop-down.
     *
     * @param uri URI to select recipients for
     * @param callback Callback for when a list of recipients has been selected
     */
    @Override
    public void selectRecipients(String uri, @NonNull URIContentRecipient.Callback callback) {
        super.selectRecipients(uri, callback);
    }

    /**
     * Send content to a list of recipients
     *
     * @param uri Content URI (see {@link URIHelper} for various URI conversion methods)
     * @param recipients List of recipients to send to (null to prompt)
     * @param callback Callback for when the content has been sent
     * @return True if send request successful
     */
    @Override
    public boolean sendContent(String uri, List<? extends URIContentRecipient> recipients, Callback callback) {
        return super.sendContent(uri, recipients, callback);
    }

    /**
     * Send content using this sender
     *
     * This will typically prompt the user to select the recipients before
     * continuing the send operation, unlike
     * {@link #sendContent(String, List, Callback)} which will send immediately
     * without prompting the user.
     *
     * @param uri Content URI (see {@link URIHelper} for various URI conversion methods)
     * @param callback Callback for when the content has been sent
     * @return True if send request successful
     */
    @Override
    public boolean sendContent(String uri, Callback callback) {
        return super.sendContent(uri, callback);
    }
}
