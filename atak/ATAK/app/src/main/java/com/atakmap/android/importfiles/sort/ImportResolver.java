
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Pair;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileFilter;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.List;
import java.util.Set;

/**
 * Matches a file based on filename, content, or other properties. Also imports or 
 * initiates import
 * 
 * 
 */
public abstract class ImportResolver {

    // Enum of flags to be used in an enum set
    // that wll be passed into Sorters when an import
    // begins or when it is finished. Use these flags
    // to configure options that might not be necessary for
    // all places were the ImportResolver interface is used.
    public enum SortFlags {
        SHOW_NOTIFICATIONS, // Show notifications related to the import process
        ZOOM_TO_FILE, // Zoom to the file when it's finished importing
        HIDE_FILE, // Turn file visibility off by default
        //IMPORT_STRATEGY_TYPES,
        //IMPORT_STRATEGY_INDIVIDUAL,
        //IMPORT_STRATEGY_LEGACY
    }

    private static final String TAG = "ImportResolver";

    protected final String _ext;
    protected boolean _bValidateExt;

    /**
     * True to copy (leave original file in place), False to move file to new location (potentially
     * more efficient). Note if false and move fails, it will revert to copy attempt
     */
    protected boolean _bCopyFile;

    protected final String _folderName;

    protected boolean _bFileSorted = false;

    /**
     * File extension to match one
     */
    protected FileFilter _filter;

    public ImportResolver(String ext, String folderName, boolean validateExt,
            boolean copyFile) {
        this._ext = FileSystemUtils.isEmpty(ext) ? null
                : ext
                        .toLowerCase(LocaleUtil.getCurrent());
        this._folderName = folderName;
        setOptions(validateExt, copyFile);
    }

    /**
     * Set the options for this resolver
     *
     * @param validateExt
     * @param copyFile
     */
    public void setOptions(boolean validateExt, boolean copyFile) {
        this._bValidateExt = validateExt;
        this._bCopyFile = copyFile;

        if (_ext != null && _bValidateExt) {
            _filter = new FileFilter() {

                @Override
                public boolean accept(File pathname) {
                    if (pathname == null)
                        return false;

                    return pathname.getName()
                            .toLowerCase(LocaleUtil.getCurrent())
                            .endsWith(_ext);
                }
            };
        } else {
            _filter = null;
        }
    }

    public String getExt() {
        return _ext;
    }

    /**
     * Returns if the file is sorted or not.
     * @return true if the file is sorted
     */
    public boolean getFileSorted() {
        return _bFileSorted;
    }

    /**
     * Return true if this sort matches the specified file
     * 
     * @param file the file to read to see if the import resolver supports the file.
     * @return true if the import resolver is capable of handling the file.
     */
    public boolean match(final File file) {
        if (!_bValidateExt)
            return true;

        if (_filter == null)
            return false;

        return _filter.accept(file);
    }

    /**
     * Provides for a capability by which a set of resolvers can be trimmed by a found sorter
     * implementation.    This provides great power to the sorter to force itself to be used over
     * a group of other resolvers.    Plugin implementers should take great caution in using this
     * only when it is guaranteed to be the correct behavior.
     *
     * @param importResolvers the list of import resolvers that to filter.    The modification are
     *                        made directly to the importResolvers list.
     * @param file the file that is being considered for the resolvers.
     */
    public void filterFoundResolvers(final List<ImportResolver> importResolvers,
            File file) {
        // no default filtering occurs
    }

    /**
     * Initiate import of the specified file
     * 
     * @param file File to import
     * @param flags Enum Set of flags that should be used to modify import behavior.
     * @return True if the import is successful, false otherwise.
     */
    public abstract boolean beginImport(File file, Set<SortFlags> flags);

    /**
    * Initiate import of the specified file
    * 
    * @param file File to import.
    * @return True if the import is successful, false otherwise
    */
    public abstract boolean beginImport(File file);

    /**
     * Perform any resolver specific cleanup
     */
    public void finalizeImport() {
    }

    public abstract File getDestinationPath(File file);

    /**
     * Invoked after the file has been successfully copied or moved to
     * initiate import.
     * 
     * <P>
     * The default implementation returns immediately.
     * 
     * @param src The original file
     * @param dst The file resulting from the copy or move.
     * @param flags Enum set of flags that the user wishes to use to modify
     * sort behavior.
     */
    protected void onFileSorted(final File src, final File dst,
            Set<SortFlags> flags) {

        _bFileSorted = true;

        MapView mv = MapView.getMapView();
        if (flags.contains(SortFlags.SHOW_NOTIFICATIONS) && mv != null) {
            Intent i = new Intent(ImportExportMapComponent.ZOOM_TO_FILE_ACTION);
            i.putExtra("filepath", dst.getAbsolutePath());
            Context ctx = mv.getContext();
            NotificationUtil.getInstance().postNotification(
                    NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                    NotificationUtil.BLUE,
                    ctx.getString(R.string.importmgr_finished_import,
                            src.getName()),
                    null, null, i);
            Log.d(TAG,
                    "Finished copying file, waiting for import code to process: "
                            + src.getAbsolutePath());
        }

        // Fire sort listeners
        List<ImportListener> importers = ImportExportMapComponent.getInstance()
                .getImportListeners();
        for (ImportListener l : importers)
            l.onFileSorted(src, dst);
    }

    @Override
    public String toString() {
        return String.format("Sorting %s, to %s", _ext, _folderName);
    }

    /**
     * Are directories supported by {@link #match(File)} and {@link #beginImport(File)}?
     * @return True if directories can be sorted by this class, false otherwise.
     */
    public boolean directoriesSupported() {
        return false;
    }

    /**
     * Get a human readable string that can be displayed to the user so that
     * they can differentiate between different sorter implementations. This
     * string MUST be unique to a sorter, and any duplicates will likely
     * be removed before the user gets a chance to see them.
     * @return Unique (by class, not instance), human readable name for this sorter. 
     */
    public abstract String getDisplayableName();

    /**
     * Get an icon that represents this sorter
     * @return Icon or null to use a generic icon
     */
    public Drawable getIcon() {
        MapView mv = MapView.getMapView();
        return mv != null ? mv.getContext().getDrawable(
                R.drawable.ic_menu_import_file) : null;
    }

    /**
     * Get the content and MIME type
     * @return Pair containing the content type and MIME type respectively
     */
    public Pair<String, String> getContentMIME() {
        return null;
    }
}
