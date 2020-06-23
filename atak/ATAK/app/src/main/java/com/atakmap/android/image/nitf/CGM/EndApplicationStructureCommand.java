
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EndApplicationStructureCommand extends Command {

    EndApplicationStructureCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        // no arguments

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        return "EndApplicationStructure";
    }

}
