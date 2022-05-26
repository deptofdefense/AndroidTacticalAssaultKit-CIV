
package com.atakmap.android.navigation.views.buttons;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.app.R;

/**
 * Toolbar shown below the nav button layout
 */
public class NavButtonChildView extends LinearLayout {

    private static final int MEASURE = View.MeasureSpec.makeMeasureSpec(0,
            View.MeasureSpec.UNSPECIFIED);
    private static final int HANDLE_MARGIN = 3;

    private LinearLayout _container;
    private ImageView _arrow;
    private ImageView _handle;

    public NavButtonChildView(Context context) {
        super(context);
        setOrientation(VERTICAL);
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
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) this
                .getLayoutParams();

        // Put the view below the anchor
        if (anchor.getParent() instanceof NavView) {
            NavView navView = (NavView) anchor.getParent();

            // If both views have the same RelativeLayout parent we can utilize
            // layout rules
            params.addRule(RelativeLayout.RIGHT_OF, anchor.getId());
            params.addRule(RelativeLayout.ALIGN_TOP, anchor.getId());
            params.setMargins(12, 12, 0, 0);

            if (navView.buttonsVisible())
                params.addRule(RelativeLayout.ALIGN_TOP, anchor.getId());
            else
                params.addRule(RelativeLayout.ALIGN_TOP,
                        RelativeLayout.ALIGN_PARENT_TOP);

        } else {
            // Otherwise we need to get the screen position and offset accordingly
            int[] anchorPt = new int[2];
            anchor.getLocationInWindow(anchorPt);
            anchorPt[1] += anchor.getHeight() / 2;

            // If the anchor is a nav button then we have special behavior
            if (anchor instanceof NavButton && _arrow != null) {
                // Measure the toolbar and related views
                this.measure(MEASURE, MEASURE);
                _arrow.measure(MEASURE, MEASURE);

                int width = getMeasuredWidth();
                int arrowWidth = _arrow.getMeasuredWidth();

                if (_handle != null)
                    _handle.measure(MEASURE, MEASURE);

                int hWidth = _handle == null ? 0
                        : _handle.getMeasuredWidth() + HANDLE_MARGIN;

                // Get the position of the parent layout
                int[] parentPt = new int[2];
                ViewGroup parent = (ViewGroup) anchor.getParent();
                parent.getLocationInWindow(parentPt);

                // Determine the position of the toolbar and the arrow
                int left = parentPt[0];
                int right = (left + width) - hWidth;
                int arrowLeft = anchorPt[0]
                        + (anchor.getWidth() - arrowWidth) / 2;
                int arrowRight = arrowLeft + arrowWidth;

                if (anchor.getVisibility() == View.VISIBLE) {
                    // If the arrow is moved further to the right than the toolbar
                    // then offset the toolbar enough so the arrow remains on top
                    if (arrowRight > right)
                        left += arrowRight - right;

                    params.setMargins(left, anchorPt[1], 0, 0);
                    // Put the arrow under the corresponding button
                    LayoutParams llp = new LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT);
                    llp.setMargins(arrowLeft - left, 0, 0, 0);
                    _arrow.setLayoutParams(llp);
                    _arrow.setVisibility(View.VISIBLE);
                } else {
                    // toolbar hidden so stick to top
                    _arrow.setVisibility(GONE);
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    left += left * .25;
                    params.setMargins(left, 12, 0, 0);
                }
            } else {
                // Default behavior
                anchorPt[0] -= anchor.getWidth() / 2;
                params.setMargins(anchorPt[0], anchorPt[1], 0, 0);
            }
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
