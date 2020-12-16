
package com.atakmap.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.opengl.GLES10;
import android.opengl.Matrix;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.util.ConfigOptions;

public final class GLText {

    private final static int CHAR_BATCH_SIZE = 80; // number of characters to render per batch

    private final static GLRenderBatch2 SPRITE_BATCH = new GLRenderBatch2(CHAR_BATCH_SIZE*6);

    private final static float[] SCRATCH_MATRIX = new float[16];

    protected static final String TAG = "GLText";

    private static Map<Long, GLText> glTextCache = new HashMap<Long, GLText>();

    /**************************************************************************/

    private final MapTextFormat textFormat;

    long pointer;

    private static final int[] sizeBins = new int[] { 0, 8, 16, 32, 64, 128 };

    /**
     * Provided a text size, find the most appropriate GlTextCache to use to provide for optimal
     * results.  The range of returns are from 0..128.
     * @param textSize the input textSize
     * @return find the best font size based on a provided text size.
     */
    private static int findBestFontSize(int textSize) {
        if (textSize > 0 && ConfigOptions.getOption("gltext.use-font-bins", 1) != 0) {
            // Scale input text size by factor of 2 if the screen density is lower than 2
            // This improves readability of smaller font sizes - see ATAK-12517
            int ts = textSize * (GLRenderGlobals.getRelativeScaling() < 2 ? 2 : 1);
            for (int i = 0; i < sizeBins.length - 1; ++i) {
                if (textSize == sizeBins[i])
                    break;
                if (ts > sizeBins[i] && ts < sizeBins[i + 1]) {
                    textSize = sizeBins[i + 1];
                    break;
                }
            }
            if (textSize > 128)
                textSize = 128;
        }
        return textSize;
    }


    /**
     * Returns an instance of the requested GLText; creates it if it hasn't already. Initializes all
     * the font metrics so that the getters work immediately.
     *
     * @return a specified GLText instance; never null
     */
    public synchronized static GLText getInstance(MapTextFormat textFormat) {
        final Typeface typeface = textFormat.getTypeface();
        final int textSize = textFormat.getFontSize();

        // performs safe volatile double checked locking
        final long key = ((long) typeface.hashCode() << 32)
                | (long) (textSize<<1) | (long)(textFormat.isOutlined() ? 1 : 0);
        GLText customGLText = glTextCache.get(Long.valueOf(key));
        if (customGLText == null) {
            customGLText = new GLText(textFormat, typeface, textSize);
            glTextCache.put(Long.valueOf(key),
                    customGLText);
        }

        return customGLText;
    }

    // does not load the texture, that is done on gl thread in draw method
    // initializes all the font metrics so that the getters work
    private GLText(MapTextFormat textFormat, Typeface typeface, float densityAdjustedTextSize) {
        final int glyphRenderFontSize = findBestFontSize(textFormat.getFontSize());

        //if(false) pointer = intern(textFormat, null); else
        pointer = intern(textFormat, new MapTextFormat(textFormat.getTypeface(), textFormat.isOutlined(), glyphRenderFontSize));
        this.textFormat = textFormat;
    }

    // used by GLTextWidget
    public void drawSplitString(String text, int[] colors) {
        SPRITE_BATCH.begin();
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        this.batchSplitString(SPRITE_BATCH, text, 0.0f, 0.0f, 0f, colors);
        SPRITE_BATCH.end();
    }

    public void batchSplitString(GLRenderBatch batch, String text, float x, float y, int[] colors) {
        this.batchSplitString(batch.impl, text, x, y, 0f, colors);
    }

    public void batchSplitString(GLRenderBatch2 batch, String text, float x, float y, float z, int[] colors) {
        int lineNum = 0;
        final int length = text.length();
        int lineStart = 0;
        int lineChars = 0;
        int c;
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\n') {
                if (lineChars > 0) {
                    if (colors != null)
                        c = colors[MathUtils.clamp(lineNum, 0, colors.length - 1)];
                    else
                        c = Color.WHITE;

                    this.batch(batch,
                            text.substring(lineStart, lineStart+lineChars),
                            x + 0,
                            y + -getBaselineSpacing() * (lineNum + 1),
                            z,
                            Color.red(c) / 255f,
                            Color.green(c) / 255f,
                            Color.blue(c) / 255f,
                            Color.alpha(c) / 255f);
                }

                lineStart = i + 1;
                lineChars = 0;
                lineNum++;
            } else {
                lineChars++;
            }
        }

        if (lineChars > 0) {
            if (colors != null)
                c = colors[MathUtils.clamp(lineNum, 0, colors.length - 1)];
            else
                c = Color.WHITE;

            this.batch(batch,
                    text.substring(lineStart, lineStart+lineChars),
                    x + 0,
                    y + -getBaselineSpacing() * (lineNum + 1),
                    z,
                    Color.red(c) / 255f,
                    Color.green(c) / 255f,
                    Color.blue(c) / 255f,
                    Color.alpha(c) / 255f);
        }
    }

    // used by GLIsoKeyWidget
    public void drawSplitString(String text, float r, float g, float b, float a) {
        SPRITE_BATCH.begin();
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        this.batchSplitString(SPRITE_BATCH, text, 0.0f, 0.0f, 0f, r, g, b, a);
        SPRITE_BATCH.end();
    }

    public void batchSplitString(GLRenderBatch batch, String text, float x, float y, float r,
            float g, float b, float a) {
        this.batchSplitString(batch.impl, text, x, y, 0f, r, g, b, a);
    }

    public void batchSplitString(GLRenderBatch2 batch, String text, float x, float y, float z, float r,
            float g, float b, float a) {
        batch(this.pointer, batch.pointer, text, x, y, z, r, g, b, a);
    }

    // one color per batch
    // moved all gl work into here
    /**
     * Draws the specified string in the current GL context.
     * 
     * @param text The text
     * @param r The red color component for the text
     * @param g The green color component for the text
     * @param b The blue color component for the text
     * @param a The alpha color component for the text
     */
    public void draw(String text, float r, float g, float b, float a) {
        SPRITE_BATCH.begin();
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        this.batch(SPRITE_BATCH, text, 0.0f, 0.0f, 0f, r, g, b, a);
        SPRITE_BATCH.end();
    }

    /**
     * Draws the specified string in the current GL context. A scissor region may be specified
     * whereby any content outside of the region is not rendered. Partial characters may be
     * rendered.
     * 
     * @param text The text
     * @param r The red color component for the text
     * @param g The green color component for the text
     * @param b The blue color component for the text
     * @param a The alpha color component for the text
     * @param scissorX0 The pixel offset, relative to the start of the string, to begin the scissor
     * @param scissorX1 The pixel offset, relative to the start of the string, to end the scissor
     */
    public void draw(String text, float r, float g, float b, float a, float scissorX0,
            float scissorX1) {
        SPRITE_BATCH.begin();
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        SPRITE_BATCH.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        this.batch(SPRITE_BATCH, text, 0.0f, 0.0f, 0f, r, g, b, a, scissorX0,
                scissorX1);
        SPRITE_BATCH.end();
    }

    public void batch(GLRenderBatch batch, String text, float x, float y, float r, float g,
            float b, float a) {
        this.batch(batch, text, x, y, r, g, b, a, 0.0f, Float.MAX_VALUE);
    }
    
    public void batch(GLRenderBatch2 batch, String text, float x, float y, float z, float r, float g,
            float b, float a) {
        this.batch(batch, text, x, y, z, r, g, b, a, 0.0f, Float.MAX_VALUE);
    }

    /**
     * Batches the specified string. A scissor region may be specified whereby any content outside
     * of the region is not batched. Partial characters may be batched.
     * 
     * @param batch The batch
     * @param text The text
     * @param x The x location of the text
     * @param y The y location of the text
     * @param r The red color component for the text
     * @param g The green color component for the text
     * @param b The blue color component for the text
     * @param a The alpha color component for the text
     * @param scissorX0 The pixel offset, relative to the start of the string, to begin the scissor
     * @param scissorX1 The pixel offset, relative to the start of the string, to end the scissor
     */
    public void batch(GLRenderBatch batch, String text, float x, float y, float r, float g,
            float b, float a, float scissorX0, float scissorX1) {

        batch(pointer, batch.impl.pointer, text, x, y, 0f, r, g, b, a, scissorX0, scissorX1);
    }
    
    public void batch(GLRenderBatch2 batch, String text, float x, float y, float z, float r, float g,
            float b, float a, float scissorX0, float scissorX1) {
        batch(pointer, batch.pointer, text, x, y, z, r, g, b, a, scissorX0, scissorX1);
    }

    // the width of the specified string; single line of text
    public final float getStringWidth(String text) {
        return this.textFormat.measureTextWidth(text);
    }

    public float getCharWidth(char chr) {
        return this.textFormat.getCharWidth(chr);
    }

    public float getCharHeight() {
        return this.textFormat.getTallestGlyphHeight();
    }

    // below baseline
    public float getDescent() {
        // return fontDescent;
        // XXX -
        return this.textFormat.getBaselineOffsetFromBottom();
    }

    public float getStringHeight() {
        return this.textFormat.getTallestGlyphHeight();
    }

    public float getStringHeight(final String text) {
        final int limit = text.length();
        int numLines = 1;
        for(int i = 0; i < limit; i++) {
            if(text.charAt(i) == '\n' && i < (limit-1))
                numLines++;
        }
        return numLines*getStringHeight();
    }
    public float getBaselineSpacing() {
        return this.textFormat.getBaselineSpacing();
    }

    /**************************************************************************/

    public static int getLineCount(String text) {
        int numLines = 1;
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\n')
                numLines++;
        return numLines;
    }
    
    public synchronized static void invalidate() {
        for(GLText text : glTextCache.values())
            invalidate(text.pointer);
        glTextCache.clear();
    }

    public static String localize(String text) {
        if(text == null)
            return null;

        StringBuilder retval = new StringBuilder();

        int lineNum = 0;
        final int length = text.length();
        int lineStart = 0;
        int lineChars = 0;
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\n') {
                if (lineChars > 0)
                    retval.append(localizeImpl(text.substring(lineStart, lineStart+lineChars)));
                retval.append('\n');
                lineStart = i + 1;
                lineChars = 0;
                lineNum++;
            } else {
                lineChars++;
            }
        }

        if (lineChars > 0)
            retval.append(localizeImpl(text.substring(lineStart, lineStart+lineChars)));
        
        return retval.toString();
    }

    private static String localizeImpl(String text) {
        char[] textSequence = text.toCharArray();

        int start = -1;
        int end = -1;
        boolean skipspace = true;

        for (int idx = 0; idx <textSequence.length; ++idx) {
            int ch = (int) textSequence[idx];
            //System.out.println("SHB: " + ch + " " + textSequence[idx]);

            // Check if character is used in a right-to-left locale
            // Currently covers Arabic and Hebrew
            if (ch >= 1425 && ch < 1632 || ch > 1641 && ch <= 1901
                    || ch >= 64285 && ch <= 65276
                    || skipspace && Character.isWhitespace(ch)) {
                //System.out.println("SHB: " + ch + " " + textSequence[idx]);
                final int sublen = end - start;
                if (sublen > 1)  {
                    final int halfLength = sublen / 2;
                    for (int i=0; i<halfLength; i++) {
                        final char temp = textSequence[start + i];
                        textSequence[start + i] = textSequence[end-1-i];
                        textSequence[end-1-i] = temp;
                    }
                }
                start = -1;
                end = -1;
                skipspace = true;
            } else {
                 skipspace = false;
                 if (start < 0) 
                    start = idx;
                 end = idx + 1;
            }
        }

        // one final run
        final int sublen = end - start;
        if (sublen > 1)  { 
            final int halfLength = sublen / 2;
            for (int i=0; i<halfLength; i++) {
                 final char temp = textSequence[start + i];
                 textSequence[start + i] = textSequence[end-1-i];
                 textSequence[end-1-i] = temp;
             }
        }
        
        // XXX - the text appears to always be ordered character-wise RTL as a
        //       result of this function
        if(true || LocaleUtil.isRTL()) {
            char[] reverse = new char[textSequence.length];
            for(int i = 0; i < textSequence.length; i++)
                reverse[i] = textSequence[textSequence.length-1-i];
            textSequence = reverse;
        }

        return new String(textSequence);
    }

    static native long intern(MapTextFormat fmt, MapTextFormat glyphRenderer);
    static native void invalidate(long ptr);
    static native void batch(long pointer, long batchPointer, String text, float x, float y, float z, float r, float g, float b, float a);
    static native void batch(long pointer, long batchPointer, String text, float x, float y, float z, float r, float g, float b, float a, float scissorX0, float scissorX1);
}
