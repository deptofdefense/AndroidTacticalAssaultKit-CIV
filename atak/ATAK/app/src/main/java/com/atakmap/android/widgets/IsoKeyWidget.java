
package com.atakmap.android.widgets;

import android.os.SystemClock;

import com.atakmap.android.elev.graphics.SharedDataModel;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.opengl.GLText;

public class IsoKeyWidget extends MapWidget2 {

    public static final int NUM_LABELS = 6;

    private long lastTime = 0;
    private float _barWidth, _barHeight;

    /**
     * Set the size of the color bar
     * @param barWidth Bar width in pixels
     * @param barHeight Bar height in pixels
     */
    public void setBarSize(float barWidth, float barHeight) {
        if (Float.compare(barWidth, _barWidth) != 0
                || Float.compare(barHeight, _barHeight) != 0) {
            _barWidth = barWidth;
            _barHeight = barHeight;
            recalcSize();
        }
    }

    public float[] getBarSize() {
        return new float[] {
                _barWidth, _barHeight
        };
    }

    @Override
    public boolean setPadding(float left, float top, float right,
            float bottom) {
        // Only left and bottom are used for inner padding of labels
        if (Float.compare(left, _padding[LEFT]) != 0
                || Float.compare(bottom, _padding[BOTTOM]) != 0) {
            _padding[LEFT] = left;
            _padding[BOTTOM] = top;
            recalcSize();
            return true;
        }
        return false;
    }

    @Override
    public void orientationChanged() {
        recalcSize();
    }

    protected void recalcSize() {
        float width = _barWidth, height = _barHeight;
        MapTextFormat mtf = MapView.getDefaultTextFormat();

        double minHeat, maxHeat;
        if (SharedDataModel.getInstance().isoDisplayMode.equals(
                SharedDataModel.ABSOLUTE)) {
            minHeat = SharedDataModel.isoScaleStart;
            maxHeat = SharedDataModel.getInstance().maxHeat;
        } else {
            minHeat = SharedDataModel.getInstance().minHeat;
            maxHeat = SharedDataModel.getInstance().maxHeat;
        }

        float spacing = mtf.getBaselineSpacing();
        if (Double.compare(minHeat, GeoPoint.UNKNOWN) != 0 &&
                Double.compare(maxHeat, GeoPoint.UNKNOWN) != 0) {
            // Calculate max label bounds
            double curAlt = minHeat;
            double incAlt = (maxHeat - minHeat) / (NUM_LABELS - 1);
            int maxWidth = 0;
            for (int j = 0; j < NUM_LABELS; j++) {
                String text = GLText.localize(String.valueOf((int) SpanUtilities
                        .convert(curAlt, Span.METER, Span.FOOT)));
                maxWidth = Math.max(maxWidth, mtf.measureTextWidth(text));
                curAlt += incAlt;
            }
            double topLabelY = Math.ceil(_barHeight / 1.125) + spacing;
            height = (float) Math.max(height, topLabelY);
            width += _padding[LEFT] + maxWidth;
        }

        // Include size of top labels
        String[] topLabels = getTopLabels();
        width = Math.max(width, mtf.measureTextWidth(topLabels[0]));
        width = Math.max(width, mtf.measureTextWidth(topLabels[1]));
        height += _padding[BOTTOM] + spacing;
        if (!FileSystemUtils.isEmpty(topLabels[1]))
            height += spacing;

        super.setSize(width, height);
    }

    public static String[] getTopLabels() {
        String[] ret = new String[] {
                "", ""
        };
        String mode = SharedDataModel.getInstance().isoDisplayMode;
        ret[0] = GLText.localize(mode);
        if (!SharedDataModel.isoCalculating
                && !mode.equals(SharedDataModel.HIDE)) {
            ret[1] = "(ft msl)";
        } else if (SharedDataModel.isoCalculating) {
            String progress = String
                    .valueOf((int) (SharedDataModel.isoProgress
                            / (SharedDataModel.isoScaleMarks * 2f + 3f)
                            * 100f));
            if (mode.equals(SharedDataModel.VISIBLE))
                ret[1] = progress + "%";
        }
        return ret;
    }

    @Override
    public boolean testHit(float x, float y) {
        if (!SharedDataModel.isoCalculating && super.testHit(x, y)) {
            long currentTime = SystemClock.elapsedRealtime();
            if ((currentTime - lastTime) > 600) {
                if (SharedDataModel.getInstance().isoDisplayMode == null) {
                    SharedDataModel
                            .getInstance().isoDisplayMode = SharedDataModel.RELATIVE;
                } else {
                    SharedDataModel
                            .getInstance().isoDisplayMode = SharedDataModel
                                    .next(SharedDataModel
                                            .getInstance().isoDisplayMode);
                }
                lastTime = currentTime;
                recalcSize();
            }
        }
        return false;
    }
}
