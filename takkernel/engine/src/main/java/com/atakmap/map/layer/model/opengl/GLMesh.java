package com.atakmap.map.layer.model.opengl;

import android.graphics.Color;
import android.graphics.PointF;
import android.opengl.GLES30;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class GLMesh implements GLMapRenderable2, Controls {

    private static String TAG = "GLMesh";

    private MapRenderer ctx;
    private Mesh subject;
    private GLMaterial[] materials;
    private boolean[] materialInitialized;
    private Matrix localFrame;
    private ModelInfo.AltitudeMode altitudeMode;
    private PointD modelAnchorPoint;
    private double modelZOffset;
    private int offsetTerrainVersion;
    private Buffer wireFrame;
    private boolean allowTexture = true;
    private int[] vbo;
    private int vboSize;
    private boolean vboDirty;
    private Shader[] shader;
    private Shader wireframeShader;
    private boolean disposeMesh = true;

    private Matrix lla2ecef;

    private MaterialManager matmgr;

    private Collection<MapControl> controls;

    float r = 1f;
    float g = 1f;
    float b = 1f;
    float a = 1f;

    boolean useVbo = true;

    public GLMesh(final ModelInfo modelInfo, final Mesh subject, final PointD anchor, final MaterialManager matmgr) {
        this(modelInfo.localFrame, modelInfo.altitudeMode, subject, anchor, matmgr);
    }

    public GLMesh(final Matrix localFrame, final ModelInfo.AltitudeMode altitudeMode, final Mesh subject, final PointD anchor, MaterialManager matmgr) {
        this(null, localFrame, altitudeMode, subject, anchor, matmgr);
    }

    public GLMesh(MapRenderer ctx, final Matrix localFrame, final ModelInfo.AltitudeMode altitudeMode, final Mesh subject, final PointD anchor, MaterialManager matmgr) {
        this.ctx = ctx;
        this.subject = subject;

        this.localFrame = localFrame;
        this.altitudeMode = altitudeMode;
        this.modelAnchorPoint = anchor;
        this.offsetTerrainVersion = -1;
        this.matmgr = matmgr;

        this.controls = new ArrayList<>(1);
        this.controls.add(new ColorControlImpl());

        // XXX - kind of arbitrary, should come in as hint
        this.useVbo = this.subject.getNumVertices() > 0xFFFF;

        // need to use VAOs right now if vertex data is not interleaved
        this.useVbo &= this.subject.getVertexDataLayout().interleaved;
    }

    private void initMaterials(GLMapView view) {
        if(this.materials != null)
            return;

        final int numMaterials = this.subject.getNumMaterials();

        final VertexDataLayout vertexDataLayout = this.subject.getVertexDataLayout();

        this.materials = new GLMaterial[numMaterials];
        this.shader = new Shader[numMaterials];
        for(int i = 0; i < this.materials.length; i++) {
            this.materials[i] = this.matmgr.load(subject.getMaterial(i));
            this.shader[i] = getShader(view, vertexDataLayout, this.materials[i]);
        }

        this.materialInitialized = new boolean[this.materials.length];
        Arrays.fill(this.materialInitialized, false);
    }

    public Mesh getSubject() {
        return subject;
    }

    /**
     * Set whether releasing this GL instance should dispose the subject mesh
     *
     * Note: It's unusual for a GL instance to modify its subject like this in
     * the first place. Ideally this class wouldn't dispose or permanently
     * modify the mesh subject.
     *
     * @param disposeMesh True to dispose mesh on release
     */
    public void setDisposeMesh(boolean disposeMesh) {
        this.disposeMesh = disposeMesh;
    }

    private static Shader getShader(GLMapView view, VertexDataLayout layout, GLMaterial material) {
        final boolean isTextured = (material.isTextured() && material.getTexture() != null);
        boolean alphaDiscard = false;
        if(isTextured) {
            GLTexture tex = material.getTexture();
            if (tex != null) 
                alphaDiscard = (tex.getFormat() == GLES30.GL_RGBA);
        }

        int flags = 0;
        if(isTextured)
            flags |= Shader.FLAG_TEXTURED;
        if(alphaDiscard)
            flags |= Shader.FLAG_ALPHA_DISCARD;
        if(MathUtils.hasBits(layout.attributes, Mesh.VERTEX_ATTR_COLOR))
            flags |= Shader.FLAG_COLOR_POINTER;
        if(MathUtils.hasBits(layout.attributes, Mesh.VERTEX_ATTR_NORMAL))
            flags |= Shader.FLAG_NORMALS;

        return Shader.get(view, flags);
    }
    private boolean resolveMaterials(GLMapView view) {
        boolean result = true;
        for (int i = 0; i < materials.length; i++) {
            GLMaterial material = materials[i];
            GLTexture texture = material.getTexture();
            if(texture == null && material.isTextured()) {
                result = false;
                continue;
            }

            if(this.materialInitialized[i])
                continue;

            if(texture != null) {
                texture.setWrapS(GLES30.GL_MIRRORED_REPEAT);
                texture.setWrapT(GLES30.GL_MIRRORED_REPEAT);
            }

            if(texture != null && MathUtils.isPowerOf2(texture.getTexWidth()) && MathUtils.isPowerOf2(texture.getTexHeight())) {
                // apply mipmap if texture is power-of-2
                GLES30.glEnable(GLES30.GL_TEXTURE_2D);
                texture.setMinFilter(GLES30.GL_LINEAR_MIPMAP_NEAREST);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.getTexId());
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,0);
                GLES30.glDisable(GLES30.GL_TEXTURE_2D);
            }

            this.shader[i] = getShader(view, this.subject.getVertexDataLayout(), this.materials[i]);
            this.materialInitialized[i] = true;
        }
        return result;
    }

    private void setMatrices(GLMapView view, Shader shader, boolean mv, boolean p, boolean t) {
        if(mv) {
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            GLES30.glUniformMatrix4fv(shader.uModelView, 1, false, view.scratch.matrixF, 0);
        }
        if(p) {
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            GLES30.glUniformMatrix4fv(shader.uProjection, 1, false, view.scratch.matrixF, 0);
        }
        if(t) {
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_TEXTURE, view.scratch.matrixF, 0);
            GLES30.glUniformMatrix4fv(shader.uTextureMx, 1, false, view.scratch.matrixF, 0);
        }
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        this.draw(view, renderPass, null);
    }

    /**
     * @deprecated !!!EXPERIMENTAL ONLY, DO NOT CREATE A DEPENDENCY!!!
     * @param view
     * @param renderPass
     * @param transform
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = false)
    public final void draw(GLMapView view, int renderPass, Matrix transform) {
        if (!MathUtils.hasBits(renderPass, getRenderPass()))
            return;

        this.initMaterials(view);

        // check if the materials are loaded
        final boolean materialsResolved = resolveMaterials(view);

                /*GLTexture texture = (materialsResolved && this.materials.length > 0) ? this.materials[0].getTexture() : null;
        final boolean isTextured0 = (MathUtils.hasBits(
                vertexDataLayout.attributes, Mesh.VERTEX_ATTR_TEXCOORD_0)
                && texture != null);
        final boolean isTextured = allowTexture && isTextured0;*/
        // Is textured if at least 1 material is textured and all materials are resolved
        final boolean isTextured = allowTexture &&
                materialsResolved &&
                this.materials.length > 0 &&
                this.materials[0].isTextured();

        final VertexDataLayout vertexDataLayout = this.subject.getVertexDataLayout();

        // draw model
        Matrix mx = Matrix.getIdentity();
        mx.set(view.scene.forward);

        if (altitudeMode == ModelInfo.AltitudeMode.Relative) {
            if(modelAnchorPoint != null) {
                view.scratch.pointD.x = modelAnchorPoint.x;
                view.scratch.pointD.y = modelAnchorPoint.y;
                view.scratch.pointD.z = modelAnchorPoint.z;
            } else {
                Envelope aabb = subject.getAABB();
                view.scratch.pointD.x = (aabb.minX+aabb.maxX)/2d;
                view.scratch.pointD.y = (aabb.minY+aabb.maxY)/2d;
                if(aabb.minZ < 0d)
                    view.scratch.pointD.z = 0d;
                else
                    view.scratch.pointD.z = aabb.minZ;
            }
            if (localFrame != null)
                localFrame.transform(view.scratch.pointD,
                        view.scratch.pointD);
            // XXX - assuming source is 4326
            if(view.drawSrid == 4978) {
                // XXX - obtain origin as LLA
                view.scratch.geo.set(view.scratch.pointD.y, view.scratch.pointD.x, view.scratch.pointD.z);
            } else {
                view.scene.mapProjection.inverse(view.scratch.pointD,
                        view.scratch.geo);
            }

            double tz;
            final int terrainVersion = view.getTerrainVersion();
            if(this.offsetTerrainVersion != terrainVersion || true) {
                double localElevation = view.getTerrainMeshElevation(
                        view.scratch.geo.getLatitude(),
                        view.scratch.geo.getLongitude());

                // adjust the model to the local elevation
                this.modelZOffset = (localElevation - view.scratch.pointD.z);
                this.offsetTerrainVersion = terrainVersion;
            }

            if(view.drawTilt > 0d)
                tz = modelZOffset;
            else
                tz = -view.scratch.pointD.z;
        }

        // XXX - assuming source is 4326
        if(view.drawSrid == 4978) {
            if(lla2ecef == null)
                this.lla2ecef = lla2ecef(this.localFrame);
            mx.concatenate(this.lla2ecef);
        }

        mx.scale(1f, 1f, view.elevationScaleFactor);
        mx.translate(0f, 0f, view.elevationOffset);
        mx.translate(0, 0, modelZOffset);

        if (localFrame != null) {
            // transform from local frame to SR CS
            mx.concatenate(localFrame);
        }
        if(transform != null)
            mx.concatenate(transform);

        // fill the forward matrix for the Model-View
        mx.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for (int i = 0; i < 16; i++)
            view.scratch.matrixF[i] = (float) view.scratch.matrixD[i];

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glLoadMatrixf(view.scratch.matrixF, 0);

        // if we have a wireframe, draw it before drawing the model
        if (!isTextured && this.wireFrame != null) {
            if(this.wireframeShader == null)
                this.wireframeShader = Shader.get(view, 0);

            GLES30.glUseProgram(this.wireframeShader.handle);

            // set the matrices
            setMatrices(view, wireframeShader, true, true, false);

            GLES30.glEnableVertexAttribArray(this.wireframeShader.aVertexCoords);
            GLES30.glVertexAttribPointer(this.wireframeShader.aVertexCoords, 3, GLES30.GL_FLOAT, false, 0, this.wireFrame);
            GLES30.glUniform4f(this.wireframeShader.uColor,0.6f, 0f, 0f, 1f);
            GLES30.glDrawArrays(GLES30.GL_LINES, 0,this.wireFrame.limit() / 3);
            GLES30.glDisableVertexAttribArray(this.wireframeShader.aVertexCoords);
        }

        if(this.shader.length == 0)
            return;

        final boolean disableCullFace = (this.subject
                .getFaceWindingOrder() != Model.WindingOrder.Undefined)
                && !GLES30.glIsEnabled(GLES30.GL_CULL_FACE);
        int[] cullFaceRestore = null;
        if (this.subject.getFaceWindingOrder() != Model.WindingOrder.Undefined) {
            cullFaceRestore = new int[2];
            GLES30.glGetIntegerv(GLES30.GL_CULL_FACE,
                    cullFaceRestore, 0);
            GLES30.glGetIntegerv(GLES30.GL_FRONT_FACE,
                    cullFaceRestore, 1);

            GLES30.glEnable(GLES30.GL_CULL_FACE);
            GLES30.glCullFace(GLES30.GL_BACK);

            // XXX - correct winding order based on enum
            GLES30.glFrontFace(GLES30.GL_CCW);
        }

        if (useVbo && (vbo == null || this.vboDirty)) {
            if (this.vboDirty && vbo != null)
                GLES30.glDeleteBuffers(1, vbo, 0);

            // generate the VBO
            vbo = new int[1];
            GLES30.glGenBuffers(1, vbo, 0);

            // bind the VBO
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);

            // upload the buffer data as static
            GLES30.glBufferData(
                    GLES30.GL_ARRAY_BUFFER,
                    subject.getNumVertices()
                            * vertexDataLayout.position.stride,
                    subject.getVertices(Mesh.VERTEX_ATTR_POSITION),
                    GLES30.GL_STATIC_DRAW);

            // free the vertex array
            //model.dispose();
            this.vboDirty = false;
        } else if(useVbo){
            // bind the VBO
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);
        }

        // Depth testing for proper face draw order
        boolean disableDepth = !GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST);
        int[] depthFunc = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_DEPTH_FUNC, depthFunc, 0);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);

        // Alpha blending
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,
                GLES30.GL_ONE_MINUS_SRC_ALPHA);

        Shader last = null;
        for(int i = 0; i < this.shader.length; i++) {
            final int a = numAttribs(last);
            final int b = numAttribs(shader[i]);
            for(int j = a; j < b; j++)
                GLES30.glEnableVertexAttribArray(j);
            for(int j = a; j > b; j--)
                GLES30.glDisableVertexAttribArray(j);

            draw(view, shader[i], materials[i], shader[i] != last);
            last = shader[i];
        }
        if(last != null) {
            for(int i = last.numAttribs; i >= 0; i--)
                GLES30.glDisableVertexAttribArray(i);
        }

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

        if(useVbo)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,
                0);

        GLES20FixedPipeline.glPopMatrix();
    }

    private static int numAttribs(Shader shader) {
        return (shader != null) ? shader.numAttribs : 0;
    }

    private void draw(GLMapView view, Shader shader, GLMaterial material, boolean reset) {
        if(ctx == null)
            ctx = view;

        final VertexDataLayout vertexDataLayout = this.subject.getVertexDataLayout();


        if(reset) {
            GLES30.glUseProgram(shader.handle);

            // XXX - use layout attribute type
            if(useVbo) {
                GLES30.glVertexAttribPointer(shader.aVertexCoords,
                        3, GLES30.GL_FLOAT,
                        false,
                        vertexDataLayout.position.stride,
                        vertexDataLayout.position.offset);
            } else {
                ByteBuffer buf = (ByteBuffer)subject.getVertices(Mesh.VERTEX_ATTR_POSITION);
                buf = buf.duplicate();
                buf.position(vertexDataLayout.position.offset);

                GLES30.glVertexAttribPointer(shader.aVertexCoords,
                        3, GLES30.GL_FLOAT,
                        false,
                        vertexDataLayout.position.stride,
                        buf);
            }
            if (MathUtils.hasBits(vertexDataLayout.attributes, Mesh.VERTEX_ATTR_COLOR)) {
                // XXX - use attribute layout type
                if(useVbo) {
                    GLES30.glVertexAttribPointer(shader.aColorPointer,
                            4, GLES30.GL_UNSIGNED_BYTE,
                            true,
                            vertexDataLayout.color.stride,
                            vertexDataLayout.color.offset);
                }  else {
                    ByteBuffer buf = (ByteBuffer)subject.getVertices(Mesh.VERTEX_ATTR_COLOR);
                    buf = buf.duplicate();
                    buf.position(vertexDataLayout.color.offset);

                    GLES30.glVertexAttribPointer(shader.aColorPointer,
                            4, GLES30.GL_UNSIGNED_BYTE,
                            true,
                            vertexDataLayout.color.stride,
                            buf);
                }
            }
        }

        if(shader.lighting) {
            if(useVbo) {
                GLES30.glVertexAttribPointer(
                        shader.aNormals,
                        3,
                        GLES30.GL_FLOAT,
                        false,
                        vertexDataLayout.normal.stride,
                        vertexDataLayout.normal.offset);
            } else {
                ByteBuffer buf = (ByteBuffer)subject.getVertices(Mesh.VERTEX_ATTR_NORMAL);
                buf = buf.duplicate();
                buf.position(vertexDataLayout.normal.offset);

                GLES30.glVertexAttribPointer(shader.aNormals,
                        3, GLES30.GL_FLOAT,
                        false,
                        vertexDataLayout.normal.stride,
                        buf);
            }
        }

        float red = 1f;
        float green = 1f;
        float blue = 1f;
        float alpha = 1f;
        if (shader.textured) {
            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_TEXTURE);
            GLES20FixedPipeline.glPushMatrix();

            if(shader.alphaDiscard)
                GLES30.glUniform1f(shader.uAlphaDiscard, 0.3f);

            GLTexture texture = material.getTexture();
            if (texture != null) {
                // XXX - tex coord scaling assumes all material textures have same size
                GLES20FixedPipeline.glScalef((float)material.getWidth() / (float)texture.getTexWidth(), (float)material.getHeight() / (float)texture.getTexHeight(), 1f);

                GLES20FixedPipeline.glActiveTexture(GLES30.GL_TEXTURE0 + material.getSubject().getTexCoordIndex());
                VertexDataLayout.Array layoutArray = vertexDataLayout.getTexCoordArray(material.getSubject().getTexCoordIndex());
                // XXX - use attribute layout type
                if(useVbo) {
                    GLES30.glVertexAttribPointer(
                            shader.aTextureCoords,
                            2,
                            GLES30.GL_FLOAT,
                            false,
                            layoutArray.stride,
                            layoutArray.offset);
                } else {
                    ByteBuffer buf = (ByteBuffer)subject.getVertices(Mesh.VERTEX_ATTR_TEXCOORD_0);
                    buf = buf.duplicate();
                    buf.position(vertexDataLayout.texCoord0.offset);

                    GLES30.glVertexAttribPointer(shader.aTextureCoords,
                            2, GLES30.GL_FLOAT,
                            false,
                            vertexDataLayout.texCoord0.stride,
                            buf);
                }

                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,
                        texture.getTexId());

                GLES30.glUniform1i(shader.uTexture, material.getSubject().getTexCoordIndex());

                red = Color.red(material.getSubject().getColor()) / 255f;
                green = Color.green(material.getSubject().getColor()) / 255f;
                blue = Color.blue(material.getSubject().getColor()) / 255f;
                alpha = Color.alpha(material.getSubject().getColor()) / 255f;

            }

            // return back to zero
            GLES20FixedPipeline.glActiveTexture(GLES30.GL_TEXTURE0);
        } else {
            red = Color.red(material.getSubject().getColor()) / 255f;
            green = Color.green(material.getSubject().getColor()) / 255f;
            blue = Color.blue(material.getSubject().getColor()) / 255f;
            alpha = Color.alpha(material.getSubject().getColor()) / 255f;
        }

        alpha *= GLES20FixedPipeline.getAlphaMod();

        GLES30.glUniform4f(shader.uColor, red*r, green*g, blue*b, alpha*a);

        int mode;
        switch (this.subject.getDrawMode()) {
            case Triangles:
                mode = GLES30.GL_TRIANGLES;
                break;
            case TriangleStrip:
                mode = GLES30.GL_TRIANGLE_STRIP;
                break;
            case Points:
                mode = GLES30.GL_POINTS;
                break;
            default:
                // XXX -
                return;
        }

        // set the matrices
        if(reset)
            setMatrices(view, shader, true, true, shader.textured);

        if (subject.isIndexed()) {
            GLES30.glDrawElements(mode,
                    Models.getNumIndices(subject),
                    GLES30.GL_UNSIGNED_SHORT, subject.getIndices());
        } else {
            GLES30.glDrawArrays(mode, 0,
                    subject.getNumVertices());
        }

        if (shader.textured) {
            GLES20FixedPipeline.glPopMatrix();
            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
        }
    }

    @Override
    public void release() {
        if (this.subject != null && this.disposeMesh)
            this.subject.dispose();
        this.subject = null;

        if (this.materials != null) {
            for(int i = 0; i < this.materials.length; i++)
                this.matmgr.unload(this.materials[i]);
            this.materials = null;
        }

        if (vbo != null)
            GLES30.glDeleteBuffers(1, vbo, 0);
        vbo = null;
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SCENES;
    }

    /**
     * Performs a hit-test on the mesh using the specified scene model at the specified location.
     *
     * <P>This call is not thread-safe and must be externally synchronized by the caller
     *
     * @param sceneModel    The parameters of the scene rendering
     * @param x             The screen X location
     * @param y             The screen Y location
     * @param result        The hit location, if succesful
     * @return  <code>true</code> if a hit occurred, <code>false</code> otherwise
     */
    public boolean hitTest(MapSceneModel sceneModel, float x, float y, GeoPoint result) {
        // this is the local frame being sent in to the MapSceneModel inverse
        // method that will translate between the mesh LCS, applying any
        // LLA->ECEF and relative altitude adjustments, and the WCS. This
        // transform may initially be 'null' if neither LLA->ECEF or relative
        // altitude adjustments are being applied
        Matrix localFrame = null;
        if (this.subject == null)
            return false;
        final double zOffset = this.modelZOffset;

        // apply LLA->ECEF transform if applicable
        if(sceneModel.mapProjection.getSpatialReferenceID() == 4978) {
            if(this.lla2ecef == null)
                this.lla2ecef = lla2ecef(this.localFrame);
            localFrame = Matrix.getIdentity();
            localFrame.set(this.lla2ecef);
        }
        // apply relative altitude adjustment, if applicable
        if (zOffset != 0d) {
            if(localFrame == null)
                localFrame = Matrix.getIdentity();
            localFrame.translate(0d, 0d, zOffset);
        }

        // member field 'localFrame' may be 'null' here if there is no local
        // frame that transforms between the mesh LCS and the WCS -- in other
        // words LCS == WCS.

        if (this.localFrame != null && localFrame == null)
            // there's a LCS->WCS transform, but no LLA->ECEF and/or relative
            // altitude adjustment. we're passing through the LCS->WCS as
            // the transform to use for hit-test computation
            localFrame = this.localFrame;
        else if (this.localFrame != null) // & localFrame != null, per above check
            // there's a LCS->WCS transform and LLA->ECEF and/or relative
            // altitude adjustment. concatenate the LCS->WCS transform to
            // the LLA->ECEF and/or altitude adjustment
            localFrame.concatenate(this.localFrame);
        // else, no LCS->WCS, use any applicable LLA->ECEF and/or relative
        // altitude adjustment only

        // Note: this method accepts 'null' local frame, in which case it
        // assumes that LCS == WCS
        GeometryModel gm = Models.createGeometryModel(this.subject, localFrame);
        if (gm == null)
            return false;

        MapSceneModel scene = sceneModel;
        if (scene.inverse(new PointF(x, y), result, gm) == null)
            return false;

        // adjust altitude for renderer elevation offset
        if (result.isAltitudeValid()) {
            // specify a very small offset to move towards the camera. this is
            // to prevent z-fighting when a point is placed directly on the
            // surface. currently moving ~1ft
            final double offset = 0.30d;
            moveTowardsCamera(scene, x, y, result, offset);
        }
        return true;
    }

    /**
     * Call when the model's local frame has been modified
     * Currently this triggers recalculation of the LLA -> ECEF matrix
     * Should only be called on the GL thread
     */
    public void refreshLocalFrame() {
        this.lla2ecef = null;
    }

    private static Matrix lla2ecef(Matrix localFrame) {
        final Matrix mx = Matrix.getIdentity();

        PointD pointD = new PointD(0d, 0d, 0d);
        GeoPoint geo = GeoPoint.createMutable();

        // if draw projection is ECEF and source comes in as LLA, we can
        // transform from LLA to ECEF by creating a local ENU CS and
        // chaining the following conversions (all via matrix)
        // 1. LCS -> LLA
        // 2. LLA -> ENU
        // 3. ENU -> ECEF
        // 4. ECEF -> NDC (via MapSceneModel 'forward' matrix)

        // obtain origin as LLA
        pointD.x = 0;
        pointD.y = 0;
        pointD.z = 0;
        if(localFrame != null)
            localFrame.transform(pointD, pointD);
        // transform origin to ECEF
        geo.set(pointD.y, pointD.x, pointD.z);
        ECEFProjection.INSTANCE.forward(geo, pointD);

        // construct ENU -> ECEF
        final double phi = Math.toRadians(geo.getLatitude());
        final double lambda = Math.toRadians(geo.getLongitude());

        mx.translate(pointD.x, pointD.y, pointD.z);

        Matrix enu2ecef = new Matrix(
                -Math.sin(lambda), -Math.sin(phi)*Math.cos(lambda), Math.cos(phi)*Math.cos(lambda), 0d,
                Math.cos(lambda), -Math.sin(phi)*Math.sin(lambda), Math.cos(phi)*Math.sin(lambda), 0d,
                0, Math.cos(phi), Math.sin(phi), 0d,
                0d, 0d, 0d, 1d
        );

        mx.concatenate(enu2ecef);

        // construct LLA -> ENU
        final double metersPerDegLat = GeoCalculations.approximateMetersPerDegreeLatitude(geo.getLatitude());
        final double metersPerDegLng = GeoCalculations.approximateMetersPerDegreeLongitude(geo.getLatitude());

        mx.scale(metersPerDegLng, metersPerDegLat, 1d);
        mx.translate(-geo.getLongitude(), -geo.getLatitude(), -geo.getAltitude());

        return mx;
    }

    private static double adjustAltitude(double alt, double offset) {
        return alt + offset;
    }

    private static void moveTowardsCamera(MapSceneModel scene, float x, float y,
                                          GeoPoint gp, double meters) {
        PointD org = new PointD(x, y, -1d);
        scene.inverse.transform(org, org);
        PointD tgt = scene.mapProjection.forward(gp, null);

        double dx = org.x - tgt.x;
        double dy = org.y - tgt.y;
        double dz = org.z - tgt.z;
        final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= d;
        dy /= d;
        dz /= d;

        PointD off;

        off = scene.mapProjection.forward(
                new GeoPoint(gp.getLatitude(),
                        gp.getLongitude(),
                        adjustAltitude(gp.getAltitude(), meters)),
                null);
        final double tz = MathUtils.distance(tgt.x, tgt.y, tgt.z, off.x, off.y,
                off.z);
        off = scene.mapProjection.forward(
                computeDestinationPoint(gp, 0d, meters),
                null);
        final double tx = MathUtils.distance(tgt.x, tgt.y, tgt.z, off.x, off.y,
                off.z);
        off = scene.mapProjection.forward(
                computeDestinationPoint(gp, 90d, meters),
                null);
        final double ty = MathUtils.distance(tgt.x, tgt.y, tgt.z, off.x, off.y,
                off.z);

        tgt.x += dx * tx;
        tgt.y += dy * ty;
        tgt.z += dz * tz;

        scene.mapProjection.inverse(tgt, gp);
    }

    private static GeoPoint computeDestinationPoint(GeoPoint p, double a,
                                                    double d) {
        GeoPoint surface = GeoCalculations.pointAtDistance(p, a, d);
        return new GeoPoint(surface.getLatitude(), surface.getLongitude(),
                p.getAltitude(), p.getAltitudeReference(), GeoPoint.UNKNOWN, GeoPoint.UNKNOWN);
    }

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        for(Object ctrl : this.controls)
            if(controlClazz.isAssignableFrom(ctrl.getClass()))
                return controlClazz.cast(ctrl);
return null;
    }

    @Override
    public void getControls(Collection<Object> controls) {
        controls.addAll(this.controls);
    }

    private static class Shader {
        final static int FLAG_TEXTURED = 0x01;
        final static int FLAG_ALPHA_DISCARD = 0x02;
        final static int FLAG_COLOR_POINTER = 0x04;
        final static int FLAG_NORMALS = 0x08;

        // XXX - vsh textured with vertex fetch for alpha discard

        private final static Map<MapRenderer, Map<Integer, Shader>> shaders = new IdentityHashMap<>();

        final int handle;

        final int uProjection;
        final int uModelView;
        final int uTextureMx;
        final int uTexture;
        final int uAlphaDiscard;
        final int uColor;
        final int aVertexCoords;
        final int aTextureCoords;
        final int aColorPointer;
        final int aNormals;

        final boolean textured;
        final boolean alphaDiscard;
        final boolean colorPointer;
        final boolean lighting;
        final int numAttribs;

        private Shader(int flags) {
            this.textured = MathUtils.hasBits(flags, FLAG_TEXTURED);
            this.alphaDiscard = MathUtils.hasBits(flags, FLAG_ALPHA_DISCARD);
            this.colorPointer = MathUtils.hasBits(flags, FLAG_COLOR_POINTER);
            this.lighting = MathUtils.hasBits(flags, FLAG_NORMALS);

            // vertex shader source
            final StringBuilder vshsrc = new StringBuilder();
            vshsrc.append("#version 100\n");
            vshsrc.append("uniform mat4 uProjection;\n");
            vshsrc.append("uniform mat4 uModelView;\n");
            if(textured) {
                vshsrc.append("uniform mat4 uTextureMx;\n");
                vshsrc.append("attribute vec2 aTextureCoords;\n");
                vshsrc.append("varying vec2 vTexPos;\n");
            }
            vshsrc.append("attribute vec3 aVertexCoords;\n");
            if(colorPointer) {
                vshsrc.append("attribute vec4 aColorPointer;\n");
                vshsrc.append("varying vec4 vColor;\n");
            }
            if(lighting) {
                vshsrc.append("attribute vec3 aNormals;\n");
                vshsrc.append("varying vec3 vNormal;\n");
            }
            vshsrc.append("void main() {\n");
            if(textured) {
                vshsrc.append("  vec4 texCoords = uTextureMx * vec4(aTextureCoords.xy, 0.0, 1.0);\n");
                vshsrc.append("  vTexPos = texCoords.xy;\n");
            }
            if(colorPointer)
                vshsrc.append("  vColor = aColorPointer;\n");
            if(lighting)
                vshsrc.append("  vNormal = normalize(mat3(uProjection * uModelView) * aNormals);\n");
            vshsrc.append("  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n");
            vshsrc.append("}");

            // fragment shader source
            final StringBuilder fshsrc = new StringBuilder();
            fshsrc.append("#version 100\n");
            fshsrc.append("precision mediump float;\n");
            if(textured) {
                fshsrc.append("uniform sampler2D uTexture;\n");
                fshsrc.append("varying vec2 vTexPos;\n");
            }
            fshsrc.append("uniform vec4 uColor;\n");
            if(alphaDiscard)
                fshsrc.append("uniform float uAlphaDiscard;\n");
            if(colorPointer)
                fshsrc.append("varying vec4 vColor;\n");
            if(lighting)
                fshsrc.append("varying vec3 vNormal;\n");
            fshsrc.append("void main(void) {\n");
            // XXX - color pointer is NOT working with modulation, don't use it
            //       with texturing right now either until issues can be tested
            //       and resolved
            if(textured && colorPointer)
                fshsrc.append("  vec4 color = texture2D(uTexture, vTexPos) * vColor;\n");
            else if(textured && !colorPointer)
                fshsrc.append("  vec4 color = texture2D(uTexture, vTexPos);\n");
            else if(colorPointer)
                fshsrc.append("  vec4 color = vColor;\n");
            else
                fshsrc.append("  vec4 color = vec4(1.0, 1.0, 1.0, 1.0);\n");
            // XXX - should discard be before or after modulation???
            if(alphaDiscard) {
                fshsrc.append("  if(color.a < uAlphaDiscard)\n");
                fshsrc.append("    discard;\n");
            }
            if(lighting) {
                // XXX - next two as uniforms
                fshsrc.append("  vec3 sun_position = vec3(3.0, 10.0, -5.0);\n");
                fshsrc.append("  vec3 sun_color = vec3(1.0, 1.0, 1.0);\n");
                fshsrc.append("  float lum = max(dot(vNormal, normalize(sun_position)), 0.0);\n");
                fshsrc.append("  color = color * vec4((0.6 + 0.4 * lum) * sun_color, 1.0);\n");
            }
            fshsrc.append("  gl_FragColor = uColor * color;\n");
            fshsrc.append("}");


            int vsh = GLES20FixedPipeline.GL_NONE;
            int fsh = GLES20FixedPipeline.GL_NONE;
            try {
                vsh = GLES20FixedPipeline.loadShader(GLES30.GL_VERTEX_SHADER, vshsrc.toString());
                fsh = GLES20FixedPipeline.loadShader(GLES30.GL_FRAGMENT_SHADER, fshsrc.toString());
                handle = GLES20FixedPipeline.createProgram(vsh, fsh);
            } finally {
                if(vsh != GLES30.GL_NONE)
                    GLES30.glDeleteShader(vsh);
                if(fsh != GLES30.GL_NONE)
                    GLES30.glDeleteShader(fsh);
            }

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

            numAttribs = 1 + (colorPointer ? 1 : 0) + (textured ? 1 : 0) + (lighting ? 1 : 0);
        }

        /**
         * <P>MUST be invoked on GL thread
         *
         * @param ctx
         * @param flags
         * @return
         */
        synchronized static Shader get(MapRenderer ctx, int flags) {
            Map<Integer, Shader> ctxShaders = shaders.get(ctx);
            if(ctxShaders == null) {
                ctxShaders = new HashMap<Integer, Shader>();
                shaders.put(ctx, ctxShaders);
            }

            Shader retval = ctxShaders.get(flags);
            if(retval == null) {
                retval = new Shader(flags);
                ctxShaders.put(flags, retval);
            }

            return retval;
        }
    }

    class ColorControlImpl implements ColorControl {

        @Override
        public void setColor(int color) {
            r = Color.red(color) / 255f;
            g = Color.green(color) / 255f;
            b = Color.blue(color) / 255f;
            a = Color.alpha(color) / 255f;

            if(ctx != null)
                ctx.requestRefresh();
        }

        @Override
        public int getColor() {
            return (int)(a*255f)<<24 |
                   (int)(r*255f)<<16 |
                   (int)(g*255f)<<8 |
                   (int)(b*255f);
        }
    }
}
