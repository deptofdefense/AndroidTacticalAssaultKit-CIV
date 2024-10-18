
package com.atakmap.android.image.equirectangular;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Encapsulates and OpenGL shader program and geometry for rendering a sphere
 * textured on the inside face.
 *
 * Call members only when holding valid GL context!
 */
public class GLTexturedSphereProgram {
    /**
     *
     * Vertex shader - very simple taking input vertices and applying
     * a user-supplied view transformation
     */
    private static final String VERTEX_SHADER = "uniform mat4 viewMatrix;\n" +
            "attribute vec4 attribPos;\n" +
            "attribute vec4 attribTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = viewMatrix * attribPos;\n" +
            "    vTexCoord = (attribTexCoord).xy;\n" +
            "}\n";

    /**
     * fragment shader - samples uniformly from the bound texture using
     * the user-supplied vertex coordinates
     */
    private static final String FRAGMENT_SHADER = "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D sampTex;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sampTex, vTexCoord);\n" +
            "}\n";

    /** Number of bytes in a float */
    private static final int FLOAT_NUM_BYTES = 4;
    /** Handle value for program not loaded */
    private static final int PROGRAM_NOT_LOADED = 0;

    /** OpenGL Handle of our program */
    private int programHandle;
    /** vertex shader handle.  Invalid unless programHandle != PROGRAM_NOT_LOADED */
    private int vertexShader;
    /** fragment shader handle.  Invalid unless programHandle != PROGRAM_NOT_LOADED */
    private int fragmentShader;

    /** handle to vertices.  Invalid unless programHandle != PROGRAM_NOT_LOADED */
    private final int attribPosLocation;
    /** handle to texcoords.  Invalid unless programHandle != PROGRAM_NOT_LOADED */
    private final int attribTexCoordLocation;
    /** handle to view matrix.  Invalid unless programHandle != PROGRAM_NOT_LOADED */
    private final int viewMatrixLocation;

    /** Our dedicated sphere geometry  */
    private final GLSphere sphere;

    /** texture id for our sphere's texture.  Invalid unless programHandle != PROGRAM_NOT_LOADED */
    private final int textureId;

    /** Exception specialization thrown by this class */
    public static class GLProgramException extends Exception {
        public GLProgramException(String reason) {
            super(reason);
        }
    }

    /**
     * Create new textured sphere program.  Uses supplied bitmap for texture if
     * non-null
     * @param texture bitmap to use as sphere's texture or null to defer texture
     *                upload
     * @throws GLProgramException if an error occurs during program creation
     */
    public GLTexturedSphereProgram(Bitmap texture) throws GLProgramException {
        programHandle = PROGRAM_NOT_LOADED;

        createGLProgram(VERTEX_SHADER, FRAGMENT_SHADER);

        attribPosLocation = GLES20.glGetAttribLocation(programHandle,
                "attribPos");
        attribTexCoordLocation = GLES20.glGetAttribLocation(programHandle,
                "attribTexCoord");
        viewMatrixLocation = GLES20.glGetUniformLocation(programHandle,
                "viewMatrix");
        if (attribPosLocation < 0 ||
                viewMatrixLocation < 0 || attribTexCoordLocation < 0) {
            cleanupProgram();
            throw new GLProgramException(
                    "Failed to find location of GL program item(s)");
        }

        try {
            textureId = createTexture();
            if (texture != null)
                uploadTexture(texture);
        } catch (GLProgramException e) {
            cleanupProgram();
            throw e;
        }

        sphere = new GLSphere(1.0f, 36 * 4, 18 * 4);
    }

    /**
     * Utility to create our texture
     * @return textureId of the new texture
     * @throws GLProgramException if an error occurs
     */
    private static int createTexture() throws GLProgramException {
        int[] tArr = new int[1];
        GLES20.glGenTextures(1, tArr, 0);
        checkGLError("Creating GL texture");

        int t = tArr[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t);
        checkGLError("Binding new texture to GL context");

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        checkGLError("Setting texturing parameters");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        return t;
    }

    /**
     * Upload new texture data, replacing any old texture data.
     * @param bitmap new image to use as texture of the sphere
     * @throws GLProgramException if an error occurs
     */
    public void uploadTexture(Bitmap bitmap) throws GLProgramException {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        checkGLError("Binding texture for upload");
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        checkGLError("Uploading new texture");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        checkGLError("Unbinding texture");
    }

    /**
     * Draw the textured sphere using the given view transformation
     * @param viewTransform transformation matrix to apply to sphere
     * @throws GLProgramException if an error occurs while drawing
     */
    public void draw(float[] viewTransform) throws GLProgramException {
        GLES20.glUseProgram(programHandle);
        checkGLError("Setting program for draw");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // Set viewMatrix
        GLES20.glUniformMatrix4fv(viewMatrixLocation, 1, false, viewTransform,
                0);
        checkGLError("Setting view transform in program");

        // Positions
        GLES20.glEnableVertexAttribArray(attribPosLocation);
        checkGLError("Enabling position attribute in program");

        GLES20.glVertexAttribPointer(attribPosLocation,
                GLSphere.COORDS_PER_VERT,
                GLES20.GL_FLOAT, false, GLSphere.VERT_STRIDE,
                sphere.vertBuffer);
        checkGLError("Pointing vertices to position attribute");

        // Texture coords
        GLES20.glEnableVertexAttribArray(attribTexCoordLocation);
        checkGLError(
                "Enabling texture coordinate location attribute in program");
        GLES20.glVertexAttribPointer(attribTexCoordLocation, 2,
                GLES20.GL_FLOAT, false, 2 * FLOAT_NUM_BYTES,
                sphere.texBuffer);
        checkGLError(
                "Pointing texture coordinates to relevant attribute in program");

        // We're looking
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_FRONT);

        // Draw!
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0,
                sphere.vertBuffer.remaining() / 3);
        checkGLError("Drawing GL primitive");

        // Complete
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisableVertexAttribArray(attribPosLocation);
        GLES20.glDisableVertexAttribArray(attribTexCoordLocation);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glUseProgram(0);
    }

    private void cleanupProgram() {
        if (programHandle != PROGRAM_NOT_LOADED) {
            GLES20.glDetachShader(programHandle, vertexShader);
            GLES20.glDetachShader(programHandle, fragmentShader);
            GLES20.glDeleteProgram(programHandle);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            programHandle = PROGRAM_NOT_LOADED;
        }
    }

    /**
     * Create GL program from supplied vertex and fragment shader sources.
     */
    private void createGLProgram(String vertShaderSource,
            String fragShaderSource)
            throws GLProgramException {
        int vertShader = loadGLShader(true, vertShaderSource);
        int fragShader;
        try {
            fragShader = loadGLShader(false, fragShaderSource);
        } catch (GLProgramException ex) {
            GLES20.glDeleteShader(vertShader);
            throw ex;
        }

        int program = GLES20.glCreateProgram();
        try {
            checkGLError("Creating GL program");
            if (program == 0)
                throw new GLProgramException("Failed to create GL program");
        } catch (GLProgramException ex) {
            GLES20.glDeleteShader(vertShader);
            GLES20.glDeleteShader(fragShader);
            throw ex;
        }
        try {
            GLES20.glAttachShader(program, vertShader);
            checkGLError("Attaching vertex shader to program");
        } catch (GLProgramException ex) {
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vertShader);
            GLES20.glDeleteShader(fragShader);
            throw ex;
        }
        try {
            GLES20.glAttachShader(program, fragShader);
            checkGLError("Attaching fragment shader to program");
        } catch (GLProgramException ex) {
            GLES20.glDetachShader(program, vertShader);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vertShader);
            GLES20.glDeleteShader(fragShader);
            throw ex;
        }

        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetProgramInfoLog(program);

            GLES20.glDetachShader(program, vertShader);
            GLES20.glDetachShader(program, fragShader);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vertShader);
            GLES20.glDeleteShader(fragShader);
            throw new GLProgramException("Failed to link GL program " + log);
        }

        // Success!  Note down all the things we need for cleanup
        vertexShader = vertShader;
        fragmentShader = fragShader;
        programHandle = program;
    }

    private static int loadGLShader(boolean isVertex, String source)
            throws GLProgramException {
        int shader = GLES20.glCreateShader(
                isVertex ? GLES20.GL_VERTEX_SHADER : GLES20.GL_FRAGMENT_SHADER);
        checkGLError(
                "Creating " + (isVertex ? "vertex" : "fragment") + " shader");
        try {
            GLES20.glShaderSource(shader, source);
            checkGLError("Setting " + (isVertex ? "vertex" : "fragment")
                    + " shader source");
            GLES20.glCompileShader(shader);
            int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS,
                    compileStatus, 0);
            if (compileStatus[0] != GLES20.GL_TRUE) {
                String log = GLES20.glGetShaderInfoLog(shader);
                String msg = "Failed to compile " +
                        (isVertex ? "vertex" : "fragment") + " shader (" + log
                        + ")";
                throw new GLProgramException(msg);
            }
            return shader;
        } catch (GLProgramException ex) {
            GLES20.glDeleteShader(shader);
            throw ex;
        }
    }

    /**
     * Utility to check the current thread's GL error state.
     *
     * @param errPreface string to preface to any generated exception's reason
     * @throws GLProgramException if current thread's GL error state is not
     *         "GL_NO_ERROR".
     */
    private static void checkGLError(String errPreface)
            throws GLProgramException {
        int err = GLES20.glGetError();
        if (err != GLES20.GL_NO_ERROR) {
            String e = errPreface + " failed (GL error code = " +
                    Integer.toHexString(err) + ")";
            throw new GLProgramException(e);
        }
    }

    /**
     * A sphere geometry drawn as piecewise triangles.  The sphere's texture
     * coordinates will wrap a 2D image around it by mapping the image's x/y
     * to the sphere's x/y.  Note that the image is mapped using the inside of
     * the sphere.
     */
    private static class GLSphere {
        /**
         * Number of coordinates per vertex in our vertex buffers
         */
        public static final int COORDS_PER_VERT = 3;
        /**
         * Stride for our vertex buffers in terms of number of bytes
         */
        public static final int VERT_STRIDE = COORDS_PER_VERT * FLOAT_NUM_BYTES;

        /**
         * Vertex buffer's byte based backer
         */
        private ByteBuffer vertBufferBytes;

        /**
         * Texture coordinate buffer's byte based backer
         */
        private ByteBuffer texBufferBytes;

        // Float-wrapped versions of above two
        /**
         * Vertex buffer float value view
         */
        public FloatBuffer vertBuffer;
        /**
         * Texture coordinate buffer float value view.
         */
        public FloatBuffer texBuffer;

        /**
         * Radius of the sphere
         */
        private final float radius;
        /**
         * Number of geometries around the sphere
         * in the X direction (aka longitude)
         */
        private final int sectors;
        /**
         * Number of geometries around the sphere
         * in the Y direction (aka latitude)
         */
        private final int stacks;

        public GLSphere(float radius, int sectors, int stacks) {
            this.radius = radius;
            this.sectors = sectors;
            this.stacks = stacks;
            build();
        }

        /**
         *  Copy vertex data from the vertNum'th vertex in the source arrays
         * vertices and texCoords.  Appends to the main geometry buffers
         */
        private void expandVertex(int vertNum,
                float[] vertices, float[] texCoords) {
            vertBuffer.put(vertices[vertNum * 3]);
            vertBuffer.put(vertices[vertNum * 3 + 1]);
            vertBuffer.put(vertices[vertNum * 3 + 2]);
            texBuffer.put(texCoords[vertNum * 2]);
            texBuffer.put(texCoords[vertNum * 2 + 1]);
        }

        /**
         * Create and populate all geometry buffers.
         * Based on code from http://www.songho.ca/opengl/gl_sphere.html
         */
        private void build() {
            double sectorStep = 2 * Math.PI / sectors;
            double stackStep = Math.PI / stacks;

            float[] vertices = new float[(stacks + 1) * (sectors + 1) * 3];
            float[] texCoords = new float[(stacks + 1) * (sectors + 1) * 2];
            int n = 0;
            int tn = 0;

            for (int i = 0; i <= stacks; ++i) {
                double stackAngle = Math.PI / 2 - i * stackStep;
                double xz = radius * Math.cos(stackAngle);
                double y = radius * Math.sin(stackAngle);

                for (int j = 0; j <= sectors; ++j) {
                    double sectorAngle = j * sectorStep;

                    double x = xz * Math.cos(sectorAngle);
                    double z = -1 * xz * Math.sin(sectorAngle);
                    vertices[n++] = (float) x;
                    vertices[n++] = (float) y;
                    vertices[n++] = (float) z;

                    texCoords[tn++] = (float) j / sectors;
                    texCoords[tn++] = (float) i / stacks;
                }
            }

            // Now using the lat/stack, lon/sector based vertices array
            // expand out into a series of triangles 
            // Note: first and last stack get one triplet/triangle, all others get 2
            vertBufferBytes = ByteBuffer.allocateDirect(
                    FLOAT_NUM_BYTES * (stacks - 1) * sectors * 2 * 3 * 3);
            vertBufferBytes.order(ByteOrder.nativeOrder());
            vertBuffer = vertBufferBytes.asFloatBuffer();
            texBufferBytes = ByteBuffer.allocateDirect(
                    FLOAT_NUM_BYTES * (stacks - 1) * sectors * 2 * 3 * 2);
            texBufferBytes.order(ByteOrder.nativeOrder());
            texBuffer = texBufferBytes.asFloatBuffer();

            for (int i = 0; i < stacks; ++i) {
                int topLeft = i * (sectors + 1);
                int bottomLeft = topLeft + sectors + 1;

                for (int j = 0; j < sectors; ++j) {
                    if (i != 0) {
                        expandVertex(topLeft, vertices, texCoords);
                        expandVertex(bottomLeft, vertices, texCoords);
                        expandVertex(topLeft + 1, vertices, texCoords);
                    }
                    if (i != (stacks - 1)) {
                        expandVertex(topLeft + 1, vertices, texCoords);
                        expandVertex(bottomLeft, vertices, texCoords);
                        expandVertex(bottomLeft + 1, vertices, texCoords);
                    }
                    ++topLeft;
                    ++bottomLeft;
                }

            }
            vertBuffer.position(0);
            texBuffer.position(0);
        }

    }

}
