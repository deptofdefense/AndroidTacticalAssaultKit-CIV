
package com.atakmap.android.imagecapture;

import android.os.Bundle;

/**
 * Implement to show up in GRG map capture
 */

public interface Capturable {

    /**
     * Save forwarded points to meta data holder
     * These will be used later in drawCanvas
     * @param capture Capture instance (use forward() to convert)
     * @return Metadata containing point data (or anything else relevant to ortho)
     */
    Bundle preDrawCanvas(CapturePP capture);

    /**
     * Draw the item to the canvas
     * @param capture Canvas to draw to (use getCanvas())
     * @param data Point data saved in preDrawCanvas
     */
    void drawCanvas(CapturePP capture, Bundle data);
}
