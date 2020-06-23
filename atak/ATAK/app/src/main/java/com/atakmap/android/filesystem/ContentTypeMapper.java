
package com.atakmap.android.filesystem;

import java.io.File;

/**
 * Interface to map files to content type
 * 
 * 
 */
public interface ContentTypeMapper {
    /**
     * Map the specified file to Content type
     * 
     * @param file the file to get the content type from
     * @return the content type
     */
    String getContentType(File file);
}
