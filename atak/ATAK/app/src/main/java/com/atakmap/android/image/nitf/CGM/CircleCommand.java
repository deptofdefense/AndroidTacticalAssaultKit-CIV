
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class CircleCommand extends Command {
    public Point center;
    public double radius;

    public CircleCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.center = makePoint();
        this.radius = makeVdc();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        return "Circle [" + this.center.x + "," + this.center.y + "] "
                + this.radius;
    }
}
