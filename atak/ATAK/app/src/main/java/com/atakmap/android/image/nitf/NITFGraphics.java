
package com.atakmap.android.image.nitf;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.BeginPictureCommand;
import com.atakmap.android.image.nitf.CGM.CharacterHeightCommand;
import com.atakmap.android.image.nitf.CGM.CircleCommand;
import com.atakmap.android.image.nitf.CGM.ColourIndexPrecisionCommand;
import com.atakmap.android.image.nitf.CGM.ColourPrecisionCommand;
import com.atakmap.android.image.nitf.CGM.ColourSelectionModeCommand;
import com.atakmap.android.image.nitf.CGM.ColourValueExtentCommand;
import com.atakmap.android.image.nitf.CGM.Command;
import com.atakmap.android.image.nitf.CGM.EdgeColourCommand;
import com.atakmap.android.image.nitf.CGM.EdgeTypeCommand;
import com.atakmap.android.image.nitf.CGM.EdgeWidthCommand;
import com.atakmap.android.image.nitf.CGM.EdgeWidthSpecificationModeCommand;
import com.atakmap.android.image.nitf.CGM.EllipseCommand;
import com.atakmap.android.image.nitf.CGM.FillColourCommand;
import com.atakmap.android.image.nitf.CGM.IndexPrecisionCommand;
import com.atakmap.android.image.nitf.CGM.IntegerPrecisionCommand;
import com.atakmap.android.image.nitf.CGM.LineColourCommand;
import com.atakmap.android.image.nitf.CGM.LineTypeCommand;
import com.atakmap.android.image.nitf.CGM.LineWidthCommand;
import com.atakmap.android.image.nitf.CGM.LineWidthSpecificationModeCommand;
import com.atakmap.android.image.nitf.CGM.PolygonCommand;
import com.atakmap.android.image.nitf.CGM.PolylineCommand;
import com.atakmap.android.image.nitf.CGM.RealPrecisionCommand;
import com.atakmap.android.image.nitf.CGM.RectangleCommand;
import com.atakmap.android.image.nitf.CGM.TextColourCommand;
import com.atakmap.android.image.nitf.CGM.TextCommand;
import com.atakmap.android.image.nitf.CGM.VDCIntegerPrecisionCommand;
import com.atakmap.android.image.nitf.CGM.VDCRealPrecisionCommand;
import com.atakmap.android.image.nitf.CGM.VDCTypeCommand;
import com.atakmap.android.image.nitf.overlays.ArrowOverlay;
import com.atakmap.android.image.nitf.overlays.CircleOverlay;
import com.atakmap.android.image.nitf.overlays.EllipseOverlay;
import com.atakmap.android.image.nitf.overlays.FreeformOverlay;
import com.atakmap.android.image.nitf.overlays.LabelOverlay;
import com.atakmap.android.image.nitf.overlays.LeaderLineOverlay;
import com.atakmap.android.image.nitf.overlays.LineOverlay;
import com.atakmap.android.image.nitf.overlays.NorthOverlay;
import com.atakmap.android.image.nitf.overlays.Overlay;
import com.atakmap.android.image.nitf.overlays.RectangleOverlay;
import com.atakmap.android.image.nitf.overlays.TriangleOverlay;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NITFGraphics {

    protected int relativeRow;
    protected int relativeColumn;
    protected int commonRow;
    protected int commonColumn;
    protected int attachedLevel;
    protected int displayLevel;
    public Overlay overlay;

    private List<Command> commands;

    private final static int INITIAL_NUM_COMMANDS = 500;

    public NITFGraphics(int relRow, int relCol, int comRow, int comCol,
            int aLvl, int dLvl, byte[] dataIn) {
        relativeRow = relRow;
        relativeColumn = relCol;
        commonRow = comRow;
        commonColumn = comCol;
        attachedLevel = aLvl;
        displayLevel = dLvl;
        byte[] data = UnescapeData(dataIn);

        InputStream inputStream = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(new BufferedInputStream(
                inputStream));
        try {
            read(in);
            in.close();
        } catch (Throwable e) {
            commands = null;
        }
        if (overlay != null) {
            overlay.offset(new PointF(comCol, comRow));
        }
    }

    private static byte[] UnescapeData(byte[] dataIn) {
        byte[] dataOut = new byte[dataIn.length];
        int iOut = 0;
        for (int iIn = 0; iIn < dataIn.length - 1
                && dataIn[iIn] != '\0'; iIn++) {
            if (dataIn[iIn] == '\\') {
                iIn++;
                if (dataIn[iIn] == 'n')
                    dataOut[iOut++] = '\n';
                else if (dataIn[iIn] == '0')
                    dataOut[iOut++] = '\0';
                else
                    dataOut[iOut++] = dataIn[iIn];
            } else {
                dataOut[iOut++] = dataIn[iIn];
            }
        }
        dataOut[iOut] = '\0';

        return Arrays.copyOf(dataOut, iOut);
    }

    public void read(DataInput in) throws IOException {
        reset();
        this.commands = new ArrayList<>(INITIAL_NUM_COMMANDS);
        while (true) {
            Command c = Command.read(in);
            if (c == null)
                break;

            this.commands.add(c);
        }
        int textColor = Color.WHITE;
        int lineColor = Color.WHITE, fillColor = 0;
        int textSize = 6;
        int strokeSize = 4, lineType = Overlay.LINE_TYPE_SOLID;
        String name = "";

        for (Command c : commands) {
            if (c instanceof BeginPictureCommand) {
                BeginPictureCommand pictureCommand = (BeginPictureCommand) c;
                name = pictureCommand.S;
            }
            if (c instanceof CircleCommand)
                overlay = new CircleOverlay((CircleCommand) c);
            if (c instanceof EllipseCommand)
                overlay = new EllipseOverlay((EllipseCommand) c);
            else if (c instanceof RectangleCommand)
                overlay = new RectangleOverlay((RectangleCommand) c);
            else if (c instanceof PolygonCommand) {
                PolygonCommand shape = (PolygonCommand) c;
                List<Point> p = shape.points;
                if (p.size() < 3)
                    continue;
                if (p.size() == 3 || p.size() == 4 &&
                        p.get(0).equals(p.get(3)))
                    overlay = new TriangleOverlay(shape);
                else
                    overlay = new FreeformOverlay(shape);
            } else if (c instanceof TextCommand) {
                if (name.equalsIgnoreCase(NorthOverlay.ID)
                        || name.equals(LeaderLineOverlay.ID))
                    continue;
                overlay = new LabelOverlay((TextCommand) c);
            } else if (c instanceof PolylineCommand) {
                PolylineCommand line = (PolylineCommand) c;
                List<Point> p = line.points;
                if (p.size() < 2)
                    continue;
                if (name.equalsIgnoreCase(ArrowOverlay.ID))
                    overlay = new ArrowOverlay(line);
                else if (name.equalsIgnoreCase(NorthOverlay.ID))
                    overlay = new NorthOverlay(line);
                else if (name.equalsIgnoreCase(LeaderLineOverlay.ID))
                    overlay = new LeaderLineOverlay(line, commands);
                else if (p.size() == 2)
                    overlay = new LineOverlay(line);
                else if (p.size() == 3 || p.size() == 4 &&
                        p.get(0).equals(p.get(3)))
                    overlay = new TriangleOverlay(line);
                else
                    overlay = new FreeformOverlay(line);
            } else if (c instanceof TextColourCommand)
                textColor = ((TextColourCommand) c).color;
            else if (c instanceof LineColourCommand)
                lineColor = ((LineColourCommand) c).color;
            else if (c instanceof EdgeColourCommand)
                lineColor = ((EdgeColourCommand) c).color;
            else if (c instanceof FillColourCommand)
                fillColor = ((FillColourCommand) c).getColor();
            else if (c instanceof CharacterHeightCommand)
                textSize = (int) ((CharacterHeightCommand) c).characterHeight;
            else if (c instanceof LineWidthCommand)
                strokeSize = (int) ((LineWidthCommand) c).width;
            else if (c instanceof EdgeWidthCommand)
                strokeSize = (int) ((EdgeWidthCommand) c).width;
            else if (c instanceof LineTypeCommand)
                lineType = ((LineTypeCommand) c).getType();
            else if (c instanceof EdgeTypeCommand)
                lineType = ((EdgeTypeCommand) c).getType();
        }
        if (overlay != null) {
            overlay.setColor(lineColor);
            overlay.setFillColor(fillColor);
            overlay.setStrokeWidth(strokeSize);
            overlay.setStrokeStyle(lineType);
            overlay.setFontSize(textSize);
            if (overlay instanceof LabelOverlay)
                overlay.setColor(textColor);
            else if (overlay instanceof LineOverlay)
                overlay.setFillColor(0);
        }
    }

    private void reset() {
        ColourIndexPrecisionCommand.reset();
        ColourPrecisionCommand.reset();
        ColourSelectionModeCommand.reset();
        ColourValueExtentCommand.reset();
        EdgeWidthSpecificationModeCommand.reset();
        IndexPrecisionCommand.reset();
        IntegerPrecisionCommand.reset();
        LineWidthSpecificationModeCommand.reset();
        RealPrecisionCommand.reset();
        VDCIntegerPrecisionCommand.reset();
        VDCRealPrecisionCommand.reset();
        VDCTypeCommand.reset();
    }

    public void draw(Canvas canvas, Paint paint, float density) {
        if (overlay != null) {
            overlay.setDpiScale(density);
            overlay.draw(canvas, paint);
        }
    }

    public int getDisplayLevel() {
        return attachedLevel;
    }
}
