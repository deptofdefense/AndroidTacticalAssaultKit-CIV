
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

public class AlertDialogHelper {

    public static final String TAG = "AlertDialogHelper";

    public static final int STANDARD_MAXIMUM_WIDTH = 2160;
    public static final int STANDARD_MAXIMUM_HEIGHT = 2160;

    /**
     * Helper method for setting the width of an AlertDialog to either be a percentage width of the
     * window size capped by a maximum width.  This needs to be performed
     * on the dialog after it has been shown.
     * @param alertDialog the alert dialog
     * @param percentageWidth the percentage of the screen to use as expressed by a number between 0 and 1
     */
    public static void adjustWidth(final AlertDialog alertDialog,
            double percentageWidth) {
        adjustWidth(alertDialog, percentageWidth, STANDARD_MAXIMUM_WIDTH);
    }

    /**
     * Helper method for setting the width of an AlertDialog to either be a percentage width of the
     * window size capped by a maximum width.  This needs to be performed
     * on the dialog after it has been shown.
     * @param alertDialog the alert dialog
     * @param percentageWidth the percentage of the screen to use as expressed by a number between 0 and 1
     * @param maximumWidth the maximum width that the alert dialog should be no matter what the screen size.
     */
    public static void adjustWidth(final AlertDialog alertDialog,
            double percentageWidth, int maximumWidth) {
        if (percentageWidth > 1)
            percentageWidth = 1;
        else if (percentageWidth < 0)
            percentageWidth = 1;

        Point p = new Point();
        WindowManager wm = (WindowManager) alertDialog.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            display.getSize(p);
        }

        // Copy over the attributes from the displayed window and then set the width
        // to be a percentage of the total window width
        Window w = alertDialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = Math.min((int) (p.x * percentageWidth), maximumWidth);
            w.setAttributes(lp);
        }
    }

    /**
     * Helper method for setting the width of an AlertDialog to either be a percentage width of the
     * window size capped by a maximum width.  This needs to be performed
     * on the dialog after it has been shown.
     * @param alertDialog the alert dialog
     * @param percentageHeight the percentage of the screen to use as expressed by a number between 0 and 1
     */
    public static void adjustHeight(final AlertDialog alertDialog,
            double percentageHeight) {
        adjustHeight(alertDialog, percentageHeight, STANDARD_MAXIMUM_HEIGHT);
    }

    /**
     * Helper method for setting the height of an AlertDialog to either be a percentage width of the
     * window size capped by a maximum width.  This needs to be performed
     * on the dialog after it has been shown.
     * @param alertDialog the alert dialog
     * @param percentageHeight the percentage of the screen to use as expressed by a number between 0 and 1
     * @param maximumHeight the maximum width that the alert dialog should be no matter what the screen size.
     */
    public static void adjustHeight(final AlertDialog alertDialog,
            double percentageHeight, int maximumHeight) {
        if (percentageHeight > 1)
            percentageHeight = 1;
        else if (percentageHeight < 0)
            percentageHeight = 1;

        Point p = new Point();
        WindowManager wm = (WindowManager) alertDialog.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            display.getSize(p);
        }

        // Copy over the attributes from the displayed window and then set the width
        // to be a percentage of the total window width
        Window w = alertDialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.height = Math.min((int) (p.y * percentageHeight), maximumHeight);
            w.setAttributes(lp);
        }
    }

    /**
     * Helper method for setting the width of an AlertDialog to either be a percentage width of the
     * window size capped by a maximum width.  This needs to be performed
     * on the dialog after it has been shown.
     * @param alertDialog the alert dialog
     * @param percentageWidth the percentage of the screen to use as expressed by a number between 0 and 1
     * @param percentageHeight the percentage of the screen to use as expressed by a number between 0 and 1
     */
    public static void adjust(final AlertDialog alertDialog,
            double percentageWidth, double percentageHeight) {
        adjustWidth(alertDialog, percentageWidth, STANDARD_MAXIMUM_WIDTH);
        adjustHeight(alertDialog, percentageHeight, STANDARD_MAXIMUM_HEIGHT);
    }

}
