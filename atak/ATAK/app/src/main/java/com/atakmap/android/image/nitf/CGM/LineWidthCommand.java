
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class LineWidthCommand extends Command {
    public double width;

    public LineWidthCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        SpecificationMode mode = LineWidthSpecificationModeCommand.getMode();
        this.width = makeSizeSpecification(mode);

        if (!SpecificationMode.ABSOLUTE.equals(mode)
                && !SpecificationMode.SCALED.equals(mode)) {
            unimplemented("LineWidth specification mode " + mode
                    + " not implemented");
        }
    }

    @Override
    public String toString() {
        return "LineWidth " + this.width;
    }
}
