
package com.atakmap.android.navigation.views.buttons;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.app.R;

/**
 * Shadow builder for tools that are dragged into the active loadout
 */
public class NavButtonShadowBuilder extends View.DragShadowBuilder {

    private final Drawable _icon;
    private final int _color;
    private int _width, _height;

    public NavButtonShadowBuilder(ImageView view, NavButtonModel mdl) {
        super(view);
        _icon = mdl.isSelected() ? mdl.getSelectedImage() : mdl.getImage();
        _color = mdl.isSelected() ? view.getResources().getColor(R.color.maize)
                : NavView.getInstance().getUserIconColor();
        float size = view.getResources().getDimension(R.dimen.nav_button_size);
        _width = (int) size;
        _height = (int) size;

        // Maintain the aspect ratio of the icon
        if (_icon != null) {
            Rect bounds = _icon.getBounds();
            float ar = (float) bounds.width() / bounds.height();
            if (ar > 1)
                _width *= ar;
            else
                _height /= ar;
        }
    }

    public NavButtonShadowBuilder(NavButton button) {
        this(button, button.getModel());
    }

    @Override
    public void onProvideShadowMetrics(Point outShadowSize,
            Point outShadowTouchPoint) {
        outShadowSize.set(_width, _height);
        outShadowTouchPoint.set(_width / 2, _height / 2);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        if (_icon != null) {
            Rect bounds = new Rect(_icon.getBounds());
            ColorFilter color = _icon.getColorFilter();
            _icon.setBounds(0, 0, _width, _height);
            _icon.setColorFilter(new PorterDuffColorFilter(_color,
                    PorterDuff.Mode.SRC_ATOP));
            _icon.draw(canvas);
            _icon.setColorFilter(color);
            _icon.setBounds(bounds);
        } else
            super.onDrawShadow(canvas);
    }
}
