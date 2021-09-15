
package com.atakmap.coremap.filesystem;

import java.io.File;

/**
 * Secure delete callback event
 */
public interface SecureDeleteEvent {
    void onFinish(File file, boolean success, long elapsed);
}
