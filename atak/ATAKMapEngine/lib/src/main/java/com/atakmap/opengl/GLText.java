
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
import com.atakmap.math.MathUtils;

public final class GLText {

    private final static int CHAR_BATCH_SIZE = 80; // number of characters to render per batch

    private final static GLRenderBatch2 SPRITE_BATCH = new GLRenderBatch2(CHAR_BATCH_SIZE*6);

    private final static float[] SCRATCH_MATRIX = new float[16];

    protected static final String TAG = "GLText";

    private final static int CHAR_START = 32; // first character (ASCII Code)
    private final static int CHAR_END = 126; // last character (ASCII Code)

    private static Map<Long, GLText> glTextCache = new HashMap<Long, GLText>();

    // NOTE: marked as static since 'batchImpl' is non-reentrant and
    //       non-recursive; there is no need to make these instance
    private static FloatBuffer trianglesVerts;
    private static FloatBuffer trianglesTexCoords;
    private static ShortBuffer trianglesIndices;
    
    private static long trianglesVertsPtr;
    private static long trianglesTexCoordsPtr;
    private static long trianglesIndicesPtr;

    /**************************************************************************/

    private final MapTextFormat textFormat;
    private int cachedFontSize;
    private float fontScalar;


    private GLTextureAtlas glyphAtlas;

    private static final Map<Integer, GLTextureAtlas> glyphAtlasCache = new HashMap<>();

    private float charMaxHeight;

    private float[] commonCharUV;
    private int[] commonCharTexId;
    private float[] commonCharWidth;

    private RectF scratchCharBounds;


    private static final int[] sizeBins = new int[] { 0, 8, 16, 32, 64, 128 };



    /**
     * Provided a text size, find the most appropriate GlTextCache to use to provide for optimal
     * results.  The range of returns are from 0..128.
     * @param textSize the input textSize
     * @return find the best font size based on a provided text size.
     */
    private static int findBestFontSize(int textSize) {
        if (textSize > 0) {
            // Scale input text size by factor of 2 if the screen density is lower than 2
            // This improves readability of smaller font sizes - see ATAK-12517
            int ts = textSize * (AtakMapView.DENSITY < 2 ? 2 : 1);
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
        GLText customGLText = glTextCache.get(Long.valueOf(((long) typeface.hashCode() << 32)
                | (long) textSize));
        if (customGLText == null) {
            customGLText = new GLText(textFormat, typeface, textSize);
            glTextCache.put(Long.valueOf(((long) typeface.hashCode() << 32) | (long) textSize),
                    customGLText);
        }
        
        if(trianglesIndices == null) {
            ByteBuffer buf;
            
            buf = com.atakmap.lang.Unsafe.allocateDirect(CHAR_BATCH_SIZE*4 * 4*3);
            buf.order(ByteOrder.nativeOrder());
            
            trianglesVerts = buf.asFloatBuffer();
            trianglesVertsPtr = Unsafe.getBufferPointer(trianglesVerts);
            
            buf = com.atakmap.lang.Unsafe.allocateDirect(CHAR_BATCH_SIZE*4 * 4*2);
            buf.order(ByteOrder.nativeOrder());
            
            trianglesTexCoords = buf.asFloatBuffer();
            trianglesTexCoordsPtr = Unsafe.getBufferPointer(trianglesTexCoords);
            
            buf = com.atakmap.lang.Unsafe.allocateDirect(CHAR_BATCH_SIZE*6 * 2);
            buf.order(ByteOrder.nativeOrder());
            
            trianglesIndices = buf.asShortBuffer();
            trianglesIndicesPtr = Unsafe.getBufferPointer(trianglesIndices);
        }

        return customGLText;
    }

    // does not load the texture, that is done on gl thread in draw method
    // initializes all the font metrics so that the getters work
    private GLText(MapTextFormat textFormat, Typeface typeface, float densityAdjustedTextSize) {
        cachedFontSize = findBestFontSize(textFormat.getFontSize());

        this.textFormat = new MapTextFormat(textFormat.getTypeface(), cachedFontSize);
        // caches the text with a font size that can be scaled appropriately.
        fontScalar = textFormat.getFontSize() / (float)cachedFontSize;



        this.charMaxHeight = this.textFormat.getTallestGlyphHeight();

        final int numCommonChars = CHAR_END - CHAR_START + 1;
        this.commonCharTexId = new int[numCommonChars];
        this.commonCharUV = new float[numCommonChars * 4];
        this.commonCharWidth = new float[numCommonChars];

        this.scratchCharBounds = new RectF();
        
        // XXX - safeguard against possibly exceeding texture atlas size -- we
        //       need to cap font size somewhere though!!!


        this.glyphAtlas = glyphAtlasCache.get(cachedFontSize);
        if (glyphAtlas == null) {
            this.glyphAtlas = new GLTextureAtlas((int) Math.max(256, this.charMaxHeight * 2), true);
            glyphAtlasCache.put(cachedFontSize, glyphAtlas);
        }
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

                    this.batchImpl(batch,
                            x + 0,
                            y + -getBaselineSpacing() * (lineNum + 1),
                            z,
                            text,
                            lineStart,
                            lineChars,
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

            this.batchImpl(batch,
                    x + 0,
                    y + -getBaselineSpacing() * (lineNum + 1),
                    z,
                    text,
                    lineStart,
                    lineChars,
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
        int lineNum = 0;
        final int length = text.length();
        int lineStart = 0;
        int lineChars = 0;
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\n') {
                if (lineChars > 0) {
                    this.batchImpl(batch,
                            x + 0.0f,
                            y + -getBaselineSpacing() * (lineNum + 1),
                            z,
                            text,
                            lineStart,
                            lineChars,
                            r, g, b, a);
                }

                lineStart = i + 1;
                lineChars = 0;
                lineNum++;
            } else {
                lineChars++;
            }
        }

        if (lineChars > 0) {
            this.batchImpl(batch,
                    x + 0.0f,
                    y + -getBaselineSpacing() * (lineNum + 1),
                    z,
                    text,
                    lineStart,
                    lineChars,
                    r, g, b, a);
        }
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
        this.batchImpl(SPRITE_BATCH, 0.0f, 0.0f, 0f, text, 0, text.length(), r, g, b, a, scissorX0,
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
        this.batchImpl(batch.impl, x, y, 0f, text, 0, text.length(), r, g, b, a, scissorX0, scissorX1);
    }
    
    public void batch(GLRenderBatch2 batch, String text, float x, float y, float z, float r, float g,
            float b, float a, float scissorX0, float scissorX1) {
        this.batchImpl(batch, x, y, z, text, 0, text.length(), r, g, b, a, scissorX0, scissorX1);
    }

    private void batchImpl(GLRenderBatch2 batch, float tx, float ty, float tz, String text, int off, int len,
            float r, float g, float b, float a) {
        this.batchImpl(batch, tx, ty, tz, text, off, len, r, g, b, a, 0.0f, Float.MAX_VALUE);
    }

    private void batchImpl(GLRenderBatch2 batch, float tx, float ty, float tz, String text, int off, int len,
            float r, float g, float b, float a, float scissorX0, float scissorX1) {

        final int size = (tz == 0f) ? 2 : 3;

        final float fontPadX = 2;
        final float fontPadY = 2;

        float cellWidth;
        float cellHeight = this.getCharHeight();// + (2*fontPadY);

        float charAdjX; // adjust start X
        float charAdjY = (cellHeight / 2.0f) - fontPadY; // adjust start Y
        charAdjY -= (this.textFormat.getBaselineOffsetFromBottom() - fontPadY) * fontScalar;

        float letterX, letterY;
        letterX = letterY = 0;

        int c;
        String uri;
        long key;
        int texId;
        float charWidth;
        final float texSize = this.glyphAtlas.getTextureSize();

        float u0;
        float v0;
        float u1;
        float v1;
        int trianglesTexId = 0;
        int numBufferedChars = 0;

        // shrink the font.
        batch.pushMatrix(GLES10.GL_MODELVIEW);

        // reset the matrix and set up for the rotation
        Matrix.setIdentityM(SCRATCH_MATRIX, 0);
        Matrix.translateM(SCRATCH_MATRIX, 0, -tx, -ty, -tz);
        Matrix.scaleM(SCRATCH_MATRIX, 0, fontScalar,fontScalar, fontScalar);
        Matrix.translateM(SCRATCH_MATRIX, 0, tx, ty, tz);

        for (int i = 0; i < len; i++) { // for each character in string
            c = (int) text.charAt(i + off);
            if (c >= CHAR_START && c <= CHAR_END) {
                c -= CHAR_START;
                texId = this.commonCharTexId[c];
                if (texId == 0) {
                    key = this.loadGlyph(String.valueOf(c), text, i + off);

                    texId = this.glyphAtlas.getTexId(key);

                    this.commonCharWidth[c] = this.glyphAtlas.getImageWidth(key);
                    this.commonCharTexId[c] = texId;

                    this.glyphAtlas.getImageRect(key, true, this.scratchCharBounds);
                    this.commonCharUV[c * 4] = this.scratchCharBounds.left;
                    this.commonCharUV[c * 4 + 1] = this.scratchCharBounds.bottom;
                    this.commonCharUV[c * 4 + 2] = this.scratchCharBounds.right;
                    this.commonCharUV[c * 4 + 3] = this.scratchCharBounds.top;
                }

                charWidth = this.commonCharWidth[c];

                u0 = this.commonCharUV[c * 4];
                v0 = this.commonCharUV[c * 4 + 1];
                u1 = this.commonCharUV[c * 4 + 2];
                v1 = this.commonCharUV[c * 4 + 3];
            } else {
                uri = String.valueOf(c);
                key = this.glyphAtlas.getTextureKey(uri);
                if (key == 0L) // load the glyph
                    key = this.loadGlyph(uri, text, i + off);

                this.glyphAtlas.getImageRect(key, false, this.scratchCharBounds);
                charWidth = this.scratchCharBounds.width();
                texId = this.glyphAtlas.getTexId(key);

                u0 = this.scratchCharBounds.left / texSize;
                v0 = this.scratchCharBounds.bottom / texSize;
                u1 = this.scratchCharBounds.right / texSize;
                v1 = this.scratchCharBounds.top / texSize;
            }


            // convert to the actual font size based on the values obtained from the cached font size
            // which is larger.
            charWidth*=fontScalar;





            cellWidth = charWidth;
            charAdjX = (cellWidth / 2.0f) - fontPadX;
            if ((letterX + charWidth) >= scissorX0 && letterX < scissorX1) {
                float x0 = charAdjX + letterX - cellWidth / 2.0f;
                float y0 = charAdjY + letterY - cellHeight / 2.0f;
                float x1 = charAdjX + letterX + cellWidth / 2.0f;
                float y1 = charAdjY + letterY + cellHeight / 2.0f;

                // adjust the vertex and text coordinates to account for
                // scissoring
                if (letterX < scissorX0) {
                    u0 += (scissorX0 - x0) / texSize;
                    x0 = scissorX0;
                }
                if ((letterX + charWidth) > scissorX1) {
                    u1 -= (x1 - scissorX1) / texSize;
                    x1 = scissorX1;
                }

                // draw the character

                if(trianglesTexId == 0) {
                    trianglesTexId = texId;
                } else if(trianglesTexId != texId || numBufferedChars == CHAR_BATCH_SIZE) {
                    // NOTE: position should always be at zero, we only ever
                    //       modify limit
                    trianglesVerts.limit(numBufferedChars*4*size);
                    trianglesTexCoords.limit(numBufferedChars*8);
                    trianglesIndices.limit(numBufferedChars*6);


                    batch.batch(trianglesTexId,
                                GLES20FixedPipeline.GL_TRIANGLES,
                                size,
                                0, trianglesVerts,
                                0, trianglesTexCoords,
                                trianglesIndices,
                                r, g, b, a);
                    
                    trianglesTexId = texId;
                    
                    numBufferedChars = 0;
                }


                bufferChar(size,
                           trianglesVertsPtr + (numBufferedChars*4*size*4),
                           trianglesTexCoordsPtr + (numBufferedChars<<5),
                           trianglesIndicesPtr + ((6*numBufferedChars)<<1),
                           numBufferedChars,
                           tx + x0, ty + y0,
                           tx + x1, ty + y1,
                           tz,
                           u0, v0,
                           u1, v1);

                numBufferedChars++;
            }

            // advance X position by scaled character width
            letterX += charWidth;
            if (letterX > scissorX1)
                break;
        }
        
        if(numBufferedChars > 0) {
            // NOTE: position should always be at zero, we only ever modify
            //       limit
            trianglesVerts.limit(numBufferedChars*4*size);
            trianglesTexCoords.limit(numBufferedChars*8);
            trianglesIndices.limit(numBufferedChars*6);

            batch.batch(trianglesTexId,
                        GLES20FixedPipeline.GL_TRIANGLES,
                        size,
                        0, trianglesVerts,
                        0, trianglesTexCoords,
                        trianglesIndices,
                        r, g, b, a);
        }
        batch.popMatrix(GLES10.GL_MODELVIEW);
    }

    // the width of the specified string; single line of text
    public final float getStringWidth(String text) {
        return this.textFormat.measureTextWidth(text) * fontScalar;
    }

    public float getCharWidth(char chr) {
        return this.textFormat.getCharWidth(chr) * fontScalar;
    }

    public float getCharHeight() {
        return this.textFormat.getTallestGlyphHeight() * fontScalar;
    }

    // below baseline
    public float getDescent() {
        // return fontDescent;
        // XXX -
        return this.textFormat.getBaselineOffsetFromBottom() * fontScalar;
    }

    public float getStringHeight() {
        return this.textFormat.getTallestGlyphHeight() * fontScalar;
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
        return this.textFormat.getBaselineSpacing() * fontScalar;
    }

    private long loadGlyph(final String entryUri, final String s, final int off) {
        Bitmap glyphBitmap = null;
        Canvas canvas;
        try {
            glyphBitmap = Bitmap.createBitmap(
                    (int) Math.max(Math.ceil(this.textFormat.getCharWidth(s.charAt(off))),1),
                    (int) Math.ceil(this.charMaxHeight), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(glyphBitmap);
            this.textFormat.renderGlyph(canvas, s, off, 0, 0);

            return this.glyphAtlas.addImage(entryUri, glyphBitmap);
        } finally {
            if (glyphBitmap != null)
                glyphBitmap.recycle();
        }
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
        for(GLText text : glTextCache.values()) {
            text.glyphAtlas.release();
            text.charMaxHeight = text.textFormat.getTallestGlyphHeight();
            Arrays.fill(text.commonCharTexId, 0);
        }
    }
    
    private static native void bufferChar(int size, long texVertsPtr, long texCoordsPtr, long texIndicesPtr, int n, float x0, float y0, float x1, float y1, float z, float u0, float v0, float u1, float v1);
    
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
}
