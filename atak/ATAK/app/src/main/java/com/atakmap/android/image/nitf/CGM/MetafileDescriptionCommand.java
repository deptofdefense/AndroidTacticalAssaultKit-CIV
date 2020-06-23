
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class MetafileDescriptionCommand extends Command {
    String S;

    public MetafileDescriptionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.S = makeString();
    }

    @Override
    public String toString() {
        return "MetafileDescription: " + this.S;
    }

    public String getDescription() {
        return S;
    }
}
