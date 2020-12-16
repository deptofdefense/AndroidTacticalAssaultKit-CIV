
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Pair;

import com.atakmap.android.image.ExifHelper;
import com.atakmap.android.image.quickpic.QuickPicReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.app.R;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Sorts JPEG Files, drops a point on the map, and attaches the image Requires EXIF location to be
 * set
 * 
 * 
 */
public class ImportJPEGSort extends ImportResolver {

    private static final String TAG = "ImportJPEGSort";
    private final Context _context;

    public ImportJPEGSort(Context context, String ext, boolean validateExt,
            boolean copyFile) {
        super(ext, null, validateExt, copyFile);
        _context = context;
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .jpg/.jpeg, now lets see if it contains EXIF location
        return isJpegExif(file);
    }

    private static boolean isJpegExif(final File file) {

        TiffImageMetadata exif = ExifHelper.getExifMetadata(file);
        if (exif != null) {
            try {
                if (exif.getGPS() != null)
                    return true;
            } catch (Exception ignore) {
            }
        }
        Log.d(TAG, "Failed to read valid .jpg exif");
        return false;
    }

    /**
     * Send intent so CoT will be dispatched internally within ATAK Also sort file to proper
     * location
     * 
     * @param file the file to import
     * @return true if the file was imported sucessfully
     */
    @Override
    public boolean beginImport(File file) {
        return beginImport(file, Collections.<SortFlags> emptySet());
    }

    @Override
    public boolean beginImport(File file, Set<SortFlags> flags) {

        // final String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
        // if (dateTime != null) {
        // TODO set creation time somehow?
        // }
        // TODO what other EXIF tags useful?

        GeoPoint point = null;
        if (MapView.getMapView() != null)
            point = ExifHelper.fixImage(MapView.getMapView(),
                    file.getAbsolutePath());
        if (point == null || !point.isValid()) {
            Log.d(TAG, "Failed to read valid .jpg exif");
            return false;
        }

        String uid = UUID.randomUUID().toString();

        // sort file
        File dest = new File(AttachmentManager.getFolderPath(uid),
                file.getName());

        File destParent = dest.getParentFile();
        if (destParent == null) {
            Log.w(TAG,
                    "Destination has no parent file: "
                            + dest.getAbsolutePath());
            return false;
        }

        if (!IOProviderFactory.exists(destParent)) {
            if (!IOProviderFactory.mkdirs(destParent)) {
                Log.w(TAG,
                        "Failed to create directory: "
                                + destParent.getAbsolutePath());
                return false;
            } else {
                Log.d(TAG,
                        "Created directory: " + destParent.getAbsolutePath());
            }
        }

        // Note we attempt to place new file on same SD card, so copying should be minimal
        if (_bCopyFile)
            try {
                FileSystemUtils.copyFile(file, dest);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy file: " + dest.getAbsolutePath(), e);
                return false;
            }
        else {
            if (!FileSystemUtils.renameTo(file, dest)) {
                return false;
            }
        }

        // Create marker
        new PlacePointTool.MarkerCreator(point)
                .setUid(uid)
                .setCallsign(file.getName())
                .setType(QuickPicReceiver.QUICK_PIC_IMAGE_TYPE)
                .showCotDetails(false)
                .placePoint();

        onFileSorted(file, dest, flags);

        return true;
    }

    @Override
    public File getDestinationPath(File file) {
        return file;
    }

    @Override
    public String getDisplayableName() {
        return _context.getString(R.string.jpeg_image);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.camera);
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>("JPEG Image", "image/jpeg");
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }
}
