
package com.atakmap.android.navigation.models;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PersistableBundle;

import com.atakmap.android.navigation.views.buttons.NavButton;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Model for use with {@link NavButton}
 * It's recommend to use {@link NavButtonModel.Builder} for creation
 */
public class NavButtonModel {

    // Represents an empty button model
    public static final NavButtonModel NONE = new NavButtonModel("empty", null);

    // Supported positions for this button model (flags)
    public static final int TOP = 1;
    public static final int LEFT = 1 << 1;

    // Reference/UID
    private final String reference;

    // The name/label for the button
    private final String name;

    // Regular and selected icon
    private final Drawable image;
    private Drawable imageSelected;

    // Actions when pressing or long-pressing
    private NavButtonIntentAction action;
    private NavButtonIntentAction actionLong;

    // The toolbar buttons
    private final List<NavButtonModel> childButtons = new ArrayList<>();

    // Supported button positions
    private int positionFlags;

    // Whether the button is selected or not
    private boolean selected;

    // Top-right badge count used by chat and certain plugins
    private int badgeCount;

    // Bottom-right customizable badge
    private Drawable badgeImage;

    /**
     * Construct a NavButtonModel given the provided parameters
     * @param reference Unique reference string
     * @param image Icon to show in the default state
     */
    public NavButtonModel(String reference, Drawable image) {
        this(reference, "", image);
    }

    /**
     * Construct a NavButtonModel given the provided parameters
     * @param reference Unique reference string
     * @param name The display name for this button
     * @param image Icon to show in the default state
     */
    public NavButtonModel(String reference, String name, Drawable image) {
        this(reference, name, image, null, null, null);
    }

    /**
     * Construct a NavButtonModel given the provided parameters
     * @param reference a reference identifier which should be universally unique for this device
     * @param name the name of the NavButton which can be localized for each language and should
     *             not be used for comparison
     * @param image an image to show in a unselected state
     * @param selectedImage an image to show in a selected state
     *
     */
    public NavButtonModel(final String reference,
            final String name,
            final Drawable image,
            final Drawable selectedImage,
            final NavButtonIntentAction action,
            final NavButtonIntentAction actionLong) {
        this.reference = reference;
        this.image = image;
        this.imageSelected = selectedImage;
        this.name = name;
        this.action = action;
        this.actionLong = actionLong;
        this.positionFlags = TOP;
    }

    /**
     * Get the unique reference for this button model
     * @return Model reference
     */
    public String getReference() {
        return reference;
    }

    /**
     * The name of the TakButtonModel.    This name will be locale specific and should not
     * be used for comparisons or equality.
     * @return the name of the button
     */
    public String getName() {
        return name;
    }

    /**
     * The image when not selected
     * @return a drawable representing the button in a unselected state
     */
    public Drawable getImage() {
        return image;
    }

    /**
     * The image when selected
     * @return a drawable representing the buttons selected state
     */
    public Drawable getSelectedImage() {
        return imageSelected;
    }

    /**
     * Change the selected image showing for the button.
     * @param image the current selected image.
     */
    public void setSelectedImage(Drawable image) {
        imageSelected = image;
    }

    /**
     * Set the count shown in a red circle in the top-right
     * @param count Count
     */
    public void setBadgeCount(int count) {
        badgeCount = count;
    }

    /**
     * Get the top-right badge count
     * @return Count
     */
    public int getBadgeCount() {
        return badgeCount;
    }

    /**
     * Set an image drawable shown in the bottom-right
     * @param image Badge image (null to hide)
     */
    public void setBadgeImage(@Nullable Drawable image) {
        badgeImage = image;
    }

    /**
     * Get the bottom-right badge image drawable
     * @return Image drawable
     */
    public Drawable getBadgeImage() {
        return badgeImage;
    }

    /**
     * Set whether this button model is currently selected
     * @param selected True if selected
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * Check if this button model is currently selected
     * @return True if selected
     */
    public boolean isSelected() {
        return this.selected;
    }

    /**
     * Set the intent action to perform when this button is pressed
     * @param action Intent action
     */
    public void setAction(NavButtonIntentAction action) {
        this.action = action;
    }

    /**
     * Set the intent action to perform when this button is pressed
     * @param intent Intent
     */
    public void setAction(@NonNull Intent intent) {
        setAction(new NavButtonIntentAction(intent));
    }

    /**
     * Set the intent action to perform when this button is long-pressed
     * @param action Intent action
     */
    public void setAction(@NonNull String action) {
        setAction(new Intent(action));
    }

    /**
     * Set the intent action to perform when this button is long-pressed
     * @param action Intent action
     */
    public void setActionLong(NavButtonIntentAction action) {
        this.actionLong = action;
    }

    /**
     * Set the intent action to perform when this button is long-pressed
     * @param intent Intent
     */
    public void setActionLong(@NonNull Intent intent) {
        setActionLong(new NavButtonIntentAction(intent));
    }

    /**
     * Set the intent action to perform when this button is pressed
     * @param action Intent action
     */
    public void setActionLong(@NonNull String action) {
        setActionLong(new Intent(action));
    }

    /**
     * The intent to fire when pressed
     * @return The intent action
     */
    @Nullable
    public NavButtonIntentAction getAction() {
        return action;
    }

    /**
     * Get the action intent to fire when pressed
     * @return The intent
     */
    @Nullable
    public Intent getActionIntent() {
        return action != null ? action.getIntent() : null;
    }

    /**
     * The intent to fire when long-pressed
     * @return The intent action
     */
    @Nullable
    public NavButtonIntentAction getActionLong() {
        return actionLong;
    }

    /**
     * Get the action intent to fire when long-pressed
     * @return The intent
     */
    @Nullable
    public Intent getActionLongIntent() {
        return actionLong != null ? actionLong.getIntent() : null;
    }

    /**
     * Returns true if this NavButton has children.
     * @return true if there are 1 or more child buttons.
     */
    public boolean hasChildren() {
        return !childButtons.isEmpty();
    }

    /**
     * Set a group of children buttons for this button model
     * @param childButtonModels the list of buttons.
     */
    public void setChildButtons(List<NavButtonModel> childButtonModels) {
        this.childButtons.clear();
        if (childButtonModels != null)
            childButtons.addAll(childButtonModels);
    }

    /**
     * Obtain the group of child buttons represented by the button model.
     * @return an unmodifiable list of children.
     */
    public List<NavButtonModel> getChildButtons() {
        return Collections.unmodifiableList(this.childButtons);
    }

    /**
     * Get the supported positions for this button model
     * @return Position flags
     */
    public int getSupportedPositions() {
        return positionFlags;
    }

    /**
     * Check if this button supports the given position
     * @param position Position flag ({@link #TOP} or {@link #LEFT})
     * @return True if supported
     */
    public boolean isPositionSupported(int position) {
        return MathUtils.hasBits(positionFlags, position);
    }

    /**
     * Create clipboard data for this model
     * @param desc Clipboard data description
     * @return Clipboard data
     */
    public ClipData createClipboardData(String desc) {
        ClipData data = new ClipData(desc,
                new String[] {
                        ClipDescription.MIMETYPE_TEXT_PLAIN
                },
                new ClipData.Item(getReference()));

        // Store the reference in the description extras so we can check if the
        // model is supported before the drop event (only supported on newer versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PersistableBundle extras = new PersistableBundle();
            extras.putString("model", getReference());
            data.getDescription().setExtras(extras);
        }

        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        NavButtonModel that = (NavButtonModel) o;
        return Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }

    @Override
    public String toString() {
        return getName() + " [" + getReference() + "]";
    }

    /**
     * Button model builder
     */
    public static class Builder {

        private String reference = NavButtonModel.NONE.getReference();
        private String name = "";
        private Drawable image = NavButtonModel.NONE.getImage();
        private Drawable imageSelected;
        private NavButtonIntentAction action;
        private NavButtonIntentAction longAction;
        private List<NavButtonModel> childButtons;
        private int positionFlags = NavButtonModel.TOP;

        /**
         * Set the button reference ID
         * @param reference Reference ID
         * @return Builder
         */
        public Builder setReference(String reference) {
            this.reference = reference;
            return this;
        }

        /**
         * Set the name of this button
         * @param name Name
         * @return Builder
         */
        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the button's default image drawable
         * @param image Drawable
         * @return Builder
         */
        public Builder setImage(Drawable image) {
            this.image = image;
            return this;
        }

        /**
         * Set the image drawable used when the button is selected
         * @param image Drawable
         * @return Builder
         */
        public Builder setSelectedImage(Drawable image) {
            this.imageSelected = image;
            return this;
        }

        /**
         * Set the intent action fired when this button is pressed
         * @param action Intent action
         * @return Builder
         */
        public Builder setAction(NavButtonIntentAction action) {
            this.action = action;
            return this;
        }

        /**
         * Set the intent action fired when this button is pressed
         * @param intent Intent action
         * @return Builder
         */
        public Builder setAction(Intent intent) {
            if (this.action != null)
                this.action.setIntent(intent);
            else
                setAction(new NavButtonIntentAction(intent));
            return this;
        }

        /**
         * Set the intent action fired when this button is pressed
         * @param action Intent action
         * @return Builder
         */
        public Builder setAction(String action) {
            if (this.action != null)
                this.action.getIntent().setAction(action);
            else
                setAction(new NavButtonIntentAction(action));
            return this;
        }

        /**
         * Set the intent action fired when this button is long-pressed
         * @param action Intent action
         * @return Builder
         */
        public Builder setActionLong(NavButtonIntentAction action) {
            this.longAction = action;
            return this;
        }

        /**
         * Set the intent action fired when this button is long-pressed
         * @param intent Intent action
         * @return Builder
         */
        public Builder setActionLong(Intent intent) {
            if (this.longAction != null)
                this.longAction.setIntent(intent);
            else
                setActionLong(new NavButtonIntentAction(intent));
            return this;
        }

        /**
         * Set the intent action fired when this button is long-pressed
         * @param action Intent action
         * @return Builder
         */
        public Builder setActionLong(String action) {
            if (this.longAction != null)
                this.longAction.getIntent().setAction(action);
            else
                setActionLong(new NavButtonIntentAction(action));
            return this;
        }

        /**
         * Set a group of children buttons for this button model
         * @param buttons The list of buttons
         * @return Builder
         */
        public Builder setChildButtons(List<NavButtonModel> buttons) {
            this.childButtons = buttons;
            return this;
        }

        /**
         * Set the supported positions for this button
         * @param positionFlags Position flags ({@link #TOP} or {@link #LEFT})
         * @return Builder
         */
        public Builder setSupportedPositions(int positionFlags) {
            this.positionFlags = positionFlags;
            return this;
        }

        /**
         * Build a new button model
         * @return New {@link NavButtonModel}
         */
        public NavButtonModel build() {
            NavButtonModel btn = new NavButtonModel(reference, name, image,
                    imageSelected, action, longAction);
            btn.setChildButtons(childButtons);
            btn.positionFlags = positionFlags;
            return btn;
        }
    }
}
