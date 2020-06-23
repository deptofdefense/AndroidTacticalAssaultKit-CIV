
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class ColourSelectionModeCommand extends Command {
    enum Type {
        INDEXED,
        DIRECT
    }

    static private Type type;

    static {
        reset();
    }

    public ColourSelectionModeCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        int e = makeEnum();
        if (e == 0)
            ColourSelectionModeCommand.type = Type.INDEXED;
        else if (e == 1)
            ColourSelectionModeCommand.type = Type.DIRECT;
        else {
            ColourSelectionModeCommand.type = Type.DIRECT;
            unsupported("color selection mode " + e);
        }

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    public static void reset() {
        type = Type.DIRECT;
    }

    public static Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ColourSelectionMode " + type;
    }
}
