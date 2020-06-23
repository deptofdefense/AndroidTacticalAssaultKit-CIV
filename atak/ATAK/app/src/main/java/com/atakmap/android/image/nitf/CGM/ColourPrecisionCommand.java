
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class ColourPrecisionCommand extends Command {
    static int precision;

    static {
        reset();
    }

    public ColourPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);
        ColourPrecisionCommand.precision = makeInt();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    public static void reset() {
        precision = 8;
    }

    static int getPrecision() {
        return precision;
    }

    @Override
    public String toString() {
        String s = "ColourPrecision "
                + ColourPrecisionCommand.precision;
        return s;
    }
}
