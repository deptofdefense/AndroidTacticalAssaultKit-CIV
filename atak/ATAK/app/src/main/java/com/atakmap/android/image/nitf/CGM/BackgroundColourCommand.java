
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

public class BackgroundColourCommand extends Command {
    private final int backgroundColor;

    public BackgroundColourCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        this.backgroundColor = makeDirectColor();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BackgroundColour ").append(this.backgroundColor);
        return sb.toString();
    }
}
