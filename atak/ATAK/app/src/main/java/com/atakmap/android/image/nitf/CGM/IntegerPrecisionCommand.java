
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class IntegerPrecisionCommand extends Command {
    private static int precision;

    static {
        reset();
    }

    public IntegerPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);
        IntegerPrecisionCommand.precision = makeInt();

        if (BuildConfig.DEBUG
                && !(IntegerPrecisionCommand.precision == 8
                        || IntegerPrecisionCommand.precision == 16 ||
                        IntegerPrecisionCommand.precision == 24
                        || IntegerPrecisionCommand.precision == 32))
            throw new AssertionError("unsupported INTEGER PRECISION");
    }

    public static void reset() {
        precision = 16;
    }

    static int getPrecision() {
        return IntegerPrecisionCommand.precision;
    }

    @Override
    public String toString() {
        String s = "IntegerPrecision "
                + IntegerPrecisionCommand.precision;
        return s;
    }
}
