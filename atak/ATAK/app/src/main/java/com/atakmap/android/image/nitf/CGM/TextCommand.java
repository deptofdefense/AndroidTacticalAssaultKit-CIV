
package com.atakmap.android.image.nitf.CGM;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

public class TextCommand extends AbstractTextCommand {
    public TextCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);
        this.position = makePoint();

        // Must be called to properly increment reader
        makeEnum();

        this.string = makeString();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    /*    @Override
        Double getTextOffset(CGMDisplay d) {
            return new Point2D.Double(0, 0);
        }*/

    /*@Override
    protected void scaleText(CGMDisplay d, Paint.FontMetrics fontMetrics,
                             GlyphVector glyphVector, double width, double height) {
        Graphics2D g2d = d.getGraphics2D();
    
        double characterHeight = d.getCharacterHeight() / height;
        g2d.scale(characterHeight, characterHeight);
    }
    */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Text position=");
        sb.append(this.position);
        sb.append(" string=").append(this.string);
        return sb.toString();
    }
}
