
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Path;
import android.graphics.Point;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PolygonCommand extends Command {
    public final Path polygon;
    public List<Point> points;

    public PolygonCommand(int ec, int eid, int l, DataInput in)
            throws IOException {

        super(ec, eid, l, in);

        if ((this.args.length - this.currentArg) % sizeOfPoint() != 0)
            throw new AssertionError();
        int n = (this.args.length - this.currentArg) / sizeOfPoint();

        this.polygon = new Path();
        this.points = new ArrayList<>();
        this.polygon.setFillType(Path.FillType.EVEN_ODD);

        Point p = makePoint();
        this.polygon.moveTo(p.x, p.y);
        points.add(p);

        for (int i = 1; i < n; i++) {
            p = makePoint();
            this.polygon.lineTo(p.x, p.y);
            points.add(p);
        }

        this.polygon.close();

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && this.currentArg != this.args.length)
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Polygon ");
        /*sb.append(printShape(this.polygon));*/
        return sb.toString();
    }
    /*
        @Override
        public void paint(CGMDisplay d) {
            Graphics2D g2d = d.getGraphics2D();
    
            d.fill(this.polygon);
    
            if (d.drawEdge()) {
                g2d.setColor(d.getEdgeColor());
                g2d.setStroke(d.getEdgeStroke());
                g2d.draw(this.polygon);
            }
        }*/
}
