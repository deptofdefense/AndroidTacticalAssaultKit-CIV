
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EdgeWidthSpecificationModeCommand extends Command {
    static private SpecificationMode mode;

    static {
        reset();
    }

    public EdgeWidthSpecificationModeCommand(int ec, int eid, int l,
            DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int mode = makeEnum();
        EdgeWidthSpecificationModeCommand.mode = SpecificationMode
                .getMode(mode);
    }

    public static void reset() {
        mode = SpecificationMode.ABSOLUTE;
    }

    public static SpecificationMode getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return "EdgeWidthSpecificationMode " + mode;
    }
}
