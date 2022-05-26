
package com.atakmap.android.util;

import android.content.Context;
import android.util.DisplayMetrics;

public class ScreenUtils {
    public static float convertPixelsToDp(float px, Context context) {
        return px
                / ((float) context.getResources().getDisplayMetrics().densityDpi
                        / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static float convertDpToPixels(float dp, Context context) {
        return dp
                * ((float) context.getResources().getDisplayMetrics().densityDpi
                        / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static float getScreenRatio(Context context) {
        DisplayMetrics displayMetrics = context.getResources()
                .getDisplayMetrics();
        if (displayMetrics.widthPixels > displayMetrics.heightPixels) {
            return (float) displayMetrics.widthPixels
                    / (float) displayMetrics.heightPixels;
        }
        return (float) displayMetrics.heightPixels
                / (float) displayMetrics.widthPixels;

    }
}
