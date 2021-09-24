
package com.atakmap.android.image;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;

import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * An ImageContainer for SQLite blob-based images (as used by GeoPackages).
 **/
public class ImageBlobContainer
        extends ImageContainer {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    /**
     * Creates an ImageContainer for displaying an image represented by a URI of
     * the form "sqlite://<database_path>&query=...".  Optionally, a set of
     * images represented by SQLite URIs may be browsed. 
     * @param context
     * @param view
     * @param imageUID
     * @param imageURI  The SQLite URI for the image to display.
     * @param imageURIs Optional set of SQLite URIs for images that may be
     * navigated among.  The imageURIs array must include imageURI. 
     **/
    public ImageBlobContainer(Context context,
            MapView view,
            String imageUID,
            String imageURI, // Non-null.
            String[] imageURIs, // Possibly null.
            String[] titles) // Possibly null.
    {
        super(context, view);
        this.imageUID = imageUID;
        this.titles = titles;
        if (imageURIs == null) {
            this.imageURIs = new String[] {
                    imageURI
            };
        } else {
            this.imageURIs = imageURIs;
            for (String uri : imageURIs) {
                if (imageURI.equals(uri)) {
                    break;
                }
                ++imageIndex;
            }
            if (imageIndex == imageURIs.length) {
                throw new IllegalArgumentException(
                        "imageURI not found in imageURIs array");
            }
        }
        //        imageInfos = new ImageInfo[this.imageURIs.length];
        //        imageInfos[imageIndex] = new ImageInfo (imageURI);
    }

    //==================================
    //  ImageContainer INTERFACE
    //==================================

    @Override
    public String getCurrentImageUID() {
        return imageUID;
    }

    @Override
    public String getCurrentImageURI() {
        return imageURIs[imageIndex];
    }

    @Override
    public boolean setCurrentImageByUID(String imageUID) {
        return imageUID != null && imageUID.equals(this.imageUID);
    }

    @Override
    public boolean setCurrentImageByURI(String imageURI) {
        boolean foundImage = false;

        if (imageURIs[imageIndex].equals(imageURI)) {
            foundImage = true;
        } else if (imageURI != null) {
            int newIndex = 0;

            for (String uri : imageURIs) {
                if (imageURI.equals(uri)) {
                    imageIndex = newIndex;
                    foundImage = true;
                    break;
                }
                ++newIndex;
            }
        }

        return foundImage;
    }

    //==================================
    //
    //  PROTECTED INTERFACE
    //
    //==================================

    //==================================
    //  ImageContainer INTERFACE
    //==================================

    @Override
    protected boolean nextImageImpl() {
        if (imageIndex + 1 < imageURIs.length) {
            ++imageIndex;
            return true;
        }

        return false;
    }

    @Override
    protected boolean prevImageImpl() {
        if (imageIndex > 0) {
            --imageIndex;
            return true;
        }

        return false;
    }

    @Override
    protected void refreshView() {
        final View layout = getLayout();
        final ImageView imageView = layout
                .findViewById(R.id.image_view_image);
        //imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        final ProgressBar progBar = layout
                .findViewById(R.id.image_view_progress);

        layout.findViewById(R.id.image_order_text).setVisibility(View.GONE);

        // Navigation controls
        layout.findViewById(R.id.nav_prev)
                .setVisibility(imageIndex > 0 ? View.VISIBLE : View.GONE);
        layout.findViewById(R.id.nav_next)
                .setVisibility(imageIndex < imageURIs.length - 1
                        ? View.VISIBLE
                        : View.GONE);

        // Clear out text views
        final TextView locText = layout
                .findViewById(R.id.image_location_text);
        final TextView dateText = layout
                .findViewById(R.id.image_date_text);
        final TextView titleText = layout
                .findViewById(R.id.image_title_text);
        final RemarksLayout caption = layout
                .findViewById(R.id.image_caption);

        // Markup not support for non-file images
        layout.findViewById(R.id.markupImage).setVisibility(View.GONE);

        locText.setText("---");
        dateText.setText("---");
        caption.setText("");

        final Context context = getContext();

        try {
            getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    Drawable d = imageView.getDrawable();

                    if (d instanceof BitmapDrawable) {
                        final Bitmap bm = ((BitmapDrawable) d).getBitmap();
                        if (bm != null)
                            bm.recycle();
                    }

                    final ImageInfo imageInfo = getCurrentImageInfo();

                    ((Activity) context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progBar.setVisibility(View.GONE);
                            imageView.setImageBitmap(imageInfo.bitmap);
                            setTouchListener(imageView);
                            imageInfo.bitmap = null;

                            //now that getCurrentImageInfo has been called so index is correct
                            String title = null;
                            if (imageURIs != null && titles != null
                                    && imageURIs.length == titles.length
                                    && imageIndex < titles.length)
                                title = titles[imageIndex];
                            if (FileSystemUtils.isEmpty(title)) {
                                titleText.setText("---");
                                titleText.setVisibility(View.GONE);
                            } else {
                                titleText.setText(title);
                                titleText.setVisibility(View.VISIBLE);
                            }
                        }
                    });

                    removeSensorFOV();
                    populateEXIFData(layout, imageInfo.exif);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "error occurred loading image", e);
        }
    }

    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================

    //==================================
    //  PRIVATE NESTED TYPES
    //==================================

    private static class ImageInfo {
        public ImageInfo(String imageURI) {
            byte[] imageBytes = resolveSQLiteURI(imageURI);

            exif = ExifHelper.getExifMetadata(imageBytes);

            BitmapFactory.Options opts = new BitmapFactory.Options();

            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(imageBytes,
                    0, imageBytes.length,
                    opts);

            int sample = Math.max(1, (opts.outWidth / 2048));

            sample = sample > 1
                    ? nextPowerOf2(sample)
                    : sample;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            bitmap = getOrientedImage(BitmapFactory.decodeByteArray(imageBytes,
                    0, imageBytes.length,
                    opts),
                    exif);
        }

        private static byte[] resolveSQLiteURI(String imageURI) {
            Uri u = Uri.parse(imageURI);
            final String dbPath = u.getPath();

            if (FileSystemUtils.isEmpty(dbPath)) {
                throw new IllegalArgumentException("Failed to parse URI path: "
                        + imageURI);
            }

            String query = null;

            try {
                final String q = u.getQueryParameter("query");
                if (q != null) {
                    query = URLDecoder.decode(q,
                            FileSystemUtils.UTF8_CHARSET.name());
                }
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException(
                        "Failed to parse URI query: "
                                + imageURI,
                        e);
            }

            if (FileSystemUtils.isEmpty(query)) {
                throw new IllegalArgumentException(
                        "Failed to parse URI query: "
                                + query);
            }

            byte[] imageBytes = null;
            DatabaseIface database = null;
            CursorIface cursor = null;

            try {
                database = Databases.openDatabase(dbPath,
                        true);
                cursor = database.query(query, null);
                if (cursor.moveToNext()) {
                    imageBytes = cursor.getBlob(cursor.getColumnIndex("data"));
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to resolve URI: "
                        + imageURI,
                        e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (database != null) {
                    database.close();
                }
            }

            if (imageBytes == null) {
                throw new IllegalArgumentException(
                        "Failed to resolve data from URI: " + imageURI);
            }

            return imageBytes;
        }

        Bitmap bitmap;
        TiffImageMetadata exif;
    }

    //==================================
    //  PRIVATE METHODS
    //==================================

    private ImageInfo getCurrentImageInfo() {
        //        ImageInfo result = null;
        //
        //        synchronized (imageInfos)
        //          {
        //            result = imageInfos[imageIndex];
        //            if (result == null)
        //              {
        //                result = imageInfos[imageIndex]
        //                       = new ImageInfo (imageURIs[imageIndex]);
        //              }
        //          }

        return new ImageInfo(imageURIs[imageIndex]);
    }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

    private static final String TAG = "ImageBlobContainer";

    private final String imageUID;
    private final String[] titles;
    private final String[] imageURIs;
    //    private final ImageInfo[] imageInfos;

    private int imageIndex;
}
