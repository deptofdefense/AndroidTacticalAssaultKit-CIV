
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class MetafileVersionCommand extends Command {
    int X;

    public MetafileVersionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.X = makeInt();
    }

    @Override
    public String toString() {
        return "MetafileVersion " + this.X;
    }

    public int getVersion() {
        return X;
    }
}
