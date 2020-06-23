
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class CircularArcCentreCommand extends Command {
    protected Point center;
    protected int startDeltaX;
    protected int startDeltaY;
    protected int endDeltaX;
    protected int endDeltaY;
    protected int radius;

    public CircularArcCentreCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.center = makePoint();
        this.startDeltaX = makeVdc();
        this.startDeltaY = makeVdc();
        this.endDeltaX = makeVdc();
        this.endDeltaY = makeVdc();
        this.radius = makeVdc();
    }

    @Override
    public String toString() {
        return "CircularArcCentre [" + this.center.x + "," + this.center.y
                + "] [" + this.startDeltaX + "," + this.startDeltaY + "] ["
                + this.endDeltaX
                + "," + this.endDeltaY + "] " + this.radius;
    }
}
