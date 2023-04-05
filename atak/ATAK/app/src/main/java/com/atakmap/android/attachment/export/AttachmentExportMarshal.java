
package com.atakmap.android.attachment.export;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.attachment.AttachmentMapOverlay;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.importexport.ExportFileMarshal;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Exports marker attachments to Zip
 *
 *
 */
public class AttachmentExportMarshal extends ExportFileMarshal {
    private static final String TAG = "AttachmentExportMarshal";

    private final List<AttachmentMapOverlay.MapItemAttachment> exports;

    /**
     * Simple file wrapper compatible ExportMarshal
     */
    public static class FileExportable implements Exportable {

        final AttachmentExportWrapper wrapper;

        public FileExportable(File f) {
            wrapper = new AttachmentExportWrapper(
                    new AttachmentMapOverlay.MapItemAttachment(null, f));
        }

        @Override
        public boolean isSupported(Class<?> target) {
            return AttachmentExportWrapper.class.equals(target);
        }

        @Override
        public Object toObjectOf(Class<?> target, ExportFilters filters)
                throws FormatNotSupportedException {
            if (AttachmentExportWrapper.class.equals(target))
                return wrapper;
            else
                return null;
        }
    }

    /**
     * Builds a Marshall capable of exporting Attachments as a zip file.
     * @param context the context used to look up resources and icons.
     */
    public AttachmentExportMarshal(Context context) {
        super(context, ResourceFile.MIMEType.ZIP.EXT,
                ResourceFile.MIMEType.ZIP.MIME,
                R.drawable.camera);

        // remove the requirement that exported map items support the target
        // class AttachmentExportWrapper
        getFilters().clear();

        exports = new ArrayList<>();
    }

    @Override
    public File getFile() {
        return new File(
                FileSystemUtils.getItem(FileSystemUtils.EXPORT_DIRECTORY),
                filename);
    }

    @Override
    protected boolean marshal(Exportable export) throws IOException,
            FormatNotSupportedException {
        if (export == null
                || !export.isSupported(AttachmentExportWrapper.class)) {
            Log.d(TAG, "Skipping unsupported export "
                    + (export == null ? "" : export.getClass().getName()));
            return false;
        }

        AttachmentExportWrapper folder = (AttachmentExportWrapper) export
                .toObjectOf(
                        AttachmentExportWrapper.class, getFilters());
        if (folder == null || folder.isEmpty()) {
            Log.d(TAG, "Skipping empty folder");
            return false;
        }
        Log.d(TAG, "Adding folder");
        this.exports.addAll(folder.getExports());

        Log.d(TAG, "Added attachment count: " + folder.getExports().size());
        return true;
    }

    @Override
    public Class<?> getTargetClass() {
        return AttachmentExportWrapper.class;
    }

    @Override
    protected void finalizeMarshal() throws IOException {
        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }

        // delete existing file, and then serialize out to file
        File file = getFile();
        if (IOProviderFactory.exists(file)) {
            FileSystemUtils.deleteFile(file);
        }

        List<File> files = getFiles();
        if (FileSystemUtils.isEmpty(files)) {
            throw new IOException("No attachments to export");
        }

        //TODO sits at 94% during serialization to KMZ/zip. Could serialize during marshall above
        File zip = FileSystemUtils.zipDirectory(files, file);
        if (zip == null) {
            throw new IOException("Failed to serialize Zip");
        }

        synchronized (this) {
            if (this.isCancelled) {
                Log.d(TAG, "Cancelled, in finalizeMarshal");
                return;
            }
        }
        if (hasProgress()) {
            this.progress.publish(94);
        }

        Log.d(TAG, "Exported: " + file.getAbsolutePath());
    }

    @Override
    public void postMarshal() {
        synchronized (this) {
            if (this.isCancelled) {
                Log.w(TAG, "Cancelled, but in postMarshal");
                return;
            }
        }
        final File file = getFile();
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
        Toast.makeText(context,
                context.getString(R.string.importmgr_exported_file,
                        file.getAbsolutePath()),
                Toast.LENGTH_LONG).show();
    }

    /**
     * Allow only attachments
     */
    @Override
    public boolean accept(HierarchyListItem item) {
        //Log.d(TAG, "filterListItem " + item.getClass().getName() + ", " + item.getTitle());

        return !(item instanceof AttachmentMapOverlay.AttachmentOverlayListModel
                || item instanceof AttachmentMapOverlay.AttachmentListItem);

    }

    @Override
    public boolean filterGroup(MapGroup group) {
        //Log.d(TAG, "filterGroup: " + group.getFriendlyName());
        return true;
    }

    @Override
    public boolean filterItem(MapItem item) {
        //Log.d(TAG, "filterItem: " + item.getUID());

        //filter out those with no attachments
        return AttachmentManager.getNumberOfAttachments(item.getUID()) < 1;
    }

    /**
     * Allow only attachments
     */
    @Override
    public boolean filterListItemImpl(HierarchyListItem item) {
        //Log.d(TAG, "filterListItemImpl " + item.getClass().getName() + ", " + item.getTitle());

        return !(item instanceof AttachmentMapOverlay.AttachmentOverlayListModel
                || item instanceof AttachmentMapOverlay.AttachmentListItem);

    }

    @Override
    public boolean filterOverlay(MapOverlay overlay) {
        //Log.d(TAG, "filterOverlay: " + overlay.getName());
        return true;
    }

    private List<File> getFiles() {
        if (FileSystemUtils.isEmpty(exports)) {
            return null;
        }

        List<File> files = new ArrayList<>();
        for (AttachmentMapOverlay.MapItemAttachment export : exports) {
            if (export == null
                    || !FileSystemUtils.isFile(export.getAttachment())) {
                Log.w(TAG, "Skipping invalid export");
                continue;
            }

            files.add(export.getAttachment());
        }

        return files;
    }
}
