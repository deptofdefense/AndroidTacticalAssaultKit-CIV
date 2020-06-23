
package com.atakmap.android.toolbar;

import android.os.Bundle;

/**
 * Simple listener for when a tool is started or ended
 */
public interface ToolListener {

    /**
     * A tool has began
     *
     * @param tool Tool instance
     * @param extras Tool extras (read-only)
     */
    void onToolBegin(Tool tool, Bundle extras);

    /**
     * A tool has ended
     *
     * @param tool Tool instance
     */
    void onToolEnded(Tool tool);
}
