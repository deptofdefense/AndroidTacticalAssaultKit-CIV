
package com.atakmap.android.importfiles.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import androidx.annotation.NonNull;
import android.text.Html;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.atakmap.android.gui.ImportFileBrowser;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Extended version of the ImportFileBrowser that can be used to select multiple files
 * and includes an up button within the view instead of requiring an external one.
 */
public class ImportManagerFileBrowser extends ImportFileBrowser implements
        DialogInterface.OnKeyListener {
    private static final String TAG = "ImportManagerFileBrowser";

    protected final Set<File> _selectedItems = new HashSet<>();
    protected boolean _multiSelect = true;

    public static ImportManagerFileBrowser inflate(MapView mv) {
        return (ImportManagerFileBrowser) LayoutInflater.from(mv.getContext())
                .inflate(R.layout.import_manager_file_browser, mv, false);
    }

    public ImportManagerFileBrowser(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImportManagerFileBrowser(Context context) {
        super(context);
    }

    public void setTitle(String title) {
        View titleView = findViewById(R.id.importManagerFileBrowserTitle);
        if (titleView != null)
            titleView.setVisibility(View.VISIBLE);
        TextView titleTV = findViewById(
                R.id.importManagerFileBrowserTitleText);
        if (titleTV != null)
            titleTV.setText(title);
    }

    public void setTitle(int titleId) {
        setTitle(getContext().getString(titleId));
    }

    public void setMultiSelect(boolean multiSelect) {
        _multiSelect = multiSelect;
        if (_adapter != null)
            _adapter.notifyDataSetChanged();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // After the view finishes inflating, attach an on click
        // handler to the up/back button next to the current directory.
        ImageButton upButton = this
                .findViewById(R.id.importManagerFileBrowserUpButton);
        if (upButton != null)
            setUpButton(upButton);

        ImageButton internalButton = this.findViewById(R.id.phone);
        if (internalButton != null)
            setInternalButton(internalButton);

        ImageButton externalButton = this.findViewById(R.id.sdcard);
        if (externalButton != null)
            setExternalButton(externalButton);

        ImageButton newFolderBtn = findViewById(R.id.newFolderBtn);
        if (newFolderBtn != null)
            setNewFolderButton(newFolderBtn);
    }

    @Override
    protected void _createAdapter() {
        // Create an adapter that can handle selection via check boxes.
        _adapter = new FileItemAdapter(getContext(),
                R.layout.import_manager_file_browser_fileitem,
                _fileList);
    }

    @Override
    public void setAlertDialog(AlertDialog alert) {
        super.setAlertDialog(alert);

        // This will take over the handling of events for the Back
        // button while the dialog is opened.
        alert.setOnKeyListener(this);
    }

    /**
     * Returns a list containing all of the files and directories
     * that the user has selected using this dialog. If no items
     * have been selected, an empty list will be returned.
     * @return List of selected files, or empty list if nothing was 
     * selected.
     */
    public List<File> getSelectedFiles() {
        return new ArrayList<>(_selectedItems);
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        // Handle the "UP" event for the device's back button. This hijacks the
        // "Cancel" behavior that is normally present in a dialog, and instead
        // treats a press as an indication that the user would like to move
        // up one directory.
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            _navigateUpOneDirectory();
            return true;
        }
        return false;
    }

    protected class FileItemAdapter extends ArrayAdapter<FileItem> {

        protected final MapView _mapView;
        protected final Context _mapCtx;

        protected FileItemAdapter(Context layoutContext, int resourceId,
                List<FileItem> items) {
            super(layoutContext, resourceId, items);
            _mapView = MapView.getMapView();
            _mapCtx = _mapView != null ? _mapView.getContext() : layoutContext;
        }

        @Override
        @NonNull
        public View getView(int position, View row, @NonNull ViewGroup parent) {
            final FileItem fileItem = getItem(position);

            ViewHolder h = row != null && row.getTag() instanceof ViewHolder
                    ? (ViewHolder) row.getTag()
                    : null;
            if (h == null) {
                h = createViewHolder(parent);
                row = h.root;
                row.setTag(h);
            }
            h.fileItem = fileItem;
            h.root.setOnClickListener(h);
            h.root.setOnLongClickListener(h);
            h.selected.setOnClickListener(h);

            if (fileItem == null)
                return row;

            // Handle the special case where a directory does not contain any files.
            // This changes the list to simply display an item that says "Directory is Empty"
            // without checkboxes or icons.
            if (_directoryEmpty) {
                h.icon.setVisibility(View.GONE);
                h.fileName
                        .setText(Html.fromHtml("<i>" + fileItem.file + "</i>"));
                h.fileInfo.setText("");
                h.selected.setVisibility(View.GONE);
                return row;
            } else {
                h.icon.setVisibility(View.VISIBLE);
                h.selected
                        .setVisibility(_multiSelect ? View.VISIBLE : View.GONE);
            }

            if (_multiSelect) {
                // If this item has been selected, update the checkbox state,
                // otherwise uncheck it.
                File checkFile = new File(_path, fileItem.file);
                try {
                    checkFile = checkFile.getCanonicalFile();
                } catch (Exception ignore) {
                }
                h.selected.setChecked(_selectedItems.contains(checkFile));

                // If one of the file's parents has been selected, disable the checkbox
                // so that it can't be toggled by itself. also make the box checked
                // to indicate that the user can't mess with them independent of the
                // parent.
                if (h.isParentSelected()) {
                    h.selected.setChecked(true);
                    h.selected.setEnabled(false);
                } else {
                    h.selected.setEnabled(true);
                }
            }

            File f = new File(_path, fileItem.file);
            h.icon.setImageDrawable(fileItem.iconDr);
            h.fileName.setText(f.getName());
            if (fileItem.type == FileItem.DIRECTORY) {
                // Filter out undesired file types
                String[] children = ioProvider.list(f, _fileFilter);
                if (FileSystemUtils.isEmpty(children))
                    h.fileInfo.setText(_mapCtx.getString(
                            R.string.importmgr_count_empty));
                else
                    h.fileInfo.setText(_mapCtx.getString(
                            R.string.importmgr_count_items, children.length));
            } else
                h.fileInfo.setText(
                        MathUtils.GetLengthString(ioProvider.length(f)));

            return row;
        }
    }

    protected final FilenameFilter _fileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String fileName) {
            File sel = new File(dir, fileName);
            String ext = StringUtils.substringAfterLast(fileName,
                    ".");
            if (isFile(sel))
                return (_testExtension(ext)) && ioProvider.canRead(sel);
            return ioProvider.canRead(sel) && ioProvider.isDirectory(sel);
        }
    };

    protected ViewHolder createViewHolder(ViewGroup parent) {
        ViewHolder h = new ViewHolder();
        h.root = LayoutInflater.from(getContext()).inflate(
                R.layout.import_manager_file_browser_fileitem, parent, false);
        h.icon = h.root.findViewById(
                R.id.importManagerBrowserIcon);
        h.fileName = h.root.findViewById(
                R.id.importManagerBrowserFileName);
        h.fileInfo = h.root.findViewById(
                R.id.importManagerBrowserFileInfo);
        h.selected = h.root.findViewById(
                R.id.importManagerBrowserFileSelected);
        return h;
    }

    public class ViewHolder implements View.OnClickListener,
            View.OnLongClickListener {

        public View root;
        public ImageView icon;
        public TextView fileName, fileInfo;
        public CheckBox selected;
        public FileItem fileItem;

        // Check to see if a checkbox is checked or not, and update
        // the selectedItems set accordingly.
        protected void recordCheckBox() {
            boolean isChecked = selected.isChecked();
            try {
                File selectedFile = new File(_path, fileItem.file)
                        .getCanonicalFile();
                if (isChecked) {
                    // If the newly checked file is the parent of a file that
                    // is already in the selectedItems list, remove that item
                    // from the selectedItems list to avoid importing items
                    // twice.
                    for (Iterator<File> it = _selectedItems.iterator(); it
                            .hasNext();) {
                        File next = it.next();
                        if (isParent(selectedFile, next)) {
                            it.remove();
                        }
                    }
                    _selectedItems.add(selectedFile);
                } else {
                    _selectedItems.remove(selectedFile);
                }
            } catch (Exception e) {
                Log.e(TAG, "Couldn't find canonical file.", e);
            }
        }

        protected boolean isParent(File possibleParent, File file) {
            if (!ioProvider.exists(possibleParent)
                    || !ioProvider.isDirectory(possibleParent) ||
                    possibleParent.equals(file)) {
                // this cannot possibly be the parent
                return false;
            }

            File possibleChild = file.getParentFile();
            while (possibleChild != null) {
                if (possibleChild.equals(possibleParent)) {
                    return true;
                }
                possibleChild = possibleChild.getParentFile();
            }

            // No match found, and we've hit the root directory
            return false;
        }

        protected boolean isParentSelected() {
            try {
                File fileToTest = new File(_path, fileItem.file)
                        .getCanonicalFile();
                for (File f : _selectedItems) {
                    if (isParent(f, fileToTest))
                        return true;
                }
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Couldn't check for parent.", e);
                return false;
            }
        }

        @Override
        public void onClick(View v) {
            if (fileItem == null || _directoryEmpty)
                return;
            if (v == selected) {
                recordCheckBox();
                return;
            }
            _currFile = fileItem.file;
            File sel = new File(_path, _currFile);
            if (ioProvider.isDirectory(sel)) {
                if (ioProvider.canRead(sel)) {
                    _pathDirsList.add(_currFile);
                    _path = new File(sel + "");
                    _loadFileList();
                    _adapter.notifyDataSetChanged();
                    _updateCurrentDirectoryTextView();
                    _scrollListToTop();
                }
            } else {
                // Clicking on an item that is not a directory
                // treats the click the same as if the user had clicked
                // on the entry's checkbox.
                onLongClick(root);
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (fileItem == null || _directoryEmpty)
                return false;
            _currFile = fileItem.file;
            if (!_multiSelect) {
                File sel = new File(_path, _currFile);
                _returnFile(sel);
            } else if (selected != null) {
                selected.toggle();
                recordCheckBox();
            }
            return true;
        }
    }
}
