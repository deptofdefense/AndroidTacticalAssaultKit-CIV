
package com.atakmap.android.model.viewer.processing;

import android.opengl.GLES30;

public class ShaderHelper {
    /**
     * Helper method to compile shader
     *
     * @param shaderType The shader type
     * @param shaderSource The shader source code
     * @return An OpenGL handle to the shader
     */
    public static int compileShader(final int shaderType,
            final String shaderSource) {
        int shaderHandle = GLES30.glCreateShader(shaderType);

        String error = "Failed to create shader";
        if (shaderHandle != 0) {
            GLES30.glShaderSource(shaderHandle, shaderSource); //pass in the shader source
            GLES30.glCompileShader(shaderHandle); //compile the shader

            //get the compilation status
            final int[] compileStatus = new int[1];
            GLES30.glGetShaderiv(shaderHandle, GLES30.GL_COMPILE_STATUS,
                    compileStatus, 0);

            //delete shader if compilation failed
            if (compileStatus[0] == 0) {
                error = GLES30.glGetShaderInfoLog(shaderHandle);
                GLES30.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }

        if (shaderHandle == 0) {
            throw new RuntimeException("Error creating shader: " + error);
        }

        return shaderHandle;
    }

    /**
     * Helper method to compile and link a program
     *
     * @param vertexShaderHandle An OpenGL handle to an already-compiled vertex shader
     * @param fragmentShaderHandle An OpenGL handle to an already-compiled fragment shader
     * @param attributes Attributes that need to be bound to the program
     * @return An OpenGL handle to the program
     */
    public static int createAndLinkProgram(final int vertexShaderHandle,
            final int fragmentShaderHandle, final String[] attributes) {
        int programHandle = GLES30.glCreateProgram();

        if (programHandle != 0) {
            GLES30.glAttachShader(programHandle, vertexShaderHandle); //bind the vertex shader to the program
            GLES30.glAttachShader(programHandle, fragmentShaderHandle); //bind the fragment shader to the program

            //bind the attributes
            if (attributes != null) {
                for (int i = 0; i < attributes.length; i++)
                    GLES30.glBindAttribLocation(programHandle, i,
                            attributes[i]);
            }

            //link the two shaders together in a program
            GLES30.glLinkProgram(programHandle);

            //get the link status
            final int[] linkStatus = new int[1];
            GLES30.glGetProgramiv(programHandle, GLES30.GL_LINK_STATUS,
                    linkStatus, 0);

            //delete the program if the link failed
            if (linkStatus[0] == 0) {
                GLES30.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }

        if (programHandle == 0)
            throw new RuntimeException("Error creating program");

        return programHandle;
    }
}
