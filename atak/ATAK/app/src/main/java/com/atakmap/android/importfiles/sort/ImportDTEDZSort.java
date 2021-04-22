
package com.atakmap.android.importfiles.sort;

import android.app.Notification;
import android.content.Context;
import android.util.Pair;

import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipInputStream;

/**
 * Imports archived DTED folders
 * The root directory in the zip file needs to be DTED 
 * 
 */
public class ImportDTEDZSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportDTEDZSort";

    private final Context _context;

    public static class ImportDTEDZv1Sort extends ImportDTEDZSort {

        /**
         * Construct an importer that works on zipped DTED files.    Users of this class
         * should either use .zip or .dpk for the mission package extension.
         *
         * @param context     the extension to be used
         * @param validateExt if the extension needs to be validated
         * @param copyFile    if the file needs to be copied
         * @param bStrict     strict if the mission package is required to have a manifest
         */
        public ImportDTEDZv1Sort(Context context, boolean validateExt,
                boolean copyFile, boolean bStrict) {

            super(context, ".zip", validateExt, copyFile, bStrict);

        }
    }

    public static class ImportDTEDZv2Sort extends ImportDTEDZSort {

        /**
         * Construct an importer that works on zipped DTED files.    Users of this class
         * should either use .zip or .dpk for the mission package extension.
         *
         * @param context     the extension to be used
         * @param validateExt if the extension needs to be validated
         * @param copyFile    if the file needs to be copied
         * @param bStrict     strict if the mission package is required to have a manifest
         */
        public ImportDTEDZv2Sort(Context context, boolean validateExt,
                boolean copyFile, boolean bStrict) {

            super(context, ".dpk", validateExt, copyFile, bStrict);

        }
    }

    protected ImportDTEDZSort(Context context, String ext, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(ext, FileSystemUtils.DTED_DIRECTORY, validateExt,
                copyFile, importInPlace,
                context.getString(R.string.zipped_dted),
                context.getDrawable(R.drawable.ic_overlay_dted));
        _context = context;
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .zip, now lets see if it contains a DTED directory 
        // but no manifest.
        return hasDTED(file);
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>("Zipped DTED", "application/zip");
    }

    private static boolean containsDT(String s) {
        if (s == null)
            return false;

        s = s.toLowerCase(LocaleUtil.getCurrent());

        return s.endsWith(".dt3") || s.endsWith(".dt2") ||
                s.endsWith(".dt1") || s.endsWith(".dt0");

    }

    /**
     * Search for a zip entry DTED, but no MANIFEST directory
     * 
     * @param file the zip file
     * @return true if the zip file has DTED entries
     */
    private static boolean hasDTED(final File file) {
        if (file == null) {
            Log.d(TAG, "ZIP file points to null.");
            return false;
        }

        if (!IOProviderFactory.exists(file)) {
            Log.d(TAG, "ZIP does not exist: " + file.getAbsolutePath());
            return false;
        }

        ZipFile zip = null;
        try {
            zip = new ZipFile(file);

            boolean hasDTED = false;
            Enumeration<? extends com.atakmap.util.zip.ZipEntry> entries = zip
                    .entries();
            while (entries.hasMoreElements() && !hasDTED) {
                com.atakmap.util.zip.ZipEntry ze = entries.nextElement();
                String name = ze.getName();
                if (containsDT(name)) {
                    File f = new File(name);
                    String s = f.getName();
                    if (s.startsWith("n") || s.startsWith("s") ||
                            s.startsWith("w") || s.startsWith("e"))
                        hasDTED = true;

                }
                //else {
                //Log.d(TAG, "ignoring: " + name);
                //}

            }

            if (hasDTED)
                Log.d(TAG, "Matched archived DTED: " + file.getAbsolutePath());

            return hasDTED;

        } catch (Exception e) {
            Log.d(TAG,
                    "Failed to find DTED content in: " + file.getAbsolutePath(),
                    e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception e) {
                    Log.e(TAG,
                            "Failed closing archived DTED: "
                                    + file.getAbsolutePath(),
                            e);
                }
            }
        }

        return false;
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        installDTED(src);
    }

    private boolean installDTED(File dtedFile) {
        byte[] buffer = new byte[4096];
        String entry;
        boolean error = false;
        ZipInputStream zis = null;

        int notificationId = NotificationUtil.getInstance().reserveNotifyId();

        NotificationUtil.getInstance().postNotification(
                notificationId,
                NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                NotificationUtil.BLUE,
                _context.getString(R.string.importing_dted), null, null, true);

        Notification.Builder builder = NotificationUtil.getInstance()
                .getNotificationBuilder(notificationId);

        try (InputStream is = IOProviderFactory.getInputStream(dtedFile)) {
            // create output directory is not exists
            File folder = FileSystemUtils
                    .getItem(FileSystemUtils.DTED_DIRECTORY);
            if (!IOProviderFactory.exists(folder)) {
                if (!IOProviderFactory.mkdir(folder)) {
                    Log.d(TAG, "could not wrap: " + folder);
                }
            }

            // get the zip file content
            zis = new ZipInputStream(is);

            // get the zipped file list entry
            java.util.zip.ZipEntry ze = zis.getNextEntry();

            int count = 0;
            while (ze != null) {
                entry = ze.getName();
                File f = new File(entry);

                String filename = f.getName();
                File newFile = null;

                if (f.getParentFile() != null) {
                    String parent = f.getParentFile().getName();
                    if (parent.startsWith("e") || parent.startsWith("w") ||
                            parent.startsWith("E") || parent.startsWith("W"))
                        filename = parent + "/" + filename;
                }

                if (containsDT(filename)) {
                    if (filename.startsWith("e") || filename.startsWith("w") ||
                            filename.startsWith("E")
                            || filename.startsWith("W")) {
                        newFile = new File(folder,
                                filename.toLowerCase(LocaleUtil.getCurrent()));
                        //Log.d(TAG, "dted file encounted: " + filename + " creating: " + newFile);
                    } else if (filename.startsWith("n")
                            || filename.startsWith("s") ||
                            filename.startsWith("N")
                            || filename.startsWith("S")) {
                        String[] s = filename.split("_");
                        String ext = filename.substring(filename.indexOf("."));
                        newFile = new File(folder,
                                (s[1] + "/" + s[0] + ext)
                                        .toLowerCase(LocaleUtil.getCurrent()));
                        //Log.d(TAG, "usgs file encounted: " + filename + " creating: " + newFile);
                    }
                }

                if (newFile != null) {

                    if (count % 10 == 0) {
                        builder.setContentText(String.format(
                                _context.getString(
                                        R.string.importmgr_processing_file),
                                newFile.getParentFile().getName(),
                                newFile.getName()));
                        Notification summaryNotification = builder.build();
                        NotificationUtil.getInstance().postNotification(
                                notificationId,
                                summaryNotification, true);
                    }
                    count++;

                    // create all non exists folders
                    // else you will hit FileNotFoundException 
                    // for compressed folder
                    if (!(IOProviderFactory
                            .mkdirs(new File(newFile.getParent())))) {
                        Log.d(TAG, "could not make: " + newFile.getParent());
                    }

                    try (FileOutputStream fos = IOProviderFactory
                            .getOutputStream(newFile)) {

                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }

                    } catch (IOException ex) {
                        // skip this file
                        Log.d(TAG, "error occurred during unzipping", ex);
                    }
                }
                //else {
                //Log.d(TAG, f.getPath());
                //}
                ze = zis.getNextEntry();
            }

            error = false;

        } catch (IOException ex) {
            Log.e(TAG, "error", ex);
            error = true;
        } finally {
            NotificationUtil.getInstance().clearNotification(notificationId);
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            notificationId,
                            NotificationUtil.GeneralIcon.SYNC_SUCCESS.getID(),
                            NotificationUtil.GREEN,
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_dted_import_completed),
                                    ((error)
                                            ? _context
                                                    .getString(
                                                            R.string.importmgr_with_errors)
                                            : "")),
                            null, null, true);
            if (zis != null) {
                try {
                    zis.closeEntry();
                } catch (IOException ioe) {
                    Log.d(TAG, "error occurred during unzipping", ioe);
                }
                try {
                    zis.close();
                } catch (IOException ioe) {
                    Log.d(TAG, "error occurred during unzipping", ioe);
                }
            }
        }
        return !error;
    }

}
