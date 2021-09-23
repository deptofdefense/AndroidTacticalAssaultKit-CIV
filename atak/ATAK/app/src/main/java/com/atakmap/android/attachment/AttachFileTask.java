
package com.atakmap.android.attachment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.android.importfiles.task.ImportFileTask;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.IoUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

/**
 * Background task to attach a single file to a map item
 * 
 * <P>This class may make use of IO that is not through the default
 * abstraction layer in order to support the following use-cases:
 * <UL>
 *     <LI>Files chosen through Android system file browser
 * </UL>
 */
public class AttachFileTask extends AsyncTask<File, File, AttachFileTask.Result>
        implements DialogInterface.OnCancelListener {

    private static final String TAG = "AttachFileTask";

    protected final Context _context;
    protected Runnable _callback;

    private final String _callsign;
    protected final String _uid;

    /**
     * Allow background thread to sync with UI thread if user is prompted for file overwrite
     */
    private final Semaphore _semaphore;
    private boolean _bOverwrite;
    protected ProgressDialog _progressDialog;

    private int _importFlags;
    private IOProvider _provider;

    private static final int FlagRejectFilesInATAKdir = 1;
    public static final int FlagPromptOverwrite = 4;

    /**
     * Result of import attempt
     * 
     * 
     */
    public static class Result {
        public final File file;
        public final boolean success;
        public final String error;

        Result(File f) {
            file = f;
            success = true;
            error = null;
        }

        Result(String e) {
            file = null;
            success = false;
            error = e;
        }

    }

    public AttachFileTask(Context context, String uid,
            String callsign) {
        this(context, uid, callsign, null);
    }

    public void setCallback(final Runnable callback) {
        this._callback = callback;
    }

    public AttachFileTask(final Context context, final String uid,
            final String callsign, final Runnable callback) {
        this._context = context;
        this._callback = callback;
        this._uid = uid;
        this._callsign = callsign;
        this._semaphore = new Semaphore(0);
        this._bOverwrite = false;
        this._importFlags = 0;
        this._provider = null;
    }

    /**
     * Get the style property value
     * 
     * @return a bitfield
     */
    public int getFlags() {
        return _importFlags;
    }

    private boolean checkFlag(int flagBits) {
        return (getFlags() & flagBits) > 0;
    }

    /**
     * Set the flags property value
     * 
     * @param flags a bitfield
     */
    public void setFlags(int flags) {
        if (_importFlags != flags) {
            _importFlags = flags;
        }
    }

    /**
     * Set the {@link IOProvider} to use to read the source file. Specify
     * <code>null</code> to use the {@link IOProvider} instance registered with
     * {@link IOProviderFactory}.
     *
     * @param provider  The {@link IOProvider} to use to access the source file
     *                  during task execution
     */
    public void setProvider(IOProvider provider) {
        _provider = provider;
    }

    @Override
    protected void onPreExecute() {
        // Before running code in background/worker thread
        _progressDialog = new ProgressDialog(_context);
        _progressDialog.setTitle(_context.getString(R.string.details_text49));
        if (!FileSystemUtils.isEmpty(_callsign))
            _progressDialog.setMessage(_context
                    .getString(R.string.attaching_to)
                    + _callsign
                    + _context.getString(R.string.ellipses));
        else
            _progressDialog.setMessage(_context.getString(R.string.attaching));
        _progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        _progressDialog.setMax(100);
        _progressDialog.setCancelable(false);
        _progressDialog.setCanceledOnTouchOutside(false);
        _progressDialog.setOnCancelListener(this);
        _progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                _context.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        _progressDialog.cancel();
                    }
                });
        _progressDialog.show();

    }

    @Override
    protected Result doInBackground(File... params) {
        Thread.currentThread().setName("AttachFileTask");

        if (params == null || params.length < 1) {
            Log.w(TAG, "No files to import...");
            return new Result("No file specified");
        }

        // obtain the provider instance to be used for the task execution
        final IOProvider sourceProvider = (_provider != null) ? _provider
                : IOProviderFactory.getProvider();

        // just take first file for now
        File file = params[0];

        // do not import a file already in ATAK
        // Note, if a centralized ATAK dir goes away, will have to instead
        // query ATAK/DBs to see if the file is already imported
        if (checkFlag(FlagRejectFilesInATAKdir)
                && file.getAbsolutePath().contains(
                        FileSystemUtils.ATAK_ROOT_DIRECTORY)) {
            Log.w(TAG, "File already in ATAK: " + file.getAbsolutePath());
            return new Result("File already in ATAK: " + file.getName());
        }

        if (!sourceProvider.exists(file)) {
            Log.w(TAG, "Import file not found: " + file.getAbsolutePath());
            return new Result("Import file not found: " + file.getName());
        }

        File parent = new File(FileSystemUtils.getItem("attachments")
                .getAbsolutePath() + File.separatorChar + _uid);

        if (sourceProvider.isDirectory(file)) {
            String[] files = sourceProvider.list(file);
            if (FileSystemUtils.isEmpty(files))
                return new Result(
                        "Import directory is empty: " + file.getName());
            for (String fn : files) {
                Result res = copyFile(parent, new File(file, fn),
                        sourceProvider);
                if (!res.success)
                    return res;
            }
            return new Result(file);
        }
        return copyFile(parent, file, sourceProvider);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        cancel(false);
        Toast.makeText(_context, R.string.import_cancelled, Toast.LENGTH_SHORT)
                .show();
    }

    private Result copyFile(File parent, File file, IOProvider sourceProvider) {
        File destPath = new File(parent, file.getName());
        if (IOProviderFactory.exists(destPath)) {

            // see if we should prompt user before overwriting
            if (!checkFlag(FlagPromptOverwrite)) {
                Log.d(TAG,
                        "Overwriting existing file without prompting user: "
                                + destPath.getAbsolutePath());
            } else {
                // ask user whether to overwrite existing file
                publishProgress(destPath);

                try {
                    // now wait for user response
                    _semaphore.acquire();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Wait interruption", e);
                }

                if (!_bOverwrite) {
                    Log.w(TAG,
                            "Cancelled import, not overwriting: "
                                    + file.getAbsolutePath());
                    return new Result("Cancelled import: " + file.getName());
                }

                // user indicated OK to overwrite
                Log.d(TAG, "User selected to overwite existing file: "
                        + destPath.getAbsolutePath());
            }
        } // end file overwrite check

        // move/copy file
        if (!IOProviderFactory.mkdirs(parent))
            Log.w(TAG,
                    "Failed to create directories" + parent.getAbsolutePath());

        try {
            return copyMoveFile(file, destPath, sourceProvider,
                    !checkFlag(ImportFileTask.FlagCopyFile));
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file: " + file, e);
            return new Result("Failed to copy: " + file.getName());
        }
    }

    /**
     * Safely copies or moves the specified file, including case where source
     * and destination files are the same. The source file will be accessed
     * using the specified {@link IOProvider} instance; the destination file
     * will be accessed using {@link IOProviderFactory}.
     *
     * @param srcFile           The source file
     * @param dstFile           The destination file
     * @param srcFileProvider   The {@link IOProvider} instance used to access
     *                          the source file
     * @param move              If <code>true</code>, the source file is
     *                          effectively moved to the destination file path,
     *                          otherwise a copy is executed. Note that the
     *                          move may be implemented via copy followed by
     *                          delete.
     * @throws IOException      an exception if a problem occured moving or copying the file.
     */
    private Result copyMoveFile(File srcFile, File dstFile,
            IOProvider srcFileProvider, boolean move) throws IOException {
        if (!srcFile.equals(dstFile)
                || srcFileProvider != IOProviderFactory.getProvider()) {
            try (FileInputStream fis = srcFileProvider.getInputStream(srcFile);
                    OutputStream os = IOProviderFactory
                            .getOutputStream(dstFile)) {
                copyStream(fis, os, srcFileProvider.length(srcFile));
            }
        } else {
            // file is the same and using same IO provider so copy/move
            // operation is no-op. toggle the move flag if set so we don't
            // delete the file
            move = false;
        }

        // move was requested, perform delete in the case that we copied
        if (move)
            srcFileProvider.delete(srcFile, IOProvider.SECURE_DELETE);

        // Delete the output file if the user cancelled
        if (isCancelled()) {
            FileSystemUtils.delete(dstFile);
            return new Result(_context.getString(
                    R.string.importmgr_cancelled_import, dstFile.getName()));
        }

        return new Result(dstFile);
    }

    private void copyStream(InputStream in, OutputStream out, long fileLen)
            throws IOException {
        byte[] buf = new byte[FileSystemUtils.BUF_SIZE];
        try {
            int len;
            long written = 0;
            while (!isCancelled() && (len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
                written += len;
                updateProgress(written, fileLen);
            }
        } finally {
            IoUtils.close(in);
            IoUtils.close(out);
        }
    }

    private void updateProgress(long written, long total) {
        final int prog = (int) Math.round(((double) written / total) * 100);
        ((Activity) _context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                _progressDialog.setProgress(prog);
            }
        });
    }

    @Override
    protected void onPostExecute(AttachFileTask.Result result) {
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (result == null) {
            Log.e(TAG, "Failed to import file");
            new AlertDialog.Builder(_context)
                    .setTitle(R.string.import_failed)
                    .setMessage(R.string.failed_to_import)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        if (result.success && result.file != null) {
            String name = result.file.getName();
            if (IOProviderFactory.isDirectory(result.file)) {
                File[] files = IOProviderFactory.listFiles(result.file);
                if (files != null) {
                    if (files.length == 1)
                        name = files[0].getName();
                    else
                        name = _context.getString(R.string.files_count,
                                files.length);
                } else {
                    name = result.file.getName();
                }
            }
            Toast.makeText(_context,
                    _context.getString(R.string.attached) + name
                            + _context.getString(R.string.ellipses),
                    Toast.LENGTH_SHORT)
                    .show();
            Log.d(TAG,
                    "Finished copying file attachment: "
                            + result.file + " for UID: " + _uid);

            // refresh to get new attachment added to the list
            if (_callback != null)
                ((Activity) _context).runOnUiThread(_callback);
        } else {
            String error = result.error;
            if (error == null || error.length() < 1)
                error = "Failed to import file, error unknown";

            new AlertDialog.Builder(_context)
                    .setTitle(R.string.import_cancelled)
                    .setMessage(error)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    /**
     * Currently, use this only to ask user prior to overwriting existing file with the same name
     * which has already been imported
     * 
     * @param files Files being attached
     */
    @Override
    protected void onProgressUpdate(File... files) {

        if (files == null || files.length < 1) {
            Log.e(TAG, "No files to confirm...");
            _bOverwrite = false;
            _semaphore.release();
            return;
        }

        // just take first file for now
        File file = files[0];

        new AlertDialog.Builder(_context)
                .setTitle(R.string.overwrite_existing)
                .setMessage(
                        _context.getString(R.string.overwrite)
                                + file.getName()
                                + _context
                                        .getString(
                                                R.string.question_mark_symbol))
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                _bOverwrite = true;
                                _semaphore.release();
                            }
                        })
                .setNegativeButton(R.string.cancel, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                _bOverwrite = false;
                                _semaphore.release();
                            }
                        })
                .show();
    }
}
