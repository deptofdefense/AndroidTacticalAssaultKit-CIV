
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class ColourIndexPrecisionCommand extends Command {
    static int precision;

    static {
        reset();
    }

    public ColourIndexPrecisionCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);
        ColourIndexPrecisionCommand.precision = makeInt();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    static int getPrecision() {
        return precision;
    }

    public static void reset() {
        precision = 8;
    }

    @Override
    public String toString() {
        String s = "ColourIndexPrecision "
                + ColourIndexPrecisionCommand.precision;
        return s;
    }
}
