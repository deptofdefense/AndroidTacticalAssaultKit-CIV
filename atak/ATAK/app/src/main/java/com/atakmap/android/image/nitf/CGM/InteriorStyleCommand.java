
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class InteriorStyleCommand extends Command {
    enum Style {
        HOLLOW,
        SOLID,
        PATTERN,
        HATCH,
        EMPTY,
        GEOMETRIC_PATTERN,
        INTERPOLATED
    }

    private final Style style;

    public InteriorStyleCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        switch (makeEnum()) {
            case 0:
                this.style = Style.HOLLOW;
                break;
            case 1:
                this.style = Style.SOLID;
                break;
            case 2:
                this.style = Style.PATTERN;
                break;
            case 3:
                this.style = Style.HATCH;
                break;
            case 4:
                this.style = Style.EMPTY;
                break;
            case 5:
                this.style = Style.GEOMETRIC_PATTERN;
                break;
            case 6:
                this.style = Style.INTERPOLATED;
                break;
            default:
                this.style = Style.HOLLOW;
        }

        if (!Style.HOLLOW.equals(this.style) || !Style.SOLID.equals(this.style)
                ||
                !Style.HATCH.equals(this.style)
                || !Style.EMPTY.equals(this.style)) {
            unimplemented(this.style.toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("InteriorStyle ").append(this.style);
        return sb.toString();
    }
}
