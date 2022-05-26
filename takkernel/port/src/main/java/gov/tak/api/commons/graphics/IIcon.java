package gov.tak.api.commons.graphics;

import java.util.Set;

public interface IIcon {
    /**
     * The default state value (zero)
     */
    int STATE_DEFAULT = 0;

    /**
     * Can be passed to setSize() to signal using the image's natural width or height.
     */
    int SIZE_DEFAULT = -1;

    /**
     * Can be passed to setAnchor() to signal using the image center as the anchor point.
     */
    int ANCHOR_CENTER = Integer.MIN_VALUE;

    /**
     * Get the icon width
     *
     * @return the width of the icon
     */
    int getWidth();

    /**
     * Get the icon height
     *
     * @return the height of the icon
     */
    int getHeight();

    /**
     * Get the icon x anchor point
     *
     * @return the x anchor point
     */
    int getAnchorX();

    /**
     * Get the icon y anchor point
     *
     * @return the y anchor point
     */
    int getAnchorY();

    /**
     * Get the image URI for a given state
     *
     * @param state the state value
     * @return the uri for a given state, if the uri is null, the state returned is 0
     */
    String getImageUri(final int state);

    /**
     * Get the color for a given state
     *
     * @param state the state value
     * @return a packed color created with android.graphics.Color
     */
    int getColor(int state);

    Set<Integer> getStates();
}
