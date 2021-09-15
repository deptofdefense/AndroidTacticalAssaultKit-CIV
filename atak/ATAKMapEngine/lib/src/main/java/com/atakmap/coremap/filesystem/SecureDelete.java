
package com.atakmap.coremap.filesystem;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Secure deletion manager
 */
public class SecureDelete {

    private static final String TAG = "SecureDelete";

    private static final ExecutorService deleteQueue = Executors
            .newFixedThreadPool(10, new NamedThreadFactory(
                    "DeletePool"));
    private static final ArrayList<String> deleteFiles = new ArrayList<>();

    /**
     * Add file to Secure Delete queue and begin delete on a separate thread
     * The result of the normal deletion is returned synchronously while the
     * result of the data overwrite process is returned in a callback event
     *
     * @param file The file to delete
     * @param event Callback event on secure delete completion (default: null)
     * @return True if the file is deleted
     */
    public static boolean delete(File file, SecureDeleteEvent event) {

        // Checks
        if (file == null) {
            Log.w(TAG, "Cannot delete null file.");
            return false;
        } else if (!file.exists()) {
            Log.w(TAG, "Does not exist: " + file.getAbsolutePath());
            return false;
        } else if (!file.canWrite()) {
            Log.w(TAG, "Cannot write to " + file.getAbsolutePath());
            return false;
        }

        // Directory or empty file = normal behavior
        long fileLength = file.length();
        if (file.isDirectory() || fileLength == 0)
            return file.delete();

        String filePath = file.getAbsolutePath();
        try {
            // Open file output stream
            FileOutputStream fos = new FileOutputStream(
                    FileSystemUtils.sanitizeWithSpacesAndSlashes(filePath));

            // Delete the file normally
            if (file.delete()) {
                // Add file and stream to new thread
                SecureDeleteThread del = new SecureDeleteThread(file,
                        fileLength, fos, event);
                addToList(del.getUID());
                deleteQueue.submit(del);
                return true;
            } else {
                Log.w(TAG, "Failed to delete: " + filePath);
                fos.close();
            }
        } catch (Exception e) {
            String msg = e.getMessage();

            // Too many open files error
            // Occasionally occurs when deleting DTED
            if (msg.contains("Too many open files")) {
                try {
                    // Sleep a bit and try again
                    Log.w(TAG,
                            "Too many open files. Trying again after 500ms sleep...");
                    Thread.sleep(500);
                    return delete(file, event);
                } catch (Exception e2) {
                    Log.d(TAG, e2.getMessage());
                }
            } else
                Log.e(TAG, msg);
        }
        return false;
    }

    public static boolean delete(File file) {
        return delete(file, null);
    }

    public synchronized static void addToList(String uid) {
        deleteFiles.add(uid);
    }

    /**
     * Remove file from deleted files queue
     *
     * @param uid Deleted file UID
     */
    public synchronized static void checkAndRemoveFromList(final String uid) {
        deleteFiles.remove(uid);
    }

    /**
     * Return remaining pending file deletions
     */
    public synchronized static int getRemaining() {
        return deleteFiles.size();
    }

    /**
     * Delete directory and containing files
     *
     * @param dir Directory to delete
     * @param contentsOnly Only delete files (default: false)
     * @param recursive Delete sub-folders (default: true)
     * @return True if everything was deleted successfully
     */
    public static boolean deleteDirectory(File dir, boolean contentsOnly,
            boolean recursive) {
        return deleteDirectory(dir, contentsOnly, recursive, null);
    }

    /**
     * Delete directory and containing files
     *
     * @param dir Directory to delete
     * @param contentsOnly Only delete files (default: false)
     * @param recursive Delete sub-folders (default: true)
     * @param ignore   [optional] filter to ignore/skip, not delete matching files
     * @return True if everything was deleted successfully
     */
    public static boolean deleteDirectory(File dir, boolean contentsOnly,
            boolean recursive, FilenameFilter ignore) {
        if (dir == null || !dir.exists()) {
            Log.w(TAG, "Directory does not exist: "
                    + (dir == null ? "null" : dir.getAbsolutePath()));
            return false;
        }
        if (!dir.isDirectory()) {
            Log.w(TAG, "Expected directory: " + dir.getAbsolutePath());
            return false;
        }
        if (!dir.canWrite()) {
            Log.w(TAG, "Cannot write/delete directory: "
                    + dir.getAbsolutePath());
            return false;
        }

        File[] all = dir.listFiles();
        boolean result = true;
        if (all != null && all.length > 0) {
            for (File file : all) {
                if (file == null || !file.exists())
                    continue;

                if (file.isDirectory()) {
                    if (recursive)
                        result = result
                                && deleteDirectory(file, contentsOnly, true,
                                        ignore);
                } else {
                    if (ignore != null
                            && ignore.accept(dir, file.getAbsolutePath())) {
                        Log.d(TAG, "Skipping file: " + file);
                        continue;
                    }

                    result = result && delete(file);
                }
            }
        }
        if (!contentsOnly)
            result = result && dir.delete();
        return result;
    }

    public static boolean deleteDirectory(File dir, boolean contentsOnly) {
        return deleteDirectory(dir, contentsOnly, true);
    }

    public static boolean deleteDirectory(File dir) {
        return deleteDirectory(dir, false, true);
    }

    public static boolean deleteDirectory(File dir, FilenameFilter ignore) {
        return deleteDirectory(dir, true, true, ignore);
    }

    /**
     * Securely erase a database
     *
     * @param dbHandle SQLite database handle
     * @return True if database file was deleted
     */
    public static boolean deleteDatabase(final SQLiteOpenHelper dbHandle) {
        SQLiteDatabase db = dbHandle.getReadableDatabase();
        String dbPath = null;
        if (db != null) {
            dbPath = db.getPath();
            db.close();
        }
        dbHandle.close();
        return dbPath != null && delete(new File(dbPath));
    }
}
