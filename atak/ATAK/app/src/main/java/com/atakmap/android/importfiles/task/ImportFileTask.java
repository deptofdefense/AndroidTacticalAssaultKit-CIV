
package com.atakmap.android.importfiles.task;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import android.content.DialogInterface;
import android.widget.TextView;

import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.image.ImageGalleryReceiver;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.sort.ImportResolver.SortFlags;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Background task to import a single file
 * 
 * 
 */
public class ImportFileTask extends
        AsyncTask<String, ImportFileTask.ProgressUpdate, ImportFileTask.Result> {

    private static final String TAG = "ImportFileTask";

    /**
     * Provides a mechanism to be notified once a file has been "imported" Allows injection of
     * additional logic beyond notifying user of success/failure Invoked if import is successful, or
     * fails, but not if cancelled
     * 
     * 
     */
    public interface Callback {
        void onCallback(Context context, Result result);
    }

    protected final Context _context;

    /**
     * Allow background thread to sync with UI thread if user is prompted for file overwrite
     */
    private final Semaphore _semaphore;
    private boolean _bOverwrite;

    private int _importFlags;

    private final Callback _callback;

    public static final int FlagValidateExt = 1;
    public static final int FlagPromptOverwrite = 1 << 1;
    public static final int FlagSkipDeleteOnMD5Match = 1 << 2;

    /**
     * Set flag to leave original file in place. Otherwise file will be moved (more efficient)
     */
    public static final int FlagCopyFile = 1 << 3;

    /**
     * Set flag to import from source location. Otherwise move or copy based on FlagCopyFile
     */
    public static final int FlagImportInPlace = 1 << 4;

    /**
     * See ImportKMZSort._bStrict
     */
    public static final int FlagKMZStrictMode = 1 << 5;

    public static final int FlagPromptOnMultipleMatch = 1 << 6;

    public static final int FlagShowNotificationsDuringImport = 1 << 7;

    public static final int FlagZoomToFile = 1 << 8;

    public static final int FlagHideFile = 1 << 9;

    protected static abstract class ProgressUpdate {
        public abstract void callOnUIThread();
    }

    /**
     * Result of import attempt
     * Note, for most implementations, this currently means the import was
     * initiated and is processing, not necessarily completed successfully
     * 
     * 
     */
    public static class Result {
        private final File file;
        private String type;
        private String fileMD5;
        private final boolean success;
        private final String error;
        private ImportResolver sorter;

        Result(File f, String t, String md5, ImportResolver s) {
            file = f;
            type = t;
            fileMD5 = md5;
            sorter = s;
            success = true;
            error = null;
        }

        Result(String e) {
            file = null;
            success = false;
            error = e;
        }

        public File getFile() {
            return file;
        }

        public String getError() {
            return error;
        }

        public String getFileMD5() {
            return fileMD5;
        }

        public ImportResolver getSorter() {
            return sorter;
        }

        public String getType() {
            return type;
        }

        public boolean isSuccess() {
            return success;
        }
    }

    public ImportFileTask(Context context, Callback callback) {
        this._context = context;
        this._callback = callback;
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

    public boolean checkFlag(int flagBits) {
        return checkFlag(getFlags(), flagBits);
    }

    public static boolean checkFlag(int flags, int flagBits) {
        return (flags & flagBits) > 0;
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

    public void addFlag(int flagBits) {
        setFlags(getFlags() | flagBits);
    }

    public void removeFlag(int flagBits) {
        setFlags(getFlags() & ~flagBits);
    }

    @Override
    protected Result doInBackground(String... params) {
        Thread.currentThread().setName("ImportFileTask");

        if (params == null || params.length < 1) {
            Log.w(TAG, "No files to import...");
            return new Result(_context.getString(R.string.no_file_specified));
        }

        // get sorters, import in place when possible, otherwise copy
        List<ImportResolver> sorters = ImportFilesTask.GetSorters(_context,
                checkFlag(FlagValidateExt), checkFlag(FlagCopyFile),
                checkFlag(FlagImportInPlace), checkFlag(FlagKMZStrictMode));
        if (sorters == null || sorters.size() < 1) {
            Log.w(TAG, "Found no ATAK import sorters");
            return new Result(
                    _context.getString(
                            R.string.importmgr_found_no_atak_import_handlers));
        }

        return sort(params[0], sorters);
    }

    private ImportFileTask.Result sort(String filePath,
            List<ImportResolver> sorters) {

        File file = null;
        Uri fileUri = Uri.parse(filePath);
        String scheme = fileUri.getScheme();
        if (!FileSystemUtils.isEmpty(scheme) && scheme.equals("content")) {
            // Retrieve file from content URI
            String fileName = ImageGalleryReceiver.getContentField(_context,
                    fileUri, MediaStore.Images.ImageColumns.DISPLAY_NAME);
            if (fileName != null) {
                try {
                    File tmpFile = new File(FileSystemUtils.validityScan(
                            FileSystemUtils
                                    .getItem(FileSystemUtils.TMP_DIRECTORY)
                                    + File.separator + fileName));
                    if (ImageGalleryReceiver.extractContent(
                            _context, fileUri, tmpFile))
                        file = tmpFile;
                } catch (Exception ignore) {
                    file = null;
                }
            }
        } else {
            if (!FileSystemUtils.isEmpty(scheme) && scheme.equals("file"))
                filePath = fileUri.getPath();
            try {
                file = new File(FileSystemUtils.validityScan(filePath));
            } catch (Exception ignore) {
                file = null;
            }
        }
        if (file == null)
            return new Result(String.format(_context.getString(
                    R.string.importmgr_import_file_not_found), filePath));
        if (!IOProviderFactory.exists(file)) {
            Log.w(TAG, "Import file not found: " + file.getAbsolutePath());
            return new Result(
                    String.format(_context.getString(
                            R.string.importmgr_import_file_not_found),
                            file.getName()));
        }

        boolean isDirectory = IOProviderFactory.isDirectory(file);

        final List<ImportResolver> matchingSorters = new ArrayList<>();
        for (ImportResolver sorter : sorters) {
            if (isDirectory && !sorter.directoriesSupported()) {
                continue;
            }
            try {
                if (sorter.match(file)) {
                    matchingSorters.add(sorter);
                }
            } catch (Exception ioe) {
                Log.e(TAG, "error using sorter[" + sorter.getClass()
                        + "] on file: " + file);
            }
        }

        // This allow for all sorters that matched to be given an opportunity to
        // modify the list appropriately.    The specific case is where there is a plugin that
        // exists that wants to make sure it is used over one of the built in functions within
        // ATAK.   As the set is reduced, the filter is only run against the remaining resolvers.

        final List<ImportResolver> lir = new ArrayList<>(matchingSorters);
        for (ImportResolver ir : lir) {
            if (matchingSorters.contains(ir)) {
                ir.filterFoundResolvers(matchingSorters, file);
            }
        }

        if (matchingSorters.size() == 0) {
            Log.w(TAG,
                    "Did not import unsupported file: "
                            + file.getAbsolutePath());
            return new Result(
                    String.format(
                            _context.getString(
                                    R.string.importmgr_did_not_import_unsupported_file),
                            file.getName()));
        }

        if (matchingSorters.size() > 1
                && checkFlag(FlagPromptOnMultipleMatch)) {

            final File finalFile = file;
            final CanceledContainer isCanceled = new CanceledContainer();
            publishProgress(new ProgressUpdate() {
                @Override
                public void callOnUIThread() {
                    promptForUserOrder(finalFile, matchingSorters, isCanceled);
                }
            });

            try {
                // now wait for user response
                _semaphore.acquire();
            } catch (InterruptedException e) {
                Log.e(TAG, "Wait interruption", e);
            }

            if (isCanceled.canceled) {
                return new Result(
                        String.format(
                                _context.getString(
                                        R.string.importmgr_cancelled_import),
                                file.getName()));
            }
        }

        Log.d(TAG, "Importing file: " + file.getAbsolutePath());
        for (ImportResolver sorter : matchingSorters) {
            final ImportStrategyContainer container = new ImportStrategyContainer();

            // check if we will be overwriting an existing file
            File destPath = sorter.getDestinationPath(file);
            if (destPath == null) {
                Log.w(TAG, sorter
                        + ", Unable to determine destination path for: "
                        + file.getAbsolutePath());
                continue;
            }

            String newMD5 = "";

            // Check if file and destPath are actually in the same place.
            boolean sameFile = false;
            try {
                sameFile = file.getCanonicalFile().equals(
                        destPath.getCanonicalFile());
            } catch (IOException e) {
                sameFile = false;
            }

            if (IOProviderFactory.exists(destPath) && !sameFile) {
                newMD5 = HashingUtils.md5sum(file);
                // see if we should compare MD5 since file will be overwritten
                if (checkFlag(FlagSkipDeleteOnMD5Match)) {
                    String existingMD5 = HashingUtils.md5sum(destPath);
                    if (existingMD5 != null && existingMD5.equals(newMD5)) {
                        Log.d(TAG,
                                sorter
                                        + ", File has not been updated, discarding: "
                                        + file.getAbsolutePath()
                                        + " based on MD5: " + newMD5);
                        if (!IOProviderFactory.delete(file,
                                IOProvider.SECURE_DELETE))
                            Log.w(TAG,
                                    sorter
                                            + ", Failed to delete un-updated file: "
                                            + file.getAbsolutePath());
                        // sorter matched and we decided not to sort/move, lets return
                        // use extension (except the period) as type
                        String type;
                        if (!FileSystemUtils.isEmpty(sorter.getExt()))
                            type = sorter.getExt().substring(1)
                                    .toUpperCase(LocaleUtil.getCurrent());
                        else
                            type = FileSystemUtils.getExtension(destPath,
                                    true, false);
                        return new Result(destPath, type, newMD5, sorter);
                    } else {
                        Log.d(TAG,
                                "Overwriting (pending user input) existing file with updates: "
                                        + destPath.getAbsolutePath());
                    }
                }

                // see if we should prompt user before overwriting
                if (!checkFlag(FlagPromptOverwrite)) {
                    Log.d(TAG,
                            "Overwriting existing file without prompting user: "
                                    + destPath.getAbsolutePath());
                } else {
                    // ask user whether to overwrite existing file
                    //publishProgress(destPath);

                    final File finalDestPath = destPath;
                    final CanceledContainer isCanceled = new CanceledContainer();
                    publishProgress(new ProgressUpdate() {
                        @Override
                        public void callOnUIThread() {
                            promptForOverwrite(finalDestPath, isCanceled);
                        }

                    });

                    try {
                        // now wait for user response
                        _semaphore.acquire();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Wait interruption", e);
                    }

                    if (isCanceled.canceled) {
                        return new Result(
                                String.format(
                                        _context.getString(
                                                R.string.importmgr_cancelled_import),
                                        file.getName()));
                    }

                    if (!_bOverwrite) {
                        Log.w(TAG,
                                "Cancelled import, not overwriting: "
                                        + file.getAbsolutePath());
                        return new Result(
                                String.format(
                                        _context.getString(
                                                R.string.importmgr_cancelled_import),
                                        file.getName()));
                    }

                    // user indicated OK to overwrite
                    Log.d(TAG,
                            "User selected to overwite existing file: "
                                    + destPath.getAbsolutePath());
                }
            }

            Set<SortFlags> flags = new HashSet<>();
            if (checkFlag(FlagShowNotificationsDuringImport))
                flags.add(SortFlags.SHOW_NOTIFICATIONS);

            if (checkFlag(FlagZoomToFile))
                flags.add(SortFlags.ZOOM_TO_FILE);

            if (checkFlag(FlagHideFile))
                flags.add(SortFlags.HIDE_FILE);

            if (container.strategy != null) {
                flags.add(container.strategy);
            }

            // now attempt to sort (i.e. move the file to proper location)
            if (sorter.beginImport(file, flags)) {
                Log.d(TAG,
                        sorter + ", initiated import of: "
                                + file.getAbsolutePath() + " to "
                                + destPath.getAbsolutePath());
                // use extension (except the period) as type
                String type;
                if (!FileSystemUtils.isEmpty(sorter.getExt()))
                    type = sorter.getExt().substring(1)
                            .toUpperCase(LocaleUtil.getCurrent());
                else
                    type = FileSystemUtils.getExtension(destPath,
                            true, false);
                return new Result(destPath, type, newMD5, sorter);
            } else
                Log.w(TAG,
                        sorter + ", Matched, but did not sort: "
                                + file.getAbsolutePath());
        } // end if sorter match was found

        Log.w(TAG,
                "Did not import unsupported file: " + file.getAbsolutePath());
        return new Result(
                String.format(
                        _context.getString(
                                R.string.importmgr_did_not_import_unsupported_file),
                        file.getName()));
    }

    private final static Object lock = new Object();
    private static AlertDialog failureDialog;
    private static AlertDialog unknownDialog;

    private void showAlertFailure() {
        synchronized (lock) {
            if (failureDialog == null) {
                failureDialog = new AlertDialog.Builder(_context)
                        .setTitle(R.string.import_failed)
                        .setMessage(R.string.failed_to_import)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        synchronized (lock) {
                                            failureDialog = null;
                                        }
                                    }
                                })
                        .create();
                failureDialog.show();
            }
        }
    }

    private void showAlertUnknown(final String error) {
        synchronized (lock) {
            if (unknownDialog == null) {
                unknownDialog = new AlertDialog.Builder(_context)
                        .setTitle(R.string.import_cancelled)
                        .setMessage(error)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        synchronized (lock) {
                                            unknownDialog = null;
                                        }
                                    }
                                })
                        .create();
                unknownDialog.show();
            } else {
                TextView tv = unknownDialog
                        .findViewById(android.R.id.message);
                if (tv != null) {
                    tv.setText(tv.getText() + "\n" + error);
                }
            }
        }
    }

    @Override
    protected void onPostExecute(ImportFileTask.Result result) {
        if (result == null) {
            Log.e(TAG, "Failed to import file");
            showAlertFailure();
            return;
        }

        if (!result.success) {
            String error = result.error;
            if (error == null || error.length() < 1)
                error = _context
                        .getString(
                                R.string.importmgr_failed_to_import_file_error_unknown);

            showAlertUnknown(error);
        }

        if (_callback != null)
            _callback.onCallback(_context, result);
    }

    private static class CanceledContainer {
        public boolean canceled = false;
    }

    private void promptForUserOrder(File file,
            final List<ImportResolver> candidates,
            final CanceledContainer isCanceled) {
        // Deduplicate the list displayed to the user. If a
        // duplicate is found, remove it from candidates so the order
        // of the list doesn't get screwed up.

        Set<String> names = new HashSet<>();
        for (Iterator<ImportResolver> it = candidates.iterator(); it
                .hasNext();) {
            ImportResolver r = it.next();
            if (!names.contains(r.getDisplayableName())) {
                names.add(r.getDisplayableName());
            } else {
                it.remove();
            }
        }

        MapView mv = MapView.getMapView();

        if (names.size() <= 1 || mv == null) {
            // Through deduplication, we've
            // gotten down to only a single
            // option and there isn't any reason to 
            // prompt the user any more.

            while (_semaphore.availablePermits() != 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
            }

            _semaphore.release();
            return;
        }

        TileButtonDialog d = new TileButtonDialog(mv);
        for (ImportResolver res : candidates)
            d.addButton(res.getIcon(), res.getDisplayableName());
        d.setTitle(R.string.importmgr_select_desired_import_method,
                file.getName());
        d.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d("ImportFilesTest", "Clicked item: " + which);

                if (which != 0) {
                    // If the selected resolver is not the first item
                    // in the list, move it to the beginning so it will
                    // be attempted first.
                    ImportResolver selected = candidates
                            .get(which);
                    candidates.remove(selected);
                    candidates.add(0, selected);
                }

                _semaphore.release();
            }
        });
        d.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                isCanceled.canceled = true;
                _semaphore.release();
            }
        });
        d.show(true);
    }

    private void promptForOverwrite(File destFile,
            final CanceledContainer isCanceled) {

        new AlertDialog.Builder(_context)
                .setTitle(R.string.importmgr_overwrite_existing_import)
                .setMessage(String.format(
                        _context.getString(R.string.importmgr_overwrite_file2),
                        destFile.getName()))
                .setPositiveButton(R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int whichButton) {
                        _bOverwrite = true;
                        _semaphore.release();
                    }
                })
                .setNegativeButton(R.string.cancel, // implemented
                        new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                _bOverwrite = false;
                                _semaphore.release();
                            }
                        })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        isCanceled.canceled = true;
                        _semaphore.release();
                    }

                })
                .show();
    }

    static private class ImportStrategyContainer {
        final SortFlags strategy = null;
    }

    /**
     * Currently, this is used to execute things on the main UI thread, since trying to
     * pop up a dialog from within an AsyncTask will cause an error.
     */
    @Override
    protected void onProgressUpdate(ProgressUpdate... updates) {

        for (ProgressUpdate p : updates) {
            p.callOnUIThread();
        }
    }
}
