
package com.atakmap.android.importexport;

import java.io.File;

/**
 * Listener fired when an import finishes successfully
 */
public interface ImportListener {
    void onFileSorted(File src, File dst);
}
