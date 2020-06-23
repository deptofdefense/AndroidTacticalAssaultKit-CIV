
package com.atakmap.android.routes.elevation.chart;

import java.util.ArrayList;
import java.util.List;

public class XYImageSeriesRenderer {
    final List<ImageSeriesRenderer> rendlist = new ArrayList<>();
    private int[] margins;

    public synchronized void addSeriesRenderer(ImageSeriesRenderer renderer) {
        rendlist.add(renderer);
    }

    public synchronized void removeAllRenderers() {
        rendlist.clear();
    }

    public synchronized ImageSeriesRenderer getSeriesRendererAt(int index) {
        return rendlist.get(index);
    }

    public synchronized int getSeriesRendererCount() {
        return rendlist.size();
    }

    public void setMargins(int[] margins) {
        this.margins = margins;
    }

    public int[] getMargins() {
        return margins;
    }
}
