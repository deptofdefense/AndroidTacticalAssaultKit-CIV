
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
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Background task to attach a single file to a map item
 * 
 * 
 */
public class AttachFileTask extends
        AsyncTask<File, File, AttachFileTask.Result> {

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
        _progressDialog.setIndeterminate(true);
        _progressDialog.setCancelable(true);
        _progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        // _progressDialog.setOnCancelListener(this);
        _progressDialog.show();

    }

    @Override
    protected Result doInBackground(File... params) {
        Thread.currentThread().setName("AttachFileTask");

        if (params == null || params.length < 1) {
            Log.w(TAG, "No files to import...");
            return new Result("No file specified");
        }

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

        if (!IOProviderFactory.exists(file)) {
            Log.w(TAG, "Import file not found: " + file.getAbsolutePath());
            return new Result("Import file not found: " + file.getName());
        }

        File parent = new File(FileSystemUtils.getItem("attachments")
                .getAbsolutePath() + File.separatorChar + _uid);

        if (IOProviderFactory.isDirectory(file)) {
            File[] files = IOProviderFactory.listFiles(file);
            if (FileSystemUtils.isEmpty(files))
                return new Result(
                        "Import directory is empty: " + file.getName());
            for (File f : files) {
                Result res = copyFile(parent, f);
                if (!res.success)
                    return res;
            }
            return new Result(file);
        }
        return copyFile(parent, file);
    }

    private Result copyFile(File parent, File file) {
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

        if (checkFlag(ImportFileTask.FlagCopyFile))
            try {
                FileSystemUtils.copyFile(file, destPath);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy file: " + file, e);
                return new Result("Failed to copy: " + file.getName());
            }
        else {
            FileSystemUtils.renameTo(file, destPath);
        }

        return new Result(destPath);
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
