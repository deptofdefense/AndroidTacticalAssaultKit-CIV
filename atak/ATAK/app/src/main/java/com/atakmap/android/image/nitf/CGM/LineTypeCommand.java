
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class LineTypeCommand extends Command {
    public final static int SOLID = 1;
    public final static int DASH = 2;
    public final static int DOT = 3;
    public final static int DASH_DOT = 4;
    public final static int DASH_DOT_DOT = 5;

    protected int type;

    public LineTypeCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.type = makeInt();
    }

    public int getType() {
        return type;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LineType ").append(this.type);
        return sb.toString();
    }
}
