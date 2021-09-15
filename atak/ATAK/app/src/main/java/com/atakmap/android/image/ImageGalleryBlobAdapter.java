
package com.atakmap.android.image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import com.atakmap.android.hierarchy.filters.MultiFilter;
import com.atakmap.android.hierarchy.items.AbstractChildlessListItem;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import com.atakmap.util.zip.IoUtils;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.FileCache;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ImageGalleryBlobAdapter
        extends ImageGalleryBaseAdapter {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    public ImageGalleryBlobAdapter(MapView view,
            final String[] uris,
            final String uid,
            boolean showDetails,
            View progressBar) {
        super(view, progressBar, showDetails);

        showProgressBar();

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<BlobItem> tmpList = new ArrayList<>();
                MapItem item = getMapView().getRootGroup().deepFindUID(uid);

                for (String uri : uris)
                    tmpList.add(new BlobItem(uri, item));
                synchronized (blobItems) {
                    blobItems.clear();
                    blobItems.addAll(tmpList);
                }
                refresh();
            }
        }, TAG + "-Init").start();
    }

    public BlobItem getImage(int position) {
        BlobItem result = null;

        synchronized (viewItems) {
            if (position >= 0 && position < viewItems.size())
                result = viewItems.get(position);
        }

        return result;
    }

    //==================================
    //  ImageGalleryBaseAdapter INTERFACE
    //==================================

    @Override
    public void customizeGalleryView(View gv) {
        //
        // We don't need/want sorting buttons or "show all" check box.
        //
        gv.findViewById(R.id.time_sort_btn).setVisibility(View.GONE);
        gv.findViewById(R.id.alpha_sort_btn).setVisibility(View.GONE);
        gv.findViewById(R.id.showAll_cb).setVisibility(View.GONE);
        gv.findViewById(R.id.gridImagesSelectCancel).setVisibility(View.GONE);
        gv.findViewById(R.id.gridImagesSelectDone).setVisibility(View.GONE);
    }

    @Override
    public void customizeToolbarView(View tbv) {
        tbv.findViewById(R.id.buttonImageMultiSelect).setVisibility(View.GONE);
    }

    @Override
    public OnStateListener getDropDownStateListener() {
        return null;
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
            return viewItems.get(position);
        }
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
        final ViewHolder<BlobItem> holder;
        final BlobItem blobItem = (BlobItem) getItem(position);

        if (convertView == null) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());

            convertView = inf.inflate(R.layout.image_grid_item, null);
            holder = new ViewHolder<>(convertView, blobItem);
            convertView.setTag(holder);
        } else {
            @SuppressWarnings("unchecked")
            ViewHolder<BlobItem> blobViewHolder = (ViewHolder<BlobItem>) convertView
                    .getTag();

            holder = blobViewHolder;
            holder.setItem(blobItem);
        }

        if (position >= 0 && position < getCount()) {
            final String imageName = blobItem.getName();
            final ResourceFile.MIMEType mimeType = ResourceFile
                    .getMIMETypeForMIME(blobItem.getType());

            if (getDisplayDetails()) {
                holder.detailsLayout.setVisibility(View.VISIBLE);
                holder.typeText.setText(mimeType != null
                        ? mimeType.name()
                        : getExtension(imageName));
                holder.nameText.setText(blobItem.getName());
            } else {
                holder.detailsLayout.setVisibility(View.GONE);
            }

            //
            // The images are always from the same MapItem, so no UID-specific
            // items need be displayed.
            //
            holder.iconView.setVisibility(View.GONE);
            holder.titleText.setVisibility(View.GONE);
            //
            // Selection is unsupported.
            //
            holder.selectLayout.setVisibility(View.GONE);
            //            holder.selectCheck.setOnCheckedChangeListener (null);
            //            holder.selectCheck.setChecked (false);

            holder.playOverlay.setVisibility(View.GONE);
            holder.imageView.setImageResource(android.R.color.transparent);

            if (ImageDropDownReceiver.ImageFileFilter.accept(null, imageName)) {
                setItemThumbnail(holder, imageThumbnailCreator);
            } else if (ImageDropDownReceiver.VideoFileFilter.accept(null,
                    imageName)) {
                setItemThumbnail(holder, videoThumbnailCreator);
                holder.playOverlay.setVisibility(ImageView.VISIBLE);
            } else if (mimeType != null) // Use MIME type icon.
            {
                ATAKUtilities.SetIcon(getMapView().getContext(),
                        holder.imageView,
                        mimeType.ICON_URI,
                        Color.WHITE);
            } else // Last resort; display a generic icon.
            {
                Log.d(TAG, "No supported icon: " + position);
                holder.imageView.setImageDrawable(getMapView().getContext()
                        .getResources()
                        .getDrawable(R.drawable.details));
            }
        } else {
            Log.w(TAG, "Invalid position: " + position);
            Drawable icon = getMapView().getContext()
                    .getResources()
                    .getDrawable(R.drawable.details);

            holder.imageView.setImageDrawable(icon);
            holder.typeText.setText("");
            holder.nameText.setText("");
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
        Log.w(TAG, "Unexpected blob attachment: " + item.getName()
                + " of type: " + ((BlobItem) item).getType());
    }

    @Override
    protected void displayVideo(GalleryItem item) {
        File videoFile = getVideoFile((BlobItem) item);

        if (videoFile != null) {
            // handle video
            Log.v(TAG, "Viewing video: " + item.getName());

            //            String videoPath = videoFile.getAbsolutePath ();
            //            Intent display
            //                = new Intent ("com.atakmap.maps.video.DISPLAY")
            //                      .putExtra ("videoUrl", "file://" + videoPath)
            //                      .putExtra ("uid", itemUID);
            //
            //            AtakBroadcast.getInstance ().sendBroadcast (display);

            // TODO use external video player while working Bug 1269
            MIMETypeMapper.openFile(videoFile,
                    getMapView().getContext());
        }
    }

    @Override
    protected String[] getImageURIs() {
        List<String> imageURIs = null;

        synchronized (viewItems) {
            imageURIs = new ArrayList<>(viewItems.size());
            for (BlobItem bi : viewItems) {
                if (bi.getType().startsWith("image/")) {
                    imageURIs.add(bi.getURI());
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

    //==================================
    //  PRIVATE NESTED TYPES
    //==================================

    private static class BlobItem extends AbstractChildlessListItem
            implements GalleryItem, MapItemUser {
        //==================================
        //
        //  PUBLIC INTERFACE
        //
        //==================================

        public synchronized byte[] getImageBytes() {
            if (!resolved) {
                resolveSQLiteURI();
            }

            return bytes;
        }

        public synchronized String getType() {
            if (!resolved) {
                resolveSQLiteURI();
            }

            return type;
        }

        //==================================
        //  GalleryItem INTERFACE
        //==================================

        @Override
        public synchronized TiffImageMetadata getExif() {
            if (!resolved) {
                resolveSQLiteURI();
            }

            return ExifHelper.getExifMetadata(bytes);
        }

        @Override
        public synchronized String getName() {
            if (!resolved) {
                resolveSQLiteURI();
            }

            return name;
        }

        @Override
        public String getAuthor() {
            return null;
        }

        @Override
        public String getUID() {
            return mapItem != null ? mapItem.getUID() : null;
        }

        @Override
        public String getURI() {
            return uri;
        }

        //==================================
        //  HierarchyListItem2 INTERFACE
        //==================================

        @Override
        public String getTitle() {
            return getName();
        }

        @Override
        public void refreshImpl() {
        }

        @Override
        public Object getUserObject() {
            return uri;
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        public MapItem getMapItem() {
            return mapItem;
        }

        //==================================
        //  Object INTERFACE
        //==================================

        @Override
        public String toString() {
            return uri;
        }

        //==================================
        //
        //  PRIVATE IMPLEMENTATION
        //
        //==================================

        private BlobItem(String uri,
                MapItem item) {
            this.uri = uri;
            this.mapItem = item;
        }

        private void resolveSQLiteURI() {
            resolved = true; // Only try this once.

            Uri u = Uri.parse(uri);

            if (u == null) {
                Log.w(TAG, "Failed to parse URI: " + uri);
                return;
            }

            final String scheme = u.getScheme();

            if (scheme == null || !scheme.equals("sqlite")) {
                Log.w(TAG, "Unsupported scheme: " + uri);
                return;
            }

            final String dbPath = u.getPath();

            if (FileSystemUtils.isEmpty(dbPath)) {
                Log.w(TAG, "Failed to parse path from URI: " + uri);
                return;
            }

            String query = null;
            try {
                final String qp = u.getQueryParameter("query");
                if (qp != null)
                    query = URLDecoder.decode(qp,
                            FileSystemUtils.UTF8_CHARSET.name());
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Failed to parse query: " + uri, e);
                return;
            }

            if (FileSystemUtils.isEmpty(query)) {
                Log.w(TAG, "Failed to parse query: " + query);
                return;
            }

            DatabaseIface database = null;
            CursorIface cursor = null;

            try {
                database = Databases.openDatabase(dbPath,
                        true);
                cursor = database.query(query, null);
                if (cursor.moveToNext()) {
                    name = cursor.getString(cursor.getColumnIndex("name"));
                    bytes = cursor.getBlob(cursor.getColumnIndex("data"));

                    ResourceFile.MIMEType mimeType = ResourceFile
                            .getMIMETypeForMIME(cursor
                                    .getString(cursor.getColumnIndex("type")));

                    if (mimeType == null) {
                        mimeType = ResourceFile.getMIMETypeForFile(name);
                    }
                    type = mimeType != null
                            ? mimeType.MIME
                            : ResourceFile.UNKNOWN_MIME_TYPE;
                }
            } catch (Exception e) {
                Log.e(TAG, "Fqiled to resolve URI " + uri
                        + " : " + e.getMessage());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (database != null) {
                    database.close();
                }
            }
        }

        private final String uri; // SQLite query for type, name, data.
        private final MapItem mapItem;
        private String type;
        private String name;
        private byte[] bytes;
        private boolean resolved;
    }

    private static class ImageThumbnailCreator
            implements ThumbnailCreator<BlobItem> {
        @Override
        public Bitmap createThumbnail(BlobItem blobItem) {
            byte[] bytes = blobItem.getImageBytes();
            TiffImageMetadata exif = ExifHelper.getExifMetadata(bytes);
            int imageOrientation = exif != null
                    ? ExifHelper.getInt(exif,
                            TiffConstants.EXIF_TAG_ORIENTATION,
                            0)
                    : 0;
            BitmapFactory.Options opts = new BitmapFactory.Options();

            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            opts.inJustDecodeBounds = false;
            if (opts.outWidth > THUMB_SIZE && opts.outHeight > THUMB_SIZE) {
                opts.inSampleSize = 1 << (int) (MathUtils.log2(Math.min(
                        opts.outWidth, opts.outHeight)) - MathUtils
                                .log2(THUMB_SIZE));
            }

            return rotateBitmap(
                    ThumbnailUtils.extractThumbnail(
                            BitmapFactory.decodeByteArray(bytes, 0,
                                    bytes.length, opts),
                            THUMB_SIZE, THUMB_SIZE,
                            ThumbnailUtils.OPTIONS_RECYCLE_INPUT),
                    imageOrientation);
        }
    }

    private static class VideoThumbnailCreator
            implements ThumbnailCreator<BlobItem> {
        @Override
        public Bitmap createThumbnail(BlobItem blobItem) {
            Bitmap thumb = null;
            File tmpFile = null;
            FileOutputStream fos = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                tmpFile = IOProviderFactory.createTempFile("vidblob", null,
                        null);
                fos = IOProviderFactory.getOutputStream(tmpFile);
                fos.write(blobItem.getImageBytes());
                fos.close();
                fos = null;

                retriever.setDataSource(tmpFile.getAbsolutePath());
                FileSystemUtils.delete(tmpFile);
                tmpFile = null;

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
                IoUtils.close(fos, TAG, "Failed to close tmp file stream");
                if (tmpFile != null)
                    FileSystemUtils.delete(tmpFile);
            }

            return thumb;
        }
    }

    //==================================
    //  PRIVATE METHODS
    //==================================

    //
    // Cribbed from FileSystemUtils, but takes a String instead of a File and
    // assumes upper case and truncation are required.
    //
    private static String getExtension(String fileName) {
        String extension = "";

        int i = fileName.lastIndexOf('.');

        if (i > 0) {
            extension = fileName.substring(i + 1).toUpperCase(
                    LocaleUtil.getCurrent());

            // FileSystemUtils.MAX_EXT_LENGTH = 6
            if (extension.length() > 6) {
                extension = extension.substring(0, 6);
            }
        }

        return extension;
    }

    private File getVideoFile(final BlobItem blobItem) {
        String ext = getExtension(blobItem.getName()).toLowerCase(
                LocaleUtil.getCurrent());
        File cacheFile = getCacheFile(blobItem, ext);
        FileCache fileCache = getFileCache();
        FileCache.Reservation<File> reservation = fileCache.reserve(cacheFile);

        if (reservation != null) {
            if (!IOProviderFactory.exists(cacheFile)) {

                try (FileOutputStream fos = IOProviderFactory
                        .getOutputStream(cacheFile)) {
                    fos.write(blobItem.getImageBytes());
                } catch (Exception e) {
                    FileSystemUtils.delete(cacheFile);
                    cacheFile = null;
                }
            }
            fileCache.unreserve(reservation);
        }

        return cacheFile;
    }

    @Override
    protected void refreshImpl() {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                showProgressBar();
            }
        });

        final List<BlobItem> filteredItems = new ArrayList<>();
        MultiFilter filter;
        synchronized (filters) {
            filter = new MultiFilter(null,
                    new ArrayList<>(filters.values()));
        }

        List<BlobItem> all;
        synchronized (blobItems) {
            all = new ArrayList<>(blobItems);
        }
        for (BlobItem blob : all) {
            if (filter.accept(blob))
                filteredItems.add(blob);
        }

        getMapView().post(new Runnable() {
            @Override
            public void run() {
                synchronized (viewItems) {
                    if (filteredItems != viewItems) {
                        viewItems.clear();
                        viewItems.addAll(filteredItems);
                        notifyDataSetChanged();
                        hideProgressBar();
                        if (receiver != null)
                            receiver.onRefresh();
                    }
                }
            }
        });
    }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

    private static final String TAG = "ImageGalleryBlobAdapter";

    private static final ThumbnailCreator<BlobItem> imageThumbnailCreator = new ImageThumbnailCreator();
    private static final ThumbnailCreator<BlobItem> videoThumbnailCreator = new VideoThumbnailCreator();

    private final List<BlobItem> blobItems = new ArrayList<>();
    private final List<BlobItem> viewItems = new ArrayList<>();
}
