
package com.atakmap.android.vehicle.model.opengl;

import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A single renderable used for multiple instances of a map item
 */
public abstract class GLInstancedRenderable implements GLMapRenderable2 {

    private static final String TAG = "GLInstancedRenderable";

    // Sizes
    protected static final int SIZE_BUF = 32;
    protected static final int SIZE_VEC2 = 8;
    protected static final int SIZE_VEC3 = 12;
    protected static final int SIZE_VEC4 = 16;
    protected static final int SIZE_MAT4 = 64;

    protected final String _name;
    protected final int _renderPass;

    protected Integer _programID;
    protected int _drawVersion;
    protected final List<GLInstanceData> _drawInstances = new ArrayList<>();
    protected final List<GLInstanceData> _pendingInstances = new ArrayList<>();
    protected int[] _vao;
    protected int _instanceLimit;
    protected LocalCoordinateSystem _lcs = new LocalCoordinateSystem();
    protected boolean _released;

    // Transform buffer
    protected int[] _mxBufferID;
    protected int _mxBufferSize;
    protected long _mxBufferPtr;
    protected FloatBuffer _mxBuffer;

    public GLInstancedRenderable(String name, int renderPass) {
        _name = name;
        _renderPass = renderPass;
    }

    @Override
    public int getRenderPass() {
        return _renderPass;
    }

    /**
     * Reset instance data
     */
    public void reset() {
        for (GLInstanceData data : _pendingInstances)
            data.setDirty(false);
        _pendingInstances.clear();
        _drawVersion = -1;
    }

    /**
     * Add an instance of this mesh to be drawn
     * @param instance Instance data
     */
    public void addInstance(GLInstanceData instance) {
        _pendingInstances.add(instance);
    }

    /**
     * Get the number of instances to draw
     * Use this for your glDrawArraysInstanced call
     * @return Number of instances to draw
     */
    protected int getNumInstances() {
        return _drawInstances.size();
    }

    /**
     * Compile the main program shader
     */
    protected abstract Integer compileShader();

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!MathUtils.hasBits(renderPass, getRenderPass()))
            return;

        // Instanced mesh has been released
        if (_released) {
            release();
            return;
        }

        // Nothing to draw
        if (_pendingInstances.isEmpty())
            return;

        // Compile the main program shader (if we haven't already)
        _programID = compileShader();

        // The VAO has not been setup yet, which implies this is the first draw call
        if (_vao == null) {

            // Setup VAO
            _vao = new int[1];
            GLES30.glGenVertexArrays(1, _vao, 0);
            GLES30.glBindVertexArray(_vao[0]);

            // Setup various buffers that will be passed to the vertex shader
            GLES30.glUseProgram(_programID);
            setupVertexBuffers();
        }

        // Flag if the instance matrices need to be updated
        boolean updateMatrices = false;

        // Map has been moved - need to update LCS
        if (!view.scene.equals(_lcs.sceneModel)) {
            view.scratch.geo.set(view.currentPass.drawLat,
                    view.currentPass.drawLng);
            view.scratch.geo.set(GeoPoint.UNKNOWN);
            view.scene.mapProjection.forward(view.scratch.geo,
                    view.scratch.pointD);
            LocalCoordinateSystem.deriveFrom(view.scene, view.scratch.pointD,
                    _lcs);
            updateMatrices = true;
        }

        // Check draw version
        if (_drawVersion != view.currentPass.drawVersion) {
            _drawVersion = view.currentPass.drawVersion;
            updateMatrices = true;
        }

        GLES30.glUseProgram(_programID);

        // Bind our vao so we don't mess with the pseudo fixed function pipeline
        GLES30.glBindVertexArray(_vao[0]);

        if (updateMatrices) {
            // Update LCS
            _drawInstances.clear();
            _drawInstances.addAll(_pendingInstances);

            // Instance count has exceeded our allocated buffer size - increase
            if (_drawInstances.size() > _instanceLimit)
                expandInstanceBuffers();
        }

        // Check if any of the drawn instances are "dirty"
        for (GLInstanceData data : _drawInstances) {
            if (data.isDirty()) {
                updateMatrices = true;
                break;
            }
        }

        // Update instance buffers
        if (updateMatrices)
            updateMatrices(view);

        Matrix viewMat = Matrix.getIdentity();
        viewMat.set(_lcs.forward);
        viewMat.scale(1f, 1f, view.elevationScaleFactor);
        viewMat.translate(0f, 0f, GLMapView.elevationOffset);

        // Upload the view matrix
        int viewLoc = GLES30.glGetUniformLocation(_programID, "view");
        viewMat.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for (int i = 0; i < 16; i++)
            view.scratch.matrixF[i] = (float) view.scratch.matrixD[i];
        GLES30.glUniformMatrix4fv(viewLoc, 1, false, view.scratch.matrixF, 0);

        // upload the projection matrix
        int projLoc = GLES30.glGetUniformLocation(_programID, "projection");
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION,
                view.scratch.matrixF, 0);
        GLES30.glUniformMatrix4fv(projLoc, 1, false, view.scratch.matrixF, 0);

        // Draw the mesh instances
        drawInstanced(view);

        // Un-bind VAO
        GLES30.glBindVertexArray(0);
    }

    /**
     * Setup vertex buffers
     */
    protected abstract void setupVertexBuffers();

    /**
     * Perform instanced draw calls here
     * At this point the matrices have been set up
     * @param view Map view
     */
    protected abstract void drawInstanced(GLMapView view);

    /**
     * Get the layout index of the location pointer (the matrix used to position the renderable)
     */
    protected abstract int getPositionPointer();

    protected void expandInstanceBuffers() {
        // Cleanup existing buffers
        deleteBuffers(_mxBuffer, _mxBufferID);

        // Increase the instance limit
        _instanceLimit = SIZE_BUF
                * ((int) Math.ceil((float) _drawInstances.size() / SIZE_BUF));

        //Log.d(TAG, _name + ": Creating new buffers with size = " + _instanceLimit);

        // Create transform buffers
        _mxBufferSize = SIZE_MAT4 * _instanceLimit;
        _mxBuffer = createFloatBuffer(_mxBufferSize);
        _mxBufferPtr = Unsafe.getBufferPointer(_mxBuffer);
        _mxBufferID = setupInstanceBuffer(_mxBuffer, _mxBufferSize);

        //Log.d(TAG, _name + ": Created new buffers with size = " + _instanceLimit);
    }

    protected void updateMatrices(GLMapView view) {
        long mxPtr = _mxBufferPtr;
        for (GLInstanceData data : _drawInstances) {
            // Update local frames
            Matrix localFrame = Matrix.getIdentity();
            localFrame.translate(-_lcs.origin.x, -_lcs.origin.y,
                    -_lcs.origin.z);
            if (view.drawSrid == ECEFProjection.INSTANCE
                    .getSpatialReferenceID()) {
                // 3D globe projection
                localFrame.concatenate(data.getLocalECEF());
            } else {
                // Default 2D equirectangular map projection
                localFrame.concatenate(data.getLocalFrame());
            }
            localFrame.get(view.scratch.matrixD,
                    Matrix.MatrixOrder.COLUMN_MAJOR);
            for (int i = 0; i < SIZE_VEC4; i += 4) {
                Unsafe.setFloats(mxPtr, (float) view.scratch.matrixD[i],
                        (float) view.scratch.matrixD[i + 1],
                        (float) view.scratch.matrixD[i + 2],
                        (float) view.scratch.matrixD[i + 3]);
                mxPtr += SIZE_VEC4;
            }
        }

        // Transfer the Matrix data from the FloatBuffer to an instance buffer
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, _mxBufferID[0]);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, _mxBufferSize,
                _mxBuffer);

        // Matrices take up 4 layout positions, so we need to setup the data for
        // each of the slots and enable them
        for (int i = 0; i < 4; i++)
            setupInstancePointer(getPositionPointer() + i, 4, SIZE_MAT4,
                    SIZE_VEC4 * i);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void release() {
        _mxBufferID = deleteBuffers(_mxBuffer, _mxBufferID);
        _mxBuffer = null;
        if (_vao != null) {
            GLES30.glDeleteVertexArrays(1, _vao, 0);
            _vao = null;
        }
        _instanceLimit = 0;
    }

    public void flagRelease() {
        _released = true;
    }

    /**
     * Compile the main program shader
     */
    protected static int compileShader(String vtxShader, String fragShader) {
        int instancedVertexShaderID = -1;
        int fragmentShaderID = -1;
        try {
            instancedVertexShaderID = GLES20FixedPipeline.loadShader(
                    GLES30.GL_VERTEX_SHADER, vtxShader);
            fragmentShaderID = GLES20FixedPipeline.loadShader(
                    GLES30.GL_FRAGMENT_SHADER, fragShader);
        } catch (Exception e) {
            int error = GLES30.glGetError();
            Log.e(TAG, "Error: " + error, e);
        }
        return GLES20FixedPipeline.createProgram(instancedVertexShaderID,
                fragmentShaderID);
    }

    /**
     * Delete a buffer and return null so we can nullify the reference in 1 line
     * @param buffer Buffer to delete (null to ignore)
     * @param bufIndex Buffer index (null to ignore)
     * @return Null
     */
    protected static int[] deleteBuffers(Buffer buffer, int[] bufIndex) {
        if (buffer != null)
            Unsafe.free(buffer);
        if (bufIndex != null)
            GLES30.glDeleteBuffers(1, bufIndex, 0);
        return null;
    }

    protected static int[] deleteBuffers(int[] bufIndex) {
        return deleteBuffers(null, bufIndex);
    }

    /**
     * Create a simple float buffer in native memory
     * @param size Size of buffer in bytes
     * @return New float buffer
     */
    protected static FloatBuffer createFloatBuffer(int size) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size);
        byteBuffer.order(ByteOrder.nativeOrder());
        return byteBuffer.asFloatBuffer();
    }

    /**
     * Setup an instance buffer
     * @param buffer Underlying buffer data
     * @param size Size of buffer
     * @return Generated buffer ID
     */
    protected static int[] setupInstanceBuffer(Buffer buffer, int size) {
        int[] id = new int[1];
        GLES30.glGenBuffers(1, id, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, id[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, size, buffer,
                GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        return id;
    }

    /**
     * Setup instance pointer
     * @param idx Vertex attribute array index (see vertex shader layout locations)
     * @param size Size of each data point in the buffer
     * @param stride Data stride in bytes
     * @param offset Offset in bytes
     */
    protected static void setupInstancePointer(int idx, int size, int stride,
            int offset) {
        GLES30.glVertexAttribPointer(idx, size, GLES30.GL_FLOAT, false,
                stride, offset);
        GLES30.glEnableVertexAttribArray(idx);
        GLES30.glVertexAttribDivisor(idx, 1);
    }
}
