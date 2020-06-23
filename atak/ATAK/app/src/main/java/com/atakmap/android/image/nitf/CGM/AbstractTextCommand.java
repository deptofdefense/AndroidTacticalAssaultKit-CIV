
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public abstract class AbstractTextCommand extends Command {

    /** The string to display */
    public String string;

    /** The position at which the string should be displayed */
    public Point position;

    public AbstractTextCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
    }

    /**
     * Flip the given string for left text path
     * @param s the string to flip
     * @return the string flipped.
     */
    protected String flipString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    public String getString() {
        return string;
    }
}
