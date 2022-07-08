
package com.atakmap.android.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.importfiles.ui.ImportManagerFileBrowser;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.annotations.DeprecatedApi;

// to properly inflate the view no matter what is displaying it 
import com.atakmap.android.maps.MapView;

import java.io.File;

/**
 * File browser within a dialog
 */
public class ImportFileBrowserDialog {

    public static final String TAG = "ImportFileBrowserDialog";

    private final Context _context;
    private String _title, _startPath;
    private String[] _exts;
    private boolean _useProvider;
    private DialogDismissed _dismissListener;

    /**
     * Construct an import file browser dialog.
     * @param mapView the mapView used to derive the context
     * @deprecated see {@link #ImportFileBrowserDialog(Context)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.6.0", forRemoval = true, removeAt = "4.9")
    public ImportFileBrowserDialog(MapView mapView) {
        _context = mapView.getContext();
    }

    /**
     * Construct and ImportFileBrowserDialog with the context.
     * @param context the context to use for the import file browser.
     */
    public ImportFileBrowserDialog(Context context) {
        _context = context;
    }

    /**
     * Set the title of the dialog
     * @param title Title string
     * @return Dialog
     */
    public ImportFileBrowserDialog setTitle(String title) {
        _title = title;
        return this;
    }

    /**
     * Set which file extensions should be displayed by the browser
     * @param exts File extensions
     * @return Dialog
     */
    public ImportFileBrowserDialog setExtensionTypes(String... exts) {
        _exts = exts;
        return this;
    }

    /**
     * Set the default directory to display when opening this browser
     * @param path Path to directory
     * @return Dialog
     */
    public ImportFileBrowserDialog setStartDirectory(String path) {
        _startPath = path;
        return this;
    }

    public ImportFileBrowserDialog setStartDirectory(File dir) {
        return setStartDirectory(dir.getAbsolutePath());
    }

    /**
     * Set whether to use the file IO provider proxy or the default
     * @param useIoProvider True to use the proxy provider, false to use the default
     * @return Dialog
     */
    public ImportFileBrowserDialog setUseProvider(boolean useIoProvider) {
        _useProvider = useIoProvider;
        return this;
    }

    /**
     * Show the dialog
     */
    public void show() {
        show(_title, _startPath, _exts, _dismissListener, _context,
                _useProvider);
    }

    /**
     * Set a listener to be used when a file is selected or the dialog is closed
     * @param l Listener
     * @return Dialog
     */
    public ImportFileBrowserDialog setOnDismissListener(DialogDismissed l) {
        _dismissListener = l;
        return this;
    }

    public interface DialogDismissed {
        /**
         * When the file dialog is dismissed, return the selected file, null if no file was selected.
         */
        void onFileSelected(File f);

        /**
         * The dialog can be closed via back press - nothing selected, but onFileSelected not 
         * triggered.  Provides a way to be aware of dialog closing.
         */
        void onDialogClosed();

    }

    /**
     * Produce a file browser without a defined location, uses the last recorded location.
     */
    synchronized static public void show(final String title,
            final String[] extensionTypes,
            final DialogDismissed dismissed,
            final Context context) {
        show(title, null, extensionTypes, dismissed, context);
    }

    /**
     * Produce a file browser with a defined location.
     */
    synchronized static public void show(final String title,
            final String location,
            final String[] extensionTypes,
            final DialogDismissed dismissed,
            final Context context) {
        showHelper(title, location, extensionTypes, dismissed, context, true);
    }

    /**
     * Produce a file browser without a defined location, uses the last recorded location and
     * whether or not a IO Provider should be used.
     *
     * @param useProvider   if <code>true</code>, the
     *                      {@link com.atakmap.coremap.io.IOProviderFactory IOProviderFactory}
     *                      will be used for all filesystem interactions, else default
     *                      filesystem IO will be utilized.
     */
    synchronized static public void show(final String title,
            final String[] extensionTypes,
            final DialogDismissed dismissed,
            final Context context, boolean useProvider) {
        showHelper(title, null, extensionTypes, dismissed, context,
                useProvider);
    }

    /**
     * Produce a file browser with a defined location and whether or not a IO Provider should be used.
     *
     * @param useProvider   if <code>true</code>, the
     *                      {@link com.atakmap.coremap.io.IOProviderFactory IOProviderFactory}
     *                      will be used for all filesystem interactions, else default
     *                      filesystem IO will be utilized.
     */
    synchronized static public void show(final String title,
            final String location,
            final String[] extensionTypes,
            final DialogDismissed dismissed,
            final Context context, boolean useProvider) {
        showHelper(title, location, extensionTypes, dismissed, context,
                useProvider);
    }

    /**
     * Helper method for showing the dialog
     */
    private synchronized static void showHelper(final String title,
            final String location,
            final String[] extensionTypes,
            final DialogDismissed dismissed,
            final Context context, boolean useProvider) {

        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        Context mapCtx = mv.getContext();
        final ImportManagerFileBrowser importFileBrowser = ImportManagerFileBrowser
                .inflate(mv);
        importFileBrowser.setUseProvider(useProvider);
        final SharedPreferences defaultPrefs = PreferenceManager
                .getDefaultSharedPreferences(mv.getContext());

        if (location != null) {
            importFileBrowser.setStartDirectory(location);
        } else {
            importFileBrowser
                    .setStartDirectory(ATAKUtilities.getStartDirectory(mapCtx));
        }

        if (extensionTypes == null)
            importFileBrowser.setExtensionTypes(new String[] {
                    "*"
            });
        else
            importFileBrowser.setExtensionTypes(extensionTypes);

        importFileBrowser.setMultiSelect(false);

        // use the user supplied context for display purposes
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(title);
        b.setView(importFileBrowser);
        b.setNegativeButton(mapCtx.getString(R.string.cancel), null);
        AlertDialog d = b.create();
        importFileBrowser.setAlertDialog(d);

        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(final DialogInterface dialog) {
                final File f = importFileBrowser.getReturnFile();
                if (f != null) {
                    // the location was not passed in, so we want to save it for next time.
                    if (location == null) {
                        defaultPrefs.edit()
                                .putString("lastDirectory", f.getParent())
                                .apply();
                    }
                    // provide for notification of the file selected, null if no file selected
                    if (dismissed != null)
                        dismissed.onFileSelected(f);
                } else {
                    if (dismissed != null)
                        dismissed.onDialogClosed();
                }
                importFileBrowser.setUseProvider(true);
            }
        });

        // XXX - https://atakmap.com/bugzilla3/show_bug.cgi?id=2838
        // Based on the literature, people recommend using isFinishing() to overcome this, but the
        // context that has been passed in is the MapView context, so I really doubt that at the point of this
        // code it is really finishing.     (but possibly?)   Low risk try catch block for 2.2.
        try {
            if (!((Activity) context).isFinishing())
                d.show();
        } catch (Exception e) {
            Log.e(TAG, "bad bad bad", e);
        }
    }
}
