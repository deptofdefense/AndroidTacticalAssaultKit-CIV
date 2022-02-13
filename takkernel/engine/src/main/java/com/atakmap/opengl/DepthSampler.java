package com.atakmap.opengl;

import android.graphics.Color;
import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.util.Disposable;

import java.nio.IntBuffer;

public final class DepthSampler implements Disposable {
    final static int VIEWPORT_WIDTH = 4;
    final static int VIEWPORT_HEIGHT = 4;

    private final static String DEPTH_VERT_SHADER =
            "precision highp float;\n" +
            "uniform mat4 uMVP;\n" +
            "attribute vec3 aVertexCoords;\n" +
            "varying highp float vDepth;\n" +
            "void main() {\n" +
            "  vec4 vcoords = uMVP * vec4(aVertexCoords.xyz, 1.0);\n" +
            "  vDepth = ((vcoords.z / vcoords.w) + 1.0) * 0.5;\n" +
            "  gl_Position = vcoords;\n" +
            "}";

    private final static String DEPTH_FRAG_SHADER_SRC =
            "precision mediump float;\n" +
            "vec4 PackDepth(in float v)\n" +
            "{\n" +
            "  float v_a = floor(v);\n" +
            "  float v_r = floor(fract(v) * 255.0) / 255.0;\n" +
            "  float v_g = floor(fract(fract(v) * 255.0) * 255.0) / 255.0;\n" +
            "  float v_b = floor(fract(fract(fract(v) * 255.0) * 255.0) * 255.0) / 255.0;\n" +
            "  return vec4(v_r, v_g, v_b, v_a);" +
            "}\n" +
            "varying highp float vDepth;\n" +
            "void main(void) {\n" +
            "  vec4 depth = PackDepth(vDepth);\n" +
            "  gl_FragColor = depth;\n" +
            "}";

    public final Program program;

    final OffscreenFrameBuffer fbo;
    int[] viewport = new int[4];
    IntBuffer pixel;
    int[] boundFbo = new int[1];
    boolean blendEnabled;

    private DepthSampler(Program program, OffscreenFrameBuffer fbo) {
        this.program = program;
        this.fbo = fbo;
        pixel = Unsafe.allocateDirect(1, IntBuffer.class);
    }

    /**
     * Begins the depth sampling process, with the specified viewport space coordinate (lower-left origin) to sample for depth at. The method, {@link #end()} must once the sampling process is complete.
     *
     * <P>The client must observe the following after invoking {@link #begin(float, float)} and before invoking {@link #end()}
     * <UL>
     *     <LI>Do not invoke {@link GLES30#glViewport(int, int, int, int)}
     *     <LI>Do not invoke {@link GLES30#glScissor(int, int, int, int)}
     *     <LI>Do not invoke {@link GLES30#glDisable(int)} with {@link GLES30#GL_SCISSOR_TEST}
     *     <LI>Do not invoke {@link GLES30#glEnable(int)} with {@link GLES30#GL_BLEND}
     *     <LI>Do not invoke {@link GLES30#glUseProgram(int)}
     * </UL>
     * @param x
     * @param y
     */
    public void begin(float x, float y) {
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0);
        GLES30.glGetIntegerv(GLES30.GL_DRAW_FRAMEBUFFER_BINDING, boundFbo, 0);
        blendEnabled = GLES30.glIsEnabled(GLES30.GL_BLEND);

        fbo.bind();

        GLES30.glViewport(-(int)x+fbo.width/2, -(int)y+fbo.height/2, viewport[2], viewport[3]);

        GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
        GLES30.glScissor(fbo.width/2-1, fbo.height/2-1, 3, 3);

        GLES30.glDisable(GLES30.GL_BLEND);

        GLES30.glUseProgram(program.handle);
    }

    /**
     * Retrieves the 'z' coordinate, in clipspace, for the pixel specified in {@link #begin(float, float)}}.
     *
     * <P>This method must be invoked after {@link #begin(float, float)} and before {@link #end()}.
     * @return
     */
    public float getClipSpaceZ() {
        return getDepth() * 2.0f - 1.0f;
    }

    /**
     * Retrieves the 'z' coordinate, as a depth value, for the pixel specified in {@link #begin(float, float)}}
     * <P>This method must be invoked after {@link #begin(float, float)} and before {@link #end()}.
     * @return
     */
    public float getDepth() {
        GLES30.glReadPixels(fbo.width/2, fbo.height/2, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel);
        final int val = pixel.get(0);

        // unpack the pixel into the depth value
        float a = Color.alpha(val)/255f;
        float b = Color.red(val)/255f;
        float g = Color.green(val)/255f;
        float r = Color.blue(val)/255f;

        return a + r + g / (255f) + b / (255f * 255f);
    }

    public void end() {
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
        GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        if(blendEnabled)
            GLES30.glEnable(GLES30.GL_BLEND);

        // reset draw FBO binding
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, boundFbo[0]);
    }

    @Override
    public void dispose() {
        fbo.release();
        if(program.handle != GLES30.GL_NONE)
            GLES30.glDeleteProgram(program.handle);
    }

    public static DepthSampler create() {
        OffscreenFrameBuffer.Options opts = new OffscreenFrameBuffer.Options();
        opts.colorFormat = GLES30.GL_RGBA;
        opts.colorInternalFormat = GLES30.GL_RGBA8;
        opts.colorType = GLES30.GL_UNSIGNED_BYTE;
        // XXX - depth texture not yielding as much precision as render buffer;
        //       need to investigate further
        //opts.depthFormat = GLES30.GL_DEPTH_COMPONENT;
        //opts.depthInternalFormat = GLES30.GL_DEPTH_COMPONENT24;
        //opts.depthType = GLES30.GL_INT;
        OffscreenFrameBuffer fbo = OffscreenFrameBuffer.create(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, opts);
        if(fbo == null)
            return null;

        return new DepthSampler(new Program(), fbo);
    }

    public static class Program {
        public final int handle;
        public final int aVertexCoords;
        public final int uMVP;

        Program() {
            int vertShader = GLES20FixedPipeline.GL_NONE;
            int fragShader = GLES20FixedPipeline.GL_NONE;
            try {
                vertShader = GLES20FixedPipeline.loadShader(GLES20FixedPipeline.GL_VERTEX_SHADER, DEPTH_VERT_SHADER);
                fragShader = GLES20FixedPipeline.loadShader(GLES20FixedPipeline.GL_FRAGMENT_SHADER, DEPTH_FRAG_SHADER_SRC);

                handle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
                aVertexCoords = GLES20FixedPipeline.glGetAttribLocation(handle, "aVertexCoords");
                uMVP = GLES20FixedPipeline.glGetUniformLocation(handle, "uMVP");
            } finally {
                if(vertShader != GLES20FixedPipeline.GL_NONE)
                    GLES20FixedPipeline.glDeleteShader(vertShader);
                if(fragShader != GLES20FixedPipeline.GL_NONE)
                    GLES20FixedPipeline.glDeleteShader(fragShader);
            }
        }
    }
}
