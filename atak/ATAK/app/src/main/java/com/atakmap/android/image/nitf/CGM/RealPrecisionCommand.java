
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class RealPrecisionCommand extends Command {
    enum Precision {
        FLOATING_32,
        FLOATING_64,
        FIXED_32,
        FIXED_64
    }

    static private Precision precision;
    /**
     * Flag to tell us whether a real precision command has already been
     * processed or not
     */
    static boolean realPrecisionCommandProcessed;

    static {
        reset();
    }

    public RealPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        int representation = makeEnum();
        int p2 = makeInt();
        int p3 = makeInt();
        if (representation == 0) {
            if (p2 == 9 && p3 == 23) {
                precision = Precision.FLOATING_32;
            } else if (p2 == 12 && p3 == 52) {
                precision = Precision.FLOATING_64;
            } else {
                if (BuildConfig.DEBUG && !false)
                    throw new AssertionError("unsupported combination");
            }
        } else if (representation == 1) {
            if (p2 == 16 && p3 == 16) {
                precision = Precision.FIXED_32;
            } else if (p2 == 32 && p3 == 32) {
                precision = Precision.FIXED_64;
            } else {
                if (BuildConfig.DEBUG && !false)
                    throw new AssertionError("unsupported combination");
            }
        } else {
            if (BuildConfig.DEBUG && !false)
                throw new AssertionError("unsupported representation");
        }

        realPrecisionCommandProcessed = true;
    }

    public static void reset() {
        precision = Precision.FIXED_32;
        realPrecisionCommandProcessed = false;
    }

    static public Precision getPrecision() {
        return RealPrecisionCommand.precision;
    }

    /**
     * Returns a flag to tell us if we encountered a real precision command
     * already or not
     *
     * @return
     */
    static public boolean hasRealPrecisionBeenProcessed() {
        return realPrecisionCommandProcessed;
    }

    @Override
    public String toString() {
        String s = "RealPrecision "
                + RealPrecisionCommand.precision;
        return s;
    }

}
