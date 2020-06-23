
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class CircularArcCentreCloseCommand extends CircularArcCentreCommand {
    private int type;

    public CircularArcCentreCloseCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int type = makeEnum();
        if (type == 0) {
            this.type = 0;
        } else if (type == 1) {
            this.type = 1;
        } else {
            unsupported("unsupported closure type " + this.type);
            this.type = 1;
        }
    }

    @Override
    public String toString() {
        return "CircularArcCentreClose [" + this.center.x + "," + this.center.y
                + "] [" + this.startDeltaX + "," + this.startDeltaY + "] ["
                + this.endDeltaX
                + "," + this.endDeltaY + "] " + this.radius + " type="
                + this.type;
    }
}
