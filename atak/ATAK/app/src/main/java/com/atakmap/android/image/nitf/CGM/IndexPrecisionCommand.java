
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class IndexPrecisionCommand extends Command {
    static int precision;

    static {
        reset();
    }

    public IndexPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);
        IndexPrecisionCommand.precision = makeInt();

        if (BuildConfig.DEBUG
                && !(precision == 8 || precision == 16 || precision == 24
                        || precision == 32))
            throw new AssertionError();
    }

    public static void reset() {
        precision = 16;
    }

    public static int getPrecision() {
        return precision;
    }

    @Override
    public String toString() {
        String s = "IndexPrecision "
                + IndexPrecisionCommand.precision;
        return s;
    }
}
