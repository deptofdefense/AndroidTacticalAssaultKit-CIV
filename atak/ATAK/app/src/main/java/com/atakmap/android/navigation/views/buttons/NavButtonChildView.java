
package com.atakmap.android.navigation.views.buttons;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.app.R;

/**
 * Toolbar shown below the nav button layout
 */
public class NavButtonChildView extends LinearLayout {

    private static final int MEASURE = View.MeasureSpec.makeMeasureSpec(0,
            View.MeasureSpec.UNSPECIFIED);
    private static final int HANDLE_MARGIN = 3;

    private final AtakPreferences _prefs;
    private LinearLayout _container;
    private View _anchor;
    private ImageView _arrow;
    private ImageView _handle;
    private boolean _horizontal = true;

    public NavButtonChildView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        _prefs = new AtakPreferences(context);
        _container = this;
    }

    public void layoutForToolbarView(View anchor, ActionBarView actionBarView) {
        removeAllViews();
        layoutHorizontal(anchor, actionBarView);
    }

    public void layoutForButton(NavButton button, NavView nav,
            boolean isHorizontal) {
        removeAllViews();
        if (isHorizontal)
            layoutHorizontal(button, nav);
        else
            layoutVertical(button, nav);
    }

    /**
     * Reposition the child view based on its existing anchor
     */
    public void reposition() {
        if (_anchor != null) {
            if (_horizontal)
                positionHorizontal(_anchor);
            else
                configureForVerticalLayout(_anchor);
        }
    }

    private void layoutHorizontal(View anchor, ActionBarView actionBarView) {
        configureForHorizontalLayout();
        actionBarView.setGravity(Gravity.CENTER_VERTICAL);
        actionBarView.setOrientation(HORIZONTAL);
        addViewsFromActionBarView(actionBarView, true);
        positionHorizontal(anchor);
    }

    private void layoutHorizontal(NavButton button, NavView nav) {
        configureForHorizontalLayout();

        for (NavButtonModel child : button.getModel().getChildButtons()) {
            NavButton childButton = new NavButton(this.getContext());
            childButton.setModel(child);
            childButton.setBackgroundColor(Color.GREEN);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    (int) getResources().getDimension(R.dimen.nav_button_size),
                    (int) getResources().getDimension(R.dimen.nav_button_size));
            buttonParams.leftMargin = (int) getResources()
                    .getDimension(R.dimen.nav_child_padding);

            childButton.setOnClickListener(nav);
            childButton.setOnLongClickListener(nav);
            _container.addView(childButton);
        }
    }

    private void layoutVertical(NavButton button, NavView nav) {
        configureForVerticalLayout(button);

        for (NavButtonModel child : button.getModel().getChildButtons()) {
            NavButton childButton = new NavButton(this.getContext());
            childButton.setModel(child);

            int btnSize = (int) getResources().getDimension(
                    R.dimen.nav_child_button_size);
            LayoutParams buttonParams = new LayoutParams(btnSize, btnSize);
            buttonParams.topMargin = (int) getResources()
                    .getDimension(R.dimen.nav_child_padding);
            childButton.setLayoutParams(buttonParams);
            childButton.setPadding(0, 0, 0, 0);

            childButton.setOnClickListener(nav);
            childButton.setOnLongClickListener(nav);
            _container.addView(childButton);
        }
    }

    private void configureForHorizontalLayout() {
        int bgColor = NavView.getInstance().getUserIconShadowColor();
        _arrow = new ImageView(getContext());
        _arrow.setImageDrawable(colorDrawable(
                R.drawable.ic_nav_child_arrow_vertical, bgColor));
        _arrow.setVisibility(View.GONE);
        this.addView(_arrow);

        _container = new LinearLayout(getContext());
        _container.setOrientation(HORIZONTAL);
        _container.setGravity(Gravity.CENTER_VERTICAL);
        _container.setBackground(colorDrawable(
                R.drawable.toolbar_container_rounded, bgColor));
        _container.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        this.addView(_container);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);

        this.setLayoutParams(params);
    }

    private void configureForVerticalLayout(View anchor) {
        _anchor = anchor;
        _horizontal = false;
        int bgColor = NavView.getInstance().getUserIconShadowColor();
        setBackground(colorDrawable(R.drawable.ic_nav_child_dropdown_vertical,
                bgColor));
        int padding = (int) getResources()
                .getDimension(R.dimen.nav_child_padding);
        int pointerPadding = (int) getResources()
                .getDimension(R.dimen.nav_child_pointer_padding);
        this.setPadding(padding, pointerPadding, padding, padding);
        this.setGravity(Gravity.CENTER_HORIZONTAL);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);

        // Put the view below the anchor
        if (anchor.getParent() instanceof NavView) {
            // If both views have the same RelativeLayout parent we can utilize
            // layout rules
            params.addRule(RelativeLayout.BELOW, anchor.getId());
            params.addRule(RelativeLayout.ALIGN_START, anchor.getId());
            params.setMarginStart((int) getResources()
                    .getDimension(R.dimen.button_marker_padding) * -1);
        } else {
            // Otherwise we need to get the screen position and offset accordingly
            int[] pt = new int[2];
            anchor.getLocationInWindow(pt);
            pt[1] += anchor.getHeight() / 2;
            params.setMargins(pt[0], pt[1], 0, 0);
        }

        this.setLayoutParams(params);
    }

    private void addViewsFromActionBarView(ActionBarView actionBarView,
            boolean isHorizontal) {
        int iconColor = NavView.getInstance().getUserIconColor();
        if (isHorizontal && (actionBarView.showCloseButton()
                && actionBarView.findViewById(R.id.close) == null)) {
            int size = getResources().getDimensionPixelSize(
                    R.dimen.nav_child_button_size);
            int padding = getResources().getDimensionPixelSize(
                    R.dimen.nav_child_padding);
            ImageView closeButton = new ImageView(getContext(), null,
                    R.style.ActionBarViewImageButton);
            closeButton.setImageDrawable(colorDrawable(
                    R.drawable.ic_close, iconColor));
            closeButton.setPadding(padding, padding, padding, padding);
            closeButton.setLayoutParams(new LayoutParams(size, size));
            closeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // close child menu
                    Intent i = new Intent(
                            "com.atakmap.android.maps.toolbar.UNSET_TOOLBAR");
                    AtakBroadcast.getInstance().sendBroadcast(i);
                }
            });

            _container.addView(closeButton);
        }

        // Remove from existing view
        ViewParent parent = actionBarView.getParent();
        if (parent instanceof ViewGroup)
            ((ViewGroup) parent).removeView(actionBarView);

        // Color the buttons by the user preferred icon color
        for (int i = 0; i < actionBarView.getChildCount(); i++) {
            View v = actionBarView.getChildAt(i);
            if (v instanceof ImageView)
                ((ImageView) v).setColorFilter(iconColor,
                        PorterDuff.Mode.MULTIPLY);
        }

        // Add the action bar
        _container.addView(actionBarView);

        // Handle
        if (isHorizontal && false) {
            _handle = new ImageView(getContext());
            _handle.setImageResource(R.drawable.ic_menu_handle);
            _handle.setScaleX(1.3f);
            LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.MATCH_PARENT);
            params.setMarginEnd(HANDLE_MARGIN);
            _handle.setLayoutParams(params);
            _container.addView(_handle);
        }
    }

    /**
     * Position the horizontal toolbar after the views have been added to it
     * @param anchor Position anchor
     */
    private void positionHorizontal(View anchor) {
        _anchor = anchor;
        _horizontal = true;
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) this
                .getLayoutParams();

        // Default margin
        int margin = getResources().getDimensionPixelSize(R.dimen.auto_margin);

        // Get the width of the map
        final MapView mapView = MapView.getMapView();
        final NavView nav = NavView.getInstance();
        int mapWidth = mapView != null ? mapView.getWidth() : nav.getWidth();

        // Current view settings
        final boolean alignRight = _prefs.get(
                NavView.PREF_NAV_ORIENTATION_RIGHT, true);
        final boolean buttonsVisible = nav.buttonsVisible();
        final View menuBtn = nav.findViewById(R.id.tak_nav_menu_button);
        final View sideLayout = nav.findViewById(R.id.side_layout);

        // Use the menu button as an anchor if the anchor isn't visible
        if (anchor.getVisibility() != View.VISIBLE && menuBtn != null)
            anchor = menuBtn;

        // Get the screen position and offset accordingly
        int anchorX = anchor.getLeft();
        int anchorY = anchor.getTop();
        int anchorWidth = anchor.getWidth();
        int anchorHeight = anchor.getHeight();

        // Need to get the absolute screen position if the view isn't part
        // of the NavView
        if (anchor.getParent() != nav) {
            int[] anchorPt = new int[2];
            int[] navPt = new int[2];
            anchor.getLocationInWindow(anchorPt);
            nav.getLocationInWindow(navPt);
            anchorX = anchorPt[0] - navPt[0];
            anchorY = anchorPt[1] - navPt[1];
        }

        // Hide the arrow by default
        if (_arrow != null)
            _arrow.setVisibility(View.GONE);

        // If the anchor is a nav button then we have special behavior
        if (anchor instanceof NavButton && _arrow != null) {

            // Measure the toolbar and related views
            this.measure(MEASURE, MEASURE);
            _arrow.measure(MEASURE, MEASURE);
            int width = getMeasuredWidth();
            int arrowWidth = _arrow.getMeasuredWidth();

            // Offset position by anchor height
            anchorY += anchorHeight;

            // Measure the handle (if applicable)
            if (_handle != null)
                _handle.measure(MEASURE, MEASURE);
            int hWidth = _handle == null ? 0
                    : _handle.getMeasuredWidth() + HANDLE_MARGIN;

            // Get the position of the menu button which is the main anchor
            int left = 0;
            if (menuBtn != null) {
                left = menuBtn.getLeft();
                if (alignRight)
                    left = Math.min(left - width, mapWidth - (width + margin));
                else
                    left += menuBtn.getWidth();
            }

            // Determine the position of the toolbar and the arrow
            int right = (left + width) - hWidth;
            int arrowLeft = anchorX + (anchor.getWidth() - arrowWidth) / 2;
            int arrowRight = arrowLeft + arrowWidth;

            // If the arrow is moved further to the right than the toolbar
            // then offset the toolbar enough so the arrow remains on top
            boolean arrowVisible = true;
            if (!alignRight && arrowRight > right) {
                // Make sure the toolbar isn't pushed off the screen
                // If the arrow can't fit then just don't show it
                int newLeft = left + (arrowRight - right);
                if (newLeft + width > mapWidth)
                    arrowVisible = false;
                else
                    left = newLeft;
            } else if (alignRight && arrowLeft < left) {
                // Make sure we're not intersecting the left-side buttons
                if (arrowLeft < sideLayout.getRight()) {
                    left = sideLayout.getRight() + margin;
                    arrowVisible = false;
                } else
                    left = arrowLeft;
            }

            // Set the toolbar position
            params.setMargins(left, anchorY, 0, 0);

            // Set the arrow position
            if (arrowVisible) {
                // Put the arrow under the corresponding button
                LayoutParams llp = new LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT);
                llp.setMargins(arrowLeft - left, 0, 0, 0);
                _arrow.setLayoutParams(llp);
                _arrow.setVisibility(View.VISIBLE);
            }

            // Update the rounded rectangle background so there's no gap
            // between the arrow and the the rounded corner it's attached to
            int bgColor = NavView.getInstance().getUserIconShadowColor();
            int tlr = arrowVisible && arrowLeft < left + margin ? 0 : margin;
            int trr = arrowVisible && arrowRight > right - margin ? 0 : margin;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColor((bgColor & 0xFFFFFF) | 0x80000000);
            bg.setCornerRadii(new float[] {
                    tlr, tlr,
                    trr, trr,
                    margin, margin,
                    margin, margin
            });
            _container.setBackground(bg);

        } else if (anchor == menuBtn) {
            // Anchor is the overflow menu button
            anchorY += margin;
            if (buttonsVisible)
                anchorY += anchorHeight;
            if (alignRight) {
                this.measure(MEASURE, MEASURE);
                int right = Math.min(mapWidth, anchorX);
                anchorX = right - (getMeasuredWidth() + margin);
            } else
                anchorX += anchorWidth + margin;
            params.setMargins(anchorX, anchorY, 0, 0);
        } else {
            // Default behavior
            if (anchorWidth > anchorHeight)
                params.setMargins(anchorX, anchorY + anchorHeight + margin, 0,
                        0);
            else
                params.setMargins(anchorX + anchorWidth + margin, anchorY, 0,
                        0);
        }
        this.setLayoutParams(params);
    }

    private Drawable colorDrawable(int drawableId, int color) {
        Drawable dr = getContext().getDrawable(drawableId).mutate();
        dr.setColorFilter(new PorterDuffColorFilter(color,
                PorterDuff.Mode.MULTIPLY));
        return dr;
    }
}
