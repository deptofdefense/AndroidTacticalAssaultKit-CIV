
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Path;
import android.graphics.Point;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PolylineCommand extends Command {
    public final Path path;
    public List<Point> points;

    //public Point start;

    public PolylineCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int n = this.args.length / sizeOfPoint();

        points = new ArrayList<>(n);

        this.path = new Path();
        //this.start = null;

        for (int i = 0; i < n; i++) {
            Point point = makePoint();
            if (i == 0) {
                this.path.moveTo(point.x, point.y);
                //this.start = point;
            } else {
                this.path.lineTo(point.x, point.y);
            }
            points.add(point);
        }

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Polyline [");
        sb.append("]");
        return sb.toString();
    }
}
