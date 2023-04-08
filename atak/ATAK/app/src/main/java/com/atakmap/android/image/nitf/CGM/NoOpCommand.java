
package com.atakmap.android.image.nitf.CGM;

import androidx.annotation.NonNull;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class NoOpCommand extends Command {
    public NoOpCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        // arguments used for padding
    }

    @NonNull
    @Override
    public String toString() {
        return "NoOp";
    }
}
