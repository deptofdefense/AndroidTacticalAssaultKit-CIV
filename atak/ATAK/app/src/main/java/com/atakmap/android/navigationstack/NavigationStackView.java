
package com.atakmap.android.navigationstack;

import java.util.List;

import android.view.View;
import android.widget.ImageButton;

public interface NavigationStackView {

    /**
     * Returns the title of the stack view
     * @return the title
     */
    String getTitle();

    /**
     * Returns the Image Buttons associated with the stack view
     * @return a list of buttons
     */
    List<ImageButton> getButtons();

    /**
     * Returns the actual view
     * @return the view associated with the stack
     */
    View getView();

    /**
     * Called when the device back button is pressed
     * @return True if handled
     */
    boolean onBackButton();

    /**
     * Called when the close button is tapped
     * @return True if handled
     */
    boolean onCloseButton();

    /**
     * Called when the stack view is closed
     */
    void onClose();
}
