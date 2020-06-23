
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class RectangleCommand extends Command {
    public Point firstCorner;
    public Point secondCorner;

    public RectangleCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.firstCorner = makePoint();
        this.secondCorner = makePoint();

        /*double x1 = this.firstCorner.x;
        double y1 = this.firstCorner.y;
        double x2 = this.secondCorner.x;
        double y2 = this.secondCorner.y;
        
        if (x1 > x2) {
            double temp = x1;
            x1 = x2;
            x2 = temp;
        }
        
        if (y1 > y2) {
            double temp = y1;
            y1 = y2;
            y2 = temp;
        }
        
        double w = x2 - x1;
        double h = y2 - y1;
        */
        //this.shape = new Rectangle2D.Double(x1, y1, w, h);

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        return "Rectangle [" + this.firstCorner.x + "," + this.firstCorner.y
                + "] [" + this.secondCorner.x + "," + this.secondCorner.y + "]";
    }
}
