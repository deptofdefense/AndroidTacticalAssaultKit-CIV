package com.atakmap.map;

public interface RenderSurface {
    public static interface OnSizeChangedListener {
        public void onSizeChanged(RenderSurface surface, int width, int height);
    }

    public double getDpi();
    public int getWidth();
    public int getHeight();

    public void addOnSizeChangedListener(OnSizeChangedListener l);
    public void removeOnSizeChangedListener(OnSizeChangedListener l);
}
