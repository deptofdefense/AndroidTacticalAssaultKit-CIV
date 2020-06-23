
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class FillColourCommand extends Command {
    protected int color = -1;
    protected int colorIndex = -1;

    public FillColourCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        if (ColourSelectionModeCommand.getType().equals(
                ColourSelectionModeCommand.Type.DIRECT)) {
            this.color = makeDirectColor();
        } else if (ColourSelectionModeCommand.getType().equals(
                ColourSelectionModeCommand.Type.INDEXED)) {
            this.colorIndex = makeColorIndex();
        }
    }

    public int getColor() {
        return this.color;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FillColour");
        if (this.color != -1) {
            sb.append(" directColor=").append(this.color);
        } else {
            sb.append(" colorIndex=").append(this.colorIndex);
        }
        return sb.toString();
    }
}
