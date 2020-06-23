
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class BeginPictureCommand extends Command {
    public String S;

    public BeginPictureCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.S = l > 0 ? makeString() : "";

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        return "BeginPicture " + this.S;
    }

    public String getPictureName() {
        return S;
    }
}
