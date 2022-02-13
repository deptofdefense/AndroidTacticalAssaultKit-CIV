
package com.atakmap.coremap.io;

import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;

import java.io.File;
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

import static junit.framework.Assert.assertTrue;

//TODO: This class should be renamed since it's only used for the GdalLayerInfo Test
/**
 * Abstracted IO Operations
 */
public class MockIOProvider extends IOProvider {

    public static final int SECURE_DELETE = 1;

    private File resolve(File f, boolean ensure) {

        // very simple implementation that just creates the file in the /sdcard/encrypted
        // directory.
        if (f.getParentFile() != null && !f.getParentFile().exists() && ensure)
            f.getParentFile().mkdirs();
        return f;
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

    @Override
    public long length(File f) {
        return resolve(f, false).length();
    }

    @Override
    public long lastModified(File f) {
        return resolve(f, false).lastModified();
    }

    @Override
    public boolean exists(File f) {
        return true;
    }

    @Override
    public boolean isDirectory(File f) {
        return false;
    }

    @Override
    public String[] list(File f) {
        return resolve(f, false).list();
    }

    @Override
    public String[] list(File f, FilenameFilter filter) {
        return resolve(f, false).list(filter);
    }

    @Override
    public boolean mkdir(File f) {
        return resolve(f, false).mkdir();
    }

    @Override
    public boolean mkdirs(File f) {
        return resolve(f, false).mkdirs();
    }

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

    @Override
    public boolean setWritable(File f, boolean writable,
            boolean ownerOnly) {
        return resolve(f, false).setWritable(writable, ownerOnly);
    }

    @Override
    public boolean setReadable(File f, boolean readable,
            boolean ownerOnly) {
        return resolve(f, false).setReadable(readable, ownerOnly);
    }

    @Override
    public FileChannel getChannel(File f, String mode)
            throws FileNotFoundException {
        return (new RandomAccessFile(resolve(f, true), mode)).getChannel();
    }

    @Override
    public File createTempFile(String prefix, String suffix, File directory)
            throws IOException {
        return null;
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
}
