
package com.atakmap.android.cotdetails;

import android.widget.LinearLayout;
import android.content.Context;
import android.util.AttributeSet;

import com.atakmap.android.maps.PointMapItem;

abstract public class ExtendedInfoView extends LinearLayout {

    public ExtendedInfoView(Context context) {
        super(context);
    }

    public ExtendedInfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExtendedInfoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * The marker as passed down from the CoTInfoView on changes.
     */
    abstract public void setMarker(PointMapItem m);

}
