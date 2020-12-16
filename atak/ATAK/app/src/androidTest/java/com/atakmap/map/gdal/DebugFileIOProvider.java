
package com.atakmap.map.gdal;

import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class DebugFileIOProvider extends IOProvider {

    public final static class InvocationDebugRecord {
        public String methodName;
        public Map<String, Object> parameters = new HashMap<>();

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof InvocationDebugRecord))
                return false;
            InvocationDebugRecord other = (InvocationDebugRecord) o;
            return methodName.equals(other.methodName)
                    && parameters.equals(other.parameters);
        }
    }

    private final IOProvider impl;
    private final LinkedList<InvocationDebugRecord> records = new LinkedList<>();

    public DebugFileIOProvider(IOProvider impl) {
        this.impl = impl;
    }

    public void resetInvocationRecord() {
        this.records.clear();
    }

    public void getInvocationRecord(List<InvocationDebugRecord> record) {
        record.addAll(this.records);
    }

    @Override
    public String getName() {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "getName";
        this.records.add(record);
        return "debug." + impl.getName();
    }

    @Override
    public FileInputStream getInputStream(File f) throws FileNotFoundException {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "getInputStream";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.getInputStream(f);
    }

    @Override
    public FileOutputStream getOutputStream(File f, boolean append)
            throws FileNotFoundException {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "getOutputStream";
        record.parameters.put("f", f);
        record.parameters.put("append", append);
        this.records.add(record);
        return impl.getOutputStream(f, append);
    }

    @Override
    public FileWriter getFileWriter(File f) throws IOException {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "getFileWriter";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.getFileWriter(f);
    }

    @Override
    public FileReader getFileReader(File f) throws IOException {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "getFileReader";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.getFileReader(f);
    }

    @Override
    public RandomAccessFile getRandomAccessFile(File f, String mode)
            throws FileNotFoundException {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "getRandomAccessFile";
        record.parameters.put("f", f);
        record.parameters.put("mode", mode);
        this.records.add(record);
        return impl.getRandomAccessFile(f, mode);
    }

    @Override
    public boolean renameTo(File f1, File f2) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "renameTo";
        record.parameters.put("f1", f1);
        record.parameters.put("f2", f2);
        this.records.add(record);
        return impl.renameTo(f1, f2);
    }

    @Override
    public boolean delete(File f, int flag) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "delete";
        record.parameters.put("f", f);
        record.parameters.put("flag", flag);
        this.records.add(record);
        return impl.delete(f, flag);
    }

    @Override
    public long length(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "length";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.length(f);
    }

    @Override
    public long lastModified(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "lastModified";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.lastModified(f);
    }

    @Override
    public boolean exists(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "exists";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.exists(f);
    }

    @Override
    public boolean isDirectory(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "isDirectory";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.isDirectory(f);
    }

    @Override
    public String[] list(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "list";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.list(f);
    }

    @Override
    public String[] list(File f, FilenameFilter filter) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "list";
        record.parameters.put("f", f);
        record.parameters.put("filter", filter);
        this.records.add(record);
        return impl.list(f, filter);
    }

    @Override
    public boolean mkdir(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "mkdir";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.mkdir(f);
    }

    @Override
    public boolean mkdirs(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "mkdirs";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.mkdirs(f);
    }

    @Override
    public URI toURI(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "toURI";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.toURI(f);
    }

    @Override
    public boolean canWrite(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "canWrite";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.canWrite(f);
    }

    @Override
    public boolean canRead(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "canRead";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.canRead(f);
    }

    @Override
    public boolean setWritable(File f, boolean writable, boolean ownerOnly) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "setWritable";
        record.parameters.put("f", f);
        record.parameters.put("writable", writable);
        record.parameters.put("ownerOnly", ownerOnly);
        this.records.add(record);
        return impl.setWritable(f, writable, ownerOnly);
    }

    @Override
    public boolean setReadable(File f, boolean readable, boolean ownerOnly) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "setReadable";
        record.parameters.put("f", f);
        record.parameters.put("readable", readable);
        record.parameters.put("ownerOnly", ownerOnly);
        this.records.add(record);
        return impl.setReadable(f, readable, ownerOnly);
    }

    @Override
    public FileChannel getChannel(File f, String mode)
            throws FileNotFoundException {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "getChannel";
        record.parameters.put("f", f);
        record.parameters.put("mode", mode);
        this.records.add(record);
        return impl.getChannel(f, mode);
    }

    @Override
    public File createTempFile(String prefix, String suffix, File directory)
            throws IOException {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "createTempFile";
        record.parameters.put("prefix", prefix);
        record.parameters.put("suffix", suffix);
        record.parameters.put("directory", directory);
        this.records.add(record);
        return impl.createTempFile(prefix, suffix, directory);
    }

    @Override
    public boolean isDatabase(File f) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "isDatabase";
        record.parameters.put("f", f);
        this.records.add(record);
        return impl.isDatabase(f);
    }

    @Override
    public DatabaseIface createDatabase(DatabaseInformation info) {
        InvocationDebugRecord record = new InvocationDebugRecord();
        record.methodName = "createDatabase";
        record.parameters.put("info", info);
        this.records.add(record);
        return impl.createDatabase(info);
    }
}
