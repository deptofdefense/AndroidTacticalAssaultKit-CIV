
package com.atakmap.android.filesharing.android.service;

import com.atakmap.android.filesharing.android.service.DirectoryWatcher.FileUpdateCallback.OPERATION;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.android.filesystem.ContentTypeMapper;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.os.FileObserver;

import java.io.File;
import java.io.FileFilter;

/**
 * Observe changes to directory and reflect changes
 * in a db, and optionally notify a callback listener
 */
public class DirectoryWatcher extends FileObserver implements ImportListener {

    private static final String TAG = "DirectoryWatcher";

    /**
     * Callback interface
     * 
     * 
     */
    public interface FileUpdateCallback {
        enum OPERATION {
            ADD,
            REMOVE
        }

        void fileUpdateCallback(File file, OPERATION op,
                long newestFileTime);
    }

    /**
     * Default is modification events only. Ignore CREATE so file writer may finish writing to disk
     * before Directory Watcher observes it e.g. no need to compute a partial MD5 after CREATE and
     * again after CLOSE_WRITE
     */
    private static final int CHANGES_ONLY = CLOSE_WRITE | DELETE | MOVE_SELF
            | MOVED_FROM | MOVED_TO;

    /**
     * Path of directory being watched
     */
    private final String absolutePath;

    /**
     * Callback listener
     */
    private FileUpdateCallback callback; // Support multiple listeners?

    private long newestFileTime = 0;

    /**
     * Maps file types to content type
     */
    private final ContentTypeMapper typeMapper;

    private final TABLETYPE dataType;

    private final boolean _ignoreExistingFiles;

    /**
     * File extension to match one
     */
    private final FileFilter _filter;

    /**
     * ctor
     * 
     * @param path the path to watch
     * @param cb callback to be triggered when a file is found
     * @param preCache true to iterate all files in the directory during ctor
     * @param ignoreExistingFiles if true, will ignore all files found in the path
     */
    public DirectoryWatcher(TABLETYPE dt, final String path,
            final FileFilter filter,
            ContentTypeMapper tm,
            FileUpdateCallback cb, boolean preCache,
            boolean ignoreExistingFiles) {
        super(path, CHANGES_ONLY);
        this._filter = filter;
        this.dataType = dt;
        this.absolutePath = path;
        this.typeMapper = tm;
        this.callback = cb;
        this._ignoreExistingFiles = ignoreExistingFiles;

        // create directory if it does not exist
        final File dir = new File(absolutePath);
        if (!IOProviderFactory.exists(dir)) {
            Log.d(TAG, "Creating directory: " + absolutePath);
            if (!IOProviderFactory.mkdirs(dir))
                Log.e(TAG, "Failed to create directory: " + absolutePath);
        } else {
            if (!IOProviderFactory.isDirectory(dir))
                Log.e(TAG, absolutePath + " is not a directory");
        }

        if (preCache)
            processDirectory();
    }

    protected void processDirectory() {

        File f = new File(absolutePath);
        File[] fileList = IOProviderFactory.listFiles(f);
        if (fileList == null) {
            // Matt, couldn't this just be an empty directory?
            Log.e(TAG, "somehow watching a file instead of a directory");
        } else {
            for (File i : fileList) {
                if (i != null && !IOProviderFactory.isDirectory(i)) {
                    if (_ignoreExistingFiles) {
                        // see if file is already in DB, if not add it
                        FileInfo fi = FileInfoPersistanceHelper.instance()
                                .getFileInfoFromFilename(
                                        i, this.dataType);
                        if (fi == null && checkFilter(i)) {
                            FileInfo fileInfo = new FileInfo(
                                    i,
                                    getUserName(),
                                    FileSystemUtils.stripExtension(i.getName()),
                                    typeMapper.getContentType(i), null);
                            boolean success = FileInfoPersistanceHelper
                                    .instance().insertOrReplace(
                                            fileInfo, dataType);
                            Log.d(TAG, "Inserting/Replacing file: " + i
                                    + " into DB "
                                    + (success ? "worked!" : "failed."));

                            // notify the LocalStorageManager or othe callback listener
                            newestFileTime = System.currentTimeMillis();
                            if (callback != null) {
                                callback.fileUpdateCallback(i, OPERATION.ADD,
                                        newestFileTime);
                            } else {
                                Log.w(TAG,
                                        "FileUpdateCallback is null so we had nowhere to send the update callback");
                            }
                        } else
                            Log.d(TAG,
                                    i.getAbsolutePath()
                                            + " already in FileInfo db or filtered by type");
                    } else {
                        if (checkFilter(i)) {
                            // insert or update in DB
                            FileInfo fileInfo = new FileInfo(
                                    i,
                                    getUserName(),
                                    FileSystemUtils.stripExtension(i.getName()),
                                    typeMapper.getContentType(i), null);
                            boolean success = FileInfoPersistanceHelper
                                    .instance().insertOrReplace(
                                            fileInfo, dataType);
                            Log.d(TAG, "Inserting/Replacing file: " + i
                                    + " into DB "
                                    + (success ? "worked!" : "failed."));

                            // notify the LocalStorageManager or othe callback listener
                            newestFileTime = System.currentTimeMillis();
                            if (callback != null) {
                                callback.fileUpdateCallback(i, OPERATION.ADD,
                                        newestFileTime);
                            } else {
                                Log.w(TAG,
                                        "FileUpdateCallback is null so we had nowhere to send the update callback");
                            }
                        } else
                            Log.d(TAG, i.getAbsolutePath()
                                    + " filtered by type");
                    }
                }
            }
        }
    }

    /**
     * @param file the file to check against the filter.
     * @return true if file passes filter and should be processed
     */
    private boolean checkFilter(final File file) {
        return _filter == null || _filter.accept(file);

    }

    @Override
    public void onFileSorted(File src, File dst) {
        if (dst.getAbsolutePath().startsWith(absolutePath)) {
            String path = dst.getAbsolutePath().replaceFirst(absolutePath, "");
            onEvent(CLOSE_WRITE, path);
        }
    }

    @Override
    public void onEvent(int event, String path) {
        // Log.d(TAG, "A file event (" + event + ") occurred in path: " + path);

        if (path == null) {
            return;
        }

        // TODO if new directory was added or moved here, add new dir listener
        // and same for any subdirs
        // TODO else if ((FileObserver.MOVE_SELF & event) != 0)
        // TODO remove this listener, and any sublisteners...
        // for now if directory change, ignore it
        File file = new File(getPath(), path);
        if (IOProviderFactory.isDirectory(file)) {
            Log.d(TAG,
                    "(" + getEventString(event)
                            + ") ignoring directory event for "
                            + file.getAbsolutePath());
            return;
        }

        if (!checkFilter(file)) {
            Log.d(TAG,
                    "(" + getEventString(event) + ") filtered by type on "
                            + file.getAbsolutePath());
            return;
        }

        OPERATION op = OPERATION.ADD;

        // insert or update DB for these events
        if ((FileObserver.CREATE & event) != 0 ||
                (FileObserver.MODIFY & event) != 0 ||
                (MOVED_TO & event) != 0 ||
                (CLOSE_WRITE & event) != 0) {

            op = OPERATION.ADD;

            if (_ignoreExistingFiles) {
                FileInfo fi = FileInfoPersistanceHelper.instance()
                        .getFileInfoFromFilename(file,
                                this.dataType);
                if (fi != null) {
                    Log.d(TAG, file.getAbsolutePath()
                            + " already in FileInfo db");
                    return;
                }
            }

            // add an entry to the DB (so the webserver knows about it)
            FileInfo fileInfo = new FileInfo(file, getUserName(),
                    FileSystemUtils.stripExtension(file.getName()),
                    typeMapper.getContentType(file), null);
            boolean success = FileInfoPersistanceHelper.instance()
                    .insertOrReplace(fileInfo,
                            dataType);
            if (!success) {
                Log.w(TAG,
                        "(" + getEventString(event)
                                + ") could NOT insert file, "
                                + file.getAbsolutePath() + ", into DB");
            } else {
                Log.d(TAG,
                        "(" + getEventString(event) + ") inserted file, "
                                + file.getAbsolutePath()
                                + " into DB");
                // for(FileInfo f : FileInfoPersistanceHelper.instance().allFiles()) {
                // Log.d(TAG, "File names in DB (" + f.id() + "): " + f.fileName());
                // }
            }

        } else if ((DELETE & event) != 0 ||
                (MOVED_FROM & event) != 0) {

            op = OPERATION.REMOVE;
            // remove from db for these events
            boolean success = FileInfoPersistanceHelper.instance().delete(file,
                    dataType);
            if (!success) {
                Log.w(TAG,
                        "(" + getEventString(event)
                                + ") could NOT delete file, "
                                + file.getAbsolutePath() + ", from DB");
            } else {
                // TODO should we send callback if delete failed?
                Log.d(TAG, "(" + getEventString(event) + ") delete file, "
                        + file.getAbsolutePath()
                        + " from DB");
                // for(FileInfo f : FileInfoPersistanceHelper.instance().allFiles()) {
                // Log.d(TAG, "File names in DB (" + f.id() + "): " + f.fileName());
                // }
            }
        }

        // notify the LocalStorageManager or other callback listener
        newestFileTime = System.currentTimeMillis();
        if (callback != null) {
            callback.fileUpdateCallback(file, op, newestFileTime);
        } else {
            Log.w(TAG,
                    "FileUpdateCallback is null so we had nowhere to send the update callback");
        }
    }

    private String getEventString(int event) {
        switch (event) {
            case FileObserver.CREATE:
                return "CREATE";
            case FileObserver.MODIFY:
                return "MODIFY";
            case MOVED_TO:
                return "MOVED_TO";
            case CLOSE_WRITE:
                return "CLOSE_WRITE";
            case DELETE:
                return "DELETE";
            case MOVED_FROM:
                return "MOVED_FROM";
        }

        // If it is an event we are not supporting in this observer
        return String.valueOf(event);
    }

    /**
     * Sets a callback that is run when the file is updated
     * @param cb the callback
     */
    public void setCallback(FileUpdateCallback cb) {
        callback = cb;
    }

    /**
     * Returns the recorded time when the file is added or added again
     * @return the time in millis that is recorded by the system clock.
     */
    public long getNewestFileTime() {
        return newestFileTime;
    }

    public String getPath() {
        return absolutePath;
    }

    /**
     * Subclasses may override to provdie a different username
     *
     */
    public String getUserName() {
        return "local";
    }

    @Override
    public void startWatching() {
        if (!IOProviderFactory.isDefault())
            ImportExportMapComponent.getInstance().addImportListener(this);
        super.startWatching();
    }

    @Override
    public void stopWatching() {
        super.stopWatching();
        ImportExportMapComponent.getInstance().removeImportListener(this);
    }
}
