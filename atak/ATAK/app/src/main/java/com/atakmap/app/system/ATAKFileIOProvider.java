
package com.atakmap.app.system;

import android.database.sqlite.SQLiteException;
import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.filesystem.SecureDelete;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.math.MathUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;

final class ATAKFileIOProvider extends IOProvider {

    private final String TAG = "ATAKFileIOProvider";

    /**
     * A human readable name describing this IOProvider.
     *
     * @return the name
     */
    @Override
    public String getName() {
        return "ATAKFileIOProvider";
    }

    /**
     * Returns a well formed input stream implementation that utilizes the file provided.
     *
     * @param f the file to use as the basis for the input stream
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    @Override
    public FileInputStream getInputStream(File f) throws FileNotFoundException {
        return new FileInputStream(f);
    }

    /**
     * Returns a well formed output stream implementation that utilizes the file provided.
     *
     * @param f      the file to use as the basis for the input stream
     * @param append true if file should be appended, false if truncating should occur
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    @Override
    public FileOutputStream getOutputStream(File f, boolean append)
            throws FileNotFoundException {
        return new FileOutputStream(f, append);
    }

    /**
     * Returns a well formed FileWriter implementation that utilizes the file provided
     *
     * @param f the file to use as the basis for this FileWriter
     * @return the file writer based on the file
     * @throws IOException an exception if the FileWriter cannot be created from the provided file.
     */
    @Override
    public FileWriter getFileWriter(File f) throws IOException {
        return new FileWriter(f);
    }

    /**
     * Returns a well formed FileReader implementation that utilizes the file provided
     *
     * @param f the file to use as the basis for this FileReader
     * @return the file reader based on the file
     * @throws IOException an exception if the FileReader cannot be created from the provided file.
     */
    @Override
    public FileReader getFileReader(File f) throws IOException {
        return new FileReader(f);
    }

    /**
     * Returns a well formed RandomAccessFile implementation that utilizes the file provided
     *
     * @param f the file to use as the basis for this RandomAccessFile
     * @return the file reader based on the file
     * @throws FileNotFoundException an exception if the RandomAccessFile cannot be created from the
     *             provided file.
     */
    @Override
    public RandomAccessFile getRandomAccessFile(File f, String mode)
            throws FileNotFoundException {
        return new RandomAccessFile(f, mode);
    }

    /**
     * Renaming a file.
     *
     * @param f1 the source file
     * @param f2 the destination file (to rename the source to)
     * @return true if the rename was successful otherwise false if it failed.
     */
    @Override
    public boolean renameTo(File f1, File f2) {
        return f1.renameTo(f2);
    }

    /**
     * Delete a file.
     *
     * @param f the file to delete
     * @param flag the flags to apply during the deletion.  At this time only
     * IOProvider.SECURE_DELETE is honored.
     * @return true if the deletion was successful otherwise false if it failed.
     */
    @Override
    public boolean delete(File f, int flag) {
        if (flag == SECURE_DELETE)
            return SecureDelete.delete(f);
        return f.delete();
    }

    /**
     * Returns the length of the file.
     *
     * @param f the file
     * @return The length of the file.
     */
    @Override
    public long length(File f) {
        return f.length();
    }

    /**
     * Returns the last modified time for the file, expressed in milliseconds since the Epoch
     * (Midnight, January 1 1970).
     *
     * @return the last modified time for the file, expressed in milliseconds since the Epoch
     *         (Midnight, January 1 1970).
     */
    @Override
    public long lastModified(File f) {
        return f.lastModified();
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     *
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    @Override
    public boolean exists(File f) {
        return f.exists();
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     *
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    @Override
    public boolean isDirectory(File f) {
        return f.isDirectory();
    }

    /**
     * Returns an array of strings naming the files and directories in the directory denoted by this
     * abstract pathname.
     * <P>
     * If this abstract pathname does not denote a directory, then this method returns
     * <code>null</code>. Otherwise an array of strings is returned, one for each file or directory
     * in the directory. Names denoting the directory itself and the directory's parent directory
     * are not included in the result. Each string is a file name rather than a complete path.
     * <P>
     * There is no guarantee that the name strings in the resulting array will appear in any
     * specific order; they are not, in particular, guaranteed to appear in alphabetical order.
     *
     * @param f The file
     * @return an array of strings naming the files and directories in the directory denoted by this
     *         abstract pathname; <code>null</code> if the specified file is not a directory.
     */
    @Override
    public String[] list(File f) {
        return f.list();
    }

    /**
     * Returns an array of strings naming the files and directories in the directory denoted by this
     * abstract pathname that satisfy the specified filter. The behavior of this method is the same
     * as that of the {@link #list(File)} method, except that the strings in the returned array must
     * satisfy the filter. If the given filter is <code>null</code> then all names are accepted.
     * Otherwise, a name satisfies the filter if and only if the value true results when the
     * <code>FilenameFilter.accept(File, String)</code> method of the filter is invoked on this
     * abstract pathname and the name of a file or directory in the directory that it denotes.
     *
     * @param f The file
     * @param filter The filter
     * @return an array of strings naming the files and directories in the directory denoted by this
     *         abstract pathname that match the filter; <code>null</code> if the specified file is
     *         not a directory.
     */
    @Override
    public String[] list(File f, FilenameFilter filter) {
        return f.list(filter);
    }

    /**
     * Creates the directory named by this abstract pathname.
     *
     * @return <code>true</code> if and only if the directory was created; <code>false</code>
     *         otherwise
     */
    @Override
    public boolean mkdir(File f) {
        return f.mkdir();
    }

    /**
     * Creates the directory named by this abstract pathname, including any necessary but
     * nonexistent parent directories. Note that if this operation fails it may have succeeded in
     * creating some of the necessary parent directories.
     *
     * @return <code>true</code> if and only if the directory was created, along with all necessary
     *         parent directories; <code>false</code> otherwise
     */
    @Override
    public boolean mkdirs(File f) {
        return f.mkdirs();
    }

    /**
     * Constructs a URI that represents this abstract pathname. The scheme may be implementation
     * dependent.
     * <P>
     * The exact form of the URI is implementation dependent. If it can be determined that the file
     * denoted by this abstract pathname is a directory, then the resulting URI will end with a
     * slash.
     *
     * @return A URI that represents this abstract pathname.
     */
    @Override
    public URI toURI(File f) {
        return f.toURI();
    }

    @Override
    public boolean canWrite(File f) {
        return f.canWrite();
    }

    @Override
    public boolean canRead(File f) {
        return f.canRead();
    }

    /**
     * Manipulates the write permissions for the abstract path designated by this file. The behavior
     * of this method is the same as that of the {@link #list(File)} method
     *
     * @param f The file
     * @param writable To allow write permission if true, otherwise disallow
     * @param ownerOnly To manipulate write permission only for owner if true, otherwise for
     *            everyone. The manipulation will apply to everyone regardless of this value if the
     *            underlying system does not distinguish owner and other users.
     * @return true if and only if the operation succeeded. If the user does not have permission to
     *         change the access permissions of this abstract pathname the operation will fail.
     */
    @Override
    public boolean setWritable(File f, boolean writable, boolean ownerOnly) {
        return f.setWritable(writable, ownerOnly);
    }

    /**
     * Manipulates the read permissions for the abstract path designated by this file. The behavior
     * of this method is the same as that of the {@link #list(File)} method
     *
     * @param f The file
     * @param readable To allow read permission if true, otherwise disallow
     * @param ownerOnly To manipulate read permission only for owner if true, otherwise for
     *            everyone. The manipulation will apply to everyone regardless of this value if the
     *            underlying system does not distinguish owner and other users.
     * @return true if and only if the operation succeeded. If the user does not have permission to
     *         change the access permissions of this abstract pathname the operation will fail. If
     *         the underlying file system does not support read permission and the value of readable
     *         is false, this operation will fail.
     */
    @Override
    public boolean setReadable(File f, boolean readable, boolean ownerOnly) {
        return f.setReadable(readable, ownerOnly);
    }

    /**
     * Returns the unique FileChannel object associated with this file.
     *
     * @param f The file
     * @return The file channel associated with the file
     */
    @Override
    public FileChannel getChannel(File f, String mode)
            throws FileNotFoundException {
        return (new RandomAccessFile(f, mode)).getChannel();
    }

    @Override
    public File createTempFile(String prefix, String suffix, File directory)
            throws IOException {
        return File.createTempFile(prefix, suffix, directory);
    }

    /**
     * Determines whether database given by its path is a SQLite database
     * @param path the path to database
     * @return true if the given path points to a database; false, otherwise
     */
    @Override
    public boolean isDatabase(File path) {
        return Databases.isSQLiteDatabase(path.getAbsolutePath());
    }

    /**
     * Creates a DatabaseIface from provided DatabaseInformation.
     *
     * @param dbi the key information required for creating a
     * DatabaseIface object.
     *
     * @return  An instance of the DatabaseIface if successfuly,
     *          <code>null</code> otherwise.
     */
    @Override
    public DatabaseIface createDatabase(final DatabaseInformation dbi) {
        if (dbi == null)
            return null;

        final Uri uri = dbi.uri;

        final String scheme = uri.getScheme();

        if (scheme == null || !scheme.equals("file"))
            return null;

        final DatabaseIface db;
        if (MathUtils.hasBits(dbi.options, DatabaseInformation.OPTION_READONLY))
            db = Databases.openDatabase(uri.getPath(), true);
        else
            db = Databases.openOrCreateDatabase(uri.getPath());

        if (MathUtils.hasBits(dbi.options,
                DatabaseInformation.OPTION_RESERVED1)) {
            final AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_APK_DOWNLOADER,
                            "com.atakmap.app.v2");

            if (credentials != null
                    && !FileSystemUtils.isEmpty(credentials.username)) {
                db.execute("PRAGMA key = '" + credentials.password + "'",
                        null);
            } else {
                db.execute("PRAGMA key = '" + "'", null);
            }

            // check to see if the database is valid
            CursorIface ci = null;
            try {
                ci = db.query("SELECT count(*) FROM sqlite_master", null);
                long ret = -1;

                if (ci.moveToNext())
                    ret = ci.getLong(0);
                if (ret == -1) {
                    Log.d(TAG, "database error");
                    try {
                        db.close();
                    } catch (Exception ignored) {
                    }
                    return null;
                }
            } catch (SQLiteException e) {
                Log.d(TAG, "corrupt database", e);
                try {
                    db.close();
                } catch (Exception ignored) {
                }

                return null;
            } finally {
                if (ci != null)
                    ci.close();
            }
        }
        return db;
    }
}
