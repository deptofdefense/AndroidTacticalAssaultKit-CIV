
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EllipseCommand extends Command {
    public Point center;
    public Point firstConjugateDiameterEndPoint;
    public Point secondConjugateDiameterEndPoint;

    public EllipseCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.center = makePoint();
        this.firstConjugateDiameterEndPoint = makePoint();
        this.secondConjugateDiameterEndPoint = makePoint();
    }

    @Override
    public String toString() {
        return "Ellipse [" + this.center.x + "," + this.center.y + "] [" +
                this.firstConjugateDiameterEndPoint.x + "," +
                this.firstConjugateDiameterEndPoint.y + "] [" +
                this.secondConjugateDiameterEndPoint.x + "," +
                this.secondConjugateDiameterEndPoint.y + "]";
    }
}
