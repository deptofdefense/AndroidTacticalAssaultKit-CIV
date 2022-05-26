
package com.atakmap.android.image.nitf.CGM;

import android.graphics.Point;

import com.atakmap.app.BuildConfig;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 */
public class PolyMarkerCommand extends Command {
    private final Point[] points;

    public PolyMarkerCommand(int ec, int eid, int l, DataInput in)
            throws IOException {
        super(ec, eid, l, in);

        int n = this.args.length / sizeOfPoint();
        this.points = new Point[n];
        for (int i = 0; i < n; i++) {
            this.points[i] = makePoint();
        }

        // make sure all the arguments were read
        if (BuildConfig.DEBUG && !(this.currentArg == this.args.length))
            throw new AssertionError();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PolyMarker [");
        for (Point point : this.points) {
            sb.append("(");
            sb.append(point.x).append(",");
            sb.append(point.y);
            sb.append(")");
        }
        sb.append("]");
        return sb.toString();
    }

    /*private void drawAsterisk(Graphics2D g2d, PointF origin, double size) {
        double halfSize = size/2;
        double fourthSize = size/4;
        Line2D.Double line = new Line2D.Double(origin.x, origin.y - halfSize, origin.x, origin.y + halfSize);
        g2d.draw(line);
    
        line = new Line2D.Double(origin.x - halfSize, origin.y - fourthSize, origin.x + halfSize, origin.y + fourthSize);
        g2d.draw(line);
    
        line = new Line2D.Double(origin.x - halfSize, origin.y + fourthSize, origin.x + halfSize, origin.y - fourthSize);
        g2d.draw(line);
    }
    
    private void drawCircle(Graphics2D g2d, PointF origin, double size, boolean fill) {
        double halfSize = size/2;
        Ellipse2D.Double circle = new Ellipse2D.Double(origin.x - halfSize, origin.y - halfSize, size, size);
    
        if (fill) {
            g2d.fill(circle);
        }
        else {
            g2d.draw(circle);
        }
    }
    
    private void drawCross(Graphics2D g2d, PointF origin, double size) {
        double halfSize = size/2;
        Line2D.Double line = new Line2D.Double(origin.x - halfSize, origin.y - halfSize, origin.x + halfSize, origin.y + halfSize);
        g2d.draw(line);
    
        line = new Line2D.Double(origin.x - halfSize, origin.y + halfSize, origin.x + halfSize, origin.y - halfSize);
        g2d.draw(line);
    }
    
    private void drawPlus(Graphics2D g2d, PointF origin, double size) {
        double halfSize = size/2;
        Line2D.Double line = new Line2D.Double(origin.x, origin.y - halfSize, origin.x, origin.y + halfSize);
        g2d.draw(line);
    
        line = new Line2D.Double(origin.x - halfSize, origin.y, origin.x + halfSize, origin.y);
        g2d.draw(line);
    }
    
    @Override
    public void paint(CGMDisplay d) {
        Graphics2D g2d = d.getGraphics2D();
        g2d.setColor(d.getMarkerColor());
    
        Type markerType = d.getMarkerType();
        double size = d.getMarkerSize();
    
        // save the current stroke and use a default one for markers
        Stroke savedStroke = g2d.getStroke();
        // TODO: what is the best stroke to use for markers?
        g2d.setStroke(new BasicStroke());
    
        if (MarkerType.Type.ASTERISK.equals(markerType)) {
            for (Point2D.Double point : this.points) {
                drawAsterisk(g2d, point, size);
            }
        }
        else if (MarkerType.Type.CIRCLE.equals(markerType)) {
            for (Point2D.Double point : this.points) {
                drawCircle(g2d, point, size, false);
            }
        }
        else if (MarkerType.Type.CROSS.equals(markerType)) {
            for (Point2D.Double point : this.points) {
                drawCross(g2d, point, size);
            }
        }
        else if (MarkerType.Type.DOT.equals(markerType)) {
            for (Point2D.Double point : this.points) {
                drawCircle(g2d, point, size, true);
            }
        }
        else if (MarkerType.Type.PLUS.equals(markerType)) {
            for (Point2D.Double point : this.points) {
                drawPlus(g2d, point, size);
            }
        }
    
        g2d.setStroke(savedStroke);
    }*/
}
