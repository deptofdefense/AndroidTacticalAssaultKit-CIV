
package com.atakmap.android.helloworld;

import android.opengl.GLES20;

import java.nio.IntBuffer;

public class ShaderInfo {
    public static final String imageVertexShaderString = "attribute vec4 position;\n"
            +
            "varying vec2 textureCoordinate;\n" +
            "\n" +
            "uniform vec2 xyscale, center;\n" +
            "uniform float rot;\n" +
            "uniform float multip, addv;\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "  float cs = cos(rot), sn = sin(rot);\n" +
            "  float posx = position.x * cs - position.y * sn;\n" +
            "  float posy = position.x * sn + position.y * cs;\n" +
            "  gl_Position = vec4( ( ( ( center.x * multip ) + addv) + xyscale.x*posx ), (center.y + xyscale.y*posy), position.z, position.w);\n"
            +
            "  textureCoordinate = (position.yx + 1.) / 2.;\n" +
            // TEXTURE REQUIRES VERTICAL FLIP
            "  textureCoordinate.y = 1.0 - textureCoordinate.y;\n" +
            "}";
    public static final String imageFragmentShaderString = "precision mediump float;\n"
            +
            "varying vec2 textureCoordinate;\n" +
            "uniform sampler2D s_texture;\n" +
            "uniform float alpha, no_alpha;\n" +
            "void main() {\n" +
            "  vec4 col = texture2D( s_texture, textureCoordinate );\n" +
            "  gl_FragColor = vec4(col.r, col.g, col.b, (1.- no_alpha) * alpha + no_alpha * col.a);\n"
            +
            "}\n";

    static public int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        IntBuffer statusBuf = IntBuffer.allocate(1);
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, statusBuf);
        int status = statusBuf.array()[0];
        if (status != 0) {
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                String infoLog = GLES20.glGetShaderInfoLog(shader);
                System.out.println("loadShader: error=" + error + " status="
                        + status + " type=" + type + " shaderCode=" + shaderCode
                        + "\ninfoLog=" + infoLog);
                Thread.currentThread().dumpStack();
            }
        }
        return shader;
    }

    static public int loadImageShader() {
        int vshaderid = loadShader(GLES20.GL_VERTEX_SHADER,
                imageVertexShaderString);
        int fshaderid = loadShader(GLES20.GL_FRAGMENT_SHADER,
                imageFragmentShaderString);
        if (vshaderid >= 0 && fshaderid >= 0) {
            int mProgram = GLES20.glCreateProgram(); // create empty OpenGL ES Program
            GLES20.glAttachShader(mProgram, vshaderid); // add the vertex shader to program
            int errv = GLES20.glGetError();
            GLES20.glAttachShader(mProgram, fshaderid); // add the fragment shader to program
            int errf = GLES20.glGetError();
            GLES20.glLinkProgram(mProgram);
            int err = GLES20.glGetError();
            if (err != 0 || errv != 0 || errf != 0) {
                System.out.println("loadImageShader: err=" + err + " errv="
                        + errv + " errf=" + errf);
            }
            return mProgram;
        }
        return -1;
    }
}
