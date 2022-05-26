
package com.atakmap.android.routes.elevation;

import java.util.ArrayList;
import java.util.List;

import org.achartengine.GraphicalView;
import org.achartengine.chart.LineChart;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.tools.PanListener;
import org.achartengine.tools.ZoomEvent;
import org.achartengine.tools.ZoomListener;
import org.achartengine.util.MathHelper;

import android.preference.PreferenceManager;
import android.content.SharedPreferences;

import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.atakmap.android.routes.elevation.chart.XYImageSeriesDataset;
import com.atakmap.android.routes.elevation.chart.XYImageSeriesRenderer;
import com.atakmap.coremap.log.Log;

public class RouteElevationChart extends GraphicalView implements
        OnSeekBarChangeListener {

    public static final String _TAG = "RouteElevationChart";
    final List<ChartSelectionListener> _countsListeners = new ArrayList<>();
    private Paint _paint = null;
    /**
     * Indicates the left margin of chart so that the vertical line can be drawn appropriately.
     */
    private int _leftMargin = 0;
    private int _rightMargin = 0;
    private float _linePosition = 0;
    private double _realPosition = 0;
    private int chartWidth = 0;
    private SeekBar _seekBar;
    private int _seekProg = 0;
    private XYImageSeriesRenderer _imageRenderer = new XYImageSeriesRenderer();
    private XYMultipleSeriesDataset _dataset = new XYMultipleSeriesDataset();

    public XYMultipleSeriesDataset getDataset() {
        return _dataset;
    }

    public XYImageSeriesDataset getImageDataset() {
        return imageDataset;
    }

    private XYImageSeriesDataset imageDataset;
    private XYSeries _selectedSeries;
    private final LineChart _lineChart;

    public RouteElevationChart(Context context, LineChart lineChart) {
        super(context, lineChart);

        this._lineChart = lineChart;
        setupLinePaint();
        initPanListener();
        initZoomListener();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right,
            int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float chartWidth = right - left - _leftMargin - _rightMargin;
        if (this.chartWidth != 0 && _seekBar != null) {
            // set the line position to be the percentage that is was scaled over
            double diff = chartWidth / (double) this.chartWidth;
            int newProg = (int) (_seekProg * diff);
            _seekBar.setProgress(_seekProg = newProg);
            repaint();
        }
        this.chartWidth = (int) chartWidth;
    }

    public RouteElevationChart(Context context, LineChart lineChart,
            XYMultipleSeriesDataset dataset) {
        this(context, lineChart);
        this._dataset = dataset;

    }

    public RouteElevationChart(Context context,
            XYMultipleSeriesRenderer renderer,
            XYImageSeriesRenderer imageRenderer,
            XYMultipleSeriesDataset dataset,
            XYImageSeriesDataset imageDataset, XYSeries selected) {
        this(context, new CustomLineChart(dataset, renderer, context));
        this._imageRenderer = imageRenderer;
        this._dataset = dataset;
        this.imageDataset = imageDataset;
        this._selectedSeries = selected;
    }

    public static class CustomLineChart extends LineChart {
        private final SharedPreferences _prefs;

        public CustomLineChart(XYMultipleSeriesDataset dataset,
                XYMultipleSeriesRenderer renderer, Context context) {
            super(dataset, renderer);
            _prefs = PreferenceManager.getDefaultSharedPreferences(context);
        }

        private String format(String label) {
            try {
                double d = Double.parseDouble(label);

                int rangeFmt = Integer
                        .parseInt(_prefs.getString("rab_rng_units_pref",
                                String.valueOf(Span.METRIC)));

                // something is wrong here - it turns out that the values are actually in feet?
                return SpanUtilities.formatType(rangeFmt, d, Span.FOOT);
            } catch (Exception ignored) {

            }
            return label;
        }

        protected void drawXLabels(List<Double> xLabels,
                Double[] xTextLabelLocations, Canvas canvas,
                Paint paint, int left, int top, int bottom,
                double xPixelsPerUnit, double minX, double maxX) {
            int length = xLabels.size();
            boolean showLabels = mRenderer.isShowLabels();
            boolean showGridY = mRenderer.isShowGridY();
            boolean showTickMarks = mRenderer.isShowTickMarks();
            for (int i = 0; i < length; i++) {
                double label = xLabels.get(i);
                float xLabel = (float) (left + xPixelsPerUnit * (label - minX));
                if (showLabels) {
                    paint.setColor(mRenderer.getXLabelsColor());
                    if (showTickMarks) {
                        canvas
                                .drawLine(xLabel, bottom, xLabel, bottom
                                        + mRenderer.getLabelsTextSize() / 3,
                                        paint);
                    }
                    drawText(canvas,
                            format(getLabel(
                                    mRenderer.getXLabelFormat(), label)),
                            xLabel,
                            bottom + mRenderer.getLabelsTextSize() * 4 / 3
                                    + mRenderer.getXLabelsPadding(),
                            paint,
                            mRenderer.getXLabelsAngle());
                }
                if (showGridY) {
                    paint.setColor(mRenderer.getGridColor(0));
                    canvas.drawLine(xLabel, bottom, xLabel, top, paint);
                }
            }
            drawXTextLabels(xTextLabelLocations, canvas, paint, showLabels,
                    left, top, bottom,
                    xPixelsPerUnit, minX, maxX);
        }
    }

    /**
     * Listener for user zoom. Note: The current version of achartengine we are using doesn't seem
     * to fire the zoom events. Need to look into this further.
     */
    private void initZoomListener() {
        ZoomListener zoomListener = new ZoomListener() {

            @Override
            public void zoomApplied(ZoomEvent event) {
                Log.d(_TAG, "User is zooming");
                updateForInteraction(false);
            }

            @Override
            public void zoomReset() {
                Log.d(_TAG, "Zoom reset");

            }

        };

        this.addZoomListener(zoomListener, false, true);
    }

    /**
     * Listener for user panning the chart and does the requisite updates.
     */
    private void initPanListener() {
        this.addPanListener(new PanListener() {

            @Override
            public void panApplied() {
                updateForInteraction(false);
            }

        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            //if the user lifts up call the listener with a special flag
            int h = this.getHeight();
            double[] realPoint = _lineChart.toRealPoint(_linePosition, h / 2f);

            if (realPoint != null && realPoint.length == 2) {
                if (realPoint[0] >= getMinXFromSeries() // superSeries.getMinX()
                        && realPoint[0] <= getMaxXFromSeries()) // superSeries.getMaxX())
                {
                    int indexForKey = getClosestIndexForX(realPoint[0]);
                    if (indexForKey > -1) {
                        long chartXVal = (long) _selectedSeries
                                .getX(indexForKey);
                        double chartYVal = _selectedSeries.getY(indexForKey);
                        updateCountsListeners(indexForKey, chartXVal,
                                chartYVal,
                                false, true);
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }

    public void addCountsListener(ChartSelectionListener l) {
        _countsListeners.add(l);
    }

    public int getLeftMargin() {
        return _leftMargin;
    }

    public void setMargins(int leftMargin, int rightMargin) {
        this._leftMargin = leftMargin;
        this._rightMargin = rightMargin;
        _linePosition += leftMargin;
    }

    public double[] toScreenPoint(double[] realPoint) {
        return _lineChart.toScreenPoint(realPoint);
    }

    public void setPositions(double realPos, int seekPos) {
        _realPosition = realPos;
        _seekProg = seekPos;
        _linePosition = seekPos + this.getLeftMargin();
        repaint();
    }

    public double getRealPosition() {
        return _realPosition;
    }

    /**
     * Sets up the vertical line that will be painted over the chart.
     */
    private void setupLinePaint() {

        _paint = new Paint();
        _paint.setStyle(Paint.Style.STROKE);
        _paint.setPathEffect(new DashPathEffect(new float[] {
                10, 20
        }, 0));
        _paint.setColor(this.getContext().getResources()
                .getColor(android.R.color.white));
        _paint.setAlpha(200);
    }

    private final double[] scratch = new double[2];

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        // Draws a vertical line over the chart to correspond to the line
        // position which was adjusted when the seek bar updated its position
        // via the listener.

        _linePosition = _seekProg + this.getLeftMargin();

        canvas.drawLine(_linePosition, 0, _linePosition, getHeight(), _paint);

        for (int i = 0; i < _imageRenderer.getSeriesRendererCount()
                && i < imageDataset.getSeriesCount(); i++) {
            for (int j = 0; j < imageDataset.getSeries()[i]
                    .getItemCount(); j++) {
                scratch[0] = imageDataset.getSeries()[i].getX(j);
                scratch[1] = imageDataset.getSeries()[i].getY(j);

                double[] p = _lineChart.toScreenPoint(scratch);
                if (p[0] >= _imageRenderer.getMargins()[1]
                        && p[0] <= (getWidth()
                                - _imageRenderer.getMargins()[3])) {
                    canvas.drawBitmap(
                            _imageRenderer.getSeriesRendererAt(i).getImage(),
                            (float) (p[0] - _imageRenderer.getSeriesRendererAt(
                                    i).getxOff()),
                            (float) (p[1] - _imageRenderer.getSeriesRendererAt(
                                    i).getyOff()),
                            _paint);
                }
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        _seekBar = seekBar;
        if (fromUser) {
            _seekProg = progress;

            // Updates the line position when this update comes in from the seek
            // bar.

            _linePosition = progress + this.getLeftMargin();

            if (_countsListeners.size() > 0) {
                for (ChartSelectionListener l : _countsListeners) {
                    if (l instanceof RouteElevationPresenter) {
                        ((RouteElevationPresenter) l)
                                .setSuppressAutoCentering(false);
                    }
                }
            }
            // update based on the changed line position.
            updateForInteraction(false);
            repaint();
        }
    }

    /**
     * Based on the line position on the chart, this function calculates the real point on the chart
     * and updates the listeners.
     */
    protected void updateForInteraction(boolean moveSeeker) {
        int h = this.getHeight();
        double[] realPoint = _lineChart.toRealPoint(_linePosition, h / 2f);

        // NOTE: At the moment, there is a strange artifact with the chart
        // such that it only returns a real point with real data once the
        // user interacts with the screen. Otherwise, it returns an x, y of
        // -Infinity,-Infinity.
        if (realPoint != null && realPoint.length == 2) {
            // Log.d(TAG, "Point: "
            // + realPoint[0] + ", " + realPoint[1]);

            // Clamp position to graph
            _realPosition = realPoint[0];
            double minX = getMinXFromSeries(), maxX = getMaxXFromSeries();
            if (realPoint[0] < minX)
                realPoint[0] = minX;
            else if (realPoint[0] > maxX)
                realPoint[0] = maxX;

            // Commenting out this call to the series because it is not
            // reliable.
            // Created a custom one for below.
            // int indexForKey = series.getIndexForKey((int)
            // realPoint[0]);
            int indexForKey = getClosestIndexForX(realPoint[0]);

            // Log.d(TAG, "Key index: "
            // + indexForKey);

            if (indexForKey > -1) {
                long chartXVal = (long) _selectedSeries.getX(indexForKey);
                double chartYVal = _selectedSeries.getY(indexForKey);
                // Log.d(TAG, "X, Y: "
                // + realPoint[0] + ", " + chartCount);

                // updateCountsListeners(realPoint[0], chartCount);
                updateCountsListeners(indexForKey, chartXVal, chartYVal,
                        moveSeeker, false);
            }
        }
    }

    public double getYFromIndex(int index) {
        return _selectedSeries.getY(index);
    }

    /**
     * Gets the min x-value from the multi series dataset.
     */
    private double getMinXFromSeries() {
        int numSeries = _dataset.getSeriesCount();

        double overallMin = Double.MAX_VALUE;

        for (int index = 0; index < numSeries; index++) {

            XYSeries subSeries = _dataset.getSeriesAt(index);

            double minimum = subSeries.getMinX();
            if (minimum < overallMin) {
                overallMin = minimum;
            }
        }
        return overallMin;
    }

    /**
     * Gets the max x-value from the multi series dataset.
     */
    private double getMaxXFromSeries() {
        int numSeries = _dataset.getSeriesCount();

        double overallMax = Double.MIN_VALUE;

        for (int index = 0; index < numSeries; index++) {

            XYSeries subSeries = _dataset.getSeriesAt(index);

            double maximum = subSeries.getMaxX();

            if (maximum > overallMax) {
                overallMax = maximum;
            }

        }

        return overallMax;
    }

    /**
     * Takes an x-value and finds the index of the closest x-value in dataset's series '0'
     * 
     * @param xVal the x value to use for finding the index of the closest x value in the dataset
     * @return returns the closes x value in the dataset
     */
    public int getClosestIndexForX(double xVal) {
        int indexToReturn = -1;
        if (_selectedSeries.getItemCount() > 0) {
            double startX = _selectedSeries.getX(0);
            if (xVal <= startX)
                return 0;
            double distance = Math.abs(xVal - startX);
            XYSeries dataSeries = _selectedSeries;
            for (int i = 0; i < dataSeries.getItemCount(); ++i) {
                double currentDistance = Math.abs(xVal - dataSeries.getX(i));

                if (currentDistance < distance
                        && dataSeries.getY(i) != MathHelper.NULL_VALUE) {
                    distance = currentDistance;
                    indexToReturn = i;
                }
            }
        }
        return indexToReturn;
    }

    private void updateCountsListeners(int index, double xVal, double yVal,
            boolean moveSeeker, boolean stopped) {
        if (_countsListeners.size() > 0) {
            for (ChartSelectionListener l : _countsListeners) {
                l.update(index, xVal, yVal, moveSeeker, stopped);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // Not implementing this function currently.

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Not implementing this function currently.

    }
}
