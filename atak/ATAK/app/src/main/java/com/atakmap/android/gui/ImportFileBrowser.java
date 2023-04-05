
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import androidx.annotation.NonNull;

import android.provider.MediaStore;
import android.provider.MediaStore.Images.Thumbnails;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.filesystem.ResourceFile.MIMEType;
import com.atakmap.android.icons.IconsMapAdapter;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.vehicle.model.icon.PendingDrawable;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.atakmap.coremap.filesystem.RemovableStorageHelper;
import com.atakmap.coremap.io.DefaultIOProvider;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.TimeZone;

//Based on code from
//https://github.com/mburman/Android-File-Explore

public class ImportFileBrowser extends LinearLayout implements
        AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private static final String TAG = "ImportFileBrowser";

    /*************************** PRIVATE FIELDS **************************/
    private final Set<String> _extensions = new HashSet<>();
    private String _userStartDirectory;
    private File _retFile;
    private View _up, _newFolder;
    private static final String FILE_SEPARATOR = File.separator;
    private static final String INITIAL_DIRECTORY = FileSystemUtils.getRoot()
            + FILE_SEPARATOR;
    protected AlertDialog _alert;

    /*************************** PROTECTED FIELDS **************************/
    protected final ArrayList<String> _pathDirsList = new ArrayList<>();
    protected final List<FileItem> _fileList = new ArrayList<>();
    protected File _path;
    protected String _currFile;
    protected ArrayAdapter<FileItem> _adapter;
    protected boolean _directoryEmpty;
    protected IOProvider ioProvider = IOProviderFactoryProxy.INSTANCE;
    protected final Map<String, Integer> _dirCounts = new HashMap<>();

    protected final FilenameFilter _fileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String fileName) {
            String ext = StringUtils.substringAfterLast(fileName, ".");
            File f = new File(dir, fileName);
            return ioProvider.canRead(f) && (_testExtension(ext)
                    || ioProvider.isDirectory(f));
        }
    };

    protected final ExecutorService _thumbLoader = Executors.newFixedThreadPool(
            5, new NamedThreadFactory("ThumbLoadPool"));

    /*************************** PUBLIC FIELDS **************************/
    public static final String WILDCARD = "*";

    /*************************** CONSTRUCTORS **************************/

    public ImportFileBrowser(Context context) {
        super(context);
    }

    public ImportFileBrowser(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /*************************** PUBLIC METHODS **************************/

    public void setUpButton(View upBtn) {
        _up = upBtn;
        _up.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                _navigateUpOneDirectory();
            }
        });
    }

    public void setInternalButton(View phoneButton) {
        phoneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                setCurrentPath(Environment.getExternalStorageDirectory());
            }
        });
    }

    public void setExternalButton(View sdcardButton) {
        sdcardButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                final String android_storage = RemovableStorageHelper
                        .getRemovableStorageDirectory()[0];

                if (android_storage != null)
                    setCurrentPath(new File(android_storage));
            }
        });
    }

    public void setCurrentPath(File path) {
        _path = path;
        _parseDirectoryPath();
        _loadFileList();
        _adapter.notifyDataSetChanged();
        _updateCurrentDirectoryTextView();
        _scrollListToTop();
    }

    public void setNewFolderButton(View folderBtn) {
        _newFolder = folderBtn;
        _newFolder.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (FileSystemUtils.canWrite(_path)) {
                    promptNewDirectory();
                } else {
                    MapView mv = MapView.getMapView();
                    if (mv != null)
                        Toast.makeText(mv.getContext(),
                                R.string.read_only_location_msg,
                                Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Allow for a user of the ImportFileBrowser to show the New Folder button.
     * @param show true, shows the new folder button.
     */
    public void showNewFolderButton(boolean show) {
        if (_newFolder != null)
            _newFolder.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Gets the selected file.
     * @return the file
     */
    public File getReturnFile() {
        return _retFile;
    }

    public void setAlertDialog(AlertDialog alert) {
        _alert = alert;
    }

    /** 
     * Clears a previously selected file.
     */
    public void clear() {
        _retFile = null;
    }

    public void allowAnyExtenstionType() {
        this.setExtensionType(WILDCARD);
    }

    /**
     * Note must currently be called before _init();
     * 
     * @param path
     */
    public void setStartDirectory(String path) {
        _userStartDirectory = path;
    }

    /**
     * Get the current path shown in the browser
     *
     * @return Current path
     */
    public String getCurrentPath() {
        return _path != null ? _path.getAbsolutePath()
                : FileSystemUtils.getRoot().getAbsolutePath();
    }

    /**
     * Note, setExtensionType or setExtensionTypes must be called prior to using the view
     *
     * @param extension
     */
    public void setExtensionType(String extension) {
        this.setExtensionTypes(new String[] {
                extension
        });
    }

    /**
     * Note, setExtensionType or setExtensionTypes must be called prior to using the view
     *
     * @param extensions
     */
    public void setExtensionTypes(String[] extensions) {
        _extensions.clear();
        for (String ext : extensions) {
            if (ext.equals(WILDCARD)) {
                // Wildcard means no filtering
                // Leave extensions empty
                _extensions.clear();
                return;
            }
            _extensions.add(ext.toLowerCase(LocaleUtil.getCurrent()));
        }
    }

    public static String getModifiedDate(Long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm:ss",
                LocaleUtil.getCurrent());
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(time);
    }

    public void setUseProvider(boolean useIoProvider) {
        if (useIoProvider)
            ioProvider = IOProviderFactoryProxy.INSTANCE;
        else
            ioProvider = new DefaultIOProvider();
    }

    /*************************** PROTECTED METHODS **************************/

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        _init();
    }

    protected void _init() {
        _setInitialDirectory();
        _parseDirectoryPath();
        _loadFileList();
        _createAdapter();
        _initButtons();
        _initFileListView();
        _updateCurrentDirectoryTextView();
    }

    protected final File[] listFiles(File f, FilenameFilter filter) {
        String[] list = ioProvider.list(f, filter);
        if (list == null)
            return null;
        File[] retval = new File[list.length];
        for (int i = 0; i < list.length; i++)
            retval[i] = new File(f, list[i]);
        return retval;
    }

    protected final boolean isFile(File f) {
        return ioProvider.exists(f) && !ioProvider.isDirectory(f);
    }

    protected void _loadFileList() {
        MapView mv = MapView.getMapView();
        Context ctx = mv != null ? mv.getContext() : getContext();
        _fileList.clear();
        _dirCounts.clear();
        if (ioProvider.exists(_path) && ioProvider.canRead(_path)) {
            File[] fList = listFiles(_path, _fileFilter);

            _directoryEmpty = false;

            if (fList != null) {
                for (File f : fList) {
                    Drawable icon = ctx
                            .getDrawable(R.drawable.import_file_icon);
                    int type = FileItem.FILE;
                    if (ioProvider.isDirectory(f)) {
                        icon = ctx.getDrawable(R.drawable.import_folder_icon);
                        type = FileItem.DIRECTORY;
                    } else {
                        MIMEType mt = ResourceFile.getMIMETypeForFile(
                                f.getName());
                        if (mt != null) {
                            Bitmap b = ATAKUtilities.getUriBitmap(mt.ICON_URI);
                            if (b != null)
                                icon = new BitmapDrawable(
                                        ctx.getResources(), b);
                        }

                        // Setup pending drawable for image thumbnail load later
                        if (IconsMapAdapter.IconFilenameFilter.accept(null,
                                f.getName()))
                            icon = new PendingDrawable(icon);
                    }
                    _fileList.add(createItem(f.getName(), icon, type));
                }
            }
            if (_fileList.size() == 0) {
                _directoryEmpty = true;
                _fileList.add(createItem(getContext().getString(
                        R.string.directory_empty), null,
                        FileItem.FILE));
            } else {
                Collections.sort(_fileList, new FileItemComparator());
            }
        } else {
            _fileList.add(createItem(
                    "Permission Denied - Unable to Read Directory Contents",
                    null,
                    FileItem.FILE));
        }
    }

    protected FileItem createItem(String fileName, Drawable icon,
            Integer type) {
        return new FileItem(fileName, icon, type);
    }

    protected void _updateCurrentDirectoryTextView() {
        StringBuilder currDirString = new StringBuilder();
        for (String d : _pathDirsList) {
            currDirString.append(d).append(FILE_SEPARATOR);
        }

        if (_pathDirsList.size() == 0) {
            if (_up != null)
                _up.setEnabled(false);
        } else {
            if (_up != null)
                _up.setEnabled(true);
        }
        TextView tv = this
                .findViewById(R.id.importBrowserCurrentDirectory);
        if (tv != null)
            tv.setText(currDirString.toString());
    }

    protected boolean _testExtension(String ext) {
        return _extensions.isEmpty() || _extensions.contains(
                ext.toLowerCase(LocaleUtil.getCurrent()));
    }

    protected void _scrollListToTop() {
        ListView lv = this
                .findViewById(R.id.importBrowserFileItemList);
        if (lv != null) {
            lv.setSelectionFromTop(0, 0);
        }
    }

    protected void _createAdapter() {
        _adapter = new FileItemAdapter(getContext(),
                R.layout.import_file_browser_fileitem,
                _fileList);
    }

    protected void _navigateUpOneDirectory() {

        _loadDirectoryUp();
        _loadFileList();
        _adapter.notifyDataSetChanged();
        _updateCurrentDirectoryTextView();

        _scrollListToTop();
    }

    protected int _getFileCount(File dir) {
        Integer childCount = _dirCounts.get(dir.getName());
        if (childCount == null) {
            String[] children = ioProvider.list(dir, _fileFilter);
            childCount = children != null ? children.length : 0;
            _dirCounts.put(dir.getName(), childCount);
        }
        return childCount;
    }

    /*************************** PRIVATE METHODS **************************/

    private void _setInitialDirectory() {
        // first check if user provided a directory to start in
        if (_userStartDirectory != null && _userStartDirectory.length() > 0) {
            File userDir = new File(_userStartDirectory);
            if (ioProvider.exists(userDir) && ioProvider.isDirectory(userDir)) {
                _path = userDir;
                return;
            }
        }

        // start in default directory
        File temp = new File(INITIAL_DIRECTORY);
        if (ioProvider.isDirectory(temp))
            _path = temp;
        if (_path == null) {
            if (ioProvider
                    .isDirectory(Environment.getExternalStorageDirectory())
                    && ioProvider.canRead(
                            Environment.getExternalStorageDirectory())) {
                _path = Environment.getExternalStorageDirectory();
            } else {
                _path = new File(FILE_SEPARATOR);
            }
        }
    }

    private void _parseDirectoryPath() {
        _pathDirsList.clear();
        String pathString = FileSystemUtils.prettyPrint(_path);
        String[] parts = pathString.split(Pattern.quote(FILE_SEPARATOR));
        Collections.addAll(_pathDirsList, parts);
    }

    private void _initButtons() {
        if (_up != null) {
            _up.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    _navigateUpOneDirectory();
                }
            });
        }

    }

    private void _loadDirectoryUp() {
        if (_pathDirsList.size() <= 1)
            return;

        if (Build.VERSION.SDK_INT >= 30) {
            if (_pathDirsList.get(1).equals("storage")
                    && _pathDirsList.size() == 3)
                return;
        }

        String s = _pathDirsList.remove(_pathDirsList.size() - 1);
        int lastIndex = _path.toString().lastIndexOf(s);
        if (lastIndex > -1)
            _path = new File(_path.toString().substring(0, lastIndex));
        _fileList.clear();
    }

    protected void _initFileListView() {
        ListView list = this
                .findViewById(R.id.importBrowserFileItemList);
        if (list != null) {
            list.setAdapter(_adapter);
            list.setOnItemClickListener(this);
            list.setOnItemLongClickListener(this);
        }
    }

    protected void _returnFile(File file) {
        _retFile = file;
        if (_alert != null) {
            View doneBtn = _alert.getButton(DialogInterface.BUTTON_POSITIVE);
            if (doneBtn != null)
                doneBtn.callOnClick();
            else
                _alert.dismiss();
        }
    }

    protected void promptNewDirectory() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        final Context ctx = mv.getContext();
        final EditText input = new EditText(ctx);
        input.setSingleLine(true);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(R.string.new_folder);
        b.setView(input);
        b.setPositiveButton(R.string.create, null);
        b.setNegativeButton(R.string.cancel, null);
        final AlertDialog d = b.create();
        Window w = d.getWindow();
        if (w != null)
            w.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                InputMethodManager imm = (InputMethodManager) ctx
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                input.clearFocus();
            }
        });

        final Button createBtn = d.getButton(DialogInterface.BUTTON_POSITIVE);
        createBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check for blank name
                String dirName = input.getText().toString();
                if (FileSystemUtils.isEmpty(dirName)) {
                    Toast.makeText(ctx, R.string.name_cannot_be_blank,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Check for invalid chars
                try {
                    FileSystemUtils.validityScan(dirName);
                } catch (Exception e) {
                    Toast.makeText(ctx, R.string.name_contains_invalid_chars,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Attempt to create new folder
                File newDir = new File(_path, dirName);
                if (!IOProviderFactory.mkdir(newDir)) {
                    Toast.makeText(ctx, R.string.new_folder_failed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Open new folder and dismiss entry dialog
                setCurrentPath(newDir);
                d.dismiss();
            }
        });
        input.requestFocus();
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent evt) {
                if (id == EditorInfo.IME_ACTION_DONE) {
                    createBtn.performClick();
                    return true;
                } else
                    return false;
            }
        });
    }

    /**
     * Get a thumbnail from the Android thumbnail cache
     * @param file Image file to query
     * @return Thumbnail bitmap or null if not found
     */
    private Bitmap getThumbnail(File file) {
        ContentResolver cr = getContext().getContentResolver();
        try (Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {
                        MediaStore.MediaColumns._ID
                },
                MediaStore.MediaColumns.DATA + "=?",
                new String[] {
                        file.getAbsolutePath()
                }, null)) {
            if (c != null && c.moveToNext())
                return Thumbnails.getThumbnail(cr,
                        c.getLong(c.getColumnIndexOrThrow(ImageColumns._ID)),
                        Thumbnails.MINI_KIND, null);
        }
        return null;
    }

    /**
     * Load a thumbnail into the specified pending icon
     * @param fileItem File item
     */
    protected void loadThumbnail(final FileItem fileItem) {

        // Needs to have a pending drawable
        Drawable dr = fileItem.iconDr;
        if (!(dr instanceof PendingDrawable))
            return;

        final PendingDrawable icon = (PendingDrawable) dr;
        final File file = new File(_path, fileItem.file);

        // Icon no longer requires load or is already loading
        if (!icon.isPending() || fileItem.iconLoading)
            return;

        fileItem.iconLoading = true;

        // Get thumbnail on thumbnail loader thread pool
        final MapView mv = MapView.getMapView();
        _thumbLoader.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap thumb = getThumbnail(file);
                if (mv == null)
                    return;

                // Update icon on UI thread, which automatically calls
                // notifyDataSetChanged() for the adapter it's attached to
                // TODO: There's still an issue where some icons won't refresh
                //  even after calling notifyDataSetChanged(). Possibly
                //  a refresh issue in the drawable itself.
                mv.post(new Runnable() {
                    @Override
                    public void run() {
                        fileItem.iconLoading = false;
                        if (thumb != null)
                            icon.setBitmap(thumb);
                    }
                });
            }
        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
            long id) {
        ViewHolder holder = view.getTag() instanceof ViewHolder
                ? (ViewHolder) view.getTag()
                : null;
        if (holder == null || holder.item == null)
            return;

        // Select file or enter directory
        _currFile = holder.item.file;
        File sel = new File(_path, _currFile);
        if (holder.item.type == FileItem.DIRECTORY) {
            if (ioProvider.canRead(sel)) {
                _pathDirsList.add(_currFile);
                _path = new File(sel + "");
                _loadFileList();
                _adapter.notifyDataSetChanged();
                _updateCurrentDirectoryTextView();

                _scrollListToTop();
            }
        } else {
            if (!_directoryEmpty) {
                _returnFile(sel);
            }
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view,
            int position, long id) {
        return false;
    }

    /*************************** PROTECTED CLASSES **************************/

    public static class FileItem {

        public static final int FILE = 1;
        public static final int DIRECTORY = 1 << 1;

        // The file name (path not included)
        public final String file;

        // Icon drawable
        public final Drawable iconDr;

        // Whether the icon is busy being loaded
        protected boolean iconLoading;

        // FILE or DIRECTORY
        public final int type;

        public FileItem(String file, Drawable icon, Integer type) {
            this.file = file;
            this.iconDr = icon;
            this.type = type;
        }

        @Override
        public String toString() {
            return file;
        }
    }

    /*************************** PRIVATE CLASSES **************************/

    private static class FileItemComparator implements Comparator<FileItem>,
            Serializable {
        @Override
        public int compare(FileItem i0, FileItem i1) {
            if (i0.type == i1.type) {
                return i0.file.toLowerCase(LocaleUtil.getCurrent()).compareTo(
                        i1.file.toLowerCase(LocaleUtil.getCurrent()));
            } else if (i0.type == FileItem.FILE) {
                return -1;
            } else if (i0.type == FileItem.DIRECTORY) {
                return 1;
            }
            return i0.file.toLowerCase(LocaleUtil.getCurrent()).compareTo(
                    i1.file.toLowerCase(LocaleUtil.getCurrent()));
        }
    }

    private static class ViewHolder {
        FileItem item;
        Button icon;
        TextView txtFilename;
        TextView txtModifiedDate;
    }

    /**
     * 
     */
    private class FileItemAdapter extends ArrayAdapter<FileItem> {

        final Context context;

        public FileItemAdapter(Context context, int resourceId,
                List<FileItem> items) {
            super(context, resourceId, items);
            this.context = context;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView,
                @NonNull ViewGroup parent) {
            ViewHolder holder;
            final FileItem fileItem = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(
                        R.layout.import_file_browser_fileitem, parent, false);
                holder = new ViewHolder();
                holder.txtFilename = convertView
                        .findViewById(R.id.importBrowserFileName);
                holder.txtModifiedDate = convertView
                        .findViewById(R.id.importBrowserModifiedDate);
                holder.icon = convertView
                        .findViewById(R.id.importBrowserIcon);
                convertView.setTag(holder);
            } else
                holder = (ViewHolder) convertView.getTag();

            holder.item = fileItem;

            File f = new File(_path, fileItem.file);
            holder.txtFilename.setText(f.getName());
            if (ioProvider.exists(f))
                holder.txtModifiedDate
                        .setText(getModifiedDate(ioProvider.lastModified(f)));
            else
                holder.txtModifiedDate.setText("");

            Drawable drawable = fileItem.iconDr;
            holder.icon.setCompoundDrawablesWithIntrinsicBounds(null, drawable,
                    null, null);

            // Load pending thumbnail icon if needed
            if (IconsMapAdapter.IconFilenameFilter.accept(null, f.getName())) {
                drawable.setCallback(holder.icon);
                loadThumbnail(fileItem);
            }

            if (fileItem.type == FileItem.FILE) {
                if (ioProvider.exists(f))
                    holder.icon.setText(
                            MathUtils.GetLengthString(ioProvider.length(f)));
                else {
                    holder.icon.setText("");
                    holder.txtModifiedDate.setText("");
                }
            } else {
                int childCount = _getFileCount(f);
                if (childCount == 0)
                    holder.icon.setText("");
                else
                    holder.icon.setText(getContext().getString(
                            R.string.items, childCount));
            }

            // handle user clicks
            holder.icon.setClickable(false);
            return convertView;
        }
    }
}
