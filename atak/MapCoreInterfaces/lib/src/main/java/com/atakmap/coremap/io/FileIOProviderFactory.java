
package com.atakmap.coremap.io;

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
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * The Factory to obtain a File IO Provider
 */
public class FileIOProviderFactory {

    /**
     * The Class name
     */
    private static final String TAG = "FileIOProviderFactory";

    /**
     * Holds the list of File IO Providers
     */
    private static final ConcurrentLinkedDeque<FileIOProvider> fileIOProviders = new ConcurrentLinkedDeque<>();

    /**
     * The default unencrypted File IO Provider
     */
    private static final FileIOProvider DEFAULT = new DefaultFileIOProvider();

    /**
     * Adds the default IO provider to the list
     */
    static {
        fileIOProviders.add(DEFAULT);
    }

    /**
     * Registers a new FileIOProvider with the system.
     *
     * @param fileIOProvider the file io provider to register
     */
    public static void registerProvider(
            final FileIOProvider fileIOProvider) {
        fileIOProviders.addFirst(fileIOProvider);
    }

    /**
     * Unregisters a new FileIOProvider with the system.
     *
     * @param fileIOProvider the file io provider to unregister
     */
    public static void unregisterProvider(
            final FileIOProvider fileIOProvider) {
        fileIOProviders.remove(fileIOProvider);
    }

    /**
     * Returns a well formed input stream implementation that utilizes the file provided for the
     * currently active FileIOProvider
     *
     * @param f the file to use as the basis for the input stream
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    public static FileInputStream getInputStream(File f)
            throws FileNotFoundException {
        return fileIOProviders.peekFirst().getInputStream(f);
    }

    /**
     * Returns a well formed output stream implementation that utilizes the file provided for the
     * currently active FileIOProvider
     *
     * @param f the file to use as the basis for the input stream
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    public static FileOutputStream getOutputStream(File f)
            throws FileNotFoundException {
        return fileIOProviders.peekFirst().getOutputStream(f);
    }

    /**
     * Returns a well formed FileWriter implementation that utilizes the file provided for the
     * currently active FileIOProvider
     *
     * @param f the file to use as the basis for this FileWriter
     * @return the file writer based on the file
     * @throws IOException an exception if the FileWriter cannot be created from the provided file.
     */
    public static FileWriter getFileWriter(File f) throws IOException {
        return fileIOProviders.peekFirst().getFileWriter(f);
    }

    /**
     * Returns a well formed FileReader implementation that utilizes the file provided for the
     * currently active FileIOProvider
     *
     * @param f the file to use as the basis for this FileReader
     * @return the file reader based on the file
     * @throws IOException an exception if the FileReader cannot be created from the provided file.
     */
    public static FileReader getFileReader(File f) throws IOException {
        return fileIOProviders.peekFirst().getFileReader(f);
    }

    /**
     * Returns a well formed RandomAccessFile implementation that utilizes the file provided for the
     * currently active FileIOProvider
     *
     * @param f the file to use as the basis for this RandomAccessFile
     * @return the file reader based on the file
     * @throws FileNotFoundException an exception if the RandomAccessFile cannot be created from the
     *             provided file.
     */
    public static RandomAccessFile getRandomAccessFile(File f, String mode)
            throws FileNotFoundException {
        return fileIOProviders.peekFirst().getRandomAccessFile(f, mode);
    }

    /**
     * Renaming a file.
     *
     * @param f1 the source file
     * @param f2 the destination file (to rename the source to)
     * @return true if the rename was successful otherwise false if it failed.
     */
    public static boolean renameTo(File f1, File f2) {
        return fileIOProviders.peekFirst().renameTo(f1, f2);
    }

    /**
     * Delete a file.
     * 
     * @param f the file to delete
     * @return true if the deletion was successful otherwise false if it failed.
     */
    public static boolean delete(File f) {
        return fileIOProviders.peekFirst().delete(f);
    }

    /**
     * Returns the length of the file.
     * 
     * @param f the file
     * @return The length of the file.
     */
    public static long length(File f) {
        return fileIOProviders.peekFirst().length(f);
    }

    /**
     * Returns the last modified time for the file, expressed in milliseconds since the Epoch
     * (Midnight, January 1 1970).
     * 
     * @return the last modified time for the file, expressed in milliseconds since the Epoch
     *         (Midnight, January 1 1970).
     */
    public static long lastModified(File f) {
        return fileIOProviders.peekFirst().lastModified(f);
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     * 
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    public static boolean exists(File f) {
        return fileIOProviders.peekFirst().exists(f);
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     * 
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    public static boolean isDirectory(File f) {
        return fileIOProviders.peekFirst().isDirectory(f);
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
        return fileIOProviders.peekFirst().list(f);
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
        return fileIOProviders.peekFirst().list(f, filter);
    }

    /**
     * Creates the directory named by this abstract pathname.
     * 
     * @return <code>true</code> if and only if the directory was created; <code>false</code>
     *         otherwise
     */
    public static boolean mkdir(File f) {
        return fileIOProviders.peekFirst().mkdir(f);
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
        return fileIOProviders.peekFirst().mkdirs(f);
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
        return fileIOProviders.peekFirst().toURI(f);
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
    public boolean setWritable(File f, boolean writable, boolean ownerOnly) {
        return fileIOProviders.peekFirst().setWritable(f, writable, ownerOnly);
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
    public boolean setReadable(File f, boolean readable, boolean ownerOnly) {
        return fileIOProviders.peekFirst().setReadable(f, readable, ownerOnly);
    }
}
