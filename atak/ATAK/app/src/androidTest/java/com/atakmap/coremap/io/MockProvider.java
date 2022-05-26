
package com.atakmap.coremap.io;

import com.atakmap.database.DatabaseIface;

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

public class MockProvider extends IOProvider {
    final String name;
    final File basedir;

    public MockProvider(String name, File basedir) {
        this.name = name;
        if (!basedir.getAbsolutePath().endsWith("/"))
            basedir = new File(basedir.getAbsolutePath() + "/");
        this.basedir = basedir;
    }

    private File resolve(File f, boolean ensure) {

        // very simple implementation that just creates the file in the /sdcard/encrypted
        // directory.
        File nf = new File(basedir, f.getAbsolutePath());
        if (nf.getParentFile() != null && !nf.getParentFile().exists()
                && ensure)
            nf.getParentFile().mkdirs();
        return nf;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FileInputStream getInputStream(File f)
            throws FileNotFoundException {
        return new FileInputStream(resolve(f, true));
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
        return new FileOutputStream(resolve(f, true), append);
    }

    @Override
    public FileWriter getFileWriter(File f) throws IOException {
        return new FileWriter(resolve(f, true));
    }

    @Override
    public FileReader getFileReader(File f) throws IOException {
        return new FileReader(resolve(f, true));
    }

    @Override
    public RandomAccessFile getRandomAccessFile(File f, String mode)
            throws FileNotFoundException {
        return new RandomAccessFile(resolve(f, true), mode);
    }

    @Override
    public boolean renameTo(File f1, File f2) {
        return resolve(f1, false).renameTo(resolve(f2, true));
    }

    @Override
    public boolean delete(File f, int flag) {
        return resolve(f, true).delete();
    }

    /**
     * Returns the length of the file.
     *
     * @param f the file
     * @return The length of the file.
     */
    @Override
    public long length(File f) {
        return resolve(f, false).length();
    }

    /**
     * Returns the last modified time for the file, expressed in milliseconds since the
     * Epoch (Midnight, January 1 1970).
     *
     * @param f
     * @return the last modified time for the file, expressed in milliseconds since the
     *         Epoch (Midnight, January 1 1970).
     */
    @Override
    public long lastModified(File f) {
        return resolve(f, false).lastModified();
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     *
     * @param f
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    @Override
    public boolean exists(File f) {
        return resolve(f, false).exists();
    }

    /**
     * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
     *
     * @param f
     * @return <code>true</code> if the file exists, <code>false</code> otherwise.
     */
    @Override
    public boolean isDirectory(File f) {
        return resolve(f, false).isDirectory();
    }

    /**
     * Returns an array of strings naming the files and directories in the directory denoted
     * by this abstract pathname.
     * <P>
     * If this abstract pathname does not denote a directory, then this method returns
     * <code>null</code>. Otherwise an array of strings is returned, one for each file or
     * directory in the directory. Names denoting the directory itself and the directory's
     * parent directory are not included in the result. Each string is a file name rather
     * than a complete path.
     * <P>
     * There is no guarantee that the name strings in the resulting array will appear in any
     * specific order; they are not, in particular, guaranteed to appear in alphabetical
     * order.
     *
     * @param f The file
     * @return an array of strings naming the files and directories in the directory denoted
     *         by this abstract pathname; <code>null</code> if the specified file is not a
     *         directory.
     */
    @Override
    public String[] list(File f) {
        return resolve(f, false).list();
    }

    /**
     * Returns an array of strings naming the files and directories in the directory denoted
     * by this abstract pathname that satisfy the specified filter. The behavior of this
     * method is the same as that of the {@link #list(File)} method, except that the strings
     * in the returned array must satisfy the filter. If the given filter is
     * <code>null</code> then all names are accepted. Otherwise, a name satisfies the filter
     * if and only if the value true results when the
     * <code>FilenameFilter.accept(File, String)</code> method of the filter is invoked on
     * this abstract pathname and the name of a file or directory in the directory that it
     * denotes.
     *
     * @param f The file
     * @param filter The filter
     * @return an array of strings naming the files and directories in the directory denoted
     *         by this abstract pathname that match the filter; <code>null</code> if the
     *         specified file is not a directory.
     */
    @Override
    public String[] list(File f, FilenameFilter filter) {
        return resolve(f, false).list(filter);
    }

    /**
     * Creates the directory named by this abstract pathname.
     *
     * @param f
     * @return <code>true</code> if and only if the directory was created;
     *         <code>false</code> otherwise
     */
    @Override
    public boolean mkdir(File f) {
        return resolve(f, false).mkdir();
    }

    /**
     * Creates the directory named by this abstract pathname, including any necessary but
     * nonexistent parent directories. Note that if this operation fails it may have
     * succeeded in creating some of the necessary parent directories.
     *
     * @param f
     * @return <code>true</code> if and only if the directory was created, along with all
     *         necessary parent directories; <code>false</code> otherwise
     */
    @Override
    public boolean mkdirs(File f) {
        return resolve(f, false).mkdirs();
    }

    /**
     * Constructs a URI that represents this abstract pathname. The scheme may be
     * implementation dependent.
     * <P>
     * The exact form of the URI is implementation dependent. If it can be determined that
     * the file denoted by this abstract pathname is a directory, then the resulting URI
     * will end with a slash.
     *
     * @param f
     * @return A URI that represents this abstract pathname.
     */
    @Override
    public URI toURI(File f) {
        return resolve(f, false).toURI();
    }

    @Override
    public boolean canWrite(File f) {
        return resolve(f, false).canWrite();
    }

    @Override
    public boolean canRead(File f) {
        return resolve(f, false).canRead();
    }

    /**
     * Manipulates the write permissions for the abstract path designated by this file. The
     * behavior of this method is the same as that of the {@link #list(File)} method
     *
     * @param f The file
     * @param writable To allow write permission if true, otherwise disallow
     * @param ownerOnly To manipulate write permission only for owner if true, otherwise for
     *            everyone. The manipulation will apply to everyone regardless of this value
     *            if the underlying system does not distinguish owner and other users.
     * @return true if and only if the operation succeeded. If the user does not have
     *         permission to change the access permissions of this abstract pathname the
     *         operation will fail.
     */
    @Override
    public boolean setWritable(File f, boolean writable,
            boolean ownerOnly) {
        return resolve(f, false).setWritable(writable, ownerOnly);
    }

    /**
     * Manipulates the read permissions for the abstract path designated by this file. The
     * behavior of this method is the same as that of the {@link #list(File)} method
     *
     * @param f The file
     * @param readable To allow read permission if true, otherwise disallow
     * @param ownerOnly To manipulate read permission only for owner if true, otherwise for
     *            everyone. The manipulation will apply to everyone regardless of this value
     *            if the underlying system does not distinguish owner and other users.
     * @return true if and only if the operation succeeded. If the user does not have
     *         permission to change the access permissions of this abstract pathname the
     *         operation will fail. If the underlying file system does not support read
     *         permission and the value of readable is false, this operation will fail.
     */
    @Override
    public boolean setReadable(File f, boolean readable,
            boolean ownerOnly) {
        return resolve(f, false).setReadable(readable, ownerOnly);
    }

    /**
     * Returns the unique FileChannel object associated with this file.
     *
     * @param f The file
     * @return The file channel associated with the file
     * @throws FileNotFoundException an exception if the file is not found
     */
    @Override
    public FileChannel getChannel(File f, String mode)
            throws FileNotFoundException {
        return (new RandomAccessFile(resolve(f, true), mode)).getChannel();
    }

    @Override
    public File createTempFile(String prefix, String suffix, File directory)
            throws IOException {
        if (directory == null) {
            String tmppath = System.getenv("java.io.tmpdir");
            if (tmppath == null)
                tmppath = "/sdcard/tmp";
            directory = new File(tmppath);
        }
        File retval = File.createTempFile(prefix, suffix,
                resolve(directory, true));
        // we need to "unmangle" the path now
        if (retval.getAbsolutePath().startsWith(this.basedir.getAbsolutePath()))
            retval = new File(retval.getAbsolutePath()
                    .substring(basedir.getAbsolutePath().length()));
        return retval;
    }

    @Override
    public DatabaseIface createDatabase(DatabaseInformation info) {
        return null;
    }

    @Override
    public boolean isDatabase(File path) {
        return false;
    }
}
