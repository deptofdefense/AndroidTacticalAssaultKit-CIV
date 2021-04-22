
package com.atakmap.android.image;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import androidx.exifinterface.media.ExifInterface;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;

import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ImageAdapter2 extends BaseAdapter {

    // Remember some things for zooming
    final PointF startPoint = new PointF();
    final PointF mid = new PointF();
    float oldDist = 1f;

    // These matrices will be used to move and zoom image
    Matrix matrix = new Matrix();
    final Matrix savedMatrix = new Matrix();

    // We can be in one of these 3 states
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;
    private final ExecutorService _execService = Executors
            .newSingleThreadExecutor(new NamedThreadFactory(
                    "ImageAdapter2Thread"));
    private final LayoutInflater _inflater;
    private final int _width;
    private final int _height;
    private final Context _context;
    private File[] _files;

    final Object lock = new Object();

    private final MapView _view;
    private SensorFOV sensorFOV = null;

    private static final String TAG = "ImageAdapter2";

    /**
     * The context in this case is required to also be an activity.
     */
    public ImageAdapter2(Context context, File[] files, int width, int height,
            MapView view) {
        _context = context;
        _files = files;
        _height = height;
        _width = width;
        _inflater = LayoutInflater.from(context);
        _view = view;
    }

    @Override
    public int getCount() {
        return _files.length;
    }

    public File[] getFiles() {
        return _files;
    }

    @Override
    public Object getItem(int position) {
        if (position < _files.length) {
            return _files[position];
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public Bitmap getImage(int position) {
        File imageFile = _files[position];
        if (imageFile.getName().endsWith(".lnk")) {
            imageFile = _readLink(imageFile);
        }
        return getImage(imageFile, _width);
    }

    public static Bitmap getImage(File imageFile, int width) {
        if (!FileSystemUtils.isFile(imageFile)) {
            Log.w(TAG, "Cannot get image");
            return null;
        }

        // just get bounds first
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (FileInputStream fis = IOProviderFactory
                .getInputStream(imageFile)) {
            BitmapFactory.decodeStream(fis, null, opts);
        } catch (IOException ignored) {
        }

        int sample = Math.max(1, (opts.outWidth / width));
        sample = sample > 1 ? _nextPowerOf2(sample) : sample;
        BitmapFactory.Options opts2 = new BitmapFactory.Options();
        opts2.inSampleSize = sample;
        try (FileInputStream fis = IOProviderFactory
                .getInputStream(imageFile)) {
            return BitmapFactory.decodeStream(fis, null, opts2);
        } catch (IOException e) {
            return null;
        }
    }

    private Bitmap getOrientedImage(File f, BitmapFactory.Options o) {
        ExifInterface exif;
        try {
            exif = new ExifInterface(f.getAbsolutePath());
            String attr = exif
                    .getAttribute(ExifInterface.TAG_ORIENTATION);
            int rot = ExifInterface.ORIENTATION_UNDEFINED;
            if (attr != null)
                rot = Integer.parseInt(attr);

            switch (rot) {
                case ExifInterface.ORIENTATION_UNDEFINED:
                    rot = 0;
                    Log.d(TAG, "ORIENTATION_UNDEFINED");
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                    Log.d(TAG, "ORIENTATION_NORMAL");
                    rot = 0;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    Log.d(TAG, "ORIENTATION_ROTATE_180");
                    rot = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    Log.d(TAG, "ORIENTATION_ROTATE_270");
                    rot = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    Log.d(TAG, "ORIENTATION_ROTATE_90");
                    rot = 90;
                    break;
                default:
                    rot = 0;
                    Log.d(TAG,
                            "ignoring orientation tag, setting rotation to 0");
            }

            Bitmap bmp;
            if (rot == 0) {
                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(f)) {
                    bmp = BitmapFactory.decodeStream(fis, null, o);
                } catch (IOException ignored) {
                    bmp = null;
                }
            } else {
                Matrix matrix = new Matrix();
                matrix.postRotate(rot);

                try (FileInputStream fis = IOProviderFactory
                        .getInputStream(f)) {
                    bmp = BitmapFactory.decodeStream(fis, null, o);
                } catch (IOException ignored) {
                    bmp = null;
                }
                if (bmp != null)
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(),
                            bmp.getHeight(), matrix, false);
            }

            return bmp;
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        }

        return null;

    }

    private static final FilenameFilter _jpegFilenameFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            String fn = filename.toLowerCase(LocaleUtil.getCurrent());
            return fn.endsWith(".jpg") || fn.endsWith(".jpeg");
        }
    };

    public void setData(int position, View v) {

        final File imageFile = _files[position];
        File bmpFile = imageFile;
        if (imageFile.getName().endsWith(".lnk")) {
            bmpFile = _readLink(imageFile);
        }
        if (bmpFile != null
                && _jpegFilenameFilter.accept(bmpFile.getParentFile(),
                        bmpFile.getName())) {
            populateEXIFData(v, bmpFile);

        }
    }

    synchronized public void populateEXIFData(final View layout,
            final File bmpFile) {
        if (sensorFOV == null)
            sensorFOV = new SensorFOV(UUID.randomUUID().toString());

        // remove the sensorFOV from the screen.
        sensorFOV.removeFromGroup();

        try {
            final float[] latLng = new float[2];
            final TextView locText = layout
                    .findViewById(R.id.image_location_text);
            final TextView dateText = layout
                    .findViewById(R.id.image_date_text);

            ExifInterface exif = new ExifInterface(
                    bmpFile.getAbsolutePath());

            final String dateTime = exif
                    .getAttribute(ExifInterface.TAG_DATETIME);
            ((Activity) _context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (dateTime != null) {
                        dateText.setText(dateTime);
                    }
                    layout.invalidate();
                }
            });

            if (exif.getLatLong(latLng)) {
                final double alt;
                final double altmsl = exif.getAltitude(Double.NaN);

                Log.d(TAG, "populate altitude: " + altmsl);
                if (Double.isNaN(altmsl))
                    alt = GeoPoint.UNKNOWN;
                else
                    // the document says MSL, but in testing on a Note 2 it is 
                    // giving me HAE.  I am gonna have to assume the data follows 
                    // the specification
                    alt = EGM96.getHAE(latLng[0], latLng[1],
                            altmsl);

                final GeoPointMetaData gp = GeoPointMetaData.wrap(
                        new GeoPoint(latLng[0], latLng[1], alt),
                        GeoPointMetaData.GPS, GeoPointMetaData.GPS);

                final SharedPreferences _prefs = PreferenceManager
                        .getDefaultSharedPreferences(_context);

                final CoordinateFormat _cFormat = CoordinateFormat
                        .find(_prefs.getString(
                                "coord_display_pref",
                                _context.getString(
                                        R.string.coord_display_pref_default)));
                // the context in this instance is indeed an instance of 
                // an activity.
                ((Activity) _context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        locText.setText(CoordinateFormatUtilities
                                .formatToString(
                                        gp.get(), _cFormat)
                                + "\n" +
                                AltitudeUtilities.format(gp.get(), _prefs));
                        layout.invalidate();
                    }
                });

                try {
                    // android currently does not deal with "other tags"
                    final String angleString = exif
                            .getAttribute("GPSImgDirection");
                    if (angleString != null) {
                        Log.d(TAG, "look angle: " + angleString);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error getting look angle", e);

                }

                try {
                    /**
                     * In order to extract the EXIF data for the direction saved
                     * by the EXIFInterface, we need to use the Sanselan to get 
                     * the GPSImgDirection tag.
                     */
                    IImageMetadata metadata = ExifHelper
                            .getExifMetadata(bmpFile);
                    JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
                    if (jpegMetadata != null) {
                        TiffImageMetadata sanexif = jpegMetadata.getExif();
                        TiffField x = sanexif
                                .findField(
                                        TiffConstants.GPS_TAG_GPS_IMG_DIRECTION);
                        if (x != null) {
                            double direction = x.getDoubleValue();

                            Log.d(TAG,
                                    "direction detected, drawing sensor FOV: "
                                            + direction);

                            TiffField y = sanexif
                                    .findField(
                                            TiffConstants.GPS_TAG_GPS_IMG_DIRECTION_REF);
                            if (y != null) {
                                final String s = y.getStringValue();
                                if (s != null && s.startsWith("M"))
                                    direction = ATAKUtilities
                                            .convertFromMagneticToTrue(gp.get(),
                                                    direction);
                            }

                            _view.getRootGroup().addItem(sensorFOV);
                            sensorFOV.setPoint(gp);
                            sensorFOV.setMetrics((float) direction, 20.0f,
                                    1000.0f);
                        }
                    }
                } catch (Exception e) {
                    // error in this case is acceptable, just does not draw the fov
                    Log.d(TAG, "error occurred finding field: " + e, e);
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "error: ", e);

        }
    }

    synchronized public void dispose() {
        if (sensorFOV != null)
            sensorFOV.removeFromGroup();
    }

    public void setIndex(int position, int numFiles, View v) {
        TextView tv = v.findViewById(R.id.image_order_text);
        tv.setText("" + (position + 1) + " of " + numFiles);
    }

    @Override
    public View getView(int position, View convertView,
            final ViewGroup parent) {

        final View layout = _inflater.inflate(R.layout.image_view, null);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));

        final ImageView imageView = layout
                .findViewById(R.id.image_view_image);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        setZoomListener(imageView);
        final File imageFile = _files[position];
        final ProgressBar progBar = layout
                .findViewById(R.id.image_view_progress);

        TextView orderText = layout
                .findViewById(R.id.image_order_text);
        orderText.setText("" + (position + 1) + R.string.of + getCount());

        try {
            _execService.execute(new Runnable() {
                @Override
                public void run() {
                    File bmpFile = imageFile;
                    if (imageFile.getName().endsWith(".lnk")) {
                        bmpFile = _readLink(imageFile);
                    }

                    if (bmpFile != null) {
                        // just get bounds first

                        synchronized (lock) {
                            BitmapFactory.Options opts = new BitmapFactory.Options();
                            opts.inJustDecodeBounds = true;
                            try (InputStream is = IOProviderFactory
                                    .getInputStream(new File(bmpFile
                                            .getAbsolutePath()))) {
                                BitmapFactory.decodeStream(is,
                                        null, opts);
                            } catch (IOException e) {
                                Log.e(TAG, "error encountered", e);
                            }

                            int sample = Math.max(1, (opts.outWidth / _width));
                            sample = sample > 1 ? _nextPowerOf2(sample)
                                    : sample;
                            BitmapFactory.Options opts2 = new BitmapFactory.Options();
                            opts2.inSampleSize = sample;
                            final Bitmap bmp = getOrientedImage(bmpFile, opts2);
                            // the context in this instance is indeed an instance of 
                            // an activity.
                            ((Activity) _context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progBar.setVisibility(View.GONE);
                                    imageView.setImageBitmap(bmp);
                                    matrix = imageView.getImageMatrix();
                                    savedMatrix.set(matrix);
                                }
                            });

                        }

                        if (_jpegFilenameFilter.accept(bmpFile.getParentFile(),
                                bmpFile.getName())) {
                            populateEXIFData(layout, bmpFile);
                        }
                    }
                }
            });
        } catch (RejectedExecutionException ree) {
            Log.e(TAG,
                    "RejectedExecutionException; ImageAdapter2:_execService.execute(new Runnable() {");
        }

        return layout;
    }

    private void setZoomListener(final ImageView viewer) {
        viewer.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent arg1) {
                switch (arg1.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:

                        savedMatrix.set(matrix);
                        startPoint.set(arg1.getX(), arg1.getY());
                        mode = DRAG;
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                    case MotionEvent.ACTION_UP:
                        break;
                    /* implement pinch zooming* */
                    case MotionEvent.ACTION_POINTER_DOWN:
                        oldDist = spacing(arg1);

                        if (oldDist > 10f) {
                            savedMatrix.set(matrix);
                            midPoint(mid, arg1);
                            mode = ZOOM;
                        }
                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(arg1.getX() - startPoint.x,
                                    arg1.getY() - startPoint.y);
                        } else if (mode == ZOOM) {
                            float newDist = spacing(arg1);
                            if (newDist > 10f) {
                                matrix.set(savedMatrix);
                                float scale = newDist / oldDist;
                                matrix.postScale(scale, scale, mid.x, mid.y);
                            }
                        }
                        break;
                    default:
                        Log.d(TAG,
                                "unhandled action mask:"
                                        + arg1.getActionMasked());
                        break;
                }
                ((Activity) _context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        viewer.setImageMatrix(matrix);
                        viewer.invalidate();
                    }
                });
                return true; // indicate event was handled
            }
        });
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    public void refreshFiles(File[] files) {
        _files = files;
    }

    public void stop() {
        Log.d(TAG, "stop()");
        // TODO ensure this shuts down quickly, not later/after all scheduled
        // tasks complete
        _execService.shutdownNow();
    }

    private static int _nextPowerOf2(int v) {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    private File _readLink(File linkFile) {
        File link = null;
        try (InputStream is = IOProviderFactory.getInputStream(linkFile);
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr)) {
            String line = br.readLine();
            link = new File(
                    FileSystemUtils.sanitizeWithSpacesAndSlashes(line));
        } catch (IOException ex) {
            Log.e(TAG, "error: ", ex);
        }
        return link;
    }
}
