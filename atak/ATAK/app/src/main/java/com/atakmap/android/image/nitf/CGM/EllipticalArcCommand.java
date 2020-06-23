
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EllipticalArcCommand extends EllipseCommand {

    protected int startVectorDeltaX;
    protected int startVectorDeltaY;
    protected int endVectorDeltaX;
    protected int endVectorDeltaY;

    public EllipticalArcCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.startVectorDeltaX = makeVdc();
        this.startVectorDeltaY = makeVdc();
        this.endVectorDeltaX = makeVdc();
        this.endVectorDeltaY = makeVdc();
    }

    protected int getClosureType() {
        return 0;
    }

    @Override
    public String toString() {
        return "EllipticalArc [" + this.center.x + "," + this.center.y + "] [" +
                this.firstConjugateDiameterEndPoint.x + "," +
                this.firstConjugateDiameterEndPoint.y + "] [" +
                this.secondConjugateDiameterEndPoint.x + "," +
                this.secondConjugateDiameterEndPoint.y + "] [" +
                this.startVectorDeltaX + "," +
                this.startVectorDeltaY + "] [" +
                this.endVectorDeltaX + "," +
                this.endVectorDeltaY + "]";
    }
}
