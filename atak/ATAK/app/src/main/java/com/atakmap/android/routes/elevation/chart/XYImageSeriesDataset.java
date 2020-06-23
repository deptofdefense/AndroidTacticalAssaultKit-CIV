
package com.atakmap.android.routes.elevation.chart;

import org.achartengine.model.XYSeries;

import java.util.Vector;

public class XYImageSeriesDataset {
    private final Vector<XYSeries> series = new Vector<>();

    public synchronized void addSeries(XYSeries series) {
        this.series.add(series);
    }

    public synchronized int getSeriesCount() {
        return this.series.size();
    }

    public synchronized XYSeries[] getSeries() {
        return this.series.toArray(new XYSeries[0]);
    }

    public synchronized XYSeries getSeriesByTitle(String title) {
        for (int i = 0; i < series.size(); i++) {
            if (series.get(i).getTitle().equals(title))
                return series.get(i);
        }
        XYSeries newSeries = new XYSeries(title);
        series.add(newSeries);
        return newSeries;
    }
}
