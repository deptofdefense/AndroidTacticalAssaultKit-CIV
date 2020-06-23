
package com.atakmap.spatial.file;

import android.net.Uri;

import com.atakmap.android.importexport.AbstractMarshal;
import com.atakmap.android.importexport.Marshal;

import java.io.IOException;
import java.io.InputStream;

public class ShapefileMarshal extends AbstractMarshal {
    private final static int MAGIC_NUMBER = 9994;

    public final static Marshal INSTANCE = new ShapefileMarshal();

    private ShapefileMarshal() {
        super(ShapefileSpatialDb.SHP_CONTENT_TYPE);
    }

    @Override
    public String marshal(InputStream in, int probeSize) throws IOException {
        if (probeSize < 4)
            return null;

        // first field (4 bytes) should be a known value
        final int ch1 = in.read();
        final int ch2 = in.read();
        final int ch3 = in.read();
        final int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            return null;

        final int value = ((ch1 << 24) | (ch2 << 16) | (ch3 << 8) | (ch4));
        if (value == MAGIC_NUMBER) {
            return ShapefileSpatialDb.SHP_FILE_MIME_TYPE;
        } else {
            return null;
        }
    }

    @Override
    public String marshal(Uri uri) throws IOException {
        return marshalUriAsStream(this, uri);
    }

    @Override
    public int getPriorityLevel() {
        return 0;
    }
}
