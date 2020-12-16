
package com.atakmap.android.widgets;

import com.atakmap.android.maps.graphics.widgets.GLMapFocusTextWidget;

/**
 * Text widget that is updated whenever the map focus point changes
 * The text updates are done within {@link GLMapFocusTextWidget}
 * Do not call {@link #setText(String)} on this widget.
 */
public class MapFocusTextWidget extends TextWidget {

    public MapFocusTextWidget() {
        super("", 2);
    }
}
