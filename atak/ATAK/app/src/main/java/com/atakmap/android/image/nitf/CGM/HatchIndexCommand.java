
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class HatchIndexCommand extends Command {
    enum HatchType {
        HORIZONTAL_LINES,
        VERTICAL_LINES,
        POSITIVE_SLOPE_LINES,
        NEGATIVE_SLOPE_LINES,
        HORIZONTAL_VERTICAL_CROSSHATCH,
        POSITIVE_NEGATIVE_CROSSHATCH
    }

    HatchType type = HatchType.HORIZONTAL_LINES;

    public HatchIndexCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int index = makeIndex();
        switch (index) {
            case 1:
                this.type = HatchType.HORIZONTAL_LINES;
                break;
            case 2:
                this.type = HatchType.VERTICAL_LINES;
                break;
            case 3:
                this.type = HatchType.POSITIVE_SLOPE_LINES;
                break;
            case 4:
                this.type = HatchType.NEGATIVE_SLOPE_LINES;
                break;
            case 5:
                this.type = HatchType.HORIZONTAL_VERTICAL_CROSSHATCH;
                break;
            case 6:
                this.type = HatchType.POSITIVE_NEGATIVE_CROSSHATCH;
                break;
            default:
                unsupported("hatch style: " + index);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("HatchIndex ").append(this.type);
        return sb.toString();
    }
}
