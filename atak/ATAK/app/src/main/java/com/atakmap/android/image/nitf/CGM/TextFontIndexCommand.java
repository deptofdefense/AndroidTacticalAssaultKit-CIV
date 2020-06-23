
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class TextFontIndexCommand extends Command {
    int fontIndex;

    public TextFontIndexCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.fontIndex = makeInt();
    }

    /*    @Override
        public void paint(CGMDisplay d) {
            d.setFontIndex(this.fontIndex);
        }*/

    @Override
    public String toString() {
        return "TextFontIndex " + this.fontIndex;
    }
}
