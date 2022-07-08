
package com.atakmap.android.attachment;

import android.content.Context;
import android.content.Intent;

import android.net.Uri;

import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.image.ImageContainer;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * DropDown for Attachments associated with a Marker
 * 
 * 
 */
public class AttachmentBroadcastReceiver extends DropDownReceiver implements
        MapEventDispatcher.MapEventDispatchListener {

    private static final String TAG = "AttachmentBroadcastReceiver";
    public static final String GALLERY = "com.atakmap.android.attachment.GALLERY";
    public static final String SEND_ATTACHMENT = "com.atakmap.android.attachment.SEND_ATTACHMENT";
    public static final String ATTACHMENT_RECEIVED = "com.atakmap.android.attachment.ATTACHMENT_RECEIVED";

    public AttachmentBroadcastReceiver(MapView mapView) {
        super(mapView);
        MapEventDispatcher dispatcher = getMapView().getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, this);
    }

    @Override
    public void disposeImpl() {
        MapEventDispatcher dispatcher = getMapView().getMapEventDispatcher();
        dispatcher.removeMapEventListener(MapEvent.ITEM_REMOVED, this);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
            MapItem item = event.getItem();
            if (item != null && item.getUID() != null) {
                List<File> dirs = new ArrayList<>();
                List<File> files = AttachmentManager.getAttachments(
                        item.getUID());
                try {
                    for (File f : files) {
                        // Add directory to delete
                        File dir = f.getParentFile();
                        if (!dirs.contains(dir))
                            dirs.add(dir);

                        //Log.d(TAG, "deleting: " + f);

                        // the cache is incorrectly set up to cache on the md5sum of
                        // the uri of the file and not the file itself.
                        String cache = FileSystemUtils.getItem(
                                "attachments/.cache").getPath() +
                                File.separator + HashingUtils.md5sum(
                                        Uri.fromFile(f).toString())
                                + ".png";
                        File cf = new File(cache);
                        //Log.d(TAG, "deleting thumb: " + cf);
                        if (IOProviderFactory.exists(cf))
                            FileSystemUtils.delete(cf);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "error occurred", e);
                }
                // Include default directory to delete, in case files is empty
                String fPath = AttachmentManager.getFolderPath(item.getUID());
                if (fPath != null) {
                    File attachments = new File(fPath);
                    if (!dirs.contains(attachments))
                        dirs.add(attachments);
                }
                // Delete attachment directories
                for (File dir : dirs) {
                    if (IOProviderFactory.exists(dir)) {
                        Log.d(TAG, "Removing attachments directory " + fPath);
                        FileSystemUtils.deleteDirectory(dir, false);
                    }
                }
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SEND_ATTACHMENT.equals(intent.getAction())) {
            // get UID and map item
            String uid = null;
            if (intent.getExtras() != null)
                uid = intent.getExtras().getString("uid");

            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Unable to Send Attachment with no UID");
                return;
            }

            MapGroup rootGroup = getMapView().getRootGroup();
            MapItem item = rootGroup.deepFindUID(uid);
            if (item == null) {
                Log.w(TAG,
                        "Unable to Send Attachments with no Map Item for UID: "
                                + uid);
                return;
            }

            // get file to send
            File file = null;
            String filepath = intent.getExtras().getString("filepath");
            if (!FileSystemUtils.isEmpty(filepath)) {
                if (FileSystemUtils.isFile(filepath)) {
                    file = new File(filepath);
                } else {
                    // a filepath was specified, but does not exist, bail out
                    Log.w(TAG, "Unable to Send Attachment with no file");
                    return;
                }
            }

            // Always use mission package
            //boolean bUseMissionPackage = intent.getExtras().getBoolean(
            //        "UseMissionPackage");
            String onReceiveAction = intent.getExtras().getString(
                    "onReceiveAction");
            sendAttachment(item, file, onReceiveAction);
        } else if (ATTACHMENT_RECEIVED.equals(intent.getAction())) {

            String sender = intent
                    .getStringExtra(
                            MissionPackageApi.INTENT_EXTRA_SENDERCALLSIGN);
            if (sender == null) {
                Log.w(TAG, "Received invalid attachment sender callsign");
                return;
            }

            MissionPackageManifest manifest = intent
                    .getParcelableExtra(
                            MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST);
            if (manifest == null || manifest.isEmpty()) {
                Log.w(TAG, "Received invalid attachment manifest");
                return;
            }

            int notificationid = intent.getIntExtra(
                    MissionPackageApi.INTENT_EXTRA_NOTIFICATION_ID,
                    -1);
            if (notificationid < 1) {
                Log.w(TAG, "Received invalid attachment notificationid");
                return;
            }

            // pull out the custom params identifying the map item the attachment is associated with
            NameValuePair p = manifest.getConfiguration().getParameter(
                    "callsign");
            if (p == null || !p.isValid()) {
                Log.w(TAG, "Received invalid attachment, missing callsign");
                return;
            }
            String callsign = p.getValue();

            p = manifest.getConfiguration().getParameter("uid");
            if (p == null || !p.isValid()) {
                Log.w(TAG, "Received invalid attachment, missing uid");
                return;
            }
            String uid = p.getValue();

            // now build and update the ongoing notification
            String message = sender + context.getString(R.string.sent)
                    + callsign
                    + "/" + manifest.getName();

            // zoom map and VIEW_ATTACHMENTS (if/when user touches notification)
            Intent notificationIntent = new Intent();
            notificationIntent.setAction(ImageGalleryReceiver.VIEW_ATTACHMENTS);
            notificationIntent.putExtra("uid", uid);
            notificationIntent.putExtra("focusmap", true);

            NotificationUtil.getInstance().postNotification(notificationid,
                    R.drawable.missionpackage_sent,
                    NotificationUtil.GREEN,
                    context.getString(R.string.file_transfer_complete),
                    message,
                    notificationIntent, true);

            Log.d(TAG, "Updated notification for: " + callsign + ", "
                    + manifest);
        } else if (GALLERY.equals(intent.getAction())) {
            Log.d(TAG, "Displaying Gallery");
            ImageGalleryReceiver.displayGallery();
        }
    }

    /**
     * @param onReceiveAction only used if bUseMissionPackage is true, If not set, then default to
     *            AttachmentListView.ATTACHMENT_RECEIVED
     */
    private void sendAttachment(final MapItem item,
            final File file, String onReceiveAction) {

        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled())
            return;

        if (FileSystemUtils.isEmpty(onReceiveAction))
            onReceiveAction = ATTACHMENT_RECEIVED;

        String uid = item.getUID();
        MissionPackageManifest manifest;
        if (file != null) {
            // send the specified file
            Log.v(TAG,
                    "Sending file via Mission Package Tool: "
                            + file.getAbsolutePath());

            // launch MPT to send the file and the CoT
            // Create the Mission Package containing the file, and the CoT for this map item
            manifest = MissionPackageApi.CreateTempManifest(file.getName(),
                    true, true, onReceiveAction);
            manifest.addMapItem(uid);
            manifest.addFile(file, uid);

            // Include extra metadata if available
            if (ImageContainer.NITF_FilenameFilter.accept(
                    file.getParentFile(), file.getName())) {
                File nitfXml = new File(file.getParent(), file.getName()
                        + ".aux.xml");
                if (IOProviderFactory.exists(nitfXml))
                    manifest.addFile(nitfXml, uid);
            }

            //set parameters to get file attached to the UID/map item
            manifest.getConfiguration().setParameter(new NameValuePair(
                    "callsign", item.getMetaString("callsign", null)));
            manifest.getConfiguration().setParameter(new NameValuePair(
                    "uid", item.getUID()));
        } else {
            // send all attachments
            List<File> attachments = AttachmentManager.getAttachments(uid);
            if (FileSystemUtils.isEmpty(attachments)) {
                Log.w(TAG, "Found no attachments to send for item: " + uid);
                return;
            }

            String callsign = item.getMetaString("callsign", uid);

            // send all attachments
            Log.v(TAG, "Sending all " + attachments.size()
                    + " files via Mission Package Tool: "
                    + uid);

            // launch MPT to send the file and the CoT
            // Create the Mission Package containing the file, and the CoT for this map item
            manifest = MissionPackageApi.CreateTempManifest(callsign,
                    true, true, onReceiveAction);
            manifest.addMapItem(uid);
            for (File attachment : attachments) {
                manifest.addFile(attachment, uid);
            }

            //set parameters to get file attached to the UID/map item
            manifest.getConfiguration().setParameter(new NameValuePair(
                    "callsign", item.getMetaString("callsign", null)));
            manifest.getConfiguration().setParameter(new NameValuePair(
                    "uid", item.getUID()));
        }

        // Send via content senders
        SendDialog.Builder b = new SendDialog.Builder(getMapView());
        b.setIcon(R.drawable.attachment);
        b.setMissionPackage(manifest);
        b.setMissionPackageCallback(new DeleteAfterSendCallback());
        b.show();
    }
}
