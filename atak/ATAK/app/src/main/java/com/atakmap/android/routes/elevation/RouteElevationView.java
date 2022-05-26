
package com.atakmap.android.routes.elevation;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.util.TypedValue;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.elevation.chart.ImageSeriesRenderer;
import com.atakmap.android.routes.elevation.chart.XYImageSeriesRenderer;
import com.atakmap.app.R;

import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.locale.LocaleUtil;

public class RouteElevationView extends LinearLayout {

    public final static int _CHART_TOP_MARGIN = 0;
    public final static int _CHART_LEFT_MARGIN = 120;
    public final static int _CHART_BOTTOM_MARGIN = 40;
    public final static int _CHART_RIGHT_MARGIN = 60;
    private final static int _EMPTY_COLOR = Color.argb(0, 1, 1, 1);
    private final static int _GRAPH_COLOR = Color.argb(80, 0, 255, 0);
    private final static int _GRAPH_COLOR_NULL = Color.argb(80, 255, 0, 0);
    private final static int _CP_COLOR = Color.argb(100, 255, 255, 255);
    private LinearLayout _layout;
    protected ChartSeekBar _seekerBar;
    private RouteElevationChart _chart;
    protected XYMultipleSeriesRenderer _renderer;
    private XYImageSeriesRenderer _imageRender;
    private SharedPreferences _prefs;
    private final Context _context;

    public RouteElevationView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        _context = context;
    }

    public void initialize() {
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);

        // RouteChart place holder
        _layout = findViewById(R.id.RouteChart);

        // Setup seeker bar
        _seekerBar = new ChartSeekBar(_context);
        _seekerBar.setLeftMarginAdjust(_CHART_LEFT_MARGIN);
        _seekerBar.setRightMarginAdjust(_CHART_RIGHT_MARGIN);
        _seekerBar.setBackgroundColor(_EMPTY_COLOR);
        _seekerBar
                .setProgressDrawable(new ColorDrawable(_context.getResources()
                        .getColor(android.R.color.black)));
        _seekerBar.setPadding(_CHART_LEFT_MARGIN, 0, _CHART_RIGHT_MARGIN, 0);
        _seekerBar.setProgress(_seekerBar.getMax() / 2); // Start by centering
                                                         // the seek bar

        // Create renderer
        _renderer = new XYMultipleSeriesRenderer();
        _renderer.setBackgroundColor(_EMPTY_COLOR);
        _renderer.setZoomButtonsVisible(false);
        _renderer.setZoomEnabled(true, false);
        _renderer.setPanEnabled(true, false);
        _renderer.setShowAxes(true);

        // shb: achartengine uses raw pixels in mind while pixel density has increased
        // determine the conversion from raw pixel size to scale independent size.
        final float sizeSP = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 10,
                _context.getResources().getDisplayMetrics());

        _renderer.setLabelsTextSize(sizeSP);
        _renderer.setLegendTextSize(sizeSP);
        _renderer.setAxisTitleTextSize(sizeSP);
        _renderer.setPointSize(sizeSP);
        _renderer.setTextTypeface("Arial", android.graphics.Typeface.BOLD);

        _renderer.setShowLabels(true);
        _renderer.setAntialiasing(false);
        _renderer.setXLabels(6);
        _renderer.setYLabels(5);
        _renderer.setYLabelFormat(LocaleUtil.getDecimalFormat("0"), 0);
        _renderer.setShowGrid(true);
        _renderer.setMargins(new int[] {
                _CHART_TOP_MARGIN, _CHART_LEFT_MARGIN,
                _CHART_BOTTOM_MARGIN, _CHART_RIGHT_MARGIN
        });
        _renderer.setMarginsColor(Color.argb(1, 1, 1, 1));
        _renderer.setApplyBackgroundColor(true);
        _renderer.setYLabelsAlign(Paint.Align.RIGHT);

        _imageRender = new XYImageSeriesRenderer();
        _imageRender.setMargins(new int[] {
                _CHART_TOP_MARGIN,
                _CHART_LEFT_MARGIN, _CHART_BOTTOM_MARGIN, _CHART_RIGHT_MARGIN
        });

        // non-null chart
        XYSeriesRenderer sRenderer = new XYSeriesRenderer();
        sRenderer.setColor(_GRAPH_COLOR);
        XYSeriesRenderer.FillOutsideLine fol = new XYSeriesRenderer.FillOutsideLine(
                XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL);
        fol.setColor(_GRAPH_COLOR);
        sRenderer.addFillOutsideLine(fol);
        sRenderer.setLineWidth(3);
        sRenderer.setShowLegendItem(false);

        // null chart
        XYSeriesRenderer nullRenderer = new XYSeriesRenderer();
        nullRenderer.setColor(_GRAPH_COLOR_NULL);
        XYSeriesRenderer.FillOutsideLine folNull = new XYSeriesRenderer.FillOutsideLine(
                XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL);
        folNull.setColor(_GRAPH_COLOR_NULL);
        nullRenderer.addFillOutsideLine(folNull);
        nullRenderer.setLineWidth(3);
        nullRenderer.setShowLegendItem(false);

        ImageSeriesRenderer cpRenderer = new ImageSeriesRenderer();
        Bitmap cpIcon = BitmapFactory.decodeResource(getResources(),
                R.drawable.reference_point);
        cpRenderer.setImage(cpIcon);
        cpRenderer.setCenter(cpIcon.getWidth() / 2f, cpIcon.getHeight() / 2f);

        ImageSeriesRenderer ownRenderer = new ImageSeriesRenderer();
        Bitmap large = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_self);
        Bitmap ownIcon = Bitmap.createScaledBitmap(large, 32, 32, false);
        ownRenderer.setImage(ownIcon);
        ownRenderer.setCenter(ownIcon.getHeight() / 2f,
                ownIcon.getHeight() / 2f);

        refresh();
        _renderer.addSeriesRenderer(sRenderer);
        _renderer.addSeriesRenderer(nullRenderer);
        _imageRender.addSeriesRenderer(cpRenderer);
        _imageRender.addSeriesRenderer(ownRenderer);
    }

    public RouteElevationChart getChart() {
        return _chart;
    }

    public void setChart(RouteElevationChart chart) {
        this._chart = chart;
    }

    public XYImageSeriesRenderer getImageRender() {
        return _imageRender;
    }

    public LinearLayout getLayout() {
        return _layout;
    }

    public void setLayout(LinearLayout layout) {
        this._layout = layout;
    }

    public XYMultipleSeriesRenderer getRenderer() {
        return _renderer;
    }

    public ChartSeekBar getSeekerBar() {
        return _seekerBar;
    }

    public void refresh() {
        String altDisplayPref = _prefs.getString("alt_display_pref",
                "MSL");

        Span rangeUnits = getRangeUnits();
        Span heightUnits = getAltUnits();

        StringBuilder yAxisTitle = new StringBuilder();

        yAxisTitle.append(MapView.getMapView()
                .getContext()
                .getString(com.atakmap.app.R.string.routes_text26));

        yAxisTitle.append(" (");
        yAxisTitle.append(heightUnits.getAbbrev());
        yAxisTitle.append(" ");
        yAxisTitle.append(altDisplayPref);
        yAxisTitle.append(")");

        StringBuilder xAxisTitle = new StringBuilder();

        xAxisTitle.append(MapView.getMapView()
                .getContext()
                .getString(com.atakmap.app.R.string.routes_text25));
        xAxisTitle.append(rangeUnits.getAbbrev());
        xAxisTitle.append(")");

        _renderer.setYTitle(yAxisTitle.toString());
        _renderer.setXTitle(xAxisTitle.toString());

        if (_chart != null) {
            _chart.repaint();
        }
    }

    public Span getRangeUnits() {
        int rangeFmt = Integer.parseInt(_prefs.getString("rab_rng_units_pref",
                String.valueOf(Span.METRIC)));
        Span rangeUnits = Span.METER;

        if (rangeFmt == Span.ENGLISH)
            rangeUnits = Span.MILE;
        else if (rangeFmt == Span.NM)
            rangeUnits = Span.NAUTICALMILE;
        return rangeUnits;
    }

    public Span getAltUnits() {
        int heightFmt = Integer.parseInt(_prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));
        Span heightUnits = Span.FOOT;
        if (heightFmt == Span.METRIC) {
            heightUnits = Span.METER;
        }
        return heightUnits;
    }
}
