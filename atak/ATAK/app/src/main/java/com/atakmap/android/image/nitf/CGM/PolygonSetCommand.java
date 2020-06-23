
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Path;
import android.graphics.Point;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class PolygonSetCommand extends Command {
    final private static int INVISIBLE = 0;
    final private static int VISIBLE = 1;
    final private static int CLOSE_INVISIBLE = 2;
    final private static int CLOSE_VISIBLE = 3;

    /** Polygon used to draw the edges */
    private Path edgePolygon;
    /** Polygon used to draw the filling */
    private Path fillPolygon;

    public PolygonSetCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);

        if (this.args.length % (sizeOfPoint() + sizeOfEnum()) != 0)
            throw new AssertionError();
        int n = this.args.length / (sizeOfPoint() + sizeOfEnum());

        Point p = makePoint();
        this.edgePolygon = new Path();
        this.fillPolygon = new Path();
        this.fillPolygon.setFillType(Path.FillType.EVEN_ODD);

        Point currentClosurePoint = p;
        this.edgePolygon.moveTo(p.x, p.y);
        this.fillPolygon.moveTo(p.x, p.y);
        int edgeOutFlag = makeEnum();

        for (int i = 1; i < n; i++) {
            p = makePoint();

            if (edgeOutFlag == INVISIBLE) {
                this.edgePolygon.moveTo(p.x, p.y);
                this.fillPolygon.lineTo(p.x, p.y);
            } else if (edgeOutFlag == VISIBLE) {
                this.edgePolygon.lineTo(p.x, p.y);
                this.fillPolygon.lineTo(p.x, p.y);
            } else if (edgeOutFlag == CLOSE_INVISIBLE) {
                this.fillPolygon.lineTo(currentClosurePoint.x,
                        currentClosurePoint.y);
                currentClosurePoint = p;
                this.edgePolygon.moveTo(p.x, p.y);

                this.fillPolygon.moveTo(p.x, p.y);
            } else if (edgeOutFlag == CLOSE_VISIBLE) {
                this.fillPolygon.lineTo(currentClosurePoint.x,
                        currentClosurePoint.y);
                this.edgePolygon.lineTo(currentClosurePoint.x,
                        currentClosurePoint.y);
                currentClosurePoint = p;
                this.edgePolygon.moveTo(p.x, p.y);

                this.fillPolygon.moveTo(p.x, p.y);
            }

            edgeOutFlag = makeEnum();
        }

        if (edgeOutFlag == VISIBLE || edgeOutFlag == CLOSE_VISIBLE) {
            this.edgePolygon.lineTo(currentClosurePoint.x,
                    currentClosurePoint.y);
            this.fillPolygon.close();
        }

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        return "PolygonSet";
    }
    /*
        @Override
        public void paint(CGMDisplay d) {
            Graphics2D g2d = d.getGraphics2D();
    
            d.fill(this.fillPolygon);
    
            if (d.drawEdge()) {
                g2d.setColor(d.getEdgeColor());
                g2d.setStroke(d.getEdgeStroke());
                g2d.draw(this.edgePolygon);
            }
        }*/
}
