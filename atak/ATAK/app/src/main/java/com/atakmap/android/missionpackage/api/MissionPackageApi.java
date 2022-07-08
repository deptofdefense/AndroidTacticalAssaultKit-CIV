
package com.atakmap.android.missionpackage.api;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.attachment.DeleteAfterSendCallback;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.contact.ContactUtil;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Mission Package Intent API interface to 'Save' or 'Save & Send' a Mission Package Provides
 * mechanisms which allow other tools in ATAK to get callback after the Mission Package has been
 * created (on sending device), sent (on sending device), and extracted (on receiving device).
 * 
 * 
 */
public class MissionPackageApi {

    private static final String TAG = "MissionPackageApi";

    /**
     * Action for intent to save a new Mission Package
     */
    public static final String INTENT_MISSIONPACKAGE_SAVE = MissionPackageReceiver.MISSIONPACKAGE_SAVE;

    /**
     * Action for intent to update an existing Mission Package
     */
    public static final String INTENT_MISSIONPACKAGE_UPDATE = MissionPackageReceiver.MISSIONPACKAGE_UPDATE;

    /**
     * Action for intent to delete an existing Mission Package
     */
    public static final String INTENT_MISSIONPACKAGE_DELETE = MissionPackageReceiver.MISSIONPACKAGE_DELETE;

    /**
     * MissionPackage Extra [Required] Type: MissionPackageManifest An instance of
     * MissionPackageManifest which contains files and/or ATAK Map Items to include in the Mission
     * Package. Note, if you want Mission Package Tool to import the Mission Package, then the
     * "path" must be set to the "ATAK/datapackage" directory on a supported mounted file system
     */
    public static final String INTENT_EXTRA_MISSIONPACKAGEMANIFEST = "MissionPackageManifest";
    public static final String INTENT_EXTRA_MISSIONPACKAGEMANIFEST_UID = "MissionPackageManifestUID";
    public static final String INTENT_EXTRA_PATH = "PackagePath";

    /**
     * SaveAndSend Extra [Optional] Type: boolean Omit or false to save only. True to save and then
     * send either to an explicit list of receivers, or to receivers selected via Contact List
     */
    public static final String INTENT_EXTRA_SAVEANDSEND = "SaveAndSend";

    /**
     * SaveOnly Extra [Optional}: boolean Omit or false to save prior to sending. True to skip
     * saving/compression and just send the package
     */
    public static final String INTENT_EXTRA_SENDONLY = "SendOnly";

    /**
     * SenderCallbackClassName Extra [Optional] Type: String Name of class to invoke when save is
     * complete and when Contact List has been launched. Must implement
     * <code>SaveAndSendCallback</code> as reflection is used to instantiate the class via a
     * parameterless constructor. Note the callback will be invoked once compression (save) is
     * complete, and again once the Contact List has been launched. The callback may check the type
     * of the provided task to determine which step has completed, and may obtain the Context or
     * ATAK MapView from the task. If a callback needs access to the "local" tool which is being
     * integrated the code may access a singleton provided by the tool implementation, or provide an
     * alternative method to get a reference to required resources. Note that use of Proguard and
     * inline classes may prohibit the execution of the required parameter-less constructor via
     * Reflection in the callback class.
     */
    public static final String INTENT_EXTRA_SENDERCALLBACKCLASSNAME = "SenderCallbackClassName";

    /**
     * SenderCallbackClassName Extra [Optional] Type: String Name of class to invoke when save is
     * complete and when Contact List has been launched. Must implement
     * <code>SaveAndSendCallback</code> as reflection is used to instantiate the class via a
     * parameterless constructor.
     */
    public static final String INTENT_EXTRA_SENDERCALLBACKPACKAGENAME = "SenderCallbackPackageName";

    /**
     * Receivers Extra [Optional] Type: NetworkContact Parcelable Array. Omit to use Contact List to
     * select receivers. Provide array of <code>NetworkContacts</code> to send to specific users.
     * Only processed if SaveAndSend is true
     */
    public static final String INTENT_EXTRA_RECEIVERS = "Receivers";

    /**
     * Sender Callsign Extra Type: String  A custom intent may sent on the receiving device once
     * the Mission Package has been extracted. See ReceiveParams extra. If so, this extra as well as
     * MissionPackage and the ID of the user notification are included in the custom intent that is
     * generated on the receiving device. This intent is not required or supported for intent
     * MISSIONPACKAGE_SAVE. See MissionPackageConfiguration.PARAMETER_OnReceiveAction
     */
    public static final String INTENT_EXTRA_SENDERCALLSIGN = "SenderCallsign";

    /**
     * NotificationId Extra Type: int See FileTransfer extra
     */
    public static final String INTENT_EXTRA_NOTIFICATION_ID = "NotificationId";

    /**
     * Send an intent to Save the specified Mission Package with the provided parameters The
     * specified contents will be compressed into Mission Package .zip
     * 
     * @param context [required]
     * @param manifest [required]
     * @param callbackClazz [optional]
     * @return true if intent was successfully populated and broadcast
     */
    public static boolean Save(Context context,
            MissionPackageManifest manifest,
            Class<? extends SaveAndSendCallback> callbackClazz) {
        if (context == null) {
            Log.w(TAG, "Cannot send with invalid context");
            return false;
        }

        Intent mpIntent = new Intent();
        mpIntent.setAction(MissionPackageApi.INTENT_MISSIONPACKAGE_SAVE);

        if (manifest == null || manifest.isEmpty()) {
            Log.w(TAG, "Cannot save with invalid contents");
            return false;
        }
        mpIntent.putExtra(
                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                manifest);

        if (callbackClazz != null) {
            mpIntent.putExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME,
                    callbackClazz.getName());
        }

        Log.d(TAG, "Saving " + manifest
                + ", with saving callback: "
                + (callbackClazz == null ? "none" : callbackClazz.getName()));
        AtakBroadcast.getInstance().sendBroadcast(mpIntent);
        return true;
    }

    /**
     * Send an intent to Save and Send the specified Mission Package with the provided parameters
     * The specified contents will be compressed into Mission Package .zip and then sent to the
     * specified contacts. If none are provided, then the ATAK Contact List will be launched for the
     * user to select the receivers
     * 
     * @param context [required]
     * @param manifest [required]
     * @param callbackClazz [optional]
     * @param netContacts [optional]
     * @return true if intent was successfully populated and broadcast
     */
    public static boolean Send(Context context,
            MissionPackageManifest manifest,
            Class<? extends SaveAndSendCallback> callbackClazz,
            Contact[] netContacts) {
        return Send(context, manifest, callbackClazz, netContacts, false);
    }

    /**
     * Send an intent to Save and Send the specified Mission Package with the provided parameters
     * The specified contents will be compressed into Mission Package .zip and then sent to the
     * specified contacts. If none are provided, then the ATAK Contact List will be launched for the
     * user to select the receivers
     *
     * @param context [required]
     * @param manifest [required]
     * @param callbackClazz [optional]
     * @param netContacts [optional]
     * @param sendOnly  if true, skip saving/compression, and just send an existing package
     * @return true if intent was successfully populated and broadcast
     */
    public static boolean Send(Context context,
            MissionPackageManifest manifest,
            Class<? extends SaveAndSendCallback> callbackClazz,
            Contact[] netContacts, boolean sendOnly) {
        return SendUIDs(context, manifest, callbackClazz,
                ContactUtil.getUIDs(netContacts), sendOnly);
    }

    /**
     * Quick send of file as a Mission Package, display contact list to select recipients
     * Package is auto deleted on sender and recipient
     *
     * @param file file to send [required]
     * @return true if the file was sent
     */
    public static boolean Send(File file) {
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, "Send invalid file");
            return false;
        }
        return MissionPackageApi.Send(file, file.getName());
    }

    /**
     * Quick send of file as a Mission Package, display contact list to select recipients
     * Package is auto deleted on sender and recipient
     *
     * @param file file to send [required]
     * @param title title of mission package[required]
     * @return true if the file was sent
     */
    public static boolean Send(File file, String title) {
        if (!FileSystemUtils.isFile(file) || FileSystemUtils.isEmpty(title)) {
            Log.w(TAG, "Send invalid file or title");
            return false;
        }

        MissionPackageManifest manifest = MissionPackageApi.CreateTempManifest(
                title, true, true, null);
        manifest.addFile(file, null);
        return MissionPackageApi.prepareSend(manifest,
                DeleteAfterSendCallback.class, false);
    }

    /**
     * Send an intent to Save and Send the specified Mission Package with the provided parameters
     * The specified contents will be compressed into Mission Package .zip and then sent to the
     * specified contacts. If none are provided, then the ATAK Contact List will be launched for the
     * user to select the receivers
     *
     * @param context [required]
     * @param manifest [required]
     * @param callbackClassName [optional]
     * @param toUIDs [optional]
     * @param sendOnly  if true, skip saving/compression, and just send an existing package
     * @return true if intent was successfully populated and broadcast
     */
    public static boolean SendUIDs(Context context,
            MissionPackageManifest manifest, String callbackClassName,
            String[] toUIDs, boolean sendOnly) {
        if (context == null) {
            Log.w(TAG, "Cannot send with invalid context");
            return false;
        }

        Intent mpIntent = new Intent();
        mpIntent.setAction(MissionPackageApi.INTENT_MISSIONPACKAGE_SAVE);

        if (manifest == null || manifest.isEmpty()) {
            Log.w(TAG, "Cannot send with invalid contents");
            return false;
        }
        mpIntent.putExtra(
                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                manifest);

        mpIntent.putExtra(MissionPackageApi.INTENT_EXTRA_SAVEANDSEND, true);
        if (toUIDs != null && toUIDs.length > 0) {
            mpIntent.putExtra(MissionPackageApi.INTENT_EXTRA_RECEIVERS,
                    toUIDs);
        }

        if (!FileSystemUtils.isEmpty(callbackClassName))
            mpIntent.putExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME,
                    callbackClassName);

        if (sendOnly) {
            mpIntent.putExtra(MissionPackageApi.INTENT_EXTRA_SENDONLY, true);
        }

        Log.d(TAG, "Sending " + manifest
                + ", with sender callback: "
                + (callbackClassName == null ? "none" : callbackClassName));
        AtakBroadcast.getInstance().sendBroadcast(mpIntent);
        return true;
    }

    public static boolean SendUIDs(Context context,
            MissionPackageManifest manifest,
            Class<? extends SaveAndSendCallback> callbackClazz,
            String[] toUIDs, boolean sendOnly) {
        return SendUIDs(context, manifest,
                callbackClazz != null ? callbackClazz.getName() : null, toUIDs,
                sendOnly);
    }

    /**
     * Brings up the contacts list BEFORE posting to the server
     * @param manifest Mission package manifest
     * @param callbackClazz Callback class
     * @param sendOnly True to only send, false to compress before sending
     */
    public static boolean prepareSend(MissionPackageManifest manifest,
            Class<? extends SaveAndSendCallback> callbackClazz,
            boolean sendOnly) {
        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled())
            return false;
        Intent send = new Intent(ContactPresenceDropdown.SEND_LIST);
        send.putExtra("sendCallback",
                MissionPackageReceiver.MISSIONPACKAGE_SEND);
        send.putExtra(MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                manifest);
        if (callbackClazz != null)
            send.putExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME,
                    callbackClazz.getName());
        send.putExtra(MissionPackageApi.INTENT_EXTRA_SENDONLY, sendOnly);
        send.putExtra("disableBroadcast", true);
        AtakBroadcast.getInstance().sendBroadcast(send);
        return true;
    }

    /**
     * Send an intent to Update the specified Mission Package by adding the
     * specified map items and files. At least one item or file is required.
     * Files attached to specified map items will be included in the package.
     * 
     * @param context    [required]
     * @param missionPackageUID    [required] UID of package to update
     * @param mapItemUIDArray    [optional]
     * @param files    [optional]
     * @param bSave true to save, false to just add to UI list
     * @param callbackClazz    [optional]
     * @return true if the package was updated.
     */
    public static boolean Update(Context context, String missionPackageUID,
            String[] mapItemUIDArray, String[] files,
            boolean bSave, Class<? extends SaveAndSendCallback> callbackClazz) {
        if (context == null) {
            Log.w(TAG, "Cannot update with invalid context");
            return false;
        }

        Intent mpIntent = new Intent();
        mpIntent.setAction(MissionPackageApi.INTENT_MISSIONPACKAGE_UPDATE);

        if (FileSystemUtils.isEmpty(missionPackageUID)) {
            Log.w(TAG, "Cannot update without Mission Package UID");
            return false;
        }
        mpIntent.putExtra(
                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST_UID,
                missionPackageUID);

        //be sure we have map items and/or files
        if ((mapItemUIDArray == null || mapItemUIDArray.length < 1) &&
                (files == null || files.length < 1)) {
            Log.w(TAG, "Cannot update with no new data");
            return false;
        }

        if (mapItemUIDArray != null && mapItemUIDArray.length > 0) {
            mpIntent.putExtra("mapitems", mapItemUIDArray);
        }
        //include attachments for mapitems
        mpIntent.putExtra("includeAttachments", true);

        if (files != null && files.length > 0) {
            mpIntent.putExtra("files", files);
        }

        if (callbackClazz != null) {
            mpIntent.putExtra(
                    MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME,
                    callbackClazz.getName());
        }

        mpIntent.putExtra("save", bSave);

        Log.d(TAG, "Updating " + missionPackageUID + ", with sender callback: "
                + (callbackClazz == null ? "none" : callbackClazz.getName()));
        AtakBroadcast.getInstance().sendBroadcast(mpIntent);
        return true;
    }

    /**
     * Send an intent to Delete the specified Mission Package
     *
     * @param context    [required]
     * @param manifest    [required] package to delete
     * @return a boolean if delete was successful.
     */
    public static boolean Delete(Context context,
            MissionPackageManifest manifest) {
        if (context == null) {
            Log.w(TAG, "Cannot delete with invalid context");
            return false;
        }

        Intent mpIntent = new Intent();
        mpIntent.setAction(MissionPackageApi.INTENT_MISSIONPACKAGE_DELETE);

        if (manifest == null || !manifest.isValid()) {
            Log.w(TAG, "Cannot delete invalid manifest");
            return false;
        }
        mpIntent.putExtra(
                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                manifest);

        Log.d(TAG, "Deleting " + manifest);
        AtakBroadcast.getInstance().sendBroadcast(mpIntent);
        return true;
    }

    /**
     * Create a manifest such that zip will be created in a temp directory
     * This directory is periodically cleaned out (contents securely deleted)
     * Use default import instructions
     *
     * @param name the name of the manifest
     * @return the MissionPackageManifest object that represents the manifest file.
     */
    public static MissionPackageManifest CreateTempManifest(String name) {
        return CreateTempManifest(name, true, false, null);
    }

    /**
     * Create a manifest such that zip will be created in a temp directory
     * This directory is periodically cleaned out (contents securely deleted)
     * Set import instructions
     *
     * @param name  name of package
     * @param bImport   true to run extracted files against Import Manager
     * @param bDelete   true to delete .zip after extraction
     * @param onReceiveAction Intent action to broadcast on receiver after extraction/deletion
     * @return
     */
    public static MissionPackageManifest CreateTempManifest(String name,
            boolean bImport,
            boolean bDelete, String onReceiveAction) {

        MissionPackageManifest manifest = new MissionPackageManifest(name,
                MissionPackageFileIO.getMissionPackageIncomingDownloadPath(
                        FileSystemUtils.getRoot().getAbsolutePath()));
        manifest.getConfiguration().setImportInstructions(bImport, bDelete,
                onReceiveAction);
        return manifest;
    }
}
