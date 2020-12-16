
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EllipticalArcCloseCommand extends EllipticalArcCommand {
    private final int type;

    public EllipticalArcCloseCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int type = makeEnum();
        if (type == 0) {
            this.type = 0;
        } else if (type == 1) {
            this.type = 1;
        } else {
            unsupported("arc closure type " + type);
            this.type = 1;
        }
    }

    @Override
    protected int getClosureType() {
        return this.type;
    }

    @Override
    public String toString() {
        return "EllipticalArcClosed [" + this.center.x + "," + this.center.y
                + "] [" +
                this.firstConjugateDiameterEndPoint.x + "," +
                this.firstConjugateDiameterEndPoint.y + "] [" +
                this.secondConjugateDiameterEndPoint.x + "," +
                this.secondConjugateDiameterEndPoint.y + "] [" +
                this.startVectorDeltaX + "," +
                this.startVectorDeltaY + "] [" +
                this.endVectorDeltaX + "," +
                this.endVectorDeltaY + "] " + this.type;
    }
}
