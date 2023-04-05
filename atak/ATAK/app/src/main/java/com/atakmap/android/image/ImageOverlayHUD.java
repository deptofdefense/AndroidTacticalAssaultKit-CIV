
package com.atakmap.android.image;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;
import android.media.ExifInterface;
import android.util.Log;

import com.atakmap.android.imagecapture.TextRect;
import com.atakmap.android.imagecapture.TiledCanvas;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.NorthReference;

import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;

/**
 * Handles application of an image overlay HUD to images (aka markup)
 */
class ImageOverlayHUD {

    private static final String TAG = "ImageOverlayHUD";

    private static final String KEY_MARKUP = "Markup";
    private static final String KEY_MARKUP_DESC = "MarkupDesc";
    private static final String KEY_MARKUP_VERSION = "MarkupVersion";
    private static final int MARKUP_VERSION = 2;

    private static final int DEG_90 = 90;
    private static final int DEG_360 = 360;
    static final int TILE_SIZE = 1024;

    private final MapView _mapView;
    private final Context _context;
    private final File _inFile;
    private TiffImageMetadata _exif;
    private ProgressCallback _callback;

    private CoordinateFormat _coordSys = CoordinateFormat.DD;
    private char _north;
    private NorthReference _northRef;
    private double _azimuth;
    private double _inclination = 0d;
    private double _gpsAccuracy = Double.NaN;
    private String _gpsProvider;
    private String _altRef;
    private Span _altUnit;
    private float _pitch, _roll, _hfov, _vfov;
    private boolean _pitchPercent, _showGSR;
    private GeoPoint _loc;
    private int _rot;
    private float _dp = 1.0f;
    private ProgressDialog _pd;
    private boolean _pdCanceled;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public ImageOverlayHUD(MapView mapView, File inFile) {
        _mapView = mapView;
        _context = mapView.getContext();
        _inFile = inFile;

        // Defaults based on preferences
        UnitPreferences prefs = new UnitPreferences(mapView);
        setCoordinateSystem(prefs.getCoordinateFormat());
        setAltitudeReference(prefs.getAltitudeReference());
        setAltitudeUnit(prefs.getAltitudeUnits());
        setNorthReference(prefs.getNorthReference());
    }

    /**
     * Set the coordinate system display
     * @param coordSys Coordinate format
     */
    public void setCoordinateSystem(CoordinateFormat coordSys) {
        _coordSys = coordSys;
    }

    /**
     * Set the altitude reference
     * @param altRef Altitude reference (i.e "MSL")
     */
    public void setAltitudeReference(String altRef) {
        if (altRef != null)
            _altRef = altRef;
    }

    /**
     * Set the altitude unit
     * @param units Units
     */
    public void setAltitudeUnit(Span units) {
        _altUnit = units;
    }

    /**
     * Set the north reference
     * @param north North reference
     */
    public void setNorthReference(NorthReference north) {
        _northRef = north;
    }

    /**
     * Set whether the pitch value is displayed as a percentage or not
     * @param pp Pitch percentage
     */
    public void setPitchPercent(boolean pp) {
        _pitchPercent = pp;
    }

    /**
     * Set whether to show the GSR or not
     * @param gsr
     */
    public void setShowGSR(boolean gsr) {
        _showGSR = gsr;
    }

    /**
     * Show the progress dialog
     * Should be called on the UI thread right before applying markup on a
     * separate thread
     */
    public void showProgressDialog() {
        if (_pd != null)
            return;
        _pd = new ProgressDialog(_context);
        _pd.setMessage(_context.getString(R.string.generating_image_overlay_hud,
                _inFile.getName(), 0));
        _pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        _pd.setCanceledOnTouchOutside(false);
        _pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                _pdCanceled = true;
            }
        });
        _pd.setButton(DialogInterface.BUTTON_NEGATIVE, _context.getString(
                R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int w) {
                        _pdCanceled = true;
                    }
                });
        _pd.show();
    }

    /**
     * Apply image markup to input file (without reading entire image into memory)
     * @param outFile Output image containing markup
     * @param cb Callback to fire when markup is finished
     * @return True if successful
     */
    public TiledCanvas applyMarkup(File outFile, ProgressCallback cb) {
        TiledCanvas tc = applyMarkupImpl(outFile, cb);
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_pd != null)
                    _pd.dismiss();
            }
        });
        return tc;
    }

    private TiledCanvas applyMarkupImpl(File outFile, ProgressCallback cb) {
        if (_inFile == null)
            return null;

        // Load tiled bitmap
        TiledCanvas in = new TiledCanvas(_inFile, TILE_SIZE, TILE_SIZE);
        if (!in.valid())
            return null;

        _callback = cb;

        // Create TIFF copy for writing to
        String[] options = {
                "TILED=YES"
        };
        Log.d(TAG, "Creating markup image: " + _inFile);
        File tmp = new File(outFile.getParent(), "." + outFile.getName()
                + "_tmp.tiff");
        in.copyToFile(tmp, "GTiff", options);

        _exif = ExifHelper.getExifMetadata(_inFile);

        boolean marked = ExifHelper.getExtra(_exif, KEY_MARKUP, false);
        if (marked)
            return in;

        _north = ExifHelper.getString(_exif,
                TiffConstants.GPS_TAG_GPS_IMG_DIRECTION_REF, "M").charAt(0);
        _azimuth = ExifHelper.getDouble(_exif,
                TiffConstants.GPS_TAG_GPS_IMG_DIRECTION, Double.NaN);
        _pitch = ExifHelper.getExtraFloat(_exif, "ImgPitch", Float.NaN);
        _roll = ExifHelper.getExtraFloat(_exif, "ImgRoll", Float.NaN);
        _hfov = ExifHelper.getExtraFloat(_exif, "HorizontalFOV", 90.0f);
        _vfov = ExifHelper.getExtraFloat(_exif, "VerticalFOV", 45.0f);
        _inclination = ExifHelper.getExtraFloat(_exif, "Inclination", 0f);
        _gpsAccuracy = ExifHelper.getDouble(_exif, ExifHelper.GPS_DOP,
                Double.NaN);
        _gpsProvider = ExifHelper.getString(_exif, ExifHelper.GPS_SOURCE, null);
        //_gpsProvider = ExifHelper.decodeGPSProvider(_gpsProvider);
        _rot = getRotation(_exif);
        _loc = ExifHelper.getLocation(_exif);

        // Load output tiled bitmap
        TiledCanvas out = new TiledCanvas(tmp, TILE_SIZE, TILE_SIZE);
        try {
            int prog = 1, maxProg = in.getTileCount() + 3;
            if (!out.valid())
                return null;

            if (!setProgress(prog, maxProg))
                return null;

            // Apply markup to each tile
            for (int y = 0; y < in.getTileCountY(); y++) {
                for (int x = 0; x < in.getTileCountX(); x++) {
                    Bitmap tile = in.readTile(x, y);
                    if (tile == null)
                        return null;
                    float tileX = x * TILE_SIZE, tileY = y * TILE_SIZE;
                    float tileRight = Math.min((x + 1) * TILE_SIZE,
                            out.getWidth());
                    float tileBottom = Math.min((y + 1) * TILE_SIZE,
                            out.getHeight());

                    float trX, trY;
                    if (_rot == 0) {
                        trX = -tileX;
                        trY = -tileY;
                    } else if (_rot == 90) {
                        trX = tileBottom - out.getHeight();
                        trY = -tileX;
                    } else if (_rot == 270) {
                        trX = -tileY;
                        trY = tileRight - out.getWidth();
                    } else {
                        trX = tileRight - out.getWidth();
                        trY = tileBottom - out.getHeight();
                    }
                    tile = applyMarkupImpl(tile, trX, trY, out);
                    if (!out.writeTile(tile, x, y)) {
                        Log.e(TAG, "Failed to apply markup to tile "
                                + x + ", " + y);
                        return null;
                    }
                    if (!setProgress(++prog, maxProg))
                        return null;
                }
            }
            // Copy to JPEG
            out.copyToFile(outFile, Bitmap.CompressFormat.JPEG, 100);
            setMarkup(outFile, true);
            // Remove leftover file from GDAL
            FileSystemUtils.delete(new File(outFile + ".aux.xml"));
            if (!setProgress(maxProg - 1, maxProg))
                return null;
        } finally {
            // Delete temporary TIFF output
            FileSystemUtils.delete(tmp);
        }
        return new TiledCanvas(outFile, TILE_SIZE, TILE_SIZE);
    }

    private boolean setProgress(final int p, final int m) {
        final boolean cont = _pdCanceled || _callback == null
                || _callback.onProgress(_inFile, p, m);
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_pd != null) {
                    if (cont) {
                        int pcnt = (int) Math.round(((float) p / m) * 100);
                        _pd.setMax(m);
                        _pd.setProgress(p);
                        _pd.setMessage(_context.getString(
                                R.string.generating_image_overlay_hud,
                                _inFile.getName(), pcnt));
                    } else
                        _pd.dismiss();
                }
            }
        });
        return cont;
    }

    // Apply image markup to input file
    private Bitmap applyMarkupImpl(Bitmap tile, float trX, float trY,
            Canvas can) {

        Resources res = _context.getResources();

        int sori = ((_rot / 90) + 3) % 4;
        boolean portrait = _rot == 90 || _rot == 270;

        // Rotate bitmap according to EXIF orientation
        int width = can.getWidth(), height = can.getHeight();
        if (_rot != 0) {
            // Get rotation matrix
            Matrix matrix = new Matrix();
            matrix.postRotate(_rot);

            // Create new rotated bitmap
            tile = Bitmap.createBitmap(tile, 0, 0, tile.getWidth(),
                    tile.getHeight(), matrix, false);
            if (portrait) {
                width = can.getHeight();
                height = can.getWidth();
            }
        }

        _dp = (float) Math.min(width, height) / 480f;

        can.setBitmap(tile);
        can.translate(trX, trY);

        // GPS location
        String mgrs = CoordinateFormatUtilities.formatToString(_loc, _coordSys);
        String alt = "Unknown Elevation";
        if (_loc.isAltitudeValid()) {
            boolean altitudeM = _altUnit == Span.METER;
            double altValue;
            if (_altRef.equals("HAE"))
                altValue = EGM96.getHAE(_loc);
            else {
                altValue = EGM96.getMSL(_loc);
                _altRef = "MSL";
            }
            // Convert to feet
            if (!altitudeM)
                altValue /= 0.3048;
            alt = String.format(res.getString(R.string.image_overlay_hud_alt),
                    altValue, altitudeM ? "m" : "ft", _altRef);
        }

        // Correct azimuth based on preferences
        if (!Double.isNaN(_azimuth)) {
            double decl = ATAKUtilities.getCurrentMagneticVariation(_loc);
            if (_north == 'M' && _northRef != NorthReference.MAGNETIC) {
                _azimuth += decl;
                _north = 'T';
            } else if (_north == 'T' && _northRef != NorthReference.TRUE) {
                _azimuth -= decl;
                _north = 'M';
            }
        }

        Path path = new Path();

        // Text paint
        float crossWidth = dp(0.5f);
        float tickWidth = dp(2);
        float padding = dp(2);
        float borderWidth = dp(1);
        float titleSize = dp(12);
        float tickSize = dp(10);
        Paint tp = new Paint();
        tp.setAntiAlias(true);

        // Determine how thick the azimuth and pitch bar should be
        tp.setTextSize(titleSize);
        RectF r1 = TextRect.getTextBounds(tp, "(");
        tp.setTextSize(tickSize);
        RectF r2 = TextRect.getTextBounds(tp, "(");
        tp.setTextSize(titleSize);
        float bgThick = ((r1.height() + r2.height()) * (3f / 2f)) + padding * 3;

        // BG paint
        float rightEdge = width - (Float.isNaN(_pitch) ? 0 : bgThick);
        Paint bp = new Paint();
        bp.setStyle(Paint.Style.FILL);

        if (!Float.isNaN(_roll)) {
            // Straight cross
            tp.setStrokeWidth(crossWidth);
            tp.setStyle(Paint.Style.STROKE);
            tp.setColor(Color.argb(255, 64, 128, 64));
            path.moveTo(0, height / 2f);
            path.rLineTo(width, 0);
            path.moveTo(width / 2f, 0);
            path.rLineTo(0, height);
            drawPathStroke(can, path, tp);
            path.reset();

            // Rolled cross
            tp.setColor(Color.argb(255, 128, 255, 128));
            double rad = Math.toRadians(Math.round(_roll)
                    * (sori % 2 == 0 ? -1 : 1));
            float radius = Math.max(width, height);
            float c = (float) Math.cos(rad) * radius, s = (float) Math.sin(rad)
                    * radius;
            float hWidth = width / 2.0f, hHeight = height / 2.0f;
            path.moveTo(hWidth - c, hHeight - s);
            path.lineTo(hWidth + c, hHeight + s);
            path.moveTo(hWidth + s, hHeight - c);
            path.lineTo(hWidth - s, hHeight + c);
            drawPathStroke(can, path, tp);
            path.reset();

            tp.setStyle(Paint.Style.FILL);
            tp.setStrokeWidth(0);
        }

        tp.setColor(0xFFCCFFCC);
        bp.setColor(0xBB222222);
        TextRect locRect = new TextRect(tp, padding * 2, _context.getString(
                R.string.image_overlay_hud_loc_stats, mgrs, alt, _inclination,
                _gpsAccuracy,
                _gpsProvider));
        //locRect.setBaselinePadding(padding);

        tp.setTextAlign(Paint.Align.RIGHT);
        locRect.setPos(new PointF(rightEdge, bgThick),
                TextRect.ALIGN_TOP | TextRect.ALIGN_RIGHT);
        can.drawRect(locRect, bp);
        locRect.draw(can, borderWidth);

        // FOV
        TextRect fovRect = new TextRect(tp, padding * 2,
                _context.getString(R.string.image_overlay_hud_fov, _hfov,
                        _vfov));
        //fovRect.setBaselinePadding(padding);
        tp.setTextAlign(Paint.Align.LEFT);
        fovRect.setPos(new PointF(0, height),
                TextRect.ALIGN_BOTTOM | TextRect.ALIGN_LEFT);
        can.drawRect(fovRect, bp);
        fovRect.draw(can, borderWidth);

        tp.setColor(Color.GREEN);

        if (!Double.isNaN(_azimuth)) {
            can.save();

            /* Bar background */
            can.drawRect(0, 0, width, bgThick, bp);
            can.drawRect(rightEdge, bgThick, width, height, bp);

            /* Azimuth bar */
            DirectionType dirType = DirectionType.getDirection(_azimuth);
            tp.setTextAlign(Paint.Align.CENTER);
            can.clipRect(0, 0, width, bgThick, Region.Op.INTERSECT);
            drawTextStroke(can,
                    res.getString(R.string.image_overlay_hud_azimuth,
                            _north, Math.round(_azimuth),
                            dirType.getAbbreviation()),
                    width / 2f, bgThick - (padding * 2), tp);

            // # of 5 degree spacings within horizontal FOV
            int numTicks = Math.max(1, Math.round(_hfov / 5));
            // Pixel spacing between each tick
            float spacing = (float) width / (float) numTicks;
            // Left-most starting degree (rounded to nearest factor of 5)
            double minDeg = _azimuth - (_hfov / 2);
            int deg = (int) Math.round(minDeg / 5.0f) * 5;
            // Pixel offset used to smooth tick movement
            int offset = -(int) Math.round(((minDeg - deg) / 5f) * spacing);
            if (deg < 0)
                deg += DEG_360;
            // Start with short or long tick (denotes 5 or 10)
            boolean shortTick = deg % 10 == 5;
            // Set text paint params
            tp.setStrokeWidth(0);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextSize(tickSize);
            for (int i = 0; i <= numTicks; i++) {
                int tickX = offset + Math.round(i * spacing);
                path.moveTo(tickX, 0);
                path.rLineTo(0, bgThick / (shortTick ? 5f : 3f));
                // Include values on long ticks
                if (!shortTick) {
                    String tickValue = String.format(
                            res.getString(R.string.image_overlay_hud_deg),
                            (float) (deg % DEG_360));
                    drawTextStroke(can, tickValue, tickX,
                            (bgThick / 3) + tickSize,
                            tp);
                }
                deg += 5;
                shortTick = !shortTick;
            }
            tp.setStrokeWidth(tickWidth);
            tp.setStyle(Paint.Style.STROKE);
            can.drawPath(path, tp);
            path.reset();
            can.restore();
        }

        if (!Float.isNaN(_pitch)) {
            can.save();

            /* Pitch bar */
            can.rotate(DEG_90);
            can.translate(0, -width);
            can.clipRect(bgThick, 0, height, bgThick, Region.Op.INTERSECT);
            tp.setStyle(Paint.Style.FILL);
            tp.setTextSize(titleSize);
            tp.setTextAlign(Paint.Align.CENTER);
            drawTextStroke(can, "Pitch: "
                    + (_pitchPercent ? Math.round(degPercent(_pitch)) + "%"
                            : res.getString(R.string.image_overlay_hud_deg,
                                    _pitch))
                    + (_showGSR ? " GSR: " + getGSR(_pitch) : ""),
                    height / 2f, bgThick - (padding * 2), tp);

            // Draw ticks
            int numTicks = (int) Math
                    .round((_pitchPercent ? degPercent(_vfov) : _vfov) / 5);
            float spacing = (float) height / (float) numTicks;
            double minDeg = _pitch - (_vfov / 2);
            if (_pitchPercent)
                minDeg = degPercent(minDeg);
            int deg = (int) Math.round(minDeg / 5.0f) * 5;
            int offset = (int) -Math.round(((minDeg - deg) / 5f) * spacing);
            boolean shortTick = Math.abs(deg % 10) == 5;
            tp.setTextSize(tickSize);
            for (int i = 0; i <= numTicks; i++) {
                int tickX = offset + Math.round(i * spacing);
                path.moveTo(tickX, 0);
                path.rLineTo(0, bgThick / (shortTick ? 5f : 3f));
                // Include values on long ticks
                if (!shortTick) {
                    String tickValue = _pitchPercent ? String.valueOf(deg)
                            : res.getString(R.string.image_overlay_hud_deg,
                                    (float) (deg % DEG_360));
                    drawTextStroke(can, tickValue, tickX,
                            (bgThick / 3) + tickSize,
                            tp);
                }
                deg += 5;
                shortTick = !shortTick;
            }
            tp.setStrokeWidth(tickWidth);
            tp.setStyle(Paint.Style.STROKE);
            can.drawPath(path, tp);
            can.restore();
        }

        // Setting this to null frees the bitmap resource
        // and resets all canvas transformations
        can.setBitmap(null);

        // Rotate bitmap back to original orientation
        if (_rot != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(-_rot);
            tile = Bitmap.createBitmap(tile, 0, 0, tile.getWidth(),
                    tile.getHeight(), matrix, false);
        }
        return tile;
    }

    /**
     * Set markup metadata to true or false
     * @param outFile Output file
     * @param markup Whether markup has been applied to the image
     */
    public void setMarkup(File outFile, boolean markup) {
        if (_exif == null)
            return;
        HashMap<String, Object> bundle = new HashMap<>();
        ExifHelper.getExtras(_exif, bundle);
        // Update metadata with "Markup" parameter set to true
        TiffOutputSet tos = ExifHelper.getExifOutput(_exif);
        try {
            if (markup) {
                bundle.put(KEY_MARKUP, true);
                bundle.put(KEY_MARKUP_DESC, ExifHelper.getString(_exif,
                        TiffConstants.TIFF_TAG_IMAGE_DESCRIPTION, ""));
                bundle.put(KEY_MARKUP_VERSION, MARKUP_VERSION);
            } else {
                bundle.remove(KEY_MARKUP);
                bundle.remove(KEY_MARKUP_DESC);
                bundle.remove(KEY_MARKUP_VERSION);
            }
            ExifHelper.putExtras(bundle, tos);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set extra parameter \"Markup\" to true.",
                    e);
        }
        ExifHelper.saveExifOutput(tos, outFile);
    }

    private void drawTextStroke(Canvas can, String text, float x,
            float y, Paint textPaint) {
        Paint strokePaint = new Paint(textPaint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStrokeWidth(dp(1));
        can.drawText(text, x, y, strokePaint);
        can.drawText(text, x, y, textPaint);
    }

    private void drawPathStroke(Canvas can, Path path,
            Paint strokePaint) {
        Paint boldPath = new Paint(strokePaint);
        boldPath.setStyle(Paint.Style.STROKE);
        boldPath.setColor(Color.BLACK);
        float pathStroke = strokePaint.getStrokeWidth();
        float outerStroke = Math.min(pathStroke / 2, dp(0.5f));
        boldPath.setStrokeWidth(pathStroke + outerStroke);
        can.drawPath(path, boldPath);
        can.drawPath(path, strokePaint);
    }

    private float dp(float v) {
        return v * _dp;
    }

    private static String getGSR(double deg) {
        if (Double.compare(deg, 0.0f) == 0)
            return "Infinity:1";
        return String.format(Locale.getDefault(), "%d:1",
                Math.round(1.0d / Math.tan(Math.toRadians(deg))));
    }

    private static int getRotation(TiffImageMetadata exif) {
        int sori = ExifHelper.getInt(exif, TiffConstants.TIFF_TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        int rot;
        switch (sori) {
            case ExifInterface.ORIENTATION_ROTATE_90:
            case ExifInterface.ORIENTATION_TRANSPOSE:
                rot = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                rot = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
            case ExifInterface.ORIENTATION_TRANSVERSE:
                rot = 270;
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
            default:
                rot = 0;
                break;
        }
        return rot;
    }

    private static double degPercent(double deg) {
        if (Double.compare(deg, 0.0f) == 0)
            return 0;
        return 100 / (1.0d / Math.tan(Math.toRadians(deg)));
    }

    public interface ProgressCallback {
        // Each time a tile of the image is finished processing
        // Return true to continue, false to quit
        boolean onProgress(File f, int prog, int max);
    }
}
