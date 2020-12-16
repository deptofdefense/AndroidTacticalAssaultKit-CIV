
package com.atakmap.coremap.io;

import android.net.Uri;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.interop.Pointer;
import com.atakmap.io.ZipVirtualFile;

import java.io.File;
import java.io.FileFilter;
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
import java.util.ArrayList;

/**
 * The Factory to obtain a File IO Provider
 */
public class IOProviderFactory {

    /**
     * The Class name
     */
    private static final String TAG = "IOProviderFactory";

    /**
     * Holds the list of native database IO Providers
     */
    private static long nativeProxy;

    /**
     * The default unencrypted File IO Provider
     */
    private static final IOProvider DEFAULT = new DefaultIOProvider();

    /**
     * State of the current IOProviderFactory.
     */
    private static IOProvider ioProvider = DEFAULT;
    private static boolean markedAsDefault = true;
    private static boolean registered = false;

    /**
     * If the IOProviderFactory has been notified that the Application is closing, do not
     * notify users of the factory that any providers have changed.     Notification of provider
     * changes should only happen when the provider is loaded or unloaded.
     */
    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    public static synchronized void notifyOnDestroy() {
    }

    /**
     * Registers a new IOProvider with the system.
     *
     * @param provider the file io provider to register
     */
    public synchronized static void registerProvider(
            final IOProvider provider) {
        registerProvider(provider, false);
    }

    /**
     * Registers a new IOProvider with the system.
     *
     * @param provider the file io provider to register
     */
    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    public synchronized static void registerProvider(
            final IOProvider provider, boolean def) {

        if (registered)
            return;
        registered = true;

        ioProvider = provider;
        markedAsDefault = def;

        if (markedAsDefault && nativeProxy != 0L) {
            // the registered provider is marked as default and native is
            // currently proxying through Java. Uninstall the proxy.
            uninstallFactory(nativeProxy);
            nativeProxy = 0L;
        } else if (!markedAsDefault && nativeProxy == 0L) {
            // the registered provider is marked as non-default and native is
            // not currently proxying through Java. Install a proxy.
            nativeProxy = installFactory();
        }
    }

    /**
     * Unregisters a new IOProvider with the system.
     *
     * @param provider the io provider to unregister
     */
    @Deprecated
    @DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
    public synchronized static void unregisterProvider(
            final IOProvider provider) {
    }

    /**
     * Returns <code>true</code> if the current provider is considered
     * default, <code>false</code> otherwise.
     *
     * @return true if the current provider is marked as default.
     */
    public synchronized static boolean isDefault() {
        return markedAsDefault;
    }

    /**
     * Returns the current IO Provider instance.
     *
     * @return the provider that is currently registered.
     */
    public synchronized static IOProvider getProvider() {
        return ioProvider;
    }

    /*************************************************************************/
    // Filesystem Abstraction

    /**
     * Returns a well formed input stream implementation that utilizes the file provided for the
     * currently active IOProvider
     *
     * @param f the file to use as the basis for the input stream
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    public static FileInputStream getInputStream(File f)
            throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException(
                    "Unable to get Input Stream for a Zip Virtual File.");
        } else {
            return ioProvider.getInputStream(f);
        }
    }

    /**
     * Returns a well formed output stream implementation that utilizes the file provided for the
     * currently active IOProvider
     *
     * @param f the file to use as the basis for the input stream
     * @return the input stream based on the file
     * @throws IOException if the parent directory is missing and cannot be created, or if the given
     *                     file is an instance of ZipVirtualFile, or if the file cannot be found.
     */
    public static FileOutputStream getOutputStream(File f) throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException(
                    "Unable to get Output Stream for a Zip Virtual File.");
        } else {
            makeParentDirIfNeeded(f);
            return ioProvider.getOutputStream(f, false);
        }
    }

    /**
     * Returns a well formed output stream implementation that utilizes the file provided.
     *
     * @param f      the file to use as the basis for the input stream
     * @param append true if file should be appended, false if truncating should occur
     * @return the input stream based on the file
     * @throws IOException if the parent directory is missing and cannot be created, or if the given
     *                     file is an instance of ZipVirtualFile, or if the file cannot be found.
     */
    public static FileOutputStream getOutputStream(File f, boolean append)
            throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException(
                    "Unable to get Output Stream for a Zip Virtual File.");
        } else {
            makeParentDirIfNeeded(f);
            return ioProvider.getOutputStream(f, append);
        }
    }

    /***
     * Checks if the directory referenced by the provided File exists and creates it if it does not.
     * @param f The file that needs to have an existing parent directory.
     * @throws IOException if the directory cannot be created.
     */
    private static void makeParentDirIfNeeded(File f) throws IOException {
        File parent = f.getParentFile();
        String pDir = parent.getPath();
        if (!exists(parent)) {
            if (mkdirs(parent)) {
                Log.w(TAG, "The " + pDir
                        + " directory was missing and had to be created.");
            } else {
                throw new IOException(
                        "Unable to create the " + pDir + " directory.");
            }
        }
    }

    /**
     * Returns a well formed FileWriter implementation that utilizes the file provided for the
     * currently active IOProvider
     *
     * @param f the file to use as the basis for this FileWriter
     * @return the file writer based on the file
     * @throws IOException if the parent directory is missing and cannot be created, or if the given
     *                     file is an instance of ZipVirtualFile, or if the file cannot be found.
     */
    public static FileWriter getFileWriter(File f) throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException(
                    "Unable to get File Writer for a Zip Virtual File.");
        } else {
            makeParentDirIfNeeded(f);
            return ioProvider.getFileWriter(f);
        }
    }

    /**
     * Returns a well formed FileReader implementation that utilizes the file provided for the
     * currently active IOProvider
     *
     * @param f the file to use as the basis for this FileReader
     * @return the file reader based on the file
     * @throws IOException an exception if the FileReader cannot be created from the provided file.
     */
    public static FileReader getFileReader(File f) throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException(
                    "Unable to get File Reader for a Zip Virtual File.");
        } else {
            return ioProvider.getFileReader(f);
        }
    }

    /**
     * Returns a well formed RandomAccessFile implementation that utilizes the file provided for the
     * currently active IOProvider
     *
     * @param f the file to use as the basis for this RandomAccessFile
     * @return the file reader based on the file
     * @throws FileNotFoundException an exception if the RandomAccessFile cannot be created from the
     *             provided file.
     */
    public static RandomAccessFile getRandomAccessFile(File f, String mode)
            throws FileNotFoundException {
        if (f instanceof ZipVirtualFile) {
            return new RandomAccessFile(f, mode);
        } else {
            return ioProvider.getRandomAccessFile(f, mode);
        }
    }

    /**
     * Renaming a file.
     *
     * @param f1 the source file
     * @param f2 the destination file (to rename the source to)
     * @return true if the rename was successful otherwise false if it failed.
     */
    public static boolean renameTo(File f1, File f2) {
        if (f1 instanceof ZipVirtualFile) {
            return f1.renameTo(f2);
        } else {
            return ioProvider.renameTo(f1, f2);
        }
    }

    /**
     * Delete a file.
     *
     * @param f the file to delete
     * @return true if the deletion was successful otherwise false if it failed.
     */
    public static boolean delete(File f) {
        //Passing 0 in as the absence of special handling flags
        return delete(f, 0);
    }

    /**
     * Delete a file.
     *
     * @param f the file to delete
     * @param flag the flags to apply during the deletion.  At this time only
     * IOProvider.SECURE_DELETE is honored.
     * @return true if the deletion was successful otherwise false if it failed.
     */
    public static boolean delete(File f, int flag) {
        if (f instanceof ZipVirtualFile) {
            return f.delete();
        } else {
            return ioProvider.delete(f, flag);
        }
    }

    /**
     * Returns the length of the file.
     * 
     * @param f the file
     * @return The length of the file.
     */
    public static long length(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.length();
        } else {
            return ioProvider.length(f);
        }
    }

    /**
     * Returns the last modified time for the file, expressed in milliseconds since the Epoch
     * (Midnight, January 1 1970).
     * 
     * @return the last modified time for the file, expressed in milliseconds since the Epoch
     *         (Midnight, January 1 1970).
     */
    public static long lastModified(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.lastModified();
        } else {
            return ioProvider.lastModified(f);
        }
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     * 
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    public static boolean exists(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.exists();
        } else {
            return ioProvider.exists(f);
        }
    }

    public static boolean isFile(File f) {
        return exists(f) && !isDirectory(f);
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     * 
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    public static boolean isDirectory(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.isDirectory();
        } else {
            return ioProvider.isDirectory(f);
        }
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
    public static String[] list(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.list();
        } else {
            return ioProvider.list(f);
        }
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
    public static String[] list(File f, FilenameFilter filter) {
        if (f instanceof ZipVirtualFile) {
            return f.list(filter);
        } else {
            return ioProvider.list(f, filter);
        }
    }

    /**
     * Converts a String[] containing filenames to a File[].
     * Note that the filenames must not contain slashes.
     * This method is to remove duplication in the implementation
     * of File.list's overloads.
     * @param dir the directory to grab files from
     * @param filenames The names of files to pull
     * @return an array of files or {@code null}.
     */
    protected static File[] filenamesToFiles(File dir, String[] filenames) {
        if (filenames == null)
            return null;
        File[] files = new File[filenames.length];
        for (int i = 0; i < filenames.length; i++) {
            files[i] = new File(dir, filenames[i]);
        }
        return files;
    }

    /**
     * Returns an array of files contained in the directory represented by this
     * file. The result is {@code null} if this file is not a directory. The
     * paths of the files in the array are absolute if the path of this file is
     * absolute, they are relative otherwise.
     *
     * @param f The file
     * @return an array of files or {@code null}.
     */
    public static File[] listFiles(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.listFiles();
        } else {
            String[] children = ioProvider.list(f);
            return filenamesToFiles(f, children);
        }
    }

    /**
     * Gets a list of the files in the directory represented by this file. This
     * list is then filtered through a FilenameFilter and files with matching
     * names are returned as an array of files. Returns {@code null} if this
     * file is not a directory. If {@code filter} is {@code null} then all
     * filenames match.
     * <p>
     * The entries {@code .} and {@code ..} representing the current and parent
     * directories are not returned as part of the list.
     *
     * @param f The file
     * @param filter The filter to match names against, may be {@code null}.
     * @return an array of files or {@code null}.
     */
    public static File[] listFiles(File f, FilenameFilter filter) {
        if (f instanceof ZipVirtualFile) {
            return f.listFiles(filter);
        } else {
            String[] children = ioProvider.list(f, filter);
            return filenamesToFiles(f, children);
        }
    }

    /**
     * Returns an array of abstract pathnames denoting the files and
     * directories in the directory denoted by this abstract pathname that
     * satisfy the specified filter.  The behavior of this method is the same
     * as that of the listFiles method, except that the pathnames in
     * the returned array must satisfy the filter.  If the given {@code filter}
     * is {@code null} then all pathnames are accepted.  Otherwise, a pathname
     * satisfies the filter if and only if the value {@code true} results when
     * the {@link FileFilter#accept FileFilter.accept(File)} method of the
     * filter is invoked on the pathname.
     *
     * @param  f The file
     * @param  filter A file filter
     *
     * @return  An array of abstract pathnames denoting the files and
     *          directories in the directory denoted by this abstract pathname.
     *          The array will be empty if the directory is empty.  Returns
     *          {@code null} if this abstract pathname does not denote a
     *          directory, or if an I/O error occurs.
     *
     * @throws  SecurityException
     *          If a security manager exists and its {@link
     *          SecurityManager#checkRead(String)} method denies read access to
     *          the directory
     */
    public static File[] listFiles(File f, FileFilter filter) {
        if (f instanceof ZipVirtualFile) {
            return f.listFiles(filter);
        } else {
            String[] children = ioProvider.list(f);
            if (children == null)
                return null;
            ArrayList<File> childFiles = new ArrayList<>();
            for (String child : children) {
                File childFile = new File(f, child);
                if ((filter == null) || filter.accept(childFile))
                    childFiles.add(childFile);
            }
            return childFiles.toArray(new File[0]);
        }
    }

    /**
     * Creates the directory named by this abstract pathname.
     * 
     * @return <code>true</code> if and only if the directory was created; <code>false</code>
     *         otherwise
     */
    public static boolean mkdir(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.mkdir();
        } else {
            return ioProvider.mkdir(f);
        }
    }

    /**
     * Creates the directory named by this abstract pathname, including any necessary but
     * nonexistent parent directories. Note that if this operation fails it may have succeeded in
     * creating some of the necessary parent directories.
     *
     * @return <code>true</code> if and only if the directory was created, along with all necessary
     *         parent directories; <code>false</code> otherwise
     */
    public static boolean mkdirs(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.mkdirs();
        } else {
            return ioProvider.mkdirs(f);
        }
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
    public static URI toURI(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.toURI();
        } else {
            return ioProvider.toURI(f);
        }
    }

    public static boolean canRead(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.canRead();
        } else {
            return ioProvider.canRead(f);
        }
    }

    public static boolean canWrite(File f) {
        if (f instanceof ZipVirtualFile) {
            return f.canWrite();
        } else {
            return ioProvider.canWrite(f);
        }
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
    public static boolean setWritable(File f, boolean writable,
            boolean ownerOnly) {
        if (f instanceof ZipVirtualFile) {
            return f.setWritable(writable, ownerOnly);
        } else {
            return ioProvider.setWritable(f, writable, ownerOnly);
        }
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
    public static boolean setReadable(File f, boolean readable,
            boolean ownerOnly) {
        if (f instanceof ZipVirtualFile) {
            return f.setReadable(readable, ownerOnly);
        } else {
            return ioProvider.setReadable(f, readable, ownerOnly);
        }
    }

    /**
     * Returns the unique FileChannel object associated with this file.
     *
     * @param file The file
     * @return The file channel associated with the file
     */
    public static FileChannel getChannel(File file, String mode)
            throws FileNotFoundException {
        return ioProvider.getChannel(file, mode);
    }

    /**
     * Creates a new file.
     * @param f The file to be created
     * @return true if the file is create, false otherwise
     */
    public static boolean createNewFile(File f) {
        if (exists(f))
            return false;
        try (FileOutputStream s = getOutputStream(f)) {
            // opening the output stream will create a new, zero length file
            return true;
        } catch (IOException e) {
            Log.e(TAG, "error creating " + f.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Creates a new empty file in the specified directory, using the given
     * prefix and suffix strings to generate its name. If this method returns
     * successfully then it is guaranteed that:
     * <OL>
     *     <LI>The file denoted by the returned abstract pathname did not exist
     *     before this method was invoked, and
     *     <LI>Neither this method nor any of its variants will return the same
     *     abstract pathname again in the current invocation of the virtual
     *     machine.
     * </LI>
     * The prefix argument must be at least three characters long. It is
     * recommended that the prefix be a short, meaningful string such as "hjb"
     * or "mail". The suffix argument may be null, in which case the suffix
     * ".tmp" will be used.
     *
     * <P>To create the new file, the prefix and the suffix may first be
     * adjusted to fit the limitations of the underlying platform. If the
     * prefix is too long then it will be truncated, but its first three
     * characters will always be preserved. If the suffix is too long then it
     * too will be truncated, but if it begins with a period character ('.')
     * then the period and the first three characters following it will
     * always be preserved. Once these adjustments have been made the name
     * of the new file will be generated by concatenating the prefix, five
     * or more internally-generated characters, and the suffix.
     *
     * <P>If the directory argument is <code>null</code> then the
     * system-dependent default temporary-file directory will be used. The
     * default temporary-file directory is specified by the system property
     * java.io.tmpdir. On UNIX systems the default value of this property
     * is typically "/tmp" or "/var/tmp"; on Microsoft Windows systems it
     * is typically "C:\\WINNT\\TEMP". A different value may be given to
     * this system property when the Java virtual machine is invoked, but
     * programmatic changes to this property are not guaranteed to have
     * any effect upon the temporary directory used by this method.
     *
     * @param prefix    The prefix string to be used in generating the file's
     *                  name; must be at least three characters long
     * @param suffix    The suffix string to be used in generating the file's
     *                  name; may be null, in which case the suffix ".tmp" will
     *                  be used
     * @param directory The directory in which the file is to be created, or
     *                  <code>null</code> if the default temporary-file
     *                  directory is to be used
     *
     * @return  An abstract pathname denoting a newly-created empty file
     */
    public static File createTempFile(String prefix, String suffix,
            File directory)
            throws IOException {
        return ioProvider.createTempFile(prefix, suffix, directory);
    }

    /*************************************************************************/
    // Database Abstraction

    /**
     * Creates or opens a database connection at the specified file. If
     * <code>null</code> is specified, an in-memory database is created.
     *
     * @param file  The file, may be <code>null</code> for memory DB
     * @return  The database connection or <code>null</code> if non could be
     *          created for the specified file.
     */
    public static DatabaseIface createDatabase(File file) {
        final DatabaseInformation info;
        if (file == null)
            info = new DatabaseInformation(
                    Uri.parse(DatabaseInformation.SCHEME_MEMORY + "://"));
        else
            info = new DatabaseInformation(Uri.fromFile(file));
        return createDatabase(info);
    }

    /**
     * Creates or opens a database connection at the specified file. If
     * <code>null</code> is specified, an in-memory database is created.
     *
     * @param file      The file, may be <code>null</code> for memory DB
     * @param options   The options to be used when creating/opening
     * @return  The database connection or <code>null</code> if non could be
     *          created for the specified file.
     */
    public static DatabaseIface createDatabase(File file, int options) {
        final DatabaseInformation info;
        if (file == null)
            info = new DatabaseInformation(
                    Uri.parse(DatabaseInformation.SCHEME_MEMORY + "://"));
        else
            info = new DatabaseInformation(Uri.fromFile(file), options);
        return createDatabase(info);
    }

    /**
     * Opens/creates the database desribed by the specified {@link DatabaseInformation}.
     *
     * @param info  Describes the database to be opened/created; may provide optional hints
     * @return  The database instance or <code>null</code> if no database could be opened
     */
    public static DatabaseIface createDatabase(DatabaseInformation info) {
        if (DatabaseInformation.isMemoryDatabase(info))
            return Databases.openOrCreateDatabase(null);

        // if the uri is null or the path is null, return null
        if (info.uri == null || info.uri.getPath() == null)
            return null;

        if ((info.options
                & DatabaseInformation.OPTION_ENSURE_PARENT_DIRS) == DatabaseInformation.OPTION_ENSURE_PARENT_DIRS
                &&
                !exists(new File(info.uri.getPath()).getParentFile())) {

            mkdirs(new File(info.uri.getPath()).getParentFile());
        }

        return ioProvider.createDatabase(info);
    }

    /**
     * Determines whether database given by its path is a SQLite database
     * @param path the path to database
     * @return true if the given path points to a database; false, otherwise
     */
    public static boolean isDatabase(File path) {
        return ioProvider.isDatabase(path);
    }

    /**
     * Installs the Java `IOProviderFactory` with the native framework
     *
     * @return  The raw pointer for the constructed native proxy
     */
    static native long installFactory();

    /**
     * @param pointer   The raw pointer for the native proxy created during a
     *                  previous call to `installFactory`. Should be considered
     *                  invalid when this method returns.
     */
    static native void uninstallFactory(long pointer);

    /**
     * Enables roundtrip testing with native interface
     *
     * @param path the path to create the database provider
     * @return the raw pointer to the provider.
     */
    static native Pointer DatabaseProvider_create(String path);
}
