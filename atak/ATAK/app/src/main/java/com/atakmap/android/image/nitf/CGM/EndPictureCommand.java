
package com.atakmap.android.image.nitf.CGM;

import androidx.annotation.NonNull;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class EndPictureCommand extends Command {

    public EndPictureCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
    }

    @NonNull
    @Override
    public String toString() {
        return "EndPicture";
    }
}
