package gov.tak.api.engine;

import com.atakmap.coremap.log.Log;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.engine.map.RenderContext;
import gov.tak.platform.commons.opengl.GLES30;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public class Shader {

    boolean textured, alphaDiscard, colorPointer, lighting, is2d, point;
    public final static String TAG = "Shader";

    static Map<RenderContext, Map<Integer, Shader>> shaders = new HashMap<>();

    public static final int FLAG_TEXTURED = 1;
    public static final int FLAG_ALPHA_DISCARD = 2;
    public static final int FLAG_COLOR_POINTER = 4;
    public static final int FLAG_NORMAL = 8;
    public static final int FLAG_2D = 16;
    public static final int FLAG_POINT = 32;

    private static int loadShader(int type, String source) {
        final int retval = GLES30.glCreateShader(type);
        GLES30.glShaderSource(retval, source);
        GLES30.glCompileShader(retval);

        int[] success = new int[1];
        GLES30.glGetShaderiv(retval, GLES30.GL_COMPILE_STATUS, success, 0);
        if (success[0] == 0) {
            Log.d(TAG, "FAILED TO LOAD SHADER: " + source);
            final String msg = GLES30.glGetShaderInfoLog(retval);
            GLES30.glDeleteShader(retval);
            throw new RuntimeException(msg);
        }
        return retval;
    }

    private int create(String vertSrc, String fragSrc) {
        final int vertShader = loadShader(GLES30.GL_VERTEX_SHADER, vertSrc);
        final int fragShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragSrc);

        try {
            return this.createProgram(vertShader, fragShader);
        } catch (RuntimeException e) {
            GLES30.glDeleteShader(vertShader);
            GLES30.glDeleteShader(fragShader);
        }
        return -1;
    }

    private static int createProgram(int vertShader, int fragShader) {
        int retval = GLES30.glCreateProgram();
        if (retval == GLES30.GL_FALSE)
            throw new RuntimeException();

        GLES30.glAttachShader(retval, vertShader);
        GLES30.glAttachShader(retval, fragShader);
        GLES30.glLinkProgram(retval);

        int[] success = new int[1];
        GLES30.glGetProgramiv(retval, GLES30.GL_LINK_STATUS, success, 0);

        if (success[0] == 0) {
            final String msg = GLES30.glGetProgramInfoLog(retval);
            GLES30.glDeleteProgram(retval);
            throw new RuntimeException(msg);
        }
        return retval;
    }

    public static Shader create(int flags)
    {
        return create(flags, null);
    }

    public static Shader create(int flags, RenderContext ctx)
    {
        synchronized (shaders)
        {
            Map<Integer, Shader> ctxShaders = shaders.get(ctx);
            if(ctxShaders == null)
                shaders.put(ctx, ctxShaders=new HashMap<>());
            if(!ctxShaders.containsKey(flags))
                ctxShaders.put(flags, new Shader(flags));

            return ctxShaders.get(flags);
        }
    }

    public static void clearAll() {
        synchronized (shaders) {
            shaders.clear();
        }
    }

    private Shader(int flags)
    {
        this.textured = (flags & FLAG_TEXTURED) != 0;
        this.alphaDiscard = (flags & FLAG_ALPHA_DISCARD) != 0;
        this.colorPointer = (flags & FLAG_COLOR_POINTER) != 0;
        this.lighting = (flags & FLAG_NORMAL) != 0;
        this.is2d = (flags & FLAG_2D) != 0;
        this.point = (flags & FLAG_POINT) != 0;

        // vertex shader source
        String  vshsrc = new String();
        vshsrc += "#version 100\n";
        vshsrc += "uniform mat4 uProjection;\n";
        vshsrc += "uniform mat4 uModelView;\n";
        if(textured) {
            if(!is2d) {
                vshsrc += "uniform mat4 uTextureMx;\n";
            }
            vshsrc += "attribute vec2 aTextureCoords;\n";
            vshsrc += "varying vec2 vTexPos;\n";
        }
        if(point) {
            vshsrc += "uniform float uPointSize;\n";
        }
        if(is2d)
            vshsrc += "attribute vec2 aVertexCoords;\n";
        else
            vshsrc += "attribute vec3 aVertexCoords;\n";
        if(colorPointer) {
            vshsrc += "attribute vec4 aColorPointer;\n";
            vshsrc += "varying vec4 vColor;\n";
        }
        if(lighting) {
            vshsrc += "attribute vec3 aNormals;\n";
            vshsrc += "varying vec3 vNormal;\n";
        }
        vshsrc += "void main() {\n";
        if(textured) {
            if(is2d) {
                vshsrc += "  vec4 texCoords = vec4(aTextureCoords.xy, 0.0, 1.0);\n";
            }
            else {
                vshsrc += "  vec4 texCoords = uTextureMx * vec4(aTextureCoords.xy, 0.0, 1.0);\n";
            }
            vshsrc += "  vTexPos = texCoords.xy;\n";
        }
        if(colorPointer)
            vshsrc += "  vColor = aColorPointer;\n";
        if(lighting)
            vshsrc += "  vNormal = normalize(mat3(uProjection * uModelView) * aNormals);\n";
        if(is2d)
            vshsrc += "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xy, 0.0, 1.0);\n";
        else
            vshsrc += "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n";

        if(point) {
            vshsrc += "  gl_PointSize = uPointSize;\n";
        }

        vshsrc += "}";

        // fragment shader source
        String fshsrc = new String();
        fshsrc += "#version 100\n";
        fshsrc += "precision mediump float;\n";
        if(textured) {
            fshsrc += "uniform sampler2D uTexture;\n";
            fshsrc += "varying vec2 vTexPos;\n";
        }
        if(colorPointer)
            fshsrc += "varying vec4 vColor;\n";
        else
            fshsrc += "uniform vec4 uColor;\n";
        if(alphaDiscard)
            fshsrc += "uniform float uAlphaDiscard;\n";
        if(lighting)
            fshsrc += "varying vec3 vNormal;\n";
        fshsrc += "void main(void) {\n";
        // XXX - color pointer is NOT working with modulation, don't use it
        //       with texturing right now either until issues can be tested
        //       and resolved
        if(textured && point)
            fshsrc += "  vec4 color = texture2D(uTexture, gl_PointCoord);\n";
        else if(textured && colorPointer)
            fshsrc += "  vec4 color = texture2D(uTexture, vTexPos) * vColor;\n";
        else if(textured && !colorPointer)
            fshsrc += "  vec4 color = texture2D(uTexture, vTexPos);\n";
        else if(colorPointer)
            fshsrc += "  vec4 color = vColor;\n";
        else
            fshsrc += "  vec4 color = vec4(1.0, 1.0, 1.0, 1.0);\n";
        // XXX - should discard be before or after modulation???
        if(alphaDiscard) {
            fshsrc += "  if(color.a < uAlphaDiscard)\n";
            fshsrc += "    discard;\n";
        }
        if(lighting) {
            // XXX - next two as uniforms
            fshsrc += "  vec3 sun_position = vec3(3.0, 10.0, -5.0);\n";
            fshsrc += "  vec3 sun_color = vec3(1.0, 1.0, 1.0);\n";
            fshsrc += "  float lum = max(dot(vNormal, normalize(sun_position)), 0.0);\n";
            fshsrc += "  color = color * vec4((0.6 + 0.4 * lum) * sun_color, 1.0);\n";
        }
        if(colorPointer) {
            fshsrc += "  gl_FragColor = color;\n";
        }
        else {
            fshsrc += "  gl_FragColor = uColor * color;\n";
        }
        fshsrc += "}";

        handle = create(vshsrc, fshsrc);

        uProjection = GLES30.glGetUniformLocation(handle, "uProjection");
        uModelView = GLES30.glGetUniformLocation(handle, "uModelView");
        uTextureMx = GLES30.glGetUniformLocation(handle, "uTextureMx");
        uTexture = GLES30.glGetUniformLocation(handle, "uTexture");
        uAlphaDiscard = GLES30.glGetUniformLocation(handle, "uAlphaDiscard");
        uColor = GLES30.glGetUniformLocation(handle, "uColor");
        aVertexCoords = GLES30.glGetAttribLocation(handle, "aVertexCoords");
        aTextureCoords = GLES30.glGetAttribLocation(handle, "aTextureCoords");
        aColorPointer = GLES30.glGetAttribLocation(handle, "aColorPointer");
        aNormals = GLES30.glGetAttribLocation(handle, "aNormals");
        uPointSize = GLES30.glGetUniformLocation(handle, "uPointSize");
    }

    public int useProgram(boolean bUse) {
        IntBuffer intbuf = IntBuffer.allocate(1);
        GLES30.glGetIntegerv(GLES30.GL_CURRENT_PROGRAM,intbuf);

        GLES30.glUseProgram(bUse ? handle : 0);
        return intbuf.get(0);
    }

    public void setColor4f(float r, float g, float b, float a) {
        GLES30.glUniform4f(getUColor(), r, g, b, a);
    }

    public void setProjection(float[] projection) {
        gov.tak.platform.commons.opengl.GLES30.glUniformMatrix4fv(uProjection, 1, false, projection, 0);
    }

    public void setModelView(float[] modelView) {
        gov.tak.platform.commons.opengl.GLES30.glUniformMatrix4fv(uModelView, 1, false, modelView, 0);
    }

    public int getHandle() {
        return handle;
    }

    public int getUProjection() {
        return uProjection;
    }

    public int getUModelView() {
        return uModelView;
    }

    public int getUTexture() {
        return uTexture;
    }

    public int getUAlphaDiscard() {
        return uAlphaDiscard;
    }

    public int getUColor() {
        return uColor;
    }

    public int getUTextureMx() {
        return uTextureMx;
    }

    public int getAColorPointer() {
        return aColorPointer;
    }

    public int getATextureCoords() {
        return aTextureCoords;
    }

    public int getAVertexCoords(){
        return aVertexCoords;
    }

    /** @deprecated use {@link } */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public int getAPointSize() {
        return getUPointSize();
    }

    public int getUPointSize() {
        return uPointSize;
    }


    /** GL_NONE when uninitialized */
    private int handle = GLES30.GL_NONE;
    // uniforms
    private int uProjection =  -1;;
    private int uModelView =  -1;
    private int uTextureMx =  -1;
    private int uTexture =  -1;
    private int uAlphaDiscard =  -1;
    private int uColor =  -1;
    private int aVertexCoords =  -1;
    private int aTextureCoords = -1;
    private int aColorPointer = -1;
    private int aNormals = -1;
    private int uPointSize = -1;
}
