package com.atakmap.map;

import android.view.MotionEvent;

public interface MapTouchHandler {
    public boolean onTouch(AtakMapView view, MotionEvent event);
}
