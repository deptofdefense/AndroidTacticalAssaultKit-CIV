
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class NamePrecisionCommand extends Command {
    private static int precision;

    static {
        reset();
    }

    NamePrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        NamePrecisionCommand.precision = makeInt();

        if (BuildConfig.DEBUG
                && !(NamePrecisionCommand.precision == 8
                        || NamePrecisionCommand.precision == 16 ||
                        NamePrecisionCommand.precision == 24
                        || NamePrecisionCommand.precision == 32))
            throw new AssertionError("unsupported NAME PRECISION");
    }

    static void reset() {
        precision = 16;
    }

    static int getPrecision() {
        return NamePrecisionCommand.precision;
    }

    @Override
    public String toString() {
        String s = "NamePrecision "
                + NamePrecisionCommand.precision;
        return s;
    }
}
