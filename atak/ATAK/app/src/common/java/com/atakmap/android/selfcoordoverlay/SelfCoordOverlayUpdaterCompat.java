
package com.atakmap.android.selfcoordoverlay;

import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater;

public class SelfCoordOverlayUpdaterCompat {
    public static void change() {
        SelfCoordOverlayUpdater.getInstance().change();
    }

    public static boolean showGPSWidget(boolean show) {
        return SelfCoordOverlayUpdater.getInstance().showGPSWidget(show);
    }
}
