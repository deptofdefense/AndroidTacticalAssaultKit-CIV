
package com.atakmap.android.rubbersheet.data.export;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.ProgressTask;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Task for creating a rubber sheet from an image file
 */
public abstract class ExportFileTask extends ProgressTask {

    private static final String TAG = "ExportFileTask";

    protected final Callback _callback;
    protected final boolean _wrap180;
    protected final AbstractSheet _item;
    protected final List<File> _outputFiles = new ArrayList<>();

    public ExportFileTask(MapView mapView, AbstractSheet item,
            Callback callback) {
        super(mapView);
        _item = item;
        _wrap180 = mapView.isContinuousScrollEnabled();
        _callback = callback;
    }

    protected synchronized void addOutputFile(File f) {
        _outputFiles.add(f);
    }

    protected synchronized List<File> getOutputFiles() {
        return new ArrayList<>(_outputFiles);
    }

    @Override
    protected void onPostExecute(Object ret) {
        super.onPostExecute(ret);
        if (_callback != null && ret instanceof File)
            _callback.onExportFinished(_item, (File) ret);
    }

    @Override
    protected void onCancelled() {
        toast(R.string.export_cancelled_for, _item.getName());
        for (File f : getOutputFiles()) {
            if (f != null && IOProviderFactory.exists(f))
                FileSystemUtils.delete(f);
        }
    }

    public interface Callback {
        void onExportFinished(AbstractSheet item, File file);
    }

    /**
     * Copy of {@link FileSystemUtils#zipDirectory(File, File)} with progress
     * tracking
     */
    /**
     * Compress the directory contents into a ZIP file
     * Copied out of FileSystemUtils with added callback functionality
     */
    protected static File zipDirectory(final File dir, File dest,
            final FileProgressCallback cb) throws IOException {
        if (dest == null) {
            Log.w(TAG, "Cannot zip to missing file");
            return null;
        }

        if (!FileSystemUtils.isFile(dir)) {
            Log.w(TAG, "Cannot zip missing Directory file");
            return null;
        }

        if (!IOProviderFactory.isDirectory(dir)) {
            Log.w(TAG,
                    "Cannot zip non Directory file: " + dir.getAbsolutePath());
            return null;
        }

        File[] files = IOProviderFactory.listFiles(dir);
        if (FileSystemUtils.isEmpty(files)) {
            Log.w(TAG, "Cannot zip empty Directory: " + dir.getAbsolutePath());
            return null;
        }

        ZipOutputStream zos = null;
        try {
            FileOutputStream fos = IOProviderFactory.getOutputStream(dest);
            zos = new ZipOutputStream(new BufferedOutputStream(fos));
            byte[] buf = new byte[FileSystemUtils.BUF_SIZE];

            // Progress on the entire task
            FileProgressCallback wrapperCB = null;
            if (cb != null) {
                long totalLen = 0;
                for (File f : files)
                    totalLen += IOProviderFactory.length(f);
                final long fMaxProg = totalLen;
                wrapperCB = new FileProgressCallback() {
                    int totalProg = 0;
                    File lastFile = null;

                    @Override
                    public boolean onProgress(File file, long prog, long max) {
                        if (lastFile != file) {
                            if (lastFile != null)
                                totalProg += IOProviderFactory.length(lastFile);
                            lastFile = file;
                        }
                        return cb.onProgress(dir, totalProg + prog, fMaxProg);
                    }
                };
            }

            // Zip all the files
            for (File f : files)
                addFile(zos, f, f.getName(), buf, wrapperCB);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Zip file", e);
            throw new IOException(e);
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (Exception e) {
                    Log.w(TAG,
                            "Failed to close Zip: " + dest.getAbsolutePath());
                }
            }
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

    protected static void addFile(ZipOutputStream zos, File file,
            String filename, byte[] buf, FileProgressCallback cb) {
        try {
            // Create new ZIP entry
            ZipEntry entry = new ZipEntry(filename);
            zos.putNextEntry(entry);

            // Write the file to the ZIP
            appendStream(file, zos, buf, cb);
            zos.closeEntry();
        } catch (Exception e) {
            Log.e(TAG, "Failed to add File: " + file.getAbsolutePath(), e);
        }
    }

    protected static void appendStream(File file, OutputStream os, byte[] buf,
            FileProgressCallback cb) {
        FileInputStream fis = null;
        long totalProg = IOProviderFactory.length(file);
        try {
            int len;
            long prog = 0;
            fis = IOProviderFactory.getInputStream(file);
            while ((len = fis.read(buf)) > 0) {
                os.write(buf, 0, len);
                if (cb != null) {
                    prog += len;
                    if (!cb.onProgress(file, prog, totalProg))
                        return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to append stream " + file, e);
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (Exception ignore) {
            }
        }
    }

    protected static boolean writeToFile(File file, String... lines) {
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(
                    IOProviderFactory.getOutputStream(file),
                    FileSystemUtils.UTF8_CHARSET.name()));
            for (String s : lines)
                pw.println(s);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write to " + file, e);
            return false;
        } finally {
            if (pw != null)
                pw.close();
        }
    }

    protected static void copyFile(String filePath, File dir) {
        if (FileSystemUtils.isEmpty(filePath))
            return;
        try {
            // Regular file
            File file = new File(filePath);
            File outFile = new File(dir, file.getName());
            if (IOProviderFactory.exists(file)
                    && IOProviderFactory.isFile(file)) {
                FileSystemUtils.copyFile(file, outFile);
                return;
            }
            // ZIP file
            if (filePath.contains(".zip/") || filePath.contains(".kmz/")) {
                ZipVirtualFile zf = new ZipVirtualFile(filePath);
                if (!IOProviderFactory.exists(zf))
                    return;
                FileSystemUtils.copy(zf.openStream(),
                        IOProviderFactory.getOutputStream(outFile));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy file: " + filePath, e);
        }
    }

    protected interface FileProgressCallback {
        /**
         * File progress event
         * @param file File being read/written to
         * @param progress Progress value
         * @param max Max value
         * @return True to continue task, false to cancel task
         */
        boolean onProgress(File file, long progress, long max);
    }
}
