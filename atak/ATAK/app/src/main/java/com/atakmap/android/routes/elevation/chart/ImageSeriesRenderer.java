
package com.atakmap.android.routes.elevation.chart;

import android.graphics.Bitmap;

public class ImageSeriesRenderer {
    private Bitmap _image;
    private double _xOff;
    private double _yOff;

    public void setImage(Bitmap image) {
        this._image = image;
    }

    public void setCenter(double x, double y) {
        this._xOff = x;
        this._yOff = y;
    }

    public Bitmap getImage() {
        return _image;
    }

    public double getxOff() {
        return _xOff;
    }

    public void setxOff(int xOff) {
        this._xOff = xOff;
    }

    public double getyOff() {
        return _yOff;
    }

    public void setyOff(int yOff) {
        this._yOff = yOff;
    }
}
