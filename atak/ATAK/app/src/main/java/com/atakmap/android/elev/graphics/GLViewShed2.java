
package com.atakmap.android.elev.graphics;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import com.atakmap.android.elev.ViewShedLayer2;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.lang.Objects;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;

public class GLViewShed2 extends GLHeatMap implements
        ViewShedLayer2.OnPointOfInterestChangedListener,
        ViewShedLayer2.OnRadiusChangedListener,
        ViewShedLayer2.OnOpacityChangedListener {

    private static final int SEEN = 0x00FF0000;
    private static final int UNSEEN = 0xFF000000;
    private static final int OUTSIDE_RANGE = 0x00000000;

    private GeoPoint pointOfInterest;
    private double radius;
    private int opacity;

    private boolean showCircle = true;

    private double calculatedRadius = 0;
    private GeoPoint calculatedPOI = null;
    private int calculatedOpacity = -1;

    private int columnCount;
    private int rowCount;
    private int startX;
    private int startY;
    private double xSampleDist;
    private double ySampleDist;
    private float[] elevGrid;
    private float[] slopeGrid;
    private float startAlt;

    public GLViewShed2(MapRenderer surface, ViewShedLayer2 subject) {
        super(surface, subject);

        subject.addOnPointOfInterestChangedListener(this);
        subject.addOnRadiusChangedListener(this);
        subject.addOnOpacityChangedListener(this);

        this.pointOfInterest = subject.getPointOfInterest();
        this.radius = subject.getRadius();
    }

    @Override
    public void onPointOfInterestChanged(ViewShedLayer2 layer) {
        final GeoPoint value = layer.getPointOfInterest();
        if (this.renderContext.isRenderThread()) {
            this.pointOfInterest = value;
            this.invalidate();
        } else {
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLViewShed2.this.pointOfInterest = value;
                    GLViewShed2.this.invalidate();
                }
            });
        }
    }

    @Override
    public void onRadiusChanged(ViewShedLayer2 layer) {
        final double value = layer.getRadius();
        if (this.renderContext.isRenderThread()) {
            this.radius = value;
            this.invalidate();
        } else {
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLViewShed2.this.radius = value;
                    GLViewShed2.this.invalidate();
                }
            });
        }
    }

    @Override
    public void onOpacityChanged(ViewShedLayer2 layer) {
        final int value = layer.getOpacity();
        if (this.renderContext.isRenderThread()) {
            this.opacity = value;
            this.invalidate();
        } else {
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLViewShed2.this.opacity = value;
                    GLViewShed2.this.invalidate();
                }
            });
        }
    }

    /*************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    protected boolean checkState() {
        if (this.invalid)
            return true;

        // only need to query when the point of interest or radius changes
        final ViewShedState2 prepared = (ViewShedState2) this.preparedState;
        final ViewShedState2 target = (ViewShedState2) this.targetState;

        return !Objects
                .equals(prepared.pointOfInterest, target.pointOfInterest)
                ||
                !((Double.isNaN(prepared.radius) && Double.isNaN(target.radius))
                        || (prepared.radius == target.radius));
    }

    @Override
    protected ViewState newViewStateInstance() {
        return new ViewShedState2();
    }

    @Override
    protected void updateRGBA(HeatMapState s, HeatMapParams result) {
        //skip finding the low res view
        if (result.quick)
            return;

        //only update if radius, or POI have changed
        final ViewShedState2 stateImpl = (ViewShedState2) s;

        result.rgbaData = new byte[result.elevationData.length * 4];
        IntBuffer ib = ByteBuffer.wrap(result.rgbaData).asIntBuffer();
        calculateVSD(stateImpl, result, ib);

        result.valid = true;
        calculatedRadius = radius;
        calculatedPOI = pointOfInterest;
        calculatedOpacity = opacity;

    }

    /**
     * Set up the variables so that the viewshed can be calculated, calculate the viewshed, then
     * convert the resulting 2d array color map back to a single dimensional array.
     * 
     * @param state - the viewshed info 
     * @param result - parameters for calculating the Viewshed overlay
     * @param resultGrid the resulting array to be filled with color values
     */
    private void calculateVSD(ViewShedState2 state, HeatMapParams result,
            IntBuffer resultGrid) {

        elevGrid = result.elevationData;

        columnCount = result.xSampleResolution;
        rowCount = result.ySampleResolution;

        //wrap the result array that will hold the color values associated with the point being seen or unseen
        xSampleDist = (state.radius * 2) / result.xSampleResolution;
        ySampleDist = (state.radius * 2) / result.ySampleResolution;

        startX = result.xSampleResolution / 2;
        startY = result.ySampleResolution / 2;

        startAlt = (float) state.pointOfInterest.getAltitude()
                + elevGrid[getIndex(startX, startY)];

        //        Log.d("JM", "Grid Size: " + result.xSampleResolution + " by " + result.ySampleResolution);
        //        long time = System.currentTimeMillis();
        calculateSlopeGrid();
        doLevelCalc(resultGrid);
        //        Log.d("JM", "VSD Calc completed: " + (System.currentTimeMillis() - time) + " mills");

    }

    private void calculateSlopeGrid() {
        slopeGrid = new float[elevGrid.length];
        int numStages = Math.min(startX, startY);
        for (int stage = 1; stage <= numStages; stage++) {

            int x = startX - stage;
            int y = startY - stage;

            //top quarter
            for (int i = 0; i < stage * 2; i++) {
                slopeGrid[getIndex(x, y)] = getSlope(x, y);
                x++;
            }

            //right quarter
            for (int i = 0; i < stage * 2; i++) {
                slopeGrid[getIndex(x, y)] = getSlope(x, y);
                y++;
            }

            //bottom quarter
            for (int i = 0; i < stage * 2; i++) {
                slopeGrid[getIndex(x, y)] = getSlope(x, y);
                x--;
            }

            //left quarter
            for (int i = 0; i < stage * 2; i++) {
                slopeGrid[getIndex(x, y)] = getSlope(x, y);
                y--;
            }

        }

    }

    private float getSlope(final int x, final int y) {
        float pointElev = elevGrid[getIndex(x, y)];
        double run = getSampleDist(x, y);
        double currentAngle = Math.atan(Math.abs(startAlt - pointElev)
                / run);
        currentAngle = Math.toDegrees(currentAngle);
        if (pointElev < startAlt)
            currentAngle = 360 - currentAngle;
        else
            currentAngle += 360;

        return (float) currentAngle;
    }

    private int getIndex(final double x, final double y) {
        return (int) y * columnCount + (int) x;
    }

    /**
     * Use a gridded approach to calculate the viewshed. Starting at the center point, test every point 
     * in the elevation grid to see if it is visible using the sightline ray approach. 
     * 
     * @param results - a 2d array of the color values corresponding to each point's visibility
     */
    private void doLevelCalc(IntBuffer results) {
        int y = 0;
        int x;
        for (int i = 0; i < columnCount - 1; i++) {
            testPoint(true, i, y, results);
        }

        x = columnCount - 1;
        //right quarter
        for (int i = 0; i < rowCount - 1; i++) {
            testPoint(false, x, i, results);
        }

        //bottom quarter
        y = rowCount - 1;
        for (int i = columnCount - 1; i > 0; i--) {
            testPoint(true, i, y, results);
        }

        //left quarter
        x = 0;
        for (int i = rowCount - 1; i > 0; i--) {
            testPoint(false, x, i, results);
        }

        //fill the center point
        testPoint(true, startX, startY, results);
    }

    /**
     * test a point at a given stage to see if it is visible or not
     * 
     * @param xAxis - true if sampling at x-axis intersections
     * @param x - the x loc of the point to be tested on the elevation grid
     * @param y - the x loc of the point to be tested on the elevation grid
     */
    private void testPoint(final boolean xAxis, final int x, final int y,
            final IntBuffer results) {
        final int stage = Math.max(Math.abs(startX - x), Math.abs(startY - y));
        final int seen = SEEN | (0x000000FF & opacity);
        final int unseen = UNSEEN | (0x000000FF & opacity);

        final double xxDist = Math.abs(startX - x) * xSampleDist;
        final double yyDist = Math.abs(startY - y) * ySampleDist;
        final double angle = Math.toDegrees(Math.atan(yyDist / xxDist));

        final double tanAngle = Math.tan(Math.toRadians(angle));
        float maxSlope = Float.NaN;

        for (int j = 1; j < stage; j++) {

            if (xAxis) {

                //now that we have the angle, get the x value at the given level
                double rise = ySampleDist * j;
                double run = rise / tanAngle;
                int yLevel = startY - j;
                if (y > startY)
                    yLevel = startY + j;

                int diff = (int) Math.round(run / xSampleDist);
                if (x < startX)
                    diff *= -1;
                final int idx = getIndex(startX + diff, yLevel);
                if (showCircle) {
                    double xDist = diff * xSampleDist;
                    double yDist = (yLevel - startY) * ySampleDist;
                    double dist = Math.sqrt(xDist * xDist + yDist * yDist);
                    if (dist > radius) {
                        return;
                    }
                }
                if (Float.isNaN(maxSlope)
                        || slopeGrid[idx] >= maxSlope) {
                    results.put(idx, seen);
                    maxSlope = slopeGrid[idx];
                } else {
                    results.put(idx, unseen);
                }
            } else {

                //now that we have the angle, get the y value at the given level
                double run = xSampleDist * j;
                double rise = run * tanAngle;

                int xLevel = startX + j;
                if (x < startX)
                    xLevel = startX - j;

                int diff = (int) Math.round(rise / ySampleDist);
                if (y < startY)
                    diff *= -1;

                final int idx = getIndex(xLevel, startY + diff);

                if (showCircle) {
                    double xDist = (xLevel - startX) * xSampleDist;
                    double yDist = diff * ySampleDist;
                    double dist = Math.sqrt(xDist * xDist + yDist * yDist);
                    if (dist > radius) {
                        return;
                    }
                }

                if (Float.isNaN(maxSlope)
                        || slopeGrid[idx] >= maxSlope) {
                    results.put(idx, seen);
                    maxSlope = slopeGrid[idx];
                } else {
                    results.put(idx, unseen);
                }

            }
        }

        final int idx = getIndex(x, y);
        if (showCircle) {
            results.put(idx, OUTSIDE_RANGE);
            return;
        }
        if (Float.isNaN(maxSlope) || slopeGrid[idx] >= maxSlope)
            results.put(idx, seen);
        else
            results.put(idx, unseen);

    }

    /**
     * Get the distance between the sampled points 
     * 
    
     * @param toX - the x position of the dest point in the elevation grid
     * @param toY - the y position of the dest point in the elevation grid
     * @return - the distance in meters between the sampled points along the vector from the start point to the end point
     */
    private double getSampleDist(int toX, int toY) {
        double xDist = Math.abs(startX - toX) * xSampleDist;
        double yDist = Math.abs(startY - toY) * ySampleDist;

        if (xDist == 0 || yDist == 0)
            return xDist + yDist;

        return Math.sqrt(xDist * xDist + yDist * yDist);
    }

    @Override
    protected void query(ViewState s, HeatMapParams result) {
        ViewShedState2 state = (ViewShedState2) s;
        if (state.pointOfInterest == null || Double.isNaN(state.radius)) {
            // if point of interest or radius are undefined, transparent fill
            result.xSampleResolution = this.quickResolutionX;
            result.ySampleResolution = this.quickResolutionY;

            if (result.rgbaData == null
                    || result.rgbaData.length < (4 * result.xSampleResolution
                            * result.ySampleResolution))
                result.rgbaData = new byte[(4 * result.xSampleResolution
                        * result.ySampleResolution)];

            Arrays.fill(result.rgbaData, (byte) 0);
        } else {

            double transPercentage = ((ViewShedLayer2) subject).getOpacity()
                    / 100d;
            opacity = (int) Math.round(255d * transPercentage);
            showCircle = ((ViewShedLayer2) subject).getCircle();

            boolean rbgaUpdateNeeded = (result.rgbaData == null
                    || result.rgbaData.length < (4 * result.xSampleResolution
                            * result.ySampleResolution));

            rbgaUpdateNeeded = rbgaUpdateNeeded || radius != calculatedRadius
                    || calculatedPOI == null
                    || calculatedPOI != pointOfInterest;

            if (!rbgaUpdateNeeded && opacity != calculatedOpacity) {
                for (int i = 3; i < result.rgbaData.length; i += 4) {
                    if (result.rgbaData[i] != (byte) 0x00)
                        result.rgbaData[i] = (byte) opacity;
                }
                return;
            }

            // set up some defaults
            result.maxElev = (float) (ConversionFactors.FEET_TO_METERS * 19000);
            result.minElev = (float) (ConversionFactors.FEET_TO_METERS * -900);

            result.numSamples = 0;

            result.drawVersion = state.drawVersion;

            result.xSampleResolution = this.fullResolutionX;
            result.ySampleResolution = this.fullResolutionY;

            // record the ROI that we are querying
            if (state.crossesIDL) {
                int hemi = state.drawLng < 0d ? GeoCalculations.HEMISPHERE_WEST
                        : GeoCalculations.HEMISPHERE_EAST;
                result.upperLeft.set(
                        GeoCalculations.wrapLongitude(state.upperLeft, hemi));
                result.upperRight.set(
                        GeoCalculations.wrapLongitude(state.upperRight, hemi));
                result.lowerRight.set(
                        GeoCalculations.wrapLongitude(state.lowerRight, hemi));
                result.lowerLeft.set(
                        GeoCalculations.wrapLongitude(state.lowerLeft, hemi));
            } else {
                result.upperLeft.set(state.upperLeft);
                result.upperRight.set(state.upperRight);
                result.lowerRight.set(state.lowerRight);
                result.lowerLeft.set(state.lowerLeft);
            }

            final double distance;
            GeoPoint gp1, gp2;

            gp1 = result.lowerLeft;
            gp2 = result.upperRight;

            distance = GeoCalculations.distanceTo(gp1, gp2);
            //            long time = System.currentTimeMillis();

            // obtain the elevation data
            if (result.elevationData == null
                    || result.elevationData.length < (result.xSampleResolution
                            * result.ySampleResolution))
                result.elevationData = new float[(result.xSampleResolution
                        * result.ySampleResolution)];

            if (state.drawMapResolution > 1000)
                Arrays.fill(result.elevationData, Float.NaN);
            else
                queryGridImpl(result, distance);

            //            Log.d("JM", "DTED query finished: " + (System.currentTimeMillis()-time) + "ms");

            // generate the RGBA data from the elevation values

            updateRGBA(state, result);
        }
    }

    /**************************************************************************/

    private class ViewShedState2 extends HeatMapState {
        private GeoPoint pointOfInterest;
        private double radius;

        @Override
        public void set(GLMapView view) {
            super.set(view);

            this.pointOfInterest = GLViewShed2.this.pointOfInterest;
            this.radius = GLViewShed2.this.radius;

            if (this.pointOfInterest != null && !Double.isNaN(radius)) {
                // compute our ROI as the minimum bounding box of the radius
                // around the point of interest
                GeoPoint north = GeoCalculations.pointAtDistance(
                        this.pointOfInterest, 0, radius);
                GeoPoint east = GeoCalculations.pointAtDistance(
                        this.pointOfInterest, 90, radius);
                GeoPoint south = GeoCalculations.pointAtDistance(
                        this.pointOfInterest, 180, radius);
                GeoPoint west = GeoCalculations.pointAtDistance(
                        this.pointOfInterest, 270, radius);

                this.upperLeft.set(north.getLatitude(), west.getLongitude());
                this.upperRight.set(north.getLatitude(), east.getLongitude());
                this.lowerRight.set(south.getLatitude(), east.getLongitude());
                this.lowerLeft.set(south.getLatitude(), west.getLongitude());
            } else {
                this.upperLeft.set(Double.NaN, Double.NaN);
                this.upperRight.set(Double.NaN, Double.NaN);
                this.lowerRight.set(Double.NaN, Double.NaN);
                this.lowerLeft.set(Double.NaN, Double.NaN);
            }
        }

        @Override
        public void copy(ViewState view) {
            super.copy(view);

            final ViewShedState2 state = (ViewShedState2) view;
            this.pointOfInterest = state.pointOfInterest;
            this.radius = state.radius;
        }
    }
}
