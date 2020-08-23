
package com.atakmap.coremap.io;

import androidx.test.runner.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

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

/**
 * Test class for FileIoProviderFactory
 */
@RunWith(AndroidJUnit4.class)
public class FileIOProviderFactoryTest extends ATAKInstrumentedTest {

    private FileIOProvider createDummyProvider() {
        return new FileIOProvider() {
            private File ensureCreate(File f) {

                // very simple implementation that just creates the file in the /sdcard/encrypted
                // directory.
                File nf = new File("/sdcard/encrypted/" + f);
                if (!nf.getParentFile().exists())
                    nf.getParentFile().mkdirs();
                return nf;
            }

            @Override
            public String getName() {
                return "dummy provider";
            }

            @Override
            public FileInputStream getInputStream(File f)
                    throws FileNotFoundException {
                return new FileInputStream(ensureCreate(f));
            }

            @Override
            public FileOutputStream getOutputStream(File f)
                    throws FileNotFoundException {
                return new FileOutputStream(ensureCreate(f));
            }

            @Override
            public FileWriter getFileWriter(File f) throws IOException {
                return new FileWriter(ensureCreate(f));
            }

            @Override
            public FileReader getFileReader(File f) throws IOException {
                return new FileReader(ensureCreate(f));
            }

            @Override
            public RandomAccessFile getRandomAccessFile(File f, String mode)
                    throws FileNotFoundException {
                return new RandomAccessFile(ensureCreate(f), mode);
            }

            @Override
            public boolean renameTo(File f1, File f2) {
                return ensureCreate(f1).renameTo(ensureCreate(f2));
            }

            @Override
            public boolean delete(File f) {
                return ensureCreate(f).delete();
            }

            /**
             * Returns the length of the file.
             *
             * @param f the file
             * @return The length of the file.
             */
            @Override
            public long length(File f) {
                return 0;
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
                return 0;
            }

            /**
             * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
             *
             * @param f
             * @return <code>true</code> if the file exists, <code>false</code> otherwise.
             */
            @Override
            public boolean exists(File f) {
                return false;
            }

            /**
             * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
             *
             * @param f
             * @return <code>true</code> if the file exists, <code>false</code> otherwise.
             */
            @Override
            public boolean isDirectory(File f) {
                return false;
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
                return new String[0];
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
                return new String[0];
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
                return false;
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
                return false;
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
                return null;
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
                return false;
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
                return false;
            }
        };
    }

    @Test
    public void register_test() throws IOException {
        FileIOProvider fiop;
        FileIOProviderFactory.registerProvider(fiop = createDummyProvider());
        FileIOProviderFactory.unregisterProvider(fiop);
    }

    @Test
    public void test_stream() throws IOException {
        FileIOProviderFactory.registerProvider(createDummyProvider());
        File f = new File("test.txt");
        FileOutputStream fos = FileIOProviderFactory.getOutputStream(f);
        fos.write("test_stream".getBytes());
        fos.close();
        FileInputStream fis = FileIOProviderFactory.getInputStream(f);
        String result = FileSystemUtils.copyStreamToString(fis, true,
                FileSystemUtils.UTF8_CHARSET);
        Assert.assertEquals("test_stream", result);
        Assert.assertTrue(new File("/sdcard/encrypted/test.txt").exists());
        Assert.assertTrue(FileIOProviderFactory.delete(f));
    }

    @Test
    public void test_writer() throws IOException {
        FileIOProviderFactory.registerProvider(createDummyProvider());
        File f = new File("test.txt");
        FileWriter fw = FileIOProviderFactory.getFileWriter(f);
        fw.write("test_writer");
        fw.close();
        FileInputStream fis = FileIOProviderFactory.getInputStream(f);
        String result = FileSystemUtils.copyStreamToString(fis, true,
                FileSystemUtils.UTF8_CHARSET);
        Assert.assertEquals("test_writer", result);
        Assert.assertTrue(new File("/sdcard/encrypted/test.txt").exists());
        Assert.assertTrue(FileIOProviderFactory.delete(f));
    }
}
