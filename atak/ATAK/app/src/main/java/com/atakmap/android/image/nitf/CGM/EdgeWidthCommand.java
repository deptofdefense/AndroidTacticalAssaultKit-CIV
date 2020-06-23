
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EdgeWidthCommand extends Command {
    public double width;

    public EdgeWidthCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.width = makeSizeSpecification(EdgeWidthSpecificationModeCommand
                .getMode());
    }

    @Override
    public String toString() {
        return "EdgeWidth " + this.width;
    }
}
