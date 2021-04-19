
package com.atakmap.android.model.viewer.io;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service for reading/writing files to persistence
 *
 * 
 */
public final class FileStorageService {
    public static final String MODEL_EXTENSION = ".v3dm";

    private static final String TAG = "FileStorageService";
    public static final String STORAGE_PATH = Environment
            .getExternalStorageDirectory() + "/VISOR";

    private FileStorageService() {
    }

    /**
     * Writes a list of {@link FileDto}s to external storage
     *
     * @param fileDtos the file data transfer objects to serialize; may not be {@code null}
     * @return the list of files that were successfully written; never null; may be empty
     * @throws NullPointerException if {@code fileDtos} is {@code null}
     * @throws IOException if an error occurred while trying to serialize the data
     */
    public static List<File> write(List<FileDto> fileDtos) throws IOException {
        Objects.requireNonNull(fileDtos, "fileDtos must not be null");

        List<File> files = new ArrayList<>();

        for (FileDto fileDto : fileDtos) {
            File file = createFile(fileDto.getFilename());
            try (FileOutputStream fileOutputStream = IOProviderFactory
                    .getOutputStream(file)) {
                fileOutputStream.write(fileDto.getBytes());
                Log.d(TAG, "wrote fileDto=" + fileDto);
            }
            files.add(file);
        }

        return files;
    }

    /**
     * Retrieves file metadata
     *
     * @param uri the uri of the file; may not be {@code null}
     * @return the file metadata; never {@code null}
     * @throws IOException if an error occurred while accessing the file
     * @throws NullPointerException if {@code uri} was null
     */
    public static FileMetadata getFileMetadata(Uri uri) throws IOException {
        Objects.requireNonNull(uri, "uri must not be null");

        String filename = getFilename(uri);
        File file = new File(STORAGE_PATH, filename);
        return new FileMetadata(filename, getFileSize(file));
    }

    /**
     * Retrieves file metadata
     *
     * @param uri the uri of the file; may not be null
     * @param contentResolver the {@link ContentResolver} used to retrieve the metadata; may not be
     *                        null
     * @return the file metadata; never null
     * @throws NullPointerException if {@code uri} or {@code contentResolver }is {@code null}
     * @throws IOException if an error occurred while accessing the file
     */
    public static FileMetadata getFileMetadata(Uri uri,
            ContentResolver contentResolver)
            throws IOException {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(contentResolver,
                "contentResolver must not be null");

        String name;
        long size;
        String[] projection = new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        };

        try (Cursor cursor = contentResolver.query(uri, projection, null, null,
                null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                Log.w(TAG, "unable to determine metadata via cursor strategy");
                name = getFilename(uri);
                if (name == null) {
                    throw new IOException("unable to determine filename");
                }
                size = getFileSize(new File(STORAGE_PATH, name));
                return new FileMetadata(name, size);
            }

            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex == -1) {
                throw new IOException("nameIndex does not exist");
            }

            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (sizeIndex == -1) {
                throw new IOException("columnIndex does not exist");
            }

            name = cursor.getString(nameIndex);
            if (name == null) {
                throw new IOException("name was null");
            }

            size = cursor.getLong(sizeIndex);
        }

        return new FileMetadata(name, size);
    }

    /**
     * Gets a file handle based off a uri
     * @param uri the uri of the file; may not be {@code null}
     * @return the file; may be {@code null} if unable to construct the file from the given {@code uri}
     * @throws NullPointerException if {@code uri} was {@code null}
     * @throws IOException if unable to construct the file object
     */
    public static File getFile(Uri uri) throws IOException {
        Objects.requireNonNull(uri, "uri must not be null");

        String path = uri.getPath();
        if (path == null)
            return null;
        File f = new File(path);
        if (FileSystemUtils.isZipPath(path))
            f = new ZipVirtualFile(path);
        if (IOProviderFactory.exists(f))
            return f;
        String filename = getFilename(uri);
        return new File(STORAGE_PATH, filename);
    }

    private static long getFileSize(File file) throws IOException {
        try (RandomAccessFile raf = IOProviderFactory.getRandomAccessFile(file,
                "r")) {
            return raf.length();
        }
    }

    private static String getFilename(Uri uri) throws IOException {
        String path = uri.getPath();

        int start = path.lastIndexOf('/');
        if (start < 0) {
            throw new IOException(
                    "Unable to determine filename from uri: " + uri);
        }

        return path.substring(start + 1);
    }

    /**
     * Read raw bytes from a given uri
     *
     * @param uri the uri of a resource to read; may not be {@code null}
     * @return the raw bytes of the uri
     * @throws IOException if unable to read the uri
     * @throws NullPointerException if {@code uri} was {@code null}
     */
    public static byte[] getBytes(Uri uri) throws IOException {
        Objects.requireNonNull(uri, "uri must not be null");

        FileMetadata metadata = getFileMetadata(uri);
        String filename = metadata.getFilename();
        long size = metadata.getSize();
        byte[] bytes = new byte[(int) size];

        File file = new File(STORAGE_PATH, filename);
        try (RandomAccessFile raf = IOProviderFactory.getRandomAccessFile(file,
                "r")) {
            raf.readFully(bytes);
        }

        return bytes;
    }

    private static File createFile(String filename) throws IOException {
        File file = new File(STORAGE_PATH, filename);
        File dir = file.getParentFile();
        if (IOProviderFactory.mkdirs(dir)) {
            Log.d(TAG, "created directory=" + dir);
        }
        if (IOProviderFactory.createNewFile(file)) {
            Log.d(TAG, "Created file=" + file);
        }
        return file;
    }

    /**
     * Represents the metadata of a file
     */
    public static final class FileMetadata {
        private final String filename;
        private final long size;

        /**
         * Creates a new instance of this class
         *
         * @param filename the filename; may not be null
         * @param size the file size in bytes; must not be negative
         * @throws IllegalArgumentException if size was negative
         * @throws NullPointerException if filename was null
         */
        FileMetadata(String filename, long size) {
            Objects.requireNonNull(filename, "filename must not be null");
            if (size <= 0) {
                throw new IllegalArgumentException("size must not be negative");
            }
            this.filename = filename;
            this.size = size;
        }

        /**
         * @return the filename; never null
         */
        public String getFilename() {
            return filename;
        }

        /**
         * @return the size of the file in bytes
         */
        public long getSize() {
            return size;
        }

    }

}
