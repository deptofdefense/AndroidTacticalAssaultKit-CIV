package com.atakmap.map.layer.model.opengl;

import android.graphics.BitmapFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.maps.tilesets.graphics.GLPendingTexture;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelFactory;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLWireFrame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLModel2 implements GLMapRenderable2, GLResolvable {

    public final static String TAG = "GLModel2";

    private final MapRenderer renderContext;
    private final ModelInfo sourceInfo;
    private ModelInfo drawInfo;
    private Model model;
    private PointD modelAnchorPoint;
    private Thread modelLoader;
    private int drawSrid;
    private GLTexture texture;
    int[] vbo;
    private GLPendingTexture ptex;
    private FloatBuffer wireframe;
    private State state;
    private boolean suspended;

    public GLModel2(MapRenderer renderContext, ModelInfo info) {
        this.renderContext = renderContext;
        this.sourceInfo = info;
        this.model = null;
        this.state = State.UNRESOLVED;
    }
    @Override
    public synchronized void draw(final GLMapView view, int renderPass) {
        if(!MathUtils.hasBits(renderPass, getRenderPass()))
            return;
        // record the SRID for the renderer
        this.drawSrid = view.drawSrid;

        // if we have a model, but its SRID does not match the renderer SRID,
        // move back into the unresolved state
        if(this.drawInfo != null && drawInfo.srid != this.drawSrid) {
            this.state = State.UNRESOLVED;
        }

        // if the model is unresolved, kick off a new load operation
        if(this.state == State.UNRESOLVED) {
            this.modelLoader = new Thread(new Worker(), TAG + "-ModelLoader");
            this.modelLoader.setPriority(Thread.NORM_PRIORITY);
            this.modelLoader.start();
            this.state = State.RESOLVING;
        }

        if(this.state != State.RESOLVED)
            return;

        // XXX - render model
        Matrix mx = Matrix.getIdentity();
        mx.set(view.scene.forward);
        mx.scale(1f, 1f, view.elevationScaleFactor);
        mx.translate(0f, 0f, view.elevationOffset);

        if(drawInfo.altitudeMode == ModelInfo.AltitudeMode.Relative) {
            view.scratch.pointD.x = modelAnchorPoint.x;
            view.scratch.pointD.y = modelAnchorPoint.y;
            view.scratch.pointD.z = modelAnchorPoint.z;
            if (drawInfo.localFrame != null)
                drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            double localElevation = view.getTerrainMeshElevation(view.scratch.geo.getLatitude(), view.scratch.geo.getLongitude());

            // adjust the model to the local elevation
            mx.translate(0, 0, (float) (localElevation - view.scratch.pointD.z));
        }

        if(this.drawInfo.localFrame != null) {
            // transform from local frame to SR CS
            mx.concatenate(this.drawInfo.localFrame);
        }

        // fill the forward matrix for the Model-View
        mx.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < 16; i++)
            view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glLoadMatrixf(view.scratch.matrixF, 0);

        if(ptex != null && ptex.isResolved()) {
            texture = ptex.getTexture();
            ptex = null;
        }

        //if(ptex != null && !ptex.isResolved()) {
        if(Models.getTextureUri(model) == null || (ptex != null && !ptex.isResolved())) {
            // if the model is textured but the texture is not yet resolved,
            // draw the wireframe
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            /*
            if (vbo == null) {
                // generate the VBO
                vbo = new int[1];
                GLES20FixedPipeline.glGenBuffers(1, vbo, 0);

                // bind the VBO
                GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, vbo[0]);

                // upload the buffer data as static
                GLES20FixedPipeline.glBufferData(GLES20FixedPipeline.GL_ARRAY_BUFFER,
                        model.getNumVertices() * model.getVertexStride(Model.VERTEX_ATTR_POSITION),
                        model.getVertices(Model.VERTEX_ATTR_POSITION),
                        GLES20FixedPipeline.GL_STATIC_DRAW);

                // free the vertex array
                model.dispose();
            } else {
                // bind the VBO
                GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, vbo[0]);
            }
            */

            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, wireframe);

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA, GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline.glColor4f(0f, 1f, 0f, 1f);

            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, wireframe.limit()/3);

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            //GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, 0);
        } else {
            final boolean disableCullFace = (model.getFaceWindingOrder() != Model.WindingOrder.Undefined) && !GLES20FixedPipeline.glIsEnabled(GLES20FixedPipeline.GL_CULL_FACE);
            int[] cullFaceRestore = null;
            if(model.getFaceWindingOrder() != Model.WindingOrder.Undefined) {
                cullFaceRestore = new int[2];
                GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_CULL_FACE, cullFaceRestore, 0);
                GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_FRONT_FACE, cullFaceRestore, 1);

                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_CULL_FACE);
                GLES20FixedPipeline.glCullFace(GLES20FixedPipeline.GL_BACK);
                GLES20FixedPipeline.glFrontFace(GLES20FixedPipeline.GL_CCW);
            }

            final VertexDataLayout vertexDataLayout = model.getVertexDataLayout();
            final boolean isTextured = (MathUtils.hasBits(vertexDataLayout.attributes, Model.VERTEX_ATTR_TEXCOORD_0) && texture != null);
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            if (isTextured)
                GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);

            if (vbo == null) {
                // generate the VBO
                vbo = new int[1];
                GLES20FixedPipeline.glGenBuffers(1, vbo, 0);

                // bind the VBO
                GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, vbo[0]);

                // upload the buffer data as static
                GLES20FixedPipeline.glBufferData(GLES20FixedPipeline.GL_ARRAY_BUFFER,
                        model.getNumVertices() * vertexDataLayout.position.stride,
                        model.getVertices(Model.VERTEX_ATTR_POSITION),
                        GLES20FixedPipeline.GL_STATIC_DRAW);

                // free the vertex array
                model.dispose();
            } else {
                // bind the VBO
                GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, vbo[0]);
            }

            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, vertexDataLayout.position.stride, vertexDataLayout.position.offset);
            if (isTextured) {
                GLES20FixedPipeline.glTexCoordPointer(2, GLES20FixedPipeline.GL_FLOAT, vertexDataLayout.texCoord0.stride, vertexDataLayout.texCoord0.offset);
                GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, texture.getTexId());
            }

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA, GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline.glColor4f(1f, 1f, 1f, 1f);

            int mode;
            switch(model.getDrawMode()) {
                case Triangles:
                    mode = GLES20FixedPipeline.GL_TRIANGLES;
                    break;
                case TriangleStrip:
                    mode = GLES20FixedPipeline.GL_TRIANGLE_STRIP;
                    break;
                default :
                    // XXX -
                    return;
            }

            if (model.isIndexed()) {
                GLES20FixedPipeline.glDrawElements(mode, Models.getNumIndices(model), GLES20FixedPipeline.GL_UNSIGNED_SHORT, model.getIndices());
            } else {
                GLES20FixedPipeline.glDrawArrays(mode, 0, model.getNumVertices());
            }

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

            if(disableCullFace)
                GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_CULL_FACE);
            if(cullFaceRestore != null) {
                GLES20FixedPipeline.glCullFace(cullFaceRestore[0]);
                GLES20FixedPipeline.glFrontFace(cullFaceRestore[1]);
            }

            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            if (isTextured)
                GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);

            GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, 0);
        }

        GLES20FixedPipeline.glPopMatrix();

        if(false) {
            Envelope aabb = model.getAABB();

            view.scratch.pointD.x = aabb.minX;
            view.scratch.pointD.y = aabb.minY;
            view.scratch.pointD.z = aabb.minZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float ax = (float) view.scratch.pointD.x;
            final float ay = (float) view.scratch.pointD.y;
            final float az = (float) view.scratch.pointD.z;

            view.scratch.pointD.x = aabb.minX;
            view.scratch.pointD.y = aabb.maxY;
            view.scratch.pointD.z = aabb.minZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float bx = (float) view.scratch.pointD.x;
            final float by = (float) view.scratch.pointD.y;
            final float bz = (float) view.scratch.pointD.z;

            view.scratch.pointD.x = aabb.maxX;
            view.scratch.pointD.y = aabb.maxY;
            view.scratch.pointD.z = aabb.minZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float cx = (float) view.scratch.pointD.x;
            final float cy = (float) view.scratch.pointD.y;
            final float cz = (float) view.scratch.pointD.z;

            view.scratch.pointD.x = aabb.maxX;
            view.scratch.pointD.y = aabb.minY;
            view.scratch.pointD.z = aabb.minZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float dx = (float) view.scratch.pointD.x;
            final float dy = (float) view.scratch.pointD.y;
            final float dz = (float) view.scratch.pointD.z;

            view.scratch.pointD.x = aabb.minX;
            view.scratch.pointD.y = aabb.minY;
            view.scratch.pointD.z = aabb.maxZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float ex = (float) view.scratch.pointD.x;
            final float ey = (float) view.scratch.pointD.y;
            final float ez = (float) view.scratch.pointD.z;

            view.scratch.pointD.x = aabb.minX;
            view.scratch.pointD.y = aabb.maxY;
            view.scratch.pointD.z = aabb.maxZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float fx = (float) view.scratch.pointD.x;
            final float fy = (float) view.scratch.pointD.y;
            final float fz = (float) view.scratch.pointD.z;

            view.scratch.pointD.x = aabb.maxX;
            view.scratch.pointD.y = aabb.maxY;
            view.scratch.pointD.z = aabb.maxZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float gx = (float) view.scratch.pointD.x;
            final float gy = (float) view.scratch.pointD.y;
            final float gz = (float) view.scratch.pointD.z;

            view.scratch.pointD.x = aabb.maxX;
            view.scratch.pointD.y = aabb.minY;
            view.scratch.pointD.z = aabb.maxZ;
            drawInfo.localFrame.transform(view.scratch.pointD, view.scratch.pointD);
            view.scene.mapProjection.inverse(view.scratch.pointD, view.scratch.geo);
            view.scene.forward(view.scratch.geo, view.scratch.pointD);
            final float hx = (float) view.scratch.pointD.x;
            final float hy = (float) view.scratch.pointD.y;
            final float hz = (float) view.scratch.pointD.z;

            ByteBuffer buf = Unsafe.allocateDirect(12 * 2 * 3 * 4);
            buf.order(ByteOrder.nativeOrder());

            // a-b
            buf.putFloat(ax);
            buf.putFloat(ay);
            buf.putFloat(az);
            buf.putFloat(bx);
            buf.putFloat(by);
            buf.putFloat(bz);

            // b-c
            buf.putFloat(bx);
            buf.putFloat(by);
            buf.putFloat(bz);
            buf.putFloat(cx);
            buf.putFloat(cy);
            buf.putFloat(cz);

            // c-d
            buf.putFloat(cx);
            buf.putFloat(cy);
            buf.putFloat(cz);
            buf.putFloat(dx);
            buf.putFloat(dy);
            buf.putFloat(dz);

            // d-a
            buf.putFloat(dx);
            buf.putFloat(dy);
            buf.putFloat(dz);
            buf.putFloat(ax);
            buf.putFloat(ay);
            buf.putFloat(az);

            // e-f
            buf.putFloat(ex);
            buf.putFloat(ey);
            buf.putFloat(ez);
            buf.putFloat(fx);
            buf.putFloat(fy);
            buf.putFloat(fz);

            // f-g
            buf.putFloat(fx);
            buf.putFloat(fy);
            buf.putFloat(fz);
            buf.putFloat(gx);
            buf.putFloat(gy);
            buf.putFloat(gz);

            // g-h
            buf.putFloat(gx);
            buf.putFloat(gy);
            buf.putFloat(gz);
            buf.putFloat(hx);
            buf.putFloat(hy);
            buf.putFloat(hz);

            // h-e
            buf.putFloat(hx);
            buf.putFloat(hy);
            buf.putFloat(hz);
            buf.putFloat(ex);
            buf.putFloat(ey);
            buf.putFloat(ez);

            // a-e
            buf.putFloat(ax);
            buf.putFloat(ay);
            buf.putFloat(az);
            buf.putFloat(ex);
            buf.putFloat(ey);
            buf.putFloat(ez);

            // b-f
            buf.putFloat(bx);
            buf.putFloat(by);
            buf.putFloat(bz);
            buf.putFloat(fx);
            buf.putFloat(fy);
            buf.putFloat(fz);

            // c-g
            buf.putFloat(cx);
            buf.putFloat(cy);
            buf.putFloat(cz);
            buf.putFloat(gx);
            buf.putFloat(gy);
            buf.putFloat(gz);

            // d-h
            buf.putFloat(dx);
            buf.putFloat(dy);
            buf.putFloat(dz);
            buf.putFloat(hx);
            buf.putFloat(hy);
            buf.putFloat(hz);

            buf.flip();

            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, buf);
            GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
            GLES20FixedPipeline.glLineWidth(4);
            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, buf.limit() / 12);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            Unsafe.free(buf);
        }
    }

    @Override
    public synchronized void release() {
        this.modelLoader = null;
        if(this.model != null) {
            this.model.dispose();
            this.model = null;
            this.drawInfo = null;
        }

        this.state = State.UNRESOLVED;

        if(vbo != null)
            GLES20FixedPipeline.glDeleteBuffers(1, vbo, 0);
        vbo = null;
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES;
    }

    @Override
    public synchronized State getState() {
        return this.suspended ? this.state : State.SUSPENDED;
    }

    @Override
    public synchronized void suspend() {
        switch(this.state) {
            case UNRESOLVED:
            case RESOLVING:
                this.suspended = true;
                break;
            default :
                // already in a terminal state
                break;
        }
    }

    @Override
    public synchronized void resume() {
        this.suspended = false;
        this.notifyAll();
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            if(!checkProceed())
                return;

            Model m = ModelFactory.create(sourceInfo);
            if (m != null) {
                final int modelSrid = drawSrid;
                ModelInfo mInfo = new ModelInfo(sourceInfo);
                mInfo.altitudeMode = ModelInfo.AltitudeMode.Relative;
                if(modelSrid != mInfo.srid) {
                    if(!checkProceed())
                        return;
                    ModelInfo dst = new ModelInfo();
                    dst.srid = modelSrid;
                    Model xformed = Models.transform(mInfo, m, dst);
                    m.dispose();
                    m = xformed;
                    mInfo = dst;
                }
                if(!checkProceed()) {
                    m.dispose();
                    return;
                }
                PointD anchor = Models.findAnchorPoint(m);
                if(!checkProceed()) {
                    m.dispose();
                    return;
                }
                FloatBuffer wf = GLWireFrame.deriveLines(GLES20FixedPipeline.GL_TRIANGLES, 3, m.getVertexDataLayout().position.stride, (ByteBuffer)m.getVertices(Model.VERTEX_ATTR_POSITION), m.getNumVertices());
                synchronized (GLModel2.this) {
                    if (checkProceed()) {
                        model = m;
                        wireframe = wf;
                        drawInfo = mInfo;
                        modelAnchorPoint = anchor;

                        if(texture == null) {
                            // XXX - fix URI
                            try {
                                ptex = new GLPendingTexture(GLRenderGlobals.get(GLModel2.this.renderContext).getBitmapLoader().loadBitmap("file://" + Models.getTextureUri(m), (BitmapFactory.Options) null), null);
                            } catch (Throwable t) {
                                Log.e(TAG, "error", t);
                            }
                        }

                        GLModel2.this.state = State.RESOLVED;
                    } else {
                        m.dispose();
                    }
                }
            } else {
                synchronized(GLModel2.this) {
                    GLModel2.this.state = State.UNRESOLVABLE;
                }
            }
        }

        private boolean checkProceed() {
            synchronized(GLModel2.this) {
                while(true) {
                    if(Thread.currentThread() != GLModel2.this.modelLoader)
                        break;
                    if(GLModel2.this.suspended) {
                        try {
                            GLModel2.this.wait();
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    break;
                }

                return (Thread.currentThread() == GLModel2.this.modelLoader);
            }
        }
    }
}
