
package com.atakmap.android.user.feedback;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.IoUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

class SubmitFeedbackHelper {

    private final static String TAG = "SubmitFeedbackHelper";

    interface SubmitFeedbackProgress {
        /**
         * Provides a progress update for the publication of the user feedback.
         * @param file the current file being published
         * @param size the size of the file being published
         * @param currentIdx the current file index being processed
         * @param maxIdx the total number of files to be processed
         * @param currentBytes the current total number of bytes submitted
         * @param maxBytes the total number of bytes to be processed
         */
        void progress(String file, long size, int currentIdx, int maxIdx,
                long currentBytes, long maxBytes);

        /**
         * Call when the submission process is complete.
         * @param error true if the the submission process has completed with an error
         */
        void finished(boolean error);

        /**
         * Checked to see if the user has cancelled the publication process.
         * @return true if the user has requested the process to be cancelled.
         */
        boolean isCancelled();
    }

    /**
     * Creates a feedback package ready for submission to the provided directory
     */
    public static void submit(final Feedback feedback,
            final SubmitFeedbackProgress callback) {
        final File folder = feedback.getFolder();
        final File dest = FileSystemUtils.getItem("support/logs");
        Thread t = new Thread() {
            public void run() {

                final List<File> files = new ArrayList<>();
                //loop and add all files
                for (File f : IOProviderFactory.listFiles(folder)) {
                    if (!FileSystemUtils.isFile(f)
                            || IOProviderFactory.isDirectory(f)) {
                        Log.w(TAG, "Skipping invalid file: "
                                + (f == null ? "" : f.getAbsolutePath()));
                        continue;
                    }
                    files.add(f);
                }

                try {
                    File zipFile = zip(files,
                            new File(dest,
                                    folder.getName() + "."
                                            + ResourceFile.MIMEType.FPKG.EXT),
                            callback);
                    if (zipFile == null) {
                        callback.finished(true);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (callback != null)
                        callback.finished(false);
                }
            }

        };
        t.start();
    }

    /**
     * Compress the file list into a ZIP file
     *
     * @param files The list of files to be compressed.
     * @param dest the destination zip file.
     * @return the destination zip file if successful otherwise null
     * @throws IOException io exception if there is an exception writing the file.
     */
    private static File zip(List<File> files, File dest,
            SubmitFeedbackProgress callback) throws IOException {

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(
                IOProviderFactory.getOutputStream(dest)))) {

            final int totalNum = files.size();
            int currNum = 0;

            long totalSize = 0;
            long currSize = 0;

            for (File file : files)
                totalSize += IOProviderFactory.length(file);

            for (File file : files) {
                String filename = file.getName();
                final long size = IOProviderFactory.length(file);
                if (callback != null)
                    callback.progress(filename, size, currNum, totalNum,
                            currSize, totalSize);
                addFile(zos, file, filename);
                currNum++;
                currSize += size;
                if (callback != null)
                    callback.progress(filename, size, currNum, totalNum,
                            currSize, totalSize);
            }
            // artificial wait for 2 seconds so the user can see the progress bar if no files are
            // found
            try {
                Thread.sleep(1000);
            } catch (Exception ignored) {
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to create Zip file", e);
            throw new IOException(e);
        }

        //validate the required files
        if (FileSystemUtils.isFile(dest)) {
            Log.d(TAG, "Exported: " + dest.getAbsolutePath());
            return dest;
        } else {
            Log.w(TAG,
                    "Failed to export valid zip: "
                            + dest.getAbsolutePath());
            return null;
        }
    }

    /**
     * Add a file to a ZIP archive stream
     *
     * @param zos ZIP output stream
     * @param file File to compress
     * @param filename File name that will show up in the ZIP
     */
    public static void addFile(ZipOutputStream zos, File file,
            String filename) {
        try {
            // create new zip entry
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(filename);
            zos.putNextEntry(entry);

            // stream file into zipstream

            FileInputStream fis = null;

            try {
                if (!IOProviderFactory.isDirectory(file)) {
                    fis = IOProviderFactory.getInputStream(file);
                    FileSystemUtils.copyStream(fis, true, zos, false);
                }
            } finally {
                IoUtils.close(fis);

                // close current file & corresponding zip entry
                zos.closeEntry();

            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to add File: " + file.getAbsolutePath(), e);
        }
    }

}
