
package com.atakmap.android.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.attachment.AttachmentBroadcastReceiver;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.importfiles.task.ImportFileTask;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.tools.AtakLayerDrawableUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.os.FileObserver;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Used for tracking number of attachments on a given map item
 * Also contains several convenience methods for item attachments
 */
public class AttachmentManager {

    public static final String TAG = "AttachmentManager";
    private final MapView mapView;
    protected MapItem mapItem;
    private final ImageButton _attachmentsButton;
    private FileObserver fObserver;
    private int _prevAttachments;

    /**
     * Given a map item and an attachment button, track the number of attachments.
     */
    public AttachmentManager(final MapView mapView,
            final ImageButton attachmentsButton) {
        this.mapView = mapView;
        this._attachmentsButton = attachmentsButton;
        _attachmentsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mapItem != null)
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(ImageGalleryReceiver.VIEW_ATTACHMENTS)
                                    .putExtra("uid", mapItem.getUID()));
            }
        });
    }

    /**
     * Sets the current Attachment Manager instance with the map item.
     * @param mi the new map item to use with the attachment manager.
     */
    public void setMapItem(MapItem mi) {
        _prevAttachments = -1;
        this.mapItem = mi;
        if (fObserver != null)
            fObserver.stopWatching();

        if (this.mapItem == null)
            return;

        fObserver = new FileObserver(
                getFolderPath(this.mapItem.getUID(), true)) {
            @Override
            public void onEvent(int event, String path) {
                if (event == FileObserver.CREATE
                        || event == FileObserver.DELETE) {
                    updateAttachmentsButton();
                }
            }
        };
        fObserver.startWatching();

        refresh();
    }

    /**
     * Updates the current button for the attachment.
     */
    public void refresh() {
        boolean enabled = !MapItem.EMPTY_TYPE.equals(mapItem.getType());
        _attachmentsButton.setEnabled(enabled);
        hideAttachmentOption(mapItem.getType().startsWith("b-m-p-j"));
        updateAttachmentsButton();
    }

    public void cleanup() {
        if (fObserver != null) {
            fObserver.stopWatching();
            fObserver = null;
        }
    }

    public void hideAttachmentOption(boolean hide) {
        if (hide)
            _attachmentsButton.setVisibility(View.INVISIBLE);
        else
            _attachmentsButton.setVisibility(View.VISIBLE);

    }

    protected void updateAttachmentsButton() {
        if (mapItem == null)
            return;
        final String uid = mapItem.getUID();
        final int numAttachments = getNumberOfAttachments(uid);
        if (numAttachments != _prevAttachments) {
            _prevAttachments = numAttachments;
        }

        mapView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    LayerDrawable ld = (LayerDrawable) AttachmentManager.this.mapView
                            .getContext().getResources()
                            .getDrawable(R.drawable.attachment_badge);
                    if (ld != null) {
                        AtakLayerDrawableUtil.getInstance(
                                AttachmentManager.this.mapView.getContext())
                                .setBadgeCount(ld, numAttachments);
                        _attachmentsButton.setImageDrawable(ld);
                    } else {
                        _attachmentsButton
                                .setImageResource(R.drawable.attachment);
                    }
                } catch (NullPointerException npe) {
                    Log.d(TAG, "unknown error occurred", npe);
                }
            }
        });
    }

    public void send() {
        send("");
    }

    public void send(String onReceiveAction) {
        final String uid = mapItem.getUID();
        final int numAttachments = getNumberOfAttachments(uid);
        if (numAttachments > 0) {
            // Prompt the user to include marker attachments
            promptSendAttachments(mapItem, onReceiveAction);
        } else {
            sendCoT(mapItem);
        }
    }

    protected void sendCoT(final MapItem item) {
        // Make sure the object is shared since the user hit "Send".
        if (item != null) {
            item.setMetaBoolean("shared", true);

            Intent contactList = new Intent();
            contactList.setAction(ContactPresenceDropdown.SEND_LIST);
            contactList.putExtra("targetUID", item.getUID());
            AtakBroadcast.getInstance().sendBroadcast(contactList);
        }
    }

    /**
      * Prompt the user with the option to include marker attachments
      * @param item Item to send
      * @param onReceiveAction Optional receive callback
      */
    public void promptSendAttachments(final MapItem item,
            final String onReceiveAction) {

        // No item = no action
        if (item == null)
            return;

        // Make sure the object is shared since the user hit "Send".
        item.setMetaBoolean("shared", true);

        final String uid = item.getUID();
        List<File> attachments = getAttachments(uid);
        if (FileSystemUtils.isEmpty(attachments)) {
            sendCoT(item);
            return;
        }

        // Need context or else we can't display the dialog
        Context context = MapView.getMapView() != null
                ? MapView.getMapView().getContext()
                : null;
        if (context == null) {
            sendCoT(item);
            return;
        }

        // ask if user would like to include attachments
        AlertDialog.Builder adb = new AlertDialog.Builder(context);
        adb.setMessage(R.string.include_attachments);
        adb.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {

                        // Make sure file sharing is enabled
                        if (!MissionPackageMapComponent.getInstance()
                                .checkFileSharingEnabled()) {
                            sendCoT(item);
                            return;
                        }
                        sendMissionPackage(item, onReceiveAction);
                    }
                });
        adb.setNegativeButton(R.string.no,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        sendCoT(item);
                    }
                });
        adb.show();
    }

    /* Static helper methods */

    /**
     * Find all items with attachments
     *
     * @param rootGroup The root map group to use for scanning UIDs
     * @return List of map items with attachments
     */
    public static List<MapItem> findAttachmentItems(MapGroup rootGroup) {
        List<MapItem> ret = new ArrayList<>();

        // Invalid map group
        if (rootGroup == null)
            return ret;

        // Check if the attachments directory even exists
        File attDir = FileSystemUtils.getItem("attachments");
        if (!IOProviderFactory.exists(attDir))
            return ret;

        // Get all attachment sub-directories
        // Directory name corresponds to the map item UID
        File[] subDirs = IOProviderFactory.listFiles(attDir);
        if (FileSystemUtils.isEmpty(subDirs))
            return ret;

        // Add map items for valid sub-directories
        for (File d : subDirs) {
            // Ignore regular files
            if (!IOProviderFactory.isDirectory(d))
                continue;

            // Ignore empty sub-directories
            File[] files = IOProviderFactory.listFiles(d);
            if (FileSystemUtils.isEmpty(files))
                continue;

            // Directory is valid - find the map item based on directory name
            String uid = d.getName();
            MapItem item = rootGroup.deepFindUID(uid);
            if (item != null)
                ret.add(item);
        }

        return ret;
    }

    public static List<MapItem> findAttachmentItems() {
        MapView mv = MapView.getMapView();
        return findAttachmentItems(mv != null ? mv.getRootGroup() : null);
    }

    /**
     * Get list of files attached to a given map item
     *
     * @param uid Map item UID
     * @return List of file attachments
     */
    public static List<File> getAttachments(final String uid) {
        List<File> ret = new ArrayList<>();
        final String retval = getFolderPath(uid);
        if (retval != null) {
            File imageDir = new File(retval);
            if (IOProviderFactory.isDirectory(imageDir)) {
                File[] files = IOProviderFactory.listFiles(imageDir,
                        _fileFilter);
                if (files == null)
                    files = new File[0];
                ret.addAll(Arrays.asList(files));
            }
        }
        return ret;
    }

    /**
     * Get the number of files attached to a given map item
     *
     * @param uid Map item UID
     * @return Number of file attachments
     */
    public static int getNumberOfAttachments(final String uid) {
        return getAttachments(uid).size();
    }

    /**
     * Convenience method for adding an attachment to to a map item.  The attachment
     * is going to be copied into the attachment folder.  No effort is made to remove
     * the attachment from the original location.
     *
     * @param mi the map item to attach a file to.
     * @param f the attachment to attach to the map item.
     * @return File the file was attached correctly.
     */
    public static File addAttachment(final MapItem mi, final File f) {
        final String uid = mi.getUID();
        String path = getFolderPath(uid, true);
        if (FileSystemUtils.isEmpty(path))
            return null;

        File outDir = new File(path);
        File attachment;
        try (InputStream is = IOProviderFactory.getInputStream(f)) {
            attachment = new File(outDir, f.getName());

            // the passed in file is equal to the location where it will be attached
            if (attachment.getCanonicalPath().equals(f.getCanonicalPath()))
                return null;

            try (OutputStream os = IOProviderFactory
                    .getOutputStream(attachment)) {
                FileSystemUtils.copy(is, os);
            }
        } catch (IOException ioe) {
            return null;
        }

        return attachment;
    }

    /**
     * Method for getting the folder path related to attachments.  The return value will
     * be the folder that contains all attachments for a marker.
     *
     * @param uid the map item uid used to generate the folder path.
     * @param createDir automatically create the directory if it does not exist.
     * @return a string that is the folder path.
     */
    public static String getFolderPath(final String uid,
            final boolean createDir) {
        String uidEncoded;
        try {
            uidEncoded = URLEncoder.encode(uid,
                    FileSystemUtils.UTF8_CHARSET.name());
        } catch (UnsupportedEncodingException e) {
            Log.d(TAG, "error getting uid", e);
            return null;
        }

        String fPath = FileSystemUtils.sanitizeWithSpacesAndSlashes(
                FileSystemUtils.getItem("attachments").getPath()
                        + File.separator +
                        uidEncoded + File.separator);

        // we can not observe a folder that does not exist
        if (createDir) {
            File f = new File(fPath);
            if (!IOProviderFactory.exists(f) && !IOProviderFactory.mkdirs(f))
                Log.d(TAG, "unable to create the directory: " + f);
        }

        return fPath;
    }

    /**
     * Method for getting the folder path related to attachments.  The return value will
     * be the folder that contains all attachments for a marker.  If the folder does not 
     * exist it will be created
     *
     * @param uid the map item uid used to generate the folder path.
     * @return a string that is the folder path.
     */
    public static String getFolderPath(String uid) {
        return getFolderPath(uid, false);
    }

    private static final FilenameFilter _fileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            String fn = filename.toLowerCase(LocaleUtil.getCurrent());
            File f = new File(dir, filename);
            if (IOProviderFactory.isDirectory(f))
                return false;
            
            return !(fn.endsWith(".lnk") || fn.endsWith(".aux.xml"));
        }
    };

    /**
     * Convenience method for sending a marker with all attachments unconditionally.
     * @param mi the map item.
     * @param onReceiveAction intent that is fired when the item is sent.
     *  Listen for this if you want to be notified that it has been sent.
     */
    public static void sendWithAttachmentsNoPrompt(final MapItem mi,
            String onReceiveAction) {

        final String uid = mi.getUID();

        Intent sendMissionPackage = new Intent();
        sendMissionPackage
                .setAction(
                        AttachmentBroadcastReceiver.SEND_ATTACHMENT);
        sendMissionPackage.putExtra("uid", uid);
        sendMissionPackage.putExtra(
                "UseMissionPackage", true);
        if (!FileSystemUtils.isEmpty(onReceiveAction))
            sendMissionPackage.putExtra("onReceiveAction",
                    onReceiveAction);
        AtakBroadcast.getInstance().sendBroadcast(sendMissionPackage);
    }

    /**
     * Send one or more attachments as a mission package
     * @param marker Attachment marker
     * @param onReceiveAction Intent action to broadcast when sent
     */
    private static void sendMissionPackage(final MapItem marker,
            final String onReceiveAction) {
        final String uid = marker.getUID();
        List<File> attachments = getAttachments(uid);

        // use Mission Package Tools if more than one attachment
        // only case we don't is a for a single image attachment (send
        // CoT)
        Intent i = new Intent(AttachmentBroadcastReceiver.SEND_ATTACHMENT);
        i.putExtra("uid", uid);
        if (attachments.size() > 1) {
            i.putExtra("UseMissionPackage", true);
            if (!FileSystemUtils.isEmpty(onReceiveAction))
                i.putExtra("onReceiveAction", onReceiveAction);
        } else {
            // send single file
            File f = attachments.get(0);
            i.putExtra("filepath", f.getAbsolutePath());

            //If it is a single image prompt for resolution
            if (ImageDropDownReceiver.ImageFileFilter.accept(
                    f.getParentFile(), f.getName()))
                i.setAction(ImageDropDownReceiver.IMAGE_SELECT_RESOLUTION);
        }
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * View a given item attachment
     *
     * @param mapView Map view instance
     * @param mapItem Attachment map item
     * @param file Attachment file
     */
    public static void viewItem(MapView mapView, MapItem mapItem,
            final File file) {
        final Context ctx = mapView.getContext();
        String uid = null;
        if (mapItem != null)
            uid = mapItem.getUID();

        if (ImageDropDownReceiver.ImageFileFilter.accept(file.getParentFile(),
                file.getName())) {
            // handle image
            Log.v(TAG, "Viewing image: " + file.getAbsolutePath());
            Intent intent = new Intent(ImageDropDownReceiver.IMAGE_DISPLAY)
                    .putExtra("uid", uid)
                    .putExtra("imageURI",
                            Uri.fromFile(file).toString());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        } else if (ImageDropDownReceiver.VideoFileFilter.accept(
                file.getParentFile(), file.getName())) {
            // handle video
            Log.v(TAG, "Viewing video: " + file.getAbsolutePath());

            // TODO use external video player while working Bug 1269
            MIMETypeMapper.openFile(file, ctx);

            // Intent intent = new Intent();
            // intent.setAction("com.atakmap.maps.video.DISPLAY");
            // intent.putExtra("videoUrl", "file://"+file.getAbsolutePath());
            // intent.putExtra("uid", file.getName());
            // AtakBroadcast.getInstance().sendBroadcast(intent);
        } else {
            // handle other file types
            boolean bImportSupported = false;
            String ext = FileSystemUtils.getExtension(file, false, false);
            for (String sext : ImportFilesTask.getSupportedExtensions()) {
                if (sext.equalsIgnoreCase(ext)) {
                    bImportSupported = true;
                    break;
                }
            }

            if (!bImportSupported) {
                Log.v(TAG, "Viewing external file: " + file.getAbsolutePath());
                MIMETypeMapper.openFile(file, ctx);
                return;
            }

            // TODO: Resource string conversion; remove ugly concatenation
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(R.string.import_file);
            b.setMessage("Import '" + file.getName() + "' into "
                    + ctx.getString(R.string.app_name)
                    + " or open with external viewer?");
            b.setPositiveButton(ctx.getString(R.string.app_name) + " Import",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            Log.d(TAG, "Importing attachment: "
                                    + file.getAbsolutePath());

                            // task to copy file
                            ImportFileTask importTask = new ImportFileTask(ctx,
                                    null);
                            importTask
                                    .addFlag(
                                            ImportFileTask.FlagValidateExt
                                                    | ImportFileTask.FlagPromptOverwrite
                                                    | ImportFileTask.FlagCopyFile);
                            importTask.execute(file.getAbsolutePath());
                        }
                    });
            b.setNegativeButton("External Viewer",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            Log.v(TAG, "Viewing external file: "
                                    + file.getAbsolutePath());
                            MIMETypeMapper.openFile(file, ctx);
                        }
                    });
            b.show();
        }
    }
}
