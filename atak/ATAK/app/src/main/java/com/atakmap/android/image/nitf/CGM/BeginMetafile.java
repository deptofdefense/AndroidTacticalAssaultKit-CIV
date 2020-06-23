
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class BeginMetafile extends Command {
    private final String fileName;

    public BeginMetafile(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.fileName = l > 0 ? makeFixedString() : "";

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    public String getFileName() {
        return this.fileName;
    }

    @Override
    public String toString() {
        return "BeginMetafile " + this.fileName;
    }
}
