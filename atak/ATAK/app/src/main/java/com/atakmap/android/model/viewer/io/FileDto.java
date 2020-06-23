
package com.atakmap.android.model.viewer.io;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Data transfer object intended to be consumed by plugins.
 *
 * 
 */
public final class FileDto {
    private final String filename;
    private final FileType fileType;
    private final byte[] bytes;

    /**
     * Classifies a file type
     */
    public enum FileType {
        OBJ,
        V3DM,
        MTL,
        DAE,
        IMAGE_OR_TEXTURE;

        public final String ext;

        FileType(String ext) {
            this.ext = ext;
        }

        FileType() {
            this.ext = "." + name().toLowerCase(LocaleUtil.getCurrent());
        }

        public static FileType get(File f) {
            String ext = f.getName();
            ext = ext.substring(ext.lastIndexOf("."))
                    .toLowerCase(LocaleUtil.getCurrent());
            for (FileType ft : values()) {
                if (FileSystemUtils.isEquals(ft.ext, ext))
                    return ft;
            }
            return IMAGE_OR_TEXTURE;
        }
    }

    /**
     * Creates a new file data transfer object
     * @param filename the filename; may not be null
     * @param fileType the file type; may not be null
     * @param bytes the actual binary data of the file
     * @throws NullPointerException if {@code filename} or {@code fileType} was null
     */
    public FileDto(String filename, FileType fileType, byte[] bytes) {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(fileType, "fileType must not be null");

        this.filename = filename;
        this.fileType = fileType;
        this.bytes = bytes;
    }

    /**
     * @return the filename; never null
     */
    public String getFilename() {
        return filename;
    }

    /**
     * @return the file type; never null
     */
    public FileType getFileType() {
        return fileType;
    }

    /**
     * Returns a {@link FileType} based on the passed in filename's extension
     * @param filename the filename; may not be null;
     * @return the file type; never null; if the file type is not recognized returns
     *         {@code IMAGE_OR_TEXTURE} by default
     * @throws NullPointerException if filename was null
     */
    public static FileDto.FileType getFileType(String filename) {
        Objects.requireNonNull(filename, "filename must not be null");

        if (filename.endsWith(".obj"))
            return FileType.OBJ;
        if (filename.endsWith(".v3dm"))
            return FileType.V3DM;
        if (filename.endsWith(".mtl"))
            return FileType.MTL;
        return FileType.IMAGE_OR_TEXTURE;
    }

    /**
     * @return the actual binary data of the file
     */
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FileDto fileDto = (FileDto) o;
        return Objects.equals(filename, fileDto.filename) &&
                fileType == fileDto.fileType &&
                Arrays.equals(bytes, fileDto.bytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(filename, fileType);
        result = 31 * result + Arrays.hashCode(bytes);
        return result;
    }

    @Override
    public String toString() {
        return String.format(Locale.US,
                "FileDto {filename=%s, fileType=%s, bytes(size)=%d}",
                filename, fileType, bytes.length);
    }

}
