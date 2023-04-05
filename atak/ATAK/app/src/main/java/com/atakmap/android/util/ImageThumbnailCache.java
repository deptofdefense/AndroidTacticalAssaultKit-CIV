
package com.atakmap.android.util;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.image.ExifHelper;
import com.atakmap.android.image.ImageContainer;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generate and cache thumbnails for general purpose use
 */
public class ImageThumbnailCache {

    private static final String[] EXT_IMAGE = new String[] {
            "jpg", "jpeg", "png", "bmp", "gif", "tif", "tiff", "ntf",
            "nitf", "nsf",
    };

    private static final String[] EXT_VIDEO = new String[] {
            "mp4", "avi", "mpg", "mkv", "ts", "webm", "wmv", "3gp", "mov"
    };

    private static final String TAG = "ImageThumbnailCache";
    private static final long WEEK_MS = 1000 * 60 * 60 * 24 * 7;

    public interface Callback {
        void onGetThumbnail(File file, Bitmap thumb);
    }

    private final ExecutorService _workPool = Executors.newFixedThreadPool(5,
            new NamedThreadFactory(TAG + "-Pool"));

    private final File _cacheDir;
    private final FileCache _fileCache;
    private final Set<File> _inProgress = new HashSet<>();

    private boolean _disposed;

    public ImageThumbnailCache(File cacheDir) {
        _cacheDir = cacheDir;
        _fileCache = new FileCache(cacheDir);
        if (!_cacheDir.exists() && !_cacheDir.mkdirs())
            Log.w(TAG, "Failed to create cache dir: " + _cacheDir);
        cleanCache();
    }

    public void dispose() {
        _disposed = true;
    }

    /**
     * Get or create a thumbnail for a given file
     * Call is asynchronous
     *
     * @param file File (image or video)
     * @param width Desired thumbnail width
     * @param height Desired thumbnail height
     * @param cb Callback for the thumbnail bitmap result
     * @return True if request successful (in progress), false if request failed
     */
    public boolean createThumbnail(final File file, final int width,
            final int height, final Callback cb) {
        if (_disposed || file == null || !file.exists() || width <= 0
                || height <= 0 || cb == null)
            return false;

        // Get the cache file for progress tracking
        final File cacheFile = getCacheFile(file, width, height);
        synchronized (_inProgress) {
            if (_inProgress.contains(cacheFile))
                return false;
            _inProgress.add(cacheFile);
        }

        _workPool.submit(new Runnable() {
            @Override
            public void run() {
                final Bitmap thumb = getThumbnailImpl(Uri.fromFile(file)
                        .toString(), width, height);
                synchronized (_inProgress) {
                    _inProgress.remove(cacheFile);
                }

                cb.onGetThumbnail(file, thumb);
            }
        });
        return true;
    }

    /**
     * Get the thumbnail from the file cache
     * Method is synchronous
     *
     * @param file File (image or video)
     * @param width Desired thumbnail width
     * @param height Desired thumbnail height
     * @return Thumbnail bitmap or null if not cached
     */
    public Bitmap getCachedThumbnail(File file, int width, int height) {
        if (file == null || !file.exists())
            return null;

        // Check that cache file exists
        File cacheFile = getCacheFile(file, width, height);
        if (!cacheFile.exists())
            return null;

        // Check if base image has been modified and needs to be re-cached
        if (file.lastModified() > cacheFile.lastModified())
            return null;

        // Read the cached image
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), opts);
    }

    /**
     * Attempt to get the cached version of a thumbnail. If it doesn't exist
     * return null and generate it.
     * @param file File
     * @param width Width in pixels
     * @param height Height in pixels
     * @param cb Callback when thumbnail is finished generating
     * @return Cached thumbnail or null if it needs to be generated
     */
    public Bitmap getOrCreateThumbnail(File file, int width, int height,
            Callback cb) {
        Bitmap thumb = getCachedThumbnail(file, width, height);
        if (thumb != null)
            return thumb;
        createThumbnail(file, width, height, cb);
        return null;
    }

    /**
     * Clean the thumbnail cache weekly
     */
    private void cleanCache() {
        _workPool.submit(new Runnable() {
            @Override
            public void run() {
                _fileCache.flushStaleCache(WEEK_MS);
            }
        });
    }

    /**
     * Read or generate the thumbnail
     *
     * @param uriString URI string
     * @param width Thumbnail width in pixels
     * @param height Thumbnail height in pixels
     * @return Thumbnail bitmap
     */
    private Bitmap getThumbnailImpl(String uriString, int width, int height) {
        if (_disposed)
            return null;

        if (FileSystemUtils.isEmpty(uriString))
            return null;

        Uri uri = Uri.parse(uriString);
        String scheme;
        if (uri == null || (scheme = uri.getScheme()) == null)
            return null;

        final File cacheFile = getCacheFile(uriString, width, height);
        FileCache.Reservation<File> reservation = _fileCache.reserve(cacheFile);
        if (reservation == null)
            return null;

        File file = null;
        if (scheme.equals("file") && uri.getPath() != null)
            file = new File(uri.getPath());

        Bitmap thumb = null;
        try {
            if (cacheFile.exists()) {
                // Check if the source image has been modified
                // and if so generate a new thumbnail
                long modTime = -1;
                if (file != null && file.exists() && file.isFile())
                    modTime = file.lastModified();
                if (modTime <= cacheFile.lastModified()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = Bitmap.Config.RGB_565;
                    thumb = BitmapFactory.decodeFile(cacheFile
                            .getAbsolutePath(), opts);
                }
                if (thumb == null)
                    FileSystemUtils.delete(cacheFile);
            }
            if (thumb != null)
                return thumb;
            if (file != null)
                thumb = createThumbnail(file, width, height);
            if (thumb == null || thumb.isRecycled())
                return null;
            Log.d(TAG, "Generated thumbnail for " + uriString);
            saveThumbnail(thumb, cacheFile);
            Log.d(TAG, "Saved thumbnail for " + uriString + " to " + cacheFile);
        } catch (Exception e) {
            if (cacheFile.exists())
                FileSystemUtils.delete(cacheFile);
        } finally {
            _fileCache.unreserve(reservation);
        }
        return thumb;
    }

    /**
     * Get the cache file associated with a specific URI
     * The URI, width, and height of the thumbnail are used to generate the
     * file name hash
     *
     * @param uriString URI stirng
     * @param width Width of the thumbnail
     * @param height Height of the thumbnail
     * @return Cache file
     */
    private File getCacheFile(String uriString, int width, int height) {
        return new File(_cacheDir, HashingUtils.sha256sum(
                uriString + ":" + width + ":" + height) + ".png");
    }

    private File getCacheFile(File file, int width, int height) {
        return getCacheFile(Uri.fromFile(file).toString(), width, height);
    }

    /**
     * Save the thumbnail bitmap to a cache file
     *
     * @param thumb Thumbnail bitmap
     * @param cacheFile Cache file
     */
    private static void saveThumbnail(Bitmap thumb, File cacheFile) {
        if (thumb.isRecycled())
            return;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(cacheFile);
            thumb.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException e) {
            Log.w(TAG, "Failed to save: " + cacheFile, e);
            FileSystemUtils.delete(cacheFile);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Failed to save (recycled): " + cacheFile, e);
            FileSystemUtils.delete(cacheFile);
        } finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (Exception ignore) {
            }
        }
    }

    /* Static helper methods */

    private static Bitmap createThumbnail(File f, int width, int height) {
        if (f == null || !f.exists())
            return null;

        final String fname = f.getName();
        for (String ext : EXT_IMAGE)
            if (fname.endsWith("." + ext)) {
                return createImageThumb(f, width, height);
            }
        for (String ext : EXT_VIDEO)
            if (fname.endsWith("." + ext)) {
                return createVideoThumb(f, width, height);
            }

        ResourceFile.MIMEType mt = ResourceFile
                .getMIMETypeForFile(f.getAbsolutePath());
        Bitmap b = ATAKUtilities.getUriBitmap(mt.ICON_URI);
        if (b != null) {
            try {
                return Bitmap.createScaledBitmap(b, 120, 120, false);
            } catch (Exception ignored) {
            }
        }
        return null;

    }

    /**
     * Create an image thumbnail (supports JPEG, PNG, and NITF)
     *
     * @param f Image file
     * @param width Desired thumbnail width
     * @param height Desired thumbnail height
     * @return Image thumbnail
     */
    public static Bitmap createImageThumb(File f, int width, int height) {
        Bitmap bitmap;

        int ori = 0;
        if (ImageContainer.NITF_FilenameFilter.accept(null, f.getName()))
            bitmap = ImageContainer.readNITF(f, width * height);
        else {

            TiffImageMetadata exif = ExifHelper.getExifMetadata(f);
            ori = exif != null ? ExifHelper.getInt(exif,
                    TiffConstants.EXIF_TAG_ORIENTATION, 0) : 0;

            String path = f.getAbsolutePath();
            BitmapFactory.Options opts = new BitmapFactory.Options();

            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            opts.inJustDecodeBounds = false;

            int sWidth = width, sHeight = height;
            int oWidth = opts.outWidth, oHeight = opts.outHeight;

            if (oWidth > sWidth && oHeight > sHeight) {
                opts.inSampleSize = 1 << (int) (MathUtils.log2(
                        Math.min(oWidth, oHeight))
                        - MathUtils.log2(Math.max(sWidth, sHeight)));
            }

            bitmap = BitmapFactory.decodeFile(path, opts);
        }

        return rotateBitmap(extractThumb(bitmap, width, height), ori);
    }

    /**
     * Create a video thumbnail
     *
     * @param f Video file
     * @param width Desired thumbnail width
     * @param height Desired thumbnail height
     * @return Video thumbnail
     */
    public static Bitmap createVideoThumb(File f, int width, int height) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(f.getAbsolutePath());
            return extractThumb(retriever.getFrameAtTime(-1), width, height);
        } catch (Exception e) {
            Log.w(TAG, "Failed to create video thumbnail", e);
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ignore) {
            }
        }
        return null;
    }

    private static Bitmap extractThumb(Bitmap bmp, int width, int height) {
        return ThumbnailUtils.extractThumbnail(bmp, width, height,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
    }

    /**
     * Rotate a bitmap using the EXIF orientation of the image
     *
     * @param bmp Bitmap to rotate
     * @param exifOrientation EXIF orientation
     * @return Rotated bitmap
     */
    private static Bitmap rotateBitmap(Bitmap bmp, int exifOrientation) {
        int orientation = 0;
        switch (exifOrientation) {
            case 3:
                orientation = 180;
                break;

            case 8:
                orientation = 270;
                break;

            case 6:
                orientation = 90;
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
}
