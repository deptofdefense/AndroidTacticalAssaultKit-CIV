package com.atakmap.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.atakmap.lang.Unsafe;

import android.opengl.GLES30;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class GLLineBatch {

    private final static String LINES_BATCH_VERTEX_SHADER =
            "uniform mat4 uProjection;\n" +
            "uniform mat4 uModelView;\n" +
            "attribute vec2 aVertexCoords;\n" +
            "attribute vec4 aColor;\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n" +
            "  vColor = aColor;\n" +
            "}";

    private final static String LINES_BATCH_FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec4 vColor;\n" +
            "void main(void) {\n" +
            "  gl_FragColor = vColor;\n" +
            "}";

    private final static float[] SCRATCH_MATRIX = new float[16];
    
    private final static int LINES_VERTEX_SIZE = 8 + // x,y floats
                                                 4;  // r,g,b,a bytes

    private ByteBuffer linesBuffer;
    
    private float lineWidth;

    protected int programHandle;
    protected int uProjectionHandle;
    protected int uModelViewHandle;
    protected int aVertexCoordsHandle;
    protected int aColorHandle;
    
    public GLLineBatch(int capacity) {
        this.linesBuffer = com.atakmap.lang.Unsafe.allocateDirect((capacity&~1)*LINES_VERTEX_SIZE);
        this.linesBuffer.order(ByteOrder.nativeOrder());
    }

    protected void initProgram() {
        final int vertShader = GLES20FixedPipeline.loadShader(GLES20FixedPipeline.GL_VERTEX_SHADER,
                LINES_BATCH_VERTEX_SHADER);
        final int fragShader = GLES20FixedPipeline.loadShader(
                GLES20FixedPipeline.GL_FRAGMENT_SHADER, LINES_BATCH_FRAGMENT_SHADER);

        this.programHandle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
        GLES20FixedPipeline.glUseProgram(this.programHandle);

        this.uProjectionHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                "uProjection");
        this.uModelViewHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                "uModelView");
        this.aVertexCoordsHandle = GLES20FixedPipeline.glGetAttribLocation(this.programHandle,
                "aVertexCoords");
        this.aColorHandle = GLES20FixedPipeline.glGetAttribLocation(this.programHandle, "aColor");
    }

    public void begin() {
        this.linesBuffer.clear();

        if (this.programHandle == 0)
            this.initProgram();
        else
            GLES20FixedPipeline.glUseProgram(this.programHandle);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(this.uProjectionHandle, 1, false, SCRATCH_MATRIX, 0);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(this.uModelViewHandle, 1, false, SCRATCH_MATRIX, 0);
        
        float[] v = new float[1];
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_LINE_WIDTH, v, 0);
        this.lineWidth = v[0];
        
    }
    
    public void end() {
        this.flush();
    }
    
    protected void flush() {
        this.linesBuffer.flip();
        if (this.linesBuffer.remaining() > 0) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline.glVertexAttribPointer(this.aVertexCoordsHandle, 2,
                    GLES20FixedPipeline.GL_FLOAT,
                    false, LINES_VERTEX_SIZE, this.linesBuffer.position(0));

            GLES20FixedPipeline.glVertexAttribPointer(this.aColorHandle, 4,
                    GLES20FixedPipeline.GL_UNSIGNED_BYTE,
                    true, LINES_VERTEX_SIZE, this.linesBuffer.position(8));

            GLES20FixedPipeline.glEnableVertexAttribArray(this.aVertexCoordsHandle);
            GLES20FixedPipeline.glEnableVertexAttribArray(this.aColorHandle);

            // invoke GLES30 explicitly as GLES20FixedPipeline will performed a
            // fixed pipeline draw
            GLES30.glDrawArrays(GLES20FixedPipeline.GL_LINES,
                    0,
                    this.linesBuffer.limit() / LINES_VERTEX_SIZE);

            GLES20FixedPipeline.glDisableVertexAttribArray(this.aVertexCoordsHandle);
            GLES20FixedPipeline.glDisableVertexAttribArray(this.aColorHandle);
            
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }
        this.linesBuffer.clear();
    }
    
    protected void validateLineWidth(float width) {
        if(width != this.lineWidth) {
            this.flush();
        
            this.lineWidth = width;
            GLES20FixedPipeline.glLineWidth(this.lineWidth);
        }
    }
    
    public void addLine(float x0, float y0, float x1, float y1, float width, float r, float g, float b, float a) {
        if(this.linesBuffer.remaining() < (LINES_VERTEX_SIZE*2))
            this.flush();
        
        this.addLineNative(this.linesBuffer,
                            this.linesBuffer.position(),
                            this.linesBuffer.limit(),
                            x0, y0,
                            x1, y1,
                            r, g, b, a);
    }
    
    private native void addLineNative(ByteBuffer linesBuffer, int linesBufferPos, int linesBufferLimit, float x0, float y0, float x1, float y1, float r, float g, float b, float a);

    public void addLines(FloatBuffer lines, float width, float r, float g, float b, float a) {
        this.validateLineWidth(width);

        final int numSegments = lines.remaining() / 4;
        int linesIdx = lines.position();
        
        final byte red = (byte)(r*255.0f);
        final byte green = (byte)(g*255.0f);
        final byte blue = (byte)(b*255.0f);
        final byte alpha = (byte)(a*255.0f);
        
        for(int i = 0; i < numSegments; i++) {
            if(this.linesBuffer.remaining() < (LINES_VERTEX_SIZE*2))
                this.flush();
            
            this.linesBuffer.putFloat(lines.get(linesIdx++));
            this.linesBuffer.putFloat(lines.get(linesIdx++));
            this.linesBuffer.put(red);
            this.linesBuffer.put(green);
            this.linesBuffer.put(blue);
            this.linesBuffer.put(alpha);
            
            this.linesBuffer.putFloat(lines.get(linesIdx++));
            this.linesBuffer.putFloat(lines.get(linesIdx++));
            this.linesBuffer.put(red);
            this.linesBuffer.put(green);
            this.linesBuffer.put(blue);
            this.linesBuffer.put(alpha);
        }
    }
    
    private native void addLinesNative(ByteBuffer linesBuffer, int linesBufferPos, int linesBufferLimit, FloatBuffer lines, int linesPos, int linesRemaining, float r, float g, float b, float a);
    
    private void copyLineStripPoints(FloatBuffer linestrip, float r, float g, float b, float a, ByteBuffer dest, boolean flushWhenFull){
        final int numSegments = (linestrip.remaining() / 2)-1;
        int linestripStart = linestrip.position();
        
        int destPosStart = dest.position();
        
        final byte[] colorBytes = new byte[]{
                (byte)(r*255.0f),
                (byte)(g*255.0f),
                (byte)(b*255.0f),
                (byte)(a*255.0f)};
        
        float[] cache = new float[4];
        for(int i = 0; i < numSegments; i++) {
            if((dest.remaining() < (LINES_VERTEX_SIZE*2))){
                if(flushWhenFull){
                    this.flush();
                }else{
                    dest.position(destPosStart);
                    return;
                }
            }
            
            linestrip.get(cache, 0, 4);
            
            Unsafe.putFloats(dest, cache[0], cache[1]);
            Unsafe.put(dest, colorBytes);

            Unsafe.putFloats(dest, cache[2], cache[3]);
            Unsafe.put(dest, colorBytes);
            
            linestrip.position(linestrip.position() - 2);
        }
        
        dest.position(destPosStart);
        linestrip.position(linestripStart);
    }
    
    public void addLineStrip(FloatBuffer linestrip, float width, float r, float g, float b, float a) {
        this.validateLineWidth(width);
        
        copyLineStripPoints(linestrip, r, g, b, a, this.linesBuffer, true);
    }
    
    public void addLineStrip(FloatBuffer linestrip, float width, float r, float g, float b, float a, ByteBuffer output){
        this.validateLineWidth(width);
        copyLineStripPoints(linestrip, r, g, b, a, output, false);
        addLineStripFormatted(output, width);
    }
    
    public void addLineStripFormatted(ByteBuffer linestrip, float width){
        this.validateLineWidth(width);
        
        int toCopy = linestrip.remaining();
        int startPos = linestrip.position();
        int startLimit = linestrip.limit();
        
        while(toCopy > 0){
            int length = 0;
            boolean flush = false;
            if(toCopy < this.linesBuffer.remaining()){
                length = toCopy;
            }else{
                length = this.linesBuffer.remaining();
                flush = true;
            }
            
            linestrip.limit(linestrip.position() + length);
            this.linesBuffer.put(linestrip);
            toCopy = toCopy - length;
            
            if(flush){
                flush();
                flush = false;
            }
        }
        
        linestrip.position(startPos);
        linestrip.limit(startLimit);
    }
    
    private native void addLinestripNative(ByteBuffer linesBuffer, int linesBufferPos, int linesBufferLimit, FloatBuffer linestrip, int linestripPos, int linesRemaining, float r, float g, float b, float a);
}
