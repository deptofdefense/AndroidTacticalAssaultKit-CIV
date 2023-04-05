
package com.atakmap.android.image;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.atakmap.map.CameraController;
import com.atakmap.util.zip.IoUtils;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.util.FileCache;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.app.R;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

public abstract class ImageGalleryBaseAdapter
        extends BaseAdapter {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    /**
     * Customizes image gallery view elements (e.g., visibility, click handlers)
     * as needed by the adapter.  (The gallery view's icon and title will have
     * already been set.)
     * @param gallery  The image gallery view.
     **/
    public abstract void customizeGalleryView(View gallery);

    /**
     * Customizes image gallery toolbar view elements (e.g., visibility, click
     * handlers) as needed by the adapter.  (The toolbar's image details and
     * full screen buttons will have already had their click handlers set.)
     * @param toolbar   The image gallery's toolbar view.
     **/
    public abstract void customizeToolbarView(View toolbar);

    public void dispose() {
        this.notifyDataSetInvalidated();
        load.shutdown();
        save.shutdown();
        refreshThread.dispose();
        disposed = true;
    }

    /**
     * @return  The adapter's OnStateListener for the image gallery DropDown,
     * or null if the adapter doesn't need to track modifications to the state
     * of the DropDown.  (The appearance/disappearance of the image gallery
     * toolbar is handled by the caller after invoking this listener.) 
     **/
    public abstract DropDown.OnStateListener getDropDownStateListener();

    /**
     * @return  The adapter's OnItemClickListener to handle clicks on items in
     * the image gallery.
     **/
    public AdapterView.OnItemClickListener getGridViewClickListener() {
        return gridViewClickListener;
    }

    /**
     * Rotates the supplied bitmap to the orientation specified by the supplied
     * Exif orientation tag value.  If the requested orientation is anything
     * other than one of the ExifInterface.ORIENTATION_ROTATE_* values, the
     * original bitmap is returned.  If a rotated bitmap is created, the
     * supplied bitmap is recycled.
     *
     * @param bmp               Bitmap to be rotated (and recycled).
     * @param orientation       Exif orientation tag value as defined by 
     *                          android.media.ExifInterface
     * @return  The supplied bitmap if no rotation is requested (or the supplied
     *          bitmap is null); otherwise, a new bitmap rotated to the supplied
     *          orientation. 
     **/
    public static Bitmap rotateBitmap(Bitmap bmp,
            int orientation) {
        //
        // Ignore mirroring & transposition.
        //
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_180:
                orientation = 180;
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                orientation = 270;
                break;

            case ExifInterface.ORIENTATION_ROTATE_90:
                orientation = 90;
                break;

            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED:
            default:
                orientation = 0;
                break;
        }

        if (bmp != null && orientation != 0) {
            Matrix matrix = new Matrix();

            matrix.postRotate(orientation);

            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0,
                    bmp.getWidth(), bmp.getHeight(),
                    matrix, false);

            bmp.recycle();
            bmp = rotated;
        }

        return bmp;
    }

    public void setDisplayDetails(boolean showDetails) {
        if (this.showDetails != showDetails) {
            this.showDetails = showDetails;
            notifyDataSetChanged();
        }
    }

    /**
     * Add filter to the list of filters to be run when refreshing
     * Only one filter per class is allowed
     * @param filter Filter object
     */
    public void addFilter(HierarchyListFilter filter) {
        if (filter == null)
            return;
        synchronized (filters) {
            filters.put(filter.getClass(), filter);
        }
    }

    /**
     * Remove a filter from the list
     * @param filter Filter object
     */
    public void removeFilter(HierarchyListFilter filter) {
        if (filter == null)
            return;
        synchronized (filters) {
            filters.remove(filter.getClass());
        }
    }

    /**
     * Remove a filter type from the list
     * @param filterClass Filter class
     */
    public void removeFilter(Class<?> filterClass) {
        synchronized (filters) {
            filters.remove(filterClass);
        }
    }

    //==================================
    //
    //  PROTECTED INTERFACE
    //
    //==================================

    //==================================
    //  PROTECTED NESTED TYPES
    //==================================

    public interface ThumbnailCreator<T> {
        Bitmap createThumbnail(T item);

        int THUMB_SIZE = 300;
    }

    protected static class ViewHolder<T> {
        protected ViewHolder(View itemView,
                T item) {
            // TODO: Do not hardcode com.atakmap.app.R resource IDs
            // This makes it difficult for plugins to extend with their own layouts
            this.item = item;
            imageView = itemView.findViewById(R.id.gridImageView);
            playOverlay = itemView
                    .findViewById(R.id.gridImageViewPlay);
            selectLayout = itemView.findViewById(R.id.gridImageSelectLayout);
            selectCheck = itemView
                    .findViewById(R.id.gridImageSelect);
            detailsLayout = itemView
                    .findViewById(R.id.linearlayoutGridImageView);
            iconView = itemView.findViewById(R.id.gridImageUidIcon);
            titleText = itemView
                    .findViewById(R.id.gridImageUidTitle);
            typeText = itemView.findViewById(R.id.gridImageType);
            nameText = itemView.findViewById(R.id.gridImageFilename);
            // set selected for marquee
            // textFilename.setSelected(true);
        }

        protected synchronized T getItem() {
            return item;
        }

        protected synchronized void setBitmap(Bitmap bmp) {
            if (this.imageView != null) {
                if (bmp != null)
                    imageView.setImageBitmap(bmp);
                else
                    imageView.setImageResource(R.drawable.details);
            }
            if (bitmap != null)
                bitmap.recycle();
            bitmap = bmp;
        }

        protected synchronized void setItem(T item) {
            this.item = item;
        }

        protected final ImageView imageView;
        protected final ImageView playOverlay;
        protected final View selectLayout;
        protected final CheckBox selectCheck;
        protected final View detailsLayout;
        protected final ImageView iconView;
        protected final TextView titleText;
        protected final TextView typeText;
        protected final TextView nameText;

        private T item;
        private Bitmap bitmap;
    }

    //==================================
    //  PROTECTED METHODS
    //==================================

    protected ImageGalleryBaseAdapter(MapView mapView,
            View progressBar,
            boolean showDetails) {
        this.mapView = mapView;
        this.progressBar = progressBar;
        this.showDetails = showDetails;
    }

    /**
     * Display the supplied item, which was clicked in the gallery and has been
     * determined not to represent an image or video.  Called by the default
     * implementation of the AdapterView.OnItemClickListener returned by
     * getGridViewClickListener.
     *
     * @param item      The GalleryItem to be displayed as a file. 
     **/
    protected abstract void displayFile(GalleryItem item);

    /**
     * Display the supplied item, which was clicked in the gallery and has been
     * determined to represent an image.  Called by the default implementation
     * of the AdapterView.OnItemClickListener returned by
     * getGridViewClickListener.
     *
     * @param item      The GalleryItem to be displayed as an image. 
     **/
    protected void displayImage(GalleryItem item) {
        Log.v(TAG, "Viewing image: " + item.getName());

        Intent intent = new Intent(ImageDropDownReceiver.IMAGE_DISPLAY)
                .putExtra("imageURI", item.getURI())
                .putExtra("imageURIs", getImageURIs())
                .putExtra("titles", getTitles())
                .putExtra("uid", item.getUID());
        if (!FileSystemUtils.isEmpty(item.getAuthor())) {
            //title to correspond with imageURI
            intent.putExtra("title", getTitle(item));
        }

        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    /**
     * Display the supplied item, which was clicked in the gallery and has been
     * determined to represent a video.  Called by the default implementation of
     * the AdapterView.OnItemClickListener returned by getGridViewClickListener.
     *
     * @param item      The GalleryItem to be displayed as a video. 
     **/
    protected abstract void displayVideo(GalleryItem item);

    public void setReceiver(ImageGalleryReceiver receiver) {
        this.receiver = receiver;
    }

    /**
     * Refresh this adapter after making changes to its contents
     */
    public void refresh() {
        refreshThread.exec();
    }

    /**
     * Refresh called on non-UI thread
     */
    protected abstract void refreshImpl();

    /**
     * Returns a File representing the possible cache location of the supplied
     * item with the supplied filename extension.
     *
     * @param item      The GalleryItem to be found/placed in the cache.
     * @param destExt   The filename extension for the cached file.
     * @return          A File referring to a location in the cache for the
     *                  supplied item.
     **/
    protected static <ItemT extends GalleryItem> File getCacheFile(
            final ItemT item,
            final String destExt) {
        return getCacheFile(item, cacheDir, destExt);
    }

    protected boolean getDisplayDetails() {
        return showDetails;
    }

    protected FileCache getFileCache() {
        return imageCache;
    }

    /**
     * Returns an array of the URIs of GalleryItems that are of an image MIME
     * type.
     *
     * @return  An array of URIs of the GalleryItems that are images.
     **/
    protected abstract String[] getImageURIs();

    /**
     * Returns an array of titles, Either null, or 1-1 with getImageURIs()
     * @return
     */
    protected String[] getTitles() {
        return null;
    }

    protected String getTitle(GalleryItem item) {
        if (item == null || FileSystemUtils.isEmpty(item.getAuthor()))
            return null;

        return mapView.getContext().getString(R.string.created_by,
                item.getAuthor());
    }

    protected MapView getMapView() {
        return mapView;
    }

    protected Executor getLoadExecutor() {
        return load;
    }

    protected Executor getSaveExecutor() {
        return save;
    }

    protected void hideProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    protected <ItemT extends GalleryItem> void setItemThumbnail(
            final ViewHolder<ItemT> holder,
            final ThumbnailCreator<ItemT> thumbCreator) {
        if (disposed)
            return;
        getLoadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                //
                // Grab the ViewHolder's item, so we can detect a repurposed
                // view/ViewHolder in the UI thread's runnable.
                //
                final ItemT item = holder.getItem();
                final Bitmap thumb = getItemThumbnail(item, thumbCreator);

                if (thumb != null) {
                    mapView.post(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (holder) {
                                if (holder.getItem() == item)
                                    holder.setBitmap(thumb);
                                else
                                    //
                                    // The holder has been reused and we've lost
                                    // the race.  This thumbnail is not needed.
                                    //
                                    thumb.recycle();
                            }
                        }
                    });
                } else {
                    Log.w(TAG, "Failed to create thumbnail for: "
                            + item.getName());

                    mapView.post(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (holder) {
                                if (holder.getItem() == item)
                                    holder.setBitmap(null);
                            }
                        }
                    });
                }
            }
        });
    }

    protected void showProgressBar() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
    }

    protected static void updateLastModified(final File f)
            throws IOException {
        if (f != null
                && IOProviderFactory.exists(f)
                && !f.setLastModified(System.currentTimeMillis())) {
            //
            // Hack to update the lastModified time.
            //
            RandomAccessFile raf = null;

            try {
                raf = IOProviderFactory.getRandomAccessFile(f, "rw");

                long length = raf.length();

                raf.setLength(length + 1);
                raf.setLength(length);
            } finally {
                IoUtils.close(raf, TAG, "failed to close the file");
            }
        }
    }

    //==================================
    //  PRIVATE METHODS
    //==================================

    private static <ItemT extends GalleryItem> File getCacheFile(
            final ItemT item,
            final File destFolder,
            final String destExt) {
        return new File(destFolder,
                HashingUtils.md5sum(item.getURI())
                        + "." + destExt);
    }

    private <ItemT extends GalleryItem> Bitmap getItemThumbnail(
            final ItemT item, final ThumbnailCreator<ItemT> thumbnailCreator) {
        //
        // On first access, flush cached files that haven't been used in a week.
        //
        synchronized (this) {
            if (!cacheCleaned) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        imageCache.flushStaleCache(week);
                    }

                    private static final long week = 1000 * 60 * 60 * 24 * 7;
                }, TAG + "-GetThumbnail").start();
                cacheCleaned = true;
            }
        }

        Bitmap thumb = null;
        final File cacheFile = getCacheFile(item, cacheDir, "png");
        FileCache.Reservation<File> reservation = imageCache.reserve(cacheFile);

        if (reservation != null) {
            try {
                if (IOProviderFactory.exists(cacheFile)) {
                    // Check if the source image has been modified
                    // and if so generate a new thumbnail
                    long modTime = -1;
                    String uriStr = item.getURI();
                    if (!FileSystemUtils.isEmpty(uriStr)) {
                        Uri uri = Uri.parse(uriStr);
                        if (uri != null && FileSystemUtils.isEquals(
                                uri.getScheme(), "file")) {
                            File f = new File(uri.getPath());
                            if (IOProviderFactory.exists(f)
                                    && IOProviderFactory.isFile(f))
                                modTime = IOProviderFactory.lastModified(f);
                        }
                    }
                    if (modTime <= IOProviderFactory.lastModified(cacheFile)) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        try (FileInputStream fis = IOProviderFactory
                                .getInputStream(cacheFile)) {
                            thumb = BitmapFactory
                                    .decodeStream(fis, null, opts);
                        } catch (IOException ignored) {
                            // `thumb` remains `null`
                        }
                    }
                    if (thumb == null)
                        FileSystemUtils.delete(cacheFile);
                }

                if (thumb == null) {
                    thumb = thumbnailCreator.createThumbnail(item);
                    if (thumb != null && !thumb.isRecycled()) {
                        // Use a copy that isn't returned by this method to avoid
                        // recycling while thumbnail is being saved (ATAK-7612)
                        final Bitmap thumbToCache = Bitmap.createBitmap(thumb);

                        // Cache the thumb, transferring the reservation to the
                        // command given to the executor.
                        final FileCache.Reservation<File> cacheReservation = reservation;

                        if (!disposed)
                            getSaveExecutor().execute(new Runnable() {
                                @Override
                                public void run() {
                                    saveThumbnail(thumbToCache,
                                            item.getExif(),
                                            cacheFile,
                                            cacheReservation);
                                    if (!thumbToCache.isRecycled())
                                        thumbToCache.recycle();
                                }
                            });
                        reservation = null; // Successful reservation transfer.
                    }
                }
            } catch (Exception e) {
                if (IOProviderFactory.exists(cacheFile))
                    FileSystemUtils.delete(cacheFile);
            } finally {
                if (reservation != null)
                    imageCache.unreserve(reservation);
            }
        }

        return thumb;
    }

    private static void saveThumbnail(Bitmap thumb, TiffImageMetadata exif,
            File cacheFile, FileCache.Reservation<File> cacheReservation) {
        if (thumb.isRecycled())
            return;
        FileOutputStream fos = null;
        try {
            fos = IOProviderFactory.getOutputStream(cacheFile);
            thumb.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            fos = null;
            if (exif != null) {
                PrintWriter printWriter = null;
                try {
                    // Note: Image caption is stored in "ImageDescription" exif
                    // tag
                    // Referenced by TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION
                    final String imageCaption = ExifHelper.getString(exif,
                            TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION,
                            null);

                    if (!FileSystemUtils.isEmpty(imageCaption)) {
                        String exifCache = cacheFile.getAbsolutePath().replace(
                                ".png",
                                ".exif");
                        printWriter = new PrintWriter(IOProviderFactory
                                .getFileWriter(new File(exifCache)));
                        printWriter.println(imageCaption);
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed to cache Exif for: " + cacheFile, e);
                } finally {
                    IoUtils.close(printWriter, TAG);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to save: " + cacheFile, e);
            FileSystemUtils.delete(cacheFile);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Failed to save (recycled): " + cacheFile, e);
            FileSystemUtils.delete(cacheFile);
        } finally {
            IoUtils.close(fos);
            imageCache.unreserve(cacheReservation);
        }
    }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

    private static final String TAG = "ImageGalleryBaseAdapter";

    private static final double LOG_2 = Math.log(2);
    private static final File cacheDir = FileSystemUtils
            .getItem("attachments/.cache/");
    private static final FileCache imageCache = new FileCache(cacheDir);

    private final AdapterView.OnItemClickListener gridViewClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView,
                View view,
                int position,
                long id) {
            GalleryItem item = (GalleryItem) getItem(position);

            if (item != null) {
                String itemName = item.getName();
                String itemUID = item.getUID();

                if (itemUID != null) {
                    MapItem mi = mapView.getRootGroup().deepFindUID(itemUID);
                    if (mi instanceof PointMapItem) {
                        // Pan to map item synchronously before displaying content

                        CameraController.Programmatic.panTo(
                                mapView.getRenderer3(),
                                ((PointMapItem) mi).getPoint(), true);
                    }
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(MapMenuReceiver.HIDE_MENU));
                }

                if (ImageDropDownReceiver.ImageFileFilter.accept(null,
                        itemName))
                    displayImage(item);
                else if (ImageDropDownReceiver.VideoFileFilter.accept(null,
                        itemName))
                    displayVideo(item);
                else
                    displayFile(item);
            }
        }
    };

    private final ExecutorService load = Executors.newFixedThreadPool(5,
            new NamedThreadFactory("CacheLoadPool"));
    private final ExecutorService save = Executors.newFixedThreadPool(5,
            new NamedThreadFactory("CacheSavePool"));

    private final MapView mapView;
    private final View progressBar;

    private boolean cacheCleaned;
    private boolean showDetails;
    private boolean disposed = false;

    protected ImageGalleryReceiver receiver;
    protected final Map<Class<?>, HierarchyListFilter> filters = new HashMap<>();

    protected final LimitingThread refreshThread = new LimitingThread(
            "RefreshGallery", new Runnable() {
                @Override
                public void run() {
                    refreshImpl();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {
                    }
                }
            });
}
