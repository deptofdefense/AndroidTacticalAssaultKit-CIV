
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EndMetafile extends Command {
    public EndMetafile(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
    }

    @Override
    public String toString() {
        return "EndMetafile";
    }
}
