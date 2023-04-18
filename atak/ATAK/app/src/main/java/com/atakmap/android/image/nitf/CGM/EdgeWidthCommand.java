
package com.atakmap.android.image.nitf.CGM;

import androidx.annotation.NonNull;

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

    @NonNull
    @Override
    public String toString() {
        return "EdgeWidth " + this.width;
    }
}
