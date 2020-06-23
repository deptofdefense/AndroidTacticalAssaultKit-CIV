
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EdgeVisibilityCommand extends Command {
    boolean isVisible;

    public EdgeVisibilityCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.isVisible = (makeEnum() != 0);

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        return "EdgeVisibility " + (this.isVisible ? "On" : "Off");
    }
}
