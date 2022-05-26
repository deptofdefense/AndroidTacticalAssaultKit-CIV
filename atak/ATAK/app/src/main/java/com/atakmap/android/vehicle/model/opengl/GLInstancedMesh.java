
package com.atakmap.android.vehicle.model.opengl;

import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.map.layer.model.opengl.GLMaterial;
import com.atakmap.map.layer.model.opengl.MaterialManager;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLTexture;

import java.nio.Buffer;
import java.nio.FloatBuffer;

/**
 * A single mesh used for multiple instances of a map item
 */
public class GLInstancedMesh extends GLInstancedRenderable {

    private static final String TAG = "GLInstancedMesh";

    // Vertex shader locations
    private static final int LOC_POSITION = 0,
            LOC_NORMAL = 1,
            LOC_TEXTURE = 2,
            LOC_COLOR = 3,
            LOC_MODEL = 4;

    private static final String VERTEX_SHADER_SOURCE = "" +
            "#version 300 es\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "layout(location = " + LOC_POSITION + ") in vec3 position;\n" +
            "layout(location = " + LOC_NORMAL + ") in vec3 normal;\n" +
            "layout(location = " + LOC_TEXTURE + ") in vec2 texCoords;\n" +
            "layout(location = " + LOC_COLOR + ") in vec4 color;\n" +
            "layout(location = " + LOC_MODEL + ") in mat4 model;\n" +
            "out vec2 oTexCoords;\n" +
            "out vec3 oNormal;\n" +
            "out vec4 oColor;\n" +
            "void main() {\n" +
            "   mat4 pvm = projection * view * model;\n" +
            "   gl_Position = pvm * vec4(position, 1.0);\n" +
            "   oNormal = normalize(mat3(pvm) * normal);\n" +
            "   oTexCoords = texCoords;\n" +
            "   oColor = color;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_SOURCE = "" +
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "uniform sampler2D uTexture;\n" +
            "out vec4 fragmentColor;\n" +
            "in vec2 oTexCoords;\n" +
            "in vec3 oNormal;\n" +
            "in vec4 oColor;\n" +
            "void main() {\n" +
            // Sample color from texture
            "  vec4 color = texture(uTexture, oTexCoords);\n" +
            // Discard translucent pixels
            "  if(color.a < 0.3)\n" +
            "    discard;\n" +
            // Apply color modulation
            "  color = color * oColor;\n" +
            // Apply lighting
            "  vec3 sun_position = vec3(3.0, 10.0, -5.0);\n" +
            "  vec3 sun_color = vec3(1.0, 1.0, 1.0);\n" +
            "  float lum = max(dot(oNormal, normalize(sun_position)), 0.0);\n" +
            "  color = color * vec4((0.6 + 0.4 * lum) * sun_color, 1.0);\n" +
            // Final fragment color
            "  fragmentColor = color;\n" +
            "}\n";

    private static Integer _programID;

    private final Mesh _subject;
    private final MaterialManager _matmgr;

    private int[] _posBuffer, _normalBuffer, _texBuffer;
    private GLMaterial[] _materials;
    private boolean[] _materialsResolved;

    // Color buffer
    private int[] _colorBufferID;
    private int _colorBufferSize;
    private long _colorBufferPtr;
    private FloatBuffer _colorBuffer;

    public GLInstancedMesh(String name, Mesh subject, MaterialManager matmgr) {
        super(name, GLMapView.RENDER_PASS_SCENES);
        _subject = subject;
        _matmgr = matmgr;
    }

    /**
     * Compile the main program shader
     */
    @Override
    protected Integer compileShader() {
        return _programID != null ? _programID
                : (_programID = compileShader(VERTEX_SHADER_SOURCE,
                        FRAGMENT_SHADER_SOURCE));
    }

    @Override
    protected int getPositionPointer() {
        return LOC_MODEL;
    }

    /**
     * Schedule materials/textures to load
     */
    private void loadMaterials() {
        if (_materials == null) {
            _materials = new GLMaterial[_subject.getNumMaterials()];
            _materialsResolved = new boolean[_materials.length];
            for (int i = 0; i < _materials.length; i++) {
                _materials[i] = _matmgr.load(_subject.getMaterial(i));
                _materialsResolved[i] = false;
            }
        }
    }

    /**
     * Setup material/texture parameters once they've been loaded
     */
    private void setupMaterials() {
        for (int i = 0; i < _materials.length; i++) {

            // Material has already been resolved
            if (_materialsResolved[i])
                continue;

            // Get material and texture
            GLMaterial material = _materials[i];
            GLTexture texture = material.getTexture();

            // Texture isn't loaded yet
            if (texture == null && material.isTextured())
                continue;

            // Set UV wrapping
            if (texture != null) {
                texture.setWrapS(GLES30.GL_MIRRORED_REPEAT);
                texture.setWrapT(GLES30.GL_MIRRORED_REPEAT);
            }

            // Apply mip-mapping if texture is a power of 2
            if (texture != null && MathUtils.isPowerOf2(texture.getTexWidth())
                    && MathUtils.isPowerOf2(texture.getTexHeight())) {
                GLES30.glEnable(GLES30.GL_TEXTURE_2D);
                texture.setMinFilter(GLES30.GL_LINEAR_MIPMAP_NEAREST);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.getTexId());
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
                GLES30.glDisable(GLES30.GL_TEXTURE_2D);
            }

            // Material has been resolved
            _materialsResolved[i] = true;
        }
    }

    @Override
    protected void setupVertexBuffers() {
        VertexDataLayout vdl = _subject.getVertexDataLayout();

        // Positions buffer
        _posBuffer = setupVertexBuffer(LOC_POSITION, vdl,
                Mesh.VERTEX_ATTR_POSITION, vdl.position, 3);

        // Normals buffer
        _normalBuffer = setupVertexBuffer(LOC_NORMAL, vdl,
                Mesh.VERTEX_ATTR_NORMAL, vdl.normal, 3);

        // Textures buffer
        _texBuffer = setupVertexBuffer(LOC_TEXTURE, vdl,
                Mesh.VERTEX_ATTR_TEXCOORD_0, vdl.texCoord0, 2);

        // Load materials for this mesh
        loadMaterials();
    }

    @Override
    protected void expandInstanceBuffers() {
        super.expandInstanceBuffers();

        // Create color modulation buffers
        deleteBuffers(_colorBuffer, _colorBufferID);
        _colorBufferSize = SIZE_VEC4 * _instanceLimit;
        _colorBuffer = createFloatBuffer(_colorBufferSize);
        _colorBufferPtr = Unsafe.getBufferPointer(_colorBuffer);
        _colorBufferID = setupInstanceBuffer(_colorBuffer, _colorBufferSize);
    }

    @Override
    protected void updateMatrices(GLMapView view) {
        super.updateMatrices(view);

        // Color modulation
        long cPtr = _colorBufferPtr;
        for (GLInstanceData data : _drawInstances) {
            float[] color = data.getColor();
            Unsafe.setFloats(cPtr, color[0], color[1], color[2], color[3]);
            cPtr += SIZE_VEC4;
        }

        // Setup instance color modulation
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, _colorBufferID[0]);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, _colorBufferSize,
                _colorBuffer);

        setupInstancePointer(LOC_COLOR, 4, SIZE_VEC4, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    @Override
    protected void drawInstanced(GLMapView view) {

        // First check if this mesh uses a valid drawing mode
        int mode = getDrawMode();
        if (mode == -1)
            return;

        // Make sure materials are set up
        setupMaterials();

        // Setup proper face culling
        final boolean disableCullFace = (_subject
                .getFaceWindingOrder() != Model.WindingOrder.Undefined)
                && !GLES30.glIsEnabled(GLES30.GL_CULL_FACE);
        int[] cullFaceRestore = null;
        if (_subject.getFaceWindingOrder() != Model.WindingOrder.Undefined) {
            cullFaceRestore = new int[2];
            GLES30.glGetIntegerv(GLES30.GL_CULL_FACE, cullFaceRestore, 0);
            GLES30.glGetIntegerv(GLES30.GL_FRONT_FACE, cullFaceRestore, 1);

            GLES30.glEnable(GLES30.GL_CULL_FACE);
            GLES30.glCullFace(GLES30.GL_BACK);

            // XXX - correct winding order based on enum
            GLES30.glFrontFace(GLES30.GL_CCW);
        }

        // Depth testing for proper face draw order
        boolean disableDepth = !GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST);
        int[] depthFunc = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_DEPTH_FUNC, depthFunc, 0);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);

        // Alpha blending
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        // Bind texture to be used (using the first one only, for now)
        if (_materials.length > 0) {
            GLTexture texture = _materials[0].getTexture();
            if (texture != null)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.getTexId());
        }

        // Set texture sampler target (texture index 0)
        int uTexture = GLES30.glGetUniformLocation(_programID, "uTexture");
        GLES30.glUniform1i(uTexture, 0);

        // Draw the model
        if (_subject.isIndexed()) {
            GLES30.glDrawElementsInstanced(mode, Models.getNumIndices(_subject),
                    GLES30.GL_UNSIGNED_SHORT, _subject.getIndices(),
                    _drawInstances.size());
        } else {
            GLES30.glDrawArraysInstanced(mode, 0, _subject.getNumVertices(),
                    _drawInstances.size());
        }

        // Unbind/disable stuff we binded/enabled above
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        GLES30.glDisable(GLES30.GL_BLEND);

        GLES30.glDepthFunc(depthFunc[0]);
        if (disableDepth)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        if (disableCullFace)
            GLES30.glDisable(GLES30.GL_CULL_FACE);
        if (cullFaceRestore != null) {
            GLES30.glCullFace(cullFaceRestore[0]);
            GLES30.glFrontFace(cullFaceRestore[1]);
        }
    }

    /**
     * Get the drawing mode for this mesh
     * @return Drawing mode
     */
    public int getDrawMode() {
        switch (_subject.getDrawMode()) {
            case Triangles:
                return GLES30.GL_TRIANGLES;
            case TriangleStrip:
                return GLES30.GL_TRIANGLE_STRIP;
            case Points:
                return GLES30.GL_POINTS;
        }
        return -1;
    }

    @Override
    public void release() {
        if (_materials != null) {
            for (GLMaterial material : _materials)
                _matmgr.unload(material);
            _materials = null;
        }
        _colorBufferID = deleteBuffers(_colorBuffer, _colorBufferID);
        _colorBuffer = null;
        _posBuffer = deleteBuffers(_posBuffer);
        _normalBuffer = deleteBuffers(_normalBuffer);
        _texBuffer = deleteBuffers(_texBuffer);
        super.release();
    }

    /**
     * Generic method for setting up a vertex buffer for the subject mesh
     * @param idx Vertex attribute array index (see vertex shader layout locations)
     * @param vdl The vertex data layout
     * @param attr Mesh attribute index (i.e. {@link Mesh#VERTEX_ATTR_POSITION})
     * @param vdlArr Vertex data layout array (for reading stride and offset)
     * @param size Size of the vertex (usually 2 for texture coordinates, 3 for everything else)
     * @return Generated buffer ID
     */
    private int[] setupVertexBuffer(int idx, VertexDataLayout vdl, int attr,
            VertexDataLayout.Array vdlArr, int size) {
        if (!MathUtils.hasBits(vdl.attributes, attr))
            return null;
        try {
            // NOTE: assumes float, per `glVertexAttribPointer` definition
            final int bufSize = vdlArr.offset
                    + ((_subject.getNumVertices() - 1) * vdlArr.stride)
                    + (size * 4);
            Buffer buf = _subject.getVertices(attr);
            int[] bufID = new int[1];
            GLES30.glGenBuffers(1, bufID, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufID[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,
                    bufSize,
                    buf, GLES30.GL_STATIC_DRAW);
            GLES30.glVertexAttribPointer(idx, size, GLES30.GL_FLOAT, false,
                    vdlArr.stride, vdlArr.offset);
            GLES30.glEnableVertexAttribArray(idx);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            return bufID;
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup vertex buffer for " + _name
                    + " (attr = " + attr + ")", e);
            return null;
        }
    }
}
