
package com.atakmap.coremap.io;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.androidtest.util.FileUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.interop.Pointer;
import com.atakmap.map.Interop;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

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

/**
 * Test class for IOProviderFactory
 */
@RunWith(AndroidJUnit4.class)
public class IOProviderFactoryTest extends ATAKInstrumentedTest {

    private final Interop<DatabaseIface> Database_interop = Interop
            .findInterop(DatabaseIface.class);

    private IOProvider createDummyProvider() {
        final String manglePrefix = "/sdcard/encrypted/";
        return new IOProvider() {
            private File resolve(File f, boolean ensure) {

                // very simple implementation that just creates the file in the /sdcard/encrypted
                // directory.
                File nf = new File(manglePrefix + f);
                if (nf.getParentFile() != null && !nf.getParentFile().exists()
                        && ensure)
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
                return (new RandomAccessFile(resolve(f, true), mode))
                        .getChannel();
            }

            @Override
            public File createTempFile(String prefix, String suffix,
                    File directory) throws IOException {
                if (directory == null) {
                    String tmppath = System.getenv("java.io.tmpdir");
                    if (tmppath == null)
                        tmppath = "/sdcard/tmp";
                    directory = new File(tmppath);
                }
                directory = resolve(directory, false);
                directory.mkdirs();
                File retval = File.createTempFile(prefix, suffix, directory);
                // we need to "unmangle" the path now
                if (retval.getAbsolutePath().startsWith(manglePrefix))
                    retval = new File(retval.getAbsolutePath()
                            .substring(manglePrefix.length()));
                return retval;
            }

            @Override
            public boolean isDatabase(File path) {
                return true;
            }

            @Override
            public DatabaseIface createDatabase(DatabaseInformation object) {
                assertTrue(object.uri.toString()
                        .contains("/sdcard/test.sqlite"));
                return new DatabaseIface() {
                    @Override
                    public void execute(String sql, String[] args) {
                    }

                    @Override
                    public CursorIface query(String sql, String[] args) {
                        return null;
                    }

                    @Override
                    public StatementIface compileStatement(String sql) {
                        return null;
                    }

                    @Override
                    public QueryIface compileQuery(String sql) {
                        return null;
                    }

                    @Override
                    public boolean isReadOnly() {
                        return false;
                    }

                    @Override
                    public void close() {

                    }

                    @Override
                    public int getVersion() {
                        return 0;
                    }

                    @Override
                    public void setVersion(int version) {

                    }

                    @Override
                    public void beginTransaction() {

                    }

                    @Override
                    public void setTransactionSuccessful() {

                    }

                    @Override
                    public void endTransaction() {

                    }

                    @Override
                    public boolean inTransaction() {
                        return false;
                    }
                };
            }
        };
    }

    /**
     * Test File Filter
     */
    static class TestFileFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            String filename = file.getName();
            boolean retval = filename.contains(".txt");
            return retval;
        }
    }

    /**
     * Helper method to write an empty file
     * @param f File to be written
     * @param fileText Text to be written to File
     */
    private void writeFile(File f, String fileText) throws IOException {
        FileWriter fw = IOProviderFactory.getFileWriter(f);
        fw.write(fileText);
        fw.close();
    }

    @Test
    public void register_test() throws IOException {
        IOProvider fiop;
        IOProviderFactory.registerProvider(fiop = createDummyProvider());
    }

    @Test
    public void test_stream() throws IOException {
        final IOProvider provider = createDummyProvider();
        try {
            IOProviderFactory.registerProvider(provider);
            File f = new File("test.txt");
            FileOutputStream fos = IOProviderFactory.getOutputStream(f);
            fos.write("test_stream".getBytes());
            fos.close();
            FileInputStream fis = IOProviderFactory.getInputStream(f);
            String result = FileSystemUtils.copyStreamToString(fis, true,
                    FileSystemUtils.UTF8_CHARSET);
            Assert.assertEquals("test_stream", result);
            Assert.assertTrue(new File("/sdcard/encrypted/test.txt").exists());
            Assert.assertTrue(IOProviderFactory.delete(f, 0));
        } finally {
        }
    }

    @Test
    public void test_writer() throws IOException {
        final IOProvider provider = createDummyProvider();
        try {
            IOProviderFactory.registerProvider(provider);
            File f = new File("test.txt");
            writeFile(f, "test_writer");

            FileInputStream fis = IOProviderFactory.getInputStream(f);
            String result = FileSystemUtils.copyStreamToString(fis, true,
                    FileSystemUtils.UTF8_CHARSET);
            Assert.assertEquals("test_writer", result);
            Assert.assertTrue(new File("/sdcard/encrypted/test.txt").exists());
            Assert.assertTrue(IOProviderFactory.delete(f, 0));
        } finally {
        }
    }

    /**
     * Tests filenamesToFiles calls
     */
    @Test
    public void filenamesToFiles_multipleFiles() throws IOException {
        // ARRANGE
        final IOProvider provider = createDummyProvider();
        try {
            IOProviderFactory.registerProvider(provider);
            String fileName1 = "filenamesToFiles1.txt";
            String fileName2 = "filenamesToFiles2.txt";
            String fileName3 = "filenamesToFiles3.txt";
            String[] fileNames = {
                    fileName1, fileName2, fileName3
            };
            File f1 = new File(fileName1);
            File f2 = new File(fileName2);
            File f3 = new File(fileName3);
            File dir = new File(".");

            String file2Text = "Some Text 2";
            writeFile(f1, "Some Text 1");
            writeFile(f2, file2Text);
            writeFile(f3, "Some Text 3");

            // ACT
            File[] files = IOProviderFactory.filenamesToFiles(dir, fileNames);

            // ASSERT
            Assert.assertEquals(files.length, 3);
            Assert.assertEquals(fileName1, files[0].getName());
            Assert.assertEquals(fileName2, files[1].getName());
            Assert.assertEquals(fileName3, files[2].getName());

            FileInputStream fis = IOProviderFactory.getInputStream(f2);
            String result = FileSystemUtils.copyStreamToString(fis, true,
                    FileSystemUtils.UTF8_CHARSET);
            Assert.assertEquals(file2Text, result);
        } finally {
        }
    }

    /**
     * Tests filenamesToFiles calls
     */
    @Test
    public void listFiles_FilterReturnsSome() throws IOException {
        // ARRANGE
        final IOProvider provider = createDummyProvider();
        try {
            IOProviderFactory.registerProvider(provider);
            File dir = new File(
                    "/sdcard/encrypted/listFiles_FilterReturnsSome");
            dir.mkdir();

            File f1 = new File(dir, "listFiles1.txt");
            File f2 = new File(dir, "listFiles2.dat");
            File f3 = new File(dir, "listFiles3.log");
            File f4 = new File(dir, "listFiles4.txt");

            writeFile(f1, "Some Text 1");
            writeFile(f2, "Some Text 2");
            writeFile(f3, "Some Text 3");
            writeFile(f4, "Some Text 4");

            FileFilter filter = new TestFileFilter();

            // ACT
            File[] files = IOProviderFactory.listFiles(dir, filter);

            // ASSERT
            Assert.assertEquals(2, files.length);
            Assert.assertEquals("listFiles1.txt", files[0].getName());
            Assert.assertEquals("listFiles4.txt", files[1].getName());
        } finally {
        }
    }

    final static class ProviderRegistration implements AutoCloseable {
        final IOProvider provider;

        public ProviderRegistration(IOProvider provider) {
            this.provider = provider;
            IOProviderFactory.registerProvider(provider);
        }

        @Override
        public void close() {
        }
    }

    /** INTEROP TESTING **/

    @Test
    public void native_does_not_invoke_default_provider() throws IOException {
        final boolean[] createDatabaseInvoked = {
                false
        };
        final IOProvider mock = new MockProvider("mock",
                new File("/dev/null")) {
            @Override
            public DatabaseIface createDatabase(DatabaseInformation info) {
                createDatabaseInvoked[0] = true;
                return super.createDatabase(info);
            }
        };
        IOProviderFactory.registerProvider(mock);
        try {
            try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                    .createTempFile()) {
                final Pointer pointer = IOProviderFactory
                        .DatabaseProvider_create(f.file.getAbsolutePath());
                assertNotNull(pointer);
                assertFalse(createDatabaseInvoked[0]);
            }
        } finally {
        }
    }

    @Test
    public void native_invokes_non_default_provider() {
        final boolean[] createDatabaseInvoked = {
                false
        };
        final IOProvider mock = new MockProvider("mock",
                new File("/dev/null")) {
            @Override
            public DatabaseIface createDatabase(DatabaseInformation info) {
                createDatabaseInvoked[0] = true;
                return super.createDatabase(info);
            }
        };
        IOProviderFactory.registerProvider(mock);
        try {
            final Pointer pointer = IOProviderFactory
                    .DatabaseProvider_create("/dev/null");
            assertNull(pointer);
            assertTrue(createDatabaseInvoked[0]);
        } finally {
        }
    }
}
