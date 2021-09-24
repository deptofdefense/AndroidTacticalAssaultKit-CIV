
package com.atakmap.android.video;

import com.atakmap.coremap.filesystem.FileSystemUtils;

public class StreamManagementUtils {

    public static ConnectionEntry createConnectionEntryFromUrl(String alias,
            String url) {
        if (FileSystemUtils.isEmpty(alias) || FileSystemUtils.isEmpty(url))
            return null;
        try {
            return new ConnectionEntry(alias, url);
        } catch (Exception e) {
            return null;
        }
    }
}
