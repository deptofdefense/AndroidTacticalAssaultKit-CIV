
package com.atakmap.android.importexport.send;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.data.URIContentRecipient;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.manager.VideoXMLHandler;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;

/**
 * Send content using the contacts list
 */
public class TAKContactSender extends MissionPackageSender
        implements URIContentRecipient.Sender {

    private static final String TAG = "TAKContactSender";
    private static final String SELECT_RECIPIENTS = "com.atakmap.android.importexport.send.SELECT_RECIPIENTS";

    public TAKContactSender(MapView mapView) {
        super(mapView);
    }

    @Override
    public String getName() {
        return _context.getString(R.string.contact);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_menu_contact);
    }

    @Override
    public boolean isSupported(String uri) {

        if (uri == null)
            return false;

        return uri.startsWith(URIScheme.MAP_ITEM)
                || uri.startsWith(URIScheme.VIDEO)
                || super.isSupported(uri);
    }

    @Override
    public void selectRecipients(final String uri,
            @NonNull
            final URIContentRecipient.Callback callback) {
        // Register temporary receiver for when the user selects which contacts
        // to send to. We generate and remember a UID to make sure the receiver
        // is only executed when its matching select intent is received.
        final String receiverUID = UUID.randomUUID().toString();
        Intent callbackIntent = new Intent(SELECT_RECIPIENTS);
        callbackIntent.putExtra("uid", receiverUID);
        AtakBroadcast.getInstance().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Unregister temporary receiver
                AtakBroadcast.getInstance().unregisterReceiver(this);

                if (SELECT_RECIPIENTS.equals(intent.getAction())) {

                    // Make sure we're receiving the intent callback for the
                    // matching selectRecipients call above
                    String intentUID = intent.getStringExtra("uid");
                    if (!receiverUID.equals(intentUID))
                        return;

                    String[] contactUIDs = intent.getStringArrayExtra("sendTo");
                    if (FileSystemUtils.isEmpty(contactUIDs))
                        return;

                    // Convert contact UIDs to recipient objects
                    List<TAKRecipient> recipients = new ArrayList<>(
                            contactUIDs.length);
                    for (String uid : contactUIDs) {
                        Contact c = Contacts.getInstance()
                                .getContactByUuid(uid);
                        if (c != null)
                            recipients.add(new TAKRecipient(c));
                    }

                    // Notify callback that recipients have been selected
                    callback.onSelectRecipients(TAKContactSender.this, uri,
                            recipients);
                }
            }
        }, new DocumentedIntentFilter(SELECT_RECIPIENTS));

        // Show contacts list
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ContactPresenceDropdown.SEND_LIST)
                        .putExtra("sendCallback", callbackIntent));
    }

    @Override
    public boolean sendContent(String uri,
            List<? extends URIContentRecipient> recipients,
            Callback callback) {

        if (uri == null)
            return false;

        // Send Mission Package or file
        if (uri.startsWith(URIScheme.MPM) || uri.startsWith(URIScheme.FILE))
            return super.sendContent(uri, recipients, callback);

        // Check if we already have recipients selected
        final Intent intent = new Intent(ContactPresenceDropdown.SEND_LIST);
        if (!FileSystemUtils.isEmpty(recipients)) {
            String[] contactUIDs = new String[recipients.size()];
            for (int i = 0; i < contactUIDs.length; i++)
                contactUIDs[i] = recipients.get(i).getUID();
            intent.setAction(ContactPresenceDropdown.SEND_TO_CONTACTS);
            intent.putExtra("contactUIDs", contactUIDs);
        }

        // Send map item
        if (uri.startsWith(URIScheme.MAP_ITEM)) {
            final MapItem item = URIHelper.getMapItem(_mapView, uri);
            if (item == null)
                return false;
            CoTInfoBroadcastReceiver.promptSendAttachments(item, null, null,
                    new Runnable() {
                        @Override
                        public void run() {
                            // Send marker only
                            intent.putExtra("targetUID", item.getUID());
                            AtakBroadcast.getInstance().sendBroadcast(intent);
                        }
                    });
            return true;
        }

        // Send video alias
        else if (uri.startsWith(URIScheme.VIDEO)) {
            ConnectionEntry entry = URIHelper.getVideoAlias(uri);
            if (entry == null)
                return false;
            CotEvent event = VideoXMLHandler.toCotEvent(entry);
            if (event == null)
                return false;
            intent.putExtra("com.atakmap.contact.CotEvent", event);
            AtakBroadcast.getInstance().sendBroadcast(intent);
            return true;
        }

        return false;
    }

    @Override
    public boolean sendMissionPackage(MissionPackageManifest mpm,
            List<? extends URIContentRecipient> recipients,
            MissionPackageBaseTask.Callback mpCallback, Callback cb) {

        // Get contact UIDs from recipients
        List<String> uids = new ArrayList<>(recipients.size());
        for (URIContentRecipient recipient : recipients) {
            if (recipient instanceof TAKRecipient)
                uids.add(recipient.getUID());
        }

        String cbClass = mpCallback != null ? mpCallback.getClass().getName()
                : null;
        return MissionPackageApi.SendUIDs(_context, mpm, cbClass,
                uids.toArray(new String[0]), false);
    }

    @Override
    public boolean sendMissionPackage(MissionPackageManifest mpm,
            MissionPackageBaseTask.Callback mpCallback, Callback cb) {
        Intent send = new Intent(ContactPresenceDropdown.SEND_LIST);
        send.putExtra("sendCallback",
                MissionPackageReceiver.MISSIONPACKAGE_SEND);
        send.putExtra(MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                mpm);
        if (mpCallback != null)
            send.putExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME,
                    mpCallback.getClass().getName());
        send.putExtra("disableBroadcast", true);
        send.putExtra(MissionPackageApi.INTENT_EXTRA_SENDONLY,
                mpm.pathExists());

        AtakBroadcast.getInstance().sendBroadcast(send);
        return true;
    }

    private static class TAKRecipient extends URIContentRecipient {

        private final Contact contact;

        TAKRecipient(Contact contact) {
            super(contact.getName(), contact.getUID());
            this.contact = contact;
        }

        @Override
        public Drawable getIcon() {
            return contact.getIconDrawable();
        }
    }
}
