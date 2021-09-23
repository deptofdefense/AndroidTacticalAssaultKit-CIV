
package com.atakmap.android.tilecapture.imagery;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;

import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.CapturePrefs;
import com.atakmap.android.imagecapture.TiledCanvas;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.tilecapture.TileCapture;
import com.atakmap.android.tilecapture.TileCaptureBounds;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.math.PointD;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Imagery capture post-processing
 */
public class ImageryCapturePP extends CapturePP {

    private static final String TAG = "ImageryCapturePP";

    protected final MapView _mapView;
    protected final SharedPreferences _prefs;
    protected final TileCapture _tileCap;
    protected final ImageryCaptureParams _capParams;
    protected final TileCaptureBounds _bounds;
    protected final GeoPoint _center;
    protected final DatasetProjection2 _imprecise;
    protected final PointD _scratchPoint = new PointD(0, 0, 0);

    protected float _drawRes = 1f;

    protected String _title, _location, _dateStamp;
    protected double _scaleFull, _scaleSeg, _mapRange;

    protected double _imprWidth, _imprHeight;
    protected double _unwrap;

    public ImageryCapturePP(MapView mapView, TileCapture tc,
            ImageryCaptureParams cp) {
        _mapView = mapView;
        _prefs = PreferenceManager.getDefaultSharedPreferences(
                mapView.getContext());
        _tileCap = tc;
        _capParams = cp;
        _bounds = tc.getBounds(cp);
        _imprecise = tc.getProjection();

        _width = _bounds.imageWidth;
        _height = _bounds.imageHeight;

        _imprWidth = _bounds.eastImageBound - _bounds.westImageBound;
        _imprHeight = _bounds.southImageBound - _bounds.northImageBound;

        _mapScale = _mapView.getMapScale();
        _mapRes = _mapView.getMapResolution();

        // Title
        _title = "Untitled";

        // Center of map bounds
        _center = cp.fitToQuad ? GeoCalculations.computeAverage(cp.points)
                : _bounds.getCenter(null);
        if (_bounds.crossesIDL())
            _unwrap = _mapView.getIDLHelper().getUnwrap(_bounds);

        _location = CoordinateFormatUtilities.formatToString(_center,
                CoordinateFormat.MGRS);
        GeoPointMetaData gpm = new GeoPointMetaData(_center);
        ElevationManager.getElevation(_center.getLatitude(),
                _center.getLongitude(), null, gpm);
        if (gpm.get().isAltitudeValid())
            _location += "\n" + String.format(LocaleUtil.getCurrent(),
                    "%.0f ft %s %s", SpanUtilities.convert(gpm.get()
                            .getAltitude(), Span.METER, Span.FOOT),
                    "MSL",
                    gpm.getAltitudeSource());

        // Date stamp
        Date now = CoordinatedTime.currentDate();
        SimpleDateFormat formatter = new SimpleDateFormat(
                "dd MMM yyyy", LocaleUtil.getCurrent());
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        _dateStamp = formatter.format(now).toUpperCase(
                LocaleUtil.getCurrent());

        // Map scale
        double meters;
        if (cp.fitToQuad && cp.points.length == 4) {
            meters = cp.points[3].distanceTo(cp.points[2]);
        } else {
            GeoPoint bl = new GeoPoint(_bounds.getSouth(),
                    _bounds.getWest());
            GeoPoint br = new GeoPoint(_bounds.getSouth(),
                    _bounds.getEast());
            meters = bl.distanceTo(br);
        }
        _mapRange = SpanUtilities.convert(meters, Span.METER,
                getDisplayUnit());
        _scaleFull = (int) Math.pow(10, Math.floor(Math.log10(_mapRange)));
        _scaleSeg = _scaleFull / 5.0;
    }

    /**
     * Get the date stamp from when the capture was taken
     * @return Date stamp
     */
    public String getDateStamp() {
        return _dateStamp;
    }

    /**
     * Get the location of the capture
     * @return Location string (in MGRS by default)
     */
    public String getLocation() {
        return _location;
    }

    /**
     * Get the map scale display size
     * @return Map scale in feet/yards (depends on survey type)
     */
    public double getMapScaleDisplay() {
        return _scaleFull;
    }

    /**
     * Get the scale bar segment interval
     * @return Scale bar segment interval (map scale / 5 by default)
     */
    public double getMapScaleSegment() {
        return _scaleSeg;
    }

    /**
     * Get the full map range
     * @return Full map range in user-preferred display units
     */
    public double getMapRange() {
        return _mapRange;
    }

    public Span getDisplayUnit() {
        String units = _prefs.getString("rab_rng_units_pref",
                String.valueOf(Span.METRIC));
        if (units.equals(String.valueOf(Span.ENGLISH)))
            return Span.FOOT;
        if (units.equals(String.valueOf(Span.NM)))
            return Span.NAUTICALMILE;
        return Span.METER;
    }

    public boolean snapToGrid() {
        return true;
    }

    /**
     * Set the 3 fields to be shown in the info box
     * By default these values are obtained during setup()
     * @param title Survey name
     * @param dateStamp Date of capture
     * @param location Location of survey
     */
    public void setInfo(String title, String location, String dateStamp) {
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
    public void setMapScale(double fullSize, double segSize) {
        _scaleFull = fullSize;
        _scaleSeg = segSize;
    }

    /**
     * Create a preview bitmap for an imagery tileset
     * @param tc Tiled canvas pointing to the captured imagery
     * @return Preview bitmap
     */
    public Bitmap createPreview(TiledCanvas tc) {
        DisplayMetrics dm = _mapView.getResources().getDisplayMetrics();
        int sampleWidth = (int) (MathUtils.log2(tc.getWidth())
                - MathUtils.log2(dm.widthPixels));
        int sampleHeight = (int) (MathUtils.log2(tc.getHeight())
                - MathUtils.log2(dm.heightPixels));
        int res = 1 << Math.max(0, Math.max(sampleWidth, sampleHeight));
        setDrawRes(res);
        return tc.createThumbnail(tc.getWidth() / res, tc.getHeight() / res);
    }

    public Bitmap createBitmap(Bitmap src) {
        return Bitmap.createBitmap(src);
    }

    public void setDrawRes(int drawRes) {
        _drawRes = 1f / drawRes;
    }

    public float getResolution() {
        return _drawRes;
    }

    /**
     * Draw overlays on map imagery
     * @param can Canvas to draw to
     */
    @Override
    public boolean drawElements(Canvas can) {
        setDrawingParameters(can);

        // Fill in background with white
        if (!CapturePrefs.get(CapturePrefs.PREF_SHOW_IMAGERY, true))
            _can.drawColor(Color.WHITE);

        return true;
    }

    @Override
    public float getWidth() {
        return dr(_width);
    }

    @Override
    public float getHeight() {
        return dr(_height);
    }

    protected void setDrawingParameters(Canvas can) {
        _can = can;
        _paint = new Paint();
        _path = new Path();
        _dp = Math.min(can.getWidth(), can.getHeight()) / 360.0f;
        _borderWidth = dp(1);
        _fontSize = dp(Math.min(CapturePrefs.get(
                CapturePrefs.PREF_FONT_SIZE, 10), 36));
        _labelSize = dp(Math.min(CapturePrefs.get(
                CapturePrefs.PREF_LABEL_SIZE, 10), 36));
        _iconSize = dp(Math.min(CapturePrefs.get(
                CapturePrefs.PREF_ICON_SIZE, 24), 48));
        _lineWeight = dp(Math.min(CapturePrefs.get(
                CapturePrefs.PREF_LINE_WEIGHT, 3), 10)) * 0.1f;
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

    @Override
    public PointF forward(GeoPoint gp) {
        if (Double.compare(_unwrap, 0) != 0) {
            double lng = gp.getLongitude();
            if (lng > 0 && _unwrap < 0 || lng < 0 && _unwrap > 0)
                lng += _unwrap;
            gp = new GeoPoint(gp.getLatitude(), lng);
        }
        _imprecise.groundToImage(gp, _scratchPoint);
        double sx = (_scratchPoint.x - _bounds.westImageBound) / _imprWidth;
        double sy = (_scratchPoint.y - _bounds.northImageBound) / _imprHeight;
        float[] pt = new float[] {
                (float) (sx * _bounds.tileImageWidth),
                (float) (sy * _bounds.tileImageHeight)
        };
        _bounds.tileToPixel.mapPoints(pt);
        return new PointF(pt[0], pt[1]);
    }

    @Override
    public GeoBounds getBounds() {
        return _bounds;
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

    /**
     * Write document KML
     * @param c Coordinate quad to use
     * @param fName File name
     * @param docKml Document KML file
     */
    public void writeKML(GeoPoint[] c, String fName, File docKml) {
        fName = CotEvent.escapeXmlText(fName);
        String docSkel = "<?xml version='1.0' encoding='UTF-8'?>"
                +
                "<kml xmlns='http://www.opengis.net/kml/2.2' xmlns:gx='http://www.google.com/kml/ext/2.2' xmlns:kml='http://www.opengis.net/kml/2.2' xmlns:atom='http://www.w3.org/2005/Atom'>"
                + "<GroundOverlay>"
                + "<name>"
                + fName
                + "</name>"
                + "<Icon>"
                + "<href>"
                + fName
                + "</href>"
                + "<viewBoundScale>0.75</viewBoundScale>"
                + "</Icon>"
                + "<gx:LatLonQuad>"
                + "<coordinates>"
                + c[3].getLongitude() + "," + c[3].getLatitude() + ",0 "
                + c[2].getLongitude() + "," + c[2].getLatitude() + ",0 "
                + c[1].getLongitude() + "," + c[1].getLatitude() + ",0 "
                + c[0].getLongitude() + "," + c[0].getLatitude() + ",0"
                + "</coordinates>"
                + "</gx:LatLonQuad>"
                + "</GroundOverlay>"
                + "</kml>";
        try (OutputStream os = IOProviderFactory.getOutputStream(docKml);
                OutputStreamWriter osw = new OutputStreamWriter(
                        os, FileSystemUtils.UTF8_CHARSET.name());
                PrintWriter out = new PrintWriter(osw)) {
            out.println(docSkel);
        } catch (IOException ioe) {
            Log.d(TAG, "error occurred writing the doc.xml file", ioe);
        }
    }

    public void writeKML(String fName, File docKml) {
        writeKML(_capParams.points, fName, docKml);
    }
}
