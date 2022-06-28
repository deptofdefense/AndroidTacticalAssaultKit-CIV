
package com.atakmap.android.navigation.views.buttons;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.LoadoutItemModel;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.navigation.views.loadout.LoadoutManager;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Button used as part of {@link NavView}
 */
public class NavButton extends ImageButton implements View.OnDragListener {

    protected NavButtonModel model = NavButtonModel.NONE;

    // Loadout editing
    protected boolean editing;
    protected int position = NavButtonModel.TOP;

    // Icon color
    protected int defaultIconColor = Color.WHITE;
    protected int shadowColor = Color.BLACK;
    protected boolean shadowEnabled = true;

    // Drag variables
    protected boolean dragEntered, dragInProgress;

    // Visibility variables
    protected boolean onScreen = true;

    public NavButton(Context context) {
        super(context);
    }

    public NavButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NavButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Get the loadout key for this button
     * @return Loadout key
     */
    public String getKey() {
        return getId() != View.NO_ID
                ? getResources().getResourceName(getId())
                : String.valueOf(getTag());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateState();
    }

    /**
     * Set the model for this button
     * @param model Button model
     */
    public void setModel(NavButtonModel model) {
        if (model == null) {
            clearModel();
            return;
        }
        this.model = model;
        updateState();
    }

    /**
     * Get the model for this button
     * @return Button model
     */
    public NavButtonModel getModel() {
        return this.model;
    }

    /**
     * Clear the model for this button
     */
    public void clearModel() {
        setModel(NavButtonModel.NONE);
    }

    /**
     * Set the position this button is located in
     * @param position Position flag
     *                 See {@link NavButtonModel#getSupportedPositions()}
     */
    public void setPosition(int position) {
        if (this.position != position) {
            this.position = position;
            updateState();
        }
    }

    /**
     * Set whether the loadout is currently being edited
     * @param editing True if loading is being edited
     */
    public void setEditing(boolean editing) {
        if (this.editing != editing) {
            this.editing = editing;
            updateState();
        }
    }

    /**
     * Check whether the loadout is being edited
     * @return True if being edited
     */
    public boolean isEditing() {
        return this.editing;
    }

    /**
     * Set the default icon color that's displayed when the icon is not selected
     * @param color Color integer
     */
    public void setDefaultIconColor(int color) {
        if (this.defaultIconColor != color) {
            this.defaultIconColor = color;
            updateState();
        }
    }

    /**
     * Set the color of the underlying shadow
     * @param color Color integer
     */
    public void setShadowColor(int color) {
        if (this.shadowColor != color) {
            this.shadowColor = color;
            updateState();
        }
    }

    /**
     * Set whether the button's shadow is enabled
     * @param enabled True if enabled
     */
    public void setShadowEnabled(boolean enabled) {
        if (this.shadowEnabled != enabled) {
            this.shadowEnabled = enabled;
            updateState();
        }
    }

    /**
     * Set whether the button is fully on screen or not (cut off)
     * @param onScreen True if on screen
     */
    public void setOnScreen(boolean onScreen) {
        if (this.onScreen != onScreen) {
            this.onScreen = onScreen;
            updateState();
        }
    }

    /**
     * Update the display state of the button
     */
    protected void updateState() {
        if (isInEditMode() || this.model == null)
            return;

        // Icon color
        boolean selected = this.model.isSelected();
        int color = selected ? getResources().getColor(R.color.maize)
                : defaultIconColor;

        Drawable resource = this.model.getImage();
        boolean dragging = dragInProgress && !dragEntered;

        Drawable dr = null;
        Drawable bg = null;
        Context c = getContext();

        if (this.model != NavButtonModel.NONE)
            dr = resource;

        if (selected && model.getSelectedImage() != null)
            dr = model.getSelectedImage();

        // Set the icon and shadow color
        if (dr != null) {
            NavButtonDrawable navDr = null;
            if (getDrawable() instanceof NavButtonDrawable)
                navDr = (NavButtonDrawable) getDrawable();
            if (navDr == null || navDr.getBaseDrawable() != dr)
                navDr = new NavButtonDrawable(c, dr);
            navDr.setColor(color);
            navDr.setShadowColor(this.shadowColor);
            navDr.setShadowRadius(this.shadowEnabled ? 16 : 0);
            navDr.setBadgeCount(model.getBadgeCount());
            navDr.setBadgeImage(model.getBadgeImage());
            dr = navDr;
        }

        // Set the background
        if (dragging)
            bg = new LayerDrawable(new Drawable[] {
                    c.getDrawable(R.drawable.nav_item_background),
                    c.getDrawable(R.drawable.nav_empty)
            });
        else if (editing)
            bg = c.getDrawable(R.drawable.ic_group).mutate();

        setImageDrawable(dr);
        if (bg != null) {
            bg.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
            setBackground(bg);
        } else
            setBackgroundColor(Color.TRANSPARENT);

        // Update visibility (ignoring the side layout buttons)
        // If the button is on screen and has a valid button model or is in edit
        // mode then it's visible
        if (getParent() == NavView.getInstance())
            setVisibility(onScreen && (model != NavButtonModel.NONE || editing)
                    ? View.VISIBLE
                    : View.INVISIBLE);
    }

    @Override
    public boolean onDrag(View view, DragEvent dragEvent) {
        if (view != this)
            return false;

        int action = dragEvent.getAction();

        if (action == DragEvent.ACTION_DRAG_ENDED && dragInProgress) {
            dragInProgress = false;
            dragEntered = false;
            updateState();
            return true;
        }

        ClipDescription desc = dragEvent.getClipDescription();
        if (desc == null)
            return false;

        // Only respond to tool-related content labels
        CharSequence label = desc.getLabel();
        if (label == null || !NavView.DRAG_ADD_TOOL.contentEquals(label)
                && !NavView.DRAG_MOVE_TOOL.contentEquals(label))
            return false;

        // Check if this model can be dragged onto this item
        NavButtonModel srcModel = getButtonModel(dragEvent);
        if (srcModel != null && !srcModel.isPositionSupported(position))
            return false;

        switch (action) {
            case DragEvent.ACTION_DRAG_STARTED: {
                dragInProgress = true;
                updateState();
                break;
            }
            case DragEvent.ACTION_DRAG_ENTERED:
                dragEntered = true;
                updateState();
                break;
            case DragEvent.ACTION_DRAG_EXITED:
                dragEntered = false;
                updateState();
                break;
            case DragEvent.ACTION_DROP: {
                // Active loadout and valid model reference required
                LoadoutItemModel loadout = LoadoutManager.getInstance()
                        .getCurrentLoadout();
                if (loadout == null || srcModel == null)
                    return false;

                // Button view ID keys
                String srcKey = loadout.getButtonKey(srcModel);
                String dstKey = getKey();

                // Dragging a button on top of itself - nothing to do
                if (FileSystemUtils.isEquals(srcKey, dstKey))
                    break;

                // If we're moving a tool that's already in the loadout
                // then swap
                if (srcKey != null) {
                    // Update the loadout
                    NavButtonModel dstModel = loadout.getButton(dstKey);
                    if (dstModel != null)
                        loadout.setButton(srcKey, dstModel);
                    else
                        loadout.removeButton(srcKey);
                }

                // Update the button we've dragged to
                loadout.setButton(dstKey, srcModel);

                // Let listeners know the loadout has been modified
                LoadoutManager.getInstance().notifyLoadoutChanged(loadout);
                break;
            }
        }
        return true;
    }

    /**
     * Get the button model being dragged
     * @param dragEvent Drag event
     * @return Button model or null if not found/valid
     */
    public static NavButtonModel getButtonModel(DragEvent dragEvent) {
        // On newer versions the model reference is also stored in the
        // description extras, which allows us to get the model before the
        // drop event
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ClipDescription desc = dragEvent.getClipDescription();
            PersistableBundle extras = desc.getExtras();
            if (extras != null && extras.containsKey("model"))
                return NavButtonManager.getInstance()
                        .getModelByReference(extras.getString("model"));
        }

        // Fallback to checking the clip data
        ClipData clipData = dragEvent.getClipData();
        if (clipData == null)
            return null;
        ClipData.Item item = dragEvent.getClipData().getItemAt(0);
        return item != null ? NavButtonManager.getInstance()
                .getModelByReference(item.getText().toString()) : null;
    }
}
