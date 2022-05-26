
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class CharacterExpansionFactorCommand extends Command {
    private final double factor;

    public CharacterExpansionFactorCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.factor = makeReal();

        unimplemented("CharacterExpansionFactor");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CharacterExpansionFactor factor=").append(this.factor);
        return sb.toString();
    }
}
