
package com.atakmap.android.image;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import com.atakmap.android.attachment.AttachmentMapOverlay;
import com.atakmap.android.attachment.export.AttachmentExportMarshal;
import com.atakmap.android.attachment.export.AttachmentExportMarshal.FileExportable;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.hierarchy.filters.FOVFilter.MapState;
import com.atakmap.android.hierarchy.filters.MultiFilter;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.VideoDropDownReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.AtakMapView;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ImageGalleryFileAdapter extends ImageGalleryBaseAdapter
        implements AtakMapView.OnMapMovedListener,
        MapEventDispatcher.MapEventDispatchListener {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    //==================================
    //  PUBLIC NESTED TYPES
    //==================================

    public enum SortBy {
        TIME(new Comparator<GalleryFileItem>() {
            @Override
            public int compare(GalleryFileItem lhs,
                    GalleryFileItem rhs) {
                return (int) (lhs.gpsTime - rhs.gpsTime);
            }
        }),
        NAME(new Comparator<GalleryFileItem>() {
            @Override
            public int compare(GalleryFileItem lhs,
                    GalleryFileItem rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });

        SortBy(Comparator<GalleryFileItem> cmp) {
            comparator = cmp;
        }

        private final Comparator<GalleryFileItem> comparator;
    }

    //==================================
    //  PUBLIC METHODS
    //==================================

    /**
     * Create a new gallery file adapter
     * @param view Map view
     * @param files List of files
     * @param uids List of attachment UIDs for each file
     * @param showDetails True to show info below each file
     * @param callbackTag2 Delete callback intent action
     * @param progressBar Progress bar view
     */
    public ImageGalleryFileAdapter(MapView view,
            final List<File> files,
            final List<String> uids,
            boolean showDetails,
            String callbackTag2,
            View progressBar) {
        super(view, progressBar, showDetails);
        this.callbackTag2 = callbackTag2;
        this.markerAttachments = false;
        this.usingUIDs = uids != null && uids.size() == files.size();

        showProgressBar();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Convert files and UIDs to single object
                List<GalleryFileItem> imgFiles = new ArrayList<>();

                if (usingUIDs) {
                    MapGroup group = getMapView().getRootGroup();
                    int i = 0;
                    for (File f : files) {
                        MapItem mapItem = group.deepFindUID(uids.get(i++));
                        imgFiles.add(new GalleryFileItem(getMapView(),
                                f, mapItem));
                    }
                } else {
                    for (File f : files) {
                        imgFiles.add(new GalleryFileItem(getMapView(),
                                f, null));
                    }
                }
                synchronized (allItems) {
                    allItems.clear();
                    allItems.addAll(imgFiles);
                }
                refreshImpl();
            }
        }, TAG + "-Init1").start();
    }

    /**
     * Create a new gallery file adapter for displaying marker attachments
     * @param view Map view
     * @param files List of files to display
     * @param marker Attachment marker
     * @param showDetails True to display info below each file
     * @param callbackTag2 Delete callback intent action
     * @param progressBar Progress bar view
     */
    public ImageGalleryFileAdapter(MapView view, final List<File> files,
            final MapItem marker, boolean showDetails, String callbackTag2,
            View progressBar) {
        super(view, progressBar, showDetails);
        this.callbackTag2 = callbackTag2;
        this.markerAttachments = true;
        this.usingUIDs = marker != null;

        showProgressBar();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Convert files and UIDs to single object
                List<GalleryFileItem> imgFiles = new ArrayList<>();
                for (File f : files) {
                    imgFiles.add(new GalleryFileItem(getMapView(), f, marker));
                }
                synchronized (allItems) {
                    allItems.clear();
                    allItems.addAll(imgFiles);
                }
                refreshImpl();
            }
        }, TAG + "-Init2").start();
    }

    public void clearSelection() {
        selection.clear();
    }

    //
    // Interface hernia for com.atakmap.android.attachment.AttachmentMapOverlay
    //
    public static File getCacheFile(File file,
            String extension) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;
        return getCacheFile(new GalleryFileItem(mv, file, null), extension);
    }

    public boolean getMultiSelect() {
        return multiSelect;
    }

    public String[] getSelection() {
        return selection.toArray(new String[0]);
    }

    public void removeImages(String... imagePaths) {
        if (!FileSystemUtils.isEmpty(imagePaths)) {
            List<GalleryFileItem> files = new ArrayList<>();
            synchronized (allItems) {
                for (String path : imagePaths) {
                    for (GalleryFileItem imgFile : allItems) {
                        if (imgFile.getAbsolutePath().equals(path)) {
                            files.add(imgFile);
                            AttachmentMapOverlay.deleteAttachmentMarker(
                                    imgFile.getMapItem());
                            allItems.remove(imgFile);
                            break;
                        }
                    }
                }
            }
            synchronized (viewItems) {
                selection.removeAll(Arrays.asList(imagePaths));
                if (viewItems.removeAll(files))
                    refresh();
            }
        }
    }

    public void removeFile(File f) {
        removeImages(f.getAbsolutePath());
    }

    /**
     * Delete a file from the filesystem, including any associated
     * metadata files (such as .ntf.aux.xml)
     * @param f
     */
    public void deleteFile(File f) {
        if (callbackTag2 != null) {
            // Send delete intent
            AtakBroadcast.getInstance().sendBroadcast(new Intent(callbackTag2)
                    .putExtra("file", f.getAbsolutePath()));
        } else {
            // Remove file
            File metadata = getMetadataFile(f);
            if (metadata != null)
                FileSystemUtils.deleteFile(f);
            FileSystemUtils.deleteFile(f);
            URIContentHandler h = URIContentManager.getInstance().getHandler(f);
            if (h != null)
                h.deleteContent();
        }
    }

    public void addFile(File file, String uid) {
        if (IOProviderFactory.isDirectory(file)) {
            File[] files = IOProviderFactory.listFiles(file);
            if (FileSystemUtils.isEmpty(files))
                return;
            for (File f : files)
                addFile(f, uid);
            return;
        }
        MapGroup root = getMapView().getRootGroup();
        MapItem mapItem = uid != null ? root.deepFindUID(uid) : null;
        synchronized (allItems) {
            // Remove existing items with the same path
            List<GalleryFileItem> rem = new ArrayList<>();
            for (GalleryFileItem f : allItems) {
                if (f != null
                        && f.getAbsolutePath().equals(file.getAbsolutePath()))
                    rem.add(f);
            }
            allItems.removeAll(rem);
            // Add new file item
            allItems.add(new GalleryFileItem(getMapView(), file, mapItem));
        }
        refresh();
    }

    /**
     * Listen for ITEM_REMOVED events which target map items we care about
     * @param event Map event
     */
    @Override
    public void onMapEvent(MapEvent event) {
        boolean refresh = false;
        synchronized (allItems) {
            List<GalleryFileItem> toRemove = new ArrayList<>();
            for (GalleryFileItem f : allItems) {
                if (f.getMapItem() == event.getItem()) {
                    toRemove.add(f);
                    refresh = true;
                }
            }
            allItems.removeAll(toRemove);
        }
        if (refresh)
            refresh();
    }

    public void setMultiSelect(boolean multiSelect) {
        if (this.multiSelect != multiSelect) {
            this.multiSelect = multiSelect;
            clearSelection();
            notifyDataSetChanged();
        }
    }

    /**
     * Set whether to show all images or only those within field-of-view
     * @param show True to show all, false to only show visible
     */
    public void showAll(boolean show) {
        if (filterByFOV == show) {
            filterByFOV = !show;
            refresh();
        }
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        if (filterByFOV)
            refresh();
    }

    /**
     * Set sorting method
     * @param sortType Either TIME_SORT or NAME_SORT.
     */
    public void sortItems(SortBy sortType) {
        if (this.sortType != sortType) {
            this.sortType = sortType;
            refresh();
        }
    }

    //==================================
    //  ImageGalleryBaseAdapter INTERFACE
    //==================================

    @Override
    public void customizeGalleryView(View gallery) {
        titleView = gallery.findViewById(R.id.gridImagesTitle);
        originalTitle = titleView.getText();

        doneButton = gallery.findViewById(R.id.gridImagesSelectDone);
        doneButton.setVisibility(View.GONE);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                endMultiSelectAction(false);
                refresh();
            }
        });

        cancelButton = gallery
                .findViewById(R.id.gridImagesSelectCancel);
        cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                endMultiSelectAction(true);
            }
        });

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getMapView().getContext());

        CheckBox showAll = gallery.findViewById(R.id.showAll_cb);

        if (usingUIDs && !markerAttachments) {
            boolean filterByFOV = prefs.getBoolean(FOVFilter.PREF, false);

            showAll.setEnabled(true);
            showAll.setChecked(!filterByFOV);
            showAll(!filterByFOV);
        } else {
            showAll.setEnabled(false);
            showAll.setChecked(true);
        }
        showAll.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (buttonView.isEnabled()) {
                            showAll(isChecked);
                            prefs.edit().putBoolean(FOVFilter.PREF,
                                    !isChecked).apply();
                        } else {
                            showAll(true);
                        }
                    }
                });

        nameSortButton = gallery.findViewById(R.id.alpha_sort_btn);
        nameSortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortItems(ImageGalleryFileAdapter.SortBy.NAME);
            }
        });

        timeSortButton = gallery.findViewById(R.id.time_sort_btn);
        timeSortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sortItems(ImageGalleryFileAdapter.SortBy.TIME);
            }
        });
    }

    @Override
    public void customizeToolbarView(View toolbar) {
        View multiButton = toolbar.findViewById(R.id.buttonImageMultiSelect);

        multiButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("InflateParams")
            @Override
            public void onClick(View view) {
                if (getMultiSelect()) {
                    Log.d(TAG, "Already in multiselect mode, ending");
                    endMultiSelectAction(true);
                    return;
                }
                Resources r = getMapView().getResources();
                TileButtonDialog d = new TileButtonDialog(getMapView());
                d.addButton(r.getDrawable(R.drawable.export_menu_default),
                        r.getString(R.string.export));
                d.addButton(r.getDrawable(R.drawable.ic_menu_delete),
                        r.getString(R.string.delete_no_space));
                d.show(R.string.multiselect_dialogue, true);
                d.setOnClickListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which != -1)
                            beginMultiSelectAction(which == 0);
                    }
                });
            }
        });
        multiButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void dispose() {
        unregisterListeners();
        refreshThread.dispose();
        super.dispose();
    }

    @Override
    public DropDown.OnStateListener getDropDownStateListener() {
        return dropDownStateListener;
    }

    //==================================
    //  Adapter INTERFACE
    //==================================

    @Override
    public int getCount() {
        synchronized (viewItems) {
            return viewItems.size();
        }
    }

    @Override
    public Object getItem(int position) {
        synchronized (viewItems) {
            if (position >= 0 && position < viewItems.size()) {
                return viewItems.get(position);
            }
        }

        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    // Create a new ImageView for each item referenced by the Adapter
    @SuppressLint("InflateParams")
    @Override
    public View getView(final int position,
            View convertView,
            ViewGroup parent) {
        final ViewHolder<GalleryFileItem> holder;
        final GalleryFileItem fileItem = (GalleryFileItem) getItem(position);

        if (convertView == null) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());

            convertView = inf.inflate(R.layout.image_grid_item, null);
            holder = new ViewHolder<>(convertView, fileItem);
            convertView.setTag(holder);
        } else {
            @SuppressWarnings("unchecked")
            ViewHolder<GalleryFileItem> fileViewHolder = (ViewHolder<GalleryFileItem>) convertView
                    .getTag();

            holder = fileViewHolder;
            holder.setItem(fileItem);
        }

        if (fileItem == null || position >= getCount()) {
            Log.w(TAG, "Invalid position or null item @ " + position);
            Drawable icon = getMapView().getContext()
                    .getResources()
                    .getDrawable(R.drawable.details);

            holder.imageView.setImageDrawable(icon);
            holder.typeText.setText("");
            holder.nameText.setText("");
            return convertView;
        }

        ResourceFile.MIMEType mimeType = ResourceFile
                .getMIMETypeForFile(fileItem.getName());

        boolean displayDetails = getDisplayDetails();

        if (displayDetails) {
            holder.detailsLayout.setVisibility(View.VISIBLE);
            holder.typeText.setText(mimeType != null
                    ? mimeType.name()
                    : FileSystemUtils.getExtension(fileItem.getFile(),
                            true,
                            true));
            holder.nameText.setText(fileItem.getName());
        } else {
            holder.detailsLayout.setVisibility(View.GONE);
        }

        MapItem item = displayDetails ? fileItem.getMapItem() : null;

        if (!markerAttachments && item != null) {
            ATAKUtilities.SetIcon(getMapView().getContext(),
                    holder.iconView,
                    item);
            holder.titleText
                    .setText(ATAKUtilities.getDisplayName(item));
            holder.titleText.setVisibility(View.VISIBLE);
        } else {
            holder.iconView.setVisibility(ImageView.GONE);
            holder.titleText.setVisibility(TextView.GONE);
        }

        holder.selectLayout.setVisibility(multiSelect
                ? CheckBox.VISIBLE
                : CheckBox.GONE);
        holder.selectCheck.setOnCheckedChangeListener(null);
        holder.selectCheck.setChecked(multiSelect && selection.contains(
                fileItem.getAbsolutePath()));

        final View checkboxParent = holder.imageView;

        // tap on parent constitutes a checkbox click
        checkboxParent.post(new Runnable() {
            @Override
            public void run() {
                if (multiSelect) {
                    Rect outRect = new Rect();

                    checkboxParent.getHitRect(outRect);
                    checkboxParent.setTouchDelegate(
                            new TouchDelegate(outRect, holder.selectCheck));
                } else {
                    // Restore regular click events
                    checkboxParent.setTouchDelegate(null);
                }
            }
        });

        holder.selectCheck.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton,
                            boolean bSelected) {
                        if (bSelected) {
                            // Log.d(TAG, "Selected: " + file.getAbsolutePath());
                            if (!selection
                                    .contains(fileItem.getAbsolutePath())) {
                                selection.add(fileItem.getAbsolutePath());
                            }
                        } else {
                            // Log.d(TAG, "Unselected: " + file.getAbsolutePath());
                            selection.remove(fileItem.getAbsolutePath());
                        }
                    }
                });

        holder.playOverlay.setVisibility(ImageView.GONE);
        holder.imageView.setImageResource(android.R.color.transparent);

        final String imageName = fileItem.getName();
        if (imageName != null
                && ImageDropDownReceiver.ImageFileFilter
                        .accept(null, imageName)) {
            setItemThumbnail(holder, imageThumbnailCreator);
        } else if (imageName != null
                && ImageDropDownReceiver.VideoFileFilter
                        .accept(null, imageName)) {
            setItemThumbnail(holder, videoThumbnailCreator);
            holder.playOverlay.setVisibility(ImageView.VISIBLE);
        } else {
            holder.imageView.setImageDrawable(fileItem.getIcon());
            holder.imageView.setColorFilter(fileItem.getIconColor(),
                    PorterDuff.Mode.MULTIPLY);
        }

        return convertView;
    }

    //==================================
    //
    //  PROTECTED INTERFACE
    //
    //==================================

    //==================================
    //  ImageGalleryBaseAdapter INTERFACE
    //==================================

    @Override
    protected void displayFile(GalleryItem item) {
        if (!(item instanceof GalleryFileItem))
            return;
        File f = ((GalleryFileItem) item).getFile();
        if (!FileSystemUtils.isFile(f))
            return;
        if (((GalleryFileItem) item).goTo(false))
            return;
        Log.v(TAG, "Viewing file: " + item.getName());
        Collection<ImportResolver> resolvers = ImportFilesTask.GetSorters(
                getMapView().getContext(), true, false, true, false);
        for (ImportResolver r : resolvers) {
            if (r.match(f)) {
                // We have at least one resolver - import the file
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ImportExportMapComponent.USER_HANDLE_IMPORT_FILE_ACTION)
                                .putExtra("filepath", f.getAbsolutePath())
                                .putExtra("promptOnMultipleMatch", true)
                                .putExtra("importInPlace", true));
                return;
            }
        }
        MIMETypeMapper.openFile(f, getMapView().getContext());
    }

    @Override
    protected void displayVideo(GalleryItem item) {
        if (!(item instanceof GalleryFileItem))
            return;
        File f = ((GalleryFileItem) item).getFile();
        if (!FileSystemUtils.isFile(f))
            return;
        Log.v(TAG, "Viewing video: " + item.getName());
        Intent i = new Intent(VideoDropDownReceiver.DISPLAY);
        i.putExtra("CONNECTION_ENTRY", new ConnectionEntry(f));
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    @Override
    protected String[] getImageURIs() {
        List<String> imageURIs = null;

        synchronized (viewItems) {
            imageURIs = new ArrayList<>(viewItems.size());
            for (GalleryFileItem fi : viewItems) {
                final ResourceFile.MIMEType mimeType = ResourceFile
                        .getMIMETypeForFile(fi.getAbsolutePath());

                if (mimeType != null && mimeType.MIME.startsWith("image/")) {
                    imageURIs.add(fi.getURI());
                }
            }
        }

        return imageURIs.toArray(new String[0]);
    }

    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================

    /**
     * Hide extraneous metadata files
     * Currently only applies to NITF metadata files created by GDAL
     * whenever NITF metadata such as file title (image caption) is modified
     */
    private static class MetadataFileFilter extends HierarchyListFilter {

        public MetadataFileFilter() {
            super(null);
        }

        @Override
        public boolean accept(HierarchyListItem item) {
            if (item instanceof GalleryFileItem) {
                File f = ((GalleryFileItem) item).getFile();
                return !f.getName().endsWith(METADATA_EXT);
            }
            return false;
        }
    }

    private static class ImageThumbnailCreator
            implements ThumbnailCreator<GalleryFileItem> {
        @Override
        public Bitmap createThumbnail(GalleryFileItem fileItem) {
            Bitmap bitmap;

            if (ImageContainer.NITF_FilenameFilter.accept(null,
                    fileItem.getName()))
                bitmap = ImageContainer.readNITF(fileItem.getFile(),
                        THUMB_SIZE * THUMB_SIZE);
            else {
                String path = fileItem.getAbsolutePath();
                BitmapFactory.Options opts = new BitmapFactory.Options();

                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                opts.inJustDecodeBounds = true;
                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(new File(path))) {
                    BitmapFactory.decodeStream(fis, null, opts);
                } catch (IOException ignored) {
                }
                opts.inJustDecodeBounds = false;
                if (opts.outWidth > THUMB_SIZE && opts.outHeight > THUMB_SIZE) {
                    opts.inSampleSize = 1 << (int) (MathUtils.log2(Math.min(
                            opts.outWidth,
                            opts.outHeight))
                            - MathUtils.log2(THUMB_SIZE));
                }
                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(new File(path))) {
                    bitmap = BitmapFactory.decodeStream(fis, null, opts);
                } catch (IOException e) {
                    bitmap = null;
                }
            }

            TiffImageMetadata exif = ExifHelper
                    .getExifMetadata(fileItem.getFile());
            int imageOrientation = exif != null
                    ? ExifHelper.getInt(exif,
                            TiffConstants.EXIF_TAG_ORIENTATION,
                            0)
                    : 0;

            return rotateBitmap(
                    ThumbnailUtils.extractThumbnail(bitmap, THUMB_SIZE,
                            THUMB_SIZE,
                            ThumbnailUtils.OPTIONS_RECYCLE_INPUT),
                    imageOrientation);
        }
    }

    private static class VideoThumbnailCreator
            implements ThumbnailCreator<GalleryFileItem> {
        @Override
        public Bitmap createThumbnail(GalleryFileItem fileItem) {
            Bitmap thumb = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(fileItem.getAbsolutePath());

                thumb = ThumbnailUtils.extractThumbnail(
                        retriever.getFrameAtTime(-1),
                        THUMB_SIZE, THUMB_SIZE,
                        ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            } catch (Exception e) {
                Log.w(TAG, "Failed to create video thumbnail", e);
            } finally {
                try {
                    retriever.release();
                } catch (RuntimeException ignored) {
                }
            }

            return thumb;
        }
    }

    //==================================
    //  PRIVATE METHODS
    //==================================

    /**
     * Initiate multi select action
     *
     * @param export true to export selections, false to delete selections
     */
    public void beginMultiSelectAction(final boolean export) {
        Context ctx = getMapView().getContext();
        setMultiSelect(true);
        String action = ctx.getString(export ? R.string.export
                : R.string.delete2);
        titleView.setText(action + " - " + originalTitle);
        nameSortButton.setVisibility(View.GONE);
        timeSortButton.setVisibility(View.GONE);
        doneButton.setText(action);
        doneButton.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        exportMode = export;
    }

    public void endMultiSelectAction(final boolean cancel) {
        Context ctx = getMapView().getContext();
        if (cancel) {
            Log.d(TAG, "Cancelled MultiSelect action");
            endMultiSelect();
            Toast.makeText(ctx,
                    ctx.getString(
                            exportMode ? R.string.export : R.string.delete2)
                            + ctx.getString(R.string.cancelled2),
                    Toast.LENGTH_LONG).show();
        } else {
            // if no selections, prompt user
            final String[] selected = getSelection();
            if (selected == null || selected.length < 1) {
                Log.d(TAG,
                        "No items selected, cannot perform multi selection action");
                Toast.makeText(ctx, ctx.getString(R.string.image_text3),
                        Toast.LENGTH_LONG).show();
            } else if (exportMode)
                showExportDialog(selected);
            else
                showDeleteDialog(selected);
        }
    }

    private void endMultiSelect() {
        setMultiSelect(false);
        titleView.setText(originalTitle);
        doneButton.setText(R.string.done);
        doneButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        nameSortButton.setVisibility(View.VISIBLE);
        timeSortButton.setVisibility(View.VISIBLE);
    }

    private void export(String[] files) {
        List<Exportable> exports = new ArrayList<>();
        int fileCount = 0;
        for (String p : files) {
            File f = new File(p);
            if (IOProviderFactory.exists(f) && IOProviderFactory.isFile(f)) {
                File metadata = getMetadataFile(f);
                if (metadata != null)
                    exports.add(new FileExportable(metadata));
                exports.add(new FileExportable(f));
                fileCount++;
            }
        }

        if (fileCount > 0) {
            Log.d(TAG, "Exporting file count: " + fileCount);

            try {
                new AttachmentExportMarshal(getMapView().getContext())
                        .execute(exports);
            } catch (Exception e) {
                Log.e(TAG, "Failed to export", e);
            } // TODO notify user?
        } else {
            Log.w(TAG, "Cannot export empty list");
        }
    }

    private void send(String[] files) {
        if (!MissionPackageMapComponent.getInstance().checkFileSharingEnabled())
            return;
        Intent sendList = new Intent(ContactPresenceDropdown.SEND_LIST)
                .putExtra("files", files)
                .putExtra("sendCallback", ImageGalleryReceiver.SEND_FILES)
                .putExtra("disableBroadcast", true);
        AtakBroadcast.getInstance().sendBroadcast(sendList);
    }

    /**
     * Filter and sort images based on "Show All" option
     */
    @Override
    protected void refreshImpl() {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                showProgressBar();
            }
        });

        // Filter
        final List<GalleryFileItem> filteredItems = new ArrayList<>();

        addFilter(new MetadataFileFilter());

        // Filter out files that are not in the current FOV
        if (filterByFOV)
            addFilter(new FOVFilter(new MapState(getMapView()),
                    new HierarchyListItem.SortAlphabet()));
        else
            removeFilter(FOVFilter.class);

        MultiFilter filter;
        synchronized (filters) {
            filter = new MultiFilter(null,
                    new ArrayList<>(filters.values()));
        }

        List<GalleryFileItem> all;
        synchronized (allItems) {
            all = new ArrayList<>(allItems);
        }
        for (GalleryFileItem img : all) {
            img.refreshImpl();
            if (FileSystemUtils.isFile(img.getFile()) && filter.accept(img))
                filteredItems.add(img);
        }

        // Sort
        Collections.sort(filteredItems, sortType.comparator);

        // Update view
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                synchronized (viewItems) {
                    if (!filteredItems.equals(viewItems)) {
                        viewItems.clear();
                        viewItems.addAll(filteredItems);
                    }
                    notifyDataSetChanged();
                    hideProgressBar();
                    if (receiver != null)
                        receiver.onRefresh();
                }
            }
        });
    }

    private synchronized void registerListeners() {
        if (!listenersRegistered) {
            getMapView().addOnMapMovedListener(this);
            getMapView().getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_REMOVED, this);
            listenersRegistered = true;
        }
    }

    private synchronized void unregisterListeners() {
        getMapView().removeOnMapMovedListener(this);
        getMapView().getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        listenersRegistered = false;
    }

    private void showDeleteDialog(final String[] selected) {
        new AlertDialog.Builder(getMapView().getContext())
                .setTitle(R.string.confirm_delete)
                .setIcon(R.drawable.ic_menu_delete)
                .setMessage(
                        getMapView().getContext().getString(R.string.delete)
                                + selected.length
                                + getMapView().getContext().getString(
                                        R.string.files_question))
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int i) {
                                dialog.dismiss();
                                Log.d(TAG, "Deleting file count: "
                                        + selected.length);
                                for (String s : selected) {
                                    File f = new File(s);
                                    deleteFile(f);
                                }
                                removeImages(selected);
                                endMultiSelect();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showExportDialog(final String[] selected) {
        Context ctx = getMapView().getContext();
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.export)
                .setIcon(R.drawable.atak_menu_export)
                .setMessage(ctx.getString(
                        R.string.selected_exports_export_or_send,
                        selected.length))
                .setPositiveButton(R.string.export,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int i) {
                                export(selected);
                                endMultiSelect();
                            }
                        })
                .setNeutralButton(R.string.send,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int i) {
                                send(selected);
                                endMultiSelect();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private File getMetadataFile(File f) {
        if (ImageContainer.NITF_FilenameFilter.accept(f.getParentFile(),
                f.getName())) {
            // Clean-up metadata files
            File xml = new File(f.getAbsolutePath() + METADATA_EXT);
            if (IOProviderFactory.exists(xml))
                return xml;
        }
        return null;
    }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

    private static final String TAG = "ImageGalleryAdapter";
    private static final String METADATA_EXT = ".aux.xml";

    private static final ThumbnailCreator<GalleryFileItem> imageThumbnailCreator = new ImageThumbnailCreator();
    private static final ThumbnailCreator<GalleryFileItem> videoThumbnailCreator = new VideoThumbnailCreator();

    private final DropDown.OnStateListener dropDownStateListener = new DropDown.OnStateListener() {
        @Override
        public void onDropDownSelectionRemoved() {
        }

        @Override
        public void onDropDownClose() {
        }

        @Override
        public void onDropDownSizeChanged(double width,
                double height) {
        }

        @Override
        public void onDropDownVisible(boolean v) {
            if (v) {
                clearSelection();
                registerListeners();

                //
                // The map might have been moved while we weren't listening.
                //
                if (filterByFOV || !markerAttachments) {
                    refresh();
                }
            } else {
                unregisterListeners();
            }
        }
    };

    private final List<GalleryFileItem> allItems = new ArrayList<>();
    private final List<GalleryFileItem> viewItems // Displayed items.
            = new ArrayList<>();
    private final List<String> selection // Selected item paths.
            = new ArrayList<>();
    private final String callbackTag2;
    private final boolean markerAttachments;
    private final boolean usingUIDs;

    private SortBy sortType = SortBy.NAME;
    private TextView titleView;
    private Button cancelButton;
    private Button doneButton;
    private View nameSortButton;
    private View timeSortButton;
    private boolean exportMode;
    private CharSequence originalTitle;
    private boolean filterByFOV;
    private boolean multiSelect;
    private boolean listenersRegistered;
}
