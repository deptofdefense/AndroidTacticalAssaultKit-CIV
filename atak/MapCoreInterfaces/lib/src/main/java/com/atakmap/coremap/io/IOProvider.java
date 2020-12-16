
package com.atakmap.coremap.io;

import com.atakmap.database.DatabaseIface;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;

/**
 * Abstracted IO Operations
 */
public abstract class IOProvider {
    /*************************************************************************/
    // File System Abstraction

    /**
     * Flag to indicate secure deletion of files
     */
    public static final int SECURE_DELETE = 1;

    /**
     * A human readable name describing this IOProvider.
     * 
     * @return the name
     */
    public abstract String getName();

    /**
     * Returns a well formed input stream implementation that utilizes the file provided.
     * 
     * @param f the file to use as the basis for the input stream
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    public abstract FileInputStream getInputStream(File f)
            throws FileNotFoundException;

    /**
     * Returns a well formed output stream implementation that utilizes the file provided.
     *
     * @param f the file to use as the basis for the input stream
     * @param append true if file should be appended, false if truncating should occur
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    public abstract FileOutputStream getOutputStream(File f, boolean append)
            throws FileNotFoundException;

    /**
     * Returns a well formed FileWriter implementation that utilizes the file provided
     *
     * @param f the file to use as the basis for this FileWriter
     * @return the file writer based on the file
     * @throws IOException an exception if the FileWriter cannot be created from the provided file.
     */
    public abstract FileWriter getFileWriter(File f) throws IOException;

    /**
     * Returns a well formed FileReader implementation that utilizes the file provided
     * 
     * @param f the file to use as the basis for this FileReader
     * @return the file reader based on the file
     * @throws IOException an exception if the FileReader cannot be created from the provided file.
     */
    public abstract FileReader getFileReader(File f) throws IOException;

    /**
     * Returns a well formed RandomAccessFile implementation that utilizes the file provided
     * 
     * @param f the file to use as the basis for this RandomAccessFile
     * @return the file reader based on the file
     * @throws FileNotFoundException an exception if the RandomAccessFile cannot be created from the
     *             provided file.
     */
    public abstract RandomAccessFile getRandomAccessFile(File f, String mode)
            throws FileNotFoundException;

    /**************************************************************************/
    // File API

    /**
     * Renaming a file.
     * 
     * @param f1 the source file
     * @param f2 the destination file (to rename the source to)
     * @return true if the rename was successful otherwise false if it failed.
     */
    public abstract boolean renameTo(File f1, File f2);

    /**
     * Delete a file using a set of flag.
     *
     * @param f the file to delete
     * @param flag the flags to apply during the deletion.  At this time only
     * IOProvider.SECURE_DELETE is honored.
     * @return true if the deletion was successful otherwise false if it failed.
     */
    public abstract boolean delete(File f, int flag);

    /**
     * Returns the length of the file.
     * 
     * @param f the file
     * @return The length of the file.
     */
    public abstract long length(File f);

    /**
     * Returns the last modified time for the file, expressed in milliseconds since the Epoch
     * (Midnight, January 1 1970).
     * 
     * @return the last modified time for the file, expressed in milliseconds since the Epoch
     *         (Midnight, January 1 1970).
     */
    public abstract long lastModified(File f);

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     * 
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    public abstract boolean exists(File f);

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     * 
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    public abstract boolean isDirectory(File f);

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
    public abstract String[] list(File f);

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
    public abstract String[] list(File f, FilenameFilter filter);

    /**
     * Creates the directory named by this abstract pathname.
     * 
     * @return <code>true</code> if and only if the directory was created; <code>false</code>
     *         otherwise
     */
    public abstract boolean mkdir(File f);

    /**
     * Creates the directory named by this abstract pathname, including any necessary but
     * nonexistent parent directories. Note that if this operation fails it may have succeeded in
     * creating some of the necessary parent directories.
     *
     * @return <code>true</code> if and only if the directory was created, along with all necessary
     *         parent directories; <code>false</code> otherwise
     */
    public abstract boolean mkdirs(File f);

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
    public abstract URI toURI(File f);

    public abstract boolean canWrite(File f);

    public abstract boolean canRead(File f);

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
    public abstract boolean setWritable(File f, boolean writable,
            boolean ownerOnly);

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
    public abstract boolean setReadable(File f, boolean readable,
            boolean ownerOnly);

    /**
     * Returns the unique FileChannel object associated with this file.
     * 
     * @param f The file
     * @return The file channel associated with the file
     * @throws FileNotFoundException an exception if the RandomAccessFile cannot be created from the
     *             provided file.
     */
    public abstract FileChannel getChannel(File f, String mode)
            throws FileNotFoundException;

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
    public abstract File createTempFile(String prefix, String suffix,
            File directory)
            throws IOException;

    /*************************************************************************/
    // Database Abstraction

    /**
     * Opens/creates the database desribed by the specified {@link DatabaseInformation}.
     *
     * @param info  Describes the database to be opened/created; may provide optional hints
     * @return  The database instance or <code>null</code> if no database could be opened
     */
    public abstract DatabaseIface createDatabase(DatabaseInformation info);

    /**
     * Determines whether database given by its path is a SQLite database
     * @param path the path to database
     * @return true if the given path points to a database; false, otherwise
     */
    public abstract boolean isDatabase(File path);
}
