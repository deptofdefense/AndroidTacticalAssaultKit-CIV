
package com.atakmap.android.importexport.send;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.data.URIHelper;
import com.atakmap.android.data.URIScheme;
import com.atakmap.android.ipc.AtakBroadcast;
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

/**
 * Send content using the contacts list
 */
public class TAKContactSender extends MissionPackageSender {

    private static final String TAG = "TAKContactSender";

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
    public boolean sendContent(String uri, Callback callback) {

        if (uri == null)
            return false;

        // Send Mission Package or file
        if (uri.startsWith(URIScheme.MPM) || uri.startsWith(URIScheme.FILE)) {
            return super.sendContent(uri, callback);
        }

        // Send map item
        else if (uri.startsWith(URIScheme.MAP_ITEM)) {
            final MapItem item = URIHelper.getMapItem(_mapView, uri);
            if (item == null)
                return false;
            CoTInfoBroadcastReceiver.promptSendAttachments(item, null, null,
                    new Runnable() {
                        @Override
                        public void run() {
                            // Send marker only
                            Intent contactList = new Intent(
                                    ContactPresenceDropdown.SEND_LIST);
                            contactList.putExtra("targetUID", item.getUID());
                            AtakBroadcast.getInstance().sendBroadcast(
                                    contactList);
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
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    ContactPresenceDropdown.SEND_LIST)
                            .putExtra("com.atakmap.contact.CotEvent", event));
            return true;
        }

        return false;
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
}
