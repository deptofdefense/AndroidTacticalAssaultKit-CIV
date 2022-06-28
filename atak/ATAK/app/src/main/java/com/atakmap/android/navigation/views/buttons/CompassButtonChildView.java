
package com.atakmap.android.navigation.views.buttons;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.atakmap.android.mapcompass.CompassArrowMapComponent;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.app.R;

import androidx.annotation.Nullable;

/**
 * Menu that is shown when the compass is long-pressed.
 * Contains controls to enable/disable 3d & map rotation
 */
public class CompassButtonChildView extends LinearLayout {

    private ImageView arrow;
    private LinearLayout buttons;
    private NavButton toggleTilt;
    private NavButton lockTilt;
    private NavButton toggleRotation;
    private NavButton lockRotation;
    private NavButtonModel rotationMdl;
    private NavButtonModel rotationLockMdl;
    private NavButtonModel tiltMdl;
    private NavButtonModel tiltLockMdl;

    private boolean tiltEnabled = false;
    private boolean tiltLocked = false;
    private boolean rotationEnabled = false;
    private boolean rotationLocked = false;

    public CompassButtonChildView(Context context) {
        super(context);
    }

    public CompassButtonChildView(Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CompassButtonChildView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CompassButtonChildView(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        arrow = findViewById(R.id.arrow);
        buttons = findViewById(R.id.buttons);
        toggleRotation = findViewById(R.id.rotate);
        lockRotation = findViewById(R.id.rotate_lock);
        toggleTilt = findViewById(R.id.tilt);
        lockTilt = findViewById(R.id.tilt_lock);

        Resources r = getResources();

        Intent toggleRot = new Intent(MapMode.USER_DEFINED_UP.getIntent());
        toggleRot.putExtra("toggle", "true");

        rotationMdl = new NavButtonModel.Builder()
                .setReference("toggleRotation")
                .setName(r.getString(R.string.toggle_rotation))
                .setImage(r.getDrawable(R.drawable.ic_rotation))
                .setAction(toggleRot)
                .setActionLong(toggleRot)
                .build();

        Intent toggleLock = new Intent(MapMode.USER_DEFINED_UP.getIntent());
        toggleLock.putExtra("toggleLock", "true");
        rotationLockMdl = new NavButtonModel.Builder()
                .setReference("lockRotation")
                .setName(r.getString(R.string.toggle_rotation_lock))
                .setImage(r.getDrawable(R.drawable.ic_rotation_lock))
                .setAction(toggleLock)
                .setActionLong(toggleRot)
                .build();

        tiltMdl = new NavButtonModel.Builder()
                .setReference("toggleTilt")
                .setName(r.getString(R.string.toggle_tilt))
                .setImage(r.getDrawable(R.drawable.ic_3d))
                .setAction(CompassArrowMapComponent.TOGGLE_3D)
                .setActionLong(CompassArrowMapComponent.TOGGLE_3D)
                .build();

        tiltLockMdl = new NavButtonModel.Builder()
                .setAction("lockTilt")
                .setName(r.getString(R.string.toggle_tilt_lock))
                .setImage(r.getDrawable(R.drawable.ic_3d_lock))
                .setAction(CompassArrowMapComponent.LOCK_TILT)
                .setActionLong(CompassArrowMapComponent.TOGGLE_3D)
                .build();
    }

    /**
     * Set the state of the Child View buttons (on or off)
     *
     * @param tiltEnabled True if tilt enabled
     * @param tiltLocked True if tilt is locked
     * @param rotationEnabled True if rotation enabled
     * @param rotationLocked True if the rotation is locked
     */
    public void setStates(boolean tiltEnabled, boolean tiltLocked,
            boolean rotationEnabled, boolean rotationLocked) {
        this.tiltEnabled = tiltEnabled;
        this.tiltLocked = tiltLocked;
        this.rotationEnabled = rotationEnabled;
        this.rotationLocked = rotationLocked;
    }

    /**
     * Layout the child menu
     *
     * @param anchor The view to attach the child menu to.
     */
    public void layoutForToolbarView(View anchor) {
        setupCompassButtons();
        configureForHorizontalLayout(anchor);
    }

    /**
     * Setup each of the compass buttons
     */
    private void setupCompassButtons() {
        Intent rotIntent = new Intent(MapMode.USER_DEFINED_UP.getIntent());
        rotIntent.putExtra(rotationEnabled ? "toggleLock" : "toggle", "true");
        rotationMdl.setAction(rotIntent);

        setupButton(toggleRotation, rotationMdl, rotationEnabled,
                !rotationEnabled || !rotationLocked);
        setupButton(lockRotation, rotationLockMdl, rotationLocked,
                rotationEnabled && rotationLocked);

        tiltMdl.setAction(tiltEnabled ? CompassArrowMapComponent.LOCK_TILT
                : CompassArrowMapComponent.TOGGLE_3D);
        setupButton(toggleTilt, tiltMdl, tiltEnabled,
                !tiltEnabled || !tiltLocked);
        setupButton(lockTilt, tiltLockMdl, tiltLocked,
                tiltEnabled && tiltLocked);

        float scale = NavView.getInstance().getUserIconScale();
        int size = (int) (getResources().getDimension(R.dimen.nav_button_size)
                * scale);
        int btnCount = buttons.getChildCount();
        for (int i = 0; i < btnCount; i++) {
            NavButton btn = (NavButton) buttons.getChildAt(i);
            setSize(btn, size);
        }
    }

    private void configureForHorizontalLayout(View anchor) {
        NavView navView = NavView.getInstance();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.END_OF, anchor.getId());
        params.addRule(RelativeLayout.ALIGN_TOP, anchor.getId());

        this.setLayoutParams(params);

        int shadowColor = navView.getUserIconShadowColor();
        arrow.setColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY);

        Drawable bg = getResources().getDrawable(
                R.drawable.toolbar_container_rounded).mutate();
        bg.setColorFilter(shadowColor, PorterDuff.Mode.MULTIPLY);
        buttons.setBackground(bg);
    }

    private void setupButton(NavButton btn, NavButtonModel mdl,
            boolean selected, boolean visible) {
        NavView navView = NavView.getInstance();
        mdl.setSelected(selected);
        btn.setModel(mdl);
        btn.setDefaultIconColor(NavView.getInstance().getUserIconColor());
        btn.setShadowEnabled(false);
        btn.setOnClickListener(navView);
        btn.setOnLongClickListener(navView);
        btn.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * Set the size of a given view
     * @param view View
     * @param size Size (width/height) in pixels
     */
    private static void setSize(View view, int size) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp.width != size || lp.height != size) {
            lp.width = size;
            lp.height = size;
            view.setLayoutParams(lp);
        }
    }
}
