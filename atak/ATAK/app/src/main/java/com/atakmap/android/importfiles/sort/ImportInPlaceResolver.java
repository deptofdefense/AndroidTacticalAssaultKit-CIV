
package com.atakmap.android.importfiles.sort;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Pair;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importexport.Marshal;
import com.atakmap.android.ipc.AtakBroadcast;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * Import Resolver that optionally leaves files in place to import from
 * source location
 * 
 * 
 */
public abstract class ImportInPlaceResolver extends ImportInternalSDResolver {

    /**
     * True to import from source location, false to move or copy based on _bCopyFile
     */
    final boolean _bImportInPlace;

    /**
     * Import In place, or move/copy. So to import in place use:
     *    copyFile=false,importInPlace=true
     *  Or to move into atak dir and remove original file use:
     *    copyFile=false,importInPlace=false
     * 
     * @param ext
     * @param folderName
     * @param validateExt
     * @param copyFile  true copy, false to move (only used importInPlace=false)
     * @param importInPlace true to import from source location, false to copy/move into atak data dir
     */
    public ImportInPlaceResolver(String ext, String folderName,
            boolean validateExt, boolean copyFile, boolean importInPlace,
            String displayName, Drawable icon) {
        super(ext, folderName, validateExt, copyFile, displayName, icon);
        _bImportInPlace = importInPlace;
    }

    public ImportInPlaceResolver(String ext, String folderName,
            boolean validateExt, boolean copyFile, boolean importInPlace,
            String displayName) {
        this(ext, folderName, validateExt, copyFile, importInPlace, displayName,
                null);
    }

    /**
     * Does not move a file, just invokes onFileSorted to import from source location
     * 
     * @param file the file to import
     * @return true if the file was successfully imported
     */
    @Override
    public boolean beginImport(File file) {
        return beginImport(file, Collections.<SortFlags> emptySet());
    }

    @Override
    public boolean beginImport(File file, Set<SortFlags> flags) {
        if (_bImportInPlace) {
            onFileSorted(file, file, flags);
            return true;
        } else {
            return super.beginImport(file, flags);
        }
    }

    @Override
    public File getDestinationPath(File file) {
        if (_bImportInPlace) {
            return file;
        } else {
            return super.getDestinationPath(file);
        }
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        Pair<String, String> contentMIME = getContentMIME();
        if (contentMIME == null) {
            super.onFileSorted(src, dst, flags);
            return;
        }
        Intent i = new Intent(ImportExportMapComponent.ACTION_IMPORT_DATA);
        i.putExtra(ImportReceiver.EXTRA_CONTENT, contentMIME.first);
        i.putExtra(ImportReceiver.EXTRA_MIME_TYPE, contentMIME.second);
        i.putExtra(ImportReceiver.EXTRA_URI, Uri.fromFile(dst).toString());
        if (flags.contains(SortFlags.SHOW_NOTIFICATIONS))
            i.putExtra(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS, true);
        if (flags.contains(SortFlags.ZOOM_TO_FILE))
            i.putExtra(ImportReceiver.EXTRA_ZOOM_TO_FILE, true);
        if (flags.contains(SortFlags.HIDE_FILE))
            i.putExtra(ImportReceiver.EXTRA_HIDE_FILE, true);
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    public static ImportResolver fromMarshal(final Marshal m, Drawable icon) {
        return new ImportInPlaceResolver(null, null, false, false, true,
                m.getContentType(), icon) {
            @Override
            public boolean match(File f) {
                try {
                    return m.marshal(Uri.fromFile(f)) != null;
                } catch (IOException e) {
                    return false;
                }
            }

            @Override
            protected void onFileSorted(File src, File dst,
                    Set<SortFlags> flags) {
                final Uri uri = Uri.fromFile(dst);
                String mime;
                try {
                    mime = m.marshal(uri);
                } catch (IOException e) {
                    mime = null;
                }
                if (mime == null) {
                    super.onFileSorted(src, dst, flags);
                    return;
                }

                Intent i = new Intent(
                        ImportExportMapComponent.ACTION_IMPORT_DATA);
                i.putExtra(ImportReceiver.EXTRA_CONTENT, m.getContentType());
                i.putExtra(ImportReceiver.EXTRA_MIME_TYPE, mime);
                i.putExtra(ImportReceiver.EXTRA_URI, uri.toString());
                if (flags.contains(SortFlags.SHOW_NOTIFICATIONS))
                    i.putExtra(ImportReceiver.EXTRA_SHOW_NOTIFICATIONS, true);
                if (flags.contains(SortFlags.ZOOM_TO_FILE))
                    i.putExtra(ImportReceiver.EXTRA_ZOOM_TO_FILE, true);
                if (flags.contains(SortFlags.HIDE_FILE))
                    i.putExtra(ImportReceiver.EXTRA_HIDE_FILE, true);
                AtakBroadcast.getInstance().sendBroadcast(i);
            }

            @Override
            public Pair<String, String> getContentMIME() {
                return new Pair<>(m.getContentType(),
                        ResourceFile.UNKNOWN_MIME_TYPE);
            }
        };
    }

    public static ImportResolver fromMarshal(Marshal m) {
        return fromMarshal(m, null);
    }
}
