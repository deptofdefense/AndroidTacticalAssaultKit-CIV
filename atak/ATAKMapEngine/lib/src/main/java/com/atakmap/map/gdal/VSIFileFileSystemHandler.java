
package com.atakmap.map.gdal;

import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

public final class VSIFileFileSystemHandler extends VSIJFileFilesystemHandler {

    private final static String TAG = "VSIFileFileSystemHandler";
    private static StructStat DEFAULT_STAT = null;

    public final static String PREFIX = "/vsitakio/";

    /**
     * Constructor
     */
    public VSIFileFileSystemHandler(){
        super(PREFIX);
    }
    /**
     * Opens the file
     *
     * @param filename path to file to open prefixed with the prefix used in
     *            installFileSystemHandler
     * @param access specifies type of access for the file
     * @return File Handle to the open file or Null if unable to open
     * @throws IOException in case of error
     */
    @Override
    public FileChannel open(String filename, String access) throws IOException {
        if(filename.startsWith(this.getPrefix())) {
            filename = filename.substring(this.getPrefix().length());
            access = convertFileAccessMode(access);
            return IOProviderFactory.getChannel(new File(filename), access);
        }
        return null;
    }

    /**
     * Gets information about the file
     *
     * @param filename path to file to open prefixed with the prefix used in
     *            installFileSystemHandler
     * @param statBuffer The buffer
     * @param flags Contains information about the file
     * @return 0 if successful, non-zero otherwise
     * @throws IOException in case of error
     */
    @Override
    public int stat(String filename, VSIStatBuf statBuffer, int flags) throws IOException {
        if(filename.startsWith(this.getPrefix())) {
            filename = filename.substring(this.getPrefix().length());
            final File file = new File(filename);
            if(!IOProviderFactory.exists(file))
                return -1;

            synchronized(VSIFileFileSystemHandler.class) {
                if(DEFAULT_STAT == null) {
                    File f = FileSystemUtils.getItem(".stat");
                    f.createNewFile();
                    try {
                        DEFAULT_STAT = Os.stat(f.getAbsolutePath());
                    } catch (ErrnoException e) {
                        Log.w(TAG, "Failed to obtain default stat struct", e);
                    }
                }
            }

            statBuffer.st_size = IOProviderFactory.length(file);
            statBuffer.st_atime = IOProviderFactory.lastModified(file);
            statBuffer.st_blksize = 512; // use typical default
            statBuffer.st_blocks = (statBuffer.st_size/statBuffer.st_blksize);
            if(statBuffer.st_size%statBuffer.st_blksize != 0)
                statBuffer.st_blocks++;
            statBuffer.st_ctime = IOProviderFactory.lastModified(file);
            statBuffer.st_mtime = IOProviderFactory.lastModified(file);
            if(DEFAULT_STAT != null) {
                statBuffer.st_uid = DEFAULT_STAT.st_uid;
                statBuffer.st_dev = DEFAULT_STAT.st_dev;
                statBuffer.st_rdev = DEFAULT_STAT.st_rdev;
                statBuffer.st_gid = DEFAULT_STAT.st_gid;
                statBuffer.st_ino = DEFAULT_STAT.st_ino;
            } else {
                // XXX - we'll fill with some values, hopefully GDAL doesn't
                //       care that much given that this is a virtual file
                //       system????
                statBuffer.st_uid = 0;
                statBuffer.st_dev = 0;
                statBuffer.st_rdev = 0;
                statBuffer.st_gid = 0;
                statBuffer.st_ino = 0;
            }
            statBuffer.st_nlink = 0; // no links
            statBuffer.st_mode = 0x666; // r+w
            return 0;
        }
        return -1;
    }
}
