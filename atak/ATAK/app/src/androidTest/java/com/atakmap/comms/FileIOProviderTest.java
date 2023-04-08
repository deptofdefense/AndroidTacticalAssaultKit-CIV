
package com.atakmap.comms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.commoncommo.CommoException;
import com.atakmap.commoncommo.FileIOProvider;
import com.atakmap.commoncommo.IOProviderTester;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.loader.NativeLoader;
import com.atakmap.database.DatabaseIface;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * This class tests the functionality of the native FileIOProvider by attempting a SimpleFileIO file
 * transfer and verifying the channel is opened and that bytes are transferred.
 */
@RunWith(AndroidJUnit4.class)
public class FileIOProviderTest extends ATAKInstrumentedTest {

    /**
     * The channel that will be returned when open is called on the FileIOProvider instance we have
     * created
     */
    private FileChannel _testChannel;

    /**
     * Holds the Test FileIOProvider we will inject into Commo
     */
    private FileIOProvider _testProvider;

    /**
     * Holds the dummy instance of the FileIOProvider
     */
    private IOProvider _dummyProvider;

    /**
     * Holds the instance of IOProviderTester
     */
    private IOProviderTester _tester;

    /**
     * Will hold the fileSize returned when a call is made to the provider
     */
    private long _fileSize = 0L;

    /**
     * Before Test: Set up Commo and load the native library, register the dummy FileIOProvider with
     * Commo
     */
    @Before
    public void beforeTest() throws CommoException {
        NativeLoader.loadLibrary("commoncommojni");
        _testProvider = createDummyNativeProvider();
        _tester = new IOProviderTester();
        _tester.registerFileIOProvider(_testProvider);
        _dummyProvider = createDummyProvider();
        IOProviderFactory.registerProvider(_dummyProvider);
    }

    /**
     * Test Cleanup
     */
    @After
    public void afterTest() {
        _tester.deregisterFileIOProvider(_testProvider);
        _testChannel = null;
    }

    /**
     * Tests that when the Provider is deregistered that no calls occur on the object
     */
    @Test
    public void deregisterProvider_Success() throws IOException {
        // ARRANGE
        _tester.deregisterFileIOProvider(_testProvider);
        File f = createTestFile();
        // ACT
        FileChannel channel = _tester.open(f.getAbsolutePath(), "rw");
        // ASSERT
        assertNull(channel);
    }

    /**
     * Tests that opening a File through the native FileIOProvider that the FileChannel is provided
     * to the native code for that file
     */
    @Test
    public void openFileChannel_Success()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel channel = _tester.open(f.getAbsolutePath(), "rw");
        assertTrue(channel.isOpen());
        // ASSERT
        Assert.assertNotNull(channel);
    }

    /**
     * Tests that a FileChannel can be closed once it is opened.
     */
    @Test
    public void closeChannel_Success()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel actualChannel = _tester.open(f.getAbsolutePath(), "rw");
        assertTrue(actualChannel.isOpen());
        _tester.close(actualChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        assertFalse(actualChannel.isOpen());
    }

    /**
     * Tests Reading successfully from a FileChannel
     */
    @Test
    public void readChannel_Success()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        ByteBuffer buffer = ByteBuffer.allocateDirect(15);
        long bytesRead = _tester.read(buffer, 1, 5, openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertNotEquals(0, bytesRead);
    }

    /**
     * Tests Writing successfully to a FileChannel
     */
    @Test
    public void writeToChannel_Success()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        ByteBuffer buffer = ByteBuffer.allocateDirect(15);
        long bytesWritten = _tester.write(buffer, 1, 5, openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertNotEquals(0, bytesWritten);
    }

    /**
     * Tests That Eof returns a 0 value when not the end of file
     */
    @Test
    public void eof_NotEndOfFile_Success()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        int val = _tester.eof(openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertEquals(0, val);
    }

    /**
     * Tests That Eof returns a non zero value when at the end of file
     */
    @Test
    public void eof_EndOfFile_Success()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        _tester.seek(0, 2, openedChannel);
        int val = _tester.eof(openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertEquals(1, val);
    }

    /**
     * Tests That seek successfully occurs and returns 0
     */
    @Test
    public void seek_NoError()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        String inputString = "something";
        String expectedString = "mething";
        ByteBuffer buffer = ByteBuffer.allocateDirect(15);
        buffer.put(inputString.getBytes());
        ByteBuffer outbuffer = ByteBuffer
                .allocateDirect(expectedString.length());
        long written = _tester.write(buffer, 1, inputString.length(),
                openedChannel);
        int val = _tester.seek(2, 0, openedChannel);
        long bytesRead = _tester.read(outbuffer, 1, expectedString.length(),
                openedChannel);
        int val2 = _tester.seek(3, 1, openedChannel);
        int val3 = _tester.seek(3, 2, openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertEquals(0, val);
        Assert.assertEquals(0, val2);
        Assert.assertEquals(0, val3);

        Assert.assertEquals(bytesRead, expectedString.length());
        String actualString = new String(outbuffer.array(),
                outbuffer.arrayOffset(), (int) (bytesRead),
                StandardCharsets.US_ASCII);
        Assert.assertEquals(actualString.length(), expectedString.length());
        Assert.assertEquals(expectedString, actualString);
    }

    /**
     * Tests That seek fails and returns an error
     */
    @Test
    public void seek_Error()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        int val = _tester.seek(-100, 2, openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertEquals(-1, val);
    }

    /**
     * Tests That the error method returns 0
     */
    @Test
    public void error_Returns0()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        int val = _tester.error(openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertEquals(0, val);
    }

    /**
     * Tests that tell returns the current position of the filechannel
     */
    @Test
    public void tell_NonNegativeReturned()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        long val = _tester.tell(openedChannel);
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertEquals(0, val);
    }

    /**
     * Tests that getSize returns the size of the file
     */
    @Test
    public void getSize_CorrectSizeReturned()
            throws IOException {
        // ARRANGE
        File f = createTestFile();
        // ACT
        FileChannel openedChannel = _tester.open(f.getAbsolutePath(), "rw");
        long actualSize = openedChannel.size();
        long size = _tester.getSize("/sdcard/test.txt");
        // ASSERT
        Assert.assertNotNull(_testChannel);
        Assert.assertEquals(actualSize, size);
    }

    /**
     * Creates and writes test data to a file
     * 
     * @return A created file
     */
    private File createTestFile() throws IOException {
        File f = new File("/sdcard/test.txt");
        FileOutputStream fos = IOProviderFactory.getOutputStream(f);
        fos.write("test_stream".getBytes());
        fos.close();
        return f;
    }

    //

    @Test
    public void openInvokedAfterRegister() throws Throwable {
        final IOProviderTester tester = new IOProviderTester();
        final MockFileIOProvider io = new MockFileIOProvider();
        tester.registerFileIOProvider(io);
        try {
            final String path = "/dev/null";
            final String mode = "rw";

            FileChannel ignored = tester.open(path, mode);
            assertNull(ignored);

            assertTrue(io.open_invoked);
            assertEquals(io.open_path, path);
            assertEquals(io.open_mode, mode);
        } finally {
            tester.deregisterFileIOProvider(io);
        }
    }

    @Test
    public void openNotInvokedAfterDeregister() throws Throwable {
        final IOProviderTester tester = new IOProviderTester();
        final MockFileIOProvider io = new MockFileIOProvider();
        tester.registerFileIOProvider(io);
        tester.deregisterFileIOProvider(io);

        final String path = "/dev/null";
        final String mode = "rw";

        FileChannel ignored = tester.open(path, mode);
        assertNull(ignored);

        assertFalse(io.open_invoked);
    }

    @Test
    public void getSizeInvokedAfterRegister() throws Throwable {
        final IOProviderTester tester = new IOProviderTester();
        final MockFileIOProvider io = new MockFileIOProvider();
        tester.registerFileIOProvider(io);
        try {
            final String path = "/dev/null";

            tester.getSize(path);

            assertTrue(io.getSize_invoked);
            assertEquals(io.getSize_path, path);
        } finally {
            tester.deregisterFileIOProvider(io);
        }
    }

    @Test
    public void getSizeNotInvokedAfterDeregister() throws Throwable {
        final IOProviderTester tester = new IOProviderTester();
        final MockFileIOProvider io = new MockFileIOProvider();
        tester.registerFileIOProvider(io);
        tester.deregisterFileIOProvider(io);

        final String path = "/dev/null";

        tester.getSize(path);
        assertFalse(io.getSize_invoked);
    }

    // region Providers
    /**
     * Creates a Dummy FileIOProvider to be used with the tests
     * 
     * @return - A Dummy FileIOProvider
     */
    private com.atakmap.coremap.io.IOProvider createDummyProvider() {
        return new IOProvider() {
            private File resolve(File f, boolean ensure) {

                // very simple implementation that just creates the file in the /sdcard/encrypted
                // directory.
                File nf = new File("/sdcard/encrypted/" + f);
                // noinspection ConstantConditions
                if (!nf.getParentFile().exists() && ensure)
                    // noinspection ResultOfMethodCallIgnored
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
             * @param f the file to use as the basis for the input stream
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
             * @param f The File
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
             * @param f The File
             * @return <code>true</code> if the file exists, <code>false</code> otherwise.
             */
            @Override
            public boolean exists(File f) {
                return resolve(f, false).exists();
            }

            /**
             * Returns <code>true</code> if the file exists, <code>false</code> otherwise.
             *
             * @param f The File
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
             * @param f The File
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
             * @param f The File
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
             * @param f The File
             * @return A URI that represents this abstract pathname.
             */
            @Override
            public URI toURI(File f) {
                return resolve(f, false).toURI();
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

            /**
             * Opens/creates the database desribed by the specified {@link DatabaseInformation}.
             *
             * @param info Describes the database to be opened/created; may provide optional hints
             * @return The database instance or <code>null</code> if no database could be opened
             */
            @Override
            public DatabaseIface createDatabase(DatabaseInformation info) {
                return null;
            }

            /**
             * Determines whether database given by its path is a SQLite database
             *
             * @param path the path to database
             * @return true if the given path points to a database; false, otherwise
             */
            @Override
            public boolean isDatabase(File path) {
                return false;
            }

            @Override
            public boolean canWrite(File f) {
                return f.canWrite();
            }

            @Override
            public boolean canRead(File f) {
                return f.canRead();
            }

            @Override
            public File createTempFile(String prefix, String suffix,
                    File directory) throws IOException {
                return File.createTempFile(prefix, suffix, directory);
            }
        };
    }

    /**
     * Creates a dummy FileIOProvider that can intercept and set the FileChannel that will be used
     * for the transfer operations so that we can verify calls on it
     *
     * @return The created dummy provider
     */
    private FileIOProvider createDummyNativeProvider() {
        return new FileIOProvider() {
            @Override
            public FileChannel open(String s, String s1) {
                try {
                    _testChannel = IOProviderFactory.getChannel(new File(s),
                            s1);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                return _testChannel;
            }

            @Override
            public long getSize(String s) {
                _fileSize = new File(s).length();
                return _fileSize;
            }

            int id;
        };
    }
    // endregion
}
