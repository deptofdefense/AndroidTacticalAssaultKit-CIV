
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class CharacterOrientationCommand extends Command {
    private final int xUp;
    private final int yUp;
    private final int xBase;
    private final int yBase;

    public CharacterOrientationCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.xUp = makeVdc();
        this.yUp = makeVdc();
        this.xBase = makeVdc();
        this.yBase = makeVdc();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CharacterOrientation");
        sb.append(" xUp=").append(this.xUp);
        sb.append(" yUp=").append(this.yUp);
        sb.append(" xBase=").append(this.xBase);
        sb.append(" yBase=").append(this.yBase);
        return sb.toString();
    }
}
