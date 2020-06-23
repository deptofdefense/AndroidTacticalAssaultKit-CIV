
package com.atakmap.android.video;

import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.android.video.manager.VideoXMLHandler;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.List;

public class StreamManagementUtils {

    private static final VideoXMLHandler XML_HANDLER = new VideoXMLHandler();

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

    /**
     * @deprecated Use {@link VideoManager#getEntries()} or {@link VideoXMLHandler} instead
     */
    @Deprecated
    public static List<ConnectionEntry> deserialize(String xml) {
        return XML_HANDLER.parse(xml);
    }

}
