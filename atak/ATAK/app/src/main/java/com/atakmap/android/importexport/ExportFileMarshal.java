
package com.atakmap.android.importexport;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import com.atakmap.android.attachment.AttachmentMapOverlay;
import com.atakmap.android.attachment.DeleteAfterSendCallback;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.event.MissionPackageShapefileHandler;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.spatial.file.ShapefileSpatialDb;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Marshals data to file
 * 
 * 
 */
public abstract class ExportFileMarshal extends ExportMarshal {

    private static final String TAG = "ExportFileMarshal";

    protected String filename;
    protected final String contentType;
    protected final String mimeType;
    protected final int iconId;
    protected final Context context;
    protected boolean isCancelled;

    /**
     * Track which UIDs have already been exported e.g. user may select
     * a group and children w/in that group
     */
    protected final List<String> exportedUIDs;

    public ExportFileMarshal(Context context, String contentType,
            String mimeType, int icon) {
        this.context = context;
        this.contentType = contentType;
        this.mimeType = mimeType;
        this.iconId = icon;
        this.exportedUIDs = new ArrayList<>();
        this.isCancelled = false;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public String getMIMEType() {
        return this.mimeType;
    }

    @Override
    public int getIconId() {
        return iconId;
    }

    /**
     * Get marshal target
     * @throws IOException if there is a problem constructing the file.
     * @return A valid file to be exported.
     */
    public abstract File getFile() throws IOException;

    /**
     * Export the specified data
     * 
     * @param export
     * @return
     * @throws FormatNotSupportedException
     */
    protected abstract boolean marshal(Exportable export) throws IOException,
            FormatNotSupportedException;

    @Override
    protected boolean marshal(final Collection<Exportable> exports)
            throws IOException,
            FormatNotSupportedException {

        if (exports == null || exports.size() < 1) {
            Log.d(TAG, "No exports provided to marshal");
            return false;
        }

        //setup progress reporting
        double percentPerExport = 90D / (double) exports.size();
        int count = 1;

        //loop exports
        boolean atlestOneExport = false;
        for (final Exportable export : exports) {
            synchronized (this) {
                if (this.isCancelled) {
                    Log.d(TAG, "Cancelled, skipping exports...");
                    return false;
                }
            }

            if (marshal(export))
                atlestOneExport = true;

            if (hasProgress()) {
                this.progress.publish((int) Math.round(percentPerExport
                        * count++));
            }
        }

        return atlestOneExport;
    }

    @Override
    public void execute(final List<Exportable> exports) {

        //First get filename
        //TODO limit to filename supporting characters
        final EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        String ext = getExtension();
        if (!FileSystemUtils.isEmpty(ext))
            ext = "." + ext.toLowerCase(LocaleUtil.getCurrent());
        final String fExt = ext;

        String defaultName = context.getString(R.string.app_name) + timestamp();
        if (exports.size() == 1) {
            Exportable e = exports.get(0);
            if (e instanceof MapItem)
                defaultName = ((MapItem) e).getTitle();
        }
        editText.setText(defaultName + fExt);

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setIcon(getIconId());
        b.setView(editText);
        b.setTitle(R.string.enter_filename);
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.export,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        filename = editText.getText().toString();
                        if (FileSystemUtils.isEmpty(filename)) {
                            Toast.makeText(
                                    context,
                                    R.string.importmgr_filename_required_export_cancelled,
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG,
                                    "Failed to export selected items, no filename specified");
                            return;
                        }

                        if (!filename.equals(FileSystemUtils
                                .sanitizeWithSpacesAndSlashes(filename))) {
                            Toast.makeText(
                                    context,
                                    "invalid filename",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (filename.endsWith("."))
                            filename = filename.substring(0,
                                    filename.lastIndexOf("."));
                        if (!FileSystemUtils.isEmpty(fExt)
                                && !filename.endsWith(fExt))
                            filename += fExt;
                        filename = FileSystemUtils.sanitizeFilename(filename);
                        checkFile(exports);
                    }
                });
        b.show();
    }

    protected String getExtension() {
        return getContentType();
    }

    private void checkFile(final List<Exportable> exports) {
        File file = null;
        try {
            file = getFile();
        } catch (IOException e) {
            Log.e(TAG, "error occurred", e);
        }

        if (file == null) {
            Toast.makeText(context,
                    R.string.importmgr_failed_to_export_selected_items,
                    Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Failed to export selected items, no File available");
            return;
        }

        if (!FileSystemUtils.isFile(file)) {
            beginMarshal(context, exports);
            return;
        }

        AlertDialog.Builder innerb = new AlertDialog.Builder(context);
        innerb.setTitle(R.string.confirm_overwrite);
        innerb.setMessage(context.getString(R.string.importmgr_overwrite_file,
                file.getName()));
        innerb.setNegativeButton(R.string.cancel, null);
        innerb.setPositiveButton(R.string.export,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        beginMarshal(context, exports);
                    }
                });
        innerb.show();
    }

    static final ThreadLocal<SimpleDateFormat> d_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("d", LocaleUtil.getCurrent());
        }
    };
    static final ThreadLocal<SimpleDateFormat> HHmm_sdf = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HHmmss", LocaleUtil.getCurrent());
        }
    };

    public static String timestamp() {
        Date d = new Date();
        return "." + d_sdf.get().format(d)
                + "." + HHmm_sdf.get().format(d);
    }

    @Override
    public String toString() {
        return getTargetClass().getSimpleName() + ", " + filename;
    }

    @Override
    public void postMarshal() {

        synchronized (this) {
            if (this.isCancelled) {
                Log.w(TAG, "Cancelled, but in postMarshal");
                return;
            }
        }

        final File file;
        try {
            file = getFile();
        } catch (IOException e) {
            Toast.makeText(context,
                    R.string.importmgr_failed_to_export_selected_items,
                    Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Failed to export selected items, no File available");
            return;
        }

        if (!IOProviderFactory.exists(file)) {
            Log.d(TAG, "Export failed: " + file.getAbsolutePath());
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    context.getString(R.string.importmgr_export_failed,
                            getContentType()),
                    context.getString(R.string.importmgr_failed_to_export,
                            getContentType()),
                    context.getString(R.string.importmgr_failed_to_export,
                            getContentType()));
        }

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(context.getString(R.string.importmgr_exported,
                getContentType()));
        b.setIcon(getIconId());
        b.setMessage(context.getString(R.string.importmgr_exported_file,
                FileSystemUtils.prettyPrint(file)));
        b.setPositiveButton(R.string.send,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MapView mv = MapView.getMapView();
                        if (mv == null)
                            return;
                        SendDialog.Builder b = new SendDialog.Builder(mv);
                        b.setName(file.getName());
                        b.setIcon(getIconId());
                        b.addFile(file, getContentType());
                        b.show();
                    }
                });
        b.setNegativeButton(R.string.done, null);
        b.show();
    }

    public static void send3rdParty(Context context, String contentType,
            String mimeType, File file) {
        Log.d(TAG, "3rd party ACTION_SEND for: " + file.getAbsolutePath());
        Uri fileUri = com.atakmap.android.util.FileProviderHelper
                .fromFile(context, file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        com.atakmap.android.util.FileProviderHelper.setReadAccess(shareIntent);

        if (!FileSystemUtils.isEmpty(mimeType))
            shareIntent.setType(mimeType);
        try {
            context.startActivity(Intent.createChooser(shareIntent,
                    context.getString(R.string.importmgr_share_via,
                            contentType)));
        } catch (Exception e) {
            Log.w(TAG, "Failed to ACTION_SEND", e);
            Toast.makeText(context,
                    R.string.importmgr_no_compatible_apps_found,
                    Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static void sendFTP(Context context,
            String contentType, String mimeType, File file) {
        sendFTP(-1, contentType, mimeType, file, null, false, null,
                null);
    }

    public static void sendFTP(int notificationId,
            String contentType, String mimeType, File file,
            String passwd, boolean skipDialog,
            String callbackAction, Parcelable callbackExtra) {
        //KML, GPX are ASCII
        //KMZ, SHP are binary
        boolean binaryTransferMode = false;
        if (contentType != null
                && (contentType
                        .equalsIgnoreCase(ShapefileSpatialDb.SHP_CONTENT_TYPE)
                        ||
                        contentType
                                .equalsIgnoreCase(KmlFileSpatialDb.KMZ_TYPE))) {
            binaryTransferMode = true;
        }
        if (mimeType != null &&
                mimeType.equalsIgnoreCase(ResourceFile.MIMEType.ZIP.MIME)) {
            binaryTransferMode = true;
        }

        Intent intent = new Intent(
                ImportExportMapComponent.FTP_UPLOAD_FILE_ACTION);
        intent.putExtra("filepath", file.getAbsolutePath());
        intent.putExtra("binary", binaryTransferMode);

        if (notificationId > 0)
            intent.putExtra("notificationId", notificationId);
        if (!FileSystemUtils.isEmpty(passwd))
            intent.putExtra("passwd", passwd);
        if (skipDialog)
            intent.putExtra("skipdialog", true);
        if (!FileSystemUtils.isEmpty(callbackAction))
            intent.putExtra("callbackAction", callbackAction);
        if (callbackExtra != null)
            intent.putExtra("callbackExtra", callbackExtra);

        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    /**
     * Send specified file via ATAK contact list
     * Note .shp files and .zip (e.g. iconsets) are sent via Mission Package
     * Tool, other files are sent directly via SharedFileListener
     * 
     * @param context
     * @param contentType
     * @param file
     * @param bImport
     * @param onReceiveAction [optional] broadcast on receiver device
     *
     * @return
     */
    public static boolean sendFile(Context context, String contentType,
            File file, boolean bImport, String onReceiveAction) {

        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled())
            return false;

        // launch MPT to send the file and the CoT
        Log.d(TAG,
                "Sending file via Mission Package Tool: "
                        + file.getAbsolutePath()
                        + ", type=" + contentType);

        // Create the Mission Package containing the file
        // receiving device will delete after file is imported
        MissionPackageManifest manifest = MissionPackageApi
                .CreateTempManifest(file.getName(), bImport, true,
                        onReceiveAction);

        //special case for SHP file...
        if (ShapefileSpatialDb.SHP_CONTENT_TYPE.equalsIgnoreCase(contentType)
                &&
                file.getName().toLowerCase(LocaleUtil.getCurrent())
                        .endsWith(ShapefileSpatialDb.SHP_TYPE)) {
            if (!MissionPackageShapefileHandler.add(context, manifest, file)
                    || manifest.isEmpty()) {
                Log.w(TAG, "Unable to add shapefile to Mission Package");
                return false;
            }
        } else {
            if (!manifest.addFile(file, null) || manifest.isEmpty()) {
                Log.w(TAG, "Unable to add file to Mission Package");
                return false;
            }
        }

        // send null contact list so Contact List is displayed, delete local
        // mission package after sent
        MissionPackageApi.prepareSend(manifest, DeleteAfterSendCallback.class,
                false);
        return true;
    }

    @Override
    protected void cancelMarshal() {
        synchronized (this) {
            this.isCancelled = true;
        }

        final File file;
        try {
            file = getFile();
            if (IOProviderFactory.exists(file)) {
                FileSystemUtils.deleteFile(file);

            }
        } catch (IOException e) {
            Log.d(TAG, "error obtaining file", e);
        }

    }

    /**
     * Allow SpatialDB files
     */
    @Override
    public boolean filterListItemImpl(HierarchyListItem item) {
        //Log.d(TAG, "filterListItemImpl " + item.getClass().getName() + ", " + item.getTitle());

        //All file exports support Attachments e.g. QuickPic markers
        return !(item instanceof AttachmentMapOverlay.AttachmentOverlayListModel
                || item instanceof AttachmentMapOverlay.AttachmentListItem)
                && super.filterListItemImpl(item);

    }
}
