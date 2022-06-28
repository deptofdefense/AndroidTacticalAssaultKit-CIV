
package com.atakmap.android.navigation.views.buttons;

import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.app.R;

/**
 * The zoom in/out button displayed on the left side of the screen
 */
public class NavZoomButton extends NavButton implements View.OnClickListener {

    private float touchY = -1;

    public NavZoomButton(Context context) {
        super(context);
    }

    public NavZoomButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NavZoomButton(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.position = NavButtonModel.LEFT;
    }

    @Override
    protected void updateState() {
        if (isInEditMode() || model == null)
            return;

        boolean dragging = dragInProgress && !dragEntered;

        Context c = getContext();

        Drawable dr = model.getImage();
        if (dr != null) {
            NavButtonDrawable navDr = null;
            if (getDrawable() instanceof NavButtonDrawable)
                navDr = (NavButtonDrawable) getDrawable();
            if (navDr == null || navDr.getBaseDrawable() != dr)
                navDr = new NavButtonDrawable(c, dr);
            navDr.setColor(this.defaultIconColor);
            navDr.setShadowColor(this.shadowColor);
            navDr.setShadowRadius(this.shadowEnabled ? 16 : 0);
            dr = navDr;
        }
        setImageDrawable(dr);

        // Set the background when we're editing
        Drawable bg = null;
        if (dragging)
            bg = c.getDrawable(R.drawable.nav_button_drag_background);
        else if (editing)
            bg = c.getDrawable(R.drawable.nav_zoom_edit);

        // Apply the user icon color to the background
        if (bg != null) {
            bg = bg.mutate();
            bg.setColorFilter(defaultIconColor, PorterDuff.Mode.MULTIPLY);
        }

        setBackground(bg);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Track which section of the button was tapped
        if (event.getAction() == MotionEvent.ACTION_UP)
            touchY = event.getY();

        return super.onTouchEvent(event);
    }

    @Override
    public void onClick(View v) {
        // Zoom in or out depending on which section of the button was pressed
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                "com.atakmap.android.map.ZOOM_"
                        + (touchY >= getHeight() / 2f ? "OUT" : "IN")));
    }
}
