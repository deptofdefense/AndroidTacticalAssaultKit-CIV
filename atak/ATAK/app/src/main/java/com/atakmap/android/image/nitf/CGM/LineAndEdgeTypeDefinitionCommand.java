
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class LineAndEdgeTypeDefinitionCommand extends Command {
    private final int lineType;
    private final double dashCycleRepeatLength;
    private final int[] dashElements;

    public LineAndEdgeTypeDefinitionCommand(int ec, int eid, int l,
            DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.lineType = makeIndex();
        if (BuildConfig.DEBUG && this.lineType > 0)
            throw new AssertionError();

        this.dashCycleRepeatLength = Math
                .abs(makeSizeSpecification(LineWidthSpecificationModeCommand
                        .getMode()));
        this.dashElements = new int[(this.args.length - this.currentArg)
                / sizeOfInt()];

        for (int i = 0; i < this.dashElements.length; i++) {
            this.dashElements[i] = makeInt();
        }

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && !(this.currentArg == this.args.length))
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LineAndEdgeTypeDefinition");
        sb.append(" lineType=").append(this.lineType);
        sb.append(" dashCycleRepeatLength=").append(this.dashCycleRepeatLength);
        sb.append(" [");
        for (int dashElement : this.dashElements) {
            sb.append(dashElement).append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
