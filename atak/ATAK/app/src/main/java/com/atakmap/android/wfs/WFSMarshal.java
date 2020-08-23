
package com.atakmap.android.wfs;

import java.io.IOException;
import java.io.InputStream;

import android.net.Uri;

import com.atakmap.android.importexport.AbstractMarshal;
import com.atakmap.android.importexport.Marshal;
import com.atakmap.coremap.filesystem.FileSystemUtils;

public class WFSMarshal extends AbstractMarshal {

    public final static Marshal INSTANCE = new WFSMarshal();

    private WFSMarshal() {
        super(WFSImporter.CONTENT);
    }

    @Override
    public String marshal(InputStream inputStream, int probeSize)
            throws IOException {
        byte[] buf = new byte[14];
        int read = inputStream.read(buf, 0, Math.min(buf.length, probeSize));
        if (read < buf.length)
            return null;
        String s = new String(buf, FileSystemUtils.UTF8_CHARSET);
        if ("<takWfsConfig>".equals(s))
            return WFSImporter.MIME_XML;
        return null;
    }

    @Override
    public String marshal(Uri uri) throws IOException {
        return marshalUriAsStream(this, uri);
    }

    @Override
    public int getPriorityLevel() {
        // octet-stream : xml : takWfsConfig
        return 2;
    }
}
