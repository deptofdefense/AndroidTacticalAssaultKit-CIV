
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

public class VDCRealPrecisionCommand extends Command {
    enum Type {
        FLOATING_POINT_32BIT,
        FLOATING_POINT_64BIT,
        FIXED_POINT_32BIT,
        FIXED_POINT_64BIT,
    }

    private static Type precision;

    static {
        reset();
    }

    VDCRealPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);
        int p1 = makeInt();
        int p2 = makeInt();
        int p3 = makeInt();

        if (p1 == 0) {
            if (p2 == 9 && p3 == 23) {
                VDCRealPrecisionCommand.precision = Type.FLOATING_POINT_32BIT;
            } else if (p2 == 12 && p3 == 52) {
                VDCRealPrecisionCommand.precision = Type.FLOATING_POINT_64BIT;
            } else {
                // use default
                unsupported("unsupported real precision");
                VDCRealPrecisionCommand.precision = Type.FIXED_POINT_32BIT;
            }
        } else if (p1 == 1) {
            if (p2 == 16 && p3 == 16) {
                VDCRealPrecisionCommand.precision = Type.FIXED_POINT_32BIT;
            } else if (p2 == 32 && p3 == 32) {
                VDCRealPrecisionCommand.precision = Type.FIXED_POINT_64BIT;
            } else {
                // use default
                unsupported("unsupported real precision");
                VDCRealPrecisionCommand.precision = Type.FIXED_POINT_32BIT;
            }
        }
    }

    public static void reset() {
        precision = Type.FIXED_POINT_32BIT;
    }

    public static Type getPrecision() {
        return VDCRealPrecisionCommand.precision;
    }

    @Override
    public String toString() {
        return "VDCRealPrecision "
                + VDCRealPrecisionCommand.precision;
    }
}
