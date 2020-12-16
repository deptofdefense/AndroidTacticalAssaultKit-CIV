
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Pair;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.locale.LocaleUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Sorts Video Files.
 */
public class ImportVideoSort extends ImportResolver {

    private static final String TAG = "ImportVideoSort";

    private static final String CONTENT_TYPE = "Video File";

    public final static Set<String> VIDEO_EXTENSIONS = new HashSet<>(
            Arrays.asList("mpeg", "mpg", "ts", "avi", "mp4",
                    "264", "265", "wmv", "mov", "webm",
                    "mov", "mkv", "flv"));

    private final Context _context;

    public ImportVideoSort(Context context, String ext, boolean validateExt,
            boolean copyFile) {
        super(ext, null, validateExt, copyFile);
        _context = context;
    }

    public ImportVideoSort(Context context, boolean validateExt,
            boolean copyFile) {
        this(context, null, validateExt, copyFile);
    }

    @Override
    public boolean match(File file) {

        // Check against default video extensions if this sorter does not
        // specify a single extension
        if (_bValidateExt && _ext == null) {
            String ext = FileSystemUtils.getExtension(file, false, false)
                    .toLowerCase(LocaleUtil.getCurrent());
            if (!VIDEO_EXTENSIONS.contains(ext))
                return false;
        }

        // Default matching
        else if (!super.match(file))
            return false;

        // TODO: Check if the file is actually a video - otherwise this sorter
        //  is useless when validateExt = false
        return true;
    }

    @Override
    public boolean beginImport(File file) {
        return beginImport(file, Collections.<SortFlags> emptySet());
    }

    @Override
    public boolean beginImport(File file, Set<SortFlags> flags) {
        File dest = getDestinationPath(file);
        if (file.equals(dest)) {
            return true;
        }

        if (_bCopyFile)
            try {
                FileSystemUtils.copyFile(file, dest);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy file: " + dest.getAbsolutePath(), e);
                return false;
            }
        else {
            if (!FileSystemUtils.renameTo(file, dest)) {
                return false;
            }
        }

        onFileSorted(file, dest, flags);

        File atakdata = new File(_context.getCacheDir(),
                FileSystemUtils.ATAKDATA);
        if (file.getAbsolutePath().startsWith(atakdata.getAbsolutePath())
                && IOProviderFactory.delete(file, IOProvider.SECURE_DELETE))
            Log.d(TAG,
                    "deleted imported video: " + file.getAbsolutePath());

        return true;
    }

    @Override
    public String getDisplayableName() {
        return _context.getString(R.string.video);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_video_alias);
    }

    @Override
    public File getDestinationPath(File file) {
        if (file == null)
            return null;
        return new File(FileSystemUtils.getItem("tools/videos"),
                file.getName());
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(CONTENT_TYPE, ResourceFile.UNKNOWN_MIME_TYPE);
    }
}
