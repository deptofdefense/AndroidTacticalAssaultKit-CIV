
package com.atakmap.android.routes;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * This class allows Route Planner Implementations to register their own UI for specifying options
 * that are relevant to them.
 */
public class RoutePlannerOptionsView extends LinearLayout {

    public RoutePlannerOptionsView(Context context) {
        super(context);
    }

    public RoutePlannerOptionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RoutePlannerOptionsView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }
}
