
package com.atakmap.android.location;

public class AlphaBetaFilter {

    private final static String TAG = "AlphaBetaFilter";

    private final double _alpha;
    private final double _beta;

    private double _delta;
    private double _estimate, _velocity;

    /**
     * Simple implementation of a alpha-beta filter to smooth data coming primarily from the system sensors
     * @param a the alpha used to correct position estimate
     * @param b the beta used to correct the velocity estimate
     */
    public AlphaBetaFilter(final double a, final double b) {
        _alpha = a;
        _beta = b;
    }

    /**
     * Set the filter back to an initial value.
     * @param initial the value
     */
    public void reset(final double initial) {
        _estimate = Double.isNaN(initial) ? 0 : initial;
        _velocity = 0d;
    }

    /**
     * Produce the next value based on a measurement and a delta.
     * @param measurement the measurement
     * @param delta the delta
     */
    private void _update(final double measurement, final double delta) {
        double oldr = measurement - _estimate;
        double e = _estimate + (_velocity * delta);
        double v = _velocity;
        double r = measurement - e;

        if (r > 180d) {
            e += 360d;
            oldr -= 360d;
            r = measurement - e;
        } else if (r < -180d) {
            e -= 360d;
            oldr += 360d;
            r = measurement - e;
        }

        // Don't allow overshoot. Not sure if this is the best filter but seems
        // to work alright.
        double newr = measurement - (e + _alpha * r);
        if (Math.signum(oldr) != Math.signum(newr)) {
            e = measurement;
            v = 0;
        } else {
            e += _alpha * r;
            v += (_beta * r) / delta;
        }

        _estimate = e;
        _velocity = v;
    }

    /**
     * Produce the next value based on a measurement and a delta.
     * @param measurement the measument
     * @param delta the delta
     * @param granularity the suggestive granularity for the values in the feedback loop
     */
    public void update(final double measurement, final double delta,
            final double granularity) {
        final boolean ignore = (Double.isNaN(measurement) || Double
                .isNaN(granularity));
        //if (ignore) {
        //    Log.d(TAG, "ignoring update measurement " + measurement + " delta "
        //            + granularity);
        //}

        _delta += delta;
        while (_delta > granularity) {
            if (!ignore)
                _update(measurement, granularity);
            _delta -= granularity;
        }
    }

    /**
     * Gets the estimated value based on iterative runs of the filter.
     * @return the estimate
     */
    public double getEstimate() {
        return _estimate;
    }

}
