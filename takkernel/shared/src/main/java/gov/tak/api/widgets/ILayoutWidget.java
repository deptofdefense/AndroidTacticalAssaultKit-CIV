
package gov.tak.api.widgets;

import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.graphics.Color;

public interface ILayoutWidget extends IParentWidget {

    PropertyInfo PROPERTY_BG_COLOR = new PropertyInfo("bgColor", Color.class, Color.WHITE);

    interface OnBackingColorChangedListener<T extends ILayoutWidget> {
        void onBackingColorChanged(T layout);
    }

    interface OnDragEnabledChangedListener<T extends ILayoutWidget> {
        void onDragEnabledChanged(T layout);
    }
    

    /**
     * Set the background color of this widget
     * @param backingColor Widget background color
     */
    void setBackingColor(int backingColor);

    int getBackingColor();

    /**
     * Set whether this layout should use a medium nine patch as its background
     * This is the same background used for text widgets
     * @param ninePatchBG True to enable nine patch background
     */
    void setNinePatchBG(boolean ninePatchBG);

    boolean getNinePatchBG();

    /**
     * Set layout alpha applied to this layout and its children
     * Calling this will cancel alpha fading
     * @param alpha Alpha value (0 - 255)
     */
    void setAlpha(int alpha);

    /**
     * Fade alpha of layout and its children
     * @param fromAlpha Start alpha value (0 - 255)
     * @param toAlpha End alpha value (0 - 255)
     * @param fadeTimeMS Duration of fade in milliseconds
     */
    void fadeAlpha(int fromAlpha, int toAlpha, int fadeTimeMS);

    boolean isFadingAlpha();

    float getAlpha();

    void setDragEnabled(boolean dragEnabled);

    boolean getDragEnabled();

    void addOnBackingColorChangedListener(OnBackingColorChangedListener<? extends ILayoutWidget> l);

    void removeOnBackingColorChangedListener(OnBackingColorChangedListener<? extends ILayoutWidget> l);

    void addOnDragEnabledChangedListener(OnDragEnabledChangedListener<? extends ILayoutWidget> l);

    void removeOnDragEnabledChangedListener(OnDragEnabledChangedListener<? extends ILayoutWidget> l);

    void onBackingColorChanged();

    void onDragEnabledChanged();
}
