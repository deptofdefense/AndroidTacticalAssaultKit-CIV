
package com.atakmap.android.mapcompass;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.view.MotionEvent;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.AbstractParentWidget;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.math.MathUtils;

// TODO:     ATAK-10521 DeX - Would like two finger click operations for external monitor
// Possibly fix the TextWidget display under the compass with having a similiar set of states a
//     a view - VISIBLE, GONE, INVISIBLE   instead of setVisibility ?
//

public class SliderWidget extends LayoutWidget implements
        MapWidget2.OnWidgetSizeChangedListener {

    private static final int xIconAnchor = 0;
    private static final int yIconAnchor = 0;

    private static final int xSizeSmall = 18; // Icon.SIZE_DEFAULT;
    private static final int ySizeSmall = 150; // Icon.SIZE_DEFAULT;

    private static final int xSize = 24; // Icon.SIZE_DEFAULT;
    private static final int ySize = 200; // Icon.SIZE_DEFAULT;

    // use a smaller compass if the screen is small
    private static final int smallScreenWidth = 480;

    private final MapView _mapView;
    private final boolean _smallScreen;
    private final float _maxHeight;
    private final MarkerIconWidget _nub;
    private final MarkerIconWidget _slider;

    // Slider value from 0.0 to 1.0
    private float _slideValue = 0f;

    public SliderWidget(MapView mapView) {
        _mapView = mapView;
        Context context = mapView.getContext();
        Point p = new Point();
        ((Activity) context).getWindowManager().getDefaultDisplay().getSize(p);
        _smallScreen = p.x <= smallScreenWidth;

        _nub = new MarkerIconWidget();
        _nub.setIcon(createIcon(R.drawable.vertical_slider_nub, true));

        _slider = new MarkerIconWidget();
        _slider.setIcon(createIcon(R.drawable.vertical_slider, false));

        setWidth(Math.max(_slider.getWidth(), _nub.getWidth()));
        _maxHeight = _slider.getHeight();

        setPadding(32f, 16f, 32f, 16f);
        addWidget(_slider);
        addWidget(_nub);
    }

    @Override
    public void setParent(AbstractParentWidget parent) {
        AbstractParentWidget oldParent = getParent();
        if (oldParent != null)
            oldParent.removeOnWidgetSizeChangedListener(this);
        super.setParent(parent);
        if (parent != null) {
            parent.addOnWidgetSizeChangedListener(this);
            onWidgetSizeChanged(parent);
        }
    }

    @Override
    public void onWidgetSizeChanged(final MapWidget2 parent) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                setHeight(Math.min(_maxHeight,
                        parent.getHeight() - _padding[TOP] - _padding[BOTTOM]));
            }
        });
    }

    @Override
    public boolean setHeight(float height) {
        if (super.setHeight(height)) {
            _slider.setIconSizePx(_slider.getWidth(), height);
            setSliderValue(_slideValue);
            return true;
        }
        return false;
    }

    @Override
    public MapWidget seekHit(MotionEvent event, float x, float y) {
        return isVisible() && testHit(x, y) ? this : null;
    }

    @Override
    public void setPoint(float x, float y) {
        super.setPoint(x, y);
    }

    public void setSliderValue(float value) {
        _slideValue = MathUtils.clamp(value, 0, 1);
        _nub.setPoint(0f, _slideValue * getSliderMax());
    }

    public float getSliderMax() {
        return _slider.getHeight() - _nub.getHeight();
    }

    private Icon createIcon(int iconID, boolean square) {
        Icon.Builder b = new Icon.Builder();
        b.setAnchor(xIconAnchor, yIconAnchor);
        b.setColor(Icon.STATE_DEFAULT, Color.WHITE);

        if (_smallScreen)
            b.setSize(xSizeSmall, square ? xSizeSmall : ySizeSmall);
        else
            b.setSize(xSize, square ? xSize : ySize);

        b.setImageUri(Icon.STATE_DEFAULT, ATAKUtilities.getResourceUri(iconID));

        return b.build();
    }
}
