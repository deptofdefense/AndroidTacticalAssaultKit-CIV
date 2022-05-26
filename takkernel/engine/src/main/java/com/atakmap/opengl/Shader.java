package com.atakmap.opengl;

import android.opengl.GLES30;

import gov.tak.api.engine.map.RenderContext;

import java.util.IdentityHashMap;
import java.util.Map;

public class Shader {
    final static String VERTEX_SHADER_SRC =             // vertex shader source
            "#version 100\n" +
            "uniform mat4 uMVP;\n" +
            "attribute vec2 aTextureCoords;\n" +
            "varying vec2 vTexPos;\n" +
            "attribute vec3 aVertexCoords;\n" +
            "attribute vec4 aColorPointer;\n" +
            "varying vec4 vColor;\n" +
            "attribute vec3 aNormals;\n" +
            "varying vec3 vNormal;\n" +
            "void main() {\n" +
            "  vec4 texCoords = vec4(aTextureCoords.xy, 0.0, 1.0);\n" +
            "  vTexPos = texCoords.xy;\n" +
            "  vColor = aColorPointer;\n" +
            "  vNormal = normalize(mat3(uMVP) * aNormals);\n" +
            "  gl_Position = uMVP * vec4(aVertexCoords.xyz, 1.0);\n" +
            "}";

    // XXX - color pointer is NOT working with modulation, don't use it
    //       with texturing right now either until issues can be tested
    //       and resolved

    final static String FRAGMENT_SHADER_SRC = // fragment shader source
            "#version 100\n" +
            "precision mediump float;\n" +
            "uniform sampler2D uTexture;\n" +
            "varying vec2 vTexPos;\n" +
            "uniform vec4 uColor;\n" +
            "varying vec4 vColor;\n" +
            "varying vec3 vNormal;\n" +
            "void main(void) {\n" + 
            "  vec4 color = texture2D(uTexture, vTexPos) * vColor;\n" +
            "  gl_FragColor = uColor * color;\n" +
            "}";

    private final static Map<RenderContext, Shader> shaders = new IdentityHashMap<>();

    /** GL_NONE when uninitialized */
    public int handle = GLES30.GL_NONE;
    // uniforms
    public int uMVP = -1;
    public int uInvModelView = -1;
    public int uTexture = -1;
    public int uColor = -1;
    // attributes
    public int aVertexCoords = -1;
    public int aTexCoords = -1;
    public int aNormals = -1;
    public int aColors = -1;

    public Shader() {
        int vsh = GLES20FixedPipeline.GL_NONE;
        int fsh = GLES20FixedPipeline.GL_NONE;
        try {
            vsh = GLES20FixedPipeline.loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_SRC);
            fsh = GLES20FixedPipeline.loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC);
            handle = GLES20FixedPipeline.createProgram(vsh, fsh);
        } finally {
            if(vsh != GLES30.GL_NONE)
                GLES30.glDeleteShader(vsh);
            if(fsh != GLES30.GL_NONE)
                GLES30.glDeleteShader(fsh);
        }

        uMVP = GLES30.glGetUniformLocation(handle, "uMVP");
        uTexture = GLES30.glGetUniformLocation(handle, "uTexture");
        uColor = GLES30.glGetUniformLocation(handle, "uColor");
        aVertexCoords = GLES30.glGetAttribLocation(handle, "aVertexCoords");
        aTexCoords = GLES30.glGetAttribLocation(handle, "aTextureCoords");
        aColors = GLES30.glGetAttribLocation(handle, "aColorPointer");
        aNormals = GLES30.glGetAttribLocation(handle, "aNormals");
    }

    public synchronized static Shader get(RenderContext ctx) {
        Shader shader = shaders.get(ctx);
        if(shader == null) {
            shader = new Shader();
            shaders.put(ctx, shader);
        }
        return shader;
    }
}
