
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

class CharacterSpacingCommand extends Command {
    private final double additionalInterCharacterSpace;

    CharacterSpacingCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.additionalInterCharacterSpace = makeFixedPoint();

        unimplemented("CharacterSpacing");
    }

    @Override
    public String toString() {
        return "CharacterSpacing " + this.additionalInterCharacterSpace;
    }
}
