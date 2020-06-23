
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

public class VDCIntegerPrecisionCommand extends Command {
    private static int precision;

    static {
        reset();
    }

    VDCIntegerPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);
        VDCIntegerPrecisionCommand.precision = makeInt();

        if (BuildConfig.DEBUG
                && !(precision == 16 || precision == 24 || precision == 32))
            throw new AssertionError();
    }

    public static void reset() {
        precision = 16;
    }

    public static int getPrecision() {
        return VDCIntegerPrecisionCommand.precision;
    }

    @Override
    public String toString() {
        return "VDCIntegerPrecision "
                + VDCIntegerPrecisionCommand.precision;
    }
}
