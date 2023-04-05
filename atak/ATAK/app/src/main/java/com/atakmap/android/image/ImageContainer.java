
package com.atakmap.android.image;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import androidx.exifinterface.media.ExifInterface;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.image.nitf.NITFHelper;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.image.nitf.NITFGraphics;
import com.atakmap.android.image.nitf.NITFImage;
import com.atakmap.android.image.nitf.NITFReader;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.gdal.GdalGraphicUtils;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import org.gdal.gdal.Dataset;

import java.io.File;
import java.io.FilenameFilter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class ImageContainer implements OnTouchListener,
        GestureDetector.OnGestureListener,
        ScaleGestureDetector.OnScaleGestureListener,
        View.OnLayoutChangeListener {
    //==================================
    //
    //  PUBLIC INTERFACE
    //
    //==================================

    //==================================
    //  PUBLIC CONSTANTS
    //==================================

    public static final FilenameFilter JPEG_FilenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir,
                String filename) {
            filename = filename.toLowerCase(LocaleUtil.getCurrent());

            return filename.endsWith(".jpg")
                    || filename.endsWith(".jpeg");
        }
    };

    public static final FilenameFilter NITF_FilenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir,
                String filename) {
            filename = filename.toLowerCase(LocaleUtil.getCurrent());

            return filename.endsWith(".nitf")
                    || filename.endsWith(".ntf")
                    || filename.endsWith(".nsf");
        }
    };

    public static final Comparator<NITFGraphics> NITF_Z_Comparator = new Comparator<NITFGraphics>() {
        @Override
        public int compare(NITFGraphics lhs, NITFGraphics rhs) {
            int zl = lhs.getDisplayLevel(), zr = rhs.getDisplayLevel();
            return Integer.compare(zl, zr);
        }
    };

    //==================================
    //  PUBLIC METHODS
    //==================================

    synchronized public void dispose() {
        removeSensorFOV();
        if (viewer != null)
            viewer.setImageDrawable(null);
        disposed = true;
    }

    public abstract String getCurrentImageUID();

    public abstract String getCurrentImageURI();

    @SuppressLint("InflateParams")
    synchronized public View getView() {
        layout = inflater.inflate(R.layout.image_view, null);
        layout.setLayoutParams(
                new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));

        ImageButton zoomInButton = layout
                .findViewById(R.id.zoomInButton);
        ImageButton zoomOutButton = layout
                .findViewById(R.id.zoomOutButton);
        zoomInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float scale = 1.2f;
                float newScale = mScale * scale;
                if (newScale >= minScale && newScale <= maxScale) {
                    matrix.postScale(scale, scale, scaleDetector.getFocusX(),
                            scaleDetector.getFocusY());
                    mScale = newScale;
                    postMatrix();
                }
            }
        });
        zoomOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float scale = .8f;
                float newScale = mScale * scale;
                if (newScale >= minScale && newScale <= maxScale) {
                    matrix.postScale(scale, scale, scaleDetector.getFocusX(),
                            scaleDetector.getFocusY());
                    mScale = newScale;
                    postMatrix();
                }
            }
        });
        if (PreferenceManager.getDefaultSharedPreferences(
                view.getContext()).getBoolean("dexControls", false)) {
            zoomInButton.setVisibility(View.VISIBLE);
            zoomOutButton.setVisibility(View.VISIBLE);
        }

        disposed = false;
        refreshView();
        return layout;
    }

    public synchronized boolean nextImage() {
        boolean changedImage = !disposed && nextImageImpl();

        if (changedImage) {
            refreshView();
        }

        return changedImage;
    }

    public synchronized boolean prevImage() {
        boolean changedImage = !disposed && prevImageImpl();

        if (changedImage) {
            refreshView();
        }

        return changedImage;
    }

    /**
     * Read in a NITF file w/ overlays and convert to a single bitmap
     * @param nitfFile NITF file
     * @param maxPixels Maximum pixels to read in/output
     * @return Flattened bitmap
     */
    public static Bitmap readNITF(File nitfFile, int maxPixels) {
        Bitmap result = null;

        if (NITF_FilenameFilter.accept(nitfFile.getParentFile(),
                nitfFile.getName())) {
            Dataset ds = GdalLibrary.openDatasetFromFile(nitfFile);

            if (ds == null) {
                Log.e(TAG,
                        "No dataset produced from file " + nitfFile);
                return null;
            }

            NITFImage img = new NITFImage(ds);
            GdalTileReader.ReaderImpl impl = new NITFReader()
                    .GetImageReader(img);

            if (impl == null) {
                Log.e(TAG,
                        "Cannot create a valid image reader implementation. Image type unsupported.");
                ds.delete();
                return null;
            }

            if (maxPixels <= 0) {
                // Use screen size max pixels
                DisplayMetrics metrics = MapView.getMapView()
                        .getResources().getDisplayMetrics();
                maxPixels = metrics.widthPixels * metrics.heightPixels;
            }

            // Convert NITF image to bitmap
            float scale = 1;
            if (img.width * img.height > maxPixels) {
                // Load each tile at reduced resolution

                // Max tile size based on screen size
                int tileSize = (int) MathUtils.log2(maxPixels);
                if ((Math.abs(tileSize) % 2) == 1)
                    tileSize--;
                tileSize = (int) Math.sqrt(1 << tileSize);

                // Reduction scale
                int w = img.width, h = img.height;
                scale = (float) Math.min(tileSize, Math.ceil(
                        Math.sqrt(((double) (w * h)) / maxPixels)));
                result = Bitmap.createBitmap((int) (img.width / scale),
                        (int) (img.height / scale), Bitmap.Config.ARGB_8888);
                Canvas can = new Canvas(result);

                // Read each tile and draw to the scaled down bitmap
                Log.d(TAG, "Reducing NITF resolution by " + (int) scale + "x ("
                        + result.getWidth() + "x" + result.getHeight() + ")");
                for (int x = 0; x < w; x += tileSize) {
                    for (int y = 0; y < h; y += tileSize) {
                        int tw = Math.min(w - x, tileSize), th = Math.min(
                                h - y, tileSize);
                        byte[] tile = img.getTile(impl, x, y, tw, th);
                        Bitmap tileBmp = GdalGraphicUtils.createBitmap(tile,
                                tw, th, img.getInterleave(),
                                img.getFormat());
                        RectF dstRect = new RectF((x / scale), (y / scale),
                                ((x + tw) / scale), ((y + th) / scale));
                        can.drawBitmap(tileBmp, null, dstRect, null);
                    }
                }
            } else {
                // Load image at full resolution
                byte[] imageData = img.getImage(impl);

                // Resource bitmaps are immutable, so we need to convert it to
                // mutable one
                result = GdalGraphicUtils.createBitmap(imageData,
                        img.width,
                        img.height,
                        img.getInterleave(),
                        img.getFormat());

                Bitmap.Config config = result.getConfig();

                result = result.copy(config != null
                        ? config
                        : Bitmap.Config.ARGB_8888,
                        true);
            }

            @SuppressWarnings("unchecked")
            Hashtable<String, String> cgmDict = ds
                    .GetMetadata_Dict("CGM");
            String segmentCount = cgmDict.get("SEGMENT_COUNT");

            if (segmentCount != null) {
                Canvas canvas = new Canvas(result);
                canvas.scale(1 / scale, 1 / scale);
                int segments = Integer.parseInt(segmentCount);
                List<NITFGraphics> nitfCGM = new ArrayList<>();
                float density = Math.min(img.width, img.height) / 360f;
                Paint paint = new Paint();
                paint.setAntiAlias(true);

                for (int i = 0; i < segments; i++) {
                    String relRow = "SEGMENT_" + i + "_SLOC_ROW";
                    String relCol = "SEGMENT_" + i + "_SLOC_COL";
                    String comRow = "SEGMENT_" + i + "_CCS_ROW";
                    String comCol = "SEGMENT_" + i + "_CCS_COL";
                    String dlvl = "SEGMENT_" + i + "_SDLVL";
                    String alvl = "SEGMENT_" + i + "_SALVL";
                    String data = "SEGMENT_" + i + "_DATA";
                    String relRowVal = cgmDict.get(relRow);
                    String relColVal = cgmDict.get(relCol);
                    String comRowVal = cgmDict.get(comRow);
                    String comColVal = cgmDict.get(comCol);
                    String dlvlVal = cgmDict.get(dlvl);
                    String alvlVal = cgmDict.get(alvl);
                    byte[] dataVal = new byte[0];

                    try {
                        String val = cgmDict.get(data);
                        if (val != null)
                            dataVal = val.getBytes("ISO_8859_1");
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "error occurred", e);
                    }

                    if (dataVal != null) {
                        try {
                            int relativeRowVal = Integer.parseInt(relRowVal);
                            int relativeColVal = Integer.parseInt(relColVal);
                            int commonRowVal = Integer.parseInt(comRowVal);
                            int commonColVal = Integer.parseInt(comColVal);
                            int displayLvlVal = Integer.parseInt(dlvlVal);
                            int attachmentLvlVal = Integer.parseInt(alvlVal);

                            nitfCGM.add(new NITFGraphics(relativeRowVal,
                                    relativeColVal,
                                    commonRowVal,
                                    commonColVal,
                                    displayLvlVal,
                                    attachmentLvlVal,
                                    dataVal));
                        } catch (Exception e) {
                            // cowardly way of not producing the graphic if any of the
                            // values are null.
                        }
                    }
                }
                final NITFGraphics[] sorted = nitfCGM
                        .toArray(new NITFGraphics[0]);
                // Draw in order based on display level
                Arrays.sort(sorted, NITF_Z_Comparator);
                for (NITFGraphics g : sorted)
                    g.draw(canvas, paint, density);
            }
            ds.delete();
        }

        return result;
    }

    public static Bitmap readNITF(File nitfFile) {
        return readNITF(nitfFile, 0);
    }

    /**
     * Tries to set the current image to one having the supplied UID.
     * Returns true if the attempt is successful.
     *
     * @param imageUID  UID of image to set as the current image.
     * @return          True if the current image was changed to the one with
     *                  the supplied UID; false otherwise.
     **/
    public abstract boolean setCurrentImageByUID(String imageUID);

    /**
     * Tries to set the current image to one having the supplied URI.
     * Returns true if the attempt is successful.
     *
     * @param imageURI  URI of image to set as the current image.
     * @return          True if the current image was changed to the one with
     *                  the supplied URI; false otherwise.
     **/
    public abstract boolean setCurrentImageByURI(String imageURI);

    public void stop() {
        Log.d(TAG, "stop()");
        // TODO ensure this shuts down quickly, not later/after all scheduled
        // tasks complete
        getExecutor().shutdownNow();
    }

    //==================================
    //
    //  PROTECTED INTERFACE
    //
    //==================================

    /**
     * The context in this case is required to also be an activity.
     */
    protected ImageContainer(Context context,
            MapView view) {
        this.context = context;
        this.view = view;
        this.inflater = LayoutInflater.from(context);
        this.gestureDetector = new GestureDetector(context, this);
        this.scaleDetector = new ScaleGestureDetector(context, this);
    }

    protected Context getContext() {
        return context;
    }

    protected ExecutorService getExecutor() {
        return execService;
    }

    protected LayoutInflater getInflater() {
        return inflater;
    }

    protected View getLayout() {
        return layout;
    }

    protected MapView getMapView() {
        return view;
    }

    protected void runOnUiThread(Runnable r) {
        if (r != null)
            ((Activity) getContext()).runOnUiThread(r);
    }

    protected static Bitmap getOrientedImage(Bitmap bmp,
            TiffImageMetadata exif) {
        int orientation = ExifHelper.getInt(exif,
                TiffConstants.EXIF_TAG_ORIENTATION,
                0);

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

            default:
                orientation = 0;
        }

        if (orientation != 0) {
            try {
                Matrix matrix = new Matrix();
                Bitmap src = bmp;

                // Create new rotated bitmap
                matrix.postRotate(orientation);
                bmp = Bitmap.createBitmap(src,
                        0, 0,
                        bmp.getWidth(),
                        bmp.getHeight(),
                        matrix, false);
                src.recycle();
            } catch (Exception e) {
                Log.e(TAG, "error: ", e);
            }
        }

        return bmp;
    }

    protected abstract boolean nextImageImpl(); // Returns true if image is changed.

    protected static int nextPowerOf2(int v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;

        return ++v;
    }

    protected synchronized void populateEXIFData(
            final View layout, final File bmpFile) {
        // Skip if the view has already been closed
        if (disposed || layout == null)
            return;
        final RemarksLayout caption = layout.findViewById(R.id.image_caption);
        layout.findViewById(R.id.markupImage).setEnabled(false);
        File dir = bmpFile.getParentFile();
        String name = bmpFile.getName().toLowerCase(LocaleUtil.getCurrent());
        if (JPEG_FilenameFilter.accept(dir, name))
            populateEXIFData(layout, ExifHelper.getExifMetadata(bmpFile));
        else if (NITF_FilenameFilter.accept(dir, name))
            populateNITFMetadata(layout,
                    GdalLibrary.openDatasetFromFile(bmpFile));
        else if (name.endsWith(".png")) {
            Dataset ds = GdalLibrary.openDatasetFromFile(bmpFile);
            if (ds != null) {
                String desc = ds.GetMetadataItem("Description");
                ds.delete();
                if (desc == null)
                    desc = "";
                setText(caption, desc);
            }
        } else {
            // Caption not supported for this file type
            setText(caption, null);
        }
    }

    protected synchronized void populateEXIFData(final View layout,
            final TiffImageMetadata exif) {
        // Skip if the view has already been closed
        if (disposed || layout == null)
            return;

        // Create new sensor FOV object
        if (sensorFOV == null)
            sensorFOV = new SensorFOV(UUID.randomUUID().toString());

        try {
            final TextView locText = layout
                    .findViewById(R.id.image_location_text);
            final TextView dateText = layout
                    .findViewById(R.id.image_date_text);
            final RemarksLayout caption = layout
                    .findViewById(R.id.image_caption);
            final ImageButton overlayBtn = layout.findViewById(
                    R.id.markupImage);

            String dateTime = null, imageCaption = "";
            if (exif != null) {
                // Update date time text and image caption, if available.
                dateTime = ExifHelper.getString(exif,
                        TiffConstants.TIFF_TAG_DATE_TIME, "");
                // Note: Image caption is stored in "ImageDescription" exif tag
                // Referenced by TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION
                imageCaption = ExifHelper.getString(exif,
                        TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION, "");
                TiffImageMetadata.GPSInfo gpsInfo = exif.getGPS();
                if (gpsInfo != null)
                    populateLocation(locText, layout, gpsInfo, exif);
            }
            // Disable button if image is missing EXIF data or
            // overlay has already been applied
            overlayBtn.setEnabled(exif != null
                    && !ExifHelper.getExtra(exif, "Markup", false));
            setText(dateText, dateTime);
            setText(caption, imageCaption);
            if (PreferenceManager.getDefaultSharedPreferences(
                    view.getContext()).getBoolean("dexControls", false)) {
                locText.setTextSize(11.5f);
                dateText.setTextSize(11.5f);
            }
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
    }

    protected synchronized void populateNITFMetadata(final View layout,
            Dataset nitf) {
        // Skip if the view has already been closed
        if (disposed || layout == null)
            return;

        // Create new sensor FOV object
        if (sensorFOV == null)
            sensorFOV = new SensorFOV(UUID.randomUUID().toString());

        // Make sure we can read the NITF and its TRE data
        if (nitf == null)
            return;

        try {
            final TextView locText = layout
                    .findViewById(R.id.image_location_text);
            final TextView dateText = layout
                    .findViewById(R.id.image_date_text);
            final RemarksLayout caption = layout
                    .findViewById(R.id.image_caption);

            // Update date time text and image caption, if available
            String dateTime = NITFHelper.getDateTime(nitf);
            String imageCaption = NITFHelper.getTitle(nitf);
            setText(dateText, dateTime == null ? "" : dateTime);
            setText(caption, imageCaption == null ? "" : imageCaption);

            // Location (center of coords)
            GeoPoint gp = NITFHelper.getCenterLocation(nitf);
            if (gp == null)
                return;

            // Altitude (MSL)
            double altMSL = NITFHelper.getExtra(nitf,
                    NITFHelper.GPS_ALTITUDE, Double.NaN);

            double altHAE = EGM96.getHAE(gp.getLatitude(),
                    gp.getLongitude(), altMSL);

            Log.d(TAG, "populate altitude: " + altMSL);

            final GeoPoint imageLocation = new GeoPoint(
                    gp.getLatitude(), gp.getLongitude(),
                    altHAE);

            // Pan to image location
            final View panButton = layout.findViewById(R.id.panButton);

            panButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    CameraController.Programmatic.panTo(
                            getMapView().getRenderer3(),
                            imageLocation, false);
                }
            });

            final String formattedLocation = getFormattedLocation(
                    imageLocation);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    panButton.setVisibility(View.VISIBLE);
                    locText.setText(formattedLocation);
                    synchronized (ImageContainer.this) {
                        layout.invalidate();
                    }
                }
            });

            // Get image direction azimuth (assuming magnetic north)
            double direction = NITFHelper.getExtra(nitf,
                    NITFHelper.GPS_DIRECTION, Double.NaN);

            if (!Double.isNaN(direction)) {
                // Assuming azimuth is always in mag
                direction = ATAKUtilities.convertFromMagneticToTrue(
                        imageLocation, direction);

                // Get horizontal FOV of direction cone
                float horizontalFOV = NITFHelper.getExtra(nitf,
                        NITFHelper.HORIZONTAL_FOV, 20.0f);

                getMapView().getRootGroup().addItem(sensorFOV);
                sensorFOV.setPoint(GeoPointMetaData.wrap(imageLocation));
                sensorFOV.setMetrics((float) direction, horizontalFOV, 1000.0f);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing NITF metadata: ", e);
        } finally {
            nitf.delete();
        }
    }

    protected String getFormattedLocation(GeoPoint loc) {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        String defaultCoordFormat = getContext().getString(
                R.string.coord_display_pref_default);
        CoordinateFormat coordFormat = CoordinateFormat.find(prefs.getString(
                "coord_display_pref",
                defaultCoordFormat));
        return CoordinateFormatUtilities.formatToString(loc, coordFormat)
                + "\n" + AltitudeUtilities.format(loc, prefs);
    }

    protected abstract boolean prevImageImpl(); // Returns true if image is changed.

    protected abstract void refreshView();

    protected void removeSensorFOV() {
        if (sensorFOV != null) {
            // remove the sensorFOV from the screen.
            sensorFOV.removeFromGroup();
        }
    }

    protected void setTouchListener(final ImageView viewer) {
        if (this.viewer != null) {
            if (this.viewer == viewer)
                return;
            this.viewer.setOnTouchListener(null);
            this.viewer.removeOnLayoutChangeListener(this);
        }
        this.viewer = viewer;
        this.viewer.setOnTouchListener(this);
        this.viewer.addOnLayoutChangeListener(this);
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right,
            int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (this.viewer != view)
            return;
        // Center fit image in view
        Drawable dr = this.viewer.getDrawable();
        if (dr != null) {
            float[] v = new float[9];
            RectF src = new RectF(0, 0, dr.getIntrinsicWidth(),
                    dr.getIntrinsicHeight());
            RectF dst = new RectF(0, 0, this.viewer.getWidth(),
                    this.viewer.getHeight());
            this.matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
            this.matrix.getValues(v);
            this.imgScale = this.mScale = v[0];
            this.maxScale = v[0] * 16f;
            this.minScale = v[0] / 2f;
            postMatrix();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean r = scaleDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress())
            r = gestureDetector.onTouchEvent(event);
        return r;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        mode = Mode.DRAG;
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float dX,
            float dY) {
        if (mode == Mode.DRAG) {
            matrix.postTranslate(-dX, -dY);
            postMatrix();
        }
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
        if (this.mScale > this.imgScale || mode == Mode.ZOOM)
            return false;
        double dist = Math.abs(e2.getX() - e1.getX());
        double deg = Math.toDegrees(Math.atan2(e1.getY() - e2.getY(),
                e2.getX() - e1.getX()));
        if (dist > 25 * context.getResources().getDisplayMetrics().density) {
            deg = (deg % 360) + (deg < 0 ? 360 : 0);
            if (deg > 315 || deg < 45)
                // Swipe left
                prevImage();
            else if (deg > 135 && deg < 225)
                // Swipe right
                nextImage();
        }
        return false;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mode == Mode.ZOOM) {
            float scale = detector.getScaleFactor();
            float newScale = mScale * scale;
            if (newScale >= this.minScale && newScale <= this.maxScale) {
                mScale = newScale;
                matrix.postScale(scale, scale, detector.getFocusX(),
                        detector.getFocusY());
                postMatrix();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mode = Mode.ZOOM;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
    }

    @Override
    public void onLongPress(MotionEvent e) {
    }

    private void postMatrix() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (viewer != null) {
                    viewer.setImageMatrix(matrix);
                    viewer.invalidate();
                }
            }
        });
    }

    //==================================
    //
    //  PRIVATE IMPLEMENTATION
    //
    //==================================

    //==================================
    //  PRIVATE NESTED TYPES
    //==================================

    // We can be in one of these 3 states
    private enum Mode {
        NONE,
        DRAG,
        ZOOM
    }

    //==================================
    //  PRIVATE METHODS
    //==================================

    private static void midPoint(PointF point,
            MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    private void populateLocation(final TextView locText,
            final View layout,
            TiffImageMetadata.GPSInfo gpsInfo,
            TiffImageMetadata exif)
            throws ImageReadException {
        // Altitude (MSL)
        // The document says MSL, but testing on a Note 2 showsed HAE. It is
        // assumed that the data follows the specification
        final double altMSL = ExifHelper.getAltitude(exif, Double.NaN);
        final double altHAE = EGM96.getHAE(
                gpsInfo.getLatitudeAsDegreesNorth(),
                gpsInfo.getLongitudeAsDegreesEast(), altMSL);

        Log.d(TAG, "populate altitude (HAE): " + altHAE);

        final GeoPoint imageLocation = new GeoPoint(
                gpsInfo.getLatitudeAsDegreesNorth(),
                gpsInfo.getLongitudeAsDegreesEast(),
                altHAE);

        // Pan to image location
        final View panButton = layout.findViewById(R.id.panButton);

        panButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraController.Programmatic.panTo(getMapView().getRenderer3(),
                        imageLocation,
                        false);
            }
        });

        final String formattedLocation = getFormattedLocation(imageLocation);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                panButton.setVisibility(View.VISIBLE);
                locText.setText(formattedLocation);
                synchronized (ImageContainer.this) {
                    layout.invalidate();
                }
            }
        });

        // Get image direction azimuth (assuming magnetic north)
        // ExifHelper.fixImage should take care of true -> mag conversion
        double direction = ExifHelper.getDouble(exif,
                TiffConstants.GPS_TAG_GPS_IMG_DIRECTION,
                Double.NaN);

        // sensor fov only takes true for the direction
        char north = ExifHelper.getString(exif,
                TiffConstants.GPS_TAG_GPS_IMG_DIRECTION_REF, "M").charAt(0);
        if (north == 'M')
            direction = ATAKUtilities.convertFromMagneticToTrue(imageLocation,
                    direction);

        if (!Double.isNaN(direction)) {
            // Get horizontal FOV if available
            float horizontalFOV = ExifHelper.getExtraFloat(exif,
                    "HorizontalFOV", 20.0f);

            Log.d(TAG, "direction detected, drawing sensor FOV: "
                    + direction + ", " + horizontalFOV);

            getMapView().getRootGroup().addItem(sensorFOV);
            sensorFOV.setPoint(GeoPointMetaData.wrap(imageLocation));
            sensorFOV.setMetrics((float) direction, horizontalFOV, 1000.0f);
        }
    }

    /**
     * Helper method for setting text view content on UI thread
     * @param tv Text view
     * @param txt Text to set (null to hide text view)
     */
    private void setText(final View tv, final String txt) {
        if (tv == null)
            return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (txt != null) {
                    if (tv instanceof RemarksLayout)
                        ((RemarksLayout) tv).setText(txt);
                    else if (tv instanceof EditText)
                        ((EditText) tv).setText(txt);
                    tv.setVisibility(View.VISIBLE);
                } else {
                    tv.setVisibility(View.GONE);
                }
            }
        });
    }

    //==================================
    //  PRIVATE REPRESENTATION
    //==================================

    private static final String TAG = "ImageContainer";

    private final ExecutorService execService = Executors
            .newSingleThreadExecutor(
                    new NamedThreadFactory("ImageContainerThread"));
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleDetector;
    private final Context context;
    private final MapView view;
    private final LayoutInflater inflater;
    private final Matrix matrix = new Matrix();

    private ImageView viewer;
    private float mScale = 1f;
    private float minScale = 1f, maxScale = 1f, imgScale = 1f;
    private Mode mode = Mode.NONE;
    private SensorFOV sensorFOV;
    private boolean disposed;
    private View layout;
}
