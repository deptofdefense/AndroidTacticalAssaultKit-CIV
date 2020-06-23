
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

public class VDCTypeCommand extends Command {
    enum Type {
        INTEGER,
        REAL
    }

    /** Default is INTEGER */
    private static Type type = Type.INTEGER;

    static {
        reset();
    }

    VDCTypeCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        int p1 = makeInt();
        if (p1 == 0)
            type = Type.INTEGER;
        else if (p1 == 1)
            type = Type.REAL;
        else
            unsupported("VDC Type " + p1);
    }

    public static void reset() {
        // default is integer
        type = Type.INTEGER;
    }

    @Override
    public String toString() {
        return "VDCType [" + type + "]";
    }

    public static Type getType() {
        return type;
    }
}
