
package com.atakmap.map.opengl;

import android.content.Context;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.map.RenderSurface;

public abstract  class GLMapSurface implements RenderContext, RenderSurface {
    private static Thread mainGLThread = null;

    public static boolean SETTING_displayLabels = true;
    public static boolean SETTING_shortenLabels = false;
    public static boolean SETTING_enableTextureTargetFBO = true;

    public abstract MapRenderer getGLMapView();
    public abstract AtakMapView getMapView();

    public abstract Context getContext();

    public static void setMainGLThread() {
        mainGLThread = Thread.currentThread();
    }

    public static boolean isGLThread() {
        return Thread.currentThread() == mainGLThread;
    }
}
