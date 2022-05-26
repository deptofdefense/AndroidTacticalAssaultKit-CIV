
package gov.tak.api.widgets;

/**
 * Parent widget with functions similar to a LinearLayout view
 */
public interface ILinearLayoutWidget extends ILayoutWidget {

    /**
     * Sets the orientation of the linear layout widget to be either VERTICAL or HORIZONTAL.
     * @param orientation the value either HORIZONTAL or VERTICAL
     */
    void setOrientation(int orientation);

    /**
     * Returns the orientation of the linear layout widget.
     * @return
     */
    int getOrientation();

    /**
     * Sets the gravity of the linear layout widget, see Gravity.CENTER_HORIZONTAL, etc.
     * @param gravity
     */
    void setGravity(int gravity);

    int getGravity();

    /**
     * Set the layout params of this widget
     * Can be WRAP_CONTENT, MATCH_PARENT, or a pixel size value
     * @param pWidth Width parameter
     * @param pHeight Height parameter
     */
    void setLayoutParams(int pWidth, int pHeight);

    int[] getLayoutParams();

    /**
     * Get the size in pixels taken up by this layout's children
     * @return [width, height]
     */
    float[] getChildrenSize();

    /**
     * Get or create a new layout widget in this hierarchy
     * @param path Layout names separated by slashes
     *             where the last letter specifies the orientation (V or H)
     *             i.e. WidgetLayoutV/WidgetLayoutH = horizontal within vertical
     * @return Existing or newly created layout widget
     */
    ILinearLayoutWidget getOrCreateLayout(String path);
}
