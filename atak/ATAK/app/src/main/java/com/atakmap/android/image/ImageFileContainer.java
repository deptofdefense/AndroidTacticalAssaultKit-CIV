
package com.atakmap.android.image;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * An ImageContainer for file-based images.
 **/
public class ImageFileContainer
        extends ImageContainer {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    /**
     * Creates an ImageContainer for displaying an image represented by a
     * file URI.  Optionally, a set of images may be browsed. 
     * @param context
     * @param view
     * @param imageUID
     * @param imageURI  The file URI for the image to display.
     * @param imageURIs Optional set of file URIs for images that may be
     * navigated among.  The imageURIs array must include imageURI. 
     **/
    public ImageFileContainer(Context context,
            MapView view,
            String imageUID,
            String imageURI, // Non-null.
            String[] imageURIs, // Possibly null.
            String[] titles) // Possibly null.
    {
        super(context, view);
        this.titles = titles;
        if (imageURIs == null) {
            String f = Uri.parse(imageURI).getPath();
            files = new File[] {
                    new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(f))
            };
        } else {
            files = new File[imageURIs.length];
            imageIndex = imageURIs.length;
            for (int i = 0; i < imageURIs.length; ++i) {
                String f = Uri.parse(imageURIs[i]).getPath();
                files[i] = new File(
                        FileSystemUtils.sanitizeWithSpacesAndSlashes(f));

                if (imageURI.equals(imageURIs[i])) {
                    imageIndex = i;
                }
            }
            if (imageIndex == imageURIs.length) {
                throw new IllegalArgumentException(
                        "imageURI not found in imageURIs array");
            }
        }
    }

    public File getCurrentImageFile() {
        if (files == null)
            return null;

        if (imageIndex >= files.length) {
            imageIndex = files.length - 1;
        }

        return files[imageIndex];
    }

    //==================================
    //  ImageContainer INTERFACE
    //==================================

    @Override
    public String getCurrentImageUID() {
        File f = getCurrentImageFile();
        if (f != null)
            return f.getParentFile().getName();
        return null;
    }

    @Override
    public String getCurrentImageURI() {
        File f = getCurrentImageFile();
        if (f != null)
            return Uri.fromFile(f).toString();
        return null;
    }

    @Override
    public boolean setCurrentImageByUID(String imageUID) {
        boolean foundImage = false;

        if (imageUID != null) {
            int index = 0;

            for (File f : files) {
                if (f.getParentFile().getName().equals(imageUID)) {
                    imageIndex = index;
                    foundImage = true;
                    break;
                }
                ++index;
            }
        }

        return foundImage;
    }

    @Override
    public boolean setCurrentImageByURI(String imageURI) {
        boolean foundImage = false;

        if (imageURI != null && imageURI.startsWith("file")) {
            String filePath = Uri.parse(imageURI).getPath();
            int index = 0;

            for (File f : files) {
                if (f.getAbsolutePath().equals(filePath)) {
                    imageIndex = index;
                    foundImage = true;
                    break;
                }
                ++index;
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
        if (imageIndex + 1 < files.length) {
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
    synchronized protected void refreshView() {
        final View layout = getLayout();
        final ImageView imageView = layout
                .findViewById(R.id.image_view_image);
        final TextView imageError = layout
                .findViewById(R.id.image_view_error);
        //imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        final File imageFile = getCurrentImageFile();
        final ProgressBar progBar = layout
                .findViewById(R.id.image_view_progress);

        layout.findViewById(R.id.image_order_text).setVisibility(View.GONE);

        // Navigation controls
        layout.findViewById(R.id.nav_prev)
                .setVisibility(imageIndex > 0 ? View.VISIBLE : View.GONE);
        if (files != null) {
            layout.findViewById(R.id.nav_next)
                    .setVisibility(imageIndex < files.length - 1
                            ? View.VISIBLE
                            : View.GONE);
        } else {
            layout.findViewById(R.id.nav_next).setVisibility(View.GONE);
        }

        // Clear out text views
        final TextView locText = layout
                .findViewById(R.id.image_location_text);
        final TextView dateText = layout
                .findViewById(R.id.image_date_text);
        final TextView titleText = layout
                .findViewById(R.id.image_title_text);
        final RemarksLayout caption = layout
                .findViewById(R.id.image_caption);

        locText.setText("---");
        dateText.setText("---");
        caption.setText("");
        imageView.setImageBitmap(null);
        imageError.setVisibility(View.GONE);
        progBar.setVisibility(View.VISIBLE);

        //now that getCurrentImageFile has been called, so index is correct
        String title = null;
        if (files != null && titles != null && files.length == titles.length
                && imageIndex < titles.length)
            title = titles[imageIndex];
        if (FileSystemUtils.isEmpty(title)) {
            titleText.setText("---");
            titleText.setVisibility(View.GONE);
        } else {
            titleText.setText(title);
            titleText.setVisibility(View.VISIBLE);
        }

        final Context context = getContext();

        try {
            getExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    File bmpFile = imageFile;

                    if (imageFile.getName().endsWith(".lnk")) {
                        bmpFile = readLink(imageFile);
                    }

                    if (bmpFile != null) {
                        Bitmap imageBitmap = readNITF(bmpFile);

                        if (imageBitmap == null) {
                            BitmapFactory.Options opts = new BitmapFactory.Options();

                            opts.inJustDecodeBounds = true;
                            try (FileInputStream fis = IOProviderFactory
                                    .getInputStream(bmpFile)) {
                                BitmapFactory.decodeStream(fis, null, opts);
                            } catch (IOException ignored) {
                            }

                            int sample = Math.max(1,
                                    Math.round(opts.outWidth / 2048f));
                            Log.d(TAG, "opening image: " +
                                    opts.outWidth + "x" + opts.outHeight
                                    + " downsample: " + sample);

                            sample = sample > 1
                                    ? nextPowerOf2(sample)
                                    : sample;
                            opts.inJustDecodeBounds = false;
                            opts.inSampleSize = sample;
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                            imageBitmap = getOrientedImage(bmpFile, opts);
                        }

                        // User has already navigated to another image
                        File f = getCurrentImageFile();
                        if (f == null || !f.equals(imageFile))
                            return;

                        final Bitmap bmp = imageBitmap;
                        // the context in this instance is indeed an instance of
                        // an activity.
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progBar.setVisibility(View.GONE);
                                imageView.setImageBitmap(bmp);
                                imageError.setVisibility(
                                        bmp == null ? View.VISIBLE : View.GONE);
                                setTouchListener(imageView);
                            }
                        });

                        removeSensorFOV();
                        final File fBmpFile = bmpFile;
                        getMapView().post(new Runnable() {
                            @Override
                            public void run() {
                                populateEXIFData(layout, fBmpFile);
                            }
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "error occurred loading image", e);
        }
    }

    private Bitmap getOrientedImage(File f,
            BitmapFactory.Options o) {
        try (FileInputStream fis = IOProviderFactory.getInputStream(f)) {
            return getOrientedImage(
                    BitmapFactory.decodeStream(fis, null, o),
                    ExifHelper.getExifMetadata(f));
        } catch (IOException e) {
            return null;
        }
    }

    private static File readLink(File linkFile) {
        File link = null;

        try (InputStream is = IOProviderFactory.getInputStream(linkFile);
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr)) {
            String line = br.readLine();
            if (line != null)
                link = new File(
                        FileSystemUtils.sanitizeWithSpacesAndSlashes(line));
        } catch (IOException ex) {
            Log.e(TAG, "error: ", ex);
        }

        return link;
    }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

    private static final String TAG = "ImageFileContainer";

    private final File[] files;
    private int imageIndex;
    private final String[] titles;
}
