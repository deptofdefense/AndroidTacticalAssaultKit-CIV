
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class ColourValueExtentCommand extends Command {
    static private int[] minimumColorValueRGB;
    static private int[] maximumColorValueRGB;
    private double firstComponentScale;
    private double secondComponentScale;
    private double thirdComponentScale;

    static {
        reset();
    }

    public ColourValueExtentCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int precision = ColourPrecisionCommand.getPrecision();

        ColourValueExtentCommand.minimumColorValueRGB = new int[] {
                makeUInt(precision), makeUInt(precision), makeUInt(precision)
        };
        ColourValueExtentCommand.maximumColorValueRGB = new int[] {
                makeUInt(precision), makeUInt(precision), makeUInt(precision)
        };

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    public static void reset() {
        minimumColorValueRGB = new int[] {
                0, 0, 0
        };
        maximumColorValueRGB = new int[] {
                255, 255, 255
        };
    }

    static int[] getMinimumColorValueRGB() {
        return minimumColorValueRGB;
    }

    static int[] getMaximumColorValueRGB() {
        return maximumColorValueRGB;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ColourValueExtent");

        sb.append(" min RGB=(").append(minimumColorValueRGB[0]).append(",");
        sb.append(minimumColorValueRGB[1]).append(",");
        sb.append(minimumColorValueRGB[2]).append(")");

        sb.append(" max RGB=(").append(maximumColorValueRGB[0]).append(",");
        sb.append(maximumColorValueRGB[1]).append(",");
        sb.append(maximumColorValueRGB[2]).append(")");

        return sb.toString();
    }
}
