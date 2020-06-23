
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EdgeTypeCommand extends Command {

    protected int type;

    public EdgeTypeCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        type = makeInt();
    }

    public int getType() {
        return type;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EdgeType ").append(this.type);
        return sb.toString();
    }
}
