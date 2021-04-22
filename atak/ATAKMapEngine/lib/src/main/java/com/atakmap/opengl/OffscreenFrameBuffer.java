package com.atakmap.opengl;

import android.opengl.GLES30;

import com.atakmap.math.MathUtils;

public final class OffscreenFrameBuffer {
    public final static class Options {
        public int depthFormat = GLES30.GL_NONE;
        public int depthInternalFormat = GLES30.GL_NONE;
        public int depthType = GLES30.GL_NONE;
        public int colorFormat = GLES30.GL_NONE;
        public int colorInternalFormat = GLES30.GL_NONE;
        public int colorType = GLES30.GL_NONE;
        public int stencilFormat = GLES30.GL_NONE;
        public int stencilInternalFormat = GLES30.GL_NONE;
        public int stencilType = GLES30.GL_NONE;
    }

    public int handle = GLES30.GL_NONE;
    public int width;
    public int height;
    public int textureHeight;
    public int textureWidth;
    public int colorTexture = GLES30.GL_NONE;
    public int depthTexture = GLES30.GL_NONE;
    public int stencilTexture = GLES30.GL_NONE;

    public void bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, handle);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT|GLES30.GL_STENCIL_BUFFER_BIT);
    }

    public void release() {
        int[] textures = new int[] {colorTexture, depthTexture, stencilTexture};
        GLES30.glDeleteTextures(3, textures, 0);
        colorTexture = GLES30.GL_NONE;
        depthTexture = GLES30.GL_NONE;
        stencilTexture = GLES30.GL_NONE;

        GLES30.glDeleteFramebuffers(1, new int[] {handle}, 0);
        handle = GLES30.GL_NONE;
    }

    private static int createTexture(int width, int height, int format, int internalFormat, int type, boolean linear) {
        int[] tex = new int[1];
        final int filter = linear ? GLES30.GL_LINEAR : GLES30.GL_NEAREST;
        if(internalFormat == GLES30.GL_NONE)
            internalFormat = format;

        GLES30.glGenTextures(1, tex, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0]);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat,
                width, height, 0,
                format, type, null);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        return tex[0];
    }

    public static OffscreenFrameBuffer create(int width, int height, Options options) {
        int[] current = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_DRAW_FRAMEBUFFER_BINDING, current, 0);
        try {
            return createImpl(width, height, options);
        } finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, current[0]);
        }
    }

    private static OffscreenFrameBuffer createImpl(int width, int height, Options options) {
        OffscreenFrameBuffer result = new OffscreenFrameBuffer();
        result.width = width;
        result.height = height;
        result.textureWidth = MathUtils.isPowerOf2(width) ? width : 1<<MathUtils.nextPowerOf2(width);
        result.textureHeight = MathUtils.isPowerOf2(height) ? height : 1<<MathUtils.nextPowerOf2(height);

        int[] fbo = new int[3];
        
        if(options.colorFormat != GLES30.GL_NONE)
            result.colorTexture = createTexture(result.textureWidth, result.textureHeight, options.colorFormat, options.colorInternalFormat, options.colorType, true);
        if(options.depthFormat != GLES30.GL_NONE)
            result.depthTexture = createTexture(result.textureWidth, result.textureHeight, options.depthFormat, options.depthInternalFormat, options.depthType, false);
        if(options.stencilFormat != GLES30.GL_NONE)
            result.stencilTexture = createTexture(result.textureWidth, result.textureHeight, options.stencilFormat, options.stencilInternalFormat, options.stencilType, false);

        boolean fboCreated = false;
        do {
            // clear any pending errors
            while(GLES30.glGetError() != GLES30.GL_NO_ERROR)
                ;
            
            GLES30.glGenFramebuffers(1, fbo, 0);
            result.handle = fbo[0];

            if (options.depthFormat == GLES30.GL_NONE) {
                GLES30.glGenRenderbuffers(1, fbo, 1);
                GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, fbo[1]);
                GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,
                        GLES30.GL_DEPTH_COMPONENT24,
                        result.textureWidth, result.textureHeight);
                GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);
            }

            // bind the FBO and set all texture attachments
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, result.handle);

            // clear any pending errors
            while(GLES30.glGetError() != GLES30.GL_NO_ERROR)
                ;

            if(result.colorTexture != GLES30.GL_NONE) {
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                        GLES30.GL_COLOR_ATTACHMENT0,
                        GLES30.GL_TEXTURE_2D, result.colorTexture, 0);
            }

            // XXX - observing hard crash following bind of "complete"
            //       FBO on SM-T230NU. reported error is 1280 (invalid
            //       enum) on glFramebufferTexture2D. I have tried using
            //       the color-renderable formats required by GLES 2.0
            //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
            //       the same outcome.
            if(GLES30.glGetError() != GLES30.GL_NO_ERROR)
                break;

            if(result.depthTexture != GLES30.GL_NONE) {
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                        GLES30.GL_DEPTH_ATTACHMENT,
                        GLES30.GL_TEXTURE_2D, result.depthTexture, 0);
            } else {
                GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, fbo[1]);
            }
            if(GLES30.glGetError() != GLES30.GL_NO_ERROR)
                break;

            if(result.stencilTexture != GLES30.GL_NONE) {
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                        GLES30.GL_STENCIL_ATTACHMENT,
                        GLES30.GL_TEXTURE_2D, result.stencilTexture, 0);
            }
            if(GLES30.glGetError() != GLES30.GL_NO_ERROR)
                break;

            final int fboStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            if(fboStatus != GLES30.GL_FRAMEBUFFER_COMPLETE)
                break;

            return result;
        } while(false);

        result.release();
        return null;
    }
}
