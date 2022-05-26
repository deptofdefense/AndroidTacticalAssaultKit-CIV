package gov.tak.api.engine.map;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface RenderSurface {
    interface OnSizeChangedListener {
        void onSizeChanged(RenderSurface surface, int width, int height);
    }

    double getDpi();
    int getWidth();
    int getHeight();

    void addOnSizeChangedListener(OnSizeChangedListener l);
    void removeOnSizeChangedListener(OnSizeChangedListener l);
}
