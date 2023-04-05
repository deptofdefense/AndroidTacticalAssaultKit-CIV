
package com.atakmap.android.image;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.atakmap.android.attachment.AttachFileTask;
import com.atakmap.android.attachment.AttachmentBroadcastReceiver;
import com.atakmap.android.attachment.DeleteAfterSendCallback;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentListener;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentProvider;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.image.gallery.GalleryContentProvider;
import com.atakmap.android.image.quickpic.QuickPicReceiver;
import com.atakmap.android.importfiles.ui.ImportManagerFileBrowser;
import com.atakmap.android.hierarchy.filters.SearchFilter;
import com.atakmap.android.importexport.ExportFileMarshal;
import com.atakmap.android.importfiles.task.ImportFileTask;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DefaultIOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.filesystem.HashingUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.map.layer.feature.geometry.Envelope;

import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ImageGalleryReceiver extends DropDownReceiver implements
        URIContentListener {

    private static final String TAG = "ImageGalleryReceiver";
    private static final int IMAGE_SELECT_CODE = 6764; //arbitrary
    private static final int VIDEO_SELECT_CODE = 6765; //arbitrary
    private static final int FILE_SELECT_CODE = 6766; //arbitrary

    public static final String PIC_CAPTURED = "com.atakmap.android.image.PIC_CAPTURED";

    private final MapView mapView;
    private final Context context;
    private ImageGalleryBaseAdapter adapter;
    private ActionBarView toolbar;
    private String title;

    private int screenState = DROPDOWN_STATE_NORMAL;
    private double currWidth = HALF_WIDTH;
    private double currHeight = HALF_HEIGHT;

    private boolean showDetails = true;

    // Search function
    private ImageButton searchBtn;
    private EditText searchInput;

    private String onAddAction;
    private String curPath;

    // Attachment import fields
    private MapItem marker;

    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    //==================================
    //  PUBLIC CONSTANTS
    //==================================

    public static final String IMAGE_GALLERY = "com.atakmap.android.image.IMAGE_GALLERY";
    public static final String VIEW_ATTACHMENTS = "com.atakmap.android.attachment.VIEW_ATTACHMENTS";
    public static final String SEND_FILES = "com.atakmap.android.image.SEND_FILES";

    //==================================
    //  PUBLIC METHODS
    //==================================

    public ImageGalleryReceiver(MapView mapView) {
        super(mapView);
        this.mapView = mapView;
        this.context = mapView.getContext();

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ATAKActivity.ACTIVITY_FINISHED,
                "Fired when Gallery is ready to import an image/video");
        filter.addAction(PIC_CAPTURED,
                "Fired when Gallery is ready to import a captured image");
        AtakBroadcast.getInstance().registerReceiver(
                _activityResultReceiver, filter);

        URIContentManager.getInstance().registerListener(this);
    }

    @Override
    protected void disposeImpl() {
        URIContentManager.getInstance().unregisterListener(this);
        AtakBroadcast.getInstance().unregisterReceiver(
                _activityResultReceiver);
    }

    //==================================
    //  BroadcastReceiver INTERFACE
    //==================================

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        String onDeleteAction;
        if (IMAGE_GALLERY.equals(action)) {
            this.title = extras.getString("title");
            this.screenState = (extras.getBoolean("fullscreen", false))
                    ? DROPDOWN_STATE_FULLSCREEN
                    : DROPDOWN_STATE_NORMAL;

            // Support a UID and a single directory
            String directory = extras.getString("directory");
            String uid = extras.getString("uid");

            // Support a UID and list of URIs
            String[] uris = extras.getStringArray("uris");

            // Support a UID per file
            String[] files = extras.getStringArray("files");
            String[] uids = extras.getStringArray("uids");

            marker = null;
            onAddAction = extras.getString("callbackTag1");
            onDeleteAction = extras.getString("callbackTag2");
            curPath = extras.getString("callbackTag3");

            if (!FileSystemUtils.isEmpty(directory)) {
                Log.d(TAG, "Processing directory: " + directory);
                List<File> fileList = new ArrayList<>();
                List<String> uidList = new ArrayList<>();
                File dir = new File(
                        FileSystemUtils
                                .sanitizeWithSpacesAndSlashes(directory));
                if (FileSystemUtils.isFile(dir)
                        && IOProviderFactory.isDirectory(dir)) {
                    File[] fileArr = IOProviderFactory.listFiles(dir);
                    if (fileArr != null) {
                        for (File f : fileArr) {
                            if (!IOProviderFactory.isDirectory(f))
                                fileList.add(f);
                        }
                    }
                }

                if (!fileList.isEmpty())
                    showGallery(fileList, uidList, uid, onDeleteAction);
                else
                    toast(R.string.gallery_no_items);
            } else if (!FileSystemUtils.isEmpty(files)) {
                Log.d(TAG, "Processing file count: " + files.length);
                List<File> fileList = new ArrayList<>();

                for (String file : files) {
                    if (FileSystemUtils.isFile(file)) {
                        File f = new File(file);
                        // redundant check, but leaving in place because it 
                        // was the original code
                        if (IOProviderFactory.exists(f)
                                && !IOProviderFactory.isDirectory(f))
                            fileList.add(f);
                    } else {
                        Log.w(TAG, "Skipping file: " + file);
                    }
                }

                //
                // The list of UIDs must support modification, so Arrays.toList
                // can't be used.
                //
                List<String> uidList = null;

                if (!FileSystemUtils.isEmpty(uids)) {
                    uidList = new ArrayList<>(uids.length);
                    Collections.addAll(uidList, uids);
                }

                if (!fileList.isEmpty())
                    showGallery(fileList, uidList, null, onDeleteAction);
                else
                    toast(R.string.gallery_no_items);
            } else if (!FileSystemUtils.isEmpty(uris)) {
                Log.d(TAG, "Processing URI count: " + uris.length);
                showGallery(uris, uid);
            } else
                toast(R.string.gallery_no_items);
        } else if (VIEW_ATTACHMENTS.equals(action)) {

            // Open marker attachments
            String uid = extras.getString("uid");
            this.marker = uid != null ? mapView.getRootGroup()
                    .deepFindUID(uid) : null;
            if (this.marker == null)
                return;

            this.title = ATAKUtilities.getDisplayName(marker)
                    + " " + context.getString(R.string.attachments);

            String dirPath = AttachmentManager.getFolderPath(uid, true);
            if (dirPath != null
                    && IOProviderFactory.exists(new File(dirPath))) {
                Log.d(TAG, "Processing directory: " + dirPath);
                List<File> fileList = AttachmentManager.getAttachments(uid);

                onDeleteAction = null;
                onAddAction = null;
                curPath = null;

                if (intent.getExtras().getBoolean("focusmap", false)) {
                    Intent focus = new Intent();
                    focus.setAction("com.atakmap.android.maps.FOCUS");
                    focus.putExtra("uid", uid);
                    AtakBroadcast.getInstance().sendBroadcast(focus);
                }

                showGallery(fileList, null, uid, null);
            }
        } else if (SEND_FILES.equals(action)) {
            // Send files to list of contacts
            String[] files = extras.getStringArray("files");
            String[] contactUIDs = extras.getStringArray("sendTo");
            if (files != null && contactUIDs != null) {
                MissionPackageManifest manifest = MissionPackageApi
                        .CreateTempManifest(context.getString(
                                R.string.app_name)
                                + ExportFileMarshal.timestamp()
                                + ".zip", true, true, null);

                for (String path : files) {
                    if (FileSystemUtils.isFile(path)) {
                        File f = new File(path);
                        String uid = null;
                        File parentFile = f.getParentFile();
                        if (parentFile != null) {
                            String parentFileStr = parentFile.getParent();
                            if (parentFileStr != null && parentFileStr
                                    .endsWith("attachments")) {
                                uid = f.getParentFile().getName();
                                manifest.addMapItem(uid);
                            }
                        }
                        manifest.addFile(f, uid);
                        // Include extra metadata if available
                        if (ImageContainer.NITF_FilenameFilter.accept(
                                f.getParentFile(), f.getName())) {
                            File nitfXml = new File(f.getParent(), f.getName()
                                    + ".aux.xml");
                            if (IOProviderFactory.exists(nitfXml))
                                manifest.addFile(nitfXml, uid);
                        }
                    }
                }

                // Find contacts by UID
                List<IndividualContact> contacts = Arrays.asList(
                        Contacts.getInstance().getIndividualContactsByUuid(
                                Arrays.asList(contactUIDs)));

                MissionPackageApi
                        .Send(getMapView().getContext(),
                                manifest, DeleteAfterSendCallback.class,
                                contacts.toArray(new Contact[0]));
            }
        }
    }

    public void onRefresh() {
        if (marker != null && marker.getGroup() == null)
            // Marker has been deleted
            closeDropDown();
    }

    @Override
    protected void onStateRequested(int state) {
        if (state == DROPDOWN_STATE_FULLSCREEN) {
            if (!isPortrait()) {
                if (Double.compare(currWidth, HALF_WIDTH) == 0) {
                    resize(FULL_WIDTH - HANDLE_THICKNESS_LANDSCAPE,
                            FULL_HEIGHT);
                }
            } else {
                if (Double.compare(currHeight, HALF_HEIGHT) == 0) {
                    resize(FULL_WIDTH, FULL_HEIGHT - HANDLE_THICKNESS_PORTRAIT);
                }
            }
        } else if (state == DROPDOWN_STATE_NORMAL) {
            if (!isPortrait()) {
                resize(HALF_WIDTH, FULL_HEIGHT);
            } else {
                resize(FULL_WIDTH, HALF_HEIGHT);
            }
        }
        screenState = state;
    }

    @Override
    public void onContentImported(URIContentHandler handler) {
        if (adapter != null)
            adapter.refresh();
    }

    @Override
    public void onContentDeleted(URIContentHandler handler) {
        if (adapter != null)
            adapter.refresh();
    }

    @Override
    public void onContentChanged(URIContentHandler handler) {
        if (adapter != null)
            adapter.refresh();
    }

    //==================================
    //
    //  PROTECTED INTERFACE
    //
    //==================================

    //==================================
    //  DropDownReceiver INTERFACE
    //==================================

    @Override
    protected boolean onBackButtonPressed() {
        if (searchInput.getVisibility() == View.VISIBLE)
            // Cancel search
            searchBtn.performClick();
        else if (adapter instanceof ImageGalleryFileAdapter
                && ((ImageGalleryFileAdapter) adapter).getMultiSelect())
            // Cancel multi-select
            ((ImageGalleryFileAdapter) adapter).endMultiSelectAction(true);
        else
            return super.onBackButtonPressed();
        return true;
    }

    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================

    //==================================
    //  PRIVATE NESTED TYPES
    //==================================

    private final class OnStateListenerWrapper
            implements DropDown.OnStateListener {
        @Override
        public void onDropDownSelectionRemoved() {
            if (listener != null)
                listener.onDropDownSelectionRemoved();
        }

        @Override
        public void onDropDownClose() {
            if (listener != null)
                listener.onDropDownClose();
            ActionBarReceiver.getInstance().setToolView(null);
        }

        @Override
        public void onDropDownSizeChanged(double width, double height) {
            Log.d(TAG, "resizing width=" + width + " height=" + height);
            currWidth = width;
            currHeight = height;

            if (listener != null)
                listener.onDropDownSizeChanged(width, height);
        }

        @Override
        public void onDropDownVisible(boolean v) {
            if (listener != null)
                listener.onDropDownVisible(v);
            ActionBarReceiver.getInstance()
                    .setToolView(v ? getToolbarView() : null);
        }

        private OnStateListenerWrapper(DropDown.OnStateListener listener) {
            this.listener = listener;
        }

        private final DropDown.OnStateListener listener;
    }

    //==================================
    //  PRIVATE METHODS
    //==================================

    private synchronized ActionBarView getToolbarView() {
        if (this.toolbar == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            this.toolbar = (ActionBarView) inflater
                    .inflate(R.layout.image_grid_toolbar, null);
        }

        return this.toolbar;
    }

    private View prepareGallery(final MapItem mapItem) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Service.LAYOUT_INFLATER_SERVICE);
        final View gallery = inflater.inflate(R.layout.image_grid, null);
        ImageView gridIcon = gallery
                .findViewById(R.id.gridImagesIcon);

        if (mapItem != null) // Gallery view for a given map item.
            // Visibility is handled by SetIcon.
            ATAKUtilities.SetIcon(context, gridIcon, mapItem);
        else
            // Gallery view for arbitrary list.
            gridIcon.setVisibility(ImageView.GONE);

        TextView titleView = gallery
                .findViewById(R.id.gridImagesTitle);

        titleView.setText(FileSystemUtils.isEmpty(this.title)
                ? context.getString(R.string.image_gallery)
                : this.title);

        final View titleLayout = gallery
                .findViewById(R.id.gallery_title_layout);
        final View actionsLayout = gallery
                .findViewById(R.id.gallery_actions_layout);

        this.searchBtn = gallery.findViewById(R.id.search_btn);
        this.searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean searchActive = !(searchInput
                        .getVisibility() == View.VISIBLE);
                searchInput.setVisibility(searchActive ? View.VISIBLE
                        : View.GONE);
                titleLayout.setVisibility(searchActive ? View.GONE
                        : View.VISIBLE);
                actionsLayout.setVisibility(searchActive ? View.GONE
                        : View.VISIBLE);
                if (!searchActive) {
                    searchInput.clearFocus();
                    searchInput.setText("");
                    adapter.removeFilter(SearchFilter.class);
                    adapter.refresh();
                } else {
                    searchInput.requestFocus();
                }
            }
        });

        this.searchInput = gallery.findViewById(R.id.search_txt);
        this.searchInput.setFocusable(true);
        this.searchInput.setText("");
        this.searchInput.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (adapter != null
                        && searchInput.getVisibility() == View.VISIBLE) {
                    adapter.addFilter(new SearchFilter(s.toString()));
                    adapter.refresh();
                }
            }
        });
        this.searchInput
                .setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean b) {
                        InputMethodManager imm = (InputMethodManager) context
                                .getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (b)
                            imm.showSoftInput(view,
                                    InputMethodManager.SHOW_IMPLICIT);
                        else
                            imm.hideSoftInputFromWindow(
                                    view.getWindowToken(), 0);
                    }
                });

        return gallery;
    }

    private ActionBarView prepareToolbar(
            final ImageGalleryBaseAdapter adapter) {
        ActionBarView tb = getToolbarView();

        View importFile = tb.findViewById(R.id.importFile);
        importFile.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showImportDialog();
                    }
                });
        importFile.setVisibility(markerValid() || onAddAction != null
                ? View.VISIBLE
                : View.GONE);
        tb.findViewById(R.id.buttonImageDetails).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        adapter.setDisplayDetails(showDetails = !showDetails);
                    }
                });

        return tb;
    }

    private void showGallery(List<File> files, List<String> uids,
            String uid, String onDeleteAction) {
        final MapItem mapItem = mapView.getRootGroup().deepFindUID(uid);
        final View gallery = prepareGallery(mapItem);
        GridView gridView = gallery.findViewById(R.id.gridImages);
        View loader = gallery.findViewById(R.id.gridLoader);

        if (adapter != null)
            adapter.dispose();

        if (markerValid())
            adapter = new ImageGalleryFileAdapter(mapView, files, marker,
                    showDetails, onDeleteAction, loader);
        else
            adapter = new ImageGalleryFileAdapter(mapView, files, uids,
                    showDetails, onDeleteAction, loader);
        adapter.setReceiver(this);

        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(adapter.getGridViewClickListener());
        gridView.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent,
                            View view, int position, long id) {
                        showFileDialog(position);
                        return true;
                    }
                });

        final DropDown.OnStateListener stateListener = new OnStateListenerWrapper(
                adapter.getDropDownStateListener());

        adapter.customizeGalleryView(gallery);
        adapter.customizeToolbarView(prepareToolbar(
                adapter));

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                if (isVisible())
                    showGallery(gallery, stateListener);
            }

            @Override
            public void onInvalidated() {
                adapter.unregisterDataSetObserver(this);
            }
        });
        //if (files.isEmpty())
        showGallery(gallery, stateListener);
    }

    private void showGallery(final String[] uris, final String uid) {
        final MapItem mapItem = mapView.getRootGroup().deepFindUID(uid);
        final View gallery = prepareGallery(mapItem);
        GridView gridView = gallery.findViewById(R.id.gridImages);

        if (adapter != null)
            adapter.dispose();
        adapter = new ImageGalleryBlobAdapter(mapView,
                uris,
                uid,
                showDetails,
                gallery.findViewById(R.id.gridLoader));
        adapter.setReceiver(this);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(adapter.getGridViewClickListener());

        final DropDown.OnStateListener stateListener = new OnStateListenerWrapper(
                adapter.getDropDownStateListener());

        adapter.customizeGalleryView(gallery);
        adapter.customizeToolbarView(prepareToolbar(
                adapter));

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                if (isVisible())
                    showGallery(gallery, stateListener);
            }

            @Override
            public void onInvalidated() {
                adapter.unregisterDataSetObserver(this);
            }
        });
    }

    private void showGallery(final View galleryView,
            final DropDown.OnStateListener dropDownListener) {
        if (isVisible()) {
            onStateRequested(screenState);
        } else {
            closeDropDown();
            setRetain(true);
            if (screenState == DROPDOWN_STATE_FULLSCREEN) {
                showDropDown(galleryView,
                        FULL_WIDTH - HANDLE_THICKNESS_LANDSCAPE, FULL_HEIGHT,
                        FULL_WIDTH, FULL_HEIGHT - HANDLE_THICKNESS_PORTRAIT,
                        dropDownListener);
            } else {
                showDropDown(galleryView,
                        HALF_WIDTH, FULL_HEIGHT,
                        FULL_WIDTH, HALF_HEIGHT,
                        dropDownListener);
            }
        }
    }

    private boolean markerValid() {
        return marker != null && marker.getGroup() != null;
    }

    private void showImportDialog() {

        TileButtonDialog d = new TileButtonDialog(mapView);
        d.setIcon(context.getDrawable(R.drawable.attachment));
        d.addButton(R.drawable.ic_menu_quickpic, R.string.camera);
        d.addButton(R.drawable.ic_gallery, R.string.image);
        d.addButton(R.drawable.ic_menu_video, R.string.video);
        d.addButton(R.drawable.ic_menu_import_file, R.string.local_file);
        d.addButton(R.drawable.ic_android_display_settings,
                R.string.choose_app);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Quick-pic
                if (which == 0) {
                    String uid = null;
                    String fullfilePath = null;
                    if (markerValid()) {
                        uid = marker.getUID();
                        File img = ImageDropDownReceiver
                                .createAndGetPathToImageFromUID(
                                        uid,
                                        "jpg");
                        if (img != null)
                            fullfilePath = img.getAbsolutePath();
                    } else if (curPath != null) {
                        fullfilePath = curPath;
                    }
                    if (uid != null && fullfilePath != null) {

                        QuickPicReceiver.chooser(mapView,
                                new AtakPreferences(mapView.getContext()),
                                new File(fullfilePath), uid,
                                new File(fullfilePath).getParentFile(),
                                new ActionBroadcastData(PIC_CAPTURED, null));
                    }
                }

                // Image select
                else if (which == 1) {
                    try {
                        Intent agc = new Intent();
                        agc.setType("image/*");
                        agc.setAction(Intent.ACTION_GET_CONTENT);
                        ((Activity) context).startActivityForResult(
                                agc, IMAGE_SELECT_CODE);
                    } catch (Exception e) {
                        Log.w(TAG,
                                "Failed to ACTION_GET_CONTENT image",
                                e);
                        toast(R.string.install_gallery);
                    }
                }

                // Video select
                else if (which == 2) {
                    try {
                        Intent agc = new Intent();
                        agc.setType("video/*");
                        agc.setAction(Intent.ACTION_GET_CONTENT);
                        ((Activity) context).startActivityForResult(
                                agc, VIDEO_SELECT_CODE);
                    } catch (Exception e) {
                        Log.w(TAG,
                                "Failed to ACTION_GET_CONTENT video",
                                e);
                        toast(R.string.install_gallery2);
                    }
                }

                // ATAK file browser
                else if (which == 3) {
                    importLocalFile();
                }

                // File browser app
                else if (which == 4) {
                    try {
                        Intent agc = new Intent();
                        agc.setType("file/*");
                        agc.setAction(Intent.ACTION_GET_CONTENT);
                        ((Activity) context).startActivityForResult(
                                agc, FILE_SELECT_CODE);
                    } catch (Exception e) {
                        Log.w(TAG,
                                "Failed to ACTION_GET_CONTENT",
                                e);
                        toast(R.string.install_browser);
                    }
                }
            }
        });
        d.show(R.string.attachment_title, true);
    }

    private void importLocalFile() {
        final ImportManagerFileBrowser importView = ImportManagerFileBrowser
                .inflate(mapView);

        final SharedPreferences defaultPrefs = PreferenceManager
                .getDefaultSharedPreferences(
                        mapView.getContext());

        importView.setTitle(R.string.select_file_to_attach);
        importView.setStartDirectory(
                ATAKUtilities.getStartDirectory(getMapView().getContext()));
        importView.setExtensionTypes(new String[] {
                "*"
        });
        AlertDialog.Builder b = new AlertDialog.Builder(mapView.getContext());
        b.setView(importView);
        b.setNegativeButton(R.string.cancel, null);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // User has selected items and touched OK. Import the data.
                List<File> selectedFiles = importView.getSelectedFiles();

                if (selectedFiles.size() == 0) {
                    Toast.makeText(mapView.getContext(),
                            R.string.no_import_files,
                            Toast.LENGTH_SHORT).show();
                } else {
                    // Store the currently displayed directory so we can open it
                    // again the next time this dialog is opened.
                    defaultPrefs.edit().putString("lastDirectory",
                            importView.getCurrentPath())
                            .apply();

                    // Iterate over all of the selected files and begin an import task.
                    for (File file : selectedFiles) {
                        Log.d(TAG, "Importing file: " + file.getAbsolutePath());
                        try {
                            attachFile(file, false, true);
                        } catch (Exception ioe) {
                            Log.d(TAG, "file: " + file);
                        }

                    }
                }
            }
        });
        final AlertDialog alert = b.create();

        // Show the dialog
        alert.show();

        AlertDialogHelper.adjustWidth(alert, 0.90d);
    }

    /**
     * Attach a file to a marker
     * @param f the file to attach
     * @param captured true if the file was captured from the camera; false otherwise.
     * @param useIOProvider true if the file should be attached using the system registered IOProvider; false otherwise.
     */
    private void attachFile(final File f, boolean captured,
            boolean useIOProvider) {
        if (f == null)
            return;

        if (markerValid()) {
            final String uid = marker.getUID();
            if (FileSystemUtils.isEmpty(uid)) {
                Log.w(TAG, "Cannot import file without UID");
                return;
            }

            Log.d(TAG,
                    "Attaching file: " + f.getAbsolutePath()
                            + " for UID: " + uid);
            String callsign = ATAKUtilities
                    .getDisplayName(marker);
            final AttachFileTask attachTask = new AttachFileTask(
                    context, uid, callsign);
            attachTask.setCallback(new Runnable() {
                @Override
                public void run() {
                    try {
                        AttachFileTask.Result result = attachTask.get();
                        if (result != null && result.success) {
                            Log.d(TAG, "attaching the item: " + result.file);
                            addItem(result.file);
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "error occurred", e);
                    }
                }
            });
            if (!captured)
                attachTask.setFlags(AttachFileTask.FlagPromptOverwrite
                        | ImportFileTask.FlagCopyFile);

            // if use of native IO is required, install a default provider
            // instance on the task
            if (!useIOProvider)
                attachTask.setProvider(new DefaultIOProvider());

            attachTask.execute(f);
        } else if (onAddAction != null) {
            Intent i = new Intent(onAddAction);
            i.putExtra("file", f.getAbsolutePath());
            AtakBroadcast.getInstance().sendBroadcast(i);
            addItem(f);
        }
    }

    private void showFileDialog(final int position) {
        // Make sure parameters are valid
        if (!(adapter instanceof ImageGalleryFileAdapter))
            return;
        final GalleryFileItem item = (GalleryFileItem) adapter
                .getItem(position);
        if (item == null || item.getFile() == null)
            return;
        final File file = item.getFile();
        MapItem mapItem = item.getMapItem();
        if (mapItem == null)
            mapItem = marker;
        final MapItem fMapItem = mapItem;
        if (!FileSystemUtils.isFile(file)) {
            Log.e(TAG, "Unable to locate file: " + item.getAbsolutePath());
            toast(R.string.mission_package_unable_to_access_file);
            return;
        }

        View v = LayoutInflater.from(context).inflate(
                R.layout.attachment_detail, null);
        AlertDialog.Builder adb = new AlertDialog.Builder(context);
        adb.setTitle(R.string.attachment_details);
        adb.setView(v);
        adb.setPositiveButton(R.string.open,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (adapter != null)
                            adapter.getGridViewClickListener().onItemClick(
                                    null, null, position, 0);
                    }
                });
        if (mapItem != null) {
            adb.setNeutralButton(R.string.send,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            sendItem(item.getFile(), fMapItem);
                        }
                    });
        }
        adb.setNegativeButton(R.string.delete, null);

        final AlertDialog details = adb.create();
        details.show();

        Log.d(TAG, "Showing details of file: " + item.getAbsolutePath());

        TextView nameText = v.findViewById(
                R.id.attachment_detail_txtName);
        nameText.setText(MissionPackageUtils.abbreviateFilename(
                file.getName(), 40));

        TextView directoryText = v.findViewById(
                R.id.attachment_detail_txtDirectory);
        directoryText.setText(file.getParent());

        TextView typeText = v
                .findViewById(R.id.attachment_detail_txtType);
        typeText.setText(FileSystemUtils.getExtension(file, true, true));

        TextView sizeText = v
                .findViewById(R.id.attachment_detail_txtSize);
        sizeText.setText(
                MathUtils.GetLengthString(IOProviderFactory.length(file)));

        TextView dateText = v
                .findViewById(R.id.attachment_detail_txtModifiedDate);
        dateText.setText(MissionPackageUtils.getModifiedDate(file));

        // TODO display "Compute MD5" button, when clicked hide button and display MD5...?
        TextView md5Text = v
                .findViewById(R.id.attachment_detail_txtMd5);
        md5Text.setText(HashingUtils.md5sum(file));

        details.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String attached = null;
                        if (fMapItem != null)
                            attached = ATAKUtilities.getDisplayName(fMapItem);
                        AlertDialog.Builder b = new AlertDialog.Builder(
                                context);
                        b.setTitle(R.string.confirm_discard);
                        if (FileSystemUtils.isEmpty(attached))
                            b.setMessage(context.getString(
                                    R.string.gallery_remove_file,
                                    file.getName()));
                        else
                            b.setMessage(context.getString(
                                    R.string.gallery_remove_file_attached,
                                    attached, file.getName()));
                        b.setCancelable(false);
                        b.setPositiveButton(R.string.yes,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d,
                                            int id) {
                                        toast("Deleting " + file.getName()
                                                + "...");
                                        ((ImageGalleryFileAdapter) adapter)
                                                .deleteFile(file);
                                        ((ImageGalleryFileAdapter) adapter)
                                                .removeFile(file);
                                        details.dismiss();
                                    }
                                });
                        b.setNegativeButton(R.string.no, null);
                        b.show();
                    }
                });
    }

    private void addItem(File file) {
        if (adapter instanceof ImageGalleryFileAdapter) {
            ((ImageGalleryFileAdapter) adapter).addFile(file, markerValid()
                    ? marker.getUID()
                    : null);
        }
    }

    private void sendItem(File file, MapItem mapItem) {
        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled())
            return;
        if (mapItem != null) {
            Intent intent = new Intent();
            intent.putExtra("uid", mapItem.getUID());
            intent.putExtra("filepath", file.getAbsolutePath());

            //If it is a single image prompt for resolution
            if (ImageDropDownReceiver.ImageFileFilter.accept(
                    file.getParentFile(),
                    file.getName()))
                intent.setAction(ImageDropDownReceiver.IMAGE_SELECT_RESOLUTION);
            else
                intent.setAction(AttachmentBroadcastReceiver.SEND_ATTACHMENT);
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
    }

    private void toast(String str) {
        Toast.makeText(context,
                str, Toast.LENGTH_LONG).show();
    }

    private void toast(int strId) {
        toast(context.getString(strId));
    }

    /**
     * Get a column from the content resolver relating to this URI
     * only acts on URI's that start with:
     *    "content://com.android.providers.media.documents"
     *    "content://media/external"
     * @param contentURI Content URI
     * @param field Column name
     * @return Column value as string
     */
    public static String getContentField(final Context context,
            final Uri contentURI,
            final String field) {
        if (contentURI == null) {
            Log.w(TAG, "Failed to get field without URI");
            return null;
        }

        // Restrict the uris that are valid when querying a file path
        if (field.equals(MediaStore.MediaColumns.DATA)
                && !isSafeURI(contentURI))
            return null;

        try (Cursor cursor = context.getContentResolver()
                .query(contentURI, null, null, null, null)) {
            if (cursor != null) {
                cursor.moveToFirst();
                int idx = cursor.getColumnIndex(field);
                if (idx < 0)
                    return null;
                String data = cursor.getString(idx);
                if (field.equals(MediaStore.MediaColumns.DISPLAY_NAME)) {
                    // Make sure the display name doesn't contain any bad data
                    FileSystemUtils.validityScan(data);
                }
                return data;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get field from URI: "
                    + contentURI, e);
        }

        return null;
    }

    /**
     * Filter for determining which URIs are safe to query
     * @param contentURI Content URI
     * @return True if the URI is safe to query
     */
    private static boolean isSafeURI(final Uri contentURI) {
        final String sURI = contentURI.toString();
        return sURI
                .startsWith("content://com.android.providers.media.documents")
                || sURI.startsWith(
                        "content://com.android.providers.downloads.documents")
                || sURI.startsWith("content://media/external")
                || sURI.startsWith("content://0@media/external/")
                || sURI.startsWith(
                        "content://com.android.externalstorage.documents");
    }

    private final BroadcastReceiver _activityResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras == null) {
                Log.d(TAG, "No extras");
                return;
            }

            String filePath;
            final int requestCode = extras.getInt("requestCode");
            final int resultCode = extras.getInt("resultCode");
            final Intent data = extras.getParcelable("data");

            Log.d(TAG, "request: " + requestCode);
            Log.d(TAG, "result: " + resultCode);

            if (PIC_CAPTURED.equals(intent.getAction())) {
                // Returning from quick-pic activity
                if (data == null)
                    Log.d(TAG, "Data is null");

                //find and check attachment information
                filePath = intent.getStringExtra("path");

                Log.d(TAG, "Path: " + filePath);
                if (!FileSystemUtils.isFile(filePath)) {
                    Log.w(TAG, "Skipping missing result: " + resultCode + ", "
                            + filePath);
                    return;
                }
                try {
                    //Attach the file
                    File src = new File(FileSystemUtils
                            .validityScan(filePath));
                    attachFile(src, true, true);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot attach file", e);
                }
            } else if (requestCode == IMAGE_SELECT_CODE
                    || requestCode == VIDEO_SELECT_CODE
                    || requestCode == FILE_SELECT_CODE) {
                // Image/video/file import
                Log.d(TAG, "Got Activity Result: "
                        + (resultCode == Activity.RESULT_OK ? "OK" : "ERROR")
                        + " for request " + requestCode);
                if (resultCode != Activity.RESULT_OK) {
                    Log.d(TAG, "Skipping result: " + resultCode);
                    return;
                }

                if (data == null || data.getData() == null) {
                    Log.w(TAG, "Skipping result, no data: " + resultCode);
                    return;
                }

                final Uri dataUri = data.getData();
                if (dataUri != null) {
                    switch (dataUri.getScheme()) {
                        case "file":
                            filePath = dataUri.getPath();
                            break;
                        case "content":
                            // content://media/external/images/media/3951.
                            filePath = getContentField(context, dataUri,
                                    MediaStore.Images.ImageColumns.DATA);
                            break;
                        default:
                            filePath = null;
                            break;
                    }

                    if (filePath == null) {
                        // Newer Android versions do not provide the file path
                        // Get content stream + display name and save to attachments
                        // content://com.android.providers.media.documents/document/image:3951

                        String name = null;
                        boolean canOpen = false;
                        if (dataUri.getScheme().equals("content")) {
                            name = getContentField(context, dataUri,
                                    MediaStore.Images.ImageColumns.DISPLAY_NAME);
                            InputStream is = null;
                            try {
                                is = context.getContentResolver()
                                        .openInputStream(
                                                dataUri);
                                canOpen = true;
                            } catch (Exception ignored) {
                            } finally {
                                try {
                                    if (is != null)
                                        is.close();
                                } catch (Exception ignore) {
                                }

                            }
                        }
                        if (name != null && canOpen && (markerValid()
                                || curPath != null)) {
                            File att;
                            if (markerValid())
                                att = ImageDropDownReceiver
                                        .createAndGetPathToImageFromUID(
                                                marker.getUID(), "");
                            else
                                att = new File(curPath);
                            try {
                                if (att != null) {
                                    att = new File(
                                            FileSystemUtils.validityScan(
                                                    att.getParent()
                                                            + File.separator
                                                            + name));
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Invalid file name " + name);
                            }
                            final File finalAtt = att;
                            if (finalAtt != null) {
                                if (IOProviderFactory.exists(finalAtt)) {
                                    AlertDialog.Builder adb = new AlertDialog.Builder(
                                            context);
                                    adb.setTitle(R.string.overwrite_existing);
                                    adb.setMessage(context
                                            .getString(R.string.overwrite)
                                            + name
                                            + context
                                                    .getString(
                                                            R.string.question_mark_symbol));
                                    adb.setPositiveButton(
                                            R.string.ok,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(
                                                        DialogInterface d,
                                                        int w) {
                                                    FileSystemUtils
                                                            .delete(finalAtt);
                                                    if (extractContent(context,
                                                            dataUri, finalAtt))
                                                        addItem(finalAtt);
                                                    else
                                                        toast(R.string.failed_to_import);
                                                }
                                            });
                                    adb.setNegativeButton(R.string.cancel,
                                            null);
                                    adb.show();
                                    return;
                                } else if (extractContent(context, dataUri,
                                        att)) {
                                    addItem(att);
                                    return;
                                }
                            }
                        }
                    }
                } else
                    filePath = null;
            } else
                return;

            if (!PIC_CAPTURED.equals(intent.getAction())) {
                if (!FileSystemUtils.isEmpty(filePath)) {
                    File importFile = null;
                    try {
                        importFile = new File(
                                FileSystemUtils.validityScan(filePath));
                    } catch (Exception e) {
                        Log.w(TAG, "Cannot import file", e);
                    }

                    if (importFile == null || !importFile.exists()) {
                        Log.w(TAG,
                                "Skipping missing result: " + resultCode + ", "
                                        + filePath);
                        toast(R.string.failed_to_import);
                        return;
                    }
                }

                try {
                    File src = new File(
                            FileSystemUtils.validityScan(filePath));
                    attachFile(src, false, false);
                } catch (Exception e) {
                    Log.w(TAG, "Cannot import file", e);
                }
            }
        }
    };

    /**
     * Extract data from the content resolver to a file
     * @param ctx Content context
     * @param dataUri Content URI
     * @param outFile Output file
     * @return True if successful
     */
    public static boolean extractContent(Context ctx, Uri dataUri,
            File outFile) {
        try (InputStream is = ctx.getContentResolver()
                .openInputStream(dataUri)) {
            if (is != null) {
                try (OutputStream os = IOProviderFactory
                        .getOutputStream(outFile)) {
                    FileSystemUtils.copyStream(is, os);
                }
                return IOProviderFactory.exists(outFile);
            } else {
                Log.e(TAG, "Failed to extract content from "
                        + dataUri + " to " + outFile);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract content from "
                    + dataUri + " to " + outFile, e);
            return false;
        }
    }

    /**
     * Pan and zoom to a feature set
     * @param e Feature envelope
     */
    public static void zoomToBounds(Envelope e) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;
        ATAKUtilities.scaleToFit(mv,
                new GeoBounds(e.minY, e.minX, e.maxY, e.maxX),
                mv.getWidth(), mv.getHeight());
    }

    public static void displayGallery(String title, List<GalleryItem> items) {
        List<String> files = new ArrayList<>();
        List<String> uids = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (GalleryItem item : items) {
            if (item instanceof GalleryFileItem) {
                GalleryFileItem gfi = (GalleryFileItem) item;
                File f = gfi.getFile();
                MapItem mi = gfi.getMapItem();
                if (f != null && !paths.contains(f.getAbsolutePath())) {
                    files.add(f.getAbsolutePath());
                    uids.add(mi != null ? mi.getUID() : null);
                    paths.add(f.getAbsolutePath());
                }
            } else {
                // TODO: If only there was a way to mix blobs and files in one gallery
            }
        }
        Intent i = new Intent(ImageGalleryReceiver.IMAGE_GALLERY);
        i.putExtra("title", title);
        i.putExtra("files", files.toArray(new String[0]));
        i.putExtra("uids", uids.toArray(new String[0]));
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    public static void displayGallery() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        List<GalleryItem> items = new ArrayList<>();
        List<URIContentProvider> providers = URIContentManager.getInstance()
                .getProviders(GalleryContentProvider.TOOL);
        for (URIContentProvider pr : providers) {
            if (!(pr instanceof GalleryContentProvider))
                continue;
            items.addAll(((GalleryContentProvider) pr).getItems());
        }
        displayGallery(mv.getContext().getString(R.string.gallery_title),
                items);
    }
}
