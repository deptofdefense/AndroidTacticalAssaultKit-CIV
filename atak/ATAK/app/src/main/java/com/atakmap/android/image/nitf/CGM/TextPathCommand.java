
package com.atakmap.android.image.nitf.CGM;

import java.io.DataInput;
import java.io.IOException;

class TextPathCommand extends Command {
    enum Type {
        RIGHT,
        LEFT,
        UP,
        DOWN
    }

    private Type path;

    TextPathCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int typ = makeEnum();
        switch (typ) {
            case 0:
                this.path = Type.RIGHT;
                break;
            case 1:
                this.path = Type.LEFT;
                break;
            case 2:
                this.path = Type.UP;
                break;
            case 3:
                this.path = Type.DOWN;
                break;
            default:
                this.path = Type.RIGHT;
        }

        unimplemented("TextPath");
    }

    /*    @Override
        public void paint(CGMDisplay d) {
            d.setTextPath(this.path);
        }*/

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TextPath path=").append(this.path);
        return sb.toString();
    }
}
