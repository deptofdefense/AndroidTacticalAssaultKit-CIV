
package com.atakmap.android.model.viewer;

import android.content.Context;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import androidx.annotation.NonNull;

import com.atakmap.android.model.viewer.processing.ShaderHelper;
import com.atakmap.android.model.viewer.processing.TextureHelper;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.math.MathUtils;

import java.nio.Buffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ModelRenderer implements GLSurfaceView.Renderer {

    private final static String FRAGMENT_SHADER_SOURCE = ""
            //set the default precision to medium
            + "precision mediump float;\n"
            //the input texture
            + "uniform sampler2D u_Texture;\n"
            //interpolated position for this fragment
            + "varying vec3 v_Position;\n"
            //interpolated texture coordinate per fragment
            + "varying vec2 v_TexCoordinate;\n"
            + "void main()\n"
            + "{\n"
            //final output color
            + "gl_FragColor = texture2D(u_Texture, v_TexCoordinate);\n"
            + "}\n";
    private final static String VERTEX_SHADER_SOURCE = ""
            //a constant representing the combined model/view/projection matrix
            + "uniform mat4 u_MVPMatrix;\n"
            //a constant representing the combined model/view matrix
            + "uniform mat4 u_MVMatrix;\n"
            //per-vertex position information we will pass in
            + "attribute vec4 a_Position;\n"
            //per-vertex normal information we will pass in
            + "attribute vec2 a_TexCoordinate;\n"
            // These will be passed into the fragment shader
            + "varying vec3 v_Position;\n"
            + "varying vec2 v_TexCoordinate;\n"
            + "void main()\n"
            + "{\n"
            //transform vertex into eye space
            + "v_Position = vec3(u_MVMatrix * a_Position);\n"
            //pass through the texture coordinate
            + "v_TexCoordinate = a_TexCoordinate;\n"
            //store final position of model
            + "gl_Position = u_MVPMatrix * a_Position;\n"
            + "}\n";
    private final Context activityContext;

    /** Store model matrix. Moves models from object space to world space */
    private final float[] modelMatrix = new float[16];

    /** Used as our camera. Transforms world space to eye space */
    private final float[] viewMatrix = new float[16];

    /** Ussed to project the scene onto a 2D viewport*/
    private final float[] projectionMatrix = new float[16];

    /** Combines model, view, and projection matrices to be passed into the shaders*/
    private final float[] mvpMatrix = new float[16];

    private final float[] accumulatedRotation = new float[16];
    private final float[] currentRotation = new float[16];

    private final float[] tempMatrix = new float[16];

    /** Used to pass their respective data into the shaders*/
    private int mvpMatrixHandle;
    private int mvMatrixHandle;
    private int positionHandle;
    private int texCoodsHandle;
    private int programHandle;
    private int pngDataHandle;

    //still work without volatile, but refreshed are not guaranteed to happen
    public volatile float deltaX;
    public volatile float deltaY;

    private com.atakmap.map.layer.model.Model objModel;
    private final Camera camera = new Camera();
    private int width;
    private int height;

    public ModelRenderer(final Context activityContext) {
        this.activityContext = activityContext;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        //Set background color to black
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);

        //read, compile, and link shaders to our program handle
        final String vertexShader = VERTEX_SHADER_SOURCE;
        final String fragmentShader = FRAGMENT_SHADER_SOURCE;
        final int vertexShaderHandle = ShaderHelper
                .compileShader(GLES30.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle = ShaderHelper
                .compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader);
        this.programHandle = ShaderHelper.createAndLinkProgram(
                vertexShaderHandle, fragmentShaderHandle, new String[] {
                        "a_Position", "a_TexCoordinate"
                });

        //load and generate model texture
        this.pngDataHandle = TextureHelper
                .loadTextureFromPath(Models.getTextureUri(objModel));
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);

        Matrix.setIdentityM(accumulatedRotation, 0); //initialize accumulated rotation matrix
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = width;
        this.height = height;

        //set OpenGL viewport to the same size as the surface
        GLES30.glViewport(0, 0, width, height);

        //initialize the view matrix (our camera)
        Matrix.setLookAtM(viewMatrix, 0, camera.xPos, camera.yPos, camera.zPos,
                camera.xView, camera.yView, camera.zView, camera.xUp,
                camera.yUp, camera.zUp);

        //wrap new perspective projection matrix based off the surface
        final float ratio = (float) width / height;
        // Matrix.frustumM(projectionMatrix,0, -ratio, ratio,-1f,1f,targetNear,targetFar);
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 1000f);

        camera.projection = projectionMatrix;
        camera.width = width;
        camera.height = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        //if any camera values change, apply those changes to our view matrix
        synchronized (camera) {
            if (camera.hasChanged()) {
                Matrix.setLookAtM(viewMatrix, 0, camera.xPos, camera.yPos,
                        camera.zPos, camera.xView, camera.yView, camera.zView,
                        camera.xUp, camera.yUp, camera.zUp);
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix,
                        0);
                camera.setChanged(false);
            }
        }

        GLES30.glUseProgram(programHandle);

        //set program handles for model drawing
        this.mvpMatrixHandle = GLES30.glGetUniformLocation(programHandle,
                "u_MVPMatrix");
        this.mvMatrixHandle = GLES30.glGetUniformLocation(programHandle,
                "u_MVMatrix");
        int textureUniformHandle = GLES30.glGetUniformLocation(programHandle,
                "u_Texture");
        this.positionHandle = GLES30.glGetAttribLocation(programHandle,
                "a_Position");
        this.texCoodsHandle = GLES30.glGetAttribLocation(programHandle,
                "a_TexCoordinate");

        //draw and translate model into the screen
        Matrix.setIdentityM(modelMatrix, 0);

        // XXX - adapted from helper function that was modifying vertex
        //       coordinates -- instead just modify the transform

        //ensure our model is scaled to fit the view
        if (resetCamForModel) {
            Envelope aabb = objModel.getAABB();

            float scaleFactor = 1f;
            float largest = (float) Math.max(aabb.maxX - aabb.minX,
                    Math.max(aabb.maxY - aabb.minY, aabb.maxZ - aabb.minZ));

            if (largest != 0f)
                scaleFactor = 3f / largest;

            float centerX = (float) (aabb.minX + aabb.maxX) / 2f;
            float centerY = (float) (aabb.minY + aabb.maxY) / 2f;
            float centerZ = (float) (aabb.minZ + aabb.maxZ) / 2f;

            camera.maxDim = largest;

            camera.xPos += centerX;
            camera.yPos += centerY;
            camera.zPos += centerZ;
            camera.xView += centerX;
            camera.yView += centerY;
            camera.zView += centerZ;
            camera.moveCameraZ(1f / scaleFactor);
            camera.setChanged(true);

            resetCamForModel = false;
        }

        //initialize matrix that contains the current rotation; rotation is performed about the
        // "lookat" location
        Matrix.setIdentityM(currentRotation, 0);
        Matrix.translateM(currentRotation, 0, camera.xView, camera.yView,
                camera.zView);
        Matrix.rotateM(currentRotation, 0, deltaX, 0f, 1f, 0f);
        Matrix.rotateM(currentRotation, 0, deltaY, 1f, 0f, 0f);
        Matrix.translateM(currentRotation, 0, -camera.xView, -camera.yView,
                -camera.zView);
        deltaX = 0f;
        deltaY = 0f;

        //multiply current rotation by accumulated rotation and set the accumulated rotation to the result
        Matrix.multiplyMM(tempMatrix, 0, currentRotation, 0,
                accumulatedRotation, 0);
        System.arraycopy(tempMatrix, 0, accumulatedRotation, 0, 16);

        //rotate the model
        Matrix.multiplyMM(tempMatrix, 0, modelMatrix, 0, accumulatedRotation,
                0);
        System.arraycopy(tempMatrix, 0, modelMatrix, 0, 16);

        //set and bind the active texture unit, then pass texture into shader
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pngDataHandle);
        GLES30.glUniform1i(textureUniformHandle, 0);

        drawModel();
    }

    /*
     * helper method to draw and connect the model's vertices
     */
    private void drawModel() {
        final VertexDataLayout layout = objModel.getVertexDataLayout();

        if (MathUtils.hasBits(layout.attributes, Mesh.VERTEX_ATTR_POSITION)) {
            // XXX - this is assuming that buffer is ByteBuffer
            Buffer modelVertices = objModel
                    .getVertices(Mesh.VERTEX_ATTR_POSITION);
            modelVertices.position(layout.position.offset);

            GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT,
                    false,
                    layout.position.stride, modelVertices);
            GLES30.glEnableVertexAttribArray(positionHandle);
        }

        if (MathUtils.hasBits(layout.attributes, Mesh.VERTEX_ATTR_TEXCOORD_0)) {
            //pass in texture coordinate information
            Buffer modelTexCoords = objModel
                    .getVertices(Mesh.VERTEX_ATTR_TEXCOORD_0);
            modelTexCoords.position(layout.texCoord0.offset);

            GLES30.glVertexAttribPointer(texCoodsHandle, 2, GLES30.GL_FLOAT,
                    false,
                    layout.texCoord0.stride, modelTexCoords);
            GLES30.glEnableVertexAttribArray(texCoodsHandle);
        }

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        GLES30.glUniformMatrix4fv(mvMatrixHandle, 1, false, mvpMatrix, 0);

        Matrix.multiplyMM(tempMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);
        System.arraycopy(tempMatrix, 0, mvpMatrix, 0, 16);

        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        int mode;
        switch (objModel.getDrawMode()) {
            case Triangles:
                mode = GLES30.GL_TRIANGLES;
                break;
            case TriangleStrip:
                mode = GLES30.GL_TRIANGLE_STRIP;
                break;
            default:
                return;
        }

        if (objModel.isIndexed()) {
            GLES30.glDrawElements(mode, Models.getNumIndices(objModel),
                    GLES30.GL_UNSIGNED_INT, objModel.getIndices());
        } else {
            GLES30.glDrawArrays(mode, 0, objModel.getNumVertices());
        }
    }

    /**
     * Returns this renderer's camera object
     *
     * @return The camera
     */
    public Camera getCamera() {
        return this.camera;
    }

    /**
     * Return's the width of this renderer's surface
     *
     * @return The width of the surface
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Return's the height of this renderer's surface
     *
     * @return The height of the surface
     */
    public int getHeight() {
        return this.height;
    }

    public void setModel(@NonNull Model model) {
        this.objModel = model;
        resetCamForModel = true;
    }

    boolean resetCamForModel = false;
}
