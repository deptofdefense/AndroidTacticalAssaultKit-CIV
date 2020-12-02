
package com.atakmap.coremap.io;

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
            throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException("Unable to get Input Stream for a Zip Virtual File.");
        } else {
            return fileIOProviders.peekFirst().getInputStream(f);
        }
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
            throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException("Unable to get Output Stream for a Zip Virtual File.");
        } else {
            return fileIOProviders.peekFirst().getOutputStream(f, false);
        }
    }

    /**
     * Returns a well formed output stream implementation that utilizes the file provided.
     *
     * @param f      the file to use as the basis for the input stream
     * @param append true if file should be appended, false if truncating should occur
     * @return the input stream based on the file
     * @throws FileNotFoundException an exception if the file is not found.
     */
    public static FileOutputStream getOutputStream(File f, boolean append) throws IOException {
        if (f instanceof ZipVirtualFile) {
            throw new IOException("Unable to get Output Stream for a Zip Virtual File.");
        } else {
            return fileIOProviders.peekFirst().getOutputStream(f, append);
        }
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
        if (f instanceof ZipVirtualFile) {
            throw new IOException("Unable to get File Writer for a Zip Virtual File.");
        } else {
            return fileIOProviders.peekFirst().getFileWriter(f);
        }
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
        if (f instanceof ZipVirtualFile) {
            throw new IOException("Unable to get File Reader for a Zip Virtual File.");
        } else {
            return fileIOProviders.peekFirst().getFileReader(f);
        }
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
        if (f instanceof ZipVirtualFile) {
            return new RandomAccessFile(f, mode);
        } else {
            return fileIOProviders.peekFirst().getRandomAccessFile(f, mode);
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
            return fileIOProviders.peekFirst().renameTo(f1, f2);
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
     * FileIOProvider.SECURE_DELETE is honored.
     * @return true if the deletion was successful otherwise false if it failed.
     */
    public static boolean delete(File f, int flag) {
        if (f instanceof ZipVirtualFile) {
            return f.delete();
        } else {
            return fileIOProviders.peekFirst().delete(f, flag);
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
            return fileIOProviders.peekFirst().length(f);
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
            return fileIOProviders.peekFirst().lastModified(f);
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
            return fileIOProviders.peekFirst().exists(f);
        }
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
            return fileIOProviders.peekFirst().isDirectory(f);
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
            return fileIOProviders.peekFirst().list(f);
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
            return fileIOProviders.peekFirst().list(f, filter);
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
        if(filenames == null)
            return null;
        File[] files = new File[filenames.length];
        for(int i = 0; i < filenames.length; i++) {
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
            String[] children = fileIOProviders.peekFirst().list(f);
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
    public static File[] listFiles(File f, FilenameFilter filter){
        if (f instanceof ZipVirtualFile) {
            return f.listFiles(filter);
        } else {
            String[] children = fileIOProviders.peekFirst().list(f, filter);
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
            String children[] = fileIOProviders.peekFirst().list(f);
            if (children == null) return null;
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
            return fileIOProviders.peekFirst().mkdir(f);
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
            return fileIOProviders.peekFirst().mkdirs(f);
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
            return fileIOProviders.peekFirst().toURI(f);
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
    public static boolean setWritable(File f, boolean writable, boolean ownerOnly) {
        if (f instanceof ZipVirtualFile) {
            return f.setWritable(writable, ownerOnly);
        } else {
            return fileIOProviders.peekFirst().setWritable(f, writable, ownerOnly);
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
    public static boolean setReadable(File f, boolean readable, boolean ownerOnly) {
        if (f instanceof ZipVirtualFile) {
            return f.setReadable(readable, ownerOnly);
        } else {
            return fileIOProviders.peekFirst().setReadable(f, readable, ownerOnly);
        }
    }

    /**
     * Returns the unique FileChannel object associated with this file.
     *
     * @param file The file
     * @return The file channel associated with the file
     */
    public static FileChannel getChannel(File file, String mode) throws FileNotFoundException {
        return fileIOProviders.peekFirst().getChannel(file, mode);
    }
}
