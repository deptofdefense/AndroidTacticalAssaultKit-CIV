
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import androidx.annotation.NonNull;

import java.io.DataInput;
import java.io.IOException;

class VDCExtentCommand extends Command {
    private Point lowerLeftCorner;
    private Point upperRightCorner;

    VDCExtentCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.lowerLeftCorner = makePoint();
        this.upperRightCorner = makePoint();
    }

    @NonNull
    @Override
    public String toString() {
        return "VDCExtent [" + this.lowerLeftCorner + "] ["
                + this.upperRightCorner + "]";
    }

    public Point[] extent() {
        Point[] points = new Point[2];
        points[0] = this.lowerLeftCorner;
        points[1] = this.upperRightCorner;
        return points;
    }
}
