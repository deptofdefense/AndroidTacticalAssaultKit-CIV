package com.atakmap.map;

public interface AtakMapController {
    interface OnFocusPointChangedListener {
        void onFocusPointChanged(float x, float y);
    }

    public float getFocusX();
    public float getFocusY();
}
