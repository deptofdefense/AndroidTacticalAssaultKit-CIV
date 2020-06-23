
package com.atakmap.android.imagecapture;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.android.gridlines.GridLinesMapComponent;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.tilecapture.imagery.ImageryCapturePP;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.opengl.GLMapSurface;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.TimeZone;

/**
 * Class for handling post-processing on base imagery
 * such as drawing map items, labels, shapes, etc.
 * @deprecated See {@link ImageryCapturePP}
 */
@Deprecated
public class ImageCapturePP extends CapturePP {

    private static final String TAG = "ImageCapturePP";

    // Required items for setup
    protected boolean _setup = false;
    protected GLCaptureTool _capture;
    protected ImageCapture _subject;
    protected GLMapSurface _surface;
    protected final MapView _mapView;
    protected final Context _context;
    protected final Resources _res;
    protected final SharedPreferences _prefs;
    protected final List<MapItem> _items;
    protected final CustomGrid _grid;
    protected final GeoBounds _bounds, _cropped;

    // Calculated
    protected String _title, _location, _dateStamp;
    protected double _scaleFull, _scaleSeg;
    protected double _mapRange, _horizRange, _vertRange;
    protected FOVFilter _fovFilter;
    protected final Map<String, Bundle> _pointData = new HashMap<>();
    protected final Map<String, Bitmap> _bitmapCache = new HashMap<>();
    protected double _forwardUnwrap;

    // Customizable, non-required fields
    protected ProgressCallback _progressCb;
    protected int _curProg = 0;
    protected long _lastProgUpdate = 0;
    protected boolean _canceled = false;

    public ImageCapturePP(GLCaptureTool capture, int drawRes) {
        _capture = capture;
        _subject = (ImageCapture) capture.getSubject();
        _surface = capture.getSurface();
        _width = _subject.getWidth();
        _height = _subject.getHeight();
        _mapView = capture.getMapView();
        _mapScale = _mapView.getMapScale();
        _mapRes = _mapView.getMapResolution();
        _context = _mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _res = _context.getResources();
        _bounds = capture.getFullBounds();
        _items = _subject.getItems(_bounds);
        _cropped = _capture.getBounds();
        _drawRes = drawRes;
        _grid = GridLinesMapComponent.getCustomGrid();
        _title = "Untitled";
        if (setup()) {
            _fovFilter = new FOVFilter(_bounds);
            if (_setup) {
                // Center of map bounds
                GeoPoint center = _bounds.getCenter(null);
                MGRSPoint mgrs = MGRSPoint.fromLatLng(Ellipsoid.WGS_84,
                        center.getLatitude(),
                        center.getLongitude(), null);
                _location = mgrs.getFormattedString().replace("  ", " ");

                // Date stamp
                Date now = CoordinatedTime.currentDate();
                SimpleDateFormat formatter = new SimpleDateFormat(
                        "dd MMM yyyy", LocaleUtil.getCurrent());
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                _dateStamp = formatter.format(now).toUpperCase(
                        LocaleUtil.getCurrent());

                // Map scale
                GeoPoint bl = new GeoPoint(_bounds.getSouth(),
                        _bounds.getWest());
                GeoPoint br = new GeoPoint(_bounds.getSouth(),
                        _bounds.getEast());
                GeoPoint tl = new GeoPoint(_bounds.getNorth(),
                        _bounds.getWest());
                _horizRange = bl.distanceTo(br);
                _vertRange = bl.distanceTo(tl);
                _mapRange = SpanUtilities.convert(_horizRange, Span.METER,
                        getDisplayUnit());
                _scaleFull = (int) Math.pow(10,
                        Math.floor(Math.log10(_mapRange)));
                _scaleSeg = _scaleFull / 5.0;
            }
        }
    }

    public synchronized void dispose() {
        for (Bitmap b : _bitmapCache.values())
            b.recycle();
        _bitmapCache.clear();
        _capture = null;
        _subject = null;
        _surface = null;
        _setup = false;
    }

    public synchronized void setDrawRes(int res) {
        _drawRes = res;
        setup();
    }

    public synchronized void setProgressCallback(ProgressCallback cb) {
        _progressCb = cb;
    }

    public interface ProgressCallback {
        void onProgress(int prog);
    }

    protected synchronized boolean setup() {
        return _setup = _mapView != null && _items != null
                && _bounds != null && _drawRes > 0;
    }

    @Override
    public synchronized PointF forward(GeoPoint gp) {
        PointF p = new PointF();
        AbstractGLMapItem2.forward(_capture, gp, p, _forwardUnwrap);
        p.y = _capture.getTop() - p.y;
        return p;
    }

    public synchronized String getTitle() {
        return _setup ? _title : "";
    }

    /**
     * Get the date stamp from when the capture was taken
     * @return Date stamp
     */
    public synchronized String getDateStamp() {
        return _setup ? _dateStamp : "";
    }

    /**
     * Get the location of the capture
     * @return Location string (in MGRS by default)
     */
    public synchronized String getLocation() {
        return _setup ? _location : "";
    }

    /**
     * Get the map scale display size
     * @return Map scale in feet/yards (depends on survey type)
     */
    public synchronized double getMapScaleDisplay() {
        return _scaleFull;
    }

    /**
     * Get the scale bar segment interval
     * @return Scale bar segment interval (map scale / 5 by default)
     */
    public synchronized double getMapScaleSegment() {
        return _scaleSeg;
    }

    /**
     * Get the full map range
     * @return Full map range in feet/yards
     */
    public synchronized double getMapRange() {
        return _mapRange;
    }

    public synchronized double getHorizontalRange() {
        return _horizRange;
    }

    public synchronized double getVerticalRange() {
        return _vertRange;
    }

    /**
     * Set the 3 fields to be shown in the info box
     * By default these values are obtained during setup()
     * @param title Survey name
     * @param dateStamp Date of capture
     * @param location Location of survey
     */
    public synchronized void setInfo(
            String title, String location, String dateStamp) {
        CapturePrefs.set(CapturePrefs.PREF_LAST_TITLE, title);
        _title = title;
        _dateStamp = dateStamp;
        _location = location;
    }

    /**
     * Set the 2 fields used to draw the map scale bar
     * @param fullSize Size of the scale bar
     * @param segSize Size of each segment (red/white interleave)
     */
    public synchronized void setMapScale(double fullSize, double segSize) {
        _scaleFull = fullSize;
        _scaleSeg = segSize;
    }

    @Override
    public synchronized float getWidth() {
        return _can != null ? _can.getWidth() : _width * _drawRes;
    }

    @Override
    public synchronized float getHeight() {
        return _can != null ? _can.getHeight() : _height * _drawRes;
    }

    public Context getContext() {
        return _context;
    }

    @Override
    public GeoBounds getBounds() {
        return _cropped;
    }

    public synchronized Span getDisplayUnit() {
        if (_setup) {
            String units = _prefs.getString("rab_rng_units_pref",
                    String.valueOf(Span.METRIC));
            if (units.equals(String.valueOf(Span.ENGLISH)))
                return Span.FOOT;
            if (units.equals(String.valueOf(Span.NM)))
                return Span.NAUTICALMILE;
        }
        return Span.METER;
    }

    public synchronized CaptureDialog getCaptureDialog() {
        return new CaptureDialog(getContext(), this);
    }

    /**
     * Load icon bitmap from cache
     * Cache is released after window is closed
     * @param uri Icon uri as a string
     * @return Icon bitmap
     */
    @Override
    public synchronized Bitmap loadBitmap(String uri) {
        if (uri != null) {
            if (_bitmapCache.containsKey(uri))
                return _bitmapCache.get(uri);
            Bitmap b = ATAKUtilities.getUriBitmap(uri);
            if (b != null)
                _bitmapCache.put(uri, b);
            return b;
        }
        return null;
    }

    /**
     * Needs to be called right when capture is started
     */
    public synchronized void calcForwardPositions() {
        if (!_setup)
            return;
        _pointData.clear();

        if (_grid != null && _grid.isValid() && _grid.isVisible())
            _pointData.put(_grid.getName(), _grid.preDrawCanvas(this));

        // Save marker screen positions (only for markers within bounds
        MutableGeoBounds mgb = new MutableGeoBounds(0, 0, 0, 0);
        for (int i = 0; i < _items.size(); i++) {
            MapItem mi = _items.get(i);
            if (mi == null || !mi.getVisible())
                continue;
            String uid = mi.getUID();
            if (mi instanceof Capturable) {
                if (_fovFilter.accept(mi)) {
                    _forwardUnwrap = 0;
                    if (mi instanceof Shape) {
                        mgb.set(((Shape) mi).getPoints(),
                                _capture.continuousScrollEnabled);
                        _forwardUnwrap = _capture.idlHelper.getUnwrap(mgb);
                    }
                    Bundle data = ((Capturable) mi).preDrawCanvas(this);
                    if (data != null)
                        _pointData.put(uid, data);
                }
            }
        }
    }

    /**
     * Create a bitmap for drawing on
     * @param src Bitmap to copy
     * @return New bitmap for drawing on
     */
    public Bitmap createBitmap(Bitmap src) {
        return Bitmap.createBitmap(src);
    }

    protected synchronized void drawMapItem(MapItem mi) {
        // Reset paint style
        resetPaint();
        if (mi instanceof Capturable) {
            Bundle pData = _pointData.get(mi.getUID());
            if (pData != null)
                ((Capturable) mi).drawCanvas(this, pData);
        }
    }

    protected void setDrawingParameters(Canvas can) {
        _can = can;
        _paint = new Paint();
        _path = new Path();
        _dp = Math.min(can.getWidth(), can.getHeight()) / 360.0f;
        _borderWidth = dp(1);
        _fontSize = dp(Math
                .min(CapturePrefs.get(CapturePrefs.PREF_FONT_SIZE, 10), 36));
        _labelSize = dp(Math
                .min(CapturePrefs.get(CapturePrefs.PREF_LABEL_SIZE, 10), 36));
        _iconSize = dp(Math
                .min(CapturePrefs.get(CapturePrefs.PREF_ICON_SIZE, 24), 48));
        _lineWeight = dp(Math
                .min(CapturePrefs.get(CapturePrefs.PREF_LINE_WEIGHT, 3), 10))
                * 0.1f;
        _pdFontSize = _fontSize / 5;
        _pdLabelSize = _labelSize / 5;
        _dashStyle = new DashPathEffect(new float[] {
                dp(1), dp(2)
        }, 0);
        _dotStyle = new DashPathEffect(new float[] {
                dp(0.25f), dp(1)
        }, 0);
        resetPaint();
    }

    /**
     * Overlay declination and survey info onto captured bitmap
     * @param can Canvas to draw to
     */
    public synchronized boolean drawElements(Canvas can) {
        if (!_setup)
            return false;
        setDrawingParameters(can);
        _canceled = false;

        // Fill in background with white
        if (!CapturePrefs.get(CapturePrefs.PREF_SHOW_IMAGERY, true))
            _can.drawColor(Color.WHITE);

        // Re-draw icons and labels with scalable DPI
        List<MapItem> ordered = new ArrayList<>(_items);

        // For keeping track of draw progress
        // Items + grid + info box + arrows + scale bar
        int progress = 0, progressTotal = ordered.size()
                + toInt(_grid.isValid());

        // Draw grid
        if (_subject.snapToGrid()) {
            _grid.drawFittedGrid(this);
            updateProgress(++progress, progressTotal);
        } else if (_grid.isValid()) {
            _grid.drawCanvas(this, _pointData.get(_grid.getName()));
            updateProgress(++progress, progressTotal);
        }

        // Draw all other map items
        Collections.sort(ordered, new Comparator<MapItem>() {
            @Override
            public int compare(MapItem item1, MapItem item2) {
                return Double.compare(item2.getZOrder(), item1.getZOrder());
            }
        });
        for (int i = 0; i < ordered.size(); i++) {
            drawMapItem(ordered.get(i));
            if (_canceled) {
                updateProgress(progressTotal, progressTotal);
                return false;
            }
            updateProgress(++progress, progressTotal);
        }

        // Custom overlays drawn here
        drawOverlays();

        updateProgress(progressTotal, progressTotal);
        return true;
    }

    @Deprecated
    public synchronized boolean drawElements(Bitmap bmp) {
        return drawElements(new Canvas(bmp));
    }

    /**
     * Draw extra overlay widgets on top of map items
     */
    public synchronized void drawOverlays() {
        // Override me!
    }

    public void cancelBusy() {
        _canceled = true;
    }

    /**
     * Update the progress bar (if necessary)
     * @param cur Current progress value
     * @param max Max progress value
     */
    private synchronized void updateProgress(int cur, int max) {
        if (_progressCb != null && (SystemClock.elapsedRealtime()
                - _lastProgUpdate > 20 || cur == max)) {
            final int prog = (int) (((float) cur / (float) max) * 100);
            if (prog == _curProg)
                return;
            _curProg = prog;
            _lastProgUpdate = SystemClock.elapsedRealtime();
            final ProgressCallback cb = _progressCb;
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    cb.onProgress(prog);
                }
            });
        }
    }

    /**
     * Convert color to a theme color
     * @param color Original color
     * @return Theme color
     */
    @Override
    public int getThemeColor(int color) {
        String theme = CapturePrefs.get(CapturePrefs.PREF_THEME,
                CapturePrefs.PREF_THEME_LIGHT);
        if (theme.equals(CapturePrefs.PREF_THEME_LIGHT)) {
            return color;
        } else if (theme.equals(CapturePrefs.PREF_THEME_DARK)) {
            switch (color) {
                case Color.WHITE:
                    return Color.BLACK;
                case Color.BLACK:
                    return Color.WHITE;
            }
            float[] hsv = new float[3];
            int inverted = Color.rgb(255 - Color.red(color),
                    255 - Color.green(color), 255 - Color.blue(color));
            Color.colorToHSV(inverted, hsv);
            hsv[0] = (hsv[0] + 180) % 360;
            return Color.HSVToColor(Color.alpha(color), hsv);
        }
        return color;
    }
}
