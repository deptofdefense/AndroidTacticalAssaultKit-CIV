
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.elev.graphics.SharedDataModel;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.IsoKeyWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

class GLIsoKeyWidget extends GLWidget2 {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // IsoWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof IsoKeyWidget) {
                IsoKeyWidget isoKey = (IsoKeyWidget) subject;
                GLIsoKeyWidget glIsoKey = new GLIsoKeyWidget(isoKey, orthoView);
                glIsoKey.startObserving(isoKey);
                return glIsoKey;
            } else {
                return null;
            }
        }
    };

    private final IsoKeyWidget subject;

    private GLIsoKeyWidget(IsoKeyWidget subject, GLMapView orthoView) {
        super(subject, orthoView);
        this.subject = subject;
    }

    @Override
    public void releaseWidget() {
        stopObserving(subject);
    }

    @Override
    public void drawWidgetContent() {
        float[] barSize = this.subject.getBarSize();

        float[] keyColor = new float[4];
        keyColor[0] = 0;
        keyColor[1] = 1;
        keyColor[2] = 0;
        keyColor[3] = 1;

        // Start at bottom-left corner
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(0.0f, -_height, 0.0f);

        MapTextFormat mtf = MapView.getDefaultTextFormat();
        GLText glText = GLText.getInstance(mtf);
        float descent = glText.getDescent();
        float spacing = mtf.getBaselineSpacing();

        double minHeat, maxHeat;
        if (SharedDataModel.getInstance().isoDisplayMode
                .equals(SharedDataModel.ABSOLUTE)) {
            minHeat = SharedDataModel.isoScaleStart;
            maxHeat = SharedDataModel.getInstance().maxHeat;
        } else {
            minHeat = SharedDataModel.getInstance().minHeat;
            maxHeat = SharedDataModel.getInstance().maxHeat;
        }

        double curAlt = minHeat;
        double incAlt = (maxHeat - minHeat) / (IsoKeyWidget.NUM_LABELS - 1);

        if (GeoPoint.isAltitudeValid(minHeat) &&
                GeoPoint.isAltitudeValid(maxHeat)) {
            // Draw scale labels
            float textY = -spacing + descent;
            float textInc = (barSize[1] - spacing)
                    / (IsoKeyWidget.NUM_LABELS - 1);
            for (int j = 0; j < IsoKeyWidget.NUM_LABELS; j++) {
                String text = GLText.localize(String.valueOf((int) SpanUtilities
                        .convert(curAlt, Span.METER, Span.FOOT)));
                keyColor = rgbaFromElevation(curAlt, minHeat, maxHeat);
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(barSize[0] + _padding[LEFT],
                        textY, 0.0f);
                glText.drawSplitString(text, keyColor[0], keyColor[1],
                        keyColor[2], 1f);
                GLES20FixedPipeline.glPopMatrix();
                textY += textInc;
                curAlt += incAlt;
            }
        }

        // Draw top labels (mode and extra if specified)
        String[] topLabels = IsoKeyWidget.getTopLabels();
        float topLabelY = barSize[1] - spacing + descent;
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(0f, topLabelY, 0f);
        if (!FileSystemUtils.isEmpty(topLabels[1])) {
            glText.drawSplitString(topLabels[1], 1f, 1f, 1f, 1f);
            GLES20FixedPipeline.glTranslatef(0f, spacing, 0f);
        }
        glText.drawSplitString(topLabels[0], 1f, 1f, 1f, 1f);
        GLES20FixedPipeline.glPopMatrix();

        // Draw scale bar
        float keyHeight = barSize[1] / SharedDataModel.isoScaleMarks;
        float keyY = 0;
        for (int x = 0; x < SharedDataModel.isoScaleMarks; x++) {
            double keyAlt = ((double) x
                    / (double) SharedDataModel.isoScaleMarks)
                    * (maxHeat - minHeat) + minHeat;
            keyColor = rgbaFromElevation(keyAlt, minHeat, maxHeat);

            FloatArray colorsFloatArray = new FloatArray(4 * 4)
                    .add(keyColor)
                    .add(keyColor)
                    .add(keyColor)
                    .add(keyColor);

            drawColoredRectangle(keyY, keyY + keyHeight, 0.0f, barSize[0],
                    colorsFloatArray.toArray());
            keyY += keyHeight;
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    private final ByteBuffer pointer = com.atakmap.lang.Unsafe.allocateDirect(
            8 * 4)
            .order(ByteOrder.nativeOrder());
    private final FloatBuffer pointerf = pointer.asFloatBuffer();

    private void drawColoredRectangle(
            float bottom, float top, float left, float right, float[] colors) {
        pointerf.clear();
        pointerf.put(left);
        pointerf.put(bottom);
        pointerf.put(left);
        pointerf.put(top);
        pointerf.put(right);
        pointerf.put(top);
        pointerf.put(right);
        pointerf.put(bottom);
        pointerf.rewind();

        FloatBuffer colorPointer = com.atakmap.lang.Unsafe
                .allocateDirect(colors.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        colorPointer.put(colors);
        colorPointer.position(0);

        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_COLOR_ARRAY);
        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                pointer);

        GLES20FixedPipeline.glColorPointer(4,
                GLES20FixedPipeline.GL_FLOAT,
                0, colorPointer);

        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN,
                0, 4);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_COLOR_ARRAY);
    }

    private float[] rgbaFromElevation(double elevation, double minElev,
            double maxElev) {
        // Special case for NaN (no elevation data available)
        if (Double.isNaN(elevation)) {
            // completely transparent
            return new float[] {
                    0f, 0f, 0f, 0f
            };
        }
        // We use HSV because it's convenient to make hue represent elevation
        // because it spans the entire primary color spectrum with a single field
        // (rather than using the three separate fields in RGB)
        //
        // We normalize using (puts elevation in the [0-1] range):
        // (elevation - minElev) / (maxElev - minElev)
        //
        // We multiple the normalized value by 255.0 because:
        // Hue expects to be a value between [0 .. 360)
        // but RED wraps in the HSV spectrum,
        // so we'll only use [0 .. 255)
        //
        // We use 255.0f - X because we want:
        // RED to be highest elevation and
        // BLUE to be the lowest
        //
        float hue = 255.0f - (float) ((elevation - minElev)
                / (maxElev - minElev) * 255.0);
        // We use android.graphics.Color to convert from HSV to RGB
        int color = Color.HSVToColor(((int) (255f * (float) 1)),
                new float[] {
                        hue, 1, 1
                });
        // We convert to RGB because that's what OpenGL wants
        return new float[] {
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f,
                1
        };
    }

    private static class FloatArray {
        private final float[] data;
        private int position = 0;

        FloatArray(int size) {
            this.data = new float[size];
        }

        public FloatArray add(float toAdd) {
            this.data[position++] = toAdd;
            return this;
        }

        public FloatArray add(float[] toAdd) {
            for (float aToAdd : toAdd) {
                this.data[position++] = aToAdd;
            }
            return this;
        }

        public float[] toArray() {
            return data;
        }

        public String toString() {
            StringBuilder ret = new StringBuilder("{ ");
            for (int i = 0; i < data.length; i++) {
                if (i >= position)
                    ret.append("N/A, ");
                ret.append(data[i]).append(", ");
            }
            ret.append("}");
            return ret.toString();
        }
    }

}
