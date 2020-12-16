
package com.atakmap.android.gui;

import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
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

final class IOProviderFactoryProxy extends IOProvider {
    final static IOProvider INSTANCE = new IOProviderFactoryProxy();

    private IOProviderFactoryProxy() {
    }

    @Override
    public String getName() {
        return "IOProviderFactory";
    }

    @Override
    public FileInputStream getInputStream(File f) throws FileNotFoundException {
        try {
            return IOProviderFactory.getInputStream(f);
        } catch (IOException e) {
            if (e instanceof FileNotFoundException)
                throw (FileNotFoundException) e;
            FileNotFoundException toThrow = new FileNotFoundException();
            toThrow.initCause(e);
            throw toThrow;
        }
    }

    @Override
    public FileOutputStream getOutputStream(File f, boolean append)
            throws FileNotFoundException {
        try {
            return IOProviderFactory.getOutputStream(f, append);
        } catch (IOException e) {
            if (e instanceof FileNotFoundException)
                throw (FileNotFoundException) e;
            FileNotFoundException toThrow = new FileNotFoundException();
            toThrow.initCause(e);
            throw toThrow;
        }
    }

    @Override
    public FileWriter getFileWriter(File f) throws IOException {
        return IOProviderFactory.getFileWriter(f);
    }

    @Override
    public FileReader getFileReader(File f) throws IOException {
        return IOProviderFactory.getFileReader(f);
    }

    @Override
    public RandomAccessFile getRandomAccessFile(File f, String mode)
            throws FileNotFoundException {
        return IOProviderFactory.getRandomAccessFile(f, mode);
    }

    @Override
    public boolean renameTo(File f1, File f2) {
        return IOProviderFactory.renameTo(f1, f2);
    }

    @Override
    public boolean delete(File f, int flag) {
        return IOProviderFactory.delete(f, flag);
    }

    @Override
    public long length(File f) {
        return IOProviderFactory.length(f);
    }

    @Override
    public long lastModified(File f) {
        return IOProviderFactory.lastModified(f);
    }

    @Override
    public boolean exists(File f) {
        return IOProviderFactory.exists(f);
    }

    @Override
    public boolean isDirectory(File f) {
        return IOProviderFactory.isDirectory(f);
    }

    @Override
    public String[] list(File f) {
        return IOProviderFactory.list(f);
    }

    @Override
    public String[] list(File f, FilenameFilter filter) {
        return IOProviderFactory.list(f, filter);
    }

    @Override
    public boolean mkdir(File f) {
        return IOProviderFactory.mkdir(f);
    }

    @Override
    public boolean mkdirs(File f) {
        return IOProviderFactory.mkdirs(f);
    }

    @Override
    public URI toURI(File f) {
        return IOProviderFactory.toURI(f);
    }

    @Override
    public boolean canWrite(File f) {
        return IOProviderFactory.canWrite(f);
    }

    @Override
    public boolean canRead(File f) {
        return IOProviderFactory.canRead(f);
    }

    @Override
    public boolean setWritable(File f, boolean writable, boolean ownerOnly) {
        return IOProviderFactory.setWritable(f, writable, ownerOnly);
    }

    @Override
    public boolean setReadable(File f, boolean readable, boolean ownerOnly) {
        return IOProviderFactory.setReadable(f, readable, ownerOnly);
    }

    @Override
    public FileChannel getChannel(File f, String mode)
            throws FileNotFoundException {
        return IOProviderFactory.getChannel(f, mode);
    }

    @Override
    public File createTempFile(String prefix, String suffix, File directory)
            throws IOException {
        return IOProviderFactory.createTempFile(prefix, suffix, directory);
    }

    @Override
    public DatabaseIface createDatabase(DatabaseInformation info) {
        return IOProviderFactory.createDatabase(info);
    }

    @Override
    public boolean isDatabase(File path) {
        return IOProviderFactory.isDatabase(path);
    }
}
